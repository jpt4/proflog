# ADR-0051: Full Robinson Q3 Theory Rule

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0051-full-q3-rule`
- AAR: [AAR-0051](../aar/AAR-0051-full-robinson-q3-theory-rule.md)

## Context

ADR-0049 and ADR-0050 made Q3 available in the `:robinson-q` proof profile, but
only as a focused branch closer for Q3's own direct refutation shape:

```text
x != zero
forall y. x != s(y)
```

That was enough to prove Q3 itself, and ADR-0050 moved the rule into the kernel
theory hook. It still falls short of the intended deduction-modulo reading of Q
as a proof profile: Q3 must be usable when a larger theorem's negated tableau
requires a predecessor to close a downstream obligation.

The concrete motivating theorem is:

```text
forall x. x != zero -> exists y. add(y, s(zero)) = x
```

Its negation is:

```text
exists x. x != zero and forall y. add(y, s(zero)) != x
```

The profile must close this branch by using Q3 to choose the universal proof
variable `y` as the predecessor of `x`, then normalizing `add(y, s(zero))` to
`s(y)` through Q5 and Q4. This theorem is equivalent to Q3 up to definitional
addition conversion, so Q1, Q2, and Q4-Q7 cannot prove it without Q3 or an
equivalent lemma.

## Decision

Extend the Robinson-Q kernel theory rule with a relevance-controlled full-Q3
closure:

- keep the ADR-0050 branch-state model; do not return to host-side whole-formula
  preprocessing;
- preserve the direct `q3-case-split` proof evidence for Q3 itself;
- add a new theory closure that recognizes a saved `x != zero` obligation and
  a current disequality whose Q-normal form is `x != s(v)` or `s(v) != x`;
- require `v` to be a proof-local universal variable from `proof-vars`, so the
  rule represents choosing that universal witness as Q3's predecessor rather
  than claiming an arbitrary fixed term is the predecessor;
- include the Q-normalization proofs in the new proof term so Q4/Q5 conversion
  remains auditable.

This is a branch closure, not a rewrite rule. It is the tableau form of using
Q3 to refute a universal disequality after the branch has already exposed a
nonzero term.

## Consequences

- The `:robinson-q` profile can prove at least one theorem that needs Q3 inside
  a larger refutation rather than only Q3 itself.
- The rule is relevance-controlled by the active current disequality and by the
  presence of a saved nonzero premise, avoiding unrestricted predecessor
  generation.
- The proof profile remains slower than host preprocessing but keeps the
  intended kernel-interleaved architecture.
- The implementation is still not a general arithmetic solver; it only permits
  Q3 predecessor use when the current branch formula actually exposes a
  successor of a proof-local universal variable after Q conversion.

## Test Obligations

- A red test must show that ordinary Q-as-antecedent can prove the
  add-one-predecessor theorem while the profiled path initially cannot.
- The final profiled proof must contain ordinary tableau evidence (`witness`,
  `once-univ`, and `neq-store`) and new full-Q3 evidence.
- The final profiled proof must contain `q-rewrite`, proving that Q3 is being
  used after Q4/Q5 conversion rather than only on a syntactic successor.
- Existing Q3 and Q7 profile proof-shape tests must continue to pass.
- The focused Robinson Q suite, fast gate, and extended gate must pass, with
  runtimes recorded.

## Exit Criteria

- `:robinson-q` proves
  `forall x. x != zero -> exists y. add(y, s(zero)) = x`.
- The proof term distinguishes direct Q3 case splitting from full-Q3
  predecessor use in a larger branch.
- The implementation stays in the kernel theory hook and does not add host-side
  formula recognition or theorem-specific code.
- Documentation, worked examples, runtime baselines, and AAR records explain
  the full-Q3 rule and its remaining limits.

## After Action Summary

ADR-0051 completed the full-Q3 rule for the motivating larger refutation. The
`:robinson-q` profile now proves
`forall x. x != zero -> exists y. add(y, s(zero)) = x` with proof evidence for
ordinary tableau steps, `neq-store`, `once-univ`, Q4/Q5 `q-rewrite`, and
`q3-predecessor-intro`.

The implementation stays inside the ADR-0050 kernel theory hook and is guarded
by a source audit that rejects theorem-specific profile code. See
[AAR-0051](../aar/AAR-0051-full-robinson-q3-theory-rule.md) for timings and
remaining limits.
