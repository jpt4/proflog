# ADR-0014: Generic Legacy Unsatisfied Family Evaluation

- Status: accepted
- Date: 2026-04-24
- Branch: `adr-0014-generic-legacy-evaluation`
- AAR: [AAR-0014](../aar/AAR-0014-generic-legacy-evaluation.md)

## Context

ADR-0012 and ADR-0013 closed the legacy reverse / append answer surface, and
the inherited experimental probes now show behavior beyond the original legacy
scope as well. But that closure came with an important qualification:

- the public answer surface now reaches parity and beyond for those known list
  families,
- the raw kernel diagnostics underneath remain symbolic,
- and the completed answer surface depends on extra-kernel stream processing and
  known-family materialization rather than on pure kernel behavior alone.

That distinction matters for the next stage of the project.

In a relational system, execution is best understood as producing a
semidecidable answer stream. A correct answer may:

1. appear directly in the raw stream early,
2. appear only after substantial fair search,
3. already be present but obscured by equivalent or shallower frontiers and
   therefore require generic stream sifting above the kernel,
4. or fail to appear at all within the current relational engine.

Legacy development stalled on exactly this problem. The group-verifier and other
still-unsatisfied families did not clearly fail because of one isolated bug;
work instead drifted into undirected performance tuning without a disciplined
account of whether the desired answers were absent from the raw stream, merely
late, or recoverable only after extra-kernel interpretation.

The greenfield project now needs a dedicated branch for the legacy families that
remain unsatisfied, especially:

- the legacy group-verifier `GV` family,
- the finite-domain `FD` family,
- and any remaining legacy tests that still lack a greenfield determination.

This next branch should push as far as possible in a generic and purely
relational manner before authorizing new family-specific handlers.

## Decision

- Preserve an explicitly accessible pure-core proof surface. The core tableau
  prover should remain callable as directly as possible, while bounded `fuel`,
  bounded `call-depth`, raw proof collection limits, answer ranking, closed
  answer filtering, and any family-specific materializers remain documented
  overlays above that core rather than becoming part of the meaning of
  `prove`.
- Create a dedicated branch to evaluate the still-unsatisfied legacy families as
  answer-stream problems before adding new family-specific materializers.
- Treat each promoted legacy query at three explicitly documented layers:
  - raw kernel / generic symbolic answer stream,
  - generic stream sifting or post-processing above that stream,
  - specialty family handling only if the first two layers prove insufficient.
- Prefer generic relational methods and generic stream processing over
  data-structure-specific or theory-specific handlers.
- Make the layer distinction explicit in tests and docs. For every target query,
  record whether the desired answer is:
  - absent from the raw stream within measured bounds,
  - present but late,
  - recoverable by generic post-processing,
  - or only recoverable by specialty handling.
- Start with the hard legacy families that previously resisted closure:
  - promote representative `GV` tests first,
  - then at least one `FD` or comparably unsatisfied family,
  - and document any remaining unsatisfied set that is deferred within the
    branch after those first promotions.
- Keep slower exploratory probes out of the default and current extended
  selectors unless and until they are proven stable enough to belong there.

## Consequences

- The project gets a disciplined method for talking about hard legacy queries:
  not just "too slow" or "needs optimization," but which layer currently fails
  to expose the desired answer.
- This branch may show that some unsatisfied families are fundamentally search
  fairness or answer-stream ordering problems rather than semantic
  incompleteness.
- It may also show the opposite: that some legacy families, including the group
  verifier, expose a real need for new generic relational machinery such as
  memoization, tabling, better fairness, or principled domain bounding.
- The branch deliberately raises the bar for new specialty handlers. If a new
  family-specific evaluator is proposed, the repo should already know that:
  - the raw stream did not expose the answer within documented bounds, and
  - generic stream processing was not enough.
- Novel data-structure or theory support should not be inferred casually from
  one-off handlers. If the project begins to support new families this way, that
  becomes a language-design question and must be documented explicitly.
- The branch now has an additional architectural obligation: every operational
  deviation from the pure core should be nameable and configurable as an
  overlay. This makes it possible to ask two different questions cleanly:
  - what the core tableau prover itself can produce,
  - and what a bounded or answer-oriented overlay can recover above it.

## Initial Probe Boundary

