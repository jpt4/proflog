(ns proflog.core-logic-tabling-probe
  "Diagnostic counters for core.logic tabling and tabled reification paths."
  (:gen-class)
  (:require [clojure.core.logic :as logic]
            [clojure.core.logic.protocols :as logic-protocols]
            [clojure.pprint :as pp]
            [proflog.list-kernel-matrix-probe :as matrix]))

(def ^:private counter-keys
  [:answer-cache-add
   :answer-cache-cached?
   :answer-cache-created
   :master
   :suspended-stream-created
   :waiting-stream-check
   :tabled-substitution-created
   :suspended-ready?
   :reify-tabled-inner
   :reify-tabled
   :reuse
   :subunify])

(defn- zero-counts
  []
  (zipmap counter-keys (repeat 0)))

(defn- count-call
  [counts k f args]
  (swap! counts update k inc)
  (apply f args))

(defn with-tabling-counters
  "Run thunk with counters around core.logic tabling protocol entry points."
  [thunk]
  (let [counts (atom (zero-counts))
        original-add logic-protocols/-add
        original-cached? logic-protocols/-cached?
        original-ready? logic-protocols/ready?
        original-reify-tabled-inner logic-protocols/-reify-tabled
        original-reify-tabled logic-protocols/reify-tabled
        original-reuse logic-protocols/reuse
        original-subunify logic-protocols/subunify
        original-answer-cache logic/answer-cache
        original-master logic/master
        original-make-suspended-stream logic/make-suspended-stream
        original-waiting-stream-check logic/waiting-stream-check
        original-tabled-s logic/tabled-s]
    (with-redefs [logic/answer-cache
                  (fn [& args]
                    (count-call counts :answer-cache-created original-answer-cache args))
                  logic/master
                  (fn [& args]
                    (count-call counts :master original-master args))
                  logic/make-suspended-stream
                  (fn [& args]
                    (count-call counts :suspended-stream-created original-make-suspended-stream args))
                  logic/waiting-stream-check
                  (fn [& args]
                    (count-call counts :waiting-stream-check original-waiting-stream-check args))
                  logic/tabled-s
                  (fn [& args]
                    (count-call counts :tabled-substitution-created original-tabled-s args))
                  logic-protocols/-add
                  (fn [& args]
                    (count-call counts :answer-cache-add original-add args))
                  logic-protocols/-cached?
                  (fn [& args]
                    (count-call counts :answer-cache-cached? original-cached? args))
                  logic-protocols/ready?
                  (fn [& args]
                    (count-call counts :suspended-ready? original-ready? args))
                  logic-protocols/-reify-tabled
                  (fn [& args]
                    (count-call counts :reify-tabled-inner original-reify-tabled-inner args))
                  logic-protocols/reify-tabled
                  (fn [& args]
                    (count-call counts :reify-tabled original-reify-tabled args))
                  logic-protocols/reuse
                  (fn [& args]
                    (count-call counts :reuse original-reuse args))
                  logic-protocols/subunify
                  (fn [& args]
                    (count-call counts :subunify original-subunify args))]
      {:result (thunk)
       :tabling-events @counts})))

(defn- exercised?
  [counts]
  (boolean
    (some (fn [[k v]]
            (and (not= k :tabled-substitution-created)
                 (pos? v)))
          counts)))

(defn tabled-smoke
  "Exercise core.logic tabled answer caching and reuse once."
  []
  (let [sameo (logic/tabled [x]
                (logic/== x :ok))]
    (doall
      (logic/run* [q]
        (sameo :ok)
        (sameo :ok)
        (logic/== q :done)))))

(defn run-tabled-smoke
  []
  (let [{:keys [result tabling-events]}
        (with-tabling-counters tabled-smoke)]
    {:id :core-logic-tabled-smoke
     :answers result
     :tabling-events tabling-events
     :active-tabled-answer-path? (exercised? tabling-events)}))

(defn run-matrix-case
  [case-id]
  (let [{:keys [result tabling-events]}
        (with-tabling-counters
          #(matrix/run-case (keyword case-id)))]
    (assoc result
           :tabling-events tabling-events
           :active-tabled-answer-path? (exercised? tabling-events))))

(defn -main
  [& case-ids]
  (if (seq case-ids)
    (doseq [case-id case-ids]
      (pp/pprint (run-matrix-case case-id)))
    (pp/pprint (run-tabled-smoke)))
  (shutdown-agents))
