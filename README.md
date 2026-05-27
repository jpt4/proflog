# Proflog

Experiments into the implementation of Melvin Fitting's Proflog, a
tableau-based logic programming language.

This repository is the greenfield implementation track for Proflog in
Clojure/core.logic.

The existing `cljtap.*` namespaces and tests are reference material and
experimental prior art. They are useful for pressure-testing ideas, but they are
not the new implementation authority. The greenfield implementation must
justify each convergent design independently against Fitting, alphaleanTAP,
miniKanren/core.logic, and the local research reports.

## Mission

See [MISSION.md](MISSION.md).

## Quickstart

Prerequisite: install Leiningen with a working JDK.

Run the fast greenfield regression suite:

```text
lein test-proflog-fast
```

Start a REPL:

```text
lein repl
```

Proflog programs should first be readable as Fitting-style logic programs. The
hand-written notation uses Prolog-like clauses, with one added distinction:
`:=` marks a definitional helper that the frontend may inline, while `:-` marks
a real Fitting-style procedure-call relation. The ADR-0010 frontend
is currently a Clojure macro surface using prefix clause operators. A later
textual parser can use the same translation contract. In prefix form,
`(:= head body)` is a definitional helper and `(|- head body)` is a real
relation clause.
Language declarations remain reusable frontend values, so multiple programs can
compile against the same language.

Start with a minimal relation: `p(x)` succeeds exactly when `x = a`.

```prolog
p(x) :- x = a.
```

The implemented frontend keeps that program visible inside a thin macro
wrapper:

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

(query/query-status p-program (pf/q (p b)))
;; => :fails
```

That frontend form descends to the source clause shape accepted by the current
language layer:

```clojure
(require '[proflog.ast :as ast]
         '[proflog.language :as language]
         '[proflog.query :as query])

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

(query/query-status
  p-program
  (ast/pos-lit (ast/app-term 'p (ast/app-term 'a))))
;; => :succeeds

(query/query-status
  p-program
  (ast/pos-lit (ast/app-term 'p (ast/app-term 'b))))
;; => :fails
```

Schematic tagged AST for the `p/1` clause body:

```clojure
(eq (var x) (app a))
```

Schematically, the compiled program stores that body as the formula the
Procedure Call Rule will use when the kernel sees a saved `p(...)` literal:

```clojure
{:relation p
 :params [x]
 :body (eq (var x) (app a))
 :negated-body (neq (var x) (app a))}
```

Here `x` is the compiled relation parameter for `p/1`. At a procedure call such
as `p(a)` or `p(b)`, the kernel binds that parameter to the actual query
argument inside the subsidiary tableau opened for the clause body.

The query wrapper is similarly thin. The surface query:

```clojure
(pf/q (p a))
```

descends to a formula:

```clojure
(pos (app p (app a)))
```

`query/query-status` then probes the kernel in both semidecision directions.
The kernel call has four parameters:

- `prog`: the compiled program containing `:language`, `:clause-list`, and the
  normalized clause bodies.
- `fml`: the formula the tableau kernel must close.
- `n`: the maximum number of proof terms to return.
- `fuel`: the current finite proof-search slice, chosen by the query wrapper's
  bounded iterative-deepening loop.

For `p(a)`, the two schematic kernel calls are:

```clojure
;; Success probe: close a tableau for the negated query.
(kernel/prove-program
  p-program
  (neg (app p (app a)))
  1
  fuel)

;; Failure probe: close a tableau for the query itself.
(kernel/prove-program
  p-program
  (pos (app p (app a)))
  1
  fuel)
```

For `p(b)`, the query formula is:

```clojure
(pos (app p (app b)))
```

and the kernel probes are:

```clojure
(kernel/prove-program
  p-program
  (neg (app p (app b)))
  1
  fuel)

(kernel/prove-program
  p-program
  (pos (app p (app b)))
  1
  fuel)
```

So the frontend wrapper does not evaluate the program by host-side search. It
builds a compiled program plus a query formula, and the query API passes those
formulas to the proof kernel.

A quantified example shows why ADR-0010 also needs definitional helpers and
inlining:

```prolog
only-zero(x) := forall y. (x != y or y = zero).

zero-only(x) :- only-zero(x).
```

In the prefix frontend, `:=` introduces a source-level formula abbreviation;
`|-` introduces a kernel-visible relation.

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

(query/query-status zero-only-program
                    (pf/q (zero-only zero))
                    {:timeout-ms 2000})
;; => :succeeds

(query/query-status zero-only-program
                    (pf/q (zero-only (s zero)))
                    {:timeout-ms 2000})
;; => :fails
```

After frontend inlining, the quantified program descends to the same current
source clause shape:

```clojure
(def zero-only-lang
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'zero-only 1}}))

(def zero-only-program
  (ast/nom x y
    (language/compile-program
      zero-only-lang
      [(ast/clause
         'zero-only
         [x]
         (ast/forall-form
           y
           (ast/or-form
             (ast/neq-lit (ast/var-term x) (ast/var-term y))
             (ast/eq-lit (ast/var-term y) (ast/app-term 'zero)))))])))

(query/query-status
  zero-only-program
  (ast/pos-lit (ast/app-term 'zero-only (ast/app-term 'zero)))
  {:timeout-ms 2000})
;; => :succeeds

(query/query-status
  zero-only-program
  (ast/pos-lit
    (ast/app-term 'zero-only
                  (ast/app-term 's (ast/app-term 'zero))))
  {:timeout-ms 2000})
;; => :fails
```

Schematic tagged AST for the inlined `zero-only/1` clause body:

```clojure
(forall
  (tie y
    (or
      (neq (var x) (var y))
      (eq (var y) (app zero)))))
```

