# ADR-0037: Core.logic miniKanren Feature and Performance Enhancements

- Status: completed
- Date: 2026-05-03
- Branch: `adr-0037-core-logic-minikanren-enhancements`
- AAR: [AAR-0037](../aar/AAR-0037-core-logic-minikanren-enhancements.md)
- Depends On:
  - [ADR-0032](ADR-0032-core-logic-performance.md)
  - [ADR-0036](ADR-0036-speculative-relational-arithmetic-and-tabling.md)

## Context

ADR-0036 imported faster-minikanren-style relational arithmetic to test whether
`step-fuelo` can stop depending on finite-domain arithmetic constraints. The
reason to do that is not aesthetic: hardcoded finite-domain ranges can block or
distort reverse and partial synthesis modes when the domain bound becomes part
of the search semantics.

That import exposed a broader host-library question. Core.logic was chosen
because it brings miniKanren to Clojure with useful host integration and a mature
nominal logic subsystem. It is not necessarily a strict superset of other
miniKanren implementations. The translated upstream arithmetic tests needed
`symbolo` and `absento`; core.logic 1.0.1 exposes lower-level constraint
machinery that can express them, but does not expose those relations directly.

## Decision

Create a separate speculative ADR branch to evaluate whether this project should
carry a project-local enhanced core.logic profile or overlay before replacing
production fuel arithmetic.

The branch should be run as a delegated research and implementation track.
Production Proflog behavior must not be changed until the evidence is concrete.

Closeout decision: retain the project-local constraint overlay and probe
namespaces as explicit ADR evidence, keep production proof search unchanged, and
keep finite-domain `kernel-support/step-fuelo` as the public fuel relation until
a later ADR accepts a representation migration or bounded projection layer.

## Work Tracks

1. Survey faster-minikanren and canonical Scheme/Racket miniKanren
   implementations for features core.logic lacks or underexposes.
2. Identify low-risk core.logic additions first, especially `symbolo`,
   `numbero`, `absento`, and related tree constraints needed by imported
   relational arithmetic tests.
3. Revisit core.logic generic performance TODOs and known hot paths without
   weakening miniKanren semantics.
4. If a project-local core.logic overlay is justified, add it behind an explicit
   profile or vendor boundary with tests against upstream-style behavior.
5. Re-examine the Proflog greenfield codebase for places where the improved
   constraint vocabulary or generic optimizations can replace project-specific
   membership/type checks or improve tableau proof handling.

## Coordinator Survey Result

The delegated coordinator pass is recorded in
[ADR-37 Coordinator Survey](../log/2026-05-03-adr37-coordinator-survey.md).

Three subordinate read-only subagents were spawned on separate local branches:

- `adr-0037-subagent-feature-survey`
- `adr-0037-subagent-core-logic-audit`
- `adr-0037-subagent-proflog-integration`

Their combined result narrows the next implementation slice:

- expose canonical symbolic constraints first: `symbolo`, `numbero`, and
  `absento`;
- use the new overlay to replace ADR-36 test-local shims before adopting it in
  production Proflog code;
- keep relational arithmetic focused on a fuel adapter/profile that preserves
  the public `nil` or integer fuel API until a migration is explicitly accepted;
- keep raw direct `core.logic/tabled` replacement closed by ADR-36;
- defer core.logic engine optimization until a focused bottleneck survives the
  existing ADR-32 negative micro-patch evidence.

## Phased Plan

1. **Constraint overlay.** Add project-local `symbolo`, `numbero`, and
   `absento` relations using existing core.logic constraint machinery where
   possible. Test ordering, delayed open-term behavior, residual reification,
   generalized targets, and interaction with disequality.
2. **Arithmetic shim cleanup.** Move the ADR-36 translated arithmetic tests off
   their test-local `symbolo` and `absento` definitions and onto the overlay.
3. **Fuel adapter probe.** Prototype bit-list relational fuel behind an opt-in
   adapter/profile. Do not replace production `step-fuelo` while callers still
   rely on host integers unless the adapter preserves that API.
4. **Proflog integration audit implementation.** Try generic constraints in
   Proflog type and absence checks only where explicit tests preserve
   proof-variable, answer-variable, and rigid-parameter distinctions.
5. **Engine optimization.** Revisit vendored core.logic internals only after
   the lower-risk overlay and fuel probes identify a concrete need. The first
   likely target is disequality maintenance; excluded near-term targets are raw
   tabling replacement, nominal unification changes, global occurs-check
   removal, and finite-domain propagation redesign.

## Closeout

Phase 1 has a project-local overlay in `proflog.minikanren-constraints`.
`symbolo` and `numbero` remain `predc`-backed positive type checks, but
`absento` has been promoted from a direct `treec` wrapper into a project-owned
deep absence constraint. The current `absento` handles delayed open terms,
compound and open targets, vectors, map keys and values, upstream push-down
orderings, same-variable target/node rejection, and canonical residual
reification in the public `absento` vocabulary.

Phase 2 is complete for the ADR-36 translated arithmetic suite: the upstream
arithmetic/interpreter tests now use `proflog.minikanren-constraints` instead
of test-local `symbolo` and `absento` shims.

