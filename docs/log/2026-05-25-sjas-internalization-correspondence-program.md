# SJAS Internalization and Proflog Correspondence Program

Date: 2026-05-25
Branch: `adr-0072-sjas-object-proof-machinery`

## Purpose

This note records the discussion that reframed the ADR-0072 "full
internalization" goal. Complete arithmeticization of the SJAS proof predicate is
still the strongest route, but the exchange clarified that calling Proflog's
tableau kernel is objectionable only insofar as it collapses deductive-apparatus
structure that is relevant to Willard-style self-justification. The resulting
program has three coordinated tracks: continue arithmeticization, determine the
relevant intensional requirements of the semantic-tableau apparatus, and prove a
correspondence between those requirements and the Proflog kernel if that
correspondence is adequate.

## Verbatim Exchange

### Host Kernel Proof Checking

User:

> Non-sjas-axiom proof predicates still validate decoded proof terms by calling kernel/prove-programo rather than checking proof-code trees object-level - Is this an issue for SJAS internalization of proof machinery?

Assistant:

> Yes. It is a central remaining issue for **full SJAS proof-machinery internalization**.
>
> `kernel/prove-programo` is better than the old generated registry shortcuts because it does not just look up a host-side proof target table. But it is still a host/kernel-level proof validator invoked during SJAS predicate evaluation. In the non-`sjas-axiom` branches, the profile decodes a proof code into a Proflog proof term, reconstructs the target formula, then asks the implementation kernel to validate that proof term.
>
> That means the reflective work is not yet being done by the SJAS object language. The SJAS predicate is still relying on the ambient prover implementation to decide whether the decoded proof is valid.
>
> So the current state is:
>
> - `sjas-axiom` citation paths are increasingly object/code driven.
> - Generated host registries have mostly been removed or isolated.
> - But generic proof certificates are still checked by the host kernel.
> - Therefore full arithmetic internalization is incomplete.
>
> For the goal to be satisfied, `kernel/prove-programo` in proof-predicate application needs to be replaced by an object-level proof-code checker: a relation over encoded formulas, encoded proof constructors, decoded system axioms, branch closure rules, substitution/equality rules, etc. That checker should verify the proof tree structurally rather than handing the decoded term to the host prover.
>
> This is not necessarily a current soundness bug if the host kernel is trusted. It is a **foundational/internalization gap**: SJAS is still outsourcing part of reflection to the implementation rather than carrying it in its own arithmetized object machinery.

### Evaluation by the Proflog Kernel

User:

> a relation over encoded formulas, encoded proof constructors, decoded system axioms, branch closure rules, substitution/equality rules, etc. - Would this relation itself be evaluated by the Proflog kernel?

Assistant:

> Yes. In this implementation, the relation would still be **evaluated by the Proflog kernel**.
>
> The distinction is not "no host interpreter runs anything." Some interpreter/kernel always runs the object program. The distinction is:
>
> - Bad: SJAS predicate says, in effect, "decode this proof code into a host Proflog proof term, then call the host prover's `kernel/prove-programo` to decide validity."
> - Better / intended: SJAS predicate expands to first-order/object-language relations like `proof-step-code(...)`, `formula-code(...)`, `axiom-member(...)`, `branch-closed(...)`, etc., and the Proflog kernel merely evaluates those relations as ordinary object-level program clauses.
>
> So the kernel remains the evaluator, but it should not be used as an oracle for "is this decoded host proof term valid?" The proof checking logic should be represented inside the SJAS object language, as relations over arithmetized codes.
>
> This is analogous to running Peano arithmetic formulas in a theorem prover: the prover evaluates/searches, but the arithmetic statement is still in the object language. The current `kernel/prove-programo` path is more like escaping from the object arithmetic into the meta-level proof engine.

### Object-Language Relations

User:

> SJAS predicate expands to first-order/object-language relations - elaborate on this, please.

Assistant:

