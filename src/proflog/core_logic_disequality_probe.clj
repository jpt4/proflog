(ns proflog.core-logic-disequality-probe
  "ADR-37 measurement harness for core.logic disequality maintenance.

   This namespace uses process-local `with-redefs` counters around core.logic
   Vars. It does not patch core.logic or production Proflog relations."
  (:gen-class)
  (:require [clojure.core.logic :as logic]
            [clojure.pprint :as pp]
            [proflog.ast :as ast]
            [proflog.core-logic-host :as host]
            [proflog.kernel :as kernel]
            [proflog.minikanren-constraints :as mkc])
  (:import [java.util.concurrent.atomic LongAdder]))

(def ^:private metric-specs
  [{:id 'clojure.core.logic/!=
    :category :disequality}
   {:id 'clojure.core.logic/!=c
    :category :disequality}
   {:id 'clojure.core.logic/disunify
    :category :disequality}
   {:id 'clojure.core.logic/recover-vars
    :category :disequality}
   {:id 'clojure.core.logic/recover-vars-from-term
    :category :disequality}

   {:id 'clojure.core.logic/walk*
    :category :walk-reification}
   {:id 'clojure.core.logic/-reify
    :category :walk-reification}
   {:id 'clojure.core.logic/-reify*
    :category :walk-reification}
   {:id 'clojure.core.logic/reifyg
    :category :walk-reification}
   {:id 'clojure.core.logic/force-ans
    :category :walk-reification}

   {:id 'clojure.core.logic/unify
    :category :unification}
   {:id 'clojure.core.logic/ext
    :category :unification}
   {:id 'clojure.core.logic/occurs-check
    :category :unification}

   {:id 'clojure.core.logic/cgoal
    :category :constraint-scheduler}
   {:id 'clojure.core.logic/run-constraint
    :category :constraint-scheduler}
   {:id 'clojure.core.logic/run-constraints
    :category :constraint-scheduler}
   {:id 'clojure.core.logic/run-constraints*
    :category :constraint-scheduler}

   {:id 'clojure.core.logic/predc
    :category :tree-predicate-constraints}
   {:id 'clojure.core.logic/fixc
    :category :tree-predicate-constraints}
   {:id 'clojure.core.logic/-fixc
    :category :tree-predicate-constraints}
   {:id 'clojure.core.logic/treec
    :category :tree-predicate-constraints}])

(defn- require-namespace
  [sym]
  (require (symbol (namespace sym))))

(defn- resolve-var
  [sym]
  (try
    (require-namespace sym)
    (ns-resolve (symbol (namespace sym)) (symbol (name sym)))
    (catch RuntimeException _
      nil)))

(defn- nanos->millis
  [nanos]
  (/ (double nanos) 1000000.0))

(defn- round3
  [x]
  (/ (Math/round (* 1000.0 (double x))) 1000.0))

(defn- truncate
  [text max-length]
  (if (<= (count text) max-length)
    text
    (str (subs text 0 max-length) "...")))

(defn- update-metric
  [metric category]
  (or metric {:category category
              :calls (LongAdder.)}))

(defn- metered-fn
  [metrics {:keys [id category]} f]
  (let [metric (get (swap! metrics update id update-metric category) id)
        calls ^LongAdder (:calls metric)]
    (fn [& args]
      (.increment calls)
      (apply f args))))

(defn- metric-redefs
  [metrics specs]
  (reduce (fn [redefs spec]
            (if-let [v (resolve-var (:id spec))]
              (assoc redefs v (metered-fn metrics spec @v))
              redefs))
          {}
          specs))

(defn- unresolved-vars
  [specs]
  (->> specs
       (keep (fn [{:keys [id]}]
               (when-not (resolve-var id)
                 id)))
       vec))

(defn- summarize-metric
  [id {:keys [category calls]}]
  {:id id
   :category category
   :calls (.sum ^LongAdder calls)})

(defn- summarize-category
  [[category metrics]]
  (let [calls (reduce + (map :calls metrics))]
    {:category category
     :calls calls}))

(defn- add-call-share
  [total-calls category]
  (assoc category
         :call-share
         (if (pos? total-calls)
           (round3 (/ (:calls category) total-calls))
           0.0)))

(defn- metrics-by-id
  [raw-metrics]
  (->> raw-metrics
       (map (fn [[id metric]] (summarize-metric id metric)))
       (sort-by :id)
       vec))

(defn- category-totals
  [metrics]
  (let [totals (->> metrics
                    (group-by :category)
                    (map summarize-category)
                    (sort-by (juxt (comp - :calls) :category))
                    vec)
        total-calls (reduce + (map :calls totals))]
    (mapv (partial add-call-share total-calls) totals)))

(defn- top-metrics
  [metrics metric-limit]
  (->> metrics
       (sort-by (juxt (comp - :calls) :id))
       (take metric-limit)
       vec))

(defn metric-calls
  "Return the call count for one metric id in a probe result."
  [result id]
  (or (some (fn [metric]
              (when (= id (:id metric))
                (:calls metric)))
            (:metrics-by-id result))
      0))

(defn- result-summary
  [result]
  (cond
    (sequential? result)
    {:kind :sequential
     :count (count result)
     :sample (some-> (first result) pr-str (truncate 180))}

    (map? result)
    (assoc result :kind :map)

    :else
    {:kind :value
     :value (truncate (pr-str result) 180)}))

