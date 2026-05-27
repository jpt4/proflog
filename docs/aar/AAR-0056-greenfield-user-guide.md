# AAR-0056: Greenfield User Guide

- Date: 2026-05-09
- ADR: [ADR-0056](../adr/ADR-0056-greenfield-user-guide.md)
- Branch: `adr-0056-greenfield-user-guide`
- Status: completed

## Summary

ADR-0056 added [Proflog Greenfield User Guide](../USER_GUIDE.md) as the current
authoritative user-facing reference for the greenfield implementation. The guide
unifies the quickstart, implementation tutorial, source map, code review, query
and answer APIs, profile boundaries, worked examples, test commands, and known
operational limits.

No runtime implementation code changed.

## Evidence

- Reviewed `README.md`, `docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md`,
  `docs/GREENFIELD_SOURCE_MAP.md`, `docs/LANGUAGE_NAMESPACE_SPEC.md`,
  `docs/TEST_RUNTIME_BASELINE.md`, and the worked-example index/descent page.
- Reviewed all current `src/proflog` namespaces through the source map,
  source inventory, and focused reads of the frontend, language, AST, query,
  answer, kernel, equality, support, profile, catalog, and probe files.
- Verified that every current `src/proflog` namespace is represented in
  `docs/GREENFIELD_SOURCE_MAP.md`.
- Added `docs/USER_GUIDE.md` and linked it from `README.md`.
- Recorded ADR-0056 in the ADR index, AAR index, execution plan, and
  development log.

## Effect

The project now has a clear documentation stack:

- `README.md` remains the quickstart and navigation entrypoint.
- `docs/USER_GUIDE.md` is the public greenfield reference for writing and
  evaluating Proflog programs.
- `docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md` remains the maintainer-oriented
  implementation tutorial.
- `docs/GREENFIELD_SOURCE_MAP.md` remains the exhaustive namespace map.
- `worked-examples/` remains the proof/result catalog for concrete examples.

The guide makes several boundaries explicit for users: the current implemented
surface is prefix Clojure syntax rather than a textual parser, `:=` is
nonrecursive helper inlining, answer residuals are first-class frontiers,
equality-fragment acceleration includes a generic deterministic host engine,
and probe namespaces are not public semantic entrypoints.

## Verification

Required documentation-only gate:

```text
git diff --check
```

No Clojure tests were required because the branch changed documentation and
navigation only.

## Follow-Up

Future ADRs that change the frontend, query/answer surfaces, proof profile
selection, or public examples should update `docs/USER_GUIDE.md` in the same
logical unit.