> By "expands to first-order/object-language relations," I mean the SJAS proof predicate should reduce to ordinary Proflog goals over encoded data, not call back into the implementation prover as a special validator.
>
> Current non-`sjas-axiom` shape is roughly:
>
> ```clojure
> decode proof-code -> decoded Proflog proof term
> decode theorem-code -> target formula
> reconstruct axiom antecedent from system-code
> (kernel/prove-programo target ... decoded-proof)
> ```
>
> That last line is the escape hatch. The SJAS predicate has decoded enough to hand the problem to the host proof kernel.
>
> The intended shape would be more like:
>
> ```clojure
> sjas-proof-check(system-code, theorem-code, proof-code)
> ```
>
> where `sjas-proof-check` is defined by ordinary relations such as:
>
> ```text
> proof-code-constructor(proof-code, ctor, children)
> formula-code-constructor(theorem-code, formula-shape, parts)
> axiom-member(system-code, theorem-code)
> tableau-step-valid(system-code, branch-code, step-code, next-branch-code)
> branch-closed(branch-code)
> subst-code(source-code, target-code)
> neg-pair(left-code, right-code)
> wff(formula-code)
> ```
>
> So a proof of an axiom citation would be checked by a relation like:
>
> ```text
> proof-check(S, F, P) :-
>   proof-code-is-axiom(P),
>   axiom-member(S, F).
> ```
>
> A conjunction proof might be checked by decomposing the target formula and proof code:
>
> ```text
> proof-check(S, and(A, B), conj(PA, PB)) :-
>   proof-check(S, A, PA),
>   proof-check(S, B, PB).
> ```
>
> A tableau closure rule would be checked by relations over encoded branch/literal/formula data, for example:
>
> ```text
> branch-closed(B) :-
>   branch-contains(B, L),
>   branch-contains(B, not(L)).
> ```
>
> The important point is that `S`, `F`, `P`, branches, formulas, literals, proof constructors, variable indices, and substitutions are all **first-order terms/codes inside SJAS**. The Proflog kernel still runs the relation, but it runs it the same way it runs any user-defined logic relation: by unification, search, and clause application. It is not asked, "please validate this decoded host proof term using your own proof checker."
>
> So "object-language relation" does not mean no implementation engine is involved. It means proof validity is represented as data and rules inside the formal system being evaluated, rather than as a meta-level host call.

### Interrupted Partial Question

User:

> If the Proflog proof kernel implements the tableau deductive apparatus re

The turn was intentionally aborted before a substantive answer.

### Kernel Shortcut and the Self-Justification Invariant

User:

> SJAS calling out to the Proflog proof kernel is an opportunity for contemplation of what degree of internalization is required to guarantee the self-justification invariant: the proof predicate simulates the deductive apparatus, and so a complete internalization would be an implementation, via arithmetic operations, of that apparatus. For IS#_D(beta), the deductive apparatus is the tableau theorem proving proof procedure. If Proflog implements tableau in the way that SJAS specifies (or if the ways in which Proflog differs, e.g. in implementation details, do not violate the requisite invariants), then decoding SJAS Godel codes to Proflog terms and calling out to the Proflog kernel does short circuit that internalized simulation, but does it violate any invariant thereby? For example, if the system is self-justifying the simulated deduction ought fail to close the trees of certain proofs; if Proflog also does not close those trees for the proofs of the same theorems, then is that enough to demonstrate self-justification, or must the internalization of the deductive apparatus, implemented atop arithmetic, be evaluated to justifiably assert that property?

Assistant:

