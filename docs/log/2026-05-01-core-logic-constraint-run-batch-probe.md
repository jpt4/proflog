# 2026-05-01 Core.logic Constraint Run Batch Probe

## Context

This probe targeted generic `core.logic` overhead around `==`, changed-variable
tracking, and constraint dispatch. The stock 1.1.1 `run-constraints*`
recursively builds composed goal closures and may drive a constraint fixpoint
for each changed variable produced by one unification.

The tested patch queued constraints for all changed variables first, then ran a
single `fix-constraints` pass when there was no active queue. Existing active
queue behavior was preserved. The patch did not encode any Proflog, list, or
program-symbol knowledge, and it did not weaken constraint checks.

## Verification

The patched source overlay reported the marker:

```text
core.logic marker: vendor/core.logic-1.1.1/src constraint-fastpath-run-constraints-batch
```

Focused compatibility tests passed:

```text
lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
  Ran 1 tests containing 5 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-source-overlay test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.
```

## Carried Row Results

Same-worktree unmodified overlay baseline:

| Row | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | --- | --- | --- |
| `reverse-input-flat` | false | 0 | 4 | 18170 ms |
| `reverse-output-nested-longer` | false | 0 | 4 | 15020 ms |
| `reverse-partial-output-tail` | false | 0 | 4 | 18271 ms |

Patched overlay:

| Row | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | --- | --- | --- |
| `reverse-input-flat` | false | 0 | 4 | 33065 ms |
| `reverse-output-nested-longer` | false | 0 | 4 | 9751 ms |
| `reverse-partial-output-tail` | false | 0 | 4 | 18845 ms |

Commands:

```text
lein with-profile +core-logic-source-overlay probe-core-logic-host
lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
lein with-profile +core-logic-source-overlay test-proflog-fast
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

## Decision

Reject and revert the patch.

The batching idea remained compatible, but it did not improve answer shape or
close any carried row. Timing was mixed, with a material slowdown on
`reverse-input-flat`, a small slowdown on `reverse-partial-output-tail`, and
only one noisy row-level improvement. The result is not broad enough to justify
changing generic constraint scheduling.
