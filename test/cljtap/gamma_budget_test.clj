(ns cljtap.gamma-budget-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.core.logic :refer :all :rename {appendo logic-appendo
                                                     membero logic-membero
                                                     is logic-is}]
            [clojure.core.logic.nominal :refer [tie hash]]
            [cljtap.alphaleantap-ep :refer :all :exclude [nom]]))

(defmacro nom [& args]
  (let [syms (butlast args)
        body (last args)]
    `(clojure.core.logic.nominal/fresh [~@syms] ~body)))

;; ============================================================================
;; γ-budget Tests: Verify that bounded γ-expansion works correctly
;; ============================================================================

(deftest test-GB01-budget-nil-backward-compat
  (testing "GB01: gamma-budget=nil behaves identically to original proveo.
            neq(a,a) closes by refl-close (no gamma needed)."
    (is (seq
          (run 1 [proof]
            (proveo ['neq ['app 'a] ['app 'a]]
                    '() '() '() '() proof nil))))))

(deftest test-GB02-budget-zero-no-gamma
  (testing "GB02: gamma-budget=0 still closes non-gamma formulas.
            neq(a,a) closes by refl-close, no gamma needed."
    (is (seq
          (run 1 [proof]
            (proveo ['neq ['app 'a] ['app 'a]]
                    '() '() '() '() proof 0))))))

(deftest test-GB03-budget-zero-free-close
  (testing "GB03: gamma-budget=0, free closure eq(a,b) works."
    (is (seq
          (run 1 [proof]
            (proveo ['eq ['app 'a] ['app 'b]]
                    '() '() '() '() proof 0))))))

(deftest test-GB04-budget-zero-blocks-gamma
  (testing "GB04: gamma-budget=0 prevents γ-rule from firing.
            ∀x.neq(x,x) needs 1 gamma to instantiate, so budget=0 fails."
    (is (empty?
          (run 1 [proof]
            (nom x
              (proveo ['forall (tie x ['neq ['var x] ['var x]])]
                      '() '() '() '() proof 0)))))))

(deftest test-GB05-budget-one-allows-gamma
  (testing "GB05: gamma-budget=1 allows 1 γ-application.
            ∀x.neq(x,x) needs 1 gamma → succeeds at budget=1."
    (is (seq
          (run 1 [proof]
            (nom x
              (proveo ['forall (tie x ['neq ['var x] ['var x]])]
                      '() '() '() '() proof 1)))))))

(deftest test-GB06-budget-terminates-non-theorem
  (testing "GB06: gamma-budget makes non-theorems terminate.
            ∀x.(x=a) — NOT valid (x could be b). With unbounded gamma
            the re-enqueued ∀ would diverge. Budget=1 terminates.
            Note: the free variable from γ may unify with (app s) where
            s ≠ a, closing via free-closure. So the formula IS refutable
            (provably false in all weak Herbrand models: take x=b).
            Instead, test a formula that truly diverges without budget:
            ∀x.(pos (app p (var x))) — no closure rule applies to a
            pos literal when there's no program and no complementary neg."
    (is (empty?
          (run 1 [proof]
            (nom x
              (proveo ['forall (tie x ['pos ['app 'p ['var x]]])]
                      '() '() '() '() proof 1)))))))

(deftest test-GB07-conjunction-with-gamma
  (testing "GB07: gamma-budget works through α-rule (conjunction).
            (and ∀x.neq(x,x) eq(a,b)) — needs 1 gamma for the ∀,
            plus free-close for eq(a,b). Budget=2 should suffice."
    (is (seq
          (run 1 [proof]
            (nom x
              (proveo ['and ['forall (tie x ['neq ['var x] ['var x]])]
                            ['eq ['app 'a] ['app 'b]]]
                      '() '() '() '() proof 2)))))))

(deftest test-GB08-beta-shares-budget
  (testing "GB08: β-rule gives both branches the same remaining budget.
            (or ∀x.neq(x,x) ∀y.neq(y,y)) — each branch needs 1 gamma.
            Budget=1 should suffice (both branches independently get budget=1)."
    (is (seq
          (run 1 [proof]
            (nom x y
              (proveo ['or ['forall (tie x ['neq ['var x] ['var x]])]
                           ['forall (tie y ['neq ['var y] ['var y]])]]
                      '() '() '() '() proof 1)))))))

(deftest test-GB09-proc-call-with-budget
  (testing "GB09: Procedure call subsidiary gets gamma-budget.
            Program: r() ← ∀x.neq(x,x).
            r() is FALSE (∀x.neq(x,x) is false: take x=a, neq(a,a) fails).
            pos-call proves it: body goes on branch, γ gives neq(v,v) → refl-close."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['r [] ['forall (tie x ['neq ['var x] ['var x]])]]]]
                (proveo ['pos ['app 'r]]
                        '() '() '() prog proof 1))))))))

(deftest test-GB10-nested-forall-budget
  (testing "GB10: Nested ∀x.∀y.neq(x,y) requires 2 gamma applications.
            Budget=1: fails (only outer ∀ instantiates).
            Budget=2: succeeds (both ∀s instantiate)."
    ;; ∀x.∀y.neq(x,y) is NOT valid (x could equal y), so it fails regardless.
    ;; But the key test: budget=2 terminates, budget=1 terminates, neither diverges.
    ;; With ∀x.∀y.(neq(x,x)) — inner neq always closes after 2 gammas:
    (is (seq
          (run 1 [proof]
            (nom x y
              (proveo ['forall (tie x ['forall (tie y ['neq ['var x] ['var x]])])]
                      '() '() '() '() proof 2)))))
    ;; Budget=1: only outer ∀ fires, inner ∀ stays in unexp, no more gamma
    (is (empty?
          (run 1 [proof]
            (nom x y
              (proveo ['forall (tie x ['forall (tie y ['neq ['var x] ['var x]])])]
                      '() '() '() '() proof 1)))))))

(deftest test-GB11-once-forall-no-budget
  (testing "GB11: once-forall does NOT consume gamma-budget.
            once-forall x.neq(x,x) works even at budget=0."
    (is (seq
          (run 1 [proof]
            (nom x
              (proveo ['once-forall (tie x ['neq ['var x] ['var x]])]
                      '() '() '() '() proof 0)))))))
