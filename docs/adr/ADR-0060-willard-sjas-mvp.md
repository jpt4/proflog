# ADR-0060: Willard SJAS MVP Implementation

- Status: completed
- Date: 2026-05-10
- Branch: `adr-0060-willard-sjas-mvp`
- AAR: [AAR-0060](../aar/AAR-0060-willard-sjas-mvp.md)

## Context

ADR-0058 defines the staged Willard-style SJAS language profile and ADR-0059
records an independent review of the same corpus and Proflog integration
boundary. The next step is to turn those records into an executable MVP:

- users can construct a finite SJAS system without hand-writing Group-3;
- the generated system names a stable reflected axiom basis;
- the selected proof profile routes through the ordinary kernel and exposes its
  profile in proof evidence;
- proof-coding and small certificate checks are executable Proflog relations,
  not host-side proof validators.

This ADR does not try to mechanize Willard's external
consistency-preservation metatheorem. It implements the minimum useful
SJAS-lang substrate needed to experiment with the object-language shape and
proof route.

## Decision

Implement `proflog.willard-sjas` and
`proflog.kernel.willard-sjas-profile`.

The MVP includes both named profiles from ADR-0058:

- `:willard-sjas-tableau0`, an ordinary-tableau contradiction-freedom profile;
- `:willard-sjas-level1`, a Level-1 profile using plain semantic tableaux as
  the reflected deduction method `D`.

The MVP does not claim Tab-1 theorem reuse. Tab-1 proof-list checking remains
out of scope unless a later ADR selects it explicitly.

The public builder should:

- expose U-grounding language declarations with total addition and no
  multiplication function symbol;
- expose `mult/3` as the multiplication graph relation;
- accept finite beta axioms and reflected user clauses;
- compile reflected user clauses into the finite Group-2/Group-2b basis;
- generate stable formula codes and `axiom-member` clauses;
- generate `SelfCons0` or `SelfCons1` as a Group-3 object-language formula;
- distinguish reflected clauses from optional external application clauses;
- provide a thin query helper that proves from the generated SJAS basis.

The proof profile may use host Clojure for source-to-kernel construction,
stable coding, and finite program assembly. Once the system has been compiled,
bounded arithmetic graph checking, formula-class recognition, axiom membership,
and miniature proof-certificate checks must be expressed as kernel-visible
relations or profile theory steps with auditable proof evidence. No promoted
path may call a hidden host proof checker for these predicates.

## Consequences

- The implemented system is an MVP SJAS-lang substrate, not a proof of
  Willard's metatheorem.
- The first proof-certificate checker is intentionally miniature: it must accept
  and reject concrete tableau-certificate shapes, but it need not encode every
  tableau rule before future work.
- Generated system ids are semantic artifacts. They must change when reflected
  beta axioms or reflected program clauses change, and must not change when only
  external application clauses change.
- Slow reflection and bounded contradiction probes belong in a focused SJAS
  selector rather than the default fast suite unless their runtime remains small.

## Test Obligations

Implementation must start with failing tests covering:

- language shape: U-grounding functions and `mult/3` are present, `mul/2` is
  absent, and both SJAS proof-profile keys are exposed;
- classifier behavior: positive and negative Delta-star-0, Pi-star-1, and
  Sigma-star-1 examples;
- U-grounding arithmetic behavior: forward, answer, and partial-synthesis modes
  for representative arithmetic and `mult/3` graph goals;
- proof-certificate behavior: valid miniature tableau certificates are accepted
  and malformed/open certificates are rejected;
- generated system behavior: Group-Zero through Group-3 are present, Group-3
  has a stable code, and a beta consequence plus the generated self-consistency
  formula can be queried through the selected profile;
- authoring behavior: users can build an SJAS system by supplying profile,
  beta axioms, reflected program clauses, and optional external clauses without
  manually constructing Group-3 or `axiom-member`;
- reflected-boundary behavior: reflected beta/program changes alter the system
  id and Group-3, external-only changes do not;
- route audits: the promoted profile namespace does not call a host proof
  checker or whole-formula proof-time normalizer;
- bounded contradiction probes record whether simultaneous Level-1 complement
  proofs are found under the selected fuel/time limit.

## Exit Criteria

- The implementation files, tests, tutorial/worked examples, `LOG.md`, ADR
  index, and AAR are updated.
- `lein test-proflog-sjas` passes and records timings.
- `lein test-proflog-fast` passes and records timings.
- `lein test-proflog-extended` is run before the final commit if proof search,
  equality, negation, or query behavior changed; otherwise the rationale for
  not running it is recorded.
- A completion audit maps every ADR-0058, ADR-0059, and ADR-0060 obligation to
  concrete evidence or to an explicitly documented follow-up limitation.
