# AAR-0055: SKI Relational Test Routing

- Date: 2026-05-09
- ADR: [ADR-0055](../adr/ADR-0055-ski-relational-routing.md)
- Branch: `adr-0055-ski-relational-routing`
- Status: completed

## Summary

ADR-0055 tightened the SKI demonstration so the tests trace and enforce the
proof route. Closed SKI proof rows now validate and negate the query, then call
`kernel/prove-programo` directly. The answer row now calls
`answer-overlay/prove-program-query-entry-scheduledo`, which invokes the
underlying query-entry relation and the relational residual scheduler before
the test uses ordinary answer-record presentation.

No SKI program clauses changed. The Clojure term helpers still only construct
object-language terms.

## Evidence

- Red route check:
  `ski-evaluation-does-not-route-through-public-or-profiled-shortcuts` first
  failed because the old helpers entered `query/query-succeeds` and
  `answers/query-answers`; elapsed time was `9.59 s`.
- Promoted route check:
  `Ran 1 tests containing 5 assertions`, `0 failures, 0 errors`,
  `real 29.14 s`.
- Answer-mode check:
  `Ran 1 tests containing 2 assertions`, `0 failures, 0 errors`,
  `real 49.32 s`.
- Full SKI selector:
  `Ran 8 tests containing 18 assertions`, `0 failures, 0 errors`,
  `real 176.02 s`.
- Aggregate Turing-completeness selector:
  `Ran 16 tests containing 35 assertions`, `0 failures, 0 errors`,
  `real 273.27 s`.

## Effect

The SKI tests now reject accidental use of public proof dispatch, public answer
dispatch, constructor-recursive sidecars, and the equality-fragment host
profile. The allowed proof route is explicitly:

- closed rows: `kernel/prove-programo`;
- answer row: `answer-overlay/prove-program-query-entry-scheduledo`, then
  `answer-overlay/prove-program-query-entryo`, then relational residual
  scheduling;
- presentation: private answer-record export only, after raw relational states
  already exist.

This is stronger evidence for the ADR-0046/0047 Turing-completeness examples:
the tests no longer rely on wrapper behavior that may attempt profiled or
host-side operational layers before reaching the relational kernel.

## Documentation

- [Combinatory Logic Example](../../worked-examples/combinatory-logic.md)
- [TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md)

## Follow-Up

The current work changes test routing and documentation only. It does not
change the public query APIs; users should still use those public APIs outside
route-audit tests.
