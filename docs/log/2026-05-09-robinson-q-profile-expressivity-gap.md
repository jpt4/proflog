# Robinson Q Profile Expressivity Gap

Date: 2026-05-09

## Context

ADR-0051 added `q3-predecessor-intro` so the `:robinson-q` profile can use Q3
inside one larger refutation:

```text
forall x. x != zero -> exists y. add(y, s(zero)) = x
```

The implemented rule is intentionally narrow. It closes a current disequality
when one normalized side is a saved nonzero term and the other normalized side
is the successor of the active proof-local universal variable. It does not add
the equality `x = s(y)` to the branch and let later congruence or equality
reasoning continue from that fact.

## Gap Theorem

A small Q theorem that exposes the remaining gap is:

```text
forall x. x != zero -> exists y. s(add(y, s(zero))) = s(x)
```

This is valid in Robinson Q. If `x != zero`, Q3 gives a predecessor `y` such
that `x = s(y)`. Q5 and Q4 reduce:

```text
add(y, s(zero)) = s(add(y, zero)) = s(y)
```

By congruence, `s(add(y, s(zero))) = s(s(y)) = s(x)`.

The negated tableau branch is:

```text
exists x. x != zero and forall y. s(add(y, s(zero))) != s(x)
```

After the universal is instantiated, Q conversion exposes:

```text
s(s(y)) != s(x)
```

The profile should be able to use Q3 to choose `y` as a predecessor of `x`, add
or expose `x = s(y)`, and then close the successor-context disequality by
ordinary equality/congruence reasoning. The current rule cannot do that because
it only recognizes the top-level shape:

```text
x != s(y)
```

or its symmetric orientation.

## Probe Definition

The probe formula was built directly with the Robinson-Q helpers:

```clojure
(def contextual-q3
  (ast/nom x y
    (ast/forall-form x
      (ast/implies-form
        (rq/neq (ast/var-term x) rq/zero)
        (ast/exists-form y
          (rq/eq (rq/s (rq/add (ast/var-term y) (rq/s rq/zero)))
                 (rq/s (ast/var-term x))))))))
```

## Results

Focused timed probe:

```text
(query/query-succeeds rq/ordinary-program (rq/q-implies contextual-q3) 1 16)
=> proof
elapsed: 7.708565 ms

(query/query-succeeds rq/profile-program contextual-q3 1 16)
=> ()
elapsed: 16140.786164 ms

(query/query-succeeds rq/profile-program contextual-q3 1 384)
=> ()
elapsed: 13956.186943 ms
```

The complete Leiningen process for that focused timed probe took:

```text
real 38.98
user 41.23
sys 1.03
```

A broader fuel scan also found ordinary Q proofs at fuel 16, 32, 48, 64, 96,
and 128, while the profile returned `()` at each fuel. A profile-only scan at
fuel 192, 256, and 384 also returned `()`; that process took `real 54.47`.

## Related Nested-Predecessor Boundary

A stronger theorem also showed the same direction:

```text
forall x. (x != zero and x != s(zero)) -> exists y. x = s(s(y))
```

Ordinary Q-as-antecedent found a proof at fuel 16. The profile returned `()` at
fuel 16. The wider combined scan hit a `180 s` timeout at the next fuel, so the
contextual theorem above is the cleaner reproducible acceptance candidate.

## Design Implication

The next Q-profile expressivity ADR should not add another theorem-specific
closure. It should make Q3 available as a reusable branch rule that can produce
a predecessor equality, or as an equivalent congruence-aware closure rule, with
relevance control so predecessor introduction does not explode search. A
minimum red test should use the contextual theorem above and require proof
evidence showing:

- ordinary tableau exposure of `x != zero`;
- Q conversion of `add(y, s(zero))` to `s(y)`;
- Q3 predecessor use for `x = s(y)`;
- equality or congruence closure under the outer successor context.

## Resolution

ADR-0052 implemented that direction. The current `:robinson-q` profile uses a
single `q3-predecessor-equality` rule for direct Q3, add-one Q3, and this
contextual theorem. The old `q3-case-split` and `q3-predecessor-intro` proof
markers are now historical. See
[AAR-0052](../aar/AAR-0052-unified-robinson-q3-theory-rule.md).
