# ADR-0040: Legacy Subsumption Parity Gates

- Status: completed
- Date: 2026-05-06
- Branch: `adr-0040-legacy-subsumption-parity`
- AAR: [AAR-0040](../aar/AAR-0040-legacy-subsumption-parity.md)
- Depends On:
  - [ADR-0035](ADR-0035-relational-residual-continuation.md)
  - [ADR-0038](ADR-0038-fitting-program-kernel-evaluation.md)
  - [ADR-0039](ADR-0039-kernel-level-group-verification.md)

## Context

After ADR-0039, greenfield no longer has the old group-verifier capability gap
relative to legacy. The remaining argument for legacy is narrower: several
legacy rows still have more explicit test coverage than their greenfield
counterparts.

The concrete parity gaps are:

- GV identity, closure, and inverses rows;
- finite-domain disjointness and totality rows; and
- Peano arithmetic reverse / partial synthesis rows corresponding to legacy
  PA12 through PA20.

## Decision

Add a focused greenfield legacy-subsumption suite. Each parity row must include
both:

- the legacy-scale row; and
- an extended row that is larger, deeper, or more demanding in reverse or
  partial synthesis mode.

The suite is allowed to be a focused selector rather than part of routine fast
tests. These cases are proof-search and answer-search gates, not micro-tests.

## Test Obligations

The focused suite must cover:

- GV `Z2` identity, closure, and inverses through the greenfield query API;
- extended GV identity, closure, and inverses on a larger finite group;
- finite-domain disjointness and totality;
- extended finite-domain disjointness and totality on a larger constant set;
- PA12 first-argument synthesis and an extended first-argument case;
- PA13 / PA15 second-argument and existential-addend synthesis plus an
  extended case;
- PA14 sum synthesis plus an extended deeper case;
- PA16 halving plus an extended halving case;
- PA17 / PA18 odd non-halving refutations plus an extended odd case;
- PA19 all pairs summing to a bounded target plus an extended larger target;
  and
- PA20 fixed-addend pair enumeration plus an extended fixed-addend row.

Where the generic answer stream is still too expensive, the suite may use
candidate-guided proof checks, but every accepted candidate must still be
validated by the greenfield kernel against the compiled Proflog program. Tests
must not assert arithmetic by host calculation alone.

## Exit Criteria

ADR-0040 is complete when:

- the focused legacy-subsumption selector passes;
- each legacy parity row has a corresponding extended row;
- any implementation issue revealed by the new tests is fixed or explicitly
  documented as a remaining frontier; and
- the AAR records whether the result is default-suite coverage or focused
  coverage.

## Result

The focused selector `lein test-proflog-legacy-subsumption` passes with 3 tests
and 63 assertions. The result is focused-suite coverage, not fast-path coverage.
Peano open answer rows are covered through the constructor-recursive profile;
FD05 has success proof evidence but an inconsistent bounded two-sided status
probe. Both operational boundaries are recorded in the AAR.
