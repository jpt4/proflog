# SJAS Proof-Tag Inventory

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note compares the current SJAS certificate alphabet in
`proflog.willard-sjas-code/proof-symbols` with proof tags emitted by the kernel,
the equality helper, and the SJAS profile. It follows
[SJAS Equality Constructor Reachability](2026-05-26-sjas-equality-reachability.md),
which found that `free-close` is reachable in an SJAS theorem proof term but is
not encodable as an SJAS proof certificate.

2026-05-27 update: `free-close`, `sjas-code-arg`,
`sjas-code-args-end`, and `sjas-ug-code-canonical-byte` are now in the SJAS
proof-symbol alphabet, and the proof-code layout has an explicit byte-payload
tag for code-reader byte arguments. See
[SJAS Proof-Code Byte Payloads](2026-05-27-sjas-proof-code-byte-payloads.md).
The same day's syntax-evidence slice added `willard-sjas-code`, the syntax
predicate relation tags, and `sjas-neg-pair-structural`; see
[SJAS Syntax Proof Evidence Alphabet](2026-05-27-sjas-syntax-proof-evidence-alphabet.md).
This note remains useful as an inventory of the broader unresolved helper-tag
surface.

The main result is stronger than the single `free-close` case: the current
declared certificate alphabet does not cover all proof evidence emitted on
current SJAS paths. Some missing tags are generic equality internals; others are
SJAS code-reader evidence that current tests already require to appear in proof
terms.

## Inventory Method

The inventory used a conservative source scan over proof-producing code:

- `src/proflog/kernel.clj`
- `src/proflog/equality.clj`
- `src/proflog/kernel/propositional.clj`
- `src/proflog/kernel/first_order.clj`
- `src/proflog/kernel/willard_sjas_profile.clj`

The scan extracted tags from common proof-term constructions such as
`(== '(tag) proof)` and `(== (list 'tag ...) proof)`, then compared them with
`proflog.willard-sjas-code/proof-symbols`.

The method intentionally over-approximates. It reports some formula/data tags
that occur near proof construction but are not themselves proof constructors.
The important output is therefore the proof-like missing set, not the raw
candidate set.

## Missing Proof-Like Tags

The following proof-like tags are emitted by current proof-producing code but
are not in `proof-symbols`:

| Area | Missing tags | Why relevant |
|---|---|---|
| Equality contradiction and unification internals | `occurs-close`, `decompose`, `args`, `eq-refl`, `eq-bind`, `par-bind`, `atom-close`; `free-close` is now encoded but unresolved | These can occur below `eq-step`, `neq-close`, or complementary literal closure. `free-close` is already reachable in an SJAS theorem proof term and still needs a primitive/macro/exclusion proof. |
| Guarded/multi-alternative procedure-call internals | `alt`, `guard-eq`, `guard-saturation-done`, `guarded-scope-done`, `guarded-seq-done`, `guarded-call-seq-done` | Guarded call proof terms can use these below already-declared guarded-call constructors. If guarded calls are admitted, these payload tags must be encoded or macro-erased. |
| SJAS compact code-reader evidence | none currently missing for the observed `sjas-code-arg`/`sjas-code-args-end` path | Current tests assert `sjas-code-arg` appears in successful compact code-reader proofs; that path is now encodable with explicit byte payloads. |
| SJAS U-Grounding bit-reader evidence | `sjas-ug-code-bit-zero`, `sjas-ug-code-bit-one`, `sjas-ug-code-bit-dbl`, `sjas-ug-code-bit-add-one`; `sjas-ug-code-canonical-byte` is now encoded and relevant | These are profile proof tags for U-Grounding byte/numeral reading. The observed canonical-byte proof evidence is now covered; lower-level bit peels still need reachability evidence before being admitted to the certificate alphabet. |
| SJAS structural syntax evidence | `sjas-read-canonical-num`; `sjas-neg-pair-structural` and syntax predicate relation tags are now encoded and relevant | These are profile proof tags for code/syntax relations and arithmetic numeral reading. The observed syntax predicate proof evidence is now covered; canonical numeral reading remains to be audited separately. |
| SJAS profile header evidence | `sjas-system-tableau0-profile`, `sjas-system-level1-profile` | These tags appear in system-code/profile decoding proof paths and are distinct from the public wrapper symbols `willard-sjas-tableau0` and `willard-sjas-level1`. |

The scan also produced obvious non-proof false positives such as `and`, `or`,
`forall`, `pos`, `neg`, `app`, `var`, and `num`; these are formula or term
constructors, not proof constructors.

## Executable Audit Update

`proflog.sjas-correspondence/audit-proof-term` now reports
`:unencodable-symbols` separately from `:unclassified-symbols`.

The red test first showed that a proof term containing

```clojure
(conj
  (sjas-code-arg 1 sjas-code-args-end)
  (free-close))
```

had no `:unencodable-symbols` field even though all three non-`conj` symbols
are outside `proof-symbols`. The green change computes `:unencodable-symbols`
from the declared SJAS proof alphabet. This did not solve the alphabet gap at
the time, but it made future reachability probes more precise. The 2026-05-27
byte-payload slice then closed this specific compact-code-reader gap.

## Track 2a Consequences

The certificate alphabet is itself a relevant intensional artifact. Exact byte
layout remains irrelevant under bounded translation, but the set of proof tags
that can be represented is not irrelevant: if the kernel can emit a proof term
that the SJAS proof-code grammar cannot encode, then the current implementation
does not yet define a correspondence over that proof term.

Track 2b must therefore avoid using `proof-symbols` as though it were already a
complete Proflog proof grammar. It needs one of the following:

1. Extend the certificate alphabet to cover every proof tag admitted in the
   correspondence fragment, then classify and prove each tag's rule or macro
   semantics.
2. Canonicalize proof terms before encoding so internal helper tags are erased
   or represented by already-declared constructors, with a proof that the
   canonicalization preserves relevant tableau structure and size.
3. Restrict the correspondence fragment so unencodable proof tags cannot occur,
   and test that the restriction holds.

Until one of these routes is complete for the remaining helper tags, the
current proof-directed checker is not correspondence-complete for all SJAS
theorem proofs. It remains useful operational evidence and a source of
reachability data for Track 2a.
