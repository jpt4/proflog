# AAR-0011: Open-Answer Relationality

- Date: 2026-04-25
- Related ADR: [ADR-0011](../adr/ADR-0011-open-answer-relationality.md)
- Outcome: complete

## What Happened

ADR-0011 changed the default greenfield open-answer path so it stayed closer to
direct relational kernel descent:

- top-level literal program queries entered through direct kernel query entry,
- `call-depth` became recursive budget below the query boundary,
- descent was tried before residual deferral while budget remained,
- and exported answers were ranked by completion so later closed answers could
  displace earlier symbolic frontiers.

This was a real architectural improvement. It removed the old eager first-layer
query unfolding from the default answer path.

## What Worked

- The default answer path became more relationally pure than the earlier staged
  answer-layer rewrite.
- Append-family behavior improved materially under the direct-entry plus
  completion-ranked path.
- The repo gained clearer diagnostics about what the open-answer path was doing
  at each `call-depth`.
- ADR-0011 drew an honest line between:
  - answer-surface improvement,
  - and raw kernel closure.

## What Did Not Work

- ADR-0011 did not close full legacy parity by itself.
- `reverse([a,b], r)` still failed to export the closed witness from the raw
  generic path, even after longer probes.
- Inverse `append` improved, but still left enough of a gap that the project
  needed a separate closed-answer parity mode and later answer-surface
  normalization work.

## Follow-Up

- ADR-0012 isolated the closed-answer parity mode instead of pretending the
  generic path was already enough.
- ADR-0013 improved the generic answer surface further through canonicalization
  and known-family pull-in.
- ADR-0014 and ADR-0015 later used the ADR-0011 findings to sharpen the
  distinction between raw core behavior and answer-oriented overlays.
