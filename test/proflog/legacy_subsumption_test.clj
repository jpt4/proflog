(ns proflog.legacy-subsumption-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.fitting-programs :as fitting]
            [proflog.gv-probe :as gv-probe]
            [proflog.kernel.constructor-recursive-profile :as constructor-profile]
            [proflog.language :as language]
            [proflog.pretty :as pretty]
            [proflog.proof :as proof]
            [proflog.query :as query]))

(defn app
  [sym & args]
  (apply ast/app-term sym args))

(defn and*
  [formulas]
  (case (count formulas)
    0 (ast/true-form)
    1 (first formulas)
    (reduce ast/and-form formulas)))

(defn or*
  [formulas]
  (case (count formulas)
    0 (ast/false-form)
    1 (first formulas)
    (reduce ast/or-form formulas)))

(defn forall*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/forall-form binding-nom acc))
          body
          (reverse noms)))

(defn exists*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/exists-form binding-nom acc))
          body
          (reverse noms)))

(defn numeral
  [n]
  (if (zero? n)
    (app 'zero)
    (app 's (numeral (dec n)))))

(defn query-for
  [relation]
  (ast/pos-lit (app relation)))

(defn timed-row
  [label f]
  (println "ADR-40 row start:" label)
  (flush)
  (let [started (System/nanoTime)
        value (f)
        elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
    (println "ADR-40 row pass:" label (format "%.3f ms" elapsed-ms))
    (flush)
    value))

(defn assert-profiled-equality-success
  [label program relation]
  (timed-row
    label
    #(let [query (query-for relation)
           status (query/query-status program query
                                      {:timeout-ms 20000
                                       :proof-limit 1
                                       :poll-ms 0})
           proofs (query/query-succeeds program query 1 32)
           proof (first proofs)]
       (is (= :succeeds status) label)
       (is proof (str label " should produce proof evidence"))
       (is (proof/contains-step? proof 'profiled)
           (str label " should use a profiled proof layer"))
       (is (proof/contains-step? proof 'equality-fragment)
           (str label " should use the equality-fragment layer")))))

(defn assert-profiled-equality-success-proof
  [label program relation]
  (timed-row
    label
    #(let [query (query-for relation)
           proofs (query/query-succeeds program query 1 32)
           proof (first proofs)]
       (is proof (str label " should produce proof evidence"))
       (is (proof/contains-step? proof 'profiled)
           (str label " should use a profiled proof layer"))
       (is (proof/contains-step? proof 'equality-fragment)
           (str label " should use the equality-fragment layer")))))

(defn assert-profiled-equality-unresolved
  [label program relation]
  (timed-row
    label
    #(let [status (query/query-status program
                                      (query-for relation)
                                      {:timeout-ms 2000
                                       :proof-limit 1
                                       :poll-ms 0})]
       (is (= :unresolved status) label))))

;; ---------------------------------------------------------------------------
;; Group verifier helpers
;; ---------------------------------------------------------------------------

(def z3-spec
  {:domain ['z0 'z1 'z2]
   :op {['z0 'z0] 'z0
        ['z0 'z1] 'z1
        ['z0 'z2] 'z2
        ['z1 'z0] 'z1
        ['z1 'z1] 'z2
        ['z1 'z2] 'z0
        ['z2 'z0] 'z2
        ['z2 'z1] 'z0
        ['z2 'z2] 'z1}
   :identity 'z0})

(defn gv-term
  [sym]
  (app sym))

(defn gv-op-inline
  [spec x y z]
  (or*
    (for [[[a b] c] (:op spec)]
      (and* [(ast/eq-lit x (gv-term a))
             (ast/eq-lit y (gv-term b))
             (ast/eq-lit z (gv-term c))]))))

(defn gv-in-domain-inline
  [spec x]
  (or* (for [d (:domain spec)]
         (ast/eq-lit x (gv-term d)))))

(defn gv-not-in-domain-inline
  [spec x]
  (and* (for [d (:domain spec)]
          (ast/neq-lit x (gv-term d)))))

(defn gv-language
  [spec relations]
  (language/language
    {:constants (:domain spec)
     :relations relations}))

(defn gv-identity-program
  [spec]
  (ast/nom x
    (let [vx (ast/var-term x)
          e (gv-term (:identity spec))]
      (language/compile-program
        (gv-language spec {'gv-identity 0})
        [(ast/clause 'gv-identity
                     []
                     (forall*
                       [x]
                       (or*
                         [(gv-not-in-domain-inline spec vx)
                          (and*
                            [(gv-op-inline spec e vx vx)
                             (gv-op-inline spec vx e vx)])])))]))))

(defn gv-closure-program
  [spec]
  (ast/nom x y z
    (let [vx (ast/var-term x)
          vy (ast/var-term y)
          vz (ast/var-term z)]
      (language/compile-program
        (gv-language spec {'gv-closure 0})
        [(ast/clause 'gv-closure
                     []
                     (forall*
                       [x y]
                       (or*
                         [(gv-not-in-domain-inline spec vx)
                          (gv-not-in-domain-inline spec vy)
                          (exists*
                            [z]
                            (and*
                              [(gv-op-inline spec vx vy vz)
                               (gv-in-domain-inline spec vz)]))])))]))))

(defn gv-inverses-program
  [spec]
  (ast/nom x y
    (let [vx (ast/var-term x)
          vy (ast/var-term y)
          e (gv-term (:identity spec))]
      (language/compile-program
        (gv-language spec {'gv-inverses 0})
        [(ast/clause 'gv-inverses
                     []
                     (forall*
                       [x]
                       (or*
                         [(gv-not-in-domain-inline spec vx)
                          (exists*
                            [y]
                            (and*
                              [(gv-in-domain-inline spec vy)
                               (gv-op-inline spec vx vy e)
                               (gv-op-inline spec vy vx e)]))])))]))))

(deftest legacy-gv-identity-closure-and-inverses-have-extended-greenfield-rows
  (testing "legacy GV01-GV03 parity rows close through greenfield"
    (doseq [scenario ["z2-identity" "z2-closure" "z2-inverses"]]
      (let [{:keys [program relation]} (gv-probe/scenario-config scenario)]
        (assert-profiled-equality-success scenario program relation))))
  (testing "larger Z3 rows close through the same equality-fragment profile"
    (assert-profiled-equality-success "z3 identity"
                                      (gv-identity-program z3-spec)
                                      'gv-identity)
    (assert-profiled-equality-success "z3 closure"
                                      (gv-closure-program z3-spec)
                                      'gv-closure)
    (assert-profiled-equality-success "z3 inverses"
                                      (gv-inverses-program z3-spec)
                                      'gv-inverses)))

;; ---------------------------------------------------------------------------
;; Finite-domain helpers
;; ---------------------------------------------------------------------------

(def finite-total-language
  (language/language
    {:constants ['red 'green 'blue]
     :relations {'total 0}}))

(def extended-fd-language
  (language/language
    {:constants ['red 'orange 'green 'blue 'yellow]
     :relations {'extended-disjoint 0
                 'extended-total 0}}))

(defn total-program
  []
  (ast/nom x
    (let [vx (ast/var-term x)]
      (language/compile-program
        finite-total-language
        [(ast/clause 'total
                     []
                     (forall*
                       [x]
                       (or*
                         [(ast/eq-lit vx (app 'red))
                          (ast/eq-lit vx (app 'green))
                          (ast/eq-lit vx (app 'blue))])))]))))

(defn extended-fd-program
  []
  (ast/nom x
    (let [vx (ast/var-term x)]
      (language/compile-program
        extended-fd-language
        [(ast/clause 'extended-disjoint
                     []
                     (forall*
                       [x]
                       (or*
                         [(and*
                            [(ast/neq-lit vx (app 'red))
                             (ast/neq-lit vx (app 'orange))])
                          (and*
                            [(ast/neq-lit vx (app 'green))
                             (ast/neq-lit vx (app 'blue))])])))
         (ast/clause 'extended-total
                     []
                     (forall*
                       [x]
                       (or*
                         [(ast/eq-lit vx (app 'red))
                          (ast/eq-lit vx (app 'orange))
                          (ast/eq-lit vx (app 'green))
                          (ast/eq-lit vx (app 'blue))
                          (ast/eq-lit vx (app 'yellow))])))]))))

(deftest finite-domain-disjointness-and-totality-have-extended-greenfield-rows
  (testing "legacy FD05 disjointness is covered by the greenfield finite-domain program"
    ;; FD05 is proof-backed. ADR-0042 also corrected the earlier bounded
    ;; two-sided :inconsistent status by preserving one shared universal witness
    ;; requirement across equality-fragment split branches.
    (assert-profiled-equality-success-proof "warm/cool disjoint"
                                            (fitting/finite-domain-program)
                                            'warm-cool-disjoint))
  (testing "larger two-by-two category disjointness closes through the same profile"
    (assert-profiled-equality-success "extended finite-domain disjointness"
                                      (extended-fd-program)
                                      'extended-disjoint))
  (testing "legacy FD06 totality remains unresolved rather than collapsed into false"
    (assert-profiled-equality-unresolved "finite totality is undefined"
                                         (total-program)
                                         'total))
  (testing "larger finite totality remains unresolved as well"
    (assert-profiled-equality-unresolved "extended finite totality is undefined"
                                         (extended-fd-program)
                                         'extended-total)))

;; ---------------------------------------------------------------------------
;; Peano arithmetic synthesis helpers
;; ---------------------------------------------------------------------------

(def arithmetic-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'plus 3}}))

(defn plus-program
  []
  (ast/nom x y z y1 z1
    (language/compile-program
      arithmetic-language
      [(ast/clause 'plus
                   [x y z]
                   (or*
                     [(and*
                        [(ast/eq-lit (ast/var-term y) (app 'zero))
                         (ast/eq-lit (ast/var-term z) (ast/var-term x))])
                      (exists*
                        [y1 z1]
                        (and*
                          [(ast/eq-lit (ast/var-term y)
                                       (app 's (ast/var-term y1)))
                           (ast/eq-lit (ast/var-term z)
                                       (app 's (ast/var-term z1)))
                           (ast/pos-lit
                             (app 'plus
                                  (ast/var-term x)
                                  (ast/var-term y1)
                                  (ast/var-term z1)))]))]))])))

(defn plus-query
  [x y z]
  (ast/pos-lit (app 'plus (numeral x) (numeral y) (numeral z))))

(defn binding-term
  [record binding-nom]
  (some (fn [[nom term]]
          (when (= nom binding-nom)
            term))
        (:bindings record)))

(defn answer-tuple
  [record answer-vars]
  (mapv #(pretty/peano->int (binding-term record %))
        answer-vars))

(defn constructor-recursive-proofs?
  [records]
  (every? (fn [record]
            (some #(and (proof/contains-step? % 'profiled)
                        (proof/contains-step? % 'constructor-recursive)
                        (proof/contains-step? % 'structural-residual-continuation)
                        (not (proof/contains-step? % 'constructor-recursive-call)))
                  (:proofs record)))
          records))

(defn assert-constructor-recursive-answers
  [label program query answer-vars expected opts]
  (timed-row
    label
    #(let [records (constructor-profile/query-records
                     program
                     query
                     answer-vars
                     opts)
           actual (mapv answer-tuple records (repeat answer-vars))]
       (is (= expected actual)
           (str label " should return the expected bounded answer stream"))
       (is (constructor-recursive-proofs? records)
           (str label " should be proved by the constructor-recursive profile")))))

(defn assert-constructor-recursive-answer-set
  [label program query answer-vars expected opts]
  (timed-row
    label
    #(let [records (constructor-profile/query-records
                     program
                     query
                     answer-vars
                     opts)
           actual (set (map (fn [record]
                              (answer-tuple record answer-vars))
                            records))]
       (is (= (set expected) actual)
           (str label " should return the expected answer set"))
       (is (constructor-recursive-proofs? records)
           (str label " should be proved by the constructor-recursive profile")))))

(defn assert-constructor-recursive-answer-set-contains
  [label program query answer-vars expected opts]
  (timed-row
    label
    #(let [records (constructor-profile/query-records
                     program
                     query
                     answer-vars
                     opts)
           actual (set (map (fn [record]
                              (answer-tuple record answer-vars))
                            records))]
       (is (set/subset? (set expected) actual)
           (str label " should include the expected bounded answer subset"))
       (is (constructor-recursive-proofs? records)
           (str label " should be proved by the constructor-recursive profile")))))

(defn assert-constructor-recursive-no-answers
  [label program query answer-vars opts]
  (timed-row
    label
    #(let [records (constructor-profile/query-records
                     program
                     query
                     answer-vars
                     opts)]
       (is (empty? records)
           (str label " should produce no constructor-recursive answers")))))

(defn assert-plus-success
  [label program x y z fuel]
  (timed-row
    label
    #(let [proofs (query/query-succeeds program
                                       (plus-query x y z)
                                       1
                                       fuel)
           proof (first proofs)]
       (is proof (str label " should have a proof-backed greenfield success"))
       (is (or (proof/contains-step? proof 'neg-call)
               (proof/contains-step? proof 'neg-call-guarded-alt))
           (str label " should exercise the Proflog procedure-call rule")))))

(deftest legacy-peano-answer-and-partial-synthesis-have-extended-rows
  (let [program (plus-program)]
    (testing "forward Peano rows use the ordinary greenfield proof kernel"
      (assert-plus-success "PA10 forward 3 + 4 = 7" program 3 4 7 256)
      (assert-plus-success "PA11 / extended forward 4 + 3 = 7" program 4 3 7 128))
    (ast/nom x y z
      (testing "PA12 first-argument synthesis and a deeper first-argument row"
        (assert-constructor-recursive-answers
          "PA12 ? + 3 = 5 gives 2"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (numeral 3) (numeral 5)))
          [x]
          [[2]]
          {:fuel 24
           :limit 2})
        (assert-constructor-recursive-answers
          "extended ? + 3 = 6 gives 3"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (numeral 3) (numeral 6)))
          [x]
          [[3]]
          {:fuel 24
           :limit 2}))
      (testing "PA13 and PA15 second-argument / existential-addend synthesis with an extension"
        (assert-constructor-recursive-answers
          "PA13/PA15 3 + ? = 5 gives 2"
          program
          (ast/pos-lit (app 'plus (numeral 3) (ast/var-term y) (numeral 5)))
          [y]
          [[2]]
          {:fuel 24
           :limit 2})
        (assert-constructor-recursive-answers
          "extended 3 + ? = 6 gives 3"
          program
          (ast/pos-lit (app 'plus (numeral 3) (ast/var-term y) (numeral 6)))
          [y]
          [[3]]
          {:fuel 24
           :limit 2}))
      (testing "PA14 sum synthesis and a deeper sum row"
        (assert-constructor-recursive-answers
          "PA14 3 + 4 = ? gives 7"
          program
          (ast/pos-lit (app 'plus (numeral 3) (numeral 4) (ast/var-term z)))
          [z]
          [[7]]
          {:fuel 24
           :limit 2})
        (assert-constructor-recursive-answers
          "extended 4 + 3 = ? gives 7"
          program
          (ast/pos-lit (app 'plus (numeral 4) (numeral 3) (ast/var-term z)))
          [z]
          [[7]]
          {:fuel 24
           :limit 2}))
      (testing "PA16 halving and a larger halving row"
        (assert-constructor-recursive-answers
          "PA16 x + x = 4 gives 2"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 4)))
          [x]
          [[2]]
          {:fuel 24
           :limit 2})
        (assert-constructor-recursive-answers
          "extended x + x = 6 gives 3"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 6)))
          [x]
          [[3]]
          {:fuel 24
           :limit 2}))
      (testing "PA17 and PA18 odd non-halving rows plus an extended odd row"
        (assert-constructor-recursive-no-answers
          "PA17 x + x = 3 has no answer"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 3)))
          [x]
          {:fuel 24
           :limit 2})
        (assert-constructor-recursive-no-answers
          "PA18 / extended x + x = 5 has no answer"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term x) (numeral 5)))
          [x]
          {:fuel 32
           :limit 2}))
      (testing "PA19 all pairs summing to 3 and a larger sum-4 row"
        (assert-constructor-recursive-answer-set
          "PA19 all pairs summing to 3"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term y) (numeral 3)))
          [x y]
          [[0 3] [1 2] [2 1] [3 0]]
          {:fuel 24
           :limit 4})
        (assert-constructor-recursive-answer-set
          "extended all pairs summing to 4"
          program
          (ast/pos-lit (app 'plus (ast/var-term x) (ast/var-term y) (numeral 4)))
          [x y]
          [[0 4] [1 3] [2 2] [3 1] [4 0]]
          {:fuel 24
           :limit 5}))
      (testing "PA20 fixed-addend pair stream and an extended fixed-addend row"
        (assert-constructor-recursive-answer-set-contains
          "PA20 fixed addend 2 stream includes legacy low pairs"
          program
          (ast/pos-lit (app 'plus (numeral 2) (ast/var-term y) (ast/var-term z)))
          [y z]
          [[0 2] [1 3] [2 4] [3 5]]
          {:fuel 24
           :limit 24})
        (assert-constructor-recursive-answer-set-contains
          "extended fixed addend 3 stream includes deeper low pairs"
          program
          (ast/pos-lit (app 'plus (numeral 3) (ast/var-term y) (ast/var-term z)))
          [y z]
          [[0 3] [1 4] [2 5] [3 6]]
          {:fuel 24
           :limit 24})))))
