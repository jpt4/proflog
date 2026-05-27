# AAR-0046: Combinatory Logic Turing Completeness

- Date: 2026-05-08
- ADR: [ADR-0046](../adr/ADR-0046-combinatory-logic-turing-completeness.md)
- Branch: `adr-0045-0046-tc-performance`
- Status: completed

## Summary

ADR-0046 added a second Turing-completeness demonstration using SKI
combinatory logic. The program is written through the ADR-0010 frontend and
compiled into ordinary Proflog relations:

- `step/2` for I, K, S, and left-spine application-context reduction;
- `eval-for/3` for Peano-bounded finite reduction.

The added left-spine context rule was required by the red boolean-false
example. With root-only reduction, `((K I) a) b` did not finish inside a
`180 s` guard because the required redex sits in the function position of the
outer application. The solution was a recursive kernel-level `step/2` rule,
not a host evaluator.

## Evidence

- Red feedback:
  - before the namespace existed, the focused SKI test failed with
    `Could not locate proflog/combinatory_logic`;
  - before the context rule, the boolean-false example did not finish inside a
    `180 s` guard.
- Root reductions passed:
  `I x -> x`, `K x y -> x`, and `S x y z -> x z (y z)`.
- Significant bounded evaluations passed:
  `SKK a -> a`, `choose(K,a,b) -> a`, and `choose(K I,a,b) -> b`.
- Answer mode exported `result = a` for `SKK a` with empty residuals.
- Source audit rejected host-side query/answer/evaluator functions in the SKI
  namespace.
- Full ADR-0046 namespace timing:
  `Ran 6 tests containing 12 assertions`, `0 failures, 0 errors`,
  `elapsed 225.50 s`.

## Effect

The TC evidence is no longer dependent on one computational model. Minsky
machines demonstrate state-transition computation over counters. SKI
combinatory logic demonstrates symbolic term rewriting over constructor trees.
Both execute through compiled Proflog clauses and kernel procedure calls.

## Performance

Focused timings:

| Row | Runtime |
|---|---:|
| Root reductions | `31.33 s` |
| `SKK a` bounded evaluation | `44.29 s` |
| Boolean true | `20.09 s` |
| Boolean false | `45.29 s` |
| Answer-mode `SKK a` export | `206.87 s` |
| Source audit | `15.80 s` |

The answer-mode row is the current practical limit. It is acceptable for the
opt-in TC suite, but it is too slow for the routine fast or extended gates.

## Documentation

- [Combinatory Logic Example](../../worked-examples/combinatory-logic.md)
- [TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md)
- [GREENFIELD_SOURCE_MAP](../GREENFIELD_SOURCE_MAP.md)

## Follow-Up

Right-argument contextual reduction, a lambda-to-SKI translator, and unbounded
normalization are intentionally out of scope. They should be added only with
new ADRs and red tests because contextual search has a visible runtime cost.

## Later Update: ADR-0047

ADR-0047 added right-argument contextual reduction in a separate `full-step/2`
relation for the omega quine trace. It did not add the rule to the default
`step/2` relation, because that made the full SKI selector exceed a `900 s`
guard.
