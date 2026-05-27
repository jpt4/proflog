(ns proflog.existential-disequality-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(def witness-language
  (language/language
    {:constants ['a 'b]
     :relations {'p 1}}))

(def status-timeout-ms
  5000)

(defn exists-neq-program
  []
  (ast/nom x y
    (language/compile-program
      witness-language
      [(ast/clause 'p [x]
                   (ast/exists-form
                     y
                     (ast/neq-lit (ast/var-term x)
                                  (ast/var-term y))))])))

(defn p-query
  [term]
  (ast/pos-lit (ast/app-term 'p term)))

(defn contains-par?
  [value]
  (cond
    (and (seq? value) (= 'par (first value))) true
    (map? value) (some contains-par? (mapcat identity value))
    (sequential? value) (some contains-par? value)
    :else false))

(deftest existential-disequality-ground-queries-succeed
  (testing "p(a) and p(b) are true because each has the opposite constant as a witness"
    (let [program (exists-neq-program)
          p-a (p-query (ast/app-term 'a))
          p-b (p-query (ast/app-term 'b))]
      (is (= :succeeds
             (query/query-status program p-a {:timeout-ms status-timeout-ms})))
      (is (= :succeeds
             (query/query-status program p-b {:timeout-ms status-timeout-ms})))
      (is (seq (query/query-succeeds program p-a 1 8)))
      (is (seq (query/query-succeeds program p-b 1 8)))
      (is (empty? (query/query-fails program p-a 1 8)))
      (is (empty? (query/query-fails program p-b 1 8))))))

(deftest existential-disequality-ground-answer-enumeration-stays-in-l
  (testing "open evaluation returns object-language witnesses, not internal par terms"
    (ast/nom answer
      (let [records (answers/query-ground-answers
                      (exists-neq-program)
                      (p-query (ast/var-term answer))
                      [answer]
                      {:max-depth 0
                       :fuel 8
                       :limit 4
                       :failure-timeout-ms 1000})
            terms (mapv #(answers/binding-term % answer) records)]
        (is (= [(ast/app-term 'a) (ast/app-term 'b)]
               terms))
        (is (not-any? contains-par? records))))))
