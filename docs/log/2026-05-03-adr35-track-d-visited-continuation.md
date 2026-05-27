# ADR-0035 Track D Visited Continuation

Date: 2026-05-03

## Summary

Track D adds an active-call visited table to the fast ADR-0035 residual
continuation state in `proflog.answer-overlay`.

The table prevents recursive continuation from reopening the same
object-language ground walked call while that call is already active. It is
intentionally narrower than full answer tabling: it prunes active ground
cycles, but it does not memoize symbolic answer sets.

## Implementation Notes

`fast-continuation-solve-atom` now walks the atom through the current
substitution, computes a canonical call key for object-language ground calls,
enters that key before spending continuation fuel, and removes it after a
successful call returns.

Open symbolic calls, including calls with unresolved internal parameters, are
deliberately not entered in the table. A broader key that collapsed
`reverse(var, var)` shapes pruned legitimate reverse synthesis, because those
calls can still make progress through surrounding substitutions.

Active reentry for the same ground key fails the current guarded alternative.
Later alternatives remain available, and a later sequential duplicate call can
still run after the first call has returned.

## Focused Coverage

The Track D tests use a generic `loop`/`twice` program:

- `loop(zero)` has a self-recursive alternative before a base equality
  alternative. The fast continuation closes through the base alternative with
  one visible call expansion and `structural-residual-visited-enter` evidence.
- `twice(zero)` calls `loop(zero)` twice in sequence. Both calls still close,
  showing that the active key is left after success rather than becoming a
  global prune.

## Verification

Focused commands:

```text
timeout -k 5s 180s lein test :only proflog.answers-test/adr35-track-d-prunes-active-recursive-reentry
timeout -k 5s 180s lein test :only proflog.answers-test/adr35-track-d-leaves-active-key-after-success
```

Result: both passed, 2 tests / 11 assertions.

Full project commands after the Track D change:

```text
timeout -k 5s 600s lein test-proflog-fast
timeout -k 5s 900s lein test-proflog-extended
```

Result: fast passed, 117 tests / 380 assertions. Extended passed, 68 tests /
203 assertions. The extended rerun required refreshing stale bounded fuel
values for deeper recursive parity and Nim synthesis probes; those probes still
closed at the higher fuel bounds.
