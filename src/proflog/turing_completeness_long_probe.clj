(ns proflog.turing-completeness-long-probe
  "Long-running ADR-0044 runtime-boundary probes.

   These commands are intentionally not tests. They reproduce TC examples that
   are too slow or too search-sensitive for the promoted opt-in regression
   suite, so their output can be recorded under explicit shell time controls."
  (:require [proflog.ast :as ast]
            [proflog.frontend :as pf]
            [proflog.query :as query]
            [proflog.turing-completeness :as tc])
  (:gen-class))

(defn- elapsed-ms
  [start-ns]
  (/ (double (- (System/nanoTime) start-ns)) 1000000.0))

(defn- summarize-proofs
  [proofs]
  {:result (if (seq proofs) :succeeds :no-proof)
   :proof-count (count proofs)
   :first-proof-tag (when-let [proof (first proofs)]
                      (when (sequential? proof)
                        (first proof)))})

(defn- summarize-record
  [record]
  {:bindings (:bindings record)
   :residuals (:residuals record)
   :proof-count (count (:proofs record))})

(defn- summarize-records
  [records]
  {:result (if (seq records) :answers :no-answers)
   :answer-count (count records)
   :answers (mapv summarize-record records)})

(defn recursive-transfer-3-steps
  "Ask recursive `halts-in-steps/3` to close a three-step transfer."
  []
  (summarize-proofs
    (query/query-succeeds
      (tc/transfer-program)
      (ast/pos-lit (ast/app-term 'halts-in-steps
                                 (tc/numeral 3)
                                 (tc/config 'l0 1 0)
                                 (tc/config 'halt-label 0 1)))
      1
      96)))

(defn recursive-transfer-5-steps
  "Ask recursive `halts-in-steps/3` to close a five-step transfer."
  []
  (summarize-proofs
    (query/query-succeeds
      (tc/transfer-program)
      (ast/pos-lit (ast/app-term 'halts-in-steps
                                 (tc/numeral 5)
                                 (tc/config 'l0 2 0)
                                 (tc/config 'halt-label 0 2)))
      1
      160)))

(defn direct-ground-three-step-trace
  "Ask direct kernel proof search to close the concrete three-step transfer trace."
  []
  (let [program (tc/transfer-program)
        start (tc/config 'l0 1 0)
        final (tc/config 'halt-label 0 1)]
    (ast/nom middle0 middle1
      (summarize-proofs
        (query/query-succeeds
          program
          (ast/and-form
            (ast/pos-lit (ast/app-term 'step start (ast/var-term middle0)))
            (ast/and-form
              (ast/pos-lit (ast/app-term 'step
                                         (ast/var-term middle0)
                                         (ast/var-term middle1)))
              (ast/and-form
                (ast/pos-lit (ast/app-term 'step
                                           (ast/var-term middle1)
                                           final))
                (ast/pos-lit (ast/app-term 'halt-config final)))))
          1
          96)))))

(defn open-predecessor-step
  "Ask answer search to synthesize the predecessor of a one-step transfer state."
  []
  (summarize-records
    (pf/run (tc/transfer-program) [before]
      (step before (cfg l0 (s zero) (s (s zero))))
      {:fuel 48
       :call-depth 3
       :proof-limit 4
       :max-raw-proof-limit 16})))

(def probes
  {"recursive-transfer-3-steps" recursive-transfer-3-steps
   "recursive-transfer-5-steps" recursive-transfer-5-steps
   "direct-ground-three-step-trace" direct-ground-three-step-trace
   "open-predecessor-step" open-predecessor-step})

(defn probe-ids
  "Return the stable probe identifiers accepted by `-main`."
  []
  (sort (keys probes)))

(defn run-probe
  [probe-id]
  (if-let [probe (get probes probe-id)]
    (let [start-ns (System/nanoTime)
          result (probe)]
      (assoc result
             :probe probe-id
             :elapsed-ms (elapsed-ms start-ns)))
    {:probe probe-id
     :result :unknown-probe
     :known-probes (probe-ids)}))

(defn -main
  [& [probe-id]]
  (let [probe-id (or probe-id "recursive-transfer-3-steps")]
    (prn {:starting-probe probe-id})
    (flush)
    (prn (run-probe probe-id))
    (flush)))
