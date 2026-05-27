(ns proflog.fd-fuel-synthesis-probe
  "Focused ADR-37 probes for finite-domain fuel in relational modes."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [!= == fresh run run*]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]))

(def closing-conjunction
  (ast/and-form
    (ast/pos-lit (ast/app-term 'p))
    (ast/neg-lit (ast/app-term 'p))))

(defn step-pairs
  [n]
  (run n [q]
    (fresh [fuel next-fuel]
      (support/step-fuelo fuel next-fuel)
      (== [fuel next-fuel] q))))

(defn finite-step-pairs
  [n]
  (run n [q]
    (fresh [fuel next-fuel]
      (!= fuel nil)
      (!= next-fuel nil)
      (support/step-fuelo fuel next-fuel)
      (== [fuel next-fuel] q))))

(defn step-chain-to-zero
  [n]
  (run n [fuel]
    (fresh [next-fuel]
      (support/step-fuelo fuel next-fuel)
      (support/step-fuelo next-fuel 0))))

(defn concrete-kernel-proofs
  [fuel n]
  (run n [proof]
    (kernel/proveo closing-conjunction '() '() '() fuel proof)))

(defn open-kernel-fuel-proofs
  [n]
  (run n [q]
    (fresh [fuel proof]
      (kernel/proveo closing-conjunction '() '() '() fuel proof)
      (== [fuel proof] q))))

(defn run-probe
  []
  {:step-pairs (step-pairs 6)
   :finite-step-pairs (finite-step-pairs 6)
   :step-chain-to-zero (step-chain-to-zero 6)
   :concrete-kernel
   {:fuel-0 (concrete-kernel-proofs 0 1)
    :fuel-1 (concrete-kernel-proofs 1 1)
    :fuel-2 (concrete-kernel-proofs 2 1)
    :fuel-unbounded (concrete-kernel-proofs nil 1)}
   :open-kernel-fuel-proofs (open-kernel-fuel-proofs 6)})

(defn -main
  [& _]
  (prn (run-probe)))
