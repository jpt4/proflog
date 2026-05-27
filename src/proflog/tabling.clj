(ns proflog.tabling
  "Canonical proof-state keys for the ADR-0017 tabling layer.

   This namespace is deliberately separate from `proflog.kernel`. The kernel
   should remain a readable Fitting-style tableau relation; tabling and
   canonical state reuse live here as derived operational machinery."
  (:require [clojure.core.logic :refer [fresh project run tabled]]
            [proflog.ast :as ast]
            [proflog.gamma :as gamma]
            [proflog.kernel :as kernel]
            [proflog.kernel-support :as support]
            [proflog.subst :as subst]))

(declare alpha-shape-term
         alpha-shape-formula
         canonical-term
         canonical-formula
         prove-stateo)

(defn- walked-term
  [sigma term]
  (support/walk-term-pure term sigma))

(defn- walked-formula
  [sigma formula]
  (case (ast/tag-of formula)
    pos (ast/pos-lit (walked-term sigma (second formula)))
    neg (ast/neg-lit (walked-term sigma (second formula)))
    eq (ast/eq-lit (walked-term sigma (second formula))
                   (walked-term sigma (nth formula 2)))
    neq (ast/neq-lit (walked-term sigma (second formula))
                     (walked-term sigma (nth formula 2)))
    and (ast/and-form (walked-formula sigma (second formula))
                      (walked-formula sigma (nth formula 2)))
    or (ast/or-form (walked-formula sigma (second formula))
                    (walked-formula sigma (nth formula 2)))
    not (ast/not-form (walked-formula sigma (second formula)))
    implies (ast/implies-form (walked-formula sigma (second formula))
                              (walked-formula sigma (nth formula 2)))
    forall (let [tied (second formula)]
             (ast/forall-form (:binding-nom tied)
                              (walked-formula sigma (:body tied))))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form (:binding-nom tied)
                                        (walked-formula sigma (:body tied))))
    exists (let [tied (second formula)]
             (ast/exists-form (:binding-nom tied)
                              (walked-formula sigma (:body tied))))
    formula))

(defn- alpha-shape-term
  [term]
  (case (ast/tag-of term)
    var [:var]
    par [:par]
    app (into [:app (second term)]
              (map alpha-shape-term (nnext term)))
    [:term term]))

(defn- alpha-shape-formula
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
    [:formula formula]))

(defn- canonical-id
  [ctx category nom prefix]
  (let [mapping-key (keyword (str (name category) "s"))
        next-key (keyword (str "next-" (name category)))]
    (if-let [canonical (get-in ctx [mapping-key nom])]
      [ctx canonical]
      (let [idx (get ctx next-key 0)
            canonical [prefix idx]]
        [(-> ctx
             (assoc-in [mapping-key nom] canonical)
             (assoc next-key (inc idx)))
         canonical]))))

(defn- canonical-binding
  [ctx nom]
  (canonical-id ctx :binding nom :binding))

(defn- canonical-var
  [ctx nom]
  (canonical-id ctx :var nom :var))

(defn- canonical-par
  [ctx nom]
  (canonical-id ctx :par nom :par))

(defn- canonical-term
  [ctx term]
  (case (ast/tag-of term)
    var (canonical-var ctx (second term))
    par (canonical-par ctx (second term))
    app (let [[ctx args]
              (reduce (fn [[ctx args] arg]
                        (let [[ctx arg-key] (canonical-term ctx arg)]
                          [ctx (conj args arg-key)]))
                      [ctx []]
                      (nnext term))]
          [ctx (into [:app (second term)] args)])
    [ctx [:term term]]))

