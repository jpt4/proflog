(ns proflog.frontend-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.frontend :as pf]
            [proflog.query :as query]))

(def p-language
  (pf/language
    (constants a b)
    (relations (p 1))))

(def p-a-program
  (pf/proflog p-language
    (|- (p x)
        (= x a))))

(def p-b-program
  (pf/proflog p-language
    (|- (p x)
        (= x b))))

(def zero-only-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (zero-only 1))))

(def zero-only-program
  (pf/proflog zero-only-language
    (:= (only-zero x)
      (forall [y]
        (or (!= x y)
            (= y zero))))

    (|- (zero-only x)
        (only-zero x))))

(def nim-language
  (pf/language
    (constants zero)
    (functions (s 1))
    (relations (win 1))))

(def nim-program
  (pf/proflog nim-language
    (:= (move x y)
      (or (= x (s y))
          (= x (s (s y)))))

    (|- (win x)
        (exists [y]
          (and (move x y)
               (not (win y)))))))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(deftest frontend-language-declarations-are-reusable
  (testing "one frontend language value can compile multiple programs"
    (is (= p-language (:language p-a-program)))
    (is (= p-language (:language p-b-program)))
    (is (= :succeeds
           (query/query-status p-a-program (pf/q (p a)))))
    (is (= :fails
           (query/query-status p-a-program (pf/q (p b)))))
    (is (= :fails
           (query/query-status p-b-program (pf/q (p a)))))
    (is (= :succeeds
           (query/query-status p-b-program (pf/q (p b)))))))

(deftest frontend-translates-simple-relation-to-backend-clause
  (testing "p(x) :- x = a descends to the expected compiled AST body"
    (let [clause (get-in p-a-program [:clauses 'p])
          param (first (:params clause))]
      (is (= 'p (:relation clause)))
      (is (= (ast/eq-lit (ast/var-term param) (ast/app-term 'a))
             (:body clause)))
      (is (= (ast/neq-lit (ast/var-term param) (ast/app-term 'a))
             (:negated-body clause))))))

(deftest frontend-can-emit-clauses-for-higher-level-builders
  (testing "clauses reuses helper inlining without compiling a program"
    (let [clauses (pf/clauses
                    (:= (is-a x)
                      (= x a))
                    (|- (p x)
                        (is-a x)))
          clause (first clauses)
          param (first (:params clause))]
      (is (= 1 (count clauses)))
      (is (= 'p (:relation clause)))
      (is (= (ast/eq-lit (ast/var-term param) (ast/app-term 'a))
             (:body clause))))))

(deftest frontend-query-macro-builds-backend-formulas
  (testing "q is a thin formula builder"
    (is (= (ast/pos-lit (ast/app-term 'p (ast/app-term 'a)))
           (pf/q (p a))))
    (is (= (ast/pos-lit
             (ast/app-term 'zero-only
                           (ast/app-term 's (ast/app-term 'zero))))
           (pf/q (zero-only (s zero)))))))

(deftest frontend-answer-query-binds-exported-vars
  (testing "answer-query binds visible answer variables for the answer APIs"
    (let [{:keys [query answer-vars]} (pf/answer-query [x] (p x))
          answer-var (first answer-vars)]
      (is (= (ast/pos-lit (ast/app-term 'p (ast/var-term answer-var)))
             query))
      (is (= 1 (count answer-vars)))
      (let [records (answers/query-answers
                      p-a-program
                      query
                      answer-vars
                      {:proof-limit 1})]
        (is (= (ast/app-term 'a)
               (-> records first :bindings first second)))
        (is (= answer-var
               (-> records first :bindings first first)))))))

(deftest frontend-answer-query-rejects-duplicate-bindings
  (testing "answer-query reports malformed binding vectors at expansion time"
    (try
      (macroexpand
        '(proflog.frontend/answer-query [x x]
           (p x)))
      (is false "duplicate frontend answer bindings should fail")
      (catch clojure.lang.Compiler$CompilerException ex
        (is (= "Duplicate frontend answer-query bindings"
               (some-> ex .getCause ex-message)))
        (is (= '[x x]
               (some-> ex .getCause ex-data :bindings)))))))

(deftest frontend-run-evaluates-open-answer-query
  (testing "run keeps answer vars in the frontend binding form"
    (let [records (pf/run p-a-program [x]
                    (p x)
                    {:proof-limit 1})
          record (first records)]
      (is (= (ast/app-term 'a)
             (-> record :bindings first second)))
      (is (empty? (:residuals record))))))

(deftest frontend-run-uses-default-answer-options
  (testing "run can omit the options map"
    (let [records (pf/run p-a-program [x]
                    (p x))]
      (is (= (ast/app-term 'a)
             (-> records first :bindings first second))))))

(deftest frontend-inline-helper-translates-before-backend-compilation
  (testing "only-zero is a frontend helper, not a runtime relation"
    (is (nil? (get-in zero-only-program [:clauses 'only-zero])))
    (let [clause (get-in zero-only-program [:clauses 'zero-only])
          param (first (:params clause))
          body (:body clause)
          tied (second body)
          quantified-nom (:binding-nom tied)]
      (is (= 'forall (ast/tag-of body)))
      (is (= (ast/or-form
               (ast/neq-lit (ast/var-term param)
                            (ast/var-term quantified-nom))
               (ast/eq-lit (ast/var-term quantified-nom)
                           (ast/app-term 'zero)))
             (:body tied))))
    (is (seq
          (query/query-succeeds
            zero-only-program
            (pf/q (zero-only zero))
            1
            16)))
    (is (seq
          (query/query-fails
            zero-only-program
            (pf/q (zero-only (s zero)))
            1
            16)))))

(deftest frontend-inline-helper-repairs-factored-nim-source
  (testing "the ADR-0010 move helper is inlined instead of compiled as a runtime relation"
    (is (nil? (get-in nim-program [:clauses 'move])))
    (is (seq
          (query/query-fails
            nim-program
            (pf/q (win zero))
            1
            16)))
    (is (seq
          (query/query-succeeds
            nim-program
            (pf/q (win (s zero)))
            1
            16)))))

(deftest frontend-rejects-recursive-inline-helpers
  (testing "recursive definitional helpers are outside the initial inlining contract"
    (try
      (macroexpand
        '(proflog.frontend/proflog p-language
           (:= (bad x)
             (bad x))
           (|- (p x)
               (bad x))))
      (is false "recursive frontend helper expansion should fail")
      (catch clojure.lang.Compiler$CompilerException ex
        (is (= "Recursive frontend helper definitions are not supported"
               (some-> ex .getCause ex-message)))
        (is (= 'bad (some-> ex .getCause ex-data :helper)))))))
