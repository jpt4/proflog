(ns proflog.kernel-finite-verifiers-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.fitting-programs :as fitting]
            [proflog.finite-transition-systems :as transition]
            [proflog.gv-probe :as gv-probe]
            [proflog.hard-family-overlay :as hard-family-overlay]
            [proflog.kernel.equality-fragment :as equality-fragment]
            [proflog.language :as language]
            [proflog.proof :as proof]
            [proflog.query :as query]))

(defn- query-for-relation
  [relation]
  (ast/pos-lit (ast/app-term relation)))

(defn- proofs-for
  [program query expected]
  (case expected
    :succeeds (query/query-succeeds program query 1 16)
    :fails (query/query-fails program query 1 16)))

(defn- assert-profiled-equality-proof
  [proofs label]
  (is (seq proofs) (str label " should produce proof evidence"))
  (let [proof (first proofs)]
    (is (proof/contains-step? proof 'profiled)
        (str label " should use a profiled proof layer"))
    (is (proof/contains-step? proof 'equality-fragment)
        (str label " should use the equality-fragment layer"))
    (is (not (proof/contains-step? proof 'hard-family-overlay))
        (str label " must not use the hard-family overlay"))))

(def finite-domain-status-language
  (language/language
    {:constants ['red 'green]
     :relations {'not-red 0
                 'not-red-or-not-red 0}}))

(defn- finite-domain-status-program
  []
  (ast/nom x
    (let [vx (ast/var-term x)
          red (ast/app-term 'red)]
      (language/compile-program
        finite-domain-status-language
        [(ast/clause 'not-red
                     []
                     (ast/forall-form x
                                      (ast/neq-lit vx red)))
         (ast/clause 'not-red-or-not-red
                     []
                     (ast/forall-form
                       x
                       (ast/or-form
                         (ast/neq-lit vx red)
                         (ast/neq-lit vx red))))]))))

(defn- status-for
  [program query]
  (query/query-status program query
                      {:timeout-ms 20000
                       :proof-limit 1
                       :poll-ms 0}))

(defn- assert-gv-status
  [scenario expected]
  (let [{:keys [program relation]} (gv-probe/scenario-config scenario)
        query (query-for-relation relation)
        status (query/query-status program query
                                   {:timeout-ms 20000
                                    :proof-limit 1
                                    :poll-ms 0})
        proofs (proofs-for program query expected)]
    (is (= expected status)
        (str scenario " status"))
    (assert-profiled-equality-proof proofs scenario)))

(deftest mandatory-group-verifier-associativity-rows-close-in-kernel
  (with-redefs [hard-family-overlay/query-status
                (fn [& _]
                  (throw (ex-info "ADR-39 must not use the hard-family overlay"
                                  {})))]
    (doseq [[scenario expected]
            [["z1-full-assoc-truth" :succeeds]
             ["z2-precomputed-assoc-truth" :succeeds]
             ["z2-full-assoc-truth" :succeeds]
             ["non-group-precomputed-assoc" :fails]
             ["non-group-full-assoc" :fails]]]
      (testing scenario
        (assert-gv-status scenario expected)))))

(defn- assert-transition-status
  [spec relation expected]
  (let [program (transition/transition-program spec)
        query (transition/proposition relation)
        status (query/query-status program query
                                   {:timeout-ms 20000
                                    :proof-limit 1
                                    :poll-ms 0})
        proofs (proofs-for program query expected)
        label (str (:id spec) "/" relation)]
    (is (= expected status) label)
    (assert-profiled-equality-proof proofs label)))

(deftest finite-transition-system-examples-are-significant-and-generic
  (testing "the complete transition system is larger than a smoke table"
    (is (>= (count (:states transition/complete-deterministic-spec)) 4))
    (is (>= (count (:symbols transition/complete-deterministic-spec)) 3))
    (is (>= (count (:transitions transition/complete-deterministic-spec)) 12)))
  (testing "complete deterministic transition systems prove totality and determinism"
    (assert-transition-status transition/complete-deterministic-spec
                              'delta-total
                              :succeeds)
    (assert-transition-status transition/complete-deterministic-spec
                              'delta-deterministic
                              :succeeds))
  (testing "incomplete and nondeterministic systems are refuted"
    (assert-transition-status transition/incomplete-spec
                              'delta-total
                              :fails)
    (assert-transition-status transition/nondeterministic-spec
                              'delta-deterministic
                              :fails)))

(deftest equality-fragment-implementation-has-no-family-dispatch
  (let [source (slurp "src/proflog/kernel/equality_fragment.clj")]
    (is (not (str/includes? source "gv-probe")))
    (is (not (str/includes? source "finite-transition-systems")))
    (is (not (str/includes? source "hard-family-overlay")))))

(deftest equality-fragment-status-does-not-rebind-universal-witness-per-branch
  (testing "FD05 warm/cool disjointness has success proof evidence but no failure proof"
    (let [program (fitting/finite-domain-program)
          query (query-for-relation 'warm-cool-disjoint)]
      (is (= :succeeds (status-for program query)))
      (assert-profiled-equality-proof
        (query/query-succeeds program query 1 32)
        "warm/cool disjoint")
      (is (empty? (equality-fragment/prove-program-host program query 1 32))
          "the profiled positive failure side must not close with branch-local bindings")))
  (testing "real shared counterexamples still refute universal finite-domain formulas"
    (let [program (finite-domain-status-program)
          not-red (query-for-relation 'not-red)
          not-red-or-not-red (query-for-relation 'not-red-or-not-red)]
      (is (= :fails (status-for program not-red)))
      (assert-profiled-equality-proof
        (query/query-fails program not-red 1 16)
        "not-red")
      (is (= :fails (status-for program not-red-or-not-red)))
      (assert-profiled-equality-proof
        (query/query-fails program not-red-or-not-red 1 16)
        "not-red-or-not-red"))))
