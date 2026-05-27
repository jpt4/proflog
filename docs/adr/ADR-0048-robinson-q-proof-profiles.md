# ADR-0048: Robinson Q Proof Profiles

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0048-robinson-q`
- AAR: [AAR-0048](../aar/AAR-0048-robinson-q-proof-profiles.md)

## Context

Robinson arithmetic Q is a small first-order theory of equality over the term
language:

```text
zero
s/1
add/2
mul/2
```

The theory is a useful stress test for Proflog because its symbols are
functions, not relations. A term such as `mul(x, s(y))` does not call a
procedure; it is an object-language term that can only be compared by equality,
ordinary axioms, or a trusted theory conversion relation.

The earlier design note
[Robinson Q And Deduction Modulo Notes](../log/2026-05-08-robinson-q-deduction-modulo.md)
records the semantic distinction. If Q7 is passed as an assumption, proving Q7
amounts to closing `Q7 and not Q7`. If Q7 is promoted to a deduction-modulo
conversion rule, proving the formula for Q7 should show a conversion step such
as:

```text
mul(a, s(b)) -> add(mul(a, b), a)
```

followed by reflexive equality.

## Decision

Implement two Robinson Q paths and compare them on a common set of formulas.

The first path keeps Q ordinary. The seven Q formulas are built as Proflog
formulae and can be conjoined as the antecedent of an implication:

```text
Q1 and ... and Q7 -> theorem
```

This path uses the existing kernel and demonstrates what the theory means as
plain assumptions.

The second path introduces an opt-in proof-profile mechanism. A language can
select a proof profile, initially `:robinson-q`, and query execution dispatches
through that profile before calling the ordinary kernel. The dispatch mechanism
must be generic: future deduction-modulo or theory profiles should register a
profile key rather than requiring Q-specific branches in query code.

The `:robinson-q` profile treats the recursive defining equations for `add`
and `mul` as conversion rules:

```text
add(x, zero) -> x
add(x, s(y)) -> s(add(x, y))
mul(x, zero) -> zero
mul(x, s(y)) -> add(mul(x, y), x)
```

Q1 and Q2 remain aligned with Proflog's constructor equality behavior. Q3 is
not promoted in this ADR because it is a predecessor-or-zero case split rather
than a terminating rewrite rule.

Proof evidence for the profile must expose that a theory profile was used, so
users can distinguish a deduction-modulo proof from an ordinary proof from
assumptions.

## Consequences

- Q-as-antecedent remains the conservative first-order reading.
- The profile path is intentionally trusted theory conversion, not a derivation
  of the conversion rules from weaker axioms.
- The first `:robinson-q` implementation is expected to work best for formulas
  whose arithmetic terms are visible before or during kernel descent. Deeper
  interaction with substitutions and generated terms may require later
  congruence or branch-local normalization.
- The proof-profile dispatch layer is part of the public architecture even
  though `:robinson-q` is the first concrete non-default profile.

## Test Obligations

- Red tests must first require Q helpers and profile dispatch that do not yet
  exist.
- Q helper tests must build Q1-Q7 over a language whose arithmetic symbols are
  functions, not relations.
- Ordinary Q-as-antecedent tests must prove at least the Q7 formula and a
  ground arithmetic theorem through the existing kernel path.
- Profile tests must prove the same selected formulas through a
  `:robinson-q` language.
- Profile proof evidence must show an explicit `:robinson-q` profile wrapper
  or equivalent auditable theory-conversion marker.
- Frontend or language tests must show that proof-profile selection is generic
  language metadata rather than Q-specific query code.
- Comparison records must report the passing runtimes for the common formulas.

## Exit Criteria

- `proflog.robinson-q` exposes the Q language, Q axiom formulae, arithmetic
  term constructors, ordinary Q-as-antecedent formula construction, and a
  profile-enabled Q program.
- A generic proof-profile dispatch layer routes the default path to the
  existing kernel and routes `:robinson-q` through the Q conversion profile.
- The shared Robinson Q test suite passes and covers both ordinary and profiled
  paths.
- Documentation explains how the two paths differ semantically and records
  correctness/performance outcomes, including shortcomings.
- Fast and relevant focused regression selectors pass.
- AAR-0048 records the final performance comparison, limitations, and follow-up
  work.
