(ns proflog.core-logic-count-probe-test
  (:require [clojure.test :refer [deftest is]]
            [proflog.core-logic-count-probe :as probe]))

(deftest counted-case-reports-core-logic-categories
  (let [result (probe/run-counted-case :reverse-forward-flat-2
                                       {:metric-limit 5})
        categories (set (map :category (:category-totals result)))]
    (is (= :core-logic-count (:probe result)))
    (is (= :reverse-forward-flat-2 (:case-id result)))
    (is (:target-found? (:case-result result)))
    (is (pos-int? (:instrumented-var-count result)))
    (is (contains? categories :unification))
    (is (contains? categories :streams))
    (is (seq (:top-metrics-by-calls result)))))
