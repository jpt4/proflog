# SJAS Proof-Size Relevance

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note refines the classification of proof size, proof-code
layout, and compression for a future Proflog/SJAS correspondence theorem. It
answers one unresolved question from
[SJAS Tableau Relevance Deep Dive](2026-05-25-sjas-tableau-relevance-deep-dive.md):
whether the existing proof-symbol lower-bound sanity test is enough to support
Willard's conventional semantic-tableau proof-size requirement.

It is not enough. The current Proflog proof-code encoder is useful operational
evidence, but it encodes a proof skeleton/certificate rather than Willard's
formula-bearing semantic-tableau proof tree. Because proof size is relevant to
SJAS self-reference, Track 2b must either expand Proflog certificates into
formula-bearing tableau trees with preserved size accounting, or prove that the
selected skeletal encoding is an admissible non-compressing encoding for the
specific self-justification theorem being claimed.

## Source Boundary

The relevant Willard facts are source-grounded:

- In the 2011 appendix, Willard permits essentially any natural Godelized
  method for semantic-tableau proofs, provided it satisfies the Conventional
  Tableaux Encoding Requirement: a proof with `J` function-symbol occurrences
  must require at least `5J` bits, equivalently its Godel number is at least
  `32^J`. Local source: `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2020-2042 and 2069-2072.
- In the 2001 proof definition, a semantic-tableau proof is a candidate tree
  whose root is the prenex-normalized negation of the theorem, whose other
  nodes are axioms or deductions from ancestors, and whose root-to-leaf
  branches close by containing a sentence and its negation. Local source:
  `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 806-839.
- In the 2001 concrete encoding, semantic-tableau proofs are byte strings with
  tree structure; each left brace corresponds to a tableau node and the node's
  stored sentence appears immediately after that brace. Local source:
  `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 3066-3096.
- In the 2001 recognition machinery, `ExSemPrf` is supported by predicates
  such as `SentenceTree`, `Cardinality`, `IndexedSentence`, `Depth`,
  `Ancestor`, `Sibling`, `Leaf`, `ProperRoot`, `Closure`, and `Deduction`.
  Local source:
  `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 3326-3378.

The exact curly-brace byte notation is not the relevant invariant. The
relevant invariant is that the proof object is a natural, inspectable
semantic-tableau proof representation whose size grows with the represented
tree and formula content rather than collapsing that content into an opaque
short witness.

## Current Proflog Encoding Facts

`proflog.willard-sjas-code/proof-code-bytes` currently serializes Proflog
kernel proof data as a base-64 byte string:

- a proof symbol is encoded with `proof-symbol-tag` plus a one-byte symbol
  index, or `proof-wide-symbol-tag` plus a two-byte index;
- a non-empty sequential proof payload is encoded with `proof-list-tag`, a
  one-byte arity, and recursively encoded children;
- an empty proof payload is encoded with `proof-empty-list-tag`;
- public compact code terms preserve the byte-string arity through `code-N`
  constructors, and U-Grounding terms preserve trailing zero bytes through a
  sentinel byte.

This gives real byte growth for the encoded certificate syntax. It rules out
the narrowest kind of hash-like witness where an arbitrary proof term is
replaced by a fixed constant byte string.

The test `sjas-proof-codes-are-byte-strings-with-symbol-bit-lower-bound` checks
only that one representative encoded proof certificate preserves its byte
string and has enough base-64 bytes to cover five bits per encoded proof
symbol. That test is a useful sanity check, but it is not the Willard proof-size
lemma.

## Relevance Classification

Proof-size growth is relevant. More precisely, the relevant aspect is not the
host runtime's number of reduction/search steps and not the exact historical
byte layout, but the relationship between the encoded proof object and the
semantic-tableau tree it denotes.

The following are relevant:

1. The proof code must determine an inspectable finite tableau tree, either
   directly or through a formally specified expansion relation.
2. The represented tree must preserve root, node formula, child, ancestor,
   sibling, leaf, closure, and deduction facts needed by `SemPrf`.
3. The proof-code size must grow at least linearly in the relevant Willard
   measure, including formula/function-symbol occurrences in the represented
   proof tree.
4. Any macro constructor must have a bounded expansion whose encoded size
   cannot hide arbitrarily large proof subtrees or formula occurrences.

The following are irrelevant only under proof:

1. Whether Proflog uses the same curly-brace byte layout as Willard 2001.
2. Whether branch and ancestor relations are recovered from bracket ranges, a
   nested list grammar, or another injective tree grammar.
