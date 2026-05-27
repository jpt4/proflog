# SJAS Decidable Difference-Logic Fragment Survey

Date: 2026-05-19

This note records the first research pass on whether a decidable first-order
fragment, especially one based on quantified difference logic, can serve as the
language and axiom basis for a self-justifying axiom system.

Primary prompt source:

- Bernard Boigelot, Pascal Fontaine, and Baptiste Vergain, "Decidability of
  Difference Logic over the Reals with Uninterpreted Unary Predicates",
  arXiv:2305.15059, 2023, <https://arxiv.org/pdf/2305.15059>.

Related SJAS sources already used by the project:

- Dan E. Willard, "A Detailed Examination of Methods for Unifying,
  Simplifying and Extending Several Results About Self-Justifying Logics",
  arXiv:1108.6330, 2011.
- Dan E. Willard, "On the Significance of Self-Justifying Axiom Systems from
  the Perspective of Analytic Tableaux", arXiv:1307.0150, 2013/2014.

Adjacent linear-arithmetic fragment sources checked for this pass:

- Marco Voigt, "The Bernays-Schönfinkel-Ramsey Fragment with Bounded
  Difference Constraints over the Reals is Decidable", arXiv:1706.08504, 2017,
  <https://arxiv.org/abs/1706.08504>.
- Matthias Horbach, Marco Voigt, and Christoph Weidenbach, "On the Combination
  of the Bernays-Schönfinkel-Ramsey Fragment with Simple Linear Integer
  Arithmetic", arXiv:1705.08792, 2017,
  <https://arxiv.org/abs/1705.08792>.
- Marco Voigt and Christoph Weidenbach, "Bernays-Schoenfinkel-Ramsey with
  Simple Bounds is NEXPTIME-complete", arXiv:1501.07209, 2015/2020,
  <https://arxiv.org/abs/1501.07209>.
- SMT-LIB 2.7 logic catalogue, especially `LIA`, `LRA`, `QF_IDL`, `QF_RDL`,
  and `QF_UFIDL`, <https://smt-lib.org/logics-all.shtml>.

## Decidability Facts From Boigelot-Fontaine-Vergain

The paper studies first-order fragments that combine individual quantification,
weak arithmetic constraints, and uninterpreted unary predicates.

Important parameter values:

- uninterpreted predicate arity: unary only in the studied fragments;
- arithmetic domains: reals, integers, and mixed real/integer guards;
- arithmetic strength: pure order, integer difference constraints, or real
  difference constraints;
- quantification: first-order quantifiers over individual variables are allowed;
- interpreted integer predicate: `x in Z` is available in mixed fragments;
- higher-arity uninterpreted predicates: unrestricted first-order quantification
  with binary uninterpreted predicates is reported as immediately undecidable.

The core frontier:

| Fragment | Parameters | Decidability status | Relevance |
| --- | --- | --- | --- |
| `uf1.ro` | unary predicates plus order over reals | decidable, via known decidability of a universal monadic order fragment | Too weak/discrete-code-poor for direct SJAS coding, but relevant as a dense-order baseline. |
| `uf1.iro` | `uf1.ro` plus integer-membership atoms | included in decidable mixed fragment | Adds integer guards but no integer difference arithmetic by itself. |
| `uf1.idl.iro` | unary predicates, real/integer order, and difference constraints only between integer-guarded variables | decidable | Best candidate in this paper for a decidable first-order substrate with both order and discrete successor-like structure. |
| integer or natural difference logic with unary predicates | quantified, monadic predicates, discrete difference constraints | decidable; reduces to S1S/MSO over one successor | Best pure discrete candidate. Expressive power is regular/automata-like rather than arbitrary arithmetic. |
| `uf1.rdl` | unary predicates plus real difference constraints | undecidable, even with one unary predicate | Reject for decidable SJAS substrate. |
| `uf1.irdl` | integer and real difference constraints with unrestricted real difference part | not separately introduced because `uf1.rdl` is already undecidable | Reject if it contains unrestricted real difference constraints. |
| Presburger arithmetic plus one unary uninterpreted predicate | quantified linear integer arithmetic plus monadic predicate | undecidable, per cited prior results | Too strong when arbitrary unary predicates are mixed with full Presburger arithmetic. |
| real closed fields | quantified polynomial real arithmetic, no arbitrary predicates | decidable | Decidable as pure arithmetic, but wrong tradeoff for Willard-style SJAS and too strong if paired with arbitrary predicates. |
| unrestricted higher-arity uninterpreted predicates | binary or higher predicates plus unrestricted quantification | undecidable | Reject unless quantification or predicate use is sharply stratified. |

