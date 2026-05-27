# AAR-0045: Minsky Trace Performance

- Date: 2026-05-08
- ADR: [ADR-0045](../adr/ADR-0045-minsky-trace-performance.md)
- Branch: `adr-0045-0046-tc-performance`
- Status: completed

## Summary

ADR-0045 converted the non-viable five-step transfer demonstration into a
passing kernel proof by changing the query formulation, not the machine
semantics. The new `trace-formula` helper builds a finite conjunction of
compiled `step/2` calls and an optional `halt-config/1` call.

This keeps the two-counter machine interpreter honest: there is still no
host-side transition evaluator, and every trace edge is proved by the ordinary
procedure-call rule over compiled Proflog clauses.

## Evidence

- Red feedback: ADR-0044 long probes had already shown
  `recursive-transfer-5-steps` timing out after a `1800 s` wrapper.
- New promoted test:
  `five-step-transfer-closes-through-a-guided-step-trace`.
- Source audit:
  `trace-helper-does-not-contain-a-host-machine-evaluator`.
- Focused five-step trace timing:
  `Ran 1 tests containing 1 assertions`, `0 failures, 0 errors`,
  `elapsed 58.89 s`.
- Full ADR-0045 namespace timing:
  `Ran 2 tests containing 4 assertions`, `0 failures, 0 errors`,
  `elapsed 55.02 s`.

## Effect

The project now has a deeper Minsky TC witness than ADR-0044's original
promoted suite. The result is not a generic performance fix for recursive
reachability; it is a proof-search formulation that is acceptable for known
finite traces.

The original poor formulations remain valuable diagnostics. In particular,
`halts-in-steps(5, cfg(l0,2,0), cfg(halt-label,0,2))` should stay on the long
probe path until recursive search improves enough to make it a reasonable
regression.

## Documentation

- [Turing Completeness Example](../../worked-examples/turing-completeness.md)
- [TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md)
- [GREENFIELD_SOURCE_MAP](../GREENFIELD_SOURCE_MAP.md)

## Follow-Up

Future proof-search work should target the original recursive formulation. The
trace helper is not a substitute for improving `run-for/3`; it is diagnostic
evidence that the compiled transition semantics are capable of proving the
deeper finite run when the proof obligations are shaped explicitly.
