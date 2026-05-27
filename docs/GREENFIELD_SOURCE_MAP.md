# Greenfield Source Map

Date: 2026-05-09
Related ADRs:
[ADR-0043](adr/ADR-0043-greenfield-documentation-refresh.md),
[ADR-0044](adr/ADR-0044-turing-completeness-demonstration.md),
[ADR-0048](adr/ADR-0048-robinson-q-proof-profiles.md),
[ADR-0049](adr/ADR-0049-robinson-q3-case-split-profile.md),
[ADR-0050](adr/ADR-0050-kernel-interleaved-robinson-q-theory.md),
[ADR-0051](adr/ADR-0051-full-robinson-q3-theory-rule.md),
[ADR-0052](adr/ADR-0052-unified-robinson-q3-theory-rule.md),
[ADR-0053](adr/ADR-0053-robinson-q-theorem-examples.md),
[ADR-0054](adr/ADR-0054-robinson-q-prime-evenness.md),
[ADR-0056](adr/ADR-0056-greenfield-user-guide.md)

This map is the current reader path for `src/proflog`. It is intentionally more
mechanical than the tutorial: every greenfield namespace is listed so a reader
can tell whether a file is part of the source language, the proof kernel, the
query/answer surface, a promoted verifier/profile, or an ADR probe.

For user-facing guidance that turns this inventory into normal workflows, see
[Proflog Greenfield User Guide](USER_GUIDE.md).

The core implementation flow is:

```text
AST and declarations
  -> source validation and compilation
  -> NNF and substitution
  -> compiled-program lookup
  -> kernel support relations and equality state
  -> full proof kernel and profiled proof layers
  -> query status
  -> answer overlay and answer records
  -> catalogs, examples, diagnostics, and probes
```

## Source Language And Compilation

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.frontend` | Prefix Proflog frontend and helper-inlining translator. | ADR-0010 surface layer: reusable frontend languages, visible `(|- ...)` relation clauses, `(:= ...)` nonrecursive inline helpers, closed query formula translation through `q`, open answer evaluation through `run`, lower-level answer query construction through `answer-query`, and descent to `proflog.ast` / `proflog.language`. |
| `proflog.ast` | Tagged object-language terms, formulas, clauses, and nominal helpers. | This is the data contract shared by the compiler, kernel, examples, and tests. Tagged lists make formulas recursively decomposable inside miniKanren goals. |
| `proflog.language` | Language declarations, validation, alpha-renaming, and source-to-core compilation. | This is where Fitting's one-clause-per-relation core shape is recovered from ergonomic surface clauses by grouping, normalizing, and precomputing each relation body's NNF negation. |
| `proflog.normalize` | Negation normal form and formula negation. | Procedure-call negation depends on this layer: negative calls use a compiled clause's precomputed NNF negated body rather than a host-side `not` wrapper. |
| `proflog.subst` | Pure compile-time substitution and relational proof-time substitution. | The project learned that proof-time substitution must be structural and relational; projected substitution created broad reverse/partial-mode failures. |
| `proflog.pretty` | Host-side presentation helpers. | These helpers are not semantic. They format terms and answers for examples and diagnostics. |

## Program Calls And Shared Proof State

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.program` | Relational compiled-clause lookup and formal/actual argument binding. | This namespace is the narrow implementation of Fitting's Procedure Call Rule interface: find the relation clause and create the call environment. |
| `proflog.kernel-support` | Shared branch-state relations. | The kernel and answer overlay both use this file for complementary closure, L-groundness, saved disequality maintenance, proof-variable discipline, and fuel stepping. Keeping these relations shared prevents semantic drift between proof and answer paths. |
| `proflog.equality` | Free-constructor equality and disequality relations. | This is Fitting's weak Herbrand equality theory operationalized as explicit equality substitution plus delayed disequality checks. |
| `proflog.gamma` | Bounded closed-term candidate generation. | Gamma instantiation is kept readable in the kernel; this namespace owns the finite operational candidate schedule. |
| `proflog.proof` | Proof-term inspection helpers. | Proofs are plain tagged lists. These helpers let tests assert layer boundaries without hiding proof search behind opaque objects. |

