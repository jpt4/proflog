(ns proflog.program-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [run]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]
            [proflog.program :as program]
            [proflog.query :as query]))

(def simple-language
  (language/language
    {:constants ['zero 'one]
     :functions {'succ 1}
     :relations {'p 1
                 'q 1}}))

(defn simple-program
  []
  (ast/nom x
    (language/compile-program
      simple-language
      [(ast/clause 'p [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))])))

(def isolation-language
  (language/language
    {:constants ['zero]
     :relations {'p 1
                 'q 1}}))

(defn isolation-program
  []
  (ast/nom x
    (language/compile-program
      isolation-language
      [(ast/clause 'p [x]
                   (ast/pos-lit (ast/app-term 'q (ast/var-term x))))])))

(def pair-language
  (language/language
    {:constants ['zero 'one]
     :relations {'pair-eq 2}}))

(defn pair-program
  []
  (ast/nom x y
    (language/compile-program
      pair-language
      [(ast/clause 'pair-eq [x y]
                   (ast/eq-lit (ast/var-term x)
                               (ast/var-term y)))])))

(deftest call-clauseo-binds-compiled-parameters-to-actual-arguments
  (testing "the program layer exposes the compiled body plus an argument-binding environment"
    (let [program (simple-program)
          actual (ast/app-term 'succ (ast/app-term 'zero))
          [[env body neg-body]]
          (run 1 [env body neg-body]
            (program/call-clauseo
              program
              (ast/app-term 'p actual)
              env
              body
              neg-body))
          bound-param (ffirst env)]
      (is (= 1 (count env)))
      (is (= actual (second (first env))))
      (is (= (ast/eq-lit (ast/var-term bound-param) (ast/app-term 'zero))
             body))
      (is (= (ast/neq-lit (ast/var-term bound-param) (ast/app-term 'zero))
             neg-body)))))

(deftest call-clauseo-accepts-registry-only-profile-metadata
  (testing "procedure lookup keeps working when profile metadata lives only in a registry"
    (let [program (assoc (simple-program)
                         :sjas/registry (atom {:sjas/system-code 'dummy-system}))
          actual (ast/app-term 'succ (ast/app-term 'zero))
          results (run 1 [env body neg-body]
                    (program/call-clauseo
                      program
                      (ast/app-term 'p actual)
                      env
                      body
                      neg-body))]
      (is (not-any? #(contains? program %)
                    [:sjas/system-code :sjas/fact-atoms :sjas/proof-targets])
          "profile-bearing programs must not require stale SJAS side-table keys")
      (is (= 1 (count results)))
      (when-let [[env body neg-body] (first results)]
        (let [bound-param (ffirst env)]
          (is (= actual (second (first env))))
          (is (= (ast/eq-lit (ast/var-term bound-param) (ast/app-term 'zero))
                 body))
          (is (= (ast/neq-lit (ast/var-term bound-param) (ast/app-term 'zero))
                 neg-body)))))))

(deftest call-clause-with-alternativeso-exposes-compiled-guard-views
  (testing "procedure lookup can return top-level alternatives without changing the ordinary body"
    (ast/nom x
      (let [program (language/compile-program
                      simple-language
                      [(ast/clause 'p [x]
                                   (ast/or-form
                                     (ast/eq-lit (ast/var-term x)
                                                 (ast/app-term 'zero))
                                     (ast/eq-lit (ast/var-term x)
                                                 (ast/app-term 'one))))])
            actual (ast/app-term 'zero)
            [[env body neg-body alternatives negated-alternatives]]
            (run 1 [env body neg-body alternatives negated-alternatives]
              (program/call-clause-with-alternativeso
                program
                (ast/app-term 'p actual)
                env
                body
                neg-body
                alternatives
                negated-alternatives))
            bound-param (ffirst env)]
        (is (= (ast/or-form
                 (ast/eq-lit (ast/var-term bound-param) (ast/app-term 'zero))
                 (ast/eq-lit (ast/var-term bound-param) (ast/app-term 'one)))
               body))
        (is (= [(ast/eq-lit (ast/var-term bound-param) (ast/app-term 'zero))
                (ast/eq-lit (ast/var-term bound-param) (ast/app-term 'one))]
               (vec alternatives)))
        (is (= [(ast/neq-lit (ast/var-term bound-param) (ast/app-term 'zero))
                (ast/neq-lit (ast/var-term bound-param) (ast/app-term 'one))]
               (vec negated-alternatives)))))))

(deftest call-clause-with-guarded-alternativeso-exposes-ir
  (testing "procedure lookup can expose guarded IR without changing ordinary call binding"
    (ast/nom x
      (let [program (language/compile-program
                      simple-language
                      [(ast/clause 'p [x]
                                   (ast/and-form
                                     (ast/eq-lit (ast/var-term x)
                                                 (ast/app-term 'zero))
                                     (ast/and-form
                                       (ast/pos-lit
                                         (ast/app-term 'p (ast/var-term x)))
                                       (ast/pos-lit
                                         (ast/app-term 'q (ast/var-term x))))))])
            actual (ast/app-term 'zero)
            [[env body neg-body alternatives negated-alternatives guarded-alternatives]]
            (run 1 [env body neg-body alternatives negated-alternatives guarded-alternatives]
              (program/call-clause-with-guarded-alternativeso
                program
                (ast/app-term 'p actual)
                env
                body
                neg-body
                alternatives
                negated-alternatives
                guarded-alternatives))
            bound-param (ffirst env)
            guarded (first guarded-alternatives)
            expected-guard (ast/eq-lit (ast/var-term bound-param)
                                       (ast/app-term 'zero))
            expected-call (ast/pos-lit
                            (ast/app-term 'p (ast/var-term bound-param)))
            expected-residual (ast/pos-lit
                                (ast/app-term 'q (ast/var-term bound-param)))]
        (is (= actual (second (first env))))
        (is (= [(ast/and-form
                  expected-guard
                  (ast/and-form expected-call expected-residual))]
               (vec alternatives)))
        (is (= [expected-guard]
               (vec (:guards guarded))))
        (is (= [expected-call]
               (vec (:calls guarded))))
        (is (= [expected-residual]
               (vec (:residuals guarded))))))))

(deftest positive-and-negative-procedure-calls-close-literals
  (testing "procedure calls close positive literals when bodies fail and negative literals when bodies succeed"
    (let [program (simple-program)]
      (is (seq
            (kernel/prove-program
              program
              (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
              1)))
      (is (seq
            (kernel/prove-program
              program
              (ast/neg-lit (ast/app-term 'p (ast/app-term 'zero)))
              1)))
      (is (= :succeeds
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero)))
               {:timeout-ms 1000})))
      (is (= :fails
             (query/query-status
               program
               (ast/pos-lit (ast/app-term 'p (ast/app-term 'one)))
               {:timeout-ms 1000}))))))

(deftest procedure-calls-do-not-borrow-the-caller-branch-context
  (testing "subsidiary tableaux stay isolated from unrelated literals on the caller branch"
    (let [program (isolation-program)]
      (is (empty?
            (kernel/prove-program
              program
              (ast/and-form
                (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero)))
                (ast/neg-lit (ast/app-term 'q (ast/app-term 'zero))))
              1)))
      (is (empty?
            (kernel/prove-program
              program
              (ast/and-form
                (ast/neg-lit (ast/app-term 'p (ast/app-term 'zero)))
                (ast/pos-lit (ast/app-term 'q (ast/app-term 'zero))))
              1))))))

(deftest multi-argument-procedure-calls-respect-the-clause-environment
  (testing "compiled programs can bind and evaluate multi-argument relations"
    (let [program (pair-program)]
      (is (seq
            (kernel/prove-program
              program
              (ast/pos-lit
                (ast/app-term 'pair-eq
                              (ast/app-term 'zero)
                              (ast/app-term 'one)))
              1)))
      (is (seq
            (kernel/prove-program
              program
              (ast/neg-lit
                (ast/app-term 'pair-eq
                              (ast/app-term 'zero)
                              (ast/app-term 'zero)))
              1))))))

(deftest procedure-calls-do-not-close-when-the-clause-body-stays-satisfiable
  (testing "plain calls only close when the subsidiary tableau actually closes"
    (let [program (simple-program)]
      (is (empty?
            (kernel/prove-program
              program
              (ast/pos-lit (ast/app-term 'p (ast/app-term 'zero)))
              1)))
      (is (empty?
            (kernel/prove-program
              program
              (ast/neg-lit (ast/app-term 'p (ast/app-term 'one)))
              1))))))
