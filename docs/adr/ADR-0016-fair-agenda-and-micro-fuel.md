# ADR-0016: Fair Agenda And Micro Fuel

- Status: completed
- Date: 2026-04-26
- Branch: `adr-0016-fair-scheduling`
- AAR: [AAR-0016](../aar/AAR-0016-fair-agenda-and-micro-fuel.md)

## Context

ADR-0015 separated the ordinary proof kernel from the answer overlay, making it
possible to change proof-search control without re-entangling answer export.
The next pressure point is search fairness.

The list-family regressions and diagnostics show that the current kernel can
starve useful work because branch expansion is effectively left-first:

- recursive list calls may be selected before equalities or complements that
  would make another branch task close,
- repeated gamma / procedure-call descent can dominate a fuel slice before
  simpler pending formulas are selected,
- and the raw answer stream becomes sensitive to one syntactic ordering of
  conjunctions rather than to the logical branch state.

core.logic already gives `conde` an interleaving search discipline, but the
kernel has been hiding too much work inside one fixed focused-formula order.
The branch needs to expose formula selection itself as relational work so
miniKanren can interleave it.

The existing `fuel` value is also too coarse. It mostly charges large
unbounded steps such as quantifier instantiation and procedure calls, while a
single admitted slice can still do substantial internal work. That made the
runtime meaning of "fuel" hard to reason about in list and hard-family probes.

## Decision

- Represent pending branch work as an explicit agenda.
- Select the next formula from that agenda relationally, not by a fixed
  leftmost rule. The intended primitive is a pure `selecto` relation:
  `selected`, `agenda`, and `rest-agenda` are related by removing exactly one
  occurrence from the agenda.
- Keep the public kernel and answer-overlay entry signatures stable while
  routing their internals through the explicit agenda.
- Apply the same agenda discipline to `proflog.answer-overlay`; answer search
  must not regain left-first behavior through a separate implementation.
- Update answer export so public answer ordering is stable under fairer raw
  proof streams. Canonicalization and ranking should absorb proof-order churn
  rather than exposing it as API instability.
- Refine fuel from a count of only large inference steps toward micro-steps.
  The branch should make explicit which operations consume budget and should
  avoid a single fuel unit admitting unbounded branch-local work.
- Preserve kernel purity. The scheduler must be expressed as relations over
  explicit state, not as host-side queues, mutation, committed choice, or
  projected control logic.
- Do not add memoization or tabling in this ADR. Fair scheduling is expected to
  revisit more equivalent states; ADR-0017 handles that follow-on explicitly.

## Consequences

- Search becomes less dependent on the syntactic left side of an `and` chain.
- Some raw proof counts and proof ordering diagnostics will change. Tests that
  asserted exact raw duplicate counts may need to assert the intended
  diagnostic property instead.
- The agenda scheduler may expose duplicate or alpha-equivalent proof
  frontiers earlier than before. That is acceptable only if answer
  canonicalization keeps the public answer surface stable.
- The implementation may access the same proof-search frontier more than once.
  That cost is expected and is the motivation for ADR-0017.
- The kernel remains readable as a direct Fitting-style tableau interpreter if
  the agenda machinery is kept small and the actual tableau rules remain in
  `proflog.kernel`.

## Test Obligations

- Add a failing regression proving that a branch can close by selecting a
  non-leftmost pending formula before a recursively productive leftmost one.
- Add a list-family regression showing that the fair agenda improves the
  documented reverse / append symbolic frontier without relying on a
  family-specific fast path.
- Add or update answer diagnostics tests so they tolerate proof-order changes
  while still proving duplicate raw proofs are merged into stable exported
  answers.
- Add targeted kernel and overlay tests proving both layers use the same agenda
  selection discipline.
- Add micro-fuel tests that demonstrate the new budget accounts for smaller
  proof-search steps and returns control predictably on recursive list-family
  probes.

## Exit Criteria

- `proflog.kernel` and `proflog.answer-overlay` both route branch expansion
  through an explicit agenda relation.
- Existing public proof and answer entry points remain source-compatible.
- The known stale list-family diagnostics are updated to assert semantic
  behavior rather than old left-first raw counts.
- The refined fuel contract is documented and covered by tests.
- `lein test-proflog-fast` passes.
- Relevant extended answer/list regressions pass.
- Any remaining duplicate-frontier cost is documented as ADR-0017 follow-up,
  not hidden inside this ADR.
