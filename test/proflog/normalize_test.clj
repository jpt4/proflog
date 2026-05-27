(ns proflog.normalize-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.normalize :as normalize]))

(deftest to-nnf-eliminates-implication-and-pushes-negation-inward
  (testing "surface implication and negation compile to tagged NNF"
    (ast/nom x
      (is (= (ast/and-form
               (ast/pos-lit (ast/app-term 'warm (ast/var-term x)))
               (ast/neg-lit (ast/app-term 'cool (ast/var-term x))))
             (normalize/to-nnf
               (ast/not-form
                 (ast/implies-form
                   (ast/pos-lit (ast/app-term 'warm (ast/var-term x)))
                   (ast/pos-lit (ast/app-term 'cool (ast/var-term x)))))))))))

(deftest to-nnf-handles-quantifier-duality-and-equality-negation
  (testing "negated quantifiers and equality switch to their dual NNF forms"
    (ast/nom x
      (is (= (ast/exists-form
               x
               (ast/neq-lit (ast/var-term x) (ast/app-term 'zero)))
             (normalize/to-nnf
               (ast/not-form
                 (ast/forall-form
                   x
                   (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))))))
      (is (= (ast/once-forall-form
               x
               (ast/neq-lit (ast/var-term x) (ast/app-term 'zero)))
             (normalize/to-nnf
               (ast/not-form
                 (ast/exists-form
                   x
                   (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))))))
      (is (= (ast/eq-lit (ast/app-term 'zero) (ast/app-term 'one))
             (normalize/negate-formula
               (ast/neq-lit (ast/app-term 'zero) (ast/app-term 'one))))))))

(deftest negate-formula-distributes-over-boolean-connectives
  (testing "negation swaps conjunction and disjunction while negating leaves"
    (is (= (ast/or-form
             (ast/neg-lit (ast/app-term 'warm))
             (ast/pos-lit (ast/app-term 'cool)))
           (normalize/negate-formula
             (ast/and-form
               (ast/pos-lit (ast/app-term 'warm))
               (ast/neg-lit (ast/app-term 'cool))))))
    (is (= (ast/and-form
             (ast/neg-lit (ast/app-term 'warm))
             (ast/pos-lit (ast/app-term 'cool)))
           (normalize/negate-formula
             (ast/or-form
               (ast/pos-lit (ast/app-term 'warm))
               (ast/neg-lit (ast/app-term 'cool))))))))

(deftest to-nnf-pushes-negation-through-existentials-and-conjunctions
  (testing "surface negation over existential conjunctions becomes single-use universal disjunction in NNF"
    (ast/nom x
      (is (= (ast/once-forall-form
               x
               (ast/or-form
                 (ast/neg-lit (ast/app-term 'p (ast/var-term x)))
                 (ast/pos-lit (ast/app-term 'q (ast/var-term x)))))
             (normalize/to-nnf
               (ast/not-form
                 (ast/exists-form
                   x
                   (ast/and-form
                     (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                     (ast/neg-lit (ast/app-term 'q (ast/var-term x))))))))))))

(deftest to-nnf-lowers-sjas-bounded-quantifiers-through-leq-guards
  (testing "bounded quantifiers become ordinary quantifiers plus relational leq guards"
    (ast/nom x
      (let [x-term (ast/var-term x)
            two (ast/app-term 'two)
            guard-atom (ast/app-term 'leq x-term two)
            body (ast/pos-lit (ast/app-term 'p x-term))
            bounded-forall (list 'bounded-forall
                                  (nominal/tie x {:bound two
                                                  :body body}))
            bounded-exists (list 'bounded-exists
                                  (nominal/tie x {:bound two
                                                  :body body}))]
        (is (ast/formula? bounded-forall))
        (is (ast/formula? bounded-exists))
        (is (= (ast/forall-form
                 x
                 (ast/or-form (ast/neg-lit guard-atom) body))
               (normalize/to-nnf bounded-forall)))
        (is (ast/nnf-formula? (normalize/to-nnf bounded-forall)))
        (is (= (ast/exists-form
                 x
                 (ast/and-form (ast/pos-lit guard-atom) body))
               (normalize/to-nnf bounded-exists)))
        (is (ast/nnf-formula? (normalize/to-nnf bounded-exists)))
        (is (= (ast/exists-form
                 x
                 (ast/and-form (ast/pos-lit guard-atom)
                               (ast/neg-lit (ast/app-term 'p x-term))))
               (normalize/negate-formula bounded-forall)))
        (is (= (ast/once-forall-form
                 x
                 (ast/or-form (ast/neg-lit guard-atom)
                              (ast/neg-lit (ast/app-term 'p x-term))))
               (normalize/negate-formula bounded-exists)))))))
