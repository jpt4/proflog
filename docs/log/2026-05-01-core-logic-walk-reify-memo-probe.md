# 2026-05-01 Core.logic Walk/Reify Memo Probe

## Context

This ADR-0032 worker tested whether generic, local caching around the vendored
`core.logic` 1.1.1 walk/reification path could improve the carried Proflog
reverse rows without list-family-specific shortcuts.

The inspected hot path was:

- substitution walking: `walk`, `ext`, and `occurs-check`;
- recursive walking: `walk*`, `walk-term`, and the `IWalkTerm`
  implementations for `LVar`, `LCons`, `ISeq`, vectors, maps, records, and
  objects;
- reification: `-reify*`, `-reify`, `reify-term`, `reifyg`, and nominal
  `reify-term` / constraint reification callers.

No `ISeq` or `LCons` structural-sharing patch was retried.

## Baseline

Clean source overlay on branch `adr32/core-logic-walk-reify-memo`, starting from
`fcf2d5e`:

| Probe | Result |
| --- | --- |
| `reverse-input-flat` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 15386.588258` |
| `reverse-output-nested-longer` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 4380.514316` |
| `reverse-partial-output-tail` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 8080.791819` |

Baseline count probe for `reverse-input-flat`:

- elapsed: `18422.899` ms;
- dominant category: `:walk-reification`;
- `clojure.core.logic/walk*`: `5302076` counted calls;
- `clojure.core.logic/occurs-check`: `1662873` counted calls;
- `clojure.core.logic/unify`: `397257` counted calls.

## Variants Tested

### `walk*-identity-memo`

Changed `walk*` to allocate one bounded `java.util.IdentityHashMap` per public
`walk*` operation, cache walked tree results by identity, and recurse through a
local helper. The cache lifetime was tied to one stable substitution walk.

Results:

| Probe | Result |
| --- | --- |
| `reverse-input-flat` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 21181.451895` |
| `reverse-output-nested-longer` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 5892.62022` |
| `reverse-partial-output-tail` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 10341.327791` |

Count probe for `reverse-input-flat`:

- elapsed: `23450.807` ms;
- dominant category shifted to `:unification`;
- `clojure.core.logic/walk*`: `198` counted calls;
- `clojure.core.logic/occurs-check`: `1662873` counted calls;
- `clojure.core.logic/unify`: `397257` counted calls.

The public `walk*` Var count collapsed because recursive calls moved into the
local helper, but total runtime regressed on every carried row. The traversal
work still happened; the lower Var count was not a useful performance proxy.

### `walk*-local-recursion`

Removed the identity cache and kept only local recursion inside `walk*`, leaving
the original `walk-term` protocol behavior intact.

Results:

| Probe | Result |
| --- | --- |
| `reverse-input-flat` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 17908.719755` |

Count probe for `reverse-input-flat`:

- elapsed: `22667.787` ms;
- dominant category shifted to `:unification`;
- `clojure.core.logic/walk*`: `198` counted calls;
- `clojure.core.logic/occurs-check`: `1662873` counted calls;
- `clojure.core.logic/unify`: `397257` counted calls.

This isolated the cache overhead from the recursion rewrite. It still did not
beat the clean overlay and did not change answer shape.

### `bindable-walk-cache`

Kept `walk*` recursion shape and added a dynamic, bounded identity cache for
`walk` results on bindable terms only while a `walk*` operation was active. This
was intended to avoid caching across substitution changes.

Result:

| Probe | Result |
| --- | --- |
| `reverse-input-flat` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 24933.731694` |

This was slower than both the clean overlay and the previous local-recursion
variant, so it was not expanded to the other carried rows.

## Decision

Reject these walk/reification memoization variants and keep no vendored source
change.

The bounded caches were local to stable substitution operations and did not
produce semantic failures in the focused probes that were run, but they did not
close any ADR-0032 target row and consistently made the measured carried rows
slower. The count probe also showed that reducing public `walk*` Var calls can
be misleading when the recursive traversal merely moves into a local helper.

Future walk/reification work should start with lower-level allocation or CPU
profiling of `walk-term` traversal itself, not another small cache around
`walk*`.

## Final Clean Overlay Verification

After reverting the source experiments, the branch keeps only this note. The
source overlay marker remains `vendor/core.logic-1.1.1/src`.

Focused tests on the final tree:

| Command | Result |
| --- | --- |
| `test-proflog-core-logic-host` | `Ran 1 tests containing 5 assertions. 0 failures, 0 errors.` |
| `test-proflog-constructor-recursive` | `Ran 6 tests containing 21 assertions. 0 failures, 0 errors.` |
| `test-proflog-fast` | `Ran 117 tests containing 380 assertions. 0 failures, 0 errors.` |

Final carried-row confirmation on the final tree:

| Probe | Result |
| --- | --- |
| `reverse-input-flat` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 19155.874095` |
| `reverse-output-nested-longer` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 5188.517553` |
| `reverse-partial-output-tail` | `target-found? false`, `found-target-count 0`, `raw-count 4`, `elapsed-ms 10107.541247` |

Final clean count probe for `reverse-input-flat`:

- elapsed: `21420.474` ms;
- dominant category: `:walk-reification`;
- `clojure.core.logic/walk*`: `5302076` counted calls;
- `clojure.core.logic/occurs-check`: `1662873` counted calls;
- `clojure.core.logic/unify`: `397257` counted calls.

## Commands

```text
timeout -k 10s 60s lein with-profile +core-logic-source-overlay probe-core-logic-host
timeout -k 10s 120s lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
timeout -k 10s 180s lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
timeout -k 10s 240s lein with-profile +core-logic-source-overlay test-proflog-fast

timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail

timeout -k 10s 300s lein with-profile +core-logic-source-overlay probe-core-logic-count reverse-input-flat 20
```
