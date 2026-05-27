# AAR-0010: Frontend Inlining Translation

- Date: 2026-05-06
- Related ADR: [ADR-0010](../adr/ADR-0010-frontend-inlining-translation.md)
- Outcome: complete

## What Happened

ADR-0010 moved from proposed design to an implemented prefix frontend in
`proflog.frontend`.

The implemented surface is deliberately thin:

- `language` builds reusable frontend language declarations.
- `proflog` translates visible source clauses into `proflog.ast` clauses and
  delegates compilation to `proflog.language/compile-program`.
- `q` translates visible source queries into backend formulas.
- `run` binds visible answer variables and evaluates open queries through
  `proflog.answers/query-answers`.
- `answer-query` binds visible answer variables and returns the query formula
  plus `:answer-vars` vector used by `proflog.answers`.
- `(:= head body)` introduces a source-level definitional helper.
- `(|- head body)` introduces a real Fitting procedure-call relation.

The frontend performs nonrecursive helper inlining before backend compilation.
This means ergonomic helpers such as `move/2` can be used as source
abbreviations without becoming semantically weaker runtime Proflog relations.

## What Worked

- Reusable frontend language declarations preserve the backend's existing
  `proflog.language/language` flexibility.
- The quickstart now shows the full descent from hand-written Proflog notation
  to prefix frontend source, backend AST clauses, compiled clause bodies, query
  formulas, and kernel proof calls.
- The factored Nim `move/2` warning is covered as a frontend regression:
  `move/2` is inlined and never appears as a compiled runtime relation.
- The open-query frontend boundary is now explicit. Users can write
  `(pf/run program [x] (p x) opts)` for the ordinary public answer path, while
  `(pf/answer-query [x] (p x))` remains available when diagnostics need the
  raw `:query` and `:answer-vars` pair.
- The worked-example corpus now introduces that `run` surface before lower-level
  answer/profile APIs, and the core frontend, AST, and language files carry
  reader-facing comments that explain the mathematical translation boundary.
- Unsupported recursive helpers fail during macro expansion with the source
  helper identifier in diagnostic data.

## What Did Not Work

The implementation does not add a textual parser for infix Prolog-like source.
The supported executable frontend is currently the Clojure-readable prefix
macro surface.

Recursive helper unfolding remains out of scope. Recursive definitions should
be written as real `(|- ...)` relations unless a later ADR introduces bounded
or proof-obligation-based unfolding.

## Validation

```text
lein test proflog.frontend-test
Ran 10 tests containing 30 assertions.
0 failures, 0 errors.
elapsed 26.96 s.

lein test-proflog-fast
Ran 128 tests containing 414 assertions.
0 failures, 0 errors.
elapsed 180.26 s.

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed 610.08 s.
```

The fast and extended suite timings above came from concurrent gate runs, so the
wall times include resource contention from the paired process.

The later worked-example and source-comment pass on 2026-05-07 reran the same
gates after documentation-only/source-comment edits:

```text
lein test proflog.frontend-test
Ran 10 tests containing 30 assertions.
0 failures, 0 errors.
elapsed 12.36 s.

lein test-proflog-fast
Ran 128 tests containing 414 assertions.
0 failures, 0 errors.
elapsed 73.39 s.

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed 206.75 s.
```

The focused suite covers:

- reusable frontend language declarations;
- `p(x) :- x = a` translation into backend clause bodies;
- query formula translation through `q`;
- open answer query binding through `answer-query`;
- open answer evaluation through `run`;
- `zero-only/1` helper inlining with quantified bodies;
- factored Nim source translating to the inline executable `win/1` form;
- duplicate answer-query binding rejection;
- rejection of recursive definitional helpers.

## Follow-Up

- Consider a textual parser that accepts Fitting-style infix notation and emits
  the same frontend source IR.
- Consider source-location metadata for richer diagnostics once there is a file
  or string parser.
- Keep recursive helper unfolding as a separate semantic decision, not an
  implicit extension of `:=`.
