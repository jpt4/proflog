# ADR-0002: Language And Semantic Boundary

- Status: completed
- Date: 2026-04-18
- Branch: `adr-0002-language-boundary`
- AAR: [AAR-0002](../aar/AAR-0002-language-and-semantic-boundary.md)

## Context

The deep research reports converge on a fixed semantic boundary:

- Proflog programs live in an explicitly declared language `L`.
- Surface syntax may be friendlier than Fitting's formal presentation, but it must compile to a tagged internal core.
- Multiple user clauses for one relation may exist only as sugar and must desugar into Fitting's single-clause-per-relation shape.
- Negative calls require relational negation and NNF handling rather than one-way host rewriting.

## Decision

- Implement a language declaration layer before kernel work proceeds.
- Introduce tagged core representations for formulas, literals, atoms, variables, applications, and internal parameters.
- Compile surface sugar into the tagged core while preserving the formal restriction of one core clause per non-equality relation symbol.
- Implement NNF conversion and substitution as greenfield modules instead of reusing the monolithic `cljtap.*` helpers.

## Consequences

- The object-language boundary becomes explicit and testable early.
- Later answer-admissibility checks have a concrete declaration to enforce.
- Negative calls and open-query substitution can build on a shared internal grammar.
- The greenfield implementation now has a list-based tagged core chosen specifically
  to support later relational kernel work without projection-heavy rewrites.

## Test Obligations

- `test/proflog/ast_test.clj`
- `test/proflog/language_test.clj`
- `test/proflog/normalize_test.clj`
- `test/proflog/subst_test.clj`

## Exit Criteria

- Undeclared symbols and arity mismatches are rejected.
- Multi-clause surface input desugars into the core single-clause program form.
- NNF conversion handles quantifier duality and literal negation correctly.
- Substitution respects nominal bindings and leaves internal parameters internal.
