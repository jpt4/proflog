# SJAS Track 2a Coverage Status

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Purpose

This note summarizes the current state of ADR-0073 Track 2a after the
single-threaded relevance-analysis slices on 2026-05-25 and 2026-05-26. It is
not a Track 2b correspondence proof. Its purpose is to state what has been
classified, what can likely be ignored under proof, and what remains open
before a proper proof-and-test correspondence can be attempted.

## Classified as Relevant

The following aspects are now clearly relevant to the SJAS self-justification
invariant:

- finite semantic-tableau tree shape;
- proper root, theorem target, and axiom/deduction ancestry;
- rule-induced child structure, including alpha/beta and quantifier rules;
- branch closure;
- formula, system, proof, substitution, and axiom-basis code inspectability;
- byte-string arity and non-lossy sequence representation;
- proof-size anti-compression under Willard's Conventional Tableaux Encoding
  Requirement;
- quantifier witness terms, parameter freshness, bounded-quantifier side
  conditions, and ordinary gamma repeatability;
- substitution-code and `subst-prf` vocabulary for Level-1 fixed-point
  self-reference;
- profile-level arithmetic, syntax-code, substitution-code, axiom-membership,
  proof-check, and subst-proof-check rules;
- proof-certificate constructor grammar, because missing or macro-compressed
  constructors can change proof objects.

## Probably Irrelevant Under Proof

The following aspects are not expected to be relevant if the stated side
conditions are proven:

- exact Willard 2001 curly-brace byte layout, provided Proflog's encoding is an
  injective natural tableau encoding with the required lower bound;
- rule-search order, agenda selection, caching, host data structures, and
  runtime evaluator choice, provided they do not alter accepted proof codes or
  proof-size accounting;
- outer `(profiled willard-sjas-tableau0 ...)` and
  `(profiled willard-sjas-level1 ...)` wrappers, provided profile selection is
  fixed by encoded system-code and wrapper erasure preserves proof structure;
- finite `fuel` as a runtime search bound, provided Track 2b states the
  mathematical predicate as unbounded or existentially sufficient-fuel.

## Macro Expansion Preference

The preferred treatment for equality, procedure calls, profile theory rules,
and other compact constructors is bounded macro expansion rather than fragment
exclusion when expansion is viable.

The critical condition is lower-bound preservation: Proflog must not accept a
more compressed proof object than the SJAS semantic-tableau predicate would
allow. Macro expansions that increase tree size, add branch-tip closure work,
or reconstruct ordinary tableau nodes are acceptable if they preserve the
relevant root/branch/rule/closure facts and satisfy the anti-compression
measure.

Fragment exclusion remains a valid fallback only for constructors whose
primitive status or bounded expansion cannot be proved.

## Evidence-Backed High-Risk Areas

Several previously speculative risks now have concrete evidence:

- `neg-call` is reachable in a reflected-clause SJAS certificate, so procedure
  calls must be primitive, macro-expanded over reflected Group-2b axioms, or
  excluded from the covered fragment.
- `refl-close` is reachable in a non-arithmetic equality SJAS theorem proof.
- `free-close` is reachable in a constructor-clash proof term. It is now in
  the SJAS proof-code alphabet and classified as unresolved, so Track 2b still
  must prove it primitive, macro-expandable, unreachable in the covered domain,
  or excluded.
- `sjas-code-arg` and `sjas-code-args-end` are emitted by current compact
  code-reader proof paths. They are now in `proof-symbols`, and byte payloads
  inside proof evidence are encoded by an explicit proof-byte tag. The observed
  U-Grounding `sjas-ug-code-canonical-byte` tag is now encoded and classified
  as relevant; lower-level bit-reader/helper tags still need separate
  reachability and classification.
- Representative `wff`, `delta-star-0-code`, and `neg-pair` syntax predicate
  proofs now have encoded/classified `willard-sjas-code`, syntax relation, and
  `sjas-neg-pair-structural` evidence. Other profile helper tags such as
  canonical numeral-reading evidence still need reachability and
  classification.
- `univ`, `once-univ`, and `witness` proof nodes are skeletal: their
  instantiation terms and parameters live in branch state rather than in the
  proof-code payload.

These are not necessarily soundness bugs. They are correspondence obligations.

## Remaining Track 2a Gaps

Track 2a is substantially sharper, but not exhausted. The main remaining
classification gaps are:

1. Full reachability coverage for generic equality internals, guarded-call
   internals, and profile sidecar proof tags.
2. A precise exclusion or expansion proof plan for generic
   `(profiled propositional ...)` and `(profiled first-order ...)` sidecars.
3. A more detailed Level-1 `subst-prf/4` relevance note, separating
   `subst-code/2`, `subst-prf/4`, theorem-code decoding, and kernel proof
   validation.
4. Axiom-basis boundary classification for Group-0, Group-1, Group-2 beta,
   reflected Group-2b clauses, and Group-3, including which parts are already
   object-code driven and which remain staged.
5. Beta truth and formula-class validation boundary: Track 2b can proceed
   under a valid-beta precondition, but full self-justification claims need the
   precondition discharged or explicitly stated.
6. Formal proof medium selection for Track 2b: direct structural semantics,
   common intermediate semantics, or third-party formalization.

## Current Handoff to Track 2b

The current Track 2b proof obligations should be stated as:

```text
For every covered system code S, theorem code F, and proof certificate P:
  ProflogAccepts(P, S, F)
  iff
  SJAS_TableauProof(translate(P,S,F), S, F)
```

with additional preservation obligations:

- tree shape, root, child, ancestor, leaf, closure, and deduction facts;
- formula/system/proof/substitution code inspectability;
- proof-size lower bound and non-compression;
- macro expansion for equality, procedure calls, profile rules, and
  quantifier witnesses where used;
- explicit fragment exclusions where expansion or primitive status is not
  proved.

Operational tests remain necessary but insufficient: they must accompany, not
replace, the formal correspondence proof.

## Track 2a Status

Track 2a has moved from a broad suspicion to a concrete relevance map. The
remaining work is no longer "what could matter?" but "which uncovered
constructors and boundary assumptions are in or out of the first correspondence
fragment?" That is a narrower and actionable foundation for both Track 1
arithmeticization and Track 2b proof-and-test work.
