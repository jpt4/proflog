(ns proflog.minikanren-constraints
  "Project-local miniKanren constraint overlay.

   Core.logic exposes the lower-level constraint hooks needed by the
   faster-miniKanren-style relations used in ADR-36, but not public
   `symbolo`, `numbero`, or `absento` vars. This namespace keeps those
   relations local to Proflog and builds them only from public core.logic
   constraint machinery."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :as logic]
            [clojure.core.logic :refer [!= predc]]
            [clojure.core.logic.protocols :as logic-protocols]))

(defn symbolo
  "Constraint relation requiring `x` to be a Clojure symbol.

   The check delays while `x` is still a logic variable and runs once the
   variable is sufficiently instantiated."
  [x]
  (predc x symbol? 'symbolo))

(defn numbero
  "Constraint relation requiring `x` to be a Clojure number.

   The check delays while `x` is still a logic variable and runs once the
   variable is sufficiently instantiated."
  [x]
  (predc x number? 'numbero))

(defn- absento-children
  [term]
  (cond
    (logic/lcons? term)
    [(logic-protocols/lfirst term) (logic-protocols/lnext term)]

    (map? term)
    (mapcat (fn [[k v]]
              [[k v] k v])
            term)

    (coll? term)
    (seq term)

    :else
    nil))

(defn- constrain-children
  [children f state]
  (loop [children (seq children)
         state state]
    (if-let [children (seq children)]
      (when-let [state (f (first children) state)]
        (recur (next children) state))
      state)))

(defn- absento-reifier
  []
  (fn [_ target+term _ r state]
    (let [[target term] target+term]
      (list 'absento
            (logic/-reify state target r)
            (logic/-reify state term r)))))

(defn- absento-runnable?
  [[target term] state]
  (let [walked-term (logic-protocols/walk state term)
        walked-target (logic-protocols/walk state target)]
    (or (not (logic/lvar? walked-term))
        (and (logic/lvar? walked-target)
             (= walked-target walked-term)))))

(defn- same-open-var?
  [left right]
  (and (logic/lvar? left)
       (logic/lvar? right)
       (= left right)))

(defn- deep-absento
  [target term]
  (let [reifier (absento-reifier)]
    (logic/fixc
      [target term]
      (fn loop [[target term] state reifier]
        (let [walked-target (logic-protocols/walk state target)
              walked-term (logic-protocols/walk state term)]
          (if (same-open-var? walked-target walked-term)
            logic/fail
            (if-let [children (seq (absento-children walked-term))]
              (fn [state]
                (when-let [state ((!= walked-target walked-term) state)]
                  (constrain-children
                    children
                    (fn [child state]
                      ((logic/fixc [target child]
                                    loop
                                    absento-runnable?
                                    reifier)
                       state))
                    state)))
              (!= walked-target walked-term)))))
      absento-runnable?
      reifier)))

(defn absento
  "Constraint relation requiring `target` to be absent from `term`.

   Every discovered node is constrained with disequality against `target`, and
   open subterms keep delayed constraints that are checked when more structure
   appears. Unlike core.logic `treec`, this overlay traverses map keys as well
   as values and reifies residuals in the public `absento` vocabulary."
  [target term]
  (deep-absento target term))
