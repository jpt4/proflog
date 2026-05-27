# AAR-0027: Transitive Relational Purity

- Date: 2026-04-29
- Related ADR: [ADR-0027](../adr/ADR-0027-transitive-relational-purity.md)
- Outcome: completed for kernel-facing formula substitution purity

## What Happened

ADR-0027 replaced the projected `proflog.subst/subst-formulao` wrapper with a
structural relation.

The old implementation projected `formula` and `env`, called host
`subst-formula`, and unified the result with `out`. That preserved forward
execution when formula and environment were already known, but it blocked
preimage modes where proof search needed to synthesize the formula,
environment, or clause body from the substituted branch obligation.

The new implementation is constructor-shaped:

- `true` and `false` pass through directly;
- `pos`, `neg`, `eq`, and `neq` substitute terms via `subst-termo`;
- `and`, `or`, `not`, and `implies` recurse structurally;
- `forall`, `once-forall`, and `exists` remove shadowed environment bindings
  with `remove-bindo` before recursing into the tied body.

Host `subst-formula` remains available as a pure compile-time helper. The
kernel-facing relation no longer depends on host projection.

## Results

New substitution tests cover the recovered modes:

- a known substituted output can be explained by an input `(var nom)` under a
  known environment;
- an environment key can be refined from a known input formula and output;
- `forall`, `once-forall`, and `exists` preserve binder shadowing in preimage
  mode;
- the previous forward binder-shadowing and nested `once-forall` cases still
  pass.

New kernel coverage demonstrates transitive recovery: a compiled program body
can now be synthesized through formal parameter substitution, not only as a
closed contradictory body.

## Remaining Boundaries

The broader recursive synthesis families are still not green:

- `proflog.synthesis-modes-test`
  - `jump(x, 0)` still exports some empty residual frontiers;
  - `down(2, y)` still returns descendants in a different order;
  - `plus(x, 1, 1)`, open `plus(x, y, z)`, and open `append(x, y, z)` still
    miss the expected recursive symbolic family.
- `proflog.recursive-synthesis-test`
  - deeper even/odd witnesses and opposite-parity refutations still fail at
    the current fuel slices.

Those failures now occur with projected formula substitution removed from the
kernel-facing path. They should be treated as remaining search, scheduling,
answer-overlay, or recursive proof-control issues, not as evidence that the
`subst-formulao` projection is still blocking them.

## Verification

- `rg -n "project" src/proflog/kernel.clj src/proflog/subst.clj`
  - no matches
- `lein test proflog.subst-test`
  - `Ran 6 tests containing 13 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.reverse-program-synthesis-test`
  - `Ran 3 tests containing 4 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `Ran 24 tests containing 39 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 108 tests containing 354 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier`
  - `Ran 3 tests containing 49 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.query-extended-test`
  - `Ran 3 tests containing 4 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.synthesis-modes-test`
  - `Ran 9 tests containing 17 assertions.`
  - `5 failures, 0 errors.`
  - failures match the remaining answer-family gaps listed above.
- `lein test proflog.recursive-synthesis-test`
  - `Ran 4 tests containing 9 assertions.`
  - `6 failures, 0 errors.`
  - failures remain in deeper recursive parity behavior.
