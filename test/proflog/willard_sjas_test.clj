(ns proflog.willard-sjas-test
  (:require [clojure.core.logic :as l]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.gamma :as gamma]
            [proflog.kernel :as kernel]
            [proflog.kernel.willard-sjas-profile :as sjas-profile]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.proof :as proof]
            [proflog.query :as query]
            [proflog.sjas-correspondence :as correspondence]
            [proflog.willard-sjas :as sjas]
            [proflog.willard-sjas-code :as sjas-code]))

(defn- successful?
  [proofs]
  (boolean (seq proofs)))

(defn- first-proof
  [proofs]
  (first proofs))

(defn- proof-symbol-audit
  [proof]
  (correspondence/audit-proof-term proof))

(defn- n
  [value]
  (sjas/numeral value))

(defn- sjas-numeral-term?
  "True when `term` is written in the public binary SJAS numeral vocabulary."
  [term]
  (and (= 'app (ast/tag-of term))
       (let [head (second term)
             args (nnext term)]
         (cond
           (= (symbol "0") head) (empty? args)
           (= (symbol "1") head) (empty? args)
           (= 'dbl head) (and (= 1 (count args))
                              (sjas-numeral-term? (first args)))
           (= 'add head) (and (= 2 (count args))
                              (sjas-numeral-term? (first args))
                              (sjas-numeral-term? (second args)))
           :else false))))

(defn- generated-code-symbol?
  [sym]
  (and (symbol? sym)
       (or (str/starts-with? (name sym) "sjas_formula_")
           (str/starts-with? (name sym) "sjas_system_"))))

(defn- code-constructor-symbol?
  [sym]
  (boolean (sjas-code/code-symbol-byte-count sym)))

(defn- binding-for
  [records nom]
  (some (fn [record]
          (some (fn [[record-nom value]]
                  (when (= nom record-nom)
                    value))
                (:bindings record)))
        records))

(defn- subst-code-relation-succeeds?
  [system source-code target-code]
  (successful?
    (l/run 1 [q]
      (l/fresh [sigma-out]
        ((var-get #'sjas-profile/sjas-subst-code-anyo)
         (:program system)
         source-code
         target-code
         '()
         sigma-out)
        (l/== true q)))))

(defn- formula-relation-symbols
  "Collect relation symbols appearing in atomic literals of an SJAS formula."
  [formula]
  (case (ast/tag-of formula)
    pos [(second (second formula))]
    neg [(second (second formula))]
    eq []
    neq []
    true []
    false []
    and (concat (formula-relation-symbols (second formula))
                (formula-relation-symbols (nth formula 2)))
    or (concat (formula-relation-symbols (second formula))
               (formula-relation-symbols (nth formula 2)))
    not (formula-relation-symbols (second formula))
    implies (concat (formula-relation-symbols (second formula))
                    (formula-relation-symbols (nth formula 2)))
    forall (formula-relation-symbols (:body (second formula)))
    once-forall (formula-relation-symbols (:body (second formula)))
    exists (formula-relation-symbols (:body (second formula)))
    bounded-forall (formula-relation-symbols (get-in (second formula) [:body :body]))
    bounded-exists (formula-relation-symbols (get-in (second formula) [:body :body]))
    []))

(defn- formula-atoms
  "Collect atomic application terms appearing in an SJAS formula."
  [formula]
  (case (ast/tag-of formula)
    pos [(second formula)]
    neg [(second formula)]
    eq []
    neq []
    true []
    false []
    and (concat (formula-atoms (second formula))
                (formula-atoms (nth formula 2)))
    or (concat (formula-atoms (second formula))
               (formula-atoms (nth formula 2)))
    not (formula-atoms (second formula))
    implies (concat (formula-atoms (second formula))
                    (formula-atoms (nth formula 2)))
    forall (formula-atoms (:body (second formula)))
    once-forall (formula-atoms (:body (second formula)))
    exists (formula-atoms (:body (second formula)))
    bounded-forall (formula-atoms (get-in (second formula) [:body :body]))
    bounded-exists (formula-atoms (get-in (second formula) [:body :body]))
    []))

(deftest sjas-profile-languages-have-binary-u-grounding-shape
  (testing "SJAS languages expose Willard-style binary U-grounding symbols"
    (doseq [[profile lang] [[:willard-sjas-tableau0 sjas/tableau0-profile-language]
                            [:willard-sjas-level1 sjas/level1-profile-language]]]
      (is (= profile (:proof-profile lang)))
      (is (contains? (:constants lang) (symbol "0")))
      (is (contains? (:constants lang) (symbol "1")))
      (is (not (contains? (:constants lang) 'zero)))
      (is (not (contains? (:constants lang) 'one)))
      (is (not (contains? (:constants lang) 'two)))
      (is (= 2 (get-in lang [:functions 'add])))
      (is (= 1 (get-in lang [:functions 'dbl])))
      (is (= 1 (get-in lang [:functions 'pred])))
      (is (= 2 (get-in lang [:functions 'sub])))
      (is (= 2 (get-in lang [:functions 'div])))
      (is (= 2 (get-in lang [:functions 'max])))
      (is (= 1 (get-in lang [:functions 'log])))
      (is (= 2 (get-in lang [:functions 'root])))
      (is (= 2 (get-in lang [:functions 'count])))
      (is (nil? (get-in lang [:functions 'mul]))
          "multiplication must be a graph relation, not a function symbol")
      (is (= 3 (get-in lang [:relations 'mult])))
      (is (= 2 (get-in lang [:relations 'subst-code])))
      (is (= 4 (get-in lang [:relations 'subst-prf]))))))

(deftest sjas-numerals-are-binary-composed-terms
  (testing "only 0 and 1 are object-language numeral constants"
    (is (= (ast/app-term (symbol "0")) sjas/zero))
    (is (= (ast/app-term (symbol "1")) sjas/one))
    (is (= (sjas/dbl-term sjas/one) sjas/two))
    (is (= (sjas/add-term (sjas/dbl-term sjas/one) sjas/one) sjas/three))
    (is (= (sjas/dbl-term sjas/two) sjas/four))
    (is (= (sjas/add-term (sjas/dbl-term sjas/two) sjas/one) (n 5)))
    (is (= (sjas/dbl-term sjas/three) sjas/six))))

(deftest sjas-formula-classifiers-cover-bounded-and-unbounded-shapes
  (testing "bounded quantifiers stay visible to the SJAS classifier"
    (ast/nom x y
      (let [x-term (ast/var-term x)
            y-term (ast/var-term y)
            delta (sjas/bounded-forall x sjas/two
                    (sjas/lt x-term sjas/three))
            nested-delta (sjas/bounded-exists y sjas/three
                           (ast/and-form
                             (sjas/leq y-term sjas/three)
                             (sjas/mult y-term sjas/two sjas/four)))
            pi (ast/forall-form x delta)
            sigma (ast/exists-form x nested-delta)
            not-pi (ast/forall-form x
                     (ast/exists-form y
                       (sjas/lt y-term x-term)))]
        (is (sjas/delta-star-0? delta))
        (is (sjas/delta-star-0? nested-delta))
        (is (sjas/pi-star-1? pi))
        (is (sjas/sigma-star-1? sigma))
        (is (not (sjas/delta-star-0? (ast/exists-form y (sjas/lt y-term x-term)))))
        (is (not (sjas/pi-star-1? not-pi)))))))

(defn- demo-beta
  []
  (ast/eq-lit sjas/one sjas/one))

(defn- reflected-demo-clause
  ([]
   (reflected-demo-clause 'demo))
  ([relation]
   (ast/nom x
     (ast/clause relation [x]
                 (ast/eq-lit (ast/var-term x) sjas/one)))))

(defn- external-demo-clause
  ([]
   (external-demo-clause 'external-demo))
  ([relation]
   (ast/nom x
     (ast/clause relation [x]
                 (ast/eq-lit (ast/var-term x) sjas/zero)))))

(defn- demo-system
  ([profile]
   (demo-system profile {}))
  ([profile opts]
   (sjas/system
     (merge
       {:profile profile
        :relations {'demo 1
                    'external-demo 1}
        :beta [(demo-beta)]
        :reflected-clauses [(reflected-demo-clause)]
        :external-clauses [(external-demo-clause)]}
       opts))))

(defn- renamed-demo-system
  [profile reflected-relation external-relation]
  (sjas/system
    {:profile profile
     :relations {reflected-relation 1
                 external-relation 1}
     :beta [(demo-beta)]
     :reflected-clauses [(reflected-demo-clause reflected-relation)]
     :external-clauses [(external-demo-clause external-relation)]}))

(defn- target-for-theorem
  [system formula]
  (normalize/negate-formula (sjas/theorem-query system formula)))

(defn- canonical-formula-code
  [system canonical-formula]
  (sjas-code/canonical-formula-code-term (:coding-context system)
                                         canonical-formula))

(defn- wff-var0-substitution-codes
  [system]
  (let [source-formula '(pos (app wff (var v0)))
        source-code (canonical-formula-code system source-formula)
        target-formula (list 'pos (list 'app 'wff source-code))
        target-code (canonical-formula-code system target-formula)]
    {:source-code source-code
     :target-code target-code}))

(defn- shadowed-var0-substitution-code
  [system]
  (canonical-formula-code
    system
    '(forall v0 (pos (app wff (var v0))))))

(defn- proof-symbol-count
  "Count proof-symbol leaves in an encoded kernel proof payload."
  [proof]
  (cond
    (symbol? proof) 1
    (sequential? proof) (reduce + 0 (map proof-symbol-count proof))
    :else 0))

(deftest sjas-system-builder-generates-groups-and-reflected-boundary
  (testing "users supply beta/program clauses; the builder supplies codes and Group-3"
    (let [system (demo-system :willard-sjas-tableau0)]
      (is (= :willard-sjas-tableau0 (:profile system)))
      (is (= #{:group-zero :group-one :group-two :group-two-b :group-three}
             (set (map :group (:axioms system)))))
      (is (:system-code system))
      (is (= :group-three (-> system :group-three :group)))
      (is (some #(= (:code (:group-three system)) (:code %)) (:axioms system)))
      (let [beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))]
        (is (successful?
              (query/query-succeeds
                (:program system)
                (sjas/wff (:code beta-record))
                1
                96)))
        (is (successful?
              (query/query-succeeds
                (:program system)
                (sjas/neg-pair
                  (:code beta-record)
                  (sjas/formula-code system
                                     (normalize/negate-formula (:formula beta-record))))
                1
                128))
            "Level-1 complement relations must decode Godel-code terms"))
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/axiom-member (:system-code system)
                                  (:code (:group-three system)))
              1
              64)))))
  (testing "reflected changes alter Group-3; external-only changes do not"
    (let [base (demo-system :willard-sjas-tableau0)
          beta-changed (sjas/system
                         {:profile :willard-sjas-tableau0
                          :relations {'demo 1 'external-demo 1}
                          :beta [(ast/eq-lit sjas/zero sjas/zero)]
                          :reflected-clauses [(reflected-demo-clause)]
                          :external-clauses [(external-demo-clause)]})
          reflected-changed (sjas/system
                              {:profile :willard-sjas-tableau0
                               :relations {'demo 1 'external-demo 1}
                               :beta [(demo-beta)]
                               :reflected-clauses [(ast/nom x
                                                    (ast/clause 'demo [x]
                                                                (ast/eq-lit (ast/var-term x)
                                                                            sjas/two)))]
                               :external-clauses [(external-demo-clause)]})
          external-changed (sjas/system
                             {:profile :willard-sjas-tableau0
                              :relations {'demo 1 'external-demo 1}
                              :beta [(demo-beta)]
                              :reflected-clauses [(reflected-demo-clause)]
                              :external-clauses [(ast/nom x
                                                  (ast/clause 'external-demo [x]
                                                              (ast/eq-lit (ast/var-term x)
                                                                          sjas/one)))]})]
      (is (not= (:system-code base) (:system-code beta-changed)))
      (is (not= (-> base :group-three :code)
                (-> beta-changed :group-three :code)))
      (is (not= (:system-code base) (:system-code reflected-changed)))
      (is (= (:system-code base) (:system-code external-changed)))
      (is (= (-> base :group-three :code)
             (-> external-changed :group-three :code))))))

(deftest sjas-formal-codes-are-godel-byte-terms
  (testing "formal SJAS codes are inspectable base-64 Godel terms, not hash labels"
    (let [system (demo-system :willard-sjas-tableau0)]
      (is (sjas-code/code-term? (:system-code system)))
      (doseq [{:keys [code]} (:axioms system)]
        (is (sjas-code/code-term? code)
            (str "axiom code is not an SJAS Godel-code term: " (pr-str code))))
      (is (empty? (filter generated-code-symbol?
                          (get-in system [:language :constants])))
          "hash-derived code labels must not be formal language constants")
      (is (not-any? #(contains? (:program system) %)
                    [:sjas/system-code :sjas/fact-atoms :sjas/proof-targets])
          "compiled SJAS programs must not carry stale host-side proof or fact tables")
      (is (not (contains? @(get-in system [:program :sjas/registry])
                          :sjas/reflected-program))
          "proof-predicate reflected calls must not depend on a reflected compiled-program side table"))))

(deftest sjas-symbol-table-is-irrelevant-up-to-signature-isomorphism
  (let [base (demo-system :willard-sjas-tableau0)
        renamed (renamed-demo-system :willard-sjas-tableau0
                                     'zz-demo
                                     'zz-external-demo)
        base-index (get-in base [:coding-context :symbol->index 'demo])
        renamed-index (get-in renamed [:coding-context :symbol->index 'zz-demo])
        base-reflected-record (first (filter #(= :group-two-b (:group %))
                                             (:axioms base)))
        renamed-reflected-record (first (filter #(= :group-two-b (:group %))
                                                (:axioms renamed)))
        base-theorem (ast/pos-lit (ast/app-term 'demo sjas/one))
        renamed-theorem (ast/pos-lit (ast/app-term 'zz-demo sjas/one))
        base-code (sjas/formula-code base base-theorem)
        renamed-code (sjas/formula-code renamed renamed-theorem)
        base-proof (first-proof
                     (sjas/query-succeeds base base-theorem
                                          {:proof-limit 1
                                           :fuel 160}))
        renamed-proof (first-proof
                        (sjas/query-succeeds renamed renamed-theorem
                                             {:proof-limit 1
                                              :fuel 160}))
        base-certificate (when base-proof
                           (sjas/proof-certificate base-proof))
        renamed-certificate (when renamed-proof
                              (sjas/proof-certificate renamed-proof))]
    (is base-proof)
    (is renamed-proof)
    (is (not= base-index renamed-index)
        "the regression must exercise an actual finite codebook renaming")
    (is (not= (:system-code base) (:system-code renamed))
        "renamed signatures are recoded rather than nominally identical")
    (is (not= base-code renamed-code))
    (is (= base-index (nth (sjas-code/code-term-bytes base-code) 2)))
    (is (= renamed-index (nth (sjas-code/code-term-bytes renamed-code) 2)))
    (is (= base-certificate renamed-certificate)
        "proof constructors and proof size are preserved by signature renaming")
    (is (successful?
          (query/query-succeeds
            (:program base)
            (sjas/axiom-member (:system-code base)
                               (:code base-reflected-record))
            1
            160)))
    (is (successful?
          (query/query-succeeds
            (:program renamed)
            (sjas/axiom-member (:system-code renamed)
                               (:code renamed-reflected-record))
            1
            160)))))

(deftest sjas-byte-codes-preserve-sequence-length-and-trailing-zeroes
  (testing "public code terms are byte strings, not lossy natural labels"
    (let [bytes [1 0]
          code (sjas-code/bytes->code-term bytes)
          normalized-through-natural (sjas-code/code-term
                                       (sjas-code/bytes->natural bytes))]
      (is (= bytes (sjas-code/code-term-bytes code)))
      (is (not= bytes (sjas-code/code-term-bytes normalized-through-natural))
          "natural-number views are diagnostic; byte-sequence encoders must not use them when trailing zeroes matter"))))

(deftest sjas-formula-codes-preserve-trailing-zero-embedded-code-payloads
  (testing "an embedded code term at formula end remains structurally decodable"
    (let [system (demo-system :willard-sjas-level1)
          embedded (sjas-code/bytes->code-term [1 0])
          formula (sjas/wff embedded)
          formula-code (sjas/formula-code system formula)
          formula-bytes (sjas-code/code-term-bytes formula-code)]
      (is (= 0 (last formula-bytes))
          "this regression must exercise a formula code whose final byte is zero")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/wff formula-code)
              1
              96))))))

(deftest sjas-u-grounding-code-format-emits-numeral-codes-without-code-constructors
  (testing "the stronger SJAS code format uses only the U-Grounding signature"
    (let [system (demo-system :willard-sjas-level1
                              {:code-format :u-grounding})
          beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))]
      (is (= :u-grounding (:code-format system)))
      (is (sjas-numeral-term? (:system-code system)))
      (is (not (sjas-code/code-term? (:system-code system))))
      (is (sjas-numeral-term? (:code beta-record)))
      (is (not (sjas-code/code-term? (:code beta-record))))
      (is (not-any? code-constructor-symbol?
                    (keys (get-in system [:language :functions])))
          "U-Grounding coded systems must not expose generated code-N constructors"))))

(deftest sjas-u-grounding-codes-preserve-trailing-zero-byte-sequences
  (testing "sentinel natural codes remain injective for byte strings ending in zero"
    (let [bytes [1 0]
          code (sjas-code/bytes->u-grounding-code-term bytes)]
      (is (sjas-numeral-term? code))
      (is (not (sjas-code/code-term? code)))
      (is (= bytes (sjas-code/u-grounding-code-term-bytes code))))))

(deftest sjas-u-grounding-syntax-predicates-decode-numeral-codes
  (testing "wff, class predicates, and neg-pair accept pure U-Grounding numeral codes"
    (let [system (demo-system :willard-sjas-level1
                              {:code-format :u-grounding})
          formula (sjas/lt sjas/one sjas/two)
          code (sjas/formula-code system formula)
          complement-code (sjas/formula-code
                            system
                            (normalize/negate-formula formula))
          wff-proofs (query/query-succeeds
                       (:program system)
                       (sjas/wff code)
                       1
                       160)]
      (is (sjas-numeral-term? code))
      (is (sjas-numeral-term? complement-code))
      (is (successful? wff-proofs))
      (is (proof/contains-step? (first-proof wff-proofs) 'sjas-ug-code-bytes)
          "the proof should route through the relation-backed U-Grounding code decoder")
      (is (proof/contains-step? (first-proof wff-proofs) 'sjas-ug-code-byte-cons)
          "ground U-Grounding code decoding must prove byte-cons equations inside the object relation")
      (is (proof/contains-step? (first-proof wff-proofs) 'sjas-ug-code-mul64-shift)
          "ground U-Grounding code decoding must cite the fixed-radix multiplication relation")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/delta-star-0-code code)
              1
              160)))
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/neg-pair code complement-code)
              1
              200))))))

(deftest sjas-u-grounding-bound-code-decoding-uses-byte-cons-relation
  (testing "non-ground entry decodes U-Grounding codes through the radix-64 relation"
    (ast/nom code-var
      (let [system (demo-system :willard-sjas-level1
                                {:code-format :u-grounding})
            formula (ast/eq-lit sjas/one sjas/one)
            code (sjas/formula-code system formula)
            code-term (ast/var-term code-var)
            proofs (query/query-succeeds
                     (:program system)
                     (ast/exists-form
                       code-var
                       (ast/and-form
                         (ast/eq-lit code-term code)
                         (sjas/wff code-term)))
                     1
                     220)]
        (is (successful? proofs))
        (is (proof/contains-step? (first-proof proofs) 'sjas-ug-code-byte-cons)
            "the fallback decoder should prove the byte = low-6-bits relation")
        (is (proof/contains-step? (first-proof proofs) 'sjas-ug-code-mul64-shift)
            "the fallback decoder should cite the fixed-radix multiplication rule")))))

(deftest sjas-u-grounding-tableau-proof-checks-numeral-system-theorem-and-proof-codes
  (testing "tableau-proof can consume U-Grounding system, theorem, and proof numerals"
    (let [system (demo-system :willard-sjas-tableau0
                              {:code-format :u-grounding})
          beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
          axiom-certificate (sjas/proof-certificate 'sjas-axiom
                                                    {:code-format :u-grounding})]
      (is (sjas-numeral-term? (:system-code system)))
      (is (sjas-numeral-term? (:code beta-record)))
      (is (sjas-numeral-term? axiom-certificate))
      (let [proofs (query/query-succeeds
                     (:program system)
                     (sjas/tableau-proof (:system-code system)
                                         (:code beta-record)
                                         axiom-certificate)
                     1
                     200)]
        (is (successful? proofs))
        (let [proof (first-proof proofs)
              audit (correspondence/audit-proof-term proof)]
          (is (proof/contains-step? proof 'sjas-ug-code-byte-cons)
              "U-Grounding proof predicates must not decode ground system or theorem codes with a host shortcut")
          (is (proof/contains-step? proof 'sjas-ug-code-canonical-byte))
          (is (= #{}
                 (:unencodable-symbols audit)))
          (is (= #{}
                 (:unclassified-symbols audit))))))))

(deftest sjas-u-grounding-subst-code-computes-level1-fixed-point
  (testing "Level-1 Subst uses the U-Grounding source code numeral as the diagonal term"
    (let [system (sjas/system {:profile :willard-sjas-level1
                               :code-format :u-grounding})
          group3-record (:group-three system)]
      (is (sjas-numeral-term? (:selfcons-skeleton-code system)))
      (is (sjas-numeral-term? (:code group3-record)))
      (is (subst-code-relation-succeeds?
            system
            (:selfcons-skeleton-code system)
            (:code group3-record))
          "the arithmeticized relation should verify the Level-1 fixed point without a host byte projector")
      (is (not (subst-code-relation-succeeds?
                 system
                 (:system-code system)
                 (:code group3-record)))
          "a system code is not a formula code and must not pass as a substitution source"))))

(deftest sjas-proof-codes-are-byte-strings-with-symbol-bit-lower-bound
  (testing "proof certificates encode proof syntax rather than hashing it"
    (let [proof '(conj (profiled willard-sjas-proof-check (sjas-code-bytes) sjas-axiom))
          bytes (sjas-code/proof-code-bytes proof)
          certificate (sjas-code/proof-code-term proof)
          symbol-count (proof-symbol-count proof)]
      (is (sjas-code/code-term? certificate))
      (is (= bytes (sjas-code/code-term-bytes certificate)))
      (is (<= (* 5 symbol-count) (* 6 (count bytes)))
          "Willard's ordinary-tableau coding requirement needs at least five bits per encoded proof symbol"))))

(deftest sjas-proof-codes-encode-byte-payload-evidence
  (testing "code-reader proof evidence carries inspectable byte payloads rather than escaping the certificate grammar"
    (let [proof '(conj
                   (sjas-code-arg 1 sjas-code-args-end)
                   (free-close))
          certificate (sjas/proof-certificate proof)
          bytes (sjas-code/code-term-bytes certificate)]
      (is (sjas-code/code-term? certificate))
      (is (= bytes (sjas-code/proof-code-bytes proof)))
      (is (some #{sjas-code/proof-byte-tag} bytes)
          "numeric byte payloads in proof evidence must be encoded explicitly"))))

(deftest sjas-proof-code-decoder-round-trips-byte-payload-evidence
  (testing "the object-level proof-code decoder consumes explicit byte payloads"
    (let [proof '(conj
                   (sjas-code-arg 1 sjas-code-args-end)
                   (free-close))
          certificate (sjas/proof-certificate proof)
          decode-proof-codeo (var-get #'sjas-profile/decode-proof-codeo)
          decoded (l/run* [q]
                    (l/fresh [bytes decoded-proof read-proof]
                      (decode-proof-codeo certificate
                                          '()
                                          '()
                                          bytes
                                          decoded-proof
                                          read-proof)
                      (l/== [bytes decoded-proof read-proof] q)))]
      (is (= 1 (count decoded)))
      (is (= proof (second (first decoded)))))))

(deftest sjas-proof-codes-encode-u-grounding-canonical-byte-evidence
  (testing "U-Grounding byte-reader evidence carries an explicit byte payload in the proof-code grammar"
    (let [proof '(sjas-ug-code-canonical-byte
                   7
                   (sjas-ug-code-byte-cons
                     (sjas-ug-code-mul64-shift)
                     (sjas-ug-code-canonical-byte)))
          certificate (sjas/proof-certificate proof)
          bytes (sjas-code/code-term-bytes certificate)]
      (is (sjas-code/code-term? certificate))
      (is (= bytes (sjas-code/proof-code-bytes proof)))
      (is (some #{sjas-code/proof-byte-tag} bytes)
          "the canonical-byte evidence payload must be represented as a proof byte"))))

(deftest sjas-syntax-predicates-decode-formula-godel-codes
  (testing "wff, class predicates, and neg-pair are derived from formula Godel codes"
    (let [system (demo-system :willard-sjas-level1)
          formula (ast/true-form)
          code (sjas/formula-code system formula)
          complement-code (sjas/formula-code system
                                             (normalize/negate-formula formula))
          registry @(get-in system [:program :sjas/registry])
          fact-atoms (get-in system [:program :sjas/fact-atoms])
          wff-proofs (query/query-succeeds
                       (:program system)
                       (sjas/wff code)
                       1
                       32)
          delta-proofs (query/query-succeeds
                         (:program system)
                         (sjas/delta-star-0-code code)
                         1
                         32)
          neg-pair-proofs (query/query-succeeds
                            (:program system)
                            (sjas/neg-pair code complement-code)
                            1
                            48)]
      (is (sjas-code/code-term? complement-code))
      (is (not= (sjas/not-code code) complement-code)
          "complements must be formula Godel-code terms, not not-code wrappers")
      (is (not-any? #(contains? registry %)
                    [:sjas/formula-entries
                     :sjas/formula-negation-entries
                     :sjas/formula-class-entries
                     :sjas/neg-pair-entries])
          "syntax predicates must not depend on generated formula lookup registries")
      (is (not-any? (fn [atom]
                      (contains? '#{wff delta-star-0-code
                                    pi-star-1-code sigma-star-1-code}
                                 (second atom)))
                    fact-atoms)
          "syntax predicates must not be generated whole-formula facts")
      (is (successful? wff-proofs))
      (is (proof/contains-step? (first-proof wff-proofs) 'sjas-code-arg)
          "compact formula-code predicates must read code constructor bytes through the object relation")
      (is (= #{}
             (:unencodable-symbols (proof-symbol-audit (first-proof wff-proofs)))))
      (is (= #{}
             (:unclassified-symbols (proof-symbol-audit (first-proof wff-proofs)))))
      (is (sjas-code/code-term?
            (sjas/proof-certificate (first-proof wff-proofs)))
          "syntax predicate proof evidence must be representable in the SJAS proof-code grammar")
      (is (successful? delta-proofs))
      (is (= #{}
             (:unencodable-symbols (proof-symbol-audit (first-proof delta-proofs)))))
      (is (= #{}
             (:unclassified-symbols (proof-symbol-audit (first-proof delta-proofs)))))
      (is (successful? neg-pair-proofs))
      (is (= #{}
             (:unencodable-symbols (proof-symbol-audit (first-proof neg-pair-proofs)))))
      (is (= #{}
             (:unclassified-symbols (proof-symbol-audit (first-proof neg-pair-proofs))))))))

(deftest sjas-system-does-not-generate-axiom-member-fact-registry
  (let [system (demo-system :willard-sjas-tableau0)
        registry @(get-in system [:program :sjas/registry])]
    (is (not (contains? registry :sjas/fact-atoms))
        "axiom-member/2 predicate evaluation must not depend on generated host fact metadata")
    (is (not (contains? (:clauses (:program system)) 'axiom-member))
        "the generated SJAS basis must not add axiom-member/2 facts as ordinary clauses")))

(deftest sjas-system-does-not-generate-proof-antecedent-registry
  (let [system (demo-system :willard-sjas-tableau0)
        registry @(get-in system [:program :sjas/registry])]
    (is (not (contains? registry :sjas/system-entries))
        "proof predicates must reconstruct the finite axiom basis from system-code, not generated host antecedents")))

(deftest sjas-proof-predicates-require-source-preprocessing-registry
  (let [system (demo-system :willard-sjas-tableau0)
        registry @(get-in system [:program :sjas/registry])
        fixed-record (first (filter #(= :group-zero (:group %)) (:axioms system)))
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)
        top-level-metadata-program (-> (:program system)
                                       (dissoc :sjas/registry)
                                       (assoc :sjas/system-code (:system-code system)
                                              :sjas/code-format (:code-format system)
                                              :sjas/symbol-index-entries (:sjas/symbol-index-entries registry)))]
    (is (not (contains? top-level-metadata-program :sjas/registry))
        "the regression must remove the source-preprocessing registry")
    (is (empty?
          (query/query-succeeds
            top-level-metadata-program
            (sjas/tableau-proof (:system-code system)
                                (:code fixed-record)
                                axiom-certificate)
            1
            96))
        "proof predicates must not accept stale top-level source metadata as a registry substitute")))

(deftest ^:slow sjas-structural-code-predicates-accept-non-generated-formula-codes
  (testing "formula-code predicates parse codes beyond the generated axiom registry"
    (let [system (demo-system :willard-sjas-level1)
          formula (sjas/lt sjas/one sjas/two)
          code (sjas/formula-code system formula)
          complement-code (sjas/formula-code system
                                             (normalize/negate-formula formula))
          generated-codes (set (map :code (:axioms system)))
          wff-proofs (query/query-succeeds
                       (:program system)
                       (sjas/wff code)
                       1
                       32)]
      (is (not (contains? generated-codes code))
          "the test formula must not be one of the generated axiom codes")
      (is (successful? wff-proofs))
      (is (proof/contains-step? (first-proof wff-proofs) 'sjas-code-arg)
          "compact formula-code predicates must read code constructor bytes through the object relation")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/delta-star-0-code code)
              1
              32)))
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/neg-pair code complement-code)
              1
              48)))
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/subst-code code code)
              1
              48))))))

(deftest sjas-source-builder-accepts-prefix-program-sections
  (testing "source users do not need to hand-build backend AST clauses"
    (let [system (sjas/system-source
                   {:profile :willard-sjas-tableau0}
                   (language
                     (constants extra)
                     (functions (mark 1))
                   (relations (demo 1)
                              (external-demo 1)))
                 (beta
                    (= 1 1))
                 (reflected
                   (|- (demo x)
                       (= x 1)))
                 (external
                   (|- (external-demo x)
                       (= x 0))))]
      (is (= :willard-sjas-tableau0 (:profile system)))
      (is (= 1 (get-in system [:language :functions 'mark])))
      (is (contains? (get-in system [:language :constants]) 'extra))
      (is (successful?
            (sjas/query-succeeds
              system
              (ast/eq-lit sjas/one sjas/one)
              {:proof-limit 1
               :fuel 64})))
      (is (successful?
            (query/query-succeeds
              (:program system)
              (ast/pos-lit (ast/app-term 'demo sjas/one))
              1
              96))
          "reflected user clauses should remain executable procedure clauses")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (ast/pos-lit (ast/app-term 'external-demo sjas/zero))
              1
              96))
          "external user clauses should be queryable outside the reflected basis")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/axiom-member (:system-code system)
                                  (:code (:group-three system)))
              1
              64))))))

(deftest sjas-composite-examples-distinguish-beta-axioms-from-reflected-procedures
  (testing "a beta-only composite axiom can prove a theorem without defining an executable relation"
    (let [system (sjas/system-source
                   {:profile :willard-sjas-tableau0}
                   (language
                     (relations (composite 1)))
                   (beta
                     (forall [x]
                       (implies
                         (mult (dbl 1) (dbl 1) x)
                         (composite x)))))
          composite-four (ast/pos-lit (ast/app-term 'composite (n 4)))]
      (is (= {:group-zero 2
              :group-one 3
              :group-two 1
              :group-three 1}
             (frequencies (map :group (:axioms system)))))
      (is (successful?
            (sjas/query-succeeds
              system
              composite-four
              {:proof-limit 1
               :fuel 64})))
      (is (empty?
            (query/query-succeeds
              (:program system)
              composite-four
              1
              64))
          "Group-2 formulas are axiom text, not Procedure Call Rule clauses")))
  (testing "a reflected composite clause is executable and also becomes Group-2b"
    (let [system (sjas/system-source
                   {:profile :willard-sjas-tableau0}
                   (language
                     (relations (composite 1)))
                   (reflected
                     (|- (composite x)
                         (mult (dbl 1) (dbl 1) x))))
          composite-four (ast/pos-lit (ast/app-term 'composite (n 4)))
          group2b-record (first (filter #(= :group-two-b (:group %))
                                        (:axioms system)))
          axiom-certificate (sjas/proof-certificate 'sjas-axiom)
          reflected-citation-proofs (query/query-succeeds
                                      (:program system)
                                      (sjas/tableau-proof (:system-code system)
                                                          (:code group2b-record)
                                                          axiom-certificate)
                                      1
                                      120)]
      (is (= {:group-zero 2
              :group-one 3
              :group-two-b 1
              :group-three 1}
             (frequencies (map :group (:axioms system)))))
      (is (successful? reflected-citation-proofs))
      (is (proof/contains-step? (first-proof reflected-citation-proofs)
                                'sjas-system-reflected-axiom)
          "reflected clause axiom citations must be recovered from encoded system-code clauses")
      (is (successful?
            (query/query-succeeds
              (:program system)
              composite-four
              1
              64)))
      (is (successful?
            (sjas/query-succeeds
              system
              composite-four
              {:proof-limit 1
               :fuel 64})))
      (ast/nom x
        (let [records (sjas/query-answers
                        system
                        (ast/pos-lit (ast/app-term 'composite (ast/var-term x)))
                        [x]
                        {:proof-limit 1
                         :fuel 64})]
          (is (= (n 4) (binding-for records x))))))))

(deftest sjas-arithmetic-runs-through-binary-relations
  (let [system (demo-system :willard-sjas-tableau0)
        program (:program system)]
    (testing "closed U-grounding function equations are proved by the SJAS profile"
      (doseq [formula [(ast/eq-lit (sjas/add-term (n 2) (n 3)) (n 5))
                       (ast/eq-lit (sjas/dbl-term (n 6)) (n 12))
                       (ast/eq-lit (sjas/pred-term (n 0)) (n 0))
                       (ast/eq-lit (sjas/pred-term (n 5)) (n 4))
                       (ast/eq-lit (sjas/sub-term (n 2) (n 5)) (n 0))
                       (ast/eq-lit (sjas/sub-term (n 7) (n 3)) (n 4))
                       (ast/eq-lit (sjas/div-term (n 7) (n 0)) (n 7))
                       (ast/eq-lit (sjas/div-term (n 7) (n 3)) (n 2))
                       (ast/eq-lit (sjas/max-term (n 4) (n 9)) (n 9))
                       (ast/eq-lit (sjas/log-term (n 1)) (n 0))
                       (ast/eq-lit (sjas/log-term (n 8)) (n 3))
                       (ast/eq-lit (sjas/root-term (n 10) (n 2)) (n 4))
                       (ast/eq-lit (sjas/root-term (n 8) (n 3)) (n 2))
                       (ast/eq-lit (sjas/count-term (n 13) (n 4)) (n 3))]]
        (is (successful?
              (query/query-succeeds program formula 1 160))
            (pr-str formula))))
    (testing "closed arithmetic relation facts are profile relations, not finite facts"
      (is (successful?
            (query/query-succeeds program
                                  (sjas/mult (n 4) (n 3) (n 12))
                                  1
                                  160)))
      (is (successful?
            (query/query-succeeds program
                                  (sjas/leq (n 13) (n 13))
                                  1
                                  80)))
      (is (successful?
            (query/query-succeeds program
                                  (sjas/lt (n 13) (n 14))
                                  1
                                  80)))
      (is (empty?
            (query/query-succeeds program
                                  (sjas/mult (n 4) (n 3) (n 11))
                                  1
                                  80)))
      (is (empty?
            (query/query-succeeds program
                                  (ast/eq-lit (sjas/add-term (n 2) (n 3)) (n 6))
                                  1
                                  80))))))

(deftest sjas-arithmetic-supports-answer-and-partial-synthesis-modes
  (let [system (demo-system :willard-sjas-tableau0)]
    (ast/nom x y z
      (testing "answer mode synthesizes missing multiplicands"
        (let [left-records (sjas/query-answers
                             system
                             (sjas/mult (ast/var-term x) (n 3) (n 12))
                             [x]
                             {:proof-limit 1
                              :fuel 160})
              right-records (sjas/query-answers
                              system
                              (sjas/mult (n 4) (ast/var-term y) (n 12))
                              [y]
                              {:proof-limit 1
                               :fuel 160})]
          (is (= (n 4) (binding-for left-records x)))
          (is (= (n 3) (binding-for right-records y)))))
      (testing "partial synthesis solves an arithmetic function equation"
        (let [records (sjas/query-answers
                        system
                        (ast/eq-lit (sjas/add-term (ast/var-term z) (n 3)) (n 7))
                        [z]
                        {:proof-limit 1
                         :fuel 160})]
          (is (= (n 4) (binding-for records z))))))))

(deftest sjas-tableau-proof-checks-kernel-certificates
  (let [system (demo-system :willard-sjas-tableau0)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        beta-proof (first-proof
                     (sjas/query-succeeds system (:formula beta-record)
                                          {:proof-limit 1
                                           :fuel 96}))
        valid (sjas/proof-certificate beta-proof)
        valid-proofs (query/query-succeeds
                       (:program system)
                       (sjas/tableau-proof (:system-code system)
                                           (:code beta-record)
                                           valid)
                       1
                       160)]
    (is beta-proof)
    (is (sjas-code/code-term? valid)
        "proof certificates must be base-64 Godel-code terms")
    (is (successful? valid-proofs))
    (is (proof/contains-step? (first-proof valid-proofs) 'willard-sjas-theorem-code)
        "tableau-proof must decode generated theorem codes structurally during predicate application")
    (is (proof/contains-step? (first-proof valid-proofs) 'sjas-code-arg)
        "compact theorem-code decoding inside tableau-proof must expose object-level byte reads")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                (:system-code system)
                                valid)
            1
            80)))))

(deftest sjas-tableau-proof-reconstructs-axiom-basis-without-system-registry
  (let [system (demo-system :willard-sjas-tableau0)
        registry (get-in system [:program :sjas/registry])
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        beta-proof (first-proof
                     (sjas/query-succeeds system (:formula beta-record)
                                          {:proof-limit 1
                                           :fuel 96}))
        certificate (sjas/proof-certificate beta-proof)]
    (is beta-proof)
    (swap! registry dissoc :sjas/system-entries)
    (is (not (contains? @registry :sjas/system-entries))
        "the regression must remove the generated host-side proof antecedent")
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                (:code beta-record)
                                certificate)
            1
            200))
        "tableau-proof must reconstruct the axiom basis from system-code during predicate application")))

(deftest sjas-proof-predicates-ignore-external-runtime-clauses
  (let [system (demo-system :willard-sjas-tableau0)
        formula (ast/pos-lit (ast/app-term 'external-demo sjas/zero))
        theorem-code (sjas/formula-code system formula)
        certificate (sjas/proof-certificate
                      '(conj
                         (neg-call
                           (profiled willard-sjas-arithmetic
                             (sjas-equal
                               (sjas-read-zero)
                               (sjas-read-zero)
                               (sjas-bind-done))))))]
    (is (successful?
          (query/query-succeeds
            (:program system)
            formula
            1
            96))
        "external clauses remain executable for ordinary host-side Proflog queries")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                theorem-code
                                certificate)
            1
            200))
        "tableau-proof must validate certificates against the reflected SJAS system, not external runtime clauses")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            theorem-code
                            theorem-code
                            certificate)
            1
            200))
        "subst-prf must validate certificates against the reflected SJAS system, not external runtime clauses")))

(deftest ^:slow sjas-tableau-proof-checks-structural-non-generated-theorem-codes
  (let [system (demo-system :willard-sjas-tableau0)
        theorem (sjas/lt sjas/one sjas/two)
        theorem-code (sjas/formula-code system theorem)
        wrong-theorem-code (sjas/formula-code system
                                              (sjas/lt sjas/two sjas/one))
        theorem-proof (first-proof
                        (sjas/query-succeeds system theorem
                                             {:proof-limit 1
                                              :fuel 96}))
        certificate (when theorem-proof
                      (sjas/proof-certificate theorem-proof))
        generated-codes (set (map :code (:axioms system)))]
    (is theorem-proof)
    (is (not (contains? generated-codes theorem-code))
        "the theorem code must not be one of the generated axiom codes")
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                theorem-code
                                certificate)
            1
            180)))
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                wrong-theorem-code
                                certificate)
            1
            120)))))

(deftest ^:slow sjas-subst-prf-checks-structural-non-generated-theorem-codes
  (let [system (demo-system :willard-sjas-tableau0)
        theorem (sjas/lt sjas/one sjas/two)
        theorem-code (sjas/formula-code system theorem)
        wrong-theorem-code (sjas/formula-code system
                                              (sjas/lt sjas/two sjas/one))
        theorem-proof (first-proof
                        (sjas/query-succeeds system theorem
                                             {:proof-limit 1
                                              :fuel 96}))
        certificate (when theorem-proof
                      (sjas/proof-certificate theorem-proof))
        generated-codes (set (map :code (:axioms system)))]
    (is theorem-proof)
    (is (not (contains? generated-codes theorem-code))
        "the theorem code must not be one of the generated axiom codes")
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            theorem-code
                            theorem-code
                            certificate)
            1
            220)))
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            theorem-code
                            wrong-theorem-code
                            certificate)
            1
            160)))))

(deftest sjas-subst-prf-checks-identity-substitution-certificates
  (let [system (demo-system :willard-sjas-tableau0)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        beta-proof (first-proof
                     (sjas/query-succeeds system (:formula beta-record)
                                          {:proof-limit 1
                                           :fuel 96}))
        valid (sjas/proof-certificate beta-proof)
        valid-proofs (query/query-succeeds
                       (:program system)
                       (sjas/subst-prf (:system-code system)
                                       (:code beta-record)
                                       (:code beta-record)
                                       valid)
                       1
                       160)]
    (is beta-proof)
    (is (successful? valid-proofs))
    (is (proof/contains-step? (first-proof valid-proofs) 'willard-sjas-theorem-code)
        "subst-prf must decode generated theorem codes structurally during predicate application")
    (is (proof/contains-step? (first-proof valid-proofs) 'sjas-code-arg)
        "compact theorem-code decoding inside subst-prf must expose object-level byte reads")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:code beta-record)
                            (:system-code system)
                            valid)
            1
            80)))
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:system-code system)
                            (:code beta-record)
                            valid)
            1
            80)))))

(deftest sjas-proof-predicates-check-simple-arithmetic-certificates-without-kernel-validator
  (let [system (demo-system :willard-sjas-tableau0)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        beta-proof (first-proof
                     (sjas/query-succeeds system (:formula beta-record)
                                          {:proof-limit 1
                                           :fuel 96}))
        certificate (when beta-proof
                      (sjas/proof-certificate beta-proof))]
    (is beta-proof)
    (with-redefs [kernel/prove-programo
                  (fn [& _]
                    (throw (ex-info "host kernel proof validator reached" {})))]
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/tableau-proof (:system-code system)
                                  (:code beta-record)
                                  certificate)
              1
              200))
          "tableau-proof must validate simple arithmetic certificates without delegating to the host kernel")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/subst-prf (:system-code system)
                              (:code beta-record)
                              (:code beta-record)
                              certificate)
              1
              240))
          "subst-prf must validate identity-substitution arithmetic certificates without delegating to the host kernel"))))

(deftest sjas-proof-predicates-check-reflected-clause-certificates-without-kernel-validator
  (let [system (demo-system :willard-sjas-tableau0)
        theorem (ast/pos-lit (ast/app-term 'demo sjas/one))
        theorem-code (sjas/formula-code system theorem)
        theorem-proof (first-proof
                        (sjas/query-succeeds system theorem
                                             {:proof-limit 1
                                              :fuel 160}))
        certificate (when theorem-proof
                      (sjas/proof-certificate theorem-proof))]
    (is theorem-proof)
    (is (proof/contains-step? theorem-proof 'neg-call))
    (with-redefs [kernel/prove-programo
                  (fn [& _]
                    (throw (ex-info "host kernel proof validator reached" {})))]
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/tableau-proof (:system-code system)
                                  theorem-code
                                  certificate)
              1
              240))
          "tableau-proof must validate reflected-clause certificates without delegating to the host kernel"))))

(deftest sjas-proof-predicates-check-reflected-calls-from-system-code
  (let [system (demo-system :willard-sjas-tableau0)
        theorem (ast/pos-lit (ast/app-term 'demo sjas/one))
        theorem-code (sjas/formula-code system theorem)
        theorem-proof (first-proof
                        (sjas/query-succeeds system theorem
                                             {:proof-limit 1
                                              :fuel 160}))
        certificate (when theorem-proof
                      (sjas/proof-certificate theorem-proof))
        registry (atom (dissoc @(get-in system [:program :sjas/registry])
                               :sjas/reflected-program))
        stripped-program (assoc (:program system)
                                :clauses nil
                                :clause-list '()
                                :alternative-clause-list '()
                                :guarded-clause-list '()
                                :sjas/registry registry)]
    (is theorem-proof)
    (is (proof/contains-step? theorem-proof 'neg-call))
    (with-redefs [kernel/prove-programo
                  (fn [& _]
                    (throw (ex-info "host kernel proof validator reached" {})))]
      (is (successful?
            (query/query-succeeds
              stripped-program
              (sjas/tableau-proof (:system-code system)
                                  theorem-code
                                  certificate)
              1
              240))
          "reflected procedure calls inside proof certificates must be recovered from encoded system-code clauses"))))

(deftest sjas-subst-prf-reconstructs-axiom-basis-without-system-registry
  (let [system (demo-system :willard-sjas-tableau0)
        registry (get-in system [:program :sjas/registry])
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        beta-proof (first-proof
                     (sjas/query-succeeds system (:formula beta-record)
                                          {:proof-limit 1
                                           :fuel 96}))
        certificate (sjas/proof-certificate beta-proof)]
    (is beta-proof)
    (swap! registry dissoc :sjas/system-entries)
    (is (not (contains? @registry :sjas/system-entries))
        "the regression must remove the generated host-side proof antecedent")
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:code beta-record)
                            (:code beta-record)
                            certificate)
            1
            220))
        "subst-prf must reconstruct the axiom basis from system-code during predicate application")))

(deftest sjas-tableau-proof-accepts-axiom-citation-certificates
  (let [system (demo-system :willard-sjas-level1)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)
        beta-citation-proofs (query/query-succeeds
                               (:program system)
                               (sjas/tableau-proof (:system-code system)
                                                   (:code beta-record)
                                                   axiom-certificate)
                               1
                               96)]
    (is (successful? beta-citation-proofs))
    (is (proof/contains-step? (first-proof beta-citation-proofs)
                              'sjas-system-beta-axiom)
        "beta axiom citations must be recovered from encoded system-code beta formulas")
    (is (proof/contains-step? (first-proof beta-citation-proofs)
                              'sjas-code-arg)
        "compact axiom citations must expose code constructor byte reads")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                (:contradiction-code system)
                                axiom-certificate)
            1
            96)))))

(deftest sjas-tableau-proof-cites-fixed-axiom-groups-from-system-code
  (let [system (demo-system :willard-sjas-tableau0)
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)]
    (doseq [[group expected-step] [[:group-zero 'sjas-system-group-zero-axiom]
                                  [:group-one 'sjas-system-group-one-axiom]]]
      (let [record (first (filter #(= group (:group %)) (:axioms system)))
            citation-proofs (query/query-succeeds
                              (:program system)
                              (sjas/tableau-proof (:system-code system)
                                                  (:code record)
                                                  axiom-certificate)
                              1
                              96)
            proof (first-proof citation-proofs)]
        (is (successful? citation-proofs))
        (is (proof/contains-step? proof expected-step)
            (str group " citations must be recovered from the fixed SJAS axiom profile"))
        (is (proof/contains-step? proof 'sjas-code-arg)
            (str group " citations must expose formula-code constructor byte reads"))
        (is (not (proof/contains-step? proof 'sjas-generated-axiom-member))
            (str group " citations should not fall back to generated axiom-member facts"))))))

(deftest sjas-tableau-proof-cites-tableau0-group-three-from-system-code
  (let [system (demo-system :willard-sjas-tableau0)
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)
        citation-proofs (query/query-succeeds
                          (:program system)
                          (sjas/tableau-proof (:system-code system)
                                              (:code (:group-three system))
                                              axiom-certificate)
                          1
                          160)
        proof (first-proof citation-proofs)]
    (is (successful? citation-proofs))
    (is (proof/contains-step? proof 'sjas-system-group-three-axiom)
        "Tableau-0 Group-3 citations must be reconstructed from system-code")
    (is (not (proof/contains-step? proof 'sjas-generated-axiom-member))
        "Tableau-0 Group-3 citations should not fall back to generated axiom-member facts")))

(deftest sjas-tableau-proof-cites-level1-group-three-from-system-code
  (let [system (demo-system :willard-sjas-level1)
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)
        citation-proofs (query/query-succeeds
                          (:program system)
                          (sjas/tableau-proof (:system-code system)
                                              (:code (:group-three system))
                                              axiom-certificate)
                          1
                          200)
        proof (first-proof citation-proofs)]
    (is (successful? citation-proofs))
    (is (proof/contains-step? proof 'sjas-system-level1-group-three-axiom)
        "Level-1 Group-3 citations must validate the fixed-point skeleton from system-code")
    (is (not (proof/contains-step? proof 'sjas-generated-axiom-member))
        "Level-1 Group-3 citations should not fall back to generated axiom-member facts")))

(deftest sjas-tableau-proof-ignores-injected-generated-axiom-member-facts
  (let [system (demo-system :willard-sjas-tableau0)
        registry (get-in system [:program :sjas/registry])
        bogus-code (:contradiction-code system)
        bogus-fact (ast/app-term 'axiom-member (:system-code system) bogus-code)
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)]
    (swap! registry update :sjas/fact-atoms conj bogus-fact)
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                bogus-code
                                axiom-certificate)
            1
            160))
        "tableau-proof must not trust generated axiom-member facts during sjas-axiom citation")))

(deftest sjas-axiom-member-query-ignores-injected-generated-facts
  (let [system (demo-system :willard-sjas-tableau0)
        registry (get-in system [:program :sjas/registry])
        bogus-code (:contradiction-code system)
        bogus-fact (ast/app-term 'axiom-member (:system-code system) bogus-code)]
    (swap! registry update :sjas/fact-atoms conj bogus-fact)
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/axiom-member (:system-code system) bogus-code)
            1
            160))
        "axiom-member/2 queries must be checked from decoded system code, not generated facts")))

(deftest sjas-subst-code-relates-structural-substitution-codes
  (let [system (demo-system :willard-sjas-level1)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        group3-record (:group-three system)]
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code (:selfcons-skeleton-code system)
                             (:code group3-record))
            1
            96)))
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code (:code beta-record)
                             (:code beta-record))
            1
            96)))
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code (:system-code system)
                             (:code group3-record))
            1
            96)))))

(deftest ^:slow sjas-subst-code-computes-general-formula-code-substitution
  (let [system (demo-system :willard-sjas-level1)
        {:keys [source-code target-code]} (wff-var0-substitution-codes system)
        shadowed-code (shadowed-var0-substitution-code system)
        registry @(get-in system [:program :sjas/registry])]
    (is (nil? (:sjas/subst-code-entries registry))
        "general Subst must not be implemented by generated substitution entries")
    (is (sjas-code/code-term? source-code))
    (is (sjas-code/code-term? target-code))
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code source-code target-code)
            1
            240))
        "Subst should replace free v0 with the source formula's own code term")
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code source-code source-code)
            1
            160))
        "open formulas containing free v0 must not pass through identity")
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-code shadowed-code shadowed-code)
            1
            160))
        "a quantifier binding v0 shadows the substitution variable")))

(deftest sjas-subst-prf-uses-substitution-code-independently-of-theorem-code
  (let [system (demo-system :willard-sjas-level1)
        beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
        certificate (sjas/proof-certificate 'sjas-axiom)]
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:selfcons-skeleton-code system)
                            (:code beta-record)
                            certificate)
            1
            220)))))

(deftest sjas-level1-group-three-uses-substitution-proof-vocabulary
  (let [system (demo-system :willard-sjas-level1)
        relations (set (formula-relation-symbols (:formula (:group-three system))))]
    (is (contains? relations 'neg-pair))
    (is (contains? relations 'subst-prf))
    (is (not (contains? relations 'tableau-proof)))))

(deftest sjas-level1-group-three-uses-selfcons-skeleton-code
  (let [system (demo-system :willard-sjas-level1)
        skeleton-code (:selfcons-skeleton-code system)
        subst-atoms (filter #(= 'subst-prf (second %))
                            (formula-atoms (:formula (:group-three system))))]
    (is (sjas-code/code-term? skeleton-code))
    (is (= 2 (count subst-atoms)))
    (is (every? #(= (:system-code system) (nth % 2)) subst-atoms))
    (is (every? #(= skeleton-code (nth % 3)) subst-atoms))
    (is (not-any? #(= (:system-code system) (nth % 3)) subst-atoms))))

(deftest ^:slow sjas-subst-prf-checks-selfcons-fixed-point-certificate
  (let [system (demo-system :willard-sjas-level1)
        group3-record (:group-three system)
        valid (sjas/proof-certificate 'sjas-axiom)]
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/tableau-proof (:system-code system)
                                (:code group3-record)
                                valid)
            1
            160)))
    (is (successful?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:selfcons-skeleton-code system)
                            (:code group3-record)
                            valid)
            1
            220)))
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:system-code system)
                            (:code group3-record)
                            valid)
            1
            120)))))

(deftest ^:slow sjas-subst-prf-rejects-selfcons-complement-axiom-certificate
  (let [system (sjas/system {:profile :willard-sjas-level1})
        group3 (:formula (:group-three system))
        complement-code (sjas/formula-code system
                                           (normalize/negate-formula group3))
        axiom-certificate (sjas/proof-certificate 'sjas-axiom)]
    (is (empty?
          (query/query-succeeds
            (:program system)
            (sjas/subst-prf (:system-code system)
                            (:selfcons-skeleton-code system)
                            complement-code
                            axiom-certificate)
            1
            220))
        "the Level-1 proof predicate must not treat the complement of its fixed-point SelfCons axiom as an axiom instance")))

(deftest sjas-selfcons-demonstration-uses-substantive-proof-targets
  (testing "the generated self-consistency axiom is a theorem with a checked certificate"
    (let [system (demo-system :willard-sjas-tableau0)
          group3-proof (first-proof
                         (sjas/query-succeeds system
                                              (:formula (:group-three system))
                                              {:proof-limit 1
                                               :fuel 96}))
          group3-certificate (when group3-proof
                               (sjas/proof-certificate group3-proof))]
      (is group3-proof)
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/tableau-proof (:system-code system)
                                  (:code (:group-three system))
                                  group3-certificate)
              1
              160)))))
  (testing "an explicitly inconsistent reflected basis can cite the real contradiction target"
    (let [system (sjas/system {:profile :willard-sjas-tableau0
                               :beta [(ast/false-form)]})
          contradiction-certificate (sjas/proof-certificate 'sjas-axiom)]
      (is (some #(and (= :group-two (:group %))
                      (= (:contradiction-code system) (:code %)))
                (:axioms system))
          "the inconsistent beta basis must encode false as a reflected axiom")
      (is (successful?
            (query/query-succeeds
              (:program system)
              (sjas/tableau-proof (:system-code system)
                                  (:contradiction-code system)
                                  contradiction-certificate)
              1
              160))))))

(deftest sjas-tableau0-and-level1-query-generated-axioms-through-selected-profile
  (doseq [profile [:willard-sjas-tableau0 :willard-sjas-level1]]
    (let [system (demo-system profile)
          beta-proof (first-proof
                       (query/query-succeeds (:program system)
                                             (demo-beta)
                                             1
                                             64))
          selfcons-proof (first-proof
                           (query/query-succeeds
                             (:program system)
                             (sjas/axiom-member (:system-code system)
                                                 (:code (:group-three system)))
                             1
                             64))]
      (is beta-proof)
      (is selfcons-proof)
      (is (proof/contains-step? beta-proof (symbol (name profile))))
      (is (proof/contains-step? selfcons-proof (symbol (name profile)))))))

(deftest sjas-level1-bounded-contradiction-probe-records-timing
  (let [system (demo-system :willard-sjas-level1)
        result (sjas/bounded-contradiction-probe system {:fuel 4
                                                         :proof-limit 1})]
    (is (= :not-found (:result result)))
    (is (= 4 (:fuel result)))
    (is (integer? (:duration-ms result)))
    (is (not (neg? (:duration-ms result))))))

(deftest ^:slow sjas-tableau0-selfcons-negating-witness-separates-external-proflog
  (let [system (sjas/system {:profile :willard-sjas-tableau0})
        witness (sjas/tableau-proof (:system-code system)
                                    (:contradiction-code system)
                                    (sjas/proof-certificate 'sjas-axiom))
        external-program (ast/nom s f p
                           (language/compile-program
                             (dissoc (:language system) :proof-profile)
                             [(ast/clause 'tableau-proof [s f p]
                                          (ast/true-form))]))
        external-proofs (binding [gamma/*closed-term-depth-cap* 0
                                  gamma/*closed-term-count-cap* 0]
                          (query/query-succeeds external-program witness 1 80))]
    (is (empty?
          (query/query-succeeds
            (:program system)
            witness
            1
            160))
        "the generated SJAS system must reject the witness negating its Tableau-0 self-consistency axiom")
    (is (successful? external-proofs)
        "ordinary Proflog accepts the same witness when tableau-proof/3 is supplied as an external runtime procedure")))

(deftest sjas-profile-source-audit-rejects-host-proof-checker-route
  (let [profile-source (slurp "src/proflog/kernel/willard_sjas_profile.clj")
        builder-source (slurp "src/proflog/willard_sjas.clj")]
    (is (not (re-find #"prove-program-host" profile-source)))
    (is (not (re-find #"host-proof" profile-source)))
    (is (not (re-find #"whole-formula" profile-source)))
    (is (not (re-find #"mini-closed" profile-source)))
    (is (not (re-find #"kernel/prove-programo target" profile-source))
        "SJAS proof predicates must not short-circuit non-axiom certificates through the host proof kernel")
    (is (not (re-find #"sjas-reflected-proof-program" profile-source))
        "SJAS proof predicates must not recover proof-time clauses from a reflected compiled-program registry")
    (is (not (re-find #"program/call-clauseo" profile-source))
        "SJAS proof-predicate procedure calls must decode reflected system-code clauses")
    (is (not (re-find #"sjas-decode-compact-formula-code-staged-proofo" profile-source))
        "SJAS proof predicates must not use staged compact theorem-code decoding")
    (is (not (re-find #"sjas-decode-substitution-target-codeo" profile-source))
        "SJAS substitution predicates must not use staged target-code decoding")
    (is (not (re-find #"ground-formal-code-term source-code" profile-source))
        "SJAS substitution predicates must not use the old broad staged source-code branch")
    (is (not (re-find #"defn- ground-formal-code-term" profile-source))
        "SJAS proof predicates must not project public code bytes through a deterministic host decoder")
    (is (not (re-find #"defn- ground-u-grounding-substitution-bytes" profile-source))
        "SJAS substitution predicates must not recover U-Grounding formula bytes through a host projector")
    (is (not (re-find #"sjas-code/code-term-bytes term" profile-source))
        "compact public code terms must be read through the object code-byte relation")
    (is (not (re-find #"ground-u-grounding-code-term-bytes" profile-source))
        "U-Grounding public code terms must be read through the object numeral relation")
    (is (not (re-find #"mini-closed" builder-source)))
    (is (not (re-find #"malformed" profile-source)))
    (is (not (re-find #"malformed" builder-source)))
    (is (not (re-find #"defn- mult-facts" builder-source)))
    (is (not (re-find #"defn- order-facts" builder-source)))))
