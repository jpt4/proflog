# SJAS Correspondence Proof Criteria

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

Prior Track 2a summary:
[SJAS Track 2a Coverage Status](2026-05-26-sjas-track2a-coverage-status.md)

## Purpose

This note records the criteria for completing ADR-0073 Track 2b. Track 2b is
not complete merely when current tests pass, or when Proflog and the SJAS
predicate accept the same sample theorem codes. It is complete only when the
kernel bridge is backed by a proper correspondence proof and by operational
tests that exercise the proof's boundary conditions.

The target theorem has the shape:

```text
For every covered system code S, theorem code F, and proof certificate P:
  ProflogAccepts(P, S, F)
  iff
  SJAS_TableauProof(translate(P,S,F), S, F)
```

The theorem is intentionally over proof objects, not only over theorem
extension. SJAS self-reference is sensitive to proof-predicate intensional
structure, so a result that preserves only "the same formulas are provable" is
not sufficient.

## Completion Criteria

Track 2b is complete only if all criteria below are satisfied.

### 1. Fixed Covered Domain

The proof must state the exact domain over which the biconditional ranges:

- covered SJAS profiles, currently at least distinguishing
  `:willard-sjas-tableau0` from `:willard-sjas-level1`;
- covered system-code format, including compact base-64 terms and, if claimed,
  U-Grounding numeral codes;
- covered beta assumptions, including whether beta truth and formula-class
  validity are preconditions or proved inside the theorem;
- covered theorem-code class, including generated axiom codes, structurally
  decoded non-generated formulas, Group-3 sentences, and Level-1 substituted
  fixed-point formulas when claimed;
- covered proof-certificate alphabet.

Every unencodable or unclassified proof tag must be handled before inclusion in
the domain. Handling means one of:

1. add it to the SJAS proof-code grammar with a formal rule;
2. prove it is a bounded macro over accepted SJAS tableau structure;
3. prove it is unreachable in the covered domain;
4. exclude the fragment that reaches it.

No proof may silently quantify over proof certificates that contain tags such
as `free-close`, `sjas-code-arg`, profile sidecar tags, generic equality tags,
or procedure-call tags unless those tags have been discharged by one of those
four routes.

### 2. Compatible Formal Semantics

The proof must put both sides into a common formal setting.

For the Proflog side, it must define a relation equivalent to the relevant
kernel behavior, not just cite the implementation informally. A suitable direct
definition would include an inductive relation over:

- focused formula and pending branch agenda;
- saved branch literals;
- quantifier environment;
- proof-time variables and rigid parameters;
- equality substitution and disequality store;
- reflected program clauses;
- SJAS theory-profile branch rules;
- proof certificate tree.

For the SJAS side, it must define the semantic-tableau proof predicate over
encoded objects:

- decoded system code and axiom basis;
- decoded theorem/formula code;
- formula-bearing tableau tree;
- alpha, beta, gamma, delta, closure, axiom, substitution, equality, and
  profile-specific rules that are included in the domain;
- proof-code and substitution-code inspectability.

The common setting may be one of:

- a direct mathematical structural semantics for both relations;
- a shared intermediate proof-tree language into which both sides are translated;
- a third-party formal verification artifact for one or both sides.

If the proof is by direct examination rather than by a proof assistant, it must
still state inference rules precisely enough that each implementation clause can
be matched to a rule or to an explicit excluded fragment.

### 3. Translation and Reconstruction

The theorem must define `translate(P,S,F)` or an equivalent relation that
reconstructs the SJAS-side proof tree from the Proflog certificate and target.

The reconstruction must not be an opaque call back into `kernel/prove-programo`.
It may replay the same formal rules, but the replay must be specified over the
object-level proof semantics and encoded formula/system/proof data.

For skeletal Proflog proof constructors such as `univ`, `once-univ`, and
`witness`, the translation must recover the relevant formula-bearing node,
witness term, parameter, freshness fact, and branch continuation. If that
information is not derivable from the certificate plus `(S,F)`, the certificate
format must be enriched or the fragment must be excluded.

### 4. Soundness Direction

The soundness direction is:

```text
ProflogAccepts(P, S, F)
implies
SJAS_TableauProof(translate(P,S,F), S, F)
```

It must show that every accepted Proflog certificate in the covered domain maps
to a valid SJAS semantic-tableau proof. The proof must proceed by structural
induction over the Proflog acceptance derivation or over an equivalent
intermediate semantics.

Each Proflog kernel step must be matched to:

- the corresponding SJAS tableau rule;
- an admitted SJAS primitive rule;
- a proved bounded macro expansion;
- or an explicitly excluded constructor.

### 5. Completeness Direction

The completeness direction is:

```text
SJAS_TableauProof(T, S, F)
implies
there exists P such that ProflogAccepts(P, S, F)
and translate(P,S,F) = T up to proved irrelevant detail.
```

This direction prevents the bridge theorem from being only a conservative
soundness wrapper. It must show that the covered SJAS proof apparatus is not
stricter than the Proflog kernel on the same proof objects.

Any quotienting by "irrelevant detail" must be named and justified by an
irrelevance lemma.

### 6. Relevant Invariant Preservation

The correspondence must preserve every Track 2a feature classified as relevant:

- finite tableau tree shape;
- root theorem target and axiom/deduction ancestry;
- parent-child structure induced by alpha, beta, gamma, and delta rules;
- branch closure conditions;
- formula, system, proof, substitution, and axiom-basis code inspectability;
- byte-string arity and non-lossy sequence representation;
- quantifier witness terms, rigid-parameter freshness, bounded-quantifier side
  conditions, and gamma repeatability where present;
- substitution-code and `subst-prf/4` vocabulary for Level-1 fixed-point
  self-reference;
- profile-level arithmetic, syntax-code, substitution-code, axiom-membership,
  `tableau-proof/3`, and `subst-prf/4` behavior when included;
- proof-certificate constructor grammar.

### 7. Proof-Size Lower Bound

The proof must include an anti-compression lemma. The required result is a
lower bound, not byte-for-byte equality: Proflog must not accept a proof object
that is more compact in the relevant SJAS proof-size measure than the
corresponding semantic-tableau proof is allowed to be.

For every accepted covered certificate, the proof must define a size measure
for the Proflog certificate plus any reconstructed formula-bearing state, and a
size measure for the SJAS tableau tree, then prove the Proflog side satisfies
Willard's Conventional Tableaux Encoding Requirement or an equivalent lower
bound.

Macro expansion is acceptable only when it preserves this lower-bound fact. A
single compact Proflog tag cannot stand for an arbitrarily large hidden proof
subtree unless either:

- that tag is admitted as a primitive rule of the selected SJAS deductive
  apparatus with its own size accounting;
- the certificate carries enough payload to account for the expanded subtree;
- or the expansion is proved to add only irrelevant/tip-local work that cannot
  reduce the relevant tableau branching and proof-size measure.

Thus generic equality, procedure calls, and profile rules may be admitted by
macro expansion only after proving they do not allow a shorter proof of the
self-referential theorem than the SJAS tableau apparatus permits.

### 8. Irrelevance Lemmas

Every implementation detail ignored by the theorem must have an irrelevance
lemma. The current probable irrelevancies are:

- rule-search order and agenda selection;
- caching or tabling;
- host data structures used by the runtime;
- finite `fuel`, provided the mathematical relation is stated as unbounded or
  existentially sufficient-fuel;
- exact compact byte layout, provided the selected encoding is injective and
  satisfies the lower bound;
- outer profile wrappers such as `(profiled willard-sjas-tableau0 p)`, provided
  wrapper erasure and encoded profile selection are proved.

These are not free assumptions. Each one must be shown not to alter accepted
proof objects or relevant size/tree measures.

### 9. Operational Tests

Operational tests are required in addition to the proof. They must not be
treated as a proof substitute.

The test suite for a completed Track 2b must include representative positive
and negative cases for:

- ordinary Tableau-0 beta proofs;
- non-generated theorem-code decoding;
- Group-3/self-consistency theorem paths;
- Level-1 `subst-prf/4` identity and fixed-point paths;
- wrong theorem code, wrong system code, wrong substitution code, and wrong
  proof certificate rejection;
- every included equality, disequality, procedure-call, profile, and code-reader
  constructor family;
- unencodable and unclassified proof tags at the fragment boundary;
- proof-size lower-bound regressions and non-lossy byte encoding.

The tests must also confirm that every proof tag reachable in the covered
domain is either encoded and classified, macro-expanded, proved unreachable, or
excluded.

## Current Track 2b Status

Under these criteria, full Track 2b is not complete in the current
implementation.

The current code and Track 2a notes expose several hard blockers:

- the Proflog kernel has not yet been formalized in a common semantics
  compatible with the SJAS tableau-side specification;
- the SJAS tableau-side relation has not yet been supplied as a formal
  formula-bearing tree semantics independent of the current executable
  proof-directed checker;
- reachable compact code-reader tags `sjas-code-arg` and `sjas-code-args-end`
  are now encoded and classified, and `free-close` is now encodable, but
  `free-close` remains an unresolved equality/free-constructor closure rule
  until it is proved primitive, macro-expanded, proved unreachable in the
  covered fragment, or excluded;
- generic equality, guarded/procedure-call internals, and some profile sidecar
  constructors are not yet proved primitive, macro-expanded, unreachable, or
  excluded;
- `univ`, `once-univ`, and `witness` certificates are skeletal and do not by
  themselves carry all formula/witness information needed by the SJAS
  formula-bearing tableau tree;
- non-`sjas-axiom` `tableau-proof/3` and `subst-prf/4` paths now use a local
  proof-directed checker rather than `kernel/prove-programo`, but that checker
  is not yet a formal correspondence proof for the full SJAS tableau
  apparatus;
- beta validity and some axiom-basis boundaries remain stated as assumptions or
  partial classifications rather than discharged proof obligations.

Therefore, completing Track 2b now would require one of two substantial next
steps:

1. finish a common formal semantics and prove the biconditional over a sharply
   bounded fragment that excludes all unresolved/unencodable constructors; or
2. continue implementation and formalization until the full current SJAS
   certificate domain is encoded, classified, and covered by the proof.

The current state is suitable for beginning Track 2b, but not for truthfully
marking Track 2b complete.
