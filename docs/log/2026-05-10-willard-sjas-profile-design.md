# Willard SJAS Profile Design Notes

Date: 2026-05-10

This note extracts the implementation-relevant content from the local Willard
SJAS corpus in `sjas/nachlass/` and maps it onto Proflog's existing proof
profile architecture. It is intentionally a design note, not a claim that
Proflog has already formalized Willard's consistency-preservation metatheorems.

## Corpus Reviewed

Primary local witnesses:

- `sjas/nachlass/papers/willard2001_self_verifying_axiom_systems_author_jsl1.pdf`
- `sjas/nachlass/papers/willard2002_semantic_tableaux_robinson_q_author_jsl2.pdf`
- `sjas/nachlass/papers/willard2004_addition_not_multiplication_fol75.pdf`
- `sjas/nachlass/papers/willard2005_addition_total_consistency_author_jsl5.pdf`
- `sjas/nachlass/papers/willard2011_self_justifying_logics_arxiv_1108.6330.pdf`
- `sjas/nachlass/papers/willard2013_significance_self_justifying_axiom_systems_arxiv_1307.0150.pdf`
- `sjas/nachlass/papers/willard2016_theta_function_symbol_arxiv_1612.08071.pdf`
- `sjas/nachlass/papers/willard2018.pdf`
- `sjas/nachlass/papers/willard2020.pdf`
- `sjas/nachlass/works-citing-dew/README.md`

The most directly implementable line for Proflog is the semantic-tableaux,
Type-A, Level-1 line described in the 2004, 2005, 2011, and 2013 witnesses.
That line uses a Fitting/Smullyan tableau apparatus, treats addition as total,
treats multiplication as a three-place relation rather than a total function,
and restricts self-consistency to a low formula class. This matches Proflog's
existing Fitting-style kernel much better than the later Hilbert/theta-function
line.

## Prerequisite Map

The corpus depends on several background results and technical devices. The
implementation only needs some of them in the first profile.

Direct implementation prerequisites:

- Fitting/Smullyan semantic tableaux. Proflog already implements this proof
  shape at the kernel level, so it is the natural first deduction apparatus.
- Kleene fixed-point and Goedel-coding machinery. The frontend/profile builder
  must construct a self-referential formula, and the kernel profile must reason
  over encoded formulas and proof certificates.
- Weak arithmetic formula classes. Delta-star-0, Pi-star-1, Sigma-star-1, and
  Level-1 consistency must become explicit classifiers over the selected
  U-grounding language.
- U-grounding arithmetic. The profile needs relational definitions for the
  grounding functions and the `mult/3` graph relation.
- Limited theorem reuse. A faithful Tab-1 variant of ISD(A) or IS#_D(beta)
  needs the proof-list apparatus, but a first implementation may faithfully set
  the reflected deduction method `D` to ordinary semantic tableaux.

Boundary-setting prerequisites:

- Robinson arithmetic Q and its extensions explain why ordinary strong
  arithmetic is the wrong implementation target for self-justification. The
  existing Proflog `:robinson-q` work is useful architectural precedent, but
  Willard's Type-A profile is not just Q with another axiom list.
- Pudlak/Solovay/Nelson/Wilkie-Paris style results set the Hilbert-side
  boundary: successor-total systems are already too strong for the Hilbert
  self-consistency target.
- Adamowicz-Zbierski and Willard's tableaux generalizations set the
  semantic-tableaux boundary: multiplication totality and higher consistency
  levels reintroduce second-incompleteness obstacles.
- Definable cuts and localized consistency are adjacent literature but are not
  the first Proflog implementation route, because Willard's SJAS line uses
  self-referential consistency declarations rather than only cut-localized
  consistency claims.

Contextual prerequisites from second-order witnesses:

- The archived works by Salehi, Artemov, Beklemishev/Shamkanov, Cheng, Chow,
  Pakhomov, Visser, Dvorkin, Yudkowsky/Herreshoff, and others confirm that
  Willard's systems are part of a broader literature on consistency,
  provability, weak theories, and self-reference.
