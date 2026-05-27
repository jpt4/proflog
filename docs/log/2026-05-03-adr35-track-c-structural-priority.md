# ADR-0035 Track C Structural Priority

Date: 2026-05-03

## Summary

Track C adds ordered structural residual selection to the ADR-0035 live
residual scheduler in `proflog.answer-overlay`.

The selector tries to continue a constructor-demanded negative residual before
less informative symbolic residuals. It is generic over guarded-clause IR and
does not dispatch on relation or constructor names.

## Implementation Notes

`prioritize-structural-residual-frontiero` has two cases:

- if the frontier head is already constructor-demanded, preserve the original
  order and emit `structural-residual-priority-head-demand`; and
- otherwise, use `appendo` to select a constructor-demanded residual from later
  in the frontier, move it to the front, preserve the relative order of the
  remaining residuals, and emit
  `structural-residual-priority-promote-demanded`.

The demand test is relation-adjacent at the raw live-state boundary. It checks
that the residual is a negative call to a defined program relation and that at
least one argument exposes constructor structure after walking the live
substitution.

The scheduler now wraps continuation/export selection in `conda`. That soft cut
keeps raw frontier export available for diagnostics, but only after prioritized
continuation has failed to close.

## Focused Coverage

The focused Track C tests assert that:

- the priority relation promotes a demanded residual without predicate-specific
  dispatch; and
- the scheduler returns the same completed answer state as direct fast
  continuation over the promoted frontier while carrying
  `structural-residual-priority-promote-demanded` and
  `structural-residual-continuation` proof evidence.

## Verification

Focused commands:

```text
timeout -k 5s 180s lein test :only proflog.answers-test/adr35-track-c-prioritizes-constructor-demanded-residuals
timeout -k 5s 180s lein test :only proflog.answers-test/adr35-track-c-scheduler-closes-with-priority-proof
```

Result: both passed, 2 tests / 6 assertions.

## Post-Merge Verification

After merging Track C behind Tracks A and B on
`adr-0035-relational-residual-continuation`, the combined scheduler passed:

```text
timeout -k 10s 240s lein test proflog.answers-test proflog.answer-overlay-guard-prefilter-test
timeout -k 10s 300s lein test-proflog-fast
timeout -k 10s 300s lein test-proflog-constructor-recursive
timeout -k 10s 240s lein test proflog.synthesis-modes-test
timeout -k 10s 420s lein test proflog.list-kernel-matrix-test
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-deep-nested-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-partial-output-longer-tail
```

The three probes each reported `:target-found? true` with one closed target
answer.
