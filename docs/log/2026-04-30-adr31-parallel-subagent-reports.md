# 2026-04-30 ADR-0031 Parallel Sub-Agent Reports

## Context

Five `gpt-5.5` / `xhigh` worker agents explored the remaining ADR-0031
list-family improvement avenues concurrently on separate branches and
worktrees. Each worker was asked to stay generic across constructor-recursive
Proflog programs, avoid production checks for `append`, `reverse`, `cons`, or
`null`, and either commit a coherent prototype or commit a documented negative
probe result.

Base branch at launch:

```text
adr-0031-list-family-kernel-generalization @ e93fbd4
```

## Summary Matrix

| Worker | Branch | Commit | Result | Recommendation |
| --- | --- | --- | --- | --- |
| Popper | `adr31/structural-descent` | `a3bd205` | Generic structural descent credit improved multiple append rows under exhausted call-depth, but did not improve reverse blockers and left two synthesis-mode failures. | Park as ADR-31 input; do not merge as-is. |
| Euler | `adr31/demand-ir` | `ca2ba2f` | Generic demand-selection prototypes regressed answer ordering or timed out reverse answer rows. | Keep documentation only. |
| Boyle | `adr31/constructor-recursive-layer` | `4642cf9` | Generic constructor-recursive sidecar layer closed multiple append and reverse ADR-31 rows with proof tags. | Worth code review as the main promising prototype; not final ADR-31 solution yet. |
| Curie | `adr31/answer-continuation` | `c1b3612` | Constructor-visible continuation rule slowed passing rows and did not close reverse blockers. | Keep documentation only. |
| Faraday | `adr31/answer-tabling` | `eecf23a` | Diagnostics showed duplicate exported records, not repeated raw proof families. | Keep documentation only. |

## Popper: Structural Descent

Branch:

```text
/home/jpt4/code/proflog-worktrees/structural-descent
adr31/structural-descent
```

Commit:

```text
a3bd205 Prototype ADR-31 structural descent credit
```

Changed files:

- `src/proflog/kernel_support.clj`
- `src/proflog/answer_overlay.clj`
- `src/proflog/list_kernel_matrix_probe.clj`
- `test/proflog/structural_descent_test.clj`
- `docs/log/2026-04-30-adr31-structural-descent-probe.md`

What changed:

- Added generic proper-subterm and same-relation structural descent relations.
- Added an opt-in answer-overlay call-stack path that can credit same-relation
  structural descent after answer `call-depth` is exhausted.
- Enabled that path only in the ADR-31 raw matrix probe.

Reported improvements:

- `append-output-flat` with `{:call-depth 0}`: target found.
- `append-output-nested` with `{:call-depth 0}`: target found.
- `append-prefix-flat` with `{:call-depth 0}`: target found.

Commands reported green:

```text
lein test proflog.structural-descent-test proflog.list-kernel-matrix-test
lein test-proflog-constructor-recursive
lein test-proflog-fast
lein test proflog.answers-test proflog.reverse-program-synthesis-test proflog.subst-test proflog.kernel-test
```

Reported failures or residual risks:

- `reverse(r, [b,a])`: still not found.
- `reverse([[a],[b],[c]], r)`: still not found.
- `reverse([a,b,c], cons(c, r))`: still not found.
- `lein test proflog.synthesis-modes-test` failed two tests on the prototype
  branch.

Recommendation:

Do not merge the branch as-is. The generic descent predicate is useful evidence
and may be reusable, but reverse still needs a broader dependent-call,
frontier-ordering, or continuation strategy.

## Euler: Demand IR

Branch:

```text
/home/jpt4/code/proflog-worktrees/demand-ir
adr31/demand-ir
```

Commit:

```text
ca2ba2f Document ADR-31 demand IR probe
```

Changed file:

- `docs/log/2026-04-30-adr31-demand-ir-worker2-probe.md`

Prototype tried:

- A generic guarded-call demand selector that preserved source order when the
  first call already exposed constructor demand, and otherwise selected a later
  demanded call.

Commands reported:

```text
lein test proflog.language-test proflog.program-test proflog.kernel-test proflog.constructor-recursive-kernel-test
timeout 120s lein probe-proflog-list-kernel-matrix reverse-output-flat
timeout 120s lein probe-proflog-list-kernel-matrix reverse-input-flat
git diff --check
```

Reported results:

- Focused tests passed: 33 tests, 75 assertions.
- `reverse-output-flat` baseline remained passing.
- `reverse-input-flat` still failed.
- One variant timed out reverse answer rows.
- The faster variant displaced the existing `reverse([a,b], r)` closed answer
  under the raw limit.

Recommendation:

Keep as a negative probe note. Do not merge a production demand selector until
demand is represented outside the hot proof stream or raw-limit accounting is
made robust against introduced proof-shape variants.

