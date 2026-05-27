(ns proflog.gamma
  "Bounded closed-term candidates for Fitting-style gamma instantiation.

   The kernel keeps the gamma rule readable as \"instantiate this universal
   with one admissible candidate\". This namespace owns the operationally
   necessary finite Herbrand enumeration policy for declared object-language
   constructors."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== fail fresh lcons membero or*]]
            [proflog.ast :as ast]))

(def ^:dynamic *closed-term-depth-cap*
  "Maximum constructor depth generated for one gamma-candidate choice.

   Fuel controls when gamma choices are available; this cap prevents one choice
   from materializing an unbounded Herbrand universe when a language has
   recursive constructors."
  2)

(def ^:dynamic *closed-term-count-cap*
  "Maximum number of closed candidates supplied to one proof search.

   This is a second generic guard for high-branching signatures such as
   `cons/2` over several constants, where even shallow depth slices can grow
   quickly."
  32)

(defn- declaration-order
  "Stable ordering for declared constructor symbols."
  [entry]
  [(val entry) (str (key entry))])

(defn constructor-facts
  "Return declared function symbols as ordered `[symbol arity]` facts.

   Host language declarations are maps because they are compiler products. The
   relational gamma experiment needs a sequence-oriented view that miniKanren
   can traverse with ordinary membership. Constants appear here as arity-zero
   function facts, matching `proflog.language/language` normalization."
  [lang]
  (->> (:functions lang)
       (sort-by declaration-order)
       (mapv (fn [[sym arity]]
               [sym arity]))))

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
    (reduce (fn [{:keys [seen] :as acc} x]
              (if (contains? seen x)
                acc
                (-> acc
                    (update :seen conj x)
                    (update :items conj x))))
            {:seen #{}
             :items []}
            xs)))

(defn closed-terms-up-to-depth
  "Enumerate declared-language closed terms up to constructor depth `max-depth`.

   Depth zero contains nullary constructors. Each later stratum uses all terms
   generated so far, but requires at least one argument from the previous exact
   depth, so every term is generated at its minimum constructor depth."
  [lang max-depth]
  (when (neg? max-depth)
    (throw (ex-info "Closed-term depth must be non-negative"
                    {:max-depth max-depth})))
  (let [declared-functions (sort-by declaration-order (:functions lang))
        nullary-terms (->> declared-functions
                           (filter (fn [[_ arity]]
                                     (zero? arity)))
                           (mapv (fn [[sym _]]
                                   (ast/app-term sym))))
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
                              ordered-distinct
                              vec)]
          (recur (inc depth)
                 (into terms-up-to exact-next)
                 exact-next))))))

(defn- fuel->closed-term-depth
  "Map the current micro-fuel slice to a finite constructor-depth bound."
  [fuel]
  (let [cap (max 0 *closed-term-depth-cap*)]
    (cond
      (nil? fuel) cap
      (integer? fuel) (min cap (max 0 fuel))
      :else 0)))

(declare formula-call-free?)

(defn- formula-call-free?
  "True when `formula` contains no positive or negative procedure atoms."
  [formula]
  (case (ast/tag-of formula)
    true true
    false true
    eq true
    neq true
    and (and (formula-call-free? (second formula))
             (formula-call-free? (nth formula 2)))
    or (and (formula-call-free? (second formula))
            (formula-call-free? (nth formula 2)))
    forall (formula-call-free? (:body (second formula)))
    once-forall (formula-call-free? (:body (second formula)))
    exists (formula-call-free? (:body (second formula)))
    false))

(defn- program-call-free?
  "True when every compiled clause body remains in the equality fragment."
  [prog]
  (every? (fn [{:keys [body negated-body]}]
            (and (formula-call-free? body)
                 (formula-call-free? negated-body)))
          (:clause-list prog)))

(defn closed-terms-for-fuel
  "Return bounded closed object-language terms for `prog` at the current fuel."
  [prog fuel]
  (if-let [lang (and (program-call-free? prog)
                    (:language prog))]
    (vec
      (take (max 0 *closed-term-count-cap*)
            (closed-terms-up-to-depth lang (fuel->closed-term-depth fuel))))
    []))

(def ^:private relational-candidate-source-tag
  ::relational-candidate-source)

(defn relational-candidate-source?
  "True for the opt-in ADR-0057 relational gamma source descriptor."
  [source]
  (and (map? source)
       (= relational-candidate-source-tag (:kind source))))

(defn candidate-source-empty?
  "True when a gamma source has no finite candidate terms.

   The proof-variable universal fallback is useful when no finite closed
   candidates exist. For finite verifier languages, however, continuing to
   explore that fallback creates a large unsound-for-performance search branch
   that the deterministic engine intentionally avoids."
  [source]
  (cond
    (relational-candidate-source? source)
    (empty? (:constructors source))

    :else
    (not (seq source))))

