# ADR-0031 Answer-Path Tabling Probe

Date: 2026-04-30
Branch: `adr31/answer-tabling`

## Question

Would targeted tabled or canonical duplicate-state suppression in the raw
answer path improve multiple ADR-0031 list-family matrix rows?

## Probe Boundary

The probe intentionally stayed above production code. It used the existing raw
answer diagnostics in `proflog.answers/query-answer-diagnostics`, which bypass
the public `query-answers` list-family materializers and report:

- raw proof count;
- exported answer count;
- unique canonical answer count;
- duplicate exported answer count;
- distinct and duplicate proof-step signature counts.

This does not prove that no internal state is ever revisited, but it is the
current generic diagnostic surface for deciding whether raw answer search is
dominated by rediscovered proof families.

## Commands

Focused regression check:

```sh
lein test proflog.answers-test proflog.tabling-test
```

Representative diagnostic sweeps were run with one JVM process per sweep, using
`timeout` around `lein trampoline run -m clojure.main -e ...` and calling
`answers/query-answer-diagnostics` for matrix cases from
`proflog.list-kernel-matrix-probe`.

## Results

| Case | Raw Limits | Duplicate Proof Signatures | Duplicate Exported Records | Notes |
| --- | ---: | ---: | ---: | --- |
| `append-output-flat` | 4, 8, 16 | 0, 0, 0 | 2, 6, 14 | Export duplicates dominate, but raw proof signatures are all distinct. |
| `append-output-nested` | 4, 8, 16 | 0, 0, 0 | 2, 6, 14 | Same shape as flat output synthesis. |
| `append-suffix-flat` | 4, 8, 16 | 0, 0, 0 | 2, 6, 13 | Unique exported records grow only slightly. |
| `append-prefix-flat` | 4, 8 | 0, 0 | 2, 5 | Passing row still has distinct proof signatures. |
| `append-inverse-flat` | 4, 8 | 0, 0 | 2, 4 | Raw limit 8 reaches four unique records, but matrix still finds only 3 of 4 closed target splits. |
| `append-inverse-nested` | 4, 8 | 0, 0 | 2, 4 | Duplicate export merging is useful, not a raw-search fix. |
| `reverse-output-flat` | 4, 8 | 0, 0 | 1, 3 | No duplicate raw signature signal. |
| `reverse-input-flat` | 1, 2, 4 | 0, 0, 0 | 0, 0, 1 | Raw limit 4 took about 65s and still showed distinct proof signatures. |
| `reverse-output-nested-longer` | 1, 2, 4 | 0, 0, 0 | 0, 0, 1 | Matrix run found no closed target at raw limit 4. |
| `reverse-partial-output-tail` | 1, 2, 4 | 0, 0, 1 | 0, 0, 1 | Only weak duplicate signal: one duplicate proof signature at raw limit 4. |

The focused regression command passed:

```text
Ran 22 tests containing 75 assertions.
0 failures, 0 errors.
```

## Interpretation

Canonical answer merging is already doing real work after export: several rows
turn most raw results into duplicate exported frontiers. That does not imply
that answer-overlay tabling would improve search, because the raw proof
signatures leading to those duplicate exports are mostly distinct.

The blocker rows do not look duplicate-bound:

- `reverse-output-nested-longer` produced no closed target at raw limit 4, with
  zero duplicate proof signatures at raw limits 1, 2, and 4.
- `reverse-partial-output-tail` produced only one duplicate proof signature at
  raw limit 4.
- `reverse-input-flat` was expensive even at raw limit 4, but all observed proof
  signatures were distinct.

This argues against implementing answer-overlay tabled suppression as the next
ADR-0031 step. A prototype would add relation-generic but invasive machinery to
the answer recursion path without evidence that it improves multiple matrix
rows. The observed blocker remains search shape and residual frontier ordering,
not repeated identical raw proof families.

## Decision

No production answer-tabling prototype was retained on this branch.

Future work should first improve broader constructor-recursive answer search
discipline. If a later diagnostic can show repeated canonical answer states
rather than merely duplicate exported records, answer-path tabling can be
reconsidered with that state key as the prototype boundary.
