# AAR-0029: Relational Fuel Purity

- Date: 2026-04-29
- Related ADR: [ADR-0029](../adr/ADR-0029-relational-fuel-purity.md)
- Outcome: completed

## What Happened

ADR-0029 removed the last executable `project` boundary from the ordinary
kernel-facing path.

Before this ADR, `step-fuelo` projected `fuel` and used host-side numeric
branching:

```clojure
(cond
  (nil? fuel) ...
  (> fuel 0) ...)
```

That worked for ordinary forward calls with known fuel, but it failed when a
direct kernel caller left fuel open. Because structural branch progress always
calls `step-fuelo`, open-fuel reverse and partial synthesis queries could throw
before the kernel had a chance to relate the fuel states.

The replacement expresses fuel stepping with core.logic finite-domain
constraints:

- `nil` relates to `nil`;
- bounded steps satisfy `fuel = next-fuel + 1`;
- the current-fuel and next-fuel domains are split to avoid overflow at
  `Long/MAX_VALUE`.

## Results

New regressions cover the exact failing modes identified in the log:

- direct predecessor synthesis through `step-fuelo`;
- direct unbounded-fuel synthesis through `step-fuelo`;
- preservation of known predecessor-to-successor behavior;
- ordinary `proveo` branch progress with open fuel;
- program-body synthesis across a procedure-call step with open fuel.

The kernel-facing source audit now reports no executable `project` use in
`kernel.clj`, `kernel_support.clj`, or `subst.clj`.

## List-Family Effect

ADR-0029 does not materially improve the raw list-family proofs that legacy
closed quickly and greenfield has historically failed or timed out on.

Focused checks on this branch:

- `append([a,b], [c], [a,b,c])`
  - `timeout 45s lein test :only proflog.list-programs-test/append-two-step-ground-case-succeeds`
  - timed out at `45.00 s`
- `reverse([a,b], [b,a])`
  - `timeout 45s lein test :only proflog.list-programs-test/reverse-two-element-list-succeeds`
  - timed out at `45.00 s`

That is consistent with the scope of the ADR. Relational fuel purity removes a
mode blocker for reverse and partial synthesis, but it does not change the
search policy enough to recover the legacy raw ground list proofs. Those still
belong to the separate list-family performance problem: clause-entry control,
equality wakeups, recursive scheduling, or answer-overlay/tabling strategy.

## Verification

- `rg -n "project" src/proflog/kernel_support.clj src/proflog/kernel.clj src/proflog/subst.clj`
  - only prose references remain; there is no executable `project` in the
    ordinary kernel-facing files.
- `lein test proflog.kernel-test`
  - `Ran 19 tests containing 30 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.reverse-program-synthesis-test`
  - `Ran 4 tests containing 5 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `Ran 29 tests containing 48 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 112 tests containing 362 assertions.`
  - `0 failures, 0 errors.`
