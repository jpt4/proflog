(ns proflog.fitting-programs
  "ADR-38 catalog for evaluating Fitting-style Proflog programs.

   This namespace builds source programs with the public AST/language layer and
   evaluates them through the ordinary proof-kernel-backed query surfaces. It
   does not use the hard-family overlay or any host-side semantic evaluator."
  (:require [proflog.ast :as ast]
            [clojure.pprint :as pp]
            [proflog.gv-probe :as gv-probe]
            [proflog.language :as language]
            [proflog.list-kernel-matrix-probe :as list-matrix]
            [proflog.proof :as proof]
            [proflog.query :as query]))

(defn app
  [sym & args]
  (apply ast/app-term sym args))

(defn numeral
  [n]
  (if (zero? n)
    (app 'zero)
    (app 's (numeral (dec n)))))

(defn- and*
  [formulas]
  (case (count formulas)
    0 (ast/true-form)
    1 (first formulas)
    (reduce ast/and-form formulas)))

(defn- or*
  [formulas]
  (case (count formulas)
    0 (ast/false-form)
    1 (first formulas)
    (reduce ast/or-form formulas)))

(defn- forall*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/forall-form binding-nom acc))
          body
          (reverse noms)))

(defn- exists*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/exists-form binding-nom acc))
          body
          (reverse noms)))

