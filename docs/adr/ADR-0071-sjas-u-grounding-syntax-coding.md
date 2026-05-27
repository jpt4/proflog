# ADR-0071: SJAS U-Grounding Syntax Coding

- Status: completed
- Date: 2026-05-16
- Branch: `adr-0071-sjas-u-grounding-syntax-coding`
- AAR: [AAR-0071](../aar/AAR-0071-sjas-u-grounding-syntax-coding.md)

## Context

ADR-0063 through ADR-0070 promoted the SJAS implementation from hash labels to
inspectable byte/base-64 codes for formulas, systems, and proof certificates.
The selected representation is formally structured, but its public term shape
uses generated `code-N` constructors:

```clojure
(code-N b0 ... bN-1)
```

That compact representation solved an operational problem: large formula and
proof codes were no longer enormous binary numeral towers. It also preserved
trailing zero bytes once ADR-0070 made byte strings first-class.

The remaining semantic objection is sharper. Willard's SJAS tradeoff removes
multiplication as a total function from the base language while retaining a
multiplication relation over division. If Proflog's arithmetized syntax layer
uses a representation whose construction and inspection do not depend on the
U-Grounding arithmetic vocabulary, then the absence of total multiplication has
no force in the self-justification demonstration. The implementation would
still show an executable reflection profile, but not the distinctive SJAS
programming-language tradeoff.

The development note
[SJAS Multiplication Tradeoff Relevance](../log/2026-05-15-sjas-multiplication-tradeoff-relevance.md)
records this criterion.

## Decision

Add an opt-in `:u-grounding` code format for SJAS systems. In that format:

- formal formula, system, and proof codes are ordinary binary U-Grounding
  numeral terms over `0`, `1`, `dbl`, and `add`;
- the finite byte string is preserved by appending a non-zero sentinel byte
  before interpreting the sequence as a base-64 natural;
- a system compiled with `:code-format :u-grounding` excludes `code-N`
  constructors from its language signature;
- `wff`, formula-class predicates, `neg-pair`, `subst-code`, `tableau-proof`,
  and `subst-prf` must accept the U-Grounding numeral codes through the same
  kernel-interleaved SJAS profile predicates;
- decoding a U-Grounding code inside the proof profile reconstructs the byte
  sequence relationally with the equation `out = byte + 64 * tail`, so the code
  layer depends on relation-backed multiplication rather than a total `mul`
  function symbol.

The existing compact code format remains the default. It is still useful as a
performance profile and preserves all existing tests and documentation. The new
format is the semantically stronger demonstration path.

## Consequences

- SJAS examples can now distinguish two implementation levels:
  compact byte-string terms for practical proof checking, and pure U-Grounding
  numeral codes for the stronger arithmetic-language demonstration.
- U-Grounding-coded systems will be slower and will produce larger public
  terms. That is acceptable for semantic tests; speed is an optimization goal,
  not the correctness condition for this ADR.
- Diagonal substitution must use the actual source code term as the replacement
  when the source code is a U-Grounding numeral. Compact `code-N` sources keep
  the historical embedded-code replacement.

## Test Obligations

Tests must be red before implementation and then pass:

- a `:code-format :u-grounding` system has no `code-N` functions in its
  language and emits formula/system codes that are binary SJAS numeral terms,
  not compact `code-N` terms;
- the U-Grounding code encoding is injective for trailing-zero byte strings;
- `wff`, class predicates, and `neg-pair` succeed for U-Grounding formula
  numeral codes, including non-generated formulas;
- the proof evidence for a U-Grounding syntax predicate includes the
  relation-backed U-Grounding code decoder;
- a code that reaches a syntax predicate through a logic binding, rather than
  as a ground top-level argument, preserves byte-cons evidence for the
  `byte + 64 * tail` relation and the fixed-radix multiplication step;
- `subst-code` succeeds for the Level-1 self-consistency fixed point under
  U-Grounding codes and rejects the wrong substitution source;
- `tableau-proof/3` accepts an encoded proof certificate when system, theorem,
  and proof codes are all U-Grounding numerals.

## Exit Criteria

- Focused red-green evidence is recorded.
- `lein test-proflog-sjas` passes and records runtime.
- `lein test-proflog-sjas-slow` passes and records runtime unless the new tests
  are explicitly kept in the fast SJAS gate.
- `lein test-proflog-fast` and `lein test-proflog-extended` pass before merge
  because this touches proof search and query behavior.
- The AAR, runtime baseline, development log, and worked SJAS example document
  the new code format and remaining performance boundaries.