(defn- canonical-formula
  [ctx formula]
  (case (ast/tag-of formula)
    true [ctx [:true]]
    false [ctx [:false]]
    pos (let [[ctx term-key] (canonical-term ctx (second formula))]
          [ctx [:pos term-key]])
    neg (let [[ctx term-key] (canonical-term ctx (second formula))]
          [ctx [:neg term-key]])
    eq (let [[ctx left-key] (canonical-term ctx (second formula))
             [ctx right-key] (canonical-term ctx (nth formula 2))]
         [ctx [:eq left-key right-key]])
    neq (let [[ctx left-key] (canonical-term ctx (second formula))
              [ctx right-key] (canonical-term ctx (nth formula 2))]
          [ctx [:neq left-key right-key]])
    and (let [[ctx left-key] (canonical-formula ctx (second formula))
              [ctx right-key] (canonical-formula ctx (nth formula 2))]
          [ctx [:and left-key right-key]])
    or (let [[ctx left-key] (canonical-formula ctx (second formula))
             [ctx right-key] (canonical-formula ctx (nth formula 2))]
         [ctx [:or left-key right-key]])
    not (let [[ctx body-key] (canonical-formula ctx (second formula))]
          [ctx [:not body-key]])
    implies (let [[ctx left-key] (canonical-formula ctx (second formula))
                  [ctx right-key] (canonical-formula ctx (nth formula 2))]
              [ctx [:implies left-key right-key]])
    forall (let [tied (second formula)
                 [ctx binding-key] (canonical-binding ctx (:binding-nom tied))
                 [ctx body-key] (canonical-formula ctx (:body tied))]
             [ctx [:forall binding-key body-key]])
    once-forall (let [tied (second formula)
                      [ctx binding-key] (canonical-binding ctx (:binding-nom tied))
                      [ctx body-key] (canonical-formula ctx (:body tied))]
                  [ctx [:once-forall binding-key body-key]])
    exists (let [tied (second formula)
                 [ctx binding-key] (canonical-binding ctx (:binding-nom tied))
                 [ctx body-key] (canonical-formula ctx (:body tied))]
             [ctx [:exists binding-key body-key]])
    [ctx [:formula formula]]))

(defn- sort-key
  [value]
  (pr-str value))

(defn- canonical-formula-set
  [ctx env sigma formulas]
  (reduce (fn [[ctx keys] formula]
            (let [[ctx formula-key] (canonical-formula ctx formula)]
              [ctx (conj keys formula-key)]))
          [ctx []]
          (sort-by (comp sort-key alpha-shape-formula)
                   (map #(walked-formula sigma (subst/subst-formula % env))
                        formulas))))

(defn- canonical-neq-set
  [ctx sigma neqs]
  (reduce (fn [[ctx keys] [left right]]
            (let [[ctx left-key] (canonical-term ctx (walked-term sigma left))
                  [ctx right-key] (canonical-term ctx (walked-term sigma right))]
              [ctx (conj keys [:neq left-key right-key])]))
          [ctx []]
          (sort-by (fn [[left right]]
                     (sort-key [(alpha-shape-term (walked-term sigma left))
                                (alpha-shape-term (walked-term sigma right))]))
                   neqs)))

(defn- canonical-sigma
  [ctx sigma]
  (reduce (fn [[ctx keys] [binding-nom value]]
            (let [[ctx binding-key] (canonical-binding ctx binding-nom)
                  [ctx value-key] (canonical-term ctx (walked-term sigma value))]
              [ctx (conj keys [:bind binding-key value-key])]))
          [ctx []]
          (sort-by (fn [[_ value]]
                   (sort-key (alpha-shape-term (walked-term sigma value))))
                   sigma)))

(defn- canonical-proof-vars
  [ctx proof-vars]
  (reduce (fn [[ctx keys] binding-nom]
            (let [[ctx var-key] (canonical-var ctx binding-nom)]
              [ctx (conj keys var-key)]))
          [ctx []]
          ;; Proof-var membership is semantic, but list order is bookkeeping.
          (sort-by (fn [binding-nom]
                     (sort-key (get-in ctx [:vars binding-nom] [:unseen-var])))
                   proof-vars)))

