# ADR-37 Core.logic Performance Triage

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`
Worker: ADR-37 Worker D

## Scope

This pass inspected the core.logic implementation surfaces relevant to Proflog:

- unification, `walk`, `walk*`, reification, and occurs check;
- constraint queueing and propagation;
- finite-domain constraints;
- disequality and deep tree constraints;
- nominal support; and
- tabling.

Sources inspected:

- default Maven dependency: `org.clojure/core.logic 1.0.1`
- published comparison dependency: `org.clojure/core.logic 1.1.1`
- vendored source overlay: `vendor/core.logic-1.1.1/src`
- ADR-32 performance logs
- ADR-36 arithmetic/tabling logs
- ADR-37 coordinator survey

Runtime host checks confirmed that default Proflog still loads the 1.0.1 Maven
jar and the upgrade profile loads the 1.1.1 Maven jar. The vendored 1.1.1 JVM
source is materially the same implementation as the 1.0.1 JVM source for the
reviewed files; `logic.clj` differs only by Proflog's source-overlay marker, and
`fd.clj`, `nominal.clj`, and `protocols.clj` have no source diff.

## TODO and Hot-Path Inventory

Relevant TODOs in `vendor/core.logic-1.1.1/src/clojure/core/logic.clj`:

- `unify` has a note that an identical-after-walk check is not currently usable
  because walking may add metadata to vars.
- `LCons` `walk-term` is stack-consuming; the comment suggests CPS/trampoline
  would need deeper redesign.
- `run-constraints*` / `fix-constraints` has a TODO about preserving more
  natural constraint run order; the file also records that a prior attempt hit
  overflow or memory problems in cryptarithm benchmarks.
- constraint enforcement and answer forcing have hardcoded finite-domain
  assumptions.
- force-answer traversal only explicitly handles selected Clojure tree types.
- disequality `!=c` explicitly notes that walking both sides with `walk*` and
  then testing equality/unification appears expensive.
- tabled goals have an open concurrency TODO.

Relevant TODOs in `fd.clj`:

- multiplication/division propagation has a TODO around division trouble.
- finite domains already carry interval min/max optimizations, but answer
  forcing still enumerates domain values through `map-sum`.

Nominal support has no local TODO cluster, but the count probe shows nominal
operations are not free on Proflog reverse rows. `swap-noms`, `hash`, and
nominal reification should therefore remain in future profiler scopes even
though they are not the first patch target.

## ADR-32 Evidence Carried Forward

ADR-32 already tested and rejected several tempting micro-patches:

- identical-after-walk `unify` fast path;
- `ISeq` and `LCons` structural-sharing `walk-term` variants;
- `Choice.take*` lazy-tail simplification;
- batched `run-constraints*` dispatch;
- vector-specialized unification;
- local `walk*` / reification memoization variants; and
- diagnostic global no-occurs-check mode.

The ADR-32 count probe on `reverse-input-flat` is still the strongest
attribution evidence:

| Category | Calls | Share |
| --- | ---: | ---: |
| walk/reification | 5,302,932 | 0.675 |
| unification | 2,282,887 | 0.291 |
| nominal | 162,945 | 0.021 |
| constraints | 94,537 | 0.012 |
| streams | 11,181 | 0.001 |
| tabling | 0 | 0.000 |

Top calls were `walk*`, `occurs-check`, `unify`,
`unify-with-sequential*`, and `ext`. This argues for continued attention to
walk/reify/unify/occurs-check pressure, but the negative patch history says the
next step must be a better benchmark/profiler, not another narrow speculative
edit.

ADR-32 also showed that the 1.1.1 dependency profile was compatible and sometimes
faster, but did not change answer shape. Since the reviewed JVM source is
effectively unchanged from 1.0.1, those timing differences should be treated as
noise or deployment variance unless a future controlled timing run shows
otherwise.

## ADR-36 Evidence Carried Forward

ADR-36 closes direct raw core.logic tabling replacement for the current Proflog
question. A smoke probe exercised core.logic answer caching, reuse,
`subunify`, and tabled reification, but ADR-35/ADR-36 list-family rows only
created tabled substitutions and did not enter the active tabled answer path.

Decision for this triage: do not spend ADR-37 engine work on `AnswerCache`,
`reuse`, `subunify`, suspended stream scheduling, or raw `core.logic/tabled`
replacement unless a new workload proves those paths are hot. Proflog's current
tabling layer is a refinement over core.logic tabling because it adds canonical
proof-state keys.

ADR-36 also explains why finite-domain behavior matters: production
`step-fuelo` uses FD arithmetic with a hardcoded domain, while the imported
relational arithmetic works over bit-list numerals. That is primarily a
semantic/search-mode question before it is a performance question.

## Ranked Opportunities

### Safe Micro-Optimization

No new source-level core.logic micro-optimization is recommended as "safe" from
this pass.

The safe work is measurement and harness improvement:

- keep `probe-core-logic-host` in every comparison;
- add narrower counters for disequality, `treec`, `predc`, and FD answer
  forcing before patching those paths;
- add small host-level benchmark relations that exercise exactly one suspected
  path at a time; and
- keep public semantic tests separate from Proflog list-family timing probes.

Reason: ADR-32 already showed that locally plausible micro-patches can be
compatible and still slow the carried workloads or fail to change answer shape.

### Needs Benchmark Before Patch

1. Disequality maintenance in `!=c`.

   This is the best current engine candidate. The source itself marks the
   repeated `walk*`-both-sides path as expensive, and ADR-32's count probe says
   walk/reify/unify pressure dominates. A patch could still be semantically
   risky because disequality residuals are observable answers, so benchmark and
   residual tests must come first.

2. Sound local occurs-check reduction.

   Global no-occurs-check was faster but unsound and did not close carried
   targets. A narrower reduction may be viable only where a proof establishes
   that extending a variable cannot introduce a cycle, for example fresh var to
   known-ground term. This needs both a soundness note and a cyclic-term
   regression suite before timing matters.

3. Reification / `walk*` fusion.

   ADR-32's small memoization attempts regressed runtime, but the call-volume
   signal remains real. A future attempt should be framed as a fused traversal
   over a stable substitution snapshot and measured with allocation profiling,
   not just Var call counts.

4. Constraint queue scheduling.

   The source TODO is real, but ADR-32's batched dispatch patch had mixed
   timings and slowed `reverse-input-flat`. Reopen only with a workload that is
   demonstrably constraint-queue bound, such as ADR-37 `absento`/`treec`
   overlays or FD-heavy host benchmarks.

5. FD answer forcing and FD propagation.

   `force-ans` and FD value enumeration matter for `step-fuelo` because FD
   constraints can force finite, hardcoded domains into answer generation. This
   should be benchmarked as part of the finite-domain vs bit-list-fuel semantic
   assessment, not as a generic FD optimization first.

6. Nominal support.

   Nominal calls are visible but much smaller than walk/unify counts in the
   ADR-32 row. Keep nominal in profiler scopes because Proflog relies on it, but
   defer nominal engine patches unless a nominal-heavy proof family isolates it
   as the bottleneck.

### Semantic Risk / Defer

- Raw direct `core.logic/tabled` replacement: closed by ADR-36 evidence.
- Global no-occurs-check: unsound, diagnostic only.
- Generic stream scheduler rewrites: previous stream/walk changes slowed the
  carried rows; a scheduler redesign needs a separate semantic ADR.
- FD propagation redesign: likely useful in general, but not the right first
  move for Proflog's concern about reverse/partial synthesis and hardcoded fuel
  domains.
- Nominal unification changes: high correctness risk; defer unless isolated
  evidence requires it.
- Deep substitution-store representation changes: possible long-term engine
  work, but too broad without a generic benchmark suite and correctness oracle.

## Recommended Benchmarks and Probes

Before any core.logic engine patch:

```text
timeout -k 10s 60s lein probe-core-logic-host
timeout -k 10s 60s lein with-profile +core-logic-1.1.1 probe-core-logic-host
timeout -k 10s 60s lein with-profile +core-logic-source-overlay probe-core-logic-host
```

Baseline Proflog compatibility:

```text
timeout -k 10s 180s lein test-proflog-core-logic-host
timeout -k 10s 300s lein test-proflog-fast
timeout -k 10s 240s lein test-proflog-constructor-recursive
timeout -k 10s 300s lein test proflog.kernel-test proflog.tabling-test proflog.closed-term-gamma-test proflog.synthesis-modes-test
```

Current count and list-family probes:

```text
timeout -k 10s 240s lein probe-core-logic-count reverse-input-flat 16
timeout -k 10s 240s lein probe-core-logic-count reverse-input-flat-longer 16
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-deep-nested-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

