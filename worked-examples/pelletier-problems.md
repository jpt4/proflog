# Pelletier Problems

ADR-0022 ports the upstream `namin/leanTAP` Pelletier benchmark through the
greenfield kernel as pure theorem proving. The test helper builds this branch:

```clojure
(normalize/to-nnf
  (conjoin (concat axioms [(ast/not-form theorem)])))
```

With no axioms, this is just the NNF negation of the theorem. With axioms, it
is the NNF of `axiom-1 and ... and axiom-n and not(theorem)`.

## Propositional Example: Problem 1

Upstream:

```clojure
(<=> (=> p q) (=> (not q) (not p)))
```

Greenfield builder:

```clojure
(iff (implies p q)
     (implies (not* q) (not* p)))
```

The helper expands `iff` as both implications, negates the theorem, and gives
the kernel a closed branch equivalent to:

```clojure
(or (and (or (neg p) (pos q))
         (and (neg q) (pos p)))
    (and (or (pos q) (neg p))
         (and (pos p) (neg q))))
```

This matches the shape already mirrored by the legacy
`test/cljtap/alphaleantap_ep_test.clj` slice, but runs through
`proflog.kernel/prove` rather than the legacy `cljtap` prover.

## Quantified Example: Problem 18

Upstream:

```clojure
(E y (A x (=> (f y) (f x))))
```

Greenfield builder:

```clojure
(ast/nom y x
  (exists y
          (forall x
                  (implies (pred 'f (v y))
                           (pred 'f (v x))))))
```

The proof branch is the normalized negation of that theorem. The greenfield
kernel closes it using its ordinary quantifier, branch-literal, and equality
state machinery. No Proflog program clauses are present.

## Current Status

ADR-0025 closes the full upstream Pelletier catalog through generic profiled
kernel layers.

- `ported-passing`: Problems 1-46.
- `ported-too-slow`: none.
- `requires-kernel-work`: none.

Pure propositional formulas use the propositional component. Equality-free
first-order theorem formulas use the lean first-order component introduced by
ADR-0025. Program-bearing and equality-bearing proof search still use the full
kernel.

Dedicated selectors keep these tiers separate:

```bash
lein test-proflog-pelletier-prompt
lein test-proflog-pelletier
lein test-proflog-pelletier-exploratory
```

## Direct Formula Descent

Pelletier problems use the direct theorem-formula layer from
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). They are not
Proflog procedure programs.

The source proposition or first-order theorem is translated directly to
`proflog.ast` formula constructors. A typical quantified theorem:

```clojure
(E y (A x (=> (f y) (f x))))
```

becomes an `exists` formula containing a `forall` formula and is evaluated as:

```clojure
(kernel/prove normalized-negated-theorem 1 fuel)
```

There is no `prog` parameter because there are no compiled clauses. Formula
profile selection may route pure propositional problems to the propositional
component and equality-free first-order problems to the first-order component,
but the proof obligation is still the emitted formula. This is why Pelletier
coverage demonstrates theorem-proving strength adjacent to Proflog, rather than
procedure-call evaluation itself.
