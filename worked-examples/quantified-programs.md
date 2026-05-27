# Quantified Programs

This file covers `test/proflog/quantified_programs_test.clj`.

## Deeper `P1` Quantified Checks

The current greenfield quantified suite explicitly exercises the original
`forall`-based odd clause on deeper ground inputs:

```clojure
even(2) => succeeds
odd(0)  => fails
```

The `odd(0)` failure proof is:

```clojure
(pos-call
 (univ
  (split
   (neg-call (conj (neq-close (eq-bind))))
   (refl-close))))
```

Operationally:

1. the positive call opens the universal body,
2. the kernel instantiates it,
3. both branches of the resulting disjunction close.

## `zero-only`

The singleton program is:

```clojure
zero-only(x) :- forall y. (x != y or y = zero)
```

### `zero-only(0)` succeeds

Proof term:

```clojure
(neg-call
 (witness
  (conj
   (eq-step (par-bind) (refl-close)))))
```

The negated universal body chooses the contradictory witness `y = 0`, which
forces a reflexive equality.

### `zero-only(1)` fails

Proof term:

```clojure
(pos-call
 (univ
  (split
   (neq-close (eq-bind))
   (free-close))))
```

Choosing `y = 1` defeats the universal condition:

- the disequality branch closes because `x = y`,
- the equality-to-zero branch closes because `1 != 0`.

## `boxed-zero`

The mixed existential/universal example is documented in detail in
[boxed-zero.md](./boxed-zero.md).

The current open query result is:

```clojure
boxed-zero(x) => x = 0
```

with no residual obligations and explicit proof terms in the exported answer
record.

### Operational Note

The greenfield kernel keeps `boxed-zero` executable by representing the
negation of an existential clause body as an internal single-use universal
`once-forall` form. The justification is operational and local to the
greenfield prover: this branch obligation should instantiate once on the
current branch, not re-enqueue as an ordinary `forall` and spin away the
query.

## Current Boundary

This namespace proves that quantified clause bodies now execute directly in the
greenfield kernel, and it now includes both finite-domain specification and
graph-property families in addition to the earlier singleton and mixed
quantifier examples.

## `subset`

The greenfield subset family uses zero-arity relations over the finite domain
`{a, b, c}`:

```clojure
sub-ab-abc() :- forall x. ((x != a and x != b) or (x = a or x = b or x = c))
sub-abc-ab() :- forall x. ((x != a and x != b and x != c) or (x = a or x = b))
sub-a-a()    :- forall x. (x != a or x = a)
```

Current committed cases:

```clojure
sub-ab-abc() => succeeds
sub-abc-ab() => fails
sub-a-a()    => succeeds
```

This family matters because it exercises quantified finite-domain reasoning
without any recursive list structure.

## `acyclic`

The greenfield graph-property family uses zero-arity relations:

```clojure
acyclic-abc()  :- forall x. not reach_abc(x, x)
acyclic-aba()  :- forall x. not reach_aba(x, x)
acyclic-abca() :- forall x. not reach_abca(x, x)
```

with each `not reach...` body inlined directly as equalities and disequalities.

Current committed cases:

```clojure
acyclic-abc()  => succeeds
acyclic-aba()  => fails
acyclic-abca() => fails
```

This family matters because it extends the quantified specification surface
from set membership to inline graph properties without introducing auxiliary
reachability relations.

## `sorted2`

The greenfield `sorted2` program is:

```clojure
sorted2(l) :- forall a. forall b. forall t.
                (l != cons(a, cons(b, t)) or le_inline(a, b))
```

where `le_inline` is expressed directly with equalities over the finite domain
`{0, 1, 2}`.

Current committed cases:

```clojure
sorted2([])        => succeeds
sorted2([1])       => succeeds
sorted2([0, 1, 2]) => succeeds
sorted2([2, 1])    => fails
sorted2([1, 2])    => succeeds
```

The important implementation point is that the singleton case only closes once
equality decomposition is allowed to carry an earlier parameter binding forward
to a later constructor clash. That was the defect fixed by the equality
regression added alongside this family.

## Source To Kernel Descent

These examples use the source-to-kernel path described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). The central
`zero-only/1` case can be read at all layers:

```prolog
only-zero(x) := forall y. (x != y or y = zero).
zero-only(x) :- only-zero(x).
```

```clojure
(def peano-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (zero-only 1))))

(def zero-only-program
  (pf/proflog peano-language
    (:= (only-zero x)
      (forall [y]
        (or (!= x y)
            (= y zero))))

    (|- (zero-only x)
        (only-zero x))))
```

After frontend inlining, the compiled `zero-only/1` entry has one relation
parameter and one local quantified nom:

```clojure
{:relation zero-only
 :params [x]
 :body
 (forall
   (tie y
     (or
       (neq (var x) (var y))
       (eq (var y) (app zero)))))
 :negated-body
 (exists
   (tie y
     (and
       (eq (var x) (var y))
       (neq (var y) (app zero)))))}
```

The success query descends as:

```clojure
(pf/q (zero-only zero))
=> (pos (app zero-only (app zero)))

(query/query-succeeds zero-only-program query 1 fuel)
=> (kernel/prove-program zero-only-program
                         (neg (app zero-only (app zero)))
                         1
                         fuel)
```

The failure query for `zero-only(s(zero))` uses the positive query formula
instead, because `query/query-fails` asks the kernel to close the query itself.

The finite-domain families follow the same shape. `subset`, `acyclic`, and
`sorted2` are written as nullary or unary relations with quantified bodies.
Inline helpers such as `le_inline` should be treated like `only-zero`: they are
source definitions that must be expanded before the Procedure Call Rule sees
the compiled program. Their kernel parameters are the quantified noms in the
body, the relation arguments in `:params`, `n` proof records, and the admitted
`fuel` or timeout slice.
