# ADR-0063: SJAS Arithmetized Coding

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0063-sjas-arithmetized-coding`
- AAR: [AAR-0063](../aar/AAR-0063-sjas-arithmetized-coding.md)

## Context

ADR-0060 through ADR-0062 built an executable Willard SJAS substrate:
finite `IS#_D(beta)`-style systems, binary U-grounding arithmetic, generated
Group-3 sentences, and a non-vacuous `tableau-proof/3` checker that validates a
concrete Proflog kernel certificate against a concrete theorem target.

That is still not enough to claim a faithful arithmetized self-justification
demonstration. The current generated formula codes are hash-derived
object-language constants, and `tableau-proof/3` finds theorem targets through a
generated `:sjas/proof-targets` table. Such codes are finite codebook labels.
They are not Willard-style Godel numbers of formulas and proofs, and the object
language cannot inspect their syntax.

The Willard corpus requires the proof predicate to be arithmetized. The 2001
paper defines Group-3 through a Delta-0 semantic-tableau proof predicate over
Godel numbers. The 2013/2014 `ISD(A)` and finite `IS#_D(beta)` versions use the
same arithmetized discipline for Level-1 consistency. The 2011 Appendix A
spells out the required components for `SelfCons_k(beta,d)`: `Neg_k`,
`Prf^d_beta`, `ExPrf^d_beta`, `Subst`, and `SubstPrf^d_beta`.

The coding method need not be unique. Willard 2011 Definition D.1 says a
semantic-tableau proof encoding may use essentially any natural method satisfying
the Conventional Tableaux Encoding Requirement: at least `5J` bits are required
for a tableau proof with `J` function symbols. Fixed-width hashes do not satisfy
that requirement. Willard 2001 Appendix B and Willard 2016 Section 6.1 describe
the compatible byte/base-64 style: formulas and proof trees are encoded as
integers whose base-64 expansions are byte strings.

The research record for this decision is
[SJAS Arithmetized Coding Research](../log/2026-05-14-sjas-arithmetized-coding-research.md).

## Decision

ADR-0063 replaces the promoted SJAS coding path with inspectable base-64
Godel-code terms:

- formula codes are compact byte-string terms `(code-N b0 ... bN-1)`, not
  hash-derived constants;
- system codes are compact byte-string terms that describe the reflected finite
  beta/reflected basis;
- complement codes are formula Godel-code terms for structural negations, not
  `not-code` wrappers over opaque labels;
- proof certificates are byte/base-64 Godel-code terms, not `proof-cons` term
  trees or opaque labels;
- `wff`, `delta-star-0-code`, `pi-star-1-code`, `sigma-star-1-code`, and
  `neg-pair` are derived by decoding generated formula-code entries keyed by
  those code terms, not by generated syntax facts;
- `tableau-proof(system-code, theorem-code, proof-code)` decodes the system,
  theorem, and proof certificate code terms and invokes the existing Proflog
  kernel over the reconstructed formula/proof data;
- generated hash labels may remain only as debug metadata, never as the formal
  code consumed by promoted SJAS predicates.

The implementation uses a concrete Willard-compatible byte coding rather than a
fixed-width digest. The host builder may translate source formulas into their
initial byte strings; after that boundary, the proof-profile predicates work
from the code terms supplied to the kernel. The public code term is deliberately
flat rather than one enormous binary `dbl/add` numeral: each byte is a small
binary numeral, and the whole code is represented by `code-N`. This preserves an
inspectable Godel-code surface while avoiding a proof-search stack overflow on
deep numeral spines.

## Consequences

- The old ADR-0062 result is reclassified as a finite reflected
  proof-substrate demonstration, not the final arithmetized SJAS claim.
- Querying syntax classes, complement pairs, and proof predicates now has an
  object-level representation discipline: finite codes are compact base-64 SJAS
  code terms.
- The implementation becomes more expensive operationally because proof search
  must decode syntax and proof bytes before checking a tableau certificate.
- The resulting self-consistency examples can fairly say that
  `tableau-proof/3` operates on inspectable sentence/proof Godel-code terms
  rather than on host-side theorem-target labels.

## Test Obligations

Tests must be red before implementation and then pass:

- every generated axiom code and system code is a compact base-64 SJAS
  Godel-code term;
- generated code symbols with prefixes such as `sjas_formula_` and
  `sjas_system_` disappear from the formal language constants;
- proof certificates returned by `proof-certificate` are compact base-64 SJAS
  Godel-code terms;
- `wff` and the formula-class predicates succeed for formula Godel-code terms
  without generated syntax fact atoms;
- `neg-pair` succeeds for code terms of a formula and its NNF complement;
- `tableau-proof/3` validates a proof certificate by decoding the theorem,
  system, and proof code terms, not by reading `:sjas/proof-targets`;
- malformed proof certificates and wrong theorem codes are rejected;
- an inconsistent finite beta control still validates a real
  contradiction certificate.

## Exit Criteria

- `lein test-proflog-sjas` passes and records runtime.
- `lein test-proflog-fast` passes and records runtime.
- `lein test-proflog-extended` is run before merge because this changes proof
  search, negation, and equality interaction.
- The worked SJAS example, runtime baseline, ADR index, AAR index, development
  log, and nachlass log describe the arithmetized coding boundary and the
  remaining limits.