> Short answer: it is **evidence**, but not enough for the full internalization claim.
>
> If Proflog faithfully implements the same tableau apparatus `D`, then calling `kernel/prove-programo` may preserve the **extensional behavior** you care about on tested inputs: the same proof codes close or fail to close the same theorem trees. That can be a useful meta-level demonstration that the encoded system behaves like `IS#_D(beta)`.
>
> But SJAS self-justification is stronger than "our host implementation also fails to prove contradiction." It requires the proof predicate itself to be an arithmetized simulation of `D` inside the object machinery. The self-consistency statement is about that internal predicate:
>
> ```text
> not exists p. Proof_D(system_code, contradiction_code, p)
> ```
>
> If `Proof_D` is implemented by decoding into host terms and calling Proflog's kernel, then the object system is no longer witnessing the deductive apparatus through its own arithmetic relations. It is using the meta-level prover as an oracle.
>
> The key distinction is:
>
> - **Operational adequacy:** Proflog and SJAS agree on the relevant proof outcomes.
> - **Internalization adequacy:** SJAS represents and evaluates the proof-checking relation over Godel codes using its own object-level arithmetic and syntactic relations.
>
> For a convincing self-justification invariant, operational adequacy is not enough unless you also prove something like:
>
> ```text
> For every relevant system code S, formula code F, and proof code P:
>   ProflogKernelAccepts(decode(S), decode(F), decode(P))
>   iff
>   SJAS-Arithmetic-ProofPredicate(S, F, P)
> ```
>
> Without that equivalence, the Proflog kernel call is a trusted external bridge. It may be correct, but it is not itself the SJAS formalized deductive apparatus.
>
> So I would classify the current `kernel/prove-programo` path as:
>
> - **Not necessarily a soundness bug**, assuming Proflog's tableau implementation is correct.
> - **A real internalization gap**, because the proof predicate is not yet fully arithmetic/object-language.
> - **Acceptable as an implementation-stage witness**, but not enough to claim the full ADR-0072 endpoint.
>
> The invariant can be preserved extensionally if Proflog exactly matches `D`; the stronger self-justification claim requires either internal proof checking or a formal equivalence theorem connecting the host kernel to the internal arithmetic relation.

### Relevant Intensional Correspondence

User:

> Yes, calling the Proflog kernel is only an internalization inadequacy if the short-circuting/collapsing the virtualization of the deductive apparatus is not sound. The kernel is sound if the above bidirectional correspondence can be proven, and it would be preferable _if that correspondence does not violate the intensional guarantees required by SJAS_ - SJAS illuminates that intensional behavior, the structure and size of proofs, affects what theorems can be proven, when those theorems self-reference over the deductive apparatus. One might speculate Proflog acceptance could be put into correspondence with SJAS-arithmeticized-deduction acceptance via some other mechanism(s) than proof trees not closing, which would fail to fully capture the computational lessons SJAS teaches to logic.

Assistant:

> Yes. That is the right distinction.
>
> Calling the Proflog kernel is not automatically "bad" just because it is host-side. It becomes an internalization problem if the call collapses distinctions that SJAS needs to preserve. SJAS is not only concerned with the extension:
>
> ```text
> does D prove F from S?
> ```
>
> It is also concerned with the **intensional representation of the deduction process**:
>
> ```text
> what counts as a proof object?
> how large is it?
> how is each proof step encoded?
> which proof-search/tree-closing operations are available?
> what formulas can the system state about that exact proof predicate?
> ```
>
> That intensional layer matters because the self-reference is over the proof predicate itself. If Proflog acceptance is merely extensionally equivalent on theorem outcomes but differs in proof-object structure, proof length/size accounting, proof-step granularity, branch closing behavior, substitution representation, or search admissibility, then the self-justification phenomenon may not be the same phenomenon.
>
> So the adequacy condition is stronger than:
>
> ```text
> Proflog proves F iff SJAS_D proves F
> ```
>
> It needs something closer to:
>
> ```text
> Proflog accepts proof object P for F
> iff
> arithmetized SJAS_D proof-checker accepts code(P) for code(F)
> ```
>
> plus preservation of the relevant intensional measures:
>
> ```text
> proof constructors correspond
> proof size/length is bounded or translated predictably
> branch expansion/closure rules correspond
> substitution and equality operations correspond
> formula/proof/code encodings correspond
> the self-referential proof predicate denotes the translated apparatus
> ```
>
> A weaker theorem-level correspondence might demonstrate that the external prover is sound for the same logic, but it may erase the computational facts SJAS is exploiting. In particular, if self-justification depends on which proof trees can be closed under a concrete encoded proof predicate, then replacing that predicate with "whatever the host kernel accepts" risks changing the object of self-reference.
>
> So I would refine the status as:
>
> - `kernel/prove-programo` is not necessarily semantically unsound.
> - It is inadequate for the strongest ADR-0072 claim unless we prove an intensional correspondence, not only theorem equivalence.
> - The safest route remains an arithmetic/object-level checker for the relevant tableau proof objects.
> - A potentially acceptable alternative would be a rigorous bridge theorem showing that Proflog's accepted proof objects translate to and from SJAS's arithmetized proof objects while preserving the proof-size and rule-structure invariants SJAS relies on.
>
> That last bridge theorem could justify the shortcut. But absent it, the shortcut is a trusted meta-level collapse of the deductive virtualization, and it weakens the self-justification claim.

