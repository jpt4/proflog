(ns proflog.turing-completeness
  "Two-counter Minsky machine programs for ADR-0044 and ADR-0045.

   This namespace demonstrates expressive power, not a new evaluator. The
   machine semantics below are written with the public ADR-0010 frontend and are
   compiled into ordinary Proflog clauses. Clojure helpers in this file build
   object-language terms for tests and examples; they do not compute machine
   transitions."
  (:require [proflog.ast :as ast]
            [proflog.frontend :as pf]))

;; -----------------------------------------------------------------------------
;; Object-language signature
;;
;; A configuration is `(cfg label counter0 counter1)`. Counters are Peano terms
;; built from `zero` and `s`. Labels are constants. Instruction-table entries are
;; relations, not data constructors, so different programs can reuse the same
;; interpreter clauses while supplying different instruction facts.
;; -----------------------------------------------------------------------------

(def counter-machine-language
  (pf/language
    (constants zero
               l0 l1 halt-label
               i0 ihalt)
    (functions (s 1)
               (cfg 3))
    (relations (inc0 2)
               (inc1 2)
               (decjz0 3)
               (decjz1 3)
               (halt-state 1)
               (step 2)
               (run 2)
               (run-for 3)
               (halt-config 1)
               (halts-in 2)
               (halts-in-steps 3))))

(defn app
  "Construct an object-language application term."
  [sym & args]
  (apply ast/app-term sym args))

(defn numeral
  "Construct a Peano numeral term for examples and tests."
  [n]
  (if (zero? n)
    (app 'zero)
    (app 's (numeral (dec n)))))

(defn config
  "Construct a two-counter machine configuration term."
  [label counter0 counter1]
  (app 'cfg
       (app label)
       (numeral counter0)
       (numeral counter1)))

(defn trace-formula
  "Build a formula asserting each adjacent state is connected by `step/2`.

   This helper constructs a Proflog query formula only. The caller supplies the
   states, and every edge is still proved by the compiled `step/2` relation.
   Set `:halt? true` to also require `(halt-config final-state)`."
  ([states]
   (trace-formula states {}))
  ([states {:keys [halt?]}]
   (let [states (vec states)]
     (when (< (count states) 2)
       (throw (ex-info "A trace formula needs at least two states"
                       {:states states})))
     (let [step-formulas (mapv (fn [[before after]]
                                 (ast/pos-lit
                                   (ast/app-term 'step before after)))
                               (partition 2 1 states))
           formulas (cond-> step-formulas
                      halt? (conj (ast/pos-lit
                                    (ast/app-term 'halt-config
                                                  (peek states)))))]
       (reduce ast/and-form formulas)))))

;; -----------------------------------------------------------------------------
;; Generic two-counter interpreter
;;
;; The interpreter is quoted source for the frontend macro below. Its clauses are
;; generic over the instruction relations: they do not mention transfer-machine
;; labels or incrementer labels. A concrete machine is obtained by appending
;; instruction facts to this interpreter.
;; -----------------------------------------------------------------------------

(def ^:private interpreter-source
  '((|- (step before after)
      (exists [label next c0 c1]
        (and (= before (cfg label c0 c1))
             (inc0 label next)
             (= after (cfg next (s c0) c1)))))

    (|- (step before after)
      (exists [label next c0 c1]
        (and (= before (cfg label c0 c1))
             (inc1 label next)
             (= after (cfg next c0 (s c1))))))

    (|- (step before after)
      (exists [label dec zero-next c1]
        (and (= before (cfg label zero c1))
             (decjz0 label dec zero-next)
             (= after (cfg zero-next zero c1)))))

    (|- (step before after)
      (exists [label dec zero-next pred c1]
        (and (= before (cfg label (s pred) c1))
             (decjz0 label dec zero-next)
             (= after (cfg dec pred c1)))))

    (|- (step before after)
      (exists [label dec zero-next c0]
        (and (= before (cfg label c0 zero))
             (decjz1 label dec zero-next)
             (= after (cfg zero-next c0 zero)))))

    (|- (step before after)
      (exists [label dec zero-next c0 pred]
        (and (= before (cfg label c0 (s pred)))
             (decjz1 label dec zero-next)
             (= after (cfg dec c0 pred)))))

    (|- (run start final)
      (= start final))

    (|- (run start final)
      (exists [middle]
        (and (step start middle)
             (run middle final))))

    (|- (run-for steps start final)
      (and (= steps zero)
           (= start final)))

    (|- (run-for steps start final)
      (exists [rest middle]
        (and (= steps (s rest))
             (step start middle)
             (run-for rest middle final))))

    (|- (halt-config config)
      (exists [label c0 c1]
        (and (= config (cfg label c0 c1))
             (halt-state label))))

    (|- (halts-in start final)
      (and (halt-config final)
           (run start final)))

    (|- (halts-in-steps steps start final)
      (and (halt-config final)
           (run-for steps start final)))))

(defmacro machine-program
  "Compile the generic interpreter plus concrete instruction facts.

   Instruction facts still use variable-only frontend heads. For example,
   `inc1(l1, l0)` is written as a relation clause whose body equates the
   instruction parameters with the concrete labels."
  [& instruction-forms]
  `(pf/proflog counter-machine-language
     ~@interpreter-source
     ~@instruction-forms))

;; -----------------------------------------------------------------------------
;; Concrete machines
;; -----------------------------------------------------------------------------

(def transfer-machine
  (machine-program
    (|- (decjz0 label dec zero-next)
      (and (= label l0)
           (= dec l1)
           (= zero-next halt-label)))

    (|- (inc1 label next)
      (and (= label l1)
           (= next l0)))

    (|- (halt-state label)
      (= label halt-label))))

(def incrementer-machine
  (machine-program
    (|- (inc0 label next)
      (and (= label i0)
           (= next ihalt)))

    (|- (halt-state label)
      (= label ihalt))))

(defn transfer-program
  "Return the transfer-loop machine program.

   Starting from `cfg(l0, n, m)`, this machine reaches
   `cfg(halt-label, 0, n + m)` after `2n + 1` transitions."
  []
  transfer-machine)

(defn incrementer-program
  "Return a second machine that increments counter0 once and halts."
  []
  incrementer-machine)
