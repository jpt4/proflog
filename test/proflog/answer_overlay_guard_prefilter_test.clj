(ns proflog.answer-overlay-guard-prefilter-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.answer-overlay :as answer-overlay]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.proof :as proof]))

(def guard-prefilter-language
  (language/language
    {:constants ['zero 'one]
     :relations {'p 1
                 'sentinel 1}}))

(defn- guard-prefilter-program
  []
  (ast/nom x
    (language/compile-program
      guard-prefilter-language
      [(ast/clause
         'p
         [x]
         (ast/or-form
           (ast/and-form
             (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
             (ast/pos-lit (ast/app-term 'sentinel (ast/var-term x))))
           (ast/eq-lit (ast/var-term x) (ast/app-term 'one))))
       (ast/clause
         'sentinel
         [x]
         (ast/pos-lit (ast/app-term 'sentinel (ast/var-term x))))])))

(defn- guarded-alternatives-for
  [program relation]
  (:guarded-alternatives
    (some #(when (= relation (:relation %)) %)
          (:guarded-clause-list program))))

(defn- tree-contains?
  [tree needle]
  (boolean
    (some #{needle}
          (tree-seq coll? seq tree))))

(deftest structural-guard-prefilter-rejects-impossible-alternative-before-recursion
  (testing "constructor-clashing guards are filtered before recursive calls are descended"
    (let [program (guard-prefilter-program)
          guarded-alternatives (guarded-alternatives-for program 'p)
          sentinel-first (first guarded-alternatives)
          results (run 1 [sigma-out neqs-out proof]
                    (answer-overlay/continue-structural-residualso
                      [(ast/neg-lit
                         (ast/app-term 'p (ast/app-term 'one)))]
                      '()
                      '()
                      sigma-out
                      '()
                      neqs-out
                      program
                      6
                      proof))
          [sigma-out neqs-out proof] (first results)]
      (is (seq results))
      (is (= 'sentinel
             (some-> sentinel-first :calls first second second))
          "the first alternative carries a recursive sentinel call")
      (is (= '() sigma-out))
      (is (= '() neqs-out))
      (is (proof/contains-step? proof 'structural-residual-guard-prefilter))
      (is (proof/contains-step? proof 'structural-residual-guard-prefilter-eq))
      (is (not (tree-contains? proof 'sentinel))
          "the sentinel call should not appear in the successful proof"))))

(deftest structural-guard-prefilter-rejects-impossible-equality-and-keeps-viable-guards
  (testing "the guard prefilter itself is a relational gate over guarded formulas"
    (let [prefilter (deref #'answer-overlay/prefilter-structural-guardso)
          impossible-results
          (run 1 [sigma-out proof]
            (prefilter
              [(ast/eq-lit (ast/app-term 'one) (ast/app-term 'zero))]
              '()
              '()
              '()
              sigma-out
              '()
              proof))
          viable-results
          (run 1 [sigma-out proof]
            (prefilter
              [(ast/eq-lit (ast/app-term 'one) (ast/app-term 'one))]
              '()
              '()
              '()
              sigma-out
              '()
              proof))
          neq-results
          (run 1 [sigma-out proof]
            (prefilter
              [(ast/neq-lit (ast/app-term 'one) (ast/app-term 'zero))]
              '()
              '()
              '()
              sigma-out
              '()
              proof))]
      (is (empty? impossible-results))
      (is (= 1 (count viable-results)))
      (is (= '() (ffirst viable-results)))
      (is (proof/contains-step?
            (second (first viable-results))
            'structural-residual-guard-prefilter-eq))
      (is (= 1 (count neq-results)))
      (is (proof/contains-step?
            (second (first neq-results))
            'structural-residual-guard-prefilter-neq-rigid)))))