The first ADR-14 raw-kernel list probes were run through
`proflog.legacy-stream-probe`, which bypasses `query-answers` and its list
 fast path and calls the raw kernel/export path directly.

Measured on `2026-04-24` with external `timeout -k 30s 900s` wall-clock bounds:

- `reverse([a,b], r)` with `fuel=nil` and `call-depth=1` exhausts by raw limit
  `8` (five raw proof states, four unique exported symbolic records) and never
  exports the closed witness `r = [b,a]`.
- `reverse([a,b], r)` with `fuel=nil` and `call-depth=2`, `3`, `4`, and `nil`
  reaches raw limits `1`, `2`, and `4` with no closed witness, then fails to
  complete the next raw slice within fifteen minutes.
- `append([a], [b,c], z)` with `fuel=nil` and `call-depth=nil` exhausts by raw
  limit `8` (six raw proof states, three unique exported records) and never
  exports the closed witness `z = [a,b,c]`.
- `append(x, y, [a,b,c])` with `fuel=nil` and `call-depth=nil` immediately
  exports the correct base split `x = [], y = [a,b,c]`, but by raw limit `8`
  it still has not exported the other three closed legacy splits, and the next
  raw slice did not complete within fifteen minutes.

So the current measured boundary is:

- the pure-core path is accessible for direct probing,
- unbounded `fuel` and unbounded `call-depth` are available there,
- but the raw kernel/export stream still does not produce full reverse or
  append synthesis parity within the measured long slices.

## Kernel Comparison

The first ADR-14 kernel comparison between legacy and greenfield has now been
recorded as an explicit architectural result.

The salient differences are:

- Legacy uses one `proveo` path for both ordinary proof and open synthesis.
  Greenfield splits ordinary proof search from answer export and answer-mode
  search control.
- Greenfield carries explicit kernel branch state:
  - equality substitution,
  - symbolic disequality store,
  - residual deferred calls,
  - answer-mode flags and budgets.
  Legacy instead leans on branch-local equality rewriting, paramodulation, and
  lemma threading.
- Legacy procedure calls fire directly inside the ordinary prover, with a
  projected `L-ground` guard that rejects only terms containing `(par ...)`.
  Bare logic variables therefore pass. Greenfield also admits `(var ...)`
  structurally, but in answer mode recursive descent competes with explicit
  residual deferral and `call-depth` control.
- Legacy has no separate answer-mode flow in the kernel. Greenfield does, and
  that distinction is now important enough to count as an architectural
  decision rather than an incidental implementation detail.

The comparison does not yet prove that the separate greenfield answer-mode flow
is the sole cause of the current gaps. But it does identify it as one of the
largest structural differences between the two provers.

## Initial GV Probe Set

ADR-14 now also includes an exploratory greenfield probe runner for the legacy
group-verifier family:

- source: `src/proflog/gv_probe.clj`
- alias: `lein probe-proflog-gv`

This runner rebuilds the legacy `GV` formulas in the greenfield AST and reports
the greenfield query surfaces separately (`query-succeeds`, `query-fails`, and
`query-status`) instead of conflating them.

Measured on `2026-04-24`:

- `Z₂` identity (`gv_identity`) resolves as `:succeeds` under
  `query-status` with a `5000 ms` budget.
- `Z₂` closure (`gv_closure`) remains `:unresolved` at the same `5000 ms`
  status probe.
- `Z₂` inverses (`gv_inverses`) remain `:unresolved` at the same `5000 ms`
  status probe.
- `Z₁` full 7-universal associativity (`gv_assoc`) remains `:unresolved` with
  a `15000 ms` status probe.
- `Z₂` precomputed associativity (`gv_assoc_pre`) did not return a result
  before the external `60 s` shell timeout on a `5000 ms` status probe.
- `Z₂` full 7-universal associativity (`gv_assoc`) did not return a result
  before the external `60 s` shell timeout on a `15000 ms` status probe, and
  the direct `query-succeeds` probe also timed out at the same external bound.
- Non-group full 7-universal associativity also did not return a result before
  the external `60 s` shell timeout on the current greenfield probes.

Update on `2026-04-25`:

- the repo now includes a dedicated hard-family selector:
  `lein test-proflog-hard-families`,
- `src/proflog/equality_fast_path.clj` adds a host-side accelerator for the
  shape "nested existentials over an equality / disequality conjunction",
- `src/proflog/hard_family_overlay.clj` exposes that accelerator as a named
  non-default overlay above the ordinary query surface,
