(ns proflog.legacy-hard-families-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.gv-probe :as gv-probe]
            [proflog.hard-family-overlay :as hard-family-overlay]
            [proflog.language :as language]
            [proflog.query :as query]))

(def fd-language
  (language/language
    {:constants ['red 'green 'blue]
     :relations {'warm-unique 0}}))

(defn warm-unique-program
  []
  (ast/nom x y
    (language/compile-program
      fd-language
      [(ast/clause 'warm-unique
                   []
                   (ast/forall-form
                     x
                     (ast/forall-form
                       y
                       (ast/or-form
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'red))
                         (ast/or-form
                           (ast/neq-lit (ast/var-term y) (ast/app-term 'red))
                           (ast/eq-lit (ast/var-term x)
                                       (ast/var-term y)))))))])))

(deftest z1-full-assoc-truth-is-now-resolved-by-the-profiled-kernel-slice
  (testing "the trivial-group associativity truth query now resolves on the profiled kernel path"
    (let [{:keys [program relation]}
          ((deref #'gv-probe/scenario-config) "z1-full-assoc-truth")
          query-formula (ast/pos-lit (ast/app-term relation))]
      (is (= :succeeds
             (query/query-status
               program
               query-formula
               {:timeout-ms 2000
                :proof-limit 1
                :poll-ms 0}))))))

(deftest hard-family-overlay-resolves-z1-full-assoc-truth
  (testing "the named hard-family overlay can recover the trivial-group associativity truth query"
    (let [{:keys [program relation]}
          ((deref #'gv-probe/scenario-config) "z1-full-assoc-truth")
          query-formula (ast/pos-lit (ast/app-term relation))]
      (is (= :succeeds
             (hard-family-overlay/query-status
               program
               query-formula
               {:timeout-ms 2000
                :proof-limit 1
                :poll-ms 5}))))))

(deftest hard-family-overlay-resolves-fd-warm-unique-truth
  (testing "the named hard-family overlay can recover the representative finite-domain uniqueness query"
    (let [program (warm-unique-program)
          query-formula (ast/pos-lit (ast/app-term 'warm-unique))]
      (is (= :succeeds
             (hard-family-overlay/query-status
               program
               query-formula
               {:timeout-ms 2000
                :proof-limit 1
                :poll-ms 5}))))))
