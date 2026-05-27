# SJAS IS#_D(beta) Completion Audit

Date: 2026-05-15

## Objective

Implement a general `Subst` over formula codes, and any other facilities needed
for a complete implementation of the finite ordinary-tableau `IS#_D(beta)`
system in the Proflog repository.

## Completed Scope

- Arithmetized public formula, proof, and system codes are compact base-64
  object-language terms.
- `wff/1`, formula-class predicates, and `neg-pair/2` can decode formula-code
  bytes structurally.
- `tableau-proof/3` and `subst-prf/4` can build theorem targets from decoded
  theorem-code bytes and check real kernel proof certificates.
- `subst-code/2` now computes diagonal formula-code substitution structurally,
  including non-generated open formulas, quantifier shadowing, and the
  Level-1 SelfCons skeleton-to-Group-3 fixed point.
- `subst-prf/4` validates the substitution source independently of the theorem
  code and then verifies the supplied theorem proof, matching ADR-0066.
- Generated substitution entries have been removed from the SJAS registry.

## Evidence

The ADR-0069 verification suite passed:

```text
lein test-proflog-sjas-slow
Ran 5 tests containing 22 assertions.
0 failures, 0 errors.
real 915.85 s

lein test-proflog-sjas
Ran 26 tests containing 188 assertions.
0 failures, 0 errors.
real 2057.15 s

lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 143.16 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 349.26 s
```

## Completion Judgment

For the selected `D` as Proflog's ordinary semantic-tableau proof kernel, the
finite `IS#_D(beta)` implementation is complete enough for the current project
claim: it mechanizes the generated SJAS language, finite reflected beta basis,
Group-3 SelfCons formula, arithmetized code predicates, diagonal substitution,
and checked proof predicates inside the kernel/profile layer.

The remaining SJAS boundaries are not blockers for this objective:

- Tab-1/proof-list theorem reuse is a different deductive-apparatus profile,
  not part of the ordinary-tableau `D` selected here.
- Open proof-code synthesis remains operationally expensive and is not promoted
  as a default regression. Ground proof-code checking is covered.
