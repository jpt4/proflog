(ns proflog.query-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answer-overlay :as answer-overlay]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(def query-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'p 1
                 'even 1
                 'odd 1
                 'move 2
                 'win 1
                 'undef 1}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn succeeds-directly?
  ([program query]
   (succeeds-directly? program query nil))
  ([program query fuel]
   (seq
     (if (nil? fuel)
       (query/query-succeeds program query 1)
       (query/query-succeeds program query 1 fuel)))))

(defn fails-directly?
  ([program query]
   (fails-directly? program query nil))
  ([program query fuel]
   (seq
     (if (nil? fuel)
       (query/query-fails program query 1)
       (query/query-fails program query 1 fuel)))))

(defn p1-program
  []
  (ast/nom x y
    (language/compile-program
      query-language
      [(ast/clause 'even [x]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
                     (ast/exists-form y
                                      (ast/and-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term y)))
                                        (ast/pos-lit (ast/app-term 'odd (ast/var-term y)))))))
       (ast/clause 'odd [x]
                   (ast/forall-form y
                                    (ast/implies-form
                                      (ast/pos-lit (ast/app-term 'even (ast/var-term y)))
                                      (ast/neq-lit (ast/var-term x) (ast/var-term y)))))])))

(defn status-program
  []
  (ast/nom x
    (language/compile-program
      query-language
      [(ast/clause 'p [x]
                   (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))])))

(defn tamper-p-clause-body
  [program body]
  (let [patch-clause (fn [clause]
                       (assoc clause
                              :body body
                              :negated-body body))
        patch-list (fn [clauses]
                     (apply list (map patch-clause clauses)))]
    (-> program
        (update-in [:clauses 'p] patch-clause)
        (update :clause-list patch-list)
        (update :alternative-clause-list patch-list)
        (update :guarded-clause-list patch-list))))

(defn inconsistent-status-program
  []
  (tamper-p-clause-body
    (status-program)
    ;; This is an intentionally invalid compiled-program artifact: a closed
    ;; constructor clash can close a subsidiary tableau, so making it both the
    ;; body and the negated body lets the same declared relation prove both
    ;; sides of the public query-status semidecision boundary.
    (ast/eq-lit (ast/app-term 'zero) (numeral 1))))

(defn p2-program
  []
  (ast/nom x y
    (language/compile-program
      query-language
      [(ast/clause 'win [x]
                   (ast/exists-form y
                                    (ast/and-form
                                      (ast/or-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term y)))
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's
                                                                  (ast/app-term 's
                                                                                (ast/var-term y)))))
                                      (ast/neg-lit (ast/app-term 'win (ast/var-term y))))))])))

(defn factored-move-program
  []
  (ast/nom x y mx my
    (language/compile-program
      query-language
      [(ast/clause 'win [x]
                   (ast/exists-form y
                                    (ast/and-form
                                      (ast/pos-lit (ast/app-term 'move
                                                                 (ast/var-term x)
                                                                 (ast/var-term y)))
                                      (ast/neg-lit (ast/app-term 'win
                                                                 (ast/var-term y))))))
       (ast/clause 'move [mx my]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term mx)
                                 (ast/app-term 's (ast/var-term my)))
                     (ast/eq-lit (ast/var-term mx)
                                 (ast/app-term 's
                                               (ast/app-term 's
                                                             (ast/var-term my))))))])))

(deftest query-status-distinguishes-success-failure-and-unresolved
  (testing "undefined declared relations stay unresolved while defined ones succeed or fail"
    (let [program (status-program)]
      (is (= :succeeds
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'p (numeral 0)))
               {:timeout-ms 1000})))
      (is (= :fails
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'p (numeral 1)))
               {:timeout-ms 1000})))
      (is (= :unresolved
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'undef (numeral 0)))
               {:timeout-ms 1000}))))))

(deftest query-status-can-report-inconsistent-for-unsound-compiled-program
  (testing "a validated query can still expose an inconsistent compiled-program artifact"
    (let [program (inconsistent-status-program)
          query (ast/pos-lit (ast/app-term 'p (numeral 0)))]
      (is (seq (query/query-succeeds program query 1 4)))
      (is (seq (query/query-fails program query 1 4)))
      (is (= :inconsistent
             (query/query-status
               program
               query
               {:timeout-ms 1000
                :poll-ms 0}))))))

(deftest direct-query-probes-stay-on-the-pure-kernel-path
  (testing "query-succeeds and query-fails do not route through the answer overlay"
    (let [program (status-program)
          success-query (ast/pos-lit (ast/app-term 'p (numeral 0)))
          failure-query (ast/pos-lit (ast/app-term 'p (numeral 1)))
          query-entry-calls (atom 0)
          general-answer-calls (atom 0)
          original-query-entry answer-overlay/prove-program-query-entryo
          original-general-answer answer-overlay/prove-program-answero]
      (with-redefs [answer-overlay/prove-program-query-entryo
                    (fn [& args]
                      (swap! query-entry-calls inc)
                      (apply original-query-entry args))
                    answer-overlay/prove-program-answero
                    (fn [& args]
                      (swap! general-answer-calls inc)
                      (apply original-general-answer args))]
        (is (succeeds-directly? program success-query))
        (is (fails-directly? program failure-query))
        (is (zero? @query-entry-calls))
        (is (zero? @general-answer-calls))))))

(deftest fitting-p1-even-zero-succeeds
  (testing "P1 proves even(0) by direct proof search"
    (is (succeeds-directly?
          (p1-program)
          (ast/pos-lit (ast/app-term 'even (numeral 0)))))))

(deftest fitting-p1-odd-one-succeeds
  (testing "P1 proves odd(s(0)) once the recursive branch gets enough fuel"
    (is (succeeds-directly?
          (p1-program)
          (ast/pos-lit (ast/app-term 'odd (numeral 1)))
          16))))

(deftest fitting-p2-win-three-fails
  (testing "P2 refutes win(3) by direct proof search"
    (is (fails-directly?
          (p2-program)
          (ast/pos-lit (ast/app-term 'win (numeral 3)))))))

(deftest fitting-p2-small-positions-follow-the-nim-pattern
  (testing "P2 directly proves the expected winners and refutes the expected losers"
    (let [program (p2-program)]
      (is (fails-directly?
            program
            (ast/pos-lit (ast/app-term 'win (numeral 0)))))
      (is (succeeds-directly?
            program
            (ast/pos-lit (ast/app-term 'win (numeral 1)))))
      (is (succeeds-directly?
            program
            (ast/pos-lit (ast/app-term 'win (numeral 2))))))))

(deftest factored-move-warning-leaves-small-win-positions-unresolved
  (testing "ground move/2 stays decidable, but factoring Nim through move/2 leaves win(0) and win(1) unresolved"
    (let [program (factored-move-program)
          inline-program (p2-program)]
      (is (succeeds-directly?
            program
            (ast/pos-lit (ast/app-term 'move (numeral 1) (numeral 0)))
            16))
      (is (fails-directly?
            program
            (ast/pos-lit (ast/app-term 'move (numeral 0) (numeral 1)))
            16))
      (is (= :unresolved
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'win (numeral 0)))
               {:timeout-ms 1000})))
      (is (= :unresolved
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'win (numeral 1)))
               {:timeout-ms 1000})))
      (is (fails-directly?
            inline-program
            (ast/pos-lit (ast/app-term 'win (numeral 0)))))
      (is (succeeds-directly?
            inline-program
            (ast/pos-lit (ast/app-term 'win (numeral 1)))
            16)))))
