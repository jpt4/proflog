# 2026-05-01 Core.logic Vector Unification Probe

## Context

ADR-0032 identified a possible generic host optimization: avoid seq allocation
when both walked terms passed to `core.logic` unification are persistent vectors
of equal count. The experiment must not encode Proflog list-family symbols or
convert relational lists to vectors, because ordinary list programs need open
tails and `LCons` semantics.

## Prototype

The source overlay added a narrow branch in `unify-with-sequential*`:

- when both walked terms are vectors, unify elements by index with `nth`;
- preserve the existing sequential path for vector/list, list/vector, `LCons`,
  and all non-vector sequential combinations;
- add a dynamic ADR-32 counter used only by probes/tests to prove the vector path
  is exercised.

The host vector probe also checks an ordinary open-tail `llist` shell. The vector
shell exercised the new path twice, while the `llist` shell exercised it zero
times and still returned the expected tail.

## Verification

```text
timeout 120s lein with-profile +core-logic-source-overlay probe-core-logic-host
timeout 120s lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
timeout 120s lein with-profile +core-logic-source-overlay probe-core-logic-vector
timeout 120s lein test-proflog-core-logic-host
timeout 300s lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
timeout 300s lein with-profile +core-logic-source-overlay test-proflog-fast
timeout 180s lein with-profile +core-logic-source-overlay probe-core-logic-vector reverse-input-flat
timeout 180s lein with-profile +core-logic-source-overlay probe-core-logic-vector reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-source-overlay probe-core-logic-vector reverse-partial-output-tail
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail
timeout 180s lein with-profile +core-logic-source-overlay probe-core-logic-count reverse-input-flat 20
timeout 180s lein with-profile +core-logic-source-overlay test proflog.synthesis-modes-test
```

Focused compatibility checks passed:

| Command | Result |
| --- | --- |
| `test-proflog-core-logic-host` | 3 tests, 9 assertions, 0 failures |
| default `test-proflog-core-logic-host` | 3 tests, 7 assertions, 0 failures |
| `test-proflog-constructor-recursive` | 6 tests, 21 assertions, 0 failures |
| `test-proflog-fast` | 117 tests, 380 assertions, 0 failures |

The vector probe showed that Proflog does feed vectors to host unification in the
carried rows:

| Case | Vector Calls | Target Found | Closed Count | Raw Count | Counter Elapsed |
| --- | ---: | --- | ---: | ---: | ---: |
| `reverse-input-flat` | 29,076 | false | 0 | 4 | 19,141 ms |
| `reverse-output-nested-longer` | 18,823 | false | 0 | 4 | 5,258 ms |
| `reverse-partial-output-tail` | 35,063 | false | 0 | 4 | 9,856 ms |

Plain matrix probes, without the vector counter, still did not close any carried
target:

| Case | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | ---: | ---: | ---: |
| `reverse-input-flat` | false | 0 | 4 | 18,566 ms |
| `reverse-output-nested-longer` | false | 0 | 4 | 6,210 ms |
| `reverse-partial-output-tail` | false | 0 | 4 | 12,477 ms |

The count probe for `reverse-input-flat` reported `unify-with-vector*` at 29,076
calls, below `walk*` at 5,302,076 calls, `occurs-check` at 1,662,873 calls,
generic `unify` at 397,257 calls, and `unify-with-sequential*` at 121,455 calls.
The answer shape remained unchanged: `target-found? false`, `closed-count 0`,
`raw-count 4`.

`proflog.synthesis-modes-test` retained the two carried failures:

- `recursive-reverse-mode-query-synthesizes-descendants` returned `[1 2]` where
  the test expected `[2 1]`.
- `composed-partial-mode-query-traverses-multiple-calls` still produced closed
  extra jump answers, so `neq-residuals-only?` failed.

## Decision

Reject this prototype as an ADR-0032 merge candidate. The path is generic and
can be exercised, including by Proflog matrix rows, but it does not change target
closure or answer shape and does not show broad timing improvement on the
carried rows. The remaining blocker is still proof-search and repeated
walk/reification pressure, not vector seq allocation alone.
