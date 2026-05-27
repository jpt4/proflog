(ns proflog.ast-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic.nominal :refer [tie]]
            [proflog.ast :as ast]))

(deftest constructors-produce-tagged-forms
  (testing "term, literal, and formula constructors produce the greenfield tagged AST"
    (ast/nom a
      (is (= (list 'var a)
             (ast/var-term a)))
      (is (= (list 'par a)
             (ast/par-term a)))
      (is (= (list 'app 'succ (list 'var a))
             (ast/app-term 'succ (ast/var-term a))))
      (is (= (list 'pos (list 'app 'even (list 'var a)))
             (ast/pos-lit (ast/app-term 'even (ast/var-term a)))))
      (is (= (list 'eq (list 'var a) (list 'app 'zero))
             (ast/eq-lit (ast/var-term a) (ast/app-term 'zero))))
      (is (= (list 'forall
                   (tie a (list 'pos (list 'app 'even (list 'var a)))))
             (ast/forall-form a
                              (ast/pos-lit
                                (ast/app-term 'even (ast/var-term a))))))
      (is (= (list 'once-forall
                   (tie a (list 'pos (list 'app 'even (list 'var a)))))
             (ast/once-forall-form a
                                   (ast/pos-lit
                                (ast/app-term 'even (ast/var-term a)))))))))

(deftest ast-predicates-distinguish-core-categories
  (testing "the AST predicates distinguish terms, literals, formulas, and NNF formulas"
    (ast/nom a
      (let [term (ast/app-term 'succ (ast/var-term a))
            lit (ast/pos-lit (ast/app-term 'even term))
            nnf (ast/and-form lit (ast/neq-lit term (ast/app-term 'zero)))
            surface (ast/not-form (ast/implies-form lit (ast/false-form)))]
        (is (ast/term? term))
        (is (ast/literal? lit))
        (is (ast/formula? nnf))
        (is (ast/nnf-formula? nnf))
        (is (ast/formula? surface))
        (is (not (ast/nnf-formula? surface)))))))
