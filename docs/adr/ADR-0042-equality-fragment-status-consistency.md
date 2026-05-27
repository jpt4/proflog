# ADR-0042: Equality-Fragment Status Consistency

- Status: completed
- Date: 2026-05-06
- Branch: `adr-0042-equality-fragment-status-consistency`
- AAR: [AAR-0042](../aar/AAR-0042-equality-fragment-status-consistency.md)
- Depends On:
  - [ADR-0039](ADR-0039-kernel-level-group-verification.md)
  - [ADR-0040](ADR-0040-legacy-subsumption-parity.md)

## Context

ADR-39 promoted `proflog.kernel.equality-fragment` as a generic proof-producing
profile for finite equality-heavy verifier formulas. ADR-40 extended finite
domain coverage and recorded a problem: `warm-cool-disjoint` has success proof
evidence, but `query/query-status` can report `:inconsistent` because the
bounded failure semidecision also finds a proof through the equality-fragment
profile.

The issue is not Proflog supervaluation semantics. The observed failure proof
for `warm-cool-disjoint` uses equality-fragment universal-variable handling and
branch-local equality bindings in a way that appears to prove each disjunct
refutable under a different binding, rather than proving the positive universal
formula false under one shared counterexample.

For the finite-domain clause:

```text
forall x. x != red or (x != green and x != blue)
```

a real counterexample must use one same `x` that makes the whole disjunction
false. That would require incompatible equalities such as `x = red` and
`x = green`. The equality-fragment profile must not turn branch-local
witnessing into a proof of global failure.

## Decision

Assess and correct equality-fragment status consistency for universal formulas,
disjunction, and disequality.

The corrected behavior must distinguish:

- genuine Proflog `:unresolved` results from bounded search;
- genuine `:succeeds` and `:fails` results with proof evidence;
- legitimate semantic incompleteness from profile non-applicability; and
- proof-profile bugs that incorrectly allow both success and failure evidence.

The equality-fragment profile should either produce sound failure proofs for
universal finite-domain formulas or decline the profile and fall back to the
ordinary kernel/status behavior. It must not report `:inconsistent` merely
because branch-local proof variables were rebound independently across
disjunctive branches.

## Consequences

Fixing this may reduce the equality-fragment profile's apparent coverage if
some current failure proofs are unsound. That is acceptable. A narrower sound
profile is preferable to a broad profile that creates truth gluts at the query
boundary.

The change must preserve ADR-39's promoted GV and transition-system verifier
coverage. If the fix invalidates any promoted equality-fragment proof, the ADR
must either repair the proof discipline or explicitly reclassify that row with
evidence.

## Test Obligations

The first failing tests must include:

- `warm-cool-disjoint` status must no longer be `:inconsistent`;
- `warm-cool-disjoint` must still have success proof evidence;
- the positive failure side for `warm-cool-disjoint` must not close through an
  unsound equality-fragment proof;
- a negative finite-domain control case must still fail when a real shared
  counterexample exists;
- ADR-39 GV associativity success/failure rows must continue to close through
  the equality-fragment profile;
- transition-system totality/determinism success/failure rows must continue to
  close through the equality-fragment profile; and
- proof-shape tests must cover universal plus disjunction/disequality so the
  branch-local witness bug cannot recur.

## Exit Criteria

ADR-0042 is complete when:

- `query/query-status` no longer reports `:inconsistent` for
  `warm-cool-disjoint`;
- the equality-fragment profile has a documented and tested discipline for
  universal variables across disjunctions;
- promoted ADR-39 and ADR-40 equality-fragment rows still pass or are
  explicitly reclassified with evidence;
- the fix is recorded in the finite-domain worked examples and parity matrix if
  the public status changes; and
- the AAR explains whether the root cause was proof scoping, polarity handling,
  profile applicability, or query-status aggregation.
