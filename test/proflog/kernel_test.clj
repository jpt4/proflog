(ns proflog.kernel-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [== fresh run run*]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.language :as language]))

(defn provable?
  "True when the greenfield kernel finds at least one closed tableau."
  [formula]
  (seq (kernel/prove formula 1)))

(defn not-provable?
  "True when the greenfield kernel finds no proof within the given bound."
  ([formula] (not-provable? formula 1 nil))
  ([formula n] (not-provable? formula n nil))
  ([formula n fuel]
   (empty?
     (if (nil? fuel)
       (kernel/prove formula n)
       (kernel/prove formula n fuel)))))

(deftest direct-complementary-closure
  (testing "a positive and negative copy of the same atom close immediately"
    (is (provable?
          (ast/and-form
            (ast/pos-lit (ast/app-term 'p))
            (ast/neg-lit (ast/app-term 'p)))))))

(deftest beta-splitting-requires-both-branches-to-close
  (testing "a disjunction closes only when both branches close"
    (is (provable?
          (ast/or-form
            (ast/and-form
              (ast/pos-lit (ast/app-term 'p))
              (ast/neg-lit (ast/app-term 'p)))
            (ast/and-form
              (ast/pos-lit (ast/app-term 'q))
              (ast/neg-lit (ast/app-term 'q))))))
    (is (not-provable?
          (ast/or-form
            (ast/and-form
              (ast/pos-lit (ast/app-term 'p))
              (ast/neg-lit (ast/app-term 'p)))
            (ast/pos-lit (ast/app-term 'q)))))))

(deftest gamma-rule-instantiates-universals
  (testing "a universal formula can close against a contrary ground literal"
    (ast/nom x
      (is (provable?
            (ast/and-form
              (ast/forall-form x
                               (ast/pos-lit
                                 (ast/app-term 'value (ast/var-term x))))
              (ast/neg-lit (ast/app-term 'value (ast/app-term 'zero)))))))))

(deftest delta-rule-introduces-one-rigid-witness
  (testing "an existential witness can close a contradiction inside its body"
    (ast/nom x
      (is (provable?
            (ast/exists-form
              x
              (ast/and-form
                (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                (ast/neg-lit (ast/app-term 'value (ast/var-term x))))))))))

(deftest once-universal-instantiates-without-reenqueueing
  (testing "a single-use universal closes its instantiated body once without needing an open-ended gamma loop"
    (ast/nom x
      (is (provable?
            (ast/once-forall-form
              x
              (ast/and-form
                (ast/pos-lit (ast/app-term 'value (ast/var-term x)))
                (ast/neg-lit (ast/app-term 'value (ast/var-term x))))))))))

(deftest nested-conjunctions-close-across-queued-literals
  (testing "later literals can still close against atoms saved from outer conjunction frames"
    (is (provable?
          (ast/and-form
            (ast/pos-lit (ast/app-term 'p))
            (ast/and-form
              (ast/pos-lit (ast/app-term 'q))
              (ast/neg-lit (ast/app-term 'p))))))))

(deftest bounded-fuel-charges-structural-branch-progress
  (testing "fuel zero allows immediate closure only, not arbitrary agenda progress"
    (let [formula (ast/and-form
                    (ast/pos-lit (ast/app-term 'p))
                    (ast/neg-lit (ast/app-term 'p)))]
      (is (not-provable? formula 1 0))
      (is (not-provable? formula 1 1))
      (is (seq (kernel/prove formula 1 2))))))

(deftest fuel-step-supports-reverse-and-partial-synthesis
  (testing "step-fuelo can synthesize a predecessor fuel value from a known successor"
    (is (= '(1)
           (run 1 [fuel]
             (support/step-fuelo fuel 0)))))
  (testing "step-fuelo can synthesize the successor from a known predecessor"
    (is (= '(0)
           (run 1 [next-fuel]
             (support/step-fuelo 1 next-fuel)))))
  (testing "step-fuelo can synthesize the unbounded fuel case"
    (is (= '(nil)
           (run 1 [fuel]
             (support/step-fuelo fuel nil))))))

(deftest kernel-synthesizes-open-fuel-for-structural-progress
  (testing "a direct relational caller can leave fuel open while proving a branch"
    (let [formula (ast/and-form
                    (ast/pos-lit (ast/app-term 'p))
                    (ast/neg-lit (ast/app-term 'p)))]
      (is (= '(nil)
             (run 1 [fuel]
               (fresh [proof]
                 (kernel/proveo formula '() '() '() fuel proof))))))))

(deftest universal-without-a-contrary-literal-stays-open
  (testing "a universal formula alone is not enough to close a branch"
    (ast/nom x
      ;; This branch is semantically open, so keep the regression on a bounded
      ;; search slice rather than asking unbounded proof search to terminate.
      (is (not-provable?
            (ast/forall-form
              x
              (ast/pos-lit (ast/app-term 'value (ast/var-term x))))
            1
            2)))))

(deftest existential-with-a-satisfiable-body-stays-open
  (testing "an existential whose body has no contradiction does not close the branch"
    (ast/nom x
      ;; Keep the open-branch check on a finite slice for the same reason as
      ;; the universal test above.
      (is (not-provable?
            (ast/exists-form
              x
              (ast/pos-lit (ast/app-term 'value (ast/var-term x))))
            1
            2)))))

(deftest disequality-can-close-by-binding-multiple-proof-variables
  (testing "a universal disequality may need more than one fresh-variable binding to refute"
    (ast/nom x y
      (is (provable?
            (ast/forall-form
              x
              (ast/forall-form
                y
                (ast/neq-lit
                  (ast/app-term 'pair
                                (ast/app-term 'a)
                                (ast/app-term 'b))
                  (ast/app-term 'pair
                                (ast/var-term x)
                                (ast/var-term y))))))))))

(deftest rigid-constructor-disequality-support
  (testing "constructor head and nested argument clashes are rigidly different"
    (is (= '(:rigid)
           (run 1 [q]
             (support/rigid-different-termo
               (ast/app-term 'cons (ast/app-term 'a) (ast/app-term 'null))
               (ast/app-term 'null)
               '())
             (== q :rigid))))
    (is (= '(:rigid)
           (run 1 [q]
             (support/rigid-different-termo
               (ast/app-term 'pair
                             (ast/app-term 'a)
                             (ast/app-term 'null))
               (ast/app-term 'pair
                             (ast/app-term 'b)
                             (ast/app-term 'null))
               '())
             (== q :rigid)))))
  (testing "unresolved variables are not rigidly different from constructors"
    (ast/nom x
      (is (empty?
            (run 1 [q]
              (support/rigid-different-termo
                (ast/var-term x)
                (ast/app-term 'a)
                '())
              (== q :not-rigid)))))))

(deftest rigid-disequality-is-discharged-before-symbolic-storage
  (testing "a constructor clash continues the branch without adding a delayed disequality"
    (is (= '(())
           (run 1 [neqs-out]
             (fresh [sigma-out]
               (kernel/prove-stateo
                 (ast/neq-lit
                   (ast/app-term 'cons (ast/app-term 'a) (ast/app-term 'null))
                   (ast/app-term 'null))
                 (list (ast/neg-lit (ast/app-term 'done)))
                 (list (ast/pos-lit (ast/app-term 'done)))
                 '()
                 '()
                 '()
                 sigma-out
                 '()
                 neqs-out
                 nil
                 '()
                 nil
                 '(neq-rigid (close))))))))
  (testing "a symbolic disequality is still delayed for later equality checks"
    (ast/nom x
      (is (= '(:delayed)
             (run 1 [q]
               (fresh [sigma-out neqs-out delayed-left]
                 (kernel/prove-stateo
                   (ast/neq-lit (ast/var-term x) (ast/app-term 'a))
                   (list (ast/neg-lit (ast/app-term 'done)))
                   (list (ast/pos-lit (ast/app-term 'done)))
                   '()
                   '()
                   '()
                   sigma-out
                   '()
                   neqs-out
                   nil
                   '()
                   nil
                   '(neq-store (close)))
                 (== (list [delayed-left (ast/app-term 'a)]) neqs-out)
                 (== q :delayed))))))))

(deftest nested-once-universals-can-share-list-shape-bindings-across-disjunctions
  (testing "one append-shaped witness assignment can refute several disequalities on the same branch"
    (ast/nom h t r
      (is (provable?
            (ast/once-forall-form
              h
              (ast/once-forall-form
                t
                (ast/once-forall-form
                  r
                  (ast/or-form
                    (ast/neq-lit
                      (ast/app-term 'cons
                                    (ast/app-term 'a)
                                    (ast/app-term 'null))
                      (ast/app-term 'cons
                                    (ast/var-term h)
                                    (ast/var-term t)))
                    (ast/or-form
                      (ast/neq-lit
                        (ast/app-term 'cons
                                      (ast/app-term 'a)
                                      (ast/app-term 'cons
                                                    (ast/app-term 'b)
                                                    (ast/app-term 'null)))
                        (ast/app-term 'cons
                                      (ast/var-term h)
                                      (ast/var-term r)))
                      (ast/neq-lit
                        (ast/var-term r)
                        (ast/app-term 'cons
                                      (ast/app-term 'b)
                                      (ast/app-term 'null)))))))))))))

(deftest outer-env-bindings-flow-through-nested-once-universals
  (testing "clause-parameter environments still substitute through nested single-use universals"
    (ast/nom a0 a2 h t r
      (let [env (list [a0 (ast/app-term 'cons
                                        (ast/app-term 'a)
                                        (ast/app-term 'null))]
                      [a2 (ast/app-term 'cons
                                        (ast/app-term 'a)
                                        (ast/app-term 'cons
                                                      (ast/app-term 'b)
                                                      (ast/app-term 'null)))])
            formula (ast/once-forall-form
                      h
                      (ast/once-forall-form
                        t
                        (ast/once-forall-form
                          r
                          (ast/or-form
                            (ast/neq-lit
                              (ast/var-term a0)
                              (ast/app-term 'cons
                                            (ast/var-term h)
                                            (ast/var-term t)))
                            (ast/or-form
                              (ast/neq-lit
                                (ast/var-term a2)
                                (ast/app-term 'cons
                                              (ast/var-term h)
                                              (ast/var-term r)))
                              (ast/neq-lit
                                (ast/var-term r)
                                (ast/app-term 'cons
                                              (ast/app-term 'b)
                                              (ast/app-term 'null))))))))]
        (is (seq
              (run 1 [proof]
                (kernel/proveo formula '() '() env 32 proof))))))))

(deftest proveo-accepts-a-partially-specified-proof-shape
  (testing "the kernel relation can fill the tail of a constrained proof skeleton"
    (is (= ['(savefml (close))]
           (run 1 [tail]
             (kernel/proveo
               (ast/and-form
                 (ast/pos-lit (ast/app-term 'p))
                 (ast/neg-lit (ast/app-term 'p)))
               '() '() '() (list 'conj tail)))))))

(def guarded-call-language
  (language/language
    {:constants ['zero]
     :relations {'r 1}}))

(defn guarded-call-program
  []
  (ast/nom x
    (language/compile-program
      guarded-call-language
      [(ast/clause 'r [x]
                   (ast/neq-lit (ast/var-term x)
                                (ast/var-term x)))])))

(deftest l-ground-helper-rejects-unresolved-parameters
  (testing "L-groundness accepts object-language terms and rejects terms containing par"
    (ast/nom p
      (is (seq
            (run 1 [_]
              (kernel/l-ground-termo
                (ast/app-term 'zero)))))
      (is (seq
            (run 1 [_]
              (kernel/l-ground-termo
                (ast/app-term 's (ast/app-term 'zero))))))
      (is (empty?
            (run 1 [_]
              (kernel/l-ground-termo
                (ast/par-term p)))))
      (is (empty?
            (run 1 [_]
              (kernel/l-ground-termo
                (ast/app-term 's (ast/par-term p)))))))))

(deftest plain-procedure-calls-wait-for-parameter-equality-to-walk-into-l
  (testing "plain calls are blocked on unresolved par arguments but reopen after equality walks them to L-terms"
    (ast/nom p
      (let [program (guarded-call-program)]
        (is (empty?
              (kernel/prove-program
                program
                (ast/pos-lit (ast/app-term 'r (ast/par-term p)))
                1)))
        (is (seq
              (kernel/prove-program
                program
                (ast/and-form
                  (ast/eq-lit (ast/par-term p) (ast/app-term 'zero))
                  (ast/pos-lit (ast/app-term 'r (ast/par-term p))))
                1)))))))

(deftest disequality-maintenance-supports-reverse-branch-state
  (testing "the equality continuation can synthesize an empty stable disequality store"
    (is (= '(())
           (run 1 [neqs]
             (fresh [sigma-out neqs-out]
               (kernel/prove-stateo
                 (ast/eq-lit (ast/app-term 'a) (ast/app-term 'a))
                 (list (ast/neg-lit (ast/app-term 'p)))
                 (list (ast/pos-lit (ast/app-term 'p)))
                 '()
                 '()
                 '()
                 sigma-out
                 neqs
                 neqs-out
                 nil
                 '()
                 nil
                 (list 'eq-step '(eq-refl) '(close))))))))
  (testing "post-closure pruning can accept a sigma refined after the kernel step"
    (ast/nom x
      (is (= '(())
             (run 1 [sigma]
               (fresh [sigma-out neqs-out]
                 (kernel/prove-stateo
                   (ast/neg-lit (ast/app-term 'p))
                   '()
                   (list (ast/pos-lit (ast/app-term 'p)))
                   '()
                   '()
                   sigma
                   sigma-out
                   (list [(ast/var-term x) (ast/app-term 'a)])
                   neqs-out
                   nil
                   '()
                   nil
                   '(close))
                 (== sigma '()))))))))

(deftest disequality-maintenance-does-not-freeze-stale-partial-terms
  (testing "pruning sees a later term refinement that makes a saved disequality reflexive"
    (is (= '(())
           (run* [neqs-out]
             (fresh [term sigma-out]
               (kernel/prove-stateo
                 (ast/neg-lit (ast/app-term 'p))
                 '()
                 (list (ast/pos-lit (ast/app-term 'p)))
                 '()
                 '()
                 '()
                 sigma-out
                 (list [term (ast/app-term 'a)])
                 neqs-out
                 nil
                 '()
                 nil
                 '(close))
               (== term (ast/app-term 'a)))))))
  (testing "stable-neqso rejects an equality continuation after the saved pair becomes reflexive"
    (is (empty?
          (run* [neqs-out]
            (fresh [term sigma-out]
              (kernel/prove-stateo
                (ast/eq-lit (ast/app-term 'a) (ast/app-term 'a))
                (list (ast/neg-lit (ast/app-term 'p)))
                (list (ast/pos-lit (ast/app-term 'p)))
                '()
                '()
                '()
                sigma-out
                (list [term (ast/app-term 'a)])
                neqs-out
                nil
                '()
                nil
                (list 'eq-step '(eq-refl) '(close)))
              (== term (ast/app-term 'a))))))))
