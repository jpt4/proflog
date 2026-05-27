(ns proflog.frontend
  "Thin source-facing Proflog frontend.

   This namespace accepts a Clojure-readable prefix surface and emits the
   existing backend forms from `proflog.ast` and `proflog.language`. It does not
   evaluate programs. `language` builds a reusable language declaration,
   `proflog` translates visible source clauses into compiled programs, and `q`
   translates visible closed queries into kernel-facing formulas.
   `answer-query` binds visible answer variables and returns the query formula
   plus the answer-variable vector expected by `proflog.answers`. `run` is the
   ergonomic frontend evaluator for open answer queries."
  (:require [proflog.ast :as ast]
            [proflog.answers :as answers]
            [proflog.language :as backend-language]))

;; -----------------------------------------------------------------------------
;; Error reporting
;;
;; Frontend errors happen during macro expansion, before the kernel sees any
;; formula. We attach the original source-shaped data wherever possible so a
;; reader can connect a diagnostic back to the visible Proflog form.
;; -----------------------------------------------------------------------------

(defn- malformed!
  "Raise a structured frontend error.

   The data map is intentionally part of the public debugging story: examples and
   tests can inspect it without parsing an English error message."
  [message data]
  (throw (ex-info message data)))

;; -----------------------------------------------------------------------------
;; Language declarations
;;
;; The frontend language form is syntax sugar over `proflog.language/language`.
;; It keeps examples close to a mathematical signature:
;;
;;   constants: nullary function symbols
;;   functions: term-forming symbols with arities
;;   relations: predicate symbols with arities
;;   proof-profile: optional query-time proof system selection
;;
;; The result is still a reusable backend language value, so several programs can
;; be compiled against the same signature.
;; -----------------------------------------------------------------------------

(defn- arity-entry
  "Parse one `(symbol arity)` declaration entry."
  [form]
  (when-not (and (seq? form)
                 (symbol? (first form))
                 (= 2 (count form))
                 (integer? (second form))
                 (<= 0 (second form)))
    (malformed! "Expected a declaration entry like (symbol arity)"
                {:form form}))
  [(first form) (second form)])

