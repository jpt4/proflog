(ns proflog.kernel.constructor-recursive-profile
  "Promoted constructor-recursive answer profile.

   This profile starts from the same exported-record shape used by the public
   answer overlay and closes its single negative procedure-call frontier through
   the ADR-0035 structural residual continuation engine. The component is
   generic over compiled guarded Proflog IR: it does not inspect relation names,
   constructor names, or test scenarios."
  (:require [proflog.answer-overlay :as answer-overlay]
            [proflog.ast :as ast]))

(def default-fuel 64)
(def default-limit 10)

(defn- initial-record
  [query answer-vars]
  (when-not (= 'pos (ast/tag-of query))
    (throw (ex-info "Constructor-recursive profile expects a positive atom"
                    {:query query})))
  (let [atom (second query)]
    {:bindings (mapv (fn [answer-var]
                       [answer-var (ast/var-term answer-var)])
                     answer-vars)
     :residuals [(ast/neg-lit atom)]
     :proofs [(list 'constructor-recursive-profile-entry atom)]}))

(defn- profiled-record
  [record]
  (assoc record
         :proofs [(list 'profiled
                        'constructor-recursive
                        (cons 'constructor-recursive-profile
                              (:proofs record)))]))

(defn query-records
  "Return promoted constructor-recursive answer records for a positive query.

   The query is represented as ordinary compiled Proflog syntax. Open answer
   variables are supplied explicitly in `answer-vars`; the same guarded-IR
   continuation machinery handles forward, reverse, and partial-synthesis modes."
  ([program query answer-vars]
   (query-records program query answer-vars {}))
  ([program query answer-vars {:keys [fuel limit]
                               :or {fuel default-fuel
                                    limit default-limit}}]
   (mapv profiled-record
         (answer-overlay/continue-exported-structural-records
           program
           (initial-record query answer-vars)
           fuel
           limit))))

(defn query-succeeds?
  "True when the promoted profile can witness a positive query."
  ([program query]
   (query-succeeds? program query {}))
  ([program query opts]
   (boolean (seq (query-records program query [] (assoc opts :limit 1))))))
