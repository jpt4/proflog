(ns proflog.ast
  "Greenfield Proflog AST constructors and recognizers.

   The representation deliberately uses tagged lists, not vectors or maps for
   the core term and formula nodes, because the later tableau kernel will need
   to recurse relationally over variable-arity applications without resorting
   to projection-heavy host inspection. Clauses and language declarations remain
   ordinary Clojure maps because they are compilation products, not proof terms."
  (:require [clojure.core.logic.nominal :as nominal]
            [clojure.core.logic :refer [lvar]]))

;; -----------------------------------------------------------------------------
;; Nominal helper
;;
;; The greenfield tests and compile-time helpers need fresh noms in ordinary
;; Clojure code, not only inside logic goals. This macro binds each requested
;; symbol to a freshly allocated Nom value using core.logic's underlying `lvar`
;; constructor. The later proof kernel can still use `clojure.core.logic.nominal`
;; primitives directly when it needs goal-level freshening.
;; -----------------------------------------------------------------------------

(defmacro nom
  "Bind one or more fresh noms for plain Clojure code."
  [& args]
  (let [[syms body] (split-with symbol? args)]
    `(let [~@(mapcat (fn [sym]
                       [sym `(nominal/nom (lvar '~sym))])
                     syms)]
       ~@body)))

;; -----------------------------------------------------------------------------
;; Core tagged constructors
;;
;; The object language deliberately has a small vocabulary:
;;
;; - `(var n)` is a user/source variable represented by a nominal name `n`;
;; - `(par n)` is an internal rigid parameter introduced by proof search;
;; - `(app f ...)` is either a function application term or an atomic relation
;;   application, depending on where it appears and what the declared language
;;   says about `f`.
;;
;; Formula constructors mirror the usual first-order connectives. They are
;; intentionally syntax only: no constructor below checks arity, declaration
;; membership, or whether a symbol is a function or a relation. Those checks live
;; in `proflog.language`, which knows the program's signature.
;; -----------------------------------------------------------------------------

