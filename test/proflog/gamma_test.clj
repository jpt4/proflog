(ns proflog.gamma-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run*]]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.gamma :as gamma]
            [proflog.language :as language]))

(def unary-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {}}))

(def tree-language
  (language/language
    {:constants ['leaf]
     :functions {'node 2}
     :relations {}}))

(def mixed-language
  (language/language
    {:constants ['b 'a]
     :functions {'g 2
                 'f 1}
     :relations {}}))

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

(defn a
  []
  (ast/app-term 'a))

(defn b
  []
  (ast/app-term 'b))

(defn f
  [term]
  (ast/app-term 'f term))

(defn g
  [left right]
  (ast/app-term 'g left right))

(deftest closed-terms-are-depth-stratified
  (testing "unary constructors grow by constructor depth from declared constants"
    (is (= [(zero)]
           (gamma/closed-terms-up-to-depth unary-language 0)))
    (is (= [(zero)
            (s (zero))
            (s (s (zero)))]
           (gamma/closed-terms-up-to-depth unary-language 2)))))

(deftest binary-constructors-need-no-family-specific-generator
  (testing "a single generic mechanism enumerates binary constructor candidates"
    (let [leaf-term (leaf)
          depth-one (node leaf-term leaf-term)]
      (is (= [leaf-term
              depth-one
              (node leaf-term depth-one)
              (node depth-one leaf-term)
              (node depth-one depth-one)]
             (gamma/closed-terms-up-to-depth tree-language 2))))))

(deftest declaration-order-is-stable-across-arities
  (testing "constants precede unary constructors, which precede binary constructors"
    (is (= [(a)
            (b)
            (f (a))
            (f (b))
            (g (a) (a))
            (g (a) (b))
            (g (b) (a))
            (g (b) (b))]
           (gamma/closed-terms-up-to-depth mixed-language 1)))))

(deftest fuel-bounds-generated-depth
  (testing "fuel grows the closed-term search up to the configured cap"
    (binding [gamma/*closed-term-depth-cap* 3]
      (is (= [(zero)
              (s (zero))]
             (gamma/closed-terms-for-fuel {:language unary-language} 1)))
      (is (= [(zero)
              (s (zero))
              (s (s (zero)))
              (s (s (s (zero))))]
             (gamma/closed-terms-for-fuel {:language unary-language} 8))))))

(deftest candidate-count-is-capped-generically
  (testing "high-branching signatures are truncated without family-specific code"
    (binding [gamma/*closed-term-depth-cap* 2
              gamma/*closed-term-count-cap* 3]
      (is (= [(a)
              (b)
              (f (a))]
             (gamma/closed-terms-for-fuel {:language mixed-language} 8))))))

(deftest candidate-relation-uses-explicit-finite-candidates
  (testing "the kernel-facing relation does not project fuel or program state"
    (let [terms (gamma/closed-terms-for-fuel {:language unary-language} 2)]
      (is (= [(zero)
              (s (zero))
              (s (s (zero)))]
             (vec
               (run* [q]
                 (gamma/closed-term-candidateo terms q))))))))
