(ns proflog.willard-sjas
  "Willard-style SJAS language builder.

   This namespace is the source-to-kernel construction layer for the SJAS ADR
   sequence. It builds finite reflected SJAS systems with stable formula codes
   and encoded system sources. Binary arithmetic, axiom membership, and
   proof-certificate checking are handled by the selected proof profile; host
   Clojure is used here only to assemble the finite source object."
  (:require [clojure.core.logic :refer [lvar]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.answer-overlay :as answer-overlay]
            [proflog.answers :as answers]
            [proflog.ast :as ast]
            [proflog.frontend :as frontend]
            [proflog.gamma :as gamma]
            [proflog.kernel.willard-sjas-profile :as willard-sjas-profile]
            [proflog.language :as language]
            [proflog.query :as query]
            [proflog.willard-sjas-code :as sjas-code]))

;; -----------------------------------------------------------------------------
;; Public terms and language declarations
;; -----------------------------------------------------------------------------

(def zero-symbol
  "Object-language symbol for the SJAS numeral zero.

   Clojure cannot read a bare digit as a symbol, so source code refers to this
   through helper vars such as `zero`; the term itself is still `(app 0)`."
  (symbol "0"))

(def one-symbol
  "Object-language symbol for the SJAS numeral one."
  (symbol "1"))

(def zero (ast/app-term zero-symbol))
(def one (ast/app-term one-symbol))

