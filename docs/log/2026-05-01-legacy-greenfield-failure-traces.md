# 2026-05-01 Legacy / Greenfield Failure Traces

## Context

ADR-32 still has two kinds of greenfield failures:

- raw reverse answer rows in `proflog.list-kernel-matrix-test` diagnostics; and
- two `proflog.synthesis-modes-test` rows.

Existing documentation already recorded the broad architectural gap:

- legacy uses one `proveo` path for proof and open synthesis;
- greenfield splits ordinary proof search, answer export, residual deferral,
  and specialty overlays;
- legacy's projected `l-ground` guard admits bare host logic variables because
  they do not contain `(par ...)`; and
- greenfield carries explicit answer-mode state, budgets, residuals, and
  priority rules.

What was missing was one current, worked note tying the exact ADR-32 failures
back to legacy-passing execution shapes. This note fills that gap.

## Commands

Targeted legacy selectors that passed:

```text
timeout -k 10s 120s lein test :only cljtap.alphaleantap-ep-test/test-Y10-reverse-synth-result
timeout -k 10s 120s lein test :only cljtap.alphaleantap-ep-test/test-Y12-append-inverse-synth-all-splits
timeout -k 10s 120s lein test :only cljtap.alphaleantap-ep-test/test-V10-joint-synth-self-recursive-run-2
timeout -k 10s 120s lein test :only cljtap.alphaleantap-ep-test/test-V12-joint-synth-double-step-outer
```

Exact legacy probes were also run through `clojure.main` with the same
`append`, `reverse`, `step`, `jump`, and `down` clauses used by the comparison
tests. The useful outputs were:

```text
:legacy-reverse-input-flat
((app cons (app a) (app cons (app b) (app nul))))

:legacy-reverse-output-nested-longer
((app cons
      (app cons (app c) (app nul))
      (app cons
           (app cons (app b) (app nul))
           (app cons (app cons (app a) (app nul)) (app nul)))))

:legacy-reverse-partial-output-tail
((app cons (app b) (app cons (app a) (app nul))))

:legacy-jump-x-zero
((app s (app s (app zero)))
 (app s (app s (app s (app zero))))
 (app s (app s (app s (app zero))))
 (app s (app s (app s (app s (app zero))))))

:legacy-down-two-y
((app s (app s (app zero))) (app s (app zero)))
```

The current greenfield ADR-32 source-overlay checks were:

```text
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout -k 10s 240s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail
timeout -k 10s 240s lein with-profile +core-logic-source-overlay test proflog.synthesis-modes-test
```

The constructor-recursive sidecar can close the three reverse rows, but ADR-32's
carried failure is specifically the ordinary raw answer path:

```text
timeout -k 10s 120s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat constructor-recursive
timeout -k 10s 120s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer constructor-recursive
timeout -k 10s 120s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail constructor-recursive
```

## Worked Trace: `reverse(r, [b,a])`

Legacy exact output:

```text
r = [a,b]
```

Operational trace:

1. The query runs as a legacy negative procedure call:
   `neg(reverse(R, [b,a]))`.
2. The legacy `l-ground` guard projects the call arguments. `[b,a]` is ground,
   and `R` is a bare host logic variable, not a `(par ...)` term, so the guard
   admits the call.
3. The recursive `reverse` alternative introduces `rh`, `rt`, and `rrp`:
   `R = cons(rh, rt)`, `reverse(rt, rrp)`,
   `append(rrp, cons(rh, nul), [b,a])`.
4. The append target `[b,a]` allows the recursive branch to bind
   `rrp = [b]` and `rh = a`.
5. The recursive call `reverse(rt, [b])` then binds `rt = [b]`.
6. Substitution composes back to `R = cons(a, cons(b, nul))`.

Greenfield ordinary raw result:

| Case | Target Found | Closed Count | Raw Count |
| --- | --- | ---: | ---: |
| `reverse-input-flat` | false | 0 | 4 |

The greenfield raw answer path exports residual frontiers before the ordinary
answer stream reaches the closed target. The constructor-recursive sidecar can
produce `r = [a,b]`, which proves the formula shape is recoverable, but that is
not yet the ordinary raw path required by ADR-32.

## Worked Trace: `reverse([[a],[b],[c]], r)`

Legacy exact output:

```text
r = [[c],[b],[a]]
```

Operational trace:

1. The query runs as `neg(reverse([[a],[b],[c]], R))`.
2. The input list is ground and `R` is a bare host logic variable, so the
   legacy guard admits the call.
