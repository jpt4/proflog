(ns proflog.combinatory-logic-test
  (:refer-clojure :exclude [==])
  (:require [clojure.string :as str]
            [clojure.core.logic :refer [== run]]
            [clojure.test :refer [deftest is testing]]
            [proflog.answer-overlay :as answer-overlay]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.combinatory-logic :as ski]
            [proflog.kernel :as kernel]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.kernel.constructor-recursive-profile :as constructor-recursive-profile]
            [proflog.kernel.equality-fragment :as equality-fragment]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.proof :as proof]
            [proflog.query :as query]))

(defn- succeeds?
  [formula fuel]
  (let [program (ski/program)
        checked-formula (language/validate-query (:language program) formula)
        negated-formula (normalize/negate-formula checked-formula)]
    (seq
      (run 1 [proof]
        (kernel/prove-programo
          negated-formula
          '()
          '()
          '()
          program
          fuel
          proof)))))

(defn- private-var
  "Return a private helper var for test-only answer record export.

   The SKI evaluation itself stays in the `answer-overlay` query-entry
   relations. Export is ordinary presentation of the raw relational proof
   state."
  [sym]
  (ns-resolve 'proflog.answers sym))

(defn- export-answer-record
  [program checked-answer-vars raw-state]
  (apply (var-get (private-var 'export-answer-record))
         (:language program)
         checked-answer-vars
         raw-state))

(defn- relational-answer-records
  [query answer-vars {:keys [fuel
                             call-depth
                             proof-limit
                             max-raw-proof-limit
                             residual-continuation-fuel]
                      :or {proof-limit 4
                           call-depth 1}}]
  (let [program (ski/program)
        checked-query (language/validate-query (:language program) query)
        checked-answer-vars ((var-get (private-var 'validate-answer-vars))
                             checked-query
                             answer-vars)
        negated-query (normalize/negate-formula checked-query)
        raw-limit (or max-raw-proof-limit proof-limit)
        continuation-fuel (or residual-continuation-fuel 96)
        raw-states (run raw-limit [answer-vars-out sigma-out neqs-out residuals-out proof]
                     (== answer-vars-out checked-answer-vars)
                     (answer-overlay/prove-program-query-entry-scheduledo
                       negated-query
                       checked-answer-vars
                       program
                       sigma-out
                       neqs-out
                       residuals-out
                       fuel
                       call-depth
                       continuation-fuel
                       proof))]
    (->> raw-states
         (map #(export-answer-record program checked-answer-vars %))
         (keep identity)
         (take proof-limit)
         vec)))

(defn- proof-backed?
  [proofs]
  (and (seq proofs)
       (some #(or (proof/contains-step? % 'neg-call)
                  (proof/contains-step? % 'pos-call)
                  (proof/contains-step? % 'neg-call-guarded-alt)
                  (proof/contains-step? % 'pos-call-guarded-alt))
             proofs)))

(defn- traced-route
  [thunk]
  (let [calls (atom [])
        original-kernel-prove-programo (var-get #'kernel/prove-programo)
        original-answer-query-entry-scheduledo
        (var-get #'answer-overlay/prove-program-query-entry-scheduledo)
        original-answer-query-entryo (var-get #'answer-overlay/prove-program-query-entryo)
        forbidden (fn [label]
                    (fn [& _]
                      (throw (ex-info "SKI evaluation used a forbidden route"
                                      {:route label}))))]
    (with-redefs [kernel/prove-programo
                  (fn [& args]
                    (swap! calls conj :kernel/prove-programo)
                    (apply original-kernel-prove-programo args))
                  answer-overlay/prove-program-query-entry-scheduledo
                  (fn [& args]
                    (swap! calls conj :answer-overlay/prove-program-query-entry-scheduledo)
                    (apply original-answer-query-entry-scheduledo args))
                  answer-overlay/prove-program-query-entryo
                  (fn [& args]
                    (swap! calls conj :answer-overlay/prove-program-query-entryo)
                    (apply original-answer-query-entryo args))
                  kernel/prove-program (forbidden :kernel-prove-program-wrapper)
                  query/query-succeeds (forbidden :query-succeeds)
                  answers/query-answers (forbidden :query-answers)
                  constructor-recursive/query-records
                  (forbidden :constructor-recursive-query-records)
                  constructor-recursive/query-succeeds?
                  (forbidden :constructor-recursive-query-succeeds)
                  constructor-recursive-profile/query-records
                  (forbidden :constructor-recursive-profile-query-records)
                  constructor-recursive-profile/query-succeeds?
                  (forbidden :constructor-recursive-profile-query-succeeds)
                  equality-fragment/prove-program-host (forbidden :equality-fragment)]
      {:value (thunk)
       :calls @calls})))

(deftest ski-evaluation-does-not-route-through-public-or-profiled-shortcuts
  (testing "SKI tests should prove through direct relational kernel/answer relations"
    (ast/nom result
      (let [{:keys [value calls]}
            (traced-route
              (fn []
                {:proofs (succeeds?
                           (ast/pos-lit
                             (ast/app-term 'step
                                           (ski/ap (ski/c 'icomb) (ski/c 'a))
                                           (ski/c 'a)))
                           32)
                 :records (relational-answer-records
                            (ast/pos-lit
                              (ast/app-term 'eval-for
                                            (ski/numeral 2)
                                            (ski/skk (ski/c 'a))
                                            (ast/var-term result)))
                            [result]
                            {:fuel 64
                             :call-depth 4
                             :proof-limit 1
                             :max-raw-proof-limit 16})}))]
        (is (proof-backed? (:proofs value)))
        (is (seq (:records value)))
        (is (some #{:kernel/prove-programo} calls))
        (is (some #{:answer-overlay/prove-program-query-entry-scheduledo} calls))
        (is (some #{:answer-overlay/prove-program-query-entryo} calls))))))

(deftest ski-root-reductions-close-through-the-kernel
  (testing "I x => x"
    (is (proof-backed?
          (succeeds?
            (ast/pos-lit (ast/app-term 'step
                                       (ski/ap (ski/c 'icomb) (ski/c 'a))
                                       (ski/c 'a)))
            32))))
  (testing "K x y => x"
    (is (proof-backed?
          (succeeds?
            (ast/pos-lit (ast/app-term 'step
                                       (ski/ap (ski/ap (ski/c 'kcomb) (ski/c 'a))
                                               (ski/c 'b))
                                       (ski/c 'a)))
            32))))
  (testing "S x y z => x z (y z)"
    (is (proof-backed?
          (succeeds?
            (ast/pos-lit (ast/app-term 'step
                                       (ski/ap (ski/ap (ski/ap (ski/c 'scomb)
                                                              (ski/c 'kcomb))
                                                       (ski/c 'kcomb))
                                               (ski/c 'a))
                                       (ski/ap (ski/ap (ski/c 'kcomb)
                                                       (ski/c 'a))
                                               (ski/ap (ski/c 'kcomb)
                                                       (ski/c 'a)))))
            48)))))

(deftest ski-skk-identity-fully-evaluates
  (is (succeeds?
        (ast/pos-lit (ast/app-term 'eval-for
                                   (ski/numeral 2)
                                   (ski/skk (ski/c 'a))
                                   (ski/c 'a)))
        64)))

(deftest ski-boolean-true-fully-evaluates
  (is (succeeds?
        (ast/pos-lit (ast/app-term 'eval-for
                                   (ski/numeral 1)
                                   (ski/choose (ski/true-term)
                                               (ski/c 'a)
                                               (ski/c 'b))
                                   (ski/c 'a)))
        64)))

(deftest ski-boolean-false-fully-evaluates
  (is (succeeds?
        (ast/pos-lit (ast/app-term 'eval-for
                                   (ski/numeral 2)
                                   (ski/choose (ski/false-term)
                                               (ski/c 'a)
                                               (ski/c 'b))
                                   (ski/c 'b)))
        96)))

(deftest ski-omega-quine-reproduces-itself-through-a-guided-trace
  (let [sii (ski/sii)
        i-sii (ski/ap (ski/c 'icomb) sii)
        omega (ski/omega)
        expanded (ski/ap i-sii i-sii)
        left-contracted (ski/ap sii i-sii)]
    (is (proof-backed?
          (succeeds?
            (ski/reduction-trace-formula
              [omega
               expanded
               left-contracted
               omega]
              {:relation 'full-step})
            160)))))

(deftest ski-answer-mode-exports-a-reduced-term
  (ast/nom result
    (let [records (relational-answer-records
                    (ast/pos-lit
                      (ast/app-term 'eval-for
                                    (ski/numeral 2)
                                    (ski/skk (ski/c 'a))
                                    (ast/var-term result)))
                    [result]
                    {:fuel 64
                     :call-depth 4
                     :proof-limit 4
                     :max-raw-proof-limit 16})
          expected (ski/c 'a)]
      (is (some #(= expected (-> % :bindings first second))
                records))
      (is (some #(and (= expected (-> % :bindings first second))
                      (empty? (:residuals %)))
                records)))))

(deftest combinatory-logic-namespace-does-not-contain-a-host-evaluator
  (let [source (slurp "src/proflog/combinatory_logic.clj")]
    (is (str/includes? source "pf/proflog"))
    (is (not (str/includes? source "query/query-succeeds")))
    (is (not (str/includes? source "answers/query-answers")))
    (is (not (re-find #"defn-?\s+(step|eval|eval-for|reduce|rewrite)" source)))))
