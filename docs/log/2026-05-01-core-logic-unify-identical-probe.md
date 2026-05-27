# 2026-05-01 Core.logic Unify Identical-After-Walk Probe

## Context

After the source-overlay lane was verified, the first local host patch tested a
small generic unifier fast path in the vendored `core.logic` source:

```clojure
(let [u (walk s u)
      v (walk s v)]
  (if (identical? u v)
    s
    ...))
```

The intent was to avoid protocol dispatch when walking both sides of a unify
operation resolves them to the exact same object. This is generic across
`core.logic` programs and does not encode Proflog or list-family knowledge.

## Verification

The patched source overlay reported the marker:

```text
core.logic marker: vendor/core.logic-1.1.1/src unify-identical-after-walk
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
| `reverse-input-flat` | 17411 ms | 17730 ms | false | 0 |
| `reverse-output-nested-longer` | 5061 ms | 4771 ms | false | 0 |
| `reverse-partial-output-tail` | 9557 ms | 10067 ms | false | 0 |

The patch produced one small timing improvement and two slowdowns in this
sample. More importantly, it did not change the answer shape: all carried rows
still missed their targets.

## Decision

Reject and revert the patch.

The source overlay remains available for future host experiments, but the
`unify` identical-after-walk fast path is not retained because it does not show
broad improvement and does not advance the ADR-0032 exit criteria.

