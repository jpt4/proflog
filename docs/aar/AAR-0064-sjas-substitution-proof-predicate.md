# AAR-0064: SJAS Substitution-Proof Predicate

- Date: 2026-05-14
- Related ADR: [ADR-0064](../adr/ADR-0064-sjas-substitution-proof-predicate.md)
- Branch: `adr-0064-sjas-subst-proof`
- Status: completed

## Outcome

ADR-0064 adds the Level-1 substitution-proof vocabulary missing after
ADR-0063. The SJAS language now declares `subst-prf/4`, and generated Level-1
Group-3 formulas cite `subst-prf(system-code, substitution-code, theorem-code,
proof-code)` rather than raw `tableau-proof/3`.

The implementation is intentionally finite. For the current `IS#_D(beta)`
substrate, `subst-prf/4` consults identity-substitution entries generated for
the formulas in the active reflected system. It then decodes the supplied proof
code and checks the substituted theorem through the same kernel proof route used
by `tableau-proof/3`.

## Evidence

Red evidence:

```text
lein test :only proflog.willard-sjas-test/sjas-profile-languages-have-binary-u-grounding-shape \
          :only proflog.willard-sjas-test/sjas-level1-group-three-uses-substitution-proof-vocabulary \
          :only proflog.willard-sjas-test/sjas-subst-prf-checks-identity-substitution-certificates

ERROR: No such var: sjas/subst-prf
```

Focused selector reruns after implementation:

```text
lein test :only proflog.willard-sjas-test/sjas-profile-languages-have-binary-u-grounding-shape
Ran 1 tests containing 36 assertions.
0 failures, 0 errors.

lein test :only proflog.willard-sjas-test/sjas-level1-group-three-uses-substitution-proof-vocabulary
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-identity-substitution-certificates
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
```

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 17 tests containing 152 assertions.
0 failures, 0 errors.
real 299.59 s
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 96.78 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 219.78 s
```

## What Worked

- `subst-prf/4` is declared in both SJAS profile languages.
- A valid beta theorem certificate is accepted through `subst-prf/4`.
- The same certificate is rejected when paired with the wrong theorem code.
- An unrelated malformed certificate is rejected.
- Level-1 Group-3 contains `neg-pair/2` and `subst-prf/4`, and does not contain
  raw `tableau-proof/3`.
- The focused SJAS suite still exercises binary arithmetic, forward/answer/
  partial-synthesis modes, formula-code predicates, tableau proof checking, and
  the bounded contradiction probe.

## Remaining Boundaries

- This ADR does not implement a general `Subst` parser/evaluator over arbitrary
  formula-code variables. The generated substitution boundary currently records
  identity substitutions for closed formulas in the finite system.
- The implementation still demonstrates a finite `IS#_D(beta)` executable
  substrate. It should not be described as a proof of Willard's external
  consistency-preservation theorem.
