# AAR-0026: Kernel Layer Interoperation

- Date: 2026-04-29
- Related ADR: [ADR-0026](../adr/ADR-0026-kernel-layer-interoperation.md)
- Outcome: completed for conservative branch-level interoperation

## What Happened

ADR-0026 moved the ADR-0023 through ADR-0025 optimized layers from
host-facing theorem entry points into the full program kernel.

The full kernel now has a guarded `profiled-closeo` branch closer. When the
selected residual formula is compound, the pending branch and saved literals
are structurally pure propositional or equality-free first-order, equality
state is empty, delayed disequality state is empty, and no residual atom names
an active compiled Proflog clause, the branch can close through:

```clojure
proflog.kernel.propositional/proveo
proflog.kernel.first-order/proveo
```

The result is recorded as:

```clojure
(profiled propositional subproof)
(profiled first-order subproof)
```

The implementation deliberately does not call host theorem wrappers such as
`first-order/prove`, so ADR-0025's host-side Skolemization remains outside the
program-kernel interoperation path.

## Results

The Pelletier layering test now composes through the program kernel: two
nullary Proflog subproblem relations reduce to Pelletier theorem branches, and
the aggregate query succeeds by using internal first-order `proveo` delegation.

Focused dispatch tests now cover:

- propositional procedure-call residuals closing through `propositional/proveo`;
- equality-free first-order procedure-call residuals closing through
  `first-order/proveo`;
- active procedure atoms remaining foreground calls before delegation;
- equality-bearing branches staying on the full kernel;
- internal delegation avoiding host-side `prove` wrappers.

## Boundaries

The handoff guard is intentionally conservative. It may miss opportunities, but
it should not delegate branches that still need full Proflog semantics.

Three implementation boundaries are important:

- The compiled program map is not projected through core.logic. Projecting it
  walked large nested program structures and caused a stack overflow. The guard
  still uses the lexical host value for the finite set of active relation names.
- A follow-up removed the residual branch `project` from `proflog.kernel`.
  Branch dispatch is now expressed as structural logic goals: empty `sigma` and
  `neqs`, compound selected formula, target-profile formula structure, and
  disequality constraints excluding active program relation names.
- Delegation starts only from compound residual formulas (`and`, `or`, and
  quantifiers). Literal-only branches remain on the full kernel first, which
  preserves the existing first result for partial proof-shape synthesis while
  still allowing compound Pelletier-style residuals to use the optimized
  layers.

This ADR does not implement residue sharing, learned theory consequences,
memoized background attempts, or separate background time budgets. Those remain
future work if failed or repeated handoffs become costly.

## Verification

- `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test`
  - `Ran 7 tests containing 39 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test proflog.kernel-test`
  - `Ran 22 tests containing 61 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 105 tests containing 349 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier`
  - `Ran 3 tests containing 49 assertions.`
  - `0 failures, 0 errors.`
- Follow-up purity verification:
  - `rg -n "project" src/proflog/kernel.clj`
    - no matches
  - `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test`
    - `Ran 7 tests containing 39 assertions.`
    - `0 failures, 0 errors.`
  - `lein test-proflog-fast`
    - `Ran 105 tests containing 349 assertions.`
    - `0 failures, 0 errors.`

`lein test-proflog-extended` was also started, but it remained in the expensive
`proflog.list-programs-test` block for several minutes and was stopped without
being counted as a pass.
