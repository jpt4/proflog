# Test Gap Closure Checklist

Date: 2026-04-21
Target branch: `adr-0008-test-gap-closure`
Related ADR: `docs/adr/ADR-0008-test-gap-closure.md`

## Purpose

Record the greenfield `src/proflog` test-coverage gaps relative to the legacy
`test/cljtap` suites, so the project has an explicit, tracked checklist for
closing mission-relevant gaps instead of relying on ad hoc comparison.

This checklist is not a promise to clone the legacy suite wholesale. It is a
prioritized closure plan for the greenfield implementation, plus a short list
of explicitly experimental tracks that should be evaluated rather than assumed.

ADR-0043 status note: this file remains a historical checklist from ADR-0008.
For current family-by-family parity, use
[LEGACY_PROGRAM_PARITY_MATRIX.md](LEGACY_PROGRAM_PARITY_MATRIX.md). The bullets
below are retained as closure history, with selected current-status notes added
where later ADRs superseded the original gaps.

## P0: Mission-Critical Parity Gaps

- [ ] Expand base tableau and NNF regression depth in `test/proflog/kernel_test.clj`,
  `test/proflog/normalize_test.clj`, and `test/proflog/proof_test.clj`.
  Cover more contradiction and non-closure cases, nested connective shapes,
  and quantifier interaction paths already stressed in legacy Sections A/C/D/O.
- [ ] Expand equality and disequality regressions in
  `test/proflog/equality_test.clj`.
  Add order-invariance, transitivity chains, same-head decomposition edge
  cases, occurs-check pathologies, and broader eq/neq interaction coverage.
- [ ] Expand procedure-call rule regressions in
  `test/proflog/program_test.clj` and `test/proflog/query_test.clj`.
  Cover branch-context isolation, multi-argument calls, equality inside clause
  bodies, equality-enabled calls, and no-spurious-call negatives.
- [ ] Add committed quantified clause-body integration tests for the greenfield
  prover.
  Include the original Fitting `P1` shape with the `forall`-based `odd` clause,
  plus additional mixed `forall`/`exists` end-to-end examples.
  Current incorporated coverage: `test/proflog/quantified_programs_test.clj`
  now covers:
  - prompt singleton `forall` execution via `zero-only(0)` / `zero-only(1)`
  - original `P1` quantified execution on deeper ground cases including
    `even(2)` and `odd(0)`
  - mixed `exists`/`forall` execution via `boxed-zero(0)` / `boxed-zero(1)`
  Operational note: these deeper quantified families are currently committed in
  the extended suite rather than the fast baseline. The greenfield kernel now
  supports them through an independently justified single-use universal NNF form
  for negated existential clause bodies.
- [ ] Add committed list-program coverage for `member`, `append`, and `reverse`.
  Include forward success/failure, inverse use, wrong-result failure, and
  recursive answer-export behavior.
  Current incorporated coverage: `test/proflog/synthesis_modes_test.clj`
  already adds non-trivial symbolic answer-export checks for recursive
  `append` behavior and should be treated as the first committed step in this
  area rather than as unrelated branch work.
  Additional current coverage: `test/proflog/list_programs_test.clj` now covers
  `member`, direct `append`/`reverse` proofs and refutations, concrete forward
  answer export, nested forward append, and nested suffix append. Current
  `test/proflog/answers_test.clj` also covers public reverse output
  `reverse([a,b], r)` and all four inverse `append(x,y,[a,b,c])` splits. The
  long-timeout list-kernel matrix eventually reaches every catalog target, with
  remaining gaps classified as operational cost rather than absent semantics.
- [ ] Deepen proof-object regressions in `test/proflog/proof_test.clj`.
  Cover call-related proof structure, nested recursive call proofs, saved-formula
  behavior, and richer equality-proof tags.
