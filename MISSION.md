# Mission Statement

Date: 2026-04-18

## Mission

Implement Melvin Fitting's Proflog end-to-end as a Clojure/core.logic system whose semantic core is justified from primary sources rather than inherited from local prior art.

## Required Outcomes

- A pure relational prover/programming system whose ground-core behavior matches Fitting's Proflog semantics.
- A user-facing Proflog surface language with explicit language declarations, clause compilation, and proof-producing execution.
- Equality implemented as finite-term free-constructor equality with occurs-checking unification and symbolic disequality.
- Procedure calls implemented as subsidiary tableaux over the same program, with positive and negative call support.
- A top-level query API that exposes success, failure, and operationally honest unresolved search.
- Answer projection that exports only terms of the declared object language and never leaks internal parameters.

## Non-Negotiable Constraints

- The greenfield implementation does not treat the current `cljtap.*` code as a porting target.
- The default kernel remains pure and relational; any compromise must be isolated as a named semantic or runtime variant.
- `run-nc`, silent rational-tree behavior, hidden committed choice, and undocumented host projection are not default behavior.
- ADRs precede feature work, and completed ADRs receive AARs.
- Tests precede code, and release decisions track the test matrix in this repository.

## Definition Of Done

The project is only mission-complete when the greenfield namespaces can:

- parse or construct the supported Proflog language,
- run Fitting's canonical examples end-to-end,
- handle equality and disequality soundly,
- return admissible answers for open queries,
- explain behavior with replayable proof objects,
- document any remaining incompleteness or performance compromises as explicit variants.
