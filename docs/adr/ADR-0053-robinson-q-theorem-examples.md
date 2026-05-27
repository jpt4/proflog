# ADR-0053: Robinson Q Theorem Examples

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0053-q-theorem-examples`
- AAR: [AAR-0053](../aar/AAR-0053-robinson-q-theorem-examples.md)

## Context

ADR-0052 completed the unified Q3 theory rule for the `:robinson-q` profile.
The profile now has enough coverage to demonstrate more than the individual Q
axioms, ground arithmetic, and the original Q3 acceptance examples.

The next documentation need is a compact set of non-trivial Q theorems that:

- prove under ordinary Q-as-antecedent;
- prove under the `:robinson-q` deduction-modulo profile;
- show both pure Q conversion and Q3 predecessor-equality use;
- expose a genuine Robinson-Q limitation rather than presenting informal
  arithmetic facts that require induction.

## Decision

Promote three theorem examples to `proflog.robinson-q`:

```text
forall x. add(x, s(s(zero))) = s(s(x))
```

This is a symbolic right-addition theorem. It proves by Q5/Q4 conversion.

```text
forall x. mul(x, s(s(zero))) = add(add(zero, x), x)
```

This is a right-multiplication-by-two normal-form theorem. It intentionally
stops at `add(add(zero, x), x)`, because Q's recursive definitions inspect the
right argument and Robinson Q has no induction principle to prove the informal
left-addition identity `add(zero, x) = x`.

```text
forall x. x != zero -> exists y. add(y, s(s(zero))) = s(x)
```

This is a Q3-dependent theorem. If `x` is nonzero, Q3 supplies `x = s(y)`;
Q5/Q4 then reduce `add(y, s(s(zero)))` to `s(s(y))`, matching `s(x)` under the
Q3 equality.

## Test Obligations

- Each theorem must prove under ordinary Q-as-antecedent.
- Each theorem must prove under the `:robinson-q` profile.
- The profiled proofs must contain `profiled`, `robinson-q`, and `q-rewrite`.
- The Q3-dependent theorem must additionally contain `q3-predecessor-equality`.
- The conversion-only theorems must not require `q3-predecessor-equality`.
- The timing comparison probe must include all three examples.
- The worked example must explain the theorem statements, proof mechanism, and
  any semantic limitation.

## Exit Criteria

- Focused Robinson Q tests pass.
- The timing probe records all three examples under both Q versions.
- Standard fast and extended gates pass with runtimes recorded.
- ADR, AAR, runtime baseline, and worked examples are current.

## After Action Summary

ADR-0053 added the three theorem examples, focused proof tests under both Q
paths, comparison-probe rows, and worked-example documentation. The focused red
run failed on the missing public theorem vars before implementation; after the
catalog/test/probe updates, `lein test-proflog-robinson-q` passed with
`Ran 13 tests containing 109 assertions`, `0 failures, 0 errors`, and
`real 22.66`. See
[AAR-0053](../aar/AAR-0053-robinson-q-theorem-examples.md).
