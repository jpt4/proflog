# Proflog, its implementation and rationale

Lecture notes for the IULS 2026 slide deck.

## 1. Proflog, its implementation and rationale

Slide text:

- https://github.com/jpt4/proflog        IU Logic Seminar - 2026MAY27
- James P. Torre, IV        jpt4@proton.me

Speaker notes:

- Open with the claim that Proflog is a useful meeting point between logic programming and proof theory: the evaluator is meant to be a deductive apparatus, not merely a host-language search routine.
- The concrete implementation is a Clojure/core.logic greenfield track for Melvin Fitting's tableau-based Proflog.

## 2. Contents

Slide text:

- I: Proflog as proof-search programming
- II: Fitting tableaus plus Procedure Call
- III: Current implementation map
- IV: Demonstrations: P1 and P2
- V: SJAS motivation and future work

Speaker notes:

- Use this as the audience contract: first motivate the language, then show the deductive mechanism, then identify where the current code faithfully follows Fitting and where implementation choices enter.

## 3. Logic Programming

Slide text:

- Permissive reading: programs are logical statements manipulated by code.
- Strict reading: evaluation is the proof procedure of a named logic.
- Proflog is strict in intent: a query result is witnessed by a tableau.
- The proof object is not an afterthought; it is the computation trace.

Speaker notes:

- The intent notes distinguish logic programming from arbitrary symbolic templating over logical syntax. This matters because the project later asks what the computational equivalent of a logical system is.
- A Proflog implementation is interesting only if its operational behavior can be related back to the deductive apparatus it claims to execute.

## 4. Semantic Tableaux

Slide text:

- To prove T, build a closed tableau for not T.
- Alpha rules extend one branch; beta rules split branches.
- Gamma introduces reusable free proof variables.
- Delta introduces rigid parameters.
- A closed branch contains a contradiction or valid theory closure.

Speaker notes:

- This is the basic tableau discipline underlying both Fitting's paper and Amin's leanTAP line. The important visual idea is a tree: some rules add work to the current branch, while beta rules branch.
- A query succeeds only when the relevant tableau closes.

## 5. From Tableaux to Proflog

Slide text:

- Fitting adds a Procedure Call rule to first-order tableau proof search.
- A defined atom R(t) opens a subsidiary tableau for R's clause body.
- A negative atom not R(t) opens one for the negated body.
- Clauses are biconditional in behavior, not Horn implications only.

Speaker notes:

- This is the conceptual bridge from theorem proving to programming. Procedure calls are not Prolog resolution steps; they are tableau steps that ask a new proof obligation about the clause body.
- Negative calls are therefore classical, not negation-as-failure.

## 6. A Small Program

Slide text:

- p(x) :- x = a.

- query p(a): close the tableau for not p(a)       => succeeds
- query p(b): close the tableau for p(b)           => fails
- no closure on either side within bounds          => unresolved

Speaker notes:

- Use the one-clause example from the README. The query API probes both semidecision directions: success is a closed tableau for the negated query; failure is a closed tableau for the query itself.
- The implementation also reports inconsistent if both closures are found.

## 7. Source to Kernel

Slide text:

- Frontend: pf/language, pf/proflog, pf/q, pf/run.
- Compiler: source clauses -> relation entries with body and negated-body.
- Kernel: prove-stateo closes branches with explicit branch state.
- Query: query-status interleaves success and failure probes.

Speaker notes:

- Point to the exact code crossover: proflog.frontend builds the public surface; proflog.language validates and compiles; proflog.program provides relational clause lookup; proflog.kernel performs tableau closure; proflog.query exposes the user-facing status probes.

## 8. Kernel State

Slide text:

- fml and unexpanded: current formula and pending branch work.
- lits: saved positive and negative atoms.
- env: lexical substitution for bound variables.
- sigma: explicit equality substitution.
- neqs: delayed disequalities.
- proof: constructor tree witnessing closure.

Speaker notes:

- The current kernel makes state explicit that Fitting's presentation can leave implicit. This is a central engineering decision: equality and procedure calls interact through saved literals, so branch state must be inspected after unification and disequality updates.

## 9. Procedure Calls in Code

Slide text:

- program/call-clauseo is relational lookup over compiled clauses.
- Positive call: prove the compiled body in a subsidiary tableau.
- Negative call: prove the precomputed NNF negated body.
- Equality can later make a saved atom callable.

Speaker notes:

- The Procedure Call rule appears concretely in proflog.kernel as pos-call, neg-call, and equality-triggered variants. The saved-call path is important: whether a call becomes ground before or after an equality step should not change completeness.

