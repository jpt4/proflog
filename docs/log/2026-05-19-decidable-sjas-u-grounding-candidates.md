# Decidable SJAS Candidates Around U-Grounding Expressivity

Date: 2026-05-19

This note records a brainstorming and literature pass on whether a decidable
language could match enough of Willard's U-Grounding language to support an
SJAS: arithmetic internalization, `Pi*1` beta axioms, proof predicates, and a
self-consistency sentence.

The immediate question is sharper than the previous difference-logic survey:
perhaps the right route is not a generic decidable first-order fragment, but a
decidable structure that is equal, or nearly equal, in expressivity to
U-Grounding on the operations needed for introspection.

## Sources Checked

Local Willard/Proflog sources:

- Dan E. Willard, "Addition, Multiplication and a New Form of the Second
  Incompleteness Theorem", local text
  `target/sjas-pdf-text/willard2004_addition_not_multiplication_fol75.txt`.
- Dan E. Willard, "A Detailed Examination of Methods for Unifying,
  Simplifying and Extending Several Results About Self-Justifying Logics",
  arXiv:1108.6330, 2011, local text
  `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`.
- [SJAS Decidable Difference-Logic Fragment Survey](2026-05-19-sjas-decidable-difference-logic-survey.md).
- [SJAS Pi-Star-1 Beta and Relational Multiplication Clarification](2026-05-19-sjas-pi-star-beta-mult-clarification.md).
- [SJAS Multiplication Tradeoff Relevance](2026-05-15-sjas-multiplication-tradeoff-relevance.md).
- [SJAS Arithmetized Coding Research](2026-05-14-sjas-arithmetized-coding-research.md).

External sources checked in this pass:

- Bernard Boigelot, Pascal Fontaine, and Baptiste Vergain, "Decidability of
  Difference Logic over the Reals with Uninterpreted Unary Predicates",
  arXiv:2305.15059, 2023, <https://arxiv.org/abs/2305.15059>.
- SMT-LIB 2.7 logic catalogue, <https://smt-lib.org/logics-all.shtml>.
- Konstantin Kovalyov, "Axiomatization of Buchi arithmetic", arXiv:2411.03043,
  2024, <https://arxiv.org/abs/2411.03043>.
- Toghrul Karimov, Florian Luca, Joris Nieuwveld, Joel Ouaknine, and James
  Worrell, "On the Decidability of Presburger Arithmetic Expanded with Powers",
  arXiv:2407.05191, 2024/2025, <https://arxiv.org/abs/2407.05191>.
- Achim Blumensath and Erich Graedel, "Automatic Structures", LICS 2000,
  <https://lics.siglog.org/2000/Grdel-AutomaticStructures.html>.
- UCSD seminar summary of Khoussainov/Nerode automatic structures,
  <https://mathematics.ucsd.edu/seminar/automatic-structures>.
- Michael O. Rabin, "Decidability of Second-Order Theories and Automata on
  Infinite Trees", Transactions AMS, 1969,
  <https://research.ibm.com/publications/decidability-of-second-order-theories-and-automata-on-infinite-trees>.
- Luisa Herrmann, Vincent Peth, and Sebastian Rudolph, "Decidable
  (Ac)counting with Parikh and Muller: Adding Presburger Arithmetic to
  Monadic Second-Order Logic over Tree-Interpretable Structures",
  arXiv:2305.01962, 2023, <https://arxiv.org/abs/2305.01962>.
- Juvenal Murwanashyaka, "Undecidability in First-Order Theories of Term
  Algebras Extended with a Substitution Operator", arXiv:2111.00573, 2021,
  <https://arxiv.org/abs/2111.00573>.
- Search results and abstracts around Skolem arithmetic, Presburger arithmetic
  with divisibility, word equations, and Cobham-Semenov/Buchi arithmetic.

## U-Grounding Burden

Willard's U-Grounding language is not just "weak arithmetic". The 2004 and 2011
texts identify the relevant operations as:

- constants for `0` and `1`;
- equality and order;
- six non-growth functions: integer subtraction, integer division, root,
  maximum, logarithm, and bit-count;