3. Whether the runtime validates the certificate by one agenda order or another.

The unresolved high-risk distinction is formula-bearing tree encoding versus
skeletal proof-certificate encoding. Willard's concrete `SemPrf` tree stores
the sentences at nodes. Current Proflog proof certificates mostly store proof
constructors and profile evidence, while theorem code, system code, focused
formula state, and clause bodies live outside the proof-code byte string and
are reconstructed by the kernel/profile evaluator. For example, an
`sjas-axiom` certificate can be very small while the cited axiom formula is
large and is supplied through the theorem/system code arguments.

That design may be justifiable, but not by theorem-level extensional agreement
alone. Since SJAS self-reference is sensitive to proof-predicate size behavior,
Track 2b must treat the skeletal certificate as one of the following:

1. A compact presentation of a larger formula-bearing tableau tree, together
   with a proved expansion relation and size lower bound for the expanded tree.
2. A formally selected alternative semantic-tableau proof encoding satisfying
   Willard's conventional lower bound for the measure used by the
   self-justification argument.
3. An implementation-stage shortcut that is operationally useful but not yet
   sufficient for the full SJAS self-justification claim.

The current evidence supports option 3. Options 1 or 2 remain open proof
obligations.

## Consequences for Track 2b

Track 2b should not state the correspondence as merely:

```text
Proflog accepts proof certificate P for system S and theorem F
iff
SJAS accepts proof code p for system code s and formula code f
```

unless `P` and `p` have compatible proof-size semantics. The proof must include
one of these stronger statements:

```text
expand(P, S, F) = T
and T is a formula-bearing SJAS tableau proof tree for F from S
and size(code(T)) >= 5J(T)
and size(P) is either irrelevant to the self-reference theorem or bounded
below by the same relevant measure up to an allowed constant/linear factor.
```

or:

```text
P itself is the selected SJAS proof object
and P's grammar is a natural semantic-tableau encoding
and P carries enough formula/tree information to satisfy the Conventional
Tableaux Encoding Requirement for the relevant J measure.
```

This creates concrete obligations:

- Define the `J` measure for the Proflog/SJAS correspondence: proof-symbol
  occurrences alone are too weak if the represented tableau node formulas can
  grow independently.
- Prove byte-count lower bounds across the full proof grammar, not just a
  representative proof term.
- Audit every constructor that can compress work: `sjas-axiom`, `profiled`
  proof-check wrappers, arithmetic/code/profile closures, procedure calls,
  equality-triggered calls, and generic propositional/first-order sidecars.
- For each macro constructor, prove bounded expansion into ordinary tableau
  nodes or exclude it from the SJAS proof predicate.
- Add negative tests showing that fixed-size or hash-like certificates cannot
  validate arbitrarily large proof trees merely because the theorem/system code
  arguments carry the missing formula content.

## Track 2a Classification Update

The refined classification is:

| Aspect | Classification | Reason |
|---|---|---|
| Exact historical curly-brace byte layout | Irrelevant under bounded/injective translation | Willard permits any natural semantic-tableau proof encoding satisfying the conventional size requirement. |
| Base-64 byte-string inspectability and arity preservation | Relevant | The proof predicate must inspect proof syntax as data; lossy natural normalization or opaque labels can erase proof structure. |
| Proof-symbol byte growth in `proof-code-bytes` | Relevant but insufficient | It prevents a trivial constant encoding of the certificate syntax, but does not prove size growth for formula-bearing tableau trees. |
| Formula-bearing tableau node content, or an equivalent expansion relation | Relevant | Willard's `SemPrf` machinery reasons over indexed sentences, roots, closures, and deductions inside a sentence tree. |
| Skeleton-only Proflog certificates | Unresolved/high risk | They may be acceptable as compact presentations only if a proof reconstructs formula-bearing trees and preserves the lower-bound measure. |
| Host runtime steps, agenda order, caching, and finite evaluator mechanics | Irrelevant under acceptance and size preservation | These do not belong to the proof object unless they affect which certificates validate or how proof size is measured. |

The practical conclusion is that the current lower-bound test should be kept,
but treated as an early guardrail rather than a correspondence proof. Track 2a
now classifies proof-size preservation as a stronger obligation: Proflog must
preserve not only the size of its encoded proof skeleton, but the relevant size
of the semantic-tableau proof object that SJAS self-reference quantifies over.
