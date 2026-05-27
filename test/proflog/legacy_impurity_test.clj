(ns proflog.legacy-impurity-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run]]
            [clojure.core.logic.nominal :as nominal]
            [clojure.test :refer [deftest is testing]]
            [cljtap.alphaleantap-ep :as legacy]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]))

(def practical-language
  (language/language
    {:constants ['a 'b]
     :relations {'p 1}}))

(defn legacy-par-term?
  [term]
  (and (sequential? term)
       (= 'par (first term))))

(defn legacy-exists-neq-program
  [param witness]
  [['p [param]
    ['exists (nominal/tie witness
              ['neq ['var param] ['var witness]])]]])

(defn greenfield-exists-neq-program
  []
  (ast/nom param witness
    (language/compile-program
      practical-language
      [(ast/clause 'p [param]
                   (ast/exists-form
                     witness
                     (ast/neq-lit (ast/var-term param)
                                  (ast/var-term witness))))])))

(deftest projected-l-groundness-does-not-persist-as-a-constraint
  (testing "legacy project-based L-groundness can be satisfied before a later par binding"
    (is (= [:legacy-admitted-par]
           (run 1 [result]
             (nominal/fresh [p]
               (fresh [term]
                 (legacy/l-ground-termo term)
                 (== term (list 'par p))
                 (== result :legacy-admitted-par)))))))
  (testing "greenfield structural L-groundness rejects the same delta parameter"
    (is (empty?
          (run 1 [result]
            (nominal/fresh [p]
              (kernel/l-ground-termo (ast/par-term p))
              (== result :greenfield-admitted-par)))))))

(deftest projected-l-groundness-can-leak-delta-parameters-as-program-witnesses
  (testing "legacy can synthesize a non-L delta parameter through a procedure call checked before the binding"
    (let [results (run 1 [answer]
                    (nominal/fresh [param witness]
                      (let [program (legacy-exists-neq-program param witness)]
                        (fresh [proof]
                          (legacy/proveo
                            ['pos ['app 'p answer]]
                            '()
                            '()
                            '()
                            program
                            proof
                            4)))))]
      (is (seq results))
      (is (legacy-par-term? (first results)))))
  (testing "greenfield keeps user-level variables distinct from proof variables and blocks the same spurious closure"
    (ast/nom answer
      (is (empty?
            (kernel/prove-program
              (greenfield-exists-neq-program)
              (ast/pos-lit (ast/app-term 'p (ast/var-term answer)))
              1
              8))))))