## Proof Kernels

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.kernel` | Full Fitting-style tableau proof kernel. | This is the authoritative program-aware proof relation. It handles connectives, quantifiers, equality, disequality, saved literals, procedure calls, fuel, and proof terms. |
| `proflog.proof-profile` | Language-selected proof-profile dispatch. | ADR-0048 layer that routes ordinary languages to `proflog.kernel` and lets theory profiles such as `:robinson-q` bind profile-specific kernel rules. |
| `proflog.formula-profile` | Structural profile classification. | Profiles decide when a weaker proof layer is sufficient. They should fail conservatively and leave the full kernel in control. |
| `proflog.kernel.propositional` | Pure propositional proof layer. | Used for call-free propositional branches and Pelletier-style theorem fragments where equality and procedure calls are absent. |
| `proflog.kernel.first-order` | Equality-free first-order proof layer. | Provides the lean first-order search policy used for Pelletier closure while preserving the full kernel for equality and Proflog programs. |
| `proflog.kernel.equality-fragment` | Finite equality-fragment verifier profile. | ADR-39/42 layer for call-free equality-heavy finite verifier bodies such as group associativity and transition-system laws. It is generic over equality formulas, not over family names. |
| `proflog.kernel.robinson-q-profile` | Deduction-modulo and Q3 profile for Robinson Q. | Preserves the generic equality-fragment sidecar when it can close first, then binds a miniKanren theory rule into `kernel/close-agendao`; it normalizes visible `add` and `mul` terms by Q conversion rules, stores exposed nonzero disequalities for Q3, and closes active disequalities with the unified `q3-predecessor-equality` rule when one proof-local universal predecessor makes the Q-normalized sides identical. Q3 is intentionally not a rewrite. |
| `proflog.kernel.constructor-recursive` | Diagnostic constructor-recursive proof layer. | This older sidecar consumes guarded-clause IR to prove/resettle constructor-recursive residuals. It remains useful as a diagnostic oracle and comparison point. |
| `proflog.kernel.constructor-recursive-profile` | Promoted constructor-recursive answer profile. | ADR-41 profile that emits integrated `profiled constructor-recursive` proof records through the answer-overlay record shape instead of using the old diagnostic sidecar API. |
| `proflog.equality-fast-path` | Host-side equality-only acceleration. | A restricted hard-family helper for existential equality/disequality conjunctions. It is an overlay, not the authoritative kernel semantics. |
| `proflog.hard-family-overlay` | Named non-default hard-family status overlay. | Retained for compatibility and historical comparisons; promoted GV/transition rows now have proof-producing equality-fragment coverage instead. |
| `proflog.tabling` | Canonical proof-state tabling wrapper. | Kept separate so the ordinary kernel remains a readable Fitting-style relation. |

## Query And Answer Surfaces

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.query` | Public success/failure/status helpers. | Implements the Proflog query boundary: success is proof of the negated query, failure is proof of the query, and bounded search can remain unresolved. |
| `proflog.answer-overlay` | Answer-mode tableau overlay. | Runs the same proof machinery with answer variables, residual procedure calls, call-depth budgeting, and ADR-35 structural residual continuation. |
| `proflog.answers` | Public answer records, export, ranking, diagnostics, parity mode. | This is where proof states become user-visible bindings and residuals. It also contains explicit parity/materialization modes that must not be confused with raw kernel evidence. |

