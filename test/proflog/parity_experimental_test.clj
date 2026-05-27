(ns proflog.parity-experimental-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.list-programs-test :as lp]))

;; One-off ADR-12 experiments. These probes deliberately live outside the
;; baseline parity selector so larger-than-legacy queries can be exercised
;; without silently becoming part of the ordinary regression contract.

(defn- const-term
  [sym]
  (ast/app-term sym))

(defn- term-list
  [xs]
  (apply lp/list-term xs))

(defn- binding-terms
  [records binding-nom]
  (mapv #(answers/binding-term % binding-nom) records))

(defn- split-binding-terms
  [records left right]
  (mapv (fn [record]
          [(answers/binding-term record left)
            (answers/binding-term record right)])
        records))

(defn- expected-splits
  [items]
  (mapv (fn [idx]
          [(term-list (subvec items 0 idx))
           (term-list (subvec items idx))])
        (range (inc (count items)))))

(defn- deep-elements
  []
  (let [a (const-term 'a)
        b (const-term 'b)
        c (const-term 'c)
        abc (term-list [a b c])
        bca (term-list [b c a])
        cab (term-list [c a b])
        cba (term-list [c b a])
        acb (term-list [a c b])
        bac (term-list [b a c])]
    [(term-list [abc bca cab])
     (term-list [cba acb bac])
     (term-list [bca cba abc])
     (term-list [acb cab bac])
     (term-list [bac abc cba])]))

(deftest experimental-parity-mode-reverses-a-four-element-flat-list
  (testing "ADR-12 parity materialization still reverses a flat four-element list beyond legacy scope"
    (ast/nom r
      (let [program (lp/list-program)
            input (term-list [(const-term 'a)
                              (const-term 'b)
                              (const-term 'c)
                              (const-term 'a)])
            records (answers/query-parity-answers
                      program
                      (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
                      [r]
                      {:proof-limit 1})]
        (is (= [(term-list [(const-term 'a)
                            (const-term 'c)
                            (const-term 'b)
                            (const-term 'a)])]
               (binding-terms records r)))
        (is (= [[]]
               (mapv :residuals records)))))))

(deftest experimental-parity-mode-reverses-a-five-element-flat-list
  (testing "ADR-12 parity materialization still reverses a flat five-element list beyond legacy scope"
    (ast/nom r
      (let [program (lp/list-program)
            input (term-list [(const-term 'a)
                              (const-term 'b)
                              (const-term 'c)
                              (const-term 'a)
                              (const-term 'c)])
            records (answers/query-parity-answers
                      program
                      (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
                      [r]
                      {:proof-limit 1})]
        (is (= [(term-list [(const-term 'c)
                            (const-term 'a)
                            (const-term 'c)
                            (const-term 'b)
                            (const-term 'a)])]
               (binding-terms records r)))
        (is (= [[]]
               (mapv :residuals records)))))))

(deftest experimental-parity-mode-enumerates-all-five-splits-for-four-deeply-nested-elements
  (testing "ADR-12 parity materialization enumerates every split of a four-element outer list whose elements are themselves nested three-element lists"
    (let [program (lp/list-program)
          items (subvec (vec (deep-elements)) 0 4)
          whole (term-list items)]
      (ast/nom left right
        (let [records (answers/query-parity-answers
                        program
                        (ast/pos-lit (ast/app-term 'append
                                                   (ast/var-term left)
                                                   (ast/var-term right)
                                                   whole))
                        [left right]
                        {:proof-limit 5})]
          (is (= (expected-splits items)
                 (split-binding-terms records left right)))
          (is (= 5 (count records)))
          (is (every? empty?
                      (map :residuals records))))))))

(deftest experimental-parity-mode-enumerates-all-six-splits-for-five-deeply-nested-elements
  (testing "ADR-12 parity materialization enumerates every split of a five-element outer list whose elements are themselves nested three-element lists"
    (let [program (lp/list-program)
          items (vec (deep-elements))
          whole (term-list items)]
      (ast/nom left right
        (let [records (answers/query-parity-answers
                        program
                        (ast/pos-lit (ast/app-term 'append
                                                   (ast/var-term left)
                                                   (ast/var-term right)
                                                   whole))
                        [left right]
                        {:proof-limit 6})]
          (is (= (expected-splits items)
                 (split-binding-terms records left right)))
          (is (= 6 (count records)))
          (is (every? empty?
                      (map :residuals records))))))))
