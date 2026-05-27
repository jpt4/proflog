(ns proflog.core-logic-disequality-probe-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.core-logic-disequality-probe :as probe]))

(defn- calls
  [result id]
  (probe/metric-calls result id))

(defn- result-count
  [result]
  (get-in result [:result :count]))

(deftest open-core-disequality-residual-exercises-constraint-path
  (let [result (probe/run-case :core-open-residual)]
    (is (= :core-open-residual (:case-id result)))
    (is (= 1 (result-count result)))
    (is (pos? (calls result 'clojure.core.logic/!=)))
    (is (pos? (calls result 'clojure.core.logic/disunify)))
    (is (pos? (calls result 'clojure.core.logic/!=c)))
    (is (pos? (calls result 'clojure.core.logic/walk*)))
    (is (some? (get-in result [:derived :walk*-calls-per-!=c-call])))))

(deftest delayed-disequality-can-still-fail-after-binding
  (let [result (probe/run-case :core-violated-after-delay)]
    (is (= :core-violated-after-delay (:case-id result)))
    (is (= 0 (result-count result)))
    (is (pos? (calls result 'clojure.core.logic/!=)))
    (is (pos? (calls result 'clojure.core.logic/run-constraint)))))

(deftest open-disequality-chain-exposes-walk-and-reification-pressure
  (let [result (probe/run-case :core-open-chain-small)]
    (is (= 1 (result-count result)))
    (is (>= (calls result 'clojure.core.logic/!=) 8))
    (is (pos? (calls result 'clojure.core.logic/walk*)))
    (is (pos? (get-in result [:derived :reify-calls])))))

(deftest absento-overlay-uses-deep-and-disequality-constraints
  (let [result (probe/run-case :absento-open-tail)]
    (is (= 1 (result-count result)))
    (is (pos? (calls result 'clojure.core.logic/fixc)))
    (is (pos? (calls result 'clojure.core.logic/!=)))
    (is (pos? (calls result 'clojure.core.logic/disunify)))))

(deftest proflog-disequality-cases-run-under-the-same-counters
  (testing "a stored Proflog neq can close after a later equality"
    (let [result (probe/run-case :proflog-saved-disequality-close)]
      (is (= {:proof-count 1
              :closed? true
              :kind :map}
             (:result result)))))
  (testing "a same-head symbolic Proflog neq remains open"
    (let [result (probe/run-case :proflog-open-disequality)]
      (is (= {:proof-count 0
              :closed? false
              :kind :map}
             (:result result))))))
