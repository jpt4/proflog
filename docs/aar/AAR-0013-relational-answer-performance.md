# AAR-0013: Relational Answer Performance

- Date: 2026-04-24
- Related ADR: [ADR-0013](../adr/ADR-0013-relational-answer-performance.md)
- Outcome: complete

## What Happened

ADR-0013 finished the answer-surface normalization work in
`src/proflog/answers.clj` and then made an explicit architectural move:
the known ADR-0012 list-family closed-answer materializer was pulled into the
default `query-answers` path for recognized `append/3` and `reverse/2` queries.

The completed branch now:

- canonicalizes internal proof vars in exported answers to stable `_0`, `_1`,
  ... names,
- deduplicates residual literals before ranking and merge,
- merges alpha-equivalent exported frontiers instead of treating them as
  distinct answers,
- and returns closed list-family answers directly through `query-answers` for
  the legacy reverse / inverse-append / nested suffix families.

## What Worked

- The generic answer surface is materially better for callers. The public
  `query-answers` API now returns closed answers for the known legacy list
  families instead of exposing only the symbolic `r = []` / `r = [a]`
  frontiers.
- Frontier normalization is now strong enough for the branch's regression goals:
  duplicate reverse residuals collapse and alpha-equivalent symbolic answers
  merge under a stable exported variable naming scheme.
- Diagnostics remain honest. `query-answer-diagnostics` still shows the raw
  symbolic reverse frontier underneath the public closed answer surface, so the
  repo can distinguish "public answer improvement" from "kernel search closure."

## What Did Not Work

- The branch did not demonstrate that recursive nonground answer-mode descent in
  the kernel, by itself, closes the reverse parity gap. The raw diagnostics
  still surface deferred recursive obligations for `reverse([a,b], r)`.
- No general memoization or tabling layer was added. The branch reached its
  practical goal through canonicalization plus explicit reuse of the ADR-0012
  list-family materializer, not through a generic recursive-state cache.
- ADR-0012 did not become unnecessary. It remains the explicit closed-answer
  API and bounded fallback materializer, even though its list-family fast path
  is now also reused by `query-answers`.

## Follow-Up

- Treat the current result honestly: for known list families, the public generic
  API now presents closed answers, but the raw kernel diagnostics still expose
  the underlying symbolic frontier.
- Keep `query-parity-answers` as the explicit extensional API for callers that
  need closed-answer-only semantics by contract.
- Revisit generic memoization or tabling only if another family shows the same
  "diagnostics symbolic, public API still needs help" shape and the repo needs a
  broader answer than the current list-family pull-in.
