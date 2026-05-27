(ns proflog.subst
  "Greenfield substitution helpers.

   This namespace provides both pure helpers for compile-time rewriting and
   relational versions for later proof search. The pure helpers keep the
   program compiler straightforward; the relational helpers preserve the data
   flow shape the later tableau kernel will need."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [!= == conde fresh lcons]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]))

;; -----------------------------------------------------------------------------
;; Pure helpers
;; -----------------------------------------------------------------------------

(defn lookup-binding
  "Return the first value bound to `binding-nom` in an environment sequence."
  [env binding-nom]
  (some (fn [[k v]]
          (when (= k binding-nom)
            v))
        env))

(defn remove-binding
  "Drop all bindings for `binding-nom` from the environment.

   Quantifier substitution uses this to preserve lexical binding and prevent a
   substitution intended for an outer scope from leaking into the body of a
   quantifier that binds the same nom."
  [env binding-nom]
  (remove (fn [[k _]]
            (= k binding-nom))
          env))

(declare subst-term subst-formula)

(defn subst-term
  "Pure term substitution for compile-time use."
  [term env]
  (let [tag (ast/tag-of term)]
    (case tag
      var (or (lookup-binding env (second term)) term)
      par term
      app (apply ast/app-term
                 (second term)
                 (map #(subst-term % env) (nnext term)))
      term)))

(defn subst-formula
  "Pure formula substitution for compile-time use."
  [formula env]
  (let [tag (ast/tag-of formula)]
    (case tag
      true formula
      false formula
      pos (ast/pos-lit (subst-term (second formula) env))
      neg (ast/neg-lit (subst-term (second formula) env))
      eq (ast/eq-lit (subst-term (second formula) env)
                     (subst-term (nth formula 2) env))
      neq (ast/neq-lit (subst-term (second formula) env)
                       (subst-term (nth formula 2) env))
      and (ast/and-form (subst-formula (second formula) env)
                        (subst-formula (nth formula 2) env))
      or (ast/or-form (subst-formula (second formula) env)
                      (subst-formula (nth formula 2) env))
      not (ast/not-form (subst-formula (second formula) env))
      implies (ast/implies-form (subst-formula (second formula) env)
                                (subst-formula (nth formula 2) env))
      forall (let [tied (second formula)
                   narrowed-env (remove-binding env (:binding-nom tied))]
               (ast/forall-form (:binding-nom tied)
                                (subst-formula (:body tied) narrowed-env)))
      once-forall (let [tied (second formula)
                        narrowed-env (remove-binding env (:binding-nom tied))]
                    (ast/once-forall-form (:binding-nom tied)
                                          (subst-formula (:body tied) narrowed-env)))
      exists (let [tied (second formula)
                   narrowed-env (remove-binding env (:binding-nom tied))]
               (ast/exists-form (:binding-nom tied)
                                (subst-formula (:body tied) narrowed-env)))
      formula)))

;; -----------------------------------------------------------------------------
;; Relational helpers
;; -----------------------------------------------------------------------------

(defn lookupo
  "Relational environment lookup."
  [binding-nom env value]
  (fresh [rest]
    (conde
      [(== (lcons [binding-nom value] rest) env)]
      [(fresh [pair]
         (== (lcons pair rest) env)
         (lookupo binding-nom rest value))])))

(defn unboundo
  "Succeed when `binding-nom` is absent from the environment."
  [binding-nom env]
  (conde
    [(== '() env)]
    [(fresh [other value rest]
       (== (lcons [other value] rest) env)
       (!= other binding-nom)
       (unboundo binding-nom rest))]))

(defn remove-bindo
  "Relationally remove all environment bindings for one nom."
  [binding-nom env out]
  (conde
    [(== '() env) (== '() out)]
    [(fresh [value rest]
       (== (lcons [binding-nom value] rest) env)
       (remove-bindo binding-nom rest out))]
    [(fresh [other value rest out-rest]
       (== (lcons [other value] rest) env)
       (!= other binding-nom)
       (== (lcons [other value] out-rest) out)
       (remove-bindo binding-nom rest out-rest))]))

(declare subst-termo subst-term*o subst-formulao)

(defn subst-termo
  "Relational term substitution.

   Unbound object-language variables pass through unchanged. That keeps the
   relation useful for partially instantiated formulas during later stages."
  [term env out]
  (conde
    [(fresh [binding-nom]
       (== (list 'var binding-nom) term)
       (conde
         [(lookupo binding-nom env out)]
         [(unboundo binding-nom env)
          (== term out)]))]
    [(fresh [binding-nom]
       (== (list 'par binding-nom) term)
       (== term out))]
    [(fresh [head args args-out]
       (== (lcons 'app (lcons head args)) term)
       (== (lcons 'app (lcons head args-out)) out)
       (subst-term*o args env args-out))]))

(defn subst-term*o
  "Relational substitution over argument lists."
  [terms env out]
  (conde
    [(== '() terms) (== '() out)]
    [(fresh [head tail head-out tail-out]
       (== (lcons head tail) terms)
       (== (lcons head-out tail-out) out)
       (subst-termo head env head-out)
       (subst-term*o tail env tail-out))]))

(defn subst-formulao
  "Relational formula substitution with binder-aware environment shadowing."
  [formula env out]
  (conde
    [(== (list 'true) formula)
     (== (list 'true) out)]
    [(== (list 'false) formula)
     (== (list 'false) out)]
    [(fresh [atom atom-out]
       (== (list 'pos atom) formula)
       (== (list 'pos atom-out) out)
       (subst-termo atom env atom-out))]
    [(fresh [atom atom-out]
       (== (list 'neg atom) formula)
       (== (list 'neg atom-out) out)
       (subst-termo atom env atom-out))]
    [(fresh [left right left-out right-out]
       (== (list 'eq left right) formula)
       (== (list 'eq left-out right-out) out)
       (subst-termo left env left-out)
       (subst-termo right env right-out))]
    [(fresh [left right left-out right-out]
       (== (list 'neq left right) formula)
       (== (list 'neq left-out right-out) out)
       (subst-termo left env left-out)
       (subst-termo right env right-out))]
    [(fresh [left right left-out right-out]
       (== (list 'and left right) formula)
       (== (list 'and left-out right-out) out)
       (subst-formulao left env left-out)
       (subst-formulao right env right-out))]
    [(fresh [left right left-out right-out]
       (== (list 'or left right) formula)
       (== (list 'or left-out right-out) out)
       (subst-formulao left env left-out)
       (subst-formulao right env right-out))]
    [(fresh [body body-out]
       (== (list 'not body) formula)
       (== (list 'not body-out) out)
       (subst-formulao body env body-out))]
    [(fresh [left right left-out right-out]
       (== (list 'implies left right) formula)
       (== (list 'implies left-out right-out) out)
       (subst-formulao left env left-out)
       (subst-formulao right env right-out))]
    [(nominal/fresh [binding-nom]
       (fresh [body body-out narrowed-env]
         (== (list 'forall (nominal/tie binding-nom body)) formula)
         (== (list 'forall (nominal/tie binding-nom body-out)) out)
         (remove-bindo binding-nom env narrowed-env)
         (subst-formulao body narrowed-env body-out)))]
    [(nominal/fresh [binding-nom]
       (fresh [body body-out narrowed-env]
         (== (list 'once-forall (nominal/tie binding-nom body)) formula)
         (== (list 'once-forall (nominal/tie binding-nom body-out)) out)
         (remove-bindo binding-nom env narrowed-env)
         (subst-formulao body narrowed-env body-out)))]
    [(nominal/fresh [binding-nom]
       (fresh [body body-out narrowed-env]
         (== (list 'exists (nominal/tie binding-nom body)) formula)
         (== (list 'exists (nominal/tie binding-nom body-out)) out)
         (remove-bindo binding-nom env narrowed-env)
         (subst-formulao body narrowed-env body-out)))]))
