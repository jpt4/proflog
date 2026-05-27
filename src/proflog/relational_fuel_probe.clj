(ns proflog.relational-fuel-probe
  "Experimental fuel stepping backed by relational bit-list arithmetic.

   This is not wired into production query APIs because existing callers pass
   host integers as finite fuel. It demonstrates the relation shape that would
   replace finite-domain arithmetic if the kernel switched bounded fuel to
   miniKanren binary numerals."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde]]
            [proflog.relational-arithmetic :as arith]))

(defn step-fuelo
  "Consume one unit of bit-list fuel.

   `nil` is retained as Proflog's unbounded-fuel sentinel. Finite fuel values are
   little-endian bit-list numerals, where `()` is zero and `(1)` is one."
  [fuel next-fuel]
  (conde
    [(== fuel nil)
     (== next-fuel nil)]
    [(arith/poso fuel)
     (arith/pluso next-fuel (arith/build-num 1) fuel)]))
