# ADR-0045: Minsky Trace Performance

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0045-0046-tc-performance`
- AAR: [AAR-0045](../aar/AAR-0045-minsky-trace-performance.md)

## Context

ADR-0044 demonstrated Turing-completeness with a Proflog-level two-counter
Minsky machine interpreter. Its promoted tests pass, but follow-up long probes
showed that several semantically finite Minsky computations are not currently
practical in their most direct formulations:

- `recursive-transfer-3-steps` eventually closes, but only after `783.72 s`;
- `open-predecessor-step` eventually returns answers, but only after `645.66 s`;
- `direct-ground-three-step-trace` did not return before a controlled stop at
  about thirty minutes;
- `recursive-transfer-5-steps` timed out after a `1800 s` wrapper.

This is not a failure of representability. It is proof-search performance and
formulation sensitivity. The project should not promote tests that are
impractical to run, but it should continue to drive the prover toward making
these Minsky computations evaluable.

## Decision

Add a focused Minsky trace-performance layer that improves evaluation of known
finite traces while preserving the ADR-0044 semantic boundary:

- no host-side machine step evaluator;
- no machine-specific arithmetic evaluator;
- no replacement of the generic `step/2`, `run/2`, or `run-for/3` semantics;
- proof evidence must still come from compiled Proflog formulas and the
  kernel/answer machinery.

The immediate strategy is to introduce a trace-shaped query surface for known
finite computations. Instead of asking the recursive `run-for/3` relation to
discover a long trace through poor conjunction order, the trace helper builds a
frontend formula containing the finite sequence of `step/2` obligations and
lets the existing kernel/answer path prove that formula. This is an
operational proof-search improvement, not a different semantics: each edge in
the trace remains a call to the compiled `step/2` relation.

## Exit Criteria

- Red tests first show at least one currently non-viable or long-control Minsky
  example is not acceptable as a promoted direct test.
- A trace-shaped evaluator or helper makes a deeper transfer example evaluate
  successfully through compiled `step/2` calls.
- The promoted Minsky performance tests include:
  - a five-step transfer from `cfg(l0, 2, 0)` to `cfg(halt-label, 0, 2)`;
  - proof/answer evidence that the final halt configuration is reached;
  - a source audit proving no host-side transition evaluator was added.
- The ADR-0044 long-probe diagnostics remain available for the still-poor
  direct recursive formulations.
- Timings are recorded in `docs/TEST_RUNTIME_BASELINE.md`.
- Worked examples explain which Minsky formulations are viable tests and which
  remain diagnostics.
- AAR-0045 records the outcome.

## Non-Goals

- This ADR does not solve arbitrary `run/2` reachability.
- This ADR does not require `recursive-transfer-5-steps` itself to close
  through the original recursive `halts-in-steps/3` formulation.
- This ADR does not add host-side execution of a Minsky machine.

## Risks

Trace-shaped queries may be mistaken for a second evaluator. Documentation and
tests must be explicit that the helper constructs formulas only; the semantics
of each transition still comes from the compiled Proflog `step/2` relation.
