# ADR-0001: Greenfield Foundation

- Status: completed
- Date: 2026-04-18
- Branch: `greenfield`
- AAR: [AAR-0001](../aar/AAR-0001-greenfield-foundation.md)

## Context

The repository already contains experimental Proflog work, but it did not contain the process infrastructure required by `development-practices.md`: a mission statement, an ADR sequence, AAR scaffolding, a concrete execution plan, a semantic-variant policy, and a test matrix.

Without that infrastructure, later implementation work would either drift back into ad hoc evolution of `cljtap.*` or violate the stated ADR-first and TDD-first workflow.

## Decision

- Bootstrap the greenfield process directly on `greenfield`.
- Treat `src/cljtap/` and `test/cljtap/` as reference material, not as the namespace tree for the authoritative implementation.
- Define the target greenfield namespace layout under `src/proflog/` and `test/proflog/`.
- Install repo-native planning documents: `README.md`, `MISSION.md`, `docs/EXECUTION_PLAN.md`, `docs/TEST_MATRIX.md`, `docs/SEMANTIC_VARIANTS.md`, ADR records, and AAR records.

## Consequences

- The project now has an explicit implementation order and merge policy.
- Future feature work has a documented place to live and a documented gate for landing.
- Some duplication with existing design notes is intentional, because process control and execution planning were previously missing.
- No production greenfield code is created by this ADR; that work begins with ADR-0002.

## Test Obligations

This ADR is documentation-only. No failing code tests are introduced here.

## Exit Criteria

- Mission and execution documents exist in the repository.
- ADR and AAR templates exist.
- The initial ADR sequence for the baseline implementation is recorded.
- The greenfield namespace target is explicit.