The paper also notes that adding constraints such as `x + y < 0` is outside the
decidability proof technique for the decidable real-order/mixed fragment. This
matters because full linear arithmetic is not a harmless extension of
difference/order fragments once unary predicates and quantifiers are present.

## Source-Backed Theorem Inventory

This inventory records the evidence used for the parameter matrix.

### Prompt Paper

- The language contains arithmetic symbols for real arithmetic, a monadic
  interpreted predicate `x in Z`, and arbitrary uninterpreted predicate symbols,
  but the paper restricts those arbitrary predicates to unary. It states that
  adding binary uninterpreted predicates with unrestricted first-order
  quantification directly yields undecidability.
- Arithmetic atoms in the studied fragments are order constraints `x < y` or
  difference constraints `x-y < c`, with relation variants and integer
  constants. Non-difference linear atoms such as `x+y < 0` are explicitly outside
  the decidability proof technique.
- `uf1.ro` has unary uninterpreted predicates plus real order only; the paper
  treats it as decidable via the universal fragment of monadic second-order
  real order.
- `uf1.iro` adds the interpreted integer-membership predicate.
- `uf1.idl.iro` permits order over real/integer variables and difference
  constraints only when the variables are integer-guarded. Theorem 1 proves
  `uf1.idl.iro` and `uf1.iro` decidable.
- `uf1.rdl` permits unrestricted real difference constraints with unary
  predicates. The paper proves its satisfiability problem undecidable, even with
  a single unary predicate; the conclusion says the proof adapts to `Q`.
- The paper cites prior results that Presburger arithmetic with a single
  monadic predicate is undecidable, including Downey and Halpern.
- The paper's long-term decision-procedure direction is automata on linear
  orderings for the decidable fragment, not a conventional tableau proof
  calculus.

### Adjacent Linear-Arithmetic Fragments

- Full first-order predicate logic plus linear real arithmetic and
  uninterpreted predicates is undecidable even when uninterpreted predicates are
  unary; Voigt's bounded-difference paper states this in its abstract.
- BSR plus bounded real difference constraints becomes decidable when
  universally quantified variables range over bounded intervals. This is a
  useful restricted higher-arity-predicate result, but the bound restriction is
  incompatible with an unbounded proof-code self-consistency predicate unless
  the SJAS claim is also bounded.
- General first-order predicate logic plus linear integer arithmetic is
  undecidable. Horbach-Voigt-Weidenbach recover decidability for the
  Bernays-Schönfinkel-Ramsey prefix `exists* forall*` with a restricted simple
  linear integer arithmetic fragment through finite ground instantiation.
- Voigt-Weidenbach's simple-bounds result is NEXPTIME-complete and nearly
  tight: adding richer arithmetic constraints such as difference inequations,
  simple additive inequations, quotient inequations, or multiplicative
  inequations breaks decidability for that BSR setting.

These adjacent fragments matter because they show that higher-arity predicates
are not absolutely impossible in every decidable setting. They are possible only
under severe prefix and arithmetic restrictions, and those restrictions are not
obviously compatible with an unbounded SJAS proof predicate.

### SMT-LIB Quantified And Quantifier-Free Baselines

- SMT-LIB `LIA` and `LRA` name closed quantified linear integer and real
  arithmetic formulas, respectively. They are useful baselines for pure
  arithmetic, but the catalogue also makes clear that multiplication/division
  are excluded except for multiplication by concrete coefficients.
- SMT-LIB `QF_IDL` and `QF_RDL` cover quantifier-free integer and rational/real
  difference logic. Their practical decision procedures can help check ground
  side conditions, but quantifier-free logics cannot state the universal
  self-consistency sentence.
- SMT-LIB `QF_UFIDL` allows quantifier-free integer difference arithmetic with
  uninterpreted symbols under restrictions that make it convertible to `QF_IDL`
  when no higher-arity free functions are present. This is a useful SMT surface,
  but still cannot serve as the whole SJAS language because it is
  quantifier-free and its proof checking would be external unless explicitly
  internalized.

## Expanded Parameter Matrix

The following matrix separates the major axes rather than only naming the
paper's four fragments.

