# 2026-04-30 ADR-0031 Answer Continuation Probe

## Context

Worker 4 explored whether answer mode could keep descending through productive
residuals before exporting a residual frontier. The goal was a generic rule:
no `append`, `reverse`, `cons`, or `null` production dispatch, and no
`core.logic` host engineering.

The probe avoided the two rejected variants from the reassessment:

- not strict residual deferral for every procedure call;
- not unconditional residual frontier re-settlement after export.

## Prototype Tried

The in-kernel prototype used this monotone/productivity criterion:

- while answer call-depth remained, a residual call whose walked arguments
  exposed constructor structure should spend the available descent budget;
- only fully symbolic residual calls, with all walked arguments still object
  variables, could export before call-depth was exhausted;
- once call-depth reached zero, the historical residual export behavior
  remained available.

This criterion was generic over relation and constructor names. It was meant to
avoid exporting a frontier when visible constructor demand could still be
decomposed, while still allowing genuinely undetermined recursive calls to
remain symbolic.

## Probe Results

Baseline before the prototype:

- `lein test proflog.answers-test`: 17 tests, 64 assertions, 0 failures.
- `timeout 120 lein run -m proflog.list-kernel-matrix-probe append-output-flat`:
  target found, elapsed about 6.1s.
- `timeout 150 lein run -m proflog.list-kernel-matrix-probe reverse-output-flat`:
  target found, elapsed about 15.0s.
- `timeout 150 lein run -m proflog.list-kernel-matrix-probe reverse-input-flat`:
  target not found, elapsed about 49.8s.

With the prototype:

- `lein test proflog.answers-test`: 17 tests, 64 assertions, 0 failures.
- `timeout 180 lein run -m proflog.list-kernel-matrix-probe append-output-flat`:
  target found, elapsed about 16.3s.
- `timeout 180 lein run -m proflog.list-kernel-matrix-probe reverse-output-flat`:
  target found, elapsed about 45.4s.
- `timeout 180 lein run -m proflog.list-kernel-matrix-probe reverse-input-flat`:
  target not found, elapsed about 135.3s.
- `timeout 180 lein run -m proflog.list-kernel-matrix-probe reverse-output-nested-longer`:
  target not found, elapsed about 98.3s.
- `timeout 180 lein run -m proflog.list-kernel-matrix-probe reverse-partial-output-tail`:
  timed out.

I also tried a host-side sketch of scored post-frontier continuation against
the first raw `reverse-input-flat` frontier. The continuation was required to
strictly improve a structural frontier score before replacing the original
record. The experiment timed out under `timeout 240` before producing a
candidate continuation, so it did not justify implementation.

## Decision

Do not retain the prototype.

The constructor-visible deferral gate is materially different from the rejected
strict deferral rule, but it still has the same practical failure mode: it
spends more search time on visible residual calls without closing the blocked
reverse rows. It also slows existing passing rows enough that it is not a good
ADR-0031 direction.

The post-frontier continuation sketch had the right monotone shape in
principle, but the current raw frontier is already too expensive to produce and
the continuation itself did not return within the probe budget. That weakens
the case for adding a host-side continuation collector unless a future design
can obtain cheaper, proof-state-local continuation candidates from the kernel.

## Next Search Implication

Answer-mode continuation probably needs a deeper proof-state representation,
not another export-time pass. A promising future version would carry a
branch-local progress measure with the residual call itself, so the kernel can
compare candidate descents before creating exported answer records. That would
avoid both broad deferral and expensive post-export re-entry.