- growth functions: addition and `Double(x) = x + x`;
- no multiplication function symbol.

`Delta*0` formulas are formulas over this language whose quantifiers are
bounded by U-Grounding terms. `Pi*1` formulas are universal closures over
`Delta*0` matrices.

The internalized self-consistency machinery needs more than arithmetic side
conditions. Willard's Appendix A vocabulary includes:

- `Neg_k(x,y)`: `x` codes a `Pi_k` sentence and `y` codes its negation;
- `Prf_beta^d(t,p)`: `p` is a proof of theorem code `t` from beta using
  deduction method `d`;
- `ExPrf_beta^d(h,t,p)`: proof from beta plus a sentence coded by `h`;
- `Subst(g,h)`: Godel substitution from formula code `g` to sentence code `h`;
- `SubstPrf_beta^d(g,t,p)`: the hybrid substitution/proof predicate;
- a `SelfCons_k(beta,d)` sentence asserting no simultaneous proofs of a
  sentence and its negation under that substituted self-reference.

Willard also gives a `Delta*0` definition of the multiplication graph using
division:

```text
[(x = 0 or y = 0) -> z = 0]
and
[(x != 0 and y != 0) -> ((z / x) = y and ((z - 1) / x) < y)]
```

This formula is the central pressure point. The language avoids a total
multiplication function symbol, but it can still talk about the multiplication
graph in bounded form.

## Screening Lemma

Any candidate that is fully definitionally equivalent to standard U-Grounding
under unrestricted first-order quantification is almost certainly not decidable.

Reason: U-Grounding has total addition and defines the multiplication graph by
the `Delta*0` division/order formula above. Therefore unrestricted first-order
truth over the standard U-Grounding structure interprets ordinary first-order
arithmetic over `+` and `*`. Any complete decision procedure for that full
structure would decide first-order arithmetic with addition and multiplication,
contradicting the classical undecidability frontier represented by Robinson
arithmetic and true arithmetic.

This does not kill the SJAS project, because Willard does not claim that the
full U-Grounding first-order theory is decidable. The narrower question is
whether some decidable language can preserve the particular `Pi*1` or
introspection-relevant fragment: enough coding, negation pairing, substitution,
proof checking, and self-consistency, without giving the system the full
unrestricted arithmetic structure.

So every candidate must answer two questions:

1. What part of the U-Grounding burden does it preserve?
2. Where exactly does it block the interpretation of full arithmetic?

## Candidate Matrix