| Domain | Arithmetic atoms | Predicate vocabulary | Quantification | Status | SJAS assessment |
| --- | --- | --- | --- | --- | --- |
| `N` / `Z` | pure equality/order/successor or difference constraints `x-y < c` | none beyond interpreted arithmetic | first-order | decidable, as a Presburger/difference fragment | Too weak by itself for ordinary arithmetized proof checking unless proof syntax is forced into a regular/unary coding. |
| `N` / `Z` | difference constraints | uninterpreted unary predicates | first-order | decidable; reducible to S1S/MSO over one successor according to the prompt paper | Most plausible decidable substrate, but only for an automata-local proof apparatus. |
| `N` / `Z` | full Presburger linear arithmetic | no arbitrary uninterpreted predicates | first-order | decidable | Stronger additive substrate, but ordinary proof checking over binary Godel codes is not Presburger-definable in general; adding arbitrary unary predicates loses decidability. |
| `N` / `Z` | full Presburger linear arithmetic | one or more arbitrary unary predicates | first-order | undecidable, per the prompt paper's cited results | Reject for a globally decidable SJAS profile. |
| `N` / `Z` | multiplication as total function or total graph axiom | interpreted arithmetic | first-order | Peano-style arithmetic undecidable; total multiplication is also exactly the Willard danger point | Reject for Willard-style self-justifying profile. |
| `R` | pure order | unary predicates | first-order | paper treats `uf1.ro` as decidable via monadic order results | Dense order gives little natural support for finite syntax/proof codes. |
| `R` plus integer predicate | order plus `x in Z` | unary predicates | first-order | included in decidable `uf1.idl.iro` envelope | Useful only when discrete proof coding is restricted to integer-guarded variables. |
| mixed `R`/integer-guarded variables | real/integer order plus integer-only difference constraints | unary predicates | first-order | decidable `uf1.idl.iro` | Best candidate from the paper if real order is needed; less direct for SJAS than pure integer difference logic. |
| `R` | real difference constraints `x-y < c` | unary predicates | first-order | undecidable `uf1.rdl`, even with one unary predicate | Reject; this is the paper's main surprise. |
| `Q` | real/rational difference constraints | unary predicates | first-order | paper states the undecidability proof adapts to `Q` | Reject for the same reason as `RDL`. |
| `R` | full linear real arithmetic | no arbitrary predicates | first-order | decidable as linear real arithmetic / ordered divisible abelian groups | Decidable pure theory, but arbitrary predicate additions subsume undecidable `uf1.rdl`. |
| `R` | real closed field operations | no arbitrary predicates | first-order | decidable by quantifier elimination | Has total multiplication and does not match Willard's restricted arithmetic tradeoff. |
| any infinite domain | weak arithmetic or none | binary or higher-arity uninterpreted predicates | unrestricted first-order | reported by the prompt paper as directly undecidable | Reject unless arity or quantification is specially stratified. |
| any domain | no arithmetic, arbitrary-arity predicates, no functions, BSR `exists* forall*` prefix | restricted first-order | decidable by finite grounding | Useful for finite/bounded verification, but lacks native arithmetic coding for syntax/proofs. |
| `R` | bounded real difference constraints | arbitrary predicates under BSR restrictions | restricted first-order | decidable when universal variables are range-bounded | Possible bounded-checking substrate; not enough for unbounded `SelfCons`. |
| `Z` | simple linear integer arithmetic | arbitrary predicates under BSR restrictions | restricted first-order | decidable by finite ground instantiation | Possible array/property-checking substrate; richer arithmetic variants become undecidable. |
| any domain | quantifier-free difference or linear arithmetic | any fixed finite set of ground atoms/predicates under SMT restrictions | quantifier-free | decidable by SMT-style procedures in many cases | Cannot state the universal self-consistency sentence; useful only as a ground checking subroutine. |

Two distinctions matter for this matrix:

1. **Pure arithmetic versus arithmetic plus arbitrary predicates.** Presburger
   arithmetic and linear real arithmetic are decidable as pure theories, but the
   prompt paper emphasizes that adding arbitrary unary predicates can destroy
   decidability. A decidable SJAS profile cannot freely add user predicate
   symbols and keep the same theorem.
2. **Satisfiability decidability versus proof-predicate definability.** A logic
   can have decidable satisfiability while still lacking the ability to define
   the proof relation needed by `SelfCons_k(beta,d)`. SJAS needs the latter.

