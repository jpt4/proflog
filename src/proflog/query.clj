(ns proflog.query
  "Top-level Proflog query helpers.

   Fitting's semantics are inherently three-valued at the query boundary:
   success is witnessed by a closed tableau for the negated query, failure by a
   closed tableau for the query itself, and some queries support neither proof.
   The bounded helpers in this namespace use finite fuel slices so the caller
   can recover predictably without trying to forcibly stop an in-process proof
   search."
  (:require [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.proof-profile :as proof-profile]))

(defn- next-fuel
  "Conservative iterative-deepening schedule for bounded operational probes."
  [fuel]
  (cond
    (zero? fuel) 1
    (>= fuel (quot Long/MAX_VALUE 2)) Long/MAX_VALUE
    :else (* 2 fuel)))

(defn- remaining-time-ms
  "Remaining wall-clock budget until `deadline-ms`, clamped at zero."
  [deadline-ms]
  (max 0 (- deadline-ms (System/currentTimeMillis))))

(defn- search-within-budget
  "Probe one semidecision procedure under an explicit wall-clock budget.

   Each probe uses a finite fuel slice, so every attempt terminates without any
   forced thread interruption. The helper may slightly exceed `timeout-ms` when
   the last admitted fuel slice itself takes longer than the remaining budget."
  [timeout-ms search]
  (let [deadline-ms (+ (System/currentTimeMillis) timeout-ms)]
    (loop [fuel 0]
      (let [proofs (search fuel)]
        (if (seq proofs)
          proofs
          (if (zero? (remaining-time-ms deadline-ms))
            '()
            (recur (next-fuel fuel))))))))

(defn query-succeeds
  "Return up to `n` proofs showing that `query` succeeds with `program`."
  ([program query] (query-succeeds program query 1))
  ([program query n]
   (query-succeeds program query n nil))
  ([program query n fuel]
   (let [checked-query (language/validate-query (:language program) query)]
     (proof-profile/prove-program
       program
       (normalize/negate-formula checked-query)
       n
       fuel))))

(defn query-fails
  "Return up to `n` proofs showing that `query` fails with `program`."
  ([program query] (query-fails program query 1))
  ([program query n]
   (query-fails program query n nil))
  ([program query n fuel]
   (let [checked-query (language/validate-query (:language program) query)]
     (proof-profile/prove-program program checked-query n fuel))))

(defn query-succeeds-within
  "Run `query-succeeds` with an explicit wall-clock budget."
  ([program query timeout-ms]
   (query-succeeds-within program query 1 timeout-ms))
  ([program query n timeout-ms]
   (search-within-budget timeout-ms
                         #(query-succeeds program query n %))))

(defn query-fails-within
  "Run `query-fails` with an explicit wall-clock budget."
  ([program query timeout-ms]
   (query-fails-within program query 1 timeout-ms))
  ([program query n timeout-ms]
   (search-within-budget timeout-ms
                         #(query-fails program query n %))))

(defn query-status
  "Interleave bounded success and failure semidecision probes for one query.

  Returns one of:
  - `:succeeds`
  - `:fails`
  - `:unresolved`
  - `:inconsistent` when both proofs are found within the probe budget"
  ([program query]
   (query-status program query {}))
  ([program query {:keys [timeout-ms proof-limit poll-ms]
                   :or {timeout-ms 250
                        proof-limit 1
                        poll-ms 5}}]
   (let [deadline-ms (+ (System/currentTimeMillis) timeout-ms)]
     (loop [fuel 0]
       (let [success-proofs (query-succeeds program query proof-limit fuel)]
         (cond
           (seq success-proofs)
           (if (and (pos? (remaining-time-ms deadline-ms))
                    (seq (query-fails program query proof-limit fuel)))
             :inconsistent
             :succeeds)

           (zero? (remaining-time-ms deadline-ms))
           :unresolved

           :else
           (let [failure-proofs (query-fails program query proof-limit fuel)]
             (cond
               (seq failure-proofs) :fails
               (zero? (remaining-time-ms deadline-ms)) :unresolved
               :else
               (do
                 (Thread/sleep poll-ms)
                 (recur (next-fuel fuel)))))))))))
