# ADR-0009: Legacy Program Family Closure And Worked Examples

- Status: completed
- Date: 2026-04-21
- Branch: `adr-0009-legacy-program-closure`
- AAR: [AAR-0009](../aar/AAR-0009-legacy-program-closure.md)

## Context

ADR-0008 established a checklist for closing greenfield test gaps relative to
the legacy `test/cljtap` suites. That branch now has enough concrete coverage
that the remaining work is no longer just "find gaps" work.

The next phase is to:

- make the current legacy-to-greenfield program-family boundary explicit,
- document the exercised semantics with worked examples,
- deepen greenfield coverage where a legacy family is already present but still
  materially weaker, and
- decide which legacy-only families should become real greenfield program
  suites rather than staying comparison material.

This phase also needs to record semantic and performance findings honestly. Some
legacy families may reveal correctness gaps, operational limits, or both.

## Decision

- Create a maintained legacy-to-greenfield program parity matrix in `docs/`.
- Treat worked examples as first-class deliverables alongside committed tests.
- Close program-family gaps in this order:
  1. document all currently extant greenfield families with worked examples,
  2. deepen families already present in greenfield until they are comparable to
     legacy coverage or explicitly bounded,
  3. build legacy-only program families where justified and feasible.
- Record semantic and performance findings as they are discovered instead of
  hiding them behind broad "future work" language.
- Keep the legacy implementation as reference material only; every greenfield
  convergence step must still be justified on greenfield semantics and
  behavior.

## Consequences

- The repository gains an explicit bridge between legacy section codes and the
  greenfield suite structure.
- Worked examples become part of the review surface for semantic changes, not
  just tests and code.
- Some legacy families may remain deferred or bounded if the greenfield kernel
  exposes real semantic or operational blockers; those blockers must be written
  down precisely.
- Commit history on this branch should stay family-oriented so each completed
  unit leaves behind both tests and an explanatory example set.

## Initial Scope

- `docs/LEGACY_PROGRAM_PARITY_MATRIX.md`
- `worked-examples/`
- `test/proflog/integration_families_test.clj`
- `test/proflog/list_programs_test.clj`
- `test/proflog/quantified_programs_test.clj`
- `test/proflog/synthesis_modes_test.clj`
- any new greenfield namespaces needed for legacy-family closure

## Exit Criteria

- The parity matrix is current and cites both legacy and greenfield locations.
- Every currently extant greenfield program-family test namespace has worked
  examples for its committed semantics.
- Every legacy family already present in greenfield is either:
  - brought to comparable depth, or
  - explicitly documented as blocked or deliberately bounded.
- Legacy-only families that are promoted into greenfield have both tests and
  worked examples.
- Semantic correctness and performance findings discovered during this phase are
  recorded for future ADR/AAR follow-on work.
