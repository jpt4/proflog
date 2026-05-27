# Combinatory Logic Example

ADR-0046 adds a second Turing-completeness demonstration using SKI
combinatory logic. The implementation lives in
`src/proflog/combinatory_logic.clj`, and its opt-in regression suite is
`test/proflog/combinatory_logic_test.clj`.

This example is intentionally different from the two-counter Minsky machine.
Minsky execution exercises state transitions and Peano counters. SKI execution
exercises symbolic term rewriting over first-order constructor trees.

Citation trail: SKI belongs to the standard combinatory-logic tradition. The
historical references are Moses Schoenfinkel's 1924 paper, "On the Building
Blocks of Mathematical Logic," and Curry and Feys' 1958 book,
*Combinatory Logic, Volume I*. SKI combinators are a standard
Turing-complete basis for computation.

## Term Model

The object language has constants:

```text
scomb
kcomb
icomb
a
b
c
zero
```

Applications are first-order terms:

```text
ap(left, right)
```

Peano numerals bound evaluation depth:

```text
zero
s(zero)
s(s(zero))
```

The key relations are:

```text
step(before, after)
full-step(before, after)
eval-for(steps, start, final)
```

## Prolog-Style Pseudo-Code

The root SKI reductions are ordinary Proflog clauses:

```prolog
step(ap(icomb, x), x).

step(ap(ap(kcomb, x), y), x).

step(ap(ap(ap(scomb, x), y), z),
     ap(ap(x, z), ap(y, z))).
```

Curried SKI terms also need a left-spine application context. This lets the
kernel reduce the function side of an outer application, as in
`((K I) a) b`:

```prolog
step(ap(function, argument), ap(reduced-function, argument)) :-
  step(function, reduced-function).
```

The bounded evaluator is the usual finite reflexive reduction relation:

```prolog
eval-for(zero, start, final) :-
  start = final.

eval-for(s(rest), start, final) :-
  exists middle.
    step(start, middle)
    and eval-for(rest, middle, final).
```

ADR-0047 adds a separate full-context one-step relation for focused examples
that need argument-position reduction:

```prolog
full-step(before, after) :-
  step(before, after).

full-step(ap(function, argument), ap(reduced-function, argument)) :-
  full-step(function, reduced-function).

full-step(ap(function, argument), ap(function, reduced-argument)) :-
  full-step(argument, reduced-argument).
```

This rule is intentionally separate from `step/2`. A direct experiment adding
argument-position contextual reduction to `step/2` made the whole SKI suite
time out inside a `900 s` guard.

Boolean-style examples use the standard encodings:

```text
true  = K
false = K I
choose(boolean, then, else) = ((boolean then) else)
```

## Frontend Definition

The executable frontend program declares the object-language signature and then
compiles clauses with the ADR-0010 `pf/proflog` frontend:

```clojure
(def ski-language
  (pf/language
    (constants zero
               scomb kcomb icomb
               a b c)
    (functions (s 1)
               (ap 2))
    (relations (step 2)
               (full-step 2)
               (eval-for 3))))
```

The source-level program is:

```clojure
(def ski-program
  (pf/proflog ski-language
    (|- (step before after)
      (exists [x]
        (and (= before (ap icomb x))
             (= after x))))

    (|- (step before after)
      (exists [x y]
        (and (= before (ap (ap kcomb x) y))
             (= after x))))

    (|- (step before after)
      (exists [x y z]
        (and (= before (ap (ap (ap scomb x) y) z))
             (= after (ap (ap x z) (ap y z))))))

    (|- (step before after)
      (exists [function argument reduced-function]
        (and (= before (ap function argument))
             (step function reduced-function)
             (= after (ap reduced-function argument)))))

    (|- (eval-for steps start final)
      (and (= steps zero)
           (= start final)))

    (|- (eval-for steps start final)
      (exists [rest middle]
        (and (= steps (s rest))
             (step start middle)
             (eval-for rest middle final))))

    (|- (full-step before after)
      (step before after))

    (|- (full-step before after)
      (exists [function argument reduced-function]
        (and (= before (ap function argument))
             (full-step function reduced-function)
             (= after (ap reduced-function argument)))))

    (|- (full-step before after)
      (exists [function argument reduced-argument]
        (and (= before (ap function argument))
             (full-step argument reduced-argument)
             (= after (ap function reduced-argument)))))))
```

The Clojure helpers in the namespace only construct terms:

