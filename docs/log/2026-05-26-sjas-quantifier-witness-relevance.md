# SJAS Quantifier and Witness Relevance

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note classifies quantifier instantiation and witness policy for
the ADR-0073 correspondence program. It refines the unresolved item from
[SJAS Tableau Relevance Deep Dive](2026-05-25-sjas-tableau-relevance-deep-dive.md):
whether Proflog's `univ`, `once-univ`, and `witness` constructors preserve the
semantic-tableau apparatus relevant to SJAS self-reference.

The classification is:

- quantifier rule shape and instantiated/witness terms are relevant;
- the exact runtime search order for trying admissible instantiations is
  irrelevant only if the accepted proof relation and lower-bound measure are
  preserved;
- Proflog's current proof terms are skeletal because `univ`, `once-univ`, and
  `witness` do not themselves carry the instantiated term or parameter object;
- Track 2b must therefore reconstruct those formula-bearing quantifier steps
  from branch state, or extend/canonicalize the certificate grammar so the
  witness terms are explicit.

## Source Boundary

Willard's candidate-tree definition explicitly constrains quantifier steps:

- `exists v. Upsilon(v)` deducts `Upsilon(u)` where `u` is a newly introduced
  parameter symbol;
- bounded existential deduction introduces a new parameter `u` and the
  conjunct `u <= s and Upsilon(u)`;
- `forall v. Upsilon(v)` deducts `Upsilon(t)` for a parameter term `t`;
- bounded universal deduction uses `t <= s -> Upsilon(t)`;
- parameter terms are built from constants, previously introduced parameters,
  and function symbols applied to parameter terms.

Local source:
`target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
lines 806-839.

These rules make witness objects part of the tableau proof, not just part of
the prover's search strategy. The chosen term determines the child formula and
can affect both branch closure and proof size.

## Current Proflog Facts

`proflog.kernel` exposes three main quantifier proof constructors:

- `univ` for ordinary universal/gamma expansion;
- `once-univ` for the operational single-use universal produced when negating
  existential procedure-call bodies;
- `witness` for existential/delta expansion.

The ordinary universal path first instantiates with a fresh proof variable
`(var nom)`. It can also instantiate with a generated closed term from the
finite `gamma-terms` candidate set. When pending branch work exists, the
ordinary universal is re-enqueued so repeated instantiation remains possible.

`once-forall` is not a primitive in the source tableau syntax. It is an
internal NNF operational form used when negating existential clause bodies for
negative procedure calls. Unlike ordinary `forall`, it is not re-enqueued.

Existential proof mode introduces a rigid parameter `(par nom)` as the witness.
This matches the source-side delta-rule intuition that the witness is a fresh
but fixed branch parameter.

## Relevance Classification

| Aspect | Classification | Reason |
|---|---|---|
| Child formula produced by quantifier expansion | Relevant | Willard's `Deduction` predicate checks that a node is obtained from an ancestor by an allowed quantifier rule. |
| Existential fresh parameter identity and freshness | Relevant | A delta witness must be newly introduced on the branch; reusing or exporting it incorrectly can change proof validity. |
| Universal instantiated parameter term | Relevant | The selected `t` determines the child formula and can affect closure and proof size. |
| Bounded quantifier side conditions | Relevant | Bounded existential/universal rules introduce `<=` side conditions or implications that must appear in the expanded tableau or an equivalent macro. |
| Gamma re-enqueue/repeatability | Relevant to accepted proof relation | Ordinary universals may need multiple instantiations; a single-use policy changes the proof system unless it is limited to a justified internal form. |
| Search order for admissible instantiations | Probably irrelevant under proof | The order of trying fresh variables versus closed terms is not itself part of the proof object if the accepted certificate relation and size accounting are unchanged. |
| Finite closed-term candidate generation from fuel | Operationally relevant, not a Willard proof-object feature | It bounds runtime search; Track 2b must state unbounded/existentially sufficient candidates or a bounded-search theorem. |
| `once-forall`/`once-univ` | Relevant macro layer; primitive status unresolved | It may be a valid NNF macro for negated existentials in procedure-call bodies, but it is not one of the plain source rules and must be expanded or admitted explicitly. |

## Certificate Gap

The current proof constructors `univ`, `once-univ`, and `witness` carry only a
subproof:

```clojure
(univ subproof)
(once-univ subproof)
(witness subproof)
```

They do not include the instantiated universal term, the fresh proof variable,
or the delta parameter. Those objects live in branch environment state during
kernel evaluation. This is acceptable as an implementation detail only if
Track 2b proves a reconstruction relation from the certificate plus theorem,
system, branch state, and kernel rule to the corresponding formula-bearing
SJAS tableau node.

Without that reconstruction proof, the certificate is too skeletal for a full
SJAS proof-predicate correspondence: a quantifier proof object must determine
which instance was used, at least up to a formally specified expansion.

## Lower-Bound Consequence

Following
[SJAS Macro Expansion and Lower-Bound Adequacy](2026-05-26-sjas-macro-expansion-lower-bound.md),
the quantifier expansion may be larger than the Proflog certificate. That is
not a problem by itself. The problem would be a compact `univ` or `witness`
node that lets Proflog accept a proof while hiding large instantiated terms or
many repeated universal instances outside the proof-size accounting.

Track 2b must therefore prove one of:

1. the Proflog certificate plus branch reconstruction determines a
   formula-bearing tableau expansion whose size satisfies Willard's lower
   bound; or
2. the certificate grammar is extended/canonicalized so instantiation terms and
   parameter introductions are explicit proof data; or
3. the correspondence fragment excludes quantifier paths whose witnesses cannot
   be reconstructed with bounded lower-bound accounting.

The preferred route is macro expansion or explicit certificate enrichment, not
fragment exclusion, if the expansion can prove that Proflog does not produce
more compact self-referential proofs.

## Track 2b Obligations

For quantifier rules, the future correspondence proof must:

- define the SJAS-side parameter-term grammar;
- prove Proflog delta parameters correspond to newly introduced SJAS
  parameters;
- prove Proflog universal instantiations use admissible SJAS parameter terms,
  or define the chosen Proflog extension explicitly;
- account for repeated ordinary gamma instantiation and single-use
  `once-forall` separately;
- specify how bounded quantifier forms are represented or desugared, preserving
  side conditions and size lower bounds;
- prove finite fuel and finite `gamma-terms` are evaluator bounds rather than
  hidden proof-system restrictions, unless the theorem intentionally formalizes
  bounded search.

## Track 2a Conclusion

Quantifier and witness policy is relevant. The likely irrelevant part is only
the runtime order in which admissible instantiations are tried. The selected
witness terms, freshness conditions, repeatability, bounded side conditions,
and proof-size contribution are all part of the proof-object correspondence
that SJAS self-reference can see.