### Refined Three-Track Program

User:

> Emphasis on _relevant_ intensional measures: if a measure is not relevant, it need not be preserved. Thus, each intensional aspect must first be determined as relevant of not, and then a correspondence proven over the relevant aspects (if any - SJAS may be robust over significant intensional variation, and only the aspects of tableau deduction specified in the literature, i.e., that a proof is structured as a tree that branches when the parent node contains certain types of formulae, are necessary) while also proving the irrelevancies of the others (if any). The current goal of "full internalization" thus floriates into three: 1) continue attempting complete arithmeticization of the SJAS proof machinery (proof predicate et al.), such that they are tractable and can actually be evaluated by the Proflog runtime (including potentially improving the performance of Proflog, or the performance of core.logic). 2a) Determine what aspects of the tableau deductive apparatus are necessary for self-justification to obtain. My suspicion, which you should refute or corroborate, is that only the structure induced by the tableau method is relevant; compared to Tab-List and Hilbert methods, the size of the proof tree for a theorem, as induced by the structural rules of the proof method, are what matter, not the mechanism of how any given rule is selected for application, nor how it in fact gets applied, so long as, when applied, it results in the size SJAS requires. 2b) Provisional on (2a), prove the correspondence between the requirements of the SJAS specified deductive apparaus, and the Proflog kernel.

Assistant:

> Yes. I would split the goal exactly along those lines, with one refinement: "full internalization" is one sufficient route, but not the only conceivable route to a justified SJAS claim.
>
> Your suspicion is plausible but needs proof, not assumption.
>
> For `IS#_D(beta)`, the relevant object is not "whatever theorem relation is extensionally equivalent." It is a deduction predicate for a specified `D`. If `D` is semantic tableau deduction, then the relevant intensional structure likely includes:
>
> - proof objects are tree-shaped, not Hilbert-style lists;
> - branching follows tableau decomposition rules;
> - closure is by complementary literals / permitted equality-theory closure;
> - quantifier/witness/substitution rules match the selected tableau variant;
> - proof encoding is natural enough to preserve Willard's size discipline, not compressed/hash-like;
> - the self-consistency sentence quantifies over that proof predicate.
>
> Likely irrelevant, if proven irrelevant:
>
> - search order;
> - rule-selection scheduling;
> - memoization/caching;
> - whether the runtime uses core.logic, explicit recursion, or another evaluator;
> - concrete host data representation;
> - proof term cosmetic layout, provided it translates to the same tree/rule structure with bounded overhead.
>
> The risk is in the middle cases. "How a rule gets applied" is irrelevant only if it does not change the accepted proof objects in a way SJAS cares about. If Proflog's kernel has derived closure rules, equality shortcuts, profile-specific oracle steps, different quantifier instantiation policy, or proof certificates that compress many tableau nodes into one accepted step, then those are not obviously irrelevant. They may change the proof-size/tree-shape facts that make semantic-tableau SJAS behave differently from Hilbert systems.
>
> So I would state the refined program as:
>
> 1. **Arithmeticization Track**
>    Continue implementing the SJAS proof predicate as arithmetic relations over formula/proof/system codes. This is the strongest and cleanest evidence, but may require runtime/core.logic performance work.
>
> 2. **Relevance Track**
>    Determine which intensional properties of Willard's selected semantic-tableau apparatus are necessary for self-justification. Your hypothesis is: tableau-induced tree structure and size growth matter; search mechanics do not. I think that is likely right, but incomplete until equality, substitution, quantifier, closure, and proof encoding compression are classified.
>
> 3. **Correspondence Track**
>    Prove Proflog kernel acceptance corresponds to the relevant SJAS tableau predicate:
>    ```text
>    ProflogAccepts(P, S, F)
>    iff
>    SJAS_TableauProof(code(P), code(S), code(F))
>    ```
>    with preservation of the relevant proof-tree and proof-size measures, and explicit proofs that ignored implementation details are irrelevant.
>
> That would let us justify the current kernel shortcut in a principled way. Without tracks 2 and 3, the shortcut remains a pragmatic implementation bridge.

