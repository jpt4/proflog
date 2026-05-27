# Frontend To Kernel Descent

This page gives the common reading pattern for the worked examples. The
quickstart shows the same path for `p(x) = a` and `zero-only/1`; this file makes
the path reusable for the larger examples.

The point of the descent is to keep three questions separate:

- What did the user write as Proflog source?
- What backend formula and compiled clause did that source become?
- Which kernel or answer-overlay entry point evaluated that formula?

## Source Layer

Hand-written Proflog should remain close to Fitting's clause notation:

```prolog
p(x) :- x = a.

only-zero(x) := forall y. (x != y or y = zero).
zero-only(x) :- only-zero(x).
```

The two clause operators have different meanings:

- `:-` introduces a real Proflog relation. Calls to that relation are evaluated
  by the Procedure Call Rule.
- `:=` introduces a frontend-only definition. The frontend inlines it before
  the backend compiler sees the program.

That distinction is what lets examples keep Fitting-style readability without
turning every helper name into a runtime procedure call.

## Prefix Frontend

The implemented ADR-0010 surface is Clojure-readable prefix syntax:

```clojure
(require '[proflog.frontend :as pf]
         '[proflog.query :as query])

(def p-language
  (pf/language
    (constants a b)
    (relations (p 1))))

(def p-program
  (pf/proflog p-language
    (|- (p x)
        (= x a))))

(query/query-status p-program (pf/q (p a)))
;; => :succeeds
```

The program remains visible inside a thin wrapper. `pf/language` builds a
reusable language value. `pf/proflog` compiles relation clauses against that
language. `pf/q` turns a visible closed query into the same backend formula
shape the kernel already accepts. `pf/run` binds visible answer variables and
evaluates open answer queries through `proflog.answers`; `pf/answer-query`
exposes the same translated query formula plus answer-var vector for diagnostic
or alternate answer paths.

Definitions are inlined at this layer:

```clojure
(def peano-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (zero-only 1))))

(def zero-only-program
  (pf/proflog peano-language
    (:= (only-zero x)
      (forall [y]
        (or (!= x y)
            (= y zero))))

    (|- (zero-only x)
        (only-zero x))))
```

There is no runtime `only-zero/1` clause in the compiled program. The body of
`zero-only/1` receives the quantified formula directly.

## Backend AST Layer

The frontend emits the public backend constructors from `proflog.ast` and
`proflog.language`. The simple `p/1` program is equivalent to:

```clojure
(require '[proflog.ast :as ast]
         '[proflog.language :as language])

(def p-lang
  (language/language
    {:constants ['a 'b]
     :relations {'p 1}}))

(def p-program
  (ast/nom x
    (language/compile-program
      p-lang
      [(ast/clause
         'p
         [x]
         (ast/eq-lit (ast/var-term x)
                     (ast/app-term 'a)))])))
```

The corresponding query:

```clojure
(pf/q (p a))
```

emits:

```clojure
(ast/pos-lit (ast/app-term 'p (ast/app-term 'a)))
```

In schematic tagged form:

```clojure
(pos (app p (app a)))
```

For a quantified body such as `zero-only/1`, the frontend-inlined backend body
is schematically:

```clojure
(forall
  (tie y
    (or
      (neq (var x) (var y))
      (eq (var y) (app zero)))))
```

Here `x` is the relation parameter and `y` is the local nominal binder created
for the universal.

## Compiled Program Layer

`language/compile-program` stores normalized relation metadata for procedure
calls. A compiled relation entry has the shape:

```clojure
{:relation p
 :params [x]
 :body (eq (var x) (app a))
 :negated-body (neq (var x) (app a))}
```

The parameters are object-language arguments. When the kernel sees `p(a)`, it
opens a subsidiary tableau for the clause body under the binding `x = a`.

For `zero-only/1`, the compiled entry is schematically:

```clojure
{:relation zero-only
 :params [x]
 :body
 (forall
   (tie y
     (or
       (neq (var x) (var y))
       (eq (var y) (app zero)))))
 :negated-body
 (exists
   (tie y
     (and
       (eq (var x) (var y))
       (neq (var y) (app zero)))))}
```

The negated body is normalized once at compile time, so negative procedure calls
do not require host-side source rewriting.

## Query And Kernel Layer

