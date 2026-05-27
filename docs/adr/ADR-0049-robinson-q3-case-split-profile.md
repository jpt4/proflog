# ADR-0049: Robinson Q3 Case-Split Profile Rule

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0049-robinson-q3-profile`
- AAR: [AAR-0049](../aar/AAR-0049-robinson-q3-case-split-profile.md)

## Context

ADR-0048 implemented Robinson Q in two forms:

- ordinary Q1-Q7 formulas as assumptions in the antecedent of an implication;
- an opt-in `:robinson-q` deduction-modulo profile for terminating `add` and
  `mul` conversion.

ADR-0048 deliberately left Q3 outside the profile:

```text
forall x. x != zero -> exists y. x = s(y)
```

The reason was sound: Q3 is not a terminating rewrite. It is the
predecessor-or-zero case split for Q objects. But Q3 is one of the Robinson Q
axioms, and the user-facing comparison should be able to prove it under both
versions. A probe after ADR-0048 showed the boundary exactly:

```clojure
(query/query-succeeds rq/ordinary-program (rq/q-implies rq/q3) 1 32)
;; => one proof

(query/query-succeeds rq/profile-program rq/q3 1 32)
;; => nil
```

## Decision

Extend the `:robinson-q` profile with a focused proof rule for the refutation
shape generated when proving Q3:

```text
exists x. x != zero and once-forall y. x != s(y)
```

The profile should close that branch by a recorded theory case split:

```text
q3-case-split predecessor-or-zero
```

This is not an arithmetic rewrite. It is a trusted Robinson-Q profile rule,
and proof objects must distinguish it from `q-rewrite` conversion.

The rule should be structural over the normalized formula shape rather than a
host-side evaluator for arbitrary arithmetic terms. It may remain specific to
Q3 because the theory profile is Robinson-Q-specific.

## Consequences

- Ordinary Q-as-antecedent remains unchanged and proves Q3 from the Q
  assumptions.
- The profiled path can now prove Q3 as a theory axiom of the selected profile.
- The proof profile has two kinds of evidence: `q-rewrite` for terminating
  `add`/`mul` conversion and `q3-case-split` for the predecessor-or-zero rule.
- General arbitrary predecessor synthesis remains out of scope; the promoted
  rule only closes the exact Q3 refutation branch.

## Test Obligations

- Add a red test showing ordinary Q proves Q3 and profiled Q must also prove
  Q3 with explicit `q3-case-split` evidence.
- Keep ADR-0048 arithmetic conversion tests passing.
- Update documentation so Q3 is no longer described as unimplemented in the
  current profile, while preserving the distinction between rewrite and
  case-split rules.

## Exit Criteria

- `lein test-proflog-robinson-q` passes with Q3 covered in both versions.
- The profile proof for Q3 contains `profiled`, `robinson-q`, and
  `q3-case-split`.
- Worked examples and ADR/AAR records describe Q3 proof behavior and remaining
  limits.
- Focused and standard regression gates pass, with runtimes recorded.
