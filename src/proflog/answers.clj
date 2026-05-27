(ns proflog.answers
  "Answer export helpers for greenfield Proflog queries.

   The kernel remains a structural relation over explicit object-language
   variables. The generic path exports symbolic substitutions and residual
   formulas for named answer vars, including deferred procedure-call
   obligations when recursive search is left symbolic. Separate bounded
   materialization helpers are kept above the kernel for operational use:
   whole-language Herbrand enumeration for generic ground probing, and a
   query-driven closed-answer parity mode for long-running legacy comparisons."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== run]]
            [proflog.answer-overlay :as answer-overlay]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.proof :as proof]
            [proflog.query :as query]
            [proflog.subst :as subst]))

(defn- declaration-order
  "Stable ordering for declared symbols during bounded enumeration."
  [entry]
  [(val entry) (str (key entry))])

(defn- tuples
  "Cartesian power of `xs` with width `n`."
  [xs n]
  (if (zero? n)
    (list '())
    (for [x xs
          tail (tuples xs (dec n))]
      (cons x tail))))

(defn- ordered-distinct
  "Preserve the first occurrence of each item in `xs`."
  [xs]
  (:items
    (reduce (fn [{:keys [seen items] :as acc} x]
              (if (contains? seen x)
                acc
                {:seen (conj seen x)
                 :items (conj items x)}))
            {:seen #{}
             :items []}
            xs)))

(defn ground-terms-up-to-depth
  "Enumerate all declared-language ground terms up to constructor depth `max-depth`.

   Depth zero contains only nullary function symbols (including constants).
   Higher depths are built from shallower terms in a stable declaration order."
  [lang max-depth]
  (when (neg? max-depth)
    (throw (ex-info "Ground-term depth must be non-negative"
                    {:max-depth max-depth})))
  (let [declared-functions (sort-by declaration-order (:functions lang))
        nullary-terms (mapv (fn [[sym _]]
                              (ast/app-term sym))
                            (filter (fn [[_ arity]]
                                      (zero? arity))
                                    declared-functions))
        positive-functions (filter (fn [[_ arity]]
                                     (pos? arity))
                                   declared-functions)]
    (loop [depth 0
           terms-up-to nullary-terms
           exact-prev nullary-terms]
      (if (= depth max-depth)
        terms-up-to
        (let [exact-prev-set (set exact-prev)
              exact-next (->> positive-functions
                              (mapcat (fn [[sym arity]]
                                        (for [args (tuples terms-up-to arity)
                                              :when (some exact-prev-set args)]
                                          (apply ast/app-term sym args))))
                              distinct
                              vec)]
          (recur (inc depth)
                 (into terms-up-to exact-next)
                 exact-next))))))

(declare export-answer-record free-vars-formula materialize-ground-query-records)

(defn- free-vars-term
  "Collect the free object-language variable noms mentioned in `term`."
  [term]
  (case (ast/tag-of term)
    var #{(second term)}
    par #{}
    app (reduce into #{} (map free-vars-term (nnext term)))
    #{}))

(defn- free-vars-formula
  "Collect the free object-language variable noms mentioned in `formula`."
  [formula]
  (case (ast/tag-of formula)
    true #{}
    false #{}
    pos (free-vars-term (second formula))
    neg (free-vars-term (second formula))
    eq (into (free-vars-term (second formula))
             (free-vars-term (nth formula 2)))
    neq (into (free-vars-term (second formula))
              (free-vars-term (nth formula 2)))
    and (into (free-vars-formula (second formula))
              (free-vars-formula (nth formula 2)))
    or (into (free-vars-formula (second formula))
             (free-vars-formula (nth formula 2)))
    not (free-vars-formula (second formula))
    implies (into (free-vars-formula (second formula))
                  (free-vars-formula (nth formula 2)))
    forall (let [tied (second formula)]
             (disj (free-vars-formula (:body tied))
                   (:binding-nom tied)))
    once-forall (let [tied (second formula)]
                  (disj (free-vars-formula (:body tied))
                        (:binding-nom tied)))
    exists (let [tied (second formula)]
             (disj (free-vars-formula (:body tied))
                   (:binding-nom tied)))
    #{}))

(defn- ground-term?
  "True when `term` is ground and contains no internal `par` terms."
  [term]
  (case (ast/tag-of term)
    var false
    par false
    app (every? ground-term? (nnext term))
    true))

(defn- term-subterms
  "Collect `term` and all of its recursive subterms in pre-order."
  [term]
  (cons term
        (mapcat term-subterms
                (if (= 'app (ast/tag-of term))
                  (nnext term)
                  '()))))

(defn- formula-term-subterms
  "Collect every term mentioned anywhere inside `formula`."
  [formula]
  (case (ast/tag-of formula)
    true '()
    false '()
    pos (term-subterms (second formula))
    neg (term-subterms (second formula))
    eq (concat (term-subterms (second formula))
               (term-subterms (nth formula 2)))
    neq (concat (term-subterms (second formula))
                (term-subterms (nth formula 2)))
    and (concat (formula-term-subterms (second formula))
                (formula-term-subterms (nth formula 2)))
    or (concat (formula-term-subterms (second formula))
               (formula-term-subterms (nth formula 2)))
    not (formula-term-subterms (second formula))
    implies (concat (formula-term-subterms (second formula))
                    (formula-term-subterms (nth formula 2)))
    forall (formula-term-subterms (:body (second formula)))
    once-forall (formula-term-subterms (:body (second formula)))
    exists (formula-term-subterms (:body (second formula)))
    '()))

(defn- term-constructor-size
  "Count positive-arity constructor applications inside `term`."
  [term]
  (case (ast/tag-of term)
    app (if (empty? (nnext term))
          0
          (+ 1 (reduce + (map term-constructor-size (nnext term)))))
    0))

(defn- validate-answer-vars
  "Check that `answer-vars` are distinct free noms of `query`."
  [query answer-vars]
  (let [answer-vars (vec answer-vars)
        query-free-vars (free-vars-formula query)]
    (doseq [binding-nom answer-vars]
      (when-not (nominal/nom? binding-nom)
        (throw (ex-info "Answer variables must be noms"
                        {:answer-vars answer-vars
                         :invalid binding-nom}))))
    (when-not (= (count answer-vars) (count (distinct answer-vars)))
      (throw (ex-info "Answer variables must be distinct"
                      {:answer-vars answer-vars})))
    (doseq [binding-nom answer-vars]
      (when-not (contains? query-free-vars binding-nom)
        (throw (ex-info "Answer variable is not free in the query"
                        {:answer-var binding-nom
                         :query query}))))
    answer-vars))

(defn- lookup-clause
  "Pure clause lookup in a compiled program."
  [program relation]
  (some (fn [clause]
          (when (= relation (:relation clause))
            clause))
        (:clause-list program)))

(defn binding-term
  "Return the answer term bound to `binding-nom`, or nil when absent."
  [answer binding-nom]
  (some (fn [[candidate-nom term]]
          (when (= candidate-nom binding-nom)
            term))
        (:bindings answer)))

(defn- walk-term
  "Purely walk one term through the explicit substitution `sigma`."
  [term sigma]
  (let [tag (ast/tag-of term)]
    (case tag
      var (if-let [value (subst/lookup-binding sigma (second term))]
            (recur value sigma)
            term)
      par (if-let [value (subst/lookup-binding sigma (second term))]
            (recur value sigma)
            term)
      app (apply ast/app-term
                 (second term)
                 (map #(walk-term % sigma) (nnext term)))
      term)))

(defn- walk-formula
  "Purely walk the term leaves of a residual formula through `sigma`."
  [formula sigma]
  (case (ast/tag-of formula)
    pos (ast/pos-lit (walk-term (second formula) sigma))
    neg (ast/neg-lit (walk-term (second formula) sigma))
    eq (ast/eq-lit (walk-term (second formula) sigma)
                   (walk-term (nth formula 2) sigma))
    neq (ast/neq-lit (walk-term (second formula) sigma)
                     (walk-term (nth formula 2) sigma))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form (:binding-nom tied)
                                        (walk-formula (:body tied) sigma)))
    formula))

(defn- contradictory-term-pair?
  "True when `left = right` is already impossible in the free constructor algebra.

   This host-side predicate mirrors the constructor-clash half of
   `proflog.equality/eq-contradictiono` for already-walked exported terms. It is
   intentionally narrower than full kernel equality: it only recognizes
   contradictions that are visible without introducing new bindings, which is
   exactly what answer export needs when discarding residual disequalities that
   are tautologically true."
  [left right]
  (let [left-tag (ast/tag-of left)
        right-tag (ast/tag-of right)]
    (cond
      (and (= 'app left-tag) (= 'app right-tag))
      (or (not= (second left) (second right))
          (not= (count (nnext left)) (count (nnext right)))
          (some true? (map contradictory-term-pair? (nnext left) (nnext right))))

      :else
      false)))

(defn- rename-term
  "Rename exported object-language vars according to `renaming`."
  [term renaming]
  (let [tag (ast/tag-of term)]
    (case tag
      var (ast/var-term (get renaming (second term) (second term)))
      par term
      app (apply ast/app-term
                 (second term)
                 (map #(rename-term % renaming) (nnext term)))
      term)))

(defn- rename-formula
  "Rename object-language vars inside an exported residual formula."
  [formula renaming]
  (case (ast/tag-of formula)
    pos (ast/pos-lit (rename-term (second formula) renaming))
    neg (ast/neg-lit (rename-term (second formula) renaming))
    eq (ast/eq-lit (rename-term (second formula) renaming)
                   (rename-term (nth formula 2) renaming))
    neq (ast/neq-lit (rename-term (second formula) renaming)
                     (rename-term (nth formula 2) renaming))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form (:binding-nom tied)
                                        (rename-formula (:body tied) renaming)))
    formula))

(defn- alpha-shape-term
  "Return a variable-name-insensitive sort key for `term`."
  [term]
  (case (ast/tag-of term)
    var [:var]
    par [:par]
    app (into [:app (second term)]
              (map alpha-shape-term (nnext term)))
    [:other term]))

(declare alpha-shape-formula)

(defn- alpha-shape-formula
  "Return a variable-name-insensitive sort key for `formula`."
  [formula]
  (case (ast/tag-of formula)
    true [:true]
    false [:false]
    pos [:pos (alpha-shape-term (second formula))]
    neg [:neg (alpha-shape-term (second formula))]
    eq [:eq
        (alpha-shape-term (second formula))
        (alpha-shape-term (nth formula 2))]
    neq [:neq
         (alpha-shape-term (second formula))
         (alpha-shape-term (nth formula 2))]
    and [:and
         (alpha-shape-formula (second formula))
         (alpha-shape-formula (nth formula 2))]
    or [:or
        (alpha-shape-formula (second formula))
        (alpha-shape-formula (nth formula 2))]
    not [:not (alpha-shape-formula (second formula))]
    implies [:implies
             (alpha-shape-formula (second formula))
             (alpha-shape-formula (nth formula 2))]
    forall [:forall (alpha-shape-formula (:body (second formula)))]
    once-forall [:once-forall (alpha-shape-formula (:body (second formula)))]
    exists [:exists (alpha-shape-formula (:body (second formula)))]
    [:other formula]))

(defn- canonical-export-var
  "Assign a stable exported name to one non-answer object-language var."
  [binding-nom protected-vars renaming next-idx]
  (cond
    (contains? protected-vars binding-nom)
    [renaming next-idx binding-nom]

    (contains? renaming binding-nom)
    [renaming next-idx (get renaming binding-nom)]

    :else
    (let [canonical-nom (symbol (str "_" next-idx))]
      [(assoc renaming binding-nom canonical-nom)
       (inc next-idx)
       canonical-nom])))

(declare canonicalize-export-term canonicalize-export-formula)

(defn- canonicalize-export-term
  "Rename internal proof vars in `term` to a stable exported numbering."
  [term protected-vars renaming next-idx]
  (case (ast/tag-of term)
    var (let [[renaming next-idx canonical-nom]
              (canonical-export-var (second term)
                                    protected-vars
                                    renaming
                                    next-idx)]
          [renaming next-idx (ast/var-term canonical-nom)])
    par [renaming next-idx term]
    app (let [[renaming next-idx args]
              (reduce (fn [[renaming next-idx args] arg]
                        (let [[renaming next-idx arg]
                              (canonicalize-export-term
                                arg
                                protected-vars
                                renaming
                                next-idx)]
                          [renaming next-idx (conj args arg)]))
                      [renaming next-idx []]
                      (nnext term))]
          [renaming
           next-idx
           (apply ast/app-term (second term) args)])
    [renaming next-idx term]))

(defn- canonicalize-quantified-export-formula
  "Canonicalize one quantified exported residual while preserving outer state."
  [constructor tied protected-vars renaming next-idx]
  (let [binding-nom (:binding-nom tied)
        previous (get renaming binding-nom ::missing)
        [renaming next-idx canonical-nom]
        (canonical-export-var binding-nom
                              protected-vars
                              renaming
                              next-idx)
        [renaming next-idx body]
        (canonicalize-export-formula
          (:body tied)
          protected-vars
          renaming
          next-idx)
        renaming (if (= ::missing previous)
                   (dissoc renaming binding-nom)
                   (assoc renaming binding-nom previous))]
    [renaming next-idx (constructor canonical-nom body)]))

(defn- canonicalize-export-formula
  "Rename internal proof vars in one exported residual formula."
  [formula protected-vars renaming next-idx]
  (case (ast/tag-of formula)
    true [renaming next-idx formula]
    false [renaming next-idx formula]
    pos (let [[renaming next-idx atom]
              (canonicalize-export-term (second formula)
                                        protected-vars
                                        renaming
                                        next-idx)]
          [renaming next-idx (ast/pos-lit atom)])
    neg (let [[renaming next-idx atom]
              (canonicalize-export-term (second formula)
                                        protected-vars
                                        renaming
                                        next-idx)]
          [renaming next-idx (ast/neg-lit atom)])
    eq (let [[renaming next-idx left]
             (canonicalize-export-term (second formula)
                                       protected-vars
                                       renaming
                                       next-idx)
             [renaming next-idx right]
             (canonicalize-export-term (nth formula 2)
                                       protected-vars
                                       renaming
                                       next-idx)]
         [renaming next-idx (ast/eq-lit left right)])
    neq (let [[renaming next-idx left]
              (canonicalize-export-term (second formula)
                                        protected-vars
                                        renaming
                                        next-idx)
              [renaming next-idx right]
              (canonicalize-export-term (nth formula 2)
                                        protected-vars
                                        renaming
                                        next-idx)]
          [renaming next-idx (ast/neq-lit left right)])
    and (let [[renaming next-idx left]
              (canonicalize-export-formula (second formula)
                                           protected-vars
                                           renaming
                                           next-idx)
              [renaming next-idx right]
              (canonicalize-export-formula (nth formula 2)
                                           protected-vars
                                           renaming
                                           next-idx)]
          [renaming next-idx (ast/and-form left right)])
    or (let [[renaming next-idx left]
             (canonicalize-export-formula (second formula)
                                          protected-vars
                                          renaming
                                          next-idx)
             [renaming next-idx right]
             (canonicalize-export-formula (nth formula 2)
                                          protected-vars
                                          renaming
                                          next-idx)]
         [renaming next-idx (ast/or-form left right)])
    not (let [[renaming next-idx body]
              (canonicalize-export-formula (second formula)
                                           protected-vars
                                           renaming
                                           next-idx)]
          [renaming next-idx (ast/not-form body)])
    implies (let [[renaming next-idx left]
                  (canonicalize-export-formula (second formula)
                                               protected-vars
                                               renaming
                                               next-idx)
                  [renaming next-idx right]
                  (canonicalize-export-formula (nth formula 2)
                                               protected-vars
                                               renaming
                                               next-idx)]
              [renaming next-idx (ast/implies-form left right)])
    forall (canonicalize-quantified-export-formula
             ast/forall-form
             (second formula)
             protected-vars
             renaming
             next-idx)
    once-forall (canonicalize-quantified-export-formula
                  ast/once-forall-form
                  (second formula)
                  protected-vars
                  renaming
                  next-idx)
    exists (canonicalize-quantified-export-formula
             ast/exists-form
             (second formula)
             protected-vars
             renaming
             next-idx)
    [renaming next-idx formula]))

(defn- canonicalize-answer-record
  "Normalize exported answer frontiers for stable merging and ranking.

   Answer vars keep their original noms, while internal proof vars are renamed
   to `_0`, `_1`, ... in first-occurrence order across the bindings followed by
   a residual list sorted by alpha-insensitive shape. Residual duplicates are
   then removed, so alpha-equivalent proof families collapse to one answer
   frontier before ranking."
  [{:keys [bindings residuals] :as record}]
  (let [protected-vars (set (map first bindings))
        [renaming next-idx bindings]
        (reduce (fn [[renaming next-idx bindings] [binding-nom term]]
                  (let [[renaming next-idx term]
                        (canonicalize-export-term
                          term
                          protected-vars
                          renaming
                          next-idx)]
                    [renaming
                     next-idx
                     (conj bindings [binding-nom term])]))
                [{} 0 []]
                bindings)
        residuals (sort-by (juxt alpha-shape-formula pr-str) residuals)
        [_ _ residuals]
        (reduce (fn [[renaming next-idx residuals] formula]
                  (let [[renaming next-idx formula]
                        (canonicalize-export-formula
                          formula
                          protected-vars
                          renaming
                          next-idx)]
                    [renaming next-idx (conj residuals formula)]))
                [renaming next-idx []]
                residuals)
        residuals (->> residuals
                       distinct
                       vec)]
    (assoc record
           :bindings bindings
           :residuals residuals)))

(defn- admissible-term?
  "True when `term` is exportable under the declared object language."
  [lang term]
  (try
    (language/validate-term lang term)
    true
    (catch Exception _
      false)))

(defn- admissible-formula?
  "True when `formula` stays inside the declared object language."
  [lang formula]
  (try
    (language/validate-formula lang formula)
    true
    (catch Exception _
      false)))

(defn- nullary-language-terms
  "Return declared nullary function symbols in stable declaration order."
  [lang]
  (->> (:functions lang)
       (sort-by declaration-order)
       (filter (fn [[_ arity]]
                 (zero? arity)))
       (mapv (fn [[sym _]]
               (ast/app-term sym)))))

(defn- query-ground-seed-terms
  "Collect stable seed terms for query-driven closed-answer materialization.

   Seeds include every ground subterm already present in `query`, followed by
   any remaining declared nullary language terms so the parity mode can still
   discover constants not mentioned directly in the query."
  [lang query]
  (vec
    (ordered-distinct
      (concat
        (filter ground-term? (formula-term-subterms query))
        (nullary-language-terms lang)))))

(defn- query-ground-terms-up-to-size
  "Enumerate query-driven ground candidates up to constructor size `max-size`.

   Candidate growth is ordered by increasing constructor size. Within each
   exact size, query-ground seed terms appear before newly generated terms so
   query subterms and their near neighbors surface early in parity probes."
  [lang query max-size]
  (when (neg? max-size)
    (throw (ex-info "Ground-term size bound must be non-negative"
                    {:max-size max-size})))
  (let [positive-functions (->> (:functions lang)
                                (sort-by declaration-order)
                                (filter (fn [[_ arity]]
                                          (pos? arity))))
        seed-buckets
        (reduce (fn [buckets term]
                  (let [size (term-constructor-size term)]
                    (if (<= size max-size)
                      (update buckets size (fnil conj []) term)
                      buckets)))
                {}
                (query-ground-seed-terms lang query))]
    (loop [size 0
           terms-up-to []]
      (if (> size max-size)
        terms-up-to
        (let [seed-exact (get seed-buckets size [])
              generated-exact
              (if (zero? size)
                []
                (->> positive-functions
                     (mapcat (fn [[sym arity]]
                               (for [args (tuples terms-up-to arity)
                                     :when (= size
                                              (inc (reduce + (map term-constructor-size args))))]
                                 (apply ast/app-term sym args))))
                     ordered-distinct))
              exact-terms (vec (ordered-distinct (concat seed-exact generated-exact)))
              terms-up-to (into terms-up-to exact-terms)]
          (recur (inc size) terms-up-to))))))

(defn- size-stratified-tuples
  "Enumerate tuples from `exact-buckets` by increasing total constructor size."
  [exact-buckets width]
  (let [max-size (apply max 0 (keys exact-buckets))]
    (letfn [(tuples-of-total-size [remaining-width remaining-size]
              (if (zero? remaining-width)
                (if (zero? remaining-size)
                  (list '())
                  '())
                (mapcat
                  (fn [size]
                    (for [term (get exact-buckets size [])
                          tail (tuples-of-total-size (dec remaining-width)
                                                     (- remaining-size size))]
                      (cons term tail)))
                  (range (inc (min max-size remaining-size))))))]
      (mapcat #(tuples-of-total-size width %)
              (range (inc (* width max-size)))))))

(defn- var-term-nom
  "Return the bound nom for an object-language variable term."
  [term]
  (when (= 'var (ast/tag-of term))
    (second term)))

(defn- cons-head
  [term]
  (nth term 2))

(defn- cons-tail
  [term]
  (nth term 3))

(defn- proper-list-items
  "Return the element vector when `term` is a ground `cons`/`null` list."
  [term]
  (loop [term term
         items []]
    (cond
      (= (ast/app-term 'null) term)
      items

      (and (= 'app (ast/tag-of term))
           (= 'cons (second term))
           (= 2 (count (nnext term))))
      (recur (cons-tail term)
             (conj items (cons-head term)))

      :else
      nil)))

(defn- list-term-from-items
  "Build a ground `cons`/`null` list from `items`."
  [items]
  (reduce (fn [tail item]
            (ast/app-term 'cons item tail))
          (ast/app-term 'null)
          (reverse items)))

(defn- answer-binding-tuple
  "Project a nom->term map into `checked-answer-vars` order."
  [checked-answer-vars bindings]
  (when (every? #(contains? bindings %) checked-answer-vars)
    (mapv bindings checked-answer-vars)))

(defn- prefix-of?
  "True when `prefix` is the first part of `whole`."
  [prefix whole]
  (= prefix (subvec whole 0 (count prefix))))

(defn- suffix-of?
  "True when `suffix` is the last part of `whole`."
  [suffix whole]
  (= suffix (subvec whole (- (count whole) (count suffix)))))

(defn- append-fast-path-assignments
  "Derive closed candidate bindings directly from extensional list structure."
  [checked-answer-vars [left right whole]]
  (let [left-var (var-term-nom left)
        right-var (var-term-nom right)
        whole-var (var-term-nom whole)
        left-items (when (ground-term? left)
                     (proper-list-items left))
        right-items (when (ground-term? right)
                      (proper-list-items right))
        whole-items (when (ground-term? whole)
                      (proper-list-items whole))]
    (cond
      (and whole-items left-var right-var)
      (->> (range (inc (count whole-items)))
           (map (fn [idx]
                  (answer-binding-tuple
                    checked-answer-vars
                    {left-var (list-term-from-items (subvec whole-items 0 idx))
                     right-var (list-term-from-items (subvec whole-items idx))})))
           (keep identity)
           vec)

      (and left-items whole-items right-var
           (<= (count left-items) (count whole-items))
           (prefix-of? left-items whole-items))
      (when-let [tuple (answer-binding-tuple
                         checked-answer-vars
                         {right-var (list-term-from-items
                                      (subvec whole-items (count left-items)))})]
        [tuple])

      (and right-items whole-items left-var
           (<= (count right-items) (count whole-items))
           (suffix-of? right-items whole-items))
      (when-let [tuple (answer-binding-tuple
                         checked-answer-vars
                         {left-var (list-term-from-items
                                     (subvec whole-items 0 (- (count whole-items)
                                                              (count right-items))))})]
        [tuple])

      (and left-items right-items whole-var)
      (when-let [tuple (answer-binding-tuple
                         checked-answer-vars
                         {whole-var (list-term-from-items (into left-items right-items))})]
        [tuple])

      :else
      nil)))

(defn- reverse-fast-path-assignments
  "Derive closed candidate bindings for list reverse queries."
  [checked-answer-vars [left right]]
  (let [left-var (var-term-nom left)
        right-var (var-term-nom right)
        left-items (when (ground-term? left)
                     (proper-list-items left))
        right-items (when (ground-term? right)
                      (proper-list-items right))]
    (cond
      (and left-items right-var)
      (when-let [tuple (answer-binding-tuple
                         checked-answer-vars
                         {right-var (list-term-from-items (reverse left-items))})]
        [tuple])

      (and right-items left-var)
      (when-let [tuple (answer-binding-tuple
                         checked-answer-vars
                         {left-var (list-term-from-items (reverse right-items))})]
        [tuple])

      :else
      nil)))

(defn- parity-fast-path-assignments
  "Return specialized candidate bindings for known legacy list parity families."
  [checked-query checked-answer-vars]
  (when (= 'pos (ast/tag-of checked-query))
    (let [term (second checked-query)]
      (when (= 'app (ast/tag-of term))
        (let [relation (second term)
              args (vec (nnext term))]
          (case relation
            append (append-fast-path-assignments checked-answer-vars args)
            reverse (reverse-fast-path-assignments checked-answer-vars args)
            nil))))))

(defn- contradictory-residual?
  "True when an exported residual is already impossible on its own shape."
  [formula]
  (case (ast/tag-of formula)
    neq (= (second formula) (nth formula 2))
    false true
    false))

(defn- tautological-residual?
  "True when an exported residual contributes no information.

   The fair-agenda kernel can expose proof families that differ only by a saved
   disequality already guaranteed by constructor clash, such as
   `neq(null, cons(_0, _1))`. Those residuals are semantically inert and should
   be discarded before answer-record merging."
  [formula]
  (case (ast/tag-of formula)
    neq (contradictory-term-pair? (second formula) (nth formula 2))
    false))

(defn- constructor-demand-term?
  "True when a term already exposes some concrete constructor structure.

   ADR-0033 residual completion should not turn a wholly symbolic recursive
   family such as `odd(_0)` into a single enumerated witness. It should only
   continue residual calls whose arguments contain real object-language
   structure that can drive guarded structural continuation."
  [term]
  (case (ast/tag-of term)
    app true
    var false
    par false
    false))

(defn- defined-negative-call-residual?
  [program formula]
  (and (= 'neg (ast/tag-of formula))
       (let [atom (second formula)]
         (and (= 'app (ast/tag-of atom))
              (some? (lookup-clause program (second atom)))))))

(defn- residual-has-constructor-demand?
  [formula]
  (let [atom (second formula)]
    (boolean (some constructor-demand-term? (nnext atom)))))

(defn- structurally-completable-record?
  "Conservatively decide whether residual continuation is warranted.

   Structural continuation is generic, but it is still a search. We only invoke
   it for records whose entire procedural frontier consists of negative calls
   to relations defined by this program and whose frontier exposes constructor
   demand somewhere.
   This keeps open symbolic families as residuals while allowing carried answer
   variables under constructor constraints to continue."
  [program record]
  (let [residuals (:residuals record)]
    (and (seq residuals)
         (every? #(defined-negative-call-residual? program %) residuals)
         (some residual-has-constructor-demand? residuals))))

(def ^:private default-residual-completion-fuel 96)

(defn- restore-answer-binding-noms
  [original-record continued-record]
  (assoc continued-record
         :bindings
         (mapv (fn [[binding-nom _] [_ term]]
                 [binding-nom term])
               (:bindings original-record)
               (:bindings continued-record))))

(defn- complete-structural-residuals
  [program record
   {:keys [complete-residuals? residual-completion-fuel]
    :or {complete-residuals? true
         residual-completion-fuel default-residual-completion-fuel}}]
  (if (and complete-residuals?
           (structurally-completable-record? program record))
    (or (some->> (first
                   (run 1 [continued-record]
                     (answer-overlay/continue-exported-structural-recordo
                       program
                       record
                       continued-record
                       residual-completion-fuel)))
                 (restore-answer-binding-noms record))
        record)
    record))

(defn- export-answer-record
  "Project one kernel proof state into an answer record or nil if inadmissible."
  [lang answer-vars reified-answer-vars sigma neqs residual-formulas proof]
  (let [renaming (zipmap reified-answer-vars answer-vars)
        bindings (mapv (fn [binding-nom reified-binding-nom]
                         [binding-nom
                          (rename-term
                            (walk-term (ast/var-term reified-binding-nom) sigma)
                            renaming)])
                       answer-vars
                       reified-answer-vars)
        residuals (vec
                    (concat
                      (map (fn [[left right]]
                             (ast/neq-lit
                               (rename-term (walk-term left sigma) renaming)
                               (rename-term (walk-term right sigma) renaming)))
                           neqs)
                      (map (fn [formula]
                             (rename-formula
                               (walk-formula formula sigma)
                               renaming))
                           residual-formulas)))
        residuals (vec (remove tautological-residual? residuals))]
    (when (and (not-any? contradictory-residual? residuals)
               (every? (fn [[_ term]]
                         (admissible-term? lang term))
                       bindings)
               (every? (fn [formula]
                         (admissible-formula? lang formula))
                       residuals))
      {:bindings bindings
       :residuals residuals
       :proofs [proof]})))

(defn- merge-answer-records
  "Merge records with the same bindings and residuals, collecting proofs.

   Preserve the first-seen answer order so callers can ask for the first `n`
   unique answers without raw proof duplication scrambling the result set."
  [records]
  (let [{:keys [order merged]}
        (reduce (fn [{:keys [order merged] :as acc}
                     record]
                  (let [{:keys [bindings residuals proofs] :as record}
                        (canonicalize-answer-record record)
                        key [bindings residuals]]
                    (if-let [existing (get merged key)]
                      (assoc acc :merged
                             (assoc merged key (update existing :proofs into proofs)))
                      {:order (conj order key)
                       :merged (assoc merged key record)})))
                {:order []
                 :merged {}}
                records)]
    (mapv merged order)))

(defn- neq-residual?
  "True when an exported residual is a plain disequality."
  [formula]
  (= 'neq (ast/tag-of formula)))

(defn- closed-answer-record?
  "True when an exported answer has no deferred non-disequality obligations."
  [record]
  (every? neq-residual? (:residuals record)))

(defn- prune-shadowed-open-records
  "Drop open residual records shadowed by a closed answer for the same bindings.

   Fairer internal scheduling can surface both:

   - a closed answer showing the query already succeeds for some bindings, and
   - an alternative proof family with the same bindings but extra deferred call
     obligations.

   The second record is not useful at the public answer API once the first
   exists, because the bindings are already justified without any remaining
   procedure-call work."
  [records]
  (let [closed-bindings (->> records
                             (filter closed-answer-record?)
                             (map :bindings)
                             set)]
    (->> records
         (remove (fn [record]
                   (and (not (closed-answer-record? record))
                        (contains? closed-bindings (:bindings record)))))
         vec)))

(defn- record-proof-rank
  "Prefer shorter completed derivations when residual status is otherwise equal."
  [{:keys [proofs]}]
  (if (seq proofs)
    (apply min (map (comp count proof/collect-steps) proofs))
    0))

(defn- answer-record-rank
  "Prefer answers that have finished all procedure-call work.

   Closed answers containing only disequalities sort ahead of records that still
   carry residual literals. Within those groups, prefer fewer free vars and
   fewer total residuals."
  [{:keys [bindings residuals]}]
  (let [open-residuals (remove neq-residual? residuals)
        binding-var-count (reduce + (map (comp count free-vars-term second) bindings))
        residual-var-count (reduce + (map (comp count free-vars-formula) residuals))]
    [(if (seq open-residuals) 1 0)
     (count open-residuals)
     binding-var-count
     residual-var-count
     (count residuals)]))

(defn- term-complexity
  "Count the AST nodes in one exported term."
  [term]
  (case (ast/tag-of term)
    app (+ 1 (reduce + 0 (map term-complexity (nnext term))))
    1))

(defn- formula-complexity
  "Count the AST nodes in one exported residual formula."
  [formula]
  (case (ast/tag-of formula)
    pos (+ 1 (term-complexity (second formula)))
    neg (+ 1 (term-complexity (second formula)))
    eq (+ 1
          (term-complexity (second formula))
          (term-complexity (nth formula 2)))
    neq (+ 1
           (term-complexity (second formula))
           (term-complexity (nth formula 2)))
    once-forall (+ 1 (formula-complexity (:body (second formula))))
    1))

(defn- answer-record-shape-key
  "Return a stable tie-break key for already-canonicalized answer records.

   ADR-0016 makes the raw proof stream less sensitive to one fixed leftmost
   schedule. Public answer ordering should therefore not depend on whichever
   equivalent proof family happened to appear first."
  [{:keys [bindings residuals]}]
  [(mapv (fn [[binding-nom term]]
           [binding-nom
            (term-complexity term)
            (pr-str term)])
         bindings)
   (mapv (fn [formula]
           [(formula-complexity formula)
            (pr-str formula)])
         residuals)])

(defn- prioritize-answer-records
  "Sort answer records by completion while preserving first-seen order on ties."
  [records]
  (->> records
       prune-shadowed-open-records
       (map-indexed vector)
       (sort-by (fn [[idx record]]
                  [(answer-record-rank record)
                   (answer-record-shape-key record)
                   idx]))
       (mapv second)))

(defn- prioritize-answer-records-by-derivation
  "Prefer shorter completed derivations after ordinary completion ranking.

   This is used for deeper recursive query-answer requests, where base
   alternatives should precede recursive descendants when both are otherwise
   closed comparable answers. Diagnostics keep the plain answer ranking so they
   continue to expose raw frontier shape without derivation-order rewriting."
  [records]
  (->> records
       prune-shadowed-open-records
       (map-indexed vector)
       (sort-by (fn [[idx record]]
                  [(answer-record-rank record)
                   (record-proof-rank record)
                   (answer-record-shape-key record)
                   idx]))
       (mapv second)))

(defn- collect-answer-records
  "Search for up to `proof-limit` unique answer records.

   `search` is a function from raw proof limit to raw reified proof states.
   The raw proof stream may contain many duplicate witnesses for the same
   exported answer shape, so the collector grows the raw limit until it either
   has enough unique answers, exhausts the search, or hits
   `max-raw-proof-limit`. When the current top `proof-limit` answers still carry
   residual non-disequality formulas, the collector keeps deepening so a later
   closed answer can displace a shallower symbolic frontier."
  [proof-limit max-raw-proof-limit search export]
  (loop [raw-limit proof-limit]
    (let [raw-results (search raw-limit)
          merged (->> raw-results
                      (map export)
                      (keep identity)
                      merge-answer-records
                      vec)
          prioritized (prioritize-answer-records merged)
          selected (vec (take proof-limit prioritized))]
      (if (or (and (>= (count prioritized) proof-limit)
                   (every? closed-answer-record? selected))
              (< (count raw-results) raw-limit)
              (>= raw-limit max-raw-proof-limit))
        selected
        (recur (min max-raw-proof-limit (* 2 raw-limit)))))))

(defn- program-raw-answer-states
  "Return up to `raw-limit` raw kernel proof states for one query formula."
  ([program formula checked-answer-vars fuel raw-limit call-depth]
   (program-raw-answer-states
     program
     formula
     checked-answer-vars
     fuel
     raw-limit
     call-depth
     {:schedule-residual-continuation? true}))
  ([program formula checked-answer-vars fuel raw-limit call-depth
    {:keys [schedule-residual-continuation? residual-continuation-fuel]
     :or {schedule-residual-continuation? true
          residual-continuation-fuel default-residual-completion-fuel}}]
   (let [query-entry? (and (#{'pos 'neg} (ast/tag-of formula))
                           (some? (lookup-clause program
                                                 (second (second formula)))))
         continuation-fuel residual-continuation-fuel]
     (vec
       (run raw-limit [answer-vars-out sigma-out neqs-out residuals-out proof]
         (== answer-vars-out checked-answer-vars)
         (if query-entry?
           (if schedule-residual-continuation?
             (answer-overlay/prove-program-query-entry-scheduledo
               formula
               checked-answer-vars
               program
               sigma-out
               neqs-out
               residuals-out
               fuel
               call-depth
               continuation-fuel
               proof)
             (answer-overlay/prove-program-query-entryo
               formula
               checked-answer-vars
               program
               sigma-out
               neqs-out
               residuals-out
               fuel
               call-depth
               proof))
           (if schedule-residual-continuation?
             (answer-overlay/prove-program-answer-scheduledo
               formula
               '()
               '()
               '()
               checked-answer-vars
               program
               sigma-out
               neqs-out
               residuals-out
               fuel
               call-depth
               continuation-fuel
               proof)
             (answer-overlay/prove-program-answero
               formula
               '()
               '()
               '()
               checked-answer-vars
               program
               sigma-out
               neqs-out
               residuals-out
               fuel
               call-depth
               proof))))))))

(defn- export-program-answer-record
  "Export one raw query proof state against `program`'s language."
  ([program checked-answer-vars raw-state]
   (export-program-answer-record program checked-answer-vars raw-state {}))
  ([program checked-answer-vars [answer-vars-out sigma-out neqs-out residuals-out proof] opts]
   (when-let [record (export-answer-record
                       (:language program)
                       checked-answer-vars
                       answer-vars-out
                       sigma-out
                       neqs-out
                       residuals-out
                       proof)]
     (complete-structural-residuals
       program
       record
       opts))))

(defn- summarize-proof-signature
  "Trim a proof-step signature for diagnostics output."
  [steps proof-step-limit]
  (let [trimmed (vec (take proof-step-limit steps))]
    (if (> (count steps) proof-step-limit)
      (conj trimmed '...)
      trimmed)))

(defn- proof-root-tag
  "Return the outermost proof tag when one is present."
  [proof]
  (when (coll? proof)
    (first proof)))

(defn- summarize-raw-proofs
  "Summarize the raw proof families found in one diagnostics slice."
  [raw-results proof-sample-limit proof-step-limit]
  (let [proofs (map #(nth % 4) raw-results)
        step-signatures (mapv (comp vec proof/collect-steps) proofs)
        signature-counts (frequencies step-signatures)]
    {:distinct-proof-signature-count (count signature-counts)
     :duplicate-proof-signature-count (- (count step-signatures)
                                         (count signature-counts))
     :proof-root-counts (into (sorted-map-by #(compare (str %1) (str %2)))
                              (frequencies (keep proof-root-tag proofs)))
     :common-proof-signatures
     (->> signature-counts
          (sort-by (juxt (comp - val) (comp pr-str key)))
          (take proof-sample-limit)
          (mapv (fn [[steps count]]
                  {:count count
                   :steps (summarize-proof-signature steps proof-step-limit)})))}))

(defn- program-answer-diagnostic-snapshot
  "Collect one diagnostics snapshot for one fixed kernel call-depth stage."
  [program formula checked-answer-vars
   {:keys [fuel raw-limit sample-limit proof-sample-limit proof-step-limit
           call-depth stage-setup-elapsed-ms]}]
  (let [started (System/nanoTime)
        raw-results (program-raw-answer-states
                      program
                      formula
                      checked-answer-vars
                      fuel
                      raw-limit
                      call-depth
                      {:schedule-residual-continuation? false})
        exported-records (->> raw-results
                              (map #(export-program-answer-record
                                      program
                                      checked-answer-vars
                                      %
                                      {:complete-residuals? false}))
                              (keep identity)
                              vec)
        unique-records (merge-answer-records exported-records)
        prioritized-records (prioritize-answer-records unique-records)
        search-elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)]
    (merge
      {:raw-limit raw-limit
       :call-depth call-depth
       :stage-setup-elapsed-ms stage-setup-elapsed-ms
       :search-elapsed-ms search-elapsed-ms
       :elapsed-ms (+ stage-setup-elapsed-ms search-elapsed-ms)
       :raw-count (count raw-results)
       :search-exhausted? (< (count raw-results) raw-limit)
       :inadmissible-count (- (count raw-results) (count exported-records))
       :exported-count (count exported-records)
       :duplicate-exported-count (- (count exported-records)
                                    (count unique-records))
       :unique-count (count unique-records)
       :sample-records (vec (take sample-limit prioritized-records))}
      (summarize-raw-proofs raw-results proof-sample-limit proof-step-limit))))

(defn- search-program-formula-answers
  "Search one exact formula relative to `program` and export answer records.

   The caller supplies an already-validated `formula`; no extra negation or
   call unfolding happens here."
  [program formula checked-answer-vars
   {:keys [fuel proof-limit max-raw-proof-limit call-depth residual-continuation-fuel]}]
  (collect-answer-records
    proof-limit
    max-raw-proof-limit
    (fn [raw-limit]
      (program-raw-answer-states
        program
        formula
        checked-answer-vars
        fuel
        raw-limit
        call-depth
        {:residual-continuation-fuel residual-continuation-fuel}))
    (fn [raw-state]
      (export-program-answer-record
        program
        checked-answer-vars
        raw-state
        {:residual-completion-fuel residual-continuation-fuel}))))

(defn- staged-query-formula
  "Prepare the formula searched at one open-answer stage.

   The default path now keeps the original negated query at every stage.
   Top-level literal program calls are entered directly in the kernel without
   spending staged `call-depth`, so the stage budget itself measures recursive
   descendants below the surface query boundary."
  [_program negated-query stage]
  (let [setup-started (System/nanoTime)
        setup-elapsed-ms (/ (- (System/nanoTime) setup-started) 1000000.0)]
    {:formula negated-query
     :kernel-call-depth stage
     :unfold-depth 0
     :stage-setup-elapsed-ms setup-elapsed-ms}))

(defn formula-answers
  "Export symbolic answers for a closed tableau over `formula`.

   This is the generic answer path: requested answer vars may remain partially
   instantiated, and residual formulas are preserved in the answer records."
  ([lang formula answer-vars]
   (formula-answers lang formula answer-vars {}))
  ([lang formula answer-vars {:keys [fuel proof-limit max-raw-proof-limit]
                              :or {proof-limit 10}}]
   (let [checked-formula (language/validate-formula lang formula)
         checked-answer-vars (validate-answer-vars checked-formula answer-vars)
         max-raw-proof-limit (or max-raw-proof-limit (max proof-limit (* 8 proof-limit)))]
     (collect-answer-records
       proof-limit
       max-raw-proof-limit
       (fn [raw-limit]
         (run raw-limit [answer-vars-out sigma-out neqs-out residuals-out proof]
           (== answer-vars-out checked-answer-vars)
           (answer-overlay/prove-answero
             checked-formula
             '()
             '()
             '()
             checked-answer-vars
             sigma-out
             neqs-out
             residuals-out
             fuel
             proof)))
       (fn [[answer-vars-out sigma-out neqs-out residuals-out proof]]
         (export-answer-record
           lang
           checked-answer-vars
           answer-vars-out
           sigma-out
           neqs-out
           residuals-out
           proof))))))

(defn query-answers
  "Export symbolic answers for `query` relative to `program`.

   This is the generic solution for reverse and partial-mode query answering.
   Returned records may contain non-ground bindings, residual disequalities,
   and residual procedure-call obligations, but they never export internal
   `par` terms. For top-level literal program queries, the kernel enters the
   query call directly and `:call-depth` bounds recursive descendants below
   that entry boundary. Within that bound, answer-mode call deferral is a
   relational choice inside the kernel rather than a staged answer-layer pass.

   Current public `query-answers` also lets known list-family `append/3` and
   `reverse/2` queries reuse the ADR-0012 closed-answer materializer as an
   extensional fast path.
   When that path can already produce the requested closed answers, those
   records are returned directly; otherwise they are merged with the symbolic
   frontier so concrete closed answers can displace shallower residual ones."
  ([program query answer-vars]
   (query-answers program query answer-vars {}))
  ([program query answer-vars {:keys [call-depth fuel proof-limit max-raw-proof-limit
                                      residual-continuation-fuel]
                               :or {proof-limit 10
                                    call-depth 1
                                    residual-continuation-fuel default-residual-completion-fuel}}]
   (let [checked-query (language/validate-query (:language program) query)
         checked-answer-vars (validate-answer-vars checked-query answer-vars)
         negated-query (normalize/negate-formula checked-query)
         max-raw-proof-limit (or max-raw-proof-limit (max proof-limit (* 8 proof-limit)))
         fast-path-records
         (mapv #(dissoc % :query)
               (materialize-ground-query-records
                 checked-query
                 checked-answer-vars
                 (take proof-limit
                       (or (parity-fast-path-assignments
                             checked-query
                             checked-answer-vars)
                           []))))]
     (if (>= (count fast-path-records) proof-limit)
       fast-path-records
       (->> (concat
              fast-path-records
              (search-program-formula-answers
                program
                negated-query
                checked-answer-vars
                {:call-depth call-depth
                 :fuel fuel
                 :residual-continuation-fuel residual-continuation-fuel
                 :proof-limit proof-limit
                 :max-raw-proof-limit max-raw-proof-limit}))
            merge-answer-records
            prioritize-answer-records
            (#(if (> call-depth 1)
                (prioritize-answer-records-by-derivation %)
                %))
            (take proof-limit)
            vec)))))

(defn query-answer-diagnostics
  "Summarize how the raw proof stream grows for one open query.

   This is a diagnostics helper for difficult symbolic queries. For each
   requested `raw-limit`, it reports how many raw kernel proof states were
   found, how many of those states exported into admissible answer records,
   how many unique answer records remained after merging duplicates, and a
   small sample of those unique exported answers. The requested `:call-depth`
   probes one exact stage; for top-level literal program queries, stage `0`
   already enters the query call directly."
  ([program query answer-vars]
   (query-answer-diagnostics program query answer-vars {}))
  ([program query answer-vars {:keys [call-depth fuel raw-limits sample-limit
                                      proof-sample-limit proof-step-limit]
                               :or {call-depth 1
                                    raw-limits [1 2 4 8]
                                    sample-limit 2
                                    proof-sample-limit 3
                                    proof-step-limit 12}}]
   (let [checked-query (language/validate-query (:language program) query)
         negated-query (normalize/negate-formula checked-query)
         checked-answer-vars (validate-answer-vars checked-query answer-vars)]
     (let [{:keys [formula kernel-call-depth stage-setup-elapsed-ms]}
           (staged-query-formula program negated-query call-depth)]
       (mapv (fn [raw-limit]
               (program-answer-diagnostic-snapshot
                 program
                 formula
                 checked-answer-vars
                 {:fuel fuel
                  :raw-limit raw-limit
                  :call-depth kernel-call-depth
                  :sample-limit sample-limit
                  :proof-sample-limit proof-sample-limit
                  :proof-step-limit proof-step-limit
                  :stage-setup-elapsed-ms stage-setup-elapsed-ms}))
             raw-limits)))))

(defn query-stage-diagnostics
  "Summarize open-query search across every staged kernel call-depth.

   This helper answers the question that `query-answer-diagnostics` cannot:
   whether deeper `call-depth` stages remain productive at all, and if they do,
   whether they mainly surface new answers or duplicate proof families."
  ([program query answer-vars]
   (query-stage-diagnostics program query answer-vars {}))
  ([program query answer-vars {:keys [call-depth fuel raw-limits sample-limit
                                      proof-sample-limit proof-step-limit]
                               :or {call-depth 1
                                    raw-limits [1 2 4 8]
                                    sample-limit 2
                                    proof-sample-limit 3
                                    proof-step-limit 12}}]
   (let [checked-query (language/validate-query (:language program) query)
         checked-answer-vars (validate-answer-vars checked-query answer-vars)
         negated-query (normalize/negate-formula checked-query)]
     (mapv
       (fn [stage]
         (let [{:keys [formula kernel-call-depth unfold-depth stage-setup-elapsed-ms]}
               (staged-query-formula program negated-query stage)
               snapshots
               (mapv
                 (fn [raw-limit]
                   (program-answer-diagnostic-snapshot
                     program
                     formula
                     checked-answer-vars
                     {:fuel fuel
                      :raw-limit raw-limit
                      :call-depth kernel-call-depth
                      :sample-limit sample-limit
                      :proof-sample-limit proof-sample-limit
                      :proof-step-limit proof-step-limit
                      :stage-setup-elapsed-ms stage-setup-elapsed-ms}))
                 raw-limits)
               best-unique-count (apply max 0 (map :unique-count snapshots))
               first-productive-raw-limit
               (some->> snapshots
                        (filter #(pos? (:unique-count %)))
                        (sort-by :raw-limit)
                       first
                       :raw-limit)]
           {:stage stage
            :query-formula formula
            :unfold-depth unfold-depth
            :kernel-call-depth kernel-call-depth
            :productive? (pos? best-unique-count)
            :best-unique-count best-unique-count
            :first-productive-raw-limit first-productive-raw-limit
            :snapshots snapshots}))
       (range (inc call-depth))))))

(defn- default-query-ground-size-bound
  "Use the largest ground query subterm as the default parity size bound."
  [query]
  (reduce max 0
          (map term-constructor-size
               (filter ground-term?
                       (formula-term-subterms query)))))

(defn- verify-ground-query-assignments
  "Check precomputed ground answer assignments against the prover."
  [program checked-query checked-answer-vars assignments
   {:keys [answer-limit failure-timeout-ms fuel query-proof-limit
           status-timeout-ms]
    :or {query-proof-limit 1
         status-timeout-ms 250}}]
  (loop [assignments (seq assignments)
         answers []]
    (cond
      (nil? assignments)
      answers

      (and answer-limit (>= (count answers) answer-limit))
      answers

      :else
      (let [terms (first assignments)
            bindings (mapv vector checked-answer-vars terms)
            instantiated-query (subst/subst-formula checked-query bindings)
            quick-status (when (some? status-timeout-ms)
                           (query/query-status
                             program
                             instantiated-query
                             {:timeout-ms status-timeout-ms
                              :proof-limit query-proof-limit}))]
        (recur
          (next assignments)
          (if (= :fails quick-status)
            answers
            (let [success-proofs (query/query-succeeds
                                   program
                                   instantiated-query
                                   query-proof-limit
                                   fuel)]
              (if (seq success-proofs)
                (let [failure-proofs
                      (if (some? failure-timeout-ms)
                        (query/query-fails-within
                          program
                          instantiated-query
                          query-proof-limit
                          failure-timeout-ms)
                        (query/query-fails
                          program
                          instantiated-query
                          query-proof-limit
                          fuel))]
                  (if (empty? failure-proofs)
                    (conj answers {:bindings bindings
                                   :query instantiated-query
                                   :proofs success-proofs})
                    answers))
                answers))))))))

(defn- collect-ground-query-records
  "Enumerate closed ground answers by testing candidate tuples directly."
  [program checked-query checked-answer-vars candidate-terms opts]
  (let [candidate-terms (vec (ordered-distinct candidate-terms))
        exact-buckets (reduce (fn [buckets term]
                                (update buckets
                                        (term-constructor-size term)
                                        (fnil conj [])
                                        term))
                              {}
                              candidate-terms)]
    (verify-ground-query-assignments
      program
      checked-query
      checked-answer-vars
      (size-stratified-tuples exact-buckets (count checked-answer-vars))
      opts)))

(defn- materialize-ground-query-records
  "Project precomputed ground assignments into closed parity records."
  [checked-query checked-answer-vars assignments]
  (mapv (fn [terms]
          (let [bindings (mapv vector checked-answer-vars terms)]
            {:bindings bindings
             :query (subst/subst-formula checked-query bindings)
             :residuals []
             :proofs []}))
        assignments))

(defn query-parity-answers
  "Enumerate closed ground answers for `query` in a dedicated parity mode.

   This mode is intentionally separate from the generic symbolic `query-answers`
   API. It returns only records with empty residuals. For the known legacy
   list-family parity queries, it materializes closed answers directly from the
   extensional query shape. For all other cases, it falls back to bounded,
   query-driven ground candidate materialization above the semantic kernel.

   Returned maps preserve the generic answer-record surface:

   - `:bindings` ordered `[nom term]` pairs for the requested answer vars
   - `:residuals` always `[]` in this mode
   - `:proofs` proof terms when the fallback verifier is used, or `[]` on the
     list-family fast path
   - `:query` the instantiated ground query that succeeded"
  ([program query answer-vars]
   (query-parity-answers program query answer-vars {}))
  ([program query answer-vars {:keys [candidate-terms failure-timeout-ms fuel
                                      max-term-size proof-limit query-proof-limit
                                      status-timeout-ms]
                               :or {proof-limit 10
                                    query-proof-limit 1
                                    failure-timeout-ms 2000
                                    status-timeout-ms 250}}]
   (let [lang (:language program)
         checked-query (language/validate-query lang query)
         checked-answer-vars (validate-answer-vars checked-query answer-vars)
         max-term-size (or max-term-size
                           (default-query-ground-size-bound checked-query))
         fast-assignments (parity-fast-path-assignments
                            checked-query
                            checked-answer-vars)]
     (if (some? fast-assignments)
       (materialize-ground-query-records
         checked-query
         checked-answer-vars
         (take proof-limit fast-assignments))
       (mapv (fn [record]
               (assoc record :residuals []))
             (collect-ground-query-records
               program
               checked-query
               checked-answer-vars
               (vec
                 (map (fn [term]
                        (language/validate-term lang term))
                      (or candidate-terms
                          (query-ground-terms-up-to-size
                            lang
                            checked-query
                            max-term-size))))
               {:answer-limit proof-limit
                :failure-timeout-ms failure-timeout-ms
                :fuel fuel
                :query-proof-limit query-proof-limit
                :status-timeout-ms status-timeout-ms}))))))

(defn query-ground-answers
  "Enumerate bounded ground answers for `query`.

   `answer-vars` must be a sequence of free noms occurring in `query`. The
   caller controls completeness via `:max-depth` and `:fuel`. This helper is
   explicitly non-generic: it materializes answers by bounded Herbrand
   enumeration above the semantic kernel. Use `query-answers` for the generic
   symbolic API. Results are returned in declaration/enumeration order as maps
   containing:

   - `:bindings` ordered `[nom term]` pairs for the requested answer vars
   - `:query` the instantiated ground query that succeeded
   - `:proofs` proof terms witnessing success for that ground instance"
  ([program query answer-vars]
   (query-ground-answers program query answer-vars {}))
  ([program query answer-vars {:keys [failure-timeout-ms fuel limit max-depth proof-limit]
                               :or {max-depth 3
                                    failure-timeout-ms 250
                                    proof-limit 1}}]
   (let [checked-query (language/validate-query (:language program) query)
         checked-answer-vars (validate-answer-vars checked-query answer-vars)
         ground-terms (ground-terms-up-to-depth (:language program) max-depth)]
     (collect-ground-query-records
       program
       checked-query
       checked-answer-vars
       ground-terms
       {:answer-limit limit
        :failure-timeout-ms failure-timeout-ms
        :fuel fuel
        :query-proof-limit proof-limit
        :status-timeout-ms nil}))))