| Candidate | Decidability basis | What it may preserve | Main risk | Ranking |
| --- | --- | --- | --- | --- |
| Single-base Buchi arithmetic: `(N,+,V_2)` | First-order theory of addition plus the base-2 valuation function is decidable; automata correspond to base-2 recognizable relations | Binary numerals, powers of two, many digit-position predicates, addition, bounded syntactic scans | Does not obviously define variable division, root, bit-count, or Willard's multiplication graph; proof substitution may exceed base-2 regularity | High priority |
| Presburger plus one powers predicate, e.g. `2^N` | Recent work gives decidability for some existential expansions with powers; Buchi arithmetic is the stronger established route for full FO with `V_p` | Compact binary landmarks, numeral-size reasoning, regular numeration checks | Existential decidability is not enough for universal `SelfCons`; adding too much exponentiation may approach open or hard frontiers | Medium |
| Word-automatic structures | FO theory of any automatic structure is decidable | Natural encoding of numerals, formulas, and finite proof strings by regular languages and synchronized automata | Full U-Grounding functions are unlikely automatic; proof checking must be regular, not host-side | High as a redesigned profile |
| WS1S/S1S over successor | Decidable by Buchi automata; matches monadic/difference-logic route | Regular word proofs, finite-state local tableau rules, Presburger-like addition through automata | Flat word encoding makes variable binding/substitution awkward; not equal to U-Grounding | High as a redesigned profile |
| WS2S/S2S or tree automata | Rabin tree theorem gives decidability for MSO over binary trees | Syntax and proof trees are native; substitution and formula formation may be structural rather than arithmetized | MSO rather than FOL; unrestricted substitution as a primitive is dangerous; may no longer be "U-Grounding equal" | Very high for non-FO SJAS variant |
| Tree-automatic or term-algebra structures | Pure term algebra FO theories are decidable; tree-automatic relations keep FO decidable | Formula codes as terms, structural well-formedness, local proof rules | A first-order substitution operator can make the theory undecidable; must keep substitution as a restricted regular relation | Medium-high |
| MSO plus Parikh/BAPA counting over trees | Recent `omegaMSO Join BAPA` results add global Presburger counting over tree-interpretable classes while preserving decidability under restrictions | Could recover a disciplined analogue of U-Grounding `Count(x,j)` and proof-size/cardinality constraints | Mild relaxations are undecidable; may be too far from Willard's arithmetic presentation | Medium-high |
| Presburger arithmetic | Full FO theory of `(N,+,<)` is decidable | Addition, order, bounded linear size arguments | Cannot define multiplication/proof coding in the required way; arbitrary unary predicates lose decidability | Low as sole basis |
| Quantified integer difference logic with unary predicates | Prompt paper and S1S route give decidability | Unary tags, successor/difference-local proof intervals | Too weak for numeric Godel coding unless proof objects are redesigned as regular words | Medium |
| Presburger arithmetic with divisibility | Existential fragments of PAD are decidable; full divisibility quickly approaches undecidability | Some quotient/divisibility tests reminiscent of U-Grounding division | Full FO with divisibility is too strong; existential fragments cannot state `SelfCons` | Low-medium |
| Skolem arithmetic `(N,*)` | FO theory with multiplication alone is decidable | Multiplicative divisibility and prime-factor structure | Lacks addition and has total multiplication, the wrong Willard tradeoff; not suitable for proof syntax | Low |
| Real closed fields or LRA | Pure theories are decidable | Rich real arithmetic or linear side conditions | Dense domain lacks natural finite syntax coding; arbitrary predicates or integers reintroduce undecidability | Low |
| Free monoid / word equations | Existential word equations are decidable | Direct string syntax and concatenation | Full FO with alternation or length constraints has hard/open/undecidable boundaries; substitution/proof predicates risky | Low-medium |
| Guarded, FO2, BSR, description-logic fragments | Many decidable fragments | Bounded or local finite model checking | Arity/prefix/model restrictions conflict with unbounded self-consistency over proof codes | Low |

## Candidate Notes

### 1. Buchi Arithmetic And Single-Base Automatic Arithmetic

This is the most plausible way to stay close to arithmetic while preserving
decidability. `V_2(x)` gives the largest power of two dividing `x`, and the
decidable theory supports base-2 automata-style reasoning. It naturally aligns
with Proflog's current move toward binary U-Grounding numerals.

Possible SJAS route:

- encode formula and proof codes in base 2;
- replace U-Grounding division/root/count with explicit automata predicates for
  the particular syntax/proof relations needed;
- define `Neg`, `Wff`, and local tableau-rule checks as base-2 recognizable
  relations;
- try to define `Subst` by a synchronized transducer relation rather than by
  arithmetic division.

Open concern: full Willard U-Grounding uses division to recover the
multiplication graph. Buchi arithmetic should not be allowed to regain that
graph. This means it is not equal to full U-Grounding. The research test is
whether it is equal enough for the introspective predicates.

Red flag: adding a second multiplicatively independent base, or enough power
machinery to reconstruct multiplication, risks crossing the Cobham-Semenov /
Peano-arithmetic boundary.

### 2. Automatic Structures

Automatic structures provide a general recipe: make the domain a regular
language and every basic relation synchronized-regular; then the first-order
theory is decidable.

This is attractive because an SJAS proof object is already a finite string or
tree in practice. Instead of forcing a proof into a Godel number and then
arithmetizing list operations, define the object language directly over regular
encodings of syntax/proofs.

Possible SJAS route:

- one sort for encoded formulas;
- one sort for encoded proof certificates;
- regular relations for constructor tags, subformula positions, rule labels,
  branch closure, and line ancestry;
- finite beta membership compiled into a regular relation;
- `SelfCons` states that no accepted proof certificate proves both a sentence
  and its negation.

