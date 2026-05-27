# 2026-04-30 ADR-0031 Experiment Reassessment

## Context

The guarded-clause IR work improved the ADR-0031 list-family matrix, but later
experiments showed that a narrow `reverse(r, [b,a])` improvement did not
generalize to length-three reverse rows. That is too weak to justify extra
answer-overlay call-order machinery by itself.

## Experiments Not Retained

### Adaptive Constructor-Demand Call Ordering

The answer overlay was briefly extended to keep both source call order and an
alternate constructor-demand call order, selecting the demand order when the
first source call did not expose walked constructor structure.

Result:

- `reverse(r, [b,a])` closed in the CI-safe raw matrix.
- `reverse([a,b,c], r)` and the output-tail reverse row still did not close.
- The improvement was therefore mode- and size-specific rather than
  family-level.

Decision: reverted. The branch should not keep that complexity unless a later
version demonstrates broader reverse-family improvement.

### Strict Residual Deferral

The answer overlay was changed experimentally so procedure-call residuals were
exported only after recursive call-depth was exhausted.

Result:

- Existing shallow answer rows remained correct.
- Passing rows became substantially slower.
- Length-three reverse rows still did not close.

Decision: not retained.

### Residual Frontier Re-Settlement

The answer overlay was changed experimentally to revisit deferred residual
calls before exporting an answer record, attempting to close residuals that had
become determined by later bindings.

Result:

- Passing rows became slower.
- Existing inverse append coverage regressed from three raw closed targets to
  two under the CI-safe limit.
- Length-three reverse rows still did not close.

Decision: not retained.

## Duplicate-State Signal

Diagnostics for `reverse([[a],[b],[c]], r)` with raw limits `4`, `8`, and `16`
showed mostly unique proof signatures. Exported records had some duplicate
frontiers after canonical answer merging, but the raw proof stream itself was
not dominated by repeated proof signatures.

This weakens the case for immediate tabled duplicate-state suppression as the
next ADR-0031 step. The blocker appears more like proof-search shape and
frontier ordering than simple rediscovery of identical states.

## Current Direction

The retained progress remains the guarded-clause IR and guard-first descent
from the source-to-IR boundary. Further ADR-0031 work should target a broader
constructor-recursive search discipline, not a mode-specific call-order tweak.

One possible broader avenue is improving the underlying `core.logic` host
language performance. This could be tableau-prover specific if the handling is
generic across Proflog programs, or it could be a fully general-purpose
`core.logic` improvement useful outside Proflog. Either way, the improvement
must be justified as a reduction in relational search, unification, scheduling,
tabling, or constraint-propagation overhead rather than as list-family special
knowledge.

That avenue should be treated as its own research-and-deployment task, not as a
small in-repo tweak. Proflog currently declares `org.clojure/core.logic` as a
normal dependency, so a patched implementation must be deliberately selected.
Any future branch pursuing this path should:

- study the relevant upstream `core.logic` internals before patching;
- document which revision or artifact was modified;
- update the Proflog dependency/deployment sequence so the patched
  implementation is actually loaded;
- add a classpath or runtime verification step for the patched implementation;
  and
- compare matrix timings against the unpatched dependency under the same
  probes.
