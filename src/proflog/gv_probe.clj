(ns proflog.gv-probe
  "Exploratory probes for the legacy group-verifier family.

   These probes rebuild the legacy `GV` programs in the greenfield AST and run
   them through the greenfield query API. The goal is not to disguise the
   answer layer, but to measure which semidecision surfaces can currently
   resolve the hard group-theory cases that legacy documented as intractable."
  (:require [clojure.pprint :as pp]
            [proflog.ast :as ast]
            [proflog.language :as language]
            [proflog.query :as query]))

(defn- gv-term
  [sym]
  (ast/app-term sym))

(defn- gv-and*
  [formulas]
  (reduce (fn [acc fml]
            (ast/and-form fml acc))
          (reverse formulas)))

(defn- gv-or*
  [formulas]
  (reduce (fn [acc fml]
            (ast/or-form fml acc))
          (reverse formulas)))

(defn- gv-forall*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/forall-form binding-nom acc))
          body
          (reverse noms)))

(defn- gv-exists*
  [noms body]
  (reduce (fn [acc binding-nom]
            (ast/exists-form binding-nom acc))
          body
          (reverse noms)))

(defn- gv-op-eq-inline
  [spec x y z]
  (gv-or*
    (for [[[a b] c] (:op spec)]
      (gv-and* [(ast/eq-lit x (gv-term a))
                (ast/eq-lit y (gv-term b))
                (ast/eq-lit z (gv-term c))]))))

(defn- gv-neg-op-eq-inline
  [spec x y z]
  (gv-and*
    (for [[[a b] c] (:op spec)]
      (gv-or* [(ast/neq-lit x (gv-term a))
               (ast/neq-lit y (gv-term b))
               (ast/neq-lit z (gv-term c))]))))

(defn- gv-in-domain-inline
  [spec x]
  (gv-or* (for [d (:domain spec)]
            (ast/eq-lit x (gv-term d)))))

(defn- gv-not-in-domain-inline
  [spec x]
  (gv-and* (for [d (:domain spec)]
             (ast/neq-lit x (gv-term d)))))

(defn- gv-language
  [spec relations]
  (language/language
    {:constants (:domain spec)
     :relations relations}))

