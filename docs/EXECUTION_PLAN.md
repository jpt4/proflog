# Execution Plan

Date: 2026-05-03
Integration branch: `main`

## Current Facts

- The repository already contains an experimental Proflog implementation in `src/cljtap/` and `test/cljtap/`.
- The greenfield effort will treat the experimental implementation as reference material, not as the codebase to incrementally polish into authority.
- The greenfield implementation now lives under `src/proflog/` and `test/proflog/`.
- `main` is the integration branch. Historical references to the original
  `greenfield` branch describe the bootstrap lineage, not the current merge
  target.

## Branch Policy

- `main` is the integration branch for the current implementation.
- Each implementation ADR should normally use a feature branch named `adr-XXXX-short-name`.
- Feature branches merge into `main` only after their ADR exit criteria are met
  and the relevant tests pass.
- Speculative ADR branches may be pushed for review without production adoption
  when their ADR records the guardrails, test evidence, and non-merge decision
  points.

## Implementation Namespace Layout

The current implementation is rooted in `src/proflog/` and `test/proflog/`.
The original baseline namespaces were:

```text
src/proflog/ast.clj
src/proflog/language.clj
src/proflog/normalize.clj
src/proflog/subst.clj
src/proflog/proof.clj
src/proflog/kernel.clj
src/proflog/equality.clj
src/proflog/program.clj
src/proflog/query.clj

test/proflog/ast_test.clj
test/proflog/language_test.clj
test/proflog/normalize_test.clj
test/proflog/subst_test.clj
test/proflog/kernel_test.clj
test/proflog/equality_test.clj
test/proflog/program_test.clj
test/proflog/query_test.clj
test/proflog/answers_test.clj
test/proflog/oracle/herbrand_test.clj
```

