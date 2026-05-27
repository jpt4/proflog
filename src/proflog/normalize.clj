(ns proflog.normalize
  "NNF conversion for the greenfield Proflog implementation.

   The surface layer may use explicit `not` and `implies`; the compiled core
   should retain only NNF connectives, quantifiers, and literals.

   This matters operationally because the kernel does not implement a generic
   runtime negation connective. Fitting's procedure-call rule for a negative
   atom opens a subsidiary tableau for the negation of the called relation's
   defining body. The language compiler therefore precomputes each compiled
   clause's `:negated-body` through this namespace, keeping negative calls as
   ordinary positive work over NNF formulas."
  (:require [proflog.ast :as ast]))

(defn- leq-guard-atom
  "Return the relational atom used to guard an SJAS bounded quantifier."
  [binding-nom bound-term]
  (ast/app-term 'leq (ast/var-term binding-nom) bound-term))

(defn- leq-guard-literal
  "Return the relational guard used when lowering an SJAS bounded quantifier.

   Bounded quantifiers are surface syntax. The core proof kernel already knows
   how to handle ordinary quantifiers and procedure-call atoms, so lowering
   `(bounded-forall x n body)` to `forall x. leq(x,n) -> body` keeps the kernel
   generic while preserving the bound as a normal Proflog relation call."
  [binding-nom bound-term]
  (ast/pos-lit (leq-guard-atom binding-nom bound-term)))

(defn- negated-leq-guard-literal
  "Return the NNF literal for the false branch of a bounded-universal guard."
  [binding-nom bound-term]
  (ast/neg-lit (leq-guard-atom binding-nom bound-term)))

(declare to-nnf negate-formula)

(defn negate-formula
  "Return the NNF negation of `formula`.

   This function is total over the greenfield surface language, not only over
   formulas that are already in NNF. That matters because negative procedure
   calls will eventually need a reliable way to negate surface bodies as well as
   previously normalized ones."
  [formula]
  (let [tag (ast/tag-of formula)]
    (case tag
      true (ast/false-form)
      false (ast/true-form)
      pos (ast/neg-lit (second formula))
      neg (ast/pos-lit (second formula))
      eq (ast/neq-lit (second formula) (nth formula 2))
      neq (ast/eq-lit (second formula) (nth formula 2))
      and (ast/or-form (negate-formula (second formula))
                       (negate-formula (nth formula 2)))
      or (ast/and-form (negate-formula (second formula))
                       (negate-formula (nth formula 2)))
      forall (let [tied (second formula)]
               (ast/exists-form (:binding-nom tied)
                                (negate-formula (:body tied))))
      once-forall (let [tied (second formula)]
                    (ast/exists-form (:binding-nom tied)
                                     (negate-formula (:body tied))))
      exists (let [tied (second formula)]
               ;; Negated existential clause bodies are operationally
               ;; single-use: instantiate once on the current branch rather
               ;; than re-enqueueing an ordinary universal indefinitely.
               (ast/once-forall-form (:binding-nom tied)
                                     (negate-formula (:body tied))))
      bounded-forall (let [tied (second formula)
                           b (:binding-nom tied)
                           {:keys [bound body]} (:body tied)]
                       (ast/exists-form b
                         (ast/and-form (leq-guard-literal b bound)
                                       (negate-formula body))))
      bounded-exists (let [tied (second formula)
                           b (:binding-nom tied)
                           {:keys [bound body]} (:body tied)]
                       (ast/once-forall-form b
                         (ast/or-form (negated-leq-guard-literal b bound)
                                      (negate-formula body))))
      not (to-nnf (second formula))
      implies (negate-formula (to-nnf formula))
      (throw (ex-info "Unsupported formula for NNF negation"
                      {:formula formula})))))

(defn to-nnf
  "Compile a greenfield surface formula into NNF."
  [formula]
  (let [tag (ast/tag-of formula)]
    (case tag
      true formula
      false formula
      pos formula
      neg formula
      eq formula
      neq formula
      and (ast/and-form (to-nnf (second formula))
                        (to-nnf (nth formula 2)))
      or (ast/or-form (to-nnf (second formula))
                      (to-nnf (nth formula 2)))
      not (negate-formula (second formula))
      implies (to-nnf (ast/or-form (ast/not-form (second formula))
                                   (nth formula 2)))
      forall (let [tied (second formula)]
               (ast/forall-form (:binding-nom tied)
                                (to-nnf (:body tied))))
      once-forall (let [tied (second formula)]
                    (ast/once-forall-form (:binding-nom tied)
                                          (to-nnf (:body tied))))
      exists (let [tied (second formula)]
               (ast/exists-form (:binding-nom tied)
                                (to-nnf (:body tied))))
      bounded-forall (let [tied (second formula)
                           b (:binding-nom tied)
                           {:keys [bound body]} (:body tied)]
                       (ast/forall-form b
                         (ast/or-form (negated-leq-guard-literal b bound)
                                      (to-nnf body))))
      bounded-exists (let [tied (second formula)
                           b (:binding-nom tied)
                           {:keys [bound body]} (:body tied)]
                       (ast/exists-form b
                         (ast/and-form (leq-guard-literal b bound)
                                       (to-nnf body))))
      (throw (ex-info "Unsupported formula for NNF compilation"
                      {:formula formula})))))
