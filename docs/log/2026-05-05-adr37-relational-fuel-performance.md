# ADR-37 Relational Fuel Performance Probe

Date: 2026-05-05
Branch: `adr-0037-core-logic-minikanren-enhancements`

## Question

Should the ADR-36 relational-arithmetic `step-fuelo` adapter replace the current
finite-domain `step-fuelo`? Semantically it reduces the number of core.logic
subsystems involved in Proflog proof search, but only if the runtime cost is not
large enough to outweigh that simplification.

## Probe

Added `proflog.relational-fuel-performance-probe`.

The probe compares:

- production `proflog.kernel-support/step-fuelo`, implemented with
  `clojure.core.logic.fd`;
- `proflog.relational-fuel-adapter-probe/step-fuelo`, implemented with
  ADR-36 bit-list relational arithmetic.

Each case runs both implementations in one JVM, warms both sides, alternates
sample order, and reports mean/median ratios. Integrated cases temporarily
rebind `kernel-support/step-fuelo` only for the measured body.

Command:

```text
timeout -k 10s 600s lein run -m proflog.relational-fuel-performance-probe
```

## Results

Mean time per iteration from the completed local run:

| case | FD ms/iter | rel-arith ms/iter | rel/FD mean |
| --- | ---: | ---: | ---: |
| direct step, fuel 8 | 0.934 | 2.547 | 2.727 |
| direct reverse step, next fuel 7 | 1.023 | 1.913 | 1.871 |
| direct three-step chain | 2.428 | 2.212 | 0.911 |
| kernel direct closure, fuel 8 | 13.657 | 16.806 | 1.231 |
| kernel gamma closure, fuel 8 | 32.431 | 36.665 | 1.131 |
| `query-succeeds` ground `step(2, 1)` | 132.946 | 135.862 | 1.022 |
| `query-fails` ground `step(0, 1)` | 94.426 | 106.413 | 1.127 |
| answer partial `step(x, 1)` | 1006.426 | 1000.074 | 0.994 |
| answer composed `jump(x, 0)` | 5220.091 | 5450.853 | 1.044 |

Validation samples showed the same proof or exported answer shape on integrated
surfaces. Direct fuel synthesis still exposes different finite representations:
FD returns host integers, while the adapter returns ADR-36 bit-list numerals.

## Assessment

The relational-arithmetic adapter is significantly slower for a single direct
ground or reverse `step-fuelo` call. That is the expected cost of replacing a
specialized finite-domain addition constraint with structural bit-list
relations plus boundary conversion.

That direct slowdown does not dominate the Proflog surfaces measured here. Once
`step-fuelo` is embedded in kernel, query, or answer-overlay work, the observed
mean ratios were:

- 1.022x to 1.127x for the direct query rows;
- 0.994x to 1.044x for the answer-mode rows;
- 1.131x for the gamma kernel row;
- 1.231x for the very small direct closure row, where fuel stepping is a larger
  fraction of total work.

This is an acceptable performance delta for an opt-in/profiled replacement
candidate. The performance evidence no longer blocks the semantic simplification
of removing finite-domain arithmetic from fuel stepping.

It is still not a production replacement decision. The remaining blocker is the
public representation boundary: open or reverse finite fuel synthesis currently
returns internal bit-list numerals rather than host integers.

## Decision

Keep the relational-arithmetic `step-fuelo` path as the preferred ADR-37
candidate for a future fuel profile, because it simplifies semantic analysis and
does not show unacceptable integrated runtime regression in this probe.

Do not replace production `step-fuelo` yet. Before promotion:

- decide the public behavior for direct open/reverse finite fuel synthesis;
- re-run this probe and the focused semantic tests after any representation
  boundary change;
- add broader workload timings if the profile is wired into production query
  defaults.