- These works are useful for tutorial motivation and future comparisons, but
  they do not change the first implementation target: a corpus-faithful
  Proflog profile should start with Willard's Type-A semantic-tableaux line.

## Extracted Mathematical Commitments

An SJAS is not just a set of formulas. Willard's repeated object is a pair:

- an axiom basis, usually written alpha;
- a deductive apparatus, usually written d or D.

The pair is self-justifying when one theorem, or sometimes one axiom, says that
the same basis plus the same deduction apparatus is consistent in a specified
weak sense, and the resulting system is in fact consistent.

The important implementation consequence is that the proof profile must track
both:

- what formulas are proper axioms;
- what proof method is being reflected by the internal proof predicate.

For the Proflog profiles, this means `:proof-profile :willard-sjas-tableau0`
and `:proof-profile :willard-sjas-level1` cannot be cosmetic language tags.
Each must select a concrete proof apparatus and make proof evidence record that
apparatus.

## Executable SJAS-Lang Motivation

The implementation motivation is to make the logical restrictions computational
and inspectable. A Proflog SJAS profile should let us ask:

- what machinery is required to mechanize the axiom basis, proof predicate,
  syntax coding, and deductive apparatus of a Willard-style SJAS;
- which Proflog programs become natural once the system can reason about its
  own proof certificates;
- which programs are blocked or forced into weaker encodings because the
  profile omits a multiplication function, strong induction, full Hilbert
  reflection, or unrestricted proof reuse;
- how much executable behavior remains once host-side arithmetic and proof
  checking are removed after source-to-kernel translation.

This is why the profile should be built as an executable language profile, not
only as a static collection of formulas. The logical boundary and the
computational boundary are the same object in different views.

## Deduction Apparatus

The semantic-tableaux line uses a Fitting/Smullyan style apparatus. That is the
best first target because Proflog's kernel already proves by negating the target,
expanding alpha/beta/gamma/delta rules, closing branches, and using subsidiary
tableaux for procedure calls.

There are corpus-faithful versions that do not extend basic tableaux with
proof-list, Tab-k, or Tab-1 theorem reuse.

- The 2001 `IS(A)` line uses an ordinary semantic-tableaux proof predicate in
  its self-consistency axiom: no `SemPrf_IS(A)(code(0 = 1), p)` exists. This is
  the least complex first-pass mechanization and corresponds to a
  Level-0-minus contradiction-freedom target.
- The 2013 presentation of `ISD(A)` and `IS#_D(beta)` is parameterized by the
  deduction method `D`; its consistency-preservation statement allows `D` to be
  ordinary semantic tableaux or the Tab-1 generalization. Therefore Level-1 can
  also be approached first with `D = semantic-tableaux`, while Tab-1 remains a
  later stronger apparatus.

The 2005 and 2013 witnesses also discuss `Tab-k` or tab-list variants. These
are semantic tableaux plus a controlled cut or theorem-reuse rule for a bounded
formula class, especially the Pi-star-1 / Sigma-star-1 level. Proflog does not
currently have a theorem-reuse or cut layer. Therefore:

- a first SJAS profile should support ordinary semantic-tableaux reflection;
- an `IS(A)`-style first pass does not need Tab-1;
- a later profile that claims Tab-1 must implement the proof-list relation and
  enforce its intermediate theorem class restriction;
- documentation must name whether the reflected `D` is plain tableaux or
  Tab-1, because this changes what the self-consistency axiom says.

The profile must not promote the Law of Excluded Middle into a logical axiom
schema. The 2020 witness emphasizes that tableau treatment of excluded middle
as derived theorem rather than built-in logical axiom is not a harmless
presentation detail for self-referential consistency claims. Proflog's current
tableau kernel is therefore a better match than a Hilbert-style logical-axiom
encoding.

## Arithmetic Language

