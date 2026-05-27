# ADR-0024: Pelletier First-Order Performance Closure

- Status: completed
- Date: 2026-04-28
- Branch: `adr-0024-pelletier-first-order-performance`
- AAR: [AAR-0024](../aar/AAR-0024-pelletier-first-order-performance.md)
- Depends On: [ADR-0023](ADR-0023-profiled-kernel-layers.md)

## Context

ADR-0022 ported all 46 upstream Pelletier problems from the `namin/leanTAP`
sources into the greenfield AST catalog. ADR-0023 fixed the last propositional
kernel miss, Pelletier Problem 12, by adding formula-profiled entry-only
dispatch to a small propositional tableau component.

The remaining non-passing Pelletier problems are not formula-porting gaps. They
are all represented in `test/proflog/pelletier_test.clj`, but 18 are still
classified as `:ported-too-slow`:

```clojure
[24 25 26 27 28 29 30 31 32 34 36 37 38 41 43 44 45 46]
```

The current passing set is 28 of 46:

```clojure
[1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20
 21 22 23 33 35 39 40 42]
```

The full `proflog.pelletier-test` namespace passes because the remaining 18
problems are asserted as visible `:ported-too-slow` catalog entries, not because
they are proved.

The next branch should treat those 18 formulas as a comparative prover
engineering problem. It must examine three implementations already present in
or adjacent to this repository:

1. **alphaleanTAP first-order prover** (`src/cljtap/alphaleantap_e.clj`)
   - Uses a narrow theorem-proving state: current formula, unexpanded stack,
     branch literals, environment, and proof.
   - The gamma rule re-enqueues universals directly.
   - Literal closure is checked against the branch without Proflog program
     state, saved disequality state, generated closed-term candidates, or
     procedure-call eligibility.
   - Equality rules exist, but Pelletier formulas should be mostly or entirely
     ordinary equality-free first-order predicate formulas.
2. **Legacy Proflog prover** (`src/cljtap/alphaleantap_ep.clj`)
   - Extends alphaleanTAP-E with procedure calls, single-use universals,
     bounded gamma search, lemma threading across beta siblings, type-dispatched
     literal groups, substitutivity-augmented calls, and equality-triggered
     call rechecks.
   - Some of these features are not relevant to pure Pelletier proving, but the
     operational ideas are worth measuring:
     - gamma budgeting / iterative deepening,
     - lemma reuse across beta branches,
     - literal type dispatch to avoid broad conde fanout,
     - tighter stack discipline for theorem-only search.
3. **Greenfield Proflog kernel** (`src/proflog/kernel.clj`)
   - Preserves the full Proflog proof-state model: fair agenda selection,
     environment, proof variables, equality substitution, delayed disequalities,
     generated gamma candidates, optional program state, saved-call rechecks,
     and procedure-call rules.
   - This breadth is necessary for the full language, but it is operationally
     expensive for pure first-order theorem proving.
   - ADR-0023 already showed that formula profiling can safely route a simpler
     fragment through a simpler relational component without theorem-specific
     dispatch.

The greenfield prover should improve because its formulas are routed through
the weakest sufficient proof component, not because Pelletier problem ids are
special-cased.

## Decision

ADR-0024 will add a comparative Pelletier investigation and use its results to
implement a generic equality-free first-order theorem-proving layer for the
remaining Pelletier problems.

The default implementation hypothesis is:

- keep `proflog.kernel` as the complete Proflog kernel,
- add an equality-free first-order component beside
  `proflog.kernel.propositional`,
- route equality-free call-free theorem formulas through that component from
  `kernel/prove`,
- leave `kernel/proveo`, `kernel/prove-programo`, and `kernel/prove-program`
  on their direct relational/full-kernel paths unless an explicit relational
  first-order entry point is being called,
- promote remaining Pelletier problems only after they close through the
  generic profiled first-order path.

The first-order component should be paper-shaped and relational. It should
initially contain only the rules required for equality-free first-order NNF:

- alpha: conjunction,
- beta: disjunction,
- gamma: universal instantiation with measured re-enqueue discipline,
- delta: existential witness,
- literal save and exact complementary closure.