(defn add-term [left right] (ast/app-term 'add left right))
(defn dbl-term [term] (ast/app-term 'dbl term))

(defn numeral
  "Return the canonical binary-composed SJAS term for a host natural number.

   This helper belongs to the source-construction boundary. It does not add
   host arithmetic to proof search; it only writes the object-language numeral
   that the kernel profile later interprets relationally."
  [value]
  {:pre [(and (integer? value) (not (neg? value)))]}
  (cond
    (zero? value) zero
    (= 1 value) one
    (even? value) (dbl-term (numeral (quot value 2)))
    :else (add-term (dbl-term (numeral (quot value 2))) one)))

(def two (numeral 2))
(def three (numeral 3))
(def four (numeral 4))
(def five (numeral 5))
(def six (numeral 6))
(def contradiction-code
  "Base-64 Godel-code term for the contradictory theorem `false`."
  (sjas-code/bytes->code-term [2]))

(def base-constants
  "Base constants used by the SJAS signature.

   The only numeral constants are `0` and `1`. Larger helper numerals in this
   namespace are composed with `dbl` and `add`, matching the binary
   U-grounding shape used by the later Type-A SJAS presentations."
  [zero-symbol one-symbol])

(def u-grounding-arithmetic-functions
  "Arithmetic U-grounding function symbols exposed by the SJAS signature."
  {'pred 1
   'sub 2
   'div 2
   'max 2
   'log 1
   'root 2
   'count 2
   'add 2
   'dbl 1})

(def u-grounding-functions
  "Function symbols exposed by the SJAS signature.

   The arithmetic symbols are Willard's U-grounding functions. The generated
   `code-N` symbols are compact base-64 Godel-code constructors; they are inert
   as arithmetic functions but are part of the object language because
   `tableau-proof/3` and the syntax predicates receive codes as terms."
  (merge u-grounding-arithmetic-functions
         sjas-code/code-functions))

(defn- functions-for-code-format
  "Return the formal function signature for one public code representation.

   The default compact representation needs generated `code-N` constructors.
   ADR-0071's U-Grounding representation deliberately omits them: formal codes
   are ordinary binary numerals built from the arithmetic functions already in
   the base language."
  [code-format]
  (case code-format
    :compact u-grounding-functions
    :u-grounding u-grounding-arithmetic-functions
    (throw (ex-info "Unsupported SJAS code format"
                    {:code-format code-format
                     :supported #{:compact :u-grounding}}))))

(def base-relations
  "Relations required by the SJAS language and generated program."
  {'leq 2
   'lt 2
   'mult 3
   'wff 1
   'delta-star-0-code 1
   'pi-star-1-code 1
   'sigma-star-1-code 1
   'neg-pair 2
   'axiom-member 2
   'tableau-proof 3
   'subst-code 2
   'subst-prf 4})

(def u-grounding-language
  "Base SJAS signature without a proof-profile selection."
  (language/language
    {:constants base-constants
     :functions u-grounding-functions
     :relations base-relations}))

(def tableau0-profile-language
  "Base SJAS signature selecting the ordinary-tableau self-consistency profile."
  (language/language
    {:constants base-constants
     :functions u-grounding-functions
     :relations base-relations
     :proof-profile :willard-sjas-tableau0}))

(def level1-profile-language
  "Base SJAS signature selecting the Level-1 self-consistency profile."
  (language/language
    {:constants base-constants
     :functions u-grounding-functions
     :relations base-relations
     :proof-profile :willard-sjas-level1}))

(defn not-code
  "Object-language code for the complement of `formula-code`."
  [formula-code]
  (ast/app-term 'not-code formula-code))

(defn proof-certificate
  "Encode a Proflog kernel proof term as an SJAS proof-code term.

   The default `:compact` format preserves the ADR-0063 public shape. Passing
   `{:code-format :u-grounding}` emits a single binary U-Grounding numeral term
   whose sentinel-terminated base-64 expansion carries the same proof bytes."
  ([proof]
   (proof-certificate proof {:code-format :compact}))
  ([proof {:keys [code-format]
           :or {code-format :compact}}]
   (sjas-code/proof-formal-code-term
     (willard-sjas-profile/strip-profile-wrapper proof)
     code-format)))

(defn pred-term [term] (ast/app-term 'pred term))
(defn sub-term [left right] (ast/app-term 'sub left right))
(defn div-term [left right] (ast/app-term 'div left right))
(defn max-term [left right] (ast/app-term 'max left right))
(defn log-term [term] (ast/app-term 'log term))
(defn root-term [left right] (ast/app-term 'root left right))
(defn count-term [left right] (ast/app-term 'count left right))

(defn leq [left right] (ast/pos-lit (ast/app-term 'leq left right)))
(defn lt [left right] (ast/pos-lit (ast/app-term 'lt left right)))
(defn mult [left right product] (ast/pos-lit (ast/app-term 'mult left right product)))
(defn wff [code] (ast/pos-lit (ast/app-term 'wff code)))
(defn delta-star-0-code [code] (ast/pos-lit (ast/app-term 'delta-star-0-code code)))
(defn pi-star-1-code [code] (ast/pos-lit (ast/app-term 'pi-star-1-code code)))
(defn sigma-star-1-code [code] (ast/pos-lit (ast/app-term 'sigma-star-1-code code)))
(defn neg-pair [left right] (ast/pos-lit (ast/app-term 'neg-pair left right)))
(defn axiom-member [system-code formula-code]
  (ast/pos-lit (ast/app-term 'axiom-member system-code formula-code)))
(defn tableau-proof [system-code theorem-code proof-code]
  (ast/pos-lit (ast/app-term 'tableau-proof system-code theorem-code proof-code)))
(defn subst-code [source-code substituted-code]
  (ast/pos-lit (ast/app-term 'subst-code source-code substituted-code)))
(defn subst-prf [system-code substitution-code theorem-code proof-code]
  (ast/pos-lit
    (ast/app-term 'subst-prf system-code substitution-code theorem-code proof-code)))

;; -----------------------------------------------------------------------------
;; Formula-class surface
;; -----------------------------------------------------------------------------

(defn bounded-forall
  "Build an SJAS-visible bounded universal quantifier.

   Bounded quantifiers are a frontend/classifier layer. `lower-bounded-formula`
   turns them into ordinary kernel formulas when needed."
  [binding-nom bound body]
  (list 'bounded-forall (nominal/tie binding-nom {:bound bound :body body})))

(defn bounded-exists
  "Build an SJAS-visible bounded existential quantifier."
  [binding-nom bound body]
  (list 'bounded-exists (nominal/tie binding-nom {:bound bound :body body})))

(defn- bounded-form?
  [formula tag]
  (and (seq? formula)
       (= tag (first formula))
       (nominal/tie? (second formula))))

(defn lower-bounded-formula
  "Lower SJAS bounded quantifiers to ordinary first-order kernel formulas."
  [formula]
  (case (ast/tag-of formula)
    bounded-forall (let [tied (second formula)
                         binding (:binding-nom tied)
                         {:keys [bound body]} (:body tied)]
                     (ast/forall-form
                       binding
                       (ast/implies-form
                         (leq (ast/var-term binding) bound)
                         (lower-bounded-formula body))))
    bounded-exists (let [tied (second formula)
                         binding (:binding-nom tied)
                         {:keys [bound body]} (:body tied)]
                     (ast/exists-form
                       binding
                       (ast/and-form
                         (leq (ast/var-term binding) bound)
                         (lower-bounded-formula body))))
    and (ast/and-form (lower-bounded-formula (second formula))
                      (lower-bounded-formula (nth formula 2)))
    or (ast/or-form (lower-bounded-formula (second formula))
                    (lower-bounded-formula (nth formula 2)))
    not (ast/not-form (lower-bounded-formula (second formula)))
    implies (ast/implies-form (lower-bounded-formula (second formula))
                              (lower-bounded-formula (nth formula 2)))
    forall (let [tied (second formula)]
             (ast/forall-form (:binding-nom tied)
                              (lower-bounded-formula (:body tied))))
    exists (let [tied (second formula)]
             (ast/exists-form (:binding-nom tied)
                              (lower-bounded-formula (:body tied))))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form (:binding-nom tied)
                                        (lower-bounded-formula (:body tied))))
    formula))

(declare delta-star-0?)

(defn delta-star-0?
  "Return true for the implemented Delta-star-0 formula class.

   The classifier deliberately accepts bounded SJAS quantifiers and rejects
   ordinary unbounded quantifiers."
  [formula]
  (case (ast/tag-of formula)
    true true
    false true
    pos true
    neg true
    eq true
    neq true
    and (and (delta-star-0? (second formula))
             (delta-star-0? (nth formula 2)))
    or (and (delta-star-0? (second formula))
            (delta-star-0? (nth formula 2)))
    bounded-forall (let [{:keys [body]} (:body (second formula))]
                     (delta-star-0? body))
    bounded-exists (let [{:keys [body]} (:body (second formula))]
                     (delta-star-0? body))
    false))

(defn- strip-prefix
  [formula tag]
  (loop [current formula
         stripped? false]
    (if (= tag (ast/tag-of current))
      (let [tied (second current)]
        (recur (:body tied) true))
      [stripped? current])))

(defn pi-star-1?
  "Return true for a universal prefix over a Delta-star-0 matrix."
  [formula]
  (let [[stripped? matrix] (strip-prefix formula 'forall)]
    (and stripped? (delta-star-0? matrix))))

(defn sigma-star-1?
  "Return true for an existential prefix over a Delta-star-0 matrix."
  [formula]
  (let [[stripped? matrix] (strip-prefix formula 'exists)]
    (and stripped? (delta-star-0? matrix))))

;; -----------------------------------------------------------------------------
;; Stable source coding
;; -----------------------------------------------------------------------------

(defn- canonical-term
  [term env]
  (case (ast/tag-of term)
    var (list 'var (get env (second term) (second term)))
    par (list 'par (get env (second term) (second term)))
    app (list* 'app (second term) (map #(canonical-term % env) (nnext term)))
    term))

(declare canonical-formula)

(defn- bind-name
  [env binding-nom]
  (let [label (symbol (str "v" (count env)))]
    [(assoc env binding-nom label) label]))

(defn- canonical-quantifier
  [tag tied env]
  (let [[env* label] (bind-name env (:binding-nom tied))]
    (list tag label (canonical-formula (:body tied) env*))))

(defn- canonical-bounded
  [tag tied env]
  (let [[env* label] (bind-name env (:binding-nom tied))
        {:keys [bound body]} (:body tied)]
    (list tag label (canonical-term bound env)
          (canonical-formula body env*))))

(defn- canonical-formula
  [formula env]
  (case (ast/tag-of formula)
    true '(true)
    false '(false)
    pos (list 'pos (canonical-term (second formula) env))
    neg (list 'neg (canonical-term (second formula) env))
    eq (list 'eq
             (canonical-term (second formula) env)
             (canonical-term (nth formula 2) env))
    neq (list 'neq
              (canonical-term (second formula) env)
              (canonical-term (nth formula 2) env))
    and (list 'and
              (canonical-formula (second formula) env)
              (canonical-formula (nth formula 2) env))
    or (list 'or
             (canonical-formula (second formula) env)
             (canonical-formula (nth formula 2) env))
    not (list 'not (canonical-formula (second formula) env))
    implies (list 'implies
                  (canonical-formula (second formula) env)
                  (canonical-formula (nth formula 2) env))
    forall (canonical-quantifier 'forall (second formula) env)
    once-forall (canonical-quantifier 'once-forall (second formula) env)
    exists (canonical-quantifier 'exists (second formula) env)
    bounded-forall (canonical-bounded 'bounded-forall (second formula) env)
    bounded-exists (canonical-bounded 'bounded-exists (second formula) env)
    formula))

(defn- canonical-code-nom
  "Return the kernel nom used when a formula-code variable index is decoded.

   Formula codes do not remember arbitrary host nom identities. They remember
   de Bruijn-like byte labels `v0`, `v1`, ... generated by `canonical-formula`.
   The SJAS proof profile decodes byte label `vN` as host nom `sjas-vN`; theorem
   queries use the same convention so certificates generated from source
   formulas can later be checked against structurally decoded theorem codes."
  [label]
  (let [name* (when (symbol? label) (name label))]
    (if (and name*
             (re-matches #"v[0-9]+" name*))
      (or (sjas-code/code-nom
            (inc (Long/parseLong (subs name* 1))))
          label)
      label)))

(declare canonical-term->ast canonical-formula->ast)

(defn- canonical-term->ast
  [term]
  (case (first term)
    var (ast/var-term (canonical-code-nom (second term)))
    par (ast/par-term (canonical-code-nom (second term)))
    app (list* 'app (second term) (map canonical-term->ast (nnext term)))
    term))

(defn- canonical-formula->ast
  [formula]
  (case (first formula)
    true (ast/true-form)
    false (ast/false-form)
    pos (ast/pos-lit (canonical-term->ast (second formula)))
    neg (ast/neg-lit (canonical-term->ast (second formula)))
    eq (ast/eq-lit (canonical-term->ast (second formula))
                   (canonical-term->ast (nth formula 2)))
    neq (ast/neq-lit (canonical-term->ast (second formula))
                     (canonical-term->ast (nth formula 2)))
    and (ast/and-form (canonical-formula->ast (second formula))
                      (canonical-formula->ast (nth formula 2)))
    or (ast/or-form (canonical-formula->ast (second formula))
                    (canonical-formula->ast (nth formula 2)))
    not (ast/not-form (canonical-formula->ast (second formula)))
    implies (ast/implies-form (canonical-formula->ast (second formula))
                              (canonical-formula->ast (nth formula 2)))
    forall (ast/forall-form (canonical-code-nom (second formula))
                            (canonical-formula->ast (nth formula 2)))
    once-forall (ast/once-forall-form (canonical-code-nom (second formula))
                                      (canonical-formula->ast (nth formula 2)))
    exists (ast/exists-form (canonical-code-nom (second formula))
                            (canonical-formula->ast (nth formula 2)))
    bounded-forall (bounded-forall (canonical-code-nom (second formula))
                                   (canonical-term->ast (nth formula 2))
                                   (canonical-formula->ast (nth formula 3)))
    bounded-exists (bounded-exists (canonical-code-nom (second formula))
                                   (canonical-term->ast (nth formula 2))
                                   (canonical-formula->ast (nth formula 3)))
    formula))

(defn- code-canonical-formula
  "Rewrite a source formula to the binder names used by structural code decode."
  [formula]
  (canonical-formula->ast (canonical-formula formula {})))

(defn- canonical-clause
  [{:keys [relation params body]}]
  (let [env (into {} (map-indexed (fn [idx nom]
                                    [nom (symbol (str "p" idx))])
                                  params))]
    {:relation relation
     :arity (count params)
     :body (canonical-formula body env)}))

(defn- canonical-system-source
  [{:keys [profile beta reflected-clauses]}]
  {:profile profile
   :beta (mapv #(canonical-formula % {}) beta)
   :reflected-clauses (mapv canonical-clause reflected-clauses)})

(defn- declared-coding-symbols
  "Return every object-language symbol that may appear in generated SJAS code.

   The Godel byte code for a formula refers to ordinary language symbols by
   finite indexes. Compact `code-N` constructors are treated specially by the
   code encoder, so they are declared for validation but not included in this
  formula-symbol table."
  [constants functions relations reflected-clauses external-clauses]
  (concat base-constants
          (keys u-grounding-arithmetic-functions)
          (keys base-relations)
          constants
          (keys functions)
          (keys relations)
          (map :relation reflected-clauses)
          (map :relation external-clauses)))

(defn- formula-code-term
  ([coding-context formula]
   (formula-code-term coding-context formula {} :compact))
  ([coding-context formula env]
   (formula-code-term coding-context formula env :compact))
  ([coding-context formula env code-format]
   (sjas-code/canonical-formula-formal-code-term
     coding-context
     (canonical-formula formula env)
     code-format)))

(defn formula-code
  "Return the public SJAS Godel-code term for `formula` in `system`'s language."
  [system formula]
  (formula-code-term (:coding-context system)
                     formula
                     {}
                     (:code-format system :compact)))

;; -----------------------------------------------------------------------------
;; Generated formulas and program clauses
;; -----------------------------------------------------------------------------

(defn- and*
  [formulae]
  (case (count formulae)
    0 (ast/true-form)
    1 (first formulae)
    (reduce ast/and-form formulae)))

(defn- clause->formula
  [{:keys [relation params body]}]
  (let [head (ast/pos-lit
               (apply ast/app-term relation (map ast/var-term params)))
        implication (ast/implies-form body head)]
    (reduce (fn [inner param]
              (ast/forall-form param inner))
            implication
            (reverse params))))

(defn- group-zero-formulas
  []
  [(ast/neq-lit one zero)
   (ast/neq-lit two zero)])

(defn- group-one-formulas
  []
  [(ast/eq-lit (add-term zero zero) zero)
   (ast/eq-lit (dbl-term one) two)
   (ast/eq-lit (sub-term two one) one)])

(defn- selfcons0-formula
  [system-code contradiction-code]
  (let [p (nominal/nom (lvar 'p))]
    (ast/forall-form
      p
      (ast/neg-lit
        (ast/app-term 'tableau-proof
                      system-code
                      contradiction-code
                      (ast/var-term p))))))

(defn- selfcons1-formula
  [system-code substitution-code]
  (let [x (nominal/nom (lvar 'x))
        y (nominal/nom (lvar 'y))
        p (nominal/nom (lvar 'p))
        q (nominal/nom (lvar 'q))]
    (ast/forall-form
      x
      (ast/forall-form
        y
        (ast/forall-form
          p
          (ast/forall-form
            q
            (ast/or-form
              (ast/neg-lit
                (ast/app-term 'neg-pair
                              (ast/var-term x)
                              (ast/var-term y)))
              (ast/or-form
                (ast/neg-lit
                  (ast/app-term 'subst-prf
                                system-code
                                substitution-code
                                (ast/var-term x)
                                (ast/var-term p)))
                (ast/neg-lit
                  (ast/app-term 'subst-prf
                                system-code
                                substitution-code
                                (ast/var-term y)
                                (ast/var-term q)))))))))))

(defn- selfcons1-record
  "Build Willard's fixed-point shaped Level-1 Group-3 record.

   Appendix A first forms a skeleton Gamma_1(g), then substitutes the numeral for
   the skeleton's own code into g. Proflog mirrors that shape with a free object
   variable in the skeleton and either a compact code term or a U-Grounding
   numeral code term in the final sentence."
  [coding-context system-code code-format]
  (let [g (nominal/nom (lvar 'g))
        skeleton (selfcons1-formula system-code (ast/var-term g))
        skeleton-code (formula-code-term coding-context skeleton {g 'v0} code-format)
        formula (selfcons1-formula system-code skeleton-code)]
    {:group :group-three
     :formula formula
     :code (formula-code-term coding-context formula {} code-format)
     :selfcons-skeleton-formula skeleton
     :selfcons-skeleton-code skeleton-code}))

(defn- group-three-record
  [profile coding-context system-code contradiction-code code-format]
  (case profile
    :willard-sjas-tableau0 (let [formula (selfcons0-formula system-code
                                                            contradiction-code)]
                             {:group :group-three
                              :formula formula
                              :code (formula-code-term coding-context
                                                       formula
                                                       {}
                                                       code-format)})
    :willard-sjas-level1 (selfcons1-record coding-context system-code code-format)
    (throw (ex-info "Unsupported Willard SJAS profile"
                    {:profile profile}))))

(defn- axiom-records
  [profile coding-context system-code contradiction-code code-format beta reflected-clauses]
  (let [grouped (concat
                  (map vector (repeat :group-zero) (group-zero-formulas))
                  (map vector (repeat :group-one) (group-one-formulas))
                  (map vector (repeat :group-two) beta)
                  (map vector (repeat :group-two-b)
                       (map clause->formula reflected-clauses)))
        initial (map-indexed (fn [idx [group formula]]
                               {:group group
                                :formula formula
                                :code (formula-code-term coding-context
                                                         formula
                                                         {}
                                                         code-format)})
                             grouped)
        group3 (group-three-record profile
                                   coding-context
                                   system-code
                                   contradiction-code
                                   code-format)]
    (vec (concat initial [group3]))))

(defn- compile-language
  [profile extra-relations constants extra-functions code-format]
  (language/language
    {:constants (vec (distinct (concat base-constants constants)))
     :functions (merge (functions-for-code-format code-format)
                       extra-functions)
     :relations (merge base-relations extra-relations)
     :proof-profile profile}))

(defn- symbol-index-entries
  "Expose the finite language-code symbol table to the SJAS proof profile.

   Formula codes contain numeric indexes for function and relation symbols. The
   profile may inspect those indexes relationally, but the finite symbol table is
   fixed when the user source is translated into this SJAS system."
  [coding-context]
  (apply list
         (map (fn [[sym idx]]
                [idx sym])
              (:symbol->index coding-context))))

(defn system
  "Build a finite reflected SJAS system.

   Options:
   - `:profile`: `:willard-sjas-tableau0` or `:willard-sjas-level1`;
   - `:code-format`: `:compact` for `code-N` terms or `:u-grounding` for
     ordinary binary numeral codes;
   - `:constants`: extra user constants;
   - `:functions`: extra user function declarations;
   - `:relations`: extra user relation declarations;
   - `:beta`: finite reflected proper axioms;
   - `:reflected-clauses`: user clauses included in the reflected basis;
   - `:external-clauses`: ordinary Proflog clauses outside the self-reference.
   "
  [{:keys [profile code-format constants functions relations beta reflected-clauses external-clauses]
    :or {profile :willard-sjas-tableau0
         code-format :compact
         constants []
         functions {}
         relations {}
         beta []
         reflected-clauses []
         external-clauses []}}]
  (let [reflected-clauses (vec reflected-clauses)
        external-clauses (vec external-clauses)
        beta (vec beta)
        source {:profile profile
                :beta beta
                :reflected-clauses reflected-clauses}
        derived-relations (into {}
                                (map (fn [{:keys [relation params]}]
                                       [relation (count params)]))
                                (concat reflected-clauses external-clauses))
        coding-context (sjas-code/context
                         (declared-coding-symbols constants
                                                 functions
                                                 (merge relations derived-relations)
                                                 reflected-clauses
                                                 external-clauses))
        canonical-source (canonical-system-source source)
        system-code (sjas-code/system-formal-code-term coding-context
                                                       canonical-source
                                                       code-format)
        contradiction-code (sjas-code/canonical-formula-formal-code-term
                             coding-context
                             (canonical-formula (ast/false-form) {})
                             code-format)
        axioms (axiom-records profile
                              coding-context
                              system-code
                              contradiction-code
                              code-format
                              beta
                              reflected-clauses)
        lang (compile-language profile
                               (merge relations derived-relations)
                               constants
                               functions
                               code-format)
        clauses (concat reflected-clauses
                        external-clauses)
        ;; U-grounding arithmetic is now interpreted by the SJAS profile.
        ;; Keeping the reflected Group-1 formulas in `axiom-member` preserves
        ;; their SJAS codes, but adding them as ordinary positive equalities in
        ;; every theorem antecedent would let the free-constructor equality
        ;; rule misread true arithmetic equations as constructor clashes.
        theorem-axioms (remove #(= :group-one (:group %)) axioms)
        ;; Formula codes remember canonical binder indexes rather than arbitrary
        ;; source nom identities. The proof predicate must later reconstruct the
        ;; same axiom antecedent from `system-code`, so theorem queries use that
        ;; canonical binder convention at the source-compilation boundary.
        axiom-formula (and* (map (comp code-canonical-formula :formula)
                                 theorem-axioms))
        group3 (first (filter #(= :group-three (:group %)) axioms))
        ;; The user-facing program includes both reflected and external clauses.
        ;; Proof-predicate validation is narrower: it may use only clauses whose
        ;; source appears in the encoded SJAS system. The profile now recovers
        ;; reflected proof-time calls from `system-code`, so no reflected
        ;; compiled-program side table is stored in the registry.
        registry (atom {:sjas/system-code system-code
                        :sjas/code-format code-format
                        :sjas/symbol-index-entries (symbol-index-entries coding-context)})
        program (assoc (language/compile-program lang clauses)
                       :sjas/registry registry)]
    {:profile profile
     :code-format code-format
     :language lang
     :program program
     :system-code system-code
     :contradiction-code contradiction-code
     :selfcons-skeleton-code (:selfcons-skeleton-code group3)
     :selfcons-skeleton-formula (:selfcons-skeleton-formula group3)
     :coding-context coding-context
     :axioms axioms
     :group-three group3
     :axiom-formula axiom-formula
     :reflected-clauses (vec reflected-clauses)
     :external-clauses (vec external-clauses)}))

;; -----------------------------------------------------------------------------
;; Source-facing SJAS builder
;; -----------------------------------------------------------------------------

(defn- arity-entry
  "Parse one source declaration entry like `(symbol arity)`."
  [form]
  (when-not (and (seq? form)
                 (symbol? (first form))
                 (= 2 (count form))
                 (integer? (second form))
                 (<= 0 (second form)))
    (throw (ex-info "Expected a declaration entry like (symbol arity)"
                    {:form form})))
  [(first form) (second form)])

(defn- parse-source-language
  "Parse the SJAS source-builder language extension section."
  [sections]
  (reduce (fn [acc section]
            (when-not (seq? section)
              (throw (ex-info "Malformed SJAS language section"
                              {:section section})))
            (let [head (first section)
                  entries (rest section)]
              (case head
                constants (update acc :constants into entries)
                functions (update acc :functions merge (into {} (map arity-entry entries)))
                relations (update acc :relations merge (into {} (map arity-entry entries)))
                (throw (ex-info "Unknown SJAS language section"
                                {:section section
                                 :known-sections '(constants functions relations)})))))
          {:constants []
           :functions {}
           :relations {}}
          sections))

(defn- parse-system-source-section
  "Classify one top-level `system-source` section."
  [section]
  (when-not (seq? section)
    (throw (ex-info "Malformed SJAS source section"
                    {:section section})))
  (let [head (first section)
        entries (rest section)]
    (case head
      language (assoc (parse-source-language entries) :kind :language)
      beta {:kind :beta :forms (vec entries)}
      reflected {:kind :reflected :forms (vec entries)}
      external {:kind :external :forms (vec entries)}
      (throw (ex-info "Unknown SJAS source section"
                      {:section section
                       :known-sections '(language beta reflected external)})))))

(defn- collect-system-source
  [sections]
  (reduce (fn [acc section]
            (let [{:keys [kind] :as parsed} (parse-system-source-section section)]
              (case kind
                :language (-> acc
                              (update :constants into (:constants parsed))
                              (update :functions merge (:functions parsed))
                              (update :relations merge (:relations parsed)))
                :beta (update acc :beta into (:forms parsed))
                :reflected (update acc :reflected into (:forms parsed))
                :external (update acc :external into (:forms parsed)))))
          {:constants []
           :functions {}
           :relations {}
           :beta []
           :reflected []
           :external []}
          sections))

(defmacro system-source
  "Build an SJAS system from Clojure-readable prefix source.

   Example:
   (system-source
     {:profile :willard-sjas-tableau0}
     (language
       (relations (demo 1)))
     (beta
       (= one one))
     (reflected
       (|- (demo x) (= x one))))

   The macro lowers beta formulas through `proflog.frontend/q` and user clauses
   through `proflog.frontend/clauses`, then delegates to `system` so Group-3 and
   object-language axiom membership are still generated in one place."
  [opts & sections]
  (let [{:keys [constants functions relations beta reflected external]}
        (collect-system-source sections)
        reflected-code (if (seq reflected)
                         `(frontend/clauses ~@reflected)
                         [])
        external-code (if (seq external)
                        `(frontend/clauses ~@external)
                        [])]
    `(system
       (merge ~opts
              {:constants '~(vec constants)
               :functions '~functions
               :relations '~relations
               :beta [~@(map (fn [formula]
                                `(frontend/q ~formula))
                              beta)]
               :reflected-clauses ~reflected-code
               :external-clauses ~external-code}))))

(defn theorem-query
  "Wrap `formula` so it is proved from the generated finite SJAS axiom basis."
  [system formula]
  (ast/implies-form (:axiom-formula system) (code-canonical-formula formula)))

(defn query-succeeds
  "Prove `formula` from an SJAS system's generated axiom basis."
  ([system formula]
   (query-succeeds system formula {}))
  ([system formula {:keys [proof-limit fuel]
                   :or {proof-limit 1}}]
   (query/query-succeeds
     (:program system)
     (theorem-query system formula)
     proof-limit
     fuel)))

(defn query-answers
  "Export answer bindings for an SJAS query under the SJAS theory profile."
  ([system formula answer-vars]
   (query-answers system formula answer-vars {}))
  ([system formula answer-vars opts]
   (binding [answer-overlay/*theory-profile-closeo*
             willard-sjas-profile/willard-sjas-answer-theory-closeo
             gamma/*closed-term-depth-cap* 0
             gamma/*closed-term-count-cap* 0]
     (answers/query-answers (:program system) formula answer-vars opts))))

(defn bounded-contradiction-probe
  "Run a bounded Level-1 complement-proof probe and record wall-clock duration."
  [system {:keys [fuel proof-limit]
           :or {fuel 32
                proof-limit 1}}]
  (let [started (System/nanoTime)
        contradiction (tableau-proof (:system-code system)
                                     (:contradiction-code system)
                                     (proof-certificate
                                       'sjas-axiom
                                       {:code-format (:code-format system
                                                                   :compact)}))
        proofs (query/query-succeeds (:program system) contradiction proof-limit fuel)
        duration-ms (long (/ (- (System/nanoTime) started) 1000000))]
    {:result (if (seq proofs) :found :not-found)
     :proof-count (count proofs)
     :fuel fuel
     :proof-limit proof-limit
     :duration-ms duration-ms}))
