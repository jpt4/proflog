# AAR-0021: Gamma Search Regression Repair

- Date: 2026-04-27
- Related ADR: [ADR-0021](../adr/ADR-0021-gamma-search-regression-repair.md)
- Outcome: completed for new gamma regressions; list raw-proof blocker is pre-existing

## What Happened

ADR-0019 made generated closed terms available too broadly. Recursive program
proofs could pay Herbrand candidate costs even when the existing fresh-variable
gamma path was sufficient.

ADR-0021 changed the policy:

- generated closed terms are a fallback after fresh proof-variable
  instantiation,
- generated ordinary `forall` candidates are only used in the empty-agenda
  case, avoiding re-enqueued generated candidates,
- generated candidates are guarded by a pure `call-free-formulao` relation so
  they only apply to equality / disequality constructor counterexample
  formulas,
- public wrappers supply no generated candidates for programs whose compiled
  clause bodies contain procedure calls,
- default candidate generation is capped at constructor depth `2` and at `32`
  terms for high-branching signatures.

This preserves the ADR-0019 semantic gates while restoring completion for the
quantified and integration-family regressions that were caused by over-eager
closed-term gamma search.

## Regression Evidence

The list namespace still contains a raw proof blocker:

```clojure
proflog.list-programs-test/append-two-step-ground-case-succeeds
```

That blocker is not new to ADR-0019 or ADR-0021. It was reproduced in separate
worktrees at:

- `3348909` (`Complete ADR-0019 documentation`)
- `8c9bfb4` (`Add ADR-0019 compound gamma gates`, before ADR-0019 implementation)

In both worktrees, the single `append-two-step-ground-case-succeeds` test did
not complete in the verification window. The full
`proflog.list-programs-test` namespace therefore remains a pre-existing raw
list-family performance blocker, not a closed-term gamma regression.

## Verification

Passed:

- `lein test proflog.gamma-test proflog.closed-term-gamma-test`
- `lein test-proflog-fast`
- `lein test proflog.integration-families-test`
- `lein test proflog.quantified-programs-test`
- `lein test proflog.answers-test`
- `lein test-proflog-legacy-impurity`

Still blocked, but not newly introduced:

- `lein test proflog.list-programs-test`
- `lein test-proflog-extended`, because it includes `proflog.list-programs-test`

## Follow-Up

The remaining list raw-proof blocker should be handled by a separate ADR
focused on recursive list proof performance. It should not be conflated with
closed-term gamma semantics.
