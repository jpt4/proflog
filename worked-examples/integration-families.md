# Integration Families

This file covers `test/proflog/integration_families_test.clj`.

## Transitive Closure

The current committed integration graph is:

```clojure
tc(x, y) :- (x = a and y = b)
         or (x = b and y = c)
         or exists z.
              ((x = a and z = b) or (x = b and z = c))
              and tc(z, y)
```

This stays inline intentionally. For this small graph, negative reachability is
part of the semantic contract, and the inline edge facts keep the impossible
edge cases available to the same tableau instead of hiding them behind another
procedure call.

### `tc(a, b)` succeeds

Proof term:

```clojure
(neg-call
 (conj
  (neg-call (conj (split (refl-close) (refl-close))))))
```

The direct `edge(a, b)` branch closes immediately.

### `tc(b, c)` succeeds

Proof term:

```clojure
(neg-call
 (conj
  (neg-call
   (conj
    (split
     (neq-store ...)
     (neq-store ...))))))
```

The proof is slightly larger because it first eliminates the `a -> b` base
branch before closing the `b -> c` branch.

### `tc(a, c)` succeeds

This is the first genuinely recursive example in the namespace.

Operationally:

1. both direct-edge branches are ruled out,
2. the recursive clause chooses the witness `z = b`,
3. the remaining obligation is `tc(b, c)`,
4. that subgoal closes through the direct `b -> c` branch.

### No-path examples now refute directly

Current negative examples:

```clojure
tc(c, a) => false
tc(a, a) => false
tc(b, a) => false
```

The shortest is `tc(c, a)`. Its proof term is:

```clojure
(pos-call
 (split
  (conj (free-close))
  (split
   (conj (free-close))
   (witness (conj (split (conj (free-close)) (conj (free-close))))))))
```

Each branch closes because `c` cannot match either graph edge source, and the
recursive clause can only reintroduce those same impossible edge shapes.

## Peano Addition

The current arithmetic relation is:

```clojure
plus(x, y, z) :- x = zero and z = y
              or exists x1 z1.
                   x = s(x1)
                   and z = s(z1)
                   and plus(x1, y, z1)
```

The committed baseline currently checks the zero-left base case.

### `plus(0, 2, 2)` succeeds

Proof term:

```clojure
(neg-call (conj (split (refl-close) (refl-close))))
```

The base branch closes because both equalities become reflexive.

### Non-base ground truths

The current extended slice also proves:

```clojure
plus(1, 0, 1)
plus(1, 1, 2)
plus(2, 1, 3)
plus(2, 3, 5)
```

The first two recursive truths already have the same outer proof shape as the
base case, but the proof objects are larger because they must peel one or more
successor layers before returning to the base branch.

The two-step example `plus(2, 1, 3)` is representative:

```clojure
(neg-call
 (conj
  (split
   (neq-store ...)
   (neq-store ...))))
```

Operationally the proof:

1. rejects the base branch because `2 != 0`,
2. opens the recursive clause,
3. strips one successor layer from the first and third arguments,
4. recurses until the base clause closes.

### Wrong sums are refuted directly

The current failure slice proves:

```clojure
plus(1, 1, 1) => false
plus(0, 1, 0) => false
plus(1, 2, 2) => false
```

The shortest wrong-sum example is:

```clojure
plus(0, 1, 0)
```

with proof term:

```clojure
(pos-call
 (split
  (conj (eq-step (decompose ()) (free-close)))
  (witness (witness (conj (free-close))))))
```

The positive call closes both branches:

1. the base branch reduces to `1 = 0`, which closes,
2. the recursive branch reduces to `0 = s(_)`, which also closes.

## Current Boundary

The `tc/2` family now covers the same small-graph truth/falsity pattern as the
legacy benchmark, while the `plus/3` family should still be read together with
the open and partial `plus` examples in `worked-examples/synthesis-modes.md`.

Operational note:

- these deeper `tc/2` negatives and multi-step `plus/3` truths are no longer
  smoke tests; they are extended-suite semantic regressions and should be
  treated as such when budgeting run time.

Those are phase-2 closure items on `ADR-0009`.

## Source To Kernel Descent

These families are ordinary source-level Proflog programs. The shared descent
is documented in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

The `tc/2` source stays intentionally inline:

```prolog
tc(x, y) :- (x = a and y = b)
         or (x = b and y = c)
         or exists z. (((x = a and z = b)
                        or (x = b and z = c))
                       and tc(z, y)).
```

In prefix frontend form, the recursive branch remains visible:

```clojure
(|- (tc x y)
  (or (and (= x a) (= y b))
      (and (= x b) (= y c))
      (exists [z]
        (and (or (and (= x a) (= z b))
                 (and (= x b) (= z c)))
             (tc z y)))))
```

The backend compiles one `tc/2` body with relation parameters `[x y]` and a
local existential nom `z`. A query such as `tc(a, c)` descends to:

```clojure
(pos (app tc (app a) (app c)))
```

`query/query-succeeds` asks `kernel/prove-program` to close the negated query;
the recursive call in the body is handled by the same Procedure Call Rule as
the top-level call.

The `plus/3` examples use the same multiple-clause-to-disjunction descent shown
in [Synthesis Modes](./synthesis-modes.md). Here the mode is forward
truth/falsity, so the exported-answer parameters are absent. The relevant
kernel parameters are only `prog`, the ground query formula, `n`, and `fuel`.