The public query API is a pair of semidecision probes:

```clojure
(query/query-succeeds program query n fuel)
(query/query-fails program query n fuel)
```

`query/query-succeeds` validates the query against the language and asks the
kernel to close the negated query. `query/query-fails` asks the kernel to close
the query itself.

Schematic calls for `p(a)`:

```clojure
;; Success probe.
(kernel/prove-program
  p-program
  (neg (app p (app a)))
  1
  fuel)

;; Failure probe.
(kernel/prove-program
  p-program
  (pos (app p (app a)))
  1
  fuel)
```

`kernel/prove-program` has four parameters:

- `prog`: the compiled program, including the language and compiled clauses.
- `fml`: the formula whose tableau should close.
- `n`: the number of proof terms requested.
- `fuel`: finite proof-search fuel, or `nil` for the unbounded branch used by
  selected direct probes.

The status helper interleaves those two semidecision paths and reports
`:succeeds`, `:fails`, `:unresolved`, or `:inconsistent` when both directions
produce proofs within the admitted slice.

## Answer Layer

Open-answer examples use the same compiled program and query formula, but they
also identify exported answer variables. The normal frontend form is:

```clojure
(pf/run program [x]
  (relation x)
  {:fuel fuel
   :call-depth call-depth
   :max-raw-proof-limit limit})
```

That form is only an ergonomic wrapper. Its descent is:

```clojure
(ast/nom x
  (answers/query-answers
    program
    (pos (app relation (var x)))
    [x]
    {:fuel fuel
     :call-depth call-depth
     :max-raw-proof-limit limit}))
```

The parameters in that call are:

- `program`: the compiled `pf/proflog` result;
- query formula: the backend `pos` formula emitted from the source query;
- `[x]`: the exported answer-variable noms whose bindings appear in records;
- `fuel`, `call-depth`, and raw proof limit: operational bounds for the answer
  overlay and recursive procedure-call descent.

When a worked example needs to show the exact backend formula, the lower-level
builder is still available:

```clojure
(pf/answer-query [x]
  (relation x))
;; => {:query (pos (app relation (var x)))
;;     :answer-vars [x]}
```

The answer path routes through `proflog.answer-overlay`, which reuses the proof
core while adding answer-variable export, residual obligations, and recursive
call-depth control. It does not make `query-answers` a host evaluator for the
source program. When specialty materializers or profiled paths are used, the
worked example names that boundary explicitly.

The main answer parameters are:

- exported variables: the source variables whose bindings should appear in the
  answer record;
- `fuel`: kernel proof-search fuel for each admitted branch slice;
- `call-depth`: how far recursive procedure calls below the top-level query may
  unfold;
- raw proof limit: how many raw proof states may be collected before exporting
  records.

## Direct Formula Layer

Some examples are theorem-proving or kernel-only tests rather than Proflog
program queries. They call:

```clojure
(kernel/prove formula n fuel)
```

Those examples skip `language/compile-program` because there is no procedure
program. Pelletier problems, equality/disequality closure tests, and Herbrand
oracle checks live at this layer.

## Profiled Kernel Components

Some worked examples mention profiled components such as the propositional,
first-order, equality-fragment, or constructor-recursive profiles. These are
kernel-level components selected from formula or guarded-IR shape. They are not
source-specific evaluators:

- the program has already been compiled to Proflog formulas;
- proof terms record the profile boundary, for example `profiled
  equality-fragment`;
- tests audit that promoted profiles do not dispatch on fixture names such as
  group-verifier, transition-system, or list-family labels.

When a profile is mandatory for a result, the worked example should say so. When
an example uses only the ordinary full kernel, it should say that too.

## Reading Checklist

Each worked example should identify as much of the following path as applies:

- source: the Fitting-style relation or formula being demonstrated;
- frontend: the prefix form, or the reason the example is backend-only;
- backend: the `ast`/compiled-clause shape or direct formula;
- kernel entry: `kernel/prove`, `kernel/prove-program`, query helpers, or
  answer-overlay helpers;
- parameters: relation arguments, quantified noms, exported answer variables,
  `n`, `fuel`, timeouts, call depth, and raw proof limits;
- outcome: proof status, answer bindings, residuals, performance, and any
  explicit shortcoming.
