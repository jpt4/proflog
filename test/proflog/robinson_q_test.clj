(ns proflog.robinson-q-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [run]]
            [clojure.string :as str]
            [proflog.ast :as ast]
            [proflog.frontend :as pf]
            [proflog.kernel :as kernel]
            [proflog.kernel.equality-fragment :as equality-fragment]
            [proflog.kernel.robinson-q-profile :as rq-profile]
            [proflog.language :as language]
            [proflog.proof :as proof]
            [proflog.query :as query]
            [proflog.robinson-q :as rq]))

(def frontend-profile-language
  (pf/language
    (constants zero)
    (functions (s 1)
               (add 2)
               (mul 2))
    (relations)
    (proof-profile :robinson-q)))

(defn succeeds?
  [program formula fuel]
  (seq (query/query-succeeds program formula 1 fuel)))

(defn first-success-proof
  [program formula fuel]
  (first (query/query-succeeds program formula 1 fuel)))

(def q3-false-double-predecessor
  "A deliberately false guard theorem.

   Q3 says every nonzero value has one predecessor. It does not imply that every
   nonzero value has two predecessors; `s(zero)` is the standard counterexample.
   The unified Q3 rule must therefore not close this theorem merely by
   unifying a predecessor variable with another successor.
   "
  (ast/nom x y
    (ast/forall-form
      x
      (ast/implies-form
        (rq/neq (ast/var-term x) rq/zero)
        (ast/exists-form
          y
          (rq/eq (ast/var-term x)
                 (rq/s (rq/s (ast/var-term y)))))))))

