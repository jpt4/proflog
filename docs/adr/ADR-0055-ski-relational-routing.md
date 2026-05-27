# ADR-0055: SKI Relational Test Routing

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0055-ski-relational-routing`
- AAR: [AAR-0055](../aar/AAR-0055-ski-relational-routing.md)

## Context

ADR-0046 and ADR-0047 added the SKI combinatory-logic demonstration. The SKI
program itself is written as frontend Proflog clauses, and the namespace source
audit rejects host-side SKI evaluators. The tests, however, used public helpers:

- closed proof rows used `query/query-succeeds`;
- answer export used `pf/run`.

Those helpers are correct public APIs, but they are not the strongest possible
evidence for the SKI Turing-completeness demonstration. `query/query-succeeds`
enters proof-profile dispatch, where forward-only profile shortcuts such as the
equality-fragment component may be attempted before the relational kernel.
`pf/run` delegates to `answers/query-answers`, whose public contract includes
some operational materializers above the generic answer-overlay relation.

For the SKI demonstration, the tests should show a stricter route: the promoted
reductions are proved by the ordinary relational kernel and the answer example
is exported from the relational answer-overlay machinery.

## Decision

Change the SKI tests to use local evaluation helpers that call:

- `kernel/prove-programo` for closed proof rows, after ordinary validation and
  NNF negation;
- `answer-overlay/prove-program-query-entry-scheduledo` for the answer row,
  which invokes `answer-overlay/prove-program-query-entryo` and then completes
  structural residuals through the relational residual scheduler, followed only
  by ordinary answer-record export.

Add a routing-trace regression that wraps the relevant functions and fails if
SKI evaluation enters public proof dispatch, equality-fragment dispatch, public
answer dispatch, or constructor-recursive/profile sidecars.

This does not change the public APIs or the SKI program. It changes what the
SKI tests demonstrate.

## Test Obligations

- The initial routing test must fail against the current SKI test helpers,
  because `query/query-succeeds` and `pf/run` are still in the path.
- After implementation, the focused SKI selector must pass.
- The routing trace must record calls to relational kernel/answer-overlay
  relations and no forbidden shortcut calls.
- Documentation must explain the exact route:
  - term helpers construct object-language terms only;
  - closed rows run through `kernel/prove-programo`;
  - answer rows run through `answer-overlay/prove-program-query-entry-scheduledo`
    and the underlying `answer-overlay/prove-program-query-entryo`;
  - no host-side SKI reducer or profiled shortcut is used.

## Exit Criteria

- `lein test-proflog-combinatory-logic` passes.
- `lein test-proflog-turing-completeness` passes.
- Standard fast and extended gates pass if this touches shared proof or answer
  behavior. If only tests/docs change, record the focused SKI and TC gates.
- ADR/AAR, runtime baseline, worked example, and log are updated.
