# AAR-0062: Non-Vacuous SJAS Self-Justification Demonstration

- Date: 2026-05-14
- Related ADR: [ADR-0062](../adr/ADR-0062-sjas-self-justification-demonstration.md)
- Branch: `adr-0062-sjas-self-justification-demonstration`
- Status: completed

## Outcome

ADR-0062 corrected the SJAS self-consistency demonstration so the proof
predicate no longer fails vacuously on an unregistered contradiction code.

The generated `:sjas/proof-targets` table now includes:

- `contradiction-code`, mapped to the theorem target for `false` from the
  generated SJAS axiom basis;
- every ordinary generated formula code, preserving the ADR-0061 behavior;
- every complement code `not-code(c)`, mapped to the theorem target for the NNF
  complement of the formula coded by `c`.

The `tableau-proof/3` checker still decodes a concrete proof certificate and
calls the kernel relation with that decoded proof term. No host-side consistency
oracle or special contradiction shortcut was introduced.

## Evidence

Initial red tests:

```text
lein test :only proflog.willard-sjas-test/sjas-system-builder-generates-groups-and-reflected-boundary
FAIL: SelfCons must quantify over a contradiction code that has a concrete tableau-proof target
real 11.36 s
```

The expanded self-justification regression then exposed that real Group-3
certificates can contain nested generic kernel profile tags such as
`first-order`. The certificate vocabulary was extended to encode those proof
tags rather than replacing the proof with a special SJAS-shaped certificate.

Focused verification:

```text
lein test-proflog-sjas
Ran 13 tests containing 125 assertions.
0 failures, 0 errors.
real 33.95 s
```

Regression gates, run after the focused SJAS suite:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 100.47 s
```

```text
lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 227.65 s
```

## What Worked

- `contradiction-code` now has a concrete meaning in the proof checker: it is
  the code whose target is proving `false` from the generated SJAS basis.
- Complement codes are no longer metadata holes. Level-1 checks can ask about
  `not-code(c)` without failing solely because the code has no proof target.
- A generated Group-3 formula can be proved as an SJAS theorem and its actual
  kernel proof can be encoded and checked by `tableau-proof/3`.
- An intentionally inconsistent control system with beta `false` produces a
  contradiction proof, and `tableau-proof(system, contradiction-code, cert)`
  accepts the certificate. This is the key non-vacuity witness.

## Remaining Boundaries

- This is still a finite `IS#_D(beta)`-style executable substrate. It does not
  mechanize Willard's external consistency-preservation theorem.
- The Level-1 profile still uses plain semantic tableaux as the reflected
  deduction method. Tab-1/proof-list theorem reuse remains unimplemented.
- Open proof-certificate synthesis remains too expensive for the focused suite;
  the promoted contradiction checks use concrete certificates.
