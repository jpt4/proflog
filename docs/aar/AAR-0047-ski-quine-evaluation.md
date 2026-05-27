# AAR-0047: SKI Quine Evaluation

- Date: 2026-05-08
- ADR: [ADR-0047](../adr/ADR-0047-ski-quine-evaluation.md)
- Branch: `adr-0047-ski-quine`
- Status: completed

## Summary

ADR-0047 evaluated the standard self-reproducing SKI term:

```text
omega = (S I I) (S I I)
```

The direct recursive query `eval-for(3, omega, omega)` did not finish inside a
`240 s` guard under the ADR-0046 relation. Adding argument-position contextual
reduction directly to `step/2` made the focused trace pass, but made the full
SKI selector exceed a `900 s` guard. That design was rejected.

The accepted result adds a separate `full-step/2` relation and proves a guided
three-edge trace:

```text
(S I I) (S I I)
=> (I (S I I)) (I (S I I))
=> (S I I) (I (S I I))
=> (S I I) (S I I)
```

Each edge is still proved by compiled Proflog clauses. No host-side SKI
evaluator was added.

## Evidence

- Red direct query:
  `eval-for(3, omega, omega)` timed out inside a `240 s` wrapper.
- Rejected broad implementation:
  argument-context reduction inside `step/2` made the full SKI selector time
  out inside a `900 s` wrapper.
- Promoted focused test:
  `ski-omega-quine-reproduces-itself-through-a-guided-trace`.
- Focused quine timing:
  `Ran 1 tests containing 1 assertions`, `0 failures, 0 errors`,
  `elapsed 95.44 s`.
- Full focused SKI selector timing after isolation:
  `Ran 7 tests containing 13 assertions`, `0 failures, 0 errors`,
  `elapsed 301.98 s`.
- Aggregate TC selector timing:
  `Ran 15 tests containing 30 assertions`, `0 failures, 0 errors`,
  `elapsed 438.34 s`.
- Standard gates:
  `lein test-proflog-fast` passed in `96.41 s`; `lein test-proflog-extended`
  passed in `237.72 s`.

## Effect

The SKI demonstration now includes a genuine positive-step self-reproduction
example. This improves the non-triviality of the TC demonstration without
pretending that open recursive loop discovery is currently practical.

The result also establishes a useful boundary: fuller contextual reduction is
semantically needed for quines, but it must be isolated from the ordinary
`step/2` relation unless proof search improves.

## Documentation

- [Combinatory Logic Example](../../worked-examples/combinatory-logic.md)
- [TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md)
- [GREENFIELD_SOURCE_MAP](../GREENFIELD_SOURCE_MAP.md)

## Follow-Up

Future work can revisit direct `eval-for(3, omega, omega)` after search-control
improvements. Until then, the quine is a guided-trace proof, not a promoted
recursive evaluator row.