Tabling guardrail:

```text
timeout -k 10s 180s lein probe-core-logic-tabling
timeout -k 10s 240s lein probe-core-logic-tabling reverse-input-flat-longer reverse-output-deep-nested-longer
```

ADR-37 constraint overlay and arithmetic guard:

```text
timeout -k 10s 240s lein test proflog.relational-arithmetic-test proflog.relational-arithmetic-upstream-test
```

For a disequality patch, add a dedicated host/proflog probe before editing
`logic.clj`:

```text
lein test proflog.existential-disequality-test proflog.equality-test proflog.subst-test
timeout -k 10s 240s lein probe-core-logic-count reverse-input-flat 16
```

For FD-vs-relational-fuel assessment, compare the current finite-domain
`step-fuelo` against the bit-list arithmetic adapter on mode-sensitive queries:

```text
lein test proflog.kernel-test proflog.synthesis-modes-test
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-deep-nested-longer
```

The fuel probe should record answer shape, residual constraints, target closure,
and timeout behavior. Wall-clock timing alone is insufficient because the
primary concern is whether FD domains block reverse or partial synthesis.

## Recommendation

Do not start ADR-37 with engine optimization.

Proceed first with the symbolic constraint overlay and fuel adapter probes. Use
those phases to determine whether `absento`, `symbolo`, `numbero`, and
bit-list fuel create new hot paths. Reopen core.logic engine work only after a
new probe shows a concrete bottleneck that survived the ADR-32 negative
micro-patch evidence.

If engine work does reopen, start with disequality maintenance and a sound local
occurs-check reduction proposal. Keep tabling replacement closed.
