# ADR-0035: Relational Structural Residual Continuation

- Status: completed
- Date: 2026-05-01
- Branch: `adr-0035-relational-residual-continuation`
- AAR: [AAR-0035](../aar/AAR-0035-relational-residual-continuation.md)
- Depends On:
  - [ADR-0033](ADR-0033-structural-answer-variable-recursion.md)
  - [ADR-0034](ADR-0034-greenfield-implementation-tutorial.md)

## Context

ADR-0033 closed the carried ADR-0031 list-family failures in the ordinary raw
matrix path, but the implementation still uses a post-export
constructor-recursive sidecar. That sidecar is generic over program symbols and
works over compiled guarded-clause IR, but it is plain Clojure, not a
`core.logic` relation.

This matters for Proflog's semantic goal. The project intends the semantic
tableau proof procedure to be the operational semantics of the language.
Background proof layers are acceptable when they are themselves variants of
tableau proving over isolated fragments, as with the profiled propositional and
first-order layers. The constructor-recursive sidecar is different: it is
closer to a small SLD / definite-clause interpreter over guarded IR.

The sidecar currently:

- selects guarded alternatives from the compiled program;
- freshens clause-local variables;
- unifies call arguments and equality guards with a host-side substitution map;
- checks rigid constructor disequalities;
- recursively solves positive defined calls; and
- discharges negative residual calls by constructively proving their atoms.

Those operations overlap strongly with existing relational machinery in
`proflog.answer-overlay`, `proflog.equality`, and `proflog.program`. In
principle, the sidecar's useful behavior should be expressible as a relational
answer-overlay rule using `core.logic` unification and the same guarded
procedure-call interface already used by the ordinary proof path.

ADR-0035 therefore attempts option (2) from the proof-term assessment: replace
the sidecar's ordinary answer-path role with relational structural residual
continuation inside `proflog.answer-overlay`.

## Decision

Implement structural residual continuation as a relational answer-overlay
capability, not as a post-export Clojure sidecar.

The new path should continue structurally productive residual frontiers while
the raw answer state is still live:

- branch substitution `sigma`;
- disequality store `neqs`;
- residual frontier;
- answer variables;
- proof variables;
- program and guarded-clause IR;
- fuel;
- call depth; and
- proof term.

The accepted implementation must:

- operate through `core.logic` relations in the answer overlay;
- use `equality/unify-termo` and related relational equality helpers rather
  than the sidecar's Clojure substitution map;
- call existing guarded procedure-call relations instead of reimplementing
  program evaluation host-side;
- preserve diagnostics that can still expose unresolved raw residual frontiers;
- keep `proflog.kernel.constructor-recursive` only as a diagnostic or
  migration oracle until parity is established; and
- produce ordinary answer-overlay proof evidence, not
  `constructor-recursive-*` proof terms, for public answer records.

Production code must remain generic. It may not dispatch on `append`,
`reverse`, `cons`, `null`, or any other named list-family symbol.

## Implementation Status

The current implementation removes the ordinary answer-path dependency on
`proflog.kernel.constructor-recursive/settle-record` for the promoted
ADR-0033/ADR-0035 rows.

The production path is a conservative answer-overlay scheduler:

- `proflog.answer-overlay/schedule-structural-residual-frontiero` runs before
  answer export while `sigma`, `neqs`, and the residual frontier are still
  live;
- it fires only for structurally productive negative-call frontiers;
- it emits compact `structural-residual-scheduler-continue` /
  `structural-residual-continuation` proof evidence;
- diagnostics can opt out and still expose the raw unresolved frontier; and
- `proflog.answer-overlay/continue-exported-structural-recordo` remains as a
  post-export compatibility fallback.

This is sidecar-independent and generic over guarded-clause IR, but it is not
yet the fully enumerating raw `core.logic` continuation described as the ideal
above. Directly wiring `continue-structural-residualso` into the default answer
path still reopens too much search on the focused reverse-input row. That
relation remains as the follow-up target for enumerating multiple residual
completions from one live frontier.

Track B adds an explicit relational guard-prefilter to the raw live-state
structural continuation relation. `prefilter-structural-guardso` runs after
guarded scope opening and before recursive descent through guarded calls. It
uses `equality/unify-termo` for equality guards, preserves the proof-variable
binding discipline and saved-disequality stability checks, and accepts
disequality guards only when `support/rigid-different-termo` can prove a
permanent constructor difference. Successful raw-continuation proofs now carry
`structural-residual-guard-prefilter` evidence. Constructor-clashing guarded
alternatives are rejected before their recursive calls can be opened, while a
later viable alternative remains available. The implementation is generic over
guarded-clause IR and does not add any kernel changes.

The Track B prefilter is intentionally conservative. Symbolic disequality
guards that would require extending the live disequality store remain outside
this narrow gate until the broader raw continuation work adds explicit
guard-level disequality threading.

