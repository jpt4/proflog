# SJAS General Subst Code

Date: 2026-05-15

ADR: [ADR-0069](../adr/ADR-0069-sjas-general-subst-code.md)

## Summary

ADR-0069 completes the general formula-code substitution gap in the finite
ordinary-tableau `IS#_D(beta)` substrate. The SJAS profile no longer consults
generated `subst-code` entries. Instead, it decodes the source formula code,
substitutes the quoted source code term for free variable index `1`, respects
quantifier shadowing, leaves embedded code terms opaque, and checks the target
formula modulo bound-variable alpha-renaming.

The alpha-renaming step is necessary because the Level-1 fixed-point skeleton
uses `v0` for the diagonal variable. After substitution, the final Group-3
sentence is encoded with compact binder numbering, so exact decoded syntax is
too strict even though the formulas are alpha-equivalent.

## Implementation Notes

- `src/proflog/kernel/willard_sjas_profile.clj` now contains structural
  substitution relations over the decoder's internal term/formula trees.
- `subst-code/2` compares substituted source syntax to the decoded target via
  an alpha-equivalence relation for bound object variables.
- `src/proflog/willard_sjas.clj` no longer emits `:sjas/subst-code-entries`.
- `subst-prf/4` uses a source-only well-formedness check when it needs only the
  existence of a substitution result, avoiding public code synthesis in ordinary
  theorem-proof branches.
- A stale `subst-prf/4` branch that tried to prove a theorem from the
  substituted formula as an extra premise was removed. ADR-0066 defines the
  predicate as substitution-source validation plus proof checking for the
  supplied theorem code.

## Test Evidence

- Red focused test: `sjas-subst-code-computes-general-formula-code-substitution`
  failed with `3` failures in `real 71.95 s`.
- Green focused tests:
  - general non-identity substitution: `real 166.54 s`;
  - structural fixed-point/identity substitution codes: `real 222.93 s`;
  - substitution-code independence for `subst-prf/4`: `real 194.00 s`;
  - identity substitution certificates: `real 335.45 s`;
  - fixed-point certificate: `real 434.16 s`.
- Gates:
  - `lein test-proflog-sjas-slow`: `5` tests, `22` assertions,
    `real 915.85 s`;
  - `lein test-proflog-sjas`: `26` tests, `188` assertions,
    `real 2057.15 s`;
  - `lein test-proflog-fast`: `145` tests, `548` assertions,
    `real 143.16 s`;
  - `lein test-proflog-extended`: `68` tests, `203` assertions,
    `real 349.26 s`.

## Remaining Boundaries

The finite `IS#_D(beta)` substrate now has structural formula-code
substitution. The remaining documented SJAS boundaries are proof-list/Tab-1
theorem reuse and open proof-code synthesis. Both are separate from the
ordinary-tableau `D` profile completed here.
