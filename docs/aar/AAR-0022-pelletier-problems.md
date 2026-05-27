# AAR-0022: Pelletier Problem Replication

- Date: 2026-04-27
- Related ADR: [ADR-0022](../adr/ADR-0022-pelletier-problems.md)
- Outcome: completed for the greenfield catalog and Problems 1-46 port

## What Happened

ADR-0022 added `test/proflog/pelletier_test.clj` as a pure-theorem benchmark
for the greenfield kernel. The test namespace treats the upstream
`namin/leanTAP` Clojure and Scheme files as the formula source of record.

The port uses local formula builders for implication, equivalence, quantifiers,
and branch construction, but those builders compile directly to existing
`proflog.ast` forms. A theorem is tested by proving the NNF of:

```clojure
axiom-1 and ... and axiom-n and not(theorem)
```

No Proflog program clauses, overlays, or theorem-specific recognizers were
added.

## Results

- Problems 1-11, 13-23, 33, 35, 39, 40, and 42 are `ported-passing`.
- Problem 12 is `requires-kernel-work`: it is propositional, but a
  fresh-process probe did not find a proof within 120 seconds.
- Problems 24-32, 34, 36-38, 41, and 43-46 are `ported-too-slow`.
- The already mirrored legacy slice, Problems 1, 2, and 18, now has greenfield
  coverage through the ordinary kernel.

Observed targeted timings:

| Problem | Result | Timing |
|---:|---|---:|
| 10 | passed | 30405 ms |
| 12 | requires kernel work | >120000 ms |
| 17 | passed | 35809 ms |
| 20 | passed | 7611 ms |
| 21 | passed | 14122 ms |
| 22 | passed | 7264 ms |
| 23 | passed | 1506 ms |
| 24 | too slow | >120000 ms |
| 25 | too slow | >120000 ms |
| 26 | too slow | >120000 ms |
| 27 | too slow | >120000 ms |
| 28 | too slow | >120000 ms |
| 29 | too slow | >120000 ms |
| 30 | too slow | >120000 ms |
| 31 | too slow | >120000 ms |
| 32 | too slow | >120000 ms |
| 33 | passed | 58109 ms |
| 34 | too slow | >120000 ms |
| 35 | passed | 713 ms |
| 36 | too slow | >120000 ms |
| 37 | too slow | >120000 ms |
| 38 | too slow | >120000 ms |
| 39 | passed | 1764 ms |
| 40 | passed | 66643 ms |
| 41 | too slow | >120000 ms |
| 42 | passed | 40668 ms |
| 43 | too slow | >120000 ms |
| 44 | too slow | >120000 ms |
| 45 | too slow | >120000 ms |
| 46 | too slow | >120000 ms |

Problem 12 is therefore a propositional search boundary, not a hidden passing
regression. The remaining non-passers are first-order formulas that are ported
but did not close within the review window.

## Selectors

ADR-0022 added three dedicated aliases:

- `lein test-proflog-pelletier-prompt`
- `lein test-proflog-pelletier`
- `lein test-proflog-pelletier-exploratory`

The default Pelletier alias runs the currently passing tranche and remains
separate from `test-proflog-fast` and `test-proflog-extended`.

## Verification

- `timeout 120s lein test-proflog-pelletier-prompt`
  - `Ran 2 tests containing 25 assertions.`
  - `0 failures, 0 errors.`
- `timeout 60s lein test-proflog-pelletier-exploratory`
  - `Ran 1 tests containing 38 assertions.`
  - `0 failures, 0 errors.`
- `timeout 420s lein test-proflog-pelletier`
  - `Ran 3 tests containing 30 assertions.`
  - `0 failures, 0 errors.`
- `timeout 480s lein test proflog.pelletier-test`
  - `Ran 5 tests containing 70 assertions.`
  - `0 failures, 0 errors.`

## Follow-Up

Problem 12 should be the first follow-up if the next branch focuses on
propositional search performance. It should be trivial for an alphaleanTAP
extension, so treating it as merely slow would hide the wrong failure mode.

The first-order timeout set should be reviewed after that:

```clojure
[24 25 26 27 28 29 30 31 32 34 36 37 38 41 43 44 45 46]
```

Those formulas are now ported and cataloged, but they should not be promoted
into the passing regression selector until the kernel closes them within a
defensible review window.
