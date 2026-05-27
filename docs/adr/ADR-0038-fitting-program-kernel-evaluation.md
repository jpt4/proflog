# ADR-0038: Fitting Program Kernel Evaluation

- Status: completed
- Date: 2026-05-05
- Branch: `adr-0038-fitting-program-kernel-evaluation`
- AAR: [AAR-0038](../aar/AAR-0038-fitting-program-kernel-evaluation.md)
- Depends On:
  - [ADR-0035](ADR-0035-relational-residual-continuation.md)
  - [ADR-0036](ADR-0036-speculative-relational-arithmetic-and-tabling.md)
  - [ADR-0037](ADR-0037-core-logic-minikanren-enhancements.md)

## Context

The current greenfield implementation has many focused regressions for Fitting
procedure calls, equality, disunification, Nim, list programs, finite-domain
examples, and group-verifier probes. Some hard examples still rely on diagnostic
probes, named overlays, host-side materialization, or bounded status helpers
whose operational role is not always separated from semantic proof search.

The next development step is to turn the Fitting-program claim into a direct
greenfield capability: deep and complex Proflog programs from the paper and its
worked legacy extensions should be translated into the kernel's formula/program
representation and then evaluated by the core proof kernel. After translation,
the evaluation path should not compute semantic answers by host-side shortcuts.

ADR-0036 and ADR-0037 close with production finite-domain fuel retained. That
means ADR-0038 starts from the current kernel surface instead of spending its
first slice on another fuel representation change.

## Decision

Build a definitive Fitting-program evaluation track for greenfield.

The branch should promote representative programs from the legacy paper-facing
suite into greenfield source/compiled-program fixtures, then evaluate them
through `proflog.kernel/prove-programo`, `proflog.kernel/prove-program`, and the
public query/answer APIs only where those APIs preserve proof-kernel authority.

The accepted implementation must not prove semantic results by:

- dispatching on named program families such as Nim, append/reverse, finite
  domains, or group verification;
- using host-side table evaluation after source translation;
- using `proflog.hard-family-overlay` as the ordinary answer;
- materializing answer sets outside the proof kernel and treating that as
  semantic truth; or
- adding host projections to the kernel-facing proof path.

Host code may still build source programs, translate them into the existing
AST/compiled-program representation, choose explicit finite fuel and proof
limits, run tests, and summarize proof results. The boundary is that, after
translation, truth/falsity/undefined evidence must come from the proof kernel or
proof-preserving query/answer layers.

## Program Families

The first evaluation catalog should include:

- Fitting P1 even/odd, including the original forall-based odd clause and
  deeper Peano inputs than the current smoke tests;
- Fitting P2 Nim, including a deeper bounded winner/loser table with proof
  evidence rather than host recurrence;
- list programs from the Fitting examples, including append, reverse, partial,
  inverse, nested, and longer rows already exercised by the list-kernel matrix;
- disunification and move-warning examples that distinguish valid Proflog
  factoring from invalid auxiliary-relation shortcuts;
- finite-domain examples that demonstrate biconditional refutation,
  universal bodies, classical negation, and undefined status; and
- group-verifier examples, including both full associativity and precomputed
  associativity forms, with any remaining hard cases classified by proof-search
  cause rather than hidden behind a named overlay.

## Test Obligations

Add or promote tests before implementation for:

- direct kernel/query success and failure for deeper P1 and P2 cases;
- answer-mode evaluation of representative append/reverse rows without
  host-side list materialization as the semantic oracle;
- status classification for finite-domain true, false, and undefined examples;
- group-verifier truth and refutation cases through the core proof kernel;
- proof-shape assertions showing that promoted results contain ordinary kernel
  or answer-overlay proof evidence; and
- a source audit that rejects new production dispatch on named Fitting program
  families.

Minimum focused commands should include:

```text
lein test-proflog-fast
lein test-proflog-extended
lein test proflog.query-test
lein test proflog.quantified-programs-test
lein test proflog.list-kernel-matrix-test
lein test proflog.legacy-hard-families-test
lein probe-proflog-gv
```

## Exit Criteria

ADR-0038 is complete when greenfield has a durable Fitting-program evaluation
catalog that:

- runs representative deep P1, P2, list, disunification, finite-domain, and
  group-verifier programs through the core proof kernel or proof-preserving
  query/answer APIs;
- records true, false, and undefined outcomes with proof evidence or explicit
  bounded-search classification;
- removes or clearly quarantines any host-side semantic computation from the
  promoted evaluation path after source translation;
- keeps production code generic, with no named-program shortcuts;
- documents remaining hard rows by kernel/answer-search cause and not by vague
  timeout; and
- passes the focused and regression suites named above.
