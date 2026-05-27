(ns proflog.hard-family-overlay
  "Named non-default overlay for ADR-0014 hard-family evaluation.

   This namespace intentionally does not modify the pure kernel. It layers a
   host-level accelerator beside the ordinary query API for the restricted shape
   handled by `proflog.equality-fast-path`, so the project can measure practical
   recovery without weakening the relational purity of `proflog.kernel`."
  (:require [proflog.ast :as ast]
            [proflog.equality-fast-path :as equality-fast-path]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.query :as query]))

(defn- proof-status
  [success-proof failure-proof]
  (cond
    (and success-proof failure-proof) :inconsistent
    success-proof :succeeds
    failure-proof :fails
    :else :unresolved))

(defn- direct-fast-status
  [checked-query]
  (proof-status
    (equality-fast-path/maybe-close-proof
      (normalize/negate-formula checked-query)
      '())
    (equality-fast-path/maybe-close-proof checked-query '())))

(defn- literal-clause-fast-status
  [program checked-query]
  (let [tag (ast/tag-of checked-query)]
    (when (#{'pos 'neg} tag)
      (let [atom (second checked-query)
            relation (second atom)
            args (nnext atom)
            clause (get-in program [:clauses relation])]
        (when clause
          (let [env (map (fn [param arg]
                           [param arg])
                         (:params clause)
                         args)
                success-formula (if (= 'pos tag)
                                  (:negated-body clause)
                                  (:body clause))
                failure-formula (if (= 'pos tag)
                                  (:body clause)
                                  (:negated-body clause))]
            (proof-status
              (equality-fast-path/maybe-close-proof success-formula env)
              (equality-fast-path/maybe-close-proof failure-formula env))))))))

(defn query-status
  "Resolve one query through this named overlay, then the pure query surface.

   Hard-family pure probes can overshoot their nominal timeout while completing
   the last admitted fuel slice. Since this is an explicit non-default overlay,
   try the restricted fast path first when it recognizes the formula; otherwise
   fall back to the ordinary pure query surface."
  ([program query-formula]
   (query-status program query-formula {}))
  ([program query-formula {:as opts}]
   (let [checked-query (language/validate-query (:language program) query-formula)
         direct-status (direct-fast-status checked-query)]
     (if (not= :unresolved direct-status)
       direct-status
       (let [clause-status (literal-clause-fast-status program checked-query)]
         (if (and clause-status (not= :unresolved clause-status))
           clause-status
           (query/query-status program checked-query opts)))))))
