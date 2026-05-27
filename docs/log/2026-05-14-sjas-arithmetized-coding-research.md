# SJAS Arithmetized Coding Research

Date: 2026-05-14

This note records the first research pass for a faithful Proflog implementation
of Willard's finite `IS#_D(beta)` profile with an arithmetized first-order
semantic-tableau proof predicate.

Primary sources checked:

- Dan E. Willard, `Self-Verifying Axiom Systems, the Incompleteness Theorem and
  Related Reflection Principles`, JSL 2001; local witness
  `sjas/nachlass/papers/willard2001_self_verifying_axiom_systems_author_jsl1.pdf`.
- Dan E. Willard, `About the characterization of a fine line that separates
  generalizations and boundary-case exceptions for the Second Incompleteness
  Theorem under semantic tableau deduction`, JSL 2002; local witness
  `sjas/nachlass/papers/willard2002_semantic_tableaux_robinson_q_author_jsl2.pdf`.
- Dan E. Willard, `A Detailed Examination of Methods for Unifying, Simplifying
  and Extending Several Results About Self-Justifying Logics`, arXiv 1108.6330;
  local witness `sjas/nachlass/papers/willard2011_self_justifying_logics_arxiv_1108.6330.pdf`
  and public source `https://arxiv.org/abs/1108.6330`.
- Dan E. Willard, `On the Significance of Self-Justifying Axiom Systems from
  the Perspective of Analytic Tableaux`, arXiv 1307.0150; local witness
  `sjas/nachlass/papers/willard2013_significance_self_justifying_axiom_systems_arxiv_1307.0150.pdf`
  and public source `https://arxiv.org/abs/1307.0150`.
- Dan E. Willard, `On How the Introducing of a New theta Function Symbol Into
  Arithmetic's Formalism...`, arXiv 1612.08071; local witness
  `sjas/nachlass/papers/willard2016_theta_function_symbol_arxiv_1612.08071.pdf`.

## Required Self-Consistency Shape

The 2001 paper's Group-3 for `IS(A)` uses an arithmetized semantic-tableau proof
predicate: `SemPrf_alpha(x,y)` is a Delta-0 formula over Godel numbers, and the
Group-3 axiom says there is no semantic-tableau proof of `0 = 1` from `IS(A)`
itself.

The 2013/2014 paper's `ISD(A)` and finite `IS#_D(beta)` versions use the same
kind of arithmetized predicate but shift to Level-1 consistency for deduction
method `D`: no proof of a Pi-star-1 sentence and its coded negation. Definition
5.1 explicitly changes the finite system's Group-2 component from an infinite
schema to the finite beta set; the self-referential "I am" fragment changes with
that beta basis.

The 2011 paper's Appendix A generalizes the fixed-point machinery as
`SelfCons_k(beta,d)`. Its required pieces are:

- `Neg_k(x,y)`: `x` codes a formula in the relevant class and `y` codes its
  logical negation;
- `Prf^d_beta(t,p)`: `p` codes a proof of theorem code `t` from beta using
  deduction method `d`;
- `ExPrf^d_beta(h,t,p)`: proof from beta plus the added sentence coded by `h`;
- `Subst(g,h)`: Godel substitution, replacing free variables in a formula code
  with the numeral for `g`;
- `SubstPrf^d_beta(g,t,p)`: the hybrid relation used to make the fixed point
  self-referential.

For `k = 1`, the resulting sentence is a Pi-star-1 formula of the shape:

```text
forall x y p q.
  not (Neg_1(x,y) and SubstPrf_beta^d(n, x, p)
       and SubstPrf_beta^d(n, y, q))
```

where `n` is the Godel number of the sentence skeleton itself.

## Tableaux Encoding Citation

The remembered citation appears in Willard 2011, Definition D.1, part iv. The
relevant excerpt is:

> "any natural method that satisfies the minor stipulation that at least 5 J bits are required"

The sentence continues by applying that lower bound to a semantic-tableau proof
with `J` function symbols. Footnote 23 sharpens the same requirement: the Godel
number of such a proof must be at least `32^J`, because a proof with `J`
function symbols has at least `2J` logical symbols and therefore at least `5J`
bits under conventional encodings. Willard calls this the Conventional Tableaux
Encoding Requirement and says usual semantic-tableau Godel codings satisfy it.

This is exactly the anti-hash argument. A fixed-width hash can eventually encode
arbitrarily large proof trees in fewer than `5J` bits. Hashing is therefore not
a faithful Godel-coding method for the arithmetized proof predicate.

## Candidate Encoding Options

### Option A: Willard Byte/Base-64 Encoding

Willard 2001 Appendix B and Willard 2016 Section 6.1 describe a byte-style
encoding. The stable core is:

- use 6-bit bytes and base-64 numerals;
- assign unique byte tags to logical symbols;
- encode constants, variables, parameters, and function symbols with a tag byte
  followed by base-32 digits for the index;
- encode formulas as byte strings;
- encode tableau proof trees by bracketing nodes, so a proof is an integer whose
  base-64 expansion is a tree-shaped byte string.

Willard 2016 states the compact version as:

> "each proof as an integer, written in base 64"

This is the best compatibility target. It is efficient enough for tests,
structurally inspectable, and satisfies the Conventional Tableaux Encoding
Requirement.

### Option B: Linear Compressed Sequence Coding

Willard 2002 defines a Delta-0 sequence encoding predicate and calls linear
compressed encodings the most efficient formal Godel coding of a sentence or
proof. The construction is backed by sequence-coding work attributed there to
Buss, Hajek, Paris, Pudlak, and Wilkie.

This is theoretically strong but more abstract for implementation. It is a good
reference for proving that byte extraction and sequence lookup can be Delta-0,
but the Proflog implementation still needs a concrete byte/string encoding.

### Option C: Pair/List S-Expression Coding

A simple recursive pair/list Godel code would be easy to specify and would not
be too efficient. It is less faithful to Willard's chosen compressed
presentation, and it would require adding pair/unpair relations or simulating
them with existing arithmetic. This may be useful as an implementation
fallback, but it should not be the default if byte/base-64 decoding is feasible.

### Option D: Host Hashes Or Opaque Symbols

This is the current ADR-0062 substrate. It is not acceptable for the faithful
goal because the object theory cannot inspect the syntax of a hashed code. It
can remain as a host-side debug label only.

## Recommended Proflog Target

Implement Option A first:

- add an SJAS byte-code namespace that assigns byte tags to the current FOL
  equality syntax and proof constructors;
- encode formulas, finite beta axioms, fixed-point skeletons, and proof objects
  as compact base-64 SJAS code terms whose bytes denote Godel numbers;
- expose arithmetic byte access relations needed by `wff`, `neg-pair`,
  formula-class predicates, substitution, and `tableau-proof`;
- keep generated hash names only for debugging;
- retain finite beta construction at the host boundary, but require the
  promoted proof predicate to consume inspectable Godel-code terms rather than
  opaque labels.

This approach is closest to Willard's sources, satisfies the lower-bound
constraint, and gives Proflog a concrete implementation path without inventing a
new nonstandard coding discipline.
