# Proflog Greenfield User Guide

Date: 2026-05-09
Related ADRs:
[ADR-0010](adr/ADR-0010-frontend-inlining-translation.md),
[ADR-0034](adr/ADR-0034-greenfield-implementation-tutorial.md),
[ADR-0043](adr/ADR-0043-greenfield-documentation-refresh.md),
[ADR-0055](adr/ADR-0055-ski-relational-routing.md),
[ADR-0056](adr/ADR-0056-greenfield-user-guide.md)

This is the authoritative user guide for the current greenfield Proflog
implementation. It explains how to write programs, how those programs descend
to the kernel's formula representation, which APIs to call, which profiles and
examples exist, and where the present operational boundaries are.

For implementation archaeology, read this guide with
[Greenfield Implementation Tutorial and Reference](GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
and [Greenfield Source Map](GREENFIELD_SOURCE_MAP.md). For concrete traces and
test results, read [worked examples](../worked-examples/README.md).

## 1. What Proflog Is

Proflog is a tableau-based logic programming language based on Melvin Fitting's
Proflog. A Proflog program defines relation symbols by first-order formulas.
Evaluation is theorem proving:

- a query succeeds when the prover closes a tableau for the negated query;
- a query fails when the prover closes a tableau for the query itself;
- a bounded query may remain unresolved when neither semidecision path has
  found a proof inside the current operational budget.

The greenfield implementation is written in Clojure and core.logic. Its normal
data path is:

```text
Fitting-style program idea
  -> Clojure-readable prefix frontend
  -> tagged backend AST
  -> validated and compiled program
  -> query formula
  -> proof kernel or selected proof profile
  -> query status, proof terms, or answer records
```

The root implementation namespace is `src/proflog`. The legacy `cljtap`
namespaces remain reference material and experimental prior art, not the
authority for the greenfield implementation.

## 2. Installing And Running

Prerequisites:

- a working JDK;
- Leiningen.

Run the routine greenfield regression suite:

```text
lein test-proflog-fast
```

Run the slower recursive, reverse, and partial-synthesis suite:

```text
lein test-proflog-extended
```

Start a REPL:

```text
lein repl
```

At the REPL, require the normal user namespaces:

```clojure
(require '[proflog.frontend :as pf]
         '[proflog.query :as query]
         '[proflog.answers :as answers]
         '[proflog.pretty :as pretty])
```

Use `proflog.frontend` for ordinary program authoring, `proflog.query` for
closed success/failure/status queries, and `proflog.answers` or `pf/run` for
answer queries.

## 3. Main User Vocabulary

The implementation uses a small fixed vocabulary.

| Word | Meaning |
|---|---|
| Language | The first-order signature: constants, functions, relations, and optional proof profile metadata. |
| Term | An object-language value such as `zero`, `(s zero)`, or `(cons a null)`. |
| Atom | A relation application such as `(p a)` or `(append xs ys zs)`. |
| Literal | A positive atom, negative atom, equality, or disequality. |
| Formula | A literal, Boolean combination, implication, or quantified formula. |
| Clause | A relation definition: `p(x) :- body`. |
| Helper definition | A frontend-only abbreviation: `h(x) := body`; it is inlined before compilation. |
| Program | A compiled set of relation clauses over one language. |
| Query | A formula evaluated relative to a program. |
| Proof term | A tagged list recording how a tableau closed. |
| Answer record | A map containing exported bindings, residual formulas, and proof terms. |
| Residual | A symbolic obligation left after answer-mode search stops recursive descent. |
| Fuel | A bounded proof-search micro-step budget; `nil` means unbounded in direct proof calls. |
| Profile | A proof component selected either structurally or by language metadata. |

## 4. Writing Programs

Programs should first be understandable as Fitting-style logic programs. The
guide uses that notation to explain intent:

```prolog
p(x) :- x = a.

only-zero(x) := forall y. (x != y or y = zero).
zero-only(x) :- only-zero(x).
```

There are two clause operators:

- `:-` introduces a real relation. Calls to that relation are evaluated by
  Fitting's Procedure Call Rule.
- `:=` introduces a frontend helper. It is inlined before the backend compiler
  sees the program; it is not a runtime relation.

The current implemented frontend is Clojure-readable prefix syntax:

```clojure
(def p-language
  (pf/language
    (constants a b)
    (relations (p 1))))

(def p-program
  (pf/proflog p-language
    (|- (p x)
        (= x a))))
```

The wrapper is thin. `pf/language` builds a reusable backend language value.
`pf/proflog` translates clauses and calls `proflog.language/compile-program`.
It does not evaluate the program.

### Language Declarations

A language declaration has four sections:

```clojure
(pf/language
  (constants zero a b)
  (functions (s 1) (cons 2))
  (relations (p 1) (append 3))
  (proof-profile :robinson-q))
```

`constants` are zero-arity function symbols. They are also recorded separately
so bounded Herbrand enumeration and examples can list them in declaration
order.

`functions` declare term constructors and arities. Function symbols live in the
term namespace.

`relations` declare predicate symbols and arities. Relation symbols live in a
separate namespace from term constructors.

`proof-profile` is optional. Most programs omit it and use the default kernel.
Robinson Q programs use `(proof-profile :robinson-q)`.

Validation rejects:

- constants also declared as functions;
- term symbols also declared as relations;
- undeclared function or relation symbols;
- arity mismatches;
- internal `(par ...)` parameter terms in surface programs.

### Clause Heads

Frontend relation and helper heads must contain variables only:

```clojure
(|- (p x) ...)
(:= (helper x y) ...)
```

Constructor patterns are written in the body:

```prolog
parent(s(x)) :- ancestor(x).
```

is written in the current frontend style as:

```clojure
(|- (parent value)
    (exists [x]
      (and (= value (s x))
           (ancestor x))))
```

This restriction keeps the backend relation representation simple: every
compiled relation has a parameter vector, and pattern information appears as
ordinary equality formulas in the body.

Multiple `|-` clauses for the same relation are allowed. The compiler
alpha-renames their parameters, normalizes their bodies to NNF, and combines
the alternatives into one Fitting-style core clause by disjunction. The
compiled program also keeps top-level alternatives and guarded alternatives for
answer diagnostics and constructor-recursive profiles.

### Formula Syntax

Frontend formulas use prefix operators:

| Proflog idea | Frontend form |
|---|---|
| equality | `(= left right)` |
| disequality | `(!= left right)` |
| conjunction | `(and f1 f2 ...)` |
| disjunction | `(or f1 f2 ...)` |
| negation | `(not f)` |
| implication | `(implies antecedent consequent)` |
| universal | `(forall [x y] body)` |
| existential | `(exists [x y] body)` |
| relation call | `(p arg1 arg2)` |

Terms are also prefix:

```clojure
zero
(s zero)
(cons a null)
(cfg l0 zero (s zero))
```

An unbound symbol in term position is a constant. A symbol bound by a clause
head, quantifier, or `pf/run` answer vector is an object-language variable.

### Helper Definitions

Use `:=` for formula abbreviations that should not become runtime procedure
calls:

```clojure
(def zero-only-program
  (pf/proflog peano-language
    (:= (only-zero x)
      (forall [y]
        (or (!= x y)
            (= y zero))))

    (|- (zero-only x)
        (only-zero x))))
```

Helpers are expanded at macro expansion time. They may call other helpers, but
direct or mutual recursive helper expansion is rejected. Use `|-` for recursive
relations.

## 5. Closed Queries

Use `pf/q` to translate one closed frontend query to a backend formula:

```clojure
(query/query-status p-program (pf/q (p a)))
;; => :succeeds

(query/query-status p-program (pf/q (p b)))
;; => :fails
```

The status API is:

```clojure
(query/query-status program query)
(query/query-status program query {:timeout-ms 2000
                                   :proof-limit 1
                                   :poll-ms 5})
```

It returns:

| Status | Meaning |
|---|---|
| `:succeeds` | A proof closed the tableau for the negated query. |
| `:fails` | A proof closed the tableau for the query itself. |
| `:unresolved` | The bounded search found neither proof. |
| `:inconsistent` | Both success and failure proofs were found in the same bounded probe. |

Use direct semidecision helpers when you need proof terms or explicit fuel:

```clojure
(query/query-succeeds p-program (pf/q (p a)) 1 8)
(query/query-fails p-program (pf/q (p b)) 1 8)
```

The parameters are `program`, `query`, proof limit, and fuel. A fuel value of
`nil` means unbounded search for direct proof calls. Public fuel is currently
either `nil` or a host integer.

## 6. Open Answer Queries

Use `pf/run` for ordinary open answer queries:

```clojure
(pf/run p-program [x]
  (p x)
  {:proof-limit 1})
```

`pf/run` mirrors miniKanren's answer-variable binder: variables named in the
vector are object-language variables and may be exported in answer records. It
is not a separate evaluator. It expands to a backend query formula, a vector of
answer-variable noms, and a call to `answers/query-answers`.

Answer records have this shape:

```clojure
{:bindings [[x (app a)]]
 :residuals []
 :proofs [...]}
```

`bindings` are ordered `[nom term]` pairs for the requested answer variables.
`residuals` are formulas that remain as symbolic obligations. `proofs` are
proof terms for the route that produced the answer.

Important answer options:

| Option | Meaning |
|---|---|
| `:proof-limit` | Maximum number of public answer records requested. |
| `:fuel` | Kernel micro-step fuel for each raw proof slice. |
| `:call-depth` | Recursive answer-mode descent budget below the query entry. |
| `:max-raw-proof-limit` | Internal raw proof stream cap before export/merge. |
| `:residual-continuation-fuel` | Fuel for the structural residual scheduler. |

For diagnostics or alternate profiled answer paths, use `pf/answer-query`:

```clojure
(let [{:keys [query answer-vars]}
      (pf/answer-query [x] (p x))]
  (answers/query-answers p-program query answer-vars {:proof-limit 1}))
```

Lower-level answer APIs:

| Function | Use |
|---|---|
| `answers/query-answers` | Generic symbolic answers relative to a program. |
| `answers/formula-answers` | Answers for a formula over a language, without procedure calls. |
| `answers/query-answer-diagnostics` | Raw proof/export growth for one call-depth stage. |
| `answers/query-stage-diagnostics` | Productivity across call-depth stages. |
| `answers/query-parity-answers` | Separate closed-answer parity/materialization mode. |
| `answers/query-ground-answers` | Bounded Herbrand enumeration above the semantic kernel. |

The generic answer path may return symbolic residuals. A residual is not an
error. It is the current proof frontier made explicit.

## 7. How Source Descends To The Kernel

The backend AST uses tagged lists.

Terms:

```clojure
(var x)
(par p)
(app zero)
(app s (app zero))
(app cons (app a) (app null))
```

Formulas:

```clojure
(pos (app p (app a)))
(neg (app p (app a)))
(eq (var x) (app a))
(neq (var x) (app b))
(and f g)
(or f g)
(forall (tie x body))
(exists (tie x body))
```

The public constructors live in `proflog.ast`:

```clojure
(require '[proflog.ast :as ast]
         '[proflog.language :as language])

(def p-lang
  (language/language
    {:constants ['a 'b]
     :relations {'p 1}}))

(def p-program-backend
  (ast/nom x
    (language/compile-program
      p-lang
      [(ast/clause
         'p
         [x]
         (ast/eq-lit (ast/var-term x)
                     (ast/app-term 'a)))])))
```

The compiled relation is schematically:

```clojure
{:relation p
 :params [x]
 :body (eq (var x) (app a))
 :negated-body (neq (var x) (app a))}
```

When a query such as `(pf/q (p a))` reaches the query layer, it has become:

```clojure
(pos (app p (app a)))
```

`query/query-succeeds` validates that formula and calls the selected proof
profile with the negated query. Under the default profile, that becomes:

```clojure
(kernel/prove-program
  p-program
  (neg (app p (app a)))
  1
  fuel)
```

`query/query-fails` calls the same selected proof layer with the original
query. Under the default profile:

```clojure
(kernel/prove-program
  p-program
  (pos (app p (app a)))
  1
  fuel)
```

`kernel/prove-program` receives four public parameters:

| Parameter | Meaning |
|---|---|
| `prog` | The compiled program, including `:language`, `:clause-list`, `:alternative-clause-list`, and `:guarded-clause-list`. |
| `fml` | The formula whose tableau should close. |
| `n` | Number of proof terms requested. |
| `fuel` | Bounded micro-step fuel, or `nil` for unbounded direct proof search. |

## 8. The Kernel Semantics

The ordinary kernel lives in `proflog.kernel`. It is a tableau branch-closing
relation over explicit state:

| State field | Meaning |
|---|---|
| `agenda` / `unexpanded` | Pending formulas on the branch. |
| `lits` | Saved positive and negative atoms. |
| `env` | Lexical substitution for quantified binders and relation parameters. |
| `proof-vars` | Gamma-introduced proof variables that may be bound during proof. |
| `sigma` | Explicit equality substitution. |
| `neqs` | Delayed disequality pairs. |
| `prog` | Compiled program used for procedure calls. |
| `gamma-terms` | Finite closed-term candidates for selected universal instantiation. |
| `fuel` | Bounded micro-step budget. |
| `proof` | Tagged proof term explaining closure. |

The kernel expands formulas in negation normal form:

- conjunction is an alpha rule: both conjuncts stay on the same branch;
- disjunction is a beta rule: both sibling branches must close;
- `forall` is a gamma rule: instantiate with a fresh proof variable, and in
  call-free cases also try bounded closed terms;
- `exists` is a delta rule: introduce a rigid parameter;
- positive equality extends `sigma` or closes on contradiction;
- disequality stores a symbolic obligation or closes when equality makes it
  impossible;
- positive and negative atoms either close against complementary saved atoms,
  enter a procedure call, or are saved for later equality-triggered closure.

Procedure calls are relational. `proflog.program` looks up the compiled clause
with `membero`, binds formal parameters to actual arguments, and opens a
subsidiary tableau for the clause body or its precomputed negated body.

Procedure calls require `L`-ground arguments: object-language variables and
constructor applications are allowed, but unresolved internal parameters are
not. That condition is implemented structurally in `proflog.kernel-support`.

## 9. Equality And Quantifiers

Equality is free-constructor equality. It is implemented in `proflog.equality`
as explicit substitution plus delayed disequality state.

Useful facts:

- `(var nom)` is an object-language variable, often introduced by gamma or
  answer binding.
- `(par nom)` is a rigid internal parameter, usually introduced by delta.
- Positive equality may bind proof variables and parameters when sound.
- Occurs checks prevent cyclic bindings.
- Disequality may be stored until later equality makes it close.
- Complementary literals can close after their arguments walk through `sigma`.

Universal quantifiers are operationally expensive because they may need repeated
instantiation. The kernel first tries fresh proof variables and only uses
bounded closed Herbrand terms in guarded call-free contexts. The finite
candidate policy lives in `proflog.gamma`.

Existential quantifiers introduce rigid parameters in ordinary proof mode.
Answer mode can treat existentials as exportable variables when partial and
reverse answer queries need symbolic witnesses.

## 10. Proof Profiles

The default profile is the ordinary program-aware kernel. Profiles are
proof-producing layers that preserve the proof boundary but use more specialized
search when a formula fragment permits it.

| Profile/layer | How selected | Namespace | Use |
|---|---|---|---|
| Default kernel | Ordinary program language | `proflog.kernel` | Full Proflog with equality, quantifiers, and procedure calls. |
| Propositional | Structural branch handoff | `proflog.kernel.propositional` | Call-free propositional residual branches. |
| First-order | Structural branch handoff or theorem call | `proflog.kernel.first-order` | Equality-free first-order theorem fragments and Pelletier examples. |
| Equality fragment | `kernel/prove-program` fallback before ordinary kernel | `proflog.kernel.equality-fragment` | Finite call-free equality verifier formulas, including group and transition examples. |
| Robinson Q | `(proof-profile :robinson-q)` | `proflog.kernel.robinson-q-profile` | Deduction-modulo-style Q conversion and Q3 predecessor-equality closure. |
| Willard SJAS | Generated by `proflog.willard-sjas/system` | `proflog.kernel.willard-sjas-profile` | Binary U-grounding arithmetic, generated axiom codes, Group-3, reflected clause boundary, and kernel-checked certificates. |
| Constructor-recursive profile | Explicit answer-profile API | `proflog.kernel.constructor-recursive-profile` | Guarded recursive answer rows such as Peano/list partial synthesis. |
| Tabled kernel | Explicit tabled API | `proflog.tabling` | Experimental canonical proof-state reuse. |

The equality-fragment layer contains the production deterministic finite proof
engine and ADR-0057's opt-in relation-backed finite route. The production
engine remains the default because it is faster on several rows. The
ADR-0057 route emits `profiled relational-equality-fragment` evidence, uses
relational gamma generation and relation-backed equality/disequality checks,
and covers the same promoted finite verifier rows without calling the
production host equality engine.

The Robinson Q profile binds theory rules into the ordinary kernel rather than
preprocessing formulas. It normalizes `add` and `mul` terms by Q4-Q7 conversion,
stores visible nonzero premises for Q3, and closes active disequalities when a
proof-local predecessor makes the normalized sides equal. Q3 is not a general
rewrite rule.

The Willard SJAS profile is built with `proflog.willard-sjas/system-source` or the
lower-level `proflog.willard-sjas/system`, not with a raw `(proof-profile ...)`
language declaration. The source builder accepts `language`, `beta`,
`reflected`, and `external` sections in Clojure-readable prefix style. Both
builders generate a finite reflected axiom basis, stable formula codes,
`axiom-member` facts, and Group-3. The object numerals are `0` and `1`; larger
numbers are composed with `dbl` and `add`. U-grounding arithmetic and
`tableau-proof/3`, `subst-code/2`, and `subst-prf/4` are handled by the SJAS
profile rather than by finite `mult/3` tables or miniature certificate predicates.
`contradiction-code` maps to the concrete theorem target for `false`, and the
Level-1 profile generates a `SelfCons` skeleton code used by the fixed-point
substitution entry. Reflected user clauses are part of the self-referenced
system; external clauses are ordinary Proflog code outside that `SelfCons`
claim.

## 11. Worked Program Families

The current worked examples are the best way to see non-trivial use cases.

| Family | File | What it demonstrates |
|---|---|---|
| Query behavior | [query-and-program-behavior.md](../worked-examples/query-and-program-behavior.md) | Success, failure, unresolved status. |
| Program calls | [program-calls.md](../worked-examples/program-calls.md) | Positive and negative procedure calls. |
| Quantified programs | [quantified-programs.md](../worked-examples/quantified-programs.md) | Quantified clause bodies and frontend inlining. |
| Answers | [answers-api.md](../worked-examples/answers-api.md) | Bindings, residuals, diagnostics. |
| Lists | [list-programs.md](../worked-examples/list-programs.md) | Recursive `append`, `reverse`, answer export, partial synthesis. |
| Fitting programs | [fitting-programs.md](../worked-examples/fitting-programs.md) | P1, P2, move warning, finite-domain programs, list rows, group verifier rows. |
| Finite verifiers | [kernel-finite-verifiers.md](../worked-examples/kernel-finite-verifiers.md) | Equality-fragment finite verification and refutation. |
| Turing completeness | [turing-completeness.md](../worked-examples/turing-completeness.md) | Two-counter Minsky interpreter written as Proflog clauses. |
| SKI combinatory logic | [combinatory-logic.md](../worked-examples/combinatory-logic.md) | SKI reduction, answer export, guided omega quine trace. |
| Robinson Q | [robinson-q.md](../worked-examples/robinson-q.md) | Q as assumptions and as a `:robinson-q` profile. |
| Willard SJAS | [willard-sjas.md](../worked-examples/willard-sjas.md) | Binary U-grounding arithmetic, generated SJAS basis, reflected/external clause boundary, formula classes, certificate checking. |
| Pelletier | [pelletier-problems.md](../worked-examples/pelletier-problems.md) | Theorem-proving benchmark fragments. |
| Constructor-recursive | [constructor-recursive-profile.md](../worked-examples/constructor-recursive-profile.md) | Guarded recursive reverse and partial synthesis. |

Run focused gates for those families:

```text
lein test-proflog-fitting-programs
lein test-proflog-kernel-finite-verifiers
lein test-proflog-turing-completeness
lein test-proflog-combinatory-logic
lein test-proflog-robinson-q
lein test-proflog-sjas
lein test-proflog-constructor-recursive
```

The Turing-completeness and SKI suites are intentionally opt-in slow suites.
They prove finite symbolic runs through the kernel; they are not intended to be
efficient machine interpreters.

## 12. Test Suites And Runtime Records

Routine gates:

```text
lein test-proflog-fast
lein test-proflog-extended
```

Other promoted gates:

```text
lein test-proflog-fitting-programs
lein test-proflog-turing-completeness
lein test-proflog-robinson-q
lein test-proflog-kernel-finite-verifiers
lein test-proflog-legacy-subsumption
lein test-proflog-constructor-recursive
```

Probe commands are reproducibility aids, not semantic gates:

```text
lein probe-proflog-list-kernel-matrix
lein probe-proflog-gv
lein probe-proflog-turing-completeness
lein probe-proflog-robinson-q
lein probe-core-logic-host
lein probe-core-logic-tabling
lein probe-core-logic-count
lein probe-relational-fuel-performance
```

Passing runtimes and known slow boundaries are recorded in
[Test Runtime Baseline](TEST_RUNTIME_BASELINE.md). Use that file when deciding
whether to run a slow proof family locally.

## 13. Source Inventory

This table identifies every current `src/proflog` namespace and its user status.
Use [Greenfield Source Map](GREENFIELD_SOURCE_MAP.md) for the more mechanical
reader map.

| Namespace | Status | Role |
|---|---|---|
| `proflog.frontend` | public | Prefix frontend: languages, programs, `q`, `answer-query`, `run`. |
| `proflog.ast` | public/backend | Tagged terms, formulas, clauses, nominal helper. |
| `proflog.language` | public/backend | Signature validation and program compilation. |
| `proflog.normalize` | backend | NNF conversion and formula negation. |
| `proflog.subst` | backend | Pure and relational substitution. |
| `proflog.pretty` | public/presentation | Display-oriented term, formula, and answer rendering. |
| `proflog.program` | kernel internal | Relational compiled-clause lookup and argument binding. |
| `proflog.kernel` | backend/diagnostic public | Ordinary proof kernel and direct proof wrappers. |
| `proflog.kernel-support` | kernel internal | Shared closure, L-groundness, fuel, disequality, proof-variable discipline. |
| `proflog.equality` | kernel internal | Free-constructor equality and disequality relations. |
| `proflog.gamma` | kernel internal | Bounded closed-term candidates for gamma instantiation. |
| `proflog.proof` | public/diagnostic | Proof-term inspection helpers. |
| `proflog.query` | public | Success, failure, bounded status, and proof probing. |
| `proflog.answer-overlay` | backend | Answer-mode tableau overlay and residual scheduling. |
| `proflog.answers` | public | Answer records, symbolic export, diagnostics, parity and ground modes. |
| `proflog.proof-profile` | backend extension point | Language-selected proof-profile dispatch. |
| `proflog.formula-profile` | backend | Structural profile classification. |
| `proflog.kernel.propositional` | profile | Propositional proof component. |
| `proflog.kernel.first-order` | profile | Equality-free first-order proof component. |
| `proflog.kernel.equality-fragment` | profile | Finite equality-fragment verifier component. |
| `proflog.kernel.robinson-q-profile` | profile | Robinson Q theory rules. |
| `proflog.kernel.willard-sjas-profile` | profile | Willard SJAS binary arithmetic and proof-certificate checker. |
| `proflog.kernel.constructor-recursive` | diagnostic/profile prior art | Guarded-IR constructor-recursive solver. |
| `proflog.kernel.constructor-recursive-profile` | profile | Promoted constructor-recursive answer profile. |
| `proflog.tabling` | opt-in operational layer | Canonical proof-state tabling wrapper. |
| `proflog.equality-fast-path` | non-default overlay support | Restricted equality-only acceleration helper. |
| `proflog.hard-family-overlay` | non-default overlay | Named hard-family status overlay. |
| `proflog.fitting-programs` | public examples | Fitting-style catalog and evaluator. |
| `proflog.finite-transition-systems` | public examples | Non-GV finite verifier fixtures. |
| `proflog.turing-completeness` | public examples | Two-counter Minsky interpreter programs. |
| `proflog.combinatory-logic` | public examples | SKI reduction programs. |
| `proflog.robinson-q` | public examples/profile data | Robinson Q language, axioms, theorems, and programs. |
| `proflog.willard-sjas` | public examples/profile data | Willard SJAS language, builder, classifiers, generated predicates, arithmetic constructors, and query helpers. |
| `proflog.robinson-q-probe` | probe | Robinson Q timing comparison. |
| `proflog.gv-probe` | probe | Historical group-verifier reconstruction. |
| `proflog.list-kernel-matrix-probe` | probe | Raw append/reverse kernel capability matrix. |
| `proflog.legacy-stream-probe` | probe | Raw answer stream classification for list rows. |
| `proflog.turing-completeness-long-probe` | probe | Slow TC runtime-boundary probes. |
| `proflog.relational-arithmetic` | opt-in library/probe foundation | Faster-minikanren bit-list arithmetic translation. |
| `proflog.relational-fuel-probe` | probe | Experimental bit-list fuel stepping. |
| `proflog.relational-fuel-adapter-probe` | probe | Host-integer to bit-list fuel boundary adapter. |
| `proflog.relational-fuel-performance-probe` | probe | FD-vs-relational fuel timing harness. |
| `proflog.fd-fuel-synthesis-probe` | probe | Production FD fuel synthesis characterization. |
| `proflog.minikanren-constraints` | opt-in library/probe support | Local `symbolo`, `numbero`, `absento` overlay. |
| `proflog.l-ground-constraint-probe` | probe | L-groundness-as-constraint experiments. |
| `proflog.relational-maps-probe` | probe | Relational map/alist experiments. |
| `proflog.core-logic-host` | diagnostic | Runtime core.logic artifact report. |
| `proflog.core-logic-host-probe` | probe entrypoint | CLI wrapper for host report. |
| `proflog.core-logic-count-probe` | probe | core.logic call-count instrumentation. |
| `proflog.core-logic-disequality-probe` | probe | Disequality maintenance instrumentation. |
| `proflog.core-logic-tabling-probe` | probe | core.logic tabling instrumentation. |

## 14. Current Boundaries

These boundaries are intentional in the current implementation:

- The official implemented authoring surface is Clojure-readable prefix syntax.
  Prolog-like syntax in this guide is explanatory pseudo-code and the contract
  a future textual parser should target.
- Relation heads are variable-only. Constructor patterns belong in clause-body
  equalities.
- `:=` helpers are nonrecursive frontend inlining only. Recursive logic belongs
  in `|-` relation clauses.
- Surface code may not mention internal `(par ...)` terms.
- `query-status` is bounded. `:unresolved` means the current operational budget
  did not decide the query.
- `:inconsistent` means the bounded probe found both success and failure proof
  paths. In a well-behaved consistent program this should be treated as a
  serious diagnostic signal.
- Generic symbolic answers may contain residuals. Use diagnostics or profiled
  answer paths to understand whether those residuals can be discharged.
- `query-parity-answers` and `query-ground-answers` are explicit
  materialization modes above the kernel, not replacements for symbolic
  answer search.
- Production finite fuel is `nil` or host integers. The bit-list relational
  arithmetic fuel experiments remain probe material.
- Equality-fragment proof acceleration is generic. The default remains a
  deterministic host-backed proof engine; ADR-0057 also provides an opt-in
  relation-backed finite route with full finite-verifier completion parity.
- The hard-family overlay is non-default and should not be used as the semantic
  authority when a kernel/profile proof exists.
- Long Turing-completeness and quine examples demonstrate expressivity, not
  practical execution speed.

## 15. What To Read Next

For quick use, start with:

- [README Quickstart](../README.md#quickstart);
- [Frontend To Kernel Descent](../worked-examples/frontend-to-kernel-descent.md);
- [Answers API](../worked-examples/answers-api.md).

For implementation work, read:

- [Greenfield Implementation Tutorial and Reference](GREENFIELD_IMPLEMENTATION_TUTORIAL.md);
- [Greenfield Source Map](GREENFIELD_SOURCE_MAP.md);
- [Language Namespace Spec](LANGUAGE_NAMESPACE_SPEC.md);
- [Test Runtime Baseline](TEST_RUNTIME_BASELINE.md).

For project history, read:

- [Execution Plan](EXECUTION_PLAN.md);
- [Development Log](../LOG.md);
- [ADR Index](adr/README.md);
- [AAR Index](aar/README.md).
