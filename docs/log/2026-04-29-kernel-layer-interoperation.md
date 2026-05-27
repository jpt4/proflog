# 2026-04-29 Kernel Layer Interoperation

## Context

ADR-0023 through ADR-0025 added optimized kernel layers for pure
propositional and equality-free first-order theorem branches. Those layers
deliver practical Pelletier performance at the top-level `kernel/prove` entry,
but the general program prover still runs procedure-call subsidiary tableaux
through the full Proflog kernel.

The immediate concern is architectural: if a generated Proflog program reduces
some subgoal to a valid Pelletier-shaped formula, the language should be able to
use the available proof machinery. Optimized layers that only help top-level
theorem calls have limited value for the general language implementation.

The current characterization test is:
[pelletier_layering_test.clj](../../test/proflog/pelletier_layering_test.clj).
It builds a valid Proflog program with two nullary Pelletier subproblem
relations and one aggregate relation. The subproblem theorem branches close
through first-order theorem dispatch individually, while the aggregate program
query stays on the full program kernel at the measured bounded slice.

## Design Direction

Kernel layers should become branch-closing subrelations, not only host-side
entry wrappers.

The full program kernel already reaches points where a branch has been reduced
to a smaller formula plus explicit branch state. At those points, it should
have an internal dispatcher roughly shaped as:

```clojure
(profiled-closeo fml unexpanded lits env proof-vars
                 sigma sigma-out neqs neqs-out
                 prog gamma-terms fuel proof)
```

The dispatcher would try conservative delegation in this order:

1. If the residual branch is purely propositional, close it through
   `proflog.kernel.propositional/proveo`.
2. If the residual branch is equality-free first-order and program-call-free,
   close it through `proflog.kernel.first-order/proveo`.
3. Otherwise, continue with the full `proflog.kernel/prove-stateo`.

This dispatcher belongs inside recursive branch search, especially where
positive and negative procedure calls open subsidiary tableaux for a clause
body or its NNF negation. That is the point where a Proflog program may expose a
Pelletier-like subgoal.

## Handoff Guards

Delegation must be conservative and mode-preserving:

- If formula structure is partial or has logic variables in structural
  positions, the guard should fail and leave the full relational kernel in
  control.
- Propositional delegation requires a residual branch over propositional
  connectives and nullary atoms.
- First-order delegation permits quantifiers and ordinary atoms, but rejects
  equality, disequality, and atoms that still need procedure-call semantics.
- Equality state must be empty, or already applied and stable in a way the
  delegated layer can soundly ignore.
- Disequality state must be empty, or similarly discharged before handoff.
- Saved literals must be compatible with the target layer. If they still
  require equality walking, procedure-call closure, or delayed disequality
  interaction, stay in the full kernel.

The first implementation should prefer false negatives over false positives.
It is acceptable to miss an optimization opportunity; it is not acceptable to
delegate away state that can still affect soundness or answer production.

## Host Wrappers Are Not the Internal API

The program kernel should not call host wrappers such as
`proflog.kernel.first-order/prove` internally. That wrapper performs
forward-only theorem preparation, including host-side Skolemization for complex
closed theorem calls.

Internal interoperation should use relational layer entry points such as
`first-order/proveo` and `propositional/proveo`. If Skolemization or another
preparation step is later needed inside program search, it should be introduced
as a proof-producing, mode-safe relation or as an explicitly guarded
forward-only optimization with documented loss of reverse/partial use.

## Proof Shape

Delegated proofs should remain visible in proof terms, for example:

```clojure
(profiled :propositional subproof)
(profiled :first-order subproof)
```

The delegated layer should not mutate full-kernel equality or disequality
state. A successful handoff returns the prior `sigma` and `neqs` unchanged, or
only after a pre-handoff normalization step has made that equivalence explicit.

## Expected Benefit

The aggregate program from the characterization test should be able to remain a
normal Proflog program:

1. the full program kernel expands the aggregate procedure call;
2. the full kernel expands each subproblem procedure call;
3. once a subsidiary branch is merely a Pelletier-style theorem branch, the
   optimized first-order layer closes it;
4. the aggregate branch then closes through ordinary program-kernel structure.

That keeps the optimized layers useful to the language as a whole while
preserving the full kernel as the semantic authority for procedure calls,
equality, disequality, reverse mode, and partial synthesis.
