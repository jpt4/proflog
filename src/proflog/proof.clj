(ns proflog.proof
  "Helpers for inspecting greenfield proof terms.

   Proof terms are plain tagged lists so they stay easy to inspect, compare,
   and partially synthesize in relational use. This namespace intentionally
   stays small: richer proof manipulation can grow later without entangling the
   kernel with presentation logic."
  (:require [clojure.walk :as walk]))

(defn contains-step?
  "Return true when `proof` contains the given step tag anywhere in the tree."
  [proof step]
  (boolean
    (some #{step}
          (tree-seq coll? seq proof))))

(defn collect-steps
  "Return the ordered sequence of proof tags contained in `proof`.

   This is primarily useful for debugging and test diagnostics."
  [proof]
  (filter symbol?
          (tree-seq coll? seq proof)))
