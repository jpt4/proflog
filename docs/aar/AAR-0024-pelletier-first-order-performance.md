# AAR-0024: Pelletier First-Order Performance Closure

- Date: 2026-04-28
- Related ADR: [ADR-0024](../adr/ADR-0024-pelletier-first-order-performance.md)
- Outcome: completed for the first equality-free first-order tranche

## What Happened

ADR-0024 compared the 18 Pelletier problems that remained `:ported-too-slow`
after ADR-0023:

```clojure
[24 25 26 27 28 29 30 31 32 34 36 37 38 41 43 44 45 46]
```

The comparison covered three prover families: alphaleanTAP-E, legacy EP, and a
new greenfield equality-free first-order component. The committed report is
`docs/PELLETIER_FIRST_ORDER_COMPARISON.md`, with a guarded regression selector
in `test/proflog/pelletier_comparison_test.clj`.

The implementation added `proflog.kernel.first-order`, a narrow tableau layer
for equality-free first-order NNF theorem branches. Its state is only the
current formula, pending branch work, saved literals, lexical environment,
optional fuel, and proof term. It intentionally excludes equality substitution,
disequality stores, generated closed-term candidates, program lookup,
procedure-call eligibility, and saved-call rechecks.

`kernel/prove` now uses entry-only profile dispatch:

- pure propositional formulas still route to `proflog.kernel.propositional`;
- equality-free first-order theorem formulas route to
  `proflog.kernel.first-order`;
- equality-bearing formulas stay on the full kernel;
- program-bearing proof search stays on `prove-program` and the full
  program-aware kernel.

Direct relational callers can still enter the relevant relation explicitly:
`proflog.kernel.first-order/proveo` for the new component, or the existing full
kernel relations for equality and program semantics.

## Results

Five formerly too-slow Pelletier problems were promoted through generic
first-order code:

```clojure
[25 30 31 36 41]
```

Those ids now live in `prompt-passing-ids`. The remaining too-slow set is:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```

No Pelletier id dispatch, theorem-specific overlay, or compiled proof plan was
added.

The comparative measurements showed:

- alphaleanTAP-E closes `24`, `25`, `27`, `28`, `29`, `30`, `31`, `32`, `36`,
  `37`, `41`, and `44` under the documented `once-forall` to `forall`
  theorem-branch conversion.
- Legacy EP closes none of the ADR-0024 baseline set under the empty-program,
  gamma-budgeted probe. Its single-use `once-forall` policy is the wrong fit
  for this theorem-only slice.
- The greenfield first-order layer closes `[25 30 31 36 41]` promptly.

## What Worked

The weakest-sufficient-layer approach from ADR-0023 generalized cleanly from
pure propositional formulas to a first-order theorem fragment. Most of the
speedup came from removing broad Proflog state rather than making the full
kernel more complex.

Treating `once-forall` as repeatable gamma inside the call-free theorem layer
was necessary and local. The full program kernel still keeps the single-use
interpretation used for negated procedure-call bodies.

The first-order layer also forced a useful tabling boundary: tabled proof
search now calls the tabled relation directly instead of accidentally entering
host-side `kernel/prove` profile dispatch.

## Deferred Work

Problem 24 is the next best target. alphaleanTAP-E closes it quickly, while
the current greenfield first-order layer still times out under the comparison
cap. That points to stack discipline, rule ordering, or beta-sibling reuse
rather than formula classification.

Problems `27`, `28`, `29`, `32`, `37`, and `44` also close in alphaleanTAP-E
but not yet in greenfield first-order. Problems `26`, `34`, `38`, `43`, `45`,
and `46` timed out even in alphaleanTAP-E under the current cap and likely need
a separate gamma-budget or lemma-reuse investigation.

Recurrent dispatch is still deferred. Entry-only dispatch was sufficient for
the first tranche and keeps component boundaries auditable.

## Verification

- `lein test proflog.formula-profile-test proflog.kernel.first-order-test proflog.kernel.dispatch-test`
  - `Ran 12 tests containing 70 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-comparison`
  - `Ran 2 tests containing 22 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.tabling-test`
  - `Ran 5 tests containing 11 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 101 tests containing 293 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier-prompt`
  - `Ran 2 tests containing 31 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-pelletier`
  - `Ran 3 tests containing 36 assertions.`
  - `0 failures, 0 errors.`
