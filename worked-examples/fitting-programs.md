# Fitting Program Kernel Examples

This page is the minimum non-trivial demonstration suite for the greenfield
Proflog implementation. The examples are implemented in
`src/proflog/fitting_programs.clj` and checked by
`test/proflog/fitting_programs_test.clj`.

The important boundary is operational: each example is first translated from
public AST/source clauses into the compiled Proflog program representation.
After that translation, success, failure, answer synthesis, and unresolved
classification come from the core proof kernel or proof-preserving answer
overlay. The ADR-38 tests redefine the hard-family overlay and the old
constructor-recursive settlement sidecar to throw, so these examples cannot pass
by host-side semantic shortcuts.

## Evaluation Process

The catalog represents each example as a case map:

```clojure
{:id :p2-win-4-succeeds
 :kind :query
 :family :p2
 :program (p2-program)
 :query (ast/pos-lit (app 'win (numeral 4)))
 :expected :succeeds
 :fuel 64}
```

`fitting/evaluate-case` then runs one of two proof-backed paths:

- `:query` cases call `query/query-succeeds`, `query/query-fails`, or
  `query/query-status`, all of which route to `kernel/prove-program`.
- `:list-matrix` cases call `list-kernel-matrix-probe/run-case`, which uses the
  raw kernel/answer state machinery and checks closed target bindings without
  public list materialization.

The focused verification command is:

```text
timeout -k 5s 480s lein test-proflog-fitting-programs
```

Current local result:

```text
Ran 6 tests containing 81 assertions.
0 failures, 0 errors.
```

This suite is intentionally separate from `test-proflog-extended`. It includes
raw list-kernel proof search and is a more expensive ADR gate than routine API
regression tests.

## P1: Even/Odd

Fitting's P1 is represented with one recursive existential `even` clause and
the original forall-based `odd` clause:

```clojure
(ast/clause 'even [x]
  (or (x = zero)
      (exists [y]
        (and (x = s(y))
             (pos odd(y))))))

(ast/clause 'odd [x]
  (forall [y]
    (implies (pos even(y))
             (x != y))))
```

Promoted outcomes:

| Case | Mode | Outcome | Notes |
| --- | --- | --- | --- |
| `even(0)` | forward success | succeeds | direct base proof |
| `odd(s(0))` | forward success | succeeds | exercises the forall-based odd clause |
| `odd(0)` | forward refutation | fails | query failure is proved by closing the positive query tableau |

Shortcoming: deeper P1 cases such as `odd(s(s(s(0))))` remain too expensive for
the default ADR-38 gate. They should be promoted only after a search-control
improvement makes them practical through the same kernel path.

## P2: Nim

Fitting's P2 is encoded with the move relation inlined in the `win` clause:

```clojure
(ast/clause 'win [x]
  (exists [y]
    (and (or (x = s(y))
             (x = s(s(y))))
         (neg win(y)))))
```

Promoted outcomes:

| Case | Mode | Outcome | Notes |
| --- | --- | --- | --- |
| `win(3)` | forward refutation | fails | loser position under the one-or-two-token move rule |
| `win(4)` | forward success | succeeds | winner position with recursive negative-call proof evidence |

`win(4)` is a useful non-trivial kernel proof: it enters a procedure call,
opens existential structure, saves and reuses branch literals, and closes nested
negative calls. Larger Nim positions are possible research targets, but they are
not yet cheap enough for the default fitting catalog.

## Move Warning

Fitting warns that factoring the move test into an auxiliary relation can change
the operational behavior. ADR-38 keeps that visible:

```clojure
(ast/clause 'win [x]
  (exists [y]
    (and (pos move(x, y))
         (neg win(y)))))

(ast/clause 'move [mx my]
  (or (mx = s(my))
      (mx = s(s(my)))))
```

Promoted outcomes:

| Case | Mode | Outcome | Notes |
| --- | --- | --- | --- |
| `move(1, 0)` | forward success | succeeds | local auxiliary relation is decidable |
| `move(0, 1)` | forward refutation | fails | impossible move closes by constructor reasoning |
| `win(1)` in the factored program | status | unresolved | classified as `:invalid-auxiliary-relation-factoring` |

This demonstrates that the evaluator is not just computing the extensional Nim
recurrence on the host. The distinction comes from the procedure-call proof
kernel.

## Finite-Domain Examples

The finite-domain catalog is encoded as ordinary Proflog clauses over constants
`red`, `green`, `blue`, and `yellow`:

```text
color(x) <- x = red or x = green or x = blue
warm(x)  <- x = red
cool(x)  <- x = green or x = blue
```

Promoted outcomes:

| Case | Mode | Outcome | Notes |
| --- | --- | --- | --- |
| `color(red)` | forward success | succeeds | positive proof |
| `color(yellow)` | forward refutation | fails | biconditional false case |
| `warm(blue)` | forward refutation | fails | constructor disequality closes the wrong category |
| `cool(green)` | forward success | succeeds | disjunctive body proof |
| `warm_unique()` | quantified invariant | succeeds | universal uniqueness proof |
| `unknown_total()` | status | unresolved | undefined procedure call remains visible |

These examples show Proflog's biconditional query behavior: false cases are
proved by a tableau for the positive query, not by Prolog-style negation as
failure.

## List Programs: Forward, Answer, And Partial Synthesis

The list examples use the existing append/reverse source program from
`list-kernel-matrix-probe`. The ADR-38 catalog selects rows that exercise all
three required modes:

