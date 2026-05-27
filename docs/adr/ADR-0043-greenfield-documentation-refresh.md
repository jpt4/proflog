# ADR-0043: Greenfield Documentation Refresh

- Status: completed
- Date: 2026-05-06
- Branch: `adr-0043-greenfield-doc-refresh`
- AAR: [AAR-0043](../aar/AAR-0043-greenfield-documentation-refresh.md)
- Depends On:
  - [ADR-0035](ADR-0035-relational-residual-continuation.md)
  - [ADR-0038](ADR-0038-fitting-program-kernel-evaluation.md)
  - [ADR-0039](ADR-0039-kernel-level-group-verification.md)
  - [ADR-0040](ADR-0040-legacy-subsumption-parity.md)
  - [ADR-0041](ADR-0041-relational-constructor-recursive-profile.md)
  - [ADR-0042](ADR-0042-equality-fragment-status-consistency.md)

## Context

The greenfield implementation has moved quickly from a small source-language,
kernel, and query stack into a layered implementation with profiled kernel
components, answer-overlay execution, finite verifier families, long-running
list-program probes, and worked examples.

Some documentation now mixes historical runtime boundaries with current
coverage. That is useful as a development record, but it is risky when a reader
uses the current tutorial or runtime baseline to understand what Proflog can do
today. A concrete example is the old list-answer boundary stating that
`reverse([a,b], r)` does not reach `r = [b,a]` through `query-answers`, while
current answer tests assert the closed answer and the ADR-35 long-timeout sweep
records broader list-family reachability.

The source code also needs a durable reader path. Many namespaces already carry
good local comments, but the project now needs a current, cross-namespace map
from surface Proflog source through compilation, kernel proof search, profiled
delegation, query status, and answer export. The goal is not to erase historical
comments or rewrite every function. The goal is to make the greenfield codebase
readable as an implementation of Fitting's Proflog specification and as a record
of lessons learned during implementation.

## Decision

Refresh current-facing documentation and source commentary under a dedicated
documentation ADR.

The cleanup will:

- mark stale runtime rows as historical when later evidence supersedes them;
- update current tutorial, example, memory, and lesson material that still
  describes superseded behavior as present behavior;
- add a source-reader map for every greenfield implementation namespace under
  `src/proflog`;
- improve namespace and boundary comments in the source language, compiled
  program, kernel support, proof kernel, profiled kernel, query, and answer
  layers; and
- preserve historical logs and AARs as records of what happened, while linking
  them to current summaries when they are no longer the best public entrypoint.

## Consequences

This ADR is documentation-only. It should not change proof search behavior,
query behavior, answer export, runtime budgets, or test selectors.

Because the branch touches comments and current-facing documentation, ordinary
tests are still useful as a guard against accidental code edits, but the core
acceptance evidence is an audit: each explicit documentation requirement must
map to an updated artifact or a documented reason for leaving a historical file
unchanged.

## Test Obligations

This ADR has no first failing semantic test obligation because it intentionally
does not change implementation behavior. Verification must instead include:

- a before/after audit of stale runtime claims found in current-facing docs;
- a grep audit for known stale phrases such as "does not reach", "No result
  before manual stop", and "sidecar" in current-facing materials;
- a source-map checklist covering every `src/proflog` namespace; and
- at least `lein test-proflog-fast` before merge, unless the final diff is
  proven to be documentation-only and the user explicitly accepts skipping it.

## Exit Criteria

ADR-0043 is complete when:

- stale current-facing runtime claims are corrected or explicitly labelled as
  historical;
- `TEST_RUNTIME_BASELINE` distinguishes historical exploratory rows from
  current post-ADR-35/40/41/42 runtime evidence;
- tutorial and worked-example pages no longer contradict current tests on
  promoted greenfield capabilities;
- `MEMORY.md` and `LESSONS.md`, if present, contain only durable current facts
  or intentionally historical notes;
- every `src/proflog` namespace is covered by a current source-reader map;
- the core source-language-to-kernel path has comments/docstrings explaining how
  it implements Fitting's specification and what implementation lessons shaped
  it; and
- an AAR records the audit, changed artifacts, verification command results,
  and any intentionally preserved historical material.
