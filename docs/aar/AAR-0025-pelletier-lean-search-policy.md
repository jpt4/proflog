# AAR-0025: Pelletier Lean Search Policy

- Date: 2026-04-28
- Related ADR: [ADR-0025](../adr/ADR-0025-pelletier-lean-search-policy.md)
- Outcome: completed for full Pelletier catalog closure

## What Happened

ADR-0025 completed the remaining equality-free first-order Pelletier tranche:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```

The comparison is recorded in
`docs/PELLETIER_LEAN_SEARCH_POLICY_COMPARISON.md`. It compares ADR-0024
greenfield first-order search, the original upstream alphaleanTAP execution
shape, local legacy EP lessons, a lean relation without lemmas, a lean relation
with beta-sibling lemmas, and the final lean+lemma+Skolemization policy.

The implementation keeps the profile boundary from ADR-0024 but changes the
unbounded equality-free first-order theorem path:

- `first-order/proveo` now uses an alphaleanTAP-shaped relation for direct
  relational use.
- The lean relation uses vector unification templates over greenfield tagged
  list formulas, compact unary proof spines, gamma re-enqueue, and
  beta-sibling lemma threading.
- Host-facing `first-order/prove` canonicalizes compact proof spines before
  returning them.
- Complex forward theorem calls use host-side Skolemization before entering
  the lean relation; these returned proofs are marked with `skolemized`.
- Fuel-bounded calls keep the ADR-0024 bounded relation so open branches still
  return under a finite slice.

## Results

All 46 Pelletier problems are now `:ported-passing`.

The previously remaining problems are in `prompt-passing-ids`:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```

`ported-too-slow-ids` is empty.

No Pelletier problem is solved by id-specific dispatch, theorem-specific
overlay, or compiled proof plan. The adopted policy is generic over
equality-free first-order theorem formulas.

## What Worked

The biggest improvement came from matching the primary source's operational
shape more closely. Vector unification templates and compact proof spines were
not just presentation choices; they changed search enough to close several
problems that the ADR-0024 relation could not close promptly.

Beta-sibling lemma threading closed the next group: `34`, `38`, `43`, and `45`
benefited directly from reusing literals proved by the left branch when proving
the right branch.

Skolemization was required for the final hard cases, especially `26` and `46`.
Keeping it forward-only preserves the direct relational path while making
theorem-style `kernel/prove` behave like the original leanTAP family where that
policy is appropriate.

## Boundaries

The Skolemization prepass is not a program-kernel rule. It applies only inside
the equality-free first-order theorem component's host-facing `prove` wrapper.
Program-bearing proof search still enters the full kernel through
`prove-program`.

Direct reverse or partial proof use still calls `first-order/proveo`; it does
not require host-side profile dispatch or Skolemization. The direct relation
returns compact proof terms because that is the operational shape it exposes.

## Verification

- `lein test proflog.kernel.first-order-test`
  - `Ran 5 tests containing 24 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-comparison`
  - `Ran 4 tests containing 50 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.formula-profile-test proflog.kernel.dispatch-test proflog.proof-test`
  - `Ran 13 tests containing 91 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-prompt`
  - `Ran 2 tests containing 44 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier`
  - `Ran 3 tests containing 49 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-exploratory`
  - `Ran 1 tests containing 1 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 102 tests containing 323 assertions.`
  - `0 failures, 0 errors.`
