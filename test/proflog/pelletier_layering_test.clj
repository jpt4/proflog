(ns proflog.pelletier-layering-test
  (:require [clojure.test :refer [deftest is testing]]
            [proflog.ast :as ast]
            [proflog.kernel :as kernel]
            [proflog.kernel.first-order :as first-order]
            [proflog.language :as language]
            [proflog.normalize :as normalize]
            [proflog.pelletier-test :as pelletier]
            [proflog.proof :as proof]
            [proflog.query :as query]))

(def pelletier-layering-ids
  [24 44])

(defn- add-arity
  [arities sym arity]
  (if-let [existing (get arities sym)]
    (do
      (when-not (= existing arity)
        (throw (ex-info "Symbol must keep one arity in the aggregate test language"
                        {:symbol sym
                         :existing existing
                         :new arity})))
      arities)
    (assoc arities sym arity)))

(defn- merge-arities
  [arity-maps]
  (reduce
    (fn [acc arities]
      (reduce-kv add-arity acc arities))
    {}
    arity-maps))

(defn- merge-signatures
  [& signatures]
  {:functions (merge-arities (map :functions signatures))
   :relations (merge-arities (map :relations signatures))})

(def empty-signature
  {:functions {}
   :relations {}})

(declare formula-signature term-signature)

(defn- term-signature
  [term]
  (case (ast/tag-of term)
    var empty-signature
    par empty-signature
    app (let [sym (second term)
              args (nnext term)]
          (update
            (apply merge-signatures (map term-signature args))
            :functions
            add-arity
            sym
            (count args)))))

(defn- atom-signature
  [atom]
  (let [sym (second atom)
        args (nnext atom)]
    (update
      (apply merge-signatures (map term-signature args))
      :relations
      add-arity
      sym
      (count args))))

(defn- formula-signature
  [formula]
  (case (ast/tag-of formula)
    true empty-signature
    false empty-signature
    pos (atom-signature (second formula))
    neg (atom-signature (second formula))
    eq (merge-signatures
         (term-signature (second formula))
         (term-signature (nth formula 2)))
    neq (merge-signatures
          (term-signature (second formula))
          (term-signature (nth formula 2)))
    and (merge-signatures
          (formula-signature (second formula))
          (formula-signature (nth formula 2)))
    or (merge-signatures
         (formula-signature (second formula))
         (formula-signature (nth formula 2)))
    not (formula-signature (second formula))
    implies (merge-signatures
              (formula-signature (second formula))
              (formula-signature (nth formula 2)))
    forall (formula-signature (:body (second formula)))
    once-forall (formula-signature (:body (second formula)))
    exists (formula-signature (:body (second formula)))))

(defn- pelletier-branch
  [id]
  (pelletier/theorem-branch ((:builder (pelletier/problem-by-id id)))))

(defn- subproblem-relation
  [id]
  (symbol (str "pelletier-subproblem-" id)))

(def aggregate-relation
  'pelletier-aggregate)

(defn- layering-program
  []
  (let [branches (into {}
                       (map (fn [id]
                              [id (pelletier-branch id)]))
                       pelletier-layering-ids)
        signature (apply merge-signatures (map formula-signature (vals branches)))
        subproblem-relations (into {}
                                   (map (fn [id]
                                          [(subproblem-relation id) 0]))
                                   pelletier-layering-ids)
        lang (language/language
               {:functions (:functions signature)
                :relations (merge (:relations signature)
                                  subproblem-relations
                                  {aggregate-relation 0})})
        subproblem-clauses
        (mapv (fn [[id branch]]
                ;; A successful query for the nullary relation opens a negative
                ;; procedure call, whose compiled negated body is this original
                ;; Pelletier theorem branch.
                (ast/clause (subproblem-relation id)
                            []
                            (normalize/negate-formula branch)))
              branches)
        aggregate-body
        (reduce
          ast/and-form
          (map (fn [id]
                 (ast/pos-lit (ast/app-term (subproblem-relation id))))
               pelletier-layering-ids))]
    {:branches branches
     :query (ast/pos-lit (ast/app-term aggregate-relation))
     :program (language/compile-program
                lang
                (conj subproblem-clauses
                      (ast/clause aggregate-relation [] aggregate-body)))}))

(deftest pelletier-subgoals-compose-through-program-kernel
  (testing "the component obligations close through theorem dispatch"
    (let [{:keys [branches]} (layering-program)
          first-order-calls (atom 0)
          original-first-order-prove first-order/prove]
      (with-redefs [first-order/prove
                    (fn [& args]
                      (swap! first-order-calls inc)
                      (apply original-first-order-prove args))]
        (doseq [[id branch] branches]
          (is (seq (kernel/prove branch 1))
              (str "Pelletier Problem " id " should close as a direct theorem"))))
        (is (= (count branches) @first-order-calls))))
  (testing "the aggregate valid Proflog program query reaches first-order subbranch dispatch"
    (let [{:keys [program query]} (layering-program)
          host-first-order-calls (atom 0)
          first-order-proveo-calls (atom 0)
          original-first-order-proveo first-order/proveo]
      (with-redefs [first-order/prove
                    (fn [& _]
                      (swap! host-first-order-calls inc)
                      (throw (ex-info "Program search unexpectedly entered first-order/prove"
                                      {})))
                    first-order/proveo
                    (fn [& args]
                      (swap! first-order-proveo-calls inc)
                      (apply original-first-order-proveo args))]
        (let [proof (first (query/query-succeeds program query 1 4))]
          (is proof)
          (is (proof/contains-step? proof 'neg-call))
          (is (proof/contains-step? proof 'profiled))
          (is (proof/contains-step? proof 'first-order))))
      (is (pos? @first-order-proveo-calls))
      (is (zero? @host-first-order-calls)))))
