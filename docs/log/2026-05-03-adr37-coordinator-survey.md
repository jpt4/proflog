# ADR-37 Coordinator Survey

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`
Base: `69c90db Record ADR-36 tabling decision`

## Branch Assumptions

ADR-0037 is branched from ADR-0036 after ADR-36 recorded the negative direct
tabling result. The current task is research and planning for a project-local
core.logic enhancement track. Production Proflog behavior and vendored
core.logic source remain unchanged in this coordinator pass.

The active branch is:

```text
adr-0037-core-logic-minikanren-enhancements
```

The branch already contains the ADR-37 scaffold and the ADR-36 handoff note.

## Delegation

Three subordinate read-only subagents were spawned in isolated worktrees from
the ADR-37 branch:

| Subagent | Branch | Worktree | Scope |
| --- | --- | --- | --- |
| Feature survey | `adr-0037-subagent-feature-survey` | `/tmp/proflog-adr37-feature-survey` | faster-minikanren and canonical Scheme/Racket miniKanren feature survey |
| Core.logic audit | `adr-0037-subagent-core-logic-audit` | `/tmp/proflog-adr37-core-logic-audit` | vendored core.logic TODO and performance audit |
| Proflog integration | `adr-0037-subagent-proflog-integration` | `/tmp/proflog-adr37-proflog-integration` | candidate Proflog call sites and migration probes |

Each subagent was instructed not to edit files. Their reports were captured in:

```text
/tmp/proflog-adr37-feature-survey.md
/tmp/proflog-adr37-core-logic-audit.md
/tmp/proflog-adr37-proflog-integration.md
```

## Primary Sources Consulted

- miniKanren implementation catalog: `https://minikanren.org/`
- miniKanren interactive tutorial constraint section:
  `https://io.livecode.ch/learn/webyrd/webmk`
- faster-miniKanren README and source:
  `https://github.com/michaelballantyne/faster-minikanren`
- faster-miniKanren `numbers.scm` and `test-numbers.scm`
- miniKanren-with-symbolic-constraints README and source:
  `https://github.com/webyrd/miniKanren-with-symbolic-constraints`
- cKanren paper:
  `https://www.schemeworkshop.org/2011/papers/Alvis2011.pdf`
- cKanren source README:
  `https://github.com/calvis/cKanren`
- alphaKanren paper:
  `https://webyrd.net/alphamk/alphamk.pdf`
- Byrd dissertation record:
  `https://scholarworks.iu.edu/dspace/items/450e1b65-70da-4a38-8e73-c182818de110/full`
- core.logic README and source:
  `https://github.com/clojure/core.logic`

Local sources inspected:

- `project.clj`
- `vendor/core.logic-1.1.1/src/clojure/core/logic.clj`
- `vendor/core.logic-1.1.1/src/clojure/core/logic/fd.clj`
- `vendor/core.logic-1.1.1/src/clojure/core/logic/nominal.clj`
- `vendor/core.logic-1.1.1/src/clojure/core/logic/arithmetic.clj`
- `src/proflog`
- `test/proflog`
- `docs/adr/ADR-0032-core-logic-performance.md`
- `docs/adr/ADR-0036-speculative-relational-arithmetic-and-tabling.md`
- `docs/log/2026-05-03-adr36-relational-arithmetic-and-tabling-probes.md`

## Tabling Decision Handoff

ADR-36 already recorded the evidence that Proflog is not trivially duplicating
raw core.logic tabling. The smoke probe exercised core.logic tabled answer
caching, but the ADR-35 list-family rows only created tabled substitutions and
did not hit answer-cache reuse, `subunify`, or tabled reification.

Decision for ADR-37:

- Do not pursue replacement of `proflog.tabling` with raw `core.logic/tabled`.
- Keep `proflog.tabling` as an extension/refinement around core.logic tabling
  with Proflog-specific canonical proof-state keys.
- Only reopen tabling if a later phase finds a specific canonical-state
  integration point, such as sharing canonical key construction with answer
  overlay continuation caching.

## Feature Gap Findings

### Priority 1: Public Symbolic Constraints

Add project-local public relations equivalent to canonical miniKanren:

- `symbolo`
- `numbero`
- `absento`

