# 2026-05-01 Core.logic ISeq Walk Sharing Probe

## Context

The second local host patch targeted `core.logic` term walking. Proflog terms
and formulas are mostly Clojure list structures, and `walk*` delegates list
walking to the `IWalkTerm` implementation for `clojure.lang.ISeq`.

The stock 1.1.1 source always allocates a walked list:

```clojure
(with-meta
  (doall (map #(walk-term (f %) f) v))
  (meta v))
```

The probe changed this path to return the original sequence when every walked
element was identical to the original element. This was intended as a generic
structural-sharing optimization for unchanged list terms.

## Verification

The patched overlay marker was:

```text
core.logic marker: vendor/core.logic-1.1.1/src iseq-walk-share
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

Compared with the published 1.1.1 jar run from the upgrade probe:

| Row | 1.1.1 Jar | Patched Overlay | Target Found | Closed Count |
| --- | --- | --- | --- | --- |
| `reverse-input-flat` | 17411 ms | 19322 ms | false | 0 |
| `reverse-output-nested-longer` | 5061 ms | 5399 ms | false | 0 |
| `reverse-partial-output-tail` | 9557 ms | 9372 ms | false | 0 |

The patch slowed two carried rows and only modestly improved one. It did not
change answer shape or close any carried target.

## Decision

Reject and revert the patch.

The idea may be worth revisiting only with lower-overhead changed-element
tracking or profiler evidence that list walking dominates a broader workload.
The tested implementation adds enough per-list comparison work to erase the
allocation-saving argument on the ADR-0032 probes.