(def peano-language
  (language/language
    {:constants ['zero]
     :functions {'s 1}
     :relations {'even 1
                 'odd 1
                 'move 2
                 'win 1}}))

(def finite-domain-language
  (language/language
    {:constants ['red 'green 'blue 'yellow]
     :relations {'color 1
                 'warm 1
                 'cool 1
                 'warm-unique 0
                 'warm-cool-disjoint 0
                 'unknown-total 0
                 'mystery 1}}))

(defn p1-program
  "Fitting P1 with the original forall-based odd clause."
  []
  (ast/nom x y
    (language/compile-program
      peano-language
      [(ast/clause 'even [x]
                   (or*
                     [(ast/eq-lit (ast/var-term x) (app 'zero))
                      (exists*
                        [y]
                        (and*
                          [(ast/eq-lit (ast/var-term x)
                                       (app 's (ast/var-term y)))
                           (ast/pos-lit
                             (app 'odd (ast/var-term y)))]))]))
       (ast/clause 'odd [x]
                   (ast/forall-form
                     y
                     (ast/implies-form
                       (ast/pos-lit (app 'even (ast/var-term y)))
                       (ast/neq-lit (ast/var-term x)
                                    (ast/var-term y)))))])))

(defn p2-program
  "Fitting P2 Nim program with the move relation inlined in the win clause."
  []
  (ast/nom x y
    (language/compile-program
      peano-language
      [(ast/clause 'win [x]
                   (exists*
                     [y]
                     (and*
                       [(or*
                          [(ast/eq-lit (ast/var-term x)
                                       (app 's (ast/var-term y)))
                           (ast/eq-lit (ast/var-term x)
                                       (app 's
                                            (app 's (ast/var-term y))))])
                        (ast/neg-lit (app 'win (ast/var-term y)))])))])))

(defn factored-move-program
  "The invalid auxiliary-relation factoring from Fitting's move warning."
  []
  (ast/nom x y mx my
    (language/compile-program
      peano-language
      [(ast/clause 'win [x]
                   (exists*
                     [y]
                     (and*
                       [(ast/pos-lit
                          (app 'move
                               (ast/var-term x)
                               (ast/var-term y)))
                        (ast/neg-lit
                          (app 'win (ast/var-term y)))])))
       (ast/clause 'move [mx my]
                   (or*
                     [(ast/eq-lit (ast/var-term mx)
                                  (app 's (ast/var-term my)))
                      (ast/eq-lit (ast/var-term mx)
                                  (app 's
                                       (app 's (ast/var-term my))))]))])))

(defn finite-domain-program
  "Finite-domain-style examples encoded as ordinary Proflog clauses."
  []
  (ast/nom x y
    (let [vx (ast/var-term x)
          vy (ast/var-term y)]
      (language/compile-program
        finite-domain-language
        [(ast/clause 'color [x]
                     (or*
                       [(ast/eq-lit vx (app 'red))
                        (ast/eq-lit vx (app 'green))
                        (ast/eq-lit vx (app 'blue))]))
         (ast/clause 'warm [x]
                     (ast/eq-lit vx (app 'red)))
         (ast/clause 'cool [x]
                     (or*
                       [(ast/eq-lit vx (app 'green))
                        (ast/eq-lit vx (app 'blue))]))
         (ast/clause 'warm-cool-disjoint []
                     (forall*
                       [x]
                       (or*
                         [(ast/neq-lit vx (app 'red))
                          (and*
                            [(ast/neq-lit vx (app 'green))
                             (ast/neq-lit vx (app 'blue))])])))
         (ast/clause 'warm-unique []
                     (forall*
                       [x y]
                       (or*
                         [(ast/neq-lit vx (app 'red))
                          (ast/neq-lit vy (app 'red))
                          (ast/eq-lit vx vy)])))
         (ast/clause 'unknown-total []
                     (forall*
                       [x]
                       (ast/pos-lit (app 'mystery vx))))]))))

(defn query-case
  [{:keys [id family description program query expected fuel proof-limit
           timeout-ms poll-ms classification]}]
  {:id id
   :kind :query
   :family family
   :description description
   :program program
   :query query
   :expected expected
   :fuel fuel
   :proof-limit (or proof-limit 1)
   :timeout-ms timeout-ms
   :poll-ms poll-ms
   :classification classification})

(defn list-case
  [case-id]
  (let [{:keys [operation mode shape size description]} (list-matrix/case-config case-id)]
    {:id case-id
     :kind :list-matrix
     :family :list
     :description description
     :operation operation
     :mode mode
     :shape shape
     :size size
     :expected :target-found}))

(defn gv-case
  [{:keys [id scenario expected timeout-ms poll-ms classification]}]
  (let [{:keys [program relation]} (gv-probe/scenario-config scenario)]
    {:id id
     :kind :query
     :family :group-verifier
     :description scenario
     :program program
     :query (ast/pos-lit (app relation))
     :expected expected
     :proof-limit 1
     :timeout-ms timeout-ms
     :poll-ms poll-ms
     :classification classification}))

(defn fitting-cases
  []
  (let [p1 (p1-program)
        p2 (p2-program)
        factored (factored-move-program)
        fd (finite-domain-program)]
    [(query-case
       {:id :p1-even-0-succeeds
        :family :p1
        :description "P1 proves even(0)"
        :program p1
        :query (ast/pos-lit (app 'even (numeral 0)))
        :expected :succeeds
        :fuel 8})
     (query-case
       {:id :p1-odd-1-succeeds
        :family :p1
        :description "P1 proves odd(s(0)) through the forall-based odd clause"
        :program p1
        :query (ast/pos-lit (app 'odd (numeral 1)))
        :expected :succeeds
        :fuel 16})
     (query-case
       {:id :p1-odd-0-fails
        :family :p1
        :description "P1 refutes odd(0)"
        :program p1
        :query (ast/pos-lit (app 'odd (numeral 0)))
        :expected :fails
        :fuel 8})
     (query-case
       {:id :p2-win-4-succeeds
        :family :p2
        :description "P2 proves win(4)"
        :program p2
        :query (ast/pos-lit (app 'win (numeral 4)))
        :expected :succeeds
        :fuel 64})
     (query-case
       {:id :p2-win-3-fails
        :family :p2
        :description "P2 refutes win(3)"
        :program p2
        :query (ast/pos-lit (app 'win (numeral 3)))
        :expected :fails
        :fuel 64})
     (query-case
       {:id :move-1-to-0-succeeds
        :family :move-warning
        :description "factored move(1, 0) is locally true"
        :program factored
        :query (ast/pos-lit (app 'move (numeral 1) (numeral 0)))
        :expected :succeeds
        :fuel 16})
     (query-case
       {:id :move-0-to-1-fails
        :family :move-warning
        :description "factored move(0, 1) is locally false"
        :program factored
        :query (ast/pos-lit (app 'move (numeral 0) (numeral 1)))
        :expected :fails
        :fuel 16})
     (query-case
       {:id :factored-win-1-unresolved
        :family :move-warning
        :description "factored win(1) remains unresolved"
        :program factored
        :query (ast/pos-lit (app 'win (numeral 1)))
        :expected :unresolved
        :timeout-ms 1000
        :poll-ms 0
        :classification :invalid-auxiliary-relation-factoring})
     (query-case
       {:id :fd-color-red-succeeds
        :family :finite-domain
        :description "color(red) is true"
        :program fd
        :query (ast/pos-lit (app 'color (app 'red)))
        :expected :succeeds
        :fuel 16})
     (query-case
       {:id :fd-color-yellow-fails
        :family :finite-domain
        :description "color(yellow) is false by free-constructor closure"
        :program fd
        :query (ast/pos-lit (app 'color (app 'yellow)))
        :expected :fails
        :fuel 16})
     (query-case
       {:id :fd-warm-blue-fails
        :family :finite-domain
        :description "warm(blue) is false"
        :program fd
        :query (ast/pos-lit (app 'warm (app 'blue)))
        :expected :fails
        :fuel 16})
     (query-case
       {:id :fd-cool-green-succeeds
        :family :finite-domain
        :description "cool(green) is true"
        :program fd
        :query (ast/pos-lit (app 'cool (app 'green)))
        :expected :succeeds
        :fuel 16})
     (query-case
       {:id :fd-warm-unique-succeeds
        :family :finite-domain
        :description "at most one closed value is warm"
        :program fd
        :query (ast/pos-lit (app 'warm-unique))
        :expected :succeeds
        :fuel 64})
     (query-case
       {:id :fd-unknown-total-unresolved
        :family :finite-domain
        :description "undefined relation in a universal body stays unresolved"
        :program fd
        :query (ast/pos-lit (app 'unknown-total))
        :expected :unresolved
        :timeout-ms 1000
        :poll-ms 0
        :classification :undefined-procedure-call})
     (list-case :append-forward-flat-3)
     (list-case :append-inverse-flat)
     (list-case :reverse-input-flat-longer)
     (list-case :reverse-output-deep-nested-longer)
     (list-case :reverse-partial-output-longer-tail)
     (gv-case
       {:id :gv-z1-full-assoc-succeeds
        :scenario "z1-full-assoc-truth"
        :expected :succeeds})
     (gv-case
       {:id :gv-z2-precomputed-assoc-succeeds
        :scenario "z2-precomputed-assoc-truth"
        :expected :succeeds})
     (gv-case
       {:id :gv-z2-full-assoc-succeeds
        :scenario "z2-full-assoc-truth"
        :expected :succeeds})
     (gv-case
       {:id :gv-non-group-precomputed-assoc-fails
        :scenario "non-group-precomputed-assoc"
        :expected :fails})
     (gv-case
       {:id :gv-non-group-full-assoc-fails
        :scenario "non-group-full-assoc"
        :expected :fails})]))

(defn case-by-id
  [case-id]
  (or (some #(when (= case-id (:id %)) %)
            (fitting-cases))
      (throw (ex-info "Unknown ADR-38 Fitting case"
                      {:case-id case-id
                       :known-case-ids (mapv :id (fitting-cases))}))))

(defn- proof-summary
  [proofs]
  {:proof-count (count proofs)
   :proof-root (some-> proofs first first)
   :proof-steps (some-> proofs first proof/collect-steps vec)})

(defn- evaluate-query
  [{:keys [program query expected fuel proof-limit timeout-ms poll-ms
           classification]}]
  (case expected
    :succeeds
    (let [proofs (doall (query/query-succeeds program query proof-limit fuel))]
      (merge {:outcome (if (seq proofs) :succeeds :unresolved)}
             (proof-summary proofs)))

    :fails
    (let [proofs (doall (query/query-fails program query proof-limit fuel))]
      (merge {:outcome (if (seq proofs) :fails :unresolved)}
             (proof-summary proofs)))

    :unresolved
    {:outcome (query/query-status
                program
                query
                {:timeout-ms (or timeout-ms 250)
                 :proof-limit proof-limit
                 :poll-ms (or poll-ms 5)})
     :classification classification}

    (throw (ex-info "Unsupported query expectation"
                    {:expected expected}))))

(defn- evaluate-list-matrix
  [{:keys [id]}]
  (let [result (list-matrix/run-case id)]
    (assoc result
           :outcome (if (:target-found? result)
                      :target-found
                      :target-missing))))

(defn evaluate-case
  [case-spec]
  (let [case-spec (if (map? case-spec) case-spec (case-by-id case-spec))
        result (clojure.core/case (:kind case-spec)
                 :query (evaluate-query case-spec)
                 :list-matrix (evaluate-list-matrix case-spec))]
    (merge (select-keys case-spec
                        [:id :kind :family :description :expected
                         :classification])
           result)))

(defn evaluate-catalog
  []
  (mapv evaluate-case (fitting-cases)))

(defn -main
  [& case-ids]
  (let [ids (map keyword case-ids)
        results (if (seq ids)
                  (mapv evaluate-case ids)
                  (evaluate-catalog))]
    (pp/pprint results)
    (shutdown-agents)))
