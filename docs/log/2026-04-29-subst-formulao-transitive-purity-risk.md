# 2026-04-29 `subst-formulao` Transitive Purity Risk

## Finding

After recovering the ADR-0026 branch profiler from `core.logic/project`, a
larger transitive purity issue surfaced: `proflog.subst/subst-formulao` is also
implemented with `project`.

The current relation is a forward wrapper around the pure host substitution
function:

```clojure
(defn subst-formulao
  [formula env out]
  (project [formula env]
    (== (subst-formula formula env) out)))
```

That means every kernel rule that calls `subst-formulao` inherits a
non-relational boundary. This is broader than the ADR-0026 profiler issue
because formula substitution appears throughout the ordinary kernel and answer
overlay:

- quantifier rules substitute through bound bodies after removing shadowed
  environment bindings;
- equality and disequality literal rules substitute the selected formula before
  unification or disequality storage;
- positive and negative procedure-call rules substitute atoms before call
  lookup, branch saving, or residual deferral;
- answer-mode recursive descent and residual export use the same substitution
  boundary.

## Likely Behavioral Cost

The impurity does not necessarily break ordinary forward execution. When a
compiled clause body and call environment are already known, the projected pure
function can still compute the substituted formula.

The lost modes are the reverse and partial modes where the relation should be
able to solve any of:

- the pre-substitution formula,
- the environment,
- a clause body containing formal parameters,
- a query shape whose substituted literal/body is constrained later by equality
  or procedure-call search.

Concrete suspect surfaces already exist in the tests:

- reverse formula/query synthesis under an environment;
- compiled program body synthesis that should discover formulas mentioning
  formal parameters;
- partial recursive procedure-call synthesis such as `jump(x, 0)`;
- recursive open queries such as `down(2, y)` and `down(x, 1)`;
- open arithmetic/list families such as `plus(x, y, z)` and
  `append(x, y, z)`;
- deeper recursive parity witness and refutation tasks.

Some of those are already known baseline failures. `subst-formulao` may not be
the only cause, but it is now on the critical path and should be treated as a
kernel-purity blocker until proven otherwise.

## ADR Direction

The next ADR should recover transitive relational purity by replacing the
projected formula-substitution wrapper with a genuinely structural relation.

The recovery should preserve the binder-shadowing behavior that motivated the
current wrapper. The original comment says earlier nominally relational
quantifier clauses failed to substitute through nested `once-forall` formulas,
so the new ADR must treat binder-aware substitution as the core problem, not as
a cosmetic refactor.

The first tests should be written at the substitution boundary before touching
the kernel:

- `subst-formulao` can run backward from substituted output to plausible input
  formulas;
- `subst-formulao` can synthesize or partially refine environments;
- binder shadowing still works for `forall`, `once-forall`, and `exists`;
- nested `once-forall` bodies remain covered.

Then the kernel tests should exercise the transitive behavior:

- program body synthesis through formal parameters;
- reverse/partial procedure-call synthesis with recursive bodies;
- at least one currently failing open recursive family if the substitution fix
  makes it reachable without unrelated search-policy changes.

## Risk

A fully relational formula substitution relation can introduce broader search
than the projected wrapper. The ADR should prefer a structural relation that is
mode-safe but still shaped enough to avoid synthesizing arbitrary malformed
formula trees.

If a fully bidirectional relation is too expensive, the acceptable fallback is
not to keep hidden `project` in the kernel. The fallback should be an explicit,
documented forward-only overlay path plus a pure structural kernel path.

## Resolution

ADR-0027 completed the recovery. `subst-formulao` is now structural and
relational, with binder shadowing handled by `remove-bindo` before recursive
substitution through `forall`, `once-forall`, and `exists` bodies.

The remaining open recursive synthesis failures still reproduce after the
projected substitution boundary is gone, so they should be investigated as
search / answer-overlay / recursive proof-control issues rather than as
`subst-formulao` purity fallout.
