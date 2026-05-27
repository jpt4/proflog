# ADR-0065: SJAS SelfCons Substitution Fixed Point

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0065-sjas-selfcons-subst-fixpoint`
- AAR: [AAR-0065](../aar/AAR-0065-sjas-selfcons-substitution-fixpoint.md)

## Context

ADR-0064 added `subst-prf/4` and changed the generated Level-1 Group-3 formula
to cite it. That corrected the object-language vocabulary, but a completion
audit exposed a remaining fixed-point error: the generated formula used the
system code as the substitution argument.

Willard 2011 Appendix A defines `SubstPrf^d_beta(g,t,p)` through
`Subst(g,h)` and `ExPrf^d_beta(h,t,p)`: `g` is the Godel number of a formula
skeleton, `h` is the sentence obtained by substituting the numeral for `g` into
the skeleton, and the proof predicate checks proof from beta plus that added
sentence. For `SelfCons_k(beta,d)`, Equation (36) defines a skeleton
`Gamma_k(g)`, and Equation (37) uses `Gamma_k(n)` where `n` is the Godel number
of the skeleton itself.

Therefore, Level-1 Group-3 must not pass `system-code` as the substitution
argument. It must pass the code of its own self-reference skeleton.

## Decision

Generate Level-1 Group-3 in two phases:

1. Build a skeleton formula `Gamma_1(g)` whose `subst-prf/4` calls use the free
   variable `g` as their substitution argument.
2. Encode that skeleton to obtain `skeleton-code`.
3. Build the final Group-3 sentence `Gamma_1(skeleton-code)`.

Update `subst-prf/4` support so the generated substitution boundary maps:

```text
system-code, skeleton-code -> group-three-code
```

The proof checker then validates `subst-prf(system-code, skeleton-code,
theorem-code, proof-code)` by checking the proof from the current finite beta
basis plus the substituted Group-3 sentence.

This ADR still does not implement arbitrary open-code `Subst`; it corrects the
fixed-point argument used by the generated `IS#_D(beta)` sentence.

The implementation also adds a formal `sjas-axiom` proof certificate accepted
through generated `axiom-member/2` facts. That is the appropriate finite proof
line for Group-3 itself: Group-3 is a generated axiom of the reflected system,
so the fixed-point `subst-prf/4` check can cite it without asking the generic
kernel to rediscover a large axiom-theorem proof.

## Consequences

- `SelfCons1` now has the same high-level fixed-point shape as Willard Appendix
  A: the substitution argument is the skeleton code, not the system code.
- The generated system should expose the skeleton code so tests and worked
  examples can inspect the fixed-point construction.
- `subst-prf/4` must reject `system-code` as a substitute for `skeleton-code` in
  the Level-1 self-consistency path.
- `tableau-proof/3` may accept a formal axiom-citation certificate only by
  checking generated `axiom-member/2` facts for the active system.
- The next fidelity gap remains a general code-level `Subst` relation over
  arbitrary formula-code variables.

## Test Obligations

- A red test must show that the current Level-1 Group-3 formula uses
  `system-code` rather than `selfcons-skeleton-code`.
- The generated Level-1 system exposes a code term for the self-consistency
  skeleton.
- The final Group-3 formula contains `subst-prf(system-code,
  selfcons-skeleton-code, x, p)` and the corresponding `y, q` call.
- `subst-prf(system-code, selfcons-skeleton-code, group3-code,
  group3-certificate)` succeeds.
- `subst-prf(system-code, system-code, group3-code, group3-certificate)` fails.
- `tableau-proof(system-code, axiom-code, sjas-axiom-certificate)` succeeds
  for generated axioms and fails for non-axiom theorem codes.
- The focused SJAS, fast, and extended suites pass and record runtimes.
- Slow fixed-point certificate tests are acceptable because they capture the
  semantic contract of the generated proof predicate. They must be marked with
  `^:slow` and their timings recorded rather than optimized away.
