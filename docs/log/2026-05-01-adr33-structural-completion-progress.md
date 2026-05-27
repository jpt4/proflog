# ADR-33 Structural Completion Progress

Date: 2026-05-01
Branch: `adr-0033-structural-answer-variable-recursion`

## Change

ADR-33 now has a bounded structural residual-completion hook in the ordinary
program answer export path.

The hook runs after a raw answer-overlay proof state has been projected into an
answer record, but before answer records are merged and selected. It is guarded
by a conservative classifier:

- every residual in the procedural frontier must be a negative call to a
  relation defined by the same compiled program;
- the frontier must expose at least one concrete constructor-shaped argument;
- wholly symbolic recursive families remain residuals; and
- diagnostics opt out so raw snapshots still show unresolved frontiers.

When the classifier accepts a record, the existing generic
`proflog.kernel.constructor-recursive/settle-record` layer tries to discharge
the residual frontier and refines the answer bindings with proof evidence.

The implementation is generic over the compiled guarded-clause IR. It does not
dispatch on list-family relation or constructor names.

## Why The Classifier Allows Chained Demand

The first version required every residual call to expose constructor demand.
That closed the carried reverse matrix rows but left a `jump(x, 0)` frontier
open:

```clojure
step(x, _0)
step(_0, zero)
```

The first residual is symbolic by itself, but the second residual grounds the
shared `_0` enough to drive the chain. The classifier now accepts a frontier
when all residuals are defined negative calls and at least one residual exposes
constructor demand.

This still rejects a purely symbolic family such as:

```clojure
odd(_0)
```

That family is intentionally preserved as a residual answer rather than
collapsed into one enumerated witness.

## Ordering

ADR-33 also adds a derivation-depth ordering pass for public query answers with
recursive call depths greater than one. This fixes the carried `down(2, y)`
ordering by preferring the shorter base derivation over deeper recursive
descendants after ordinary residual and binding ranking.

Diagnostics keep the older ordering so raw frontier reports remain comparable
to previous ADR logs.

## Focused Verification

Commands run:

```text
timeout -k 10s 180s lein test proflog.answers-test
timeout -k 10s 180s lein test proflog.synthesis-modes-test
timeout -k 10s 180s lein test proflog.list-kernel-matrix-test
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

Results:

- `proflog.answers-test`: 18 tests, 69 assertions, 0 failures;
- `proflog.synthesis-modes-test`: 9 tests, 17 assertions, 0 failures;
- `proflog.list-kernel-matrix-test`: 2 tests, 19 assertions, 0 failures;
- `reverse-input-flat`: target found through ordinary raw matrix path;
- `reverse-output-nested-longer`: target found through ordinary raw matrix
  path;
- `reverse-partial-output-tail`: target found through ordinary raw matrix path.

## Language Namespace Documentation

The branch also adds `docs/LANGUAGE_NAMESPACE_SPEC.md`, a language-layer spec
and pedagogical explanation covering:

- declaration normalization;
- AST validation;
- surface clause shape;
- alpha-renaming;
- NNF normalization;
- compiled program views;
- alternatives and guarded alternatives;
- demand ordering; and
- the boundary between public Proflog compilation and low-level kernel proof
  relations.

## Broader Verification

The broader ADR-33 gates also passed:

```text
lein test-proflog-constructor-recursive
lein test-proflog-fast
```

Results:

- `test-proflog-constructor-recursive`: 6 tests, 21 assertions, 0 failures;
- `test-proflog-fast`: 117 tests, 380 assertions, 0 failures.
