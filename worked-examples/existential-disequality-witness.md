# Existential Disequality Witness

This example records the semantic split covered by
`test/proflog/legacy_impurity_test.clj` and promoted by ADR-0018.

## Program

Use a language with exactly the declared constants `a` and `b`, and relation
`p/1`:

```prolog
p(x) :- exists y. x != y
```

Over this language, the intended ground meaning is finite and direct:

- `p(a)` should succeed, witnessed by `y = b`.
- `p(b)` should succeed, witnessed by `y = a`.
- `p(x)` should export exactly the object-language answers `a` and `b`.
- No public answer should contain an internal delta parameter `(par ...)`.

## Legacy Execution

The legacy probe asks for an open answer:

```clojure
(legacy/proveo
  ['pos ['app 'p answer]]
  '()
  '()
  '()
  program
  proof
  4)
```

The first result binds `answer` to a term whose head is `par`.
`legacy_impurity_test.clj` intentionally asserts only the shape of that result,
because the printed nominal identity is not stable:

```clojure
(legacy-par-term? (first results)) => true
```

Operationally, legacy reaches that result as follows:

1. The procedure-call guard runs `l-ground-termo` on the call argument.
2. Legacy `l-ground-termo` uses `project`, so an unbound logic variable is
   inspected host-side and admitted because it does not yet contain `(par ...)`.
3. The clause call maps formal `x` to the still-open answer variable.
4. The existential `y` introduces a fresh internal delta parameter `(par p)`.
5. The disequality branch can then close after the open answer variable is
   unified with that same delta parameter.

That is a useful negative reference, not a valid result. The answer is outside
the declared language `L`; it is an implementation artifact from `L^par`.
Accepting it would let a proof-local witness escape as a user-level program
answer.

## Greenfield Execution

The same program is compiled through the greenfield frontend and language
boundary:

```clojure
(def witness-language
  (pf/language
    (constants a b)
    (relations (p 1))))

(def witness-program
  (pf/proflog witness-language
    (|- (p x)
      (exists [y]
        (!= x y)))))
```

Before ADR-0018, greenfield behavior measured on 2026-04-26 was:

```text
query-status p(a), timeout 1000 ms  => :unresolved
query-status p(b), timeout 1000 ms  => :unresolved
query-succeeds p(a), fuel 8         => ()
query-fails p(a), fuel 8            => ()
query-answers p(answer), fuel 8     => []
query-ground-answers p(answer),
  max-depth 0, fuel 8               => []
```

Greenfield is correct on the boundary that legacy violates:

- `kernel/l-ground-termo` walks terms structurally instead of using `project`.
- unresolved `(par ...)` terms are rejected at the procedure-call boundary.
- user answer variables are kept distinct from proof-local delta parameters.
- the public answer exporters refuse to emit `(par ...)`.

At that point, greenfield was still incomplete for this program. It avoided
legacy's unsound internal-parameter answer, but it did not yet discover the
available object-language witnesses `a` and `b`.

ADR-0018 changes the pure proof path by allowing the single-use universal
created from the negated existential body to instantiate with declared nullary
object-language terms. After that change:

```text
query-status p(a), timeout 1000 ms  => :succeeds
query-status p(b), timeout 1000 ms  => :succeeds
query-succeeds p(a), fuel 8         => ((neg-call (once-univ (free-close))))
query-fails p(a), fuel 8            => ()
query-ground-answers p(answer),
  max-depth 0, fuel 8               => [(app a) (app b)]
query-parity-answers p(answer),
  max-term-size 0, fuel 8           => [(app a) (app b)]
(pf/run witness-program [answer]
  (p answer)
  {:fuel 8})                        => []
```

The last line is deliberate. The generic symbolic answer overlay still avoids
turning all open queries into concrete Herbrand enumeration, because existing
programs rely on that API preserving symbolic frontiers and residual calls. The
accurate finite evaluation of this program is now available through direct
ground query status and the explicit bounded materialization APIs.

## Target Behavior

ADR-0018 treats this as a gatekeeping example. The completed implementation
satisfies all of the following:

- `p(a)` returns `:succeeds`.
- `p(b)` returns `:succeeds`.
- `p(a)` and `p(b)` do not also return failure proofs.
- explicit bounded answer materialization returns exactly `a` and `b`.
- no query, answer record, residual, or public proof witness exports `(par ...)`
  as a user-level answer.

The practical point is that rejecting legacy's `(par ...)` answer is necessary
but not sufficient. The implementation must replace that artifact with the
real object-language witnesses that make the program true.

## Source To Kernel Descent

The source program is small enough to show all layers:

```prolog
p(x) :- exists y. x != y.
```

Prefix frontend:

```clojure
(def witness-language
  (pf/language
    (constants a b)
    (relations (p 1))))

(def witness-program
  (pf/proflog witness-language
    (|- (p x)
      (exists [y]
        (!= x y)))))
```

The compiled clause has one relation parameter and one existential nom:

```clojure
{:relation p
 :params [x]
 :body
 (exists
   (tie y
     (neq (var x) (var y))))
 :negated-body
 (once-forall
   (tie y
     (eq (var x) (var y))))}
```

For the ground query `p(a)`, `query/query-succeeds` validates:

```clojure
(pos (app p (app a)))
```

and then calls:

```clojure
(kernel/prove-program witness-program
                      (neg (app p (app a)))
                      1
                      fuel)
```

The key ADR-0018 correction is in the kernel's instantiation boundary. The
single-use universal created by the negated existential may instantiate with
declared object-language terms such as `a` and `b`, but public answer export
must not leak internal `(par ...)` witnesses. That is why this example reports
both ground query status and explicit bounded materialization behavior.
