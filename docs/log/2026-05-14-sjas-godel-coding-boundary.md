# SJAS Godel-Coding Boundary

Date: 2026-05-14

## Exchange Summary

After ADR-0062 made `contradiction-code` and `not-code(c)` concrete
proof-target entries, the remaining question was whether Proflog could claim to
demonstrate Willard-style self-justification while its theorem codes were
hash-derived symbolic keys rather than arithmetic Godel codes.

The answer is no, not in the full Willard sense.

The current implementation demonstrates a finite SJAS-style reflected proof
substrate:

- the builder generates a finite axiom basis;
- Group-3 has the intended self-referential formula shape;
- `tableau-proof(system, theorem-code, proof-code)` no longer fails vacuously
  for `contradiction-code`;
- proof certificates are decoded and checked by the Proflog kernel.

But the generated formula codes are opaque host-side identifiers. The object
theory cannot inspect their syntax, decode them as sequences of symbols,
recognize formula constructors from their arithmetic representation, or prove
`wff`, `neg-pair`, formula-class, substitution, and proof-step facts by
arithmetized reasoning over the code itself.

## Correct Claim

The current SJAS profile should be described as a finite codebook/tableau
self-reference demonstrator, not as a full Willard SJAS.

The stronger target requires arithmetized syntax:

- formula and proof codes must be numeric terms in the SJAS language;
- formula constructors must be encoded by inspectable arithmetic/sequence
  structure;
- `wff`, `neg-pair`, formula-class classifiers, substitution, and
  `tableau-proof` must operate over those codes;
- host hashing may remain only as debugging/stable labeling, not as the formal
  code used by the proof predicate.

This boundary motivates the next ADR: a faithful `IS#_D(beta)` implementation
for standard first-order logic with equality tableau deduction, including the
arithmetized proof predicate and its supporting syntax/proof coding facilities.
