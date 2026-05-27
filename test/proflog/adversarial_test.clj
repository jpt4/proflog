(ns proflog.adversarial-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]))

(def adversarial-language
  (language/language
    {:constants ['zero]
     :relations {'p 1
                 'r 1}}))

(defn neq-zero-program
  []
  (ast/nom x
    (language/compile-program
      adversarial-language
      [(ast/clause 'r [x]
                   (ast/neq-lit (ast/var-term x)
                                (ast/app-term 'zero)))])))

(defn eq-zero-program
  []
  (ast/nom x
    (language/compile-program
      adversarial-language
      [(ast/clause 'p [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))])))

(deftest positive-calls-close-even-when-equality-arrives-late
  (testing "saved positive calls can still close once a later equality unlocks the contradictory body"
    (ast/nom x
      (let [program (neq-zero-program)
            equality-first
            (ast/and-form
              (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
              (ast/pos-lit (ast/app-term 'r (ast/var-term x))))
            call-first
            (ast/and-form
              (ast/pos-lit (ast/app-term 'r (ast/var-term x)))
              (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))]
        (is (seq (kernel/prove-program program equality-first 1)))
        (is (seq (kernel/prove-program program call-first 1)))))))

(deftest negative-calls-close-even-when-equality-arrives-late
  (testing "saved negative calls can still close once a later equality makes the clause body valid"
    (ast/nom x
      (let [program (eq-zero-program)
            equality-first
            (ast/and-form
              (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
              (ast/neg-lit (ast/app-term 'p (ast/var-term x))))
            call-first
            (ast/and-form
              (ast/neg-lit (ast/app-term 'p (ast/var-term x)))
              (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))]
        (is (seq (kernel/prove-program program equality-first 1)))
        (is (seq (kernel/prove-program program call-first 1)))))))
