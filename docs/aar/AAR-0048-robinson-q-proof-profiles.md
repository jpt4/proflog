# AAR-0048: Robinson Q Proof Profiles

- Date: 2026-05-08
- Related ADR: [ADR-0048](../adr/ADR-0048-robinson-q-proof-profiles.md)
- Outcome: complete

## What Happened

ADR-0048 implemented Robinson arithmetic Q in two ways.

The ordinary path lives in `proflog.robinson-q`: Q1-Q7 are ordinary formulae
over the function-only language `zero`, `s/1`, `add/2`, and `mul/2`. A theorem
can be tested as:

```clojure
(rq/q-implies theorem)
```

which builds `Q1 and ... and Q7 -> theorem`.

The profiled path adds `proflog.proof-profile`, a generic query-facing
dispatch layer. Languages without profile metadata use `:default` and continue
through the existing kernel. Languages with `:proof-profile :robinson-q` route
through `proflog.kernel.robinson-q-profile`, which normalizes visible `add` and
`mul` terms by Q conversion rules and wraps proof evidence with
`profiled robinson-q` and `q-rewrite` markers before delegating to the kernel.

The frontend language form now accepts:

```clojure
(proof-profile :robinson-q)
```

and the backend language validator preserves the same metadata.

## What Worked

The common suite proves the same formulas through both paths:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q7` | 32 | `7.639 ms` | 16 | `2.144 ms` |
| `add(1, zero) = 1` | 48 | `2.064 ms` | 16 | `2.899 ms` |
| `mul(2, zero) = zero` | 48 | `2.855 ms` | 16 | `1.487 ms` |
| `add(1, 2) = 3` | 64 | `3.283 ms` | 16 | `1.234 ms` |
| `mul(2, 2) = 4` | 96 | `4.675 ms` | 16 | `1.451 ms` |

These are in-process timings from `lein probe-proflog-robinson-q`; the full
probe process passed in `wall 7.82 s`.

The focused regression `lein test-proflog-robinson-q` passed with:

```text
Ran 5 tests containing 42 assertions.
0 failures, 0 errors.
wall 8.94 s
```

The supporting language/frontend/query selector passed with:

```text
Ran 22 tests containing 68 assertions.
0 failures, 0 errors.
wall 25.54 s
```

The ADR-0048 commit gate ran fast and extended concurrently. Fast passed with:

```text
Ran 133 tests containing 456 assertions.
0 failures, 0 errors.
wall 75.27 s
```

Extended passed with:

```text
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
wall 197.59 s
```

The proof evidence distinguishes the two readings. The ordinary Q-as-antecedent
tests assert that `robinson-q` is absent from the proof. The profiled tests
assert `profiled`, `robinson-q`, and repeated `q-rewrite` evidence.

## What Did Not Work

Q3 was intentionally not promoted into the deduction-modulo conversion profile.
It is a predecessor-or-zero case split rather than a terminating rewrite rule.
Adding it without relevance controls would risk unbounded predecessor search.
ADR-0049 later added a focused `q3-case-split` rule for proving Q3 itself
without turning Q3 into a rewrite or general predecessor synthesizer.

The profile normalizes visible terms before kernel proof search. It is not yet
a branch-local congruence engine that renormalizes after every later equality
substitution. That limitation is acceptable for ADR-0048 because the promoted
tests and worked example exercise visible Q arithmetic formulas, including
terms under quantifiers.

The ordinary Q-as-antecedent path is semantically conservative, but some
call-free equality formulas may close through the existing equality-fragment
kernel component. That is still proof-kernel evidence, not a host-side Q
evaluator.

## Follow-Up

- Consider a later ADR for branch-local theory conversion if Q terms need to
  normalize after equality substitutions or generated term instantiation.
- Treat Q3 as a separate controlled case-split problem, not as a rewrite.
  ADR-0049 completed the focused Q3 theorem case.
- Extend proof-profile selection only through the generic
  `proflog.proof-profile/prove-program*` multimethod so future theory profiles
  do not add Q-shaped conditionals to the query layer.
- ADR-0050 later replaced the host-side whole-formula profile path with a
  kernel-interleaved theory hook.
