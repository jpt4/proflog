# ADR-0056: Greenfield User Guide

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0056-greenfield-user-guide`
- AAR: [AAR-0056](../aar/AAR-0056-greenfield-user-guide.md)
- Depends On:
  - [ADR-0010](ADR-0010-frontend-inlining-translation.md)
  - [ADR-0034](ADR-0034-greenfield-implementation-tutorial.md)
  - [ADR-0043](ADR-0043-greenfield-documentation-refresh.md)
  - [ADR-0055](ADR-0055-ski-relational-routing.md)

## Context

The greenfield implementation now has an implementation tutorial, a source map,
worked examples, ADR/AAR history, and many focused tests. Those documents are
useful to maintainers, but they do not yet provide one authoritative user guide
that explains how to write Proflog programs, how frontend forms descend to the
kernel, which query and answer APIs to use, which proof profiles exist, and
where the current operational boundaries are.

The distinction matters because the project now has several legitimate user
surfaces:

- the ADR-0010 prefix frontend for languages, clauses, helper definitions,
  closed queries, and open answer queries;
- the lower-level AST and language compiler for diagnostics and tests;
- the proof kernel and proof profiles for direct proof inspection;
- the query and answer APIs for normal use;
- worked examples for Fitting programs, finite verifiers, Turing-completeness
  demonstrations, SKI reduction, and Robinson Q; and
- probe namespaces that are intentionally not public semantic entrypoints.

A new reader should not have to reconstruct the authoritative path from all of
those materials. The public guide should describe the current implementation as
it exists now, while linking deeper documents for implementation archaeology.

## Decision

Add `docs/USER_GUIDE.md` as the current authoritative greenfield user guide.
The guide will be documentation-only and will explain:

- installation and REPL/test entrypoints;
- the current recommended authoring style;
- Fitting-style pseudo-code, prefix frontend forms, backend AST, compiled
  programs, query formulas, and kernel calls;
- language declarations and arity validation;
- relation clauses, helper definitions, and inlining rules;
- closed success/failure/status queries;
- answer-mode queries, exported variables, residuals, and partial synthesis;
- direct AST and kernel use for diagnostics;
- equality, quantifiers, gamma instantiation, procedure calls, and fuel;
- proof profiles and when they should be selected;
- example program families and their demonstrated coverage;
- runtime suites and recorded slow-path expectations; and
- known boundaries and non-public probe namespaces.

The guide should be written for competent mathematicians and computer
scientists who may not be Clojure specialists. It should therefore define the
core terms before using them operationally, name the files that implement each
layer, and show enough concrete code that the guide can be used directly at a
REPL.

## Consequences

`docs/USER_GUIDE.md` becomes the stable public reference for greenfield use.
The existing `docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md` remains the deeper
implementation tutorial, and `docs/GREENFIELD_SOURCE_MAP.md` remains the
mechanical source-reader map. README should link the User Guide as the next
document after the quickstart.

The main risk is staleness. To reduce that risk, this ADR requires the guide to
map its claims back to the current tutorial, source map, frontend/language/AST,
kernel/profile, query/answer, and worked-example files during review.

Rejected alternatives:

- Expand the README into a full reference. That would make the first page too
  heavy and blur the boundary between quickstart and reference.
- Replace the implementation tutorial. That tutorial is still useful for
  maintainers because it emphasizes internal proof-state mechanics.
- Rely on worked examples alone. Examples demonstrate use cases, but they do
  not define the whole public surface or its boundaries.

## Test Obligations

This ADR is documentation-only. Required verification is:

```text
git diff --check
```

No Clojure test suite is required unless implementation files or executable
example code are modified.

## Exit Criteria

ADR-0056 is complete when:

- `docs/USER_GUIDE.md` exists and is linked from README;
- the guide reflects review of the tutorial, source map, and all
  `src/proflog` namespaces, especially frontend, language, AST, kernel, and
  profile files;
- the ADR index, AAR index, execution plan, and `LOG.md` record the guide;
- an AAR records what was delivered and any remaining documentation risks;
- no runtime implementation files are modified; and
- `git diff --check` passes.
