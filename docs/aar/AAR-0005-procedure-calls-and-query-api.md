# AAR-0005: Procedure Calls And Query API

- Date: 2026-04-18
- Related ADR: [ADR-0005](../adr/ADR-0005-procedure-calls-and-query-api.md)
- Outcome: complete

## What Happened

ADR-0005 added the first full Proflog execution path:

- `src/proflog/program.clj`
- `src/proflog/query.clj`
- `test/proflog/program_test.clj`
- `test/proflog/query_test.clj`

The kernel now supports the Procedure Call Rule by:

- looking up compiled clauses relationally,
- binding formal parameters to actual arguments,
- starting fresh subsidiary tableaux for positive and negative calls,
- reusing the current equality state across those subsidiary tableaux,
- exposing top-level success, failure, and unresolved status through query helpers.

## What Worked

- The compiled-program layer stayed small and explicit. `:clause-list` plus
  precomputed `:negated-body` was enough to keep clause lookup relational
  without dropping host projection into the kernel.
- Positive and negative calls both compose cleanly with the explicit equality
  state from ADR-0004.
- The public query boundary is now honest: it has separate semidecision
  procedures plus a status helper that can return `:unresolved`.
- Representative end-to-end program cases are green for both Fitting `P1` and
  `P2`, and the full current greenfield suite passes:
  `lein test proflog.ast-test proflog.language-test proflog.normalize-test proflog.subst-test proflog.kernel-test proflog.proof-test proflog.equality-test proflog.oracle.herbrand-test proflog.program-test proflog.query-test`

## What Did Not Work

- The deeper recursive `P2` success cases are still operationally heavier than
  the simpler `P1` and `P2` representatives that now anchor ADR-0005. That is
  a search-control limitation, not a missing procedure-call rule.
- Wall-clock bounded helpers were easier to integrate at the query boundary than
  a new kernel-internal fuel model, but they are clearly an API-level control
  layer rather than the last word on recursion management.
- Stopping abandoned query workers had to stay an API concern. That reinforces
  the need for ADR-0006 to make answer and operational profiles more explicit.

## Follow-Up

- Start ADR-0006 on a fresh branch from `greenfield`.
- Package returned proofs and admissible substitutions explicitly instead of
  leaving query helpers at keyword status only.
- Decide whether deeper recursive coverage belongs to a baseline answer/profile
  story or to a later optimization/search-control ADR.
