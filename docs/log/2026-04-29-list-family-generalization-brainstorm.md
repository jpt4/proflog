# 2026-04-29 List-Family Kernel Generalization Brainstorm

## Context

ADR-0030 closed a narrow set of two-step ground list examples, but the fuller
matrix showed that the prover still does not generalize over the append/reverse
families. The follow-up discussion asked for possible generic remedies, with
research as needed, and then emphasized that source programs may be transformed
to intermediate forms that are easier for the proof kernel to evaluate.

## Research Signals

- SLG tabling and XSB-style execution suggest reusing variant subgoals and
  answers instead of rediscovering the same recursive frontier:
  <https://www.sciencedirect.com/science/article/pii/S0743106600000054>
- SLT-resolution is relevant because it combines tabling with top-down
  goal-directed proof search:
  <https://link.springer.com/article/10.1023/A%3A1020116927466>
- Needed narrowing for constructor systems is a useful analogue for delaying
  or selecting recursive descent by demanded constructor positions:
  <https://www.cambridge.org/core/journals/theory-and-practice-of-logic-programming/article/rewriting-and-narrowing-for-constructor-systems-with-calltime-choice-semantics1/E75114F48BD6598729AD54F7281C0EDD>
- Magic-set rewriting is a source-to-IR transformation pattern for propagating
  query demand into recursive programs:
  <https://colab.ws/articles/10.1145/6012.15399>
- Constraint logic programming remains relevant as the design model for keeping
  equality, disequality, constructor shape, and delayed calls explicit rather
  than hidden in host-side projection:
  <https://research.ibm.com/publications/constraint-logic-programming>
- Answer subsumption is a later option for keeping only better canonical
  recursive frontier summaries:
  <https://biblio.ugent.be/publication/8513229>

## Candidate Directions

1. Build a family-parametric matrix before relying on single examples. The
   matrix should report which layer answered, the size of each list shape, fuel
   and call-depth settings, and representative proof tags.
2. Add a generic guarded-clause intermediate representation at compile time.
   The IR should partition each top-level alternative into equality/disequality
   guards, procedure calls, and residual formulas, without naming list
   relations or constructors.
3. Use the IR to saturate guards before recursive call descent. Constructor
   clashes, same-head decomposition, and rigid disequality discharge should
   shrink branch state before deeper recursive calls are tried.
4. Add a structural descent metric over walked constructor arguments. Recursive
   calls whose arguments are proper subterms of an ancestor call should be
   preferred or bounded differently, but the metric must stay generic.
5. Treat source-to-IR transformation as an intended benefit of the multi-layer
   Proflog architecture. A source program can be lowered to a prover-facing
   representation that preserves relational meaning while exposing demand,
   guards, residual calls, and layer-eligible subproblems. This is not a
   workaround around the kernel; it is how a general-purpose language can make
   its central proof kernel practical.
6. Consider tabled duplicate-state suppression keyed by canonical branch state,
   call state, and depth. This should be introduced only after the matrix shows
   where repeated states dominate runtime.
7. Keep interoperation proof-producing. Optimized propositional, first-order,
   and future constructor-recursive layers may close branch-local residuals,
   but any closure must remain an explicit proof step rather than an opaque
   oracle.
8. Consider improving the underlying `core.logic` host-language performance.
   This can be tableau-prover specific if the optimization remains generic
   across Proflog programs, or it can be a fully general `core.logic`
   enhancement that benefits non-Proflog relations as well. The important
   boundary is that this avenue must not encode list-family knowledge; it
   should reduce the cost of relational search, unification, scheduling,
   tabling, or constraint propagation for broad classes of programs.

   This direction requires a separate research and deployment plan before it
   can be credited as ADR-0031 progress. The plan must include:

   - review of the upstream `core.logic` codebase at the version Proflog is
     actually using;
   - focused benchmarks that distinguish Proflog tableau overhead from general
     `core.logic` overhead;
   - a patched dependency strategy that makes Proflog use the modified
     `core.logic` implementation rather than the default dependency; and
   - a verification step that records the patched source revision or artifact
     in the probe output before comparing matrix timings.

## Implementation Order Adopted

ADR-0031 starts with the compile/program boundary rather than a new special
list mechanism:

1. Promote the size-parametric matrix and complexity telemetry.
2. Compile generic guarded-clause IR from source programs.
3. Expose the IR through relational program lookup.
4. Use the IR to guide guard-first call descent and answer-mode residual
   handling.
5. Add tabled/canonical duplicate-state suppression only if descent discipline
   alone leaves exponential repeated work.
6. If proof-search shape looks reasonable but runtime remains prohibitive,
   evaluate generic `core.logic`/host-language performance work before adding
   relation-specific search tricks. This evaluation must include a revised
   deployment sequence that pins the patched implementation and proves it is on
   Proflog's classpath.
7. Re-run the matrix and write the AAR against family-level behavior, not
   isolated examples.
