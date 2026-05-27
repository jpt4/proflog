(ns proflog.legacy-stream-probe
   "Exploratory raw-kernel probes for legacy-style open list queries.

    This runner intentionally bypasses the public `query-answers` fast paths and
    drives the raw kernel answer export layer directly. It is meant for ADR-0014
    answer-stream classification work: determine whether a desired closed answer
    is present in the raw stream at all, how late it appears, and how that
   depends on `call-depth` when `fuel` is unbounded."
   (:require [clojure.pprint :as pp]
             [proflog.answers :as answers]
             [proflog.ast :as ast]
             [proflog.language :as language]
             [proflog.normalize :as normalize]))

(def list-language
  (language/language
    {:constants ['a 'b 'c 'null]
     :functions {'cons 2}
     :relations {'append 3
                 'member 2
                 'reverse 2}}))

(defn- list-term
  [& xs]
  (reduce (fn [tail x]
            (ast/app-term 'cons x tail))
          (ast/app-term 'null)
          (reverse xs)))

(defn- member-body
  [x xs head tail]
  (ast/exists-form
    head
    (ast/exists-form
      tail
      (ast/and-form
        (ast/eq-lit
          (ast/var-term xs)
          (ast/app-term 'cons
                        (ast/var-term head)
                        (ast/var-term tail)))
        (ast/or-form
          (ast/eq-lit (ast/var-term x) (ast/var-term head))
          (ast/pos-lit
            (ast/app-term 'member
                          (ast/var-term x)
                          (ast/var-term tail))))))))

(defn- append-body
  [xs ys zs head tail rest]
  (ast/or-form
    (ast/and-form
      (ast/eq-lit (ast/var-term xs) (ast/app-term 'null))
      (ast/eq-lit (ast/var-term zs) (ast/var-term ys)))
    (ast/exists-form
      head
      (ast/exists-form
        tail
        (ast/exists-form
          rest
          (ast/and-form
            (ast/eq-lit
              (ast/var-term xs)
              (ast/app-term 'cons
                            (ast/var-term head)
                            (ast/var-term tail)))
            (ast/and-form
              (ast/eq-lit
                (ast/var-term zs)
                (ast/app-term 'cons
                              (ast/var-term head)
                              (ast/var-term rest)))
              (ast/pos-lit
                (ast/app-term 'append
                              (ast/var-term tail)
                              (ast/var-term ys)
                              (ast/var-term rest))))))))))

(defn- reverse-body
  [r1 r2 head tail rrp]
  (ast/or-form
    (ast/and-form
      (ast/eq-lit (ast/var-term r1) (ast/app-term 'null))
      (ast/eq-lit (ast/var-term r2) (ast/app-term 'null)))
    (ast/exists-form
      head
      (ast/exists-form
        tail
        (ast/exists-form
          rrp
          (ast/and-form
            (ast/eq-lit
              (ast/var-term r1)
              (ast/app-term 'cons
                            (ast/var-term head)
                            (ast/var-term tail)))
            (ast/and-form
              (ast/pos-lit
                (ast/app-term 'reverse
                              (ast/var-term tail)
                              (ast/var-term rrp)))
              (ast/pos-lit
                (ast/app-term 'append
                              (ast/var-term rrp)
                              (ast/app-term 'cons
                                            (ast/var-term head)
                                            (ast/app-term 'null))
                              (ast/var-term r2))))))))))

(defn- list-program
  []
  (ast/nom x xs head tail ys zs rest r1 r2 rrp
    (language/compile-program
      list-language
      [(ast/clause 'member [x xs] (member-body x xs head tail))
       (ast/clause 'append [xs ys zs] (append-body xs ys zs head tail rest))
       (ast/clause 'reverse [r1 r2] (reverse-body r1 r2 head tail rrp))])))

 (defn- raw-answer-records
   "Return exported and merged raw answer records for one exact kernel slice.

    This intentionally uses the private raw-answer helpers because the public
    `query-answers` API now includes list-family materialization that would mask
    the kernel behavior this probe is trying to measure."
   [program query answer-vars fuel raw-limit call-depth]
   (let [checked-query (language/validate-query (:language program) query)
         negated-query (normalize/negate-formula checked-query)
         raw-results ((deref #'answers/program-raw-answer-states)
                       program
                       negated-query
                       answer-vars
                       fuel
                       raw-limit
                       call-depth)
         exported-records (->> raw-results
                               (map #((deref #'answers/export-program-answer-record)
                                       program
                                       answer-vars
                                       %))
                               (keep identity)
                               vec)
         unique-records ((deref #'answers/merge-answer-records) exported-records)]
     {:raw-count (count raw-results)
      :search-exhausted? (< (count raw-results) raw-limit)
      :exported-count (count exported-records)
      :unique-count (count unique-records)
      :records unique-records}))

 (defn- closed-record?
   [record]
   (empty? (:residuals record)))

 (defn- record-bindings-for
   [record answer-vars]
   (mapv (fn [answer-var]
           [answer-var (answers/binding-term record answer-var)])
         answer-vars))

(defn- canonical-binding-summary
  [bindings]
  (mapv (fn [[answer-var term]]
          [(str answer-var) term])
        bindings))

 (defn- summarize-closed-records
   [records answer-vars]
   (->> records
        (filter closed-record?)
        (map #(record-bindings-for % answer-vars))
        distinct
        (map canonical-binding-summary)
        vec))

 (defn- probe-result
   [probe-name call-depth raw-limit elapsed-ms answer-vars target-bindings-set slice]
   (let [matching-targets (->> (:records slice)
                               (filter closed-record?)
                               (map #(record-bindings-for % answer-vars))
                               (filter target-bindings-set)
                               set)]
     {:probe probe-name
      :call-depth call-depth
      :fuel nil
      :raw-limit raw-limit
      :elapsed-ms elapsed-ms
      :raw-count (:raw-count slice)
      :search-exhausted? (:search-exhausted? slice)
      :exported-count (:exported-count slice)
      :unique-count (:unique-count slice)
      :closed-records (summarize-closed-records (:records slice) answer-vars)
      :target-bindings (mapv canonical-binding-summary target-bindings-set)
      :matching-targets (mapv canonical-binding-summary matching-targets)
      :target-found? (= target-bindings-set matching-targets)}))

 (defn- reverse-open-config
   []
   (ast/nom r
            (let [input (list-term (ast/app-term 'a)
                                   (ast/app-term 'b))
                  expected (list-term (ast/app-term 'b)
                                      (ast/app-term 'a))]
              {:query (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
               :answer-vars [r]
               :target-bindings #{[[r expected]]}})))

 (defn- append-forward-config
   []
   (ast/nom z
            (let [left (list-term (ast/app-term 'a))
                  right (list-term (ast/app-term 'b)
                                   (ast/app-term 'c))
                  expected (list-term (ast/app-term 'a)
                                      (ast/app-term 'b)
                                      (ast/app-term 'c))]
              {:query (ast/pos-lit (ast/app-term 'append
                                                 left
                                                 right
                                                 (ast/var-term z)))
               :answer-vars [z]
               :target-bindings #{[[z expected]]}})))

 (defn- append-inverse-config
   []
   (ast/nom x y
            (let [whole (list-term (ast/app-term 'a)
                                   (ast/app-term 'b)
                                   (ast/app-term 'c))]
              {:query (ast/pos-lit (ast/app-term 'append
                                                 (ast/var-term x)
                                                 (ast/var-term y)
                                                 whole))
               :answer-vars [x y]
               :target-bindings
               #{[[x (list-term)]
                  [y whole]]
                 [[x (list-term (ast/app-term 'a))]
                  [y (list-term (ast/app-term 'b)
                                (ast/app-term 'c))]]
                 [[x (list-term (ast/app-term 'a)
                                (ast/app-term 'b))]
                  [y (list-term (ast/app-term 'c))]]
                 [[x whole]
                  [y (list-term)]]}})))

 (defn- probe-config
   [probe-name]
   (case probe-name
     "reverse-open" (reverse-open-config)
     "append-forward" (append-forward-config)
     "append-inverse" (append-inverse-config)
     (throw (ex-info "Unknown probe name"
                     {:probe-name probe-name
                      :supported ["reverse-open"
                                  "append-forward"
                                  "append-inverse"]}))))

 (defn- raw-limits-up-to
   [max-raw-limit]
   (loop [limit 1
          acc []]
     (if (> limit max-raw-limit)
       acc
       (recur (* 2 limit) (conj acc limit)))))

(defn- run-probe!
  [probe-name call-depth max-raw-limit]
  (let [program (list-program)
        {:keys [query answer-vars target-bindings]} (probe-config probe-name)]
    (loop [[raw-limit & remaining] (raw-limits-up-to max-raw-limit)
           last-result nil]
      (if (nil? raw-limit)
        last-result
        (let [started (System/nanoTime)
              slice (raw-answer-records
                      program
                      query
                      answer-vars
                      nil
                      raw-limit
                      call-depth)
              elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)
              result (probe-result
                       probe-name
                       call-depth
                       raw-limit
                       elapsed-ms
                       answer-vars
                       target-bindings
                       slice)]
          (pp/pprint result)
          (flush)
          (if (:target-found? result)
            (do
              (println :target-found)
              (flush)
              result)
            (recur remaining result)))))))

(defn -main
   "Run one raw-kernel probe and print progressive EDN-ish checkpoints.

    Usage:
      lein run -m proflog.legacy-stream-probe reverse-open 3 64

    This means:
    - use the `reverse-open` probe shape,
    - run with `call-depth = 3`,
    - search raw limits `1, 2, 4, ..., 64`,
    - and use `fuel = nil` throughout."
  [& [probe-name call-depth-text max-raw-limit-text]]
  (let [probe-name (or probe-name "reverse-open")
         call-depth-text (or call-depth-text "1")
         call-depth (when-not (= "nil" call-depth-text)
                      (Long/parseLong call-depth-text))
         max-raw-limit (Long/parseLong (or max-raw-limit-text "64"))]
     (run-probe! probe-name call-depth max-raw-limit)
     (shutdown-agents)))
