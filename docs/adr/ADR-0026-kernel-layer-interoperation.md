# ADR-0026: Kernel Layer Interoperation

- Status: completed
- Date: 2026-04-29
- Branch: `adr-0026-kernel-layer-interoperation`
- AAR: [AAR-0026](../aar/AAR-0026-kernel-layer-interoperation.md)
- Depends On: [ADR-0025](ADR-0025-pelletier-lean-search-policy.md)

## Context

ADR-0023 through ADR-0025 added optimized propositional and equality-free
first-order theorem layers. Those layers are effective when `kernel/prove`
receives a formula directly, but they are not yet useful inside general Proflog
program execution.

The current gap is captured by
`test/proflog/pelletier_layering_test.clj`: two nullary Proflog relations have
clause bodies whose negated procedure-call obligations reduce to Pelletier
theorem branches. Each branch closes through the first-order theorem layer when
proved directly, but the aggregate program query remains on the full program
kernel.

This is an architectural problem, not merely a benchmark problem. If a Proflog
program reduces a subgoal to a pure propositional or equality-free first-order
branch, the program prover should be able to use the existing specialized
kernel component while preserving the full kernel as the semantic authority for
procedure calls, equality, disequality, reverse mode, and partial synthesis.

The foreground/background tableau literature supports this shape. The full
program kernel should remain the foreground reasoner. Optimized layers should be
proof-producing background branch closers, not opaque host-side oracles. See
[2026-04-29 Tableau Foreground/Background Lessons](../log/2026-04-29-tableau-foreground-background-lessons.md).

## Decision

Add conservative branch-level interoperation inside the full kernel.

The full kernel will try a profiled branch closure before expanding a branch
with the general Proflog rules:

1. If the current residual agenda and saved literals are pure propositional,
   call `proflog.kernel.propositional/proveo`.
2. If the current residual agenda and saved literals are equality-free
   first-order and contain no atoms defined by the active compiled program,
   call `proflog.kernel.first-order/proveo`.
3. Otherwise, continue with the existing full kernel.

The handoff is total theory reasoning in the tableau sense: the delegated layer
either closes the branch and returns a proof, or the full kernel remains
available through ordinary search. The first implementation will not introduce
residue-producing partial delegation.

The delegated proof is retained under an explicit proof tag:

```clojure
(profiled propositional subproof)
(profiled first-order subproof)
```

The internal dispatcher must not call host wrappers such as
`first-order/prove`, because those wrappers perform forward theorem
preparation such as host-side Skolemization. Internal interoperation must use
proof-producing relations.

## Guards

Delegation is allowed only when it is conservative:

- the residual branch is structurally known to be in the target profile;
- equality substitution state is empty;
- delayed disequality state is empty;
- the delegated branch contains no relation symbol with an active compiled
  Proflog clause;
- the selected residual formula is compound, so literal-only proof-shape
  synthesis keeps the existing full-kernel first result;
- the target layer receives the existing proof environment and saved literals;
- full-kernel `sigma` and `neqs` are returned unchanged.

The first version may miss valid optimization opportunities. False negatives are
acceptable; false positives are not.

## Consequences

This should make the optimized layers useful to Proflog programs without
collapsing the architecture into one large kernel.

The main risk is resource control. Background branch closure behaves like a
macro proof step from the full kernel's perspective. That is consistent with the
foreground/background tableau pattern, but it means future work should consider
incremental cache keys and background budgets so early failed handoffs are not
wasted.

The second risk is proof-term drift. Tests should assert the explicit
`profiled` proof boundary so delegated closures remain inspectable.

## Test Obligations

- Program-kernel tests:
  - a propositional procedure-call subbranch closes through the propositional
    relation and records a profiled proof;
  - a first-order procedure-call subbranch closes through the first-order
    relation and records a profiled proof;
  - the Pelletier aggregate layering test now succeeds through internal
    first-order delegation.
- Boundary tests:
  - internal dispatch uses `proveo`, not host-side `prove` wrappers;
  - equality-bearing program branches stay on the full kernel;
  - branches containing active procedure-call atoms are not delegated before the
    full kernel expands those calls.
- Regression:
  - `lein test proflog.pelletier-layering-test`
  - `lein test proflog.kernel.dispatch-test`
  - `lein test-proflog-fast`

## Exit Criteria

- The Pelletier aggregate program from the layering-gap test succeeds.
- Delegated program subproofs contain explicit profiled proof tags.
- Program proof search can dispatch to propositional and first-order layers
  without calling host wrappers or using opaque proof oracles.
- Existing fast greenfield regressions remain green.
