# ADR-0039: Kernel-Level Finite Equality Verification

- Status: completed
- Date: 2026-05-06
- Branch: `adr-0039-kernel-level-group-verification`
- AAR: [AAR-0039](../aar/AAR-0039-kernel-level-group-verification.md)
- Depends On:
  - [ADR-0023](ADR-0023-profiled-kernel-layers.md)
  - [ADR-0026](ADR-0026-kernel-layer-interoperation.md)
  - [ADR-0038](ADR-0038-fitting-program-kernel-evaluation.md)

## Context

ADR-0038 deliberately left the group-verifier associativity rows as bounded
core-kernel frontiers. The named hard-family overlay can recover one
trivial-group associativity row, but that result is not acceptable as
kernel-level group verification because it relies on a host-side equality fast
path outside the proof kernel.

Adding another profiled proof layer for a fragment of first-order logic with
equality and procedure calls is not ideal, but it is acceptable if the layer is
generic, proof-producing, and strong enough to close the full group-verifier
associativity rows. This ADR therefore treats group verification as one
required consumer of a finite equality-fragment kernel layer, not as a
group-specific evaluator.

The terminology for this family is "profiled", matching the existing profiled
kernel layers.

## Decision

Add a proof-producing profiled kernel component for finite equality-fragment
verification.

The component should live under `proflog.kernel` and be reached from the
program proof entry when a procedure call expands to a call-free equality
fragment containing only:

- `true` and `false`;
- `eq` and `neq`;
- `and` and `or`;
- `forall`, `once-forall`, and `exists`.

The implementation must remain generic. It must not dispatch on group-verifier
scenario ids, relation names, multiplication tables, DFA names, or any other
problem-family identifier. Source code may still construct examples and tests,
but after translation into the compiled Proflog formula/program
representation, truth and falsity must come from proof-kernel evidence.

The equality-fragment component may reuse the existing equality and gamma
machinery, including explicit finite closed-term candidates. It may use
host-side formula/profile inspection, candidate generation, and generic proof
search over the compiled formula representation, but it must not compute
semantic truth by evaluating a source-level group table or transition table.

## Required Program Families

### Group Verifier

ADR-0039 is not complete until the core/profiled kernel path resolves:

- `Z1` full seven-universal associativity as success;
- `Z2` precomputed associativity as success;
- `Z2` full seven-universal associativity as success;
- non-group precomputed associativity as failure; and
- non-group full seven-universal associativity as failure.

The `Z2` full and non-group full rows are mandatory exit goals, not stretch
goals.

### Non-GV Genericity: Finite Transition Systems

ADR-0039 must also add a distinct finite verification family. The accepted
family is finite transition-system verification, expressed as DFA-like
transition-table laws over constants:

- transition totality: every `(state, symbol)` pair has some target state;
- transition determinism: any two targets for the same `(state, symbol)` pair
  are equal.

The transition examples must be of significant length and size. They must use
more than a tiny two-state smoke table, and they must include both success and
failure cases:

- a complete deterministic transition system proving totality;
- a complete deterministic transition system proving determinism;
- an incomplete transition system refuting totality; and
- a nondeterministic transition system refuting determinism.

These tests demonstrate that the new layer is a generic finite
equality-fragment verifier rather than a group-verifier shortcut.

## Test Obligations

Add focused tests that:

- prove the new profile classification accepts equality-fragment formulas and
  rejects formulas with active procedure atoms;
- prove program proof entry produces `profiled equality-fragment` proof
  evidence;
- redefine `proflog.hard-family-overlay/query-status` to throw while ADR-0039
  cases run;
- promote the required GV rows as proof-kernel success/failure outcomes, not
  unresolved classifications;
- add the significant finite transition-system success/failure cases; and
- keep the focused test selector isolated from routine fast tests if the full
  associativity rows are expensive.

Minimum focused commands:

```text
lein test-proflog-kernel-finite-verifiers
lein test-proflog-fitting-programs
lein test-proflog-hard-families
```

Before merge, run:

```text
lein test-proflog-fast
lein test-proflog-extended
```

## Exit Criteria

ADR-0039 is complete only when:

- all mandatory GV success/failure rows pass through the core/profiled kernel
  path;
- all significant transition-system rows pass through the same generic feature;
- proof-shape tests show the equality-fragment layer is actually exercised;
- source audits show no group-verifier or transition-system family dispatch in
  the production kernel path;
- ADR-0038's Fitting catalog is updated from GV frontier classification to the
  newly proved outcomes where applicable;
- worked examples document the GV and transition-system definitions,
  evaluation process, results, performance, and any remaining shortcomings;
- the focused and regression commands above pass; and
- an AAR records what changed, what remains expensive, and why the result is
  kernel-level rather than overlay-level.
