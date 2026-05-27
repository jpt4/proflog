(ns proflog.relational-equality-fragment-probe
  "Timing probe for ADR-0057 relational equality-fragment parity.

   The production equality-fragment route remains host-backed by default. This
   probe compares that route with the opt-in relational finite driver over the
   ADR-0039 finite verifier row set."
  (:require [proflog.ast :as ast]
            [proflog.finite-transition-systems :as transition]
            [proflog.gv-probe :as gv-probe]
            [proflog.kernel.equality-fragment :as equality-fragment]
            [proflog.normalize :as normalize]
            [proflog.proof :as proof]))

(defn- proposition
  [relation]
  (ast/pos-lit (ast/app-term relation)))

(defn- timed
  [f]
  (let [started (System/nanoTime)
        value (f)]
    {:elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)
     :value value}))

(defn- proof-formula
  [query expected]
  (case expected
    :succeeds (normalize/negate-formula query)
    :fails query))

(defn- proof-summary
  [proof]
  {:found? (boolean proof)
   :profiled? (proof/contains-step? proof 'profiled)
   :equality-fragment? (proof/contains-step? proof 'equality-fragment)
   :relational-equality-fragment? (proof/contains-step?
                                    proof
                                    'relational-equality-fragment)})

(defn- row-result
  [{:keys [id program query expected fuel]}]
  (let [formula (proof-formula query expected)
        host (timed #(first
                       (equality-fragment/prove-program-host
                         program
                         formula
                         1
                         fuel)))
        relational (timed #(first
                             (equality-fragment/prove-program-relational
                               program
                               query
                               1
                               fuel
                               expected)))]
    {:id id
     :expected expected
     :fuel fuel
     :host (merge (select-keys host [:elapsed-ms])
                  (proof-summary (:value host)))
     :relational (merge (select-keys relational [:elapsed-ms])
                        (proof-summary (:value relational)))}))

(defn rows
  []
  (let [gv-row (fn [scenario expected]
                 (let [{:keys [program relation]} (gv-probe/scenario-config
                                                     scenario)]
                   {:id scenario
                    :program program
                    :query (proposition relation)
                    :expected expected
                    :fuel 16}))
        transition-row (fn [id spec relation expected]
                         {:id id
                          :program (transition/transition-program spec)
                          :query (transition/proposition relation)
                          :expected expected
                          :fuel 16})]
    [(gv-row "z1-full-assoc-truth" :succeeds)
     (gv-row "z2-precomputed-assoc-truth" :succeeds)
     (gv-row "z2-full-assoc-truth" :succeeds)
     (gv-row "non-group-precomputed-assoc" :fails)
     (gv-row "non-group-full-assoc" :fails)
     (transition-row "complete-delta-total"
                     transition/complete-deterministic-spec
                     'delta-total
                     :succeeds)
     (transition-row "complete-delta-deterministic"
                     transition/complete-deterministic-spec
                     'delta-deterministic
                     :succeeds)
     (transition-row "incomplete-delta-total"
                     transition/incomplete-spec
                     'delta-total
                     :fails)
     (transition-row "nondeterministic-delta-deterministic"
                     transition/nondeterministic-spec
                     'delta-deterministic
                     :fails)]))

(defn run-probe
  []
  (mapv row-result (rows)))

(defn -main
  [& _]
  (doseq [result (run-probe)]
    (prn result)))
