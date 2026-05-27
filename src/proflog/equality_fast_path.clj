(ns proflog.equality-fast-path
  "Generic acceleration for existential equality-only branch closure.

   Some hard legacy families reduce, after ordinary negation and branching, to
   nested existentials over conjunctions of only `eq` / `neq` literals. The
   ordinary kernel can prove these cases, but doing so through the full
   relational tableau machinery is expensive. This namespace keeps the
   optimization separate from the kernel rules: it recognizes that restricted
   branch shape and performs the same consistency check directly."
  (:require [proflog.ast :as ast]
            [proflog.kernel-support :as support]
            [proflog.subst :as subst]))

(declare instantiate-existentials
         conjunction-literals
         contains-exists?
         occurs-binding?
         unify-termo-pure
         unify-term*o-pure)

(defn- fresh-parameter-term
  []
  (ast/par-term (gensym "fast-par-")))

(defn- same-termo?
  [left right sigma]
  (support/same-walked-term?
    (support/walk-term-pure left sigma)
    (support/walk-term-pure right sigma)))

(defn- occurs-binding?
  [tag binding-nom term sigma]
  (let [walked (support/walk-term-pure term sigma)]
    (case (ast/tag-of walked)
      var (and (= tag 'var)
               (= binding-nom (second walked)))
      par (and (= tag 'par)
               (= binding-nom (second walked)))
      app (boolean (some #(occurs-binding? tag binding-nom % sigma)
                         (nnext walked)))
      false)))

(defn- bindable-term?
  [term]
  (let [tag (ast/tag-of term)]
    (or (= 'var tag)
        (= 'par tag))))

(defn- bind-term
  [term value sigma]
  (cons [(second term) value] sigma))

(defn- unify-term*o-pure
  [left-args right-args sigma]
  (loop [sigma sigma
         left-args left-args
         right-args right-args]
    (cond
      (and (empty? left-args) (empty? right-args))
      {:status :ok :sigma sigma}

      (or (empty? left-args) (empty? right-args))
      {:status :contradiction}

      :else
      (let [step (unify-termo-pure (first left-args) (first right-args) sigma)]
        (if (= :ok (:status step))
          (recur (:sigma step) (rest left-args) (rest right-args))
          step)))))

(defn- unify-termo-pure
  [left right sigma]
  (let [left-root (support/walk-term-pure left sigma)
        right-root (support/walk-term-pure right sigma)
        left-tag (ast/tag-of left-root)
        right-tag (ast/tag-of right-root)]
    (cond
      (support/same-walked-term? left-root right-root)
      {:status :ok :sigma sigma}

      (= 'var left-tag)
      (if (occurs-binding? 'var (second left-root) right-root sigma)
        {:status :contradiction}
        {:status :ok :sigma (bind-term left-root right-root sigma)})

      (= 'var right-tag)
      (if (occurs-binding? 'var (second right-root) left-root sigma)
        {:status :contradiction}
        {:status :ok :sigma (bind-term right-root left-root sigma)})

      (= 'par left-tag)
      (if (occurs-binding? 'par (second left-root) right-root sigma)
        {:status :contradiction}
        {:status :ok :sigma (bind-term left-root right-root sigma)})

      (= 'par right-tag)
      (if (occurs-binding? 'par (second right-root) left-root sigma)
        {:status :contradiction}
        {:status :ok :sigma (bind-term right-root left-root sigma)})

      (and (= 'app left-tag) (= 'app right-tag))
      (if (and (= (second left-root) (second right-root))
               (= (count (nnext left-root)) (count (nnext right-root))))
        (unify-term*o-pure (nnext left-root) (nnext right-root) sigma)
        {:status :contradiction})

      :else
      {:status :contradiction})))

(defn- instantiate-existentials
  [formula]
  (case (ast/tag-of formula)
    exists (let [tied (second formula)]
             (recur
               (subst/subst-formula
                 (:body tied)
                 [[(:binding-nom tied) (fresh-parameter-term)]])))
    and (let [left (instantiate-existentials (second formula))
              right (instantiate-existentials (nth formula 2))]
          (when (and left right)
            (ast/and-form left right)))
    eq formula
    neq formula
    true formula
    false formula
    nil))

(defn- contains-exists?
  [formula]
  (case (ast/tag-of formula)
    exists true
    and (or (contains-exists? (second formula))
            (contains-exists? (nth formula 2)))
    eq false
    neq false
    true false
    false false
    false))

(defn- conjunction-literals
  [formula]
  (case (ast/tag-of formula)
    and (let [left (conjunction-literals (second formula))
              right (conjunction-literals (nth formula 2))]
          (when (and left right)
            (into left right)))
    eq [formula]
    neq [formula]
    true []
    false [:false]
    nil))

(defn maybe-close-proof
  "Return a proof tag when `formula` closes through the equality-only fast path.

   The optimization only handles nested existentials over conjunctions of `eq`
   and `neq` literals after the current lexical environment has been applied.
   Unsupported shapes return nil so the kernel can continue with its ordinary
   relational rules."
  [formula env]
  (let [substituted (subst/subst-formula formula env)
        instantiated (instantiate-existentials substituted)
        literals (and instantiated (conjunction-literals instantiated))]
    (when (and (contains-exists? substituted)
               literals
               (some #(= 'neq (ast/tag-of %)) literals))
      (if (= [:false] literals)
        '(fast-equality-close)
        (let [eqs (filter #(= 'eq (ast/tag-of %)) literals)
              neqs (filter #(= 'neq (ast/tag-of %)) literals)
              result (reduce (fn [state eq-lit]
                               (if (= :ok (:status state))
                                 (unify-termo-pure (second eq-lit)
                                                   (nth eq-lit 2)
                                                   (:sigma state))
                                 (reduced state)))
                             {:status :ok :sigma '()}
                             eqs)]
          (cond
            (= :contradiction (:status result))
            '(fast-equality-close)

            (some (fn [neq-lit]
                    (same-termo? (second neq-lit)
                                 (nth neq-lit 2)
                                 (:sigma result)))
                  neqs)
            '(fast-equality-close)

            :else
            nil))))))
