# List Programs

This file covers `test/proflog/list_programs_test.clj`.

List constructors:

```clojure
null
cons(head, tail)
```

Current relations:

```clojure
member(x, xs)
append(xs, ys, zs)
reverse(r1, r2)
```

## `append([], [a], [a])`

Query:

```clojure
append(null, cons(a, null), cons(a, null))
```

Proof term:

```clojure
(neg-call (conj (split (refl-close) (refl-close))))
```

The base branch fires directly:

- `xs = null`
- `zs = ys`

Both equalities become reflexive.

## `append([], [a], z)`

Open query:

```clojure
append(null, cons(a, null), z)
```

Exported answer record:

```clojure
{:bindings [[z (app cons (app a) (app null))]]
 :residuals []
 :proofs [(conj (split (refl-close) (neq-close (eq-bind))))]}
```

So the exporter returns the expected concrete binding:

```clojure
z = [a]
```

with no residual obligations.

## `reverse([], [])`

Query:

```clojure
reverse(null, null)
```

Proof term:

```clojure
(neg-call (conj (split (refl-close) (refl-close))))
```

Again the base clause closes immediately.

## `member(a, [a])`

Query:

```clojure
member(a, cons(a, null))
```

Proof term:

```clojure
(neg-call
 (once-univ
  (once-univ
   (split
    (neq-close (decompose (args (eq-bind) (args (eq-bind) ()))))
    (conj (refl-close))))))
```

The first disequality branch forces the list head to `a`, and the head-case
disjunct then closes reflexively.

## `member(a, [b, a])`

Query:

```clojure
member(a, cons(b, cons(a, null)))
```

This is the first non-trivial recursive `member` success. The proof descends
through the tail after the head-case disjunct fails on `b`, then closes on the
recursive call over `[a]`.

## `member(a, [])`

Query:

```clojure
member(a, null)
```

Proof term:

```clojure
(pos-call (witness (witness (conj (free-close))))))
```

This is the empty-list failure case:

1. the positive call opens the clause body,
2. the two existential witnesses stand for the would-be `head` and `tail`,
3. the body immediately requires `null = cons(head, tail)`,
4. that closes by constructor clash.

So the relation does not need any recursive search to reject membership in the
empty list.

Recorded successful runtime for the final committed iteration:

```text
565.030374 ms
```

## `append([a], [b], [a, b])`

Query:

```clojure
append(cons(a, null), cons(b, null), cons(a, cons(b, null)))
```

Proof term:

```clojure
(neg-call
 (conj
  (split
   (neq-store
    (once-univ
     (once-univ
      (once-univ
       (split
        (neq-close (decompose (args (eq-bind) (args (eq-bind) ()))))
        (split
         (neq-close (decompose (args (decompose ()) (args (eq-bind) ()))))
         (neg-call (conj (split (refl-close) (refl-close))))))))))
   ...)))
```

Operationally:

1. the base-clause disequalities are stored because this is not the empty-list case,
2. the recursive clause instantiates `head = a`, `tail = []`, and `rest = [b]`,
3. the recursive call reduces to the base append case `append([], [b], [b])`.

## Wrong Result Example

The current suite also records:

```clojure
append([a], [b], [a]) => fails
member(c, [a, b])     => fails
```

So the recovered recursive list behavior now includes both positive and
negative one-step cases.

## `reverse([a], [a])`

This singleton reverse example now succeeds as well. It unfolds one recursive
step and then discharges the trailing append through the base case
`append([], [a], [a])`.

## `append([a, b], [c], [a, b, c])`

Query:

```clojure
append(cons(a, cons(b, null)),
       cons(c, null),
       cons(a, cons(b, cons(c, null))))
```

This is the next fully ground recursive append case beyond the earlier
one-step regression. Operationally, the proof:

1. rejects the base branch because the left input is non-empty,
2. binds `head = a` and reduces to
   `append([b], [c], [b, c])`,
3. repeats the same recursive clause once more,
4. closes on the base append case
   `append([], [c], [c])`.

The important point is semantic, not aesthetic: the greenfield kernel can now
carry the recursive append relation through two full constructor layers and
still close the proof with no residual obligations.

Recorded successful runtime for the final committed iteration:

```text
154219.489533 ms
```

## `append([a], [b, c], z)`

Open query:

```clojure
append(cons(a, null),
       cons(b, cons(c, null)),
       z)
```

Current exported answer record:

```clojure
{:bindings [[z (app cons (app a)
                    (app cons (app b)
                              (app cons (app c) (app null))))]]
 :residuals
 [(neq (app cons (app a)
             (app cons (app b) (app cons (app c) (app null))))
       (app cons (app b) (app cons (app c) (app null))))
  (neq (app cons (app a) (app null))
       (app null))]}
```

So the exporter reconstructs the expected concrete result:

```clojure
z = [a, b, c]
```

As with the nested answer cases, the constructive binding is already ground but
the exporter still leaves behind the disequalities that ruled out the wrong
append branches.

Recorded successful runtime for the final committed iteration:

```text
68873.149268 ms
```

## `reverse([a, b], [b, a])`

Query:

```clojure
reverse(cons(a, cons(b, null)),
        cons(b, cons(a, null)))
```

This is the first non-trivial ground reverse case beyond the singleton list.
Its proof path is:

1. decompose the input as `head = a`, `tail = [b]`,
2. recurse on `reverse([b], [b])`,
3. reduce the trailing append obligation to
   `append([b], [a], [b, a])`,
4. close that append through the recursive append chain.

This case matters because it confirms that recursive `reverse` is executable in
the greenfield stack, not just its base and singleton slices.

Recorded successful runtime for the final committed iteration:

```text
276769.773115 ms
```

## `append([[a]], [[b]], z)`

Open query:

```clojure
append(cons(cons(a, null), null),
       cons(cons(b, null), null),
       z)
```

Current exported answer record:

```clojure
{:bindings [[z (app cons
                    (app cons (app a) (app null))
                    (app cons
                     (app cons (app b) (app null))
                     (app null)))]]
 :residuals
 [(neq (app cons (app cons (app a) (app null))
             (app cons (app cons (app b) (app null)) (app null)))
       (app cons (app cons (app b) (app null)) (app null)))
  (neq (app cons (app cons (app a) (app null)) (app null))
       (app null))]}
```

So the answer exporter does recover the intended nested list:

```clojure
z = [[a], [b]]
```

but it does not yet fully normalize away the supporting disequalities introduced
while ruling out the wrong append branches. That is still a valid greenfield
answer record: the constructive binding is concrete, and the residuals make the
remaining proof obligations explicit instead of silently dropping them.

Recorded successful runtime for the final committed iteration:

```text
41655.620203 ms
```

## `append([[a, b]], z, [[a, b], [c]])`

Open query:

```clojure
append(cons(cons(a, cons(b, null)), null),
       z,
       cons(cons(a, cons(b, null)),
            cons(cons(c, null), null)))
```

Current exported answer record:

```clojure
{:bindings [[z (app cons (app cons (app c) (app null)) (app null))]]
 :residuals
 [(neq (app cons (app cons (app a) (app cons (app b) (app null)))
             (app cons (app cons (app c) (app null)) (app null)))
       (app cons (app cons (app c) (app null)) (app null)))
  (neq (app cons (app cons (app a) (app cons (app b) (app null)))
             (app null))
       (app null))]}
```

So the exporter reconstructs the intended nested suffix:

```clojure
z = [[c]]
```

The residual shape is the same as the earlier nested forward case: the answer
is concrete, but the current exporter still retains the disequalities used to
exclude the wrong append branches.

Recorded successful runtime for the final committed iteration:

```text
26539.838541 ms
```

## Current Boundary

The greenfield list program itself already contains `member`, recursive
`append`, and recursive `reverse`. The basic `list-programs-test` namespace is
still deliberately narrower than the full answer-synthesis surface: it focuses
on direct proof/refutation, one concrete forward answer, and representative
nested answer recovery. Broader inverse enumeration now lives in
`proflog.answers-test` and the long-timeout matrix probes rather than in this
small worked-example namespace.

Current public answer coverage now includes the full four-way inverse split
family for:

```clojure
append(x, y, [a, b, c])
```

The focused regression
`query-answers-prefer-the-first-concrete-inverse-append-split-over-symbolic-frontiers`
records all four closed splits with empty residuals and passed during ADR-0043
cleanup in `10.91 s`.

The raw-kernel matrix also reaches the short nested inverse family:

```text
append-inverse-nested: 3 / 3 splits, raw 8, 13283.4934 ms
```

This boundary is intentional for the committed baseline: the current slice now
covers base cases, recovered recursive `member`, one- and two-step ground
`member` including the empty-list miss, `append` including a concrete
three-element synthesized result, singleton and two-element ground `reverse`,
the first nested forward and nested suffix append answers, public inverse
append enumeration, and long-timeout raw matrix reachability. The remaining
constraint is operational cost and default-suite placement, not absence of the
semantic targets.

## Source To Kernel Descent

The list examples are ordinary recursive Proflog programs after the descent
described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

The source append relation is:

```prolog
append([], ys, ys).
append(cons(head, tail), ys, cons(head, rest)) :-
  append(tail, ys, rest).
```

The current frontend-compatible form keeps relation heads variable-only and
puts constructor patterns in the body:

```clojure
(pf/proflog list-language
  (|- (append xs ys zs)
      (and (= xs null)
           (= zs ys)))

  (|- (append xs ys zs)
      (exists [head tail rest]
        (and (= xs (cons head tail))
             (= zs (cons head rest))
             (append tail ys rest)))))
```

The backend compiler combines those clauses into one disjunctive compiled body
for `append/3`. A forward query such as `append([a], [b], [a,b])` descends to a
ground `pos` formula and uses `query/query-succeeds` or `query/query-fails`.

An answer query such as:

```prolog
append(x, y, [a, b, c])?
```

is evaluated with the frontend answer form:

```clojure
(pf/run append-program [x y]
  (append x y (cons a (cons b (cons c null))))
  opts)
```

Schematically, it descends to:

```clojure
(pos
  (app append
       (var x)
       (var y)
       (app cons (app a)
                 (app cons (app b)
                           (app cons (app c) (app null))))))
```

and `pf/run` exports `[x y]` through `answers/query-answers`. The parameters
that control the examples are the exported variable list, `fuel`, `call-depth`,
and raw proof limit. The residuals and long-timeout matrix rows record where
recursive proof search remains expensive after this source has already become
compiled kernel formulae.
