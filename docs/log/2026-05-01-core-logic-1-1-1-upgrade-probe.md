# 2026-05-01 Core.logic 1.1.1 Upgrade Probe

## Context

ADR-0032's first deployment lane was a published `core.logic` upgrade before
local host patching. This tests whether upstream work after Proflog's pinned
1.0.1 dependency already improves the carried ADR-0031 list-family failures.

## Deployment Verification

Default runtime:

```text
lein probe-core-logic-host

core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.0.1/core.logic-1.0.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic group-id: org.clojure
core.logic artifact-id: core.logic
core.logic version: 1.0.1
```

Upgrade runtime:

```text
lein with-profile +core-logic-1.1.1 probe-core-logic-host

core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.1.1/core.logic-1.1.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic group-id: org.clojure
core.logic artifact-id: core.logic
core.logic version: 1.1.1
```

The profile is therefore selecting the intended host artifact.

## Compatibility

Default:

```text
lein test-proflog-core-logic-host
  Ran 1 tests containing 5 assertions.
  0 failures, 0 errors.

lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

lein test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

timeout 300s lein test proflog.list-kernel-matrix-test
  Ran 2 tests containing 19 assertions.
  0 failures, 0 errors.
```

Upgrade profile:

```text
lein with-profile +core-logic-1.1.1 test-proflog-core-logic-host
  Ran 1 tests containing 5 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-1.1.1 test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-1.1.1 test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

timeout 300s lein with-profile +core-logic-1.1.1 test proflog.list-kernel-matrix-test
  Ran 2 tests containing 19 assertions.
  0 failures, 0 errors.
```

The upgrade is compatible with the focused suites exercised here.

## Carried Matrix Rows

| Row | core.logic | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | --- | --- | --- | --- |
| `reverse-input-flat` | 1.0.1 | false | 0 | 4 | 19463 ms |
| `reverse-input-flat` | 1.1.1 | false | 0 | 4 | 17411 ms |
| `reverse-output-nested-longer` | 1.0.1 | false | 0 | 4 | 6559 ms |
| `reverse-output-nested-longer` | 1.1.1 | false | 0 | 4 | 5061 ms |
| `reverse-partial-output-tail` | 1.0.1 | false | 0 | 4 | 9995 ms |
| `reverse-partial-output-tail` | 1.1.1 | false | 0 | 4 | 9557 ms |

Commands:

```text
timeout 180s lein probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-input-flat

timeout 180s lein probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-output-nested-longer

timeout 180s lein probe-proflog-list-kernel-matrix reverse-partial-output-tail
timeout 180s lein with-profile +core-logic-1.1.1 probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

The upgrade profile is modestly faster in this run, but it does not change the
answer shape: all three carried rows still have `target-found? false`,
`closed-count 0`, and `raw-count 4`.

## Carried Synthesis Namespace

Default:

```text
timeout 180s lein test proflog.synthesis-modes-test
  Ran 9 tests containing 17 assertions.
  2 failures, 0 errors.
```

Upgrade profile:

```text
timeout 180s lein with-profile +core-logic-1.1.1 test proflog.synthesis-modes-test
  Ran 9 tests containing 17 assertions.
  2 failures, 0 errors.
```

The failing tests are unchanged:

- `recursive-reverse-mode-query-synthesizes-descendants`
- `composed-partial-mode-query-traverses-multiple-calls`

## Decision

Keep the published-upgrade profile because it is useful for comparison and
appears compatible, but do not treat the upgrade as an ADR-0032 solution.

The next experiment needs either:

- a verified local source overlay for host patching; or
- a profiler/instrumentation pass that identifies whether unification,
  reification, stream scheduling, constraints, nominal handling, or tabling
  dominates the carried rows.