This is not U-Grounding equivalence. It is an alternate internalization
discipline. It may be the cleanest way to get a decidable self-justifying
profile, provided "proof predicate" remains automatic and is not an
uninterpreted escape hatch.

### 3. WS1S/S1S Word Automata

The previous difference-logic survey identified integer/natural difference
logic with unary predicates as a plausible decidable route because it reduces
to S1S/MSO over successor. The current pass strengthens that candidate:
WS1S/S1S should be read as an object language for proof intervals and word
positions, not merely as an arithmetic side theory.

Possible SJAS route:

- a proof is a word interval with unary predicates marking formula starts,
  connective tags, variables, branch markers, and rule applications;
- `SelfCons` ranges over start/end positions of alleged proof intervals;
- proof validity is expressed by local MSO conditions rather than a ternary
  `Proof(system,theorem,proof)` predicate.

This is weaker than U-Grounding as arithmetic, but stronger as a native syntax
language. The challenge is substitution and variable binding: unrestricted
capture-avoiding substitution is not obviously regular in a flat word encoding.

### 4. WS2S/S2S, Tree Automata, And Tree-Interpretable Structures

Tree logics may be a better fit than words because formulas and proofs are
trees. Rabin's theorem gives a major decidability result for MSO over the
infinite binary tree. A weak/finite-tree variant can represent formula and proof
syntax with less artificial coding.

Possible SJAS route:

- formulas are finite labeled subtrees;
- proof trees are finite labeled subtrees with explicit rule nodes;
- `Neg`, `Wff`, subformula, branch closure, and local tableau checks are MSO
  tree properties;
- beta is a finite tree language or finite set of tree constants;
- `SelfCons` quantifies over finite proof trees.

The major warning comes from term algebra with a substitution operator:
Murwanashyaka shows undecidability in first-order finite-tree theories once a
substitution-like operator is added in broad enough form. Therefore substitution
must be kept as a carefully restricted MSO-definable relation, not promoted to
an unconstrained first-order function symbol.

This is probably the strongest non-FO candidate for a decidable SJAS-like
system. It is not equal to U-Grounding; it replaces arithmetized syntax with
native tree syntax.

### 5. MSO Plus Counting / BAPA / Parikh Constraints

U-Grounding's `Count(x,j)` exists because proof/syntax coding often needs
bounded counting over bits. Plain regular/MSO systems are weak at global
cardinality comparisons. Recent work on `omegaMSO Join BAPA` and Parikh-Muller
tree automata suggests a possible middle ground: keep tree/automata
decidability while admitting controlled Presburger constraints over counts.

Possible SJAS route:

- use tree MSO for syntax/proof shape;
- use BAPA/Parikh constraints only for global cardinalities needed by formula
  classes, code lengths, or bounded substitution witnesses;
- prohibit relations that reconstruct arbitrary multiplication.

This is speculative but important because it may preserve the practical purpose
of U-Grounding `Count` without importing all of U-Grounding arithmetic.

### 6. Term Algebras

The first-order theory of pure term algebras is decidable, and term algebras
give a natural home for formula syntax. However, pure term algebra is too weak
for proof checking unless extended with relations over subterms, positions,
substitution, or proof-tree well-formedness.

The substitution-operator literature is a direct warning. A term-algebra SJAS
profile should not add a general substitution function and hope decidability
survives. It should instead use a tree-automatic or MSO-definable family of
restricted substitution checks, with a separate proof that the relation remains
inside the decidable presentation.

### 7. Skolem Arithmetic

Skolem arithmetic is decidable because it has multiplication without addition.
It is a useful negative control: addition-only Presburger and
multiplication-only Skolem arithmetic are decidable separately, while their
combination is the familiar arithmetic danger point.

For SJAS, Skolem arithmetic is not promising:

- it lacks total addition, which Willard's Type-A line retains;
- it treats multiplication as total, which is not the Willard tradeoff;
- it does not naturally encode finite syntax/proof sequences.

### 8. Presburger With Divisibility

