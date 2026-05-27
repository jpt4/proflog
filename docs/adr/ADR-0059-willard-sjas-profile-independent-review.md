# ADR-0059: Willard SJAS Profile — Independent Design Review Record

- Status: completed
- Date: 2026-05-10
- Branch: `review/sjas-lang-profile-design`
- AAR: n/a (documentation-only ADR; no implementation artifact on this branch)

## Context

Dan Willard’s self-justifying axiom system (SJAS) work is archived locally under `sjas/nachlass/`, including bibliographies, PDF witnesses, and secondary citations. A parallel contributor opened [ADR-0058](ADR-0058-willard-sjas-language-profile.md) (`adr-0058-sjas-profile-design`) with a concrete target profile and architecture sketch.

This ADR records an **independent** reading of the same corpus posture and Proflog integration constraints, produced on **`review/sjas-lang-profile-design`** so it can ship without modifying ADR-0058’s branch-owned text. The intent is **reconciliation material**: future implementation should satisfy the **union** of explicit obligations in ADR-0058 and the commitments recorded here; where they agree, that strengthens confidence; where they diverge, both branches must be compared before coding.

Proflog already routes proofs through language-selected profiles (`:proof-profile` → `prove-program*`) and supports kernel-interleaved theory rules via `kernel/*theory-profile-closeo*` (Robinson-Q family, ADR-0048–0052).

## Decision

1. **Acknowledge ADR-0058 as the primary numbered design ADR** for `:willard-sjas-level1`, including its named namespaces, proof-term shape, and test obligations list.

2. **Record this agent-led review as binding documentation on `review/sjas-lang-profile-design` only**, backed by the synthesis note [Willard SJAS — Independent Agent Review Synthesis](../log/2026-05-10-willard-sjas-agent-review-synthesis.md).

3. **Endorse the same first implementation target**: Willard’s **Type-A** arithmetic (total addition; multiplication **only** as a three-place relation with \(\Delta^\*_0\) graph over U-grounding terms), **semantic tableau** apparatus \(D\), **Level-1** consistency formulation (\(\Pi^\*_1\) / complement pathology excluded under \(D\)), encoded via explicit formula and proof **coding** inside the object language.

4. **Defer** the Hilbert-style **\(\theta\)-function** line as an initial profile: weaker alignment with the tableau kernel and heavier metamathematical baggage.

5. **Treat Tab-1 / proof-list apparatus as conditional**: mandatory before marketing the profile as a faithful **ISD(A)** / **IS\#\_D(\(\beta\))** implementation; optional for an honestly labeled **tableau SJAS substrate**.

6. **Preserve strict relational purity on the proof route** after AST construction: no host-language proof checking, stratification oracles, or arithmetic truth engines where ADR-0057-style auditing would classify them as shortcuts.

## Consequences

- Maintainers gain **two authored traces** of the design intent (human-led ADR-0058 versus this review branch). Merge negotiations should **fold duplicates** into a single implementation ADR rather than carrying both indefinitely.
- The nachlass alone does not specify finite coding conventions; those remain **implementation choices** but must be **stable** once chosen, because fixed-point sentences quantify over codes.
- Certificate-checking relations are likely **performance hotspots**; automated timings belong chiefly in the extended suite per project rules.

## Test Obligations

No new tests are added on this documentation-only branch. Implementation should begin under a future ADR using ADR-0058’s failing-test checklist as authoritative baseline; this record adds **no relaxation** of those obligations.

Supplementary review checks worth carrying forward (already aligned with ADR-0058 in substance):

- Source audits rejecting host-side proof validators on the promoted profile path.
- Classifier tests that fail when bounded structure is lost during surface lowering.
- Demonstrator proves **both** an ordinary \(\beta\) consequence **and** the intended Level-1 self-consistency sentence under declared \(D\).

## Exit Criteria

- This ADR and the linked synthesis note exist on `review/sjas-lang-profile-design`.
- `LOG.md` references both ADR-0059 and the synthesis note.
- `docs/adr/README.md` indexes ADR-0059.

All criteria are met when this branch records the corresponding commit.
