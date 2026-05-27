# AAR-0007: Nim Correctness And Query Bounds

- Date: 2026-04-25
- Related ADR: [ADR-0007](../adr/ADR-0007-nim-correctness-and-query-bounds.md)
- Outcome: complete

## What Happened

ADR-0007 corrected the over-optimistic claim left behind by ADR-0005.

The greenfield implementation now:

- distinguishes semantic Nim correctness from bounded query helper behavior,
- keeps the greenfield `L-ground` guard structural,
- blocks plain procedure calls on unresolved internal `par` terms,
- and exposes bounded query helpers as operational probes rather than semantic
  oracles.

The current evidence for that outcome lives primarily in:

- `test/proflog/query_test.clj`
- `test/proflog/query_extended_test.clj`
- `test/proflog/kernel_test.clj`
- `worked-examples/query-boundaries.md`

## What Worked

- Inline Nim `P2` is now covered as a semantic program family rather than one
  isolated loss case. The greenfield tests distinguish the expected winning and
  losing positions directly through `query-succeeds` / `query-fails`.
- The factored `move/2` warning is recorded honestly. Ground `move/2` remains
  decidable, while the factored `win/1` cases stay unresolved. That is the
  right semantic contrast for this branch.
- The bounded query helpers now have the right contract. They return control on
  bounded slices and they report unresolved operationally instead of turning
  timeout into truth or falsity.
- The greenfield implementation preserved its structural `L-ground` discipline
  instead of importing the legacy projected guard.

## What Did Not Work

- ADR-0007 did not make bounded query helpers semantic authorities. That was
  the correct decision, but it means callers still need to understand the split
  between semidecision procedures and bounded operational probes.
- The branch did not eliminate all recursive operational limits. The factored
  `move` warning remains part of the documented behavior.

## Follow-Up

- Treat ADR-0007 as the corrected baseline for later answer and parity work.
- ADR-0008 built on this by turning the remaining semantic/test gaps into an
  explicit closure checklist.
- Future query-boundary work should preserve the ADR-0007 rule: bounded helper
  APIs are operational overlays, not semantic verdicts.