## Parameter Coverage Audit

The goal asked for combinations of the parameter values discussed in and around
the prompt paper. The relevant coverage is:

| Axis | Values considered | Decisive observation |
| --- | --- | --- |
| Domain | naturals, integers, rationals, reals, mixed real/integer guards | integer/natural difference logic remains the plausible decidable discrete route; real difference logic with unary predicates is undecidable; the prompt paper says the real-difference undecidability adapts to `Q`; mixed real/integer order plus integer-guarded difference remains decidable. |
| Arithmetic strength | equality/order, successor-like difference constraints, full linear arithmetic, Presburger arithmetic, real closed field operations, total multiplication | pure linear arithmetic and real closed fields are decidable as pure theories, but arbitrary predicates or total multiplication either lose decidability or violate Willard's U-Grounding tradeoff. |
| Predicate interpretation | no arbitrary predicates, interpreted integer-membership predicate, uninterpreted unary predicates, higher-arity uninterpreted predicates | unary predicates are the maximum unrestricted predicate vocabulary supported by the prompt decidability frontier; binary/higher-arity predicates require special restrictions such as BSR and cannot be freely combined with unbounded proof coding. |
| Predicate arity | unary versus binary/higher-arity | monadicity is the key to the decidable prompt fragments; a direct `Proof(system,theorem,proof)` relation is therefore not admissible as an arbitrary object-language predicate. |
| Quantification | quantifier-free, unrestricted first-order, BSR `exists* forall*` | quantifier-free SMT fragments are useful sidecar checkers but cannot state `SelfCons`; unrestricted FO is available only in the monadic/difference frontier; BSR admits higher arity but not the unbounded self-reference shape. |
| Code representation | numeric Godel codes, tuple predicates, unary word-position encodings | existing Willard/Proflog proof coding wants numeric and tuple relations; a decidable variant must likely move to word-position/automata-local encodings. |
| Deductive apparatus | semantic tableaux, `Tab-1`, automata/S1S, automata on linear orderings, quantifier elimination, finite ground instantiation, quantifier-free SMT | only semantic tableaux/`Tab-1` are backed by Willard's current consistency theorem; only automata-style apparatuses align naturally with the decidable monadic/difference fragments. |

This is not a complete product lattice of every mathematically possible
first-order fragment. It covers the combinations made relevant by the prompt
paper, the adjacent BSR/linear-arithmetic frontier, and the extra SJAS
requirement that proof checking itself be internalized.

## Relation To Willard's Requirements

A candidate SJAS substrate is not merely a decidable satisfiability problem. It
must support:

1. a finite beta basis whose installed Group-2 axioms have checked `Pi*1`
   encodings;
2. a coding of formulas, finite systems, and proof objects;
3. object-language predicates corresponding to well-formedness, negation-pair,
   substitution, and proof checking;
4. a generated self-consistency sentence over those predicates;
5. a deduction apparatus `D` whose proof predicate can be internalized without
   reintroducing the strength that causes diagonalization.

This creates a tension. Decidable difference-logic fragments are attractive
because they are weak, but their weakness is also what makes Willard-style
arithmetized syntax and proof checking difficult.

In particular:

- quantifier-free fragments are decidable but cannot state the universal
  `SelfCons` sentence;
- dense real order with unary predicates lacks a natural discrete coding
  apparatus for finite syntax and proof objects;
- integer difference logic with unary predicates has a discrete order and a
  successor-like structure, but its definability is essentially automata/regular
  in character, not general primitive-recursive proof checking over binary
  Godel numbers;
- Presburger arithmetic has enough linear structure to be useful, but adding an
  arbitrary unary predicate already crosses into undecidability;
- unrestricted higher-arity uninterpreted predicates are unavailable if global
  decidability is required.

This means that "decidable first-order language" is not by itself the right
goal. The stronger requirement is a decidable language whose syntax, finite
axiom membership, substitution, negation pairing, and proof-certificate checks
are also definable without leaving the fragment.

## Multiplication Parameter

The earlier `Pi*1` beta clarification remains important here. Universal
conditional use of a multiplication graph is not itself totality:

```text
forall x y z. Mult(x,y,z) -> Phi(x,y,z)
```

The dangerous form is existential totality:

```text
forall x y. exists z. Mult(x,y,z)
```

