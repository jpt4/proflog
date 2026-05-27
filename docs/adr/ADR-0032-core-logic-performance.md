# ADR-0032: Core.logic Host Performance

- Status: accepted
- Date: 2026-05-01
- Branch: `adr-0032-core-logic-performance`
- AAR: pending
- Depends On: [ADR-0031](ADR-0031-list-family-kernel-generalization.md)

## Context

ADR-0031 made useful progress on the list family. It added a size-aware matrix,
compiled guarded-clause IR, used guarded alternatives in the ordinary kernel and
raw answer overlay, and retained a generic constructor-recursive sidecar layer
that can close several blocked append/reverse rows through explicit proof tags.

ADR-0031 still did not satisfy the family-level exit criteria. The ordinary raw
answer path still fails representative reverse and partial rows within the
current bounds:

- `reverse-input-flat`
- `reverse-output-nested-longer`
- `reverse-partial-output-tail`

`proflog.synthesis-modes-test` also still has two failures:

- `recursive-reverse-mode-query-synthesizes-descendants`
- `composed-partial-mode-query-traverses-multiple-calls`

These failures are carried into ADR-0032 deliberately. The new branch keeps the
same nominal list-family outcome, but the implementation strategy is large
enough to require a new ADR: improve or replace the underlying `core.logic`
host substrate used by Proflog.

The current project dependency is:

```clojure
[org.clojure/core.logic "1.0.1"]
```

The local extracted `clojure/core/logic.clj` tree is not a deployment mechanism.
It is not listed in `:source-paths`, so Proflog continues to load the published
artifact unless the branch deliberately changes the dependency or classpath.

## Decision

Pursue a generic host-language performance branch for `core.logic`.

The branch may test:

- a published `core.logic` upgrade;
- a local source overlay of a patched `core.logic`;
- a locally installed patched Maven artifact; or
- narrow host-side changes that are tableau-prover aware only when they remain
  generic across Proflog programs.

No accepted change may encode knowledge of `append`, `reverse`, `cons`, `null`,
or any other list-family-specific program symbol.

The first deliverable is a research and deployment design that identifies the
loaded host implementation, the relevant `core.logic` internals, the runtime
verification command, and the before/after probes. Only then should the branch
credit performance or search improvements to host changes.

## Required Capabilities

### 1. Runtime Host Verification

The branch must provide a command or test that reports the loaded
`clojure/core/logic.clj` resource and, when available, the Maven
`pom.properties` version. This must pass under the default dependency, the
published-upgrade profile, and any patched-source or patched-artifact profile.

### 2. Before/After Probe Discipline

Every credited host experiment must run against the same Proflog probes before
and after the host change. The minimum comparison set is:

- `lein test-proflog-fast`
- `lein test-proflog-constructor-recursive`
- `timeout 300s lein test proflog.list-kernel-matrix-test`
- targeted raw matrix probes for the three ADR-0031 carried failures
- `timeout 180s lein test proflog.synthesis-modes-test`

Known failing carried rows may remain failing during early experiments, but the
branch must record whether host changes improve answer count, target closure,
proof shape, or wall-clock behavior.

### 3. Generic Core.logic Review

The branch must review the exact implementation being modified. Initial review
targets are:

- unification: `walk`, `walk*`, `unify`, `ext`, `LVar`, `LCons`, and sequence
  unification;
- stream scheduling: `Choice`, `bind`, `mplus`, `take*`, `fresh`, and `conde`;
- constraint maintenance: `==`, changed-var tracking, constraint queues, and
  disequality interaction;
- tabling: `AnswerCache`, `reuse`, `subunify`, and suspended streams;
- nominal terms: `Nom`, `Tie`, `hash`, `swap-noms`, and nominal reification.

### 4. Deployment Lanes

The branch should keep deployment lanes explicit:

- default: `org.clojure/core.logic` 1.0.1 from Maven;
- published upgrade: a Leiningen profile selecting the current upstream stable
  artifact;
- patched source overlay: a profile with local `core.logic` source ahead of the
  jar on the classpath;
- patched artifact: a new local Maven coordinate only if the source overlay is
  too fragile for repeated test runs.

### 5. List-Family Exit Criteria Retained

ADR-0032 inherits ADR-0031's nominal exit criteria:

- the CI-safe matrix demonstrates family generalization beyond ADR-0030's
  narrow examples;
- the raw answer path produces closed targets for representative reverse and
  partial synthesis rows without relying on public list materialization;
- the long-running diagnostic matrix records complexity growth and identifies
  any remaining impractical rows by proof-search cause;
- the implementation remains generic and projection-free in the kernel-facing
  path.

## Implementation Order

1. Document local and upstream `core.logic` source findings.
2. Add a runtime host probe and published-upgrade profile.
3. Run default and upgrade-profile smoke tests and targeted matrix probes.
4. If the upgrade alone is not sufficient, create a source-overlay deployment
   path with a verification marker.
5. Profile or instrument the matrix enough to choose one host patch target.
6. Test host patches against default, upgrade, and patched profiles.
7. Record an AAR stating whether host changes satisfy the carried list-family
   criteria or only narrow runtime cost.

## Progress Notes

- Added a runtime host probe and a `core-logic-1.1.1` Leiningen profile.
- The published 1.1.1 upgrade is compatible with the focused Proflog suites
  exercised so far and modestly improves wall-clock time on the carried raw
  matrix probes, but it does not close any of the carried targets. See
  [Core.logic 1.1.1 Upgrade Probe](../log/2026-05-01-core-logic-1-1-1-upgrade-probe.md).
- Added a verified source-overlay lane for local host patches. See
  [Core.logic Source Overlay Deployment](../log/2026-05-01-core-logic-source-overlay.md).
