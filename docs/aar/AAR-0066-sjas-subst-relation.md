# AAR-0066: SJAS Finite Substitution Relation

- Date: 2026-05-14
- Related ADR: [ADR-0066](../adr/ADR-0066-sjas-subst-relation.md)
- Branch: `adr-0066-sjas-subst-relation`
- Status: completed

## Outcome

ADR-0066 promotes the finite Willard substitution boundary into an explicit
object-language relation, `subst-code/2`. Generated systems now expose
substitution facts independently from `subst-prf/4`:

```text
subst-code(source-code, substituted-code)
```

For the current finite `IS#_D(beta)` substrate, the generated relation includes
identity substitutions for closed generated formula codes and the Level-1
fixed-point entry:

```text
selfcons-skeleton-code -> group-three-code
```

`subst-prf(system-code, substitution-code, theorem-code, proof-code)` now checks
that `substitution-code` maps to some substituted code for the active system,
then verifies a proof of `theorem-code`. The theorem code no longer has to be
identical to the substituted code.

## Evidence

Red evidence:

```text
lein test :only proflog.willard-sjas-test/sjas-subst-code-relates-generated-substitution-codes
Syntax error compiling ... No such var: sjas/subst-code
real 12.05 s
```

Focused selector reruns after implementation:

```text
lein test :only proflog.willard-sjas-test/sjas-subst-code-relates-generated-substitution-codes
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 44.10 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-uses-substitution-code-independently-of-theorem-code
Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
real 25.84 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-identity-substitution-certificates
Ran 1 tests containing 5 assertions.
0 failures, 0 errors.
real 188.78 s

lein test-proflog-sjas-slow
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 91.34 s
```

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 22 tests containing 169 assertions.
0 failures, 0 errors.
real 561.14 s
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 97.61 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 225.08 s
```

## What Worked

- Both SJAS profile languages declare `subst-code/2`.
- `subst-code(selfcons-skeleton-code, group-three-code)` succeeds.
- `subst-code(beta-code, beta-code)` succeeds for a closed generated formula.
- `subst-code(system-code, group-three-code)` fails.
- `subst-prf(system, selfcons-skeleton-code, beta-code, sjas-axiom)` succeeds,
  demonstrating that substitution code and theorem code are no longer coupled.
- The carried identity and fixed-point `subst-prf/4` checks remain green under
  the new formula-code identity convention.

## Remaining Boundaries

- `subst-code/2` is still generated for the finite active system. It is not yet
  an arbitrary parser/evaluator for every possible formula code term.
- Proof checking remains finite and profile-dispatched. The current
  implementation demonstrates the executable `IS#_D(beta)` substrate, not
  Willard's external consistency-preservation metatheorem.
