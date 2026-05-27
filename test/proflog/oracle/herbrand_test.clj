(ns proflog.oracle.herbrand-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]))

(def small-ground-terms
  [(ast/app-term 'zero)
   (ast/app-term 'one)
   (ast/app-term 'succ (ast/app-term 'zero))
   (ast/app-term 'succ (ast/app-term 'one))])

(defn free-constructor=
  "Direct structural equality over the greenfield term AST."
  [left right]
  (= left right))

(deftest bounded-ground-equality-agrees-with-a-direct-structural-oracle
  (testing "ground eq/neq behavior matches direct structural identity on a tiny signature"
    (doseq [left small-ground-terms
            right small-ground-terms]
      (let [eq-provable? (seq (kernel/prove (ast/eq-lit left right) 1))
            neq-provable? (seq (kernel/prove (ast/neq-lit left right) 1))
            same? (free-constructor= left right)]
        (is (= (not same?) (boolean eq-provable?))
            (str "eq oracle mismatch for " left " and " right))
        (is (= same? (boolean neq-provable?))
            (str "neq oracle mismatch for " left " and " right))))))
