# 2026-05-01 Core.logic Stream/Walk Negative Probe

## Context

This ADR-0032 follow-up looked only at generic `core.logic` stream and walking
allocation surfaces in the vendored 1.1.1 source overlay. It did not retry the
rejected identical-after-walk unifier fast path or the rejected `ISeq`
structural-sharing walk patch.

Two separate source-overlay variants were tested and then reverted:

- `choice-take-tail`: changed `Choice.take*` from
  `(lazy-seq (cons a (lazy-seq (take* f))))` to
  `(lazy-seq (cons a (take* f)))`, targeting one lazy wrapper per stream cell.
- `lcons-walk-share`: returned the original `LCons` from `walk-term` when both
  walked head and tail were identical, targeting logic-list walking allocation
  without changing the already rejected `ISeq` path.

## Verification

The final branch keeps the stock source-overlay marker:

```text
core.logic marker: vendor/core.logic-1.1.1/src
```

Final stock-overlay compatibility checks passed:

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

Both source-overlay variants also passed `test-proflog-core-logic-host` and
`test-proflog-constructor-recursive`. `choice-take-tail` also passed
`test-proflog-fast`.

## Carried Row Results

Final stock overlay, after the variants were reverted:

| Row | Final Stock Overlay | Target Found | Closed Count | Raw Count |
| --- | ---: | --- | ---: | ---: |
| `reverse-input-flat` | 17387 ms | false | 0 | 4 |
| `reverse-output-nested-longer` | 4676 ms | false | 0 | 4 |
| `reverse-partial-output-tail` | 9565 ms | false | 0 | 4 |

`choice-take-tail` overlay:

| Row | Patched Overlay | Target Found | Closed Count | Raw Count |
| --- | ---: | --- | ---: | ---: |
| `reverse-input-flat` | 34581 ms | false | 0 | 4 |
| `reverse-output-nested-longer` | 11279 ms | false | 0 | 4 |
| `reverse-partial-output-tail` | 10622 ms | false | 0 | 4 |

`lcons-walk-share` overlay:

| Row | Patched Overlay | Target Found | Closed Count | Raw Count |
| --- | ---: | --- | ---: | ---: |
| `reverse-input-flat` | 21029 ms | false | 0 | 4 |
| `reverse-output-nested-longer` | 5363 ms | false | 0 | 4 |
| `reverse-partial-output-tail` | 10342 ms | false | 0 | 4 |

Fresh published 1.1.1 jar comparison:

| Row | 1.1.1 Jar | Target Found | Closed Count | Raw Count |
| --- | ---: | --- | ---: | ---: |
| `reverse-input-flat` | 19072 ms | false | 0 | 4 |
| `reverse-output-nested-longer` | 5508 ms | false | 0 | 4 |
| `reverse-partial-output-tail` | 9000 ms | false | 0 | 4 |

## Decision

Reject both stream/walk variants and keep no vendored source change on this
branch. Neither variant changed the answer shape, and each was slower than the
final stock overlay on all three carried rows in this run.

The current evidence does not justify a small generic patch in
`Choice.take*`, `mplus`/`bind`, `conde`/`fresh` expansion, `walk*`, or
`LCons` walking. Future work should start with lower-level allocation profiling
or a broader search-scheduling design, not another local micro-patch in these
paths.

## Commands

```text
lein with-profile +core-logic-source-overlay probe-core-logic-host
lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
lein with-profile +core-logic-source-overlay test-proflog-fast

timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail

timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-partial-output-tail
```