It must not include equality substitution, disequality stores, generated closed
term candidates, program lookup, procedure-call eligibility, or saved-call
rechecks.

The branch must compare the three prover families before committing to a
specific operational policy. The comparison should answer:

- Which of the remaining 18 close in alphaleanTAP-E, the legacy EP prover, and
  greenfield?
- For each prover that closes a problem, which rule families dominate the proof
  term?
- Is greenfield losing primarily to broad proof state, fair agenda fanout,
  gamma re-enqueue policy, missing lemma reuse, or some combination?
- Which optimizations are generic enough to belong in the equality-free
  first-order layer rather than the full Proflog kernel?

Expected implementation order:

1. Add a comparative harness for the 18 too-slow Pelletier problems.
   - Run the same normalized branch formula, or a documented isomorphic
     converted form, against alphaleanTAP-E, legacy EP, and greenfield.
   - Record pass/fail/timeout, elapsed time, and available proof-step counts.
   - Keep the harness under an explicit selector so it does not enter the fast
     path.
2. Add an equality-free first-order profile test tranche.
   - The remaining 18 should classify as equality-free first-order or be
     explicitly explained if any do not.
3. Add `proflog.kernel.first-order` or
   `proflog.kernel.equality-free-first-order`.
   - Start with the narrow alphaleanTAP-style state.
   - Prefer deterministic stack discipline unless measurements require fair
     agenda selection.
   - Add gamma budgeting or iterative deepening only if the comparison shows it
     improves completeness or bounded operation for the target class.
4. Add public entry-only dispatch from `kernel/prove` for equality-free
   first-order theorem formulas.
   - Program-bearing queries must remain on `kernel/prove-program`.
   - Direct reverse/partial relational use must call a relation directly rather
     than pass through host-side profile dispatch.
5. Promote remaining Pelletier problems in measured tranches.
   - Fast enough problems move into `:pelletier-prompt` only if they are prompt.
   - Slower but reliable problems move into `:pelletier-passing`.
   - Problems still too slow remain classified with measurements and a follow-up
     hypothesis.

Possible enhancements after the first-order layer exists:

- Lemma threading across beta siblings, if legacy EP shows decisive benefit on
  the remaining Pelletier formulas.
- Type-dispatched literal groups, if broad literal conde fanout remains a
  measurable cost.
- Recurrent dispatch through a single dispatcher, only if mixed-profile formulas
  are shown to spend substantial time in broader layers after decomposition.
- A relational structural dispatcher for reverse/partial profile selection, but
  only if direct component entry points prove insufficient.

Rejected first-pass alternatives:

- Marking the 18 problems passing by raising timeouts without changing the
  prover. That would hide the operational gap rather than address it.
- Problem-id dispatch or theorem-specific proof plans. That would violate the
  Pelletier benchmark boundary established in ADR-0022 and ADR-0023.
- Moving legacy EP wholesale into greenfield. The legacy prover contains useful
  operational lessons, but greenfield still needs a small auditable component
  whose state matches the supported formula profile.
- Adding lemma threading or recurrent dispatch directly to the full kernel
  before measuring an equality-free layer. That risks obscuring the complete
  Proflog rules without proving the broader machinery is needed.

## Consequences

This ADR makes the remaining Pelletier work a comparative, measurement-driven
effort instead of an undirected performance chase.

The likely positive outcome is a second profiled layer after ADR-0023: pure
propositional formulas use `proflog.kernel.propositional`, equality-free
first-order theorem formulas use a new first-order component, and the full
kernel remains reserved for equality-bearing and program-bearing formulas.

The primary risk is duplicating alpha/beta/literal mechanics across components.
That duplication is acceptable only while it keeps each component readable as a
small tableau fragment. Shared helpers may be introduced later if they do not
hide rule-local proof-state transitions.

The second risk is weakening relational use by putting more host-side profile
dispatch in front of proof search. The boundary from ADR-0023 remains in force:
host-side dispatch is a forward convenience for `kernel/prove`; reverse and
partial miniKanren use must call relational entry points directly.

