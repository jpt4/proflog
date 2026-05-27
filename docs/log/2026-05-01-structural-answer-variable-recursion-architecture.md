# 2026-05-01 Structural Answer-Variable Recursion Architecture

## Context

The legacy/greenfield trace comparison led to one specific lesson that deserves
architecture before implementation: greenfield should preserve legacy's useful
answer-variable flow without copying legacy's projected `l-ground` guard.

Legacy lets bare host logic variables enter ordinary `proveo` recursion and
binds them later through equality and constructor constraints. That is why
queries such as `reverse(r, [b,a])` can close in legacy: `r` remains live while
recursive `reverse` and `append` calls refine it.

Greenfield has the cleaner object language, but its raw answer path can turn
similar open answer variables into exported procedural residuals too early.
ADR-31's constructor-recursive sidecar proves the carried reverse rows are
recoverable by generic guarded-recursion analysis. ADR-32's host experiments
suggest the remaining problem is not a small `core.logic` substrate patch.

## Structural Variant

The structural greenfield analogue of legacy's behavior should be:

- track user answer variables explicitly in raw answer states;
- classify procedure-call residuals by structural safety before export;
- keep structurally safe answer variables live through recursive descent;
- allow ordinary equality and constructor constraints to bind those variables;
- export procedural residuals only after a real budget boundary or an unsafe
  structural condition; and
- preserve stable base-before-recursive ordering where clause structure makes
  that order meaningful.

This is not the legacy projected guard. The unsafe legacy rule is:

```text
projection did not find (par ...), so proceed
```

The greenfield rule should instead be:

```text
the call's open positions are declared answer variables, proof-local variables,
or constructor-shaped terms whose open leaves are structurally safe; therefore
the answer overlay may continue this residual under explicit fuel/call-depth
budgets before final export
```

## Architectural Boundary

The completion must happen before exported records lose branch context. Earlier
experiments showed that reconstructing the next search stage from exported
answer records is too lossy for composed and constrained queries. The new work
therefore belongs in the raw answer-state path, not as a replay over already
exported records.

The work may reuse the constructor-recursive sidecar's guarded-clause analysis,
but its accepted behavior must be visible through the ordinary raw answer path.
The public list-family materializer must not be treated as evidence that this
ADR is complete.

## Design Consequences

- `reverse(r, [b,a])` should keep `r` live while recursive calls and append
  constraints refine it, rather than exporting a residual frontier.
- `reverse([[a],[b],[c]], r)` should descend through the outer list recursion
  while treating nested list elements opaquely.
- `reverse([a,b,c], cons(c, r))` should keep the tail answer variable live
  under the output constructor.
- `jump(x, 0)` should attempt residual completion for remaining `step`
  residuals before export.
- `down(2, y)` should preserve base-before-recursive ordering once the same
  answer set is available.

## ADR

This architecture is promoted to
[ADR-0033: Structural Answer-Variable Recursion](../adr/ADR-0033-structural-answer-variable-recursion.md).
