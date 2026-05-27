# AAR-0069: SJAS General Formula-Code Substitution

- Date: 2026-05-15
- Related ADR: [ADR-0069](../adr/ADR-0069-sjas-general-subst-code.md)
- Branch: `adr-0069-sjas-general-subst-code`
- Status: completed

## Outcome

ADR-0069 removes the generated `:sjas/subst-code-entries` table and makes
`subst-code/2` compute diagonal substitution by decoding formula-code bytes.
The profile now substitutes the quoted source code term for free variable
index `1`, respects quantifier shadowing, leaves embedded code terms opaque,
and compares the decoded target modulo bound-variable alpha-renaming.

The alpha-equivalence check is required for the Level-1 fixed point: the
`Gamma_1(g)` skeleton reserves `v0` for the diagonal variable, so its bound
variables are numbered from `v1`, while the final Group-3 sentence is encoded
with compact binder names starting at `v0`.

`subst-prf/4` was also tightened to match ADR-0066: it validates that the
substitution source is a well-formed formula code when only existence of a
substitution result is needed, and it invokes full `subst-code(source,target)`
only when a concrete target code must be compared. A stale proof branch that
attempted to prove a theorem from the substituted formula as an added premise
was removed because `subst-prf/4` checks a proof of the supplied theorem code.

## Evidence

Red evidence:

```text
lein test :only proflog.willard-sjas-test/sjas-subst-code-computes-general-formula-code-substitution
Ran 1 tests containing 6 assertions.
3 failures, 0 errors.
real 71.95 s
```

The failures showed that generated substitution entries were still present,
`subst-code(code(wff(v0)), code(wff(code(wff(v0)))))` failed, and the identity
fallback incorrectly accepted the open source formula.

Focused post-implementation selectors passed:

```text
lein test :only proflog.willard-sjas-test/sjas-subst-code-computes-general-formula-code-substitution
Ran 1 tests containing 6 assertions.
0 failures, 0 errors.
real 166.54 s

lein test :only proflog.willard-sjas-test/sjas-subst-code-relates-structural-substitution-codes
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 222.93 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-uses-substitution-code-independently-of-theorem-code
Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
real 194.00 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-identity-substitution-certificates
Ran 1 tests containing 5 assertions.
0 failures, 0 errors.
real 335.45 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-selfcons-fixed-point-certificate
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 434.16 s
```

Regression gates:

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

## What Worked

- General diagonal substitution now handles non-generated open formula codes.
- Open formula identity no longer succeeds accidentally.
- Quantifier shadowing is tested directly.
- The Level-1 `selfcons-skeleton-code -> group-three-code` relation is derived
  structurally rather than emitted as a generated substitution entry.
- `subst-prf/4` still supports theorem-code independence for axiom proofs while
  rejecting invalid substitution source codes.

## Remaining Boundaries

- The structural relation is intentionally slow for large SJAS formulas. The
  cost is documented and isolated in the slow/SJAS gates rather than hidden by
  generated answer tables.
- Open proof-code synthesis remains an exploratory search problem. ADR-0069
  completes formula-code substitution and the finite ordinary-tableau
  `IS#_D(beta)` substrate; it does not implement Tab-1/proof-list theorem reuse.
