(ns proflog.fitting-programs-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [proflog.fitting-programs :as fitting]
            [proflog.hard-family-overlay :as hard-family-overlay]
            [proflog.kernel.constructor-recursive :as constructor-recursive]
            [proflog.proof :as proof]))

(defn- evaluate-with-host-overlays-disabled
  [case-id]
  (with-redefs [hard-family-overlay/query-status
                (fn [& _]
                  (throw (ex-info "ADR-38 must not use the hard-family overlay"
                                  {:case-id case-id})))
                constructor-recursive/settle-record
                (fn [& _]
                  (throw (ex-info "ADR-38 list rows must not use sidecar settlement"
                                  {:case-id case-id})))]
    (fitting/evaluate-case case-id)))

(defn- assert-outcome
  [case-id expected]
  (let [result (evaluate-with-host-overlays-disabled case-id)]
    (is (= expected (:outcome result))
        (str case-id " result: " (pr-str result)))
    result))

(deftest p1-and-p2-deeper-cases-evaluate-through-kernel-proofs
  (testing "P1 direct proof and refutation cases carry ordinary proof evidence"
    (doseq [[case-id expected] [[:p1-even-0-succeeds :succeeds]
                               [:p1-odd-1-succeeds :succeeds]
                               [:p1-odd-0-fails :fails]]]
      (let [result (assert-outcome case-id expected)]
        (is (pos? (:proof-count result)))
        (is (seq (:proof-steps result)))
        (is (not (some #{'constructor-recursive-settle}
                       (:proof-steps result)))))))
  (testing "P2 proves the deeper Nim winner/loser pattern by proof search"
    (doseq [[case-id expected] [[:p2-win-3-fails :fails]
                               [:p2-win-4-succeeds :succeeds]]]
      (let [result (assert-outcome case-id expected)]
        (is (pos? (:proof-count result)))
        (is (seq (:proof-steps result)))))))

(deftest move-warning-and-finite-domain-cases-are-kernel-classified
  (testing "Fitting's move-warning factoring remains visible in the core kernel outcome"
    (assert-outcome :move-1-to-0-succeeds :succeeds)
    (assert-outcome :move-0-to-1-fails :fails)
    (let [result (assert-outcome :factored-win-1-unresolved :unresolved)]
      (is (= :invalid-auxiliary-relation-factoring
             (:classification result)))))
  (testing "finite-domain examples cover true, false, and unresolved outcomes"
    (doseq [[case-id expected] [[:fd-color-red-succeeds :succeeds]
                               [:fd-color-yellow-fails :fails]
                               [:fd-warm-blue-fails :fails]
                               [:fd-cool-green-succeeds :succeeds]
                               [:fd-warm-unique-succeeds :succeeds]]]
      (let [result (assert-outcome case-id expected)]
        (is (pos? (:proof-count result))
            (str case-id " should include proof evidence"))))
    (let [result (assert-outcome :fd-unknown-total-unresolved :unresolved)]
      (is (= :undefined-procedure-call (:classification result))))))

(deftest list-family-catalog-uses-raw-kernel-matrix-not-sidecar-settlement
  (doseq [case-id [:append-forward-flat-3
                  :append-inverse-flat
                  :reverse-input-flat-longer
                  :reverse-output-deep-nested-longer
                  :reverse-partial-output-longer-tail]]
    (let [result (assert-outcome case-id :target-found)]
      (is (:target-found? result))
      (is (or (pos? (get result :proof-count 0))
              (pos? (get result :closed-count 0)))
          (str case-id " should expose proof-backed ground or answer results")))))

(deftest fitting-catalog-exercises-forward-answer-and-partial-synthesis-modes
  (let [catalog (fitting/fitting-cases)
        by-id (into {} (map (juxt :id identity) catalog))]
    (testing "forward mode is represented by ground query proof cases"
      (is (= :query (get-in by-id [:p2-win-4-succeeds :kind])))
      (is (= :query (get-in by-id [:fd-color-red-succeeds :kind]))))
    (testing "answer synthesis is represented by inverse and open-list rows"
      (is (= :inverse-splits (get-in by-id [:append-inverse-flat :mode])))
      (is (= :input-synthesis
             (get-in by-id [:reverse-input-flat-longer :mode])))
      (is (= :output-synthesis
             (get-in by-id [:reverse-output-deep-nested-longer :mode]))))
    (testing "partial synthesis is represented by a constructor-constrained output row"
      (is (= :partial-output
             (get-in by-id [:reverse-partial-output-longer-tail :mode]))))))

(deftest group-verifier-cases-are-proof-kernel-results
  (testing "group-verifier associativity forms are proved or refuted by the profiled equality-fragment kernel layer"
    (doseq [[case-id expected]
            [[:gv-z1-full-assoc-succeeds :succeeds]
             [:gv-z2-precomputed-assoc-succeeds :succeeds]
             [:gv-z2-full-assoc-succeeds :succeeds]
             [:gv-non-group-precomputed-assoc-fails :fails]
             [:gv-non-group-full-assoc-fails :fails]]]
      (let [result (assert-outcome case-id expected)]
        (is (pos? (:proof-count result)))
        (is (some #{'profiled} (:proof-steps result)))
        (is (some #{'equality-fragment} (:proof-steps result)))))))

(deftest adr38-catalog-is-broad-and-does-not-reference-hard-overlay
  (let [catalog (fitting/fitting-cases)
        families (set (map :family catalog))]
    (is (every? #{:p1 :p2 :move-warning :finite-domain :list :group-verifier}
                families))
    (is (every? families
                [:p1 :p2 :move-warning :finite-domain :list :group-verifier]))
    (is (not (str/includes?
               (slurp "src/proflog/fitting_programs.clj")
               "hard-family-overlay"))))
  (testing "representative proofs use ordinary kernel or answer-overlay tags"
    (let [result (assert-outcome :p2-win-4-succeeds :succeeds)]
      (is (some #{'neg-call 'pos-call 'neg-call-guarded-alt
                  'pos-call-guarded-alt}
                (:proof-steps result)))
      (is (not (some #{'hard-family-overlay}
                     (:proof-steps result)))))))