However, the decidable fragments surveyed by Boigelot-Fontaine-Vergain do not
include Willard's full U-Grounding multiplication graph as a native interpreted
relation. A decidable-difference-logic SJAS therefore cannot simply import the
current Proflog U-Grounding `mult/3` and proof-code decoder without rechecking
decidability. If `Mult` is left uninterpreted, the system loses the intended
arithmetized-syntax content; if it is fully axiomatized, it may leave the
decidable fragment.

## Candidate Deductive Apparatuses

### Semantic Tableaux Or Tab-1 Over Willard U-Grounding

This is the established Willard path. The 2013/2014 paper's finite
`IS#_D(beta)` result is stated for `D` as semantic tableaux or the `Tab-1`
construct. The same paper warns that stronger deduction methods can break the
consistency-preservation invariant, including Hilbert deduction and `Tab-2`.

This path is the best match to the current Proflog implementation, but it is
not a claim that the whole satisfiability problem for arbitrary beta/theorem
queries is decidable. It is a metamathematical consistency-preservation route,
not a decidable-SMT-fragment route.

### Automata/S1S Apparatus For Integer Difference Logic

Quantified integer/natural difference logic with unary predicates reduces to
S1S, the monadic second-order theory of one successor, and is decidable by
automata methods. A candidate SJAS here would have to make the proof predicate
native to this automata representation.

This suggests a possible new research direction:

- encode formulas/proofs as unary/word positions with monadic predicates for
  tags, delimiters, variable classes, proof-line classes, and local rule
  witnesses;
- restrict proof rules so each proof-checking condition is regular/MSO-definable
  over the word/successor structure;
- use the S1S/automata decision procedure, or a proof-carrying automata
  calculus, as `D`;
- state self-consistency against that automata-checkable proof predicate.

This would be a different SJAS, not a drop-in replacement for Willard's
U-Grounding tableau proof predicate. Its central open question is whether enough
logical syntax and substitution can be made regular/local without smuggling in
stronger arithmetic through auxiliary predicates.

The key design challenge is tuple coding. The paper's decidable fragments allow
unary predicate atoms `P(x)` plus arithmetic relations between variables. A
Willard-style proof predicate, however, is naturally a relation like
`Proof(system-code,theorem-code,proof-code)`. To stay monadic, a decidable
variant must avoid introducing `Proof/3` as an arbitrary higher-arity predicate.
It must instead represent proof objects as word/position structures whose
well-formedness and local rule checks are expressible by unary tags and
successor/difference constraints.

The most coherent object-language shape for this route is therefore not
`Proof(s,t,p)` over numeric Godel codes. It is closer to:

```text
forall start end.
  not ProofInterval(start,end)
```

where `ProofInterval` abbreviates a finite collection of first-order formulas
over word positions, unary tag predicates, and bounded successor/difference
constraints. This preserves monadicity by making proof data part of the
structure of an interval rather than a tuple relation over numeric codes.

### Automata On Linear Orderings For `uf1.ro` Or `uf1.idl.iro`

The paper reduces the decidable mixed fragment to order with unary predicates
and cites automata on linear orderings as a likely route toward practical
decision procedures. This could support a dense/discrete hybrid proof system:
real variables provide order regions, integer-guarded variables provide
difference steps, and unary predicates encode finite-state annotations.

This is less directly aligned with SJAS than the pure integer/S1S route because
syntax and proof codes are discrete. The real-order component may be useful for
model-theoretic experiments, but it does not obviously help with
self-reference.

### Decision-Procedure Consistency Rather Than Tableau Consistency

A more radical option is to make `D` be a complete decision procedure for the
chosen decidable fragment. The self-consistency statement would then be about no
accepted certificate pair for a formula and its negation.

This is attractive only if the decision procedure emits checkable certificates
whose correctness relation is definable inside the same weak object language.
If certificate checking is performed by the host or by an uninterpreted
predicate, the system no longer demonstrates internalized self-justification.

### Quantifier-Free SMT Apparatus

Quantifier-free difference logic and quantifier-free linear arithmetic are
practically decidable and useful as proof-search subroutines. They cannot serve
as the whole SJAS object language because `SelfCons` is universal and talks
about all relevant proof/certificate codes. They may still be useful as leaves
inside a larger decidable proof system, where arithmetic side conditions are
checked by a quantifier-free certificate rule.

### BSR With Simple Bounds Or Bounded Difference Constraints

