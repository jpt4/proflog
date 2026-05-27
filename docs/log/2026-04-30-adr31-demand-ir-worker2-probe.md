# 2026-04-30 ADR-0031 Demand IR Worker 2 Probe

## Scope

This probe explored a small generic query-demand propagation step between the
compiled guarded-clause IR and the kernel-facing guarded negative-call
sequence. The prototype deliberately did not inspect relation names or
constructor names.

The attempted rule was:

- after equality guard saturation, inspect the remaining negated procedure
  calls in one guarded alternative;
- keep source order when the first remaining call already exposes a walked
  positive-arity constructor argument;
- otherwise choose a later call with visible constructor demand before falling
  back to source order.

This is the local analogue of a magic-set demand step: demand from the current
query bindings may select a more constrained recursive subgoal before a fully
open one.

## Result

The prototype was not retained.

Two implementation variants were tried in `src/proflog/kernel.clj` and
`src/proflog/answer_overlay.clj`:

- a fully relational "first demanded call" selector with an explicit
  no-visible-constructor complement;
- a committed selector that preserved source order when the first call was
  demanded and otherwise selected a demanded later call.

Both stayed generic, but neither was a mergeable small patch:

- the complement-based selector made the reverse answer rows exceed a 75 second
  subprocess timeout;
- the committed selector kept the code fast enough but changed the raw answer
  stream so `reverse([a,b], r)` no longer produced the existing closed target
  within the CI-safe raw limit;
- raising that row's raw limit to 12 still produced no closed record under the
  committed selector.

Representative retained baseline after backing out the prototype:

```clojure
{:id :reverse-output-flat
 :target-found? true
 :closed-count 1
 :raw-count 4
 :elapsed-ms 10489.260389}

{:id :reverse-input-flat
 :target-found? false
 :closed-count 0
 :raw-count 4
 :elapsed-ms 35850.759136}
```

The key lesson is that demand detection cannot be a casual relational test in
the hot proof stream. Even when the test is generic, it can add proof families,
alter answer prioritization under `raw-limit`, or compete with the residual
fallback path.

## What Would Be Needed

A viable demand or magic-set-style pass likely needs one of these stronger
designs:

- compile query-pattern-specific guarded IR outside the proof stream, with
  demand facts represented as data rather than discovered by extra relational
  search;
- carry a canonical demand summary alongside guarded alternatives and use it to
  order already-known finite call sequences without generating additional proof
  witnesses;
- update answer-record prioritization or raw-limit accounting so proof-shape
  variants introduced by demand scheduling cannot displace a closed target;
- verify on multiple rows at once, at minimum preserving `reverse-output-flat`
  and `reverse-output-nested` while improving `reverse-input-flat` and a nested
  input-synthesis counterpart.

Until those pieces exist, the retained guarded-clause IR remains the safer
baseline and this demand-selection approach should not be promoted as ADR-0031
progress.
