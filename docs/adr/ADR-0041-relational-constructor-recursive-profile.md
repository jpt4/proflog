# ADR-0041: Relational Constructor-Recursive Profile

- Status: completed
- Date: 2026-05-06
- Branch: `adr-0041-relational-constructor-recursive-profile`
- AAR: [AAR-0041](../aar/AAR-0041-relational-constructor-recursive-profile.md)
- Depends On:
  - [ADR-0031](ADR-0031-list-family-kernel-generalization.md)
  - [ADR-0035](ADR-0035-relational-residual-continuation.md)
  - [ADR-0040](ADR-0040-legacy-subsumption-parity.md)

## Context

ADR-31 introduced `proflog.kernel.constructor-recursive` as a generic
guarded-clause sidecar. It consumes compiled guarded-clause IR, does not inspect
relation names or constructor names, and can close constructor-recursive
programs that are hard for the ordinary tableau path.

ADR-35 deliberately removed ordinary public list answer completion's dependency
on `constructor-recursive/settle-record`, replacing it with answer-overlay
residual continuation. That was the right boundary for the list-family public
answer path, but it left the constructor-recursive layer as a diagnostic oracle
with prototype `constructor-recursive-*` proof terms.

ADR-40 then used `constructor-recursive/query-records` as a focused Peano
answer profile for legacy PA12 through PA20 parity. That raised the next design
question: whether constructor-recursive descent should remain a diagnostic
sidecar or become a fully integrated, dispatched greenfield kernel profile like
the propositional, first-order, and equality-fragment profiles.

The promotion must not regress the project's relational-purity direction. In
particular, constructor-recursive operations must be relational over the
compiled Proflog representation, not host-side procedures that compute answers
for known families. Reverse and partial synthesis must execute through the same
machinery as forward mode.

## Decision

Promote constructor-recursive descent into an integrated profiled kernel path
only if it can be implemented as a relationally pure component over guarded
Proflog IR.

The target component must:

- dispatch by structural formula/program profile, not by relation name,
  constructor name, namespace, or test scenario;
- expose proof evidence under an integrated profile tag rather than relying on
  diagnostic `constructor-recursive-*` terms as the public proof boundary;
- support forward, reverse, and partial-synthesis modes with the same relation;
- avoid host-side semantic evaluators, host-side answer computation, and
  projected family-specific shortcuts;
- preserve the ordinary tableau kernel and answer-overlay semantics for
  formulas outside the constructor-recursive profile; and
- retain the old sidecar only as a diagnostic comparison tool until it can be
  retired.

## Consequences

If successful, this ADR turns the ADR-40 Peano answer parity profile into an
ordinary promoted greenfield proof profile and reduces the special status of
constructor-recursive examples.

The risk is that the current sidecar's host implementation hides operational
choices that are difficult to recover relationally. If the relational version
cannot sustain reverse and partial synthesis, the ADR must not promote a
forward-only host procedure under a kernel-profile name. In that case, the
correct result is a documented non-promotion with failing evidence and a more
precise follow-up.

## Test Obligations

The first failing tests must include:

- a dispatch test proving a constructor-recursive program enters the new
  profile and emits integrated `profiled` proof evidence;
- a non-list constructor-recursive program, such as Peano `peel/2`, proving the
  profile is not list-specific;
- forward, reverse, and partial-synthesis tests for Peano `plus/3` using the
  same promoted profile machinery;
- list `append/3` and `reverse/2` rows showing the profile handles existing
  guarded constructor-recursive programs without relation-name dispatch;
- source-audit tests rejecting `project`, host arithmetic answer computation,
  hard-family overlay calls, and relation-name/test-id dispatch in the promoted
  profile path; and
- a regression proving existing fast and extended selectors still pass when the
  promoted profile is available.

## Exit Criteria

ADR-0041 is complete when:

- the promoted constructor-recursive profile is integrated into the proof or
  answer dispatch path;
- promoted proofs contain ordinary integrated profile evidence, not only
  diagnostic sidecar proof tags;
- forward, reverse, and partial-synthesis rows execute through the same
  relational machinery;
- no promoted path uses host-side semantic answer computation or family-specific
  dispatch;
- ADR-40 Peano answer parity no longer needs to call the diagnostic sidecar
  directly; and
- the AAR records performance, remaining unsupported shapes, and whether the
  old sidecar can be retired or must remain as a diagnostic oracle.