(defn state-key
  "Return a conservative canonical key for a proof-search state.

   The key normalizes alpha-equivalent noms and ignores ordering of agenda
   formulas, saved literals, residual formulas, disequalities, and substitution
   entries. It is intentionally conservative: future ADR-0017 work can merge
   more states, but this first key must not collapse obviously distinct states."
  [{:keys [agenda lits neqs sigma residuals env proof-vars prog-key program-id program gamma-terms fuel call-depth]}]
  (let [env (or env '())
        sigma (or sigma '())
        ctx {:bindings {}
             :vars {}
             :pars {}
             :next-binding 0
             :next-var 0
             :next-par 0}
        [ctx agenda-key] (canonical-formula-set ctx env sigma (or agenda '()))
        [ctx lits-key] (canonical-formula-set ctx env sigma (or lits '()))
        [ctx residuals-key] (canonical-formula-set ctx env sigma (or residuals '()))
        [ctx neqs-key] (canonical-neq-set ctx sigma (or neqs '()))
        [ctx sigma-key] (canonical-sigma ctx sigma)
        [_ proof-vars-key] (canonical-proof-vars ctx (or proof-vars '()))]
    {:agenda agenda-key
     :lits lits-key
     :residuals residuals-key
     :neqs neqs-key
     :sigma sigma-key
     :proof-vars proof-vars-key
     :program (or prog-key program-id program)
     :gamma-terms (seq gamma-terms)
     :fuel fuel
     :call-depth call-depth}))

(deftype KernelTableState [key raw]
  Object
  (equals [_ other]
    (and (instance? KernelTableState other)
         (= key (.-key ^KernelTableState other))))
  (hashCode [_]
    (hash key))
  (toString [_]
    (str "#proflog/kernel-table-state " (pr-str key))))

(def ^:dynamic *kernel-table-stats*
  "Optional atom updated when a tabled kernel state is evaluated from scratch.

   This is diagnostic instrumentation only. The semantic table is the
   core.logic table attached to the current run substitution, not this atom."
  nil)

(defn- program-cache-key
  [prog]
  (when prog
    [:compiled-program (System/identityHashCode prog)]))

(defn- kernel-state
  [fml unexpanded lits env proof-vars sigma neqs prog gamma-terms fuel]
  (let [agenda (cons fml (or unexpanded '()))
        key (state-key {:agenda agenda
                        :lits lits
                        :neqs neqs
                        :sigma sigma
                        :env env
                        :proof-vars proof-vars
                        :prog-key (program-cache-key prog)
                        :gamma-terms gamma-terms
                        :fuel fuel})]
    (KernelTableState.
      key
      {:fml fml
       :unexpanded unexpanded
       :lits lits
       :env env
       :proof-vars proof-vars
       :sigma sigma
       :neqs neqs
       :prog prog
       :gamma-terms gamma-terms
       :fuel fuel})))

(defn- record-kernel-cache-miss!
  [^KernelTableState state]
  (when *kernel-table-stats*
    (swap! *kernel-table-stats*
           (fnil (fn [stats]
                   (-> stats
                       (update :misses (fnil inc 0))
                       (update-in [:misses-by-key (.-key state)] (fnil inc 0))))
                 {}))))

(defn- with-recursive-kernel-tabling
  [goal]
  (fn [substitution]
    (with-redefs [kernel/*recursive-prove-stateo* prove-stateo]
      (goal substitution))))

(def ^:private tabled-kernel-stateo
  (tabled [state sigma-out neqs-out proof]
    (let [{:keys [fml unexpanded lits env proof-vars sigma neqs prog gamma-terms fuel]}
          (.-raw ^KernelTableState state)]
      (record-kernel-cache-miss! state)
      (with-recursive-kernel-tabling
        (kernel/prove-stateo fml
                             unexpanded
                             lits
                             env
                             proof-vars
                             sigma
                             sigma-out
                             neqs
                             neqs-out
                             prog
                             gamma-terms
                             fuel
                             proof)))))

(defn prove-stateo
  "Tabled kernel-state relation for ADR-0017.

   Recursive calls are routed back through this wrapper only when callers bind
   `proflog.kernel/*recursive-prove-stateo*`. Public helpers below do that for
   the duration of `run`, keeping ordinary `proflog.kernel` behavior unchanged."
  [fml unexpanded lits env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (project [fml unexpanded lits env proof-vars sigma neqs fuel]
    (tabled-kernel-stateo
      (kernel-state fml unexpanded lits env proof-vars sigma neqs prog gamma-terms fuel)
      sigma-out
      neqs-out
      proof)))

(defn proveo
  "Tabled analogue of `proflog.kernel/proveo`."
  ([fml unexpanded lits env proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() nil proof)))
  ([fml unexpanded lits env fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() fuel proof))))

(defn prove-programo
  "Tabled analogue of `proflog.kernel/prove-programo`."
  ([fml unexpanded lits env prog proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out prog (gamma/closed-terms-for-fuel prog nil) nil proof)))
  ([fml unexpanded lits env prog fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out prog (gamma/closed-terms-for-fuel prog fuel) fuel proof))))

(defn prove
  "Return up to `n` proof terms using the ADR-0017 tabled kernel wrapper."
  ([fml] (prove fml 1))
  ([fml n]
   (with-redefs [kernel/*recursive-prove-stateo* prove-stateo]
     (doall
       (run n [proof]
         (proveo fml '() '() '() proof)))))
  ([fml n fuel]
   (with-redefs [kernel/*recursive-prove-stateo* prove-stateo]
     (doall
       (run n [proof]
         (proveo fml '() '() '() fuel proof))))))

(defn prove-program
  "Return up to `n` proof terms relative to `prog` through the tabled wrapper."
  ([prog fml n]
   (with-redefs [kernel/*recursive-prove-stateo* prove-stateo]
     (doall (kernel/prove-program prog fml n))))
  ([prog fml n fuel]
   (with-redefs [kernel/*recursive-prove-stateo* prove-stateo]
     (doall (kernel/prove-program prog fml n fuel)))))
