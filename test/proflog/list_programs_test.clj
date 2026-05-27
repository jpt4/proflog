(ns proflog.list-programs-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(def list-language
  (language/language
   {:constants ['a 'b 'c 'null]
    :functions {'cons 2}
    :relations {'append 3
                'member 2
                'reverse 2}}))

(defn list-term
  [& xs]
  (reduce (fn [tail x]
            (ast/app-term 'cons x tail))
          (ast/app-term 'null)
          (reverse xs)))

(defn member-body
  [x xs head tail]
  (ast/exists-form
   head
   (ast/exists-form
    tail
    (ast/and-form
     (ast/eq-lit
      (ast/var-term xs)
      (ast/app-term 'cons
                    (ast/var-term head)
                    (ast/var-term tail)))
     (ast/or-form
      (ast/eq-lit (ast/var-term x) (ast/var-term head))
      (ast/pos-lit
       (ast/app-term 'member
                     (ast/var-term x)
                     (ast/var-term tail))))))))

(defn append-body
  [xs ys zs head tail rest]
  (ast/or-form
   (ast/and-form
    (ast/eq-lit (ast/var-term xs) (ast/app-term 'null))
    (ast/eq-lit (ast/var-term zs) (ast/var-term ys)))
   (ast/exists-form
    head
    (ast/exists-form
     tail
     (ast/exists-form
      rest
      (ast/and-form
       (ast/eq-lit
        (ast/var-term xs)
        (ast/app-term 'cons
                      (ast/var-term head)
                      (ast/var-term tail)))
       (ast/and-form
        (ast/eq-lit
         (ast/var-term zs)
         (ast/app-term 'cons
                       (ast/var-term head)
                       (ast/var-term rest)))
        (ast/pos-lit
         (ast/app-term 'append
                       (ast/var-term tail)
                       (ast/var-term ys)
                       (ast/var-term rest))))))))))

(defn reverse-body
  [r1 r2 head tail rrp]
  (ast/or-form
   (ast/and-form
    (ast/eq-lit (ast/var-term r1) (ast/app-term 'null))
    (ast/eq-lit (ast/var-term r2) (ast/app-term 'null)))
   (ast/exists-form
    head
    (ast/exists-form
     tail
     (ast/exists-form
      rrp
      (ast/and-form
       (ast/eq-lit
        (ast/var-term r1)
        (ast/app-term 'cons
                      (ast/var-term head)
                      (ast/var-term tail)))
       (ast/and-form
        (ast/pos-lit
         (ast/app-term 'reverse
                       (ast/var-term tail)
                       (ast/var-term rrp)))
        (ast/pos-lit
         (ast/app-term 'append
                       (ast/var-term rrp)
                       (ast/app-term 'cons
                                     (ast/var-term head)
                                     (ast/app-term 'null))
                       (ast/var-term r2))))))))))

(defn list-program
  []
  (ast/nom x xs head tail ys zs rest r1 r2 rrp
           (language/compile-program
            list-language
            [(ast/clause 'member [x xs] (member-body x xs head tail))
             (ast/clause 'append [xs ys zs] (append-body xs ys zs head tail rest))
             (ast/clause 'reverse [r1 r2] (reverse-body r1 r2 head tail rrp))])))

;; Prompt greenfield list-program coverage is currently limited to the cases
;; that close without the deeper recursive search costs seen in `member` and
;; non-empty `reverse`/`append` proofs. Those heavier families stay documented
;; in ADR-0008 as exploratory rather than baseline regressions for now.

(deftest append-base-case-succeeds-and-exports-an-open-result
  (testing "append([], [a], [a]) closes directly and append([], [a], z) exports z = [a]"
    (let [program (list-program)
          empty-list (list-term)
          a-list (list-term (ast/app-term 'a))]
      (is (seq
           (query/query-succeeds
            program
            (ast/pos-lit (ast/app-term 'append empty-list a-list a-list))
            1
            8)))
      (ast/nom z
               (let [records (answers/query-answers
                              program
                              (ast/pos-lit
                               (ast/app-term 'append
                                             empty-list
                                             a-list
                                             (ast/var-term z)))
                              [z]
                              {:proof-limit 2
                               :fuel 4
                               :call-depth 1})]
                 (is (= [a-list]
                        (mapv #(answers/binding-term % z) records)))
                 (is (= [[]]
                        (mapv :residuals records))))))))

(deftest reverse-empty-list-succeeds
  (testing "reverse([], []) closes through the direct base case"
    (let [program (list-program)
          empty-list (list-term)]
      (is (seq
           (query/query-succeeds
            program
            (ast/pos-lit (ast/app-term 'reverse empty-list empty-list))
            1
            8))))))

(deftest member-covers-direct-hit-recursive-hit-and-miss
  (testing "member reaches both the head case and a recursive tail case, and rejects a missing element"
    (let [program (list-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'member
                              (ast/app-term 'a)
                              (list-term (ast/app-term 'a))))
              1
              32)))
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'member
                              (ast/app-term 'a)
                              (list-term (ast/app-term 'b)
                                         (ast/app-term 'a))))
              1
              64)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit
                (ast/app-term 'member
                              (ast/app-term 'c)
                              (list-term (ast/app-term 'a)
                                         (ast/app-term 'b))))
              1
              32))))))

(deftest member-empty-list-fails
  (testing "member(a, []) fails immediately because [] cannot decompose as cons(head, tail)"
    (let [program (list-program)]
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit
                (ast/app-term 'member
                              (ast/app-term 'a)
                              (list-term)))
              1
              8))))))

(deftest append-covers-one-step-recursion-and-a-wrong-result
  (testing "append executes the first recursive case and refutes an incorrect target list"
    (let [program (list-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'append
                              (list-term (ast/app-term 'a))
                              (list-term (ast/app-term 'b))
                              (list-term (ast/app-term 'a)
                                         (ast/app-term 'b))))
              1
              32)))
      (is (seq
            (query/query-fails
              program
              (ast/pos-lit
                (ast/app-term 'append
                              (list-term (ast/app-term 'a))
                              (list-term (ast/app-term 'b))
                              (list-term (ast/app-term 'a))))
              1
              32))))))

(deftest reverse-singleton-list-succeeds
  (testing "reverse([a], [a]) now closes through one recursive unfold plus base append"
    (let [program (list-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'reverse
                              (list-term (ast/app-term 'a))
                              (list-term (ast/app-term 'a))))
              1
              64))))))

(deftest ^:constructor-recursive append-two-step-ground-case-succeeds
  (testing "append([a, b], [c], [a, b, c]) is semantically reachable, though expensive"
    (let [program (list-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'append
                              (list-term (ast/app-term 'a)
                                         (ast/app-term 'b))
                              (list-term (ast/app-term 'c))
                              (list-term (ast/app-term 'a)
                                         (ast/app-term 'b)
                                         (ast/app-term 'c))))
              1
              256))))))

(deftest append-forward-query-binds-a-three-element-result
  (testing "append([a], [b, c], z) now returns the closed concrete list answer directly"
    (let [program (list-program)
          left (list-term (ast/app-term 'a))
          right (list-term (ast/app-term 'b)
                           (ast/app-term 'c))
          expected (list-term (ast/app-term 'a)
                              (ast/app-term 'b)
                              (ast/app-term 'c))]
      (ast/nom z
               (let [records (answers/query-answers
                              program
                              (ast/pos-lit
                               (ast/app-term 'append
                                             left
                                             right
                                             (ast/var-term z)))
                              [z]
                              {:proof-limit 1
                               :fuel 16
                               :call-depth 2})]
                 (is (= [expected]
                        (mapv #(answers/binding-term % z) records)))
                 (is (every? empty?
                             (map :residuals records))))))))

(deftest ^:constructor-recursive reverse-two-element-list-succeeds
  (testing "reverse([a, b], [b, a]) is semantically reachable, though expensive"
    (let [program (list-program)]
      (is (seq
            (query/query-succeeds
              program
              (ast/pos-lit
                (ast/app-term 'reverse
                              (list-term (ast/app-term 'a)
                                         (ast/app-term 'b))
                              (list-term (ast/app-term 'b)
                                         (ast/app-term 'a))))
              1
              256))))))

(deftest append-nested-forward-query-binds-the-concrete-result
  (testing "append([[a]], [[b]], z) now returns the closed nested list answer directly"
    (let [program (list-program)
          sub-a (list-term (ast/app-term 'a))
          sub-b (list-term (ast/app-term 'b))
          left (list-term sub-a)
          right (list-term sub-b)
          expected (list-term sub-a sub-b)]
      (ast/nom z
               (let [records (answers/query-answers
                              program
                              (ast/pos-lit
                               (ast/app-term 'append
                                             left
                                             right
                                             (ast/var-term z)))
                              [z]
                              {:proof-limit 1
                               :fuel 16
                               :call-depth 2})]
                 (is (= [expected]
                        (mapv #(answers/binding-term % z) records)))
                 (is (every? empty?
                             (map :residuals records))))))))

(deftest append-nested-suffix-query-materializes-the-concrete-second-argument-in-generic-answer-mode
  (testing "generic open answers now prioritize the closed nested suffix answer"
    (let [program (list-program)
          sub-ab (list-term (ast/app-term 'a)
                            (ast/app-term 'b))
          sub-c (list-term (ast/app-term 'c))
          left (list-term sub-ab)
          whole (list-term sub-ab sub-c)]
      (ast/nom z
               (let [query (ast/pos-lit
                             (ast/app-term 'append
                                           left
                                           (ast/var-term z)
                                           whole))
                     symbolic-records (answers/query-answers
                                        program
                                        query
                                        [z]
                                        {:proof-limit 1
                                         :max-raw-proof-limit 64
                                         :fuel 64
                                         :call-depth 2})]
                 (is (= [(list-term sub-c)]
                        (mapv #(answers/binding-term % z) symbolic-records)))
                 (is (every? empty?
                             (map :residuals symbolic-records))))))))
