# Turing Completeness Example

ADR-0044 demonstrates that Proflog can encode a known minimal
Turing-complete model: two-counter Minsky machines. ADR-0045 adds a
trace-shaped proof path for deeper known finite runs. The implementation lives
in `src/proflog/turing_completeness.clj`, and the relevant opt-in regression
suites are `test/proflog/turing_completeness_test.clj` and
`test/proflog/minsky_trace_performance_test.clj`.

This is an expressive-power demonstration. It does not claim that arbitrary
machine reachability is decidable or cheap. The tests prove small finite runs
through the compiled Proflog program and record the current runtime boundaries.

## Machine Model

A two-counter machine has labels, two natural-number counters, and instructions
of these forms:

```text
inc0(label, next)          ; counter0++, goto next
inc1(label, next)          ; counter1++, goto next
decjz0(label, dec, zero)   ; if counter0 = 0 goto zero, else counter0-- and goto dec
decjz1(label, dec, zero)   ; if counter1 = 0 goto zero, else counter1-- and goto dec
halt-state(label)
```

Machine configurations are first-order terms:

```text
cfg(label, counter0, counter1)
```

Counters are Peano terms:

```text
zero
s(zero)
s(s(zero))
```

Two-counter machines are Turing-complete. Proflog therefore has the same
expressive lower bound when it can represent arbitrary finite instruction
tables and a generic transition relation over those tables.

