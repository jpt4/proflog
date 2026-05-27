(ns proflog.program
  "Compiled-program helpers for Fitting's Procedure Call Rule.

   The proof kernels should not know how source clauses were grouped, validated,
   or optimized. They only need the operation described by the procedure-call
   rule: given a ground atom over a defined relation, find the relation's
   compiled clause, bind formal parameters to actual arguments, and prove the
   selected body in a subsidiary tableau.

   This namespace keeps that operation relational. Compiled programs retain
   sequential clause views because core.logic can search them with `membero`;
   the richer map view remains useful to host-side callers and diagnostics.
   Alternative and guarded-alternative lookups expose extra IR only to the
   layers that need it, without changing the ordinary call contract."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde fresh lcons membero]]))

(defn- compiled-program-viewo
  "Expose the standard compiled-program views, allowing registry metadata.

   `proflog.language/compile-program` returns the core map shape used by the
   procedure-call rules. Profiles may attach finite metadata at the top level
   of that map, but SJAS now keeps that payload behind a registry so the
   ordinary Procedure Call Rule still sees the same clause views without stale
   proof-target or fact-table slots. Keeping these accepted shapes explicit
   preserves the relational lookup contract while allowing SJAS-style profile
   annotations."
  [program language clauses clause-list alternative-clause-list guarded-clause-list]
  (conde
    [(fresh [registry]
       (== {:language language
            :clauses clauses
            :clause-list clause-list
            :alternative-clause-list alternative-clause-list
            :guarded-clause-list guarded-clause-list
            :sjas/registry registry}
           program))]
    [(== {:language language
          :clauses clauses
          :clause-list clause-list
          :alternative-clause-list alternative-clause-list
          :guarded-clause-list guarded-clause-list}
         program)]))

(defn- compiled-program-alternative-viewo
  "Expose the standard alternative view, including legacy pre-guarded maps."
  [program language clauses clause-list alternative-clause-list]
  (conde
    [(fresh [guarded-clause-list]
       (compiled-program-viewo
         program language clauses clause-list alternative-clause-list guarded-clause-list))]
    [(== {:language language
          :clauses clauses
          :clause-list clause-list
          :alternative-clause-list alternative-clause-list}
         program)]))

(defn- compiled-program-clause-viewo
  "Expose the ordinary clause view, including legacy compiled-program maps."
  [program language clauses clause-list]
  (conde
    [(fresh [alternative-clause-list guarded-clause-list]
       (compiled-program-viewo
         program language clauses clause-list alternative-clause-list guarded-clause-list))]
    [(fresh [alternative-clause-list]
       (== {:language language
            :clauses clauses
            :clause-list clause-list
            :alternative-clause-list alternative-clause-list}
           program))]
    [(== {:language language
          :clauses clauses
          :clause-list clause-list}
         program)]))

(defn lookup-clause-with-alternativeso
  "Find the compiled clause for `relation` in `program`.

   Returns the clause parameters, body, precomputed NNF negation, and the
   compiled top-level alternatives. The compiled program keeps a list view
   specifically so this lookup can remain relational inside the kernel."
  [program relation params body negated-body alternatives negated-alternatives]
  (fresh [language clauses clause-list alternative-clause-list]
    (compiled-program-alternative-viewo
      program language clauses clause-list alternative-clause-list)
    (membero {:relation relation
              :params params
              :body body
              :negated-body negated-body
              :alternatives alternatives
              :negated-alternatives negated-alternatives}
             alternative-clause-list)))

(defn lookup-clause-with-guarded-alternativeso
  "Find the compiled clause for `relation` and expose guarded alternative IR."
  [program relation params body negated-body alternatives negated-alternatives guarded-alternatives]
  (fresh [language clauses clause-list alternative-clause-list guarded-clause-list]
    (compiled-program-viewo
      program language clauses clause-list alternative-clause-list guarded-clause-list)
    (membero {:relation relation
              :params params
              :body body
              :negated-body negated-body
              :alternatives alternatives
              :negated-alternatives negated-alternatives
              :guarded-alternatives guarded-alternatives}
             guarded-clause-list)))

(defn lookup-clauseo
  "Find the compiled clause for `relation` in `program`.

   Returns the clause parameters, body, and precomputed NNF negation of the
   body. The compiled program keeps a list view specifically so this lookup can
   remain relational inside the kernel."
  [program relation params body negated-body]
  (fresh [language clauses clause-list]
    (compiled-program-clause-viewo program language clauses clause-list)
    (membero {:relation relation
              :params params
              :body body
              :negated-body negated-body}
             clause-list)))

(defn bind-argso
  "Create an environment mapping formal parameter noms to actual argument terms."
  [params args env]
  (conde
    [(== '() params) (== '() args) (== '() env)]
    [(fresh [param param-rest arg arg-rest env-rest]
       (== (lcons param param-rest) params)
       (== (lcons arg arg-rest) args)
       (== (lcons [param arg] env-rest) env)
       (bind-argso param-rest arg-rest env-rest))]))

(defn call-clauseo
  "Resolve an atomic procedure call against a compiled program."
  [program atom env body negated-body]
  (fresh [relation args params]
    (== (lcons 'app (lcons relation args)) atom)
    (lookup-clauseo program relation params body negated-body)
    (bind-argso params args env)))

(defn call-clause-with-alternativeso
  "Resolve a procedure call and expose compiled top-level body alternatives."
  [program atom env body negated-body alternatives negated-alternatives]
  (fresh [relation args params]
    (== (lcons 'app (lcons relation args)) atom)
    (lookup-clause-with-alternativeso
      program relation params body negated-body alternatives negated-alternatives)
    (bind-argso params args env)))

(defn call-clause-with-guarded-alternativeso
  "Resolve a procedure call and expose guarded alternative IR."
  [program atom env body negated-body alternatives negated-alternatives guarded-alternatives]
  (fresh [relation args params]
    (== (lcons 'app (lcons relation args)) atom)
    (lookup-clause-with-guarded-alternativeso
      program
      relation
      params
      body
      negated-body
      alternatives
      negated-alternatives
      guarded-alternatives)
    (bind-argso params args env)))