## Boyle: Constructor-Recursive Layer

Branch:

```text
/home/jpt4/code/proflog-worktrees/constructor-recursive-layer
adr31/constructor-recursive-layer
```

Commit:

```text
4642cf9 Prototype constructor-recursive proof layer
```

Changed files:

- `src/proflog/kernel/constructor_recursive.clj`
- `test/proflog/kernel/constructor_recursive_test.clj`
- `src/proflog/list_kernel_matrix_probe.clj`
- `project.clj`
- `docs/log/2026-04-30-constructor-recursive-layer-prototype.md`

What changed:

- Added a generic guarded-IR constructor-recursive sidecar layer with explicit
  `constructor-recursive-*` proof tags.
- Added conservative settlement for deferred negative defined-call residuals.
- Added an opt-in `constructor-recursive` matrix probe mode.

Commands reported green:

```text
lein test proflog.kernel.constructor-recursive-test
lein test-proflog-constructor-recursive
lein test proflog.kernel.dispatch-test
lein test-proflog-fast
lein run -m proflog.list-kernel-matrix-probe reverse-input-flat constructor-recursive
lein run -m proflog.list-kernel-matrix-probe reverse-output-nested-longer constructor-recursive
lein run -m proflog.list-kernel-matrix-probe append-inverse-flat constructor-recursive
```

Reported improvements:

- `reverse-input-flat`: target found.
- `reverse-output-nested-longer`: target found.
- `append-inverse-flat`: all four targets found.
- Focused tests also covered Peano recursion, residual settlement, and multiple
  append/reverse matrix rows.

Reported boundary:

- `timeout 240s lein test proflog.list-kernel-matrix-test` timed out, which
  preserves the known raw-matrix blocker.
- The layer is a sidecar diagnostic/proof layer, not yet wired into ordinary
  branch-level dispatch.

Recommendation:

Worth detailed review as the most promising ADR-31 prototype. Do not treat it
as ADR-31 complete until its role relative to the ordinary kernel/raw answer
boundary is settled.

## Curie: Answer Continuation

Branch:

```text
/home/jpt4/code/proflog-worktrees/answer-continuation
adr31/answer-continuation
```

Commit:

```text
c1b3612 Document ADR-31 answer continuation probe
```

Changed file:

- `docs/log/2026-04-30-adr31-answer-continuation-probe.md`

Prototype tried:

- A generic constructor-visible residual continuation rule, materially
  different from strict residual deferral.
- A scored post-frontier continuation sketch.

Commands and results reported:

```text
lein test proflog.answers-test
```

The test passed: 17 tests, 64 assertions.

Baseline probes:

- `append-output-flat`: target found in about 6.1s.
- `reverse-output-flat`: target found in about 15.0s.
- `reverse-input-flat`: target not found in about 49.8s.

Prototype probes:

- `append-output-flat`: slowed to about 16.3s.
- `reverse-output-flat`: slowed to about 45.4s.
- `reverse-input-flat`: still failed in about 135.3s.
- `reverse-output-nested-longer`: still failed in about 98.3s.
- `reverse-partial-output-tail`: timed out.

Recommendation:

Keep as a negative probe note. The continuation idea likely needs a deeper
proof-state representation rather than another export-time or broad deferral
pass.

## Faraday: Answer Tabling

Branch:

```text
/home/jpt4/code/proflog-worktrees/answer-tabling
adr31/answer-tabling
```

Commit:

```text
eecf23a Document ADR-31 answer tabling probe
```

Changed file:

- `docs/probe/2026-04-30-adr31-answer-tabling.md`

Commands reported:

```text
lein test proflog.answers-test proflog.tabling-test
```

The tests passed: 22 tests, 75 assertions.

Diagnostic findings:

- Append rows showed many duplicate exported records, but zero duplicate proof
  signatures.
- Reverse blocker rows showed little to no duplicate proof-signature signal.
- `reverse-partial-output-tail` had only one duplicate proof signature at raw
  limit 4.
- `reverse-input-flat` and `reverse-partial-output-tail` timed out under 90s
  targeted matrix runs.

Recommendation:

Keep as a negative probe note. Current diagnostics argue against implementing
answer-overlay tabling next, because the observed duplication is mostly after
export/canonical answer merging rather than repeated raw proof families.

## Overall Implication

The parallel search narrowed ADR-31 substantially:

- Demand scheduling, answer continuation, and answer-path tabling are not
  justified as immediate implementation directions.
- Structural descent is a useful generic primitive but insufficient by itself.
- The constructor-recursive sidecar is the only branch that demonstrated broad
  reverse-family improvement, but it still needs architectural review before it
  can be credited against the ordinary kernel/raw answer exit criteria.
