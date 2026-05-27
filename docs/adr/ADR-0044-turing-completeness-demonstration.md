# ADR-0044: Turing Completeness Demonstration

- Status: completed
- Date: 2026-05-07
- Branch: `adr-0044-turing-completeness`
- AAR: [AAR-0044](../aar/AAR-0044-turing-completeness-demonstration.md)

## Context

Proflog now has enough frontend, kernel, answer, and documentation structure to
move beyond individual examples and demonstrate an expressive lower bound. The
project needs a concrete, reviewable demonstration that Proflog can implement a
known minimal Turing-complete computation model, with execution proceeding
through compiled Proflog formulas and the proof kernel rather than host-side
semantic code.

Two-counter Minsky machines are the right target for this ADR:

- they are a standard minimal Turing-complete model;
- machine configurations are first-order terms;
- counters are Peano terms already used throughout the project;
- finite instruction tables are ordinary Proflog facts;
- the interpreter requires only relation calls, equality, existential variables,
  and recursive transitive closure.

The demonstration cannot prove all possible runs by exhaustive testing. Instead,
it must supply a generic interpreter and document the standard reduction
argument: any two-counter machine can be represented as a finite collection of
instruction facts, and the Proflog `run/2` relation is the reflexive transitive
closure of the transition relation. Since two-counter machines are
Turing-complete, Proflog can simulate Turing-complete computation.

## Decision

Add `proflog.turing-completeness`, which defines:

- a reusable frontend language for two-counter machines;
- a generic Proflog interpreter over instruction facts:
  - `inc0(label, next)`;
  - `inc1(label, next)`;
  - `decjz0(label, decrement-next, zero-next)`;
  - `decjz1(label, decrement-next, zero-next)`;
  - `halt-state(label)`;
  - `step(config, config)`;
  - `run(config, config)`;
  - `run-for(steps, start, final)`;
  - `halt-config(config)`;
  - `halts-in(start, final)`;
  - `halts-in-steps(steps, start, final)`;
- at least two concrete machine programs that reuse the same language and
  interpreter clauses but provide different instruction-table facts.

`run/2` is the unbounded reachability relation used for the expressiveness
argument. `run-for/3` is the object-language bounded execution relation used by
tests and examples when a finite number of machine steps is known. It is still a
Proflog relation over Peano terms, not a host loop.

The primary machine is a transfer loop:

```text
l0: if counter0 = 0 then halt else counter0-- ; goto l1
l1: counter1++ ; goto l0
```

It transfers the value in counter0 into counter1. For example:

```text
cfg(l0, 2, 0) ->* cfg(halt, 0, 2)
```

The second machine is a small incrementer used to prove the interpreter is not
hard-coded to one instruction table.

## Representation

Configurations are first-order terms:

```clojure
(cfg label counter0 counter1)
```

Counters are Peano terms:

```clojure
zero
(s zero)
(s (s zero))
```

The interpreter is written in the ADR-0010 frontend. For example, the increment
case descends schematically as:

```clojure
(|- (step before after)
  (exists [label next c0 c1]
    (and (= before (cfg label c0 c1))
         (inc0 label next)
         (= after (cfg next (s c0) c1)))))
```

No host-side evaluator may inspect a configuration and compute the next state.
Host helpers may build terms, collect proof records, or format examples, but the
machine semantics must be encoded as compiled Proflog clauses.

## Exit Criteria

- A failing test is added before implementation.
- `proflog.turing-completeness` exposes the reusable language, generic
  interpreter clauses, concrete machine programs, and small term helpers.
- Tests prove:
  - individual increment, zero-branch, and decrement-branch transitions;
  - the same interpreter reused with a second instruction table;
  - at least one `pf/run` open-answer query exporting a final halt
    configuration;
  - at least one `pf/run` partial-synthesis query over a machine instruction;
  - source audit evidence that no host-side step evaluator is used.
- The worked examples include a Turing-completeness example with:
  - pseudo-code;
  - frontend source;
  - backend formula descent;
  - query and answer evaluation;
  - correctness/performance notes and shortcomings.
- The tutorial links the TC example from the frontend-to-kernel flow.
- The README links the TC worked example as a non-trivial capability
  demonstration.
- Passing runtimes are recorded in `docs/TEST_RUNTIME_BASELINE.md`.
- AAR-0044 records the result.

## Non-Goals

- This ADR does not add a textual parser for machine syntax.
- This ADR does not prove that every possible machine run terminates.
- This ADR does not add arithmetic fast paths or host-side counter operations.
- This ADR does not require default open-answer search to enumerate arbitrary
  unbounded machine histories.

## Risks

Recursive `run/2` can diverge or become expensive in reverse modes. The tests
must stay bounded and explicit about fuel, call-depth, and proof limits. The
worked example should state that the Turing-completeness claim is an expressive
power claim, not a claim that arbitrary machine reachability is decidable or
cheap.
