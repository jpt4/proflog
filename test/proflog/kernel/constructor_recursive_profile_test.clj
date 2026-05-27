(ns proflog.kernel.constructor-recursive-profile-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel.constructor-recursive-profile :as profile]
            [proflog.language :as language]
            [proflog.list-kernel-matrix-probe :as matrix]
            [proflog.pretty :as pretty]
            [proflog.proof :as proof]))

(defn app
  [sym & args]
  (apply ast/app-term sym args))

(defn and*
  [formulas]
  (case (count formulas)
    0 (ast/true-form)
    1 (first formulas)
    (reduce ast/and-form formulas)))

(defn or*
  [formulas]
  (case (count formulas)
    0 (ast/false-form)
    1 (first formulas)
    (reduce ast/or-form formulas)))

(defn exists*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/exists-form binding-nom acc))
          body
          (reverse noms)))

(defn numeral
  [n]
  (if (zero? n)
    (app 'zero)
    (app 's (numeral (dec n)))))

(def arithmetic-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'plus 3
                 'peel 2}}))

(defn plus-program
  []
  (ast/nom x y z y1 z1
    (language/compile-program
      arithmetic-language
      [(ast/clause 'plus
                   [x y z]
                   (or*
                     [(and*
                        [(ast/eq-lit (ast/var-term y) (app 'zero))
                         (ast/eq-lit (ast/var-term z) (ast/var-term x))])
                      (exists*
                        [y1 z1]
                        (and*
                          [(ast/eq-lit (ast/var-term y)
                                       (app 's (ast/var-term y1)))
                           (ast/eq-lit (ast/var-term z)
                                       (app 's (ast/var-term z1)))
                           (ast/pos-lit
                             (app 'plus
                                  (ast/var-term x)
                                  (ast/var-term y1)
                                  (ast/var-term z1)))]))]))])))

(defn peel-program
  []
  (ast/nom x y predecessor
    (language/compile-program
      arithmetic-language
      [(ast/clause
         'peel
         [x y]
         (or*
           [(ast/eq-lit (ast/var-term x) (ast/var-term y))
            (exists*
              [predecessor]
              (and*
                [(ast/eq-lit (ast/var-term x)
                             (app 's (ast/var-term predecessor)))
                 (ast/pos-lit
                   (app 'peel
                        (ast/var-term predecessor)
                        (ast/var-term y)))]))]))])))

(defn binding-term
  [record binding-nom]
  (some (fn [[nom term]]
          (when (= nom binding-nom)
            term))
        (:bindings record)))

(defn answer-tuple
  [record answer-vars]
  (mapv #(pretty/peano->int (binding-term record %))
        answer-vars))

(defn proof-backed-profile-records?
  [records]
  (and (seq records)
       (every?
         (fn [record]
           (some (fn [proof]
                   (and (proof/contains-step? proof 'profiled)
                        (proof/contains-step? proof 'constructor-recursive)
                        (proof/contains-step? proof 'structural-residual-continuation)
                        (not (proof/contains-step? proof 'constructor-recursive-call))))
                 (:proofs record)))
         records)))

(defn profile-records
  [program query answer-vars opts]
  (profile/query-records program query answer-vars opts))

(deftest ^:constructor-recursive promoted-profile-proves-non-list-peano-recursion
  (testing "the promoted profile is not list-specific"
    (let [records (profile-records
                    (peel-program)
                    (ast/pos-lit (app 'peel (numeral 3) (numeral 0)))
                    []
                    {:fuel 16
                     :limit 1})]
      (is (= 1 (count records)))
      (is (proof-backed-profile-records? records)))))

(deftest ^:constructor-recursive promoted-profile-exercises-forward-reverse-and-partial-peano-plus
  (let [program (plus-program)]
    (ast/nom x y z
      (testing "forward result synthesis uses the promoted profile"
        (let [records (profile-records
                        program
                        (ast/pos-lit (app 'plus (numeral 3) (numeral 4) (ast/var-term z)))
                        [z]
                        {:fuel 24
                         :limit 2})]
          (is (= [[7]] (mapv answer-tuple records (repeat [z]))))
          (is (proof-backed-profile-records? records))))
      (testing "reverse addend synthesis uses the same promoted profile"
        (let [records (profile-records
                        program
                        (ast/pos-lit (app 'plus (ast/var-term x) (numeral 3) (numeral 5)))
                        [x]
                        {:fuel 24
                         :limit 2})]
          (is (= [[2]] (mapv answer-tuple records (repeat [x]))))
          (is (proof-backed-profile-records? records))))
      (testing "partial synthesis and bounded enumeration use the same profile"
        (let [halves (profile-records
                       program
                       (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 4)))
                       [x]
                       {:fuel 24
                        :limit 2})
              pairs (profile-records
                      program
                      (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term y) (numeral 3)))
                      [x y]
                      {:fuel 24
                       :limit 4})
              odd (profile-records
                    program
                    (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 3)))
                    [x]
                    {:fuel 24
                     :limit 2})]
          (is (= [[2]] (mapv answer-tuple halves (repeat [x]))))
          (is (= #{[0 3] [1 2] [2 1] [3 0]}
                 (set (map #(answer-tuple % [x y]) pairs))))
          (is (empty? odd))
          (is (proof-backed-profile-records? halves))
          (is (proof-backed-profile-records? pairs)))))))

(deftest ^:constructor-recursive promoted-profile-handles-list-append-and-reverse
  (testing "append and reverse use the same generic profile without name dispatch"
    (let [program (matrix/list-program)
          a matrix/a
          b matrix/b]
      (ast/nom z r
        (let [append-records (profile-records
                               program
                               (ast/pos-lit
                                 (ast/app-term 'append
                                               (matrix/list-term a)
                                               (matrix/list-term b)
                                               (ast/var-term z)))
                               [z]
                               {:fuel 48
                                :limit 2})
              reverse-records (profile-records
                                program
                                (ast/pos-lit
                                  (ast/app-term 'reverse
                                                (matrix/list-term a b)
                                                (ast/var-term r)))
                                [r]
                                {:fuel 96
                                 :limit 2})]
          (is (= (matrix/list-term a b)
                 (binding-term (first append-records) z)))
          (is (= (matrix/list-term b a)
                 (binding-term (first reverse-records) r)))
          (is (proof-backed-profile-records? append-records))
          (is (proof-backed-profile-records? reverse-records)))))))

(deftest ^:constructor-recursive promoted-profile-source-has-no-diagnostic-sidecar-or-family-dispatch
  (let [source (slurp "src/proflog/kernel/constructor_recursive_profile.clj")]
    (is (not (str/includes? source "proflog.kernel.constructor-recursive :as")))
    (is (not (str/includes? source "constructor-recursive/query-records")))
    (is (not (str/includes? source "project [")))
    (is (not (str/includes? source "append-fast-path")))
    (is (not (str/includes? source "reverse-fast-path")))
    (is (not (str/includes? source "case relation")))))
