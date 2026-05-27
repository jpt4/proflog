(ns proflog.turing-completeness-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.frontend :as pf]
            [proflog.proof :as proof]
            [proflog.query :as query]
            [proflog.turing-completeness :as tc]
            [proflog.turing-completeness-long-probe :as long-probe]))

(defn- succeeds?
  [program formula fuel]
  (seq (query/query-succeeds program formula 1 fuel)))

(defn- proof-backed?
  [proofs]
  (and (seq proofs)
       (some #(or (proof/contains-step? % 'neg-call)
                  (proof/contains-step? % 'pos-call)
                  (proof/contains-step? % 'neg-call-guarded-alt)
                  (proof/contains-step? % 'pos-call-guarded-alt))
             proofs)))

(deftest two-counter-machine-step-cases-close-through-the-kernel
  (let [program (tc/transfer-program)]
    (testing "decjz0 takes the zero branch without changing counters"
      (let [proofs (query/query-succeeds
                     program
                     (ast/pos-lit
                       (ast/app-term 'step
                                     (tc/config 'l0 0 1)
                                     (tc/config 'halt-label 0 1)))
                     1
                     32)]
        (is (proof-backed? proofs))))
    (testing "decjz0 takes the decrement branch on a successor counter"
      (let [proofs (query/query-succeeds
                     program
                     (ast/pos-lit
                       (ast/app-term 'step
                                     (tc/config 'l0 2 1)
                                     (tc/config 'l1 1 1)))
                     1
                     32)]
        (is (proof-backed? proofs))))
    (testing "inc1 increments counter1 and moves to its next label"
      (let [proofs (query/query-succeeds
                     program
                     (ast/pos-lit
                       (ast/app-term 'step
                                     (tc/config 'l1 1 1)
                                     (tc/config 'l0 1 2)))
                     1
                     32)]
        (is (proof-backed? proofs))))))

(deftest same-interpreter-runs-a-second-instruction-table
  (let [program (tc/incrementer-program)
        start (tc/config 'i0 1 2)
        final (tc/config 'ihalt 2 2)]
    (is (succeeds?
          program
          (ast/pos-lit (ast/app-term 'halts-in-steps
                                     (tc/numeral 1)
                                     start
                                     final))
          48))))

(deftest frontend-run-exports-the-transfer-machine-final-config
  (let [records (pf/run (tc/transfer-program) [final]
                  (exists [middle0 middle1]
                    (and (step (cfg l0 (s zero) zero) middle0)
                         (step middle0 middle1)
                         (step middle1 final)
                         (halt-config final)))
                  {:fuel 96
                   :call-depth 5
                   :proof-limit 8
                   :max-raw-proof-limit 32})
        expected (tc/config 'halt-label 0 1)
        binding-terms (mapv #(-> % :bindings first second) records)]
    (is (some #{expected} binding-terms))
    (is (some #(and (= expected (-> % :bindings first second))
                    (empty? (:residuals %)))
              records))))

(deftest frontend-run-can-partially-synthesize-an-instruction
  (let [records (pf/run (tc/transfer-program) [label]
                  (inc1 label l0)
                  {:fuel 48
                   :call-depth 1
                   :proof-limit 4
                   :max-raw-proof-limit 16})
        expected (tc/app 'l1)
        binding-terms (mapv #(-> % :bindings first second) records)]
    (is (some #{expected} binding-terms))
    (is (some #(and (= expected (-> % :bindings first second))
                    (empty? (:residuals %)))
              records))))

(deftest turing-completeness-namespace-does-not-contain-a-host-step-evaluator
  (let [source (slurp "src/proflog/turing_completeness.clj")]
    (is (str/includes? source "pf/proflog"))
    (is (not (str/includes? source "query/query-succeeds")))
    (is (not (str/includes? source "answers/query-answers")))
    (is (not (re-find #"defn-?\s+(step|run|halts-in)" source)))))

(deftest long-probe-identifiers-are-stable
  (is (= ["direct-ground-three-step-trace"
          "open-predecessor-step"
          "recursive-transfer-3-steps"
          "recursive-transfer-5-steps"]
         (long-probe/probe-ids))))