- the isolated pure query surface still leaves `Z₁` full 7-universal
  associativity (`gv_assoc`) unresolved under the `2000 ms` regression budget,
- and ADR-14 now also includes one promoted representative `FD` query through
  the same named overlay: `warm-unique`, which resolves as `:succeeds` there
  under the same `2000 ms` budget.

These first probes matter because they are not merely "hard legacy cases." They
already include formulas that legacy *could* answer, such as precomputed
associativity and the `Z₁` full associativity case. So the current picture is
not "greenfield can answer a different overlapping subset than legacy." The
current picture is that greenfield appears strictly weaker on the first `GV`
slice, aside from the simple identity success.

That historical conclusion is now narrower than it first appeared. Greenfield
still remains weaker than legacy on the broader isolated `GV` pure-kernel
slice, especially `Z₂` precomputed and full associativity, but the repo now has
one explicit named overlay that recovers additional legacy-aligned successes
without claiming they came from the core kernel itself.

## Paramodulation Investigation

The branch also examined the legacy equality-rewriting / paramodulation path
directly.

The result is more specific than "legacy paramodulation is impure."

- The legacy rewrite subsystem itself is written relationally:
  `collect-eqso`, `rewrite-termo`, `rewrite-term-with-eqso`, `eq-neq-closeo`,
  and `para-free-closeo` are defined with `conde`, `fresh`, `membero`,
  `appendo`, `selecto`, `==`, and `!=`, without `project`.
- The surrounding legacy prover is still not purely relational end to end.
  It uses `project` in `subst-termo`, the `L-ground` guard,
  `propagate-par-eqo`, and bounded `gamma-budget` control.
- So the accurate architectural statement is:
  legacy paramodulation is mostly encoded as relations, but it lives inside a
  prover that still mixes relational search with one-way meta-level guards.

For greenfield, a purely relational transcription of the legacy rewrite
machinery is therefore possible in principle. But it should not be confused
with the temporary host-side fast path that was rolled back from the kernel.

The current greenfield kernel already gives equality a different semantic
center:

- explicit substitution `sigma`,
- symbolic disequalities,
- saved-atom contradiction checks,
- and saved-call reopening after equality walking.

That means a future relational paramodulation layer should be treated as an
optional relational extension or alternate kernel surface, not as an opaque
host-side shortcut inside `proflog.kernel`.

## Architectural Implication

If later ADR-14 work uncovers a genuinely different greenfield capability slice
on `GV` or `FD`, then the current architecture likely deserves further
refinement rather than replacement.

If that does *not* happen, then the repo now has explicit grounds to consider a
more substantial revision, including:

- shrinking or removing the separate answer-mode flow inside the kernel, and
- re-establishing answer-oriented behavior as a documented overlay above a more
  uniform core prover.

That is now an explicitly recorded downstream architectural question, not an
informal suspicion.

## Test Obligations

- Add a dedicated exploratory selector for legacy-unsatisfied-family probes, so
  long-running `GV` / `FD` work is isolated from the ordinary regression path.
- Promote at least one representative `GV` query into a greenfield exploratory
  namespace with an explicit expected answer surface and a failing or initially
  bounded regression.
- Promote at least one additional unsatisfied legacy query outside the list
  family, ideally from `FD`, under the same discipline.
- Add probe helpers or diagnostics tests that can distinguish:
  - raw-stream presence,
  - generic post-processing recovery,
  - and outright absence within the measured slice.
- Add or update worked examples for at least one hard unsatisfied family so the
  repo shows the exact query, bounds, stream shape, and current conclusion.

## Exit Criteria

- The repo has an explicit set of promoted legacy-unsatisfied target queries.
- At least one representative `GV` query has been evaluated end to end across:
  - raw kernel stream,
  - generic answer-layer stream processing,
  - and, if necessary, documented fallback handling.
- At least one additional non-`GV` unsatisfied legacy family has been evaluated
  under the same method.
- The branch concludes, query by query, whether the desired answer is:
  - already present in the raw stream,
  - present but impractically late,
  - recoverable by generic post-processing,
  - or still absent.
- The branch leaves behind a clear architectural recommendation for the next
  step:
  - continue generic relational engine work,
  - add a general-purpose stream-processing layer,
  - or explicitly justify a new specialty handler.