Evidence:

- faster-miniKanren exposes `symbolo`, `numbero`, `stringo`, and `absento`.
- miniKanren-with-symbolic-constraints exposes `symbolo`, `numbero`,
  generalized `absento`, and disequality.
- cKanren documents stable libraries for disequality plus
  `absento`/`symbolo`/`numbero`.
- core.logic 1.0.1 and the vendored 1.1.1 source expose `predc`, `treec`,
  `!=`, and `distincto`, but not public `symbolo`, `numbero`, `stringo`, or
  `absento`.

Verification command:

```text
lein trampoline run -m clojure.main -e \
  "(require '[clojure.core.logic :as l])
   (doseq [s '[symbolo numbero stringo absento predc treec != distincto]]
     (println s (boolean (ns-resolve 'clojure.core.logic s))))"

symbolo false
numbero false
stringo false
absento false
predc true
treec true
!= true
distincto true
```

The same result holds under `+core-logic-source-overlay`.

The first implementation should be an overlay or wrapper layer, not an engine
rewrite:

- `symbolo` can be expressed initially with `predc` and `symbol?`;
- `numbero` can be expressed initially with `predc` and `number?`;
- `absento` can be expressed initially with `treec` plus disequality, matching
  the ADR-36 translated arithmetic tests.

This is low risk relative to engine work, but the wrappers still need tests for
ordering, residual reification, open terms, nested `lcons`, ordinary lists,
vectors, maps, and interaction with disequality.

### Priority 2: Constraint Residual Vocabulary and Reification

The wrapper layer must not only decide success/failure. It must also reify
answers in a vocabulary close enough to canonical miniKanren that test output
and future users see `symbolo`, `numbero`, and `absento`, rather than only
lower-level `predc` or `treec` artifacts.

This should be phase 1.5 because the public relation behavior can be tested
before residual rendering is polished, but production answer tests will depend
on stable residuals before adoption.

### Priority 3: Relational Arithmetic Fuel Adapter

The imported faster-miniKanren relational arithmetic remains useful because it
does not rely on hardcoded finite-domain ranges. Its immediate Proflog purpose
is narrower than a general arithmetic library:

- test replacing `kernel-support/step-fuelo` finite-domain constraints;
- preserve the current public `nil` or host-integer fuel API if possible;
- avoid a hardcoded numeric domain becoming part of reverse or partial search
  semantics.

ADR-36 proved that bit-list fuel can step relationally, but it is not a
drop-in production replacement because production callers pass host integers.
ADR-37 should therefore build an explicit adapter/profile before touching the
production kernel path.

### Priority 4: Proflog Type Relations

After `symbolo`/`numbero`/`absento` exist, Proflog may benefit from reusable
relations for project-specific object-language categories:

- proof variable term;
- answer variable term;
- rigid parameter term;
- constructor application term;
- L-ground term;
- call-free formula;
- closed object-language term.

These should be introduced only if they simplify existing structural relations
without making the tableau rules less readable or less relational.

### Priority 5: Engine Optimizations

ADR-32 already rejected several small generic host patches:

- identical-after-walk unification shortcut;
- structural-sharing returns from `ISeq` and `LCons` walking;
- `Choice.take*` and related stream/walk micro-patches;
- batched constraint dispatch;
- global no-occurs-check diagnostic.

The remaining generic optimization target with the strongest evidence is still
walk/reify/unify/occurs-check pressure, especially around disequality
maintenance. The vendored `!=c` path explicitly notes that walking both sides
and checking equality/unification appears expensive. That is promising, but it
is not phase-1-sized because changing disequality normalization can alter
answer residuals and constraint semantics.

Engine work should wait until phase 2 or later and must be accompanied by
generic core.logic probes, not only Proflog list-family measurements.

## Proflog Integration Candidates

### Fuel

Primary target:

- `src/proflog/kernel_support.clj` `step-fuelo`

Callers are widespread in:

- `src/proflog/kernel.clj`
- `src/proflog/answer_overlay.clj`

Migration:

1. Keep current production `step-fuelo` untouched.
2. Add an opt-in adapter that converts integer fuel to bit-list fuel at the
   boundary or runs a separate bit-list-fuel profile.
