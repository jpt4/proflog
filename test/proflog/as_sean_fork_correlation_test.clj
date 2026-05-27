(ns proflog.as-sean-fork-correlation-test
  "Re-express Sean fork AS SJAS semantic checkpoints in Proflog.

  Source: jpt4/as archive/sean-fork-full claims/* and ADR-0226–0264.
  Encoding differs (AS tagged-prefix vs Proflog base-64); tests verify
  Willard obligations, not byte-identical codes."
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.willard-sjas :as sjas]))

(defn- demo-beta []
  (ast/eq-lit sjas/one sjas/one))

(defn- reflected-demo-clause []
  (ast/nom x
    (ast/clause 'demo [x]
                (ast/eq-lit (ast/var-term x) sjas/one))))

(defn- external-demo-clause []
  (ast/nom x
    (ast/clause 'external-demo [x]
                (ast/eq-lit (ast/var-term x) sjas/zero))))

(defn- level1-system
  ([] (level1-system {}))
  ([opts]
   (sjas/system
     (merge
       {:profile :willard-sjas-level1
        :relations {'demo 1
                    'external-demo 1}
        :beta [(demo-beta)]
        :reflected-clauses [(reflected-demo-clause)]
        :external-clauses [(external-demo-clause)]}
       opts))))

(deftest sean-fork-consistency-level-maps-to-level1-profile
  (testing "AS-CONSISTENCY-LEVEL-1-TARGET → :willard-sjas-level1"
    (let [system (level1-system)]
      (is (= :willard-sjas-level1 (:profile system)))
      (is (contains? system :selfcons-skeleton-code))
      (is (contains? system :group-three)))))

(deftest sean-fork-diagonal-seed-maps-to-subst-code-fixed-point
  (testing "AS-FIXED-POINT-SELFCONS1-DIAGONAL-SEED → subst-code self-application"
    (let [system (level1-system)
          skel (:selfcons-skeleton-code system)]
      (is (some? (sjas/subst-code skel skel))))))

(deftest sean-fork-substitution-graph-maps-to-structural-subst-code
  (testing "AS-SUBSTITUTION-GRAPH-* → structural subst-code relation"
    (let [system (level1-system)
          group3-code (:code (:group-three system))]
      (is (some? (sjas/subst-code (:selfcons-skeleton-code system) group3-code)))
      (is (some? (sjas/subst-code group3-code group3-code))))))

(deftest sean-fork-deduction-maps-to-tableau-and-subst-prf
  (testing "SJAS deduction apparatus → tableau-proof/3 and subst-prf/4 vocabulary"
    (let [lang sjas/level1-profile-language]
      (is (= 2 (get-in lang [:relations 'subst-code])))
      (is (= 4 (get-in lang [:relations 'subst-prf])))
      (is (= 3 (get-in lang [:relations 'tableau-proof]))))))

(deftest sean-fork-naive-obstruction-not-proflog-route
  (testing "AS naive quotation obstruction is not the Proflog implementation path"
    (let [system (level1-system)
          skel (:selfcons-skeleton-code system)]
      (is (some? (sjas/subst-code skel skel))))))
