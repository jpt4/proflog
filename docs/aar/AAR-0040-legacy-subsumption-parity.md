# AAR-0040: Legacy Subsumption Parity Gates

- Date: 2026-05-06
- Related ADR: [ADR-0040](../adr/ADR-0040-legacy-subsumption-parity.md)
- Outcome: completed with focused greenfield parity and extended rows

## What Happened

ADR-40 added `test/proflog/legacy_subsumption_test.clj` and the focused
selector `lein test-proflog-legacy-subsumption`. The suite covers the remaining
legacy-covered families that were not yet gathered into one greenfield
subsumption gate:

- group-verifier identity, closure, and inverses;
- finite-domain disjointness and totality; and
- Peano `plus/3` forward, answer, reverse, and partial-synthesis rows
  corresponding to legacy PA12 through PA20.

Every legacy-scale row has a larger, deeper, or more demanding companion row.

## What Worked

The group-verifier rows close through the ADR-39 profiled equality-fragment
path. The suite covers the original `Z2` identity, closure, and inverses rows
and larger `Z3` identity, closure, and inverses rows.

The finite-domain rows now have a focused greenfield gate. The disjointness
rows produce profiled equality-fragment proof evidence, and the totality rows
remain unresolved rather than being collapsed into false.

Peano open answer behavior now uses the generic constructor-recursive profile
over the compiled guarded Proflog clause. ADR-41 later promoted that path to
integrated `profiled constructor-recursive` proof records backed by the ADR-35
structural continuation engine. That gives direct open-answer coverage for
first-argument synthesis, second-argument synthesis, sum synthesis, halving, odd
non-halving, all-pairs enumeration, and fixed-addend answer streams.

## Plus Timing Diagnosis

The initially reported asymmetry:

```text
plus(3,4,7) closes in about 37s
plus(4,3,7) does not finish inside 120s
```

was caused by the first ADR-40 local test fixture, not by the production kernel.
That fixture accidentally recursed on the first argument of `plus/3`.

Legacy PA addition recurses on the second argument:

```text
add(x, 0, x)
add(x, s(y1), s(z1)) <- add(x, y1, z1)
```

ADR-40 corrected the local greenfield fixture to the same recursion direction.
After the correction, the direct kernel proof cost follows the second argument:
`3 + 4 = 7` is the slower four-step row, while `4 + 3 = 7` is the faster
three-step row.

## What Remains

The suite is a focused selector, not a fast-path regression. The direct kernel
`PA10 3 + 4 = 7` row remains the expensive row, although the ADR-41 rerun
recorded it at `25198.864 ms` on the current machine.

Peano PA12 through PA20 parity is covered through the promoted
`profiled constructor-recursive` profile rather than the ordinary public
`query-answers` path. This is an operational profile boundary, not a claim that
the default answer exporter can fully enumerate every legacy Peano stream
cheaply.

ADR-42 later corrected the `warm-cool-disjoint` status behavior recorded during
ADR-40. The root cause was equality-fragment proof-variable scoping across
disjunction, not supervaluation semantics. The bounded status now reports
`:succeeds`; ADR-40's direct success-proof row remains useful as the historical
parity baseline.

## Verification

Focused verification:

```text
timeout -k 5s 900s lein test-proflog-legacy-subsumption
  Ran 3 tests containing 63 assertions.
  0 failures, 0 errors.
  elapsed 50.37 s
```

Regression verification:

```text
timeout -k 5s 600s lein test-proflog-fast
  Ran 117 tests containing 381 assertions.
  0 failures, 0 errors.
  elapsed 250.55 s

timeout -k 5s 1000s lein test-proflog-extended
  Ran 68 tests containing 203 assertions.
  0 failures, 0 errors.
  elapsed 654.86 s
```

Passing row timings from the focused selector:

| Row | Runtime |
|---|---:|
| `z2-identity` | `856.145 ms` |
| `z2-closure` | `77.389 ms` |
| `z2-inverses` | `53.596 ms` |
| `z3 identity` | `962.273 ms` |
| `z3 closure` | `2059.279 ms` |
| `z3 inverses` | `811.033 ms` |
| `warm/cool disjoint` | `3.282 ms` |
| `extended finite-domain disjointness` | `26.541 ms` |
| `finite totality is undefined` | `2454.314 ms` |
| `extended finite totality is undefined` | `3401.862 ms` |
| `PA10 forward 3 + 4 = 7` | `70586.114 ms` |
| `PA11 / extended forward 4 + 3 = 7` | `11456.428 ms` |
| `PA12 ? + 3 = 5 gives 2` | `29.909 ms` |
| `extended ? + 3 = 6 gives 3` | `11.843 ms` |
| `PA13/PA15 3 + ? = 5 gives 2` | `14.453 ms` |
| `extended 3 + ? = 6 gives 3` | `15.640 ms` |
| `PA14 3 + 4 = ? gives 7` | `7.692 ms` |
| `extended 4 + 3 = ? gives 7` | `6.319 ms` |
| `PA16 x + x = 4 gives 2` | `7.086 ms` |
| `extended x + x = 6 gives 3` | `11.063 ms` |
| `PA17 x + x = 3 has no answer` | `6.738 ms` |
| `PA18 / extended x + x = 5 has no answer` | `10.846 ms` |
| `PA19 all pairs summing to 3` | `9.105 ms` |
| `extended all pairs summing to 4` | `11.712 ms` |
| `PA20 fixed addend 2 stream includes legacy low pairs` | `50.322 ms` |
| `extended fixed addend 3 stream includes deeper low pairs` | `53.246 ms` |
