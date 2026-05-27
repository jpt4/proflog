# AAR-0009: Legacy Program Family Closure And Worked Examples

- Date: 2026-04-25
- Related ADR: [ADR-0009](../adr/ADR-0009-legacy-program-closure.md)
- Outcome: complete

## What Happened

ADR-0009 turned the legacy/greenfield comparison into maintained repo
artifacts:

- `docs/LEGACY_PROGRAM_PARITY_MATRIX.md`
- the `worked-examples/` family documentation
- deeper greenfield family regressions across:
  - `integration_families_test.clj`
  - `list_programs_test.clj`
  - `quantified_programs_test.clj`
  - `synthesis_modes_test.clj`

The branch outcome is visible in the current parity matrix. The repository can
now say, family by family, which greenfield surfaces are comparable, partial,
or absent.

## What Worked

- The parity matrix is a real maintenance tool rather than a one-off note. It
  now records the major legacy program families and the current greenfield
  surface for each.
- Worked examples became part of the project’s semantic documentation rather
  than optional commentary.
- The currently extant greenfield families were deepened enough to support
  explicit "Comparable" judgments for major families such as:
  - `P1` even/odd,
  - inline Nim and the factored `move` warning,
  - `member`,
  - `tc`,
  - `plus`,
  - quantified specification families such as `sorted2`, `subset`, and
    `acyclic`.

## What Did Not Work

- Legacy list-synthesis parity did not fully close at the raw relational answer
  level during ADR-0009. That work spilled forward into ADR-0011, ADR-0012,
  and ADR-0013.
- `GV` and `FD` remained absent as stable greenfield families. The matrix now
  records that absence explicitly rather than pretending those rows are simply
  "future performance work."

## Follow-Up

- ADR-0011 took over the default open-answer execution question exposed by the
  list-family gaps.
- ADR-0014 took over the hard unsatisfied legacy families, especially `GV` and
  later `FD`.
- Keep the parity matrix and worked examples current when later ADRs change the
  observable surface of a family.
