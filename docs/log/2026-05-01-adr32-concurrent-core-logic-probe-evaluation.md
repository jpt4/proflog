# 2026-05-01 ADR-32 Concurrent Core.logic Probe Evaluation

## Context

After the first ADR-32 host experiments rejected small `core.logic`
micro-patches, two remaining frontiers were split into concurrent worktree
experiments:

- `adr32/core-logic-vector-unification`
- `adr32/core-logic-walk-reify-memo`

Both workers branched from `fcf2d5e`, the logged ADR-32 remaining-frontiers
baseline.

## Worker Outcomes

### Vector-Specialized Unification

Branch: `adr32/core-logic-vector-unification`

Worker commit: `3c595e72b4b4bc4faabecb15777a28562bd0fa8d`

The branch added a generic vector-vs-vector path inside the vendored
`unify-with-sequential*`, plus probes proving that the path was exercised by
both a host vector shell and the carried Proflog rows. It preserved ordinary
`llist` open-tail behavior.

The carried rows still did not close:

| Case | Vector Calls | Target Found | Closed Count | Raw Count |
| --- | ---: | --- | ---: | ---: |
| `reverse-input-flat` | 29,076 | false | 0 | 4 |
| `reverse-output-nested-longer` | 18,823 | false | 0 | 4 |
| `reverse-partial-output-tail` | 35,063 | false | 0 | 4 |

Decision: reject as an implementation merge candidate. The patch is generic
and exercised, but it does not change answer shape or show broad performance
improvement on the ADR-32 blockers.

Detailed note:
[Core.logic Vector Unification Probe](2026-05-01-core-logic-vector-unification-probe.md).

### Walk/Reify Memoization

Branch: `adr32/core-logic-walk-reify-memo`

Worker commit: `9ec77af96e353275ff474059fd54892abe4f46eb`

The branch tested three local walk/reification variants:

- `walk*-identity-memo`;
- `walk*-local-recursion`; and
- `bindable-walk-cache`.

The most dramatic counter result reduced public `walk*` Var calls from
`5,302,076` to `198`, but this was misleading because traversal moved into a
local helper and runtime regressed. No target row closed, and no vendored
source change survived in the final worker commit.

Decision: reject as an implementation merge candidate. The useful result is the
negative evidence that small local caches around `walk*` are not a mergeable
ADR-32 optimization frontier.

Detailed note:
[Core.logic Walk/Reify Memo Probe](2026-05-01-core-logic-walk-reify-memo-probe.md).

## Merge Decision

No implementation branch was merged.

The vector branch demonstrated real path exercise but no target closure or broad
performance improvement. The walk/reify branch demonstrated negative local-cache
results and kept no source patch. The main ADR-32 branch therefore retains only
the experiment records.

## Main-Branch Retest

Retest branch: `adr-0032-core-logic-performance`

Retest baseline: unchanged source-overlay implementation with marker
`vendor/core.logic-1.1.1/src`.

Commands:

```text
timeout -k 10s 60s lein with-profile +core-logic-source-overlay probe-core-logic-host
timeout -k 10s 120s lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
timeout -k 10s 180s lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
timeout -k 10s 300s lein with-profile +core-logic-source-overlay test-proflog-fast
timeout -k 10s 300s lein with-profile +core-logic-source-overlay test proflog.list-kernel-matrix-test
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail
timeout -k 10s 240s lein with-profile +core-logic-source-overlay test proflog.synthesis-modes-test
```

Passing checks:

| Check | Result |
| --- | --- |
| Host probe | local source overlay loaded; marker `vendor/core.logic-1.1.1/src` |
| `test-proflog-core-logic-host` | 1 test, 5 assertions, 0 failures |
| `test-proflog-constructor-recursive` | 6 tests, 21 assertions, 0 failures |
| `test-proflog-fast` | 117 tests, 380 assertions, 0 failures |
| `proflog.list-kernel-matrix-test` | 2 tests, 19 assertions, 0 failures |

Carried raw probes remain failing:

| Case | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | ---: | ---: | ---: |
| `reverse-input-flat` | false | 0 | 4 | 17,141.799909 ms |
| `reverse-output-nested-longer` | false | 0 | 4 | 4,772.890307 ms |
| `reverse-partial-output-tail` | false | 0 | 4 | 9,293.501653 ms |

`proflog.synthesis-modes-test` still reports 2 failures in 9 tests / 17
assertions:

- `recursive-reverse-mode-query-synthesizes-descendants`
- `composed-partial-mode-query-traverses-multiple-calls`

## Decision

ADR-32 remains open. The concurrent experiments provide useful negative
evidence but do not satisfy ADR-32's carried exit criteria.
