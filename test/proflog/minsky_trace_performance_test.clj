(ns proflog.minsky-trace-performance-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [proflog.ast :as ast]
            [proflog.proof :as proof]
            [proflog.query :as query]
            [proflog.turing-completeness :as tc]))

(defn- proof-backed?
  [proofs]
  (and (seq proofs)
       (some #(or (proof/contains-step? % 'neg-call)
                  (proof/contains-step? % 'pos-call)
                  (proof/contains-step? % 'neg-call-guarded-alt)
                  (proof/contains-step? % 'pos-call-guarded-alt))
             proofs)))

(deftest five-step-transfer-closes-through-a-guided-step-trace
  (let [states [(tc/config 'l0 2 0)
                (tc/config 'l1 1 0)
                (tc/config 'l0 1 1)
                (tc/config 'l1 0 1)
                (tc/config 'l0 0 2)
                (tc/config 'halt-label 0 2)]
        proofs (query/query-succeeds
                 (tc/transfer-program)
                 (tc/trace-formula states {:halt? true})
                 1
                 160)]
    (is (proof-backed? proofs))))

(deftest trace-helper-does-not-contain-a-host-machine-evaluator
  (let [source (slurp "src/proflog/turing_completeness.clj")]
    (is (str/includes? source "trace-formula"))
    (is (not (re-find #"case\s+label" source)))
    (is (not (re-find #"defn-?\s+(step|run|halts-in|next-config)" source)))))
