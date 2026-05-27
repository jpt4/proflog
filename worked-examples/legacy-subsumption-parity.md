# Legacy Subsumption Parity Examples

These examples document the focused ADR-40 suite in
`test/proflog/legacy_subsumption_test.clj`. They are intended as tutorial
material for non-trivial Proflog use cases that now have greenfield coverage
matching or exceeding the old legacy rows.

Run them with:

```text
timeout -k 5s 900s lein test-proflog-legacy-subsumption
```

Current result:

```text
Ran 3 tests containing 63 assertions.
0 failures, 0 errors.
elapsed 50.37 s
```

## Group Verifier Rows

The group examples use finite multiplication tables translated into ordinary
closed Proflog formulas. The identity law has the shape:

```clojure
(forall [x]
  (or (not-in-domain x)
      (and (op e x x)
           (op x e x))))
```

Closure uses a nested existential witness:

```clojure
(forall [x y]
  (or (not-in-domain x)
      (not-in-domain y)
      (exists [z]
        (and (op x y z)
             (in-domain z)))))
```

Inverses require a domain member `y` that multiplies with `x` to the identity
on both sides:

```clojure
(forall [x]
  (or (not-in-domain x)
      (exists [y]
        (and (in-domain y)
             (op x y e)
             (op y x e)))))
```

The ADR-40 test first reuses the exact `Z2` legacy probe scenarios, then
rebuilds the same laws over the larger cyclic group `Z3`. The successful proofs
must contain both `profiled` and `equality-fragment`, proving that they close
through the generic finite equality-fragment profile rather than a group-name
shortcut.

| Row | Outcome | Runtime |
|---|---|---:|
| `z2-identity` | succeeds | `430.475 ms` |
| `z2-closure` | succeeds | `52.508 ms` |
| `z2-inverses` | succeeds | `32.085 ms` |
| `z3 identity` | succeeds | `410.048 ms` |
| `z3 closure` | succeeds | `986.148 ms` |
| `z3 inverses` | succeeds | `405.171 ms` |

## Finite-Domain Rows

The finite-domain examples encode category facts and invariants as ordinary
Proflog clauses over constructors. The legacy disjointness row asserts that
every value is either not warm-red or not one of the cool colors:

```clojure
(forall [x]
  (or (x != red)
      (and (x != green)
           (x != blue))))
```

The extended row checks a larger two-by-two disjointness condition over
`red`, `orange`, `green`, `blue`, and `yellow`.

Totality is intentionally different. The test asks whether every value is one
of a finite list of named constants. In open Proflog semantics, that query is
not closed just because the examples mention a finite-looking set; it remains
unresolved.

| Row | Outcome | Runtime |
|---|---|---:|
| `warm/cool disjoint` | succeeds | `1.745 ms`; ADR-42 status probe `526 ms` |
| `extended finite-domain disjointness` | succeeds | `11.403 ms` |
| `finite totality is undefined` | unresolved | `3497.516 ms` |
| `extended finite totality is undefined` | unresolved | `3320.326 ms` |

ADR-42 corrected the earlier `:inconsistent` status for `warm/cool disjoint`.
The issue was not supervaluation semantics; it was an equality-fragment proof
scoping bug that allowed different disjunctive branches to bind the same
universal proof witness differently. The profile now requires branch-local
requirements to merge into one compatible witness assignment before a split proof
is accepted, so the bounded public status reports `:succeeds`.

## Peano Plus Rows

The Peano program matches the legacy PA definition: recursion is on the second
argument.

```clojure
plus(x, zero, x).
plus(x, s(y1), s(z1)) :- plus(x, y1, z1).
```

The direct forward rows run through the ordinary proof kernel:

| Row | Outcome | Runtime |
|---|---|---:|
| `PA10 forward 3 + 4 = 7` | succeeds | `25198.864 ms` |
| `PA11 / extended forward 4 + 3 = 7` | succeeds | `3680.729 ms` |

