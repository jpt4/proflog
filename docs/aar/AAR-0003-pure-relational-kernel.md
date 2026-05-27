# AAR-0003: Pure Relational Kernel

- Date: 2026-04-18
- Related ADR: [ADR-0003](../adr/ADR-0003-pure-relational-kernel.md)
- Outcome: complete

## What Happened

ADR-0003 added the baseline proving layer:

- `src/proflog/kernel.clj`
- `src/proflog/proof.clj`
- `test/proflog/kernel_test.clj`
- `test/proflog/proof_test.clj`

The kernel now supports:

- α expansion for conjunction,
- β branching for disjunction,
- γ instantiation for universal quantifiers,
- δ witness introduction for existential quantifiers,
- complementary literal closure,
- proof terms for the major tableau steps,
- a top-level `prove` wrapper over the proving relation.

## What Worked

- The kernel composes cleanly with the AST, normalization, and substitution modules from ADR-0002.
- The proof terms are simple tagged lists, which makes them easy to inspect and partially constrain in relational tests.
- The partially specified proof-shape test gives a concrete example that the kernel relation is not locked into a single one-way use.
- The full current greenfield suite passes:
  `lein test proflog.ast-test proflog.language-test proflog.normalize-test proflog.subst-test proflog.kernel-test proflog.proof-test`

## What Did Not Work

- None of the failures here were deep semantic bugs; the only correction needed during implementation was to remove a malformed test query that had accidentally fixed the proof output to a literal symbol instead of leaving it relational.
- Search control is still intentionally minimal. That is acceptable at this stage, but equality and recursive calls will put more pressure on fairness and proof size.

## Follow-Up

- Start ADR-0004 on a fresh branch from `greenfield`.
- Extend the kernel with equality and disequality in a way that preserves the current proof and branch structure.
- Keep the kernel free of hidden theory changes while equality is introduced.
