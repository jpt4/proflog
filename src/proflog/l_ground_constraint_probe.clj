(ns proflog.l-ground-constraint-probe
  "ADR-37 probe for expressing Proflog L-groundness with core.logic constraints.

   This namespace is intentionally experimental. It compares three shapes:

   - `l-ground-no-paro`: a reusable `treec` absence constraint;
   - `l-ground-predco`: an intentionally unsafe `predc`-only guard;
   - `l-ground-root-and-no-paro`: a hybrid root-shape relation plus absence
     constraint.

   The production relation remains `proflog.kernel-support/l-ground-termo`."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde fresh lcons lvar? predc treec]]))

(defn par-term-shape?
  "Host predicate for a concrete Proflog `(par nom)` term."
  [term]
  (and (sequential? term)
       (= 'par (first term))
       (nil? (nnext term))))

(defn host-no-par-open-ok?
  "Projection-style host check used only to demonstrate the rejected behavior.

   It treats open logic variables as harmless. A `predc` guard using this
   predicate can therefore succeed while nested variables are still open and
   fail to notice a later `(par ...)` refinement."
  [term]
  (cond
    (lvar? term) true
    (par-term-shape? term) false
    (sequential? term) (every? host-no-par-open-ok? term)
    :else true))

(defn not-par-termo
  "Constraint that rejects a discovered `(par nom)` tree node.

   `predc` delays when `node` is still an open logic variable. Wrapped by
   `treec`, the delay is pushed down into later-discovered subterms."
  [node]
  (predc node (complement par-term-shape?) 'not-par-termo))

(defn l-ground-no-paro
  "A `treec` absence constraint for Proflog delta parameters.

   This is sound for the no-`par` part of L-groundness: open subterms keep
   delayed constraints, and later refinement to `(par nom)` fails. It is not a
   complete object-language term recognizer because `treec` has no parent
   context for deciding whether a symbol or list position should be a term,
   tag, head symbol, or argument-list tail."
  [term]
  (treec term not-par-termo 'l-ground-no-paro))

(defn l-ground-predco
  "Rejected `predc`-only guard that behaves like a projected host check.

   Kept as a negative control: it can admit `(app f x)` before `x` later
   becomes `(par nom)` because `predc` does not recursively watch nested
   variables after the outer predicate succeeds."
  [term]
  (predc term host-no-par-open-ok? 'l-ground-predco))

(defn l-ground-root-shapeo
  "Recognize only the root shape of a Proflog object-language term."
  [term]
  (conde
    [(fresh [binding-nom]
       (== (list 'var binding-nom) term))]
    [(fresh [head args]
       (== (lcons 'app (lcons head args)) term))]))

(defn l-ground-root-and-no-paro
  "Hybrid probe: root term shape plus persistent no-`par` tree constraint.

   This remains weaker than `support/l-ground-termo`: it proves the root is a
   `var` or `app` and that no delta parameter appears anywhere, but it does not
   validate that every app argument is itself a Proflog term."
  [term]
  (fresh []
    (l-ground-root-shapeo term)
    (l-ground-no-paro term)))