Willard's semantic-tableaux systems use a revised arithmetic language rather
than Peano arithmetic's ordinary function language.

The Type-A line recognizes addition as total but does not recognize
multiplication as a total function. Multiplication is available as a
three-place relation `Mult(x,y,z)`, whose graph can be expressed by a
Delta-star-0 formula over the weaker grounding language.

The U-grounding language used in the 2004, 2005, 2011, and 2013 witnesses has:

- constants for zero and one;
- relation symbols such as equality and order;
- non-growth or low-growth grounding functions such as predecessor,
  subtraction, division, maximum, logarithm, root, and bit-count;
- growth functions for addition and double in the Type-A/tableaux line;
- no multiplication function symbol.

In Proflog terms this should become a declared language with:

- constants: `zero`, `one`;
- functions: `pred/1`, `sub/2`, `div/2`, `max/2`, `log/1`, `root/2`,
  `count/2`, `add/2`, `dbl/1`;
- relations: `lt/2`, `leq/2`, `mult/3`, and proof-coding relations used by
  the reflected proof predicate.

The absence of `mul/2` as a function is not incidental. It is the semantic
boundary that keeps the profile Type-A rather than Type-M.

## Formula Classes

The profile needs a classifier for Willard formula classes:

- Delta-star-0: formulas over the U-grounding language whose quantifiers are
  bounded by U-grounding terms;
- Pi-star-1: universal closure of a Delta-star-0 matrix;
- Sigma-star-1: existential closure of a Delta-star-0 matrix;
- Level-1 consistency: no simultaneous proofs of a Pi-star-1 sentence and its
  negation under the selected deductive apparatus.

This is a separate classifier from `proflog.formula-profile/profile`, which is
currently a theorem-prover routing classification. A Willard classifier must
understand bounded quantifier syntax and the selected arithmetic vocabulary.

The frontend will eventually need explicit bounded quantifier forms, for
example:

```clojure
(forall<= [x t] body)
(exists<= [x t] body)
```

Until such syntax exists, a profile helper can desugar bounded quantification:

```clojure
(forall [x] (implies (leq x t) body))
(exists [x] (and (leq x t) body))
```

The classifier should preserve the bounded status before desugaring destroys
that information.

## Self-Reference And Proof Coding

Willard's self-consistency axiom is a fixed-point sentence. Its content is not
merely "there is no proof of 0 = 1" in all versions. For the Level-1 line it is
closer to:

```text
there are no proofs, from this very system under D, of both a Pi-star-1 sentence
and its negation.
```

The profile therefore needs object-language encodings for:

- formula codes;
- proof certificate codes;
- the selected axiom basis;
- the selected deduction apparatus;
- negation pairing or a relation saying that two formula codes are complements;
- `Prf(system, formula-code, proof-code)`;
- `PiStar1(formula-code)`.

The frontend may compute the initial self-reference/fixed-point syntax during
source translation. That is still on the accepted source-to-kernel boundary.
After translation, however, proof checking must be relational Proflog/kernel
work, not host-side proof validation.

This suggests an explicit boundary:

- allowed at translation time: parse SJAS source, build the finite axiom list,
  assign stable codes, construct the fixed-point AST;
- not allowed at proof time: use host Clojure to decide whether a proof code is
  valid, whether a formula is Delta-star-0/Pi-star-1, or whether a bounded
  arithmetic fact is true.

## Q Comparison And Axiom Membership

Existing Proflog examples do not use object-language axiom codes in the SJAS
sense.

- Robinson Q has stable host-side labels such as `:q1` through `:q7`, and the
  ordinary Q path conjoins Q's axioms into an implication antecedent. The Q
  profile path promotes selected Q principles to trusted theory rules. Neither
  path asks the object language to decide whether a proof step cites an allowed
  Q axiom.
- Pelletier examples carry a host-side `:axioms` vector that is conjoined with
  the negated theorem before kernel proof search. Again, there is no reflected
  predicate for axiom membership.
