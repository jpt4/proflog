# ADR-0050: Kernel-Interleaved Robinson Q Theory Rules

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0050-kernel-q-theory`
- AAR: [AAR-0050](../aar/AAR-0050-kernel-interleaved-robinson-q-theory.md)

## Context

ADR-0048 and ADR-0049 made Robinson Q demonstrable, but the `:robinson-q`
profile still had the wrong architectural shape. It normalized whole formulas
before proof search and used host-side structural recognition for Q3. That
proved the examples, but it did not look like deduction modulo inside a tableau
kernel.

A deduction-modulo profile should provide theory rules to the core prover. Those
rules should be invokable when a branch reaches a relevant formula, just as the
existing propositional, first-order, equality-fragment, and recursive profiles
interoperate with ordinary kernel steps. Robinson Q should not be an external
query preprocessor that hides the theorem-prover work.

## Decision

Add a generic optional theory-rule hook to `proflog.kernel/close-agendao`.

The ordinary kernel keeps its current behavior when no hook is bound. A proof
profile can bind a relation that receives the current focused formula,
unexpanded work, saved literals, environment, equality state, disequality state,
program, gamma terms, fuel, and proof term. The hook must be a miniKanren goal,
not a host-side postprocessor.

Refactor `:robinson-q` to use that hook:

- `add` / `mul` conversion closes equality/disequality branches through a
  relational normal-form relation over terms.
- Q3 closes only when ordinary kernel steps have exposed the branch containing
  `x != zero` and `x != s(y)`.
- Proofs expose both ordinary tableau steps and theory steps, e.g. `witness`,
  `once-univ`, `neq-store`, `profiled robinson-q`, `q-rewrite`, and
  `q3-case-split`.

## Consequences

- The Q profile becomes a kernel-interleaved theory component rather than a
  query-time formula transformer.
- Q proofs become more inspectable because quantifier and branch steps remain
  in the proof object around the theory closure.
- The first relational normalizer can remain directional from a known term to a
  normal form. Full reverse synthesis over Q conversion is not a goal here.
- Later theory profiles can reuse the kernel hook without adding
  profile-specific code to the default kernel or query surface.

## Test Obligations

- Red tests must show the current Q profile lacks ordinary kernel quantifier /
  disequality evidence around Q3 and Q7 profile closures.
- Tests must require Q3 profile proofs to contain `witness`, `once-univ`,
  `neq-store`, and `q3-case-split`.
- Tests must require Q7 profile proofs to contain ordinary kernel `witness`
  evidence and `q-rewrite` evidence.
- Source audit must reject the old host-side whole-formula normalization and Q3
  formula recognizer names.
- Existing Robinson Q correctness and timing probes must still pass.

## Exit Criteria

- `:robinson-q` proofs are produced by a kernel-bound relational theory rule.
- No host-side whole-formula Q normalizer remains in the proof path.
- `lein test-proflog-robinson-q`, fast, and extended gates pass.
- Documentation and AAR records explain the architecture correction and
  remaining limits.

## After Action Summary

ADR-0050 completed the architecture correction. The Q profile now binds
`kernel/*theory-profile-closeo*` and proves through the ordinary kernel instead
of preprocessing formulas on the host. The focused Q suite requires ordinary
kernel evidence around Q closures, and the source audit rejects the old
whole-formula normalizer and Q3 structural recognizer.

The implementation exposed two reusable lessons:

- bounded `core.logic/run` results must be realized inside any dynamic theory
  binding;
- relational "not a pattern" tests need positive structural recognizers when
  the excluded pattern contains fresh variables.

The resulting profile is slower than the former host preprocessor but now has
the intended deduction-modulo shape. See
[AAR-0050](../aar/AAR-0050-kernel-interleaved-robinson-q-theory.md) for timings
and remaining limits.
