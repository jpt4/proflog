# ADR-0035 Sidecar-Independent Structural Continuation

Date: 2026-05-01

## Summary

ADR-0035 now has a sidecar-independent ordinary answer path for the promoted
ADR-0033 residual-completion rows. Public answer export no longer calls
`proflog.kernel.constructor-recursive/settle-record`; tests redefine that
function to throw while checking the promoted matrix rows and the proof shape
for `reverse(r, [c,b,a])`.

The implementation keeps the existing constructor-recursive namespace as a
diagnostic oracle suite, but ordinary answer search now runs an answer-overlay
residual scheduler before answer export. Successful scheduled records append
compact `structural-residual-scheduler-continue` /
`structural-residual-continuation` proof evidence and do not append
`constructor-recursive-*` proof tags.

## Implementation Notes

The first attempted implementation added a direct `core.logic` relation in
`proflog.answer-overlay` that continued the raw residual frontier while the
substitution and disequality store were still live. That relation compiled, but
it reopened too much answer-mode search and timed out on the focused
`reverse-input-flat-longer` row.

The retained public path uses the same guarded-clause IR generically, with no
dispatch on `append`, `reverse`, `cons`, `null`, or other list symbols. It
freshens guarded alternatives, unifies parameters and equality guards with an
occurs check, checks rigid constructor disequalities, recursively solves
defined calls, and exports answer-overlay proof tags. The relation is
intentionally concrete at the export boundary: `program`, `record`, and `fuel`
are already known, so it avoids asking `core.logic` to walk and reify the whole
compiled program or a large recursive proof trace.

This removes the ordinary answer-path dependency on the constructor-recursive
sidecar and replaces the earlier `answers.clj` bounded continuation with
answer-overlay relations. The raw live-state `core.logic` prototype remains as
an exploratory relation, but direct use still reopens too much search on the
focused reverse-input row.

The public path now has two sidecar-independent answer-overlay stages. First,
`proflog.answer-overlay/schedule-structural-residual-frontiero` runs while
`sigma`, `neqs`, and the residual frontier are still live. It fires only for
the ADR-0033/ADR-0035 structural shape and uses the same guarded-clause IR to
close the current-test frontiers before export. Second,
`proflog.answer-overlay/continue-exported-structural-recordo` remains as a
post-export compatibility fallback for structurally completable records that
reach export unresolved.

The scheduled path is still forward-only and returns the first successful
structural completion for a concrete live frontier. It is not yet an enumerator
of all possible residual completions. This does not change which programs can
be parsed, compiled, or entered into ordinary proof search. It can affect
completeness for programs whose residual frontier has multiple valid structural
completions that should appear as distinct answers; those remain in scope for
the fully enumerating raw live-state continuation follow-up.

## Raw Live-State Scheduler

The relation scheduler should live in `proflog.answer-overlay`, adjacent to
the answer-mode `close-agendao` / `prove-stateo` agenda machinery, rather than
inside `proflog.kernel`. The ordinary kernel should stay a transparent tableau
engine. Residual continuation is an answer-export concern: it decides when an
answer-mode branch should keep proving a structurally productive residual
frontier before exporting that frontier as an answer record.

The implemented scheduler is a small answer-overlay strategy layer. It receives
the live `sigma`, `neqs`, residual frontier, proof variables, program IR, and
fuel, classifies whether the frontier is structurally productive, and chooses
one of two current outcomes:

- continue the residual frontier before export while `sigma` and `neqs` are
  still live; or
- defer/export the frontier when it is genuinely open or not structurally
  productive.

That keeps the kernel machinery visible: the scheduler does not replace
tableau rules, call-clause lookup, equality, or guarded alternatives. It only
controls when the answer overlay invokes those existing relations for residual
completion.