### Literature and Goal Prompt Request

User:

> > then the relevant intensional structure likely includes - while a review of the literature should be the foundation of this, additional reasoning may be necessary should Willard not be sufficiently explicit or complete in his specification. Log the above exchange, including that discussion leading to "SJAS calling out to the Proflog kernel is an opportunity for contemplation", and thence to here, verbatim; summarize it for future reference. The new goal will be pursue in concert the refined program, recognizing the dependence of (3) on (2); please compose a goal prompt with all requisite detail for your future use.

## Summary for Future Reference

The previous ADR-0072 formulation treated `kernel/prove-programo` inside
`tableau-proof/3` and `subst-prf/4` as a straightforward internalization gap.
The exchange refined this: a host-kernel call is problematic for SJAS only if it
collapses or changes deductive-apparatus structure that is relevant to the
self-justification invariant. The Proflog kernel can remain the evaluator of
relations; the question is whether the proof predicate denotes the relevant
semantic-tableau apparatus, either by direct arithmeticization or by a proved
correspondence.

The resulting research/implementation program has three coupled tracks and one
speculative reciprocal track:

1. **Arithmeticization.** Continue replacing host-side registries and staged
   decoders with object-level arithmetic relations over system, formula,
   substitution, and proof codes. Make those relations tractable in the Proflog
   runtime, including performance work in Proflog or core.logic if necessary.

2. **Relevance Analysis.** Determine which intensional features of Willard's
   selected semantic-tableau deductive apparatus are necessary for
   self-justification. Literature review is the foundation, but additional
   reasoning is required where Willard is underspecified. The working hypothesis
   is that tableau-induced tree structure and size growth are relevant, while
   search order and rule scheduling are likely irrelevant if they do not alter
   accepted proof trees or the size discipline. This hypothesis must be
   corroborated or refuted, not assumed.

3. **Correspondence.** Only after the relevant features are classified, prove
   that Proflog kernel acceptance corresponds to the SJAS-specified deduction
   predicate over those features, and test the implementation against that
   correspondence. The proof must preserve relevant proof-tree and proof-size
   properties and must prove the irrelevance of ignored implementation details;
   the tests must provide operational evidence that the code executes according
   to the proved correspondence.

2c. **Proflog-as-D Formalization.** Speculatively formalize the Proflog proof
   kernel itself as a deductive apparatus `D_Proflog`, then determine whether
   Willard-style SJAS results can be stated and proved for
   `IS#_{D_Proflog}(beta)`. This is not a substitute for Track 2b unless the
   implementation is abstracted into a stable formal calculus, the relevant
   proof-size/tree invariants are supplied, and the literature proof
   obligations are adapted to that calculus.

The correspondence track depends on the relevance track. A theorem-level
equivalence is not enough if it loses proof-object structure, proof size, branch
shape, substitution/equality behavior, or another feature later found relevant
to the SJAS self-reference. Conversely, if a feature is proven irrelevant, it
need not be preserved. The speculative Track 2c asks whether the selected
deductive apparatus could be a formalized Proflog calculus rather than
Willard's original semantic-tableau presentation; it still must conserve the
invariants used by the SJAS arguments.

## Goal Prompt for Future Use

```text
Objective: Pursue the refined ADR-0072 SJAS internalization program in three
coordinated tracks, recognizing that the correspondence track depends on the
relevance analysis track.

Context:
- The original ADR-0072 goal was to internalize SJAS proof machinery as
  object-level arithmetic, eliminating host-side formula/proof registries and
  kernel predicate host decoding except source preprocessing.
- Current Proflog implementation has removed many generated registry shortcuts,
  but non-sjas-axiom proof predicates still decode SJAS proof codes to Proflog
  proof terms and call `kernel/prove-programo`.
- That shortcut is not automatically unsound. It is inadequate only if it
  collapses deductive-apparatus structure relevant to Willard-style
  self-justification.
- For `IS#_D(beta)`, `D` is the selected semantic-tableau deductive apparatus.
  The self-justification invariant concerns a proof predicate over that
  apparatus, not merely extensional theorem equivalence.

