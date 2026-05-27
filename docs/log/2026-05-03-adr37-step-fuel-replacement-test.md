# ADR-37 Step Fuel Replacement Test

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`
Worker: H

## Scope

This pass tested an isolated replacement of `proflog.kernel-support/step-fuelo`
with `proflog.relational-fuel-adapter-probe/step-fuelo`. Production
`kernel_support.clj` was not edited; all kernel and answer-mode checks use
`with-redefs`.

The goal was to check whether ADR-36 bit-list relational arithmetic can replace
finite-domain fuel stepping across more realistic proof and answer-overlay
surfaces while preserving the public `nil` / host integer entry boundary.

## Tested Surfaces

New focused tests live in
`test/proflog/relational_fuel_replacement_test.clj`.

Covered cases:

- Direct `step-fuelo` reverse synthesis:
  - FD `fuel -> next-fuel=0` synthesizes host integer `1`.
  - Relational arithmetic synthesizes bit-list `(1)`.
  - FD `fuel=1 -> next-fuel` synthesizes host integer `0`.
  - Relational arithmetic synthesizes bit-list `()`.
- Direct unbounded fuel:
  - Both implementations preserve `nil -> nil`.
- Kernel proof entry:
  - Fixed public fuel boundaries `0`, `1`, `2`, and `nil` produce the same
    first proof shape for a small closing conjunction.
  - Small finite fuel slices `0..6` produce the same first proof shape across
    direct complementary closure, beta split, gamma instantiation, and delta
    witness formulas.
- Direct query proof surface:
  - Ground `step(2, 1)` success and `step(0, 1)` failure probes match
    production across fuel slices `0`, `1`, `2`, `4`, and `8`.
- Answer-overlay query export:
  - `step(x, 1)` partial synthesis keeps the same exported public answer
    bindings as FD fuel: `x = 2`, `x = 3`.
  - `step(3, y) & y != 2` reverse synthesis keeps the same exported public
    answer binding as FD fuel: `y = 1`.
  - `jump(x, 0)`, which crosses two procedure-call steps, keeps the same
    exported answer shape and reaches `x in {2, 3, 4}`.
- Open kernel fuel:
  - With fuel itself open, both paths expose the same first public answer:
    the unbounded `nil` sentinel.

## Command

```text
/usr/bin/time -p timeout -k 10s 240s lein test proflog.relational-fuel-replacement-test
```

Result:

```text
Ran 7 tests containing 57 assertions.
0 failures, 0 errors.
```

Four parallel `:only` timing splits were attempted afterward, but they timed
out during concurrent Leiningen/JVM startup contention and did not reach useful
test evidence. They are not counted as semantic or performance evidence.

## Comparison

The replacement is semantically viable for the next ADR-37 phase on fixed public
entry fuel, direct kernel rule shapes, direct query proof surfaces, and
answer-mode query export. On those surfaces, proof samples and public answer
record bindings/residuals matched production FD fuel.

A later same-JVM performance probe kept the replacement viable: direct one-step
fuel calls were significantly slower, but measured kernel/query/answer surfaces
ranged from roughly parity to a 1.35x mean slowdown. See
[ADR-37 Relational Fuel Performance Probe](2026-05-05-adr37-relational-fuel-performance.md).

The known leak remains direct synthesis of fuel values themselves. FD fuel
synthesizes host integers, while the relational replacement synthesizes ADR-36
little-endian bit-list numerals. The answer overlay did not leak those bit-list
fuel values into exported Proflog answer records in the tested cases, because
fuel is not an answer variable there.

## Decision

Proceed with the relational fuel replacement as an opt-in/profiled ADR-37 phase,
not as a production replacement yet.

Required before production:

- keep the public `nil` / host integer entry API stable;
- decide whether open/reverse public fuel synthesis should expose bit-list fuel,
  host integer fuel through a bounded projection, or remain unsupported;
- add more answer-mode regression rows beyond the small `step` / `jump` program
  if the profile is promoted from probe to production;
- re-run the performance probe after any representation-boundary change and add
  broader workload timing if the profile is promoted from probe to production.
