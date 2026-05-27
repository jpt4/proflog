(ns proflog.synthesis-modes-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.pretty :as pretty]))

(def synthesis-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'step 2
                 'jump 2}}))

(def recursive-synthesis-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'down 2}}))

(def structured-synthesis-language
  (language/language
    {:constants ['zero 'null]
     :functions {'s 1
                 'cons 2}
     :relations {'plus 3
                 'append 3}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn list-term
  [& xs]
  (reduce (fn [tail x]
            (ast/app-term 'cons x tail))
          (ast/app-term 'null)
          (reverse xs)))

(defn synthesis-program
  []
  (ast/nom x y z
    (language/compile-program
      synthesis-language
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

(defn down-program
  []
  (ast/nom x y z
    (language/compile-program
      recursive-synthesis-language
      [(ast/clause 'down [x y]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x) (ast/var-term y))
                     (ast/exists-form z
                                      (ast/and-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term z)))
                                        (ast/pos-lit (ast/app-term 'down
                                                                   (ast/var-term z)
                                                                   (ast/var-term y)))))))])))

(defn plus-append-program
  []
  (ast/nom x y z x1 z1 xs ys zs head tail rest
    (let [plus-base
          (ast/and-form
            (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
            (ast/eq-lit (ast/var-term z) (ast/var-term y)))
          plus-step
          (ast/and-form
            (ast/eq-lit (ast/var-term x)
                        (ast/app-term 's (ast/var-term x1)))
            (ast/and-form
              (ast/eq-lit (ast/var-term z)
                          (ast/app-term 's (ast/var-term z1)))
              (ast/pos-lit (ast/app-term 'plus
                                         (ast/var-term x1)
                                         (ast/var-term y)
                                         (ast/var-term z1)))))
          plus-body
          (ast/or-form plus-base
                       (ast/exists-form x1
                                        (ast/exists-form z1 plus-step)))
          append-base
          (ast/and-form
            (ast/eq-lit (ast/var-term xs) (ast/app-term 'null))
            (ast/eq-lit (ast/var-term zs) (ast/var-term ys)))
          append-step
          (ast/and-form
            (ast/eq-lit (ast/var-term xs)
                        (ast/app-term 'cons
                                      (ast/var-term head)
                                      (ast/var-term tail)))
            (ast/and-form
              (ast/eq-lit (ast/var-term zs)
                          (ast/app-term 'cons
                                        (ast/var-term head)
                                        (ast/var-term rest)))
              (ast/pos-lit (ast/app-term 'append
                                         (ast/var-term tail)
                                         (ast/var-term ys)
                                         (ast/var-term rest)))))
          append-body
          (ast/or-form append-base
                       (ast/exists-form head
                                        (ast/exists-form tail
                                                         (ast/exists-form rest append-step))))]
      (language/compile-program
        structured-synthesis-language
        [(ast/clause 'plus [x y z] plus-body)
         (ast/clause 'append [xs ys zs] append-body)]))))

(defn ground-decimals
  [records binding-nom]
  (->> records
       (keep #(pretty/peano->int (answers/binding-term % binding-nom)))
       vec))

(defn neq-residuals-only?
  [records]
  (every? (fn [record]
            (every? #(= 'neq (ast/tag-of %))
                    (:residuals record)))
          records))

(defn app-of?
  [sym arity term]
  (and (= 'app (ast/tag-of term))
       (= sym (second term))
       (= arity (count (nnext term)))))

(defn successor-term?
  [term]
  (app-of? 's 1 term))

(defn successor-arg
  [term]
  (nth term 2))

(defn null-term?
  [term]
  (app-of? 'null 0 term))

(defn cons-term?
  [term]
  (app-of? 'cons 2 term))

(defn cons-head
  [term]
  (nth term 2))

(defn cons-tail
  [term]
  (nth term 3))

(deftest partial-mode-step-query-produces-ground-predecessor-successors
  (testing "step(x, 1) synthesizes the two ground successors of 1"
    (ast/nom x
      (let [records (answers/query-answers
                      (synthesis-program)
                      (ast/pos-lit (ast/app-term 'step (ast/var-term x) (numeral 1)))
                      [x]
                      {:proof-limit 3
                       :call-depth 1})]
        (is (= [2 3]
               (ground-decimals records x)))
        (is (neq-residuals-only? records))))))

(deftest reverse-mode-step-query-honors-additional-constraints
  (testing "step(3, y) with y != 2 keeps only the remaining valid predecessor"
    (ast/nom y
      (let [records (answers/query-answers
                      (synthesis-program)
                      (ast/and-form
                        (ast/pos-lit (ast/app-term 'step (numeral 3) (ast/var-term y)))
                        (ast/neq-lit (ast/var-term y) (numeral 2)))
                      [y]
                      {:proof-limit 3
                       :call-depth 1})]
        (is (= [1]
               (ground-decimals records y)))))))

(deftest open-step-query-exports-symbolic-families
  (testing "step(x, y) exports the two symbolic clause families directly"
    (ast/nom x y
      (let [records (answers/query-answers
                      (synthesis-program)
                      (ast/pos-lit (ast/app-term 'step (ast/var-term x) (ast/var-term y)))
                      [x y]
                      {:proof-limit 2
                       :call-depth 1})
            bindings (set (map :bindings records))]
        (is (= #{[[x (ast/app-term 's (ast/var-term y))]
                  [y (ast/var-term y)]]
                 [[x (ast/app-term 's (ast/app-term 's (ast/var-term y)))]
                  [y (ast/var-term y)]]}
               bindings))
        (is (neq-residuals-only? records))))))

(deftest composed-partial-mode-query-traverses-multiple-calls
  (testing "jump(x, 0) synthesizes the reachable positions through the intermediate call chain"
    (ast/nom x
      (let [records (answers/query-answers
                      (synthesis-program)
                      (ast/pos-lit (ast/app-term 'jump (ast/var-term x) (numeral 0)))
                      [x]
                      {:proof-limit 4
                       :call-depth 2})
            decimals (set (ground-decimals records x))]
        (is (= #{2 3 4} decimals))
        (is (neq-residuals-only? records))))))

(deftest recursive-reverse-mode-query-synthesizes-descendants
  (testing "down(2, y) returns the reachable recursive descendants of 2"
    (ast/nom y
      (let [records (answers/query-answers
                      (down-program)
                      (ast/pos-lit (ast/app-term 'down (numeral 2) (ast/var-term y)))
                      [y]
                      {:proof-limit 2
                       :fuel 8
                       :call-depth 3})]
        (is (= [2 1]
               (ground-decimals records y)))
        (is (neq-residuals-only? records))))))

(deftest recursive-partial-mode-query-synthesizes-ancestors
  (testing "down(x, 1) returns the recursive ancestors of 1 within the unfolding bound"
    (ast/nom x
      (let [records (answers/query-answers
                      (down-program)
                      (ast/pos-lit (ast/app-term 'down (ast/var-term x) (numeral 1)))
                      [x]
                      {:proof-limit 2
                       :fuel 8
                       :call-depth 3})]
        (is (= [1 2]
               (ground-decimals records x)))
        (is (neq-residuals-only? records))))))

(deftest partial-plus-query-exports-a-ground-witness-and-a-recursive-residual-family
  (testing "plus(x, 1, 1) yields both the direct zero witness and a deferred recursive family"
    (ast/nom x
      (let [records (answers/query-answers
                      (plus-append-program)
                      (ast/pos-lit (ast/app-term 'plus
                                                 (ast/var-term x)
                                                 (numeral 1)
                                                 (numeral 1)))
                      [x]
                      {:proof-limit 3
                       :fuel 6
                       :call-depth 1})
            ground-record (some (fn [record]
                                  (when (= (numeral 0)
                                           (answers/binding-term record x))
                                    record))
                                records)
            recursive-record (some (fn [record]
                                     (let [x-term (answers/binding-term record x)]
                                       (when (and (successor-term? x-term)
                                                  (some (fn [residual]
                                                          (and (= 'neg (ast/tag-of residual))
                                                               (= 'plus (second (second residual)))
                                                               (= [(successor-arg x-term)
                                                                   (numeral 1)
                                                                   (numeral 0)]
                                                                  (vec (nnext (second residual))))))
                                                        (:residuals record)))
                                         record)))
                                   records)]
        (is ground-record)
        (is recursive-record)))))

(deftest open-plus-query-exports-base-and-recursive-families
  (testing "plus(x, y, z) exports both the base alias family and a one-step recursive successor refinement"
    (ast/nom x y z
      (let [records (answers/query-answers
                      (plus-append-program)
                      (ast/pos-lit (ast/app-term 'plus
                                                 (ast/var-term x)
                                                 (ast/var-term y)
                                                 (ast/var-term z)))
                      [x y z]
                      {:proof-limit 5
                       :fuel 6
                       :call-depth 1})
            base-record (some (fn [record]
                                (let [x-term (answers/binding-term record x)
                                      y-term (answers/binding-term record y)
                                      z-term (answers/binding-term record z)]
                                  (when (and (= (numeral 0) x-term)
                                             (= y-term z-term)
                                             (empty? (:residuals record)))
                                    record)))
                              records)
            recursive-record (some (fn [record]
                                     (let [x-term (answers/binding-term record x)
                                           y-term (answers/binding-term record y)
                                           z-term (answers/binding-term record z)]
                                       (when (and (successor-term? x-term)
                                                  (successor-term? z-term)
                                                  (= y-term (successor-arg z-term))
                                                  (neq-residuals-only? [record]))
                                         record)))
                                   records)]
        (is base-record)
        (is recursive-record)))))

(deftest open-append-query-exports-base-and-recursive-families
  (testing "append(x, y, z) exports both the base alias family and a one-step recursive cons refinement"
    (ast/nom x y z
      (let [records (answers/query-answers
                      (plus-append-program)
                      (ast/pos-lit (ast/app-term 'append
                                                 (ast/var-term x)
                                                 (ast/var-term y)
                                                 (ast/var-term z)))
                      [x y z]
                      {:proof-limit 5
                       :fuel 4
                       :call-depth 1})
            base-record (some (fn [record]
                                (let [x-term (answers/binding-term record x)
                                      y-term (answers/binding-term record y)
                                      z-term (answers/binding-term record z)]
                                  (when (and (null-term? x-term)
                                             (= y-term z-term)
                                             (empty? (:residuals record)))
                                    record)))
                              records)
            recursive-record (some (fn [record]
                                     (let [x-term (answers/binding-term record x)
                                           y-term (answers/binding-term record y)
                                           z-term (answers/binding-term record z)]
                                       (when (and (cons-term? x-term)
                                                  (cons-term? z-term)
                                                  (null-term? (cons-tail x-term))
                                                  (= (cons-head x-term) (cons-head z-term))
                                                  (= y-term (cons-tail z-term))
                                                  (neq-residuals-only? [record]))
                                         record)))
                                   records)]
        (is base-record)
        (is recursive-record)))))