3. Re-run kernel, answer-overlay, synthesis, and list-family probes.
4. Replace production finite-domain fuel only if reverse/partial synthesis
   improves without unacceptable runtime regression.

### Absence and Membership Checks

Candidates:

- `proflog.equality/absent-termo`
- `proflog.equality/absent-paro`
- `proflog.equality/occurs-termo`
- `proflog.kernel-support/l-ground-termo`
- `proflog.kernel-support/call-free-formulao`
- proof-variable membership checks in `proflog.kernel-support/proof-bindingso`

Do not mechanically replace these with generic `absento`. Proflog absence
relations walk through explicit tableau substitutions and distinguish proof
variables, answer variables, and rigid parameters. Generic `absento` is a useful
building block only after tests show it composes with those walked substitutions.

### Type Relations

Candidates:

- structural term/formula classifiers in `proflog.kernel`
- L-ground checks in `proflog.kernel-support`
- call-free formula checks in `proflog.kernel-support`
- answer-overlay fast-continuation classifiers in `proflog.answer-overlay`

These are candidates for project-local relation vocabulary after the low-risk
symbolic constraints are available.

### Tabling and Canonicalization

Candidate:

- share canonical key logic between `proflog.tabling` and answer-overlay
  continuation detection.

Rejected:

- raw replacement of `proflog.tabling` with direct `core.logic/tabled`.

## Recommended ADR-37 Phases

### Phase 0: Documentation and Baseline

- Keep this coordinator report as the baseline.
- Preserve the ADR-36 tabling closure.
- Verify loaded core.logic hosts under default, published 1.1.1, and source
  overlay profiles before any code work.

### Phase 1: Constraint Overlay

- Add project-local `symbolo`, `numbero`, and `absento` wrappers.
- Add upstream-style behavior tests and residual reification tests.
- Port ADR-36 arithmetic tests to consume the overlay API rather than test-local
  shims.
- Do not modify production Proflog proof code.

### Phase 2: Fuel Adapter Probe

- Build an opt-in bit-list fuel adapter or profile.
- Preserve the public `nil` or integer fuel interface unless an explicit
  migration is accepted.
- Compare finite-domain and bit-list fuel on reverse/partial synthesis probes.

### Phase 3: Proflog Relation Vocabulary

- Evaluate whether `absento` and type constraints simplify Proflog-specific
  absence/type checks.
- Start with tests around equality and support helpers, not the full kernel.
- Adopt only where proof-variable, answer-variable, and parameter distinctions
  remain explicit.

### Phase 4: Engine Optimization

- Revisit generic core.logic optimizations only after phases 1-3 identify a
  real bottleneck or semantic constraint gap.
- Likely first target: disequality maintenance around `!=c`.
- Excluded from near-term work: tabling engine rewrite, nominal unification
  changes, global occurs-check removal, and FD propagation redesign.

## Test Strategy

Constraint overlay:

```text
lein test proflog.relational-arithmetic-upstream-test
lein test proflog.relational-arithmetic-test
```

Core Proflog regression gate:

```text
lein test-proflog-fast
lein test-proflog-constructor-recursive
lein test proflog.kernel-test proflog.tabling-test proflog.closed-term-gamma-test proflog.synthesis-modes-test
lein test proflog.equality-test proflog.existential-disequality-test proflog.subst-test proflog.adversarial-test
```

Fuel and performance probes:

```text
lein probe-core-logic-host
lein with-profile +core-logic-source-overlay probe-core-logic-host
lein probe-core-logic-count reverse-input-flat 16
lein probe-core-logic-tabling
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-deep-nested-longer
```

Any vendored core.logic change must run through `+core-logic-source-overlay`
and compare against both the default 1.0.1 jar and published 1.1.1 jar.

## Current Decision

Proceed with ADR-37 as a speculative branch, but keep the next implementation
slice narrow:

1. Add a project-local symbolic constraint overlay first.
2. Use that overlay to retire ADR-36 test-local `symbolo` and `absento` shims.
3. Prototype relational fuel replacement only behind an opt-in adapter/profile.
4. Do not pursue raw core.logic tabling further.
5. Defer engine-level optimization until concrete phase-1/phase-2 probes show
   where it matters.
