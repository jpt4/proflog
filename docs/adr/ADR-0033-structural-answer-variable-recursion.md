# ADR-0033: Structural Answer-Variable Recursion

- Status: accepted
- Date: 2026-05-01
- Branch: `adr-0033-structural-answer-variable-recursion`
- AAR: pending
- Depends On:
  - [ADR-0031](ADR-0031-list-family-kernel-generalization.md)
  - [ADR-0032](ADR-0032-core-logic-performance.md)

## Context

ADR-0031 advanced the list-family goal by adding guarded-clause IR and a
constructor-recursive sidecar. That sidecar closes representative blocked
reverse rows, but ADR-0031's nominal exit criteria remain unsatisfied because
the ordinary raw answer path still does not produce the carried closed targets.

ADR-0032 explored the next plausible layer down: the `core.logic` host
substrate. The branch added runtime host verification, a 1.1.1 upgrade lane, a
source-overlay lane, count probes, source comparison, and several generic host
patch attempts. Those attempts did not close the carried targets. The later
legacy/greenfield trace comparison showed a stronger architectural signal:
legacy succeeds because open answer variables stay live through ordinary
recursive `proveo`, while greenfield's raw answer path can export procedural
residual frontiers before those variables are refined.

The carried failures remain:

- `reverse-input-flat`: `reverse(r, [b,a])`;
- `reverse-output-nested-longer`: `reverse([[a],[b],[c]], r)`;
- `reverse-partial-output-tail`: `reverse([a,b,c], cons(c, r))`;
- `recursive-reverse-mode-query-synthesizes-descendants`; and
- `composed-partial-mode-query-traverses-multiple-calls`.

ADR-0033 keeps ADR-0031's list-family goal, but moves the implementation
strategy back to greenfield's proof/answer frontier.

## Decision

Implement a structural answer-variable recursion path in the greenfield answer
overlay.

The branch should preserve legacy's useful operational property without copying
legacy's projected guard:

- legacy behavior to learn from: open answer variables remain active across
  recursive calls and bind through later equality / constructor constraints;
- legacy behavior not to copy: "projection did not find `(par ...)`, therefore
  treat the call as ground enough."

The greenfield version must be structural:

- track user answer variables explicitly inside raw answer states;
- classify unresolved procedure-call residuals by structural safety before
  export;
- keep structurally safe answer variables live through recursive descent under
  explicit fuel and call-depth budgets;
- allow generic equality, disequality, and constructor constraints to refine
  those variables;
- export procedural residuals only when the call is structurally unsafe or a
  real budget frontier has been reached; and
- preserve base-before-recursive answer ordering where clause order and
  derivation depth support it.

The accepted implementation must not encode knowledge of `append`, `reverse`,
`cons`, `null`, or any specific list-family symbol in production logic.

## Required Capabilities

### 1. Structural Safety Classification

Add a conservative classifier for open terms in procedure-call residuals.

At minimum, it should distinguish:

- closed object-language terms;
- declared answer variables;
- proof-local variables already present in the branch state;
- constructor-shaped terms whose leaves are structurally safe; and
- unsafe open structures, predicate variables, or program-shape variables that
  should remain residual.

The classifier may live in the answer overlay as structural AST analysis over
greenfield terms. It must not use `core.logic/project` in a kernel-facing
relation.

### 2. Pre-Export Residual Completion

Residual completion must happen before answer records are exported.

Earlier experiments showed that reconstructing continuation state from
exported records is too lossy. ADR-0033 must therefore operate over raw
answer-state branch context: substitutions, disequalities, residuals, pending
formula state, answer variables, fuel, call depth, and proof metadata.

### 3. Live Answer-Variable Descent

When a residual procedure call is structurally safe, the answer path should
continue descent instead of exporting the residual immediately. Answer
variables under constructors, such as the `r` in `cons(c, r)`, must remain
eligible for refinement by subsequent equality and constructor constraints.

### 4. Constructor-Recursive Integration

The constructor-recursive sidecar's generic insight should move toward the
ordinary raw answer path:

- guarded constructor-recursive calls may descend through outer constructors;
- nested elements should be treated opaquely unless the program recurses into
  them; and
- successful completions should produce ordinary raw answer records with proof
  evidence, not sidecar-only success.

