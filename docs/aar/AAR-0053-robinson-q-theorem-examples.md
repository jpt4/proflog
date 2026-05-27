# AAR-0053: Robinson Q Theorem Examples

- Status: completed
- Date: 2026-05-09
- Related ADR: [ADR-0053](../adr/ADR-0053-robinson-q-theorem-examples.md)

## Summary

ADR-0053 promotes three non-trivial Robinson Q theorem examples and proves each
under both Q versions:

- ordinary Q-as-antecedent: `Q1 and ... and Q7 -> theorem`;
- profiled Q: empty program with language metadata `:proof-profile :robinson-q`.

The examples cover pure symbolic conversion, multiplication normal forms, and
Q3 predecessor-equality use.

## Theorems

```text
forall x. add(x, s(s(zero))) = s(s(x))
```

This proves by Q5/Q4 conversion only.

```text
forall x. mul(x, s(s(zero))) = add(add(zero, x), x)
```

This proves by Q7/Q6 conversion. The statement deliberately keeps
`add(zero, x)` because Q's recursive equations inspect right arguments and Q
has no induction axiom for proving left-addition identity over arbitrary `x`.

```text
forall x. x != zero -> exists y. add(y, s(s(zero))) = s(x)
```

This uses Q3 to choose `y` as the predecessor of nonzero `x`, then Q5/Q4
conversion exposes `s(s(y))` on the left and `s(x)` becomes `s(s(y))` under the
temporary Q3 predecessor equality.

## What Changed

- Added `rq/add-right-two-successors`.
- Added `rq/mul-right-two-normal-form`.
- Added `rq/q3-add-two-successor`.
- Added focused proof tests for all three examples under both Q paths.
- Added proof-evidence checks:
  - all profiled examples contain `profiled`, `robinson-q`, and `q-rewrite`;
  - conversion-only examples do not contain `q3-predecessor-equality`;
  - the Q3 example does contain `q3-predecessor-equality`.
- Extended `lein probe-proflog-robinson-q` with rows for all three examples.
- Updated the Robinson Q worked example and runtime baseline.

## Results

Red TDD run before theorem definitions:

```text
lein test-proflog-robinson-q
Syntax error compiling at (proflog/robinson_q_test.clj:223:5).
No such var: rq/add-right-two-successors
real 10.83
```

Focused Robinson Q gate after implementation:

```text
lein test-proflog-robinson-q
Ran 13 tests containing 109 assertions.
0 failures, 0 errors.
real 22.66
```

Robinson Q comparison probe:

```text
lein probe-proflog-robinson-q
real 14.80
```

Per-row in-process timings for the new examples:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `add-right-two-successors` | 64 | `2.289 ms` | 16 | `87.787 ms` |
| `mul-right-two-normal-form` | 96 | `2.595 ms` | 16 | `133.538 ms` |
| `q3-add-two-successor` | 64 | `3.175 ms` | 32 | `1174.210 ms` |

Standard gates:

```text
lein test-proflog-fast
Ran 141 tests containing 523 assertions.
0 failures, 0 errors.
real 98.85

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 228.07
```

## Assessment

The three examples give a minimum useful demonstration set beyond axioms and
ground arithmetic. The first two prove that the profile handles symbolic
conversion with universal variables. The multiplication theorem is especially
useful because it documents a Q-specific boundary: the normal form is not the
informal arithmetic simplification a user might expect from Peano arithmetic
with induction. The third theorem exercises the ADR-0052 Q3 predecessor
equality together with two addition rewrites.
