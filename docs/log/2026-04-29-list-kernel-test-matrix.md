# List Kernel Test Matrix

Date: 2026-04-29

This note records a raw-kernel matrix for `append/3` and `reverse/2` list
programs after ADR-0030. The probe intentionally bypasses public
`query-answers`, because that API can use list-family materializers above the
central program prover. The goal is to determine what the ordinary kernel and
answer overlay can prove/export unaided.

## Probe

The executable matrix lives in:

- `src/proflog/list_kernel_matrix_probe.clj`
- lightweight catalog coverage: `test/proflog/list_kernel_matrix_test.clj`

Run one row per process:

```sh
timeout 90s lein probe-proflog-list-kernel-matrix append-forward-flat-2
```

Ground rows call `query/query-succeeds` directly. Synthesis rows call the
private raw answer-state path used by diagnostics:

- `answers/program-raw-answer-states`
- `answers/export-program-answer-record`
- `answers/merge-answer-records`
- `answers/prioritize-answer-records`

This is deliberately a diagnostic boundary, not a public API.

## Results

| Case | Mode | Shape | Result |
| --- | --- | --- | --- |
| `append-forward-flat-2` | forward ground | flat two-step | pass |
| `append-forward-nested-2` | forward ground | nested two-step | pass |
| `append-forward-flat-3` | forward ground | flat longer | 90s timeout |
| `append-forward-nested-3` | forward ground | nested longer | 90s timeout |
| `reverse-forward-flat-2` | forward ground | flat two-element | pass |
| `reverse-forward-nested-2` | forward ground | nested two-element | pass |
| `reverse-forward-flat-3` | forward ground | flat longer | 90s timeout |
| `reverse-forward-nested-3` | forward ground | nested longer | 90s timeout |
| `append-output-flat` | output synthesis | flat | no closed target; 4 raw states in 22.6s |
| `append-output-nested` | output synthesis | nested | no closed target; 4 raw states in 21.5s |
| `append-suffix-flat` | partial suffix | flat | no closed target; 4 raw states in 8.4s |
| `append-suffix-nested` | partial suffix | nested | no closed target; 4 raw states in 7.8s |
| `append-prefix-flat` | partial prefix | flat | no closed target; 4 raw states in 45.5s |
| `append-inverse-flat` | inverse splits | flat | partial only: 1 of 4 closed splits |
| `append-inverse-nested` | inverse splits | nested | partial only: 1 of 3 closed splits |
| `reverse-output-flat` | output synthesis | flat | no closed target; 4 raw states in 6.5s |
| `reverse-input-flat` | input synthesis | flat | no closed target; 4 raw states in 38.5s |
| `reverse-output-nested` | output synthesis | nested | no closed target; 4 raw states in 6.2s |
| `reverse-output-nested-longer` | output synthesis | nested longer | no closed target; 4 raw states in 6.4s |
| `reverse-partial-output-tail` | partial output | flat longer | no closed target; 4 raw states in 22.9s |

An additional legacy-stream raw probe for `reverse([a,b], r)` with
`call-depth = 3` reached raw limits 1, 2, 4, 8, 16, and 32 without the closed
target, then hit the same 90 second process timeout before raw limit 64.

## Interpretation

ADR-0030 made an important but narrow improvement. The central program prover
can now close the two-step ground constructor-recursive list cases, including
nested list elements because the recursion depends on outer list shape rather
than element internals.

The kernel does not yet scale to longer outer-list recursion. Length-three
ground append and reverse cases time out under the same 90 second process
budget used for the matrix.

Reverse and partial synthesis modes for `append` and `reverse` are not yet
served by the central program prover in a practical way. The answer overlay can
export shallow residual frontiers, and public `query-answers` can sometimes
materialize closed list-family answers above the kernel, but the raw answer
state path does not produce the requested closed targets for the matrix rows.

The next kernel improvement should therefore target recursive answer-mode
descent and closed-answer extraction for constructor-recursive calls, not just
ground branch closure. The matrix suggests two concrete gaps:

- recursive calls are still exported as residuals before enough guard-driven
  descent happens to close the target;
- inverse split enumeration finds the base split but does not enumerate the
  recursive splits through the raw kernel within the tested bounds.