## 10. Proof Objects

Slide text:

- Proof search returns structured terms: split, conj, close, savefml, ...
- Procedure evidence records pos-call, neg-call, and triggered calls.
- Proof objects are used for tests, diagnostics, and SJAS reflection.
- The implementation is therefore executable proof theory.

Speaker notes:

- Stress that the proof object is not decorative. It is the artifact that lets the implementation be audited against Fitting's rules and later be encoded for self-justifying axiom-system work.

## 11. Demo I: Fitting P1

Slide text:

- even(x) <- x = 0 or exists y.(x = s(y) and odd(y))
- odd(x)  <- forall y.(even(y) => x != y)

- Implemented cases:
- even(0) succeeds; odd(s(0)) succeeds; odd(0) fails.

Speaker notes:

- This is Fitting's original forall-based even/odd P1 shape, not merely the simplified mutual recursion. In the current catalog it lives in proflog.fitting-programs/p1-program, with direct tests over success and failure cases.

## 12. Demo II: Fitting P2

Slide text:

- win(x) <- exists y.((x = s(y) or x = s(s(y))) and not win(y))

- One-clause Nim: remove one or two tokens.
- The body keeps move logic inline, per Fitting's warning.
- win(4) succeeds; win(3) fails.

Speaker notes:

- P2 demonstrates recursive classical negation. The implementation keeps the move relation inline because Fitting warns that factoring it into an auxiliary relation changes the program behavior under the Procedure Call discipline.

## 13. Beyond the Paper

Slide text:

- Proof-producing query API with bounded iterative deepening.
- Answer export and residuals for open variables.
- Explicit equality, disequality, and delayed-call machinery.
- Opt-in profiles: Robinson Q, constructor recursion, Willard SJAS.

Speaker notes:

- Make clear that these are implementation layers around the deductive core. They are useful, but they must remain accountable to the logic. This theme recurs in the SJAS section, where shortcuts become suspect.

## 14. Performance Discipline

Slide text:

- core.logic makes the tableau kernel relational but search-sensitive.
- Fast gate: lein test-proflog-fast.
- Extended gate: lein test-proflog-extended.
- Resource-heavy SJAS work uses focused var-by-var timing.
- Performance work is subordinate to proof-rule correctness.

Speaker notes:

- The current development practice separates semantic regressions from deep synthesis and recursive probes. For this talk, the point is that tractability is a real engineering problem, but it should not obscure whether the intended proof relation has been implemented.

## 15. Why SJAS Enters

Slide text:

- Willard-style SJAS asks a system to reason about its own proofs.
- Then implementation details can become mathematically relevant.
- A shortcut through the host kernel may preserve theorem extension.
- But self-reference can depend on proof shape, code size, and closure.

Speaker notes:

- This is the transition from Proflog as a language implementation to Proflog as an object of proof-theoretic scrutiny. If a proof predicate talks about the deductive apparatus, replacing that apparatus with a host callback must be justified, not assumed harmless.

## 16. SJAS Internalization

Slide text:

- System, theorem, proof, and substitution codes are inspected as bytes.
- Recent work removed host public-code byte projectors.
- tableau-proof and subst-prf now use local proof-check relations.
- Remaining work: more proof constructors, signature coding, tractability.

Speaker notes:

- Summarize the current branch without overclaiming. The project has moved proof predicates away from direct kernel validation and toward arithmetized object-language checks, but full arithmetic internalization is not finished.

## 17. Autarkic Formal Systems

Slide text:

- Question: what parts of a formal system are determined internally?
- Consistency, definability, decidability, interpretation, replication.
- Proflog supplies an executable setting for intensional proof questions.
- SJAS supplies the pressure test: proof machinery must account for itself.

Speaker notes:

- Tie the talk back to the broader research program. The computational lesson is that equivalence at the theorem level may be too weak when the system can encode statements about the proof procedure itself.

## 18. References

Slide text:

- Fitting, Tableaus for Logic Programming, 1993.
- Amin, leanTAP / alphaleanTAP line.
- Willard, self-justifying axiom systems.
- Project: github.com/jpt4/proflog
- Support: FUTO Fellowship; Bloominglabs; Seth Frey, UC Davis.
- AI disclosure: Codex GPT-5.4/5.5; Claude Opus 4.6/4.7.

Speaker notes:

- Close with references and disclosure. Mention that the phrase 'animate literature' is apt here: the implementation is a way of making the proof-theoretic literature executable and then interrogating the gaps.