(defn var-term
  "Construct an object-language variable reference."
  [binding-nom]
  (list 'var binding-nom))

(defn par-term
  "Construct an internal rigid parameter term.

   Parameter terms are proof-time artifacts only; surface program validation
   must reject them."
  [binding-nom]
  (list 'par binding-nom))

(defn app-term
  "Construct a tagged application term or atom.

   The distinction between function symbols and relation symbols is enforced by
   the language validator, not by the raw AST constructor."
  [head-symbol & args]
  (list* 'app head-symbol args))

(defn pos-lit
  "Construct a positive atomic literal."
  [atom]
  (list 'pos atom))

(defn neg-lit
  "Construct a negative atomic literal."
  [atom]
  (list 'neg atom))

(defn eq-lit
  "Construct an equality literal."
  [left right]
  (list 'eq left right))

(defn neq-lit
  "Construct a disequality literal."
  [left right]
  (list 'neq left right))

(defn true-form
  "Construct the distinguished truth formula."
  []
  (list 'true))

(defn false-form
  "Construct the distinguished falsehood formula."
  []
  (list 'false))

(defn and-form
  "Construct a conjunction formula."
  [left right]
  (list 'and left right))

(defn or-form
  "Construct a disjunction formula."
  [left right]
  (list 'or left right))

(defn not-form
  "Construct a surface negation. The normalized core should not retain this."
  [formula]
  (list 'not formula))

(defn implies-form
  "Construct a surface implication. The normalized core should not retain this."
  [antecedent consequent]
  (list 'implies antecedent consequent))

(defn forall-form
  "Construct a universally quantified formula using a nominal tie."
  [binding-nom body]
  (list 'forall (nominal/tie binding-nom body)))

(defn once-forall-form
  "Construct a single-use universal formula using a nominal tie.

   This is an internal NNF-oriented form used when negating existential clause
   bodies for execution: it instantiates like a universal, but does not
   re-enqueue itself on the branch."
  [binding-nom body]
  (list 'once-forall (nominal/tie binding-nom body)))

(defn exists-form
  "Construct an existentially quantified formula using a nominal tie."
  [binding-nom body]
  (list 'exists (nominal/tie binding-nom body)))

(defn clause
  "Construct a surface Proflog clause.

   A surface program may temporarily have multiple clauses per relation. The
   language compiler will later desugar those into Fitting's single-clause core."
  [relation params body]
  {:relation relation
   :params (vec params)
   :body body})

;; -----------------------------------------------------------------------------
;; Predicates and selectors
;; -----------------------------------------------------------------------------

(defn tag-of
  "Return the leading tag symbol for a tagged list node, or nil."
  [node]
  (when (seq? node)
    (first node)))

(defn var-term?
  "Return true for a syntactically well-shaped object-language variable."
  [node]
  (and (seq? node)
       (= 'var (first node))
       (nil? (nnext node))))

(defn par-term?
  "Return true for a syntactically well-shaped internal parameter term."
  [node]
  (and (seq? node)
       (= 'par (first node))
       (nil? (nnext node))))

(defn app-term?
  "Return true for a tagged application with a symbolic head."
  [node]
  (and (seq? node)
       (= 'app (first node))
       (symbol? (second node))))

(declare literal? formula? nnf-formula?)

(defn term?
  "Return true when `node` is a well-formed greenfield term."
  [node]
  (cond
    (var-term? node) true
    (par-term? node) true
    (app-term? node) (every? term? (nnext node))
    :else false))

(defn atom?
  "Return true when `node` is an atomic application shape."
  [node]
  (app-term? node))

(defn literal?
  "Return true when `node` is a tagged literal."
  [node]
  (and (seq? node)
       (case (first node)
         pos (and (atom? (second node))
                  (nil? (nnext node)))
         neg (and (atom? (second node))
                  (nil? (nnext node)))
         eq  (and (term? (second node))
                  (term? (nth node 2 nil))
                  (nil? (nnext (next node))))
         neq (and (term? (second node))
                  (term? (nth node 2 nil))
                  (nil? (nnext (next node))))
         false)))

(defn quantifier-form?
  "Return true when `node` is a quantified formula with a nominal tie."
  [node quantifier-tag]
  (and (seq? node)
       (= quantifier-tag (first node))
       (nominal/tie? (second node))
       (nil? (nnext node))
       (formula? (:body (second node)))))

(defn bounded-quantifier-form?
  "Return true when `node` is an SJAS bounded quantifier form.

   The Willard SJAS surface keeps bounded quantification visible until NNF
   lowering can turn it into an ordinary quantifier plus a relational `leq`
   guard. The tie body is therefore a small map, not a formula directly."
  [node quantifier-tag]
  (and (seq? node)
       (= quantifier-tag (first node))
       (nominal/tie? (second node))
       (nil? (nnext node))
       (let [payload (:body (second node))]
         (and (map? payload)
              (contains? payload :bound)
              (contains? payload :body)
              (term? (:bound payload))
              (formula? (:body payload))))))

(defn formula?
  "Return true when `node` is a well-formed greenfield surface formula."
  [node]
  (cond
    (literal? node) true
    (and (seq? node) (= 'true (first node)) (nil? (next node))) true
    (and (seq? node) (= 'false (first node)) (nil? (next node))) true
    (and (seq? node) (= 'and (first node)))
    (and (formula? (second node))
         (formula? (nth node 2 nil))
         (nil? (nnext (next node))))
    (and (seq? node) (= 'or (first node)))
    (and (formula? (second node))
         (formula? (nth node 2 nil))
         (nil? (nnext (next node))))
    (and (seq? node) (= 'not (first node)))
    (and (formula? (second node))
         (nil? (nnext node)))
    (and (seq? node) (= 'implies (first node)))
    (and (formula? (second node))
         (formula? (nth node 2 nil))
         (nil? (nnext (next node))))
    (quantifier-form? node 'forall) true
    (quantifier-form? node 'once-forall) true
    (quantifier-form? node 'exists) true
    (bounded-quantifier-form? node 'bounded-forall) true
    (bounded-quantifier-form? node 'bounded-exists) true
    :else false))

(defn nnf-formula?
  "Return true when `node` is already in the NNF-oriented greenfield core.

   Surface `not` and `implies` are excluded here because the proof kernel works
   over negation normal form. Negative information is represented by negative
   literals, disequality, and dualized quantifiers instead of a runtime `not`
   connective."
  [node]
  (cond
    (literal? node) true
    (and (seq? node) (= 'true (first node)) (nil? (next node))) true
    (and (seq? node) (= 'false (first node)) (nil? (next node))) true
    (and (seq? node) (= 'and (first node)))
    (and (nnf-formula? (second node))
         (nnf-formula? (nth node 2 nil))
         (nil? (nnext (next node))))
    (and (seq? node) (= 'or (first node)))
    (and (nnf-formula? (second node))
         (nnf-formula? (nth node 2 nil))
         (nil? (nnext (next node))))
    (and (seq? node) (= 'forall (first node)))
    (and (nominal/tie? (second node))
         (nil? (nnext node))
         (nnf-formula? (:body (second node))))
    (and (seq? node) (= 'once-forall (first node)))
    (and (nominal/tie? (second node))
         (nil? (nnext node))
         (nnf-formula? (:body (second node))))
    (and (seq? node) (= 'exists (first node)))
    (and (nominal/tie? (second node))
         (nil? (nnext node))
         (nnf-formula? (:body (second node))))
    (and (bounded-quantifier-form? node 'bounded-forall)
         (nnf-formula? (:body (:body (second node)))))
    (and (bounded-quantifier-form? node 'bounded-exists)
         (nnf-formula? (:body (:body (second node)))))
    :else false))
