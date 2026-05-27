# Test Matrix

Date: 2026-05-07

This matrix is the release gate until a separate automated coverage tool is added. Every ADR must map its code changes to these obligations.

## Coverage Policy

- Every public relation or externally visible function must have at least one direct test.
- Every semantic rule must have at least one contradiction test and one non-closure test where applicable.
- Every major relation should be exercised in both straightforward forward use and at least one partially instantiated or reverse-style use when the semantics claim mode freedom.
- Any uncovered path must be called out in the ADR or AAR with a concrete reason.
- Performance instrumentation, debug printers, and intentionally unreachable guard code are the only acceptable routine exceptions.
- Speculative ADR work, including source overlays and probe namespaces, must
  have focused tests for the claimed behavior even when it is not promoted to
  production. The ADR must say which probe results are evidence and which are
  not merge gates.

## Matrix

| Area | Micro-tests | End-to-end tests | Oracle or property checks | Must pass before merge |
|---|---|---|---|---|
| AST and constructors | tagged term and formula shape tests | build a small program/query pair from the public constructors | none required | yes |
| Language declaration | arity checks, declared-symbol checks, clause merge/desugar checks | compile multi-clause surface input into single-clause Fitting form | none required | yes |
| NNF and negation | connective pushdown, quantifier duality, literal negation | negative procedure-call body preparation | round-trip checks for bounded formulas | yes |
| Substitution | variable lookup, nominal binding, parameter pass-through | clause instantiation for calls | parameter non-leak checks | yes |
| Base tableau kernel | alpha, beta, gamma, delta, complementary closure | classical first-order examples without programs | bounded theorem regression set | yes |
| Equality kernel | reflexivity, clash, injectivity, congruence, occurs-check, disequality-store behavior | equality inside subsidiary tableaux | bounded Herbrand evaluator for small signatures | yes |
| Procedure-call rule | positive call, negative call, recursion, mutual recursion | Fitting `P1` even/odd and `P2` Nim | clause biconditional spot-checks over bounded ground terms | yes |
| Query API | succeed/fail race, unresolved search budget handling | user-facing query helpers over sample programs | consistency checks between `A` and `not A` races | yes |
| Answer discipline | admissible substitutions, residual disequalities, proof attachment | open-query examples with quantified bodies | answer terms contain only language `L` symbols and no `par` | yes |
| Regression and performance | previously fixed bug cases, ordering/pathology cases | flagship program suite | bounded runtime budgets only after baseline correctness exists | yes |
| Minimal TC demonstrations | transition clauses, bounded reachability, trace-shaped proof, symbolic reduction, self-reproduction trace, answer export, instruction partial synthesis | two-counter Minsky machine transfer/incrementer programs and SKI combinatory logic | source audits for absence of host-side machine/SKI evaluators; runtime-boundary notes for non-promoted slow probes | opt-in focused gates |
| Proof profiles and deduction modulo | language-level profile metadata, profile dispatch, kernel-interleaved theory conversion, unified Q3 predecessor-equality proof markers, and preservation of generic equality-fragment closures under selected theory profiles | Robinson Q as ordinary assumptions and as `:robinson-q` conversion/Q3 profile | common theorem timing comparison, corrected prime/evenness Q-as-antecedent examples, source audit against host preprocessing and old Q3 closers, and proof evidence distinguishing assumptions from interleaved Q conversion and Q3 predecessor equality | yes when production query dispatch changes |
| Speculative overlays and probes | direct relation/constraint behavior, compatibility tests, wrapper boundary tests | focused integration probes against one production surface where relevant | before/after measurements or upstream-style behavior tests | no, unless the ADR promotes the feature |

## Flagship Program Families

- Fitting `P1`: even/odd.
- Fitting `P2`: Nim.
- Undefined self-reference such as `p <- not p`.
- Extensional definitions such as subset or set equality using universal quantification.
- Global specification examples such as sortedness or uniqueness over structural relations.
- Two-counter Minsky machine interpreter and SKI combinatory logic (`lein test-proflog-turing-completeness`).
- Robinson arithmetic Q ordinary/proof-profile comparison (`lein test-proflog-robinson-q`).

## Equality Oracle Requirements

The equality milestone is not complete until the greenfield implementation can be checked against a bounded direct evaluator over a tiny declared language. The oracle should compare:

- `eq` versus structural identity over free constructors,
- `neq` versus structural non-identity,
- complementary atom closure under the current walked substitution,
- small equality-bearing program clauses in subsidiary tableaux.

## Release Checklist

- New tests are organized by semantic area, not by edited source file.
- Every bug fix adds a regression test.
- Every optimization claim names the semantic surface it must preserve.
- The AAR for the merged ADR records which matrix rows were satisfied and which remain open.
