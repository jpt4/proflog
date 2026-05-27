# ADR-0028: Kernel Support Disequality Purity

- Status: completed
- Date: 2026-04-29
- Branch: `adr-0028-kernel-support-disequality-purity`
- AAR: [AAR-0028](../aar/AAR-0028-kernel-support-disequality-purity.md)
- Depends On: [ADR-0027](ADR-0027-transitive-relational-purity.md)

## Context

ADR-0027 removed projected formula substitution from the kernel-facing path,
but the ordinary kernel and answer overlay still call two projected helpers in
`proflog.kernel-support`:

- `prune-contradictory-neqso`
- `stable-neqso`

Those helpers are not fuel policy. They maintain saved disequalities after
equality and literal-closure steps. Because they projected `neqs` and `sigma`,
they made the kernel relation forward-only across a branch-state boundary even
after `kernel.clj` and `subst.clj` had been cleaned of `project`.

The triggering analysis is recorded without summary in
[Kernel Support Disequality Purity Gap](../log/2026-04-29-kernel-support-disequality-purity-gap.md).

## Decision

Replace the projected saved-disequality maintenance helpers with structural
relations.

The implementation introduces:

- `different-termo`, a relational test that two terms are not already the same
  after walking through `sigma`;
- `different-term*o`, the list-valued companion for constructor arguments;
- structural `prune-contradictory-neqso`, which recursively removes pairs that
  satisfy `equality/same-termo` and keeps pairs that satisfy `different-termo`;
- structural `stable-neqso`, which succeeds only when every saved pair satisfies
  `different-termo`.

This deliberately tests only "already same under the current branch state", not
general unifiability. A saved disequality such as `x != a` remains stable until
a later equality actually binds `x` to `a`.

The projected `step-fuelo` boundary remains accepted for now. It is bounded
operational bookkeeping over a known fuel value, not a proof-state inspection
over formula, substitution, or disequality data.

## Consequences

The kernel support layer now preserves transitive relational purity for saved
disequality maintenance. Direct callers of `prove-stateo` can leave `neqs`,
`sigma`, or terms inside saved disequality pairs partially open without forcing
host-side projection.

The structural relations also fix a subtler ordering bug: a projected helper
could inspect a term while it was still a core.logic variable, keep the saved
disequality, and then allow a later `==` refinement that made the pair
reflexive. The structural relation instead leaves constraints that reject that
later refinement or prunes the pair through the reflexive branch.

The added relations can generate term shapes in fully open modes. The committed
tests therefore target constrained reverse and partial branch-state modes
rather than unconstrained arbitrary formula generation.

## Test Obligations

- The equality continuation can synthesize an empty stable disequality store
  instead of throwing on open `neqs`.
- Complementary closure plus pruning can accept a `sigma` refined after the
  kernel step instead of throwing on open `sigma`.
- Pruning does not retain a stale saved disequality when a later term
  refinement makes it reflexive.
- `stable-neqso` rejects a continuation when a later term refinement would make
  the saved pair reflexive.
- Existing kernel behavior remains green.

## Exit Criteria

- `prune-contradictory-neqso` no longer uses `project`.
- `stable-neqso` no longer uses `project`.
- `lein test proflog.kernel-test` passes.
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test` passes.
- `lein test-proflog-fast` passes.
