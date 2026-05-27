(ns proflog.constructor-recursive-kernel-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(defn proof-contains?
  [tag proof]
  (boolean
    (some (fn [node]
            (and (seq? node)
                 (= tag (first node))))
          (tree-seq sequential? seq proof))))

(def peano-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'peel 2}}))

(defn peano-term
  [n]
  (nth (iterate #(ast/app-term 's %) (ast/app-term 'zero)) n))

(defn peel-body
  [x y predecessor]
  (ast/or-form
    (ast/eq-lit (ast/var-term x) (ast/var-term y))
    (ast/exists-form
      predecessor
      (ast/and-form
        (ast/eq-lit (ast/var-term x)
                    (ast/app-term 's (ast/var-term predecessor)))
        (ast/pos-lit
          (ast/app-term 'peel
                        (ast/var-term predecessor)
                        (ast/var-term y)))))))

(defn peel-program
  []
  (ast/nom x y predecessor
    (language/compile-program
      peano-language
      [(ast/clause 'peel [x y] (peel-body x y predecessor))])))

(deftest ^:constructor-recursive non-list-constructor-recursion-closes-through-guarded-alternatives
  (testing "a Peano-style recursive relation uses the same generic guarded call path as lists"
    (let [program (peel-program)
          proof (first
                  (query/query-succeeds
                    program
                    (ast/pos-lit
                      (ast/app-term 'peel
                                    (peano-term 2)
                                    (peano-term 0)))
                    1
                    128))]
      (is proof)
      (is (or (proof-contains? 'neg-call-guarded-alt proof)
              (proof-contains? 'neg-call-alt proof))))))
