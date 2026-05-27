(ns proflog.equality-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.proof :as proof]))

(defn provable?
  "True when the greenfield kernel finds at least one closed tableau."
  [formula]
  (seq (kernel/prove formula 1)))

(defn not-provable?
  "True when the greenfield kernel finds no proof within the given bound."
  ([formula] (not-provable? formula 1))
  ([formula n]
   (empty? (kernel/prove formula n))))

(deftest free-constructor-clash-closes-equality
  (testing "distinct constructors cannot be equal in the free-constructor theory"
    (is (provable?
          (ast/eq-lit (ast/app-term 'zero)
                      (ast/app-term 'one))))
    (is (provable?
          (ast/eq-lit (ast/app-term 'zero)
                      (ast/app-term 'succ (ast/app-term 'zero)))))))

(deftest injectivity-and-eq-neq-closure-work-together
  (testing "same-head equalities bind inner free variables and can violate disequalities"
    (ast/nom x
      (is (provable?
            (ast/and-form
              (ast/eq-lit (ast/app-term 'succ (ast/var-term x))
                          (ast/app-term 'succ (ast/app-term 'a)))
              (ast/neq-lit (ast/var-term x) (ast/app-term 'a)))))
      (is (provable?
            (ast/and-form
              (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
              (ast/eq-lit (ast/app-term 'succ (ast/var-term x))
                          (ast/app-term 'succ (ast/app-term 'a)))))))))

(deftest equality-bindings-compose-through-transitive-chains
  (testing "multiple equalities can propagate through a branch and violate a later disequality"
    (ast/nom x y
      (is (provable?
            (ast/and-form
              (ast/eq-lit (ast/var-term x) (ast/var-term y))
              (ast/and-form
                (ast/eq-lit (ast/var-term y) (ast/app-term 'a))
                (ast/neq-lit (ast/var-term x) (ast/app-term 'a))))))
      (is (provable?
            (ast/and-form
              (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
              (ast/and-form
                (ast/eq-lit (ast/var-term y) (ast/app-term 'a))
                (ast/eq-lit (ast/var-term x) (ast/var-term y)))))))))

(deftest equality-supports-atom-congruence-on-the-branch
  (testing "equality bindings propagate into later atom closure checks"
    (ast/nom x
      (is (provable?
            (ast/and-form
              (ast/eq-lit (ast/var-term x) (ast/app-term 'a))
              (ast/and-form
                (ast/pos-lit (ast/app-term 'color (ast/var-term x)))
                (ast/neg-lit (ast/app-term 'color (ast/app-term 'a))))))))))

(deftest disequality-stays-open-until-it-is-violated
  (testing "symbolic disequalities remain open until later equalities force them false"
    (ast/nom x
      (is (not-provable?
            (ast/neq-lit (ast/app-term 'succ (ast/var-term x))
                         (ast/app-term 'succ (ast/app-term 'a)))))
      (is (provable?
            (ast/and-form
              (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
              (ast/eq-lit (ast/var-term x) (ast/app-term 'a))))))))

(deftest ground-occurs-check-shape-closes-by-constructor-clash
  (testing "the ground shape a = f(a) is unsatisfiable in the free-constructor theory"
    (is (provable?
          (ast/eq-lit (ast/app-term 'a)
                      (ast/app-term 'f (ast/app-term 'a)))))))

(deftest cyclic-open-equality-fails-by-occurs-check
  (testing "a free variable cannot unify with a term that contains it"
    (ast/nom x
      (is (provable?
            (ast/eq-lit (ast/var-term x)
                        (ast/app-term 'f (ast/var-term x))))))))

(deftest decomposition-finds-inner-constructor-clashes
  (testing "same-head equalities recurse into their arguments to find contradictions"
    (let [proof (first
                  (kernel/prove
                    (ast/eq-lit
                      (ast/app-term 'pair
                                    (ast/app-term 'a)
                                    (ast/app-term 'b))
                      (ast/app-term 'pair
                                    (ast/app-term 'a)
                                    (ast/app-term 'c)))
                    1))]
      (is proof)
      (is (proof/contains-step? proof 'decompose))
      (is (proof/contains-step? proof 'free-close)))))

(deftest decomposition-can-bind-earlier-arguments-before-finding-a-later-clash
  (testing "same-head equality can become contradictory only after an earlier argument binds a proof-time parameter"
    (ast/nom a b t
      (let [formula
            (ast/exists-form
              a
              (ast/exists-form
                b
                (ast/exists-form
                  t
                  (ast/eq-lit
                    (ast/app-term 'cons
                                  (ast/app-term 's (ast/app-term 'zero))
                                  (ast/app-term 'null))
                    (ast/app-term 'cons
                                  (ast/var-term a)
                                  (ast/app-term 'cons
                                                (ast/var-term b)
                                                (ast/var-term t)))))))
            proof (first (kernel/prove formula 1))]
        (is proof)
        (is (proof/contains-step? proof 'par-bind))
        (is (proof/contains-step? proof 'decompose))
        (is (proof/contains-step? proof 'free-close))))))

(deftest symbolic-disequalities-stay-open-when-no-conflict-is-forced
  (testing "a same-head disequality remains open until some branch equality violates it"
    (ast/nom x
      (is (not-provable?
            (ast/neq-lit
              (ast/app-term 'pair
                            (ast/var-term x)
                            (ast/app-term 'a))
              (ast/app-term 'pair
                            (ast/var-term x)
                            (ast/app-term 'b))))))))

(deftest nested-occurs-checks-close-cyclic-equalities
  (testing "occurs-check rejection also handles variables nested under multiple constructors"
    (ast/nom x
      (is (provable?
            (ast/eq-lit
              (ast/var-term x)
              (ast/app-term 'f
                            (ast/app-term 'g
                                          (ast/var-term x)))))))))

(deftest unresolved-parameters-do-not-close-by-constructor-clash-alone
  (testing "an unresolved internal parameter stays open until some equality constrains it"
    (ast/nom p q
      (is (not-provable?
            (ast/eq-lit (ast/par-term p)
                        (ast/app-term 'zero))))
      (is (not-provable?
            (ast/eq-lit (ast/par-term p)
                        (ast/par-term q))))
      (is (provable?
            (ast/and-form
              (ast/eq-lit (ast/par-term p)
                          (ast/app-term 'zero))
              (ast/neq-lit (ast/par-term p)
                           (ast/app-term 'zero))))))))

(deftest equality-proof-tags-remain-inspectable
  (testing "equality closure leaves explicit proof tags for debugging"
    (ast/nom x
      (let [clash-proof (first
                          (kernel/prove
                            (ast/eq-lit (ast/app-term 'zero)
                                        (ast/app-term 'one))
                            1))
            atom-proof (first
                         (kernel/prove
                           (ast/and-form
                             (ast/eq-lit (ast/var-term x) (ast/app-term 'a))
                             (ast/and-form
                               (ast/pos-lit (ast/app-term 'color (ast/var-term x)))
                               (ast/neg-lit (ast/app-term 'color (ast/app-term 'a)))))
                           1))
            occurs-proof (first
                           (kernel/prove
                             (ast/eq-lit (ast/var-term x)
                                         (ast/app-term 'f (ast/var-term x)))
                             1))]
      (is (proof/contains-step? clash-proof 'free-close))
      (is (proof/contains-step? atom-proof 'eq-bind))
      (is (proof/contains-step? atom-proof 'close))
      (is (proof/contains-step? occurs-proof 'occurs-close))))))
