# ADR-36 Relational Arithmetic and Tabling Probes

Date: 2026-05-03
Branch: `adr-0036-spec-relational-arithmetic-tabling`
Base: `8eb48c2 Merge ADR-35 relational residual continuation`

## Purpose

ADR-0036 is a speculative branch created from `main` after ADR-0035 was merged.
It evaluates two independent ideas:

- translating faster-minikanren `numbers.scm` and `test-numbers.scm` to
  Clojure/core.logic;
- reassessing whether direct core.logic tabling is better for the current
  Proflog workloads than the existing `proflog.tabling` canonical-state wrapper.

## Arithmetic Translation

Added:

- `src/proflog/relational_arithmetic.clj`
- `src/proflog/relational_fuel_probe.clj`
- `test/proflog/relational_arithmetic_test.clj`
- `test/proflog/relational_arithmetic_upstream_test.clj`

The translated numerals use the upstream little-endian bit-list representation:
`()`, `(1)`, `(0 1)`, `(1 1)`, and so on.

The upstream `test-numbers.scm` interpreter tests use `absento` and `symbolo`.
`org.clojure/core.logic 1.0.1` does not expose those names, so the translated
test namespace supplies local delayed constraints:

- `absento` is represented with `core.logic/treec` plus disequality;
- `symbolo` is represented with `core.logic/predc`.

Those replacements are test evidence only. They are not a commitment to add a
public production constraint API in ADR-0036.

## Arithmetic Results

```text
timeout -k 10s 240s lein test proflog.relational-arithmetic-test proflog.relational-arithmetic-upstream-test

Ran 13 tests containing 48 assertions.
0 failures, 0 errors.
real 29.88
user 32.34
sys 1.92
```

Focused upstream checks after replacing the naive `absento`/`symbolo` stand-ins:

```text
timeout -k 5s 60s lein test :only proflog.relational-arithmetic-upstream-test/upstream-absento-push-down-problems

Ran 1 tests containing 6 assertions.
0 failures, 0 errors.
Tool-observed wall time: 20.2860 s.

timeout -k 5s 120s lein test :only proflog.relational-arithmetic-upstream-test/upstream-rel-fact5-backwards-test

Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
Tool-observed wall time: 18.1880 s.
```

Fuel experiment:

- `proflog.relational-fuel-probe/step-fuelo` works when finite fuel is expressed
  as translated bit-list numerals.
- It is not drop-in compatible with production callers that pass host integers.
- Production `proflog.kernel-support/step-fuelo` is unchanged.

## Focused Regression Gate

```text
/usr/bin/time -p timeout -k 10s 300s lein test proflog.kernel-test proflog.tabling-test proflog.relational-arithmetic-test proflog.relational-arithmetic-upstream-test

Ran 39 tests containing 94 assertions.
0 failures, 0 errors.
real 106.82
user 108.17
sys 2.65
```

## Core.logic Tabling Results

This subtrack was primarily a check that Proflog was not trivially duplicating
functionality already supplied by core.logic. The evidence says it is not:
Proflog's current tabling layer refines core.logic tabling with canonical
proof-state keys, which remain project-specific.

Smoke probe:

```text
timeout -k 5s 120s lein probe-core-logic-tabling

{:id :core-logic-tabled-smoke,
 :answers (:done),
 :active-tabled-answer-path? true,
 :tabling-events
 {:answer-cache-created 1,
  :reuse 1,
  :subunify 1,
  :reify-tabled 2,
  :reify-tabled-inner 6,
  :tabled-substitution-created 1,
  :suspended-stream-created 3,
  :waiting-stream-check 3}}
```

ADR-0035 list-family row probes:

```text
timeout -k 10s 240s lein probe-core-logic-tabling reverse-input-flat-longer

:target-found? true
:found-target-count 1
:raw-count 4
:elapsed-ms 38162.246176
:active-tabled-answer-path? false
:tabling-events
  {:tabled-substitution-created 3,
   :answer-cache-created 0,
   :answer-cache-add 0,
   :reuse 0,
   :subunify 0,
   :reify-tabled 0,
   :reify-tabled-inner 0}

timeout -k 10s 240s lein probe-core-logic-tabling reverse-output-deep-nested-longer

:target-found? true
:found-target-count 1
:raw-count 4
:elapsed-ms 28083.027481
:active-tabled-answer-path? false
:tabling-events
  {:tabled-substitution-created 2,
   :answer-cache-created 0,
   :answer-cache-add 0,
   :reuse 0,
   :subunify 0,
   :reify-tabled 0,
   :reify-tabled-inner 0}
```

## Interpretation

The arithmetic translation is correct enough for the translated fast and
upstream-style tests that have been ported. It should remain speculative until
larger performance probes decide whether it can replace finite-domain fuel
without preserving a hardcoded numeric domain that blocks reverse or partial
synthesis, or whether it is only useful as an auxiliary relation library.

The tabling result is negative for the current question. Direct core.logic
tabling can work, as the smoke probe shows, but the current ADR-0035 list-family
rows do not use the tabled answer-cache machinery. Proflog's existing wrapper
around `core.logic/tabled` still has the important project-specific piece:
canonical proof-state keys. Raw direct `core.logic/tabled` is therefore not
better for these workloads at this checkpoint, and ADR-0036 will not pursue that
replacement further.

## Recommendation

Keep ADR-0036 open and speculative.

- Keep the arithmetic translation for further evaluation.
- Park replacing production `step-fuelo` until there is an explicit fuel
  representation migration or an adapter that preserves the integer API.
- Do not replace `proflog.tabling` with raw direct core.logic tabling based on
  the current evidence. This tabling subtrack is closed unless a later ADR finds
  a different canonical-state integration point.
