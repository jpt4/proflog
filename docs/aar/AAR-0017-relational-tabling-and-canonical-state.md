# AAR-0017: Relational Tabling And Canonical State

- Date: 2026-04-26
- Related ADR: [ADR-0017](../adr/ADR-0017-relational-tabling-and-canonical-state.md)
- Outcome: complete with documented raw-list limitations

## What Happened

ADR-0017 added a separate `proflog.tabling` namespace for canonical proof-state
keys and kernel-level tabled proof search.

The implementation keeps `proflog.kernel` readable by exposing only one
optional recursive proof dispatcher. The table management, canonical keys,
core.logic tabled relation, and duplicate-state instrumentation live outside
the kernel.

The canonical key deliberately under-merges. It includes agenda formulas after
lexical environment substitution, saved literals, disequalities, equality
substitution shape, proof-variable eligibility, fuel, call-depth, residuals,
and compiled-program identity.

## What Worked

- Canonical keys are stable across alpha-equivalent noms and order-insensitive
  branch stores.
- Negative key tests now cover different predicates, substitutions, programs,
  lexical environments, proof-variable sets, and bounded fuel slices.
- A tabled-vs-untabled recursive program regression passes under fixed fuel.
- A duplicate beta-substate regression shows a repeated canonical branch state
  is evaluated once inside one tabled run.
- `lein test-proflog-fast` remains green, so the ordinary kernel path still
  works without importing the tabling layer.

## List-Family Report

All timings below are targeted runs on 2026-04-26 and include Lein/JVM startup
time.

| Probe | Result |
|---|---:|
| Greenfield `query-answers` reverse `reverse([a,b], r)` | pass, `9.78 s` |
| Greenfield `query-answers` inverse `append(x, y, [a,b,c])` | pass, `8.30 s` |
| Greenfield list answer `append([a], [b,c], z)` | pass, `8.54 s` |
| Greenfield nested append answer `append([[a]], [[b]], z)` | pass, `8.45 s` |
| Greenfield nested suffix answer | pass, `8.57 s` |
| Legacy `Y10` reverse synthesis | pass, `11.48 s` |
| Legacy `Y12` inverse append synthesis | pass, `11.86 s` |
| Legacy `Y04` append two-step ground proof | pass, `11.83 s` |
| Legacy `Y08` reverse two-element ground proof | pass, `11.59 s` |
| Greenfield `append([a,b], [c], [a,b,c])` raw proof | timed out at `180 s` |
| Greenfield `reverse([a,b], [b,a])` raw proof | timed out at `180 s` |
| Greenfield tabled `reverse([a,b], [b,a])` raw proof | timed out at `180 s` |

The conclusion is narrow but important: the current greenfield answer surface
now beats or matches legacy for the known list answer-export cases, but legacy
still strongly outperforms greenfield on raw multi-step ground list proofs.
ADR-0017's kernel-level tabling is not enough to recover those raw proofs.

## What Did Not Work

- Tabling recursive kernel substates did not materially improve the expensive
  raw list proof cases. The table keys are intentionally conservative, and the
  remaining list blow-up appears to occur before enough identical canonical
  substates are revisited to pay for tabling.
- The answer overlay is not tabled by this ADR. Its known list-family fast path
  remains a higher-level answer-surface recovery mechanism.
- The full extended suite still should not be treated as a routine closeout
  gate until the raw list proof namespace is split or bounded more explicitly.

## Verification

- `lein test-proflog-fast`
- `lein test proflog.tabling-test`
- `lein test proflog.kernel-test`
- `timeout -k 10s 240s lein test proflog.answers-test`
- targeted timed list and legacy selectors recorded above

## Follow-Up

- Treat raw multi-step list proofs as the next performance problem, not as
  solved by ADR-0017.
- If tabling is extended further, table the answer overlay separately from the
  kernel and include residual/call-depth state in its keys.
- Investigate why legacy closes `Y04` and `Y08` quickly while greenfield raw
  proof search exceeds 180 seconds; likely candidates are clause-entry control,
  equality wakeups, and the cost of fair agenda branching before canonical
  states repeat.