Track C adds ordered structural residual selection for the live scheduler.
`prioritize-structural-residual-frontiero` keeps the selector generic over
guarded-clause IR: it preserves an already demanded head residual, otherwise
uses relational list selection to move a constructor-demanded negative call to
the front while preserving the relative order of the remaining frontier. The
default scheduler now records `structural-residual-priority-*` proof evidence
inside `structural-residual-scheduler-continue` and uses a soft cut so raw
frontier export is considered only if prioritized continuation cannot close.

Track D adds an active-call visited table to the fast residual continuation
state. Object-language ground calls are keyed by the canonical walked atom.
Open symbolic calls, including calls with unresolved internal parameters, are
not tabled, because productive reverse/append continuation can revisit the
same open shape while making progress through surrounding substitutions. The
table rejects recursive reentry for a ground call already active on the
continuation stack, then removes that key after a successful call returns so
sequential duplicate calls are not globally pruned. This is a conservative
cycle guard, not a full answer memo table; it bounds ground self-recursive
continuation alternatives without changing the kernel or adding
predicate-specific dispatch.

## Required Capabilities

### 1. Relational Structural Frontier Classification

Add a relational or relation-adjacent classifier for residual frontiers that
identifies when a residual list is safe to continue rather than export.

The first accepted version may match ADR-0033's conservative shape:

- every residual is a negative call to a defined relation; and
- at least one residual exposes constructor demand after walking through the
  current substitution, or shares answer variables with such a demanded
  residual.

The classifier must remain structural. It may inspect term shape, declared
program relation availability, answer-variable membership, constructor form,
and current equality state. It must not inspect specific predicate or
constructor names.

### 2. Relational Residual Continuation

Add an answer-overlay relation that consumes a residual frontier and attempts
to close structurally continuable negative defined calls before export.

The relation should thread the same state shape as `prove-stateo`:

- `sigma` to `sigma-out`;
- `neqs` to `neqs-out`;
- `residuals` to `residuals-out`;
- `fuel`;
- `call-depth`;
- `answer-vars`;
- `proof-vars`;
- `prog`;
- `gamma-terms`; and
- `proof`.

It should call the existing guarded-call machinery where possible:

- `program/call-clause-with-guarded-alternativeso`;
- `close-one-guarded-alternativeo`;
- `close-guarded-negated-call-sequenceo`;
- `saturate-eq-guardso`; and
- `close-formula-sequenceo`.

If the continuation exhausts a real budget or sees an unsafe frontier, it
should leave residuals explicit rather than silently dropping them.

### 3. Ordinary Proof Vocabulary

The relational path should use existing answer-overlay proof tags where
possible:

- `guarded-call-seq-step`;
- `neg-call-guarded-alt`;
- `guarded-alt`;
- `guarded-neg-alt-saturated`;
- `guard-eq`;
- `guarded-residual-seq-step`; and
- ordinary equality proof tags such as `eq-bind` and `decompose`.

If a new tag is needed, it should be an answer-overlay tag such as
`structural-residual-continuation`, not a `constructor-recursive-*` tag. The
new tag must document which ordinary tableau obligations it abbreviates.

### 4. Sidecar Oracle During Migration

During implementation, the existing constructor-recursive namespace may remain
as a comparison oracle. Tests may compare its closed bindings against the new
relational path.

The sidecar must not be required for `query-answers`, the ordinary raw matrix
rows, or the ADR exit criteria once ADR-0035 is complete.

### 5. Diagnostics and Opt-Out

ADR-0033 preserved diagnostics that expose raw unresolved frontiers. ADR-0035
must preserve that property.

There should be a way to ask:

- what the raw answer overlay produced before structural continuation; and
- what the integrated continuation completed afterward.

This is necessary both for debugging and for demonstrating that the new rule is
not merely hiding unresolved proof work.

## Derived Tableau Macro-Rule

ADR-0035 treats the current sidecar behavior as evidence for a derived tableau
macro-rule, not as a final independent semantics.

Informally:

```text
If, under the current branch substitution, a residual negative defined call
not R(t1, ..., tn) is structurally guarded, and guarded descent through the
compiled clauses constructively proves R(t1, ..., tn), then the branch may
close that residual obligation.
```

The rule is "derived" because it should be admissible from ordinary Proflog
tableau rules:

- expand the defined procedure call through the Procedure Call Rule;
- open the clause body's quantifier scope;
- saturate equality guards by ordinary equality reasoning;
- recursively close guarded calls by the same procedure-call rule; and
- close constructor disequalities only when free constructors are rigidly
  distinct.

The rule is a "macro" only in the explanatory sense. The preferred ADR-0035
endpoint is not a permanent unchecked macro prover; it is a relational
implementation in `answer-overlay` that makes the macro disappear back into
ordinary proof search.

## Implementation Order

