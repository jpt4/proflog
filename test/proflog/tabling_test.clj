(ns proflog.tabling-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.tabling :as tabling]))

(def recursive-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'even 1
                 'odd 1}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn even-odd-program
  []
  (ast/nom x y
    (language/compile-program
      recursive-language
      [(ast/clause 'even [x]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))
                     (ast/exists-form y
                                      (ast/and-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term y)))
                                        (ast/pos-lit (ast/app-term 'odd (ast/var-term y)))))))
       (ast/clause 'odd [x]
                   (ast/forall-form y
                                    (ast/implies-form
                                      (ast/pos-lit (ast/app-term 'even (ast/var-term y)))
                                      (ast/neq-lit (ast/var-term x) (ast/var-term y)))))])))

(deftest canonical-state-keys-ignore-order-and-alpha-equivalent-noms
  (testing "agenda, saved literals, disequalities, and substitutions are canonicalized"
    (let [left-key
          (ast/nom x y
            (tabling/state-key
              {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                        (ast/neg-lit (ast/app-term 'q (ast/var-term y)))]
               :lits [(ast/pos-lit (ast/app-term 'r (ast/var-term x)))
                      (ast/neg-lit (ast/app-term 's (ast/var-term y)))]
               :neqs [[(ast/var-term x) (ast/app-term 'a)]
                      [(ast/var-term y) (ast/app-term 'b)]]
               :sigma [[x (ast/app-term 'a)]
                       [y (ast/app-term 'b)]]
               :prog-key :same-program}))
          right-key
          (ast/nom a b
            (tabling/state-key
              {:agenda [(ast/neg-lit (ast/app-term 'q (ast/var-term b)))
                        (ast/pos-lit (ast/app-term 'p (ast/var-term a)))]
               :lits [(ast/neg-lit (ast/app-term 's (ast/var-term b)))
                      (ast/pos-lit (ast/app-term 'r (ast/var-term a)))]
               :neqs [[(ast/var-term b) (ast/app-term 'b)]
                      [(ast/var-term a) (ast/app-term 'a)]]
               :sigma [[b (ast/app-term 'b)]
                       [a (ast/app-term 'a)]]
               :prog-key :same-program}))]
      (is (= left-key right-key)))))

(deftest canonical-state-keys-distinguish-non-equivalent-states
  (testing "different predicates, bindings, or program keys do not collapse"
    (ast/nom x
      (let [base {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))]
                  :sigma [[x (ast/app-term 'a)]]
                  :prog-key :program-a}
            different-predicate {:agenda [(ast/pos-lit (ast/app-term 'q (ast/var-term x)))]
                                 :sigma [[x (ast/app-term 'a)]]
                                 :prog-key :program-a}
            different-binding {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))]
                               :sigma [[x (ast/app-term 'b)]]
                               :prog-key :program-a}
            different-program {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))]
                               :sigma [[x (ast/app-term 'a)]]
                               :prog-key :program-b}
            different-env {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))]
                           :env [[x (ast/app-term 'b)]]
                           :prog-key :program-a}
            different-proof-vars {:agenda [(ast/neq-lit (ast/var-term x)
                                                        (ast/app-term 'a))]
                                  :proof-vars [x]
                                  :prog-key :program-a}]
        (is (not= (tabling/state-key base)
                  (tabling/state-key different-predicate)))
        (is (not= (tabling/state-key base)
                  (tabling/state-key different-binding)))
        (is (not= (tabling/state-key base)
                  (tabling/state-key different-program)))
        (is (not= (tabling/state-key base)
                  (tabling/state-key different-env)))
        (is (not= (tabling/state-key {:agenda [(ast/neq-lit (ast/var-term x)
                                                             (ast/app-term 'a))]
                                      :prog-key :program-a})
                  (tabling/state-key different-proof-vars)))))))

(deftest canonical-state-keys-distinguish-bounded-fuel
  (testing "bounded table entries are not reused across different fuel slices"
    (ast/nom x
      (let [state {:agenda [(ast/pos-lit (ast/app-term 'p (ast/var-term x)))]
                   :prog-key :program-a}]
        (is (not= (tabling/state-key (assoc state :fuel 1))
                  (tabling/state-key (assoc state :fuel 2))))))))

(deftest tabled-kernel-agrees-with-untabled-kernel-on-recursive-program
  (testing "tabling is an operational wrapper, not a new proof source"
    (let [program (even-odd-program)
          query (ast/pos-lit (ast/app-term 'odd (numeral 1)))
          proof-obligation (normalize/negate-formula query)
          untabled (kernel/prove-program program proof-obligation 1 16)
          tabled (tabling/prove-program program proof-obligation 1 16)]
      (is (seq untabled))
      (is (= (boolean (seq untabled))
             (boolean (seq tabled)))))))

(deftest tabled-kernel-reuses-duplicate-beta-substate
  (testing "the same canonical branch state is evaluated once inside one tabled run"
    (let [p-atom (ast/app-term 'p (ast/app-term 'a))
          close-form (ast/and-form (ast/pos-lit p-atom)
                                   (ast/neg-lit p-atom))
          formula (ast/or-form close-form close-form)
          stats (atom {})]
      (binding [tabling/*kernel-table-stats* stats]
        (is (seq (tabling/prove formula 1 8))))
      (is (= 1
             (get-in @stats
                     [:misses-by-key
                      (tabling/state-key {:agenda [close-form]
                                          :fuel 7})]))))))
