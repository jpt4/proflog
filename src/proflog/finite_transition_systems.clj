(ns proflog.finite-transition-systems
  "ADR-0039 finite transition-system verifier examples.

   These fixtures are deliberately not group-verifier examples. They encode
   DFA-like transition-table laws as ordinary Proflog source clauses over
   constants, then leave truth and falsity to the kernel-level equality
   verifier after compilation."
  (:require [proflog.ast :as ast]
            [proflog.language :as language]))

(defn app
  [sym & args]
  (apply ast/app-term sym args))

(defn- and*
  [formulas]
  (case (count formulas)
    0 (ast/true-form)
    1 (first formulas)
    (reduce ast/and-form formulas)))

(defn- or*
  [formulas]
  (case (count formulas)
    0 (ast/false-form)
    1 (first formulas)
    (reduce ast/or-form formulas)))

(defn- forall*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/forall-form binding-nom acc))
          body
          (reverse noms)))

(defn- exists*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/exists-form binding-nom acc))
          body
          (reverse noms)))

(defn- in-set-inline
  [values term]
  (or* (for [value values]
         (ast/eq-lit term (app value)))))

(defn- not-in-set-inline
  [values term]
  (and* (for [value values]
          (ast/neq-lit term (app value)))))

(defn- delta-inline
  [transitions q sym target]
  (or* (for [[from input to] transitions]
         (and* [(ast/eq-lit q (app from))
                (ast/eq-lit sym (app input))
                (ast/eq-lit target (app to))]))))

(defn- not-delta-inline
  [transitions q sym target]
  (and* (for [[from input to] transitions]
          (or* [(ast/neq-lit q (app from))
                (ast/neq-lit sym (app input))
                (ast/neq-lit target (app to))]))))

(def base-states
  ['q0 'q1 'q2 'q3])

(def base-symbols
  ['a 'b 'c])

(def complete-transitions
  [['q0 'a 'q1]
   ['q0 'b 'q2]
   ['q0 'c 'q3]
   ['q1 'a 'q1]
   ['q1 'b 'q3]
   ['q1 'c 'q0]
   ['q2 'a 'q3]
   ['q2 'b 'q0]
   ['q2 'c 'q2]
   ['q3 'a 'q0]
   ['q3 'b 'q1]
   ['q3 'c 'q2]])

(def complete-deterministic-spec
  {:id :complete-deterministic
   :states base-states
   :symbols base-symbols
   :transitions complete-transitions})

(def incomplete-spec
  {:id :incomplete
   :states base-states
   :symbols base-symbols
   :transitions (vec (remove #{['q3 'c 'q2]}
                             complete-transitions))})

(def nondeterministic-spec
  {:id :nondeterministic
   :states base-states
   :symbols base-symbols
   :transitions (conj complete-transitions
                      ['q0 'a 'q2])})

(defn transition-language
  [spec]
  (language/language
    {:constants (vec (concat (:states spec) (:symbols spec)))
     :relations {'delta-total 0
                 'delta-deterministic 0}}))

(defn totality-formula
  [spec q sym target]
  (let [vq (ast/var-term q)
        vsym (ast/var-term sym)
        vt (ast/var-term target)]
    (forall*
      [q sym]
      (or*
        [(not-in-set-inline (:states spec) vq)
         (not-in-set-inline (:symbols spec) vsym)
         (exists*
           [target]
           (and*
             [(in-set-inline (:states spec) vt)
              (delta-inline (:transitions spec) vq vsym vt)]))]))))

(defn determinism-formula
  [spec q sym t1 t2]
  (let [vq (ast/var-term q)
        vsym (ast/var-term sym)
        vt1 (ast/var-term t1)
        vt2 (ast/var-term t2)]
    (forall*
      [q sym t1 t2]
      (or*
        [(not-in-set-inline (:states spec) vq)
         (not-in-set-inline (:symbols spec) vsym)
         (not-in-set-inline (:states spec) vt1)
         (not-in-set-inline (:states spec) vt2)
         (not-delta-inline (:transitions spec) vq vsym vt1)
         (not-delta-inline (:transitions spec) vq vsym vt2)
         (ast/eq-lit vt1 vt2)]))))

(defn transition-program
  [spec]
  (ast/nom q sym target t1 t2
    (language/compile-program
      (transition-language spec)
      [(ast/clause 'delta-total
                   []
                   (totality-formula spec q sym target))
       (ast/clause 'delta-deterministic
                   []
                   (determinism-formula spec q sym t1 t2))])))

(defn proposition
  [relation]
  (ast/pos-lit (app relation)))