Track 1: Arithmeticization
- Continue implementing SJAS proof machinery as arithmetic/object-level
  relations over encoded system, formula, substitution, and proof objects.
- Remove or strictly isolate remaining host-side decoding such as
  `ground-formal-code-term`, staged compact proof theorem decoding, staged
  substitution decoding, and proof-code validation via `kernel/prove-programo`.
- Keep all implementation work red-green: tests must fail before each retained
  change and pass after.
- If direct arithmeticization is not tractable, investigate and improve Proflog
  or core.logic performance, or design bounded proof/code relations that
  preserve the required SJAS properties.

Track 2a: Relevance Analysis
- Review Willard/SJAS literature and project notes to identify which
  intensional aspects of the semantic-tableau apparatus are necessary for
  self-justification.
- Classify each aspect as relevant, irrelevant, or unresolved. Candidate
  aspects include:
  - tree-shaped proof object rather than Hilbert/list proof;
  - tableau branching induced by formula class/decomposition rules;
  - proof-tree size and natural encoding size discipline;
  - branch closure conditions;
  - quantifier, witness, substitution, and equality handling;
  - formula/proof-code encoding and compression limits;
  - rule-selection order and scheduling;
  - host representation details, caching, memoization, and runtime search
    strategy.
- Corroborate or refute the working hypothesis: the tableau-induced tree
  structure and size growth are relevant; rule-selection mechanics and runtime
  scheduling are irrelevant when they preserve the same accepted proof trees and
  size bounds.
- Where Willard is not explicit enough, supply explicit reasoning and record the
  assumption, argument, and residual uncertainty.

Track 2b: Proflog Correspondence
- Only after Track 2a identifies the relevant aspects, prove and test a
  correspondence between the SJAS-specified semantic-tableau proof predicate and
  Proflog kernel acceptance.
- Target statement:
    ProflogAccepts(P, S, F)
    iff
    SJAS_TableauProof(code(P), code(S), code(F))
  with preservation of every Track-2a-relevant measure and proof of
  irrelevance for ignored implementation details.
- Supply a proper proof of the correspondence, and also supply operational
  tests/evidence that the implemented code executes according to the proved
  correspondence.
- Do not treat theorem-level equivalence alone as sufficient if proof-object
  structure, proof size, tree shape, equality/substitution behavior, or encoding
  discipline is relevant.
- If a rigorous correspondence is established, it may justify the current
  `kernel/prove-programo` shortcut as a theorem-backed virtualization rather
  than an unjustified meta-level collapse.

Track 2c: Proflog-as-D Formalization
- Speculatively formalize the Proflog proof kernel as a candidate deductive
  apparatus `D_Proflog`.
- Define Proflog proof states, branch agendas, saved literals, environments,
  proof variables, rigid parameters, equality substitutions, disequality
  stores, reflected procedure calls, profile theory rules, proof certificates,
  and any fuel/search parameters in a mathematical semantics independent of
  accidental implementation details.
- Determine whether Willard-style SJAS results can be adapted to
  `IS#_{D_Proflog}(beta)`, including the self-consistency statement that
  quantifies over the Proflog-formalized proof predicate.
- Supply a proof-code and proof-size measure for Proflog certificates that
  satisfies the relevant SJAS lower-bound/anti-compression requirements, or
  prove a replacement measure adequate for the adapted argument.
- Classify Proflog-specific features as admitted primitives, bounded macros,
  irrelevant scheduler/runtime details, or excluded features.
- Treat this as a possible future route, not as permission to identify SJAS
  with whatever the current implementation happens to accept.

Documentation obligations:
- Maintain `LOG.md` as the chronological spine and put detailed analysis under
  `docs/log/`.
- If the refined program changes ADR-0072's accepted exit criteria, update
  ADR-0072 or add a successor ADR before implementation.
- Record failed probes and performance limits, especially when direct
  arithmeticization becomes intractable.

Verification obligations:
- For implementation slices, run focused red-green tests first.
- Before committing semantic changes, run `lein test-proflog-fast`,
  `lein test-proflog-extended`, and `lein test-proflog-sjas`.
- Before claiming completion, audit every remaining host-side proof/coding path
  and every relevant correspondence requirement.
```
