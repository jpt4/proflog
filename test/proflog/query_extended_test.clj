(ns proflog.query-extended-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.query :as query]
            [proflog.query-test :as qt]))

(deftest bounded-success-query-helper-returns-control-on-timeout
  (testing "bounded success queries eventually return an empty result when the budget expires"
    ;; Keep this in the extended suite: the finite-fuel timeout contract is
    ;; operational rather than hard real-time, so loaded machines can stretch
    ;; the last admitted slice well past the nominal timeout.
    (is (= '()
           (query/query-succeeds-within
             (qt/p2-program)
             (ast/pos-lit (ast/app-term 'win (qt/numeral 0)))
             1
             25)))))

(deftest bounded-failure-query-helper-returns-control-on-timeout
  (testing "bounded failure queries also recover predictably when no failure proof arrives in budget"
    (is (= '()
           (query/query-fails-within
             (qt/p2-program)
             (ast/pos-lit (ast/app-term 'win (qt/numeral 1)))
             1
             25)))))

(deftest bounded-helpers-still-return-easy-proofs-when-they-exist
  (testing "the bounded helpers are operational probes, but they should still surface easy non-recursive proofs"
    (is (seq
          (query/query-succeeds-within
            (qt/status-program)
            (ast/pos-lit (ast/app-term 'p (qt/numeral 0)))
            1
            500)))
    (is (seq
          (query/query-fails-within
            (qt/status-program)
            (ast/pos-lit (ast/app-term 'p (qt/numeral 1)))
            1
            500)))))