The BSR family permits higher-arity uninterpreted predicates under a restricted
quantifier prefix and, in the cited arithmetic extensions, under simple or
bounded arithmetic constraints. This gives a decidable first-order setting with
more relational vocabulary than `uf1`.

It is still a poor direct SJAS fit:

- the `SelfCons` sentence is naturally universal over unbounded formula/proof
  codes, not over a bounded instantiation set;
- finite ground instantiation is a decision method for a fixed formula, not an
  internal proof predicate over arbitrary proof objects;
- adding the richer arithmetic needed for unbounded code manipulation is exactly
  what the cited BSR arithmetic results identify as breaking decidability.

BSR-style fragments may be useful for a bounded "finite certificate sanity"
profile, but that would be a bounded verification tool rather than an SJAS in
Willard's sense.

## Deductive Apparatus Verdict Matrix

| Apparatus `D` | Natural fragment | Decidability status | SJAS verdict |
| --- | --- | --- | --- |
| Semantic tableaux | Willard U-Grounding `Pi*1` beta basis | Willard consistency-preservation theorem applies for the stated `IS#_D(beta)` setting, but global satisfiability is not claimed decidable | Current Proflog/Willard route; faithful to the literature, not a decidable-fragment route. |
| `Tab-1` | Willard U-Grounding `Pi*1` beta basis with controlled theorem reuse | Same Willard theorem family; stronger `Tab-2`/Hilbert-style methods are warned against | Valid future extension target if proof-list reuse is mechanized carefully. |
| S1S/Büchi-style automata | natural/integer order or difference logic with unary predicates | Decidable by automata methods | Best candidate for a new decidable SJAS-like system, but only if proof certificates are redesigned as regular/local word structures. |
| Automata on linear orderings | `uf1.ro` and the prompt paper's `uf1.idl.iro` reduction target | Decidable in principle; prompt paper notes no practical `uf1.ro` procedure yet | Possible dense/discrete experiment; less direct than pure integer/natural S1S for proof syntax. |
| Quantifier elimination | pure Presburger, pure LRA, pure RCF | Decidable pure theories | Useful for arithmetic side conditions; not enough once arbitrary proof predicates or unary program predicates are added. |
| Finite ground instantiation | BSR plus simple bounds/simple LIA/bounded real difference constraints | Decidable under strict prefix and arithmetic restrictions | Useful bounded verification profile; does not express unbounded self-consistency over proof codes. |
| Quantifier-free SMT certificates | `QF_IDL`, `QF_RDL`, `QF_LIA`, `QF_UFIDL`-style surfaces | Practically decidable fragments | Sidecar checker only unless certificate checking is itself encoded in the SJAS object language. |
| Host-side proof predicate or uninterpreted `Proof/3` | any fragment where proof checking is delegated | May preserve external decidability only by hiding the hard part | Not acceptable for self-justification; it assumes the core property instead of internalizing it. |

## Current Answer

No reviewed decidable first-order fragment is currently ready to serve as a
faithful Willard-style SJAS language and axiom basis with the existing Proflog
proof predicate.

The strongest negative reason is not simply undecidability. It is that the
decidable fragments appear too weak to internalize the formula/proof/substitution
machinery required by `SelfCons_k(beta,d)` unless the proof apparatus itself is
redesigned to have regular/automata-local certificates.

The most plausible research candidate is:

```text
quantified integer/natural difference logic
+ unary uninterpreted predicates
+ automata/S1S-style proof or decision certificates
```

or the paper's mixed decidable fragment:

```text
uf1.idl.iro
```

but only if the SJAS proof predicate is redesigned around automata-checkable
local certificates. The currently implemented Willard/Proflog route remains:

```text
Willard U-Grounding Pi*1 language
+ finite beta
+ semantic tableaux or Tab-1 as D
```

with decidability of the global satisfiability problem not claimed.

There is therefore no currently identified off-the-shelf decidable first-order
logic that both:

1. admits enough arithmetic/coding to internalize Willard's existing
   `SelfCons_k(beta,d)` proof predicate; and
2. preserves decidability under the needed predicates, quantification, and
   proof-code operations.

The positive result is weaker and conditional: a decidable SJAS-like system may
be possible if the proof apparatus is redesigned around monadic/regular data
rather than imported from semantic tableaux over binary Godel codes.

## Completion Criteria For A Future Decidable-SJAS ADR

