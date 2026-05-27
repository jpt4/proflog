(ns proflog.proof-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]
            [proflog.proof :as proof]))

(def proof-language
  (language/language
    {:constants ['zero]
     :relations {'p 1
                 'q 1}}))

(defn false-body-program
  []
  (ast/nom x
    (language/compile-program
      proof-language
      [(ast/clause 'p [x]
                   (ast/neq-lit (ast/var-term x)
                                (ast/var-term x)))])))

(defn exact-zero-program
  []
  (ast/nom x
    (language/compile-program
      proof-language
      [(ast/clause 'q [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))])))

(deftest proof-terms-record-the-major-tableau-steps
  (testing "proof terms expose the structural steps used by the base kernel"
    (let [conj-proof (first
                       (kernel/prove
                         (ast/and-form
                           (ast/pos-lit (ast/app-term 'p))
                           (ast/neg-lit (ast/app-term 'p)))
                         1))
          split-proof (first
                        (kernel/prove
                          (ast/or-form
                            (ast/and-form
                              (ast/pos-lit (ast/app-term 'p))
                              (ast/neg-lit (ast/app-term 'p)))
                            (ast/and-form
                              (ast/pos-lit (ast/app-term 'q))
                              (ast/neg-lit (ast/app-term 'q))))
                          1))]
      (is (proof/contains-step? conj-proof 'conj))
      (is (proof/contains-step? conj-proof 'close))
      (is (proof/contains-step? split-proof 'split)))))

(deftest quantifier-proof-steps-are-distinguishable
  (testing "universal, single-use universal, and existential work leave distinct proof tags"
    (ast/nom x
      (let [univ-proof (first
                         (kernel/prove
                           (ast/and-form
                             (ast/forall-form x
                                              (ast/pos-lit
                                                (ast/app-term 'value (ast/var-term x))))
                             (ast/neg-lit (ast/app-term 'value (ast/app-term 'zero))))
                           1))
            witness-proof (first
                            (kernel/prove
                              (ast/exists-form
                                x
                                (ast/and-form
                                  (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                              (ast/neg-lit (ast/app-term 'value (ast/var-term x)))))
                              1))
            once-proof (first
                         (kernel/prove
                           (ast/once-forall-form
                             x
                             (ast/and-form
                               (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                               (ast/neg-lit (ast/app-term 'value (ast/var-term x)))))
                           1))]
        (is (proof/contains-step? univ-proof 'univ))
        (is (proof/contains-step? once-proof 'once-univ))
        (is (proof/contains-step? witness-proof 'witness))))))

(deftest proof-terms-preserve-equality-and-disequality-steps
  (testing "later closure still exposes both the neq-store and eq-step tags"
    (ast/nom x
      (let [proof (first
                    (kernel/prove
                      (ast/and-form
                        (ast/neq-lit (ast/var-term x) (ast/app-term 'zero))
                        (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))
                      1))]
        (is (proof/contains-step? proof 'neq-store))
        (is (proof/contains-step? proof 'eq-step))
        (is (proof/contains-step? proof 'neq-close))))))

(deftest proof-terms-record-saved-literals-before-a-later-closure
  (testing "the structural proof tree retains savefml when a literal closes later on the branch"
    (let [proof (first
                  (kernel/prove
                    (ast/and-form
                      (ast/pos-lit (ast/app-term 'p))
                      (ast/and-form
                        (ast/pos-lit (ast/app-term 'q))
                        (ast/neg-lit (ast/app-term 'p))))
                    1))]
      (is (proof/contains-step? proof 'savefml))
      (is (proof/contains-step? proof 'close)))))

(deftest proof-terms-distinguish-positive-and-negative-procedure-calls
  (testing "procedure-call closure leaves explicit pos-call and neg-call tags"
    (let [pos-call-proof (first
                           (kernel/prove-program
                             (false-body-program)
                             (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero)))
                             1))
          neg-call-proof (first
                           (kernel/prove-program
                             (exact-zero-program)
                             (ast/neg-lit (ast/app-term 'q (ast/app-term 'zero)))
                             1))]
      (is (proof/contains-step? pos-call-proof 'pos-call))
      (is (or (proof/contains-step? neg-call-proof 'neg-call)
              (proof/contains-step? neg-call-proof 'neg-call-guarded-alt)
              (proof/contains-step? neg-call-proof 'neg-call-alt)))
      (is (proof/contains-step? pos-call-proof 'refl-close))
      (is (proof/contains-step? neg-call-proof 'refl-close)))))
