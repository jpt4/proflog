# 2026-04-29 Step Fuel Relational Purity Gap

## Prompt

step-fuelo is used in the kernel, but only optionally, correct? That is, is it
possible to run queries against the proof kernel without calling step-fuelo? If
not, are there any tests that would fail because step-fuelo must be called?

## Follow-up Prompt

Copy. Log this, then branch to a new ADR and devise reverse/partial synthesis
tests that fail due to lack of relational purity in step-fuelo. The goal is
for the entire kernel to be a pure relation, including its transitive
dependencies.

## Recorded Answer

`step-fuelo` is optional only in the sense that callers can choose the
unbounded fuel value `nil`.

It is not optional in the full proof kernel execution path. Any non-closing
branch-progress rule calls `support/step-fuelo`, including:

- alpha / beta connective expansion;
- gamma and once-forall instantiation;
- delta witness introduction;
- equality continuation after a non-closing equality;
- storing a delayed disequality;
- positive and negative procedure calls;
- saving positive and negative literals for later closure.

Immediate branch-closing rules can avoid it because they do not consume a
micro-step. For example, direct complementary literal closure and immediate
equality / disequality contradiction do not need fuel. But a query that needs
any structural branch progress reaches `step-fuelo` even when the fuel value is
`nil`; in that case `step-fuelo` is still called to relate `fuel` and
`next-fuel` as the unbounded pair.

The public wrapper without an explicit fuel argument seeds the kernel with
`fuel = nil`:

```clojure
(defn proveo
  ([fml unexpanded lits env proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() nil proof)))
  ([fml unexpanded lits env fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() fuel proof))))
```

The old `step-fuelo` boundary projected `fuel` to the host:

```clojure
(defn step-fuelo
  [fuel next-fuel]
  (project [fuel]
    (cond
      (nil? fuel) (== next-fuel nil)
      (> fuel 0) (== next-fuel (dec fuel))
      :else fail)))
```

That made forward, ground-fuel use work, but it broke reverse and partial
synthesis modes. The host comparison `> fuel 0` cannot run when `fuel` is a
core.logic variable.

## Failing Tests Identified

Direct reverse synthesis of a predecessor fuel value:

```clojure
(run 1 [fuel]
  (support/step-fuelo fuel 0))
```

Expected after repair:

```clojure
'(1)
```

Current projected result before repair: `ClassCastException` from trying to
compare an `LVar` with `0`.

Direct synthesis of the unbounded fuel case:

```clojure
(run 1 [fuel]
  (support/step-fuelo fuel nil))
```

Expected after repair:

```clojure
'(nil)
```

Current projected result before repair: `ClassCastException` from trying to
compare an `LVar` with `0`.

Forward successor synthesis remains a guard that the replacement preserves
extant behavior:

```clojure
(run 1 [next-fuel]
  (support/step-fuelo 1 next-fuel))
```

Expected:

```clojure
'(0)
```

The ordinary pure-kernel relation also fails when a direct caller leaves fuel
open and the proof requires structural progress:

```clojure
(let [formula (ast/and-form
                (ast/pos-lit (ast/app-term 'p))
                (ast/neg-lit (ast/app-term 'p)))]
  (run 1 [fuel]
    (fresh [proof]
      (kernel/proveo formula '() '() '() fuel proof))))
```

Expected after repair:

```clojure
'(nil)
```

Current projected result before repair: `ClassCastException`, because the
conjunction step must call `step-fuelo` with `fuel` still open.

Program-body synthesis also fails when the procedure-call step must consume
open fuel:

```clojure
(ast/nom x
  (run 1 [fuel]
    (fresh [body-left proof]
      (kernel/prove-programo
        (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
        '() '() '()
        {:language '_
         :clauses '_
         :clause-list (list {:relation 'p
                             :params (list x)
                             :body (ast/eq-lit body-left
                                               (ast/app-term 'zero))
                             :negated-body (ast/true-form)})}
        '()
        fuel
        proof)
      (== body-left (ast/var-term x)))))
```

Expected after repair:

```clojure
'(nil)
```

Current projected result before repair: `ClassCastException`, because the
positive procedure-call rule must call `step-fuelo` before proving the
synthesized body.

## Decision Pointer

This prompted [ADR-0029](../adr/ADR-0029-relational-fuel-purity.md), whose
goal is to remove the last executable `project` boundary from the ordinary
kernel path by making fuel stepping a structural relation.
