(ns proflog.relational-fuel-performance-probe
  "ADR-37 timing probe for FD fuel versus relational-arithmetic fuel.

   This is a process-local comparison harness. It does not alter production
   `step-fuelo`; integrated cases temporarily rebind that var while one probe
   body runs."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run]]
            [clojure.pprint :as pp]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.language :as language]
            [proflog.pretty :as pretty]
            [proflog.query :as query]
            [proflog.relational-fuel-adapter-probe :as adapter]))

(def production-step-fuelo
  (var-get #'support/step-fuelo))

(def replacement-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'step 2
                 'jump 2}}))

(defn- numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn- replacement-program
  []
  (ast/nom x y z
    (language/compile-program
      replacement-language
      [(ast/clause 'step [x y]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's (ast/var-term y)))
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's
                                               (ast/app-term 's
                                                             (ast/var-term y))))))
       (ast/clause 'jump [x y]
                   (ast/exists-form z
                                    (ast/and-form
                                      (ast/pos-lit (ast/app-term 'step
                                                                 (ast/var-term x)
                                                                 (ast/var-term z)))
                                      (ast/pos-lit (ast/app-term 'step
                                                                 (ast/var-term z)
                                                                 (ast/var-term y))))))])))

(def ^:private closing-conjunction
  (ast/and-form
    (ast/pos-lit (ast/app-term 'p))
    (ast/neg-lit (ast/app-term 'p))))

(defn- gamma-formula
  []
  (ast/nom x
    (ast/and-form
      (ast/forall-form x
                       (ast/pos-lit
                         (ast/app-term 'value (ast/var-term x))))
      (ast/neg-lit (ast/app-term 'value (ast/app-term 'zero))))))

(defn- with-step-fuelo
  [step-fuelo f]
  (with-redefs [support/step-fuelo step-fuelo]
    (f)))

(defn- ground-decimals
  [records binding-nom]
  (->> records
       (keep #(pretty/peano->int (answers/binding-term % binding-nom)))
       vec))

(defn- answer-shape
  [answer-vars records]
  (mapv (fn [record]
          {:bindings (mapv (fn [answer-var]
                             [answer-var (answers/binding-term record answer-var)])
                           answer-vars)
           :residuals (:residuals record)})
        records))

(defn- direct-step-ground
  [step-fuelo]
  (doall
    (run 1 [next-fuel]
      (step-fuelo 8 next-fuel))))

(defn- direct-step-reverse
  [step-fuelo]
  (doall
    (run 1 [fuel]
      (step-fuelo fuel 7))))

(defn- direct-step-chain
  [step-fuelo]
  (doall
    (run 1 [q]
      (fresh [fuel-2 fuel-1 fuel-0]
        (step-fuelo 3 fuel-2)
        (step-fuelo fuel-2 fuel-1)
        (step-fuelo fuel-1 fuel-0)
        (== [fuel-2 fuel-1 fuel-0] q)))))

(defn- kernel-closure
  [step-fuelo]
  (with-step-fuelo step-fuelo
    #(doall
       (run 1 [proof]
         (kernel/proveo closing-conjunction '() '() '() 8 proof)))))

(defn- kernel-gamma
  [step-fuelo]
  (with-step-fuelo step-fuelo
    #(doall
       (run 1 [proof]
         (kernel/proveo (gamma-formula) '() '() '() 8 proof)))))

(defn- query-succeeds-ground
  [step-fuelo]
  (let [program (replacement-program)
        q (ast/pos-lit (ast/app-term 'step (numeral 2) (numeral 1)))]
    (with-step-fuelo step-fuelo
      #(doall (query/query-succeeds program q 1 8)))))

(defn- query-fails-ground
  [step-fuelo]
  (let [program (replacement-program)
        q (ast/pos-lit (ast/app-term 'step (numeral 0) (numeral 1)))]
    (with-step-fuelo step-fuelo
      #(doall (query/query-fails program q 1 8)))))

(defn- answer-partial-step
  [step-fuelo]
  (ast/nom x
    (let [q (ast/pos-lit
              (ast/app-term 'step (ast/var-term x) (numeral 1)))
          opts {:proof-limit 3
                :max-raw-proof-limit 24
                :fuel 8
                :call-depth 1}]
      (with-step-fuelo step-fuelo
        #(let [records (doall
                         (answers/query-answers
                           (replacement-program)
                           q
                           [x]
                           opts))]
           {:decimals (ground-decimals records x)
            :shape (answer-shape [x] records)})))))

(defn- answer-composed-jump
  [step-fuelo]
  (ast/nom x
    (let [q (ast/pos-lit
              (ast/app-term 'jump (ast/var-term x) (numeral 0)))
          opts {:proof-limit 4
                :max-raw-proof-limit 32
                :fuel 12
                :call-depth 2}]
      (with-step-fuelo step-fuelo
        #(let [records (doall
                         (answers/query-answers
                           (replacement-program)
                           q
                           [x]
                           opts))]
           {:decimals (ground-decimals records x)
            :shape (answer-shape [x] records)})))))

(def ^:private default-cases
  [{:id :direct-step-ground
    :description "direct `step-fuelo` from public finite fuel 8"
    :iterations 250
    :run direct-step-ground}
   {:id :direct-step-reverse
    :description "direct `step-fuelo` with known next fuel 7"
    :iterations 250
    :run direct-step-reverse}
   {:id :direct-step-chain
    :description "three finite fuel steps after public entry fuel 3"
    :iterations 120
    :run direct-step-chain}
   {:id :kernel-closure
    :description "direct kernel closure at fuel 8"
    :iterations 40
    :run kernel-closure}
   {:id :kernel-gamma
    :description "direct kernel gamma-instantiation closure at fuel 8"
    :iterations 40
    :run kernel-gamma}
   {:id :query-succeeds-ground
    :description "bounded `query-succeeds` for ground `step(2, 1)`"
    :iterations 20
    :run query-succeeds-ground}
   {:id :query-fails-ground
    :description "bounded `query-fails` for ground `step(0, 1)`"
    :iterations 20
    :run query-fails-ground}
   {:id :answer-partial-step
    :description "answer-mode partial synthesis for `step(x, 1)`"
    :iterations 5
    :run answer-partial-step}
   {:id :answer-composed-jump
    :description "answer-mode two-call synthesis for `jump(x, 0)`"
    :iterations 3
    :run answer-composed-jump}])

(def ^:private implementations
  {:fd {:label "production finite-domain step-fuelo"
        :step-fuelo production-step-fuelo}
   :relational {:label "relational arithmetic adapter step-fuelo"
                :step-fuelo adapter/step-fuelo}})

(defn- nanos->millis
  [nanos]
  (/ (double nanos) 1000000.0))

(defn- round3
  [x]
  (/ (Math/round (* 1000.0 (double x))) 1000.0))

(defn- truncate
  [text max-length]
  (if (<= (count text) max-length)
    text
    (str (subs text 0 max-length) "...")))

(defn- result-summary
  [result]
  (cond
    (sequential? result)
    {:kind :sequential
     :count (count result)
     :sample (some-> (first result) pr-str (truncate 240))}

    (map? result)
    {:kind :map
     :keys (sort (keys result))
     :sample (truncate (pr-str result) 240)}

    :else
    {:kind :value
     :sample (truncate (pr-str result) 240)}))

(defn- timed-batch
  [run-case step-fuelo iterations]
  (let [sink (volatile! 0)
        started (System/nanoTime)]
    (dotimes [_ iterations]
      (vswap! sink unchecked-add-int (hash (run-case step-fuelo))))
    {:elapsed-ms (nanos->millis (- (System/nanoTime) started))
     :sink @sink}))

(defn- summarize-samples
  [samples iterations]
  (let [elapsed (sort (map :elapsed-ms samples))
        sample-count (count elapsed)
        total (reduce + elapsed)
        mean (/ total sample-count)
        median (nth elapsed (quot sample-count 2))]
    {:samples sample-count
     :iterations-per-sample iterations
     :min-ms (round3 (first elapsed))
     :median-ms (round3 median)
     :mean-ms (round3 mean)
     :max-ms (round3 (last elapsed))
     :mean-ms-per-iteration (round3 (/ mean iterations))}))

(defn- run-warmup
  [{:keys [run iterations]} warmup-samples]
  (doseq [_ (range warmup-samples)
          {:keys [step-fuelo]} (vals implementations)]
    (timed-batch run step-fuelo iterations)))

(defn- sample-case
  [{:keys [run iterations]} sample-count]
  (reduce
    (fn [samples sample-idx]
      (let [order (if (even? sample-idx)
                    [:fd :relational]
                    [:relational :fd])]
        (reduce
          (fn [samples impl-id]
            (let [{:keys [step-fuelo]} (get implementations impl-id)]
              (update samples impl-id conj
                      (timed-batch run step-fuelo iterations))))
          samples
          order)))
    {:fd []
     :relational []}
    (range sample-count)))

(defn- ratio
  [relational fd metric]
  (let [denominator (get fd metric)]
    (when (and denominator (pos? denominator))
      (round3 (/ (get relational metric) denominator)))))

(defn measure-case
  ([case]
   (measure-case case {:warmup-samples 2
                       :sample-count 7}))
  ([{:keys [id description iterations run] :as case}
    {:keys [warmup-samples sample-count]}]
   (run-warmup case warmup-samples)
   (let [validation (into {}
                          (map (fn [[impl-id {:keys [step-fuelo]}]]
                                 [impl-id (result-summary (run step-fuelo))]))
                          implementations)
         samples (sample-case case sample-count)
         fd-summary (summarize-samples (:fd samples) iterations)
         relational-summary (summarize-samples (:relational samples) iterations)]
     {:case-id id
      :description description
      :validation validation
      :fd fd-summary
      :relational relational-summary
      :relational-vs-fd
      {:mean-ratio (ratio relational-summary fd-summary :mean-ms)
       :median-ratio (ratio relational-summary fd-summary :median-ms)}})))

(defn run-probe
  ([] (run-probe {}))
  ([{:keys [case-ids warmup-samples sample-count]
     :or {warmup-samples 2
          sample-count 7}}]
   (let [selected (if (seq case-ids)
                    (filter (comp (set (map keyword case-ids)) :id) default-cases)
                    default-cases)]
     {:probe :relational-fuel-performance
      :warmup-samples warmup-samples
      :sample-count sample-count
      :implementations (update-vals implementations #(dissoc % :step-fuelo))
      :cases (mapv #(measure-case %
                                  {:warmup-samples warmup-samples
                                   :sample-count sample-count})
                   selected)})))

(defn -main
  [& case-id-texts]
  (pp/pprint
    (run-probe
      {:case-ids (seq case-id-texts)})))
