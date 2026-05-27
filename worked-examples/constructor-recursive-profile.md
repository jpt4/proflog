# Constructor-Recursive Profile Examples

These examples document
`test/proflog/kernel/constructor_recursive_profile_test.clj`. The promoted
profile is an explicit answer profile for constructor-recursive guarded programs.
It returns answer records with integrated proof evidence:

```text
(profiled constructor-recursive ...)
```

Run the focused examples with:

```text
timeout -k 5s 180s lein test proflog.kernel.constructor-recursive-profile-test
```

Current result:

```text
Ran 4 tests containing 21 assertions.
0 failures, 0 errors.
elapsed 11.32 s
```

## Peano Peel

The non-list control program is:

```clojure
peel(x, y) :-
  x = y
  or exists predecessor.
       x = s(predecessor)
       and peel(predecessor, y).
```

The query:

```clojure
peel(3, 0)
```

closes with one promoted proof record. This demonstrates that the profile is not
list-specific and does not depend on relation names such as `append` or
`reverse`.

## Peano Plus

The addition program is the legacy PA recursion direction:

```clojure
plus(x, zero, x).
plus(x, s(y1), s(z1)) :- plus(x, y1, z1).
```

The same `profile/query-records` surface handles forward, reverse, and partial
synthesis:

| Query | Mode | Result |
|---|---|---|
| `plus(3, 4, z)` | forward result synthesis | `z = 7` |
| `plus(x, 3, 5)` | reverse first addend | `x = 2` |
| `plus(x, x, 4)` | partial synthesis | `x = 2` |
| `plus(x, y, 3)` | bounded enumeration | `[0,3] [1,2] [2,1] [3,0]` |
| `plus(x, x, 3)` | no-answer control | no records |

Each positive row contains `profiled`, `constructor-recursive`, and
`structural-residual-continuation` proof evidence. The tests also reject the old
`constructor-recursive-call` diagnostic proof tag in promoted records.

## List Append And Reverse

The list examples reuse the guarded list program from
`proflog.list-kernel-matrix-probe`:

```clojure
append(null, ys, ys).
append(cons(head, tail), ys, cons(head, rest)) :-
  append(tail, ys, rest).

reverse(null, null).
reverse(cons(head, tail), out) :-
  reverse(tail, rrp)
  and append(rrp, cons(head, null), out).
```

The promoted profile returns:

| Query | Result |
|---|---|
| `append([a], [b], z)` | `z = [a,b]` |
| `reverse([a,b], r)` | `r = [b,a]` |

These rows are generic guarded-IR evaluations. The source audit for the promoted
profile rejects imports or calls to the old diagnostic sidecar, `project`, and
known list-family fast-path names.

## Shortcomings

The profile is explicit. It is not yet the default `query-answers` behavior for
every recursive program, because the public answer API still preserves symbolic
frontiers in cases where eager constructor-recursive enumeration would be too
expensive or would change answer-surface expectations.

## Source To Kernel Descent

The profile consumes compiled Proflog structure, not relation-specific host
procedures. The shared layers are described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

For Peano addition, the source program is:

```prolog
plus(x, zero, x).
plus(x, s(y1), s(z1)) :- plus(x, y1, z1).
```

The prefix frontend shape is:

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

At the backend layer, that becomes a guarded recursive compiled body over the
relation parameters `[x y z]`. The constructor-recursive profile receives the
compiled guarded IR plus a query such as:

```clojure
(pos (app plus (var x) (app s (app s (app s (app zero))))
                (app s (app s (app s (app s (app s (app zero))))))))
```

for `plus(x, 3, 5)`.

The profile's public parameters are the same semantic ingredients as the
answer API: compiled program, query formula, exported variables, proof fuel,
and answer limit. Its proof records must contain `profiled`,
`constructor-recursive`, and `structural-residual-continuation` evidence. That
is the genericity claim: the profile reasons over constructor-recursive clause
shape after compilation, and the tests reject the old diagnostic sidecar and
known list-family shortcuts.

At the default answer surface, the same query would be written with `pf/run`:

```clojure
(pf/run plus-program [x]
  (plus x (s (s (s zero))) (s (s (s (s (s zero))))))
  {:fuel 24
   :limit 2})
```

This worked example deliberately routes the translated query into the promoted
constructor-recursive profile instead of the default `answers/query-answers`
surface. That is when the lower-level builder is useful:

```clojure
(let [{:keys [query answer-vars]}
      (pf/answer-query [x]
        (plus x (s (s (s zero))) (s (s (s (s (s zero)))))))]
  (profile/query-records plus-program query answer-vars {:fuel 24 :limit 2}))
```

The frontend contribution is still generic: it supplies the compiled program,
the backend query formula, and the exported variable vector. The profiled layer
then decides how to continue the residual recursive frontier.
