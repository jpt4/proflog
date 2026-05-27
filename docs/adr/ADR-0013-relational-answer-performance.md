# ADR-0013: Relational Answer Performance

- Status: completed
- Date: 2026-04-23
- Branch: `adr-0013-relational-answer-performance`
- AAR: [AAR-0013](../aar/AAR-0013-relational-answer-performance.md)

## Context

ADR-0011 made the default greenfield open-answer path more relationally pure,
but it also clarified where the remaining legacy gap actually lives.

- `append` often looks like an ordering and budget problem: more concrete
  answers are already in the raw stream, but later than the first symbolic
  frontiers.
- `reverse` looks more structural: longer-running direct-entry probes still do
  not export `r = [b,a]`.

The main structural asymmetry is the procedure-call boundary.

- Top-level literal program queries get a direct answer-mode entry path.
- Recursive program calls still rely on the ordinary procedure-call rule, which
  requires ground arguments before descent.

That makes the generic path more relationally pure than legacy, but it still
leaves open recursive modes vulnerable to residual-call export instead of full
materialization. The search also pays obvious duplication costs:

- repeated residual literals,
- distinct proof paths collapsing to the same exported answer,
- and no branch/frontier canonicalization strong enough to keep equivalent
  states from being explored repeatedly.

The project therefore needs a second branch that aims to close parity without
adding more specialty modes than necessary.

## Decision

- Attempt to close the remaining answer-path gap by strengthening the generic
  relational search itself rather than by defaulting to a new specialty mode.
- This branch will investigate and, where justified, implement the following as
  one coherent track:
  - recursive direct-entry semantics for nonground answer-mode subcalls,
  - frontier canonicalization, memoization, or tabling sufficient to cut off
    equivalent recursive answer states,
  - residual normalization and deduplication so answer merging and ranking see
    semantically identical frontiers as the same state.
- The design goal is to reduce the need for additional search modes, not to
  create another one.
- If ADR-0012 proves that a closed-answer parity mode is still necessary even
  after this work, its changes may be pulled into this branch deliberately and
  documented as such.

## Consequences

- The branch did improve the generic answer surface, but not by proving that
  recursive nonground kernel descent alone could close the reverse gap.
- The implemented changes are:
  - residual normalization and deduplication in exported answer records,
  - alpha-equivalent frontier canonicalization via stable `_0`, `_1`, ...
    renaming of internal proof vars,
  - and an intentional pull-in of ADR-0012's closed list-family materializer so
    known `append/3` and `reverse/2` queries can return closed answers directly
    through `query-answers`.
- The raw symbolic diagnostics remain important. They still expose the open
  reverse frontier under the kernel, which means the branch did not settle the
  parity gap purely by moving the recursive procedure-call boundary.
- ADR-0012 is therefore still required as an explicit closed-answer API and
  bounded fallback materializer, even though its list-family fast path is now
  also reused by the default `query-answers` surface for those known families.
- The branch reduced practical dependence on the specialty parity mode for the
  legacy list questions, but it did not eliminate the architectural distinction
  between:
  - a generic answer API that may still return symbolic frontiers for other
    families, and
  - a closed-answer-only API for callers that need extensional behavior by
    contract.

## Test Obligations

- Add failing regressions that make the generic path answer the actual open
  parity questions:
  - `reverse([a,b], r)` should eventually export `r = [b,a]`,
  - `append(x, y, [a,b,c])` should recover deeper split families without a
    separate parity-only API,
  - nested suffix and nested inverse append queries should either close under
    the generic path or remain explicitly documented as open.
- Add regressions for frontier normalization:
  - duplicate residual literals collapse,
  - alpha-equivalent answer frontiers merge,
  - and repeated proof families do not crowd out later distinct answers.
- Add at least one targeted regression around recursive nonground subcall entry
  so this branch proves whether the procedure-call boundary has actually moved.

## Exit Criteria

- The generic open-answer path is remeasured with longer-running probes.
- The repo documents whether recursive nonground answer-mode descent is both
  semantically acceptable and operationally useful.
- Residual normalization and frontier canonicalization demonstrably reduce
  duplicate answer families.
- The branch concludes explicitly whether ADR-0012 is still needed:
  - unnecessary because the generic path now closes the parity gap well enough,
    or
  - still required, in which case its branch is intentionally pulled in.

## Branch Conclusion

- The branch completed the frontier-normalization work:
  - duplicate residual literals now collapse,
  - alpha-equivalent exported frontiers merge,
  - and repeated proof families no longer crowd out the first concrete answer
    families for the known list queries.
- `query-answers` now returns the closed reverse and inverse-append legacy
  answers for the known list-family cases, and it also closes the nested suffix
  and nested forward append queries covered in `list_programs_test.clj`.
- The raw diagnostics still expose the symbolic reverse frontier
  `r = []` with deferred `reverse/append` obligations. That is the key semantic
  finding of the branch: the public parity closure now comes from the
  intentionally pulled-in list-family materializer, not from proving that the
  kernel alone now materializes those answers generically.
- ADR-0012 therefore remains necessary. Its branch has been intentionally
  pulled in here for the known list families, but the dedicated
  `query-parity-answers` API still serves a distinct purpose as the explicit
  closed-answer-only and bounded materialization interface.
