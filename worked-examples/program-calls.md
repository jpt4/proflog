# Program Calls

This file covers the expanded regressions in `test/proflog/program_test.clj`.

## Multi-Argument Calls Respect The Clause Environment

Program:

```clojure
pair-eq(x, y) :- x = y
```

Two representative queries now have explicit closure proofs:

```clojure
pair-eq(zero, one)      => (pos-call (free-close))
not pair-eq(zero, zero) => (neg-call (refl-close))
```

The positive call closes because the clause body reduces to `zero = one`,
which fails by constructor clash. The negative call closes because the clause
body reduces to `zero = zero`, which succeeds reflexively.

## Subsidiary Tableaux Stay Isolated

Program:

```clojure
p(x) :- q(x)
```

Caller query:

```clojure
p(zero) and not q(zero)
```

Current result:

```clojure
()
```

There is no proof. The caller branch already contains `not q(zero)`, but that
literal does not get borrowed into the subsidiary tableau opened for the call
to `p(zero)`. This is the intended semantics: procedure calls are checked
against the compiled clause body, not against arbitrary caller-branch context.

## Boundary

The namespace also keeps the complementary negative examples:

- a plain positive call does not close when its clause body remains satisfiable,
- a plain negative call does not close when the clause body remains open,
- and compiled clause lookup still fails cleanly when no matching relation
  exists.

## Source To Kernel Descent

This example follows the common descent in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

At the hand-written source layer, the first relation is:

```prolog
pair-eq(x, y) :- x = y.
```

The prefix frontend form is:

```clojure
(def pair-language
  (pf/language
    (constants zero one)
    (relations (pair-eq 2))))

(def pair-program
  (pf/proflog pair-language
    (|- (pair-eq x y)
        (= x y))))
```

That compiles to one relation entry whose parameters are the two formal
object-language arguments:

```clojure
{:relation pair-eq
 :params [x y]
 :body (eq (var x) (var y))
 :negated-body (neq (var x) (var y))}
```

The query `(pf/q (pair-eq zero one))` becomes:

```clojure
(pos (app pair-eq (app zero) (app one)))
```

`query/query-fails` passes that positive formula to
`kernel/prove-program`, and the Procedure Call Rule opens the compiled body
under `x = zero` and `y = one`. The proof closes because `zero = one` is a
constructor clash. The negative-call example uses the same compiled relation,
but starts from the negated formula and closes when the body is reflexive.

The isolation example is likewise source-level Proflog:

```prolog
p(x) :- q(x).
```

The important kernel parameter is `prog`: the subsidiary tableau for `p(zero)`
receives only the compiled `p/1` body and the program's clause table. It does
not inherit caller-branch literals such as `not q(zero)` as if they were
additional facts.