A future ADR should not be considered successful merely because its user
formula fragment has a decidable satisfiability problem. It must show all of the
following:

1. **Language restriction.** The frontend rejects higher-arity arbitrary
   predicates, unrestricted real difference constraints, full Presburger plus
   arbitrary unary predicates, and total multiplication.
2. **Code representation.** Formula and proof objects are represented in a form
   native to the decidable fragment, likely unary/word-position coding rather
   than binary Godel numerals.
3. **Object-language syntax checks.** `wff`, formula class, and negation-pair
   checks are definable in the fragment.
4. **Substitution discipline.** Either diagonal substitution is definable in the
   fragment, or the self-consistency sentence is redesigned around a native
   fixed-point/certificate mechanism that does not require general substitution.
5. **Proof predicate.** The selected `D` has certificates whose validity is
   expressible inside the same fragment, not hidden in the host and not modeled
   by an uninterpreted higher-arity predicate.
6. **Consistency theorem.** The resulting self-consistency axiom must come with
   a metatheoretic argument analogous in role to Willard's semantic-tableau or
   Tab-1 consistency-preservation theorem. Decidability alone does not prove
   self-justification.

## Immediate Design Consequences For Proflog

1. Do not represent the current Proflog SJAS as a decidable difference-logic
   fragment. It is a Willard U-Grounding/tableau implementation.
2. If a decidable SJAS variant is pursued, give it a new ADR and a new proof
   profile name; do not overload `:willard-sjas-level1`.
3. The new profile's first red test should reject any attempt to cite an
   uninterpreted proof predicate as self-justification.
4. The first positive tests should demonstrate that formula syntax,
   complement/negation, and at least one proof-certificate rule are checked by
   the same decidable object-language apparatus.
5. Higher-arity uninterpreted predicates and unrestricted real difference
   constraints should be excluded from any decidable-profile language
   declaration.

## Completion Audit For This Survey Goal

Objective requirement: draw from the mentions in arXiv:2305.15059.

- Evidence: the source inventory records the paper's named fragments
  `uf1.ro`, `uf1.iro`, `uf1.idl.iro`, and `uf1.rdl`; its unary-predicate
  restriction; Theorem 1 decidability; Theorem 2 real-difference
  undecidability; the `Q` adaptation note; the exclusion of non-difference
  atoms such as `x+y < 0`; and the automata-on-linear-orderings direction.
- Status: satisfied.

Objective requirement: review quantified difference logic.

- Evidence: the matrix distinguishes quantifier-free SMT difference logic from
  quantified integer/natural difference logic with unary predicates and the
  prompt paper's mixed real/integer guarded difference fragment.
- Status: satisfied for the fragments relevant to the prompt paper and SJAS
  design.

Objective requirement: review linear arithmetic.

- Evidence: the note covers pure Presburger/LIA, pure LRA, real closed fields,
  full Presburger plus unary predicates, BSR with simple LIA, BSR with simple
  bounds, and quantifier-free SMT linear fragments.
- Status: satisfied for the decidability frontier needed by this goal.

Objective requirement: review combinations of interpreted/uninterpreted
predicates, unary/higher arity, real/integer/natural/rational domains, and
quantified/unquantified forms.

- Evidence: the expanded parameter matrix and parameter coverage audit cover
  those axes explicitly, including the decisive higher-arity exception for BSR
  restrictions.
- Status: satisfied at the level needed for an SJAS architectural decision.

Objective requirement: determine whether a decidable first-order system can
serve as the language and axiom basis for an SJAS.

- Evidence: the current answer gives a negative result for off-the-shelf
  systems: no reviewed decidable FOL fragment both internalizes the existing
  Willard/Proflog `SelfCons_k(beta,d)` proof predicate and preserves global
  decidability. It also identifies the only plausible new direction:
  integer/natural difference logic with unary predicates plus an
  automata/S1S-style proof apparatus using regular/local certificates.
- Status: satisfied, with the caveat that the automata-local SJAS is a future
  research candidate, not an established Willard-equivalent system.

Objective requirement: identify compatible deductive apparatuses.

- Evidence: the deductive apparatus sections and verdict matrix cover
  semantic tableaux, `Tab-1`, S1S/Büchi automata, automata on linear orderings,
  quantifier elimination, finite ground instantiation, quantifier-free SMT, and
  the rejected host-side/uninterpreted proof-predicate route.
- Status: satisfied.
