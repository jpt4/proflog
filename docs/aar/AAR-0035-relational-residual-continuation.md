# AAR-0035: Relational Structural Residual Continuation

- Date: 2026-05-03
- Related ADR: [ADR-0035](../adr/ADR-0035-relational-residual-continuation.md)
- Outcome: completed with follow-up for full raw live-state enumeration

## What Happened

ADR-0035 replaced the ordinary answer-path dependency on
`proflog.kernel.constructor-recursive/settle-record` with generic residual
continuation in `proflog.answer-overlay`.

The completed implementation is a conservative pre-export scheduler:

- it runs while the raw answer state still has live `sigma`, `neqs`, residuals,
  proof variables, and program guarded-clause IR;
- it prioritizes structurally demanded residual calls without naming list
  predicates or constructors;
- it prefilters guarded alternatives relationally before recursive descent;
- it tracks active ground calls to avoid recursive reentry loops; and
- it emits answer-overlay proof evidence instead of
  `constructor-recursive-*` proof tags.

The older constructor-recursive namespace remains as a diagnostic oracle and
comparison layer, not as the public answer completion path.

## Exit Criteria

ADR-0035 satisfies its exit criteria:

- promoted ADR-0033/ADR-0035 matrix rows close with
  `constructor-recursive/settle-record` redefined to throw;
- public answer records for the promoted rows contain ordinary answer-overlay
  evidence rather than `constructor-recursive-*` proof tags;
- ordinary `query-answers` no longer requires sidecar settlement for structural
  completion;
- diagnostics can still expose raw unresolved frontiers when structural
  continuation is disabled;
- production code remains generic over guarded-clause IR;
- `proflog.synthesis-modes-test`, `proflog.list-kernel-matrix-test`,
  `lein test-proflog-constructor-recursive`, and `lein test-proflog-fast` are
  green on the completed branch; and
- the full list-kernel probe catalog eventually finds every target under a
  longer per-case timeout.

## Verification

The ADR work was committed as:

```text
f9e34a3 Add ADR-35 visited continuation guard
```

Focused and suite verification performed during closeout:

```text
lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

lein test-proflog-extended
  Ran 68 tests containing 203 assertions.
  0 failures, 0 errors.

lein test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

lein test proflog.synthesis-modes-test
  0 failures, 0 errors.

timeout -k 10s 420s lein test proflog.list-kernel-matrix-test
  0 failures, 0 errors.

timeout -k 10s 480s lein test :only proflog.list-kernel-matrix-test/list-kernel-matrix-promotes-guarded-raw-kernel-rows
  Ran 1 test containing 11 assertions.
  0 failures, 0 errors.
```

The current long-timeout catalog sweep also found every target. The expensive
outlier remains `append-inverse-flat-longer`, which found all five inverse split
targets in about `509.5 s` with `raw-limit 32`. See
[List-Kernel Matrix Long-Timeout Sweep](../log/2026-05-03-list-kernel-matrix-long-timeout-sweep.md).

## What Remains

ADR-0035 completes the intended replacement of the sidecar in the ordinary
answer path, but it does not turn the prototype
`continue-structural-residualso` relation into a fully enumerating raw
live-state continuation.

That is an explicit follow-up, not an exit blocker. The default scheduler is a
controlled derived tableau macro-rule that closes the promoted residual families
without exporting unresolved obligations and without relying on host-side
constructor-recursive settlement.

The remaining practical constraint is runtime: the full list-family probe
catalog eventually closes, but some rows are far too expensive for default test
gates. `append-inverse-flat-longer` should remain a logged stress probe until
there is a new search-control or tabling improvement.

## Decision

Close ADR-0035 as completed. Future work should treat the constructor-recursive
layer as a diagnostic oracle and should focus on either:

- a fully relational raw live-state continuation that can enumerate multiple
  completions from one frontier; or
- a better generic memoization/tabling strategy for repeated continuation
  subproblems.
