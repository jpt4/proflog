# AAR-0028: Kernel Support Disequality Purity

- Date: 2026-04-29
- Related ADR: [ADR-0028](../adr/ADR-0028-kernel-support-disequality-purity.md)
- Outcome: completed

## What Happened

ADR-0028 removed the projected saved-disequality maintenance boundary from
`proflog.kernel-support`.

Before this ADR, `prune-contradictory-neqso` and `stable-neqso` both used
`core.logic/project` over `neqs` and `sigma`. That was acceptable for ordinary
forward proof search where both branch-state values were already known, but it
was not relational. Direct `prove-stateo` callers could make the helpers throw
by leaving `neqs` or `sigma` open, and partial term refinements could leave
stale contradictory disequalities in the branch state.

The fix replaced those helpers with structural relations:

- `different-termo`
- `different-term*o`
- structural `prune-contradictory-neqso`
- structural `stable-neqso`

## Results

New kernel regressions cover the exact failing modes identified in the log:

- reverse `neqs` synthesis through the equality continuation;
- partial `sigma` synthesis through post-closure pruning;
- pruning after a later term refinement that makes a saved pair reflexive;
- stable-disequality rejection after a later term refinement that makes a saved
  pair reflexive.

The only remaining `project` in `src/proflog/kernel_support.clj` is
`step-fuelo`, the accepted fuel boundary.

## Verification

- `rg -n "project" src/proflog/kernel_support.clj src/proflog/kernel.clj src/proflog/subst.clj`
  - no projected formula, substitution, or saved-disequality maintenance remains;
    the only executable `project` match is `step-fuelo`.
- `lein test proflog.kernel-test`
  - `Ran 17 tests containing 26 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `Ran 26 tests containing 43 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 110 tests containing 358 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.answers-test proflog.query-extended-test`
  - `Ran 20 tests containing 68 assertions.`
  - `0 failures, 0 errors.`
