(ns proflog.subst-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [fresh run* ==]]
            [proflog.ast :as ast]
            [proflog.subst :as subst]))

(deftest subst-termo-replaces-vars-and-preserves-pars
  (testing "term substitution replaces tagged vars and preserves internal parameters"
    (ast/nom x p
      (is (= (ast/app-term 'zero)
             (subst/subst-term
               (ast/var-term x)
               (list [x (ast/app-term 'zero)]))))
      (is (= [true]
             (run* [q]
               (== q true)
               (subst/subst-termo
                 (ast/var-term x)
                 (list [x (ast/app-term 'zero)])
                 (ast/app-term 'zero)))))
      (is (= (ast/par-term p)
             (subst/subst-term
               (ast/par-term p)
               (list [x (ast/app-term 'zero)]))))
      (is (= [true]
             (run* [q]
               (== q true)
               (subst/subst-termo
                 (ast/par-term p)
                 (list [x (ast/app-term 'zero)])
                 (ast/par-term p))))))))

(deftest subst-formulao-respects-binding-and-shadowing
  (testing "substitution does not replace occurrences protected by a quantifier binder"
    (ast/nom x y
      (let [formula (ast/forall-form
                      x
                      (ast/and-form
                        (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                        (ast/pos-lit (ast/app-term 'value (ast/var-term y)))))
            expected (ast/forall-form
                       x
                       (ast/and-form
                         (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                         (ast/pos-lit (ast/app-term 'value (ast/app-term 'one)))))]
        (is (= [expected]
               [(subst/subst-formula
                  formula
                  (list [x (ast/app-term 'zero)]
                        [y (ast/app-term 'one)]))]))
        (is (= [true]
               (run* [q]
                 (== q true)
                 (subst/subst-formulao
                   formula
                   (list [x (ast/app-term 'zero)]
                         [y (ast/app-term 'one)])
                   expected))))))))

(deftest subst-formulao-supports-once-forall-bodies
  (testing "forward substitution also threads through the internal single-use universal form"
    (ast/nom x y z
      (let [formula (ast/once-forall-form
                      x
                      (ast/or-form
                        (ast/neq-lit (ast/var-term y) (ast/var-term x))
                        (ast/neg-lit (ast/app-term 'p (ast/var-term z)))))
            expected (ast/once-forall-form
                       x
                       (ast/or-form
                         (ast/neq-lit (ast/app-term 'zero) (ast/var-term x))
                         (ast/neg-lit (ast/app-term 'p (ast/app-term 'one)))))]
        (is (= expected
               (subst/subst-formula
                 formula
                 (list [y (ast/app-term 'zero)]
                       [z (ast/app-term 'one)]))))
        (is (= [true]
               (run* [q]
                 (== q true)
                 (subst/subst-formulao
                   formula
                   (list [y (ast/app-term 'zero)]
                         [z (ast/app-term 'one)])
                   expected))))))))

(deftest subst-formulao-synthesizes-term-preimages
  (testing "a substituted output can be explained by an input variable under a known environment"
    (ast/nom x
      (is (= [true]
             (run* [q]
               (== q true)
               (fresh [arg]
                 (subst/subst-formulao
                   (ast/pos-lit (ast/app-term 'p arg))
                   (list [x (ast/app-term 'zero)])
                   (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero))))
                 (== arg (ast/var-term x)))))))))

(deftest subst-formulao-synthesizes-environment-keys
  (testing "environment bindings can be refined from a known input formula and substituted output"
    (ast/nom x
      (is (= [true]
             (run* [q]
               (== q true)
               (fresh [binding-nom]
                 (subst/subst-formulao
                   (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                   (list [binding-nom (ast/app-term 'zero)])
                   (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero))))
                 (== binding-nom x))))))))

(deftest subst-formulao-preserves-binder-shadowing-in-preimage-mode
  (testing "reverse substitution under binders still removes shadowed environment bindings"
    (ast/nom x y
      (let [env (list [x (ast/app-term 'zero)]
                      [y (ast/app-term 'one)])
            out-body (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))]
        (is (= [true]
               (run* [q]
                 (== q true)
                 (fresh [arg]
                   (subst/subst-formulao
                     (ast/forall-form x
                                       (ast/pos-lit (ast/app-term 'p arg)))
                     env
                     (ast/forall-form x out-body))
                   (== arg (ast/var-term y))))))
        (is (= [true]
               (run* [q]
                 (== q true)
                 (fresh [arg]
                   (subst/subst-formulao
                     (ast/once-forall-form x
                                            (ast/pos-lit (ast/app-term 'p arg)))
                     env
                     (ast/once-forall-form x out-body))
                   (== arg (ast/var-term y))))))
        (is (= [true]
               (run* [q]
                 (== q true)
                 (fresh [arg]
                   (subst/subst-formulao
                     (ast/exists-form x
                                      (ast/pos-lit (ast/app-term 'p arg)))
                     env
                     (ast/exists-form x out-body))
                   (== arg (ast/var-term y))))))))))