```clojure
(ski/ap (ski/c 'icomb) (ski/c 'a))
;; ap(icomb, a)

(ski/skk (ski/c 'a))
;; ap(ap(ap(scomb, kcomb), kcomb), a)

(ski/omega)
;; ap(ap(ap(scomb, icomb), icomb), ap(ap(scomb, icomb), icomb))

(ski/choose (ski/false-term) (ski/c 'a) (ski/c 'b))
;; ap(ap(ap(kcomb, icomb), a), b)
```

They do not reduce or evaluate SKI terms.

## Backend Descent

The K rule:

```clojure
(|- (step before after)
  (exists [x y]
    (and (= before (ap (ap kcomb x) y))
         (= after x))))
```

descends to a compiled `step/2` alternative whose body is schematically:

```clojure
(exists
  (tie x
    (exists
      (tie y
        (and
          (eq (var before)
              (app ap
                   (app ap (app kcomb) (var x))
                   (var y)))
          (eq (var after) (var x)))))))
```

The Procedure Call Rule proves `step/2` by choosing one compiled alternative,
binding the formal parameters `before` and `after` to the actual query terms,
and proving the body. The left-spine context rule is just another compiled
`step/2` alternative; it recursively calls `step/2` inside the kernel.

## Evaluation Process

Closed proof checks in the route-audit tests bypass public proof-profile
dispatch. They validate the formula, negate it for tableau refutation, and call
the relational kernel directly:

```clojure
(let [program (ski/program)
      formula (ast/pos-lit
                (ast/app-term 'step
                              (ski/ap (ski/ap (ski/c 'kcomb) (ski/c 'a))
                                      (ski/c 'b))
                              (ski/c 'a)))
      checked-formula (language/validate-query (:language program) formula)
      negated-formula (normalize/negate-formula checked-formula)]
  (run 1 [proof]
    (kernel/prove-programo
      negated-formula
      '()
      '()
      '()
      program
      32
      proof)))
```

The parameters are:

| Parameter | Meaning |
|---|---|
| `(ski/program)` | compiled Proflog program containing the SKI clauses |
| `formula` | positive object-language literal to prove |
| `checked-formula` | query after language validation |
| `negated-formula` | NNF negation used as the tableau refutation target |
| `'()` environment arguments | initial substitution, disequality, and residual state |
| `32` | fuel bound for proof search |
| `proof` | relational proof term returned by `kernel/prove-programo` |

Answer-mode route-audit checks likewise bypass `pf/run` and the public
`answers/query-answers` wrapper. They still use ordinary language validation,
but the proof state is produced by the answer overlay relations:

```clojure
(ast/nom result
  (let [program (ski/program)
        query (ast/pos-lit
                (ast/app-term 'eval-for
                              (ski/numeral 2)
                              (ski/skk (ski/c 'a))
                              (ast/var-term result)))
        checked-query (language/validate-query (:language program) query)
        checked-answer-vars [result]
        negated-query (normalize/negate-formula checked-query)]
    (run 16 [answer-vars-out sigma-out neqs-out residuals-out proof]
      (== answer-vars-out checked-answer-vars)
      (answer-overlay/prove-program-query-entry-scheduledo
        negated-query
        checked-answer-vars
        program
        sigma-out
        neqs-out
        residuals-out
        64
        4
        96
        proof))))
```

The answer parameters are:

| Parameter | Meaning |
|---|---|
| `query` | positive object-language `eval-for/3` literal |
| `checked-answer-vars` | answer variable noms to export after proof search |
| `negated-query` | NNF negation used as the answer-tableau entry |
| `answer-vars-out` | relation output used to keep the answer-variable vector visible |
| `sigma-out` | relational substitution accumulated during proof search |
| `neqs-out` | relational disequality constraints accumulated during proof search |
| `residuals-out` | unresolved residual goals after scheduler completion |
| `64` | proof-search fuel passed to the answer overlay |
| `4` | answer-overlay procedure-call expansion depth |
| `96` | residual-continuation fuel for the relational scheduler |
| `proof` | relational proof term for the raw answer state |

The test then uses the private answer-record exporter as presentation only. The
raw state has already been found by `prove-program-query-entry-scheduledo`,
which invokes `prove-program-query-entryo` and the relational residual
scheduler.

The promoted answer row exports:

```clojure
{:bindings [[result a]]
 :residuals []}
```

ADR-0055 adds a route trace around these helpers. The trace records calls to
`kernel/prove-programo`,
`answer-overlay/prove-program-query-entry-scheduledo`, and
`answer-overlay/prove-program-query-entryo`, and fails if SKI evaluation enters
`kernel/prove-program`, `query/query-succeeds`, `answers/query-answers`,
constructor-recursive sidecars, or the equality-fragment host profile.

