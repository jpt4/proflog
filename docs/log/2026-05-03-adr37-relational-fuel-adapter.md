# ADR-37 relational fuel adapter probe

Branch: `adr-0037-core-logic-minikanren-enhancements`

Worker: ADR-37 Worker B

## Question

ADR-0029 made `step-fuelo` relational by replacing projection with
`clojure.core.logic.fd` constraints. The remaining concern is that finite-domain
arithmetic still fixes the finite fuel search space to host integers in a
hardcoded interval:

- current fuel: `1..Long/MAX_VALUE`
- next fuel: `0..Long/MAX_VALUE - 1`
- relation: `fd/+ next-fuel 1 fuel`

This probe tests whether ADR-36's bit-list relational arithmetic can sit behind
the existing public `nil`/host-integer fuel boundary while internal kernel steps
use structural miniKanren numerals instead of finite-domain arithmetic.

## Prototype

Added `proflog.relational-fuel-adapter-probe`:

- `boundary-fuelo` maps ground public fuel to the internal representation:
  - `nil` remains the unbounded sentinel.
  - non-negative host integers become ADR-36 little-endian bit-list numerals.
  - logic variables and already-internal bit-list numerals remain relational.
- `step-fuelo` consumes one finite unit with `proflog.relational-arithmetic/pluso`
  instead of `fd/in` plus `fd/+`.
- `prove-with-adapted-fuel` is an opt-in probe wrapper that rebinds
  `kernel-support/step-fuelo` only while a direct kernel proof run executes.

Production code remains unchanged.

## Reverse and partial synthesis assessment

The finite-domain implementation does not block the simplest one-step reverse
queries:

```clojure
(run 1 [fuel]
  (support/step-fuelo fuel 0))
;; => (1)

(run 1 [next-fuel]
  (support/step-fuelo 1 next-fuel))
;; => (0)
```

The limitation is representation and search-space shape, not those single
ground-neighbor cases. Finite answers are host integers constrained by a fixed
`Long/MAX_VALUE` domain. That is acceptable for bounded forward execution, but
it is not an unbounded relational numeral system, and any reverse or partial
synthesis that needs fuel outside that host interval, or needs to carry symbolic
fuel structurally through the kernel, inherits that hardcoded bound.

The adapter shifts finite synthesis to bit-list numerals:

```clojure
(run 1 [fuel]
  (adapter/step-fuelo fuel 0))
;; => ((1))

(run 1 [next-fuel]
  (adapter/step-fuelo 1 next-fuel))
;; => (())

(run 1 [fuel]
  (adapter/step-fuelo fuel (arith/build-num 1)))
;; => ((0 1))
```

That avoids the finite-domain interval for internal fuel. It also exposes the
main API tradeoff: finite reverse answers are internal bit-list values, not host
integers. A production replacement would need an explicit decision that direct
`step-fuelo` synthesis returns internal numerals, or a separate bounded
projection/enumeration layer at the public API. Adding such a projection would
reintroduce an operational boundary and should not be hidden in the core
relation.

The polished adapter tests now cover small ground finite boundaries in both
directions and an entry chain from host fuel `3` through internal bit-list
successors `2`, `1`, and `0`. Fully open finite-step enumeration is deliberately
not treated as a public host-integer stream; the relation may expose symbolic
bit-list families.

## Kernel probe result

The simple closing conjunction used by the existing kernel fuel tests runs
through the adapter from a host integer boundary:

```clojure
(adapter/prove-with-adapted-fuel closing-conjunction 1 2)
```

The focused test compares this with the direct production kernel relation and
also checks the same comparison at smaller finite slices. This confirms that a
ground host-integer entry fuel can transition to bit-list recursive fuel without
changing the simple proof result.

Open public fuel synthesis still returns the existing first answer, `nil`, for
the unbounded branch. Finite open synthesis is available at the direct
`step-fuelo` level as bit-list fuel, but this probe does not yet make the public
kernel wrapper enumerate finite host integer fuel values.

## Viability

The adapter is viable as an opt-in experiment for ADR-37 Phase 2/3:

- It preserves `nil` and ground host integers at the proof entry boundary.
- It removes finite-domain arithmetic from internal finite fuel stepping.
- It supports reverse/partial finite stepping over unbounded structural
  bit-list numerals.
- It keeps production behavior unchanged.

It is not yet a drop-in production replacement:

- Direct finite fuel synthesis returns bit-list numerals rather than host
  integers.
- The boundary conversion handles ground host integers when the adapter goal is
  built; it is not a delayed relational host-integer encoder.
- A later timing pass found acceptable integrated overhead, but a significant
  direct one-step slowdown. Production promotion still needs a public fuel
  representation decision and broader workload timing if the profile is wired
  into defaults.

See
[ADR-37 Relational Fuel Performance Probe](2026-05-05-adr37-relational-fuel-performance.md).

## Validation

```text
/usr/bin/time -p timeout -k 10s 180s lein test proflog.relational-fuel-adapter-probe-test

Ran 8 tests containing 32 assertions.
0 failures, 0 errors.
```

```text
/usr/bin/time -p timeout -k 10s 240s lein test proflog.relational-arithmetic-test proflog.relational-fuel-adapter-probe-test

Ran 12 tests containing 32 assertions.
0 failures, 0 errors.
real 67.03
user 40.45
sys 2.82
```