The compiled relation parameter is `x`, the object-language argument to
`zero-only/1`. The quantified `y` is local to the clause body and is represented
by a nominal tie. Schematically, the compiled clause is:

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

The query descent is the same. For `zero-only(zero)`:

```clojure
(pf/q (zero-only zero))
```

becomes:

```clojure
(pos (app zero-only (app zero)))
```

and the two kernel probes are:

```clojure
(kernel/prove-program
  zero-only-program
  (neg (app zero-only (app zero)))
  1
  fuel)

(kernel/prove-program
  zero-only-program
  (pos (app zero-only (app zero)))
  1
  fuel)
```

For `zero-only(s(zero))`:

```clojure
(pf/q (zero-only (s zero)))
```

becomes:

```clojure
(pos (app zero-only (app s (app zero))))
```

and the kernel probes are:

```clojure
(kernel/prove-program
  zero-only-program
  (neg (app zero-only (app s (app zero))))
  1
  fuel)

(kernel/prove-program
  zero-only-program
  (pos (app zero-only (app s (app zero))))
  1
  fuel)
```

The public `proflog.ast` constructors and `proflog.language/compile-program`
API remain available for lower-level inspection, tests, and examples that need
to talk directly about the backend representation.

Open answer queries use the same frontend layer. `pf/run` binds visible answer
variables in the evaluation form, like miniKanren's `run`, and delegates to the
public answer API:

```clojure
(pf/run p-program [x]
  (p x)
  {:proof-limit 1})
;; schematically => [{:bindings [[x a]], :residuals [], :proofs [...]}]
```

For diagnostics or alternate answer APIs, the lower-level `pf/answer-query`
builder still returns `{:query formula :answer-vars [...]}` without evaluating
the program.

For non-trivial examples, run the focused Fitting and legacy-subsumption gates:

```text
lein test-proflog-fitting-programs
lein test-proflog-legacy-subsumption
```

For Robinson arithmetic Q as both ordinary assumptions and an opt-in
deduction-modulo profile, run:

```text
lein test-proflog-robinson-q
lein probe-proflog-robinson-q
```

The worked example in [worked-examples/robinson-q.md](worked-examples/robinson-q.md)
includes Q conversion, Q3, and the corrected prime/evenness formulas.

For the Willard SJAS-lang builder and proof profiles, run:

```text
lein test-proflog-sjas-focused
```

The focused command runs `proflog.willard-sjas-test` one test var at a time and
prints start/end timing for each var. Prefer it while doing active SJAS work;
it makes slow but advancing proof checks visible and avoids losing minutes to
an opaque namespace run. Use `lein test-proflog-sjas` as the ordinary
`clojure.test` namespace gate only after the focused run has shown acceptable
progress, or when a final full namespace confirmation is specifically needed.

The worked example in [worked-examples/willard-sjas.md](worked-examples/willard-sjas.md)
shows generated Group-Zero through Group-3 axiom bases, reflected versus
external clauses, formula-class checks, binary U-grounding arithmetic,
answer/partial-synthesis modes, compact base-64 Godel-code terms,
kernel-checked proof certificates, and the non-vacuous `SelfCons`
demonstration for concrete contradiction/complement and Level-1 fixed-point
substitution proof targets, including the finite `subst-code/2` relation.

For the opt-in Turing-completeness demonstration, run:

```text
lein test-proflog-turing-completeness
```

This suite is slower than the routine greenfield gates. It demonstrates a
two-counter Minsky machine interpreter written as Proflog clauses, including
forward transition proofs, answer export, partial synthesis over an instruction
relation, and a deeper trace-shaped Minsky proof. It also includes an
independent SKI combinatory-logic demonstration with root reductions, bounded
evaluation, answer export, and a guided self-reproducing omega quine trace.

The new TC demonstrations can also be run separately:

```text
lein test-proflog-minsky-trace-performance
lein test-proflog-combinatory-logic
```

The worked examples in [worked-examples/](worked-examples/README.md) show how
programs are defined, translated, queried, and evaluated.

## Execution Docs

- [docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md)
- [docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md](docs/GREENFIELD_IMPLEMENTATION_TUTORIAL.md)
- [docs/GREENFIELD_SOURCE_MAP.md](docs/GREENFIELD_SOURCE_MAP.md)
- [worked-examples/README.md](worked-examples/README.md)
- [LOG.md](LOG.md)
- [docs/TEST_MATRIX.md](docs/TEST_MATRIX.md)
- [docs/TEST_RUNTIME_BASELINE.md](docs/TEST_RUNTIME_BASELINE.md)
- [docs/SEMANTIC_VARIANTS.md](docs/SEMANTIC_VARIANTS.md)
- [docs/adr/README.md](docs/adr/README.md)
- [docs/aar/README.md](docs/aar/README.md)

## Local Source Stack

- [development-practices.md](development-practices.md)
- [DESIGN.md](DESIGN.md)
- [LESSONS.md](LESSONS.md)
- [MEMORY.md](MEMORY.md)
- [LPTableaus.pdf](LPTableaus.pdf)

## External Primary Sources Reviewed

- alphaleanTAP paper: <https://people.csail.mit.edu/jnear/papers/alphatap.pdf>
- core.logic repository: <https://github.com/clojure/core.logic>
- core.logic API reference: <https://clojure.github.io/core.logic/>
- Byrd dissertation: <https://hdl.handle.net/2022/8777>
- Fitting 1994 bibliographic record: <https://dblp.org/rec/journals/jar/Fitting94>

## Working Agreement

- `main` is the integration branch for the implementation.
- New feature work should follow ADR-specific branches and merge into `main`
  once closed or deliberately carried forward.
- New implementation code lives under `src/proflog/` and `test/proflog/`.
- `src/cljtap/` and `test/cljtap/` remain reference and regression material
  unless a later ADR explicitly retires them.