The implementation has since added profiled kernel layers, answer-overlay
execution, tabling, constructor-recursive diagnostics, list-family probes,
relational arithmetic experiments, and ADR-37 core.logic enhancement probes.
Use [GREENFIELD_IMPLEMENTATION_TUTORIAL.md](GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
as the current source map.

## ADR Sequence

| ADR | Status | Branch | Scope | Depends on | First failing tests to write | Exit criteria |
|---|---|---|---|---|---|---|
| [ADR-0001](adr/ADR-0001-greenfield-foundation.md) | completed | `greenfield` | mission, process, branch plan, ADR/AAR stack | none | none; documentation-only bootstrap | docs exist, naming is fixed, implementation order is explicit |
| [ADR-0002](adr/ADR-0002-language-and-semantic-boundary.md) | completed | `adr-0002-language-boundary` | AST, language declaration, clause desugaring, NNF, substitution | ADR-0001 | `ast_test`, `language_test`, `normalize_test`, `subst_test` | language declaration enforced, surface syntax compiles to tagged core, NNF and substitution proven by tests |
| [ADR-0003](adr/ADR-0003-pure-relational-kernel.md) | completed | `adr-0003-kernel` | proof terms, tableau kernel, base quantifier/connective rules | ADR-0002 | `kernel_test`, `proof_test` | αleanTAP-style pure relational kernel runs the base first-order tableau fragment |
| [ADR-0004](adr/ADR-0004-equality-kernel.md) | completed | `adr-0004-equality` | free-constructor equality, occurs-check, disequality store | ADR-0003 | `equality_test`, `oracle/herbrand_test` | equality and disequality pass micro-tests and bounded Herbrand oracle checks |
| [ADR-0005](adr/ADR-0005-procedure-calls-and-query-api.md) | completed | `adr-0005-calls-query` | program lookup/binding, subsidiary tableaux, succeed/fail race | ADR-0004 | `program_test`, `query_test` | Fitting `P1` and `P2` run end-to-end and query statuses are honest |
| [ADR-0007](adr/ADR-0007-nim-correctness-and-query-bounds.md) | completed | `adr-0007-nim-correctness-query-bounds` | remediate ADR-0005 on Nim correctness, L-ground calls, and bounded query control | ADR-0005 | `equality_test`, `kernel_test`, `query_test` | winning and losing Nim positions are distinguished correctly and bounded query helpers return predictably |
| [ADR-0008](adr/ADR-0008-test-gap-closure.md) | completed | `adr-0008-test-gap-closure` | close mission-relevant greenfield test gaps and determine reverse-program-synthesis feasibility | ADR-0007 | expand `kernel_test`, `equality_test`, `program_test`, `query_test`, `answers_test`, `synthesis_modes_test` | checklist is current, core gap families are either covered or explicitly deferred, and reverse program synthesis has a documented greenfield determination |
| [ADR-0009](adr/ADR-0009-legacy-program-closure.md) | completed | `adr-0009-legacy-program-closure` | turn the remaining legacy program-family comparison into parity tracking, worked examples, and family-by-family closure | ADR-0008 | `integration_families_test`, `list_programs_test`, `quantified_programs_test`, `synthesis_modes_test`, plus new family namespaces as needed | parity matrix is current, extant families have worked examples, present-but-weaker families are closed or bounded, and promoted legacy-only families are documented honestly |
| [ADR-0010](adr/ADR-0010-frontend-inlining-translation.md) | completed | `adr-0010-dsl-quickstart-docs` | prefix frontend language and helper inlining translation into backend forms | ADR-0009 | `frontend_test` for reusable languages, source clauses, query formulas, inline helper descent, factored Nim, and unsupported recursive helpers | `proflog.frontend` translates visible `(:= ...)` and `(|- ...)` source into backend AST/program/query forms without a separate evaluator; [AAR-0010](aar/AAR-0010-frontend-inlining-translation.md) records validation |
| [ADR-0006](adr/ADR-0006-answer-discipline-and-variant-boundary.md) | proposed | `adr-0006-answers-variants` | answer projection, residual constraints, proof replay, variant gating | ADR-0007 | `answers_test`, `query_test` open-query cases | exported answers are admissible and semantic variants are explicit |
| [ADR-0011](adr/ADR-0011-open-answer-relationality.md) | completed | `adr-0011-open-answer-relationality` | default open-answer search via staged kernel call descent instead of eager pre-unfolding | ADR-0009 | `answers_test`, `list_programs_test`, and any narrow kernel regression needed for direct answer descent | default open-answer mode stages kernel call-depth directly, docs record the new reverse/append boundary, and remaining legacy gaps stay explicit |
| [ADR-0012](adr/ADR-0012-closed-answer-parity-mode.md) | completed | `adr-0012-closed-answer-parity-mode` | long-running closed-answer parity search mode, isolated from the generic symbolic API so its necessity can be evaluated honestly | ADR-0011 | parity-mode regressions for `reverse([a,b],r)`, inverse `append`, and nested list families | the repo can run dedicated closed-answer parity probes without changing the generic symbolic contract, and the branch concludes that the specialty mode is currently necessary |
| [ADR-0013](adr/ADR-0013-relational-answer-performance.md) | completed | `adr-0013-relational-answer-performance` | recursive nonground answer-mode descent, frontier canonicalization, and residual normalization to reduce the need for specialty modes | ADR-0011 | generic-path regressions for reverse parity, deeper append splits, and duplicate frontier collapse | duplicate frontiers are normalized, the known list-family closed answers are now available through `query-answers`, and the branch concludes that ADR-0012 still remains necessary as the explicit closed-answer API |
| [ADR-0014](adr/ADR-0014-generic-legacy-evaluation.md) | accepted | `adr-0014-generic-legacy-evaluation` | generic evaluation of still-unsatisfied legacy families through raw-stream probes, generic post-processing, and explicit layer accounting | ADR-0013 | exploratory selectors and promoted regressions for `GV`, `FD`, and other still-unsatisfied legacy queries | the repo can say for each promoted legacy-unsatisfied query whether the desired answer is absent, late, generically recoverable by stream processing, or only reachable by specialty handling |
| [ADR-0015](adr/ADR-0015-answer-overlay-extraction.md) | completed | `adr-0015-answer-overlay` | extract the separate answer-mode flow from `proflog.kernel` into a dedicated overlay namespace while preserving the pure proof kernel | ADR-0014 | narrow kernel and answer regressions proving the extracted overlay still supports the documented answer surface | the ordinary kernel is callable without embedded answer-mode flow, the answer APIs route through a separate overlay namespace, the shared proof core lives in `kernel_support.clj`, and the extended suite is green on the extracted boundary |
| [ADR-0016](adr/ADR-0016-fair-agenda-and-micro-fuel.md) | completed | `adr-0016-fair-scheduling` | fair agenda scheduling and refined micro-step fuel for proof search | ADR-0015 | scheduler, list-family, answer-diagnostics, and micro-fuel regressions | kernel and answer overlay select pending branch work relationally, fuel has a documented micro-step contract, stale left-first diagnostics are corrected, and fast plus targeted extended regressions are green |
| [ADR-0017](adr/ADR-0017-relational-tabling-and-canonical-state.md) | completed | `adr-0017-relational-tabling` | separate relational tabling and canonical proof-state reuse | ADR-0016 | canonical-key, tabled-vs-untabled, duplicate-state, list-family measurement regressions | a separate tabling namespace reuses canonical kernel states without obscuring the kernel; duplicate fair-agenda substates are tabled, while raw multi-step list proofs remain a documented follow-up |
| [ADR-0018](adr/ADR-0018-existential-disequality-witnesses.md) | completed | `adr-0018-existential-disequality-witnesses` | accurate object-language witnesses for existential disequality programs | ADR-0017 | gatekeeping regressions for `p(x) :- exists y. x != y` over `{a,b}`: `p(a)` and `p(b)` succeed, explicit bounded answers are exactly `a` and `b`, and no `(par ...)` escapes | greenfield evaluates the existential disequality witness program accurately without imitating legacy's impure `project`-based answer leak; fast and legacy-impurity suites pass |
| [ADR-0019](adr/ADR-0019-closed-term-gamma-instantiation.md) | completed | `adr-0019-closed-term-gamma-instantiation` | generic bounded closed-term generation for Fitting gamma instantiation | ADR-0018 | generator regressions for constants, unary and binary constructors, plus gamma / once-forall proofs requiring compound generated terms | gamma instantiation can fairly try generated closed terms from any declared constructor signature without family-specific code, while preserving the kernel's readable Fitting-rule surface |
| [ADR-0020](adr/ADR-0020-pure-gamma-candidate-boundary.md) | completed | `adr-0020-0021-gamma-purity-regressions` | remove projected gamma candidate choice from the kernel path | ADR-0019 | purity regression for explicit finite gamma candidates and no `project` in `proflog.gamma` | gamma candidate choice is explicit finite membership threaded through proof state, preserving reverse/partial kernel use better than projected host inspection |
| [ADR-0021](adr/ADR-0021-gamma-search-regression-repair.md) | completed | `adr-0020-0021-gamma-purity-regressions` | repair closed-term gamma search regressions in extended suites | ADR-0020 | integration and quantified regressions, plus list-family comparison against pre-ADR-0019 checkpoints | generated closed terms remain available for call-free constructor counterexamples without front-loading Herbrand enumeration into recursive program-call search |
| [ADR-0022](adr/ADR-0022-pelletier-problems.md) | completed | `adr-0022-pelletier-problems` | replicate the upstream alphaleanTAP Pelletier problem benchmark suite in greenfield | ADR-0021 | `pelletier_test` for the existing legacy slice, then upstream Problems 1-20 | a greenfield Pelletier catalog exists, the committed tranche passes or is explicitly classified, timings are recorded, and no theorem-specific overlay is used |
| [ADR-0023](adr/ADR-0023-profiled-kernel-layers.md) | completed | `adr-0023-profiled-kernel-layers` | formula-profiled kernel layers that keep the full Proflog rule structure readable while routing simpler formulas through simpler relational components | ADR-0022 | profile classification tests, a generic propositional kernel regression for Pelletier Problem 12, and dispatch tests proving equality/program formulas still use the full kernel | Problem 12 is promoted through a generic pure-propositional path, no theorem-specific dispatch exists, and the full kernel remains didactically transparent |
| [ADR-0024](adr/ADR-0024-pelletier-first-order-performance.md) | completed | `adr-0024-pelletier-first-order-performance` | equality-free first-order performance closure for the remaining Pelletier benchmark tranche | ADR-0023 | comparative measurements for the too-slow tranche, equality-free first-order component tests, and dispatch tests proving equality/program formulas still use broader layers | first generic first-order tranche is promoted, remaining non-passers keep measurements plus follow-up hypotheses, and [AAR-0024](aar/AAR-0024-pelletier-first-order-performance.md) records the outcome |
| [ADR-0025](adr/ADR-0025-pelletier-lean-search-policy.md) | completed | `adr-0025-pelletier-lean-search-policy` | lean alphaleanTAP-shaped first-order search policy for complete Pelletier closure | ADR-0024 | remaining Pelletier closure tests, lean policy comparison records, and direct relational boundary tests | all Pelletier problems are passing without id dispatch, and [AAR-0025](aar/AAR-0025-pelletier-lean-search-policy.md) records the search-policy tradeoffs |
| [ADR-0026](adr/ADR-0026-kernel-layer-interoperation.md) | completed | `adr-0026-kernel-layer-interoperation` | branch-level interoperation between the full program kernel and optimized propositional / first-order layers | ADR-0025 | program-kernel tests for propositional and first-order delegated subbranches, Pelletier aggregate layering closure, and proof-boundary assertions | optimized layers can close purified program subbranches through proof-producing relations while full-kernel equality, disequality, and procedure-call semantics remain authoritative; [AAR-0026](aar/AAR-0026-kernel-layer-interoperation.md) records the outcome |
| [ADR-0027](adr/ADR-0027-transitive-relational-purity.md) | completed | `adr-0027-transitive-relational-purity` | recover transitive relational purity by replacing projected formula substitution in the kernel-facing path | ADR-0026 | substitution preimage regressions, binder-shadowing regressions, kernel reverse-program/body synthesis, and purity grep for `kernel.clj` / `subst.clj` | `subst-formulao` is structural and relational, no projected substitution remains on the kernel path, and remaining reverse/partial failures are reclassified with substitution projection removed as a blocker; [AAR-0027](aar/AAR-0027-transitive-relational-purity.md) records the outcome |
| [ADR-0028](adr/ADR-0028-kernel-support-disequality-purity.md) | completed | `adr-0028-kernel-support-disequality-purity` | recover transitive relational purity for saved disequality maintenance in `kernel_support.clj` | ADR-0027 | reverse/open `neqs`, open `sigma`, stale-prune, and stable-guard regressions around `prove-stateo` | `prune-contradictory-neqso` and `stable-neqso` are structural, the only remaining `kernel_support.clj` projection is fuel, and [AAR-0028](aar/AAR-0028-kernel-support-disequality-purity.md) records the outcome |
| [ADR-0029](adr/ADR-0029-relational-fuel-purity.md) | completed | `adr-0029-relational-fuel-purity` | recover relational purity for `step-fuelo` in `kernel_support.clj` | ADR-0028 | direct fuel predecessor/unbounded synthesis, open-fuel `proveo`, and open-fuel procedure-call body synthesis | `step-fuelo` is structural over unbounded and bounded fuel states, no executable `project` remains in the ordinary kernel-facing path, and [AAR-0029](aar/AAR-0029-relational-fuel-purity.md) records the outcome |
| [ADR-0030](adr/ADR-0030-relational-constructor-search.md) | completed | `adr-0030-relational-constructor-search` | generic relational constructor search control for raw list-family proof closure | ADR-0029 | focused raw list proof selector, rigid constructor disequality discharge tests, guarded procedure-call descent tests, and a non-list constructor-recursive control case | raw `append([a,b], [c], [a,b,c])` and `reverse([a,b], [b,a])` close through the ordinary kernel without list-specific production code or projection; [AAR-0030](aar/AAR-0030-relational-constructor-search.md) records the call-local alternative strategy |
| [ADR-0031](adr/ADR-0031-list-family-kernel-generalization.md) | completed | `adr-0031-list-family-kernel-generalization` | revisit ADR-0030 against family-level append/reverse kernel generalization | ADR-0030 | parameterized raw matrix rows for forward, reverse, partial, inverse, flat, nested, and longer list cases | the branch adds guarded-clause IR and a constructor-recursive sidecar, but carries ordinary raw reverse and synthesis failures forward; [AAR-0031](aar/AAR-0031-list-family-kernel-generalization.md) records the outcome |
| [ADR-0032](adr/ADR-0032-core-logic-performance.md) | accepted | `adr-0032-core-logic-performance` | generic host-language performance and deployment work for the carried ADR-0031 list-family failures | ADR-0031 | runtime host verification, source-overlay deployment, count probes, and carried matrix/synthesis probes | host-level `core.logic` experiments are credited only with runtime verification and broad carried-row improvement; current evidence points back to the proof/answer frontier |
| [ADR-0033](adr/ADR-0033-structural-answer-variable-recursion.md) | accepted | `adr-0033-structural-answer-variable-recursion` | structural answer-variable recursion and residual completion for the carried ADR-0031 list-family failures | ADR-0032 | exact legacy/greenfield failure traces, structural safety tests, carried raw reverse rows, synthesis modes, and fast/constructor-recursive suites | the ordinary raw answer path keeps structurally safe answer variables live across recursive descent, completes procedural residuals before export, and closes the carried rows without list-specific materialization or projection |
| [ADR-0034](adr/ADR-0034-greenfield-implementation-tutorial.md) | accepted | `adr-0034-greenfield-implementation-tutorial-docs` | comprehensive greenfield implementation tutorial and reference | ADR-0033 | documentation-only review of the current source/docs stack | [Greenfield Implementation Tutorial and Reference](GREENFIELD_IMPLEMENTATION_TUTORIAL.md) explains the full implementation stack, cross-layer data flow, diagnostics, and test surfaces |
| [ADR-0035](adr/ADR-0035-relational-residual-continuation.md) | completed | `adr-0035-relational-residual-continuation` | relational structural residual continuation in the answer overlay | ADR-0033 | sidecar-disabled ADR-0033 matrix rows, proof-shape tests, raw diagnostics, and non-list constructor recursion | promoted list-family rows close through a pre-export answer-overlay residual scheduler without ordinary answer-path dependence on the constructor-recursive sidecar; diagnostics can still expose raw frontiers, the full probe catalog eventually closes under a long timeout, and [AAR-0035](aar/AAR-0035-relational-residual-continuation.md) records the follow-up for fully enumerating raw live-state continuation |
| [ADR-0036](adr/ADR-0036-speculative-relational-arithmetic-and-tabling.md) | completed | `adr-0036-spec-relational-arithmetic-tabling` | speculative faster-minikanren arithmetic translation and core.logic tabling reassessment | ADR-0029, ADR-0017, ADR-0035 | translated `numbers.scm` tests, upstream `test-numbers.scm` cases, opt-in bit-list `step-fuelo`, and tabling probes for ADR-0035 list-kernel rows | arithmetic and probes retained, production finite-domain fuel kept, and raw core.logic tabling replacement rejected; [AAR-0036](aar/AAR-0036-speculative-relational-arithmetic-and-tabling.md) records the outcome |
| [ADR-0037](adr/ADR-0037-core-logic-minikanren-enhancements.md) | completed | `adr-0037-core-logic-minikanren-enhancements` | project-local core.logic miniKanren feature and performance enhancements | ADR-0032, ADR-0036 | miniKanren implementation survey, core.logic TODO/performance audit, constraint feature tests, fuel replacement probes, and Proflog integration audit | project-local overlay and probe surfaces retained, production proof search unchanged, finite-domain fuel kept; [AAR-0037](aar/AAR-0037-core-logic-minikanren-enhancements.md) records the outcome |
| [ADR-0038](adr/ADR-0038-fitting-program-kernel-evaluation.md) | completed | `adr-0038-fitting-program-kernel-evaluation` | definitive greenfield evaluation of deep Fitting Proflog programs through the core proof kernel | ADR-0035, ADR-0036, ADR-0037 | deeper P1/P2 tests, list and finite-domain status catalog, group-verifier kernel evaluations, proof-shape assertions, and named-overlay source audits | [AAR-0038](aar/AAR-0038-fitting-program-kernel-evaluation.md) records the kernel-backed fitting-program catalog, proof evidence for promoted true/false outcomes, and explicit GV associativity frontiers |
| [ADR-0039](adr/ADR-0039-kernel-level-group-verification.md) | completed | `adr-0039-kernel-level-group-verification` | kernel-level finite equality verification for full group-verifier associativity and significant transition-system examples | ADR-0023, ADR-0026, ADR-0038 | full GV associativity success/failure rows, significant non-GV transition-system totality/determinism rows, proof-shape assertions, and source audits against family dispatch | the equality-fragment kernel component proves/refutes full GV associativity and larger transition-system laws without hard-family overlay or family-name dispatch; [AAR-0039](aar/AAR-0039-kernel-level-group-verification.md) records the outcome |
| [ADR-0040](adr/ADR-0040-legacy-subsumption-parity.md) | completed | `adr-0040-legacy-subsumption-parity` | focused greenfield parity and extended subsumption gates for remaining legacy-covered rows | ADR-0035, ADR-0038, ADR-0039 | GV identity/closure/inverses parity plus larger group rows, finite-domain disjointness/totality parity plus larger rows, and Peano reverse/partial synthesis parity plus larger rows | focused selector passes; [AAR-0040](aar/AAR-0040-legacy-subsumption-parity.md) records timings, profile boundaries, and shortcomings |
| [ADR-0041](adr/ADR-0041-relational-constructor-recursive-profile.md) | completed | `adr-0041-relational-constructor-recursive-profile` | promote constructor-recursive descent into a relationally pure dispatched kernel profile | ADR-0031, ADR-0035, ADR-0040 | dispatch proof-shape tests, non-list constructor recursion, Peano forward/reverse/partial rows through one relation, source audits for no host-side semantic procedures | `proflog.kernel.constructor-recursive-profile` now emits integrated `profiled constructor-recursive` records through the ADR-0035 structural continuation engine; ADR-40 Peano rows no longer call the diagnostic sidecar directly; [AAR-0041](aar/AAR-0041-relational-constructor-recursive-profile.md) records performance and remaining unsupported shapes |
| [ADR-0042](adr/ADR-0042-equality-fragment-status-consistency.md) | completed | `adr-0042-equality-fragment-status-consistency` | assess and correct equality-fragment `:inconsistent` status behavior for universal finite-domain formulas | ADR-0039, ADR-0040 | `warm-cool-disjoint` status regression, failure-side non-closure for unsound universal branch proofs, positive control with real counterexample, ADR-39 GV/transition regressions | `query-status` now reports `:succeeds` for `warm-cool-disjoint`; [AAR-0042](aar/AAR-0042-equality-fragment-status-consistency.md) records the proof-variable requirement discipline and preserved finite-verifier suite |
| [ADR-0043](adr/ADR-0043-greenfield-documentation-refresh.md) | completed | `adr-0043-greenfield-doc-refresh` | refresh stale runtime/tutorial/example/source documentation for the greenfield implementation | ADR-0035, ADR-0038, ADR-0039, ADR-0040, ADR-0041, ADR-0042 | documentation audit for stale runtime claims, source-reader map for `src/proflog`, and comment/docstring review of source-language-to-kernel layers | current-facing docs distinguish historical runtime boundaries from current evidence; every greenfield namespace is mapped for readers; [AAR-0043](aar/AAR-0043-greenfield-documentation-refresh.md) records the audit and verification |
| [ADR-0044](adr/ADR-0044-turing-completeness-demonstration.md) | completed | `adr-0044-turing-completeness` | Proflog-level implementation of a minimal Turing-complete two-counter Minsky machine model | ADR-0010, ADR-0038, ADR-0043 | generic interpreter clauses, concrete transfer and incrementer instruction tables, forward proof rows, frontend `pf/run` answer/partial-synthesis rows, no host-side step evaluator source audit, worked example/tutorial/README links | [AAR-0044](aar/AAR-0044-turing-completeness-demonstration.md) |
| [ADR-0045](adr/ADR-0045-minsky-trace-performance.md) | completed | `adr-0045-0046-tc-performance` | improve currently impractical Minsky two-counter trace evaluation while preserving kernel-facing semantics | ADR-0044 | red trace-performance test for a five-step transfer, trace-shaped formula construction, proof/answer evidence, source audit against host-side step evaluation | [AAR-0045](aar/AAR-0045-minsky-trace-performance.md) |
| [ADR-0046](adr/ADR-0046-combinatory-logic-turing-completeness.md) | completed | `adr-0045-0046-tc-performance` | add a second Turing-completeness demonstration using SKI combinatory logic | ADR-0010, ADR-0044 | red namespace test, SKI root-reduction tests, fully evaluating examples, answer-mode export, source audit, worked example | [AAR-0046](aar/AAR-0046-combinatory-logic-turing-completeness.md) |
| [ADR-0047](adr/ADR-0047-ski-quine-evaluation.md) | completed | `adr-0047-ski-quine` | evaluate the SKI self-reproducing omega term through contextual reduction | ADR-0046 | red quine evaluation test, isolated `full-step/2` contextual reduction relation, focused timing, worked-example update | [AAR-0047](aar/AAR-0047-ski-quine-evaluation.md) |
| [ADR-0048](adr/ADR-0048-robinson-q-proof-profiles.md) | completed | `adr-0048-robinson-q` | represent Robinson Q both as ordinary first-order assumptions and as an opt-in deduction-modulo proof profile | ADR-0004, ADR-0023, ADR-0043 | Q language/axiom helpers, ordinary Q-as-antecedent proofs, profiled Q conversion proofs, generic proof-profile dispatch tests, common performance comparison | [AAR-0048](aar/AAR-0048-robinson-q-proof-profiles.md) |
| [ADR-0049](adr/ADR-0049-robinson-q3-case-split-profile.md) | completed | `adr-0049-robinson-q3-profile` | close Robinson Q3 under the `:robinson-q` profile with a recorded predecessor-or-zero case-split rule | ADR-0048 | red Q3 profile proof test, ordinary Q3 control, proof evidence assertions, documentation update | [AAR-0049](aar/AAR-0049-robinson-q3-case-split-profile.md) |
| [ADR-0050](adr/ADR-0050-kernel-interleaved-robinson-q-theory.md) | completed | `adr-0050-kernel-q-theory` | refactor Robinson Q profile from query-time host preprocessing into kernel-interleaved relational theory rules | ADR-0048, ADR-0049 | proof-shape tests for kernel quantifier/disequality evidence, source audit against old host normalizer/recognizer, focused Q regression | [AAR-0050](aar/AAR-0050-kernel-interleaved-robinson-q-theory.md) |
| [ADR-0051](adr/ADR-0051-full-robinson-q3-theory-rule.md) | completed | `adr-0051-full-q3-rule` | add a full kernel-interleaved Q3 predecessor rule for larger deduction-modulo refutations | ADR-0048, ADR-0049, ADR-0050 | red add-one-predecessor theorem test, proof evidence for Q3 predecessor introduction plus Q4/Q5 conversion, focused Q regression and standard gates | [AAR-0051](aar/AAR-0051-full-robinson-q3-theory-rule.md) |
| [ADR-0052](adr/ADR-0052-unified-robinson-q3-theory-rule.md) | completed | `adr-0052-final-q3-deduction-modulo` | replace incremental Q3 closers with one unified predecessor-equality theory rule | ADR-0048, ADR-0049, ADR-0050, ADR-0051 | red contextual Q3 theorem, unified proof evidence, source audit against old Q3 closers, focused Q regression and standard gates | [AAR-0052](aar/AAR-0052-unified-robinson-q3-theory-rule.md) |
| [ADR-0053](adr/ADR-0053-robinson-q-theorem-examples.md) | completed | `adr-0053-q-theorem-examples` | promote three non-trivial Robinson Q theorem examples under both Q proof paths | ADR-0048, ADR-0052 | symbolic addition theorem, symbolic multiplication normal-form theorem, Q3 add-two successor theorem, proof evidence checks, timing probe rows | [AAR-0053](aar/AAR-0053-robinson-q-theorem-examples.md) |
| [ADR-0055](adr/ADR-0055-ski-relational-routing.md) | completed | `adr-0055-ski-relational-routing` | trace SKI test evaluation and require direct relational kernel/answer-overlay routes | ADR-0046, ADR-0047 | red route guard against public proof/answer wrappers, direct kernel proof helper, scheduled relational answer-overlay helper, focused timing update | [AAR-0055](aar/AAR-0055-ski-relational-routing.md) |
| [ADR-0056](adr/ADR-0056-greenfield-user-guide.md) | completed | `adr-0056-greenfield-user-guide` | compose an authoritative greenfield user guide from the tutorial, source map, code review, and worked examples | ADR-0010, ADR-0034, ADR-0043, ADR-0055 | documentation-only source/documentation review, README link, AAR, and whitespace verification | [AAR-0056](aar/AAR-0056-greenfield-user-guide.md) |
| [ADR-0057](adr/ADR-0057-relational-equality-fragment.md) | completed | `adr-0057-relational-equality-fragment` | experiment with an opt-in relation-backed equality-fragment profile, including relational closed-term gamma enumeration | ADR-0020, ADR-0039, ADR-0042, ADR-0055 | red route guards against the deterministic equality-fragment host engine and host gamma materialization; relational gamma enumeration tests; full finite-verifier parity rows | [AAR-0057](aar/AAR-0057-relational-equality-fragment.md) |

## Deferred Tracks

The following work is intentionally downstream of the baseline implementation and should not be folded into earlier ADRs by default:

- congruence-cache acceleration,
- bounded disunifier enumeration,
- arithmetic extensions beyond symbolic Peano coverage,
- any closed-world or Clark-completion semantic profile.

Frontier canonicalization and recursive nonground answer-mode descent have now
graduated from backlog into ADR-0013.
Generic evaluation of the still-unsatisfied `GV` / `FD` legacy families has now
graduated into ADR-0014.
Answer-mode extraction from the kernel into a separate overlay has now
graduated into ADR-0015.
Fair agenda scheduling and micro-step fuel have now graduated into ADR-0016.
Tabling and proof-state memoization have now graduated into ADR-0017.
Existential disequality witness evaluation has now graduated into ADR-0018.
Closed-term gamma instantiation has now graduated into ADR-0019.
Pure gamma candidate threading has now graduated into ADR-0020.
Closed-term gamma search regression repair has now graduated into ADR-0021.
Pelletier problem replication has now graduated into ADR-0022.
Profiled kernel layering has now graduated into completed ADR-0023.
Pelletier first-order performance closure has graduated into completed ADR-0024.
Pelletier lean first-order search policy has graduated into completed ADR-0025.
Kernel layer interoperation has graduated into completed ADR-0026.
Relational fuel purity has graduated into completed ADR-0029.
Relational constructor search control has graduated into completed ADR-0030.
List-family kernel generalization has graduated into accepted ADR-0031.
Structural answer-variable recursion has graduated into accepted ADR-0033.
The greenfield implementation tutorial reference has graduated into accepted
ADR-0034.
Relational residual continuation has graduated into completed ADR-0035.
Speculative relational arithmetic and tabling reassessment has graduated into
completed ADR-0036.
Core.logic miniKanren enhancement work has graduated into completed ADR-0037.
Deep Fitting-program kernel evaluation has graduated into completed ADR-0038.
Kernel-level finite equality verification has graduated into completed ADR-0039.
Legacy subsumption parity gates have graduated into completed ADR-0040.
Relational constructor-recursive profile promotion has graduated into completed
ADR-0041.
Equality-fragment status consistency has graduated into completed ADR-0042.
The Turing-completeness demonstration has graduated into completed ADR-0044.
Minsky trace performance and the second TC demonstration have graduated into
completed ADR-0045 and ADR-0046.
Robinson Q proof-profile work has graduated into completed ADR-0048.
Robinson Q3 case-split profile work has graduated into completed ADR-0049.
Kernel-interleaved Robinson Q theory-rule work has graduated into completed
ADR-0050.
Full Robinson Q3 predecessor-rule work has graduated into completed ADR-0051.
Unified Robinson Q3 predecessor-equality work has graduated into completed
ADR-0052.
Robinson Q theorem examples have graduated into completed ADR-0053.
The relational equality-fragment experiment has graduated into completed
ADR-0057.
The non-vacuous Willard SJAS self-justification demonstration has graduated
into completed ADR-0062.

## ADR-0007 Task List

- Strengthen greenfield Nim coverage beyond the single `win(3)` regression.
- Restore the L-ground call boundary for plain procedure calls.
- Preserve branch-local equality information strong enough to rewrite a walked
  `par` argument back into the object language before a call is attempted.
- Replace the current force-stop timeout behavior with bounded helpers that
  return control reliably.
- Keep semantic Nim coverage on direct success/failure proof checks instead of
  treating bounded query races as the semantic authority.
- Correct the ADR trail so ADR-0006 does not build on an overstated ADR-0005
  completion claim.

Each deferred track should become its own ADR if it graduates from backlog to active work.

## Merge Gate For Each ADR

- ADR status is updated from `proposed` to `accepted` before implementation starts.
- Failing tests for the ADR exist before production code lands.
- The relevant greenfield test namespaces pass.
- Existing regression suites needed for confidence still pass.
- The ADR has either a completed AAR or an explicit note that the AAR will be written immediately after merge because data collection is still pending.

## Working Loop

1. Start from the next accepted ADR in dependency order.
2. Write the narrowest failing tests that express the ADR success criteria.
3. Implement only enough code to make those tests pass, and do so substantively: do not satisfy tests by bypassing, defrauding, hard-coding around, or otherwise failing to implement the feature the tests are meant to capture.
4. Run the targeted greenfield tests and any necessary regression selectors.
5. Update the ADR, write or update the AAR, then merge back into `main`.
