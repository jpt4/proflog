# Boxed Zero

This worked example records the greenfield proof trace for the query
`boxed-zero(2)`.

## Program

The test program defines:

```clojure
boxed-zero(x) :- exists y.
                   x = y
                   and forall z.
                         (y != z or z = zero)
```

Intuitively, this says that `x` must be equal to some `y` such that every
`z` is either different from `y` or else equal to `zero`. That forces
`y = zero`, so the relation characterizes exactly the singleton `{zero}`.

## Query

```clojure
boxed-zero(2)
```

In the project language, `2` is the Peano term:

```clojure
(app s (app s (app zero)))
```

## Proof Term

The greenfield prover found this failure proof for `boxed-zero(2)`:

```clojure
(pos-call
 (witness
  (conj
   (eq-step
    (par-bind)
    (univ
     (split
      (neq-close (eq-bind))
      (free-close)))))))
```

## Trace

1. `pos-call`
   The prover opens the clause body for `boxed-zero(2)` to show that the query
   is false.

2. `witness`
   The clause body begins with `exists y`, so the prover introduces an
   existential witness parameter for `y`.

3. `conj`
   The body is a conjunction:
   - `x = y`
   - `forall z. (y != z or z = zero)`

4. `eq-step (par-bind)`
   From `x = y` and `x = 2`, the existential witness `y` is bound to `2`.

5. `univ`
   The prover instantiates the universal formula with a fresh proof variable
   `z`.

6. `split`
   The instantiated body is the disjunction:
   - `y != z`
   - `z = zero`

   To refute that disjunction, both branches must close.

7. Left branch: `neq-close (eq-bind)`
   The prover chooses `z = y = 2`, so `y != z` becomes false.

8. Right branch: `free-close`
   With the same `z = 2`, the equation `z = zero` becomes `2 = 0`, which
   closes by constructor clash.

## Conclusion

Choosing `z = 2` defeats the universal condition:

- `y != z` fails because `z = y`
- `z = zero` fails because `2 != 0`

So `boxed-zero(2)` fails, exactly as intended for a relation that denotes only
`zero`.

## Open Query

The open query:

```clojure
boxed-zero(x)
```

returns one concrete answer:

```clojure
{:bindings [[x (app zero)]]
 :residuals []}
```

So the only exported binding is:

```clojure
x = 0
```

with no residual obligations.

## Answer Proof Term

The exported answer record also carries proof terms in `:proofs`. One of the
proofs for `boxed-zero(x)` is:

```clojure
(once-univ
 (split
  (neq-close (eq-bind))
  (witness
   (conj
    (eq-step (eq-bind) (neq-close (eq-bind)))))))
```

The answer exporter keeps all proofs that lead to the same bindings and
residuals, so the actual record may contain several proof terms with this same
shape.

## Open-Query Trace

This proof has a slightly different shape from the `boxed-zero(2)` failure
trace because it is proving that there exists a successful answer for free
`x`, not refuting a fixed ground input.

1. `once-univ`
   The open answer path reaches the negation of the existential clause body,
   which the greenfield kernel represents operationally as a single-use
   universal.

2. `split`
   The instantiated universal body produces the same disjunction structure:
   - `y != z`
   - `z = zero`

3. Left branch: `neq-close (eq-bind)`
   The prover binds the fresh proof variable so the disequality branch closes.

4. Right branch: `witness (conj (eq-step (eq-bind) (neq-close (eq-bind))))`
   This branch witnesses the existential and then uses equality to force the
   exported answer variable `x` to `zero`, after which the remaining
   disequality closes.

## Open-Query Conclusion

The important point is that the open query does not just succeed abstractly. It
returns an answer record with:

- a concrete binding `x = 0`
- no residual constraints
- explicit proof terms explaining why that answer is valid

## Source To Kernel Descent

`boxed-zero/1` follows the source descent in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md), but it exercises
both existential and universal binders in one clause:

```prolog
boxed-zero(x) :- exists y.
                   (x = y
                    and forall z. (y != z or z = zero)).
```

The prefix frontend form is:

```clojure
(def boxed-zero-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (boxed-zero 1))))

(def boxed-zero-program
  (pf/proflog boxed-zero-language
    (|- (boxed-zero x)
      (exists [y]
        (and (= x y)
             (forall [z]
               (or (!= y z)
                   (= z zero))))))))
```

The compiled relation has one parameter and two nested quantified noms:

```clojure
{:relation boxed-zero
 :params [x]
 :body
 (exists
   (tie y
     (and
       (eq (var x) (var y))
       (forall
         (tie z
           (or
             (neq (var y) (var z))
             (eq (var z) (app zero))))))))
 :negated-body
 (once-forall
   (tie y
     (or
       (neq (var x) (var y))
       (exists
         (tie z
           (and
             (eq (var y) (var z))
             (neq (var z) (app zero))))))))}
```

The ground failure `boxed-zero(2)` uses:

```clojure
(query/query-fails boxed-zero-program
                   (pf/q (boxed-zero (s (s zero))))
                   1
                   fuel)
```

so the kernel closes the positive query formula. The ordinary open answer case
uses `pf/run`:

```clojure
(pf/run boxed-zero-program [x]
  (boxed-zero x)
  {:fuel fuel
   :proof-limit 1})
```

Inside that call, the frontend emits:

```clojure
(pos (app boxed-zero (var x)))
```

and exports `[x]` through `answers/query-answers`. The returned binding
`x = zero` is therefore an answer-overlay export from kernel proof states, not
a host-side singleton computation.