(defn- parse-language-section
  "Parse one declaration section such as `(functions (s 1))`."
  [section]
  (when-not (seq? section)
    (malformed! "Malformed language section" {:section section}))
  (let [head (first section)
        entries (rest section)]
    (case head
      constants {:constants (vec entries)}
      functions {:functions (into {} (map arity-entry entries))}
      relations {:relations (into {} (map arity-entry entries))}
      proof-profile (do
                      (when-not (and (= 1 (count entries))
                                     (keyword? (first entries)))
                        (malformed! "Expected a proof profile section like (proof-profile :profile-name)"
                                    {:section section}))
                      {:proof-profile (first entries)})
      (malformed! "Unknown language section"
                  {:section section
                   :known-sections '(constants functions relations proof-profile)}))))

(defn- parse-language-declaration
  "Merge all frontend declaration sections into the backend declaration map."
  [sections]
  (reduce (fn [declaration section]
            (merge-with (fn [left right]
                          (cond
                            (vector? left) (into left right)
                            (map? left) (merge left right)
                            :else right))
                        declaration
                        (parse-language-section section)))
          {}
          sections))

(defmacro language
  "Build a reusable frontend language declaration.

   Example:
   (language
     (constants zero)
     (functions (s 1))
     (relations (p 1)))"
  [& sections]
  `(backend-language/language
     ~(list 'quote (parse-language-declaration sections))))

;; -----------------------------------------------------------------------------
;; Source clauses
;;
;; `(|- head body)` means a real Proflog relation clause. The compiled program
;; will contain that relation, and Fitting's Procedure Call Rule can open it.
;;
;; `(:= head body)` means a frontend definition. It is a macro-level formula
;; abbreviation and is inlined before compilation. Recursive `:=` is rejected
;; because that would be a second, implicit procedure-call semantics.
;; -----------------------------------------------------------------------------

(defn- parse-head
  "Parse a frontend head `(relation x y)` into a relation and parameter names.

   The current surface requires variable-only heads. Prolog-style constructor
   patterns are written as equalities in the body so the backend always receives
   the same simple relation-parameter shape."
  [head]
  (when-not (and (seq? head)
                 (symbol? (first head))
                 (every? symbol? (rest head)))
    (malformed! "Expected a predicate head like (relation x y)"
                {:head head}))
  {:relation (first head)
   :params (vec (rest head))})

(defn- parse-clause-form
  "Classify a frontend source form as either an inline definition or a relation."
  [form]
  (when-not (and (seq? form)
                 (= 3 (count form)))
    (malformed! "Expected a frontend clause like (|- head body) or (:= head body)"
                {:form form}))
  (let [op (first form)
        {:keys [relation params]} (parse-head (second form))]
    (cond
      (= := op) {:kind :definition
                 :relation relation
                 :params params
                 :body (nth form 2)}
      (= '|- op) {:kind :relation
                  :relation relation
                  :params params
                  :body (nth form 2)}
      :else (malformed! "Unknown frontend clause operator"
                        {:operator op
                         :form form
                         :known-operators '(:= |-)}))))

;; -----------------------------------------------------------------------------
;; Translation to backend AST constructors
;;
;; These emitters run at macro expansion time and produce Clojure code that will
;; construct tagged AST values at runtime. The important semantic distinction is
;; the environment `env`: a symbol in `env` is an object-language variable, while
;; an unbound symbol is a nullary function symbol, i.e. a constant.
;; -----------------------------------------------------------------------------

(defn- register-nom!
  "Remember that a frontend variable name must become a fresh backend nom."
  [noms sym]
  (swap! noms conj sym)
  sym)

(declare emit-formula)

(defn- emit-term
  "Emit backend-constructor code for a frontend term.

   Examples:
   - a bound `x` emits `(ast/var-term x)`;
   - an unbound `zero` emits `(ast/app-term 'zero)`;
   - `(s zero)` emits an application term with one argument."
  [term env helpers noms]
  (cond
    ;; Some mathematical profiles, including Willard's SJAS U-grounding, use
    ;; constants whose printed object-language names are digits. Clojure reads
    ;; `0` and `1` as numbers rather than symbols, so the frontend accepts
    ;; non-negative integer literals and lowers them to same-spelled constant
    ;; symbols. Profile-specific builders decide which such constants are
    ;; actually declared.
    (and (integer? term) (not (neg? term)))
    `(ast/app-term '~(symbol (str term)))

    (symbol? term)
    (if-let [bound (get env term)]
      bound
      `(ast/app-term '~term))

    (seq? term)
    (let [head (first term)]
      (when-not (symbol? head)
        (malformed! "Expected a function symbol in term position"
                    {:term term}))
      `(ast/app-term '~head ~@(map #(emit-term % env helpers noms)
                                   (rest term))))

    :else
    (malformed! "Unsupported frontend term" {:term term})))

(defn- emit-nary-formula
  "Emit a left-associated binary AST tree for n-ary frontend `and` / `or`.

   The backend core has binary conjunction and disjunction. The frontend accepts
   zero or more operands because hand-written examples are easier to scan that
   way; empty `and` is truth and empty `or` is falsehood."
  [constructor empty-form args env helpers noms stack]
  (case (count args)
    0 empty-form
    1 (emit-formula (first args) env helpers noms stack)
    (reduce (fn [left right]
              `(~constructor ~left ~right))
            (map #(emit-formula % env helpers noms stack) args))))

(defn- emit-quantifier
  "Emit nested backend quantifiers for a frontend binding vector.

   Each bound source name receives a generated nom and shadows any outer binding
   of the same source symbol. The generated noms are registered so the macro can
   wrap the final constructor expression in `ast/nom`."
  [constructor bindings body env helpers noms stack]
  (when-not (and (vector? bindings)
                 (seq bindings)
                 (every? symbol? bindings))
    (malformed! "Expected a nonempty vector of quantifier bindings"
                {:bindings bindings
                 :body body}))
  (let [pairs (mapv (fn [binding]
                      [binding (register-nom! noms (gensym (str (name binding) "__")))])
                    bindings)
        scoped-env (reduce (fn [acc [source generated]]
                             (assoc acc source `(ast/var-term ~generated)))
                           env
                           pairs)
        body-code (emit-formula body scoped-env helpers noms stack)]
    (reduce (fn [inner [_ generated]]
              `(~constructor ~generated ~inner))
            body-code
            (reverse pairs))))

(defn- emit-helper-call
  "Inline one `:=` helper call by translating its body under an argument env.

   This is textual substitution at the formula level, not a runtime call. The
   `stack` set rejects direct or mutual recursive helper expansion."
  [helper form env helpers noms stack]
  (let [{:keys [relation params body]} helper
        args (rest form)]
    (when (contains? stack relation)
      (malformed! "Recursive frontend helper definitions are not supported"
                  {:helper relation
                   :call form
                   :stack stack}))
    (when-not (= (count params) (count args))
      (malformed! "Arity mismatch for frontend helper call"
                  {:helper relation
                   :expected-arity (count params)
                   :actual-arity (count args)
                   :call form}))
    (let [helper-env (zipmap params
                             (map #(emit-term % env helpers noms) args))]
      (emit-formula body helper-env helpers noms (conj stack relation)))))

(defn- emit-formula
  "Emit backend-constructor code for a frontend formula.

   Relation calls are the fallback case for ordinary symbolic heads, so
   `(p x)` becomes `(ast/pos-lit (ast/app-term 'p ...))`. Formula connectives
   and quantifiers are recognized before that fallback."
  [formula env helpers noms stack]
  (cond
    (true? formula) `(ast/true-form)
    (false? formula) `(ast/false-form)

    (seq? formula)
    (let [op (first formula)
          args (rest formula)]
      (cond
        (= '= op)
        (do
          (when-not (= 2 (count args))
            (malformed! "Expected (= left right)" {:formula formula}))
          `(ast/eq-lit ~(emit-term (first args) env helpers noms)
                       ~(emit-term (second args) env helpers noms)))

        (= '!= op)
        (do
          (when-not (= 2 (count args))
            (malformed! "Expected (!= left right)" {:formula formula}))
          `(ast/neq-lit ~(emit-term (first args) env helpers noms)
                        ~(emit-term (second args) env helpers noms)))

        (= 'and op)
        (emit-nary-formula `ast/and-form `(ast/true-form) args env helpers noms stack)

        (= 'or op)
        (emit-nary-formula `ast/or-form `(ast/false-form) args env helpers noms stack)

        (= 'not op)
        (do
          (when-not (= 1 (count args))
            (malformed! "Expected (not formula)" {:formula formula}))
          `(ast/not-form ~(emit-formula (first args) env helpers noms stack)))

        (= 'implies op)
        (do
          (when-not (= 2 (count args))
            (malformed! "Expected (implies antecedent consequent)"
                        {:formula formula}))
          `(ast/implies-form ~(emit-formula (first args) env helpers noms stack)
                             ~(emit-formula (second args) env helpers noms stack)))

        (= 'forall op)
        (do
          (when-not (= 2 (count args))
            (malformed! "Expected (forall [x] body)" {:formula formula}))
          (emit-quantifier `ast/forall-form (first args) (second args)
                           env helpers noms stack))

        (= 'exists op)
        (do
          (when-not (= 2 (count args))
            (malformed! "Expected (exists [x] body)" {:formula formula}))
          (emit-quantifier `ast/exists-form (first args) (second args)
                           env helpers noms stack))

        (contains? helpers op)
        (emit-helper-call (get helpers op) formula env helpers noms stack)

        (symbol? op)
        `(ast/pos-lit
           (ast/app-term '~op ~@(map #(emit-term % env helpers noms) args)))

        :else
        (malformed! "Unsupported frontend formula" {:formula formula})))

    :else
    (malformed! "Unsupported frontend formula" {:formula formula})))

(defn- helper-map
  "Collect all `:=` definitions by helper name."
  [parsed-forms]
  (reduce (fn [helpers {:keys [kind relation params] :as parsed}]
            (if (= :definition kind)
              (do
                (when (contains? helpers relation)
                  (malformed! "Duplicate frontend helper definition"
                              {:helper relation}))
                (assoc helpers relation parsed))
              helpers))
          {}
          parsed-forms))

(defn- emit-relation-clause
  "Emit backend-constructor code for one real `|-` relation clause."
  [{:keys [relation params body]} helpers noms]
  (doseq [param params]
    (register-nom! noms param))
  (let [env (into {} (map (fn [param]
                            [param `(ast/var-term ~param)])
                          params))]
    `(ast/clause '~relation
                 [~@params]
                 ~(emit-formula body env helpers noms #{}))))

(defn- emit-clause-vector
  "Emit a vector of backend clause constructors for visible frontend source."
  [source-forms]
  (let [parsed (mapv parse-clause-form source-forms)
        helpers (helper-map parsed)
        relation-forms (filterv #(= :relation (:kind %)) parsed)
        noms (atom [])
        clauses (mapv #(emit-relation-clause % helpers noms) relation-forms)
        unique-noms (vec (distinct @noms))]
    (when-not (seq relation-forms)
      (malformed! "A Proflog program must contain at least one relation clause"
                  {:source-forms source-forms}))
    `(ast/nom ~@unique-noms
       [~@clauses])))

(defmacro clauses
  "Translate visible prefix Proflog source clauses without compiling a program.

   This is the hook used by higher-level builders such as `proflog.willard-sjas`.
   It keeps frontend helper expansion and variable binding in one place while
   letting another namespace decide how generated clauses and user clauses are
   assembled into a final program."
  [& source-forms]
  (emit-clause-vector source-forms))

(defmacro proflog
  "Compile visible prefix Proflog source clauses against a reusable language.

   `(:= head body)` introduces an inline source-level helper.
   `(|- head body)` introduces a real Proflog relation clause."
  [frontend-language & source-forms]
  `(backend-language/compile-program
     ~frontend-language
     ~(emit-clause-vector source-forms)))

(defmacro q
  "Translate one visible frontend query/formula into a backend formula."
  [formula]
  (let [noms (atom [])
        code (emit-formula formula {} {} noms #{})
        unique-noms (vec (distinct @noms))]
    `(ast/nom ~@unique-noms
       ~code)))

;; -----------------------------------------------------------------------------
;; Open answer queries
;;
;; `pf/run` is the tutorial-facing surface:
;;
;;   (pf/run program [x] (p x) opts)
;;
;; It mirrors miniKanren's answer-variable binder but does not introduce a new
;; prover. The macro emits exactly the query formula and answer-var vector that
;; `answers/query-answers` already expects. `pf/answer-query` exposes that pair
;; for profiled or diagnostic evaluators that are intentionally not the default
;; answer surface.
;; -----------------------------------------------------------------------------

(defn- validate-answer-query-bindings
  "Validate the answer-variable vector used by `run` and `answer-query`."
  [bindings]
  (when-not (and (vector? bindings)
                 (seq bindings)
                 (every? symbol? bindings))
    (malformed! "Expected answer-query bindings like [x y]"
                {:bindings bindings}))
  (when-not (= (count bindings) (count (distinct bindings)))
    (malformed! "Duplicate frontend answer-query bindings"
                {:bindings bindings}))
  bindings)

(defn- emit-answer-query-map
  "Emit shared expansion data for `run` and `answer-query`.

   Answer variables are placed in the initial environment so uses of those names
   in the query become `(var ...)` terms. Other unbound source symbols remain
   constants, preserving the frontend rule used by `q`."
  [bindings formula]
  (let [bindings (validate-answer-query-bindings bindings)
        noms (atom (vec bindings))
        env (into {} (map (fn [binding]
                            [binding `(ast/var-term ~binding)])
                          bindings))
        code (emit-formula formula env {} noms #{})
        unique-noms (vec (distinct @noms))]
    {:bindings bindings
     :code code
     :unique-noms unique-noms}))

(defmacro answer-query
  "Bind visible answer variables for a frontend query.

   Returns a map compatible with the public answer APIs:
   {:query formula :answer-vars [noms...]}."
  [bindings formula]
  (let [{:keys [bindings code unique-noms]} (emit-answer-query-map bindings formula)]
    `(ast/nom ~@unique-noms
       {:query ~code
        :answer-vars [~@bindings]})))

(defmacro run
  "Evaluate a frontend open answer query.

   `run` is a thin frontend wrapper around `answers/query-answers`; it does not
   add a second evaluator. The binding vector names exactly the answer variables
   whose bindings may appear in returned records.

   Example:
   (run program [x] (p x) {:proof-limit 1})"
  ([program bindings formula]
   `(run ~program ~bindings ~formula {}))
  ([program bindings formula opts]
   (let [{:keys [bindings code unique-noms]} (emit-answer-query-map bindings formula)]
     `(ast/nom ~@unique-noms
        (answers/query-answers
          ~program
          ~code
          [~@bindings]
          ~opts)))))
