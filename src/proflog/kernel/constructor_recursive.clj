(ns proflog.kernel.constructor-recursive
  "Prototype constructor-recursive proof layer.

   This namespace deliberately sits beside the ordinary tableau kernel. It
   consumes the generic guarded-clause IR produced by `proflog.language` and
   tries to close constructor-recursive call frontiers by guard saturation plus
   recursive definite-program descent. It does not inspect relation names or
   constructor names.

   The layer is intentionally conservative:

   - positive defined calls are solved from guarded alternatives;
   - equality guards use free-constructor unification with an occurs check;
   - disequality guards only succeed when the walked terms are rigidly
     constructor-different;
   - residual settlement currently discharges negative procedure-call
     residuals by proving their atoms through this layer and leaves anything
     else explicit.

   Successful searches return explicit proof terms under
   `constructor-recursive-*` tags. They are prototype proof objects for the
   layer boundary, not ordinary kernel proof terms."
  (:require [proflog.ast :as ast]))

(def default-fuel 64)

(defn- lookup-guarded-clause
  [program relation]
  (some (fn [clause]
          (when (= relation (:relation clause))
            clause))
        (:guarded-clause-list program)))

(defn- defined-relation?
  [program relation]
  (boolean (lookup-guarded-clause program relation)))

(defn- term-vars
  [term]
  (case (ast/tag-of term)
    var #{(second term)}
    par #{}
    app (reduce into #{} (map term-vars (nnext term)))
    #{}))

(defn- formula-vars
  [formula]
  (case (ast/tag-of formula)
    true #{}
    false #{}
    pos (term-vars (second formula))
    neg (term-vars (second formula))
    eq (into (term-vars (second formula))
             (term-vars (nth formula 2)))
    neq (into (term-vars (second formula))
              (term-vars (nth formula 2)))
    and (into (formula-vars (second formula))
              (formula-vars (nth formula 2)))
    or (into (formula-vars (second formula))
             (formula-vars (nth formula 2)))
    forall (disj (formula-vars (:body (second formula)))
                 (:binding-nom (second formula)))
    once-forall (disj (formula-vars (:body (second formula)))
                      (:binding-nom (second formula)))
    exists (disj (formula-vars (:body (second formula)))
                 (:binding-nom (second formula)))
    #{}))

(defn- guarded-alternative-vars
  [guarded]
  (reduce into
          (set (map :binding-nom (:scope guarded)))
          (concat
            (map formula-vars (:guards guarded))
            (map formula-vars (:calls guarded))
            (map formula-vars (:residuals guarded)))))

(defn- fresh-var
  [nom]
  (symbol (str "cr$" (hash nom) "$" (gensym))))

(defn- freshen-term
  [renaming term]
  (case (ast/tag-of term)
    var (ast/var-term (get renaming (second term) (second term)))
    par term
    app (apply ast/app-term
               (second term)
               (map #(freshen-term renaming %) (nnext term)))
    term))

(defn- freshen-formula
  [renaming formula]
  (case (ast/tag-of formula)
    true formula
    false formula
    pos (ast/pos-lit (freshen-term renaming (second formula)))
    neg (ast/neg-lit (freshen-term renaming (second formula)))
    eq (ast/eq-lit (freshen-term renaming (second formula))
                   (freshen-term renaming (nth formula 2)))
    neq (ast/neq-lit (freshen-term renaming (second formula))
                     (freshen-term renaming (nth formula 2)))
    and (ast/and-form (freshen-formula renaming (second formula))
                      (freshen-formula renaming (nth formula 2)))
    or (ast/or-form (freshen-formula renaming (second formula))
                    (freshen-formula renaming (nth formula 2)))
    exists (let [tied (second formula)]
             (ast/exists-form (get renaming (:binding-nom tied) (:binding-nom tied))
                              (freshen-formula renaming (:body tied))))
    forall (let [tied (second formula)]
             (ast/forall-form (get renaming (:binding-nom tied) (:binding-nom tied))
                              (freshen-formula renaming (:body tied))))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form (get renaming (:binding-nom tied)
                                             (:binding-nom tied))
                                        (freshen-formula renaming (:body tied))))
    formula))

(defn- freshen-guarded-alternative
  [params guarded]
  (let [renaming (into {}
                       (map (fn [nom]
                              [nom (fresh-var nom)]))
                       (into (set params)
                             (guarded-alternative-vars guarded)))]
    {:params (mapv #(get renaming %) params)
     :scope (mapv (fn [entry]
                    (update entry :binding-nom #(get renaming % %)))
                  (:scope guarded))
     :guards (mapv #(freshen-formula renaming %) (:guards guarded))
     :calls (mapv #(freshen-formula renaming %) (:calls guarded))
     :residuals (mapv #(freshen-formula renaming %) (:residuals guarded))}))

(defn- walk-term
  [subst term]
  (case (ast/tag-of term)
    var (if-let [value (get subst (second term))]
          (recur subst value)
          term)
    par term
    app (apply ast/app-term
               (second term)
               (map #(walk-term subst %) (nnext term)))
    term))

(defn- occurs?
  [subst binding-nom term]
  (let [term (walk-term subst term)]
    (case (ast/tag-of term)
      var (= binding-nom (second term))
      app (boolean (some #(occurs? subst binding-nom %) (nnext term)))
      false)))

(declare unify-term)

(defn- unify-term-list
  [subst left right]
  (cond
    (and (empty? left) (empty? right))
    [subst '()]

    (or (empty? left) (empty? right))
    nil

    :else
    (when-let [[subst head-proof] (unify-term subst (first left) (first right))]
      (when-let [[subst tail-proof] (unify-term-list subst (rest left) (rest right))]
        [subst (list 'cr-args head-proof tail-proof)]))))

(defn- bind-var
  [subst binding-nom value]
  (when-not (occurs? subst binding-nom value)
    [(assoc subst binding-nom value) '(cr-bind)]))

(defn- unify-term
  [subst left right]
  (let [left (walk-term subst left)
        right (walk-term subst right)]
    (cond
      (= left right)
      [subst '(cr-refl)]

      (= 'var (ast/tag-of left))
      (bind-var subst (second left) right)

      (= 'var (ast/tag-of right))
      (bind-var subst (second right) left)

      (and (= 'app (ast/tag-of left))
           (= 'app (ast/tag-of right))
           (= (second left) (second right)))
      (when-let [[subst arg-proof] (unify-term-list subst (nnext left) (nnext right))]
        [subst (list 'cr-decompose arg-proof)])

      :else
      nil)))

(declare rigid-different?)

(defn- rigid-different-list?
  [subst left right]
  (cond
    (and (empty? left) (empty? right)) false
    (or (empty? left) (empty? right)) true
    :else (or (rigid-different? subst (first left) (first right))
              (rigid-different-list? subst (rest left) (rest right)))))

(defn- rigid-different?
  [subst left right]
  (let [left (walk-term subst left)
        right (walk-term subst right)]
    (and (= 'app (ast/tag-of left))
         (= 'app (ast/tag-of right))
         (or (not= (second left) (second right))
             (rigid-different-list? subst (nnext left) (nnext right))))))

(defn- solve-eq-guard
  [state guard]
  (let [[_ left right] guard]
    (when-let [[subst proof] (unify-term (:subst state) left right)]
      [(assoc state :subst subst)
       (list 'constructor-recursive-guard guard proof)])))

(defn- solve-neq-guard
  [state guard]
  (let [[_ left right] guard]
    (when (rigid-different? (:subst state) left right)
      [state (list 'constructor-recursive-guard guard '(cr-rigid-neq))])))

(defn- solve-guard
  [state guard]
  (case (ast/tag-of guard)
    eq (solve-eq-guard state guard)
    neq (solve-neq-guard state guard)
    nil))

(defn- step-fuel
  [state]
  (when (pos? (:fuel state))
    (update state :fuel dec)))

(declare solve-formula solve-atom)

(defn- solve-sequence
  [program state formulas]
  (if (empty? formulas)
    (list [state '(constructor-recursive-seq-done)])
    (for [[head-state head-proof] (solve-formula program state (first formulas))
          [tail-state tail-proof] (solve-sequence program head-state (rest formulas))]
      [tail-state
       (list 'constructor-recursive-seq-step head-proof tail-proof)])))

(defn- solve-guards
  [state guards]
  (if (empty? guards)
    (list [state '(constructor-recursive-guards-done)])
    (when-let [[state head-proof] (solve-guard state (first guards))]
      (for [[state tail-proof] (solve-guards state (rest guards))]
        [state (list 'constructor-recursive-guards-step head-proof tail-proof)]))))

(defn- solve-residual
  [program state formula]
  (case (ast/tag-of formula)
    true (list [state '(constructor-recursive-true)])
    false '()
    eq (if-let [[state proof] (solve-eq-guard state formula)]
         (list [state proof])
         '())
    neq (if-let [[state proof] (solve-neq-guard state formula)]
          (list [state proof])
          '())
    and (for [[left-state left-proof] (solve-formula program state (second formula))
              [right-state right-proof] (solve-formula program left-state (nth formula 2))]
          [right-state (list 'constructor-recursive-and left-proof right-proof)])
    or (concat
         (solve-formula program state (second formula))
         (solve-formula program state (nth formula 2)))
    pos (let [atom (walk-term (:subst state) (second formula))]
          (when (defined-relation? program (second atom))
            (solve-atom program state atom)))
    ;; A negative defined-call residual closes when its atom is constructively
    ;; witnessed. This is the residual-settlement analogue of the ordinary
    ;; kernel's negative procedure-call closure for a deferred call frontier.
    neg (let [atom (walk-term (:subst state) (second formula))]
          (when (defined-relation? program (second atom))
            (for [[state proof] (solve-atom program state atom)]
              [state (list 'constructor-recursive-neg-residual formula proof)])))
    '()))

(defn- solve-formula
  [program state formula]
  (solve-residual program state formula))

(defn- bind-params
  [state params args]
  (loop [state state
         params params
         args args
         proofs []]
    (cond
      (and (empty? params) (empty? args))
      [state (list 'constructor-recursive-bind-params proofs)]

      (or (empty? params) (empty? args))
      nil

      :else
      (when-let [[subst proof] (unify-term (:subst state)
                                           (ast/var-term (first params))
                                           (first args))]
        (recur (assoc state :subst subst)
               (rest params)
               (rest args)
               (conj proofs proof))))))

(defn- solve-alternative
  [program state params args guarded]
  (let [guarded (freshen-guarded-alternative params guarded)]
    (when-let [[state bind-proof] (bind-params state (:params guarded) args)]
      (for [[guard-state guard-proof] (solve-guards state (:guards guarded))
            [call-state call-proof] (solve-sequence program guard-state (:calls guarded))
            [residual-state residual-proof] (solve-sequence program call-state (:residuals guarded))]
        [residual-state
         (list 'constructor-recursive-alt
               bind-proof
               guard-proof
               call-proof
               residual-proof)]))))

(defn- solve-atom
  [program state atom]
  (when-let [state (step-fuel state)]
    (let [atom (walk-term (:subst state) atom)
          relation (second atom)
          args (vec (nnext atom))
          {:keys [params guarded-alternatives]} (lookup-guarded-clause program relation)]
      (when guarded-alternatives
        (mapcat
          (fn [guarded]
            (for [[state proof] (solve-alternative program state params args guarded)]
              [state (list 'constructor-recursive-call atom proof)]))
          guarded-alternatives)))))

(defn solve-query
  "Return up to `limit` constructor-recursive proofs for one positive atom.

   Each result has `:subst`, `:proof`, and remaining `:fuel`. The query may
  contain object-language variables; callers decide which bindings to export."
  ([program atom]
   (solve-query program atom {}))
  ([program atom {:keys [fuel limit subst]
                  :or {fuel default-fuel
                       limit 10
                       subst {}}}]
   (take limit
         (for [[state proof] (solve-atom program {:subst subst :fuel fuel} atom)]
           (assoc state :proof (list 'constructor-recursive atom proof))))))

(defn- self-binding?
  [[binding-nom term]]
  (= term (ast/var-term binding-nom)))

(defn- binding-subst
  [bindings]
  (into {}
        (keep (fn [[binding-nom term]]
                (when-not (self-binding? [binding-nom term])
                  [binding-nom term])))
        bindings))

(defn- walk-binding
  [subst [binding-nom term]]
  [binding-nom (walk-term subst term)])

(defn- settle-negative-residual
  [program state formula]
  (when (= 'neg (ast/tag-of formula))
    (let [atom (walk-term (:subst state) (second formula))]
      (when (defined-relation? program (second atom))
        (for [[state proof] (solve-atom program state atom)]
          [state (list 'constructor-recursive-neg-residual formula proof)])))))

(defn- settle-residual-sequence
  [program state formulas]
  (if (empty? formulas)
    (list [state '(constructor-recursive-residuals-done)])
    (for [[head-state head-proof] (settle-negative-residual program state (first formulas))
          [tail-state tail-proof] (settle-residual-sequence program head-state (rest formulas))]
      [tail-state
       (list 'constructor-recursive-residuals-step head-proof tail-proof)])))

(defn settle-record
  "Try to discharge a single exported answer record's residual frontier.

   The function is intentionally conservative: only negative defined-call
   residuals are discharged. Solved residuals refine bindings through the same
   substitution state and append constructor-recursive proof terms to `:proofs`."
  ([program record]
   (settle-record program record {}))
  ([program record {:keys [fuel limit]
                    :or {fuel default-fuel
                         limit 1}}]
   (let [initial-state {:subst (binding-subst (:bindings record))
                        :fuel fuel}]
     (or
       (first
         (for [[state proof] (take limit
                                   (settle-residual-sequence
                                     program
                                     initial-state
                                     (:residuals record)))]
           (assoc record
                  :bindings (mapv #(walk-binding (:subst state) %)
                                  (:bindings record))
                  :residuals []
                  :proofs (conj (vec (:proofs record))
                                (list 'constructor-recursive-residual-settlement
                                      proof)))))
       record))))

(defn query-records
  "Return constructor-recursive answer records for a positive atomic query."
  ([program query answer-vars]
   (query-records program query answer-vars {}))
  ([program query answer-vars {:keys [fuel limit]
                               :or {fuel default-fuel
                                    limit 10}}]
   (when-not (= 'pos (ast/tag-of query))
     (throw (ex-info "Constructor-recursive query layer expects a positive atom"
                     {:query query})))
   (let [atom (second query)]
     (mapv (fn [{:keys [subst proof]}]
             {:bindings (mapv (fn [answer-var]
                                 [answer-var (walk-term subst (ast/var-term answer-var))])
                               answer-vars)
              :residuals []
              :proofs [proof]})
           (solve-query program atom {:fuel fuel :limit limit})))))

(defn query-succeeds?
  "True when the constructor-recursive layer can witness a positive query."
  ([program query]
   (query-succeeds? program query {}))
  ([program query opts]
   (boolean (seq (query-records program query [] (assoc opts :limit 1))))))
