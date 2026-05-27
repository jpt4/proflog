# ADR-0004: Equality Kernel

- Status: completed
- Date: 2026-04-18
- Branch: `adr-0004-equality`
- AAR: [AAR-0004](../aar/AAR-0004-equality-kernel.md)

## Context

The research stack supports implementing equality as free-constructor finite-term equality rather than as a separate rewriting or paramodulation engine. That choice is semantically central and needs its own ADR.

## Decision

- Implement positive equality with occurs-checking unification over the object-language term algebra.
- Implement negative equality with a symbolic disequality store.
- Treat internal parameters as rigid proof-time constants that are inadmissible final answers.
- Keep any congruence cache derived, optional, and non-authoritative.

## Consequences

- Equality remains aligned with the intended Herbrand-style reading.
- The baseline theory stays finite-term rather than silently drifting into rational trees.
- The kernel now carries explicit free-variable equality state instead of relying on host unification side effects alone.
- Future performance work has a clean semantic baseline to preserve.

## Test Obligations

- `test/proflog/equality_test.clj`
- `test/proflog/oracle/herbrand_test.clj`

## Exit Criteria

- Reflexivity, clash, injectivity, congruence, and occurs-check behavior are covered.
- Disequality storage and recheck behavior are covered.
- Equality in subsidiary tableaux is covered.
- A bounded direct evaluator agrees with the kernel on tiny declared signatures.
