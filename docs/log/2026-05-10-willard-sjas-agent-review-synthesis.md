# Willard SJAS — Independent Agent Review Synthesis

Date: 2026-05-10  
Branch: `review/sjas-lang-profile-design`  
Authoring context: complementary to [ADR-0058](../adr/ADR-0058-willard-sjas-language-profile.md) and [Willard SJAS Profile Design Notes](2026-05-10-willard-sjas-profile-design.md); this note records a separate pass over `sjas/nachlass/` and Proflog integration points.

## Corpus posture (`sjas/nachlass`)

The nachlass is primarily **evidence and bibliography**, not executable specification:

- `paperlist` indexes Willard’s publications with stable bracket keys and public URLs.
- `papers/README.md` maps keys to **local PDF witnesses** (when present); checksums live in `papers/SHA256SUMS`.
- `works-citing-dew/` holds **secondary** literature on SJAS / incompleteness-boundary logic, with inclusion criteria documented there.
- `LOG.md` in the nested SJAS tree records archival maintenance (aggregation, hashing, `.gitattributes`), not mathematical content.

Implementation detail therefore flows from **reading those PDFs** (or trusted summaries) plus architectural mapping—not from parsing the nachlass directory alone.

## Mathematical objects to preserve in software

Willard’s SJAS constructions are **pairs**:

1. **Axiom basis** (often \(\alpha\) or a finite \(\beta\) in localized statements).
2. **Deduction apparatus** \(D\) (semantic tableaux in the Type-A line; later variants add controlled reuse / “Tab-1” proof lists).

“Self-justifying” means the system **asserts** (typically via a fixed-point sentence) a **weak consistency** claim about **that same pair** \((\alpha, D)\), under conditions where the **external** metatheorem establishes genuine consistency.

### Type-A arithmetic language (first Proflog target)

- **Addition** is available as a **total** function (in the sense of the official function symbols of the revised language).
- **Multiplication is not** a total unary/binary function symbol. It appears as a **three-place relation** \(\mathrm{Mult}(x,y,z)\), whose graph is \(\Delta^\*_0\) over the **U-grounding** vocabulary.
- The U-grounding layer supplies low-growth operations (predecessor, subtraction, division, max, logarithmic and counting primitives in Willard’s presentations—exact inventory must match the chosen paper witness when coding).

This split is not cosmetic: it is the boundary that keeps the semantic-tableaux consistency reflection **strong enough to state** but **weak enough to survive** the usual second-incompleteness obstacles in this line of work.

### Formula stratification

The reflection sentences reference classes such as \(\Delta^\*_0\), \(\Pi^\*_1\), \(\Sigma^\*_1\) **relative to that arithmetic language and bounded quantification**. A Proflog profile needs a **Willard-aware classifier**, distinct from mere theorem-prover routing (`formula-profile`): bounded quantifiers must be tracked structurally so desugaring to plain \(\forall/\exists\) does not accidentally widen a formula’s class.

**Level-1 consistency** (first target): simultaneous proofs of a \(\Pi^\*_1\) sentence and its negation are excluded—under the **specified** apparatus \(D\).

### Deduction apparatus

The **Fitting/Smullyan semantic tableau** treatment aligns with Proflog’s existing kernel (goal negation, \(\alpha/\beta/\gamma/\delta\) expansion, branch closure).

Willard’s fuller **ISD(A)** / **IS\#\_D(\(\beta\))** statements often assume **additional controlled reuse** of intermediate results (proof lists, Tab-1). Proflog does not currently ship that layer. Honest labeling:

- **Without** Tab-1-style checking: implement a **tableau SJAS substrate** plus object-language coding of proofs; do not claim full ISD(A).
- **With** Tab-1: enforce intermediate theorem class restrictions in the proof-list verifier.

### Logical infrastructure

Recent Willard writing (e.g. the 2020 boundary-case paper in the bibliography) emphasizes how **Law of Excluded Middle** is packaged—derived in a tableau setting versus elevated as a logical axiom schema—affects self-referential consistency claims. Proflog’s tableau-first kernel is therefore a better host than a Hilbert-style axiom engine for this profile family.

The **\(\theta\)-function / Hilbert** line (2016 witness) should remain **out of scope** for the first profile: different logical packaging and extra metamathematical commitments, weaker alignment with the current codebase.

## Proflog mapping (greenfield)

### Proof profile is the spine

`:proof-profile` on the language selects `prove-program*` dispatch (`proflog.proof-profile`). The SJAS target should add:

- `(defmethod prove-program* :willard-sjas-level1 …)`  
  (profile name chosen to match [ADR-0058](../adr/ADR-0058-willard-sjas-language-profile.md)—renaming would require coordinated ADR amendment).

### Kernel interleaving, not host checking

Following ADR-0048–0052 and Robinson-Q (`kernel/*theory-profile-closeo*`):

- SJAS theory steps (normalization, arithmetic graph reasoning, proof-certificate validation) must be **miniKanren relations** over branch state where they participate in proof search.
- **Forbidden** on the proof-critical path: Clojure-side “oracle” checkers that decide proof validity, \(\Delta^\*_0\) membership, or bounded truth.

### Explicit translation boundary

Acceptable **before** kernel proof obligations:

- Parse SJAS-oriented surface syntax.
- Build finite axiom enumerations and apparatus identifiers.
- Assign **stable codes** for formulas and proofs.
- Construct fixed-point / self-referential AST skeletons.

Everything that answers “is this a legal proof object under \(D\) for this coded system?” must live in **relational** machinery consumed by the kernel route.

### Namespace sketch

| Namespace | Role |
|-----------|------|
| `proflog.willard-sjas` | Public builders: signatures, term constructors, bounded quantifier forms, classifiers, `SelfCons1` template, finite-system constructors. |
| `proflog.kernel.willard-sjas-profile` | `willard-sjas-theory-closeo`, relational arithmetic normalization, tableau certificate checker, optional Tab-1 list checker. |

Proof evidence should **name the profile**, e.g. `(profiled willard-sjas-level1 …)`, parallel to existing profiled-kernel discipline.

## Implementation slices (ordering)

1. **Language metadata + builders + classifier tests** — notably **absence of `mul/2`**, presence of `mult/3`, positive/negative stratification examples.
2. **Relational U-grounding arithmetic** — forward, answer, and partial-synthesis modes; \(\mathrm{Mult}\) facts via the \(\Delta^\*_0\) graph definition.
3. **Miniature proof-certificate relations** — accept valid tiny tableaux; reject open or malformed certificates.
4. **Tab-1** (conditional on claims).
5. **Finite SJAS demonstrator** — small \(\beta\), proves an ordinary consequence and the generated Level-1 self-consistency sentence; **extended** suite houses bounded contradiction probes with timings.

## Documentation ethics

- Passing finite **contradiction probes** supports trust in the **implementation**, not a substitute for Willard’s consistency-preservation proof.
- Adding `SelfCons1` as an axiom demonstrates **intended fixed-point content**, not automatic verification of every external Part-ii condition.
- Performance: coding and certificate checking will be expensive; keep heavy probes in `lein test-proflog-extended` per project convention.

## Relation to sibling records

[ADR-0058](../adr/ADR-0058-willard-sjas-language-profile.md) remains the canonical numbered decision record opened from the parallel human-led design branch. [ADR-0059](../adr/ADR-0059-willard-sjas-profile-independent-review.md) on `review/sjas-lang-profile-design` captures this independent synthesis for merge-time comparison without rewriting the sibling ADR.