(deftest robinson-q-language-keeps-arithmetic-in-term-namespace
  (testing "Q uses constants and function symbols, not procedural relations"
    (is (= #{'zero} (:constants rq/language)))
    (is (= {'zero 0
            's 1
            'add 2
            'mul 2}
           (:functions rq/language)))
    (is (empty? (:relations rq/language)))
    (is (= :robinson-q (:proof-profile rq/profile-language)))
    (is (= :robinson-q (:proof-profile frontend-profile-language)))))

(deftest robinson-q-axioms-are-valid-first-order-formulas
  (testing "the seven Q axioms validate over the arithmetic function language"
    (is (= 7 (count rq/axioms)))
    (doseq [[label formula] rq/axioms]
      (is (= formula (language/validate-query rq/language formula))
          (str label " should validate over the Q language")))))

(deftest ordinary-q-as-antecedent-proves-shared-formulas
  (testing "ordinary Q assumptions can prove selected consequences through the existing kernel"
    (doseq [[label theorem fuel] [[:q7 rq/q7 32]
                                  [:add-one-zero (rq/eq (rq/add (rq/numeral 1)
                                                                 rq/zero)
                                                        (rq/numeral 1))
                                   48]
                                  [:mul-two-zero (rq/eq (rq/mul (rq/numeral 2)
                                                                 rq/zero)
                                                        rq/zero)
                                   48]
                                  [:add-one-two (rq/eq (rq/add (rq/numeral 1)
                                                                (rq/numeral 2))
                                                       (rq/numeral 3))
                                   64]
                                  [:mul-two-two (rq/eq (rq/mul (rq/numeral 2)
                                                                (rq/numeral 2))
                                                       (rq/numeral 4))
                                   96]]]
      (let [proof (first-success-proof
                    rq/ordinary-program
                    (rq/q-implies theorem)
                    fuel)]
        (is proof (str "ordinary Q antecedent should prove " label))
        (is (not (proof/contains-step? proof 'robinson-q))
            "ordinary Q should not silently use the deduction-modulo profile")))))

(deftest profiled-robinson-q-proves-shared-formulas-by-conversion
  (testing "the opt-in profile proves the same examples with explicit profile evidence"
    (doseq [[label theorem fuel] [[:q7 rq/q7 16]
                                  [:add-one-zero (rq/eq (rq/add (rq/numeral 1)
                                                                 rq/zero)
                                                        (rq/numeral 1))
                                   16]
                                  [:mul-two-zero (rq/eq (rq/mul (rq/numeral 2)
                                                                 rq/zero)
                                                        rq/zero)
                                   16]
                                  [:add-one-two (rq/eq (rq/add (rq/numeral 1)
                                                                (rq/numeral 2))
                                                       (rq/numeral 3))
                                   16]
                                  [:mul-two-two (rq/eq (rq/mul (rq/numeral 2)
                                                                (rq/numeral 2))
                                                       (rq/numeral 4))
                                   16]]]
      (let [proof (first-success-proof rq/profile-program theorem fuel)]
        (is proof (str "profiled Q should prove " label))
        (is (proof/contains-step? proof 'profiled))
        (is (proof/contains-step? proof 'robinson-q))))))

(deftest profiled-robinson-q-records-repeated-arithmetic-conversion
  (testing "profile conversion records repeated add/mul rewrites at branch closure"
    (let [add-proof (first-success-proof
                      rq/profile-program
                      (rq/eq (rq/add (rq/numeral 1)
                                     (rq/numeral 2))
                             (rq/numeral 3))
                      16)
          mul-proof (first-success-proof
                      rq/profile-program
                      (rq/eq (rq/mul (rq/numeral 2)
                                     (rq/numeral 2))
                             (rq/numeral 4))
                      16)]
      (is add-proof)
      (is (proof/contains-step? add-proof 'q-rewrite))
      (is mul-proof)
      (is (proof/contains-step? mul-proof 'q-rewrite)))))

(deftest robinson-q-normalizer-canonically-rewrites-ground-arithmetic
  (testing "ground Q arithmetic does not leak unreduced neutral alternatives"
    (let [add-results (run 2 [out proof]
                        (rq-profile/q-normal-termo
                          (rq/add (rq/numeral 1) (rq/numeral 2))
                          out
                          proof))
          mul-results (run 2 [out proof]
                        (rq-profile/q-normal-termo
                          (rq/mul (rq/numeral 2) (rq/numeral 2))
                          out
                          proof))]
      (is (= 1 (count add-results)))
      (is (= (rq/numeral 3) (ffirst add-results)))
      (is (proof/contains-step? (second (first add-results)) 'q-rewrite))
      (is (= 1 (count mul-results)))
      (is (= (rq/numeral 4) (ffirst mul-results)))
      (is (proof/contains-step? (second (first mul-results)) 'q-rewrite)))))

(deftest q3-is-proved-by-ordinary-assumptions-and-profile-case-split
  (testing "ordinary Q proves Q3 from assumptions, while the profile records the Q3 case split"
    (let [ordinary-proof (first-success-proof
                           rq/ordinary-program
                           (rq/q-implies rq/q3)
                           32)
          profile-proof (first-success-proof
                          rq/profile-program
                          rq/q3
                          32)]
      (is ordinary-proof)
      (is (not (proof/contains-step? ordinary-proof 'robinson-q)))
      (is profile-proof)
      (is (proof/contains-step? profile-proof 'profiled))
      (is (proof/contains-step? profile-proof 'robinson-q))
      (is (proof/contains-step? profile-proof 'q3-predecessor-equality))
      (is (not (proof/contains-step? profile-proof 'q3-case-split))))))

(deftest full-q3-profile-rule-proves-add-one-predecessor-theorem
  (testing "Q3 can be used inside a larger refutation after Q4/Q5 conversion"
    (let [ordinary-proof (first-success-proof
                           rq/ordinary-program
                           (rq/q-implies rq/q3-add-one-predecessor)
                           64)
          profile-proof (first-success-proof
                          rq/profile-program
                          rq/q3-add-one-predecessor
                          48)]
      (is ordinary-proof)
      (is (not (proof/contains-step? ordinary-proof 'robinson-q)))
      (is profile-proof)
      (is (proof/contains-step? profile-proof 'witness))
      (is (proof/contains-step? profile-proof 'once-univ))
      (is (proof/contains-step? profile-proof 'neq-store))
      (is (proof/contains-step? profile-proof 'q3-predecessor-equality))
      (is (not (proof/contains-step? profile-proof 'q3-predecessor-intro)))
      (is (proof/contains-step? profile-proof 'q-rewrite)))))

(deftest final-q3-profile-rule-proves-contextual-predecessor-theorem
  (testing "Q3 is available as a predecessor equality under Q conversion and congruence"
    (let [ordinary-proof (first-success-proof
                           rq/ordinary-program
                           (rq/q-implies rq/q3-contextual-successor-predecessor)
                           16)
          profile-proof (first-success-proof
                          rq/profile-program
                          rq/q3-contextual-successor-predecessor
                          16)]
      (is ordinary-proof)
      (is (not (proof/contains-step? ordinary-proof 'robinson-q)))
      (is profile-proof)
      (is (proof/contains-step? profile-proof 'witness))
      (is (proof/contains-step? profile-proof 'once-univ))
      (is (proof/contains-step? profile-proof 'neq-store))
      (is (proof/contains-step? profile-proof 'q3-predecessor-equality))
      (is (proof/contains-step? profile-proof 'q-rewrite)))))

(deftest final-q3-profile-rule-does-not-invent-second-predecessors
  (testing "the trusted Q3 equality must not force a deeper successor shape"
    (is (empty? (query/query-succeeds
                  rq/profile-program
                  q3-false-double-predecessor
                  1
                  16)))))

(deftest nontrivial-q-theorem-examples-prove-under-both-q-versions
  (testing "promoted Q theorem examples prove as ordinary assumptions and as profiled theory proofs"
    (doseq [[label theorem ordinary-fuel profile-fuel needs-q3?]
            [[:add-right-two-successors
              rq/add-right-two-successors
              64
              16
              false]
             [:mul-right-two-normal-form
              rq/mul-right-two-normal-form
              96
              16
              false]
             [:q3-add-two-successor
              rq/q3-add-two-successor
              64
              32
              true]]]
      (let [ordinary-proof (first-success-proof
                             rq/ordinary-program
                             (rq/q-implies theorem)
                             ordinary-fuel)
            profile-proof (first-success-proof
                            rq/profile-program
                            theorem
                            profile-fuel)]
        (is ordinary-proof (str "ordinary Q should prove " label))
        (is (not (proof/contains-step? ordinary-proof 'robinson-q))
            "ordinary Q should not use the profile")
        (is profile-proof (str "profiled Q should prove " label))
        (is (proof/contains-step? profile-proof 'profiled))
        (is (proof/contains-step? profile-proof 'robinson-q))
        (is (proof/contains-step? profile-proof 'q-rewrite))
        (if needs-q3?
          (is (proof/contains-step? profile-proof 'q3-predecessor-equality)
              (str label " should use Q3 predecessor equality"))
          (is (not (proof/contains-step? profile-proof 'q3-predecessor-equality))
              (str label " should be conversion-only")))))))

(deftest corrected-prime-evenness-examples-prove-as-q-antecedents-under-both-languages
  (testing "corrected primality excludes one and excludes two from the evenness theorem"
    (doseq [[label theorem fuel]
            [[:no-two-factor rq/prime-other-than-two-has-no-two-factor 128]
             [:not-left-even rq/prime-other-than-two-is-not-left-even 128]]]
      (let [ordinary-proof (first-success-proof
                             rq/ordinary-program
                             (rq/q-implies theorem)
                             fuel)
            profile-proof (first-success-proof
                            rq/profile-program
                            (rq/q-implies theorem)
                            fuel)]
        (is ordinary-proof (str "ordinary Q should prove corrected prime example " label))
        (is (not (proof/contains-step? ordinary-proof 'robinson-q))
            "ordinary Q should not silently use the Robinson-Q profile")
        (is profile-proof (str "profiled language should preserve the Q-as-antecedent proof for " label))
        (is (proof/contains-step? profile-proof 'equality-fragment)
            "the corrected prime examples should close through the generic equality fragment")
        (is (not (proof/contains-step? profile-proof 'q-rewrite))
            "these examples follow from the inline prime definition, not Q arithmetic conversion")
        (is (not (proof/contains-step? profile-proof 'q3-predecessor-equality))
            "these examples do not need Q3 predecessor synthesis")))))

(deftest robinson-q-profile-preserves-equality-fragment-fast-path
  (testing "Q profile dispatch keeps the generic equality-fragment sidecar before full Q search"
    (let [calls (atom [])
          sentinel '((profiled equality-fragment prime-fast-path))]
      (with-redefs [equality-fragment/prove-program-host
                    (fn [program formula proof-limit fuel]
                      (swap! calls conj [program formula proof-limit fuel])
                      sentinel)
                    kernel/prove-programo
                    (fn [& _]
                      (throw (ex-info "full Q kernel should not run after equality-fragment success"
                                      {})))]
        (is (= sentinel
               (rq-profile/prove-program
                 rq/profile-program
                 rq/prime-other-than-two-has-no-two-factor
                 1
                 128)))
        (is (= [[rq/profile-program
                 rq/prime-other-than-two-has-no-two-factor
                 1
                 128]]
               @calls))))))

(deftest profiled-robinson-q-theory-rules-are-interleaved-with-kernel-steps
  (testing "Q theory closure happens after ordinary kernel branch decomposition"
    (let [q3-proof (first-success-proof rq/profile-program rq/q3 32)
          q7-proof (first-success-proof rq/profile-program rq/q7 16)]
      (is (proof/contains-step? q3-proof 'witness))
      (is (proof/contains-step? q3-proof 'once-univ))
      (is (proof/contains-step? q3-proof 'neq-store))
      (is (proof/contains-step? q3-proof 'q3-predecessor-equality))
      (is (not (proof/contains-step? q3-proof 'q3-case-split)))
      (is (proof/contains-step? q7-proof 'witness))
      (is (proof/contains-step? q7-proof 'q-rewrite)))))

(deftest robinson-q-profile-source-uses-kernel-theory-rules
  (testing "the Q profile no longer proves by whole-formula host preprocessing"
    (let [source (slurp "src/proflog/kernel/robinson_q_profile.clj")]
      (is (str/includes? source "robinson-q-theory-closeo"))
      (is (str/includes? source "q-normal-termo"))
      (is (str/includes? source "q3-predecessor-equality-closeo"))
      (is (not (str/includes? source "q-normalize-formula")))
      (is (not (str/includes? source "q3-predecessor-refutation?")))
      (is (not (str/includes? source "q3-case-splito")))
      (is (not (str/includes? source "q3-predecessor-intro-closeo")))
      (is (not (str/includes? source "q3-add-one-predecessor"))))))
