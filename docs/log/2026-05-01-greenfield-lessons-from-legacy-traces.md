# 2026-05-01 Greenfield Lessons From Legacy Traces

## Context

The worked legacy/greenfield traces show that ADR-32's remaining failures are
not primarily `core.logic` host allocation problems. They are concentrated at
the proof/answer frontier:

- legacy lets open host logic variables participate in ordinary `proveo`
  recursion;
- greenfield routes open answers through explicit answer-state export,
  residual deferral, ordering, and overlay layers; and
- the constructor-recursive sidecar already demonstrates that the three carried
  reverse rows are recoverable by a generic guarded-recursion technique.

## Lessons

### 1. Do not export procedural residuals too early

`jump(x, 0)` has the right ground set in greenfield, but one answer still
carries a deferred `neg(step(...))` residual. Before final answer export,
greenfield should try a generic residual-completion pass for remaining
procedure-call residuals while fuel and call-depth remain.

### 2. Preserve base-before-recursive answer order where it is natural

`down(2, y)` finds the same two descendants as legacy, but ranks the recursive
answer before the base answer. Legacy's direct clause execution tries the base
alternative first, then recursion. Greenfield prioritization should consider
clause order, alternative order, and derivation depth before later heuristics.

### 3. Promote constructor-recursive sidecar lessons into the ordinary raw path

The constructor-recursive sidecar closes all three carried reverse rows. Its
accepted insight is generic: guarded constructor-recursive calls can descend
through outer constructors while treating nested elements opaquely. The current
gap is that ordinary raw answer export does not yet use that competence.

### 4. Let answer variables flow through recursion instead of becoming residual frontiers

Legacy's projected `l-ground` guard admits bare host logic variables because
they do not contain `(par ...)`. That is not a purity model for greenfield to
copy directly, but it exposes a useful operational property: open answer
variables remain live across recursive procedure calls and can be bound by
later equality constraints inside the same proof path.

Greenfield should preserve that operational benefit in a structural way. A
query such as `reverse(r, [b,a])` should not be forced into a residual frontier
merely because `r` is open. If the call arguments satisfy a structural safety
predicate, the answer layer should keep descending and allow recursive calls and
equality constraints to refine `r` until either a closed answer is reached or a
real budget boundary is hit.

The key distinction is:

- unsafe: "treat every open term as ground enough because projection did not
  find `(par ...)`";
- promising: "recognize structurally safe open answer positions, keep them in
  the active proof state, and let generic equality/constructor constraints bind
  them."

### 5. Prioritize closed answers only after real completion attempts

Greenfield's explicit residual and budget state is cleaner than legacy's direct
host-variable flow, but it creates premature stopping points. The answer layer
should distinguish a temporarily residual answer from an irreducibly residual
answer. Closed-answer prioritization should happen after generic completion has
had a chance to discharge procedural residuals.

## Implication

ADR-32's next productive direction is likely a greenfield answer-frontier
repair, not another small `core.logic` host patch:

- residual completion for procedure-call residuals;
- stable base-first / shallower-first answer ordering; and
- integration of constructor-recursive descent into the ordinary raw answer
  path, especially for structurally safe open answer variables.
