# AAR-0057: Relational Equality-Fragment Experiment

- Date: 2026-05-09
- ADR: [ADR-0057](../adr/ADR-0057-relational-equality-fragment.md)
- Branch: `adr-0057-relational-equality-fragment`
- Status: completed

## Summary

ADR-0057 added an opt-in relation-backed equality-fragment route with full
completion parity against the ADR-0039 finite verifier row set.

The production `kernel/prove-program` default still tries the established
`profiled equality-fragment` host-backed engine first. The new route is exposed
as `proflog.kernel.equality-fragment/prove-program-relational` and emits
`profiled relational-equality-fragment` proof evidence. It is intentionally
separate so callers and tests can compare the two profiles without changing
default query behavior.

## What Changed

- Added relational gamma candidate sources in `proflog.gamma`:
  `constructor-facts`, `relational-candidate-source`,
  `closed-term-exact-deptho`, and `closed-term-up-to-deptho`.
- Extended `closed-term-candidateo` so the production path still performs
  finite membership over a supplied collection, while ADR-0057 can generate
  closed terms from constructor facts inside miniKanren.
- Added an opt-in equality-fragment relation entry,
  `prove-program-relationalo`, plus a finite parity driver used by
  `prove-program-relational`.
- The parity driver does not call `prove-program-host`, `prove-host`,
  `close-branch-result`, the deterministic host unification helpers, or
  `gamma/closed-terms-for-fuel`.
- Added `proflog.relational-equality-fragment-probe` to compare host-backed
  and relation-backed finite verifier timings.
- Added the focused selector
  `lein test-proflog-relational-equality-fragment`.

## Semantic Boundary

The experiment reduced the equality-fragment semantic surface but did not make
the production default disappear.

The successful parity path uses miniKanren relations for:

- closed-term gamma generation;
- equality contradiction checks;
- equality unification;
- disequality reflexivity/rigid-difference checks; and
- saved disequality maintenance.

For performance, the opt-in finite driver still uses deterministic Clojure
control to select formulas, memoize repeated relation-backed term checks, and
look up a compiled clause body from the program map. That control is generic:
it does not inspect group-verifier ids, transition-system ids, relation-family
names, multiplication tables, or finite verifier scenario names.

This is therefore not a reason to remove the production host engine by default.
It is enough evidence that the last host-backed profile can be matched for the
promoted finite verifier row set when its semantic operations are routed through
miniKanren relations.

## Correctness Evidence

The focused test namespace covers:

- relational gamma generation for constants, unary constructors, binary
  constructors, and depth bounds;
- route guards forbidding the deterministic host equality engine, deterministic
  host equality helpers, and host gamma materialization;
- distinct `profiled relational-equality-fragment` proof markers;
- ADR-0042's shared universal witness discipline; and
- full ADR-0039 completion parity for all promoted GV and transition rows.

The parity rows are:

| Row | Expected |
|---|---|
| `z1-full-assoc-truth` | succeeds |
| `z2-precomputed-assoc-truth` | succeeds |
| `z2-full-assoc-truth` | succeeds |
| `non-group-precomputed-assoc` | fails |
| `non-group-full-assoc` | fails |
| `complete-delta-total` | succeeds |
| `complete-delta-deterministic` | succeeds |
| `incomplete-delta-total` | fails |
| `nondeterministic-delta-deterministic` | fails |

## Timing Evidence

Focused selector:

```text
lein test-proflog-relational-equality-fragment
Ran 5 tests containing 32 assertions.
0 failures, 0 errors.
real 82.97 s
```

Final commit-gate rerun while the standard gates were also active:

```text
lein test-proflog-relational-equality-fragment
Ran 5 tests containing 32 assertions.
0 failures, 0 errors.
real 198.56 s
```

Comparison probe:

```text
lein probe-proflog-relational-equality-fragment
real 106.21 s
```

| Row | Host ms | Relational ms |
|---|---:|---:|
| `z1-full-assoc-truth` | `7.790` | `203.453` |
| `z2-precomputed-assoc-truth` | `14.162` | `83.748` |
| `z2-full-assoc-truth` | `19.938` | `667.185` |
| `non-group-precomputed-assoc` | `83.473` | `91.309` |
| `non-group-full-assoc` | `13.244` | `87.405` |
| `complete-delta-total` | `8283.238` | `6884.622` |
| `complete-delta-deterministic` | `151.795` | `4366.812` |
| `incomplete-delta-total` | `25.260` | `18.374` |
| `nondeterministic-delta-deterministic` | `7327.128` | `60515.029` |

The relational route has completion parity, not uniform speed parity. The
largest remaining cost is nondeterministic transition determinism refutation,
which still takes about one minute in the comparison probe.

## Verification

Final required gates:

| Command | Result | Runtime |
|---|---|---:|
| `lein test-proflog-relational-equality-fragment` | `Ran 5 tests containing 32 assertions`, `0 failures`, `0 errors` | `real 198.56 s` |
| `lein test-proflog-kernel-finite-verifiers` | `Ran 4 tests containing 67 assertions`, `0 failures`, `0 errors` | `real 221.58 s` |
| `lein test-proflog-fitting-programs` | `Ran 6 tests containing 81 assertions`, `0 failures`, `0 errors` | `real 172.93 s` |
| `lein test-proflog-fast` | `Ran 143 tests containing 537 assertions`, `0 failures`, `0 errors` | `real 196.31 s` |
| `lein test-proflog-extended` | `Ran 68 tests containing 203 assertions`, `0 failures`, `0 errors` | `real 319.86 s` |
| `git diff --check` | passed | n/a |

## Follow-Up

- Keep production default dispatch on the host-backed equality-fragment engine
  unless a later ADR decides to promote the relation-backed finite driver.
- If promotion is considered, first decide whether generic deterministic
  formula scheduling and compiled map lookup are acceptable inside the public
  semantic boundary.
- The route now has enough coverage to serve as a regression target for future
  attempts to remove more host control from the equality-fragment path.