(defn- gv-identity-program
  [spec x]
  (let [lang (gv-language spec {'gv_identity 0})
        e (gv-term (:identity spec))
        vx (ast/var-term x)]
    (language/compile-program
      lang
      [(ast/clause
         'gv_identity
         []
         (gv-forall*
           [x]
           (ast/or-form
             (gv-not-in-domain-inline spec vx)
             (ast/and-form
               (gv-op-eq-inline spec e vx vx)
               (gv-op-eq-inline spec vx e vx)))))])))

(defn- gv-closure-program
  [spec x y z]
  (let [lang (gv-language spec {'gv_closure 0})
        vx (ast/var-term x)
        vy (ast/var-term y)
        vz (ast/var-term z)]
    (language/compile-program
      lang
      [(ast/clause
         'gv_closure
         []
         (gv-forall*
           [x y]
           (gv-or* [(gv-not-in-domain-inline spec vx)
                    (gv-not-in-domain-inline spec vy)
                    (gv-exists*
                      [z]
                      (ast/and-form
                        (gv-op-eq-inline spec vx vy vz)
                        (gv-in-domain-inline spec vz)))])))])))

(defn- gv-inverses-program
  [spec x y]
  (let [lang (gv-language spec {'gv_inverses 0})
        e (gv-term (:identity spec))
        vx (ast/var-term x)
        vy (ast/var-term y)]
    (language/compile-program
      lang
      [(ast/clause
         'gv_inverses
         []
         (gv-forall*
           [x]
           (ast/or-form
             (gv-not-in-domain-inline spec vx)
             (gv-exists*
               [y]
               (gv-and* [(gv-in-domain-inline spec vy)
                         (gv-op-eq-inline spec vx vy e)
                         (gv-op-eq-inline spec vy vx e)])))))])))

(defn- gv-assoc-program
  [spec x y z w1 w2 w3 w4]
  (let [lang (gv-language spec {'gv_assoc 0})
        vx (ast/var-term x)
        vy (ast/var-term y)
        vz (ast/var-term z)
        vw1 (ast/var-term w1)
        vw2 (ast/var-term w2)
        vw3 (ast/var-term w3)
        vw4 (ast/var-term w4)]
    (language/compile-program
      lang
      [(ast/clause
         'gv_assoc
         []
         (gv-forall*
           [x y z w1 w2 w3 w4]
           (gv-or* [(gv-neg-op-eq-inline spec vx vy vw1)
                    (gv-neg-op-eq-inline spec vw1 vz vw2)
                    (gv-neg-op-eq-inline spec vy vz vw3)
                    (gv-neg-op-eq-inline spec vx vw3 vw4)
                    (ast/eq-lit vw2 vw4)])))])))

(defn- gv-assoc-precomputed-program
  [spec x y z]
  (let [lang (gv-language spec {'gv_assoc_pre 0})
        vx (ast/var-term x)
        vy (ast/var-term y)
        vz (ast/var-term z)
        op (:op spec)
        dom (:domain spec)
        triple-checks
        (for [a dom
              b dom
              c dom]
          (let [ab (get op [a b])
                ab-c (get op [ab c])
                bc (get op [b c])
                a-bc (get op [a bc])]
            (gv-or* [(ast/neq-lit vx (gv-term a))
                     (ast/neq-lit vy (gv-term b))
                     (ast/neq-lit vz (gv-term c))
                     (ast/eq-lit (gv-term ab-c) (gv-term a-bc))])))]
    (language/compile-program
      lang
      [(ast/clause
         'gv_assoc_pre
         []
         (gv-forall*
           [x y z]
           (gv-or* [(gv-not-in-domain-inline spec vx)
                    (gv-not-in-domain-inline spec vy)
                    (gv-not-in-domain-inline spec vz)
                    (gv-and* triple-checks)])))])))

(def gv-z2
  {:domain ['zero 'one]
   :op {['zero 'zero] 'zero
        ['zero 'one] 'one
        ['one 'zero] 'one
        ['one 'one] 'zero}
   :identity 'zero})

(def gv-z1
  {:domain ['e]
   :op {['e 'e] 'e}
   :identity 'e})

(def gv-non-group
  {:domain ['zero 'one]
   :op {['zero 'zero] 'zero
        ['zero 'one] 'one
        ['one 'zero] 'zero
        ['one 'one] 'zero}
   :identity 'zero})

(defn- timed
  [f]
  (let [started (System/nanoTime)
        value (f)]
    {:elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)
     :value value}))

(defn- succeeds-probe
  [program relation timeout-ms]
  (let [query-formula (ast/pos-lit (ast/app-term relation))
        succeeds (timed #(query/query-succeeds-within program query-formula 1 timeout-ms))]
    {:mode :succeeds
     :relation relation
     :timeout-ms timeout-ms
     :query-succeeds? (boolean (seq (:value succeeds)))
     :query-succeeds-elapsed-ms (:elapsed-ms succeeds)}))

(defn- fails-probe
  [program relation timeout-ms]
  (let [query-formula (ast/pos-lit (ast/app-term relation))
        fails (timed #(query/query-fails-within program query-formula 1 timeout-ms))]
    {:mode :fails
     :relation relation
     :timeout-ms timeout-ms
     :query-fails? (boolean (seq (:value fails)))
     :query-fails-elapsed-ms (:elapsed-ms fails)}))

(defn- status-probe
  [program relation timeout-ms]
  (let [query-formula (ast/pos-lit (ast/app-term relation))
        status (timed #(query/query-status program query-formula
                                           {:timeout-ms timeout-ms
                                            :proof-limit 1
                                            :poll-ms 5}))]
    {:mode :status
     :relation relation
     :timeout-ms timeout-ms
     :query-status (:value status)
     :query-status-elapsed-ms (:elapsed-ms status)}))

(defn scenario-config
  [scenario]
  (case scenario
    "z1-precomputed-assoc-truth"
    (ast/nom x y z
      {:scenario scenario
       :expected :succeeds
       :program (gv-assoc-precomputed-program gv-z1 x y z)
       :relation 'gv_assoc_pre})

    "z1-full-assoc-truth"
    (ast/nom x y z w1 w2 w3 w4
      {:scenario scenario
       :expected :succeeds
       :program (gv-assoc-program gv-z1 x y z w1 w2 w3 w4)
       :relation 'gv_assoc})

    "z2-precomputed-assoc-truth"
    (ast/nom x y z
      {:scenario scenario
       :expected :succeeds
       :program (gv-assoc-precomputed-program gv-z2 x y z)
       :relation 'gv_assoc_pre})

    "z2-full-assoc-truth"
    (ast/nom x y z w1 w2 w3 w4
      {:scenario scenario
       :expected :succeeds
       :program (gv-assoc-program gv-z2 x y z w1 w2 w3 w4)
       :relation 'gv_assoc})

    "non-group-full-assoc"
    (ast/nom x y z w1 w2 w3 w4
      {:scenario scenario
       :expected :fails
       :program (gv-assoc-program gv-non-group x y z w1 w2 w3 w4)
       :relation 'gv_assoc})

    "non-group-precomputed-assoc"
    (ast/nom x y z
      {:scenario scenario
       :expected :fails
       :program (gv-assoc-precomputed-program gv-non-group x y z)
       :relation 'gv_assoc_pre})

    "z2-identity"
    (ast/nom x
      {:scenario scenario
       :expected :succeeds
       :program (gv-identity-program gv-z2 x)
       :relation 'gv_identity})

    "z2-closure"
    (ast/nom x y z
      {:scenario scenario
       :expected :succeeds
       :program (gv-closure-program gv-z2 x y z)
       :relation 'gv_closure})

    "z2-inverses"
    (ast/nom x y
      {:scenario scenario
       :expected :succeeds
       :program (gv-inverses-program gv-z2 x y)
       :relation 'gv_inverses})

    (throw (ex-info "Unknown GV probe scenario"
                    {:scenario scenario
                     :supported ["z1-precomputed-assoc-truth"
                                 "z1-full-assoc-truth"
                                 "z2-precomputed-assoc-truth"
                                 "z2-full-assoc-truth"
                                 "non-group-full-assoc"
                                 "non-group-precomputed-assoc"
                                 "z2-identity"
                                 "z2-closure"
                                 "z2-inverses"]}))))

(defn- run-scenario!
  [scenario mode timeout-ms]
  (let [{:keys [program relation expected] :as config} (scenario-config scenario)
        result (case mode
                 :succeeds (succeeds-probe program relation timeout-ms)
                 :fails (fails-probe program relation timeout-ms)
                 :status (status-probe program relation timeout-ms)
                 :all (merge (succeeds-probe program relation timeout-ms)
                             (fails-probe program relation timeout-ms)
                             (status-probe program relation timeout-ms)))]
    (pp/pprint (merge (dissoc config :program)
                      result))
    (flush)))

(defn -main
  "Run one or more GV probe scenarios.

   Usage:
     lein run -m proflog.gv-probe z2-full-assoc-truth status 15000
     lein run -m proflog.gv-probe all status 15000"
  [& [scenario mode-text timeout-text]]
  (let [mode (keyword (or mode-text "status"))
        timeout-ms (Long/parseLong (or timeout-text "15000"))
        scenarios (if (or (nil? scenario) (= "all" scenario))
                    ["z1-precomputed-assoc-truth"
                     "z1-full-assoc-truth"
                     "z2-precomputed-assoc-truth"
                     "z2-full-assoc-truth"
                     "non-group-full-assoc"
                     "non-group-precomputed-assoc"]
                    [scenario])]
    (doseq [one-scenario scenarios]
      (run-scenario! one-scenario mode timeout-ms))
    (shutdown-agents)))
