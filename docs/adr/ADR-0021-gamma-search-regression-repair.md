# ADR-0021: Gamma Search Regression Repair

- Status: completed
- Date: 2026-04-27
- Branch: `adr-0020-0021-gamma-purity-regressions`
- AAR: [AAR-0021](../aar/AAR-0021-gamma-search-regression-repair.md)
- Depends On: [ADR-0020](ADR-0020-pure-gamma-candidate-boundary.md)

## Context

ADR-0019 restored an important semantic case by allowing generated closed
terms as gamma candidates, but some previously passing extended tests no
longer completed in a practical verification window:

- `lein test-proflog-extended`
- `lein test proflog.integration-families-test`
- `lein test proflog.list-programs-test`

That is a regression. The project may document that legacy still outperforms
greenfield on raw recursive list families, but a new ADR must not make
previously useful greenfield regression suites substantially worse.

The likely cause is not the generator itself, but where generated candidates
enter the search. If closed-term enumeration is tried too eagerly, ordinary
recursive failures and quantified programs pay Herbrand search costs even when
the existing fresh-variable path is sufficient.

## Decision

- Keep ADR-0019's semantic capability: generated closed terms must still solve
  the compound gamma and `once-forall` gates.
- Treat generated closed terms as a fallback, not as the first branch, whenever
  the fresh proof-variable instantiation can close the branch.
- Avoid re-enqueueing generated closed-term ordinary `forall` candidates unless
  there is a concrete regression requiring it. Repeated ordinary gamma
  instantiation should remain dominated by the fresh-variable path.
- Run and record the previously regressed namespaces. If any remain too slow,
  continue repair until they complete or isolate a smaller pre-existing
  blocker with evidence.

## Consequences

- Closed-term gamma remains semantically available without front-loading
  enumeration into common recursive cases.
- ADR-0019's gate tests remain the semantic guard for compound constructors.
- Extended-suite completion is a release gate for this follow-up ADR, not an
  optional measurement.

## Exit Criteria

- ADR-0019 gate tests pass.
- `lein test-proflog-fast` passes.
- `lein test proflog.integration-families-test` completes.
- `lein test proflog.list-programs-test` completes.
- `lein test-proflog-extended` completes or any remaining issue is reduced to a
  separate, pre-existing failing namespace with a committed regression record.