The ADR-0047 quine example uses the standard self-reproducing SKI term:

```text
omega = (S I I) (S I I)
```

The proof is not the trivial zero-step equality case. The guided trace proves
three positive `full-step/2` edges:

```clojure
(let [sii (ski/sii)
      i-sii (ski/ap (ski/c 'icomb) sii)
      omega (ski/omega)
      expanded (ski/ap i-sii i-sii)
      left-contracted (ski/ap sii i-sii)]
  (query/query-succeeds
    (ski/program)
    (ski/reduction-trace-formula
      [omega
       expanded
       left-contracted
       omega]
      {:relation 'full-step})
    1
    160))
```

The trace is:

```text
(S I I) (S I I)
=> (I (S I I)) (I (S I I))
=> (S I I) (I (S I I))
=> (S I I) (S I I)
```

Direct `eval-for(3, omega, omega)` was attempted first and timed out inside a
`240 s` guard under the ADR-0046 relation. Adding right-argument context
directly to `step/2` still left direct bounded evaluation unable to finish
inside a `360 s` guard and made the full SKI suite time out inside `900 s`.
The promoted result is therefore a guided kernel trace, not open recursive
discovery of the loop.

## Test Results

Run the focused suite explicitly:

```text
lein test-proflog-combinatory-logic
```

Current promoted checks:

| Test | Mode | Outcome | Focused runtime |
|---|---|---|---:|
| `ski-evaluation-does-not-route-through-public-or-profiled-shortcuts` | route audit | records direct relational kernel and scheduled answer-overlay calls while forbidding public dispatch, constructor-recursive sidecars, and equality-fragment host shortcuts | `29.14 s` |
| `ski-root-reductions-close-through-the-kernel` | forward | proves I, K, and S root reductions with procedure-call evidence | `31.33 s` |
| `ski-skk-identity-fully-evaluates` | bounded forward recursion | `eval-for(2, SKK a, a)` succeeds | `44.29 s` |
| `ski-boolean-true-fully-evaluates` | bounded forward recursion | `choose(K, a, b)` reduces to `a` | `20.09 s` |
| `ski-boolean-false-fully-evaluates` | bounded forward recursion | `choose(K I, a, b)` reduces to `b` through the left-spine context rule | `45.29 s` |
| `ski-omega-quine-reproduces-itself-through-a-guided-trace` | guided full-context trace | proves `(S I I)(S I I)` returns to itself after three positive reductions | `95.44 s` |
| `ski-answer-mode-exports-a-reduced-term` | answer | exports `result = a` for `SKK a` with no residuals through the scheduled answer-overlay relation | `49.32 s` |
| `combinatory-logic-namespace-does-not-contain-a-host-evaluator` | source audit | no host query/answer evaluator or host `step`/`eval`/`reduce` function in the namespace | `15.80 s` |

The ADR-0046 full focused suite passed with:

```text
Ran 6 tests containing 12 assertions.
0 failures, 0 errors.
elapsed_seconds 225.50
```

After ADR-0047, the full focused suite passed with:

```text
Ran 7 tests containing 13 assertions.
0 failures, 0 errors.
elapsed_seconds 301.98
```

After ADR-0055, the full focused suite passed with the route guard and direct
relational answer helper:

```text
Ran 8 tests containing 18 assertions.
0 failures, 0 errors.
real 176.02
```

The aggregate Turing-completeness selector also passed after ADR-0055:

```text
Ran 16 tests containing 35 assertions.
0 failures, 0 errors.
real 273.27
```

## Correctness And Shortcomings

Correctness guardrails:

- every reduction rule is a compiled Proflog clause;
- the left-spine context rule is a recursive kernel-level `step/2` clause, not
  a host evaluator;
- proof tests inspect procedure-call evidence;
- answer-mode tests require an empty-residual exported result;
- source audit rejects hidden host-side query, answer, step, eval, reduce, or
  rewrite helpers.

Operational shortcomings:

- the answer-mode SKK example is slow after adding the left-spine context rule;
- only function-position contextual reduction is implemented, because the
  promoted examples do not need right-argument reduction;
- there is no lambda-to-SKI translator;
- no confluence, normalization, or arbitrary open-ended reduction theorem is
  claimed;
- the suite is opt-in and should not be placed on the routine fast path.
- direct recursive quine discovery with `eval-for(3, omega, omega)` remains
  too slow for promotion; the passing quine result uses a trace-shaped formula
  over kernel-proved `full-step/2` edges.

The current result is a second, independent minimum viable demonstration:
Proflog can encode and evaluate finite computations in a known
Turing-complete symbolic rewriting calculus through the proof kernel.
