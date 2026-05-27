(ns proflog.kernel.dispatch-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel.first-order :as first-order]
            [proflog.kernel.propositional :as propositional]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.pelletier-test :as pelletier]
            [proflog.proof :as proof]))

(defn closed-propositional-formula
  []
  (ast/and-form
    (ast/pos-lit (ast/app-term 'p))
    (ast/neg-lit (ast/app-term 'p))))

(defn contains-any-step?
  [proof & tags]
  (boolean
    (some #(proof/contains-step? proof %) tags)))

(deftest pure-propositional-proof-entry-uses-propositional-component
  (testing "kernel/prove dispatches theorem-style pure propositional formulas"
    (let [propositional-calls (atom 0)
          first-order-calls (atom 0)
          original-propositional-prove propositional/prove
          original-first-order-prove first-order/prove]
      (with-redefs [propositional/prove
                    (fn [& args]
                      (swap! propositional-calls inc)
                      (apply original-propositional-prove args))
                    first-order/prove
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-prove args))]
        (is (seq (kernel/prove (closed-propositional-formula) 1 2)))
        (is (= 1 @propositional-calls))
        (is (zero? @first-order-calls))))))

(deftest equality-free-first-order-proof-entry-uses-first-order-component
  (testing "kernel/prove dispatches quantified theorem formulas to the first-order layer"
    (let [propositional-calls (atom 0)
          first-order-calls (atom 0)
          original-propositional-prove propositional/prove
          original-first-order-prove first-order/prove]
      (with-redefs [propositional/prove
                    (fn [& args]
                      (swap! propositional-calls inc)
                      (apply original-propositional-prove args))
                    first-order/prove
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-prove args))]
        (is (seq
              (kernel/prove
                (pelletier/theorem-branch (pelletier/problem-18))
                1)))
        (is (zero? @propositional-calls))
        (is (= 1 @first-order-calls))))))

(deftest equality-bearing-formulas-stay-on-the-full-kernel
  (testing "equality formulas do not enter the propositional component"
    (let [propositional-calls (atom 0)
          first-order-calls (atom 0)
          original-propositional-prove propositional/prove
          original-first-order-prove first-order/prove]
      (with-redefs [propositional/prove
                    (fn [& args]
                      (swap! propositional-calls inc)
                      (apply original-propositional-prove args))
                    first-order/prove
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-prove args))]
        (is (seq (kernel/prove
                   (ast/eq-lit (ast/app-term 'a) (ast/app-term 'b))
                   1)))
        (is (zero? @propositional-calls))
        (is (zero? @first-order-calls))))))

(def nullary-call-language
  (language/language
    {:constants ['a]
     :relations {'p 0}}))

(defn nullary-call-program
  []
  (language/compile-program
    nullary-call-language
    [(ast/clause 'p []
                 (ast/neq-lit (ast/app-term 'a)
                              (ast/app-term 'a)))]))

(deftest equality-fragment-program-bearing-proof-search-enters-profiled-layer
  (testing "equality-fragment program calls keep procedure-call proof tags and enter the profiled layer"
    (let [propositional-calls (atom 0)
          first-order-calls (atom 0)
          original-propositional-prove propositional/prove
          original-first-order-prove first-order/prove
          original-propositional-proveo propositional/proveo
          original-first-order-proveo first-order/proveo]
      (with-redefs [propositional/prove
                    (fn [& args]
                      (swap! propositional-calls inc)
                      (apply original-propositional-prove args))
                    first-order/prove
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-prove args))
                    propositional/proveo
                    (fn [& args]
                      (swap! propositional-calls inc)
                      (apply original-propositional-proveo args))
                    first-order/proveo
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-proveo args))]
        (let [proof (first
                      (kernel/prove-program
                        (nullary-call-program)
                        (ast/pos-lit (ast/app-term 'p))
                        1))]
          (is proof)
          (is (proof/contains-step? proof 'pos-call))
          (is (proof/contains-step? proof 'profiled))
          (is (proof/contains-step? proof 'equality-fragment))
          (is (zero? @propositional-calls))
          (is (zero? @first-order-calls)))))))

(def profiled-propositional-language
  (language/language
    {:relations {'fast-prop 0
                 'p 0}}))

(defn profiled-propositional-program
  []
  (language/compile-program
    profiled-propositional-language
    [(ast/clause 'fast-prop []
                 (normalize/negate-formula
                   (closed-propositional-formula)))]))

(deftest program-subbranch-can-dispatch-to-propositional-component
  (testing "a pure propositional procedure-call residual closes through proveo"
    (let [host-prove-calls (atom 0)
          proveo-calls (atom 0)
          original-proveo propositional/proveo]
      (with-redefs [propositional/prove
                    (fn [& _]
                      (swap! host-prove-calls inc)
                      (throw (ex-info "Program dispatch must not call propositional/prove"
                                      {})))
                    propositional/proveo
                    (fn [& args]
                      (swap! proveo-calls inc)
                      (apply original-proveo args))]
        (let [proof (first
                      (kernel/prove-program
                        (profiled-propositional-program)
                        (ast/neg-lit (ast/app-term 'fast-prop))
                        1
                        4))]
          (is proof)
          (is (contains-any-step? proof 'neg-call 'neg-call-guarded-alt 'neg-call-alt))
          (is (proof/contains-step? proof 'profiled))
          (is (proof/contains-step? proof 'propositional))
          (is (pos? @proveo-calls))
          (is (zero? @host-prove-calls)))))))

(def profiled-first-order-language
  (language/language
    {:relations {'fast-fo 0
                 'p 1}}))

(defn closed-first-order-formula
  []
  (ast/nom x
    (ast/forall-form x
      (ast/and-form
        (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
        (ast/neg-lit (ast/app-term 'p (ast/var-term x)))))))

(defn profiled-first-order-program
  []
  (language/compile-program
    profiled-first-order-language
    [(ast/clause 'fast-fo []
                 (normalize/negate-formula
                   (closed-first-order-formula)))]))

(deftest program-subbranch-can-dispatch-to-first-order-component
  (testing "an equality-free first-order procedure-call residual closes through proveo"
    (let [host-prove-calls (atom 0)
          proveo-calls (atom 0)
          original-proveo first-order/proveo]
      (with-redefs [first-order/prove
                    (fn [& _]
                      (swap! host-prove-calls inc)
                      (throw (ex-info "Program dispatch must not call first-order/prove"
                                      {})))
                    first-order/proveo
                    (fn [& args]
                      (swap! proveo-calls inc)
                      (apply original-proveo args))]
        (let [proof (first
                      (kernel/prove-program
                        (profiled-first-order-program)
                        (ast/neg-lit (ast/app-term 'fast-fo))
                        1
                        4))]
          (is proof)
          (is (contains-any-step? proof 'neg-call 'neg-call-guarded-alt 'neg-call-alt))
          (is (proof/contains-step? proof 'profiled))
          (is (proof/contains-step? proof 'first-order))
          (is (pos? @proveo-calls))
          (is (zero? @host-prove-calls)))))))
