# ADR-0027: Transitive Relational Purity

- Status: completed
- Date: 2026-04-29
- Branch: `adr-0027-transitive-relational-purity`
- AAR: [AAR-0027](../aar/AAR-0027-transitive-relational-purity.md)
- Depends On: [ADR-0026](ADR-0026-kernel-layer-interoperation.md)

## Context

ADR-0026 removed a direct `core.logic/project` impurity from
`proflog.kernel` by converting the profiled branch dispatcher into structural
relations. That recovery exposed a larger transitive purity problem:
`proflog.subst/subst-formulao` still uses `project`.

`subst-formulao` is not a peripheral helper. The ordinary kernel and answer
overlay call it in quantifier, equality, disequality, atom, procedure-call,
branch-saving, recursive-descent, and residual-export rules. A projected
implementation in `proflog.subst` therefore becomes a kernel-wide
non-relational boundary even when `src/proflog/kernel.clj` itself no longer
contains `project`.

The current implementation is explicitly forward-only:

```clojure
(defn subst-formulao
  [formula env out]
  (project [formula env]
    (== (subst-formula formula env) out)))
```

This works when `formula` and `env` are already sufficiently known. It cannot
support modes where proof search should synthesize or partially refine the
pre-substitution formula, the environment, or a clause body from constraints on
the substituted output.

The code comment also records why the projected wrapper was introduced: earlier
nominally relational quantifier clauses were fragile and failed to substitute
through nested `once-forall` formulas. Recovering purity must preserve correct
binder shadowing for `forall`, `once-forall`, and `exists`; it is not enough to
delete the wrapper.

See
[2026-04-29 `subst-formulao` Transitive Purity Risk](../log/2026-04-29-subst-formulao-transitive-purity-risk.md)
for the triggering note.

## Decision

Replace `proflog.subst/subst-formulao` with a structural relational
substitution relation.

The relation must:

- use `subst-termo`, `subst-term*o`, `lookupo`, `unboundo`, and
  `remove-bindo`-style structural goals instead of host projection;
- cover every NNF/core formula constructor used by the kernel:
  `true`, `false`, `pos`, `neg`, `eq`, `neq`, `and`, `or`, `forall`,
  `once-forall`, and `exists`;
- preserve lexical shadowing by removing bindings for the quantifier's binding
  nom before recursing into its body;
- support the forward mode required by existing kernel execution;
- support reverse and partial modes enough to synthesize formula or environment
  preimages for substituted literals and binder bodies;
- keep host-side `subst-formula` available only as a compile-time / convenience
  pure helper, not as the kernel-facing relation.

The recovery should keep the relation well-shaped. It may require structural
formula guards or constructor-specific clauses to avoid manufacturing malformed
formula trees during reverse use.

## Consequences

The kernel's purity boundary becomes transitive rather than file-local: helper
relations called by kernel rules must also be relational.

The main benefit is mode recovery. Reverse and partial synthesis tasks can
start asking for formulas, environments, and clause bodies that produce a
substituted branch obligation, instead of being blocked by projected host
inspection.

The main risk is search expansion. A bidirectional formula substitution
relation can generate many possible preimages. The first implementation should
prefer narrowly shaped tests and conservative structural clauses over broad
enumeration. If some host-only substitution path remains useful for public
forward wrappers, it must be kept outside the semantic kernel path and named as
forward-only.

This ADR does not promise to solve every open recursive answer-family gap. It
does require that those gaps no longer be caused by a projected substitution
boundary.

## Test Obligations

- Substitution boundary tests:
  - `subst-formulao` runs forward for existing binder-shadowing cases;
  - `subst-formulao` still substitutes through nested `once-forall` bodies;
  - `subst-formulao` can synthesize at least one formula preimage from a known
    environment and substituted output;
  - `subst-formulao` can synthesize or partially refine an environment from a
    known input formula and substituted output;
  - binder shadowing works in reverse/partial modes for `forall`,
    `once-forall`, and `exists`.
- Kernel transitive-purity tests:
  - no `project` remains in `src/proflog/kernel.clj` or `src/proflog/subst.clj`;
  - a compiled program body can be synthesized through formal parameters rather
    than only as a closed contradictory body;
  - a partial or reverse procedure-call query exercises substitution through a
    clause body with open answer variables.
- Regression:
  - `lein test proflog.subst-test`
  - `lein test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `lein test proflog.synthesis-modes-test`
  - `lein test-proflog-fast`

## Exit Criteria

- `subst-formulao` no longer uses `project`.
- `rg -n "project" src/proflog/kernel.clj src/proflog/subst.clj` returns no
  matches.
- Existing forward substitution and kernel tests still pass.
- New reverse/partial substitution tests pass.
- At least one kernel-level reverse or partial synthesis regression demonstrates
  behavior that the projected wrapper could not support.
- Any remaining failing synthesis-family tests are reclassified with evidence
  that substitution projection is no longer the blocker.
