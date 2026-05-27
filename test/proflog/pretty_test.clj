(ns proflog.pretty-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.pretty :as pretty]))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(deftest peano->int-decodes-ground-numerals
  (testing "ground zero/s numerals collapse to ordinary decimals"
    (is (= 0 (pretty/peano->int (numeral 0))))
    (is (= 1 (pretty/peano->int (numeral 1))))
    (is (= 2 (pretty/peano->int (numeral 2))))
    (is (= 5 (pretty/peano->int (numeral 5))))))

(deftest peano->int-rejects-symbolic-and-non-peano-terms
  (testing "only fully ground Peano numerals decode cleanly"
    (ast/nom x
      (is (nil? (pretty/peano->int (ast/app-term 's (ast/var-term x)))))
      (is (nil? (pretty/peano->int (ast/app-term 'pair (numeral 1) (numeral 0))))))))

(deftest pretty-term-collapses-ground-peano-subterms
  (testing "non-Peano applications still render their Peano children as decimals"
    (ast/nom x
      (is (= (list 'move 2 (ast/var-term x))
             (pretty/pretty-term
               (ast/app-term 'move (numeral 2) (ast/var-term x))))))))

(deftest pretty-answer-renders-bindings-and-residuals
  (testing "answer records render ground numerals as decimals while preserving symbolic structure"
    (ast/nom x y
      (let [answer {:bindings [[x (numeral 2)]]
                    :residuals [(ast/pos-lit (ast/app-term 'win (numeral 1)))
                                (ast/neq-lit (numeral 2) (ast/var-term y))]
                    :proofs ['dummy-proof]}]
        (is (= {:bindings [[x 2]]
                :residuals [(list 'pos (list 'win 1))
                            (list 'neq 2 (ast/var-term y))]
                :proofs ['dummy-proof]}
               (pretty/pretty-answer answer)))))))
