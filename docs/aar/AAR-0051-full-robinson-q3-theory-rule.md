# AAR-0051: Full Robinson Q3 Theory Rule

- Status: completed
- Date: 2026-05-08
- Related ADR: [ADR-0051](../adr/ADR-0051-full-robinson-q3-theory-rule.md)

## Summary

ADR-0051 extends the `:robinson-q` kernel theory profile with a full Q3
predecessor-use rule. ADR-0049 proved Q3 itself, and ADR-0050 moved that rule
into the kernel theory hook, but the profile still could not prove a theorem
whose negated tableau needed Q3 inside a larger branch after arithmetic
conversion.

The new acceptance theorem is:

```text
forall x. x != zero -> exists y. add(y, s(zero)) = x
```

The theorem is Q3 expressed through addition, because Q5 and Q4 reduce
`add(y, s(zero))` to `s(y)`. Its negated branch requires:

```text
exists x. x != zero and forall y. add(y, s(zero)) != x
```

The profile now closes that branch by storing `x != zero`, instantiating the
single-use universal with a proof-local variable, normalizing
`add(y, s(zero))` to `s(y)`, and using Q3 to choose that proof-local variable
as the predecessor of `x`.

## What Changed

- Added `rq/q3-add-one-predecessor` to the Robinson-Q theorem catalog.
- Added a red/green test requiring both ordinary Q-as-antecedent and profiled
  `:robinson-q` proofs for that theorem.
- Added `q3-predecessor-intro-closeo` to
  `proflog.kernel.robinson-q-profile`.
- Restricted full-Q3 predecessor use to successors of proof-local universal
  variables. This avoids the unsound claim that Q3 provides any arbitrary fixed
  predecessor term.
- Kept direct Q3 proof evidence as `q3-case-split`; the new larger-branch use
  records `q3-predecessor-intro`.
- Extended the source audit so the profile cannot name the new theorem.

## Proof Shape

The profiled proof for `q3-add-one-predecessor` is:

```clojure
(witness
  (conj
    (neq-store
      (once-univ
        (profiled robinson-q
          (q3-predecessor-intro
            predecessor-or-zero
            (par a_0)
            (var a_1)
            (q-convert-close
              (q-normal-add
                (q-normal-var)
                (q-normal-s (q-normal-zero))
                (q-rewrite :add-succ ...)
                (q-normal-s
                  (q-normal-add
                    (q-normal-var)
                    (q-normal-zero)
                    (q-rewrite :add-zero ...))))
              (q-normal-par))))))))
```

This shows the intended interleaving: ordinary tableau steps expose the branch,
Q4/Q5 conversion exposes the successor shape, and Q3 supplies the predecessor
use.

## Results

Focused Robinson Q gate:

```text
lein test-proflog-robinson-q
Ran 10 tests containing 73 assertions.
0 failures, 0 errors.
real 14.96
```

Robinson Q comparison probe:

```text
lein probe-proflog-robinson-q
real 12.01
```

Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `7.851 ms` | 32 | `2218.523 ms` |
| `Q7` | 32 | `2.916 ms` | 16 | `446.169 ms` |
| `add(1, zero) = 1` | 48 | `2.835 ms` | 16 | `19.881 ms` |
| `mul(2, zero) = zero` | 48 | `3.764 ms` | 16 | `12.010 ms` |
| `add(1, 2) = 3` | 64 | `3.800 ms` | 16 | `61.401 ms` |
| `mul(2, 2) = 4` | 96 | `4.026 ms` | 16 | `342.546 ms` |
| `q3-add-one-predecessor` | 64 | `2.761 ms` | 48 | `649.812 ms` |

Standard gates passed concurrently:

```text
lein test-proflog-fast
Ran 138 tests containing 487 assertions.
0 failures, 0 errors.
real 89.92

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 226.94
```

## Assessment

ADR-0051 satisfies the missing Q3 intent from the previous profile attempts.
The profile can now use Q3 in a larger tableau refutation after Q4/Q5
conversion, without falling back to a host-side formula recognizer or a
theorem-specific special case.

The rule is deliberately narrower than unrestricted predecessor generation:
it fires only when a branch has a saved nonzero term and the current
disequality Q-normalizes to that term against a successor of a proof-local
universal variable. That is the smallest sound rule needed for the acceptance
theorem and the intended deduction-modulo proof shape.

## Remaining Limits

- Q3 is still not a rewrite.
- The full-Q3 rule does not introduce arbitrary predecessor terms for later
  unrelated branch work; it closes a relevant active disequality.
- Larger Q proofs may need additional relevance-controlled Q3 uses, especially
  if their successor shape is hidden behind non-arithmetic functions or fixed
  parameters rather than proof-local universal variables.
- Performance remains acceptable for focused coverage, but the profiled Q path
  is materially slower than ordinary Q-as-antecedent on the current examples.

## Follow-Up: Contextual Q3 Gap

A 2026-05-09 expressivity audit found a concrete theorem that ordinary Q proves
but the current profile cannot:

```text
forall x. x != zero -> exists y. s(add(y, s(zero))) = s(x)
```

Ordinary Q-as-antecedent proved it at fuel 16 in `7.708565 ms`. The
`:robinson-q` profile returned `()` at fuel 16 in `16140.786164 ms` and also
returned `()` at fuel 384. The issue is not Q conversion: the profile can reduce
`add(y, s(zero))` to `s(y)`. The issue is that `q3-predecessor-intro` only
closes top-level `x != s(y)` disequalities; it does not expose the predecessor
equality `x = s(y)` so equality/congruence can close `s(s(y)) != s(x)`.

See
[Robinson Q Profile Expressivity Gap](../log/2026-05-09-robinson-q-profile-expressivity-gap.md)
for the probe definition and timings.

ADR-0052 later addressed this gap by replacing the separate Q3 closers with the
unified `q3-predecessor-equality` rule. See
[AAR-0052](AAR-0052-unified-robinson-q3-theory-rule.md).
