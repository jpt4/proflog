(ns proflog.robinson-q-probe
  "Timing probe for Robinson Q proof paths.

   This is a reproducible documentation aid rather than a regression gate. The
   committed tests assert correctness; this namespace records wall-clock timing
   for the shared ordinary-vs-profiled examples, including the ADR-0050
   kernel-interleaved Q theory profile and ADR-0052 unified Q3 rule."
  (:require [proflog.query :as query]
            [proflog.robinson-q :as rq]))

(def common-theorems
  "The formulas used to compare Q-as-antecedent and profiled conversion.

   Most rows ask the `:robinson-q` profile to prove the theorem directly.
   ADR-0054's prime rows are marked `:q-antecedent` because the corrected
   theorem-only profile search remains a documented runtime boundary; those
   rows instead confirm that the profiled language still preserves the generic
   Q-as-antecedent equality-fragment proof path.
   "
  [[:q3 rq/q3 32 32]
   [:q7 rq/q7 32 16]
   [:add-one-zero (rq/eq (rq/add (rq/numeral 1) rq/zero)
                         (rq/numeral 1))
    48 16]
   [:mul-two-zero (rq/eq (rq/mul (rq/numeral 2) rq/zero)
                         rq/zero)
    48 16]
   [:add-one-two (rq/eq (rq/add (rq/numeral 1) (rq/numeral 2))
                        (rq/numeral 3))
    64 16]
   [:mul-two-two (rq/eq (rq/mul (rq/numeral 2) (rq/numeral 2))
                        (rq/numeral 4))
    96 16]
   [:q3-add-one-predecessor rq/q3-add-one-predecessor 64 48]
   [:q3-contextual-successor-predecessor
    rq/q3-contextual-successor-predecessor
    16
    16]
   [:add-right-two-successors
    rq/add-right-two-successors
    64
    16]
   [:mul-right-two-normal-form
    rq/mul-right-two-normal-form
    96
    16]
   [:q3-add-two-successor
    rq/q3-add-two-successor
    64
    32]
   [:prime-other-than-two-has-no-two-factor
    rq/prime-other-than-two-has-no-two-factor
    128
    128
    :q-antecedent]
   [:prime-other-than-two-is-not-left-even
    rq/prime-other-than-two-is-not-left-even
    128
    128
    :q-antecedent]])

(defn- elapsed-ms
  "Return `[value elapsed-ms]` for one thunk."
  [f]
  (let [start (System/nanoTime)
        value (f)]
    [value (/ (double (- (System/nanoTime) start)) 1000000.0)]))

(defn- ordinary-row
  [[label theorem ordinary-fuel _profile-fuel]]
  (let [[proofs ms] (elapsed-ms
                      #(query/query-succeeds
                         rq/ordinary-program
                         (rq/q-implies theorem)
                         1
                         ordinary-fuel))]
    {:path :ordinary-q-antecedent
     :label label
     :fuel ordinary-fuel
     :proofs (count proofs)
     :elapsed-ms ms}))

(defn- profile-row
  [[label theorem _ordinary-fuel profile-fuel profile-mode]]
  (let [profile-formula (if (= :q-antecedent profile-mode)
                          (rq/q-implies theorem)
                          theorem)
        [proofs ms] (elapsed-ms
                      #(query/query-succeeds
                         rq/profile-program
                         profile-formula
                         1
                         profile-fuel))]
    {:path :robinson-q-profile
     :label label
     :fuel profile-fuel
     :proofs (count proofs)
     :elapsed-ms ms}))

(defn run-probe
  "Return timing rows for the Robinson Q comparison."
  []
  (concat
    (map ordinary-row common-theorems)
    (map profile-row common-theorems)))

(defn -main
  [& _args]
  (doseq [row (run-probe)]
    (prn row)))
