# AAR-0054: Robinson Q Prime Evenness Example

- Date: 2026-05-09
- Related ADR: [ADR-0054](../adr/ADR-0054-robinson-q-prime-evenness.md)
- Status: completed with documented theorem-only profile boundary

## Summary

ADR-0054 corrected the proposed Robinson Q primality/evenness example and added
it to the Q theorem catalog as an inline formula helper. The original informal
predicate missed `x != s(zero)`, so it classified one as prime. The original
theorem also omitted the necessary exception for two, which is prime and even.

The promoted examples are:

```text
is-prime(x) :=
  x != zero
  AND x != s(zero)
  AND forall y. forall z.
    mul(y, z) = x ->
      ((y = x AND z = s(zero))
       OR
       (y = s(zero) AND z = x))

forall x y z.
  is-prime(x)
  AND x != s(s(zero))
  AND mul(y, z) = x
  ->
  y != s(s(zero)) AND z != s(s(zero))

forall x.
  is-prime(x)
  AND x != s(s(zero))
  ->
  forall n. mul(s(s(zero)), n) != x
```

The second theorem keeps the user's factor-variable shape. The third records
the left-factor version of "not even"; Q does not include multiplication
commutativity, so the orientation is explicit.

## Implementation

- Added `rq/one` and `rq/two`.
- Added `rq/prime-form` as an inline formula helper, not a Q relation symbol.
- Added:
  - `rq/prime-other-than-two-has-no-two-factor`;
  - `rq/prime-other-than-two-is-not-left-even`.
- Preserved the generic equality-fragment fast path inside
  `proflog.kernel.robinson-q-profile/prove-program` before falling back to the
  full Robinson-Q theory search.
- Extended `lein probe-proflog-robinson-q` with passing ADR-0054 rows. These
  rows are marked as Q-as-antecedent rows under the profiled language, because
  theorem-only profile evaluation remains slow.

## Test Results

Red TDD check:

```text
lein test-proflog-robinson-q :only proflog.robinson-q-test/corrected-prime-evenness-examples-prove-under-both-q-versions
No such var: rq/prime-other-than-two-has-no-two-factor
real 8.74 s
```

Focused selectors after implementation:

```text
lein test-proflog-robinson-q :only proflog.robinson-q-test/corrected-prime-evenness-examples-prove-as-q-antecedents-under-both-languages
Ran 1 tests containing 12 assertions.
0 failures, 0 errors.
real 8.44 s

lein test-proflog-robinson-q :only proflog.robinson-q-test/robinson-q-profile-preserves-equality-fragment-fast-path
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
real 8.96 s
```

Focused Q gate:

```text
lein test-proflog-robinson-q
Ran 15 tests containing 123 assertions.
0 failures, 0 errors.
real 20.69 s
```

Comparison probe:

```text
lein probe-proflog-robinson-q
real 12.27 s
```

New in-process probe rows:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `prime-other-than-two-has-no-two-factor` | 128 | `4.470 ms` | 128 | `4.385 ms` |
| `prime-other-than-two-is-not-left-even` | 128 | `1.855 ms` | 128 | `2.140 ms` |

The profiled rows above use the Q-as-antecedent formula under the profiled
language. They demonstrate that selecting `:robinson-q` no longer disables the
generic equality-fragment sidecar.

Standard gates:

```text
lein test-proflog-fast
Ran 143 tests containing 537 assertions.
0 failures, 0 errors.
real 84.57 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 200.09 s
```

## Shortcoming

The theorem-only `:robinson-q` query

```clojure
(query/query-succeeds
  rq/profile-program
  rq/prime-other-than-two-has-no-two-factor
  1
  128)
```

did not finish inside a final `timeout -k 5s 60s` wrapper:

```text
real 60.07 s
```

Earlier scratch probes also showed the same theorem-only shape failing inside a
180s wrapper. This is not a mathematical counterexample; it is a search-control
boundary. The proof requires using the branch-local factor terms as instances
of the universal quantifiers inside `prime-form`. The current theorem-only
profile does not find that route quickly. ADR-0054 therefore documents the
passing Q-as-antecedent path and leaves theorem-only inline-prime closure as a
future equality-fragment / theory-profile search improvement.

## Assessment

The corrected examples are useful tutorial material, but they should be
presented honestly. They are not Q multiplication-computation theorems: the
proof closes from the inline definition of primality and first-order equality.
No `q-rewrite` or `q3-predecessor-equality` marker is expected in these proofs.

The profile dispatch fix is still worthwhile. It keeps a profiled language from
regressing formulas that the generic equality-fragment layer can already close,
while preserving the existing Q conversion and Q3 behavior for arithmetic
normalization theorems.
