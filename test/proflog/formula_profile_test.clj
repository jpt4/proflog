(ns proflog.formula-profile-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.formula-profile :as profile]
            [proflog.pelletier-test :as pelletier]))

(deftest pelletier-problem-12-is-pure-propositional
  (testing "Problem 12 is classified by shape, not by problem id"
    (let [branch (pelletier/theorem-branch (pelletier/problem-12))]
      (is (profile/pure-propositional? branch))
      (is (profile/equality-free-first-order? branch))
      (is (= :pure-propositional
             (profile/profile branch))))))

(deftest quantified-pelletier-formulas-are-not-propositional
  (testing "quantifiers keep a formula out of the propositional layer"
    (let [branch (pelletier/theorem-branch (pelletier/problem-18))]
      (is (not (profile/pure-propositional? branch)))
      (is (profile/equality-free-first-order? branch))
      (is (= :equality-free-first-order
             (profile/profile branch))))))

(deftest remaining-too-slow-pelletier-formulas-are-equality-free-first-order
  (testing "the ADR-0024 target tranche is structurally in scope for the first-order layer"
    (doseq [id [24 25 26 27 28 29 30 31 32 34 36 37 38 41 43 44 45 46]]
      (let [branch (pelletier/theorem-branch ((:builder (pelletier/problem-by-id id))))]
        (is (not (profile/pure-propositional? branch))
            (str "Pelletier Problem " id " should not be propositional"))
        (is (profile/equality-free-first-order? branch)
            (str "Pelletier Problem " id " should be equality-free first-order"))
        (is (= :equality-free-first-order
               (profile/profile branch))
            (str "Pelletier Problem " id " should classify into the first-order layer"))))))

(deftest equality-and-disequality-formulas-are-equality-bearing
  (testing "eq and neq are both full-kernel features"
    (ast/nom x
      (let [formula (ast/and-form
                      (ast/eq-lit (ast/var-term x) (ast/app-term 'a))
                      (ast/neq-lit (ast/var-term x) (ast/app-term 'b)))]
        (is (profile/equality-bearing? formula))
        (is (not (profile/equality-free-first-order? formula)))
        (is (= :equality-bearing
               (profile/profile formula)))))))
