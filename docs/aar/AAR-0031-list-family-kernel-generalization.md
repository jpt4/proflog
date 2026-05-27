# AAR-0031: List-Family Kernel Generalization

- Date: 2026-04-30
- Related ADR: [ADR-0031](../adr/ADR-0031-list-family-kernel-generalization.md)
- Outcome: closed with carry-forward to ADR-0032

## What Happened

ADR-0031 revisited ADR-0030's list-family result after the raw matrix showed
that two-step list proofs did not amount to family-level constructor-recursive
generalization.

The branch made real progress:

- added a family-parametric append/reverse matrix with explicit layer, fuel,
  call-depth, raw-limit, timing, and target accounting;
- compiled guarded-clause IR from source programs;
- exposed guarded alternatives through relational program lookup;
- used guarded IR in the ordinary kernel and raw answer overlay;
- reassessed and documented several candidate search improvements; and
- merged a generic constructor-recursive sidecar layer that can close multiple
  blocked rows through explicit `constructor-recursive-*` proof tags.

The implementation stayed generic. Production code did not add checks for
`append`, `reverse`, `cons`, or `null`.

## Fruitful Result

The fruitful experiment was the constructor-recursive sidecar layer in
`proflog.kernel.constructor-recursive`. It consumes the existing guarded IR,
saturates constructor guards with generic free-constructor unification, recurses
through positive defined calls, and conservatively settles deferred negative
defined-call residuals.

Representative successful opt-in probes after merging:

```text
timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-input-flat constructor-recursive
  target-found? true
  found-target-count 1
  elapsed about 5.4s

timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-output-nested-longer constructor-recursive
  target-found? true
  found-target-count 1
  elapsed about 0.1s

timeout 180s lein run -m proflog.list-kernel-matrix-probe append-inverse-flat constructor-recursive
  target-found? true
  found-target-count 4
  elapsed about 0.1s
```

This is useful evidence that a generic guarded constructor-recursive proof
layer can close multiple append and reverse families without list-specific
production dispatch.

## Experiments Not Retained

Four avenues were rejected or parked:

- adaptive constructor-demand call ordering, because it improved only
  `reverse(r, [b,a])` and did not generalize;
- demand IR scheduling, because generic demand selection regressed answer
  ordering or timed out reverse rows;
- answer continuation, because constructor-visible continuation slowed passing
  rows without closing reverse blockers;
- answer-path tabling, because diagnostics showed duplicate exported records,
  not repeated raw proof families.

Structural descent was parked rather than merged. It proved that a generic
proper-subterm call metric can improve multiple append rows under exhausted
answer call-depth, but it did not improve reverse blockers and its prototype
left `proflog.synthesis-modes-test` failures.

See
[ADR-0031 Parallel Sub-Agent Reports](../log/2026-04-30-adr31-parallel-subagent-reports.md)
for the collated branch reports.

## Remaining Failures

ADR-0031 did not satisfy its full exit criteria. The ordinary raw answer path
still fails representative reverse and partial rows within CI-safe settings:

```text
timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-input-flat
  target-found? false
  closed-count 0
  raw-count 4
  elapsed about 30.5s

timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-output-nested-longer
  target-found? false
  closed-count 0
  raw-count 4
  elapsed about 13.4s

timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-partial-output-tail
  target-found? false
  closed-count 0
  raw-count 4
  elapsed about 21.4s
```

The broader synthesis-mode regression namespace also still fails:

```text
timeout 180s lein test proflog.synthesis-modes-test
  Ran 9 tests containing 17 assertions.
  2 failures, 0 errors.
```

The failing tests are:

- `recursive-reverse-mode-query-synthesizes-descendants`, where answers are
  produced in the wrong order (`[1 2]` instead of `[2 1]`);
- `composed-partial-mode-query-traverses-multiple-calls`, where the current
  records include closed bindings instead of only residual disequality
  frontiers.

These failures are explicitly carried forward to ADR-0032.

## Verification

The merged ADR-0031 branch passed the focused and fast suites:

```text
lein test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

lein test proflog.kernel.dispatch-test
  Ran 6 tests containing 26 assertions.
  0 failures, 0 errors.

lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

timeout 300s lein test proflog.list-kernel-matrix-test
  Ran 2 tests containing 19 assertions.
  0 failures, 0 errors.
```

## Decision

Close ADR-0031 and merge it back to `master` so the repository returns to a
shared baseline. The sidecar layer is worth retaining as a diagnostic and
proof-producing prototype, but it does not finish the ordinary kernel/raw
answer objective.

ADR-0032 will carry the same nominal list-family exit criteria forward and
will pursue the larger `core.logic` host-language performance avenue. That
work requires review of the exact `core.logic` implementation used by Proflog,
a revised dependency/deployment sequence for patched host code, runtime
verification that the patched implementation is actually loaded, and
before/after probes that separate generic host-language effects from
list-family-specific behavior.
