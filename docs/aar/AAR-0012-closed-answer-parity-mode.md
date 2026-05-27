# AAR-0012: Closed-Answer Parity Mode

- Date: 2026-04-23
- Related ADR: [ADR-0012](../adr/ADR-0012-closed-answer-parity-mode.md)
- Outcome: complete

## What Happened

ADR-0012 added a dedicated closed-answer parity track for the legacy list
families:

- `src/proflog/answers.clj`
- `test/proflog/parity_test.clj`
- `project.clj`

The completed branch introduces `query-parity-answers`, a specialty API kept
separate from the generic symbolic `query-answers` path. It now:

- leaves the default symbolic answer contract unchanged,
- materializes fully closed list-family answers for `append/3` and `reverse/2`
  in a dedicated parity mode,
- keeps parity residuals empty,
- and runs under the explicit `lein test-proflog-parity` alias.

## What Worked

- The branch cleanly separated "generic symbolic answers" from "closed legacy
  parity answers" instead of overloading one API with both meanings.
- The parity namespace now covers the concrete list-family gaps that the
  generic path still leaves symbolic:
  `reverse([a,b], r)`, all four inverse `append` splits, the nested inverse
  append family, and the nested suffix family.
- The existing fast and extended suites still pass unchanged, which means the
  new parity mode did not move the generic answer semantics under current
  callers.

## What Did Not Work

- A prover-backed generic closed-answer search remained operationally
  impractical for these families even after budget tuning. The list-family
  parity branch only became practical once it stopped pretending to be a second
  generic search API.
- The resulting mode is less relationally pure than the generic answer engine.
  It is an extensional materializer for known parity families, not evidence
  that the generic proof search has reached legacy closure.
- Proof payloads are intentionally empty on the list-family fast path. The
  proof authority for those concrete cases remains the direct semantic tests in
  `test/proflog/list_programs_test.clj`.

## Follow-Up

- Keep ADR-0012 isolated and explicit. It solves a real parity need, but it is
  not the final architectural answer.
- Use ADR-0013 to see whether improved generic relational performance can
  reduce or eliminate the need for this specialty mode.
- Revisit whether parity mode should collect proof payloads once ADR-0013 makes
  the underlying generic search cheaper.
- Update after ADR-0013: the known list-family fast path is now also reused by
  the default `query-answers` API for recognized `append/3` and `reverse/2`
  queries, but `query-parity-answers` still remains the explicit closed-answer
  and bounded materialization interface.