Phase 3 has an opt-in relational fuel adapter probe. The direct finite-domain
fuel assessment is narrowed rather than negative: current fixed-fuel
reverse/partial answer synthesis is not blocked by FD fuel alone, but open or
reverse synthesis of fuel itself still inherits the hardcoded host integer
domain. The adapter now has expanded tests for small finite boundaries,
internal bit-list chains, kernel rule shapes, direct query proof surfaces, and
answer-mode export parity. A focused performance probe shows a real direct
micro-cost for single `step-fuelo` calls, but only modest integrated overhead on
the measured Proflog surfaces. That keeps the relational arithmetic replacement
relevant as an opt-in/profiled path before any production change.

The Proflog integration and core.logic performance audits are recorded as
separate logs. The integration scope is broader than simply adopting existing
miniKanren constraints: ADR-37 should also look for Proflog-motivated generic
core.logic improvements, such as relational maps or delayed tree predicates,
when those features would be generally useful outside Proflog. Their closeout
recommendation is conservative: prototype those capabilities behind isolated
probes and keep production proof search unchanged until the evidence is clear.

The second ADR-37 worker phase is complete:

- relational maps: `b311dbe`, with canonical association lists preferred over
  open Clojure persistent maps;
- L-ground/tree constraints: `652c42f`, with `treec` useful for no-`par`
  absence but not a replacement for strict Proflog term recognition;
- disequality performance: `dd3d2dc`, with no core.logic engine patch justified
  by current Proflog measurements;
- relational fuel replacement: `5e8b643`, semantically viable as an
  opt-in/profiled path; the later performance probe kept it viable, but it is
  not production-ready until public fuel representation is settled.

The constraint-port decision is recorded in
[ADR-37 Constraint Port Assessment](../log/2026-05-03-adr37-constraint-port-assessment.md).
The pre-integration polish pass is recorded in
[ADR-36/37 Polish Before Main Integration](../log/2026-05-05-adr36-37-polish.md).
The relational fuel timing assessment is recorded in
[ADR-37 Relational Fuel Performance Probe](../log/2026-05-05-adr37-relational-fuel-performance.md).
ADR-37 should port faster-minikanren's `symbolo`, `numbero`, and `absento`
semantics, but not the faster-minikanren engine wholesale. The current overlay
now covers the general-purpose `absento` slice needed by ADR-36/37. The next
serious constraint slice should add native-style positive type constraints and
normalization for `symbolo`, `numbero`, and future `stringo` before any
production Proflog integration.

ADR-37 does not promote relational-arithmetic fuel to production. The adapter is
viable for fixed public entry fuel and integrated proof/answer surfaces, but
open or reverse fuel synthesis exposes ADR-36 bit-list numerals. Production
therefore keeps the finite-domain host-integer fuel relation for now.

## Candidate Questions

- Can production `step-fuelo` retain the public integer/nil API while delegating
  bounded arithmetic to pure relational bit-list numerals internally?
- How should native-style `symbolo`, `numbero`, `stringo`, and generalized
  `absento` integrate with core.logic disequality, reification, and constraint
  scheduling?
- Can Proflog-specific type relations be expressed as reusable core.logic
  predicate or tree constraints without making the kernel less relational?
- Which Proflog membership checks are actually absence/type constraints and
  would become cleaner or more complete with `absento`-style constraints?
- Which generic Proflog-motivated constraints are worth carrying, especially
  relational associative maps, walk-aware absence, and context-aware recursive
  tree constraints?
- Which core.logic TODOs are performance-only improvements, and which risk
  changing search order, reification, constraints, nominal behavior, or tabling?

## Guardrails

- Preserve miniKanren correctness and fair search semantics.
- Keep core.logic changes behind a project-local profile or explicit source
  overlay until upstream compatibility and regression risk are understood.
- Do not replace production `step-fuelo` until reverse/partial synthesis probes
  show a concrete benefit, integrated performance remains acceptable, and
  existing integer-fuel callers remain supported or receive an explicit
  migration.
- Treat direct raw core.logic tabling as closed by ADR-0036 unless a new
  canonical-state integration point is discovered.
- Prefer generic relations and constraints over Proflog-specific shortcuts.
- Do not accept an engine-level core.logic patch merely because it improves one
  Proflog row; generic host changes require host verification, before/after
  probes, and no regression in constraint, nominal, tabling, or reification
  semantics.

## Initial Evidence Required

- Survey log with primary-source links and a feature matrix across selected
  miniKanren implementations.
- Core.logic TODO/performance audit with ranked opportunities and risk levels.
- Tests for any imported or newly exposed constraints.
- Focused `step-fuelo` replacement probes showing reverse/partial synthesis
  behavior without finite-domain hard bounds.
- Focused performance evidence comparing finite-domain fuel with relational
  arithmetic fuel on direct and integrated Proflog surfaces.
- Proflog integration audit listing candidate replacements and cases rejected.

## Initial Test Obligations

- ADR-36 arithmetic tests.
- Existing kernel, query, and synthesis-mode tests affected by fuel handling.
- Upstream-style constraint tests for `symbolo`, `numbero`, `absento`, and any
  additional imported relation.
- `lein probe-relational-fuel-performance` for any proposed fuel replacement.
- Focused performance probes before and after any core.logic engine change.

## References

- [ADR-0036](ADR-0036-speculative-relational-arithmetic-and-tabling.md)
- faster-minikanren `numbers.scm` and `test-numbers.scm`
- core.logic source and current project dependency profiles
- miniKanren.org implementation and paper catalog