- Kernel proof terms contain tags such as `profiled`, `q-rewrite`, or
  `witness`, but those are evidence produced by the prover, not
  object-language formula identifiers.

SJAS is different because its self-consistency axiom quantifies over proofs
from this very system. A relational checker for
`tableau-proof(system, theorem-code, proof-code)` must inspect proof
certificates. If a certificate cites an axiom, the checker needs an
object-language relation such as:

```text
axiom-member(system, formula-code)
```

This is a new reflected-coding layer. It should be generated from the SJAS
axiom basis and exposed to the proof predicate as object-language data, not
used as a hidden host-side shortcut.

## Authoring Model

An SJAS programmer should not manually construct the fixed-point
self-consistency axiom. The intended authoring flow is a dedicated SJAS
frontend or builder:

```clojure
(sjas/system
  {:profile :willard-sjas-tableau0}
  (language
    (constants zero one)
    (functions [pred 1] [sub 2] [div 2] [max 2] [log 1]
               [root 2] [count 2] [add 2] [dbl 1])
    (relations [leq 2] [lt 2] [mult 3] ...))
  (beta
    ;; finite user-supplied Pi-star-1 axioms for the first demonstrator
    ...)
  (program
    ;; ordinary Proflog relation clauses over the SJAS language
    ...))
```

The builder would then:

1. generate Group-Zero and Group-1 from the selected profile;
2. validate and store the user beta axioms as Group-2;
3. reserve stable formula identifiers for Group-Zero through Group-2 and the
   future Group-3 formula;
4. generate the fixed-point `SelfCons0` or `SelfCons1` formula once;
5. add Group-3 to the reflected axiom basis;
6. generate object-language `axiom-member` facts/relations and proof-coding
   support;
7. expose a thin query API that proves user theorems from the generated SJAS
   basis.

Current Proflog has relation clauses but not a general arbitrary axiom-context
slot in compiled programs. The SJAS implementation therefore needs either:

- a generated system object carrying `:program`, `:axioms`, `:axiom-formula`,
  and reflected coding relations; or
- a query wrapper that conjoins the generated axiom formula with each user
  theorem, while still passing the reflected axiom membership data to the
  `tableau-proof` checker.

The first route is cleaner for tutorial use because it lets users write "a
program in SJAS" as one system, not as repeated manual implications. In either
route, the user writes beta axioms and Proflog clauses; the frontend supplies
the self-consistency axiom.

## Reflected Programs And Group-2 Scope

The user-supplied program must be classified by whether it is inside the
reflected SJAS or outside it.

The local Willard witnesses support this boundary. In Willard's 2001
`Self-Verifying Axiom Systems, the Incompleteness Theorem and Related
Reflection Principles`, Section 2 introduces `IS(A)` as four axiom groups, and
Group-3 states that there is no semantic-tableaux proof of `0 = 1` from
`IS(A)` itself. Appendix B makes the fixed-point mechanism more explicit by
defining `UNION(A)` as the union of Group-Zero, Group-1, and Group-2, then
checking proofs from that union plus the one further self-referential sentence.
In Willard's 2013/2014 `On the Significance of Self-Justifying Axiom Systems
from the Perspective of Analytic Tableaux`, Section 4 again presents `ISD(A)`
as four groups, and Group-3 refers to deduction method `D` applied to an axiom
system consisting of Group-0, Group-1, Group-2, and "this sentence" looking at
itself. The same paper's Definition 5.1 for finite `IS#_D(beta)` says that
replacing the infinite Group-2 schema with a finite beta set changes the "I am"
fragment of Group-3 in the obvious manner.

That last point is the key implementation rationale. If a Proflog SJAS program
clause is one of the facts/rules that `tableau-proof(this-system, theorem,
proof)` may cite through `axiom-member`, then it is part of the reflected axiom
basis. It should be compiled as finite Group-2 data, or as a named Group-2b
finite user extension, before Group-3 is generated. Changing such a clause
changes the set of proofs from "this system"; therefore it must change the
generated system identifier and the fixed-point `SelfCons0` or `SelfCons1`
formula.

