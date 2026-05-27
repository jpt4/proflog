# AAR-0023: Profiled Kernel Layers

- Date: 2026-04-28
- Related ADR: [ADR-0023](../adr/ADR-0023-profiled-kernel-layers.md)
- Outcome: completed for entry-only pure-propositional dispatch

## What Happened

ADR-0023 added a formula-profiled theorem-proving entry path without changing
the full Proflog kernel relation.

The new `proflog.formula-profile` namespace classifies normalized formulas by
structural capability: pure propositional, equality-free first-order,
equality-bearing, or unsupported. The classifier is not a theorem prover and
does not inspect Pelletier problem ids.

The new `proflog.kernel.propositional` namespace implements a small relational
tableau for pure propositional NNF formulas. Its state is just the current
formula, pending branch work, saved literals, optional fuel, and proof term.
It supports alpha, beta, exact complementary literal closure, literal saving,
and `true` / `false` branch handling.

`kernel/prove` now uses entry-only dispatch: pure propositional formulas route
to the propositional component, while broader theorem formulas continue through
the full kernel. `kernel/proveo`, `kernel/prove-programo`, and
`kernel/prove-program` remain on the complete Proflog kernel.

The host-side profiler is therefore a forward convenience boundary, not a
semantic prerequisite for every relational call. Reverse and partial synthesis
queries that need miniKanren behavior enter through direct relations:
`proflog.kernel.propositional/proveo` for the propositional layer, or the full
kernel's `proveo` / `prove-programo` relations for broader formulas.

## Results

- Pelletier Problem 12 is now `ported-passing`.
- The Pelletier prompt selector includes Problem 12.
- Equality-bearing formulas stay on the full kernel.
- Program-bearing proof search stays on the program-aware full kernel, even for
  nullary procedure atoms that look propositional as formulas.
- The propositional layer is covered in direct relational use: one regression
  fills a partial proof skeleton, and another synthesizes a missing
  complementary atom under a constrained formula shape.
- No theorem-specific overlay, benchmark id dispatch, or compiled proof plan was
  added.

## Deferred Work

Recurrent dispatch is still deferred. Entry-only dispatch closed the motivating
Problem 12 failure without requiring cross-component state transitions.

The equality-free first-order component is also deferred until the remaining
Pelletier non-passers are remeasured under this baseline.

The broad full-kernel exact-complement fast path was not added in this ADR. A
sound implementation there needs a no-new-bindings relational equality check;
using ordinary logic unification would not be exact, and using host projection
inside the proof rule would cut against the ADR purity constraint.

## Verification

- `lein test proflog.formula-profile-test proflog.kernel.propositional-test proflog.kernel.dispatch-test`
  - `Ran 12 tests containing 30 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-prompt`
  - `Ran 2 tests containing 26 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 95 tests containing 239 assertions.`
  - `0 failures, 0 errors.`
- `timeout 600s lein test-proflog-pelletier`
  - `Ran 3 tests containing 31 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-exploratory`
  - `Ran 1 tests containing 37 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.pelletier-test`
  - `Ran 5 tests containing 70 assertions.`
  - `0 failures, 0 errors.`