- Tested and rejected a generic `unify` identical-after-walk fast path because
  it was compatible but did not produce broad timing improvement or close
  carried rows. See
  [Core.logic Unify Identical-After-Walk Probe](../log/2026-05-01-core-logic-unify-identical-probe.md).
- Tested and rejected a generic `ISeq` walk structural-sharing patch because it
  was compatible but slowed most carried rows and closed none. See
  [Core.logic ISeq Walk Sharing Probe](../log/2026-05-01-core-logic-iseq-walk-probe.md).
- Compared the pinned 1.0.1 JVM source with the published 1.1.1 JVM source and
  found no implementation diff in the reviewed files beyond Proflog's overlay
  marker. See
  [Core.logic 1.0.1 vs 1.1.1 Source Comparison](../log/2026-05-01-core-logic-1-0-1-1-1-source-comparison.md).
- Probed `core.logic` tabling/reification internals and found that the carried
  raw matrix rows do not exercise `AnswerCache`, `reuse`, `subunify`, tabled
  reification, or suspended streams. No production patch was retained. See
  [Core.logic Tabling/Reification Probe](../log/2026-05-01-core-logic-tabling-reification-probe.md).
- Tested and rejected batched `run-constraints*` dispatch across changed
  variables because it was compatible but did not change carried answer shape
  and produced mixed timing with a material slowdown on one carried row. See
  [Core.logic Constraint Run Batch Probe](../log/2026-05-01-core-logic-constraint-run-batch-probe.md).
- Added a Proflog-side count probe for selected `core.logic` entry points. On
  the carried `reverse-input-flat` row, both default 1.0.1 and upgraded 1.1.1
  reported the same call distribution: `walk*`/reification dominated counted
  calls, unification was second, nominal and constraint hooks were much smaller,
  stream choice construction was tiny, and tabling was unused. See
  [Core.logic Count Probe](../log/2026-05-01-core-logic-count-probe.md).
- Tested and rejected small `Choice.take*` and `LCons` walk allocation patches
  because they were compatible but slower on the carried rows and closed none.
  See
  [Core.logic Stream/Walk Negative Probe](../log/2026-05-01-core-logic-stream-walk-negative-probe.md).
- Ran and reverted a diagnostic no-occurs-check source-overlay experiment. It
  confirmed that occurs-check has runtime cost but still did not close carried
  targets, so no unsound production path was retained. See
  [Core.logic No Occurs-Check Diagnostic](../log/2026-05-01-core-logic-no-occurs-check-diagnostic.md).
- Recorded the remaining generic host optimization frontiers. The next
  concurrent experiments are vector-specialized unification with a workload
  that actually exercises vectors, and bounded walk/reification memoization or
  fusion. See
  [Core.logic Remaining Optimization Frontiers](../log/2026-05-01-core-logic-remaining-frontiers.md).
- Evaluated both concurrent experiments and merged neither implementation. The
  vector-specialized path was generic and exercised by carried rows, but it did
  not improve closure or answer shape. The walk/reify memo variants reduced
  misleading public `walk*` counts or added caches but regressed runtime and
  closed no targets. The main branch retest still passes host verification,
  constructor-recursive, fast, and CI-safe matrix checks, while the three
  carried raw reverse rows and two synthesis-mode failures remain. See
  [ADR-32 Concurrent Core.logic Probe Evaluation](../log/2026-05-01-adr32-concurrent-core-logic-probe-evaluation.md),
  [Core.logic Vector Unification Probe](../log/2026-05-01-core-logic-vector-unification-probe.md),
  and
  [Core.logic Walk/Reify Memo Probe](../log/2026-05-01-core-logic-walk-reify-memo-probe.md).
- Added exact legacy/greenfield worked traces for the current failures. The
  three reverse rows are legacy-passable and constructor-recursive-passable, but
  still ordinary-raw-greenfield failing. The synthesis-mode rows are closer:
  one is a residual-frontier failure and the other is answer ordering. See
  [Legacy / Greenfield Failure Traces](../log/2026-05-01-legacy-greenfield-failure-traces.md).
- Recorded the design lessons from those traces. The remaining evidence points
  toward greenfield answer-frontier work, especially residual completion,
  base-before-recursive ordering, constructor-recursive descent in the ordinary
  raw path, and structurally safe answer variables flowing through recursion.
  See
  [Greenfield Lessons From Legacy Traces](../log/2026-05-01-greenfield-lessons-from-legacy-traces.md).
- Promoted the structural answer-variable direction into successor
  [ADR-0033](ADR-0033-structural-answer-variable-recursion.md). ADR-0033 keeps
  ADR-0031's carried list-family goal but leaves the `core.logic` host layer and
  targets greenfield's ordinary raw answer path instead.

## Constraints

- Preserve kernel purity. No new executable `core.logic/project` may enter the
  proof-facing path.
- Preserve open, reverse, and partial synthesis behavior.
- Do not disable occurs check in a credited production path unless a separate
  soundness argument proves the path cannot construct cyclic terms.
- Do not accept a speedup that only improves one named list test while
  regressing family-level behavior.
- Do not treat a local checkout or extracted jar source as active until runtime
  verification proves it is the loaded implementation.

## Test Obligations

- Add and run a host verification probe.
- Compare default and upgraded `core.logic` behavior on fast and focused
  Proflog suites.
- Re-run the ADR-0031 carried failures after every host experiment.
- Keep ADR-0031's constructor-recursive sidecar tests green unless a deliberate
  replacement makes that layer obsolete and the AAR explains why.
- Write an AAR before merging back to the primary branch.
