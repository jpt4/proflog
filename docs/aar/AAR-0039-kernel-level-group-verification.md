# AAR-0039: Kernel-Level Finite Equality Verification

- Date: 2026-05-06
- Related ADR: [ADR-0039](../adr/ADR-0039-kernel-level-group-verification.md)
- Outcome: completed with proof-backed GV and transition-system verifier rows

## What Happened

ADR-39 added `proflog.kernel.equality-fragment`, a generic proof-producing
finite equality-fragment component reached from the program proof entry. It
operates on compiled call-free equality formulas and emits proof terms under
`profiled equality-fragment`.

The implementation also added `proflog.finite-transition-systems`, a non-GV
verifier family with larger DFA-like `delta` tables:

- four states,
- three symbols,
- twelve complete deterministic transitions,
- an incomplete variant, and
- a nondeterministic variant.

## What Worked

The mandatory group-verifier rows now close without the hard-family overlay:

- `Z1` full associativity succeeds;
- `Z2` precomputed associativity succeeds;
- `Z2` full associativity succeeds;
- non-group precomputed associativity fails; and
- non-group full associativity fails.

The transition-system rows also close through the same proof component:

- complete deterministic `delta_total()` succeeds;
- complete deterministic `delta_deterministic()` succeeds;
- incomplete `delta_total()` fails; and
- nondeterministic `delta_deterministic()` fails.

The initial relational branch-profile attempt was rejected during the branch
because it made the ADR-38 Fitting suite time out. The retained implementation
keeps the proof-producing equality-fragment component at the program proof
entry, where it is effective for compiled verifier bodies and does not slow the
list-kernel matrix rows.

## What Remains

The focused transition-system suite is intentionally slow. The determinism law
compares two full twelve-row delta lookups and should remain outside the fast
selector.

The equality-fragment component is not a replacement for the full relational
kernel. Recursive procedure calls, open answer synthesis, and non-equality
first-order reasoning still use the existing kernel and answer-overlay paths.

## Verification

Focused verification:

```text
timeout -k 5s 360s lein test-proflog-kernel-finite-verifiers
  Ran 3 tests containing 51 assertions.
  0 failures, 0 errors.

timeout -k 5s 900s lein test-proflog-fitting-programs
  Ran 6 tests containing 81 assertions.
  0 failures, 0 errors.

timeout -k 5s 180s lein test-proflog-hard-families
  Ran 3 tests containing 3 assertions.
  0 failures, 0 errors.
```

Regression verification:

```text
timeout -k 5s 420s lein test-proflog-fast
  Ran 117 tests containing 381 assertions.
  0 failures, 0 errors.

timeout -k 5s 900s lein test-proflog-extended
  Ran 68 tests containing 203 assertions.
  0 failures, 0 errors.
```
