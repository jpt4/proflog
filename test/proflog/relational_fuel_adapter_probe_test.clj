(ns proflog.relational-fuel-adapter-probe-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.logic :refer [== fresh run]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.relational-arithmetic :as arith]
            [proflog.relational-fuel-adapter-probe :as adapter]))

(def closing-conjunction
  (ast/and-form
    (ast/pos-lit (ast/app-term 'p))
    (ast/neg-lit (ast/app-term 'p))))

(deftest boundary-fuel-values-convert-only-at-the-entry-shape
  (testing "ground public integer fuel becomes an internal bit-list numeral"
    (is (= (list (arith/build-num 2))
           (run 1 [internal-fuel]
             (adapter/boundary-fuelo 2 internal-fuel)))))
  (testing "the unbounded sentinel remains nil"
    (is (= '(nil)
           (run 1 [internal-fuel]
             (adapter/boundary-fuelo nil internal-fuel)))))
  (testing "negative host fuel is rejected"
    (is (= '()
           (run 1 [internal-fuel]
             (adapter/boundary-fuelo -1 internal-fuel))))))

(deftest direct-step-synthesis-compares-fd-host-values-with-bit-list-values
  (testing "production finite-domain step synthesizes host integer fuel"
    (is (= '(1)
           (run 1 [fuel]
             (support/step-fuelo fuel 0))))
    (is (= '(0)
           (run 1 [next-fuel]
             (support/step-fuelo 1 next-fuel)))))
  (testing "the adapter accepts host inputs but synthesizes internal bit-lists"
    (is (= (list (arith/build-num 1))
           (run 1 [fuel]
             (adapter/step-fuelo fuel 0))))
    (is (= (list (arith/build-num 0))
           (run 1 [next-fuel]
             (adapter/step-fuelo 1 next-fuel)))))
  (testing "unbounded fuel is still a separate nil case"
    (is (= '(nil)
           (run 1 [fuel]
             (adapter/step-fuelo fuel nil))))))

(deftest direct-step-ground-boundaries-cover-small-finite-fuel-slices
  (testing "ground host fuel consumes to the expected internal bit-list successor"
    (doseq [fuel (range 0 8)]
      (let [expected (if (pos? fuel)
                       (list (arith/build-num (dec fuel)))
                       '())]
        (is (= expected
               (run 1 [next-fuel]
                 (adapter/step-fuelo fuel next-fuel)))
            (str "unexpected next fuel for host fuel " fuel)))))
  (testing "known host next-fuel synthesizes the expected internal predecessor"
    (doseq [next-fuel (range 0 7)]
      (is (= (list (arith/build-num (inc next-fuel)))
             (run 1 [fuel]
               (adapter/step-fuelo fuel next-fuel)))
          (str "unexpected predecessor for host next fuel " next-fuel)))))

(deftest finite-host-entry-continues-as-a-bit-list-fuel-chain
  (testing "after entry conversion, recursive steps stay in the internal numeral representation"
    (is (= (list [(arith/build-num 2)
                  (arith/build-num 1)
                  (arith/build-num 0)])
           (run 1 [q]
             (fresh [fuel-2 fuel-1 fuel-0]
               (adapter/step-fuelo 3 fuel-2)
               (adapter/step-fuelo fuel-2 fuel-1)
               (adapter/step-fuelo fuel-1 fuel-0)
               (== [fuel-2 fuel-1 fuel-0] q)))))))

(deftest bit-list-reverse-synthesis-avoids-the-fd-interval-boundary
  (testing "a known internal successor synthesizes an unbounded bit-list predecessor"
    (is (= (list (arith/build-num 1))
           (run 1 [fuel]
             (adapter/step-fuelo fuel (arith/build-num 0)))))
    (is (= (list (arith/build-num 2))
           (run 1 [fuel]
             (adapter/step-fuelo fuel (arith/build-num 1)))))))

(deftest boundary-conversion-has-no-production-fd-long-domain-check
  (testing "the adapter can encode a host integer outside the production fd interval"
    (let [beyond-long-max (bigint "9223372036854775808")]
      (is (= (list (arith/build-num beyond-long-max))
             (run 1 [internal-fuel]
               (adapter/boundary-fuelo beyond-long-max internal-fuel)))))))

(deftest simple-kernel-proof-matches-production-from-host-integer-boundary
  (testing "finite host integer fuel remains the caller-facing entry value"
    (let [production (run 1 [proof]
                       (kernel/proveo closing-conjunction '() '() '() 2 proof))
          adapted (adapter/prove-with-adapted-fuel closing-conjunction 1 2)]
      (is (seq production))
      (is (= production adapted))))
  (testing "the adapter tracks the direct full-kernel result for smaller finite slices"
    (is (= (run 1 [proof]
             (kernel/proveo closing-conjunction '() '() '() 1 proof))
           (adapter/prove-with-adapted-fuel closing-conjunction 1 1)))
    (is (= (run 1 [proof]
             (kernel/proveo closing-conjunction '() '() '() 0 proof))
           (adapter/prove-with-adapted-fuel closing-conjunction 1 0)))))

(deftest open-kernel-fuel-still-prefers-the-unbounded-sentinel
  (testing "open public fuel synthesis keeps the existing first answer"
    (with-redefs [support/step-fuelo adapter/step-fuelo]
      (is (= '(nil)
             (run 1 [fuel]
               (fresh [proof]
                 (kernel/proveo closing-conjunction '() '() '() fuel proof))))))))
