(ns proflog.language-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.language :as language]))

(def simple-language
  (language/language
    {:constants ['zero 'one]
     :functions {'succ 1}
     :relations {'even 1
                 'odd 1
                 'value 1}}))

(deftest language-rejects-undeclared-and-mismatched-symbols
  (testing "queries using undeclared relations, undeclared functions, or wrong arities are rejected"
    (ast/nom x
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Undeclared relation symbol: mystery"
            (language/validate-query
              simple-language
              (ast/pos-lit (ast/app-term 'mystery (ast/var-term x))))))
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Undeclared function symbol: weird"
            (language/validate-query
              simple-language
              (ast/pos-lit (ast/app-term 'even (ast/app-term 'weird))))))
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Arity mismatch for relation symbol even"
            (language/validate-query
              simple-language
              (ast/pos-lit (ast/app-term 'even (ast/var-term x) (ast/app-term 'zero)))))))))

(deftest compile-program-desugars-multiple-clauses-into-one-core-clause
  (testing "multiple surface clauses for the same relation become one compiled clause with an OR body"
    (ast/nom x y
      (let [program (language/compile-program
                      simple-language
                      [(ast/clause 'value [x]
                                   (ast/eq-lit (ast/var-term x) (ast/app-term 'zero)))
                       (ast/clause 'value [y]
                                   (ast/eq-lit (ast/var-term y) (ast/app-term 'one)))])
            compiled (get-in program [:clauses 'value])]
        (is (= 'value (:relation compiled)))
        (is (= 1 (count (:params compiled))))
        (is (= (ast/or-form
                 (ast/eq-lit (ast/var-term (first (:params compiled)))
                             (ast/app-term 'zero))
                 (ast/eq-lit (ast/var-term (first (:params compiled)))
                             (ast/app-term 'one)))
               (:body compiled)))
        (is (= [(ast/eq-lit (ast/var-term (first (:params compiled)))
                             (ast/app-term 'zero))
                (ast/eq-lit (ast/var-term (first (:params compiled)))
                             (ast/app-term 'one))]
               (vec (:alternatives compiled))))
        (is (= [(ast/neq-lit (ast/var-term (first (:params compiled)))
                              (ast/app-term 'zero))
                (ast/neq-lit (ast/var-term (first (:params compiled)))
                              (ast/app-term 'one))]
               (vec (:negated-alternatives compiled))))))))

(deftest compile-program-records-generic-guarded-alternative-ir
  (testing "guarded IR separates guards, defined procedure calls, and residual relation literals"
    (ast/nom x y
      (let [program (language/compile-program
                      simple-language
                      [(ast/clause 'value [x]
                                   (ast/exists-form
                                     y
                                     (ast/and-form
                                       (ast/eq-lit
                                         (ast/var-term x)
                                         (ast/app-term 'succ (ast/var-term y)))
                                       (ast/and-form
                                         (ast/pos-lit
                                           (ast/app-term 'value (ast/var-term y)))
                                         (ast/pos-lit
                                           (ast/app-term 'even (ast/var-term y)))))))])
            compiled (get-in program [:clauses 'value])
            param (first (:params compiled))
            guarded (first (:guarded-alternatives compiled))
            expected-guard (ast/eq-lit
                             (ast/var-term param)
                             (ast/app-term 'succ (ast/var-term y)))
            expected-call (ast/pos-lit
                            (ast/app-term 'value (ast/var-term y)))
            expected-residual (ast/pos-lit
                                (ast/app-term 'even (ast/var-term y)))]
        (is (= 'exists (:quantifier (first (:scope guarded)))))
        (is (= y (:binding-nom (first (:scope guarded)))))
        (is (= [expected-guard expected-call expected-residual]
               (vec (:conjuncts guarded))))
        (is (= [expected-guard]
               (vec (:guards guarded))))
        (is (= [expected-call]
               (vec (:calls guarded))))
        (is (= [expected-residual]
               (vec (:residuals guarded))))
        (is (= [(select-keys compiled [:relation
                                       :params
                                       :body
                                       :negated-body
                                       :alternatives
                                       :negated-alternatives
                                       :guarded-alternatives])]
               (vec (:guarded-clause-list program))))))))

(deftest compile-program-rejects-par-in-surface-programs
  (testing "internal parameters are not admissible in user programs"
    (ast/nom x p
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Internal parameter terms are not admissible in surface programs"
            (language/compile-program
              simple-language
              [(ast/clause 'value [x]
                           (ast/eq-lit (ast/var-term x) (ast/par-term p)))]))))))