## Program Catalogs And Promoted Examples

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.fitting-programs` | ADR-38/39 catalog of Fitting-style programs and verifier examples. | Builds public AST/source clauses and evaluates them through proof-backed query surfaces for tutorial and regression use. |
| `proflog.finite-transition-systems` | Non-GV finite verifier examples. | Shows the equality-fragment profile is generic by verifying transition-table totality and determinism laws. |
| `proflog.turing-completeness` | ADR-44/45 two-counter Minsky machine demonstration. | Defines a reusable frontend language, generic Proflog interpreter clauses, transfer/incrementer instruction tables, trace-shaped proof formulas, and term helpers. It does not evaluate machine steps on the host. |
| `proflog.combinatory-logic` | ADR-46/47 SKI combinatory-logic demonstration. | Defines SKI reduction, isolated full-context reduction for the omega quine trace, and bounded evaluation as frontend Proflog clauses. Host helpers construct terms only; they do not reduce SKI expressions. |
| `proflog.robinson-q` | ADR-0048/0054 Robinson arithmetic Q formulas and term helpers. | Exposes Q's function-only language, Q1-Q7 formulas, Q3-dependent predecessor theorems, promoted symbolic conversion theorems, corrected inline prime/evenness formulas, ordinary Q-as-antecedent helpers, and profiled empty programs for tests and examples. |
| `proflog.robinson-q-probe` | Robinson Q timing comparison probe. | Reproducible CLI probe for the common ordinary-vs-profiled Robinson Q formula set, including ADR-0054's passing Q-as-antecedent prime rows and documented theorem-only profile boundary; not a semantic gate beyond the promoted tests. |
| `proflog.turing-completeness-long-probe` | ADR-44 long runtime-boundary probes. | Reproducible CLI-only diagnostics for slow recursive and reverse two-counter machine queries. These probes are not tests and are not part of default semantics. |
| `proflog.gv-probe` | Legacy group-verifier reconstruction probes. | Historical and exploratory GV probe surface. Current promoted GV proof evidence lives in the equality-fragment test/catalog path. |
| `proflog.list-kernel-matrix-probe` | Raw-kernel append/reverse capability matrix. | Bypasses public answer conveniences to ask what the raw kernel/export path can eventually surface. |
| `proflog.legacy-stream-probe` | Raw-stream probes for legacy-style open list queries. | Used to classify at which layer a difficult legacy answer first appears. |

## ADR-36/37 Arithmetic, Constraint, And Host Probes

| Namespace | Role | Reader note |
|---|---|---|
| `proflog.relational-arithmetic` | Faster-minikanren bit-list arithmetic translation. | Production fuel still uses finite-domain host integers; this relation remains an opt-in arithmetic/probe foundation. |
| `proflog.relational-fuel-probe` | Experimental bit-list fuel stepping. | Demonstrates a pure relational fuel representation but does not change public fuel semantics. |
| `proflog.relational-fuel-adapter-probe` | Host-integer boundary adapter for relational fuel. | Preserves ground host integers at entry but exposes the semantic boundary for open/reverse fuel synthesis. |
| `proflog.relational-fuel-performance-probe` | FD-vs-relational fuel timing harness. | Process-local comparison harness only. |
| `proflog.fd-fuel-synthesis-probe` | Focused finite-domain fuel synthesis probes. | Records current production fuel behavior in reverse/partial modes. |
| `proflog.minikanren-constraints` | Project-local symbolic constraint overlay. | Compatibility layer for constraint experiments; production semantics only adopt pieces through later ADRs. |
| `proflog.l-ground-constraint-probe` | L-groundness-as-constraint probe. | Negative-control and experimental constraints for rejecting proof parameters in object-language terms. |
| `proflog.relational-maps-probe` | Map-like relational data-structure probe. | Documents why ordinary Clojure maps do not provide open-map relational behavior. |
| `proflog.core-logic-disequality-probe` | Disequality maintenance measurement harness. | Measures core.logic and Proflog disequality shapes without patching production relations. |
| `proflog.core-logic-count-probe` | Core.logic call-count measurement harness. | Process-local instrumentation for selected hard list-kernel rows. |
| `proflog.core-logic-tabling-probe` | Core.logic tabling/reification measurement harness. | Records why raw `core.logic/tabled` was not promoted as a production replacement. |
| `proflog.core-logic-host` | Runtime core.logic artifact reporting. | Helps distinguish source/dependency deployment facts from semantic regressions. |
| `proflog.core-logic-host-probe` | CLI wrapper for host reporting. | Thin process entrypoint for `proflog.core-logic-host`. |
