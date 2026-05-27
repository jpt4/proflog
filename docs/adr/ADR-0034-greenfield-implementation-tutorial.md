# ADR-0034: Greenfield Implementation Tutorial Reference

- Status: accepted
- Date: 2026-05-01
- Branch: `adr-0034-greenfield-implementation-tutorial-docs`
- AAR: pending
- Depends On:
  - [ADR-0033](ADR-0033-structural-answer-variable-recursion.md)

## Context

The greenfield implementation now has a broad ADR and AAR trail, a language
namespace specification, many focused logs, and a mature set of source layers
under `src/proflog`. Those records explain individual decisions well, but the
repository still lacks one durable tutorial/reference that explains the whole
implementation stack as a coherent system.

That gap matters because the implementation is intentionally layered:

- the AST, language, normalization, and substitution namespaces define the
  source and compiled object-language boundary;
- the program namespace gives the kernel a relational Procedure Call Rule
  interface;
- the kernel, equality, kernel-support, gamma, proof, and tabled/profiled
  layers implement branch-closing proof search;
- the query, answer-overlay, answers, and constructor-recursive layers expose
  public truth and answer surfaces; and
- diagnostics, probes, tests, ADRs, AARs, and logs explain operational behavior
  that is not visible from any one namespace.

ADR-0033 added `docs/LANGUAGE_NAMESPACE_SPEC.md`, which covers the language
compiler boundary in pedagogical detail. The project now needs the same level
of explanation for the entire greenfield stack, including how data and proof
state move between layers.

## Decision

Add [Greenfield Implementation Tutorial and Reference](../GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
as the authoritative whole-stack tutorial for the current greenfield
implementation.

The reference must be suitable both as:

- a guided tutorial for a reader learning the implementation; and
- a stable technical reference for later ADRs, thesis-style writeups, and
  reviews of semantic or operational changes.

The document will explain, at both conceptual and technical levels:

- the object-language AST and public language boundary;
- declaration normalization, validation, NNF compilation, substitution, and
  compiled program views;
- the Procedure Call Rule interface in `proflog.program`;
- the full proof-kernel state model and tableau rule families;
- equality, disequality, L-groundness, fuel, gamma candidate generation, proof
  variables, and proof terms;
- profiled propositional / first-order proof layers and tabling as separated
  operational layers;
- query status semantics;
- answer-overlay execution, answer record export, residual completion, public
  answer APIs, and closed-answer materialization;
- constructor-recursive guarded-IR settlement;
- diagnostics, probes, and test surfaces; and
- the end-to-end movement of source data, branch state, residual state, proof
  evidence, and exported answers.

The branch is documentation-only. It does not change implementation behavior or
test expectations.

## Consequences

The tutorial becomes the first place to send future maintainers who need to
understand the whole greenfield implementation rather than one ADR slice. Later
ADRs that change a major layer should update this reference when the old
explanation would become misleading.

The main risk is staleness. To reduce that risk, the tutorial names concrete
source namespaces and explains invariants rather than only summarizing branch
outcomes. The reference is intentionally linked from the ADR index, execution
plan, and development log so future architecture changes can find it.

Rejected alternatives:

- Do nothing and rely on the ADR trail. That keeps each decision local but
  forces readers to reconstruct cross-layer behavior from dozens of records.
- Expand only `docs/LANGUAGE_NAMESPACE_SPEC.md`. That would overload a focused
  compiler-boundary document with kernel, answer, and diagnostics material.
- Write a results summary. The needed artifact is a tutorial/reference for how
  the implementation works, not a retrospective list of wins.

## Test Obligations

Because this branch changes documentation only, required verification is
documentation-safe:

```text
git diff --check
```

No Clojure tests are required unless source files are modified.

## Exit Criteria

ADR-0034 is complete when:

- `docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md` exists and covers the full
  stack described in the decision;
- the ADR index links ADR-0034;
- `docs/EXECUTION_PLAN.md` records ADR-0034 in the ADR sequence;
- `LOG.md` records the documentation addition;
- no implementation files are modified; and
- `git diff --check` passes.