3. Recursive reverse peels only the outer list constructor. The nested element
   `[a]` is an opaque term at this level, not a recursive list to traverse.
4. Three recursive reverse steps reach the base case `reverse(nul, nul)`.
5. The unwind uses append:
   `append([], [[c]], [[c]])`,
   `append([[c]], [[b]], [[c],[b]])`,
   `append([[c],[b]], [[a]], [[c],[b],[a]])`.
6. Substitution binds `R = [[c],[b],[a]]`.

Greenfield ordinary raw result:

| Case | Target Found | Closed Count | Raw Count |
| --- | --- | ---: | ---: |
| `reverse-output-nested-longer` | false | 0 | 4 |

The current ordinary raw path does not descend far enough before export. The
constructor-recursive sidecar closes this row quickly because it recognizes the
guarded constructor-recursive shape and treats nested elements opaquely, as the
legacy proof effectively does.

## Worked Trace: `reverse([a,b,c], cons(c, r))`

Legacy exact output:

```text
r = [b,a]
```

Operational trace:

1. The query runs as `neg(reverse([a,b,c], cons(c, R)))`.
2. The input list and output head constructor are ground enough for legacy's
   projected guard; `R` is a bare host logic variable and is admitted.
3. Recursive reverse peels `a`, then `b`, then `c`.
4. The base case produces the empty reversed tail.
5. Append constraints rebuild the output from the unwind. The outer output is
   already constrained to `cons(c, R)`, so the first reconstructed head must be
   `c`.
6. The remaining append constraints bind `R = cons(b, cons(a, nul))`.

Greenfield ordinary raw result:

| Case | Target Found | Closed Count | Raw Count |
| --- | --- | ---: | ---: |
| `reverse-partial-output-tail` | false | 0 | 4 |

Again, the constructor-recursive sidecar closes the row, but the ordinary raw
answer export path records no closed target within the ADR-32 slice.

## Worked Trace: `jump(x, 0)`

Legacy exact output:

```text
x = 2, 3, 3, 4
```

The duplicate `3` is expected from two paths.

Operational trace:

1. `jump(x, 0)` unfolds to `exists z. step(x, z) and step(z, 0)`.
2. `step(z, 0)` has two alternatives:
   `z = s(0)` and `z = s(s(0))`.
3. If `z = 1`, then `step(x, 1)` yields `x = 2` and `x = 3`.
4. If `z = 2`, then `step(x, 2)` yields `x = 3` and `x = 4`.
5. Legacy therefore emits `2, 3, 3, 4` under `run 4`.

Greenfield result:

- The decimal set is correct: `{2, 3, 4}`.
- The test still fails because one exported record carries a deferred
  non-disequality residual, `neg(step(...))`, while the test requires residuals
  to be empty or disequality-only.

So this failure is not a missing numeric answer. It is an answer-frontier
discipline failure: greenfield exports an unresolved procedural residual that
legacy's direct `proveo` path does not expose for this test.

## Worked Trace: `down(2, y)`

Legacy exact output:

```text
y = 2, 1
```

Operational trace:

1. The clause is `down(x, y) :- x = y ; exists z. x = s(z) and down(z, y)`.
2. The base alternative for `down(2, y)` binds `y = 2`.
3. The recursive alternative binds `z = 1` and calls `down(1, y)`.
4. The next base alternative binds `y = 1`.
5. Under `run 2`, legacy therefore emits `[2, 1]`.

Greenfield result:

```text
expected: [2 1]
actual:   [1 2]
```

This is not a semantic absence. Greenfield finds the same two descendants but
ranks the recursive answer before the base answer. The failure is therefore an
answer ordering / prioritization difference from the legacy direct path.

## Summary

The exact legacy probes confirm that legacy can close all three ADR-32 carried
reverse shapes. The current greenfield ordinary raw answer path still cannot,
even though the constructor-recursive sidecar can.

For the two synthesis-mode failures, greenfield is closer:

- `jump(x, 0)` has the right ground set, but one record still carries a
  non-disequality residual;
- `down(2, y)` has the right set, but the order is reversed.

The common architectural signal is not core.logic vector allocation or a small
walk cache. The difference is still at the proof/answer frontier: legacy lets
bare host logic variables flow through ordinary `proveo` recursion and bind
inside the same path, while greenfield routes open answers through explicit
answer-state export, residual deferral, and prioritization.
