# Willard Tableau Proof Encoding

Date: 2026-05-20

This note records the source answer to the question: what encoding does
Willard prescribe for semantic-tableaux proofs?

## Summary

Willard does not prescribe one unique canonical byte layout for tableau proofs.
The later statement is deliberately permissive: a Godelized method for
encoding semantic-tableaux proofs may be essentially any natural method that
satisfies the **Conventional Tableaux Encoding Requirement**.

That requirement is a size lower bound:

- if a semantic-tableaux proof has `J` function-symbol occurrences, its Godel
  number must require at least `5J` bits;
- equivalently, the Godel number must be at least `32^J`.

Willard says ordinary encodings satisfy this condition and points to Appendix A
of the earlier work as one possible method, while allowing other natural
mechanisms.

Local references:

- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2020-2042.
- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2069-2072.

## Concrete Encoding Example

The 2001 paper gives the concrete model Proflog has been using as its reference
point: a byte-string / base-64 encoding of formulas and proof trees.

The key ingredients are:

- a byte is six bits;
- atomic symbols receive distinct six-bit codes;
- constants, variables, parameters, and function symbols are encoded by marker
  bytes such as `C`, `V`, `U`, and `F`, followed by base-32 digits for the
  relevant index;
- a formula is encoded as the base-64 integer represented by its byte string;
- a semantic-tableaux proof is also encoded as the base-64 integer represented
  by its byte string;
- unlike Hilbert proofs, which are list-like, semantic-tableaux proofs are
  encoded as tree-shaped byte strings;
- each left curly brace corresponds to a tableau node;
- the sentence stored in a node appears immediately to the right of that
  node's left curly brace;
- descendant structure is recovered from bracket-range containment.

Local reference:

- `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 3066-3093.

The encoded object must represent an actual semantic-tableaux proof, not just
an arbitrary tree. The proof root is the negated theorem, normally in prenex*
normal form; other nodes are axioms or deductions from ancestor nodes; the
tableau rules govern propositional, negation, existential, bounded existential,
universal, and bounded universal expansions; and every root-to-leaf branch must
be closed by containing a formula and its negation.

Local references:

- `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 806-839.
- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2081-2118.

Appendix C then names the bounded-recognition machinery needed to inspect
these byte strings. The relevant predicates include `FormulaTree`,
`SentenceTree`, `IndexedSentence`, `Depth`, `Ancestor`, `Sibling`, `Leaf`,
`ProperRoot`, `Closure`, and `Deduction`; together these support the
`SemPrf` predicate that says a number encodes a semantic-tableaux proof of a
given sentence from a given axiom system.

Local reference:

- `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 3326-3376.

The 2016 paper repeats the same broad coding discipline for another profile:
semantic objects are encoded with a B-adic/base-64 byte-string method whose bit
length is approximately proportional to the written expression length.

Local reference:

- `target/sjas-pdf-text/willard2016_theta_function_symbol_arxiv_1612.08071.txt`,
  lines 1100-1136.

## Proflog Cross-Link

The code that currently performs Proflog's selected proof-code encoding is:

- `src/proflog/willard_sjas_code.clj`, especially `proof-code-bytes`,
  `proof-code-term`, and `proof-formal-code-term`.

That encoder serializes the current Proflog kernel proof-term language as
base-64 byte strings. It is intended to satisfy the Willard size discipline for
the selected ordinary-tableau kernel profile, but it is not a byte-for-byte copy
of Willard's 2001 curly-brace proof-tree notation and it is not an
implementation of every historical proof-list or Tab-k variant.

The checker that consumes these proof-code terms is:

- `src/proflog/kernel/willard_sjas_profile.clj`, especially
  `decode-proof-byteso`, `decode-proof-code-kindo`, and
  `sjas-tableau-proof-closeo`.

That checker decodes the supplied proof-code term and then validates the
decoded certificate through the Proflog kernel relation. Thus the present
implementation is a finite executable `SemPrf` substrate for Proflog's selected
ordinary-tableau proof terms, not a complete formalization of every byte-level
predicate listed in Willard 2001 Appendix C.

## Implication

When Proflog documentation says "Willard-compatible tableau proof encoding,"
the precise meaning should be:

> a base-64 byte-string Godel encoding of the selected ordinary-tableau proof
> certificates, designed to satisfy Willard's Conventional Tableaux Encoding
> Requirement.

It should not be read as:

> Willard mandated one exact byte layout, and Proflog implements that layout
> byte for byte.

The exact proof-code layout is an implementation choice. The mathematical
constraint is that it remain a natural, inspectable, tree/proof-code encoding
whose size is not so compressed that it defeats the U-height and small-proof
arguments used in the Type-A semantic-tableaux metatheory.
