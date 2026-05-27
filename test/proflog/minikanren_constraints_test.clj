(ns proflog.minikanren-constraints-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh lcons run run*]]
            [clojure.test :refer [deftest is testing]]
            [proflog.minikanren-constraints :as mkc]))

(defn- residual-answer?
  [answer]
  (and (seq? answer)
       (some #{':-} answer)))

(deftest symbolo-accepts-symbols-and-rejects-non-symbols
  (testing "ground symbols pass"
    (is (= '(token)
           (run* [q]
             (mkc/symbolo q)
             (== q 'token)))))
  (testing "non-symbols fail"
    (is (= '()
           (run* [q]
             (mkc/symbolo q)
             (== q 12)))))
  (testing "open variables retain a delayed symbolic constraint"
    (let [answers (run 1 [q]
                    (mkc/symbolo q))]
      (is (= 1 (count answers)))
      (is (residual-answer? (first answers)))
      (is (some #{'symbolo} (flatten answers))))))

(deftest numbero-accepts-numbers-and-rejects-non-numbers
  (testing "ground numbers pass"
    (is (= '(12)
           (run* [q]
             (mkc/numbero q)
             (== q 12)))))
  (testing "non-numbers fail"
    (is (= '()
           (run* [q]
             (mkc/numbero q)
             (== q 'token)))))
  (testing "open variables retain a delayed numeric constraint"
    (let [answers (run 1 [q]
                    (mkc/numbero q))]
      (is (= 1 (count answers)))
      (is (residual-answer? (first answers)))
      (is (some #{'numbero} (flatten answers))))))

(deftest absento-accepts-terms-without-target-and-rejects-present-targets
  (testing "ground terms without the target pass"
    (is (= '(:ok)
           (run* [q]
             (mkc/absento 'intval '(call arg))
             (== q :ok)))))
  (testing "ground terms containing the target fail"
    (is (= '()
           (run* [q]
             (mkc/absento 'intval '(call intval))
             (== q :ok)))))
  (testing "the target may appear neither at the root nor in a discovered child"
    (is (= '()
           (run* [q]
             (mkc/absento 'intval q)
             (== q '(intval)))))
    (is (= '()
           (run* [q]
             (mkc/absento 'intval q)
             (== q '(call (intval))))))))

(deftest absento-delays-over-open-terms
  (testing "an entirely open term leaves a residual tree constraint"
    (let [answers (run 1 [q]
                    (mkc/absento 'intval q))]
      (is (= 1 (count answers)))
      (is (residual-answer? (first answers)))
      (is (some #{'absento} (flatten answers)))))
  (testing "an open tail keeps the absence check delayed after known safe structure"
    (let [answers (run 1 [q]
                    (fresh [tail]
                      (mkc/absento 'intval q)
                      (== (lcons 'call tail) q)))]
      (is (= 1 (count answers)))
      (is (residual-answer? (first answers)))))
  (testing "later discovery of a forbidden value fails"
    (is (= '()
           (run* [q]
             (fresh [tail]
               (mkc/absento 'intval q)
               (== (lcons 'call tail) q)
               (== (lcons 'intval '()) tail)))))))

(deftest absento-handles-general-tree-shapes
  (testing "compound targets are rejected at any discovered tree node"
    (is (= '()
           (run* [q]
             (mkc/absento '(call intval) '(wrapper (call intval)))
             (== q :bad)))))
  (testing "an open target remains live and can later be rejected"
    (is (= '()
           (run* [q]
             (fresh [target]
               (mkc/absento target '(call intval))
               (== target 'intval)
               (== q :bad))))))
  (testing "an open target may later be safely refined"
    (is (= '(safe)
           (run* [q]
             (fresh [target]
               (mkc/absento target '(call intval))
               (== target 'other)
               (== q 'safe))))))
  (testing "an open target cannot be the same variable as the constrained node"
    (is (= '()
           (run* [q]
             (fresh [target]
               (mkc/absento target target)
               (== q :bad)))))
    (is (= '()
           (run* [q]
             (fresh [target]
               (mkc/absento target (list target))
               (== q :bad)))))
    (is (= '()
           (run* [q]
             (fresh [target term]
               (mkc/absento target term)
               (== target term)
               (== q :bad))))))
  (testing "vectors and map keys are part of the constrained tree"
    (is (= '()
           (run* [q]
             (mkc/absento :forbidden [:ok {:nested :forbidden}])
             (== q :bad))))
    (is (= '()
           (run* [q]
             (mkc/absento :forbidden {:forbidden :value})
             (== q :bad))))
    (is (= '(:ok)
           (run* [q]
             (mkc/absento :forbidden {:safe [:value]})
             (== q :ok))))))

(deftest absento-pushes-down-across-upstream-orderings
  (doseq [[label goal]
          [["push-down problems 2"
            (fn []
              (fresh [x a d]
                (mkc/absento 'intval x)
                (== 'intval a)
                (== (lcons a d) x)))]
           ["push-down problems 3"
            (fn []
              (fresh [x a d]
                (== (lcons a d) x)
                (mkc/absento 'intval x)
                (== 'intval a)))]
           ["push-down problems 4"
            (fn []
              (fresh [x a d]
                (== (lcons a d) x)
                (== 'intval a)
                (mkc/absento 'intval x)))]
           ["push-down problems 6"
            (fn []
              (fresh [x a d]
                (== 'intval a)
                (== (lcons a d) x)
                (mkc/absento 'intval x)))]
           ["push-down problems 1"
            (fn []
              (fresh [x a d]
                (mkc/absento 'intval x)
                (== (lcons a d) x)
                (== 'intval a)))]
           ["push-down problems 5"
            (fn []
              (fresh [x a d]
                (== 'intval a)
                (mkc/absento 'intval x)
                (== (lcons a d) x)))]]]
    (testing label
      (is (= '()
             (run* [q]
               (goal)))))))
