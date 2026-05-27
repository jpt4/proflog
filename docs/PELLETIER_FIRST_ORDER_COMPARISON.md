# Pelletier First-Order Comparison

Date: 2026-04-28
Related ADR: [ADR-0024](adr/ADR-0024-pelletier-first-order-performance.md)

## Scope

ADR-0024 starts from the 18 Pelletier problems that were still classified as
`:ported-too-slow` after ADR-0023:

```clojure
[24 25 26 27 28 29 30 31 32 34 36 37 38 41 43 44 45 46]
```

The comparison is intentionally formula-generic. No Pelletier problem id is
used by proof search.

The coverage table is also represented in
`test/proflog/pelletier_comparison_test.clj` and guarded by
`lein test-proflog-pelletier-comparison`.

## Method

- Input formula: `proflog.pelletier-test/theorem-branch` for each problem.
- alphaleanTAP-E: run the branch after converting greenfield `once-forall` to
  ordinary `forall`, because alphaleanTAP-E has only classical first-order
  quantifiers.
- Legacy EP: run the original greenfield NNF branch with an empty program and
  gamma budget `80`.
- Greenfield first-order: run the original greenfield NNF branch through
  `proflog.kernel.first-order/prove` with fuel `80`.
- Timeout: each command was given a fresh-process `12s` wall-clock cap.
- Times below are relation elapsed time measured inside the JVM; JVM startup is
  not included in the millisecond figure, but is included in the timeout cap.

## Results

| Problem | alphaleanTAP-E | legacy EP | greenfield first-order |
|---:|---|---|---|
| 24 | closes, `0.656ms` | no proof, `1.296ms` | timeout |
| 25 | closes, `0.538ms` | no proof, `0.904ms` | closes, `0.460ms` |
| 26 | timeout | no proof, `1.423ms` | timeout |
| 27 | closes, `0.593ms` | no proof, `0.793ms` | timeout |
| 28 | closes, `0.582ms` | no proof, `0.844ms` | timeout |
| 29 | closes, `0.759ms` | no proof, `0.944ms` | timeout |
| 30 | closes, `0.892ms` | no proof, `0.828ms` | closes, `0.415ms` |
| 31 | closes, `0.564ms` | no proof, `0.798ms` | closes, `0.463ms` |
| 32 | closes, `0.531ms` | no proof, `1.249ms` | timeout |
| 34 | timeout | no proof, `1.204ms` | timeout |
| 36 | closes, `1.053ms` | no proof, `0.871ms` | closes, `0.426ms` |
| 37 | closes, `0.801ms` | no proof, `0.847ms` | timeout |
| 38 | timeout | no proof, `0.949ms` | timeout |
| 41 | closes, `0.559ms` | no proof, `1.762ms` | closes, `0.475ms` |
| 43 | timeout | no proof, `0.901ms` | timeout |
| 44 | closes, `0.698ms` | no proof, `1.500ms` | timeout |
| 45 | timeout | no proof, `1.241ms` | timeout |
| 46 | timeout | no proof, `0.808ms` | timeout |

## Findings

- alphaleanTAP-E closes 12 of the 18 when `once-forall` is treated as ordinary
  classical `forall`.
- Legacy EP closes none of the 18 under the empty-program, gamma-budgeted probe;
  its single-use `once-forall` behavior is the wrong operational policy for
  this theorem-only slice.
- The new greenfield first-order layer closes the first five-problem tranche:
  `[25 30 31 36 41]`.
- Problem 24 is the clearest remaining gap: alphaleanTAP-E closes it promptly,
  but the current greenfield first-order relation still times out. That points
  to rule ordering or stack discipline, not formula profile classification.

## Adopted In This Tranche

- Add a dedicated equality-free first-order component.
- Dispatch `kernel/prove` to that component for equality-free first-order
  theorem formulas.
- Treat `once-forall` as repeatable gamma only inside the call-free first-order
  theorem component.
- Promote `[25 30 31 36 41]` to `:ported-passing`.

## Deferred Hypotheses

- Problem 24 likely needs a closer match to alphaleanTAP-E stack behavior or a
  narrower deterministic agenda heuristic.
- Problems 27, 28, 29, 32, 37, and 44 close in alphaleanTAP-E but not yet in
  greenfield first-order; they should be the next tranche after Problem 24.
- Problems 26, 34, 38, 43, 45, and 46 timed out even in alphaleanTAP-E under
  the current cap, so they likely require a separate gamma-budget or lemma-reuse
  investigation.
