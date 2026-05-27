# ADR-37 L-ground Constraint Probe

Worker F tested whether the production `kernel-support/l-ground-termo`
structural guard can be replaced or accelerated by reusable core.logic
constraint machinery.

## Variants

- `l-ground-no-paro` uses `treec` plus `predc` to reject every discovered
  `(par nom)` node.
- `l-ground-predco` is a negative-control `predc` guard that treats open nested
  variables as safe.
- `l-ground-root-and-no-paro` combines a root `var`/`app` structural relation
  with the `treec` no-`par` constraint.

## Findings

The `treec` variant is sound for the absence part of L-groundness. It leaves a
residual constraint on open terms, accepts later refinement to an object term,
and rejects later refinement to a direct or nested `(par nom)`.

The `predc`-only variant is not acceptable. It can accept an outer application
while a child is still open, remove the constraint, and then allow that child to
become `(par nom)`. This is the same timing class as the rejected legacy
`project` guard.

The hybrid root-shape plus `treec` absence constraint is useful but incomplete.
It rejects `par` and avoids the unsound projection timing, but it does not prove
that every application argument is itself a Proflog term. For example,
`(app f not-a-term)` has the right root tag and contains no `par`, so the hybrid
admits it while the production structural relation rejects it.

A direct production test with `(kernel-support/l-ground-termo term)` after
`term = (app s child)` but before `child` is refined was not kept as an
executable test: that ordering drives the structural relation into recursive
term generation. The finite test binds the child first and confirms that the
production relation remains the strict reference. This is useful evidence for a
future constraint: persistent child watches can improve delayed-refinement
behavior, but only if they preserve the full object-term grammar.

## Recommendation

Do not replace `kernel-support/l-ground-termo` with the current `treec` probe.
Keep the production relation structural for strict Proflog object-language
recognition.

Pursue `treec` only as a reusable Proflog-local absence constraint when a caller
already has term-shape evidence from another relation. A future core.logic
extension could make this more attractive if it supported context-aware tree
constraints or a reusable recursive constraint that can watch child positions
without re-encoding the Proflog term grammar at every call site.
