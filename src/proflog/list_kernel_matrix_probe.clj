(ns proflog.list-kernel-matrix-probe
  "Raw-kernel capability matrix for append/reverse list programs.

   This probe intentionally bypasses `query-answers`, because that public API
   includes list-family materializers that can hide what the central program
   prover can do unaided. Run one case per process and wrap long cases with
   the shell's `timeout` command."
  (:gen-class)
  (:require [clojure.pprint :as pp]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.query :as query]))

(def list-language
  (language/language
    {:constants ['a 'b 'c 'null]
     :functions {'cons 2}
     :relations {'append 3
                 'member 2
                 'reverse 2}}))

(defn list-term
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

(defn list-program
  []
  (ast/nom x xs head tail ys zs rest r1 r2 rrp
    (language/compile-program
      list-language
      [(ast/clause 'member [x xs] (member-body x xs head tail))
       (ast/clause 'append [xs ys zs] (append-body xs ys zs head tail rest))
       (ast/clause 'reverse [r1 r2] (reverse-body r1 r2 head tail rrp))])))

(def a (ast/app-term 'a))
(def b (ast/app-term 'b))
(def c (ast/app-term 'c))

(defn case-catalog
  []
  [{:id :append-forward-flat-2
    :operation :append
    :mode :forward
    :shape :flat
    :size :two-step
    :kind :ground
    :description "append([a,b], [c], [a,b,c])"}
   {:id :append-forward-flat-3
    :operation :append
    :mode :forward
    :shape :flat
    :size :longer
    :kind :ground
    :description "append([a,b,c], [a], [a,b,c,a])"}
   {:id :append-forward-nested-2
    :operation :append
    :mode :forward
    :shape :nested
    :size :two-step
    :kind :ground
    :description "append([[a],[b]], [[c]], [[a],[b],[c]])"}
   {:id :append-forward-nested-3
    :operation :append
    :mode :forward
    :shape :nested
    :size :longer
    :kind :ground
    :description "append([[a],[b],[c]], [[a]], [[a],[b],[c],[a]])"}
   {:id :append-output-flat
    :operation :append
    :mode :output-synthesis
    :shape :flat
    :size :three-total
    :kind :answer
    :description "append([a], [b,c], z)"}
   {:id :append-output-nested
    :operation :append
    :mode :output-synthesis
    :shape :nested
    :size :two-total
    :kind :answer
    :description "append([[a]], [[b]], z)"}
   {:id :append-suffix-flat
    :operation :append
    :mode :partial-suffix
    :shape :flat
    :size :three-total
    :kind :answer
    :description "append([a,b], y, [a,b,c])"}
   {:id :append-prefix-flat
    :operation :append
    :mode :partial-prefix
    :shape :flat
    :size :three-total
    :kind :answer
    :description "append(x, [c], [a,b,c])"}
   {:id :append-suffix-nested
    :operation :append
    :mode :partial-suffix
    :shape :nested
    :size :two-total
    :kind :answer
    :description "append([[a]], y, [[a],[b]])"}
   {:id :append-inverse-flat
    :operation :append
    :mode :inverse-splits
    :shape :flat
    :size :three-total
    :kind :answer
    :description "append(x, y, [a,b,c])"}
   {:id :append-inverse-flat-longer
    :operation :append
    :mode :inverse-splits
    :shape :flat
    :size :longer
    :kind :answer
    :description "append(x, y, [a,b,c,a])"}
   {:id :append-inverse-nested
    :operation :append
    :mode :inverse-splits
    :shape :nested
    :size :two-total
    :kind :answer
    :description "append(x, y, [[a],[b]])"}
   {:id :reverse-forward-flat-2
    :operation :reverse
    :mode :forward
    :shape :flat
    :size :two
    :kind :ground
    :description "reverse([a,b], [b,a])"}
   {:id :reverse-forward-flat-3
    :operation :reverse
    :mode :forward
    :shape :flat
    :size :longer
    :kind :ground
    :description "reverse([a,b,c], [c,b,a])"}
   {:id :reverse-forward-nested-2
    :operation :reverse
    :mode :forward
    :shape :nested
    :size :two
    :kind :ground
    :description "reverse([[a],[b]], [[b],[a]])"}
   {:id :reverse-forward-nested-3
    :operation :reverse
    :mode :forward
    :shape :nested
    :size :longer
    :kind :ground
    :description "reverse([[a],[b],[c]], [[c],[b],[a]])"}
   {:id :reverse-output-flat
    :operation :reverse
    :mode :output-synthesis
    :shape :flat
    :size :two
    :kind :answer
    :description "reverse([a,b], r)"}
   {:id :reverse-input-flat
    :operation :reverse
    :mode :input-synthesis
    :shape :flat
    :size :two
    :kind :answer
    :description "reverse(r, [b,a])"}
   {:id :reverse-input-flat-longer
    :operation :reverse
    :mode :input-synthesis
    :shape :flat
    :size :longer
    :kind :answer
    :description "reverse(r, [c,b,a])"}
   {:id :reverse-output-nested
    :operation :reverse
    :mode :output-synthesis
    :shape :nested
    :size :two
    :kind :answer
    :description "reverse([[a],[b]], r)"}
   {:id :reverse-output-nested-longer
    :operation :reverse
    :mode :output-synthesis
    :shape :nested
    :size :longer
    :kind :answer
    :description "reverse([[a],[b],[c]], r)"}
   {:id :reverse-output-deep-nested-longer
    :operation :reverse
    :mode :output-synthesis
    :shape :deep-nested
    :size :longer
    :kind :answer
    :description "reverse([[[a]],[[b]],[[c]]], r)"}
   {:id :reverse-partial-output-tail
    :operation :reverse
    :mode :partial-output
    :shape :flat
    :size :three
    :kind :answer
    :description "reverse([a,b,c], cons(c, r))"}
   {:id :reverse-partial-output-longer-tail
    :operation :reverse
    :mode :partial-output
    :shape :flat
    :size :longer
    :kind :answer
    :description "reverse([a,b,c,a], cons(a, r))"}])

(defn- catalog-entry
  [case-id]
  (some (fn [entry]
          (when (= case-id (:id entry))
            entry))
        (case-catalog)))

(defn- q-append
  [left right whole]
  (ast/pos-lit (ast/app-term 'append left right whole)))

(defn- q-reverse
  [input output]
  (ast/pos-lit (ast/app-term 'reverse input output)))

(defn case-config
  [case-id]
  (let [case-id (keyword case-id)
        nested-a (list-term a)
        nested-b (list-term b)
        nested-c (list-term c)
        deep-a (list-term nested-a)
        deep-b (list-term nested-b)
        deep-c (list-term nested-c)]
    (merge
      (or (catalog-entry case-id)
          (throw (ex-info "Unknown list-kernel matrix case"
                          {:case-id case-id
                           :known-case-ids (mapv :id (case-catalog))})))
      (case case-id
        :append-forward-flat-2
        {:query (q-append (list-term a b) (list-term c) (list-term a b c))
         :fuel 256}

        :append-forward-flat-3
        {:query (q-append (list-term a b c) (list-term a) (list-term a b c a))
         :fuel 256}

        :append-forward-nested-2
        {:query (q-append (list-term nested-a nested-b)
                          (list-term nested-c)
                          (list-term nested-a nested-b nested-c))
         :fuel 256}

        :append-forward-nested-3
        {:query (q-append (list-term nested-a nested-b nested-c)
                          (list-term nested-a)
                          (list-term nested-a nested-b nested-c nested-a))
         :fuel 256}

        :append-output-flat
        (ast/nom z
          {:query (q-append (list-term a) (list-term b c) (ast/var-term z))
           :answer-vars [z]
           :target-bindings #{[[z (list-term a b c)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :append-output-nested
        (ast/nom z
          {:query (q-append (list-term nested-a) (list-term nested-b) (ast/var-term z))
           :answer-vars [z]
           :target-bindings #{[[z (list-term nested-a nested-b)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :append-suffix-flat
        (ast/nom y
          {:query (q-append (list-term a b) (ast/var-term y) (list-term a b c))
           :answer-vars [y]
           :target-bindings #{[[y (list-term c)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :append-prefix-flat
        (ast/nom x
          {:query (q-append (ast/var-term x) (list-term c) (list-term a b c))
           :answer-vars [x]
           :target-bindings #{[[x (list-term a b)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :append-suffix-nested
        (ast/nom y
          {:query (q-append (list-term nested-a)
                            (ast/var-term y)
                            (list-term nested-a nested-b))
           :answer-vars [y]
           :target-bindings #{[[y (list-term nested-b)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :append-inverse-flat
        (ast/nom x y
          (let [whole (list-term a b c)]
            {:query (q-append (ast/var-term x) (ast/var-term y) whole)
             :answer-vars [x y]
             :target-bindings
             #{[[x (list-term)]
                [y whole]]
               [[x (list-term a)]
                [y (list-term b c)]]
               [[x (list-term a b)]
                [y (list-term c)]]
               [[x whole]
                [y (list-term)]]}
             :fuel 64
             :raw-limit 8
             :call-depth 2}))

        :append-inverse-flat-longer
        (ast/nom x y
          (let [whole (list-term a b c a)]
            {:query (q-append (ast/var-term x) (ast/var-term y) whole)
             :answer-vars [x y]
             :target-bindings
             #{[[x (list-term)]
                [y whole]]
               [[x (list-term a)]
                [y (list-term b c a)]]
               [[x (list-term a b)]
                [y (list-term c a)]]
               [[x (list-term a b c)]
                [y (list-term a)]]
               [[x whole]
                [y (list-term)]]}
             :fuel 96
             :raw-limit 32
             :call-depth 3}))

        :append-inverse-nested
        (ast/nom x y
          (let [whole (list-term nested-a nested-b)]
            {:query (q-append (ast/var-term x) (ast/var-term y) whole)
             :answer-vars [x y]
             :target-bindings
             #{[[x (list-term)]
                [y whole]]
               [[x (list-term nested-a)]
                [y (list-term nested-b)]]
               [[x whole]
                [y (list-term)]]}
             :fuel 64
             :raw-limit 8
             :call-depth 2}))

        :reverse-forward-flat-2
        {:query (q-reverse (list-term a b) (list-term b a))
         :fuel 256}

        :reverse-forward-flat-3
        {:query (q-reverse (list-term a b c) (list-term c b a))
         :fuel 256}

        :reverse-forward-nested-2
        {:query (q-reverse (list-term nested-a nested-b)
                           (list-term nested-b nested-a))
         :fuel 256}

        :reverse-forward-nested-3
        {:query (q-reverse (list-term nested-a nested-b nested-c)
                           (list-term nested-c nested-b nested-a))
         :fuel 256}

        :reverse-output-flat
        (ast/nom r
          {:query (q-reverse (list-term a b) (ast/var-term r))
           :answer-vars [r]
           :target-bindings #{[[r (list-term b a)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :reverse-input-flat
        (ast/nom r
          {:query (q-reverse (ast/var-term r) (list-term b a))
           :answer-vars [r]
           :target-bindings #{[[r (list-term a b)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :reverse-input-flat-longer
        (ast/nom r
          {:query (q-reverse (ast/var-term r) (list-term c b a))
           :answer-vars [r]
           :target-bindings #{[[r (list-term a b c)]]}
           :fuel 96
           :raw-limit 4
           :call-depth 2})

        :reverse-output-nested
        (ast/nom r
          {:query (q-reverse (list-term nested-a nested-b) (ast/var-term r))
           :answer-vars [r]
           :target-bindings #{[[r (list-term nested-b nested-a)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :reverse-output-nested-longer
        (ast/nom r
          {:query (q-reverse (list-term nested-a nested-b nested-c)
                             (ast/var-term r))
           :answer-vars [r]
           :target-bindings #{[[r (list-term nested-c nested-b nested-a)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :reverse-output-deep-nested-longer
        (ast/nom r
          {:query (q-reverse (list-term deep-a deep-b deep-c)
                             (ast/var-term r))
           :answer-vars [r]
           :target-bindings #{[[r (list-term deep-c deep-b deep-a)]]}
           :fuel 96
           :raw-limit 4
           :call-depth 2})

        :reverse-partial-output-tail
        (ast/nom r
          {:query (q-reverse (list-term a b c)
                             (ast/app-term 'cons c (ast/var-term r)))
           :answer-vars [r]
           :target-bindings #{[[r (list-term b a)]]}
           :fuel 64
           :raw-limit 4
           :call-depth 2})

        :reverse-partial-output-longer-tail
        (ast/nom r
          {:query (q-reverse (list-term a b c a)
                             (ast/app-term 'cons a (ast/var-term r)))
           :answer-vars [r]
           :target-bindings #{[[r (list-term c b a)]]}
           :fuel 96
           :raw-limit 4
           :call-depth 2})))))

(defn- raw-answer-records
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
        unique-records ((deref #'answers/merge-answer-records) exported-records)
        prioritized-records ((deref #'answers/prioritize-answer-records)
                              unique-records)]
    {:raw-count (count raw-results)
     :search-exhausted? (< (count raw-results) raw-limit)
     :records prioritized-records}))

(defn- closed-record?
  [record]
  (empty? (:residuals record)))

(defn- record-bindings-for
  [record answer-vars]
  (mapv (fn [answer-var]
          [answer-var (answers/binding-term record answer-var)])
        answer-vars))

(defn- canonical-bindings
  [bindings]
  (mapv (fn [[answer-var term]]
          [(str answer-var) term])
        bindings))

(defn- constructor-layer-records
  [program query answer-vars fuel _raw-limit target-bindings]
  (constructor-recursive/query-records
    program
    query
    answer-vars
    {:fuel (max 96 fuel)
     :limit (max (count target-bindings) 1)}))

(defn run-constructor-layer-case
  "Run one matrix case through the generic constructor-recursive prototype.

   This intentionally reports a separate layer from the ordinary kernel and raw
   answer overlay. It is a diagnostic surface for ADR-31's proof-producing
   constructor-recursive layer experiment, not public list materialization."
  [case-id]
  (let [program (list-program)
        {:keys [kind query fuel answer-vars target-bindings raw-limit]
         :as config}
        (case-config case-id)
        started (System/nanoTime)]
    (case kind
      :ground
      (let [target-found? (constructor-recursive/query-succeeds?
                            program
                            query
                            {:fuel fuel})
            elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
        (assoc (select-keys config [:id :operation :mode :shape :size :kind :description])
               :layer :constructor-recursive
               :fuel fuel
               :elapsed-ms elapsed-ms
               :target-found? target-found?))

      :answer
      (let [records (constructor-layer-records
                      program
                      query
                      answer-vars
                      fuel
                      raw-limit
                      target-bindings)
            closed-bindings (->> records
                                 (filter closed-record?)
                                 (map #(record-bindings-for % answer-vars))
                                 set)
            found-targets (set (filter closed-bindings target-bindings))
            elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
        (assoc (select-keys config [:id :operation :mode :shape :size :kind :description])
               :layer :constructor-recursive
               :fuel (max 96 fuel)
               :limit (max (count target-bindings) 1)
               :elapsed-ms elapsed-ms
               :exported-count (count records)
               :closed-count (count closed-bindings)
               :target-count (count target-bindings)
               :found-target-count (count found-targets)
               :target-found? (= target-bindings found-targets)
               :closed-bindings (mapv canonical-bindings closed-bindings)
               :target-bindings (mapv canonical-bindings target-bindings))))))

(defn run-case
  [case-id]
  (let [program (list-program)
        {:keys [kind query fuel answer-vars target-bindings raw-limit call-depth]
         :as config}
        (case-config case-id)
        started (System/nanoTime)]
    (case kind
      :ground
      (let [proofs (query/query-succeeds program query 1 fuel)
            elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
        (assoc (select-keys config [:id :operation :mode :shape :size :kind :description])
               :fuel fuel
               :elapsed-ms elapsed-ms
               :target-found? (boolean (seq proofs))
               :proof-count (count proofs)))

      :answer
      (let [slice (raw-answer-records
                    program query answer-vars fuel raw-limit call-depth)
            closed-bindings (->> (:records slice)
                                 (filter closed-record?)
                                 (map #(record-bindings-for % answer-vars))
                                 set)
            found-targets (set (filter closed-bindings target-bindings))
            elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
        (assoc (select-keys config [:id :operation :mode :shape :size :kind :description])
               :fuel fuel
               :raw-limit raw-limit
               :call-depth call-depth
               :elapsed-ms elapsed-ms
               :raw-count (:raw-count slice)
               :search-exhausted? (:search-exhausted? slice)
               :exported-count (count (:records slice))
               :closed-count (count closed-bindings)
               :target-count (count target-bindings)
               :found-target-count (count found-targets)
               :target-found? (= target-bindings found-targets)
               :closed-bindings (mapv canonical-bindings closed-bindings)
               :target-bindings (mapv canonical-bindings target-bindings))))))

(defn -main
  [& [case-id layer]]
  (cond
    (and case-id (= "constructor-recursive" layer))
    (pp/pprint (run-constructor-layer-case (keyword case-id)))

    case-id
    (pp/pprint (run-case (keyword case-id)))

    :else
    (pp/pprint (case-catalog)))
  (shutdown-agents))
