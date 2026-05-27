(ns proflog.relational-fuel-adapter-probe
  "Opt-in probe for host integer fuel backed by relational bit-list arithmetic.

   Production Proflog callers currently pass `nil` for unbounded search or a
   host integer for bounded search. This adapter keeps that entry shape for
   ground boundary values, but finite recursive steps use the ADR-36 bit-list
   arithmetic relation internally.

   This namespace is deliberately not wired into production. It exists to test
   whether finite-domain arithmetic in `kernel-support/step-fuelo` can be
   replaced without hardcoded finite-domain bounds."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde fail fresh run]]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.relational-arithmetic :as arith]))

(defn host-finite-fuel?
  "True when `fuel` is a public finite fuel value at the Proflog boundary."
  [fuel]
  (and (integer? fuel) (not (neg? fuel))))

(defn boundary-fuelo
  "Relate a public boundary fuel value to the internal fuel representation.

   Ground public finite fuel is converted to miniKanren's little-endian bit-list
   numerals. `nil` remains Proflog's unbounded-search sentinel. Logic variables
   and already-internal bit-list values are left relational.

   This is intentionally a boundary adapter, not a full relational integer
   encoder. If callers want to synthesize finite fuel, the finite answer is an
   internal bit-list numeral rather than a host integer."
  [fuel internal-fuel]
  (cond
    (nil? fuel)
    (== internal-fuel nil)

    (host-finite-fuel? fuel)
    (== internal-fuel (arith/build-num fuel))

    (integer? fuel)
    fail

    :else
    (== internal-fuel fuel)))

(defn step-fuelo
  "Consume one unit of finite fuel without finite-domain arithmetic.

   Known host integer inputs are accepted at the boundary. Finite recursive
   outputs are bit-list numerals, so a kernel run that starts with host integer
   fuel continues internally with bit-list fuel after the first step."
  [fuel next-fuel]
  (fresh [internal-fuel internal-next-fuel]
    (boundary-fuelo fuel internal-fuel)
    (boundary-fuelo next-fuel internal-next-fuel)
    (conde
      [(== internal-fuel nil)
       (== internal-next-fuel nil)]
      [(arith/poso internal-fuel)
       (arith/pluso internal-next-fuel (arith/build-num 1) internal-fuel)])))

(defn prove-with-adapted-fuel
  "Return up to `n` proofs using host `nil`/integer fuel at the entry boundary."
  [fml n fuel]
  (with-redefs [support/step-fuelo step-fuelo]
    (run n [proof]
      (kernel/proveo fml '() '() '() fuel proof))))