(defn relational-candidate-source
  "Build an opt-in relational gamma source for a language or compiled program.

   Unlike `closed-terms-for-fuel`, this function does not enumerate terms. It
   only records the constructor facts and depth bound needed by
   `closed-term-candidateo` to generate candidates relationally during proof
   search. Passing a compiled program preserves the current call-free guard used
   by the production host enumerator."
  [lang-or-prog fuel]
  (let [lang (if (:language lang-or-prog)
               (and (program-call-free? lang-or-prog)
                    (:language lang-or-prog))
               lang-or-prog)]
    {:kind relational-candidate-source-tag
     :constructors (if lang
                     (vec (reverse (constructor-facts lang)))
                     [])
     :max-depth (fuel->closed-term-depth fuel)}))

(defn- any-goalo
  "Return a disjunction over `goals`, or fail for an empty branch set."
  [goals]
  (let [goals (seq goals)]
    (if goals
      (or* goals)
      fail)))

(defn constructor-facto
  "Relate `sym` and `arity` to one constructor fact in `constructor-facts`."
  [constructor-facts sym arity]
  (if (seq constructor-facts)
    (membero [sym arity] (apply list constructor-facts))
    fail))

(declare closed-term-up-to-deptho
         closed-term-exact-deptho)

(defn- all-args-up-to-deptho
  "Relate `args` to `arity` closed terms whose depth is at most `max-depth`."
  [constructor-facts arity max-depth args]
  (if (zero? arity)
    (== args '())
    (fresh [arg rest]
      (== (lcons arg rest) args)
      (closed-term-up-to-deptho constructor-facts max-depth arg)
      (all-args-up-to-deptho constructor-facts (dec arity) max-depth rest))))

(defn- args-first-exact-at-indexo
  "Relate constructor arguments whose first max-depth argument is at `idx`.

   This keeps exact-depth generation finite without duplicating binary terms
   such as `node(depth1, depth1)` through both argument positions. Arguments
   before `idx` must be shallower; the selected argument is exact; later
   arguments may be any term up to that exact depth."
  [constructor-facts idx arity shallower-depth exact-depth args]
  (cond
    (zero? arity)
    fail

    (zero? idx)
    (fresh [arg rest]
      (== (lcons arg rest) args)
      (closed-term-exact-deptho constructor-facts exact-depth arg)
      (all-args-up-to-deptho constructor-facts
                             (dec arity)
                             exact-depth
                             rest))

    :else
    (fresh [arg rest]
      (== (lcons arg rest) args)
      (closed-term-up-to-deptho constructor-facts shallower-depth arg)
      (args-first-exact-at-indexo constructor-facts
                                  (dec idx)
                                  (dec arity)
                                  shallower-depth
                                  exact-depth
                                  rest))))

(defn- args-with-first-exacto
  "Relate `args` to constructor arguments that make a term exact-depth."
  [constructor-facts arity exact-depth args]
  (let [shallower-depth (dec exact-depth)]
    (any-goalo
      (map (fn [idx]
             (args-first-exact-at-indexo constructor-facts
                                         idx
                                         arity
                                         shallower-depth
                                         exact-depth
                                         args))
           (range arity)))))

(defn closed-term-exact-deptho
  "Relate `term` to a closed constructor term of exactly `depth`.

   The depth argument is a ground operational bound supplied by the proof
   profile. The relation remains open in `term`, so proof search can choose the
   closed object-language term inside miniKanren instead of receiving a
   pre-materialized host vector."
  [constructor-facts depth term]
  (if (neg? depth)
    fail
    (any-goalo
      (for [[sym arity] constructor-facts]
        (cond
          (zero? arity)
          (if (zero? depth)
            (== term (ast/app-term sym))
            fail)

          (pos? depth)
          (fresh [args]
            (== (lcons 'app (lcons sym args)) term)
            (args-with-first-exacto constructor-facts
                                    arity
                                    (dec depth)
                                    args))

          :else
          fail)))))

(defn closed-term-up-to-deptho
  "Relate `term` to a closed constructor term no deeper than `max-depth`."
  [constructor-facts max-depth term]
  (if (neg? max-depth)
    fail
    (any-goalo
      (map (fn [depth]
             (closed-term-exact-deptho constructor-facts depth term))
           (range (inc max-depth))))))

(defn closed-term-candidateo
  "Relate `term` to one closed gamma candidate.

   The production path supplies a finite concrete collection as explicit state,
   preserving the ADR-0020 membership boundary. ADR-0057 adds an opt-in
   relational source descriptor that generates bounded closed terms inside
   miniKanren without calling `closed-terms-for-fuel`."
  [terms term]
  (cond
    (relational-candidate-source? terms)
    (closed-term-up-to-deptho (:constructors terms)
                              (:max-depth terms)
                              term)

    (seq terms)
    (membero term (apply list terms))

    :else
    fail))
