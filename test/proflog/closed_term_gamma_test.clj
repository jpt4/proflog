(ns proflog.closed-term-gamma-test
  (:require [clojure.core.logic :refer [run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.gamma :as gamma]
            [proflog.kernel :as kernel]
            [proflog.language :as language]
            [proflog.query :as query]))

(def unary-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'nonzero-witness 0
                 'outside-small-unary 0
                 'covered-small-unary 0}}))

(def tree-language
  (language/language
    {:constants ['leaf]
     :functions {'node 2}
     :relations {'nonleaf-witness 0
                 'outside-small-tree 0}}))

(defn zero
  []
  (ast/app-term 'zero))

(defn s
  [term]
  (ast/app-term 's term))

(defn leaf
  []
  (ast/app-term 'leaf))

(defn node
  [left right]
  (ast/app-term 'node left right))

(defn proposition
  [relation]
  (ast/pos-lit (ast/app-term relation)))

(defn unary-program
  []
  (ast/nom y z w
    (language/compile-program
      unary-language
      [(ast/clause 'nonzero-witness
                   []
                   (ast/exists-form
                     y
                     (ast/neq-lit (ast/var-term y)
                                  (zero))))
       (ast/clause 'outside-small-unary
                   []
                   (ast/exists-form
                     z
                     (ast/and-form
                       (ast/neq-lit (ast/var-term z)
                                    (zero))
                       (ast/neq-lit (ast/var-term z)
                                    (s (zero))))))
       (ast/clause 'covered-small-unary
                   []
                   (ast/forall-form
                     w
                     (ast/or-form
                       (ast/eq-lit (ast/var-term w)
                                   (zero))
                       (ast/eq-lit (ast/var-term w)
                                   (s (zero))))))])))

(defn tree-program
  []
  (ast/nom t u
    (language/compile-program
      tree-language
      [(ast/clause 'nonleaf-witness
                   []
                   (ast/exists-form
                     t
                     (ast/neq-lit (ast/var-term t)
                                  (leaf))))
       (ast/clause 'outside-small-tree
                   []
                   (ast/exists-form
                     u
                     (ast/and-form
                       (ast/neq-lit (ast/var-term u)
                                    (leaf))
                       (ast/neq-lit (ast/var-term u)
                                    (node (leaf) (leaf))))))])))

(deftest once-forall-needs-unary-compound-counterexample
  (testing "exists y. y != zero requires gamma to try s(zero), not only zero"
    (let [program (unary-program)]
      (is (seq
            (query/query-succeeds
              program
              (proposition 'nonzero-witness)
              1
              8))))))

(deftest once-forall-needs-depth-two-unary-counterexample
  (testing "exists y. y is outside {zero, s(zero)} requires the generated term s(s(zero))"
    (let [program (unary-program)]
      (is (seq
            (query/query-succeeds
              program
              (proposition 'outside-small-unary)
              1
              8))))))

(deftest ordinary-forall-needs-generated-compound-counterexample
  (testing "forall y. y is zero or s(zero) is false because s(s(zero)) is in the language"
    (let [program (unary-program)]
      (is (seq
            (query/query-fails
              program
              (proposition 'covered-small-unary)
              1
              8))))))

(deftest once-forall-needs-binary-compound-counterexample
  (testing "exists t. t != leaf requires gamma to generate node(leaf, leaf)"
    (let [program (tree-program)]
      (is (seq
            (query/query-succeeds
              program
              (proposition 'nonleaf-witness)
              1
              8))))))

(deftest once-forall-needs-depth-two-binary-counterexample
  (testing "a binary constructor signature must generate deeper trees without a tree-specific handler"
    (let [program (tree-program)]
      (is (seq
            (query/query-succeeds
              program
              (proposition 'outside-small-tree)
              1
              8))))))

(deftest kernel-uses-explicit-gamma-candidates
  (testing "closed-term gamma candidates are supplied as finite state, not projected from fuel inside the kernel"
    (let [program (unary-program)
          terms (gamma/closed-terms-for-fuel program 2)
          negated-body (get-in program [:clauses 'outside-small-unary :negated-body])]
      (is (seq
            (run 1 [proof]
              (kernel/prove-programo
                negated-body
                '()
                '()
                '()
                program
                terms
                8
                proof)))))))
