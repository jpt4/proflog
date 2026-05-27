# ADR-0006: Answer Discipline And Variant Boundary

- Status: proposed
- Date: 2026-04-18
- Branch: `adr-0006-answers-variants`
- AAR: pending

## Context

Open queries are the point where lifted relational use meets the semantic warning that answers must remain in the original language `L`. This ADR also separates baseline semantics from later optimizations or alternate theories.

## Decision

- Export answers as explicit records containing admissible substitutions, proof terms, and residual constraints when needed.
  Residual constraints may include deferred procedure-call obligations, not only disequalities.
- Treat bounded ground-term enumeration as a non-generic materialization helper, not as the baseline answer semantics.
- Reject any answer that contains internal parameters or undeclared symbols.
- Keep semantic and runtime variants documented in one place and off by default unless named.

## Consequences

- Open-query behavior becomes inspectable and reviewable.
- Later optimization work has to declare whether it changes theory, completeness, or only runtime shape.
- The project gets a clean boundary between baseline Proflog and experiments.

## Test Obligations

- `test/proflog/answers_test.clj`
- additional open-query cases in `test/proflog/query_test.clj`

## Exit Criteria

- Exported answers respect the declared language boundary.
- Residual disequalities and deferred call obligations are preserved when full materialization is not requested.
- Proof evidence remains attached or replayable.
- Any non-default execution profile is explicitly named and documented.
