(ns proflog.answers-test
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fresh run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.answer-overlay :as answer-overlay]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.language :as language]
            [proflog.list-kernel-matrix-probe :as matrix]
            [proflog.list-programs-test :as lp]
            [proflog.normalize :as normalize]
            [proflog.proof :as proof]
            [proflog.recursive-synthesis-test :as rst]))

(def answer-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'p 1
                 'dup 1
                 'sym 1
                 'loop 1
                 'even 1
                 'odd 1
                 'win 1}}))

(def completion-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'step 2}}))

(def track-a-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'descend 2}}))

(def track-d-language
  (language/language
    {:constants ['zero]
     :relations {'loop 1
                 'twice 1}}))

(defn numeral
  [n]
  (if (zero? n)
    (ast/app-term 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn simple-program
  []
  (ast/nom x
    (language/compile-program
      answer-language
      [(ast/clause 'p [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))])))

(defn nim-program
  []
  (ast/nom x y
    (language/compile-program
      answer-language
      [(ast/clause 'win [x]
                   (ast/exists-form y
                                    (ast/and-form
                                      (ast/or-form
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's (ast/var-term y)))
                                        (ast/eq-lit (ast/var-term x)
                                                    (ast/app-term 's
                                                                  (ast/app-term 's
                                                                                (ast/var-term y)))))
                                      (ast/neg-lit (ast/app-term 'win (ast/var-term y))))))])))

(defn duplicate-answer-program
  []
  (ast/nom x
    (language/compile-program
      answer-language
      [(ast/clause 'dup [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))
       (ast/clause 'dup [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 'zero)))
       (ast/clause 'dup [x]
                   (ast/eq-lit (ast/var-term x)
                               (ast/app-term 's (ast/app-term 'zero))))])))

(defn duplicate-symbolic-answer-program
  []
  (ast/nom x y z
    (language/compile-program
      answer-language
      [(ast/clause 'sym [x]
                   (ast/exists-form y
                     (ast/and-form
                       (ast/eq-lit (ast/var-term x)
                                   (ast/app-term 's (ast/var-term y)))
                       (ast/pos-lit (ast/app-term 'loop (ast/var-term y))))))
       (ast/clause 'sym [x]
                   (ast/exists-form z
                     (ast/and-form
                       (ast/eq-lit (ast/var-term x)
                                   (ast/app-term 's (ast/var-term z)))
                       (ast/pos-lit (ast/app-term 'loop (ast/var-term z))))))])))

(defn completion-program
  []
  (ast/nom x y
    (language/compile-program
      completion-language
      [(ast/clause 'step [x y]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's (ast/var-term y)))
                     (ast/eq-lit (ast/var-term x)
                                 (ast/app-term 's
                                               (ast/app-term 's
                                                             (ast/var-term y))))))])))

(defn track-a-recursive-program
  []
  (ast/nom x y predecessor
    (language/compile-program
      track-a-language
      [(ast/clause 'descend [x y]
                   (ast/or-form
                     (ast/eq-lit (ast/var-term x)
                                 (ast/var-term y))
                     (ast/exists-form predecessor
                                      (ast/and-form
                                        (ast/eq-lit
                                          (ast/var-term x)
                                          (ast/app-term 's
                                                        (ast/var-term predecessor)))
                                        (ast/pos-lit
                                          (ast/app-term 'descend
                                                        (ast/var-term predecessor)
                                                        (ast/var-term y)))))))])))

(defn track-d-recursive-program
  []
  (ast/nom x
    (language/compile-program
      track-d-language
      [(ast/clause 'loop [x]
                   (ast/or-form
                     (ast/pos-lit (ast/app-term 'loop (ast/var-term x)))
                     (ast/eq-lit (ast/var-term x) (ast/app-term 'zero))))
       (ast/clause 'twice [x]
                   (ast/and-form
                     (ast/pos-lit (ast/app-term 'loop (ast/var-term x)))
                     (ast/pos-lit (ast/app-term 'loop (ast/var-term x)))))])))

(defn answer-terms
  [records]
  (mapv (fn [record]
          (-> record :bindings first second))
        records))

(defn self-contradictory-neq?
  [formula]
  (and (= 'neq (ast/tag-of formula))
       (= (second formula) (nth formula 2))))

(defn proof-subtrees
  [proof step]
  (filter (fn [node]
            (and (coll? node)
                 (= step (first node))))
          (tree-seq coll? seq proof)))

(defn scheduler-results
  [program frontier proof-vars sigma neqs proof-fuel continuation-fuel]
  (run 1 [sigma-out neqs-out residuals-out proof]
    (answer-overlay/schedule-structural-residual-frontiero
      frontier
      proof-vars
      sigma
      sigma-out
      neqs
      neqs-out
      residuals-out
      program
      proof-fuel
      continuation-fuel
      proof)))

(defn- matrix-answer-records-for-config
  [{:keys [query answer-vars fuel raw-limit call-depth]}]
  (let [program (matrix/list-program)
        negated-query (normalize/negate-formula
                        (language/validate-query (:language program) query))
        raw-states ((deref #'answers/program-raw-answer-states)
                    program
                    negated-query
                    answer-vars
                    fuel
                    raw-limit
                    call-depth)
        exported (->> raw-states
                      (map #((deref #'answers/export-program-answer-record)
                              program
                              answer-vars
                              %))
                      (keep identity)
                      vec)]
    ((deref #'answers/prioritize-answer-records)
     ((deref #'answers/merge-answer-records) exported))))

(defn matrix-answer-records
  [case-id]
  (matrix-answer-records-for-config (matrix/case-config case-id)))

(deftest bounded-ground-enumerator-follows-constructor-depth
  (testing "ground term generation stays inside the declared language and depth bound"
    (is (= [(numeral 0) (numeral 1) (numeral 2) (numeral 3)]
           (answers/ground-terms-up-to-depth answer-language 3)))))

(deftest generic-query-answers-export-symbolic-bindings
  (testing "the generic answer API exports direct substitutions without bounded term enumeration"
    (ast/nom x
      (let [records (answers/query-answers
                      (simple-program)
                      (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                      [x]
                      {:proof-limit 4})]
        (is (= [(numeral 0)]
               (answer-terms records)))
        (is (= [] (:residuals (first records))))))))

(deftest query-answers-collect-unique-answers-beyond-duplicate-proof-paths
  (testing "duplicate proofs for one answer do not starve later distinct answers"
    (ast/nom x
      (let [records (answers/query-answers
                      (duplicate-answer-program)
                      (ast/pos-lit (ast/app-term 'dup (ast/var-term x)))
                      [x]
                      {:proof-limit 2})
            answer-terms (answer-terms records)]
        (is (= [(numeral 0) (numeral 1)]
               answer-terms))
        (is (not-any? self-contradictory-neq?
                      (mapcat :residuals records)))
        (is (= []
               (:residuals (first records))))
        (is (every? (fn [residual]
                      (= (ast/neq-lit (numeral 1) (numeral 0))
                         residual))
                    (:residuals (second records))))))))

(deftest query-answer-diagnostics-reports-raw-vs-unique-growth
  (testing "diagnostics show duplicate raw proof paths before later unique answers surface"
    (ast/nom x
      (let [snapshots (answers/query-answer-diagnostics
                        (duplicate-answer-program)
                        (ast/pos-lit (ast/app-term 'dup (ast/var-term x)))
                        [x]
                        {:raw-limits [1 2 4]
                         :sample-limit 2})
            first-snapshot (first snapshots)
            last-snapshot (last snapshots)]
        (is (= [1 2 4]
               (mapv :raw-count snapshots)))
        (is (= 1
               (:unique-count first-snapshot)))
        (is (= 2
               (:unique-count last-snapshot)))
        (is (= [(numeral 0) (numeral 1)]
               (answer-terms (:sample-records last-snapshot))))))))

(deftest query-answer-diagnostics-route-literal-program-queries-through-the-answer-overlay-entry
  (testing "top-level literal program queries now call the extracted answer overlay entry relation"
    (ast/nom x
      (let [program (duplicate-answer-program)
            query (ast/pos-lit (ast/app-term 'dup (ast/var-term x)))
            query-entry-calls (atom 0)
            general-answer-calls (atom 0)
            original-query-entry answer-overlay/prove-program-query-entryo
            original-general-answer answer-overlay/prove-program-answero]
        (with-redefs [answer-overlay/prove-program-query-entryo
                      (fn [& args]
                        (swap! query-entry-calls inc)
                        (apply original-query-entry args))
                      answer-overlay/prove-program-answero
                      (fn [& args]
                        (swap! general-answer-calls inc)
                        (apply original-general-answer args))]
          (answers/query-answer-diagnostics
            program
            query
            [x]
            {:raw-limits [1]
             :sample-limit 1})
          (is (pos? @query-entry-calls))
          (is (zero? @general-answer-calls)))))))

(deftest query-answer-diagnostics-route-composite-program-queries-through-the-general-answer-overlay
  (testing "non-literal program queries use the extracted general answer overlay relation"
    (ast/nom x
      (let [program (simple-program)
            query (ast/and-form
                    (ast/pos-lit (ast/app-term 'p (ast/var-term x)))
                    (ast/neq-lit (ast/var-term x) (numeral 1)))
            query-entry-calls (atom 0)
            general-answer-calls (atom 0)
            original-query-entry answer-overlay/prove-program-query-entryo
            original-general-answer answer-overlay/prove-program-answero]
        (with-redefs [answer-overlay/prove-program-query-entryo
                      (fn [& args]
                        (swap! query-entry-calls inc)
                        (apply original-query-entry args))
                      answer-overlay/prove-program-answero
                      (fn [& args]
                        (swap! general-answer-calls inc)
                        (apply original-general-answer args))]
          (answers/query-answer-diagnostics
            program
            query
            [x]
            {:raw-limits [1]
             :sample-limit 1})
          (is (zero? @query-entry-calls))
          (is (pos? @general-answer-calls)))))))

(deftest adr33-structural-completion-requires-constructor-demand
  (testing "wholly symbolic residual families stay residual, but demanded frontiers are structurally identified"
    (ast/nom x y
      (let [program (completion-program)
            symbolic-record
            {:bindings [[x (ast/var-term x)]]
             :residuals [(ast/neg-lit
                           (ast/app-term 'step
                                         (ast/var-term x)
                                         (ast/var-term y)))]
             :proofs ['raw-frontier]}
            demanded-record
            {:bindings [[x (ast/var-term x)]]
             :residuals [(ast/neg-lit
                           (ast/app-term 'step
                                         (ast/var-term x)
                                         (numeral 1)))]
             :proofs ['raw-frontier]}
            chained-demand-record
            {:bindings [[x (ast/var-term x)]]
             :residuals [(ast/neg-lit
                           (ast/app-term 'step
                                         (ast/var-term x)
                                         (ast/var-term y)))
                         (ast/neg-lit
                           (ast/app-term 'step
                                         (ast/var-term y)
                                         (numeral 0)))]
             :proofs ['raw-frontier]}
            structurally-completable?
            (deref #'answers/structurally-completable-record?)]
        (is (not (structurally-completable? program symbolic-record)))
        (is (structurally-completable? program demanded-record))
        (is (structurally-completable? program chained-demand-record))))))

(deftest track-a-continuation-agenda-uses-independent-fuel
  (testing "the live residual scheduler exports on exhausted continuation fuel and closes with its own fuel budget"
    (ast/nom q
      (let [program (track-a-recursive-program)
            frontier (list (ast/neg-lit
                             (ast/app-term 'descend
                                           (ast/var-term q)
                                           (ast/app-term 'zero))))
            sigma (list [q (numeral 2)])
            neqs '()]
        (doseq [continuation-fuel [0 1]]
          (let [[[sigma-out neqs-out residuals-out proof]]
                (scheduler-results
                  program
                  frontier
                  [q]
                  sigma
                  neqs
                  64
                  continuation-fuel)]
            (is (= [(numeral 2)] (mapv second sigma-out)))
            (is (= neqs neqs-out))
            (is (= ['descend] (mapv (comp second second) residuals-out)))
            (is (proof/contains-step? proof 'structural-residual-scheduler-export))
            (is (not (proof/contains-step? proof 'structural-residual-scheduler-continue)))))
        (let [[[sigma-out neqs-out residuals-out proof]]
              (scheduler-results
                program
                frontier
                [q]
                sigma
                neqs
                0
                4)
              agenda-proofs (proof-subtrees proof
                                            'structural-residual-continuation-agenda)]
          (is (seq sigma-out))
          (is (= neqs neqs-out))
          (is (= '() residuals-out))
          (is (proof/contains-step? proof 'structural-residual-scheduler-continue))
          (is (proof/contains-step? proof 'structural-residual-continuation-agenda))
          (is (proof/contains-step? proof 'structural-residual-continuation-fuel))
          (is (not (proof/contains-step? proof 'constructor-recursive)))
          (is (seq agenda-proofs))
          (doseq [agenda-proof agenda-proofs
                  forbidden-step ['defer-call
                                  'guarded-call-seq-defer
                                  'eq-triggered-residual-call
                                  'eq-triggered-residual-neg-call]]
            (is (not (proof/contains-step? agenda-proof forbidden-step))
                (str forbidden-step
                     " should not appear inside the continuation agenda"))))))))

(deftest adr35-track-c-prioritizes-constructor-demanded-residuals
  (testing "the scheduler relation promotes a constructor-demanded residual without predicate-specific dispatch"
    (let [x 'track-c-x
          y 'track-c-y
          program (completion-program)
          symbolic-residual (ast/neg-lit
                              (ast/app-term 'step
                                            (ast/var-term x)
                                            (ast/var-term y)))
          demanded-residual (ast/neg-lit
                              (ast/app-term 'step
                                            (ast/var-term y)
                                            (numeral 0)))
          frontier (list symbolic-residual demanded-residual)
          results (run 1 [out]
                    (fresh [ordered proof]
                      ((deref #'answer-overlay/prioritize-structural-residual-frontiero)
                       program
                       '()
                       frontier
                       ordered
                       proof)
                      (== out [ordered proof])))]
      (is (= [[[demanded-residual symbolic-residual]
               '(structural-residual-priority-promote-demanded)]]
             results)))))

(deftest adr35-track-c-scheduler-closes-with-priority-proof
  (testing "priority scheduling preserves the completed answer state and records promoted demand evidence"
    (let [x 'track-c-x
          y 'track-c-y
          program (completion-program)
          symbolic-residual (ast/neg-lit
                              (ast/app-term 'step
                                            (ast/var-term x)
                                            (ast/var-term y)))
          demanded-residual (ast/neg-lit
                              (ast/app-term 'step
                                            (ast/var-term y)
                                            (numeral 0)))
          frontier (list symbolic-residual demanded-residual)
          sigma '()
          expected-bindings (select-keys
                              (into {}
                                    (:sigma
                                      ((deref #'answer-overlay/fast-schedule-live-structural-frontier)
                                       program
                                       sigma
                                       '()
                                       (list demanded-residual symbolic-residual)
                                       4)))
                              [x y])
          results (run 1 [out]
                    (fresh [sigma-out neqs-out residuals-out proof]
                      (answer-overlay/schedule-structural-residual-frontiero
                        frontier
                        '()
                        sigma
                        sigma-out
                        '()
                        neqs-out
                        residuals-out
                        program
                        4
                        4
                        proof)
                      (== out [sigma-out neqs-out residuals-out proof])))
          [[sigma-out neqs-out residuals-out proof]] results]
      (is (= expected-bindings
             (select-keys (into {} sigma-out) [x y])))
      (is (= '() neqs-out))
      (is (= '() residuals-out))
      (is (proof/contains-step? proof 'structural-residual-priority-promote-demanded))
      (is (proof/contains-step? proof 'structural-residual-continuation)))))

(deftest adr35-track-d-prunes-active-recursive-reentry
  (testing "the fast residual continuation visited set rejects active self-recursion"
    (let [program (track-d-recursive-program)
          frontier (list (ast/neg-lit
                           (ast/app-term 'loop (ast/app-term 'zero))))
          [[sigma-out neqs-out residuals-out _proof]]
          (scheduler-results
            program
            frontier
            '()
            '()
            '()
            16
            2)
          [_ fast-proof]
          (first ((deref #'answer-overlay/fast-continuation-settle-residual-sequence)
                  program
                  {:subst {} :fuel 2}
                  frontier))]
      (is (seq sigma-out))
      (is (= '() neqs-out))
      (is (= '() residuals-out))
      (is (proof/contains-step? fast-proof 'structural-residual-visited-enter))
      (is (= 1 (count (proof-subtrees fast-proof 'structural-residual-call))))
      (is (not (proof/contains-step? fast-proof 'constructor-recursive))))))

(deftest adr35-track-d-leaves-active-key-after-success
  (testing "sequential duplicate calls are not globally pruned after one call closes"
    (let [program (track-d-recursive-program)
          frontier (list (ast/neg-lit
                           (ast/app-term 'twice (ast/app-term 'zero))))
          [[sigma-out neqs-out residuals-out _proof]]
          (scheduler-results
            program
            frontier
            '()
            '()
            '()
            16
            4)
          [_ fast-proof]
          (first ((deref #'answer-overlay/fast-continuation-settle-residual-sequence)
                  program
                  {:subst {} :fuel 4}
                  frontier))
          calls (proof-subtrees fast-proof 'structural-residual-call)
          loop-calls (filter (fn [call]
                               (= 'loop (second (second call))))
                             calls)]
      (is (seq sigma-out))
      (is (= '() neqs-out))
      (is (= '() residuals-out))
      (is (= 2 (count loop-calls)))
      (is (every? #(proof/contains-step?
                     %
                     'structural-residual-visited-enter)
                  loop-calls)))))

(deftest adr35-raw-matrix-answers-use-relational-continuation-proofs
  (testing "completed raw matrix answers do not carry constructor-recursive sidecar proof tags"
    (with-redefs [constructor-recursive/settle-record
                  (fn [& _]
                    (throw (ex-info "ADR-35 raw answers must not call sidecar settlement"
                                    {})))]
      (let [{:keys [answer-vars target-bindings] :as config}
            (matrix/case-config :reverse-input-flat-longer)
            target-bindings (set target-bindings)
            records (matrix-answer-records-for-config config)
            target-record (some (fn [record]
                                  (let [bindings (mapv (fn [answer-var]
                                                         [answer-var
                                                          (answers/binding-term record answer-var)])
                                                       answer-vars)]
                                    (when (and (empty? (:residuals record))
                                               (contains? target-bindings bindings))
                                      record)))
                                records)]
        (is target-record)
        (is (some #(proof/contains-step? % 'structural-residual-scheduler-continue)
                  (:proofs target-record)))
        (is (some #(proof/contains-step? % 'structural-residual-continuation)
                  (:proofs target-record)))
        (is (not-any? #(proof/contains-step? % 'constructor-recursive)
                      (:proofs target-record)))))))

(deftest query-stage-diagnostics-summarize-proof-families
  (testing "stage diagnostics expose duplicate exported answers and proof-family summaries"
    (ast/nom x
      (let [stages (answers/query-stage-diagnostics
                     (duplicate-answer-program)
                     (ast/pos-lit (ast/app-term 'dup (ast/var-term x)))
                     [x]
                     {:call-depth 1
                      :raw-limits [4]
                      :sample-limit 2
                      :proof-sample-limit 2
                      :proof-step-limit 6})
            stage (second stages)
            snapshot (-> stage :snapshots first)]
        (is (= 1 (:stage stage)))
        (is (:productive? stage))
        (is (= 2 (:best-unique-count stage)))
        (is (= 2 (:duplicate-exported-count snapshot)))
        (is (= (:raw-count snapshot)
               (+ (:distinct-proof-signature-count snapshot)
                  (:duplicate-proof-signature-count snapshot))))
        (is (seq (:common-proof-signatures snapshot)))))))

(deftest generic-formula-answers-preserve-residual-disequalities
  (testing "symbolic answer export keeps residual neq constraints when the proof closes elsewhere"
    (ast/nom x
      (let [records (answers/formula-answers
                      answer-language
                      (ast/and-form
                        (ast/neq-lit (ast/var-term x) (numeral 1))
                        (ast/eq-lit (numeral 0) (numeral 1)))
                      [x]
                      {:proof-limit 4})
            residual-record (some (fn [record]
                                    (when (= [(ast/neq-lit (ast/var-term x) (numeral 1))]
                                             (:residuals record))
                                      record))
                                  records)]
        (is residual-record)
        (is (= (ast/var-term x)
               (answers/binding-term residual-record x)))))))

(deftest generic-query-answers-handle-a-recursive-open-query
  (testing "the symbolic answer path returns both a direct witness and a residual recursive obligation"
    (ast/nom x
      (let [records (answers/query-answers
                      (rst/recursive-parity-program)
                      (ast/pos-lit (ast/app-term 'even (ast/var-term x)))
                      [x]
                      {:proof-limit 2
                       :fuel 8
                       :call-depth 1})
            zero-record (some (fn [record]
                                (when (= (numeral 0)
                                         (answers/binding-term record x))
                                  record))
                              records)
            symbolic-record (some (fn [record]
                                    (let [term (answers/binding-term record x)
                                          residuals (:residuals record)]
                                      (when (and (= 'app (ast/tag-of term))
                                                 (= 's (second term))
                                                 (some (fn [residual]
                                                         (and (= 'neg (ast/tag-of residual))
                                                              (= 'odd (second (second residual)))))
                                                       residuals))
                                        record)))
                                  records)]
        (is zero-record)
        (is symbolic-record)))))

(deftest generic-query-answers-preserve-nim-call-obligations
  (testing "open win(x) exports a symbolic predecessor constraint plus a residual win obligation"
    (ast/nom x
      (let [records (answers/query-answers
                      (nim-program)
                      (ast/pos-lit (ast/app-term 'win (ast/var-term x)))
                      [x]
                      {:proof-limit 2
                       :fuel 8
                       :call-depth 1})
            record (first records)
            x-term (answers/binding-term record x)
            residual (first (:residuals record))]
        (is (= 'app (ast/tag-of x-term)))
        (is (= 's (second x-term)))
        (is (ast/var-term? (nth x-term 2)))
        (is (= [(ast/pos-lit (ast/app-term 'win (nth x-term 2)))]
               (:residuals record)))))))

(deftest query-answer-diagnostics-show-the-reverse-recursive-subcall-boundary
  (testing "diagnostics still expose the raw symbolic reverse frontier even though query-answers now prefers the closed list-family fast path"
    (ast/nom r
      (let [input (lp/list-term (ast/app-term 'a)
                                (ast/app-term 'b))
            snapshots (answers/query-answer-diagnostics
                        (lp/list-program)
                        (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
                        [r]
                        {:raw-limits [1]
                         :fuel 32
                         :call-depth 1
                         :sample-limit 1})
            snapshot (first snapshots)
            record (first (:sample-records snapshot))]
        (is (= 1 (:raw-count snapshot)))
        (is (= 1 (:unique-count snapshot)))
        (is (= 1 (:call-depth snapshot)))
        (is (= (lp/list-term (ast/app-term 'a))
               (answers/binding-term record r)))
        ;; ADR-0016's fairer agenda plus residual normalization now drops the
        ;; old constructor-clash disequality, leaving only the two meaningful
        ;; deferred recursive obligations.
        (is (= 2 (count (:residuals record))))
        (is (some (fn [residual]
                    (and (= 'neg (ast/tag-of residual))
                         (= 'append (second (second residual)))))
                  (:residuals record)))
        (is (some (fn [residual]
                    (and (= 'neg (ast/tag-of residual))
                         (= 'reverse (second (second residual)))))
                  (:residuals record)))))))

(deftest query-stage-diagnostics-distinguish-productive-and-dry-reverse-stages
  (testing "stage diagnostics show that reverse stays productive through one recursive stage under fully relational deferral"
    (ast/nom r
      (let [input (lp/list-term (ast/app-term 'a)
                                (ast/app-term 'b))
            query (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
            negated-query (normalize/negate-formula query)
            stages (answers/query-stage-diagnostics
                     (lp/list-program)
                     query
                     [r]
                     {:call-depth 1
                      :raw-limits [1]
                      :fuel 32
                      :sample-limit 1
                      :proof-sample-limit 1
                      :proof-step-limit 8})
            stage-0 (nth stages 0)
            stage-1 (nth stages 1)
            snapshot-0 (-> stage-0 :snapshots first)
            snapshot-1 (-> stage-1 :snapshots first)]
        (is (= negated-query (:query-formula stage-0)))
        (is (zero? (:unfold-depth stage-0)))
        (is (zero? (:kernel-call-depth stage-0)))
        (is (= negated-query (:query-formula stage-1)))
        (is (zero? (:unfold-depth stage-1)))
        (is (= 1 (:kernel-call-depth stage-1)))
        (is (= 0 (:call-depth snapshot-0)))
        (is (= 1 (:call-depth snapshot-1)))
        (is (:productive? stage-0))
        (is (= 1 (:best-unique-count stage-0)))
        (is (= 1 (:first-productive-raw-limit stage-0)))
        (is (:productive? stage-1))
        (is (= 1 (:best-unique-count stage-1)))
        (is (= 1 (:first-productive-raw-limit stage-1)))
        (is (= 1 (:raw-count snapshot-0)))
        (is (= 1 (:raw-count snapshot-1)))))))

(deftest query-answers-use-call-depth-1-to-refine-the-direct-reverse-frontier
  (testing "the generic path now prioritizes the closed reverse answer while diagnostics still expose the symbolic frontier"
    (ast/nom r
      (let [program (lp/list-program)
            input (lp/list-term (ast/app-term 'a)
                                (ast/app-term 'b))
            query (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
            depth-1 (answers/query-answers
                      program
                      query
                      [r]
                      {:proof-limit 1
                       :max-raw-proof-limit 64
                       :fuel 64
                       :call-depth 1})
            first-depth-1 (first depth-1)]
        (is (= (lp/list-term (ast/app-term 'b)
                             (ast/app-term 'a))
               (answers/binding-term first-depth-1 r)))
        (is (= [] (:residuals first-depth-1)))))))

(deftest query-answers-prefer-the-first-concrete-inverse-append-split-over-symbolic-frontiers
  (testing "the generic path now recovers the full inverse-append split family"
    (let [program (lp/list-program)
          abc (lp/list-term (ast/app-term 'a)
                            (ast/app-term 'b)
                            (ast/app-term 'c))]
      (ast/nom left right
        (let [records (answers/query-answers
                        program
                        (ast/pos-lit (ast/app-term 'append
                                                   (ast/var-term left)
                                                   (ast/var-term right)
                                                   abc))
                        [left right]
                        {:proof-limit 4
                         :max-raw-proof-limit 64
                         :fuel 64
                         :call-depth 1})]
          (is (= [[[left (lp/list-term)]
                    [right abc]]
                   [[left (lp/list-term (ast/app-term 'a))]
                    [right (lp/list-term (ast/app-term 'b)
                                         (ast/app-term 'c))]]
                   [[left (lp/list-term (ast/app-term 'a)
                                        (ast/app-term 'b))]
                    [right (lp/list-term (ast/app-term 'c))]]
                   [[left abc]
                    [right (lp/list-term)]]]
                 (mapv :bindings records)))
          (is (every? empty? (map :residuals records))))))))

(deftest query-answers-collapse-duplicate-reverse-residuals
  (testing "symbolic reverse frontiers no longer export duplicate residual literals"
    (ast/nom r
      (let [program (lp/list-program)
            input (lp/list-term (ast/app-term 'a)
                                (ast/app-term 'b))
            query (ast/pos-lit (ast/app-term 'reverse input (ast/var-term r)))
            diagnostics (answers/query-answer-diagnostics
                          program
                          query
                          [r]
                          {:raw-limits [1]
                           :fuel 32
                           :call-depth 1
                           :sample-limit 1})
            residuals (-> diagnostics first :sample-records first :residuals)]
        (is (= (count residuals)
               (count (distinct residuals))))))))

(deftest query-answers-merge-alpha-equivalent-symbolic-frontiers
  (testing "alpha-equivalent symbolic answers merge into one canonical exported frontier"
    (ast/nom x
      (let [snapshot (-> (answers/query-answer-diagnostics
                           (duplicate-symbolic-answer-program)
                           (ast/pos-lit (ast/app-term 'sym (ast/var-term x)))
                           [x]
                           {:raw-limits [4]
                            :fuel 8
                            :call-depth 1
                            :sample-limit 2})
                         first)
            record (-> snapshot :sample-records first)]
        (is (= 4 (:raw-count snapshot)))
        ;; The fairer raw stream now reaches four alpha-equivalent symbolic
        ;; proofs, all of which merge into one canonical exported frontier.
        (is (= 1 (:unique-count snapshot)))
        (is (= 3 (:duplicate-exported-count snapshot)))
        (is (= (ast/app-term 's (ast/var-term '_0))
               (answers/binding-term record x)))
        (is (= [(ast/neg-lit (ast/app-term 'loop (ast/var-term '_0)))]
               (:residuals record)))
        (is (< 1 (count (:proofs record))))))))

(deftest bounded-open-query-generation-finds-first-small-nim-winner
  (testing "the non-generic bounded materializer still recovers the first winning Nim position"
    (ast/nom x
      (let [records (answers/query-ground-answers
                      (nim-program)
                      (ast/pos-lit (ast/app-term 'win (ast/var-term x)))
                      [x]
                      {:max-depth 4
                       :limit 1
                       :failure-timeout-ms 2000
                       :fuel 8})]
        (is (= [(numeral 1)]
               (answer-terms records)))))))