If instead a user wants to reuse a fixed SJAS basis from ordinary Proflog code,
those surrounding clauses can be treated as an external application layer. They
may call into the SJAS query API, but they are not axioms of the self-justifying
system. The internal proof predicate must not cite them as `axiom-member`
entries, and `SelfCons0` / `SelfCons1` does not assert their consistency.

So the answer to "is the user-supplied program effectively an extension of the
Group-2 axioms?" is yes for the default "program in SJAS" mode. More precisely,
the user program is a finite reflected extension alongside beta, subject to the
same truth/formula-class restrictions needed for the chosen Willard profile. It
need not be called Group-2 if that would obscure Willard's original grouping;
`Group-2b` is a useful Proflog name for user program clauses that are reflected
as proper axioms. But semantically it plays the Group-2 role: it is part of the
proper axiom basis whose membership relation the proof predicate checks, and it
is included in the self-reference generated by Group-3.

## Profile Architecture

The profile should follow ADR-0048 through ADR-0052's proof-profile pattern:

- language metadata selects `:proof-profile :willard-sjas-tableau0` or
  `:proof-profile :willard-sjas-level1`;
- `proflog.proof-profile/prove-program*` dispatches to a new profile namespace;
- the profile binds `kernel/*theory-profile-closeo*` to a kernel-interleaved
  relation;
- proof terms identify the trusted profile step, for example:

```clojure
(profiled willard-sjas-tableau0
  (proof-code-check ...))

(profiled willard-sjas-level1
  (delta-star-0-close ...))

(profiled willard-sjas-level1
  (tab1-cut pi-star-1 ...))

(profiled willard-sjas-level1
  (selfcons1-reflection ...))
```

The first implementation should avoid a host preprocessor analogous to the
early Robinson-Q normalizer that ADR-0050 removed. All branch-relevant theory
steps should be miniKanren relations operating on the current branch state.

## Axiom Group Placement

The language declaration is only the signature. It declares constants,
functions, and relations, but it does not decide any arithmetic or proof-coding
truths. The SJAS builder must generate a named reflected axiom basis, and the
profile must make proof evidence show which basis is being reflected.

### Group-Zero

Group-Zero defines the initial constants and primitive U-grounding operations.
In Proflog:

- the symbols live in `u-grounding-language`;
- their behavioral equations or graph relations live in generated proper
  axioms and profile-local relational definitions;
- if a helper normalizes U-grounding terms operationally, it must appear as an
  auditable profile step rather than hidden host computation.

### Group-1

Group-1 is the finite grounding and coding prelude. It should include the
object-language axioms and relations needed for:

- equality and order facts over the selected numeral representation;
- Delta-star-0 arithmetic over the U-grounding primitives;
- syntax coding predicates such as `wff`, formula class recognition, and
  complement pairing;
- proof-certificate predicates used by the reflected deduction apparatus.

This group is where the predicates needed for coding logical statements as
SJAS-arithmetic terms primarily live. They are declared in the language but
defined as Proflog relations/axioms in Group-1.

### Group-2

Group-2 imports or simulates the external base theory's Pi-star-1 consequences.
The first implementation should avoid the full infinite `ISD(A)` schema and use
the finite `IS#_D(beta)` shape instead:

- beta is supplied as a finite vector of Pi-star-1 formulas;
- each beta formula is a proper axiom of the generated SJAS program;
- later work may add schema generation for a richer base theory.

### Group-3

Group-3 is the fixed-point self-consistency axiom. It should be generated by
the SJAS frontend/profile builder after Group-Zero through Group-2 have stable
codes, then added as a proper axiom of the same reflected program.

For the first `IS(A)`-style pass, Group-3 can state that there is no ordinary
semantic-tableaux proof of contradiction from this very axiom basis. For the
later Level-1 pass, Group-3 states that there are no proofs of both a
Pi-star-1 sentence and its complement under the selected `D`.

