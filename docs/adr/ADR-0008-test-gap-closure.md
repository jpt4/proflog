# ADR-0008: Greenfield Test Gap Closure

- Status: completed
- Date: 2026-04-21
- Branch: `adr-0008-test-gap-closure`
- AAR: [AAR-0008](../aar/AAR-0008-test-gap-closure.md)

## Context

The greenfield `src/proflog` implementation now has a coherent baseline test
stack, but its committed regression coverage is still materially smaller and
less adversarial than the legacy `test/cljtap` suites.

That is not automatically a defect: the greenfield effort is not a line-by-line
port of the legacy system. However, the current gap is large enough that the
repository needs an explicit closure plan rather than informal comparison.

The strongest shared-semantic gaps are:

- thinner base-kernel, equality, proof, and procedure-call regressions,
- much lighter end-to-end coverage for quantified bodies and list programs,
- limited adversarial/pathology coverage for ordering and search behavior,
- a narrower set of reverse/partial answer tests than the legacy suite,
- no explicit greenfield determination yet about full reverse program
  synthesis feasibility,
- no explicit placement yet for legacy `GV` and `FD` style families beyond
  "not baseline today".

## Decision

- Record a tracked greenfield test-gap checklist in
  `docs/TEST_GAP_CLOSURE_CHECKLIST.md`.
- Use that checklist as the working reference for branch-local implementation
  and for future merge review on this topic.
- Prioritize mission-relevant semantic parity over raw legacy test-count parity.
- Treat full reverse program synthesis as a feasibility question that must be
  answered explicitly on the greenfield stack, not assumed from legacy behavior.
- Keep legacy `GV` and `FD` families on the list as future greenfield
  experiments rather than baseline obligations.
- Add new greenfield tests by semantic area and public surface, not by copying
  legacy file layout wholesale.

## Consequences

- The branch gets an explicit scope boundary: it is about closing meaningful
  greenfield coverage gaps, not reproducing every legacy experiment.
- The project gains a documented place to record infeasible or variant-only
  surfaces, which is especially important for reverse program synthesis claims.
- Some checklist items may resolve as "documented not currently feasible" rather
  than immediate implementation wins; that is acceptable if the result is
  precise and operationally honest.
- Future AAR work will need to distinguish:
  - gaps actually closed,
  - gaps deferred deliberately,
  - experiments evaluated but not promoted to baseline coverage.

## Current Findings

- Existing greenfield work in `test/proflog/synthesis_modes_test.clj` should be
  incorporated into this ADR rather than treated as unrelated branch state.
  Those tests materially expand non-trivial reverse/partial coverage by adding
  structured recursive `plus` and `append` answer-family checks.
- A bounded reverse-program-synthesis claim is already justified on this branch:
  the kernel can synthesize an internal compiled clause body under a fixed
  clause shape, and that boundary is covered by
  `test/proflog/reverse_program_synthesis_test.clj`.
- Full surface-program synthesis is not yet established. The current greenfield
  stack compiles programs in a forward-only way, and the internal compiled
  representation does not relationally guarantee `body`/`negated-body`
  coherence.

## Test Obligations

- `test/proflog/kernel_test.clj`
- `test/proflog/equality_test.clj`
- `test/proflog/program_test.clj`
- `test/proflog/query_test.clj`
- `test/proflog/query_extended_test.clj`
- `test/proflog/answers_test.clj`
- `test/proflog/synthesis_modes_test.clj`
- any new greenfield namespace required for reverse program synthesis
  feasibility checks

## Exit Criteria

- The checklist document exists and is kept current while this ADR is active.
- Mission-critical greenfield parity gaps have committed tests or an explicit,
  documented defer/reject decision.
- Full reverse program synthesis has a concrete greenfield assessment:
  feasible, infeasible, or feasible only under a named narrower boundary.
- `GV` and `FD` are explicitly recorded as future greenfield experiments rather
  than silently dropped from consideration.