Presburger arithmetic with divisibility looks tempting because U-Grounding has
division. The positive results are mostly for existential fragments and related
restricted systems. Full first-order divisibility is dangerous because
divisibility plus addition can define enough multiplicative structure to lose
decidability.

This candidate is useful only as a bounded/existential side checker. It cannot
host the universal `SelfCons` sentence.

### 9. Free Monoids And Word Equations

A string-native theory is attractive for syntax. Makanin-style word equations
give major decidability results for existential word equations, but the
combination with length constraints and quantifier alternation has hard, open,
or undecidable boundaries depending on the exact language.

This is lower priority than automatic/WS1S/WS2S because those theories give a
cleaner decidability envelope for the universal self-consistency shape.

## Emerging Answer

There is a strong negative answer for exact equality:

```text
full U-Grounding expressivity
+ unrestricted first-order quantification
=> addition plus definable multiplication graph
=> full arithmetic interpretation
=> undecidable
```

Therefore a decidable SJAS cannot be "equal to U-Grounding" in the full
definitional-equivalence sense.

There are three plausible weaker readings:

1. **Pi*1-preserving arithmetic reduct.** Preserve just the `Pi*1` beta axioms
   and introspective predicates needed by `SelfCons`, not all U-Grounding
   `Delta*0` arithmetic.
2. **Proof-native decidable syntax.** Replace Godel-number arithmetic with
   automatic/WS1S/WS2S syntax and proof objects.
3. **Hybrid automata plus controlled counting.** Use tree/word automata for
   proof shape, plus Parikh/BAPA-style counting for the few global numeric
   constraints needed by proof classes.

The best concrete candidates are:

1. base-2 Buchi arithmetic / automatic arithmetic for a numerically flavored
   decidable profile;
2. WS2S/tree-automata proof trees for a syntax-native decidable profile;
3. MSO+BAPA/Parikh counting for a syntax-native profile that needs bounded
   counts;
4. WS1S/integer-difference proof intervals for a simpler but less ergonomic
   first experiment.

## Axiom-Basis Implications

A decidable SJAS candidate needs three layers to remain decidable:

1. **Base theory.** The background structure's theory must be decidable:
   Buchi arithmetic, an automatic structure, WS1S/WS2S, or a similarly
   well-bounded logic.
2. **Finite beta basis.** User beta axioms must be finite, syntactically
   checked, and either verified by the base decision procedure or restricted to
   a class with a separate truth/consistency argument.
3. **Internal proof predicate.** The proof predicate must be a definable
   relation of the same decidable structure. A host-side checker or
   uninterpreted `Proof` predicate preserves external decidability only by
   removing the very introspection an SJAS needs.

For Buchi/automatic/WS1S/WS2S candidates, the likely axiom basis is not an
infinite arithmetic axiom schema. It is a finite or regular presentation of the
intended structure plus a decision procedure/certificate calculus for that
structure. The SJAS proper would then add a finite beta set and a generated
`SelfCons` sentence whose proof predicate is checked by the same presentation.

This differs from Willard's existing Type-A route. Willard's metatheorem
supports finite true `Pi*1` beta axioms plus semantic tableaux or `Tab-1`, but
it does not claim that arbitrary theoremhood in the U-Grounding structure is
globally decidable.

## Recommended Next Experiments

### Experiment A: Buchi-Arithmetic Introspection Screen

Goal: determine whether `Neg`, `Wff`, finite beta membership, and one tableau
rule can be encoded in single-base Buchi arithmetic without defining full
multiplication.

Red tests:

- reject any encoding that introduces an uninterpreted `Proof/3` predicate;
- reject any encoding that defines `forall x y. exists z. Mult(x,y,z)`;
- reject any encoding that requires two multiplicatively independent bases.

Green tests:

- a base-2 recognizable code for a tiny formula language;
- definable negation pairing;
- definable proof certificate for one alpha or closure rule;
- a universal no-contradiction sentence over that certificate relation.

### Experiment B: WS2S Proof-Tree Prototype

Goal: encode formulas and tableau proof trees as finite labeled binary trees and
check whether substitution and proof validity stay MSO-definable.

Red tests:

