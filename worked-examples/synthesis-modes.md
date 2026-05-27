# Synthesis Modes

This file covers `test/proflog/synthesis_modes_test.clj`.

The namespace exercises three families:

- `step` / `jump`
- `down`
- structured `plus` / `append`

## `step(x, 1)`

Open query:

```clojure
step(x, 1)
```

Current exported answers are the two concrete predecessors of `1`:

```clojure
x = 2
x = 3
```

The second answer carries a residual disequality showing that the two-step
branch stays distinct from the one-step branch.

## `step(3, y)` With An Extra Constraint

Query:

```clojure
step(3, y) and y != 2
```

Current answer:

```clojure
y = 1
```

with a residual proof that `3 != 2`.

## Open `step(x, y)`

Open query:

```clojure
step(x, y)
```

Current exported symbolic families are:

```clojure
x = s(y)
x = s(s(y))
```

So the answer API preserves the clause structure directly.

## `jump(x, 0)`

Open query:

```clojure
jump(x, 0)
```

Current concrete ground values recovered from the exported records are:

```clojure
{2 3 4}
```

The answer records include two distinct symbolic routes to `3`, so the raw
records are richer than the final ground-value set used by the test.

## `down`

Current reverse-mode example:

```clojure
down(2, y) => y in [2 1]
```

Current partial-mode example:

```clojure
down(x, 1) => x in [1 2]
```

These two tests show the same recursive relation from opposite query
directions.

## `plus(x, 1, 1)`

Open query:

```clojure
plus(x, 1, 1)
```

Current answer families include:

```clojure
{:bindings [[x 0]]
 :residuals []}

{:bindings [[x s(a_2)]]
 :residuals [x != 0, not plus(a_2, 1, 0)]}
```

So the exporter preserves both:

- the direct zero witness,
- a recursive symbolic family that still carries a deferred `plus` obligation.

## Open `plus(x, y, z)`

Open query:

```clojure
plus(x, y, z)
```

Current exported families are:

```clojure
x = 0 and z = y
x = s(a_4) and y = s(a_3) and z = s(a_3)
  with residual not plus(a_4, s(a_3), a_3)
```

That is the current greenfield answer-mode summary of the base and recursive
clause families.

## Open `append(x, y, z)`

Open query:

```clojure
append(x, y, z)
```

Current exported families include:

```clojure
x = [] and z = y
x = cons(a_3, a_5), y = cons(a_3, a_4), z = cons(a_3, a_4)
  with residual not append(a_5, cons(a_3, a_4), a_4)
```

So the answer exporter currently exposes:

- the base alias family,
- one recursive cons family with a deferred recursive call.

## Why The Residuals Matter

These structured `plus` and `append` examples are not incomplete by accident.
They are the intended partial-answer contract for open recursive queries in the
current greenfield exporter:

- concrete base families come back fully discharged,
- recursive families come back with explicit residual negated calls,
- and the residuals show exactly what deeper work remains instead of hiding it
  behind a flattened or overcommitted answer.

## Source To Kernel Descent

The answer examples use the same compiled-program path described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md), but their public
entry point is the answer overlay rather than `query-status`.

A representative source relation is Peano addition:

```prolog
plus(x, y, z) :- x = zero and z = y.
plus(x, y, z) :- exists x1 z1.
                   x = s(x1)
                   and z = s(z1)
                   and plus(x1, y, z1).
```

The prefix frontend can express that as multiple `|-` clauses, which the
backend compiler combines into one disjunctive compiled body:

```clojure
(pf/proflog peano-language
  (|- (plus x y z)
      (and (= x zero)
           (= z y)))

  (|- (plus x y z)
      (exists [x1 z1]
        (and (= x (s x1))
             (= z (s z1))
             (plus x1 y z1)))))
```

The source-level open query:

```prolog
plus(x, y, z)?
```

is evaluated with the frontend answer form:

```clojure
(pf/run plus-program [x y z]
  (plus x y z)
  opts)
```

Schematically, the query formula is:

```clojure
(pos (app plus (var x) (var y) (var z)))
```

Internally, the answer path uses the same query formula and exported variables:

```clojure
(ast/nom x y z
  (answers/query-answers
    plus-program
    (pos (app plus (var x) (var y) (var z)))
    [x y z]
    opts))
```

The important parameters are the exported variables `[x y z]`, the recursive
`call-depth`, the proof `fuel`, and the raw proof limit. Residuals such as
`not plus(a_4, s(a_3), a_3)` are compiled program calls that the admitted
answer slice did not fully discharge. They are part of the answer record, not
post-hoc prose.
