# ADR-0070: SJAS Byte-Sequence Coding Audit

- Status: completed
- Date: 2026-05-15
- Branch: `adr-0070-sjas-tableau-proof-coding-audit`
- AAR: [AAR-0070](../aar/AAR-0070-sjas-byte-sequence-coding-audit.md)

## Context

ADR-0063 selected Willard-compatible byte/base-64 code terms for the finite
ordinary-tableau `IS#_D(beta)` SJAS profile. ADR-0067 through ADR-0069 then made
formula syntax, theorem targets, and diagonal substitution structural over those
codes.

The remaining audit question is sharper: Willard does not merely code formulae.
His self-consistency predicates quantify over coded proofs. In the 2001 JSL
paper, `Prf_alpha(x,y)` is a bounded formula saying that `y` is the Godel
number of a proof of the sentence whose Godel number is `x`; the semantic
tableaux specialization `SemPrf_alpha(x,y)` says that `y` is the Godel number
of a semantic-tableaux proof. Group-3 then asserts that no such proof exists for
`0=1`. Section 4 of the same paper defines the semantic-tableaux proof object
as a closed candidate tree rooted at the negated theorem. The 2009 paper's
`Prf`, `ExPrf`, `Subst`, and `SubstPrf` definitions reinforce that the
fixed-point construction combines formula-code substitution with proof-code
checking.

The Proflog implementation intentionally selects pure ordinary semantic
tableaux as `D`; Tab-1, Tab-k, and proof-list theorem reuse remain deferred. The
current proof code is the byte serialization of the Proflog kernel proof term
for that ordinary tableau kernel. This is the selected finite implementation of
`SemPrf` for Proflog's kernel, not an implementation of every historical
Willard proof-list variant.

One coding detail still needs a hard regression: byte strings must remain byte
strings. If a source-boundary encoder converts a byte string to a natural and
then back to bytes, trailing zero bytes are lost. That is harmless for many
generated examples, but it violates the conceptual sequence-coding discipline
needed for syntax/proof objects, especially when an embedded code term appears
at the end of an encoded formula.

## Decision

Keep the ADR-0063 coding method: compact base-64 byte-string terms
`(code-N b0 ... bN-1)`, with each byte written as a small binary SJAS numeral.
This method is still the cleanest fit for Willard's minimum-bits-per-symbol
requirement and for Proflog's relational decoder.

Tighten the implementation so canonical formula, system, and proof encoders
construct public code terms directly from their byte strings. Natural-number
views may remain as diagnostics, but they must not be the canonical path for
code terms that may contain trailing zero bytes.

## Consequences

- The public code term is injective as a byte-string representation because the
  constructor arity carries the byte count.
- Generated formula and proof codes no longer depend on lossy
  natural-to-byte normalization.
- `tableau-proof/3` continues to check decoded Proflog ordinary-tableau proof
  certificates through the kernel relation.
- Tab-1/Tab-k proof-list theorem reuse remains explicitly outside this ADR.

## Test Obligations

- A red test must show that an encoded formula whose final byte is zero still
  decodes through `wff/1`.
- Byte-level tests must show that public `code-N` terms preserve byte count and
  trailing zero payloads.
- Proof-code tests must show that proof certificates are byte strings, not
  hashes, and satisfy the selected lower-bound sanity check: at least five bits
  per encoded proof symbol.
- Existing `tableau-proof/3`, `subst-prf/4`, and general `subst-code/2`
  regressions must remain green.

## Exit Criteria

- Focused red-green evidence is recorded.
- `lein test-proflog-sjas-slow` passes and records runtime.
- `lein test-proflog-sjas` passes and records runtime.
- `lein test-proflog-fast` and `lein test-proflog-extended` pass before merge
  because this touches proof-code and formula-code construction.
- The development log and AAR record the proof-coding citations, selected
  ordinary-tableau scope, and remaining Tab-1/Tab-k boundary.
