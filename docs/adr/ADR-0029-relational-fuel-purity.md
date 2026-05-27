# ADR-0029: Relational Fuel Purity

- Status: completed
- Date: 2026-04-29
- Branch: `adr-0029-relational-fuel-purity`
- AAR: [AAR-0029](../aar/AAR-0029-relational-fuel-purity.md)
- Depends On: [ADR-0028](ADR-0028-kernel-support-disequality-purity.md)

## Context

ADR-0028 deliberately left `step-fuelo` as the last accepted projected helper
in `proflog.kernel-support`, treating it as bounded operational bookkeeping.
That was too weak for the stronger kernel invariant: the proof kernel should be
a pure relation across its transitive dependencies.

`step-fuelo` is not bypassed by ordinary full-kernel proof search. Direct
branch closures do not consume fuel, but any structural progress step calls
`support/step-fuelo`. Passing `fuel = nil` means unbounded search; it does not
mean the relation is skipped.

The triggering discussion and concrete failing examples are recorded in
[Step Fuel Relational Purity Gap](../log/2026-04-29-step-fuelo-relational-purity-gap.md).

## Decision

Replace projected fuel stepping with a structural core.logic relation.

The implementation keeps the two supported fuel modes:

- `nil` relates only to `nil`, meaning unbounded search remains unbounded;
- bounded fuel uses finite-domain integer constraints where current fuel is
  positive, next fuel is non-negative, and `fuel = next-fuel + 1`.

The bounded branch uses split finite domains:

- `fuel`: `1..Long/MAX_VALUE`
- `next-fuel`: `0..Long/MAX_VALUE - 1`

Splitting the domains preserves the old largest valid bounded step
`Long/MAX_VALUE -> Long/MAX_VALUE - 1` while avoiding arithmetic overflow
inside `fd/+`.

## Consequences

Direct reverse and partial fuel queries can now synthesize both sides of a
fuel step. Kernel callers can also leave fuel open across structural progress;
the first answer remains the unbounded `nil` mode, preserving existing
unbounded proof behavior.

The ordinary kernel-facing purity audit now has no executable `project` calls
in `src/proflog/kernel.clj`, `src/proflog/kernel_support.clj`, or
`src/proflog/subst.clj`.

Fuel remains an operational budget rather than object-language proof state.
The bounded numeric branch is intentionally constrained to finite-domain
integers. The unbounded mode remains represented by `nil`.

## Test Obligations

- `step-fuelo` can synthesize a predecessor from a known successor.
- `step-fuelo` can synthesize a successor from a known predecessor.
- `step-fuelo` can synthesize the unbounded fuel case.
- A direct `proveo` caller can leave fuel open across structural branch
  progress.
- A program-synthesis caller can leave fuel open across a procedure-call step.
- Existing kernel, substitution, and reverse-program synthesis regressions
  remain green.

## Exit Criteria

- `step-fuelo` no longer uses `project`.
- No executable `project` remains in `kernel.clj`, `kernel_support.clj`, or
  `subst.clj`.
- `lein test proflog.kernel-test` passes.
- `lein test proflog.reverse-program-synthesis-test` passes.
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test` passes.
- `lein test-proflog-fast` passes.
