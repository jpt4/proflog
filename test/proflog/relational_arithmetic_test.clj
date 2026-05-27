(ns proflog.relational-arithmetic-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [== fresh run run*]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.relational-arithmetic :as arith]
            [proflog.relational-fuel-probe :as relational-fuel]))

(deftest build-num-uses-little-endian-binary-numerals
  (is (= '() (arith/build-num 0)))
  (is (= '(1) (arith/build-num 1)))
  (is (= '(0 1) (arith/build-num 2)))
  (is (= '(1 1) (arith/build-num 3)))
  (is (= '(0 0 1) (arith/build-num 4))))

(deftest translated-plus-and-minus-run-as-core-logic-relations
  (testing "addition works forward"
    (is (= (list (arith/build-num 5))
           (run 1 [sum]
             (arith/pluso (arith/build-num 2)
                          (arith/build-num 3)
                          sum)))))
  (testing "addition can synthesize a missing predecessor"
    (is (= (list (arith/build-num 2))
           (run 1 [pred]
             (arith/pluso pred
                          (arith/build-num 1)
                          (arith/build-num 3))))))
  (testing "subtraction is just addition in reverse"
    (is (= (list (arith/build-num 3))
           (run 1 [diff]
             (arith/minuso (arith/build-num 5)
                           (arith/build-num 2)
                           diff))))))

(deftest translated-multiplication-and-comparison-cover-small-ground-cases
  (testing "multiplication works forward on small numerals"
    (is (= (list (arith/build-num 6))
           (run 1 [product]
             (arith/*o (arith/build-num 2)
                       (arith/build-num 3)
                       product)))))
  (testing "less-than distinguishes small numerals"
    (is (= '(:ok)
           (run 1 [q]
             (arith/<o (arith/build-num 2) (arith/build-num 3))
             (== q :ok))))
    (is (= '()
           (run 1 [q]
             (arith/<o (arith/build-num 3) (arith/build-num 2))
             (== q :ok))))))

(deftest relational-fuel-probe-replaces-fd-step-over-bit-list-fuel
  (testing "finite predecessor synthesis uses pluso rather than finite-domain constraints"
    (is (= (list (arith/build-num 1))
           (run 1 [fuel]
             (relational-fuel/step-fuelo fuel (arith/build-num 0))))))
  (testing "finite successor synthesis uses pluso rather than finite-domain constraints"
    (is (= (list (arith/build-num 0))
           (run 1 [next-fuel]
             (relational-fuel/step-fuelo (arith/build-num 1) next-fuel)))))
  (testing "Proflog's unbounded sentinel remains a separate nil case"
    (is (= '(nil)
           (run 1 [fuel]
             (relational-fuel/step-fuelo fuel nil))))))

(deftest simple-kernel-proof-can-run-with-relational-bit-list-fuel-step
  (testing "a direct kernel caller can swap in the arithmetic fuel relation when finite fuel is a bit-list numeral"
    (let [formula (ast/and-form
                    (ast/pos-lit (ast/app-term 'p))
                    (ast/neg-lit (ast/app-term 'p)))]
      (with-redefs [support/step-fuelo relational-fuel/step-fuelo]
        (is (seq
              (run 1 [proof]
                (kernel/proveo formula
                               '()
                               '()
                               '()
                               (arith/build-num 2)
                               proof))))))))

(deftest integer-fuel-api-is-not-drop-in-compatible-with-bit-list-fuel-step
  (testing "existing production callers pass host integers, not miniKanren bit-list numerals"
    (with-redefs [support/step-fuelo relational-fuel/step-fuelo]
      (is (= '()
             (run 1 [next-fuel]
               (support/step-fuelo 1 next-fuel)))))))
