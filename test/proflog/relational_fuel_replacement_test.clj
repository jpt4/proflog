(ns proflog.relational-fuel-replacement-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.language :as language]
            [proflog.pretty :as pretty]
            [proflog.query :as query]
            [proflog.relational-arithmetic :as arith]
            [proflog.relational-fuel-adapter-probe :as adapter]))

(def production-step-fuelo
  (var-get #'support/step-fuelo))

(def replacement-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'step 2
                 'jump 2}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(def closing-conjunction
  (ast/and-form
    (ast/pos-lit (ast/app-term 'p))
    (ast/neg-lit (ast/app-term 'p))))

(defn replacement-program
  []
  (ast/nom x y z
    (language/compile-program
      replacement-language
      [(ast/clause 'step [x y]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's (ast/var-term y)))
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's
                                               (ast/app-term 's
                                                             (ast/var-term y))))))
       (ast/clause 'jump [x y]
                   (ast/exists-form z
                                    (ast/and-form
                                      (ast/pos-lit (ast/app-term 'step
                                                                 (ast/var-term x)
                                                                 (ast/var-term z)))
                                      (ast/pos-lit (ast/app-term 'step
                                                                 (ast/var-term z)
                                                                 (ast/var-term y))))))])))

(defn answer-shape
  [answer-vars records]
  (mapv (fn [record]
          {:bindings (mapv (fn [answer-var]
                             [answer-var (answers/binding-term record answer-var)])
                           answer-vars)
           :residuals (:residuals record)})
        records))

(defn query-shape-with-step
  [step-fuelo query answer-vars opts]
  (with-redefs [support/step-fuelo step-fuelo]
    (answer-shape
      answer-vars
      (answers/query-answers
        (replacement-program)
        query
        answer-vars
        opts))))

(defn query-records-with-step
  [step-fuelo query answer-vars opts]
  (with-redefs [support/step-fuelo step-fuelo]
    (answers/query-answers
      (replacement-program)
      query
      answer-vars
      opts)))

(defn proof-sample-with-step
  [step-fuelo formula fuel]
  (with-redefs [support/step-fuelo step-fuelo]
    (run 1 [proof]
      (kernel/proveo formula '() '() '() fuel proof))))

(defn query-succeeds-with-step
  [step-fuelo program query fuel]
  (with-redefs [support/step-fuelo step-fuelo]
    (query/query-succeeds program query 1 fuel)))

(defn query-fails-with-step
  [step-fuelo program query fuel]
  (with-redefs [support/step-fuelo step-fuelo]
    (query/query-fails program query 1 fuel)))

(defn ground-decimals
  [records binding-nom]
  (->> records
       (keep #(pretty/peano->int (answers/binding-term % binding-nom)))
       vec))

(defn kernel-fuel-fixtures
  []
  (ast/nom x
    [{:label "direct complementary closure"
      :formula closing-conjunction}
     {:label "beta split over two closing branches"
      :formula (ast/or-form
                 closing-conjunction
                 (ast/and-form
                   (ast/pos-lit (ast/app-term 'q))
                   (ast/neg-lit (ast/app-term 'q))))}
     {:label "gamma instantiation"
      :formula (ast/and-form
                 (ast/forall-form x
                                  (ast/pos-lit
                                    (ast/app-term 'value (ast/var-term x))))
                 (ast/neg-lit (ast/app-term 'value (ast/app-term 'zero))))}
     {:label "delta witness"
      :formula (ast/exists-form
                 x
                 (ast/and-form
                   (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                   (ast/neg-lit (ast/app-term 'value (ast/var-term x)))))}]))

(deftest direct-step-replacement-shows-where-bit-list-fuel-leaks
  (testing "production finite-domain fuel synthesizes host integers"
    (is (= '(1)
           (run 1 [fuel]
             (production-step-fuelo fuel 0))))
    (is (= '(0)
           (run 1 [next-fuel]
             (production-step-fuelo 1 next-fuel)))))
  (testing "the relational replacement accepts host integer boundaries but synthesizes internal bit-list fuel"
    (is (= (list (arith/build-num 1))
           (run 1 [fuel]
             (adapter/step-fuelo fuel 0))))
    (is (= (list (arith/build-num 0))
           (run 1 [next-fuel]
             (adapter/step-fuelo 1 next-fuel))))
    (is (= (list (arith/build-num 2))
           (run 1 [fuel]
             (adapter/step-fuelo fuel (arith/build-num 1))))))
  (testing "the unbounded sentinel stays shared across both relations"
    (is (= '(nil)
           (run 1 [next-fuel]
             (production-step-fuelo nil next-fuel))))
    (is (= '(nil)
           (run 1 [next-fuel]
             (adapter/step-fuelo nil next-fuel))))))

(deftest fixed-kernel-fuel-boundaries-match-production-proof-shapes
  (testing "a fixed public integer fuel boundary can run the ordinary kernel through the replacement"
    (doseq [fuel [0 1 2 nil]]
      (let [production (with-redefs [support/step-fuelo production-step-fuelo]
                         (run 1 [proof]
                           (kernel/proveo closing-conjunction '() '() '() fuel proof)))
            replacement (with-redefs [support/step-fuelo adapter/step-fuelo]
                          (run 1 [proof]
                            (kernel/proveo closing-conjunction '() '() '() fuel proof)))]
        (is (= production replacement)
            (str "kernel proof mismatch at fuel " fuel))))))

(deftest kernel-fuel-slices-match-production-across-rule-shapes
  (doseq [{:keys [label formula]} (kernel-fuel-fixtures)
          fuel (range 0 7)]
    (testing (str label " at fuel " fuel)
      (is (= (proof-sample-with-step production-step-fuelo formula fuel)
             (proof-sample-with-step adapter/step-fuelo formula fuel))))))

(deftest direct-query-proof-surface-matches-production
  (let [program (replacement-program)
        succeeds-query (ast/pos-lit
                         (ast/app-term 'step (numeral 2) (numeral 1)))
        fails-query (ast/pos-lit
                      (ast/app-term 'step (numeral 0) (numeral 1)))]
    (doseq [fuel [0 1 2 4 8]]
      (testing (str "query succeeds surface at fuel " fuel)
        (is (= (query-succeeds-with-step production-step-fuelo program succeeds-query fuel)
               (query-succeeds-with-step adapter/step-fuelo program succeeds-query fuel))))
      (testing (str "query fails surface at fuel " fuel)
        (is (= (query-fails-with-step production-step-fuelo program fails-query fuel)
               (query-fails-with-step adapter/step-fuelo program fails-query fuel)))))))

(deftest answer-mode-partial-and-reverse-synthesis-match-fd-record-shapes
  (testing "partial step synthesis exports the same public answer bindings"
    (ast/nom x
      (let [query (ast/pos-lit
                    (ast/app-term 'step (ast/var-term x) (numeral 1)))
            opts {:proof-limit 3
                  :max-raw-proof-limit 24
                  :fuel 8
                  :call-depth 1}
            production-records (query-records-with-step
                                 production-step-fuelo query [x] opts)
            replacement-records (query-records-with-step
                                  adapter/step-fuelo query [x] opts)]
        (is (= [2 3]
               (ground-decimals production-records x)))
        (is (= (answer-shape [x] production-records)
               (answer-shape [x] replacement-records))))))
  (testing "reverse step synthesis with an extra constraint keeps the same result"
    (ast/nom y
      (let [query (ast/and-form
                    (ast/pos-lit
                      (ast/app-term 'step (numeral 3) (ast/var-term y)))
                    (ast/neq-lit (ast/var-term y) (numeral 2)))
            opts {:proof-limit 3
                  :max-raw-proof-limit 24
                  :fuel 8
                  :call-depth 1}
            production-records (query-records-with-step
                                 production-step-fuelo query [y] opts)
            replacement-records (query-records-with-step
                                  adapter/step-fuelo query [y] opts)]
        (is (= [1]
               (ground-decimals production-records y)))
        (is (= (answer-shape [y] production-records)
               (answer-shape [y] replacement-records)))))))

(deftest answer-mode-composed-query-crosses-the-recursive-call-surface
  (testing "a two-call partial synthesis query keeps the same exported shape"
    (ast/nom x
      (let [query (ast/pos-lit
                    (ast/app-term 'jump (ast/var-term x) (numeral 0)))
            opts {:proof-limit 4
                  :max-raw-proof-limit 32
                  :fuel 12
                  :call-depth 2}
            production-records (query-records-with-step
                                 production-step-fuelo query [x] opts)
            replacement-records (query-records-with-step
                                  adapter/step-fuelo query [x] opts)]
        (is (= #{2 3 4}
               (set (ground-decimals production-records x))))
        (is (= (answer-shape [x] production-records)
               (answer-shape [x] replacement-records)))))))

(deftest open-kernel-fuel-keeps-the-first-public-answer-shape
  (testing "when fuel itself is open, both paths first expose the nil unbounded sentinel"
    (let [production (with-redefs [support/step-fuelo production-step-fuelo]
                       (run 1 [q]
                         (fresh [fuel proof]
                           (kernel/proveo closing-conjunction '() '() '() fuel proof)
                           (== [fuel proof] q))))
          replacement (with-redefs [support/step-fuelo adapter/step-fuelo]
                        (run 1 [q]
                          (fresh [fuel proof]
                            (kernel/proveo closing-conjunction '() '() '() fuel proof)
                            (== [fuel proof] q))))]
      (is (= production replacement))
      (is (= nil (ffirst production))))))