Group-3 must not live only in the kernel profile. The kernel profile may know
how to check or accelerate the proof predicate, but the self-consistency claim
itself is an object-language axiom of the SJAS.

## Implementation Slices

### Slice 1: Corpus-Derived Public Builders

Add a namespace such as `proflog.willard-sjas` exposing:

- `u-grounding-language`;
- `tableau0-profile-language`;
- `level1-profile-language`;
- term builders for U-grounding terms;
- relation/formula builders for `Mult`, bounded quantifiers, Delta-star-0,
  Pi-star-1, proof predicates, ordinary-tableau `SelfCons0`, and Level-1
  `SelfCons1`;
- constructors for finite `IS#_D(beta)`-style systems.

This slice is mostly data construction, but tests should still be red-green:

- the language contains no multiplication function symbol;
- a formula with only bounded quantifiers classifies as Delta-star-0;
- a universal closure of a Delta-star-0 matrix classifies as Pi-star-1;
- an unbounded existential under a universal is rejected as Pi-star-1;
- the generated `SelfCons0` or `SelfCons1` formula mentions the selected system
  id and proof apparatus.

### Slice 2: Relational Grounding Arithmetic

Implement relational operations for the U-grounding functions over a canonical
internal numeral representation. The public source may use named numerals or
U-grounded terms, but the profile should normalize through relations.

Tests should exercise forward, answer, and partial-synthesis modes, for example:

- `add(one, one) = two`;
- synthesize `x` from `add(one, x) = two`;
- prove that a closed `Mult(two, three, six)` graph fact holds through the
  Delta-star-0 definition;
- reject a false `Mult` graph fact.

This is the place to decide whether to reuse the existing relational arithmetic
adapter work, use Peano constructors, or introduce a profile-local binary
numeral. The decision should be performance-driven, but public answers must be
documented.

### Slice 3: Proof-Certificate Relations

Represent formulas and tableau proof certificates as ordinary object-language
data and write relational checkers for small certificates:

- `wff(code)`;
- `pi-star-1-code(code)`;
- `neg-pair(code-a, code-b)`;
- `tableau-proof(system, theorem-code, proof-code)`;
- `closed-branch(branch-code)`.

This is the highest-risk slice. It must start with tiny proof certificates that
are deliberately not optimized:

- a valid propositional branch closure;
- an invalid branch that remains open;
- a valid universal instantiation over a U-grounded term;
- a deliberately malformed proof code rejected by the checker.

No Clojure-side proof checker should be introduced. Clojure may construct test
data, but the proof predicate itself must be relational.

### Slice 4: First Plain-Tableau SJAS Demonstrator

Build the least complex `IS(A)`-style profile first:

- Group-Zero and Group-1 provide a tiny U-grounding/coding basis;
- Group-2 is finite and contains only the beta consequences needed by the
  demonstrator;
- Group-3 states ordinary semantic-tableaux contradiction-freedom for this
  generated system;
- tests prove that the self-consistency formula is present as an object axiom,
  that its proof predicate routes through relational proof-certificate
  checking, and that malformed proof certificates are rejected.

This stage is enough to begin studying executable SJAS-lang behavior without
first implementing theorem-list reuse.

### Slice 5: Limited Tab-1 Apparatus

Implement tab-list proof certificates for a bounded formula class:

- a proof list is a sequence of theorem/proof pairs;
- each proof may cite proper axioms or earlier list entries;
- intermediate theorem entries must classify as Pi-star-1 or Sigma-star-1;
- the final theorem is the queried target.

This is needed before claiming the implemented system is a faithful
ISD(A)/IS#_D(beta) profile rather than only a semantic-tableaux substrate.

### Slice 6: Level-1 SJAS Demonstrator

Build a finite `IS#_D(beta)`-style profile over a small beta:

