(ns proflog.l-ground-constraint-probe-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run run*]]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel-support :as support]
            [proflog.l-ground-constraint-probe :as probe]))

(defn- residual-answer?
  [answer]
  (and (seq? answer)
       (some #{':-} answer)))

(deftest tree-absence-constraint-handles-basic-proflog-terms
  (ast/nom x p
    (testing "object variables and applications without par pass"
      (is (= '(:ok)
             (run* [q]
               (probe/l-ground-no-paro (ast/var-term x))
               (== q :ok))))
      (is (= '(:ok)
             (run* [q]
               (probe/l-ground-no-paro
                 (ast/app-term 's (ast/app-term 'zero)))
               (== q :ok)))))
    (testing "direct and nested delta parameters fail"
      (is (= '()
             (run* [q]
               (probe/l-ground-no-paro (ast/par-term p))
               (== q :bad))))
      (is (= '()
             (run* [q]
               (probe/l-ground-no-paro
                 (ast/app-term 's (ast/par-term p)))
               (== q :bad)))))))

(deftest tree-absence-constraint-remains-live-across-later-refinement
  (ast/nom p
    (testing "an open term retains a delayed tree constraint"
      (let [answers (run 1 [term]
                      (probe/l-ground-no-paro term))]
        (is (= 1 (count answers)))
        (is (residual-answer? (first answers)))))
    (testing "later safe refinement succeeds"
      (is (= '(:ok)
             (run* [q]
               (fresh [term]
                 (probe/l-ground-no-paro term)
                 (== term (ast/app-term 's (ast/app-term 'zero)))
                 (== q :ok))))))
    (testing "later par refinement fails even after a safe outer app shape"
      (is (= '()
             (run* [q]
               (fresh [term child]
                 (probe/l-ground-no-paro term)
                 (== term (ast/app-term 's child))
                 (== child (ast/par-term p))
                 (== q :bad))))))))

(deftest predc-only-guard-is-a-negative-control
  (ast/nom p
    (testing "predc can accept an outer app while a nested child is still open"
      (is (= '(:bad)
             (run* [q]
               (fresh [term child]
                 (probe/l-ground-predco term)
                 (== term (ast/app-term 's child))
                 (== child (ast/par-term p))
                 (== q :bad))))))
    (testing "treec rejects the same later refinement"
      (is (= '()
             (run* [q]
               (fresh [term child]
                 (probe/l-ground-no-paro term)
                 (== term (ast/app-term 's child))
                 (== child (ast/par-term p))
                 (== q :bad))))))))

(deftest root-plus-tree-constraint-is-still-weaker-than-production-l-ground
  (ast/nom x p
    (testing "the hybrid agrees on ordinary var/app/par cases"
      (is (= '(:ok)
             (run* [q]
               (probe/l-ground-root-and-no-paro (ast/var-term x))
               (== q :ok))))
      (is (= '(:ok)
             (run* [q]
               (probe/l-ground-root-and-no-paro
                 (ast/app-term 's (ast/app-term 'zero)))
               (== q :ok))))
      (is (= '()
             (run* [q]
               (probe/l-ground-root-and-no-paro (ast/par-term p))
               (== q :bad)))))
    (testing "the hybrid does not validate object-term shape under app arguments"
      (let [malformed (list 'app 'f 'not-a-term)]
        (is (= '(:hybrid-admitted)
               (run* [q]
                 (probe/l-ground-root-and-no-paro malformed)
                 (== q :hybrid-admitted))))
        (is (= '()
               (run* [q]
                 (support/l-ground-termo malformed)
                 (== q :production-admitted))))))))

(deftest production-structural-relation-remains-the-strict-reference
  (ast/nom x p
    (testing "production relation accepts real L terms and rejects par terms"
      (is (= '(:ok)
             (run* [q]
               (support/l-ground-termo
                 (ast/app-term 's (ast/var-term x)))
               (== q :ok))))
      (is (= '()
             (run* [q]
               (support/l-ground-termo
                 (ast/app-term 's (ast/par-term p)))
               (== q :bad)))))
    (testing "production relation is strict once the child shape is known"
      (is (= '()
             (run* [q]
               (fresh [term child]
                 (== term (ast/app-term 's child))
                 (== child (ast/par-term p))
                 (support/l-ground-termo term)
                 (== q :bad))))))))
