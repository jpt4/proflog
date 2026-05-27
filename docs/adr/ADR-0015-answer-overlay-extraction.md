# ADR-0015: Answer Overlay Extraction

- Status: accepted
- Date: 2026-04-24
- Branch: `adr-0015-answer-overlay`
- AAR: [AAR-0015](../aar/AAR-0015-answer-overlay-extraction.md)

## Context

ADR-0014 clarified two related facts:

- the greenfield project needs an explicitly accessible pure-core proof surface,
- and the greenfield kernel still contains a separate answer-mode flow that is
  one of the clearest structural differences from both the legacy prover and a
  more nearly pure miniKanren-style core.

Today, `src/proflog/kernel.clj` still mixes two concerns:

1. ordinary proof search over first-order formulas with equality,
2. answer-oriented search control and export support, including:
   - answer vars,
   - existential-as-variable behavior,
   - residual deferred calls,
   - call-depth budgeting below the query boundary,
   - and answer-mode entry relations for program queries.

That mixed kernel makes it harder to answer the architectural question ADR-0014
raised directly:

```text
What can the pure core prover do on its own, before answer overlays intervene?
```

The repo now has enough evidence to justify separating those layers
explicitly.

## Decision

- Preserve `proflog.kernel` as the ordinary proof-search kernel.
- Extract the answer-oriented flow into a separate overlay namespace that the
  answer API can call explicitly.
- Treat the following as overlay concerns rather than ordinary kernel concerns:
  - answer vars,
  - existential witness export as `(var ...)`,
  - residual deferred procedure-call obligations,
  - recursive answer-call budgeting (`call-depth`),
  - and top-level open-query entry relations.
- Keep the public answer APIs behaviorally stable where feasible during the
  extraction. This ADR is about architectural separation first, not about
  intentionally changing the answer contract.
- Accept some temporary duplication if needed to complete the extraction
  cleanly, but document it and keep it narrow.

## Consequences

- The repo gains a cleaner distinction between:
  - pure proof search,
  - and answer-oriented overlays above that core.
- ADR-0014 probes against the pure core become easier to state and trust,
  because they no longer need to rely on kernel entry points that also carry
  answer-mode semantics.
- The extraction does not by itself prove that answer-mode flow is unnecessary.
  It only makes that question testable.
- If later ADR-14 / ADR-15 work shows that the overlay can fully preserve the
  useful answer behavior without living inside the kernel, then the repo will
  have stronger grounds for a more substantial kernel simplification.
- The cleanest implementation of that separation still needs one more layer of
  factoring: shared branch-state relations should not fork into one copy in the
  pure kernel and another copy in the overlay.

## Implementation Notes

ADR-0015 is complete with three concrete implementation moves:

1. `src/proflog/kernel.clj` now exposes only the ordinary proof-search surface:
   `proveo`, `prove-programo`, `prove`, and `prove-program`.
2. `src/proflog/answers.clj` routes answer search through
   `src/proflog/answer_overlay.clj`, not through answer-mode entry points in the
   kernel.
3. Shared proof-state relations now live in
   `src/proflog/kernel_support.clj`, so the overlay no longer carries a
   second copy of the common L-ground, disequality, complementary-literal, and
   fuel-stepping machinery.

That last move is the first follow-on improvement unlocked by the extraction.
It keeps one semantic definition for the proof core while leaving only the
answer-specific residual and call-depth behavior in the overlay.

## Test Obligations

- Add targeted regressions proving that the ordinary kernel proof API still
  works after the extraction.
- Add targeted answer regressions proving that `query-answers` still reaches
  the currently documented list-family answer surface through the extracted
  overlay path.
- Add at least one narrow regression demonstrating that the answer API now uses
  overlay entry points instead of kernel-resident answer entry points.

## Exit Criteria

- `src/proflog/kernel.clj` no longer exposes the answer-mode execution path as
  part of the ordinary kernel implementation.
- A separate answer overlay namespace exists and is used by
  `src/proflog/answers.clj`.
- The fast suite passes.
- The relevant extended answer/list regressions pass.
- ADR-0015 leaves behind updated documentation explaining the new kernel vs
  overlay boundary.
- The ordinary `query` API is explicitly locked to the pure kernel path by
  regression, so future answer-surface work cannot silently re-entangle the two
  layers.
