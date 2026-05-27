# AAR-0036: Speculative Relational Arithmetic and Core.logic Tabling

- Date: 2026-05-05
- Related ADR: [ADR-0036](../adr/ADR-0036-speculative-relational-arithmetic-and-tabling.md)
- Outcome: completed with arithmetic retained as an opt-in probe and production fuel unchanged

## What Happened

ADR-0036 translated faster-minikanren-style bit-list arithmetic into Clojure
core.logic, ported the relevant upstream arithmetic tests, and used the result
to probe whether Proflog fuel stepping could move away from finite-domain
constraints.

The branch also reassessed direct `core.logic/tabled` as a possible replacement
for Proflog's canonical proof-state tabling layer.

## What Worked

The arithmetic translation is useful as a reusable relation library and evidence
surface. The upstream-style arithmetic, factorization, comparison, subtraction,
and small interpreter tests run under the project-local miniKanren constraint
overlay.

The relational fuel adapter can preserve normal public entry calls where users
pass finite host integers or `nil`, and integrated kernel/query/answer probes
match production answer shapes. Focused timing showed direct `step-fuelo` calls
are slower, but the measured integrated Proflog surfaces stay close enough for
the adapter to remain a credible opt-in candidate.

The tabling probe clarified the boundary: Proflog's tabling layer is not a thin
duplicate of raw `core.logic/tabled`. It adds project-specific canonical
proof-state keys, and the measured ADR-0035 rows do not justify replacing it
with raw core.logic tabled goals.

## What Did Not Change

Production `proflog.kernel-support/step-fuelo` remains the finite-domain
host-integer relation. Direct open/reverse finite fuel synthesis is a public
representation question: FD fuel returns host integers, while the adapter
returns ADR-36 bit-list numerals. ADR-0036 deliberately does not hide that by
adding a bounded projection layer.

No production core.logic engine patch or raw tabling replacement was retained.

## Verification

Focused verification for the closeout branch:

```text
lein test proflog.relational-arithmetic-test proflog.relational-arithmetic-upstream-test proflog.minikanren-constraints-test proflog.relational-fuel-adapter-probe-test proflog.relational-fuel-replacement-test proflog.relational-maps-probe-test proflog.l-ground-constraint-probe-test proflog.core-logic-disequality-probe-test
  Ran 48 tests containing 233 assertions.
  0 failures, 0 errors.

lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

lein probe-core-logic-tabling
  :active-tabled-answer-path? true

timeout -k 10s 600s lein probe-relational-fuel-performance
  completed 9 cases; integrated relational/FD mean ratios ranged from 0.994 to
  1.231, while direct one-step rows were 1.871 to 2.727x slower.
```

Supporting evidence is recorded in related logs:

- [ADR-36 Relational Arithmetic and Tabling Probes](../log/2026-05-03-adr36-relational-arithmetic-and-tabling-probes.md)
- [ADR-37 Step Fuel Replacement Test](../log/2026-05-03-adr37-step-fuel-replacement-test.md)
- [ADR-37 Relational Fuel Performance Probe](../log/2026-05-05-adr37-relational-fuel-performance.md)

## Follow-Up

Any future fuel promotion must explicitly choose the public answer
representation for fuel-as-an-answer:

- keep host integers by adding a bounded public projection layer;
- expose bit-list numerals as the finite relational fuel representation; or
- support relational arithmetic only at ground public entry and document direct
  open/reverse fuel synthesis as internal.
