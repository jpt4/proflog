# Query And Program Behavior

This file covers the current procedure-call, query-status, `P1`, `P2`, and
bounded-query examples from:

- `test/proflog/program_test.clj`
- `test/proflog/query_test.clj`
- `test/proflog/query_extended_test.clj`

## Clause Lookup Walkthrough

The simplest compiled program is:

```clojure
p(x) :- x = zero
```

Calling `program/call-clauseo` with the actual argument `succ(zero)` yields:

```clojure
env         = [[a_0 (app succ (app zero))]]
body        = (eq a_0 (app zero))
negated-body = (neq a_0 (app zero))
```

So the program layer does two things at once:

1. binds the compiled clause parameter to the actual argument,
2. exposes both the body and its negation for positive and negative
   subsidiary tableaux.

## Positive And Negative Procedure Calls

On the same program:

```clojure
p(x) :- x = zero
```

the current kernel shows the two closure directions directly.

### `p(one)` is false

Query:

```clojure
(pos (app p (app one)))
```

Proof term:

```clojure
(pos-call (free-close))
```

The positive call opens the clause body `one = zero`, which closes by
constructor clash.

### `not p(zero)` is false

Query:

```clojure
(neg (app p (app zero)))
```

Proof term:

```clojure
(neg-call (refl-close))
```

The negative call opens the negated body `zero != zero`, which closes
immediately.

The same pattern appears for the multi-argument example:

```clojure
pair-eq(x, y) :- x = y
```

- `pair-eq(zero, one)` closes as `(pos-call (free-close))`
- `not pair-eq(zero, zero)` closes as `(neg-call (refl-close))`

## Caller-Branch Isolation

The isolation example is:

```clojure
p(x) :- q(x)
```

The key point is negative:

- `p(zero) and not q(zero)` stays open
- `not p(zero) and q(zero)` stays open

The subsidiary tableau for `p` does not borrow unrelated caller literals.

## Query Status Walkthrough

For:

```clojure
p(x) :- x = zero
```

and declared-but-undefined relation `undef/1`, the public query API reports:

```clojure
p(0)      => :succeeds
p(1)      => :fails
undef(0)  => :unresolved
```

This is the operational distinction the greenfield query layer is currently
committed to preserve.

## Fitting `P1`

The current `P1` program is:

```clojure
even(x) :- x = zero
        or exists y. x = s(y) and odd(y)

odd(x)  :- forall y. (even(y) -> x != y)
```

### `even(0)` succeeds

Proof term:

```clojure
(neg-call (conj (refl-close)))
```

The base branch closes immediately because the negated body contains
`zero != zero`.

### `odd(1)` succeeds

Representative proof shape:

```clojure
(neg-call
 (witness
  (conj
   (savefml
    (eq-step
     (par-bind)
     (eq-triggered-call ...))))))
```

Operationally:

1. the negative call opens the negation of the `forall` body,
2. an existential witness is introduced,
3. equality binds that witness to `1`,
4. the saved equality unlocks the recursive `even` call,
5. the recursive branch eventually closes through `even(0)`.

### Deeper committed `P1` checks

The extended quantified suite also exercises:

```clojure
even(2) => succeeds
odd(0)  => fails
```

The `odd(0)` failure proof is compact:

```clojure
(pos-call
 (univ
  (split
   (neg-call (conj (neq-close (eq-bind))))
   (refl-close))))
```

The prover instantiates the universal, then closes both branches of the
disjunction.

## Fitting `P2` Inline Nim

The current inline Nim program is:

```clojure
win(x) :- exists y.
            (x = s(y) or x = s(s(y)))
            and not win(y)
```

Current direct ground pattern:

```clojure
win(0) => fails
win(1) => succeeds
win(2) => succeeds
win(3) => fails
win(4) => succeeds
win(5) => succeeds
```

### `win(3)` fails

Representative proof shape:

```clojure
(pos-call
 (witness
  (conj
   (split
    ...
    ...))))
```

The prover must show that every move from `3` lands in a winning position.
Both one-step and two-step move branches close.

## Factored `move` Warning

The factored program is:

```clojure
win(x)  :- exists y. move(x, y) and not win(y)
move(x, y) :- x = s(y) or x = s(s(y))
```

Ground `move/2` itself behaves normally under direct proof search:

```clojure
move(1, 0) => :succeeds
move(0, 1) => :fails
```

But the factored `win/1` no longer matches the inline Nim behavior on the
smallest positions. Using the bounded public status helper with a `1000ms`
probe budget:

```clojure
factored  win(0) => :unresolved
factored  win(1) => :unresolved
```

The inline program remains directly decidable by ordinary proof search:

```clojure
inline win(0) => fails
inline win(1) => succeeds
```

This is not just a greenfield implementation quirk. Section 8 of
[LPTableaus.pdf](/home/jpt4/code/proflog/LPTableaus.pdf) on page 12 says the
factored presentation "does not work as expected" because `move` can admit
"non-standard moves" in weak Herbrand models. The current regression therefore
matches the paper's semantic warning rather than contradicting it.

Operationally, the warning is not that `move/2` is broken. It is that the
factored `win/1` proof has to make subsidiary calls through `move/2` with
non-ground branch parameters, and those calls do not enjoy the same direct
equality closure that the inline formulation gets. So the greenfield public API
surfaces exactly the semantic warning Fitting points to: auxiliary factoring
changes executability even when the ground relation itself looks fine.

### `win(1)` succeeds

Representative proof shape:

```clojure
(neg-call
 (once-univ
  (split
   (conj (neq-close (eq-bind)))
   (pos-call ...))))
```

The winning witness is `y = 0`, and the remaining obligation is to show that
`win(0)` fails.

## Bounded Query Helpers

The bounded helper tests deliberately separate operational time control from
semantic truth.

Current examples:

```clojure
(query-succeeds-within P2 win(0) 25ms) => ()
(query-fails-within    P2 win(1) 25ms) => ()
```

Both calls return the empty result when the time budget expires before a proof
arrives.

Easy non-recursive proofs still surface within budget:

```clojure
(query-succeeds-within status-program p(0) 500ms) => non-empty
(query-fails-within    status-program p(1) 500ms) => non-empty
```

## Source To Kernel Descent

This file is the broadest source-program walkthrough and follows
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

The status program is the minimal source:

```prolog
p(x) :- x = zero.
```

Prefix frontend:

```clojure
(def status-language
  (pf/language
    (constants zero one)
    (relations (p 1) (undef 1))))

(def status-program
  (pf/proflog status-language
    (|- (p x)
        (= x zero))))
```

The query `(pf/q (p zero))` descends to:

```clojure
(pos (app p (app zero)))
```

`query/query-status` interleaves:

```clojure
(kernel/prove-program status-program
                      (neg (app p (app zero)))
                      1
                      fuel)

(kernel/prove-program status-program
                      (pos (app p (app zero)))
                      1
                      fuel)
```

Fitting P1 and P2 add recursive calls, quantifiers, and negative calls, but they
use the same kernel parameters: compiled `prog`, query `fml`, requested proof
count `n`, and fuel or timeout slice. Undefined relations such as `undef/1`
are declared in the language but have no compiled clause entry, which is why
the query layer can report `:unresolved` instead of manufacturing a success or
failure proof.
