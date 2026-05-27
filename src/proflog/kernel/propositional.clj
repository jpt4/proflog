(ns proflog.kernel.propositional
  "Pure propositional tableau component for profile-dispatched theorem proving.

   The state is intentionally smaller than `proflog.kernel`: current formula,
   pending branch work, saved literals, optional fuel, and proof term. There is
   no environment, equality substitution, disequality store, program, gamma
   term source, or procedure-call machinery."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde fresh lcons membero run]]
            [proflog.kernel-support :as support]))

(declare prove-stateo)

(defn- close-lito
  [lit lits proof]
  (conde
    [(fresh [atom]
       (== (list 'pos atom) lit)
       (membero (list 'neg atom) lits)
       (== '(close) proof))]
    [(fresh [atom]
       (== (list 'neg atom) lit)
       (membero (list 'pos atom) lits)
       (== '(close) proof))]))

(defn- save-lito
  [lit unexpanded lits fuel proof]
  (fresh [next rest next-fuel prf]
    (== (lcons next rest) unexpanded)
    (== (list 'savefml prf) proof)
    (support/step-fuelo fuel next-fuel)
    (prove-stateo next rest (lcons lit lits) next-fuel prf)))

(defn prove-stateo
  "Relational branch-closing tableau for pure propositional NNF formulas."
  [fml unexpanded lits fuel proof]
  (conde
    ;; Alpha rule: both conjuncts remain on the same branch.
    [(fresh [left right next-fuel prf]
       (== (list 'and left right) fml)
       (== (list 'conj prf) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo left (lcons right unexpanded) lits next-fuel prf))]

    ;; Beta rule: both disjunctive branches must close independently.
    [(fresh [left right next-fuel left-proof right-proof]
       (== (list 'or left right) fml)
       (== (list 'split left-proof right-proof) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo left unexpanded lits next-fuel left-proof)
       (prove-stateo right unexpanded lits next-fuel right-proof))]

    ;; False on a branch is an immediate propositional contradiction.
    [(== (list 'false) fml)
     (== '(false-close) proof)]

    ;; True contributes no contradiction; continue only if branch work remains.
    [(fresh [next rest next-fuel prf]
       (== (list 'true) fml)
       (== (lcons next rest) unexpanded)
       (== (list 'skip-true prf) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo next rest lits next-fuel prf))]

    ;; Positive and negative literals close by exact complementary membership.
    [(fresh [atom]
       (== (list 'pos atom) fml)
       (close-lito fml lits proof))]
    [(fresh [atom]
       (== (list 'neg atom) fml)
       (close-lito fml lits proof))]

    ;; Otherwise save the literal and continue with pending branch work.
    [(fresh [atom]
       (== (list 'pos atom) fml)
       (save-lito fml unexpanded lits fuel proof))]
    [(fresh [atom]
       (== (list 'neg atom) fml)
       (save-lito fml unexpanded lits fuel proof))]))

(defn proveo
  "Public propositional proof relation."
  ([fml unexpanded lits proof]
   (prove-stateo fml unexpanded lits nil proof))
  ([fml unexpanded lits fuel proof]
   (prove-stateo fml unexpanded lits fuel proof)))

(defn prove
  "Return up to `n` propositional proof terms for `fml`."
  ([fml] (prove fml 1))
  ([fml n]
   (run n [proof]
     (proveo fml '() '() proof)))
  ([fml n fuel]
   (run n [proof]
     (proveo fml '() '() fuel proof))))
