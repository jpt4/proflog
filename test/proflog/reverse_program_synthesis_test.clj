(ns proflog.reverse-program-synthesis-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [!= == run fresh]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]))

(deftest compiled-program-body-can-be-synthesized-under-a-fixed-clause-shape
  (testing "the kernel can close a positive call by synthesizing a contradictory compiled clause body"
    (ast/nom x
      (is (seq
            (run 1 [proof]
              (fresh [f g]
                (!= f g)
                (kernel/prove-programo
                  (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
                  '() '() '()
                  {:language '_
                   :clauses '_
                   :clause-list (list {:relation 'p
                                       :params (list x)
                                       :body (ast/eq-lit (ast/app-term f)
                                                         (ast/app-term g))
                                       :negated-body (ast/true-form)})}
                  proof))))))))

(deftest compiled-program-body-can-be-synthesized-through-formal-parameter-substitution
  (testing "body synthesis can depend on the call environment rather than only closed contradictions"
    (ast/nom x
      (is (seq
            (run 1 [proof]
              (fresh [body-left]
                (kernel/prove-programo
                  (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
                  '() '() '()
                  {:language '_
                   :clauses '_
                   :clause-list (list {:relation 'p
                                       :params (list x)
                                       :body (ast/eq-lit body-left
                                                         (ast/app-term 'zero))
                                       :negated-body (ast/true-form)})}
                  proof)
                (== body-left (ast/var-term x)))))))))

(deftest compiled-program-body-synthesis-can-leave-fuel-open
  (testing "procedure-call synthesis can leave fuel relational while stepping the kernel"
    (ast/nom x
      (is (= '(nil)
             (run 1 [fuel]
               (fresh [body-left proof]
                 (kernel/prove-programo
                   (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
                   '() '() '()
                   {:language '_
                    :clauses '_
                    :clause-list (list {:relation 'p
                                        :params (list x)
                                        :body (ast/eq-lit body-left
                                                          (ast/app-term 'zero))
                                        :negated-body (ast/true-form)})}
                   '()
                   fuel
                   proof)
                 (== body-left (ast/var-term x)))))))))

(deftest compiled-program-shape-is-not-yet-a-sound-surface-synthesis-contract
  (testing "a directly supplied compiled program can carry inconsistent body and negated-body formulas"
    (ast/nom x
      (let [contradiction (ast/eq-lit (ast/app-term 'zero) (ast/app-term 'one))
            inconsistent-program
            {:language '_
             :clauses '_
             :clause-list (list {:relation 'p
                                 :params (list x)
                                 :body contradiction
                                 :negated-body contradiction})}]
        (is (seq
              (kernel/prove-program
                inconsistent-program
                (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
                1)))
        (is (seq
              (kernel/prove-program
                inconsistent-program
                (ast/neg-lit (ast/app-term 'p (ast/app-term 'one)))
                1)))))))
