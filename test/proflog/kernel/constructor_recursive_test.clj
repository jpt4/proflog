(ns proflog.kernel.constructor-recursive-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.language :as language]
            [proflog.list-kernel-matrix-probe :as matrix]
            [proflog.proof :as proof]))

(defn- record-bindings-for
  [record answer-vars]
  (mapv (fn [answer-var]
          [answer-var (some (fn [[binding-nom term]]
                              (when (= binding-nom answer-var)
                                term))
                            (:bindings record))])
        answer-vars))

(defn- matrix-layer-result
  [case-id]
  (let [program (matrix/list-program)
        {:keys [kind query answer-vars target-bindings fuel] :as config}
        (matrix/case-config case-id)]
    (case kind
      :ground
      (assoc (select-keys config [:id :kind :description])
             :target-found?
             (constructor-recursive/query-succeeds?
               program
               query
               {:fuel fuel}))

      :answer
      (let [records (constructor-recursive/query-records
                      program
                      query
                      answer-vars
                      {:fuel (max 96 fuel)
                       :limit (max 1 (count target-bindings))})
            closed-bindings (->> records
                                 (filter #(empty? (:residuals %)))
                                 (map #(record-bindings-for % answer-vars))
                                 set)
            found-targets (set (filter closed-bindings target-bindings))]
        (assoc (select-keys config [:id :kind :description])
               :target-found? (= target-bindings found-targets)
               :found-target-count (count found-targets)
               :target-count (count target-bindings)
               :records records)))))

(def peano-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'peel 2}}))

(defn peano-term
  [n]
  (nth (iterate #(ast/app-term 's %) (ast/app-term 'zero)) n))

(defn peel-body
  [x y predecessor]
  (ast/or-form
    (ast/eq-lit (ast/var-term x) (ast/var-term y))
    (ast/exists-form
      predecessor
      (ast/and-form
        (ast/eq-lit (ast/var-term x)
                    (ast/app-term 's (ast/var-term predecessor)))
        (ast/pos-lit
          (ast/app-term 'peel
                        (ast/var-term predecessor)
                        (ast/var-term y)))))))

(defn peel-program
  []
  (ast/nom x y predecessor
    (language/compile-program
      peano-language
      [(ast/clause 'peel [x y] (peel-body x y predecessor))])))

(deftest ^:constructor-recursive non-list-constructor-recursion-produces-layer-proof
  (testing "the constructor-recursive layer uses guarded IR without list-family names"
    (let [records (constructor-recursive/query-records
                    (peel-program)
                    (ast/pos-lit
                      (ast/app-term 'peel
                                    (peano-term 3)
                                    (peano-term 0)))
                    []
                    {:fuel 16
                     :limit 1})
          proof (-> records first :proofs first)]
      (is (= 1 (count records)))
      (is (proof/contains-step? proof 'constructor-recursive))
      (is (proof/contains-step? proof 'constructor-recursive-call))
      (is (proof/contains-step? proof 'constructor-recursive-guard)))))

(deftest ^:constructor-recursive residual-settlement-refines-deferred-negative-calls
  (testing "branch-local residuals can be discharged generically after export"
    (let [program (matrix/list-program)
          a matrix/a
          b matrix/b]
      (ast/nom r mid
        (let [record {:bindings [[r (ast/var-term r)]]
                      :residuals [(ast/neg-lit
                                    (ast/app-term 'reverse
                                                  (matrix/list-term b)
                                                  (ast/var-term mid)))
                                  (ast/neg-lit
                                    (ast/app-term 'append
                                                  (ast/var-term mid)
                                                  (matrix/list-term a)
                                                  (ast/var-term r)))]
                      :proofs ['(deferred-frontier)]}
              settled (constructor-recursive/settle-record
                        program
                        record
                        {:fuel 96})]
          (is (empty? (:residuals settled)))
          (is (= (matrix/list-term b a)
                 (some (fn [[binding-nom term]]
                         (when (= binding-nom r)
                           term))
                       (:bindings settled))))
          (is (proof/contains-step?
                (last (:proofs settled))
                'constructor-recursive-residual-settlement)))))))

(deftest ^:constructor-recursive constructor-recursive-layer-improves-multiple-matrix-rows
  (testing "the prototype closes append and reverse rows through one generic layer"
    (doseq [case-id [:append-forward-flat-3
                     :append-output-flat
                     :append-suffix-flat
                     :append-prefix-flat
                     :append-inverse-flat
                     :reverse-output-flat
                     :reverse-input-flat
                     :reverse-output-nested
                     :reverse-output-nested-longer
                     :reverse-partial-output-tail]]
      (let [result (matrix-layer-result case-id)]
        (is (:target-found? result)
            (str case-id " should close through constructor-recursive layer, got "
                 (pr-str (dissoc result :records))))))))
