# AAR-0043: Greenfield Documentation Refresh

Date: 2026-05-06
ADR: [ADR-0043](../adr/ADR-0043-greenfield-documentation-refresh.md)
Status: completed

## Summary

ADR-0043 refreshed current-facing documentation after ADR-35/38/39/40/41/42
changed several capability boundaries. The branch keeps historical runtime
records, but labels them as historical when they no longer describe current
public behavior.

The branch also adds an exhaustive source-reader map for every `src/proflog`
namespace and expands comments/docstrings at the source-language and compiled
program boundaries. The goal is that a reader can follow greenfield Proflog from
surface AST and language declarations through compilation, proof search, query
status, answer export, profiles, examples, and probes without guessing which
layer owns which semantic responsibility.

## What Changed

- Added [Greenfield Source Map](../GREENFIELD_SOURCE_MAP.md), covering every
  current `src/proflog` namespace.
- Added ADR-0043 to the ADR index and execution plan.
- Updated [Greenfield Implementation Tutorial](../GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
  to include ADR-40 through ADR-43 and to point at the exhaustive source map.
- Updated [TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md):
  - current focused reverse answer runtime: `elapsed 14.06 s`;
  - current focused inverse append runtime: `elapsed 10.91 s`;
  - ADR-43 fast gate runtime: `elapsed 67.73 s`;
  - older exploratory rows are now labelled historical instead of current.
- Updated stale current-facing list and GV examples:
  - [List Programs](../../worked-examples/list-programs.md) now records current
    public inverse append coverage and the post-ADR-35 nested inverse matrix row;
  - [GV Probes](../../worked-examples/gv-probes.md) now distinguishes the
    historical timeout/overlay record from current equality-fragment proof
    coverage.
- Updated [TEST_GAP_CLOSURE_CHECKLIST](../TEST_GAP_CLOSURE_CHECKLIST.md) with a
  historical-status warning and current notes for list, quantified, integration,
  reverse program synthesis, GV, and finite-domain rows.
- Updated [MEMORY](../../MEMORY.md) and [LESSONS](../../LESSONS.md) with current
  ADR-0043 facts so older list/GV notes are read as history unless explicitly
  updated by later ADRs.
- Expanded namespace comments/docstrings in:
  - `src/proflog/language.clj`;
  - `src/proflog/program.clj`;
  - `src/proflog/normalize.clj`;
  - `src/proflog/answers.clj`;
  - `src/proflog/core_logic_host.clj`;
  - `src/proflog/core_logic_host_probe.clj`.

## Completion Audit

| Requirement | Evidence |
|---|---|
| Branch to a new ADR | Branch `adr-0043-greenfield-doc-refresh`; ADR record added at `docs/adr/ADR-0043-greenfield-documentation-refresh.md`. |
| Clean stale test results and durations | `TEST_RUNTIME_BASELINE` now records current focused answer timings and labels superseded exploratory rows as historical. |
| Clean stale tutorial/example material | `GREENFIELD_IMPLEMENTATION_TUTORIAL`, `worked-examples/list-programs.md`, `worked-examples/gv-probes.md`, and `TEST_GAP_CLOSURE_CHECKLIST` were updated against current ADR-35/39/41/42 evidence. |
| Clean memory/lesson material | `MEMORY.md` and `LESSONS.md` now identify the current list/GV facts and mark older gap records as historical. |
| Document all greenfield code | `docs/GREENFIELD_SOURCE_MAP.md` maps every `src/proflog` namespace; a grep-based namespace coverage audit returned no missing namespaces. |
| Explain source-language-to-kernel contribution to Fitting Proflog | Expanded docstrings in `language`, `program`, and `normalize`; the source map and tutorial now explicitly describe AST, language `L`, one-clause core compilation, NNF negated bodies, Procedure Call Rule lookup, equality state, query status, and answer overlay boundaries. |
| Preserve historical records honestly | Historical runtime rows remain in `TEST_RUNTIME_BASELINE`, but with explicit supersession notes instead of presenting them as current capability boundaries. |
| Verify no implementation behavior changed accidentally | `lein test-proflog-fast` passed after the source comment/docstring edits. |

## Audit Commands

Focused timing probes:

```text
timeout -k 5s 240s lein test :only proflog.answers-test/query-answers-use-call-depth-1-to-refine-the-direct-reverse-frontier
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
elapsed 14.06 s

timeout -k 5s 360s lein test :only proflog.answers-test/query-answers-prefer-the-first-concrete-inverse-append-split-over-symbolic-frontiers
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
elapsed 10.91 s
```

Source map coverage audit:

```text
rg --files src/proflog | sort > /tmp/src-proflog-files.txt
sed 's#src/proflog/##; s#/#.#g; s#_#-#g; s#.clj$##; s#^#proflog.#' \
  /tmp/src-proflog-files.txt > /tmp/src-proflog-ns.txt
while read ns; do
  rg -q "\`$ns\`" docs/GREENFIELD_SOURCE_MAP.md || echo "missing $ns"
done < /tmp/src-proflog-ns.txt
```

Result: no missing namespace output.

Stale-phrase audit over current-facing docs:

```text
rg "No result before|does not reach|does not recover|current.*weaker|currently weaker|remain the live gap|stops short|still does not" \
  README.md docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md docs/GREENFIELD_SOURCE_MAP.md \
  docs/TEST_RUNTIME_BASELINE.md docs/TEST_GAP_CLOSURE_CHECKLIST.md \
  docs/LEGACY_PROGRAM_PARITY_MATRIX.md worked-examples MEMORY.md LESSONS.md
```

Remaining hits are either explicitly historical rows in `TEST_RUNTIME_BASELINE`
or unrelated non-stale wording such as disequality/residual caveats.

Fast gate:

```text
timeout -k 5s 600s lein test-proflog-fast
Ran 118 tests containing 384 assertions.
0 failures, 0 errors.
elapsed 67.73 s
```

## Remaining Notes

ADR-0043 intentionally does not rewrite historical AARs or development logs.
Those records document what was known when each ADR closed. Current-facing entry
points now point readers to the updated source map, parity matrix, and runtime
baseline instead.

The extended suite was not rerun because this branch changes documentation and
namespace comments/docstrings only. No proof-search, equality, query, or answer
implementation semantics changed.
