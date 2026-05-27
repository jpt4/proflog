# ADR-0054: Robinson Q Prime Evenness Example

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0054-robinson-q-prime-evenness`
- AAR: [AAR-0054](../aar/AAR-0054-robinson-q-prime-evenness.md)

## Context

The Robinson Q examples added by ADR-0053 compare two ways of proving formulas
over the Q language:

- ordinary Q-as-antecedent proofs, where `Q1 and ... and Q7 -> theorem`;
- the opt-in `:robinson-q` proof profile, where Q4-Q7 conversion and Q3's
  predecessor principle are exposed as kernel-interleaved theory rules.

A proposed primality example exposed two separate issues.

First, the informal definition

```text
forall y. forall z.
  x != zero
  AND
  (mul(y, z) = x ->
    ((y = x AND z = s(zero))
     OR
     (y = s(zero) AND z = x)))
```

does not exclude `s(zero)`, so it classifies one as prime. The corrected
inline abbreviation must include both `x != zero` and `x != s(zero)`.

Second, the theorem "any prime number is not even" is false as stated because
two is prime and even. The promoted theorem must explicitly exclude
`s(s(zero))`, for example:

```text
forall x y z.
  is-prime(x)
  AND x != s(s(zero))
  AND mul(y, z) = x
  ->
  y != s(s(zero)) AND z != s(s(zero))
```

This theorem is expressible over Q's function-only language as an inline
formula abbreviation. It is not an object-language relation unless a separate
frontend helper layer inlines it before query validation.

The initial probe also exposed an operational boundary. The ordinary
Q-as-antecedent path proved the corrected theorem quickly through the existing
equality-fragment layer. The theorem-only `:robinson-q` path timed out, because
the current profile delegates to full-kernel search after Q-specific conversion
rules fail to find a relevant arithmetic closure. The same Q-as-antecedent
formula over the profiled language should still preserve the generic
equality-fragment fast path rather than being forced into the slower full
Robinson-Q kernel search.

## Decision

Add public Robinson Q formula helpers for corrected primality examples:

- `one` and `two` numerals;
- `prime-form`, an inline formula builder for corrected primality;
- a corrected factor theorem excluding two;
- a divisibility-oriented theorem excluding two.

Keep these as formula helpers, not procedure-call relations. Q's language has
functions and equality but no `prime/1` relation symbol; a future frontend DSL
may expose `is-prime(x) := ...` as a helper that inlines to these formulas.

Preserve the generic equality-fragment proof layer inside the `:robinson-q`
profile. The selected Q profile should still make Q conversion and Q3 theory
rules available, but it must not lose the existing generic kernel sidecar for
call-free equality formulas that need no Q-specific conversion, including
Q-as-antecedent formulas evaluated under the profiled language.

Do not claim that the prime theorem demonstrates Q multiplication computation.
The proof closes from the corrected primality definition and equality logic; Q
conversion is not required. The worked example must say this explicitly.

## Test Obligations

- Red test before implementation must fail on the missing public corrected
  prime theorem helpers.
- Regression tests must prove the corrected prime examples as Q-as-antecedent
  formulas through both Q language declarations:
  - ordinary Q-as-antecedent;
  - the same Q-as-antecedent formula under the `:robinson-q` language, using
    the preserved equality-fragment sidecar.
- A routing test must show that a successful equality-fragment closure prevents
  the profiled path from falling into the slower full Robinson-Q kernel search.
- The Robinson Q probe must record timings for the corrected examples.
- The worked example, runtime baseline, ADR/AAR indexes, source map, and log
  must be updated.
- The theorem-only `:robinson-q` timeout must be documented as a current
  shortcoming rather than hidden by the Q-as-antecedent proof.

## Exit Criteria

- `lein test-proflog-robinson-q` passes and records runtime.
- `lein probe-proflog-robinson-q` passes and records per-row timings for the
  corrected prime examples.
- `lein test-proflog-fast` and `lein test-proflog-extended` pass before merge.
- Documentation distinguishes the false original theorem from the corrected
  theorem and explains why the proof is an equality-fragment theorem under the
  selected Q language. It must also state that theorem-only `:robinson-q`
  evaluation remains a runtime boundary for the full inline primality formula.

## After Action Summary

ADR-0054 added the corrected inline primality formula, two corrected
prime/evenness catalog theorems, tests for the passing Q-as-antecedent proof
path under both Q language declarations, the `:robinson-q` equality-fragment
dispatch preservation test, probe rows, and worked-example documentation. The
focused Robinson Q gate passed with `Ran 15 tests containing 123 assertions`,
`0 failures, 0 errors`, and `real 20.69 s`. The theorem-only profile path for
the full factor theorem remains a documented runtime boundary after a 60s
wrapper timeout. See
[AAR-0054](../aar/AAR-0054-robinson-q-prime-evenness.md).