The third risk is optimizing for Pelletier at the expense of Proflog procedure
semantics. This ADR should not change `kernel/prove-program` behavior except as
incidental refactoring covered by existing program/equality regression suites.

## Test Obligations

- Comparative harness tests or probes:
  - every remaining too-slow Pelletier id has a measurement record for
    alphaleanTAP-E, legacy EP, and greenfield, or an explicit reason that one
    prover cannot run that formula.
  - proof-step summaries are collected for at least the problems that close in
    another prover but not greenfield.
- Profile tests:
  - the 18 remaining Pelletier branch formulas classify as equality-free
    first-order, or exceptions are documented.
  - equality-bearing and program-bearing formulas still do not enter the
    equality-free first-order component.
- First-order component tests:
  - existing passing quantified Pelletier problems still close through the new
    generic first-order path.
  - at least the first promoted tranche from the 18 too-slow problems closes.
  - an open equality-free first-order branch remains open under a bounded slice.
  - direct relational `proveo` coverage demonstrates partial proof or formula
    synthesis without the host-side profiler.
- Dispatch tests:
  - `kernel/prove` routes equality-free first-order theorem formulas through the
    first-order component.
  - pure propositional formulas continue to route through the propositional
    component.
  - equality-bearing formulas still use the full kernel.
  - `kernel/prove-program` still uses the program-aware full kernel.
- Regression suites:
  - `lein test-proflog-fast`
  - `lein test-proflog-pelletier-prompt`
  - `lein test-proflog-pelletier`
  - the new comparative Pelletier selector
  - targeted equality/program tests if any shared kernel code changes

## Exit Criteria

- The ADR branch contains a comparative report for alphaleanTAP-E, legacy EP,
  and greenfield on the 18 currently too-slow Pelletier problems.
- The report identifies which generic enhancements were adopted and which were
  rejected or deferred.
- A generic equality-free first-order proof path exists, or the comparative
  evidence explains why a different generic path is required.
- At least one formerly `:ported-too-slow` problem is promoted to
  `:ported-passing` through generic code.
- No Pelletier problem is solved by id-specific dispatch, theorem-specific
  overlays, or compiled proof plans.
- Remaining non-passers, if any, have updated measurements and concrete
  follow-up hypotheses.
- The full Proflog kernel remains readable as the complete equality/program
  rule set, and reverse/partial relational entry points remain available
  without host-side profiling.

## Implementation Progress

The first implementation tranche added `proflog.kernel.first-order`, a small
equality-free first-order relation beside the propositional component. The
public theorem convenience wrapper `kernel/prove` now dispatches:

- pure propositional formulas to `proflog.kernel.propositional`,
- equality-free call-free first-order formulas to
  `proflog.kernel.first-order`,
- equality-bearing formulas to the full kernel,
- program-bearing searches through `kernel/prove-program` and the full kernel.

The first-order component deliberately keeps the full equality, disequality,
gamma candidate, and procedure-call machinery out of its state. It uses the
ordinary alpha, beta, gamma, delta, literal-save, and complementary-closure
rules over current formula, pending branch formulas, saved literals, an
environment, optional fuel, and a proof term.

The comparative slice showed one important boundary: the old greenfield NNF
`once-forall` form is an operational single-use universal for program-call
bodies, but the equality-free theorem component must treat a negated
existential as an ordinary repeatable classical universal. Re-enqueueing
`once-forall` only inside `proflog.kernel.first-order` matched the
alphaleanTAP-E behavior on the first promoted tranche while preserving the full
program kernel's existing single-use procedure-call behavior.

Current promoted tranche:

```clojure
[25 30 31 36 41]
```

These five problems moved from `:ported-too-slow` to `:ported-passing` through
generic equality-free first-order code. They are prompt enough to join
`prompt-passing-ids`.

The current comparative report is tracked in
[`docs/PELLETIER_FIRST_ORDER_COMPARISON.md`](../PELLETIER_FIRST_ORDER_COMPARISON.md).
The report's coverage table is guarded by
`lein test-proflog-pelletier-comparison`.
The remaining too-slow tranche after this slice is:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```
