(ns proflog.combinatory-logic
  "SKI combinatory logic programs for ADR-0046 and ADR-0047.

   The semantics are ordinary Proflog clauses written through the ADR-0010
   frontend. Host helpers in this namespace build object-language terms for
   examples; they do not reduce or evaluate SKI terms."
  (:require [proflog.ast :as ast]
            [proflog.frontend :as pf]))

;; -----------------------------------------------------------------------------
;; Object-language signature
;;
;; `ap/2` is the application constructor. `scomb`, `kcomb`, and `icomb` are the
;; primitive combinators. The remaining constants are inert data used by worked
;; examples and tests.
;; -----------------------------------------------------------------------------

(def ski-language
  (pf/language
    (constants zero
               scomb kcomb icomb
               a b c)
    (functions (s 1)
               (ap 2))
    (relations (step 2)
               (full-step 2)
               (eval-for 3))))

(defn c
  "Construct a nullary object-language constant."
  [sym]
  (ast/app-term sym))

(defn ap
  "Construct object-language application."
  [left right]
  (ast/app-term 'ap left right))

(defn numeral
  "Construct a Peano numeral term for bounded evaluation examples."
  [n]
  (if (zero? n)
    (c 'zero)
    (ast/app-term 's (numeral (dec n)))))

(defn true-term
  "Boolean true encoded as the K combinator."
  []
  (c 'kcomb))

(defn false-term
  "Boolean false encoded as K I."
  []
  (ap (c 'kcomb) (c 'icomb)))

(defn choose
  "Build `(boolean then else)` in curried SKI application form."
  [boolean-term then-term else-term]
  (ap (ap boolean-term then-term) else-term))

(defn skk
  "Build `S K K x`, the usual SKI identity expression."
  [x]
  (ap (ap (ap (c 'scomb) (c 'kcomb)) (c 'kcomb)) x))

(defn sii
  "Build `S I I`, the SKI duplicator."
  []
  (ap (ap (c 'scomb) (c 'icomb)) (c 'icomb)))

(defn omega
  "Build `(S I I) (S I I)`, the self-reproducing SKI term."
  []
  (ap (sii) (sii)))

(defn reduction-trace-formula
  "Build a formula asserting each adjacent SKI term is connected by a relation.

   This is a query-construction helper only. It does not reduce a term on the
   host; each edge in the supplied trace is proved by the compiled relation.
   The default relation is `step/2`; pass `{:relation 'full-step}` for the
   fuller contextual relation used by the quine example."
  ([terms]
   (reduction-trace-formula terms {}))
  ([terms {:keys [relation]
           :or {relation 'step}}]
   (let [terms (vec terms)]
     (when (< (count terms) 2)
       (throw (ex-info "A reduction trace needs at least two terms"
                       {:terms terms})))
     (reduce ast/and-form
             (mapv (fn [[before after]]
                     (ast/pos-lit
                       (ast/app-term relation before after)))
                   (partition 2 1 terms))))))

;; -----------------------------------------------------------------------------
;; Program
;; -----------------------------------------------------------------------------

(def ski-program
  (pf/proflog ski-language
    (|- (step before after)
      (exists [x]
        (and (= before (ap icomb x))
             (= after x))))

    (|- (step before after)
      (exists [x y]
        (and (= before (ap (ap kcomb x) y))
             (= after x))))

    (|- (step before after)
      (exists [x y z]
        (and (= before (ap (ap (ap scomb x) y) z))
             (= after (ap (ap x z) (ap y z))))))

    ;; Reduce the function side of an application. This is enough to evaluate
    ;; ordinary left-associated SKI programs such as `((K I) a) b`, while still
    ;; leaving the reduction strategy explicit in the Proflog program.
    (|- (step before after)
      (exists [function argument reduced-function]
        (and (= before (ap function argument))
             (step function reduced-function)
             (= after (ap reduced-function argument)))))

    (|- (eval-for steps start final)
      (and (= steps zero)
           (= start final)))

    (|- (eval-for steps start final)
      (exists [rest middle]
        (and (= steps (s rest))
             (step start middle)
             (eval-for rest middle final))))

    ;; Full contextual one-step reduction for focused examples that need
    ;; argument-position contraction. Keeping it separate from `step/2` avoids
    ;; slowing the ADR-0046 examples that only need root and left-spine search.
    (|- (full-step before after)
      (step before after))

    (|- (full-step before after)
      (exists [function argument reduced-function]
        (and (= before (ap function argument))
             (full-step function reduced-function)
             (= after (ap reduced-function argument)))))

    (|- (full-step before after)
      (exists [function argument reduced-argument]
        (and (= before (ap function argument))
             (full-step argument reduced-argument)
             (= after (ap function reduced-argument)))))))

(defn program
  "Return the compiled SKI program."
  []
  ski-program)
