# ADR-37 FD Fuel Synthesis Impact

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`

## Question

Assess whether `kernel-support/step-fuelo` finite-domain constraints currently
block reverse or partial-mode synthesis, before judging the performance impact
of replacing them with ADR-36 relational arithmetic.

The concern is specific: `step-fuelo` encodes finite fuel as host integers inside
hardcoded finite-domain intervals. If those intervals become part of the logical
relation, they may constrain reverse or partial synthesis in ways that pure
relational arithmetic would not.

## Current Production Surface

The public query and answer APIs pass fuel as either `nil` or a host integer.
The user-facing reverse/partial answer variables are object-language terms, not
the fuel variable itself. In that ordinary surface, finite-domain fuel is an
operational bound rather than an exported answer-domain constraint.

That means the current fixed-fuel synthesis behavior is not presently blocked by
FD fuel alone. Focused synthesis-mode tests pass:

```text
timeout -k 5s 120s lein test :only proflog.synthesis-modes-test/partial-mode-step-query-produces-ground-predecessor-successors

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.

timeout -k 5s 120s lein test :only proflog.synthesis-modes-test/reverse-mode-step-query-honors-additional-constraints

Ran 1 tests containing 1 assertions.
0 failures, 0 errors.

timeout -k 5s 120s lein test :only proflog.synthesis-modes-test/open-step-query-exports-symbolic-families

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.

timeout -k 5s 120s lein test :only proflog.synthesis-modes-test/composed-partial-mode-query-traverses-multiple-calls

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
```

The full `proflog.synthesis-modes-test` namespace exceeded a `240s` wrapper
during this pass under concurrent worker load, so the evidence above is focused
on the fuel-relevant rows rather than a full namespace gate.

## Direct Fuel Relation Probe

Direct `step-fuelo` reverse/partial synthesis works for simple one-step and
two-step fuel relations:

```clojure
{:step-pairs
 ([nil nil] [1 0] [2 1] [3 2] [4 3] [5 4]),
 :finite-step-pairs
 ([1 0] [2 1] [3 2] [4 3] [5 4] [6 5]),
 :step-chain-to-zero
 (2)}
```

This shows the FD relation can synthesize:

- the predecessor fuel from a known successor;
- the successor fuel from a known predecessor;
- multi-step finite fuel for small chains.

## Open Kernel Fuel Probe

The direct full-kernel relation can also enumerate proof/fuel pairs when fuel is
open:

```clojure
{:concrete-kernel
 {:fuel-0 (),
  :fuel-1 ((profiled propositional (conj (savefml (close))))),
  :fuel-2 ((profiled propositional (conj (savefml (close))))),
  :fuel-unbounded ((profiled propositional (conj (savefml (close)))))},
 :open-kernel-fuel-proofs
 ([nil (profiled propositional (conj (savefml (close))))]
  [1 (profiled propositional (conj (savefml (close))))]
 [2 (profiled propositional (conj (savefml (close))))]
 [3 (profiled propositional (conj (savefml (close))))]
 [4 (profiled propositional (conj (savefml (close))))]
  [5 (profiled propositional (conj (savefml (close))))])}
```

This corrects an earlier stale assumption: for the direct full-kernel relation,
the simple closing conjunction succeeds at finite fuel `1`; the
`kernel/prove` convenience wrapper may route comparable ground formulas through
profiled sublayers, so wrapper behavior should not be mixed with the full
kernel relation when assessing fuel relationality.

## Risk That Remains

The FD concern is not refuted; it is narrowed.

What is not currently failing:

- ordinary reverse/partial answer synthesis with fixed public fuel;
- direct one-step and small-chain fuel synthesis;
- direct full-kernel open-fuel enumeration on a simple proof.

What remains risky:

- any future API or diagnostic that exports finite fuel as an answer;
- any proof-depth synthesis that expects an unbounded natural-number relation
  rather than a host integer constrained to `1..Long/MAX_VALUE`;
- any adapter that claims to be fully relational but converts open bit-list fuel
  back into bounded host integers too early;
- large recursive searches where FD constraint propagation/reification becomes
  part of the search cost or answer shape.

## Decision

Proceed with relational arithmetic replacement as an opt-in adapter/profile, not
because current fixed-fuel reverse/partial synthesis is demonstrably broken, but
because ADR-37 wants to remove a hardcoded host numeric domain from the logical
fuel relation before relying on open/reverse fuel synthesis as a public
capability.

The first semantic gate for the replacement should be:

- preserve the current `nil` and ground integer public API;
- keep current fixed-fuel synthesis tests green;
- add explicit open/reverse fuel tests that compare FD and bit-list behavior;
- avoid projecting bit-list fuel back to bounded host integers inside the
  relation.