The sidecar may remain as a diagnostic or compatibility layer during the ADR,
but the ADR exit criteria require ordinary raw answer-path closure.

### 5. Stable Answer Ordering

The answer priority layer should preserve base-before-recursive ordering when
two answers are otherwise comparable. This directly targets
`down(2, y)`, where greenfield currently finds the correct set but returns the
recursive answer before the base answer.

Ordering metadata may include:

- clause order;
- alternative order;
- derivation depth;
- number and kind of residuals; and
- whether a record was completed from a residual frontier.

## Implementation Order

1. Add diagnostics showing which carried failures residualize structurally safe
   calls before export.
2. Add focused tests for structural safety classification on closed terms,
   answer variables, proof-local variables, constructor-contained answer
   variables, and unsafe open program structures.
3. Introduce pre-export residual completion for a single safe procedure-call
   residual while preserving existing answer records.
4. Integrate guarded constructor-recursive descent into that ordinary raw path.
5. Add ordering metadata and base-before-recursive prioritization.
6. Re-run the ADR-31/ADR-32 carried matrix and synthesis-mode gates.
7. Write an AAR classifying any remaining failures by answer-frontier cause.

## Exit Criteria

ADR-0033 satisfies the carried ADR-31 goal only if all of the following hold:

- `reverse-input-flat` closes through the ordinary raw answer path;
- `reverse-output-nested-longer` closes through the ordinary raw answer path;
- `reverse-partial-output-tail` closes through the ordinary raw answer path;
- `proflog.synthesis-modes-test` passes;
- `proflog.list-kernel-matrix-test` passes;
- `lein test-proflog-constructor-recursive` remains green;
- `lein test-proflog-fast` remains green; and
- production code remains generic and projection-free in the kernel-facing
  path.

Passing through public list-family materialization or a sidecar-only layer does
not satisfy the first three criteria.

## Test Obligations

Minimum commands:

```text
lein test-proflog-fast
lein test-proflog-constructor-recursive
timeout 300s lein test proflog.list-kernel-matrix-test
timeout 240s lein test proflog.synthesis-modes-test
timeout 240s lein probe-proflog-list-kernel-matrix reverse-input-flat
timeout 240s lein probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 240s lein probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

Add focused tests for:

- structural safety classification;
- live answer variables under constructors;
- residual completion before export;
- non-disequality procedural residual suppression for `jump(x, 0)`;
- base-before-recursive ordering for `down(2, y)`; and
- a non-list constructor-recursive program proving the path is not
  list-specific.

## Constraints

- No production code may dispatch on list-family predicate or constructor
  symbols.
- No new `core.logic/project` may enter the kernel-facing proof path.
- Do not hide failures behind public answer materialization.
- Do not reconstruct continuation from already exported records when raw branch
  state is required.
- Do not drop residuals silently. A procedural residual may disappear only
  because a recorded completion attempt closed it, proved it redundant, or
  exhausted an explicit budget.

## Risks

- Residual completion can duplicate search if it does not reuse raw branch
  context carefully.
- Base-first ordering can regress answer families that intentionally prefer
  deeper closed completions unless ordering metadata is scoped to comparable
  records.
- A classifier that is too permissive may recreate legacy impurity; a
  classifier that is too conservative will leave the carried failures
  unchanged.
- Integrating the constructor-recursive sidecar too mechanically may import a
  second proof path instead of improving the ordinary raw answer path.

## References

- [Legacy / Greenfield Failure Traces](../log/2026-05-01-legacy-greenfield-failure-traces.md)
- [Greenfield Lessons From Legacy Traces](../log/2026-05-01-greenfield-lessons-from-legacy-traces.md)
- [Structural Answer-Variable Recursion Architecture](../log/2026-05-01-structural-answer-variable-recursion-architecture.md)
- [ADR-33 Structural Completion Progress](../log/2026-05-01-adr33-structural-completion-progress.md)
- [Intensified List-Family Matrix](../log/2026-05-01-list-family-intensified-matrix.md)
- [Three-Element Reverse Input-Synthesis Trace](../log/2026-05-01-three-element-reverse-trace.md)
- [Language Namespace Spec](../LANGUAGE_NAMESPACE_SPEC.md)
- [ADR-32 Concurrent Core.logic Probe Evaluation](../log/2026-05-01-adr32-concurrent-core-logic-probe-evaluation.md)
