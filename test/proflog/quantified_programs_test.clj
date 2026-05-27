(ns proflog.quantified-programs-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]
            [proflog.query-test :as qt]))

(def quantified-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'boxed-zero 1
                 'zero-only 1}}))

(def subset-language
  (language/language
    {:constants ['a 'b 'c]
     :relations {'sub-a-a 0
                 'sub-ab-abc 0
                 'sub-abc-ab 0}}))

(def graph-language
  (language/language
    {:constants ['a 'b 'c]
     :relations {'acyclic-aba 0
                 'acyclic-abc 0
                 'acyclic-abca 0}}))

(def sorted-language
  (language/language
    {:constants ['zero 'null]
     :functions {'cons 2
                 's 1}
     :relations {'sorted2 1}}))

(defn quantified-list
  [& nums]
  (reduce (fn [tail n]
            (ast/app-term 'cons (qt/numeral n) tail))
          (ast/app-term 'null)
          (reverse nums)))

(defn zero-only-program
  []
  (ast/nom x y
    (language/compile-program
      quantified-language
      [(ast/clause 'zero-only [x]
                   (ast/forall-form
                     y
                     (ast/or-form
                       (ast/neq-lit (ast/var-term x) (ast/var-term y))
                       (ast/eq-lit (ast/var-term y) (ast/app-term 'zero)))))])))

(defn boxed-zero-program
  []
  (ast/nom x y z
    (language/compile-program
      quantified-language
      [(ast/clause 'boxed-zero [x]
                   (ast/exists-form
                     y
                     (ast/and-form
                       (ast/eq-lit (ast/var-term x) (ast/var-term y))
                       (ast/forall-form
                         z
                         (ast/or-form
                           (ast/neq-lit (ast/var-term y) (ast/var-term z))
                           (ast/eq-lit (ast/var-term z) (ast/app-term 'zero)))))))])))

(defn subset-program
  []
  (ast/nom x
    (language/compile-program
      subset-language
      [(ast/clause 'sub-ab-abc []
                   (ast/forall-form
                     x
                     (ast/or-form
                       (ast/and-form
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'b)))
                       (ast/or-form
                         (ast/eq-lit (ast/var-term x) (ast/app-term 'a))
                         (ast/or-form
                           (ast/eq-lit (ast/var-term x) (ast/app-term 'b))
                           (ast/eq-lit (ast/var-term x) (ast/app-term 'c)))))))
       (ast/clause 'sub-abc-ab []
                   (ast/forall-form
                     x
                     (ast/or-form
                       (ast/and-form
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                         (ast/and-form
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'b))
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'c))))
                       (ast/or-form
                         (ast/eq-lit (ast/var-term x) (ast/app-term 'a))
                         (ast/eq-lit (ast/var-term x) (ast/app-term 'b))))))
       (ast/clause 'sub-a-a []
                   (ast/forall-form
                     x
                     (ast/or-form
                       (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                       (ast/eq-lit (ast/var-term x) (ast/app-term 'a)))))])))

(defn acyclic-program
  []
  (ast/nom x
    (language/compile-program
      graph-language
      [(ast/clause 'acyclic-abc []
                   (ast/forall-form
                     x
                     (ast/and-form
                       (ast/or-form
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'b)))
                       (ast/and-form
                         (ast/or-form
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'b))
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'c)))
                         (ast/or-form
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                           (ast/neq-lit (ast/var-term x) (ast/app-term 'c)))))))
       (ast/clause 'acyclic-aba []
                   (ast/forall-form
                     x
                     (ast/and-form
                       (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                       (ast/neq-lit (ast/var-term x) (ast/app-term 'b)))))
       (ast/clause 'acyclic-abca []
                   (ast/forall-form
                     x
                     (ast/and-form
                       (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                       (ast/and-form
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'b))
                         (ast/neq-lit (ast/var-term x) (ast/app-term 'c))))))])))

(defn le-inline-form
  [a-term b-term]
  (ast/or-form
    (ast/eq-lit a-term (qt/numeral 0))
    (ast/or-form
      (ast/and-form
        (ast/eq-lit a-term (qt/numeral 1))
        (ast/or-form
          (ast/eq-lit b-term (qt/numeral 1))
          (ast/eq-lit b-term (qt/numeral 2))))
      (ast/and-form
        (ast/eq-lit a-term (qt/numeral 2))
        (ast/eq-lit b-term (qt/numeral 2))))))

(defn sorted2-program
  []
  (ast/nom l a b t
    (language/compile-program
      sorted-language
      [(ast/clause 'sorted2 [l]
                   (ast/forall-form
                     a
                     (ast/forall-form
                       b
                       (ast/forall-form
                         t
                         (ast/or-form
                           (ast/neq-lit
                             (ast/var-term l)
                             (ast/app-term 'cons
                                           (ast/var-term a)
                                           (ast/app-term 'cons
                                                         (ast/var-term b)
                                                         (ast/var-term t))))
                           (le-inline-form (ast/var-term a)
                                           (ast/var-term b)))))))])))

(deftest original-p1-quantified-clause-handles-deeper-ground-cases
  (testing "the original forall-based P1 clause now executes deeper success and failure cases directly"
    (let [program (qt/p1-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'even (qt/numeral 2)))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'odd (qt/numeral 0)))
              1
              8))))))

(deftest universally-quantified-clause-bodies-support-ground-success-and-failure
  (testing "a forall-based clause body can distinguish the intended singleton case"
    (let [program (zero-only-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'zero-only (qt/numeral 0)))
              1
              16)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'zero-only (qt/numeral 1)))
              1
              16))))))

(deftest mixed-exists-and-forall-clause-bodies-support-ground-success-and-failure
  (testing "a clause body combining existential and universal structure executes end to end"
    (let [program (boxed-zero-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'boxed-zero (qt/numeral 0)))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'boxed-zero (qt/numeral 1)))
              1
              32))))))

(deftest subset-quantified-spec-handles-true-false-and-reflexive-cases
  (testing "subset specifications over the finite domain {a, b, c} execute as expected"
    (let [program (subset-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sub-ab-abc))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'sub-abc-ab))
              1
              32)))
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sub-a-a))
              1
              16))))))

(deftest acyclic-quantified-spec-distinguishes-acyclic-and-cyclic-small-graphs
  (testing "acyclic specifications distinguish the inline acyclic and cyclic finite graphs"
    (let [program (acyclic-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'acyclic-abc))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'acyclic-aba))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'acyclic-abca))
              1
              32))))))

(deftest sorted2-quantified-spec-distinguishes-small-sorted-and-unsorted-lists
  (testing "sorted2 handles the legacy empty, singleton, sorted, and unsorted small-list cases"
    (let [program (sorted2-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sorted2 (quantified-list)))
              1
              32)))
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sorted2 (quantified-list 1)))
              1
              128)))
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sorted2 (quantified-list 0 1 2)))
              1
              128)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit (ast/app-term 'sorted2 (quantified-list 2 1)))
              1
              64)))
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit (ast/app-term 'sorted2 (quantified-list 1 2)))
              1
              64))))))
