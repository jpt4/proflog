# ADR-0023: Profiled Kernel Layers

- Status: completed
- Date: 2026-04-28
- Branch: `adr-0023-profiled-kernel-layers`
- AAR: [AAR-0023](../aar/AAR-0023-profiled-kernel-layers.md)
- Depends On: [ADR-0022](ADR-0022-pelletier-problems.md)

## Context

Pelletier Problem 12 exposed a kernel/search boundary that should not exist for
an extension of alphaleanTAP. The problem is purely propositional:

```clojure
(<=> (<=> (<=> p q) r) (<=> p (<=> q r)))
```

The ported greenfield formula is correct. A simple stack tableau over the exact
greenfield NNF closes immediately, and the local alphaleanTAP-E reference
closes the converted same NNF. The current greenfield kernel does not find a
proof within the review window because it routes every formula through the full
first-order/equality/procedure-call proof state:

- fair agenda selection over pending formulas,
- environment substitution,
- explicit equality substitution `sigma`,
- delayed disequality state `neqs`,
- equality-aware complementary literal closure,
- saved-call rechecks and procedure-call eligibility.

Quantifier clauses already fail structurally for propositional formulas, but the
search relation is still operationally broader than necessary. A formula with
no equality terms also does not need equality-state maintenance, and a formula
with no procedure atoms does not need procedure-call machinery.

The kernel must remain relationally pure and didactically transparent: it should
continue to read as an immediate translation of Proflog's tableau rules from
paper to code. Any optimization layer must preserve that readability instead of
turning the kernel into theorem-specific dispatch or opaque host-side
precomputation.

Generic improvements under consideration:

- Add formula profiling before proof search:
  - pure propositional NNF: only `and`, `or`, `pos`, `neg`, with nullary atoms.
  - equality-free first-order NNF: quantifiers and atoms are allowed, but no
    `eq` or `neq`.
  - equality-bearing NNF: any `eq` or `neq`.
  - program-bearing proof search: procedure-call semantics are in scope because
    a compiled program is present.
- Add a pure propositional tableau component:
  - `and` pushes the right conjunct and proves the left conjunct.
  - `or` proves both branches.
  - `pos` / `neg` close by exact complementary literal membership.
  - no environment, equality substitution, disequality store, program, gamma
    candidate list, or equality unifier is in the state.
- Add an equality-free first-order component:
  - keep alpha, beta, gamma, delta, and ordinary atom closure.
  - skip equality contradiction, disequality storage, equality-triggered saved
    atom rechecks, and equality-triggered procedure-call rechecks.
- Add a direct syntactic closure fast path in broader components:
  - exact complementary literals close before invoking equality-aware unification.
  - this is sound for all formulas and useful even when equality is present.
- Prefer deterministic agenda discipline for closed theorem proving when the
  active profile does not require fair formula selection.

## Decision

ADR-0023 will introduce layered kernel structure, not theorem-specific handling.
The first target is to make pure propositional formulas, including Pelletier
Problem 12, close through a generic propositional tableau path.

The architecture should be staged.

1. Keep the full Proflog kernel visible as the reference implementation of the
   complete rule set. It may remain in `proflog.kernel` during the first pass,
   or later be moved behind a thin public facade, but its rule clauses should
   stay readable as Proflog rules.
2. Add a formula-profile namespace that classifies already-normalized formulas
   structurally. The classifier is not a theorem prover and must not inspect a
   theorem identity; it only reports syntactic capabilities needed by the proof
   relation.
3. Add a pure propositional component that is relational and paper-shaped. It
   should be small enough to audit as alpha, beta, and literal closure.
4. Add a dispatch namespace or facade that chooses the weakest sufficient
   component for the public theorem-proving entry point.
5. Add an equality-free first-order component only after the propositional layer
   is in place and measured.

Two dispatch architectures are acceptable candidates:

### Entry-Only Dispatch

The dispatcher classifies the whole input once and calls one non-interacting
component.

Benefits:

- Clearest component boundaries.
- Easiest to explain and test.
- Lowest risk of cross-layer logic leakage.
- Keeps each component as a standalone Proflog fragment.

Costs:

- Alpha/beta/literal machinery is duplicated across components.
- A formula that starts in a broader profile cannot later drop into a narrower
  component after quantifier instantiation or branch decomposition.
- The full kernel may still pay more than necessary inside mixed formulas.

### Recurrent Dispatch

The dispatcher is re-entered throughout proof search. A component evaluates the
current formula as far as its specialty permits, then returns remaining branch
work to the dispatcher. The flow is:

```text
prover entry point
  -> dispatch layer
  -> specialized component
  -> dispatch layer
  -> specialized component
  -> ...
```

Benefits:

- Avoids duplicating pure propositional mechanics inside every broader component.
- Allows formula fragments to move down to the weakest sufficient layer as proof
  search reveals them.
- Provides a path to optimize mixed first-order/equality inputs without a
  monolithic kernel.

Costs:

- State threading is harder to audit.
- Component boundaries can become porous if components call each other directly.
- Proof-term construction can become less obviously tied to one Proflog rule at
  a time.

If recurrent dispatch is used, it must follow these constraints:

- Components do not call one another directly.
- All cross-component transitions go through the dispatcher.
- Components operate on one canonical proof-state shape for their supported
  state fields, or on an explicitly projected narrower state.
- A component may only consume rules it owns; it must hand unsupported formulas
  back to dispatch rather than peeking into another component's logic.
- Proof terms remain rule-local: `conj`, `split`, `univ`, `witness`, `close`,
  equality proof tags, and procedure-call proof tags must still identify the
  Proflog rule that fired.

