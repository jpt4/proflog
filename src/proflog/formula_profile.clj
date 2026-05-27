(ns proflog.formula-profile
  "Structural formula profiles for choosing the weakest sufficient proof layer.

   These predicates inspect already-normalized greenfield formulas. They do not
   prove anything and they do not know about benchmark ids; they only report
   which syntactic capabilities a proof search must support."
  (:require [proflog.ast :as ast]))

(defn nullary-atom?
  "Return true when `atom` is a relation symbol with no arguments."
  [atom]
  (and (ast/app-term? atom)
       (nil? (nnext atom))))

(declare pure-propositional?
         equality-free-first-order?
         equality-bearing?)

(defn- tied-body
  [formula]
  (:body (second formula)))

(defn pure-propositional?
  "Return true for NNF formulas over only propositional connectives and atoms."
  [formula]
  (case (ast/tag-of formula)
    true true
    false true
    pos (nullary-atom? (second formula))
    neg (nullary-atom? (second formula))
    and (and (pure-propositional? (second formula))
             (pure-propositional? (nth formula 2 nil)))
    or (and (pure-propositional? (second formula))
            (pure-propositional? (nth formula 2 nil)))
    false))

(defn equality-bearing?
  "Return true when an NNF formula contains equality or disequality."
  [formula]
  (case (ast/tag-of formula)
    eq true
    neq true
    and (or (equality-bearing? (second formula))
            (equality-bearing? (nth formula 2 nil)))
    or (or (equality-bearing? (second formula))
           (equality-bearing? (nth formula 2 nil)))
    forall (equality-bearing? (tied-body formula))
    once-forall (equality-bearing? (tied-body formula))
    exists (equality-bearing? (tied-body formula))
    false))

(defn equality-free-first-order?
  "Return true for NNF first-order formulas that do not contain eq or neq."
  [formula]
  (case (ast/tag-of formula)
    true true
    false true
    pos (ast/atom? (second formula))
    neg (ast/atom? (second formula))
    and (and (equality-free-first-order? (second formula))
             (equality-free-first-order? (nth formula 2 nil)))
    or (and (equality-free-first-order? (second formula))
            (equality-free-first-order? (nth formula 2 nil)))
    forall (equality-free-first-order? (tied-body formula))
    once-forall (equality-free-first-order? (tied-body formula))
    exists (equality-free-first-order? (tied-body formula))
    false))

(defn profile
  "Return the weakest currently named profile for an NNF formula."
  [formula]
  (cond
    (pure-propositional? formula) :pure-propositional
    (equality-bearing? formula) :equality-bearing
    (equality-free-first-order? formula) :equality-free-first-order
    :else :unsupported))