These timings explain the earlier probe asymmetry. A first ADR-40 fixture draft
accidentally recursed on the first argument, making `4 + 3 = 7` look worse than
`3 + 4 = 7`. After restoring the legacy second-argument recursion, `3 + 4 = 7`
is correctly the slower row because its second argument has four successors.

The open answer rows now run through the ADR-41 promoted
`profiled constructor-recursive` answer profile over compiled guarded clauses.
This still uses the translated Proflog formula representation and the ADR-35
structural residual continuation engine, but it is an explicit profile rather
than the default public answer exporter.

| Row | Answer | Runtime |
|---|---|---:|
| `PA12 ? + 3 = 5` | `2` | `15.283 ms` |
| extended `? + 3 = 6` | `3` | `3.959 ms` |
| `PA13/PA15 3 + ? = 5` | `2` | `4.693 ms` |
| extended `3 + ? = 6` | `3` | `5.009 ms` |
| `PA14 3 + 4 = ?` | `7` | `3.794 ms` |
| extended `4 + 3 = ?` | `7` | `4.135 ms` |
| `PA16 x + x = 4` | `2` | `4.950 ms` |
| extended `x + x = 6` | `3` | `4.312 ms` |
| `PA17 x + x = 3` | no answer | `5.789 ms` |
| `PA18 / extended x + x = 5` | no answer | `5.662 ms` |
| `PA19 all pairs summing to 3` | `[0,3] [1,2] [2,1] [3,0]` | `6.732 ms` |
| extended all pairs summing to 4 | `[0,4] [1,3] [2,2] [3,1] [4,0]` | `5.945 ms` |
| `PA20 fixed addend 2` | includes `[0,2] [1,3] [2,4] [3,5]` | `27.725 ms` |
| extended fixed addend 3 | includes `[0,3] [1,4] [2,5] [3,6]` | `25.594 ms` |

Shortcoming: PA12 through PA20 are covered as explicit
`profiled constructor-recursive` answer rows, not as a claim that raw default
`query-answers` can cheaply enumerate every Peano stream. The suite deliberately
records that operational boundary while still validating each accepted answer
against the compiled Proflog clause.

## Source To Kernel Descent

The parity rows compare legacy behavior to greenfield behavior after the source
program has been translated to compiled Proflog formulas. The common descent is
summarized in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

For a representative Peano row:

```prolog
plus(x, zero, x).
plus(x, s(y1), s(z1)) :- plus(x, y1, z1).
```

the current frontend-compatible form keeps variables in the head and moves
patterns into the body:

```clojure
(pf/proflog peano-language
  (|- (plus x y z)
      (and (= y zero)
           (= z x)))

  (|- (plus x y z)
      (exists [y1 z1]
        (and (= y (s y1))
             (= z (s z1))
             (plus x y1 z1)))))
```

Forward rows such as `plus(4, 3, 7)` use `query/query-succeeds`, which descends
to `kernel/prove-program` on the negated ground query. Open rows such as
`plus(x, 3, 5)` use the ADR-41 profiled constructor-recursive answer path with
exported variable `[x]`.

At the ordinary frontend answer surface, the row has the same visible shape as a
miniKanren-style query:

```clojure
(pf/run peano-program [x]
  (plus x (s (s (s zero))) (s (s (s (s (s zero))))))
  opts)
```

The parity suite uses the lower-level `pf/answer-query` only because it must
feed the translated query into `constructor-profile/query-records` rather than
the default `answers/query-answers` evaluator:

```clojure
(let [{:keys [query answer-vars]}
      (pf/answer-query [x]
        (plus x (s (s (s zero))) (s (s (s (s (s zero)))))))]
  (constructor-profile/query-records peano-program query answer-vars opts))
```

The timing table is therefore not comparing host arithmetic to Proflog
arithmetic. It compares proof and answer behavior over the compiled guarded
clause. The recorded asymmetry fix also belongs at this layer: restoring
second-argument recursion changed the compiled recursive body, so the proof
cost now follows the intended Peano descent argument.