- adding a general substitution function makes the profile invalid;
- proof validity cannot depend on host-side tree traversal.

Green tests:

- MSO-definable `wff`;
- MSO-definable complement/negation relation;
- MSO-definable alpha/beta/closure tableau rules;
- finite beta membership as a tree language;
- self-consistency sentence quantifying over proof trees.

### Experiment C: Counting-Enhanced Tree Profile

Goal: determine whether U-Grounding-style bit-count obligations can be replaced
by controlled Parikh/BAPA constraints while keeping decidability.

Red tests:

- constraints that reconstruct general multiplication;
- width-unrestricted combinations known to be undecidable.

Green tests:

- count proof lines, branch lengths, or tag occurrences with Presburger
  constraints;
- keep all syntax/proof relations tree-interpretable.

### Experiment D: Full-U-Grounding No-Go Formalization

Goal: write the short formal argument that unrestricted FO over standard
U-Grounding is undecidable.

Proof sketch:

1. U-Grounding contains total addition.
2. U-Grounding defines the multiplication graph by Willard's division formula.
3. Therefore `(N,+,*)` is first-order interpretable/definable in U-Grounding.
4. The first-order theory of arithmetic with addition and multiplication is
   undecidable.
5. Therefore any unrestricted FO theory definitionally equivalent to standard
   U-Grounding is undecidable.

This should be recorded as the first theorem/lemma of any future decidable-SJAS
ADR, because it blocks the most tempting but invalid target.

## Working Hypothesis

The most likely path to a decidable SJAS is not finding a hidden decidable
theory exactly as expressive as U-Grounding. The most likely path is to identify
which U-Grounding operations were only serving the implementation of proof
coding, then replace those operations with a proof-native decidable structure:
base-2 automatic words, WS1S proof intervals, or WS2S proof trees.

The design question becomes:

```text
Can SelfCons be stated over a decidable proof-certificate language
whose syntax/proof/substitution predicates are internal,
while the language is too weak to define full arithmetic?
```

That is the right next ADR question.

## Completion Audit For This Brainstorming Pass

Objective requirement: brainstorm possible candidates.

- Evidence: the candidate matrix covers Buchi arithmetic, powers/Presburger
  expansions, automatic structures, WS1S/S1S, WS2S/S2S, tree-automatic and
  term-algebra routes, MSO+BAPA/Parikh counting, Presburger, difference logic,
  Presburger with divisibility, Skolem arithmetic, real arithmetic, free
  monoids, and guarded/BSR-style fragments.
- Status: satisfied for a broad first-pass candidate map.

Objective requirement: reason about whether a decidable language can be equal
in expressivity to U-Grounding.

- Evidence: the screening lemma records why full unrestricted first-order
  definitional equivalence to standard U-Grounding should be undecidable:
  U-Grounding has total addition and a `Delta*0` multiplication graph.
- Status: satisfied, with the conclusion that exact equality is a no-go unless
  "expressivity" is restricted to a smaller introspective fragment.

Objective requirement: preserve enough `Pi*1` axioms and introspective
predicates.

- Evidence: the U-Grounding burden section identifies the required Willard
  predicates (`Neg`, `Prf`, `ExPrf`, `Subst`, `SubstPrf`, `SelfCons`), and the
  recommended experiments test whether those predicates can be encoded in
  Buchi arithmetic, WS2S, or counting-enhanced tree profiles.
- Status: satisfied as a research plan; actual preservation remains an ADR
  implementation/research task.

Objective requirement: expansive and inclusive literature review.

- Evidence: the source list includes the prompt difference-logic paper,
  Willard witnesses, SMT-LIB, Buchi arithmetic, Presburger-with-powers,
  automatic structures, Rabin tree automata, MSO+BAPA/Parikh counting, term
  algebra substitution undecidability, and negative-control areas.
- Status: satisfied for this brainstorming unit.

Objective requirement: address consistent, decidable language and axiom basis.

- Evidence: the axiom-basis section separates base theory, finite beta basis,
  and internal proof predicate, and records how candidate decidable structures
  would have to host finite beta and `SelfCons`.
- Status: satisfied at design level.
