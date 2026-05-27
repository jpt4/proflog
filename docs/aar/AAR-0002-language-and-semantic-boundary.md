# AAR-0002: Language And Semantic Boundary

- Date: 2026-04-18
- Related ADR: [ADR-0002](../adr/ADR-0002-language-and-semantic-boundary.md)
- Outcome: complete

## What Happened

ADR-0002 added the first greenfield implementation modules:

- `src/proflog/ast.clj`
- `src/proflog/language.clj`
- `src/proflog/normalize.clj`
- `src/proflog/subst.clj`

and the first greenfield tests:

- `test/proflog/ast_test.clj`
- `test/proflog/language_test.clj`
- `test/proflog/normalize_test.clj`
- `test/proflog/subst_test.clj`

The implementation provides:

- a tagged list-based core AST,
- explicit language declarations,
- surface-program validation,
- safe multi-clause compilation into a single Fitting-style clause per relation,
- NNF conversion and negation,
- pure and relational substitution with binder-aware shadowing.

## What Worked

- The list-based representation keeps the later kernel path open for relational recursion over variable-arity applications.
- Clause compilation now alpha-renames to genuinely fresh noms, which avoids variable capture across merged surface clauses.
- The focused greenfield test suite passes cleanly with `lein test proflog.ast-test proflog.language-test proflog.normalize-test proflog.subst-test`.

## What Did Not Work

- The first version of the test helper for fresh noms was inert because it reused a goal-oriented nominal macro shape in ordinary test code; this was corrected by switching to plain-Clojure fresh nom binding.
- The first relational substitution rule over-approximated by allowing unchanged output even when a binding existed; the final version only passes a variable through when it is genuinely unbound in the environment.

## Follow-Up

- Start ADR-0003 on a fresh branch from `greenfield`.
- Build the base tableau kernel against the new `proflog.*` modules rather than extending `cljtap.*`.
- Keep future clauses and kernel state aligned with the list-based AST introduced here.