Citation trail: the counter-machine family used here is the standard Minsky
machine / register-machine model associated with Marvin Minsky's universality
work. The historical sources to cite are Minsky's 1961 Annals of Mathematics
paper, "Recursive Unsolvability of Post's Problem of 'Tag' and Other Topics in
the Theory of Turing Machines," and Minsky's 1967 book, *Computation: Finite and
Infinite Machines*. The 1961 paper establishes universality through extremely
restricted machine models; the 1967 book is the canonical reference for the
program-machine / counter-machine presentation. Bibliographic records:
[Cambridge Core JSL review of the 1961 paper](https://www.cambridge.org/core/journals/journal-of-symbolic-logic/article/marvin-l-minsky-recursive-unsolvability-of-posts-problem-of-tag-and-other-topics-in-the-theory-of-turing-machines-annals-of-mathematics-second-series-vol-74-1961-pp-437455/E592A179A70AA2CE2721126EFDBD099A)
and
[Open Library record for the 1967 book](https://openlibrary.org/books/OL5535641M).

## Prolog-Style Pseudo-Code

The generic interpreter is written as ordinary Proflog clauses. A representative
increment case is:

```prolog
step(before, after) :-
  exists label next c0 c1.
    before = cfg(label, c0, c1)
    and inc0(label, next)
    and after = cfg(next, s(c0), c1).
```

The recursive reachability relation is the reflexive transitive closure of
`step/2`:

```prolog
run(start, final) :- start = final.

run(start, final) :-
  exists middle.
    step(start, middle)
    and run(middle, final).
```

The bounded variant used by tests takes a Peano step counter:

```prolog
run-for(zero, start, final) :-
  start = final.

run-for(s(rest), start, final) :-
  exists middle.
    step(start, middle)
    and run-for(rest, middle, final).
```

The transfer-machine instruction table is:

```text
l0: if counter0 = 0 then halt else counter0-- ; goto l1
l1: counter1++ ; goto l0
```

In Proflog pseudo-code:

```prolog
decjz0(label, dec, zero-next) :-
  label = l0 and dec = l1 and zero-next = halt-label.

inc1(label, next) :-
  label = l1 and next = l0.

halt-state(label) :-
  label = halt-label.
```

Starting from `cfg(l0, 1, 0)`, the intended trace is:

```text
cfg(l0, 1, 0)
=> cfg(l1, 0, 0)
=> cfg(l0, 0, 1)
=> cfg(halt-label, 0, 1)
```

## Frontend Definition

The executable frontend uses reusable language declarations and prefix clause
operators. The language is shared by the transfer machine and a second
incrementer machine:

```clojure
(def counter-machine-language
  (pf/language
    (constants zero
               l0 l1 halt-label
               i0 ihalt)
    (functions (s 1)
               (cfg 3))
    (relations (inc0 2)
               (inc1 2)
               (decjz0 3)
               (decjz1 3)
               (halt-state 1)
               (step 2)
               (run 2)
               (run-for 3)
               (halt-config 1)
               (halts-in 2)
               (halts-in-steps 3))))
```

The small Clojure helpers below only construct object-language terms for tests
and examples. They are not machine evaluators:

```clojure
(defn app
  [sym & args]
  (apply ast/app-term sym args))

(defn numeral
  [n]
  (if (zero? n)
    (app 'zero)
    (app 's (numeral (dec n)))))

(defn config
  [label counter0 counter1]
  (app 'cfg
       (app label)
       (numeral counter0)
       (numeral counter1)))
```

The generic interpreter source is a quoted list of frontend clauses. It is
defined once as `interpreter-source`:

```clojure
(def ^:private interpreter-source
  '((|- (step before after)
      (exists [label next c0 c1]
        (and (= before (cfg label c0 c1))
             (inc0 label next)
             (= after (cfg next (s c0) c1)))))

    (|- (step before after)
      (exists [label next c0 c1]
        (and (= before (cfg label c0 c1))
             (inc1 label next)
             (= after (cfg next c0 (s c1))))))

    (|- (step before after)
      (exists [label dec zero-next c1]
        (and (= before (cfg label zero c1))
             (decjz0 label dec zero-next)
             (= after (cfg zero-next zero c1)))))

    (|- (step before after)
      (exists [label dec zero-next pred c1]
        (and (= before (cfg label (s pred) c1))
             (decjz0 label dec zero-next)
             (= after (cfg dec pred c1)))))

    (|- (step before after)
      (exists [label dec zero-next c0]
        (and (= before (cfg label c0 zero))
             (decjz1 label dec zero-next)
             (= after (cfg zero-next c0 zero)))))

    (|- (step before after)
      (exists [label dec zero-next c0 pred]
        (and (= before (cfg label c0 (s pred)))
             (decjz1 label dec zero-next)
             (= after (cfg dec c0 pred)))))

    (|- (run start final)
      (= start final))

    (|- (run start final)
      (exists [middle]
        (and (step start middle)
             (run middle final))))

    (|- (run-for steps start final)
      (and (= steps zero)
           (= start final)))

    (|- (run-for steps start final)
      (exists [rest middle]
        (and (= steps (s rest))
             (step start middle)
             (run-for rest middle final))))

    (|- (halt-config config)
      (exists [label c0 c1]
        (and (= config (cfg label c0 c1))
             (halt-state label))))

    (|- (halts-in start final)
      (and (halt-config final)
           (run start final)))

    (|- (halts-in-steps steps start final)
      (and (halt-config final)
           (run-for steps start final)))))
```

`machine-program` appends those generic interpreter clauses to concrete
instruction-table clauses:

```clojure
(defmacro machine-program
  [& instruction-forms]
  `(pf/proflog counter-machine-language
     ~@interpreter-source
     ~@instruction-forms))
```

This is ordinary Clojure macro syntax. The backtick starts a syntax-quoted
template. `~@interpreter-source` splices every clause in the quoted
`interpreter-source` list into the `pf/proflog` form. `~@instruction-forms`
splices every clause supplied to `(machine-program ...)` after the interpreter.
The macro expansion is therefore equivalent to writing:

```clojure
(pf/proflog counter-machine-language
  ;; every generic `step`, `run`, `run-for`, and halt clause
  ;; ...
  ;; then every concrete instruction-table clause
  ;; ...)
```

The generated program is still a compiled Proflog program. The macro only
assembles source clauses before translation.

The transfer machine is then just instruction-table clauses:

```clojure
(def transfer-machine
  (machine-program
    (|- (decjz0 label dec zero-next)
      (and (= label l0)
           (= dec l1)
           (= zero-next halt-label)))

    (|- (inc1 label next)
      (and (= label l1)
           (= next l0)))

    (|- (halt-state label)
      (= label halt-label))))
```

The second machine proves the interpreter is generic over instruction tables:

```clojure
(def incrementer-machine
  (machine-program
    (|- (inc0 label next)
      (and (= label i0)
           (= next ihalt)))

    (|- (halt-state label)
      (= label ihalt))))
```

This shape matters: the interpreter is generic, and the concrete machine is not
hard-coded into a host evaluator.

## Backend Descent

The increment case above descends to one compiled relation clause for `step/2`.
Schematically, the source clause:

```clojure
(|- (step before after)
  (exists [label next c0 c1]
    (and (= before (cfg label c0 c1))
         (inc0 label next)
         (= after (cfg next (s c0) c1)))))
```

becomes a backend formula body:

```clojure
(exists
  (tie label
    (exists
      (tie next
        (exists
          (tie c0
            (exists
              (tie c1
                (and
                  (eq (var before) (app cfg (var label) (var c0) (var c1)))
                  (and
                    (pos (app inc0 (var label) (var next)))
                    (eq (var after)
                        (app cfg
                             (var next)
                             (app s (var c0))
                             (var c1)))))))))))))
```

The compiler stores that body with its normalized negation in the compiled
program. When the kernel sees `step(before, after)`, the Procedure Call Rule
looks up the compiled `step/2` alternatives, binds the formal parameters to the
actual call arguments, and proves the selected body.

## Evaluation Process

The forward transition tests call the kernel-backed query surface directly:

```clojure
(query/query-succeeds
  (tc/transfer-program)
  (ast/pos-lit
    (ast/app-term 'step
                  (tc/config 'l0 2 1)
                  (tc/config 'l1 1 1)))
  1
  32)
```

This proves the decrement branch of `decjz0`: from `cfg(l0, 2, 1)` the machine
can step to `cfg(l1, 1, 1)`.

The second-machine test reuses the same interpreter with a different
instruction table:

```clojure
(query/query-succeeds
  (tc/incrementer-program)
  (ast/pos-lit
    (ast/app-term 'halts-in-steps
                  (tc/numeral 1)
                  (tc/config 'i0 1 2)
                  (tc/config 'ihalt 2 2)))
  1
  48)
```

This is a bounded recursive run through `run-for/3`, not a host loop.

The answer-mode transfer example uses the public frontend evaluator:

```clojure
(pf/run (tc/transfer-program) [final]
  (exists [middle0 middle1]
    (and (step (cfg l0 (s zero) zero) middle0)
         (step middle0 middle1)
         (step middle1 final)
         (halt-config final)))
  {:fuel 96
   :call-depth 5
   :proof-limit 8
   :max-raw-proof-limit 32})
```

It exports:

```clojure
{:bindings [[final (cfg halt-label zero (s zero))]]
 :residuals []}
```

The partial-synthesis check asks Proflog to fill one argument of an instruction
relation:

```clojure
(pf/run (tc/transfer-program) [label]
  (inc1 label l0)
  {:fuel 48
   :call-depth 1
   :proof-limit 4
   :max-raw-proof-limit 16})
```

It exports:

```clojure
{:bindings [[label l1]]
 :residuals []}
```

ADR-0045 adds a trace-shaped formula helper for deeper known runs:

```clojure
(tc/trace-formula
  [(tc/config 'l0 2 0)
   (tc/config 'l1 1 0)
   (tc/config 'l0 1 1)
   (tc/config 'l1 0 1)
   (tc/config 'l0 0 2)
   (tc/config 'halt-label 0 2)]
  {:halt? true})
```

This helper constructs a formula equivalent to:

```clojure
(and (step cfg0 cfg1)
     (and (step cfg1 cfg2)
          (and (step cfg2 cfg3)
               (and (step cfg3 cfg4)
                    (and (step cfg4 cfg5)
                         (halt-config cfg5))))))
```

It does not execute the machine on the host. Every edge is still a call to the
compiled `step/2` relation, and `halt-config/1` is still proved from the
compiled halt clauses.

## Test Results

Run the aggregate TC suite explicitly:

```text
lein test-proflog-turing-completeness
```

That selector now includes this Minsky example, the ADR-0045 Minsky trace
performance namespace, and the ADR-0046 SKI combinatory-logic namespace. To run
only the Minsky trace-performance addition:

```text
lein test-proflog-minsky-trace-performance
```

Current ADR-0044 promoted checks:

| Test | Mode | Outcome | Focused runtime |
|---|---|---|---:|
| `two-counter-machine-step-cases-close-through-the-kernel` | forward | three `step/2` branch proofs close with procedure-call evidence | `31.46 s` |
| `same-interpreter-runs-a-second-instruction-table` | bounded forward recursion | `halts-in-steps(1, cfg(i0,1,2), cfg(ihalt,2,2))` succeeds | `26.51 s` |
| `frontend-run-exports-the-transfer-machine-final-config` | answer | exports `cfg(halt-label, 0, 1)` with no residuals | `73.66 s` |
| `frontend-run-can-partially-synthesize-an-instruction` | partial synthesis | exports `label = l1` for `inc1(label, l0)` | `21.68 s` |
| `turing-completeness-namespace-does-not-contain-a-host-step-evaluator` | source audit | no host query/answer evaluator or host `step`/`run` functions in the namespace | `9.85 s` |
| `long-probe-identifiers-are-stable` | diagnostic surface | confirms long-probe CLI IDs without running long probes | `8.87 s` |

Current ADR-0045 promoted checks:

| Test | Mode | Outcome | Focused runtime |
|---|---|---|---:|
| `five-step-transfer-closes-through-a-guided-step-trace` | forward trace formula | proves the five transition edges from `cfg(l0,2,0)` to `cfg(halt-label,0,2)` plus `halt-config` | `58.89 s` |
| `trace-helper-does-not-contain-a-host-machine-evaluator` | source audit | verifies the helper is formula construction only and no host `step`/`run` evaluator was added | `15.83 s` |

The focused ADR-0045 namespace passed with:

```text
Ran 2 tests containing 4 assertions.
0 failures, 0 errors.
elapsed_seconds 55.02
```

Before ADR-0045, the comparable direct recursive five-step formulation
`halts-in-steps(5, cfg(l0,2,0), cfg(halt-label,0,2))` did not return inside a
`1800 s` wrapper. The trace-shaped query therefore converts a non-viable
regression candidate into a passing kernel proof, while retaining the original
recursive formulation as diagnostic evidence for future search-control work.

The original ADR-0044 namespace passed with:

```text
Ran 6 tests containing 13 assertions.
0 failures, 0 errors.
elapsed_seconds 68.64
```

The aggregate TC selector passed with:

```text
Ran 14 tests containing 29 assertions.
0 failures, 0 errors.
elapsed_seconds 328.17
```

## Long Diagnostic Probes

The promoted TC tests are deliberately not the hardest recursive or reverse
queries. Those harder forms are tracked by
`proflog.turing-completeness-long-probe` and the alias:

```text
lein probe-proflog-turing-completeness <probe-id>
```

The stable probe identifiers are:

```text
recursive-transfer-3-steps
recursive-transfer-5-steps
direct-ground-three-step-trace
open-predecessor-step
```

The three-step recursive transfer eventually succeeds, but it is not an
appropriate regression test:

```text
timeout -k 5s 900s /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness recursive-transfer-3-steps

{:starting-probe "recursive-transfer-3-steps"}
{:result :succeeds,
 :proof-count 1,
 :first-proof-tag neg-call,
 :probe "recursive-transfer-3-steps",
 :elapsed-ms 773835.238895}
elapsed_seconds 783.72
```

The open predecessor query also eventually returns, but only after a long
answer-mode search. The first answer is the expected concrete predecessor; the
remaining answers are symbolic residual alternatives:

```text
timeout -k 30s 7200s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness open-predecessor-step

{:starting-probe "open-predecessor-step"}
{:result :answers,
 :answer-count 4,
 :answers
 [{:bindings [[before cfg(l1, s(zero), s(zero))]],
   :residuals [],
   :proof-count 3}
  {:bindings [[before cfg(_0, zero, s(s(zero)))]],
   :residuals [(neg inc0(_0, l0))],
   :proof-count 2}
  {:bindings [[before cfg(_0, s(zero), s(s(s(zero))))]],
   :residuals [(neg decjz1(_0, l0, _1))],
   :proof-count 10}
  {:bindings [[before cfg(_0, s(s(zero)), s(s(zero)))]],
   :residuals [(neg decjz0(_0, l0, _1))],
   :proof-count 1}],
 :probe "open-predecessor-step",
 :elapsed-ms 637275.435493}
elapsed_seconds 645.66
```

The direct ground three-step trace did not return before a controlled stop at
about thirty minutes:

```text
timeout -k 30s 7200s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness direct-ground-three-step-trace

{:starting-probe "direct-ground-three-step-trace"}
;; no proof result before controlled stop at about 30 minutes
```

The five-step recursive transfer did not return before a thirty-minute wrapper:

```text
timeout -k 30s 1800s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness recursive-transfer-5-steps

{:starting-probe "recursive-transfer-5-steps"}
;; process exited with timeout status 124 after the 1800s wrapper
```

These results answer the viability question directly. Some nontrivial TC
witnesses are viable through the current kernel, including recursive bounded
execution and reverse answer search, but the scaling is poor and highly
sensitive to query formulation. They are evidence for future proof-search
performance work, not candidates for the normal TC regression suite.

## Correctness And Shortcomings

Correctness guardrails:

- transition, run, and halt semantics are compiled Proflog clauses;
- concrete machines provide instruction facts only;
- tests inspect proof terms for procedure-call evidence;
- source audit rejects a hidden host evaluator in the TC namespace;
- `pf/run` answer rows require empty residuals for the promoted answers.

Operational shortcomings:

- the direct three-step transfer answer is slow even when the trace shape is
  supplied explicitly;
- a direct open predecessor query over `step/2` timed out inside a 180s wrapper
  but eventually returned in `645.66 s`;
- `halts-in-steps(3, cfg(l0,1,0), cfg(halt-label,0,1))` timed out inside a
  180s wrapper but eventually closed in `783.72 s`;
- `halts-in-steps(5, cfg(l0,2,0), cfg(halt-label,0,2))` did not return inside a
  30-minute wrapper;
- a direct ground three-step trace did not return before a controlled stop at
  about thirty minutes;
- unbounded `run/2` is present for the expressiveness argument, but it should
  not be treated as a practical default enumerator for arbitrary machine
  histories.

The current result is therefore a minimum viable but genuine demonstration:
Proflog represents a Turing-complete machine model at the kernel source level,
and finite computations are evaluated by the proof kernel after frontend
translation.
