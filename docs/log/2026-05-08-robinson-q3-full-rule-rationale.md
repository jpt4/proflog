# Robinson Q3 Full Rule Rationale

Date: 2026-05-08

## Context

ADR-0049 and ADR-0050 support Q3 in the `:robinson-q` deduction-modulo profile
as a focused branch-closing rule. The current rule closes the direct refutation
shape for Q3 after ordinary tableau search exposes:

```text
x != zero
forall y. x != s(y)
```

Equivalently, after `exists x`, `and`, and `once-forall` tableau steps, the
profile records:

```clojure
(q3-case-split predecessor-or-zero x y)
```

This proves Q3 itself in the profile, but it is not a general predecessor rule.
It does not synthesize a predecessor `p`, add `x = s(p)` to the branch, and let
the kernel continue proving other obligations with that equality.

## Why This Matters

A valid Q theorem that requires general Q3 use is:

```text
forall x. x != zero -> exists y. add(y, s(zero)) = x
```

This is Q3 expressed through addition. By Q5 and Q4:

```text
add(y, s(zero)) = s(add(y, zero)) = s(y)
```

So the theorem is equivalent, up to equality orientation and definitional
addition conversion, to:

```text
forall x. x != zero -> exists y. s(y) = x
```

That is Q3.

In tableau form, proving the theorem means refuting its negation:

```text
exists x. x != zero and forall y. add(y, s(zero)) != x
```

The current focused `q3-case-split` rule cannot close this branch directly,
because the universal disequality is not syntactically `x != s(y)`. A full Q3
theory rule would need to:

1. use `x != zero` to introduce a fresh predecessor `p`;
2. add or expose `x = s(p)` on the branch;
3. instantiate the universal with `p`;
4. normalize `add(p, s(zero))` to `s(p)` by Q5 and Q4;
5. close `add(p, s(zero)) != x` against `x = s(p)`.

This is not merely a convenience issue. It changes which Q theorems the
deduction-modulo profile can refute after negation.

## Why Other Q Rules Are Insufficient

The theorem above is not derivable from Q1, Q2, Q4, Q5, Q6, and Q7 alone. A
standard model sketch without Q3 has:

```text
Domain: N plus a second chain a0, a1, a2, ...
zero = 0
s(n) = n + 1
s(ai) = a(i+1)
```

In this model, successor is injective and never returns `zero`, so Q1 and Q2
hold. Addition and multiplication can satisfy Q4-Q7 by the usual recursion on
right successor arguments, while leaving unconstrained behavior at `a0` chosen
arbitrarily.

But `a0` is nonzero and has no predecessor. Therefore there is no `y` with:

```text
s(y) = a0
```

and since `add(y, s(zero)) = s(y)`, there is also no `y` with:

```text
add(y, s(zero)) = a0
```

By soundness, any proof of the theorem must use Q3 or a lemma equivalent to
Q3. The current profile can prove the direct Q3 refutation shape, but it cannot
yet use Q3 constructively inside larger tableau branches.

## Design Implication

A future full-Q3 ADR should treat Q3 as a relevance-controlled kernel theory
rule, not as a rewrite. The rule should be able to introduce a predecessor and
branch equality when a branch contains a nonzero term and a downstream
obligation can use the predecessor. It must be fuel- and relevance-controlled,
because unrestricted predecessor introduction can create irrelevant search.

The minimal acceptance test for that future work should include refuting:

```text
exists x. x != zero and forall y. add(y, s(zero)) != x
```

under the `:robinson-q` profile, with proof evidence showing ordinary tableau
steps, a full-Q3 predecessor introduction, Q4/Q5 conversion, and equality or
disequality closure.

## Follow-Up

ADR-0051 implements this minimal acceptance test with the proof tag
`q3-predecessor-intro`. The implemented rule remains relevance-controlled: it
uses a saved nonzero premise and a current Q-normalized successor of a
proof-local universal variable, not unrestricted predecessor generation.
