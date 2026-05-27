# ADR-0011: Open-Answer Relationality

- Status: completed
- Date: 2026-04-23
- Branch: `adr-0011-open-answer-relationality`
- AAR: [AAR-0011](../aar/AAR-0011-open-answer-relationality.md)

## Context

The earlier greenfield open-answer path was semantically disciplined but still
operationally hybrid.

- `query-answers` staged syntactic call unfolding outside the kernel.
- Raw program-answer search then called `prove-program-answero` with a staged
  kernel `call-depth`, but the first exposed program-call layer still came from
  answer-layer rewriting rather than direct relational descent.
- That kept admissibility filtering and residual export simple, but it left the
  default open-query path less relational than legacy end to end.

ADR-0003 set the expectation that the greenfield kernel should remain a pure
relation with no baked-in mode restriction. ADR-0006 established answer
discipline and variant boundaries. ADR-0009 then showed the practical cost of
the current open-answer shape: legacy direct `proveo` search still reaches the
concrete `reverse([a,b], [b,a])` answer and all four inverse-`append` splits,
while greenfield staged answer search tops out at a shallower symbolic
frontier.

The repository therefore needs a default answer-search path that stays closer
to direct relational kernel descent without giving up the current answer export
boundary.

## Decision

- Make the default greenfield open-answer path keep the original negated query
  and enter top-level literal program calls directly in the kernel through
  `prove-program-query-entryo`.
- Treat `call-depth` as recursive descent budget below the surface query
  boundary; the entry call itself does not consume that budget.
- Keep answer-mode call deferral as a relational kernel choice instead of an
  answer-layer unfolding pass.
- While descent budget remains, order the kernel branches so real recursive
  descent is tried before residual call deferral.
- Rank exported answer records by completion so closed answers containing only
  disequalities can displace earlier symbolic frontiers when they appear later
  in the raw proof stream.
- Preserve the existing answer-discipline boundary:
  - exported answers stay inside the declared language,
  - residual constraints remain explicit,
  - proof evidence remains attached.
- Keep stage diagnostics for investigation, but let `query-answers` itself run
  one exact direct-entry search at the requested `call-depth`.

## Consequences

- The default open-answer mode becomes more relationally pure because the first
  query-call exposure and the defer-vs-descend choice both live inside the
  kernel rather than in a meta-level expansion helper.
- Stage numbering shifts. For literal program queries, stage `0` now means
  "direct entry call with no recursive descent below it," not "leave the query
  call itself deferred."
- `query-answers` no longer merges across stages. Instead, one exact search at
  the requested `call-depth` is post-processed by a completion-aware ranking
  layer.
- Operationally, that ranking improves append-family top-`N` behavior: inverse
  `append` again prefers the base split plus the first concrete recursive split,
  and the nested suffix family can recover its concrete answer when the raw
  proof budget is pushed to `64`.
- Reverse remains materially farther from legacy parity. After the direct-entry,
  defer-order, and ranking changes, `call-depth 1` now refines the first
  frontier from `r = []` to `r = [a]`, but a `>120 s` probe at `fuel 64`,
  `call-depth 3`, and raw budgets up to `64` still failed to export
  `r = [b, a]`.
- The remaining boundary is now clearer: exact direct-entry search can still
  only descend through recursive program calls whose arguments are ground under
  the current procedure-call rule. Open recursive modes that leave a later call
  argument nonground still tend to surface residual calls rather than full
  legacy-style materialization.

## Test Obligations

- Update `test/proflog/answers_test.clj` to assert that direct-entry reverse
  stages remain productive and that `call-depth 1` refines the stage-`0`
  frontier instead of falling back to it.
- Add a regression that inverse `append` prefers the first closed recursive
  split over shallower symbolic frontiers.
- Re-run the existing list and answers namespaces that document the current
  reverse/append boundary, including the nested suffix query that now remains
  symbolic at the default raw-proof cap.

## Exit Criteria

- `query-answers` no longer relies on eager first-layer query unfolding in the
  default path.
- The default open-answer path enters top-level literal program calls directly
  in the kernel and spends budget only on recursive descendants.
- Reverse and append open-query coverage is re-measured and documented,
  including the distinction between append cases that recover under a larger raw
  budget and reverse cases that still do not.
- The docs describe the new boundary and call out the remaining legacy gap
  honestly.
