(ns proflog.pretty
  "Small presentation helpers for greenfield Proflog terms and answers.

   These helpers are intentionally host-side only. They never change kernel
   semantics; they just collapse ground Peano numerals into decimals for
   inspection while leaving symbolic terms intact."
  (:require [proflog.ast :as ast]))

(declare pretty-term pretty-formula)

(defn peano->int
  "Return the decimal value of a ground Peano numeral, or nil otherwise.

   Recognized numerals use the declared `zero`/`s` object-language encoding:
   `(app zero)`, `(app s (app zero))`, `(app s (app s (app zero)))`, ..."
  [term]
  (loop [n 0
         current term]
    (cond
      (and (= 'app (ast/tag-of current))
           (= 'zero (second current))
           (empty? (nnext current)))
      n

      (and (= 'app (ast/tag-of current))
           (= 's (second current))
           (= 1 (count (nnext current))))
      (recur (inc n) (nth current 2))

      :else nil)))

(defn pretty-term
  "Render a term into a display-oriented shape.

   Ground Peano numerals become integers. Other applications become ordinary
   s-expressions with pretty-printed arguments. Variables and parameters remain
   explicit tagged terms."
  [term]
  (if-some [n (peano->int term)]
    n
    (case (ast/tag-of term)
      app (apply list (second term) (map pretty-term (nnext term)))
      term)))

(defn pretty-formula
  "Render a formula into a display-oriented shape with decimal numerals."
  [formula]
  (case (ast/tag-of formula)
    pos (list 'pos (pretty-term (second formula)))
    neg (list 'neg (pretty-term (second formula)))
    eq (list 'eq (pretty-term (second formula))
             (pretty-term (nth formula 2)))
    neq (list 'neq (pretty-term (second formula))
              (pretty-term (nth formula 2)))
    once-forall (let [tied (second formula)]
                  (list 'once-forall
                        (:binding-nom tied)
                        (pretty-formula (:body tied))))
    and (list 'and (pretty-formula (second formula))
              (pretty-formula (nth formula 2)))
    or (list 'or (pretty-formula (second formula))
             (pretty-formula (nth formula 2)))
    true '(true)
    false '(false)
    formula))

(defn pretty-answer
  "Render one exported answer record with decimal numerals where possible."
  [{:keys [bindings residuals proofs] :as answer}]
  (cond-> answer
    bindings (assoc :bindings
                    (mapv (fn [[binding-nom term]]
                            [binding-nom (pretty-term term)])
                          bindings))
    residuals (assoc :residuals (mapv pretty-formula residuals))
    proofs (assoc :proofs proofs)))
