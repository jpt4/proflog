# 2026-05-01 Core.logic Count Probe

## Context

ADR-0032 needed a bounded attribution probe before trying more host patches.
Two local overlay patches had already been rejected:

- `unify` identical-after-walk: compatible, but no broad timing improvement.
- `ISeq` walk structural sharing: compatible, but slowed most carried rows.

This probe is Proflog-side instrumentation. It wraps selected `core.logic` Vars
with process-local counters via `with-redefs-fn` while running one existing
list-kernel matrix case. It does not alter Proflog proof relations or the
vendored host source.

The first timed variant tried wrapping lower-level protocol Vars including
`bind`, `mplus`, `take*`, raw `walk`, `walk-term`, and `reify-term`. That was
too intrusive for repeatable carried-row use: even a tiny forward reverse case
did not complete promptly. The committed probe therefore counts higher-level
entry points only. Its `:streams` category is a `choice`/`to-stream` surface
proxy rather than a full scheduler profile.

## Commands

```text
lein probe-core-logic-host
lein with-profile +core-logic-1.1.1 probe-core-logic-host
lein test-proflog-core-logic-host
timeout 120s lein test proflog.core-logic-count-probe-test
timeout 180s lein probe-core-logic-count reverse-input-flat 16
timeout 180s lein with-profile +core-logic-1.1.1 probe-core-logic-count reverse-input-flat 16
```

## Host Verification

Default runtime:

```text
core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.0.1/core.logic-1.0.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic group-id: org.clojure
core.logic artifact-id: core.logic
core.logic version: 1.0.1
core.logic marker: <none>
```

Upgrade runtime:

```text
core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.1.1/core.logic-1.1.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic group-id: org.clojure
core.logic artifact-id: core.logic
core.logic version: 1.1.1
core.logic marker: <none>
```

The focused host test also passed:

```text
Ran 1 tests containing 5 assertions.
0 failures, 0 errors.
```

## Carried Row Result

The carried row was `reverse-input-flat`:

```text
reverse(r, [b,a])
fuel 64, raw-limit 4, call-depth 2
```

Both hosts still missed the target:

| Host | Target Found | Closed Count | Raw Count | Instrumented Elapsed |
| --- | --- | ---: | ---: | ---: |
| 1.0.1 | false | 0 | 4 | 46726.673 ms |
| 1.1.1 | false | 0 | 4 | 37396.639 ms |

The counted call distribution was identical across the two hosts:

| Category | Calls | Share |
| --- | ---: | ---: |
| walk/reification | 5,302,932 | 0.675 |
| unification | 2,282,887 | 0.291 |
| nominal | 162,945 | 0.021 |
| constraints | 94,537 | 0.012 |
| streams | 11,181 | 0.001 |
| tabling | 0 | 0.000 |

Top counted functions:

| Function | Category | Calls |
| --- | --- | ---: |
| `clojure.core.logic/walk*` | walk/reification | 5,302,076 |
| `clojure.core.logic/occurs-check` | unification | 1,662,873 |
| `clojure.core.logic/unify` | unification | 397,257 |
| `clojure.core.logic/unify-with-sequential*` | unification | 121,455 |
| `clojure.core.logic/ext` | unification | 101,048 |
| `clojure.core.logic.nominal/swap-noms` | nominal | 93,753 |
| `clojure.core.logic/cgoal` | constraints | 46,992 |
| `clojure.core.logic.nominal/hash` | nominal | 45,885 |

## Interpretation

This is not a flamegraph. It is a low-overhead call-volume probe, so elapsed
time remains a whole-run comparison and category dominance is by counted calls.

The result argues against tabling as the immediate ADR-0032 host target for
this carried row, because no table reuse/subunification hooks fired. It also
does not point first at stream scheduling: the committed stream proxy is small,
and the deeper scheduler protocol wrappers were too intrusive for this probe
shape.

The strongest patch target is repeated walking and occurs-check pressure around
unification/reification. Because the rejected `ISeq` structural-sharing patch
already showed that a naive walk allocation optimization can slow the carried
rows, the next experiment should be narrower than generic list walk sharing.
Promising targets are places that provoke repeated `walk*` or occurs checks,
especially disequality/nominal constraint maintenance and reification around
the answer frontier.
