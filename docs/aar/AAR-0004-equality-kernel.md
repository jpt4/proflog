# AAR-0004: Equality Kernel

- Date: 2026-04-18
- Related ADR: [ADR-0004](../adr/ADR-0004-equality-kernel.md)
- Outcome: complete

## What Happened

ADR-0004 replaced the initial rewrite-first equality experiment with a
constraint-style equality layer:

- `src/proflog/equality.clj`
- `src/proflog/kernel.clj`
- `test/proflog/equality_test.clj`
- `test/proflog/oracle/herbrand_test.clj`

The completed design now uses:

- explicit free proof variables as `(var nom)` terms,
- an explicit branch substitution for positive equality,
- a symbolic disequality store rechecked after each new equality binding,
- direct occurs-check failure for cyclic equalities,
- proof terms that expose clash, occurs-check, and equality-binding steps.

## What Worked

- Replacing the rewrite-first branch logic with explicit equality state brought
  the implementation back into line with the research docs, the mission
  statement, and the ADR itself.
- The existing kernel structure survived the refactor cleanly: conjunction,
  disjunction, and quantifier handling stayed intact while equality moved into
  dedicated support relations.
- Using explicit `(var nom)` terms gives the project a better substrate for the
  upcoming procedure-call and answer-projection ADRs than host logic-variable
  side effects alone would have.
- The greenfield suite now passes end to end:
  `lein test proflog.ast-test proflog.language-test proflog.normalize-test proflog.subst-test proflog.kernel-test proflog.proof-test proflog.equality-test proflog.oracle.herbrand-test`

## What Did Not Work

- The first ADR-0004 attempt implemented equality through saved-equality
  rewriting and atom rewriting on the branch. That approach was close enough to
  pass some ground cases, but it diverged from the documented design and made
  open-variable behavior harder to reason about.
- One of the early equality tests used host logic variables directly, which
  asked an existential search question instead of the intended object-language
  one. The final suite now uses explicit free variables in the AST where that
  distinction matters.
- A hoped-for standalone `atom-close` equality proof rule turned out not to be
  the right signal for ordinary complementary atom closure: in the free-variable
  tableau setting, those atoms already unify directly. The proof tests now
  inspect the real semantic evidence instead, namely `eq-bind`, `close`,
  `free-close`, and `occurs-close`.

## Follow-Up

- Start ADR-0005 on a fresh branch from `greenfield`.
- Add procedure-call closure over compiled programs without discarding the new
  explicit equality state.
- Build the top-level query API around honest success, failure, and unresolved
  search rather than a binary result.