- beta contains only explicit Pi-star-1 formulas over the U-grounding language;
- `SelfCons1(beta,D)` is generated by the frontend/profile builder;
- the program proves selected beta consequences;
- the program proves or directly contains its own Level-1 self-consistency
  sentence, with proof evidence naming the SJAS profile;
- bounded contradiction probes fail to find simultaneous Pi-star-1 and
  complement proofs under a documented fuel/time bound.

This is the minimum honest demonstration. It shows the object-language shape
and proof-route discipline without claiming that Proflog has mechanized
Willard's full consistency-preservation proof.

## Shortcomings To Preserve In Documentation

- Proflog can implement and exercise a finite SJAS profile, but the statement
  "the resulting system is in fact consistent" is a metatheorem. Passing bounded
  contradiction probes are useful evidence about the implementation, not a proof
  of Willard's metatheorem.
- A profile that simply treats `SelfCons1` as an axiom proves that one axiom has
  the intended self-referential content. It does not by itself prove the
  external Part-ii consistency condition.
- A profile without Tab-1 theorem reuse should not claim to implement the
  Tab-1 apparatus. It may still be a faithful ordinary-tableau `IS(A)` pass, or
  an `ISD(A)` / `IS#_D(beta)` pass whose reflected deduction method `D` is
  explicitly plain semantic tableaux.
- The Hilbert/theta-function line from the 2016 witness should be deferred. It
  uses a Hilbert-style apparatus, a theta primitive, and a conjectural
  assumption in the paper. It is less aligned with Proflog's current kernel than
  the Type-A tableau line.
- Formula coding and proof checking will likely be slow. The tests must record
  timings and keep expensive reflection probes in the extended suite.

## Proposed Initial Profile Name

Use two explicit profile names:

- `:willard-sjas-tableau0` for the first ordinary-tableau `IS(A)`-style
  demonstrator;
- `:willard-sjas-level1` for the later Level-1 `ISD(A)` / `IS#_D(beta)`
  demonstrator.

These names say three important things:

- it is Willard-style, not a generic arithmetic profile;
- it is an SJAS/reflection profile, not only a U-grounding arithmetic profile;
- it names whether the reflected consistency claim is ordinary-tableau
  contradiction freedom or Level-1 Pi-star-1/complement consistency.

## Relationship To Existing Proflog Work

The design should reuse:

- ADR-0010 frontend translation discipline;
- ADR-0023 and ADR-0026 profiled kernel interoperation;
- ADR-0048 through ADR-0052 proof-profile dispatch and kernel-interleaved
  theory-rule structure;
- ADR-0053 and ADR-0054 Robinson-Q example discipline, especially the
  distinction between axioms-as-assumptions and trusted theory profile rules;
- ADR-0057's requirement to distinguish host convenience from relational proof
  routes.

It should not reuse:

- the equality-fragment host engine as the proof route for SJAS reflection;
- whole-formula host preprocessing for arithmetic or proof predicates;
- test-specific evaluators that decide proof validity outside the kernel.

## Minimum Completion Standard For A Future Implementation ADR

A future implementation ADR should not exit until:

- the source-to-kernel boundary is documented with examples;
- the generated language lacks `mul/2` as a function and contains `mult/3` as a
  relation;
- every generated axiom is assigned to Group-Zero, Group-1, Group-2, or Group-3
  in documentation and proof evidence;
- Delta-star-0 and Pi-star-1 classifiers have positive and negative tests;
- U-grounding arithmetic has forward, answer, and partial-synthesis tests;
- proof-certificate relations accept valid and reject invalid miniature tableau
  proofs;
- the first ordinary-tableau demonstrator proves or exposes its intended
  self-consistency axiom and rejects malformed contradiction proofs;
- Tab-1 proof-list restrictions are enforced only if the ADR claims a Tab-1
  apparatus;
- the later Level-1 SJAS demonstrator proves its intended self-consistency
  statement;
- bounded contradiction probes and all focused timing results are recorded;
- the worked example explains exactly which part is a formal Proflog proof and
  which part remains Willard's external consistency-preservation metatheorem.
