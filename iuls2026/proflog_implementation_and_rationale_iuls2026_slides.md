# Proflog IULS 2026 Slide Source

## Slide 1: Proflog, its implementation and rationale

https://github.com/jpt4/proflog        IU Logic Seminar - 2026MAY27
James P. Torre, IV        jpt4@proton.me

## Slide 2: Contents

I: Proflog as proof-search programming
II: Fitting tableaus plus Procedure Call
III: Current implementation map
IV: Demonstrations: P1 and P2
V: SJAS motivation and future work

## Slide 3: Logic Programming

Permissive reading: programs are logical statements manipulated by code.
Strict reading: evaluation is the proof procedure of a named logic.
Proflog is strict in intent: a query result is witnessed by a tableau.
The proof object is not an afterthought; it is the computation trace.

## Slide 4: Semantic Tableaux

To prove T, build a closed tableau for not T.
Alpha rules extend one branch; beta rules split branches.
Gamma introduces reusable free proof variables.
Delta introduces rigid parameters.
A closed branch contains a contradiction or valid theory closure.

## Slide 5: From Tableaux to Proflog

Fitting adds a Procedure Call rule to first-order tableau proof search.
A defined atom R(t) opens a subsidiary tableau for R's clause body.
A negative atom not R(t) opens one for the negated body.
Clauses are biconditional in behavior, not Horn implications only.

## Slide 6: A Small Program

p(x) :- x = a.

query p(a): close the tableau for not p(a)       => succeeds
query p(b): close the tableau for p(b)           => fails
no closure on either side within bounds          => unresolved

## Slide 7: Source to Kernel

Frontend: pf/language, pf/proflog, pf/q, pf/run.
Compiler: source clauses -> relation entries with body and negated-body.
Kernel: prove-stateo closes branches with explicit branch state.
Query: query-status interleaves success and failure probes.

## Slide 8: Kernel State

fml and unexpanded: current formula and pending branch work.
lits: saved positive and negative atoms.
env: lexical substitution for bound variables.
sigma: explicit equality substitution.
neqs: delayed disequalities.
proof: constructor tree witnessing closure.

## Slide 9: Procedure Calls in Code

program/call-clauseo is relational lookup over compiled clauses.
Positive call: prove the compiled body in a subsidiary tableau.
Negative call: prove the precomputed NNF negated body.
Equality can later make a saved atom callable.

## Slide 10: Proof Objects

Proof search returns structured terms: split, conj, close, savefml, ...
Procedure evidence records pos-call, neg-call, and triggered calls.
Proof objects are used for tests, diagnostics, and SJAS reflection.
The implementation is therefore executable proof theory.

## Slide 11: P1 Program

Paper-equivalent Proflog:
even(x) <-
  x = 0 or exists y.(x = s(y) and odd(y))

odd(x) <-
  forall y.(even(y) => x != y)

## Slide 12: P1 Output

Run:
  evaluate-case :p1-even-0-succeeds
  evaluate-case :p1-odd-1-succeeds

Results:
  even(0)  => :succeeds, root neg-call-guarded-alt
  odd(s(0)) => :succeeds, root neg-call
  both carry proof-count 1

## Slide 13: P1 Proof Traces

even(0) trace:
  neg-call-guarded-alt > guarded-alt > guard-eq
  > decompose > guarded-seq-done

odd(s(0)) trace:
  neg-call > witness > conj > eq-step > par-bind
  > pos-call > split > free-close > univ > refl-close

## Slide 14: P2 Program

Paper-equivalent Proflog:
win(x) <- exists y.
  ((x = s(y) or x = s(s(y)))
   and not win(y))

One-clause Nim: remove one or two tokens.
Move logic stays inline, per Fitting's warning.

## Slide 15: P2 Output

Run:
  evaluate-case :p2-win-4-succeeds
  evaluate-case :p2-win-3-fails

Results:
  win(4) => :succeeds, root neg-call
  win(3) => :fails, root pos-call
  both carry proof-count 1

## Slide 16: P2 Proof Traces

win(4) trace:
  neg-call > once-univ > split > conj > neq-close
  > pos-call > eq-triggered-neg-call > free-close

win(3) trace:
  pos-call > witness > conj > savefml > split
  > eq-triggered-neg-call > pos-call > free-close

## Slide 17: Beyond the Paper

Proof-producing query API with bounded iterative deepening.
Answer export and residuals for open variables.
Explicit equality, disequality, and delayed-call machinery.
Opt-in profiles: Robinson Q, constructor recursion, Willard SJAS.

## Slide 18: Performance Discipline

core.logic makes the tableau kernel relational but search-sensitive.
Fast gate: lein test-proflog-fast.
Extended gate: lein test-proflog-extended.
Resource-heavy SJAS work uses focused var-by-var timing.
Performance work is subordinate to proof-rule correctness.

## Slide 19: Why SJAS Enters

Willard-style SJAS asks a system to reason about its own proofs.
Then implementation details can become mathematically relevant.
A shortcut through the host kernel may preserve theorem extension.
But self-reference can depend on proof shape, code size, and closure.

## Slide 20: SJAS Internalization

System, theorem, proof, and substitution codes are inspected as bytes.
Recent work removed host public-code byte projectors.
tableau-proof and subst-prf now use local proof-check relations.
Remaining work: more proof constructors, signature coding, tractability.

## Slide 21: Autarkic Formal Systems

Question: what parts of a formal system are determined internally?
Consistency, definability, decidability, interpretation, replication.
Proflog supplies an executable setting for intensional proof questions.
SJAS supplies the pressure test: proof machinery must account for itself.

## Slide 22: References

Fitting, Tableaus for Logic Programming, 1993.
Amin, leanTAP / alphaleanTAP line.
Willard, self-justifying axiom systems.
Project: github.com/jpt4/proflog
Support: FUTO Fellowship; Bloominglabs; Seth Frey, UC Davis.
AI disclosure: Codex GPT-5.4/5.5; Claude Opus 4.6/4.7.