- [ ] Expand reverse and partial query-answer coverage in
  `test/proflog/answers_test.clj` and `test/proflog/synthesis_modes_test.clj`.
  Cover more multi-step symbolic families, inverse structural queries, and
  recursive residual obligations.
  Current incorporated coverage: `test/proflog/synthesis_modes_test.clj`
  already covers structured recursive `plus` and `append` answer families,
  including direct witnesses plus residual recursive obligations for open and
  partial queries.
- [ ] Add adversarial and pathology regressions for semantic drift.
  Prioritize ordering sensitivity, equality/call unlock order, rewrite-trigger
  edge cases, and bounded-search pathologies that legacy coverage currently
  catches first.
- [ ] Strengthen query-boundary operational regressions in
  `test/proflog/query_extended_test.clj`.
  Cover more unresolved patterns and recursive cases without turning timeout
  helpers into semantic oracles.
- [ ] Add broader flagship integration families that compose recursion,
  equality, quantification, and answer behavior.
  Prioritize transitive closure, Peano arithmetic, and additional quantified
  specification programs.
  Current incorporated coverage: `test/proflog/integration_families_test.clj`
  covers direct and recursive `tc` success, no-path `tc` failures, Peano
  zero-left base cases, non-base ground successes, and wrong-sum refutations.
  ADR-40/41 add focused Peano reverse and partial-synthesis parity rows through
  the constructor-recursive profile.

## P1: Feasibility Determinations

- [x] Determine whether full reverse program synthesis is feasible with the
  current greenfield implementation.
  This means probing whether `src/proflog` can support synthesis where the
  program itself is left relationally open, rather than only reverse or partial
  query answering against a compiled program.
- [x] If full reverse program synthesis is not currently feasible, document the
  exact boundary.
  Record whether the blocker is language compilation, program representation,
  kernel call discipline, answer export, or operational search behavior.
- [x] If a bounded or variant-based approximation is feasible, document that
  narrower claim explicitly instead of overstating full reverse capability.
  Current finding on `adr-0008-test-gap-closure`: internal compiled-program
  synthesis is feasible under a fixed clause shape, and that boundary is now
  covered in `test/proflog/reverse_program_synthesis_test.clj`. Fully open
  surface-program synthesis remains unresolved because `language/compile-program`
  is one-way and the internal compiled representation does not relationally
  enforce `body`/`negated-body` coherence.

## P2: Lower-Priority Greenfield Parity Targets

- [ ] Add deeper inverse list-structure coverage, including multi-split
  `append` queries and nested-list examples, if the core list-program work
  proves stable.
  Current status: public inverse `append(x,y,[a,b,c])`, nested forward append,
  nested suffix append, and long-timeout nested inverse matrix coverage now
  exist. Remaining work is runtime/default-suite placement for the expensive
  stress rows.
- [ ] Add more end-to-end quantified specification families such as sortedness
  and subset once the core quantified-body regressions are in place.
  Current status: `test/proflog/quantified_programs_test.clj` now covers
  sortedness, subset, and acyclic graph specifications.
- [ ] Add broader proof-debugging regressions only after the semantic surface is
  stable enough that proof shape is not expected to churn.

## Future Experiments

- [x] Evaluate whether the legacy `GV` family should become a greenfield
  experiment track.
  Treat it as a future semantic stress test for quantified specifications, not
  as baseline parity work.
  Current status: ADR-39/40 promoted proof-backed GV rows, including full
  associativity success/failure through the equality-fragment profile.
- [x] Evaluate whether the legacy `FD` family should become a greenfield
  experiment track.
  Treat it as a future exploration of finite-domain style reasoning rather than
  a default mission requirement.
  Current status: ADR-38/40/42 promoted finite-domain rows and corrected the
  known `warm-cool-disjoint` status inconsistency.

## Notes

- The checklist is scoped to the greenfield implementation in `src/proflog` and
  `test/proflog`.
- Legacy `test/cljtap` remains comparison material, not an implementation
  authority.
- Work under this checklist should prefer mission-relevant semantic closure over
  one-to-one test count parity.
