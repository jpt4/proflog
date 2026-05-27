# ADR-0046: Combinatory Logic Turing Completeness

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0045-0046-tc-performance`
- AAR: [AAR-0046](../aar/AAR-0046-combinatory-logic-turing-completeness.md)

## Context

ADR-0044 demonstrates Turing-completeness through two-counter Minsky machines.
The next line of evidence should use a different known Turing-complete model,
both to reduce dependence on one example family and to exercise a different
program shape.

SKI combinatory logic is a good second model:

- its terms are first-order constructor trees;
- its reduction rules are local equations over constructors;
- significant examples can be represented as finite reductions without host
  arithmetic;
- it is a standard Turing-complete calculus, historically tied to
  Schönfinkel's 1924 combinatory logic and Curry/Feys' 1958 presentation.

Compared with Minsky machines, SKI emphasizes symbolic term rewriting rather
than counter-state transition. That makes it a useful independent test of
Proflog's ability to encode computation.

## Decision

Add `proflog.combinatory-logic`, a frontend Proflog program for a small SKI
reduction calculus:

- constants: `scomb`, `kcomb`, `icomb`, plus data constants used by examples;
- function: `ap/2` for application;
- relations:
  - `step/2` for one reduction;
  - `eval-for/3` for bounded finite reduction.

The core rules are:

```text
step(ap(icomb, x), x).
step(ap(ap(kcomb, x), y), x).
step(ap(ap(ap(scomb, x), y), z), ap(ap(x, z), ap(y, z))).
step(ap(f, arg), ap(f2, arg)) :- step(f, f2).
```

The final rule is an explicit left-spine application context. It is required for
ordinary curried examples such as `((K I) a) b`, where the next redex is inside
the function position of the outer application. This remains a kernel-level
relation: the host builds terms and formulas only, and the recursive `step/2`
call is proved by the proof kernel.

`eval-for/3` is the same Peano-bounded reflexive reduction relation used by the
Minsky demonstration: `eval-for(zero, start, final)` requires equality, and
`eval-for(s(rest), start, final)` requires one `step/2` followed by recursive
`eval-for(rest, middle, final)`.

## Exit Criteria

- Red tests first fail before `proflog.combinatory-logic` exists.
- Tests prove root reductions for `I`, `K`, and `S`.
- Tests prove significant fully evaluating examples, including:
  - `SKK a` reduces to `a`;
  - boolean-style examples using `K` and `K I` reduce to their expected data
    constants.
- At least one answer-mode test exports a reduced term.
- Source audit proves no host-side SKI evaluator is present.
- Worked examples explain the pseudo-code, frontend source, backend descent,
  evaluation process, test results, and shortcomings.
- Timings are recorded in `docs/TEST_RUNTIME_BASELINE.md`.
- AAR-0046 records the result.

## Non-Goals

- This ADR does not add a full lambda-to-SKI translator.
- This ADR does not prove confluence or normalization for arbitrary SKI terms.
- This ADR does not require open-ended reduction search.

## Risks

Full contextual reduction can introduce expensive search. This ADR adds only the
left-spine context needed by normal curried SKI examples; right-argument
contextual reduction remains a later extension unless a worked example requires
it.
