(ns proflog.kernel.propositional-test
  (:require [clojure.core.logic :refer [run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel.propositional :as propositional]
            [proflog.pelletier-test :as pelletier]))

(deftest propositional-component-closes-pelletier-problem-12
  (testing "Problem 12 closes through the generic propositional tableau"
    (is (seq
          (propositional/prove
            (pelletier/theorem-branch (pelletier/problem-12))
            1)))))

(deftest existing-propositional-pelletier-passers-still-close
  (testing "the propositional component handles ordinary propositional passers"
    (doseq [id [1 6 11 13 14 15 16]
            :let [{:keys [builder]} (pelletier/problem-by-id id)]]
      (is (seq (propositional/prove
                 (pelletier/theorem-branch (builder))
                 1))
          (str "Pelletier Problem " id " should close propositionally")))))

(deftest open-propositional-branches-do-not-close
  (testing "an isolated literal is satisfiable and should stay open"
    (is (empty?
          (propositional/prove
            (ast/pos-lit (ast/app-term 'p))
            1)))))

(deftest bounded-propositional-fuel-matches-structural-progress
  (testing "fuel is charged for alpha expansion and literal saving"
    (let [formula (ast/and-form
                    (ast/pos-lit (ast/app-term 'p))
                    (ast/neg-lit (ast/app-term 'p)))]
      (is (empty? (propositional/prove formula 1 0)))
      (is (empty? (propositional/prove formula 1 1)))
      (is (seq (propositional/prove formula 1 2))))))

(deftest propositional-relation-fills-partial-proof-skeletons
  (testing "direct relational callers can run the propositional layer with a partial proof"
    (is (= ['(savefml (close))]
           (run 1 [tail]
             (propositional/proveo
               (ast/and-form
                 (ast/pos-lit (ast/app-term 'p))
                 (ast/neg-lit (ast/app-term 'p)))
               '()
               '()
               (list 'conj tail)))))))

(deftest propositional-relation-synthesizes-a-missing-complement
  (testing "direct relational callers can synthesize part of a propositional branch"
    (is (= [(ast/app-term 'p)]
           (run 1 [atom]
             (propositional/proveo
               (ast/and-form
                 (ast/pos-lit (ast/app-term 'p))
                 (ast/neg-lit atom))
               '()
               '()
               '(conj (savefml (close)))))))))