For the current tests, the scheduler is conservative: it fires only for the
ADR-0033/ADR-0035 structural shape already accepted by `answers.clj`
classification, bounds recursive continuation with fuel, emits compact
answer-overlay proof evidence, and retains diagnostic paths that can still
expose unresolved residual frontiers. The broader relation
`proflog.answer-overlay/continue-structural-residualso` remains in the file as
the target for a future fully enumerating raw live-state continuation, but it
is not used by the default scheduler because direct use still times out.

## Verification

Commands run:

```text
timeout -k 5s 90s lein test :only proflog.answers-test/adr35-raw-matrix-answers-use-relational-continuation-proofs
timeout -k 10s 360s lein test :only proflog.list-kernel-matrix-test/list-kernel-matrix-promotes-guarded-raw-kernel-rows
timeout -k 10s 240s lein test proflog.answers-test
timeout -k 10s 380s lein test proflog.list-kernel-matrix-test
timeout -k 10s 180s lein test proflog.synthesis-modes-test
timeout -k 10s 240s lein test-proflog-constructor-recursive
timeout -k 10s 300s lein test-proflog-fast
```

All passed.

## Follow-Up

- Revisit the raw live-state
  `proflog.answer-overlay/continue-structural-residualso` prototype if ADR-0035
  needs a fully enumerating substitution-store continuation rather than the
  scheduler's first-success live-frontier completion.
- Extend `proflog.answer-overlay/schedule-structural-residual-frontiero` with a
  prune/fail outcome only after there is a focused test showing contradictory
  residual continuation should remove a branch before export.
- Add an AAR only after deciding whether ADR-0035 is complete, partial, or
  superseded by another proof-search strategy.

## Relational Package Integration Plan

Baseline commit for the ordered integration sequence:
`76c33a1 Implement ADR-35 answer-overlay residual scheduler`.

The next package is split into individually gated branches:

- Track A, `adr-0035-track-a-continuation-agenda`: dedicated continuation
  agenda plus explicit continuation fuel.
- Track B, `adr-0035-track-b-guard-prefilter`: relational guard prefiltering
  before recursive continuation descent.
- Track C, `adr-0035-track-c-structural-priority`: ordered structural residual
  selection that tries constructor-demanded frontiers first.
- Track D: relational visited-set/tabling after Tracks A-C establish the final
  continuation state shape.

Tracks A-C may be developed concurrently in separate worktrees, but they merge
back to `adr-0035-relational-residual-continuation` in A, B, C order. Each
merge must carry its own focused property test, pass ADR-0035 focused tests,
and complete integration testing before the next merge. Track D begins only
after the A-C package is integrated. Regressions observed during individual
tracks are recorded, but are not treated as definitive unless the complete
relational package fails to improve coverage/performance, with correctness and
broader test-family coverage prioritized over raw speed.

## Session Reset Handoff

Current base branch:
`adr-0035-relational-residual-continuation` at
`add611e Log ADR-35 relational package integration plan`.

Base commits added before the reset:

- `76c33a1 Implement ADR-35 answer-overlay residual scheduler`
- `add611e Log ADR-35 relational package integration plan`

Concurrent track worktrees:

- Track A worktree: `/home/jpt4/code/proflog-track-a`
  - branch: `adr-0035-track-a-continuation-agenda`
  - subagent: `019dedfa-e458-7e82-941b-252f2896b6cb` (`Linnaeus`)
- Track B worktree: `/home/jpt4/code/proflog-track-b`
  - branch: `adr-0035-track-b-guard-prefilter`
  - subagent: `019dedfa-fc2c-7d12-bb2b-a1d68a0578ee` (`Archimedes`)
- Track C worktree: `/home/jpt4/code/proflog-track-c`
  - branch: `adr-0035-track-c-structural-priority`
  - subagent: `019dedfb-1081-78f0-ae88-414d9f593f94` (`Carver`)

No track branch had been merged at the handoff point. The required merge order
remains Track A, then Track B, then Track C, with focused property tests and
ADR-0035 integration tests after each merge before continuing.
