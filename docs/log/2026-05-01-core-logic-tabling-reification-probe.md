# 2026-05-01 Core.logic Tabling/Reification Probe

## Context

ADR-0032 lists `core.logic` tabling internals as a possible host-performance
surface: `AnswerCache`, `reuse`, `subunify`, tabled reification, and suspended
streams. Before patching that surface, this probe checks whether Proflog's
carried raw matrix rows actually execute those paths.

## Probe

Added `proflog.core-logic-tabling-probe`, a diagnostic wrapper around the
source-overlay host. It counts calls through:

- `answer-cache`, `master`, `make-suspended-stream`, and `waiting-stream-check`;
- `reuse`, `subunify`, `reify-tabled`, and `-reify-tabled`;
- `AnswerCache` protocol calls where var-level wrapping observes them; and
- `tabled-s`, separately, as the baseline tabled-capable substitution setup.

The probe reports `active-tabled-answer-path?` only when it sees activity beyond
the ordinary `tabled-s` substitution allocation.

The smoke run validates the counters against a tiny repeated `tabled` goal:

```text
lein with-profile +core-logic-source-overlay probe-core-logic-tabling

{:id :core-logic-tabled-smoke,
 :answers (:done),
 :tabling-events
 {:suspended-stream-created 3,
  :answer-cache-created 1,
  :tabled-substitution-created 1,
  :reuse 1,
  :subunify 1,
  :reify-tabled 2,
  :reify-tabled-inner 6,
  :master 1,
  :waiting-stream-check 3,
  ...},
 :active-tabled-answer-path? true}
```

## Host Verification

The patched-source lane was active:

```text
lein with-profile +core-logic-source-overlay probe-core-logic-host

core.logic source: file:/home/jpt4/code/proflog-worktrees/adr32-tabling-reification/vendor/core.logic-1.1.1/src/clojure/core/logic.clj
core.logic source-kind: local-source
core.logic version: 1.1.1
core.logic marker: vendor/core.logic-1.1.1/src
```

The host verification test and tabling regression suite both passed:

```text
lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
  Ran 1 tests containing 5 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-source-overlay test proflog.tabling-test
  Ran 5 tests containing 11 assertions.
  0 failures, 0 errors.
```

## Carried Row Results

Command:

```text
timeout 240s lein with-profile +core-logic-source-overlay probe-core-logic-tabling reverse-input-flat reverse-output-nested-longer reverse-partial-output-tail
```

| Row | Elapsed | Target Found | Closed Count | Raw Count | Active Tabled Answer Path |
| --- | ---: | --- | ---: | ---: | --- |
| `reverse-input-flat` | 35744 ms | false | 0 | 4 | false |
| `reverse-output-nested-longer` | 7707 ms | false | 0 | 4 | false |
| `reverse-partial-output-tail` | 17837 ms | false | 0 | 4 | false |

All three rows reported the same tabling counters:

```clojure
{:suspended-stream-created 0,
 :answer-cache-add 0,
 :answer-cache-created 0,
 :tabled-substitution-created 1,
 :reuse 0,
 :suspended-ready? 0,
 :subunify 0,
 :reify-tabled 0,
 :answer-cache-cached? 0,
 :reify-tabled-inner 0,
 :master 0,
 :waiting-stream-check 0}
```

## Decision

Do not patch `core.logic` tabling/reification for ADR-0032's carried rows.

The carried rows create core.logic's tabled-capable substitution state, but they
do not create answer caches, suspended streams, reuse cached answers, subunify
answers, or perform tabled reification. A generic patch in `AnswerCache`,
`reuse`, `subunify`, `reify-tabled`, or suspended-stream scheduling would not be
exercised by these rows and therefore would not address the observed ADR-0032
failure mode.

This does not reject `core.logic` tabling improvements in general. It only says
that they are not a justified production patch for the current carried Proflog
raw matrix failures.