1. Add failing tests that run the ADR-0033 passing rows while redefining
   `constructor-recursive/settle-record` to throw. These tests should
   demonstrate that the current implementation still depends on the sidecar.
2. Add proof-shape tests showing that completed public answers contain ordinary
   answer-overlay proof tags and no `constructor-recursive-*` tags.
3. Add a relational structural-frontier classifier for the conservative
   ADR-0033 residual shape.
4. Add a bounded residual-continuation relation in `answer-overlay` and invoke
   it before residual export.
5. Compare the relational path against the sidecar oracle on non-list
   constructor recursion, representative reverse rows, and inverse append.
6. Remove the ordinary answer-path call to `constructor-recursive/settle-record`
   once relational parity is established.
7. Retest the ADR-0033 matrix and synthesis gates.
8. Write an AAR classifying whether the sidecar was fully retired, retained as
   diagnostics only, or still required for any row.

## Test Obligations

Add focused failing tests for:

- ADR-0033 matrix rows with `constructor-recursive/settle-record` disabled;
- proof records for closed answer rows containing no `constructor-recursive-*`
  tags;
- raw diagnostics still exposing unresolved frontiers when continuation is
  disabled;
- a non-list constructor-recursive program closing through the relational
  answer-overlay path;
- relational continuation of `reverse(r, [c,b,a])`;
- relational continuation of
  `reverse([[[a]],[[b]],[[c]]], r)`;
- relational continuation of `reverse([a,b,c,a], cons(a, r))`; and
- multi-answer inverse append without sidecar settlement.

Minimum verification commands:

```text
lein test proflog.answers-test
lein test proflog.synthesis-modes-test
timeout -k 10s 360s lein test proflog.list-kernel-matrix-test
lein test-proflog-constructor-recursive
lein test-proflog-fast
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-output-deep-nested-longer
timeout -k 10s 240s lein probe-proflog-list-kernel-matrix reverse-partial-output-longer-tail
```

The heavy `append-inverse-flat-longer` probe should be run and logged, but it
need not be a default gate because it is intentionally slow.

## Exit Criteria

ADR-0035 is complete only if:

- all ADR-0033 promoted list-family rows still pass;
- the promoted rows pass with `constructor-recursive/settle-record` disabled;
- public answer records for those rows contain ordinary answer-overlay proof
  evidence and no `constructor-recursive-*` proof tags;
- ordinary `query-answers` no longer requires
  `constructor-recursive/settle-record` for structural completion;
- diagnostics can still expose pre-continuation residual frontiers;
- the implementation remains generic and projection-free in the kernel-facing
  path;
- `proflog.synthesis-modes-test` passes;
- `proflog.list-kernel-matrix-test` passes;
- `lein test-proflog-constructor-recursive` remains green as a diagnostic
  oracle suite; and
- `lein test-proflog-fast` remains green.

## Consequences

If successful, ADR-0035 restores a cleaner semantic story: list-family answer
closure remains an answer-overlay/tableau capability rather than a second
host-side evaluator.

The cost is implementation risk. The current sidecar succeeds partly because it
controls search with direct Clojure recursion, direct substitution walking, and
deterministic alternative order. The relational version may reopen timeouts
unless its continuation point, fuel accounting, residual ordering, and demand
selection are carefully constrained.

Rejected alternatives:

- Keep the Clojure sidecar as a permanent background prover. This preserves
  current behavior but weakens the claim that tableau proof search is Proflog's
  operational semantics.
- Wrap sidecar results as unchecked proof terms. This improves proof reporting
  but does not solve the semantic concern.
- Delete sidecar completion immediately. That would regress ADR-0033's
  recovered rows before the relational path exists.

## References

- [ADR-0033 Structural Answer-Variable Recursion](ADR-0033-structural-answer-variable-recursion.md)
- [Constructor-Recursive Proof Terms and Integration Path](../log/2026-05-01-constructor-recursive-proof-terms.md)
- [ADR-33 Structural Completion Progress](../log/2026-05-01-adr33-structural-completion-progress.md)
- [Intensified List-Family Matrix](../log/2026-05-01-list-family-intensified-matrix.md)
- [Three-Element Reverse Input-Synthesis Trace](../log/2026-05-01-three-element-reverse-trace.md)
- [ADR-0035 Track B Guard Prefiltering](../log/2026-05-03-adr35-track-b-guard-prefilter.md)
- [ADR-0035 Track C Structural Priority](../log/2026-05-03-adr35-track-c-structural-priority.md)
- [ADR-0035 Track D Visited Continuation](../log/2026-05-03-adr35-track-d-visited-continuation.md)
- [List-Kernel Matrix Long-Timeout Sweep](../log/2026-05-03-list-kernel-matrix-long-timeout-sweep.md)
- [AAR-0035 Relational Structural Residual Continuation](../aar/AAR-0035-relational-residual-continuation.md)
- [Greenfield Implementation Tutorial and Reference](../GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
