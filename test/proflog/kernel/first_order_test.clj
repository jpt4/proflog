(ns proflog.kernel.first-order-test
  (:require [clojure.core.logic :refer [lcons run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel.first-order :as first-order]
            [proflog.pelletier-test :as pelletier]
            [proflog.proof :as proof]))

(deftest first-order-component-closes-existing-quantified-passing-pelletier-problems
  (testing "existing quantified passers stay within the generic first-order path"
    (doseq [id [18 21]]
      (let [branch (pelletier/theorem-branch
                     ((:builder (pelletier/problem-by-id id))))]
        (is (seq (first-order/prove branch 1))
            (str "Pelletier Problem " id
                 " should close through the first-order component"))))))

(deftest first-order-component-closes-promoted-pelletier-tranche
  (testing "the first promoted too-slow problems close generically"
    (doseq [id [25 30 31 36 41]]
      (let [branch (pelletier/theorem-branch
                     ((:builder (pelletier/problem-by-id id))))]
        (is (seq (first-order/prove branch 1))
            (str "Pelletier Problem " id
                 " should close through the first-order component"))))))

(deftest first-order-component-closes-remaining-pelletier-problems
  (testing "ADR-0025 closes the remaining Pelletier formulas generically"
    (doseq [id [24 26 27 28 29 32 34 37 38 43 44 45 46]]
      (let [branch (pelletier/theorem-branch
                     ((:builder (pelletier/problem-by-id id))))]
        (is (seq (first-order/prove branch 1))
            (str "Pelletier Problem " id
                 " should close through the lean first-order policy"))))))

(deftest open-equality-free-first-order-branches-do-not-close
  (testing "a branch with only a universal positive atom remains open"
    (ast/nom x
      (is (empty?
            (first-order/prove
              (ast/forall-form
                x
                (ast/pos-lit (ast/app-term 'p (ast/var-term x))))
              1
              3))))))

(deftest first-order-relation-fills-partial-proof-skeletons
  (testing "direct relational callers can constrain the first-order proof shape"
    (ast/nom x
      (let [tails (run 1 [tail]
                    (first-order/proveo
                      (ast/forall-form
                        x
                        (ast/and-form
                          (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                          (ast/neg-lit (ast/app-term 'p (ast/var-term x)))))
                      '()
                      '()
                      '()
                      (lcons 'univ tail)))]
        (is (= 1 (count tails)))
        (is (proof/contains-step? (first tails) 'conj))
        (is (proof/contains-step? (first tails) 'close))))))