| Case | Mode | Outcome | Notes |
| --- | --- | --- | --- |
| `append([a,b,c], [a], [a,b,c,a])` | forward | target found | ground proof |
| `append(x, y, [a,b,c])` | answer synthesis | all inverse split targets found | open answer variables |
| `reverse(r, [c,b,a])` | answer synthesis | target `r = [a,b,c]` found | open input |
| `reverse([[[a]],[[b]],[[c]]], r)` | answer synthesis | deep nested output found | open output |
| `reverse([a,b,c,a], cons(a, r))` | partial synthesis | target `r = [c,b,a]` found | constructor-constrained output |

The tests disable `constructor-recursive/settle-record`, so these rows cannot
pass by post-export sidecar settlement. They must close through the raw
kernel/answer-overlay path.

## Group-Verifier Associativity

The GV examples build finite group-axiom programs from a multiplication table.
ADR-39 promotes the associativity rows from bounded frontiers to proof-backed
kernel outcomes. The named hard-family overlay is still disabled in the tests.

Current promoted GV rows:

| Case | Mode | Outcome | Classification |
| --- | --- | --- | --- |
| Z1 full seven-universal associativity | quantified success | succeeds | profiled equality-fragment proof |
| Z2 precomputed associativity | quantified success | succeeds | profiled equality-fragment proof |
| Z2 full seven-universal associativity | quantified success | succeeds | profiled equality-fragment proof |
| non-group precomputed associativity | quantified refutation | fails | profiled equality-fragment proof |
| non-group full seven-universal associativity | quantified refutation | fails | profiled equality-fragment proof |

This is still not a group-specific evaluator. The proof terms contain
`profiled equality-fragment` evidence, and the production equality-fragment
component does not depend on `gv-probe`, `hard-family-overlay`, or transition
fixtures.

## Performance And Correctness Summary

Correctness guardrails:

- promoted true/false outcomes include proof evidence;
- unresolved outcomes carry explicit classifications;
- hard-family overlay use is redefined to throw in tests;
- constructor-recursive sidecar settlement is redefined to throw in list rows;
- source audit checks that `fitting_programs.clj` does not depend on
  `hard-family-overlay`.

Performance profile:

- direct finite-domain and shallow P1/P2 rows are comparatively small;
- `win(4)` and `odd(s(0))` are slower but still practical proof-kernel cases;
- list answer/partial-synthesis rows dominate the focused suite runtime;
- the larger finite-verifier rows are now practical but remain expensive enough
  to live in focused suites rather than routine fast tests;
- deeper P1/P2 rows remain proof-search frontiers.

The suite is therefore a minimum viable non-trivial demonstration, not a claim
that all Fitting-style programs are solved cheaply. It shows forward proof,
refutation, answer synthesis, partial synthesis, and honest unresolved
classification through the greenfield kernel boundary.

## Source To Kernel Descent

The shared descent contract is documented in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). This catalog is
the main non-trivial application of that contract.

Fitting P1 can be written as source:

```prolog
even(x) :- x = zero
        or exists y. (x = s(y) and odd(y)).

odd(x) :- forall y. (even(y) -> x != y).
```

In prefix frontend form:

```clojure
(def p1-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (even 1) (odd 1))))

(def p1-program
  (pf/proflog p1-language
    (|- (even x)
      (or (= x zero)
          (exists [y]
            (and (= x (s y))
                 (odd y)))))

    (|- (odd x)
      (forall [y]
        (implies (even y)
                 (!= x y))))))
```

The backend stores `even/1` and `odd/1` as compiled relation entries. A query
such as `(pf/q (odd (s zero)))` descends to:

```clojure
(pos (app odd (app s (app zero))))
```

`query/query-succeeds` then calls `kernel/prove-program` on the negated query,
with `prog = p1-program`, `fml = (neg (app odd ...))`, `n = 1`, and the current
fuel slice. The proof is non-trivial because the call to `odd/1` opens a
universal body and then calls back into `even/1`.

Fitting P2 can be written with the move test inlined:

```prolog
win(x) :- exists y.
            ((x = s(y) or x = s(s(y))) and not win(y)).
```

The frontend equivalent is:

```clojure
(def p2-program
  (pf/proflog p2-language
    (|- (win x)
      (exists [y]
        (and (or (= x (s y))
                 (= x (s (s y))))
             (not (win y)))))))
```

The warning example deliberately contrasts that with a real auxiliary
procedure:

```prolog
win(x) :- exists y. (move(x, y) and not win(y)).
move(x, y) :- x = s(y) or x = s(s(y)).
```

If `move/2` is compiled with `:-`, the kernel must evaluate it through an
ordinary procedure call. ADR-0010 also supports a different source contract:

```clojure
(:= (move x y)
  (or (= x (s y))
      (= x (s (s y)))))
```

Under `:=`, `move` is inlined before compilation, so the kernel receives the
same formula shape as the inlined P2 program rather than an auxiliary runtime
relation. The catalog keeps both behaviors visible because Fitting's warning is
about that operational boundary.

The list and finite-verifier rows descend the same way but use specialized
kernel entrances after compilation. Ordinary list answer rows now use the
frontend answer surface:

```clojure
(pf/run list-program [x y]
  (append x y (cons a (cons b (cons c null))))
  {:call-depth call-depth
   :fuel fuel
   :max-raw-proof-limit raw-limit})
```

This emits the backend formula:

```clojure
(pos
  (app append
       (var x)
       (var y)
       (app cons (app a)
                 (app cons (app b)
                           (app cons (app c) (app null))))))
```

and exports `[x y]` through `answers/query-answers`. When the catalog names a
raw matrix helper instead, it is deliberately bypassing the default answer API
after this same frontend translation. GV rows enter `query/query-status`, but
`kernel/prove-program` selects the proof-producing equality-fragment profile
from the compiled finite formula shape.
