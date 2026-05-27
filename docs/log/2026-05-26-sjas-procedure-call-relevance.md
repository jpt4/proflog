# SJAS Procedure-Call Relevance

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note refines the classification of Proflog procedure-call proof
constructors in SJAS proof certificates. It follows the reflected-clause
reachability result in
[SJAS Proof-Constructor Reachability Audit](2026-05-25-sjas-proof-constructor-reachability-audit.md),
where the Tableau-0 demo theorem `demo(1)` produced a certificate containing
`neg-call`.

The classification is:

- procedure-call constructors are not ignorable runtime mechanics;
- they are not part of Willard's plain candidate-tree rule list as currently
  documented;
- they may be admissible in Proflog's selected deduction apparatus only if the
  selected `D` explicitly includes Fitting/Proflog procedure calls, or if they
  are proved to be bounded macros over reflected Group-2b axiom applications
  and ordinary tableau expansion;
- a correspondence theorem that excludes them must also exclude reflected-clause
  theorem demonstrations using those constructors.

## Source Boundary

Willard's semantic-tableau source trail gives the plain tree obligations:

- A proof tree has a root equal to the normalized negation of the theorem;
  non-root nodes are axioms or deductions from ancestors; all branches close.
  Local source:
  `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 806-839.
- Appendix C's decision procedure for `ExSemPrf` names tree predicates such as
  `SentenceTree`, `ProperRoot`, `Closure`, and `Deduction`; the `Deduction`
  item lists ordinary semantic-tableau rules for conjunction, negation,
  disjunction, implication, existential, and universal expansion. Local source:
  `target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
  lines 3326-3378.
- Willard's later statements allow a Fitting/Smullyan semantic-tableau
  apparatus, but the reviewed source trail does not by itself identify
  Proflog's logic-program Procedure Call Rule as a primitive rule of the
  specific SJAS `D`.

Project design notes deliberately chose Proflog's Fitting-style kernel as the
implementation substrate, including subsidiary tableaux for procedure calls.
That is a plausible implementation route, but it creates a Track 2b proof
obligation rather than a free equivalence to Willard's plain `SemPrf`.

## Current Proflog Facts

The kernel treats procedure calls as proof-producing tableau steps:

- positive atoms may close by opening a subsidiary tableau for the matching
  compiled clause body, producing `(pos-call subproof)`;
- negative atoms may close by opening a subsidiary tableau for the compiled
  NNF negation of the clause body, producing `(neg-call subproof)`;
- multi-alternative and guarded variants produce `neg-call-alt`,
  `neg-call-guarded-alt`, and guarded sequence constructors;
- saved atoms can become callable after equality walking, producing
  `eq-triggered-call` or `eq-triggered-neg-call`.

The source builder also gives reflected user clauses a dual role:

- they remain executable procedure clauses in the compiled Proflog program;
- they are encoded in the finite SJAS system as reflected Group-2b axiom
  formulas of the shape `forall x1 ... forall xn. body -> R(x1,...,xn)`.

Tests already distinguish these roles. A beta-only composite formula can prove
a theorem as axiom text without defining an executable relation, while a
reflected composite clause is both executable and available as a Group-2b axiom
citation recovered from encoded `system-code`.

## Why This Is Relevant

The procedure-call rule changes the proof object. It does not merely affect
which branch the evaluator visits first.

For a reflected clause

```text
R(x) :- Body(x)
```

the Proflog kernel can treat an atom `R(t)` as a call and open a subsidiary
tableau for `Body(t)` or its negation. A plain Willard-style candidate tree,
by contrast, would treat the universally closed reflected formula

```text
forall x. Body(x) -> R(x)
```

as an axiom node and then use ordinary universal, implication, negation, and
closure rules to connect that axiom to the branch.

Those two presentations may be extensionally equivalent for covered clauses,
but they have different proof objects unless a correspondence proof supplies an
explicit expansion. The difference can affect:

- node count and branch shape;
- where the reflected axiom formula appears in the proof tree;
- witness/instantiation policy for the universally closed reflected axiom;
- size accounting, because a compact `neg-call` constructor can hide the full
  clause formula and its tableau expansion;
- equality interaction, because `eq-triggered-call` variants wait until branch
  equalities make a saved atom callable.

Therefore, procedure-call constructors are relevant as certificate-level
macros. Their primitive status is unresolved.

## Track 2b Options

A future correspondence proof must pick one of these routes.

### Primitive-Rule Route

The selected SJAS deduction apparatus `D` may explicitly include the
Fitting/Proflog Procedure Call Rule. In that case Track 2b must formalize that
rule on the SJAS side, including:

- one compiled clause per relation, or the exact multi-alternative/guarded
  semantics if those variants are admitted;
- L-groundness or whatever admissibility condition is used for calls;
- positive and negative call semantics;
- fresh subsidiary tableaux with the correct branch-state isolation;
- equality-triggered call reopening, if equality interaction is admitted;
- proof-size contribution of the call node and the subsidiary proof.

This route is plausible for Proflog as a logic-programming implementation, but
it must be explicitly named as the selected `D`. It is not the same as claiming
plain Willard candidate-tree tableaux without extension.

### Macro-Expansion Route

Procedure calls may be treated as compact macros over reflected Group-2b axiom
applications. For each accepted call certificate, Track 2b would expand the
call into an ordinary formula-bearing tableau subtree:

1. cite the reflected axiom formula from encoded `system-code`;
2. instantiate its universal binders with the call arguments;
3. apply implication/negation tableau rules in the polarity-appropriate way;
4. continue with the subsidiary proof body;
5. prove that the expansion preserves branch closure and has bounded size
   relative to the compact call certificate plus the reflected clause code.

This route keeps the SJAS-side `D` closer to ordinary semantic tableaux, but it
inherits the proof-size problem from
[SJAS Proof-Size Relevance](2026-05-26-sjas-proof-size-relevance.md). A small
`neg-call` node cannot be allowed to hide an unbounded formula-bearing subtree
without explicit size accounting.

### Fragment-Exclusion Route

The correspondence theorem may exclude procedure-call constructors from its
covered fragment. That is rigorous but narrow. It would not cover the current
reflected-clause demonstration where `demo(1)` validates through `neg-call`,
nor any future SJAS theorem whose proof certificate uses `pos-call`,
guarded-call, or equality-triggered call constructors.

## Classification Update

| Aspect | Classification | Reason |
|---|---|---|
| Procedure-call constructors in proof certificates | Relevant macro layer; primitive status unresolved | They alter the proof object and can hide reflected-axiom expansion, but may be justified as selected-D primitives or bounded macros. |
| Reflected Group-2b axiom membership | Relevant | Reflected clauses are part of encoded system-code and can be cited as axiom formulas. |
| Runtime lookup data structures for compiled clauses | Probably irrelevant under proof | Map/list layout and lookup order are implementation details if the selected relation over reflected clauses is preserved. |
| L-groundness/admissibility checks | Relevant if procedure calls are admitted | These checks determine when a call rule may fire and therefore affect accepted proof trees. |
| Equality-triggered calls | Relevant and coupled to equality classification | They depend on branch equality state, so they cannot be classified independently of the equality/disequality calculus. |
| Guarded and multi-alternative call variants | Unresolved/high risk | They can encode larger disjunction/guard structure under compact proof constructors and need primitive, macro, or exclusion treatment. |

The working Track 2a conclusion is that procedure calls should remain
`unresolved` in the executable audit until Track 2b chooses a route, but their
relevance is no longer speculative. Any correspondence claim covering reflected
SJAS clauses must preserve, expand, or explicitly exclude the procedure-call
proof constructors.
