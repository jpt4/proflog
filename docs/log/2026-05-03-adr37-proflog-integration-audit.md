# ADR-37 Proflog Integration Audit

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`
Worker: C, Proflog integration audit

## Scope

This pass inspected `src/proflog` and `test/proflog` for places where an ADR-37
constraint overlay (`symbolo`, `numbero`, `absento`, tree constraints) or
Proflog-specific type relations could replace membership checks, type checks,
projection-heavy guards, or finite-domain fuel arithmetic.

No source or tests were changed. The only write from this pass is this log.

## Correction: Broader Integration Frame

The initial audit framed the question too narrowly around where existing
miniKanren-generic constraints can be adopted in Proflog. ADR-37 should also
ask where Proflog's data structures and proof procedures expose generic
core.logic gaps that are worth fixing below Proflog.

The useful distinction is not "generic miniKanren feature" versus
"Proflog-specific feature." It is:

- **generic relation/constraint, Proflog-motivated**: a capability such as
  relational map lookup/update, indexed associative state, delayed tree
  predicates, or relational finite arithmetic that could be used by any
  core.logic program but is motivated by Proflog's workload;
- **Proflog-local relation**: a relation that depends directly on Fitting's
  tableau representation, object-language terms, proof variables, rigid
  parameters, or answer residual semantics.

Under that frame, ADR-37 should actively investigate core.logic-level support
for Proflog-shaped state when the resulting abstraction is generally useful.
Examples:

- If `sigma`, program clause lookup, residual frontiers, or disequality indexes
  would be faster or clearer as maps, then the generic question is whether
  core.logic has adequate relational map support. If it does not, a Proflog
  motivated relational-map layer or core.logic extension is in scope.
- Legacy used `project` for the L-groundness guard, while greenfield uses a
  structural relation. The ADR-37 question is whether `treec`, `predc`, or a
  custom core.logic constraint can express a delayed L-ground-style tree
  predicate without the unsound timing behavior of `project` and without
  repeatedly deconstructing the same object-language term.
- If Proflog repeatedly walks or reifies the same proof-state structure because
  core.logic lacks a suitable indexed/constraint representation, that may be a
  generic core.logic performance feature even though Proflog is the workload
  that revealed it.

The rest of this audit should therefore be read as a first pass over safe
adoption points and obvious rejects, not as a complete inventory of
Proflog-motivated core.logic extension opportunities.

## Primary Finding

The best Proflog integration target remains fuel, not a broad mechanical
rewrite of existing membership checks.

`proflog.kernel-support/step-fuelo` currently uses finite-domain constraints
over a hardcoded host range:

- `src/proflog/kernel_support.clj:381` defines `fuel-domain` as
  `(fd/interval 1 Long/MAX_VALUE)`.
- `src/proflog/kernel_support.clj:384` defines `next-fuel-domain` as
  `(fd/interval 0 (dec Long/MAX_VALUE))`.
- `src/proflog/kernel_support.clj:387` implements `step-fuelo` with a `nil`
  unbounded branch and an FD branch using `fd/in` plus `fd/+`.

The FD choice is operationally acceptable when callers pass concrete host
integer fuel, as most production and synthesis tests do. It is less suitable as
a relational object in reverse or partial modes because the numeric host domain
becomes part of the relation. The current open-fuel behavior also prefers the
`nil` branch, as captured by `test/proflog/reverse_program_synthesis_test.clj`
where compiled program synthesis can leave fuel open and receives `nil`.

ADR-36's bit-list relation proves the shape needed for a replacement:

- `src/proflog/relational_fuel_probe.clj:12` defines a bit-list
  `step-fuelo` where finite fuel is related by `pluso`.
- `test/proflog/relational_arithmetic_test.clj:55` verifies predecessor and
  successor synthesis without FD constraints.
- `test/proflog/relational_arithmetic_test.clj:69` shows a direct kernel proof
  can run when finite fuel is supplied as a bit-list numeral.
- `test/proflog/relational_arithmetic_test.clj:84` records the blocker:
  production integer fuel is not drop-in compatible with bit-list fuel.

Assessment: FD fuel probably does not explain current fixed-fuel answer-mode
successes or failures by itself, but it is a real obstacle for future
meta-level synthesis where fuel is left open, partially constrained, or used as
an answer variable. The next implementation should be an opt-in adapter/profile
that preserves the public `nil` or host-integer API while allowing internal
bit-list fuel where reverse/partial probes need it.

## Ranked Candidates

### Safe / Low Risk

1. **Move ADR-36 arithmetic tests onto the ADR-37 constraint overlay.**

   Current local shims:

   - `test/proflog/relational_arithmetic_upstream_test.clj` defines local
     `absento` with `treec`.
   - The same test namespace defines local `symbolo` with `predc`.

   This should be the first adoption point for public `symbolo` and `absento`.
   It is test-local today, exercises upstream-style semantics, and does not
   alter Proflog proof behavior.

2. **Extract Proflog type vocabulary for existing structural relations.**

   Low-risk names can wrap existing relations rather than replacing them:

   - `proflog.kernel-support/l-ground-termo`
     (`src/proflog/kernel_support.clj:313`)
   - `proflog.kernel-support/l-ground-term*o`
     (`src/proflog/kernel_support.clj:333`)
   - `proflog.kernel-support/call-free-formulao`
     (`src/proflog/kernel_support.clj:347`)

   These are already relational and already avoid projection. A clearer
   Proflog-local vocabulary such as `object-termo`, `constructor-termo`,
   `par-free-termo`, or `call-free-formulao` could improve readability and
   reuse in tableau code, but it is unlikely to change synthesis power unless
   those relations are used at currently projected boundaries.

3. **Keep core.logic overlay generic; keep Proflog types in Proflog.**

   Relations that know about `(var nom)`, `(par nom)`, constructor application
   terms, Fitting procedure-call admissibility, or answer residuals should not
   be added to the generic core.logic overlay. They are object-language facts,
   not miniKanren host constraints.

### Needs Prototype

1. **Fuel adapter/profile for `step-fuelo`.**

   Affects non-closing branch progress throughout:

   - `src/proflog/kernel.clj:302` profiled branch handoff
   - `src/proflog/kernel.clj:347` and `src/proflog/kernel.clj:370` saved
     equality-triggered calls
   - `src/proflog/kernel.clj:834` and `src/proflog/kernel.clj:861` gamma
     instantiation
   - `src/proflog/kernel.clj:965` delta instantiation
   - `src/proflog/kernel.clj:1033` equality continuation
   - `src/proflog/kernel.clj:1144` positive procedure calls
   - `src/proflog/kernel.clj:1248` negative procedure calls
   - `src/proflog/answer_overlay.clj:96` and
     `src/proflog/answer_overlay.clj:146` answer-mode recursive descent

   Expected benefit: removes hardcoded FD intervals from reverse/partial
   synthesis over fuel and lets finite fuel be related by miniKanren arithmetic.

   Risk: the current public API accepts `nil` and host integers. Bit-list fuel
   is a different representation. A production replacement must either convert
   at API boundaries or provide a separate profile for relational fuel probes.

2. **Walk-aware absence built from generic `absento`.**

   Candidate functions:

   - `proflog.equality/absent-termo`
     (`src/proflog/equality.clj:193`)
   - `proflog.equality/absent-paro`
     (`src/proflog/equality.clj:225`)
   - `proflog.equality/occurs-termo`
     (`src/proflog/equality.clj:259`)
   - `proflog.equality/unify-termo`
     (`src/proflog/equality.clj:412`) uses the absence checks before binding.

   Expected benefit: `absento`-style delayed tree constraints may improve
   partial synthesis when the term shape is not known yet, especially around
   equality and occurs checks.

   Risk: a plain generic `absento` over raw Clojure trees is not a replacement.
   These functions first walk through Proflog's explicit `sigma` and distinguish
   proof variables from rigid parameters. Prototype only as an internal helper,
   for example `walk-absento` or `proflog-var-absento`, with tests for proof
   variables, answer variables, and rigid parameters.

3. **Residual scheduler projection boundary.**

   Current projected guards:

   - `proflog.answer-overlay/demanded-negative-callo`
     (`src/proflog/answer_overlay.clj:1618`) projects `sigma` and `formula`.
   - `proflog.answer-overlay/schedule-structural-residual-frontiero`
     (`src/proflog/answer_overlay.clj:1679`) projects the frontier before
     entering the fast continuation scheduler.

   Existing tests show why this matters:

   - `test/proflog/answers_test.clj:331` keeps wholly symbolic residuals
     residual while allowing constructor-demanded frontiers.
   - `test/proflog/answers_test.clj:418` checks relation-level prioritization
     of demanded residuals.
   - `test/proflog/answers_test.clj:668` checks the reverse residual frontier
     exposed by answer diagnostics.

   Expected benefit: replacing the demand check with a relation over walked
   terms could let partially known residual frontiers carry constraints longer
   before projection, improving tableau proof handling for reverse queries.

   Risk: the surrounding fast scheduler is intentionally a concrete boundary.
   Full conversion of the fast scheduler to core.logic would be high risk and
   may erase the ADR-35 performance gain. Prototype only the guard/selector
   boundary first.

4. **Project-specific term/formula classifiers for answer-mode demand.**

   Candidate host predicates:

   - `answers/ground-term?` (`src/proflog/answers.clj:126`)
   - `answers/admissible-term?` (`src/proflog/answers.clj:540`)
   - `answers/admissible-formula?` (`src/proflog/answers.clj:549`)
   - `answers/constructor-demand-term?` (`src/proflog/answers.clj:806`)
   - `answers/defined-negative-call-residual?` (`src/proflog/answers.clj:820`)
   - `answers/residual-has-constructor-demand?`
     (`src/proflog/answers.clj:827`)

   Expected benefit: relation versions could unify terminology with
   `kernel-support/l-ground-termo` and support future proof-producing demand
   checks.

   Risk: most of these functions run after raw proof-state projection during
   answer export or diagnostics. They are not currently blocking reverse search
   because the relation has already produced a state. Prototype only if the
   scheduler boundary above needs a shared relation.

### Reject / Keep As Is

1. **Semantic branch membership with `membero`.**

   Keep these relational membership checks:

   - `kernel-support/complementary-lito`
     (`src/proflog/kernel_support.clj:72`)
   - `equality/contradictory-atomso`
     (`src/proflog/equality.clj:518`)
   - `program/lookup-clauseo`
     (`src/proflog/program.clj:54`)
   - `program/lookup-clause-with-alternativeso`
     (`src/proflog/program.clj:7`)
   - `program/lookup-clause-with-guarded-alternativeso`
     (`src/proflog/program.clj:35`)

   These are not host-side membership shortcuts. They are genuine relational
   selection over branch literals or compiled clause lists. Replacing them with
   `contains?`, `absento`, or type constraints would harm reverse/partial
   synthesis.

2. **Proof-variable membership discipline.**

   `kernel-support/proof-bindingso`
   (`src/proflog/kernel_support.clj:430`) uses `membero` to ensure bindings
   introduced while closing disequalities target gamma proof variables. This is
   a branch-state membership relation, not a generic absence constraint. It can
   be wrapped in a Proflog-local `proof-varo`, but should not move into the
   generic core.logic overlay.

3. **Gamma closed-term candidate enumeration.**

   `gamma/closed-term-candidateo` (`src/proflog/gamma.clj:138`) deliberately
   chooses from a finite concrete candidate set, and `gamma/fuel->closed-term-depth`
   (`src/proflog/gamma.clj:92`) deliberately maps current host fuel into a
   bounded constructor-depth cap.

   This is a bounded Herbrand generation policy, not an accidental type check.
   Replacing it with an open relational generator would likely explode search.
   Revisit only after the fuel adapter exists, and keep the finite cap explicit.

4. **Compile-time validation and AST recognizers.**

   Keep host predicates in:

   - `language/language` declaration validation
     (`src/proflog/language.clj:39`)
   - `language/validate-term` (`src/proflog/language.clj:69`)
   - `language/validate-formula` (`src/proflog/language.clj:119`)
   - `ast/app-term?`, `ast/term?`, and related recognizers
     (`src/proflog/ast.clj:156`)

   These run at construction or validation time. `symbolo` and `numbero` would
   not improve tableau proof search here.

5. **Answer materialization fast paths.**

   Keep the host-level list-specific paths:

   - `answers/append-fast-path-assignments`
     (`src/proflog/answers.clj:697`)
   - `answers/reverse-fast-path-assignments`
     (`src/proflog/answers.clj:748`)
   - `answers/parity-fast-path-assignments`
     (`src/proflog/answers.clj:773`)

   These are explicit parity/materialization helpers above the kernel. They do
   not define Proflog semantics and should not be confused with proof-search
   relations. Replacing them with constraints would not improve reverse/partial
   synthesis; it would remove a deliberately bounded export shortcut.

6. **Raw tabling replacement.**

   Keep ADR-36's decision closed. `proflog.tabling/prove-stateo`
   (`src/proflog/tabling.clj:337`) projects a concrete kernel state only to
   build a canonical table key around core.logic tabling. That is an extension
   of core.logic tabling, not trivial duplication. Generic constraints are not
   the right tool for this boundary.

## Proflog-Specific Relations That Should Stay Out Of The Generic Overlay

The following are useful candidates for a Proflog-local relation vocabulary,
but not for a generic core.logic overlay:

- object-language variable term: `(var nom)`
- rigid parameter term: `(par nom)`
- constructor application term: `(app symbol args...)`
- L-ground or par-free object-language term
- call-free formula
- proof-variable membership in `proof-vars`
- defined program-call residual
- constructor-demanded residual frontier
- walk-aware absence through explicit `sigma`

They depend on Proflog's tableau representation, Fitting's procedure-call
admissibility rule, or answer-overlay residual semantics. The generic overlay
should expose miniKanren-level constraints (`symbolo`, `numbero`, `absento`,
possibly `stringo`) and leave these object-language predicates in Proflog.

## Suggested Phase-4 Implementation Order

1. Replace ADR-36 test-local `symbolo` and `absento` with the ADR-37 overlay.
2. Prototype generic relational map support or an indexed association-list
   representation, then test whether any Proflog state transition can use it
   without losing relational behavior.
3. Prototype an L-ground-style delayed tree constraint and compare it with the
   current structural relation and the rejected legacy `project` behavior.
4. Add a Proflog-local relation namespace or section for object-language type
   predicates, initially wrapping existing `l-ground-termo` and
   `call-free-formulao`.
5. Prototype relational bit-list fuel behind an adapter/profile; run existing
   synthesis tests plus new open-fuel probes.
6. Prototype a relation-level demanded residual selector before projection in
   `answer-overlay`; preserve ADR-35 scheduler tests.
7. Consider walk-aware `absento` inside equality only after the overlay has
   passing upstream-style tests and the fuel prototype shows whether equality
   absence is an actual synthesis blocker.

## Validation

No test suite was run for this audit because no executable source changed. An
exploratory combined finite-domain/open-fuel REPL probe was attempted with a
60s timeout, but the open query dominated the expression and the process exited
with timeout code 124 before useful output was produced. This log therefore
uses source inspection and existing ADR-36/ADR-35 tests as evidence.
