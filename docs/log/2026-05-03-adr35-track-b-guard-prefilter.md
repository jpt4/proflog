# ADR-0035 Track B Guard Prefiltering

Date: 2026-05-03

## Summary

Track B adds a relational guard-prefilter to raw live-state structural
residual continuation in `proflog.answer-overlay`.

The prefilter runs after guarded scope opening and before recursive descent
through guarded calls. That makes guard viability an explicit gate instead of
an incidental first step inside alternative closure.

## Implementation Notes

The new private relation, `prefilter-structural-guardso`, handles guarded
formula lists structurally:

- equality guards are substituted through the guarded-call environment and
  saturated with `equality/unify-termo`;
- newly added equality bindings must target proof-local variables through
  `support/proof-bindingso`;
- saved disequalities must remain stable after equality saturation; and
- disequality guards are accepted only when
  `support/rigid-different-termo` proves a permanent constructor difference.

`close-structural-guarded-alternativeo` now emits
`structural-residual-guard-prefilter` proof evidence and only descends into
the alternative's recursive calls after the prefilter succeeds.

This is intentionally conservative. Symbolic disequality guards that would
need to extend the live `neqs` store are not accepted by this narrow prefilter
yet. That keeps Track B focused on rejecting impossible alternatives before
recursive descent without broadening raw continuation search.

## Focused Coverage

The focused Track B tests use a generic sentinel recursive relation, not a
list-family name. The first guarded alternative for `p(one)` has a
constructor-clashing equality guard and a recursive `sentinel` call. The test
asserts that successful continuation carries guard-prefilter proof evidence,
does not descend into `sentinel`, and still reaches a later viable guarded
alternative.

The tests also exercise the private guard-prefilter relation directly:

- `eq(one, zero)` yields no results;
- `eq(one, one)` remains viable; and
- `neq(one, zero)` remains viable through rigid constructor disequality.

## Verification

Initial focused command:

```text
timeout -k 5s 90s lein test proflog.answer-overlay-guard-prefilter-test
```

Result: passed, 2 tests / 13 assertions.
