# AAR-0037: Core.logic miniKanren Feature and Performance Enhancements

- Date: 2026-05-05
- Related ADR: [ADR-0037](../adr/ADR-0037-core-logic-minikanren-enhancements.md)
- Outcome: completed with a project-local constraint overlay and probe surfaces retained

## What Happened

ADR-0037 evaluated whether Proflog should carry project-local miniKanren
features or core.logic performance changes before replacing finite-domain fuel
or adopting broader generic constraints in production proof search.

The branch added `proflog.minikanren-constraints`, moved the ADR-36 upstream
arithmetic tests onto that overlay, broadened the `absento` implementation into
a project-owned deep absence constraint, and kept `symbolo` and `numbero` as
`predc`-backed compatibility relations.

It also recorded probes for relational maps, L-ground/tree constraints,
disequality maintenance performance, relational fuel replacement, and
relational fuel timing.

## What Worked

The overlay now covers the general-purpose `absento` behavior needed by the
translated arithmetic suite: delayed open terms, open targets, compound
targets, vectors, map keys and values, upstream push-down orderings,
same-variable target/node rejection, and residual reification in the public
`absento` vocabulary.

The relational fuel adapter is semantically viable on fixed public entry fuel
and on measured integrated proof surfaces. It remains useful as a future profile
candidate because performance no longer blocks the experiment.

The performance and integration audits were useful precisely because they
prevented premature production changes. No current Proflog workload justified a
core.logic engine patch, raw tabling replacement, or immediate replacement of
production fuel arithmetic.

## What Did Not Change

Production proof search remains on the existing core kernel and answer-overlay
paths. Production fuel remains FD-backed host integer or `nil` fuel. The
relational fuel adapter, relational maps, L-ground/tree probes, and disequality
timing harness are explicit ADR/probe surfaces, not default semantics.

Native-style positive type constraints for `symbolo`, `numbero`, and future
`stringo` remain a later slice. So does any normalized shared constraint store
for type and absence constraints.

## Verification

Focused verification for the closeout branch:

```text
lein test proflog.relational-arithmetic-test proflog.relational-arithmetic-upstream-test proflog.minikanren-constraints-test proflog.relational-fuel-adapter-probe-test proflog.relational-fuel-replacement-test proflog.relational-maps-probe-test proflog.l-ground-constraint-probe-test proflog.core-logic-disequality-probe-test
  Ran 48 tests containing 233 assertions.
  0 failures, 0 errors.

lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

timeout -k 10s 600s lein probe-relational-fuel-performance
  completed 9 cases; integrated relational/FD mean ratios ranged from 0.994 to
  1.231, while direct one-step rows were 1.871 to 2.727x slower.
```

Related logs:

- [ADR-37 Constraint Port Assessment](../log/2026-05-03-adr37-constraint-port-assessment.md)
- [ADR-36/37 Polish Before Main Integration](../log/2026-05-05-adr36-37-polish.md)
- [ADR-37 Relational Fuel Performance Probe](../log/2026-05-05-adr37-relational-fuel-performance.md)

## Follow-Up

The next implementation direction is not another fuel or host-engine pass. It
should evaluate deep and complex Fitting Proflog programs in greenfield through
the core proof kernel: once source programs are translated into the kernel's
formula representation, success/failure/undefined results should be established
by proof search rather than host-side computation or named overlays.