ADR-0023 should begin with entry-only dispatch. Recurrent dispatch should be
introduced only if measurements show that entry-only dispatch leaves important
mixed-profile formulas needlessly in the full kernel.

Other possible designs are rejected for the first implementation:

- A single monolithic kernel with profile-guarded clauses keeps all logic in one
  file, but it obscures which Proflog fragment is active.
- A strategy parameter that changes agenda selection and literal closure inside
  the existing kernel is a smaller edit, but it leaves the full proof state in
  every path and is less didactically clear.
- Compiling formulas into an imperative proof plan may be fast, but it drifts
  too far from the paper-to-code translation goal.

## Consequences

The layered design preserves the ordinary full kernel while allowing simpler
fragments to run without equality or program overhead. It also creates a place
to document and test semantic boundaries: propositional, equality-free
first-order, equality-bearing, and program-bearing search.

The main risk is duplicated logic. The first implementation should accept small
duplication in the propositional component because it buys clarity. Refactoring
shared alpha/beta helpers is allowed only if the helper remains rule-shaped and
does not hide proof-state transitions.

The second risk is losing relational purity by making host-side classification
part of the semantic kernel. To avoid that:

- The raw relational component relations remain callable directly.
- Public convenience functions may use host-side profile classification for
  ground NNF formulas.
- Any relational entry point that must support partially instantiated formulas
  should dispatch with structural relations or fall back to the full kernel.
- No `project` boundary should be introduced into the proof rules for this ADR.

## Implementation Plan

1. Add `proflog.formula-profile` or `proflog.kernel.profile`.
   - Provide pure structural predicates for propositional, equality-free, and
     equality-bearing formulas.
   - Track whether atoms are nullary for the pure propositional profile.
2. Add tests for profile classification.
   - Problem 12 must classify as pure propositional.
   - Existing quantified Pelletier problems must not classify as propositional.
   - Equality/disequality formulas must classify as equality-bearing.
3. Add `proflog.kernel.propositional`.
   - Implement alpha, beta, and exact complementary literal closure.
   - Keep the relation small and didactic.
   - Return proof terms compatible with the existing proof vocabulary where
     possible.
4. Route `kernel/prove` for ground pure-propositional formulas through the new
   component.
   - Preserve the existing full relation for callers that need the complete
     first-order/equality kernel.
   - Do not add theorem-specific checks.
5. Promote Pelletier Problem 12 to `ported-passing` after it closes through the
   generic propositional path.
6. Add a direct exact-complement closure fast path to the full kernel if it does
   not obscure the existing literal-closure rule.
7. Measure the remaining Pelletier non-passers.
   - If equality-free first-order formulas remain slow for equality overhead
     reasons, implement the equality-free first-order component.
   - If mixed formulas still pay avoidable broad-kernel costs, revisit recurrent
     dispatch under the constraints above.

## Test Obligations

- Profile classification unit tests for pure propositional, equality-free
  first-order, and equality-bearing formulas.
- Propositional kernel regressions:
  - Pelletier Problem 12 closes.
  - Existing propositional Pelletier passers still close.
  - A non-theorem propositional branch does not close.
- Public dispatch tests:
  - Pure propositional formulas use the propositional path.
  - Equality-bearing formulas still use the full kernel.
  - Program-bearing proof search still uses the program-aware full kernel.
- Regression suites:
  - `lein test-proflog-fast`
  - `lein test-proflog-pelletier-prompt`
  - `lein test-proflog-pelletier`
  - targeted equality and program-call tests if the full kernel literal rule is
    touched.

## Implementation Outcome

ADR-0023 implemented the first staged layer only:

- Added `proflog.formula-profile` for structural classification of normalized
  formulas as pure propositional, equality-free first-order, equality-bearing,
  or unsupported.
- Added `proflog.kernel.propositional`, a small relational tableau over current
  formula, pending branch work, saved literals, optional fuel, and proof terms.
- Routed `kernel/prove` through the propositional component when the input
  formula is pure propositional.
- Left `kernel/proveo`, `kernel/prove-programo`, and `kernel/prove-program` on
  the full kernel so relational callers and program-bearing searches retain the
  complete Proflog rule set.
- Covered the propositional component's direct relation with partial-proof and
  constrained reverse-formula synthesis tests.
- Promoted Pelletier Problem 12 to `ported-passing` through the generic
  formula-profiled path.

Recurrent dispatch remains deferred. The entry-only dispatch layer was enough
to close the motivating propositional failure without mixing component states.
The equality-free first-order component also remains deferred until the
remaining Pelletier non-passers are measured under the new baseline.

The full-kernel literal rule was not changed in this ADR. A separate exact
complement fast path in the broad kernel would need a no-new-bindings
relational equality check to avoid weakening existing relational behavior; the
propositional component already supplies exact closure for the target profile.

The host-side formula profiler is only part of the forward convenience wrapper
`kernel/prove`. Reverse and partial relational use must call a relation
directly: `proflog.kernel.propositional/proveo` for the propositional layer, or
the existing full-kernel `kernel/proveo` / `kernel/prove-programo` relations
when the formula or program is partially instantiated.

## Exit Criteria

- Pelletier Problem 12 is `ported-passing` through a generic formula-profiled
  path.
- No theorem-specific overlay or problem-id dispatch exists.
- The full Proflog kernel remains readable as the complete tableau rule set.
- The propositional component is relational, small, and test-covered.
- The public proof API documents when it uses profiled dispatch and when it
  falls back to the full kernel.
- The ADR/AAR trail records whether recurrent dispatch is still deferred or has
  become necessary.
