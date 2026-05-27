# Proflog as a Candidate SJAS Deductive Apparatus

Date: 2026-05-27

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## User Question

The user refined Track 2 as follows:

> Track 2 asks whether the Proflog proof kernel is equivalent up to isomorphism
> to the deductive apparatus of IS#_D(beta), specified in the SJAS literature
> as a type of semantic tableau deduction. If so, then where an SJAS proof
> predicate asks "Does a formula with this code have a proof with this code?",
> instead of 1) building an object-language formula whose proof simulates in
> arithmetic the construction of a tableau tree, and assesses whether the tree
> closes or not 2) handing this formula to the Proflog kernel for proving, the
> formula and proof can be "encoded" as Proflog terms directly, and whether the
> formula has a proof can be proven directly, without the arithmetic
> intermediate representation. First, is or is this not a correct
> characterization of the relationship between Proflog and SJAS, and why? If
> so, is this or is this not a correct description of the Track 2 objectives,
> and why? If so also, then the reciprocal of this question is whether Proflog
> can be sufficiently formally defined so as to supply the deductive apparatus
> used by IS#_D(beta), and the proofs in the literature adapted to use
> specifically the Proflog kernel, rather than the semantic tableau method as
> given by Willard.

The follow-up instruction was to log the clarification and add a speculative
Track 2c for formalizing the Proflog kernel sufficiently to determine whether
SJAS results hold over `IS#_{D_Proflog}(beta)`.

## Assessment

The characterization is mostly correct, with one important tightening. The
question is not whether Proflog and SJAS prove the same formulas. The question
is whether the Proflog proof kernel, with a formal proof-object relation, can
stand in for the selected SJAS deductive apparatus without changing the
self-referential proof predicate's relevant intensional content.

Track 2b therefore asks for a proof-object correspondence:

```text
ProflogAccepts(P, S, F)
iff
SJAS_TableauProof(translate(P,S,F), S, F)
```

where `translate` preserves every Track-2a-relevant invariant and every ignored
implementation detail has an irrelevance proof.

The reciprocal question is a distinct but related research route: define a
formal deductive apparatus `D_Proflog` from the Proflog kernel itself and then
ask whether Willard's SJAS arguments can be carried out for
`IS#_{D_Proflog}(beta)`. This is not the same as proving Proflog equivalent to
Willard's already specified semantic-tableau `D`; it asks whether Proflog can
be nominated as the `D` in the `IS#_D(beta)` construction.

## Track 2c: Proflog-as-D Formalization

Track 2c is speculative and reciprocal to Track 2b.

Track 2b:

```text
Is current Proflog acceptance equivalent, up to relevant isomorphism, to the
SJAS literature's selected semantic-tableau proof predicate?
```

Track 2c:

```text
Can the Proflog kernel be formalized as a deductive apparatus D_Proflog such
that Willard-style SJAS results hold for IS#_{D_Proflog}(beta)?
```

Track 2c requires:

- a mathematical specification of Proflog proof states, branch agendas,
  literals, environments, equality substitutions, disequality stores,
  procedure calls, profile theory rules, fuel/search bounds, and proof
  certificates;
- a proof-code/size measure for Proflog certificates that satisfies the
  relevant SJAS conventional-tableau lower-bound discipline, or an explicit
  proof that the substituted measure is adequate for Willard's arguments;
- a classification of Proflog-specific rules as SJAS-admissible primitives,
  bounded macros, irrelevant scheduler/runtime details, or excluded features;
- an adaptation of the relevant Willard proof obligations to
  `IS#_{D_Proflog}(beta)`, including the self-consistency sentence that
  quantifies over the Proflog-formalized proof predicate;
- operational tests showing that the implementation realizes the formal
  `D_Proflog` relation on representative positive and negative proof objects.

Track 2c does not justify using "whatever the implementation currently
accepts" as `D`. It only becomes a legitimate route if the implementation is
abstracted into a stable formal calculus and the SJAS results are shown to
hold for that calculus.

## Why Theorem-Level Equivalence Is Too Weak

A theorem-level result has the form:

```text
Proflog proves F iff SJAS_D proves F
```

That is insufficient because the SJAS self-consistency sentence is not merely
about theorem extension. It is about a proof predicate:

```text
not exists p. Proof_D(S, contradiction, p)
```

If the proof predicate changes, the self-referential sentence changes its
subject. Two systems can prove the same external formulas while assigning
different meanings, sizes, or encodings to "p is a proof of F from S".

It is also not enough to check only that Proflog fails to close the same
headline contradiction or Goedel-sentence proof that SJAS fails to close. That
is useful operational evidence, but unless it ranges over the relevant proof
object domain with a size/tree-preserving translation, it does not show that
the self-reference is over the same apparatus.

If "Proflog cannot close the same proofs" means the strong statement

```text
for every covered P, S, F:
  ProflogAccepts(P,S,F) iff SJAS_TableauProof(translate(P,S,F),S,F)
```

then it is no longer mere theorem-level equivalence; it is essentially the
Track 2b proof-object correspondence. If it means only "the same theorem is not
provable" or "the same named counterexample is rejected", it is too weak.

## Non-Isomorphic Differences

The following differences cannot be dismissed as harmless isomorphisms unless
they are proved irrelevant or translated with bounded overhead:

- proof-code compression that allows one compact Proflog constructor to stand
  for an unbounded SJAS tableau subtree;
- procedure-call, equality, arithmetic, or profile rules that act as hidden
  oracles rather than explicit tableau primitives or bounded macros;
- different branch structure, child order only if relevant, or missing
  formula-bearing nodes needed to reconstruct the tableau tree;
- closure rules that close branches for reasons not present in the selected
  SJAS `D`, or fail to expose the closure reason in the proof object;
- quantifier/witness policies where witness terms, parameter freshness, or
  bounded side conditions are not recoverable from the proof certificate plus
  target;
- substitution and diagonalization behavior that changes which formula code the
  proof predicate self-references over;
- system-code or axiom-basis boundaries where Proflog sees external runtime
  clauses not present in the encoded SJAS system;
- proof predicates whose accepted proof-object set is correct extensionally but
  not inspectable/arithmeticizable in the object language;
- finite fuel or search limits treated as part of the proof predicate without a
  theorem relating them to the unbounded deductive apparatus;
- changes to the proof-size lower bound required by Willard's
  Conventional Tableaux Encoding Requirement.

The harmless cases are the genuine isomorphisms: renaming symbols, changing
byte layout, changing scheduler order, or wrapping proof terms, provided these
preserve the accepted proof-object relation, the relevant tree/size measures,
and the proof predicate's object-language inspectability.