(defn- deep-term
  [leaf depth]
  (if (zero? depth)
    leaf
    (list 'node depth (deep-term leaf (dec depth)))))

(defn- disequality-chain-goals
  [x n depth]
  (map (fn [idx]
         (logic/!= (deep-term x depth)
                   (deep-term (symbol (str "v" idx)) depth)))
       (range n)))

(defn- core-open-residual-case
  []
  (doall
    (logic/run 1 [q]
      (logic/fresh [x y]
        (logic/!= (list 'node x) (list 'node y))
        (logic/== q [x y])))))

(defn- core-open-chain-case
  [n depth]
  (doall
    (logic/run 1 [q]
      (logic/fresh [x]
        (logic/and* (disequality-chain-goals x n depth))
        (logic/== q x)))))

(defn- core-violated-after-delay-case
  []
  (doall
    (logic/run* [q]
      (logic/fresh [x]
        (logic/!= x 'a)
        (logic/== x 'a)
        (logic/== q :unreachable)))))

(defn- absento-open-tail-case
  []
  (doall
    (logic/run 1 [q]
      (logic/fresh [tail]
        (mkc/absento 'forbidden (logic/lcons 'safe tail))
        (logic/== q tail)))))

(defn- proflog-saved-disequality-close-case
  []
  (ast/nom x
    (let [proofs (doall
                   (kernel/prove
                     (ast/and-form
                       (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                       (ast/eq-lit (ast/var-term x) (ast/app-term 'a)))
                     1))]
      {:proof-count (count proofs)
       :closed? (boolean (seq proofs))})))

(defn- proflog-open-disequality-case
  []
  (ast/nom x
    (let [proofs (doall
                   (kernel/prove
                     (ast/neq-lit
                       (ast/app-term 'succ (ast/var-term x))
                       (ast/app-term 'succ (ast/app-term 'a)))
                     1))]
      {:proof-count (count proofs)
       :closed? (boolean (seq proofs))})))

(def ^:private cases
  {:core-open-residual
   {:description "one open core.logic disequality residual"
    :run core-open-residual-case}
   :core-open-chain-small
   {:description "eight delayed disequality residuals over depth-four terms"
    :run #(core-open-chain-case 8 4)}
   :core-open-chain-medium
   {:description "thirty-two delayed disequality residuals over depth-five terms"
    :run #(core-open-chain-case 32 5)}
   :core-violated-after-delay
   {:description "one delayed disequality falsified by a later binding"
    :run core-violated-after-delay-case}
   :absento-open-tail
   {:description "Proflog miniKanren overlay absento over an open tail"
    :run absento-open-tail-case}
   :proflog-saved-disequality-close
   {:description "Proflog neq store closes after a later equality"
    :run proflog-saved-disequality-close-case}
   :proflog-open-disequality
   {:description "Proflog same-head symbolic disequality remains open"
    :run proflog-open-disequality-case}})

(defn case-ids
  []
  (sort (keys cases)))

(defn- derived-ratios
  [result]
  (let [walk*-calls (metric-calls result 'clojure.core.logic/walk*)
        !=c-calls (metric-calls result 'clojure.core.logic/!=c)
        run-constraint-calls (metric-calls result 'clojure.core.logic/run-constraint)
        reify-calls (+ (metric-calls result 'clojure.core.logic/-reify)
                       (metric-calls result 'clojure.core.logic/-reify*))]
    {:walk*-calls-per-!=c-call
     (if (pos? !=c-calls)
       (round3 (/ walk*-calls !=c-calls))
       nil)
     :walk*-calls-per-run-constraint-call
     (if (pos? run-constraint-calls)
       (round3 (/ walk*-calls run-constraint-calls))
       nil)
     :reify-calls reify-calls}))

(defn run-case
  "Run one disequality probe case with process-local counters."
  ([case-id]
   (run-case case-id {}))
  ([case-id {:keys [metric-limit]
             :or {metric-limit 12}}]
   (let [case-key (keyword case-id)
         {:keys [description run]} (get cases case-key)]
     (when-not run
       (throw (ex-info "Unknown disequality probe case"
                       {:case-id case-id
                        :known-case-ids (case-ids)})))
     (let [metrics (atom {})
           redefs (metric-redefs metrics metric-specs)
           started (System/nanoTime)
           raw-result (with-redefs-fn redefs run)
           elapsed-ns (- (System/nanoTime) started)
           metrics (metrics-by-id @metrics)
           base-result {:probe :core-logic-disequality
                        :case-id case-key
                        :description description
                        :host (select-keys (host/host-info)
                                           [:source :source-kind :group-id :artifact-id :version :marker])
                        :instrumented-var-count (count redefs)
                        :unresolved-vars (unresolved-vars metric-specs)
                        :elapsed-ms (round3 (nanos->millis elapsed-ns))
                        :result (result-summary raw-result)
                        :category-totals (category-totals metrics)
                        :metrics-by-id metrics
                        :top-metrics-by-calls (top-metrics metrics metric-limit)}]
       (assoc base-result :derived (derived-ratios base-result))))))

(defn run-all
  []
  (mapv run-case (case-ids)))

(defn -main
  [& case-id-texts]
  (if (seq case-id-texts)
    (doseq [case-id case-id-texts]
      (pp/pprint (run-case case-id)))
    (doseq [result (run-all)]
      (pp/pprint result)))
  (shutdown-agents))
