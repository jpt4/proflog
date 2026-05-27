(ns proflog.kernel.first-order
  "Equality-free first-order tableau component for profile-dispatched proofs.

   This layer narrows theorem proving to the branch state actually needed for
   equality-free first-order NNF. The unbounded theorem path follows the
   operational shape of alphaleanTAP: compact proof spines, vector unification
   templates over tagged formulas, gamma re-enqueue, and beta-sibling lemma
   threading. Equality, disequality maintenance, generated closed terms, and
   program-call machinery stay in the full Proflog kernel."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== conde fresh lcons run]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.kernel-support :as support]
            [proflog.subst :as subst]))

(declare prove-stateo
         lean-prove-stateo
         subst-termo
         subst-term*o
         subst-lito)

(defn- appendo
  [left right out]
  (conde
    [(== '() left)
     (== right out)]
    [(fresh [head tail rest]
       (== (lcons head tail) left)
       (== (lcons head rest) out)
       (appendo tail right rest))]))

(defn- membero
  [x xs]
  (fresh [head tail]
    (== (lcons head tail) xs)
    (conde
      [(== head x)]
      [(membero x tail)])))

(defn- lookupo
  [binding-nom env value]
  (fresh [rest]
    (conde
      [(== (lcons [binding-nom value] rest) env)]
      [(fresh [pair]
         (== (lcons pair rest) env)
         (lookupo binding-nom rest value))])))

(defn- subst-termo
  [term env out]
  (conde
    [(fresh [binding-nom]
       (== (list 'var binding-nom) term)
       (lookupo binding-nom env out))]
    [(fresh [binding-nom]
       (== (list 'par binding-nom) term)
       (== term out))]
    [(fresh [head args args-out]
       (== (lcons 'app (lcons head args)) term)
       (== (lcons 'app (lcons head args-out)) out)
       (subst-term*o args env args-out))]))

(defn- subst-term*o
  [terms env out]
  (conde
    [(== '() terms)
     (== '() out)]
    [(fresh [head tail head-out tail-out]
       (== (lcons head tail) terms)
       (== (lcons head-out tail-out) out)
       (subst-termo head env head-out)
       (subst-term*o tail env tail-out))]))

(defn- subst-lito
  [fml env out]
  (conde
    [(fresh [term term-out]
       (== (list 'pos term) fml)
       (== (list 'pos term-out) out)
       (subst-termo term env term-out))]
    [(fresh [term term-out]
       (== (list 'neg term) fml)
       (== (list 'neg term-out) out)
       (subst-termo term env term-out))]))

(defn- close-lito
  [lit lits proof]
  (conde
    [(fresh [atom]
       (== (list 'pos atom) lit)
       (membero (list 'neg atom) lits)
       (== '(close) proof))]
    [(fresh [atom]
       (== (list 'neg atom) lit)
       (membero (list 'pos atom) lits)
       (== '(close) proof))]))

(defn- save-lito
  [lit unexpanded lits env fuel proof]
  (fresh [next rest next-fuel prf]
    (== (lcons next rest) unexpanded)
    (== (list 'savefml prf) proof)
    (support/step-fuelo fuel next-fuel)
    (prove-stateo next rest (lcons lit lits) env next-fuel prf)))

(defn prove-stateo
  "Bounded equality-free first-order branch-closing tableau.

   This relation keeps ADR-0024's explicit fuel behavior for bounded slices.
   The unbounded public relation below uses the lean alphaleanTAP-shaped
   relation instead."
  [fml unexpanded lits env fuel proof]
  (conde
    [(fresh [left right next-fuel prf]
       (== (list 'and left right) fml)
       (== (list 'conj prf) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo left (lcons right unexpanded) lits env next-fuel prf))]

    [(fresh [left right next-fuel left-proof right-proof]
       (== (list 'or left right) fml)
       (== (list 'split left-proof right-proof) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo left unexpanded lits env next-fuel left-proof)
       (prove-stateo right unexpanded lits env next-fuel right-proof))]

    [(== (list 'false) fml)
     (== '(false-close) proof)]

    [(fresh [next rest next-fuel prf]
       (== (list 'true) fml)
       (== (lcons next rest) unexpanded)
       (== (list 'skip-true prf) proof)
       (support/step-fuelo fuel next-fuel)
       (prove-stateo next rest lits env next-fuel prf))]

    [(nominal/fresh [binding-nom]
       (fresh [free-var body pending next-fuel prf]
         (== (list 'forall (nominal/tie binding-nom body)) fml)
         (== (list 'univ prf) proof)
         (appendo unexpanded (list fml) pending)
         (support/step-fuelo fuel next-fuel)
         (prove-stateo body
                       pending
                       lits
                       (lcons [binding-nom free-var] env)
                       next-fuel
                       prf)))]

    [(nominal/fresh [binding-nom]
       (fresh [free-var body pending next-fuel prf]
         (== (list 'once-forall (nominal/tie binding-nom body)) fml)
         (== (list 'once-univ prf) proof)
         (appendo unexpanded (list fml) pending)
         (support/step-fuelo fuel next-fuel)
         (prove-stateo body
                       pending
                       lits
                       (lcons [binding-nom free-var] env)
                       next-fuel
                       prf)))]

    [(nominal/fresh [binding-nom]
       (nominal/fresh [parameter-nom]
         (fresh [body next-fuel prf]
           (== (list 'exists (nominal/tie binding-nom body)) fml)
           (== (list 'witness prf) proof)
           (support/step-fuelo fuel next-fuel)
           (prove-stateo body
                         unexpanded
                         lits
                         (lcons [binding-nom (ast/par-term parameter-nom)] env)
                         next-fuel
                         prf))))]

    [(fresh [lit]
       (subst-lito fml env lit)
       (conde
         [(close-lito lit lits proof)]
         [(save-lito lit unexpanded lits env fuel proof)]))]))

(defn- lean-prove-stateo
  "Unbounded alphaleanTAP-shaped equality-free branch tableau."
  ([fml unexpanded lits env proof]
   (fresh [lem-out]
     (lean-prove-stateo fml unexpanded lits env proof '() lem-out)))
  ([fml unexpanded lits env proof lem-in lem-out]
   (conde
     [(fresh [left right prf]
        (== ['and left right] fml)
        (== (lcons 'conj prf) proof)
        (lean-prove-stateo left
                           (lcons right unexpanded)
                           lits
                           env
                           prf
                           lem-in
                           lem-out))]

     [(fresh [left right left-proof right-proof lem-mid]
        (== ['or left right] fml)
        (== ['split left-proof right-proof] proof)
        (lean-prove-stateo left
                           unexpanded
                           lits
                           env
                           left-proof
                           lem-in
                           lem-mid)
        (lean-prove-stateo right
                           unexpanded
                           lits
                           env
                           right-proof
                           lem-mid
                           lem-out))]

     [(== ['false] fml)
      (== ['false-close] proof)
      (== lem-out lem-in)]

     [(fresh [next rest prf]
        (== ['true] fml)
        (== (lcons next rest) unexpanded)
        (== (lcons 'skip-true prf) proof)
        (lean-prove-stateo next rest lits env prf lem-in lem-out))]

     [(nominal/fresh [binding-nom]
        (fresh [free-var body pending prf]
          (== ['forall (nominal/tie binding-nom body)] fml)
          (== (lcons 'univ prf) proof)
          (appendo unexpanded (list fml) pending)
          (lean-prove-stateo body
                             pending
                             lits
                             (lcons [binding-nom free-var] env)
                             prf
                             lem-in
                             lem-out)))]

     [(nominal/fresh [binding-nom]
        (fresh [free-var body pending prf]
          (== ['once-forall (nominal/tie binding-nom body)] fml)
          (== (lcons 'once-univ prf) proof)
          (appendo unexpanded (list fml) pending)
          (lean-prove-stateo body
                             pending
                             lits
                             (lcons [binding-nom free-var] env)
                             prf
                             lem-in
                             lem-out)))]

     [(nominal/fresh [binding-nom]
        (nominal/fresh [parameter-nom]
          (fresh [body prf]
            (== ['exists (nominal/tie binding-nom body)] fml)
            (== (lcons 'witness prf) proof)
            (lean-prove-stateo body
                               unexpanded
                               lits
                               (lcons [binding-nom ['par parameter-nom]] env)
                               prf
                               lem-in
                               lem-out))))]

     [(fresh [lit]
        (subst-lito fml env lit)
        (conde
          [(fresh [atom]
             (== ['pos atom] lit)
             (conde
               [(== ['close] proof)
                (membero ['neg atom] lits)
                (== lem-out (lcons ['pos atom] lem-in))]
               [(== ['lem-close] proof)
                (membero ['neg atom] lem-in)
                (== lem-out (lcons ['pos atom] lem-in))]
               [(fresh [next rest prf]
                  (== (lcons next rest) unexpanded)
                  (== (lcons 'savefml prf) proof)
                  (lean-prove-stateo next
                                     rest
                                     (lcons lit lits)
                                     env
                                     prf
                                     lem-in
                                     lem-out))]))]
          [(fresh [atom]
             (== ['neg atom] lit)
             (conde
               [(== ['close] proof)
                (membero ['pos atom] lits)
                (== lem-out (lcons ['neg atom] lem-in))]
               [(== ['lem-close] proof)
                (membero ['pos atom] lem-in)
                (== lem-out (lcons ['neg atom] lem-in))]
               [(fresh [next rest prf]
                  (== (lcons next rest) unexpanded)
                  (== (lcons 'savefml prf) proof)
                  (lean-prove-stateo next
                                     rest
                                     (lcons lit lits)
                                     env
                                     prf
                                     lem-in
                                     lem-out))]))]))])))

(defn- skolemization-profile
  [formula]
  (letfn [(walk [f]
            (case (ast/tag-of f)
              and (merge-with + (walk (second f)) (walk (nth f 2)))
              or (merge-with + (walk (second f)) (walk (nth f 2)))
              forall (update (walk (:body (second f))) :forall (fnil inc 0))
              once-forall (update (walk (:body (second f))) :once-forall (fnil inc 0))
              exists (update (walk (:body (second f))) :exists (fnil inc 0))
              {}))]
    (walk formula)))

(defn- skolemization-beneficial?
  [formula]
  (let [{:keys [exists forall once-forall]} (skolemization-profile formula)]
    (and (pos? (or exists 0))
         (pos? (+ (or forall 0) (or once-forall 0))))))

(defn- skolem-symbol
  [counter]
  (symbol (str "fo-skolem-" counter)))

(defn- skolemize-nnf*
  [formula universals counter]
  (case (ast/tag-of formula)
    and (let [[left next-counter] (skolemize-nnf* (second formula)
                                                  universals
                                                  counter)
              [right final-counter] (skolemize-nnf* (nth formula 2)
                                                    universals
                                                    next-counter)]
          [(ast/and-form left right) final-counter])
    or (let [[left next-counter] (skolemize-nnf* (second formula)
                                                 universals
                                                 counter)
             [right final-counter] (skolemize-nnf* (nth formula 2)
                                                   universals
                                                   next-counter)]
         [(ast/or-form left right) final-counter])
    forall (let [tied (second formula)
                 var-term (ast/var-term (:binding-nom tied))
                 [body final-counter] (skolemize-nnf* (:body tied)
                                                      (conj universals var-term)
                                                      counter)]
             [(ast/forall-form (:binding-nom tied) body) final-counter])
    once-forall (let [tied (second formula)
                      var-term (ast/var-term (:binding-nom tied))
                      [body final-counter] (skolemize-nnf* (:body tied)
                                                           (conj universals var-term)
                                                           counter)]
                  [(ast/forall-form (:binding-nom tied) body) final-counter])
    exists (let [tied (second formula)
                 skolem-term (apply ast/app-term
                                    (skolem-symbol counter)
                                    universals)
                 body (subst/subst-formula
                        (:body tied)
                        [[(:binding-nom tied) skolem-term]])]
             (skolemize-nnf* body universals (inc counter)))
    [formula counter]))

(defn- skolemize-nnf
  [formula]
  (first (skolemize-nnf* formula [] 0)))

(def ^:private unary-proof-tags
  #{'conj 'univ 'once-univ 'witness 'savefml 'skip-true 'skolemized})

(def ^:private leaf-proof-tags
  #{'close 'lem-close 'false-close})

(declare canonicalize-proof)

(defn- single-coll?
  [xs]
  (and (seq xs)
       (nil? (next xs))
       (coll? (first xs))))

(defn- canonical-tail
  [proof]
  (let [tail (next proof)]
    (if (single-coll? tail)
      (first tail)
      tail)))

(defn- canonicalize-proof
  [proof]
  (cond
    (vector? proof)
    (let [tag (first proof)]
      (cond
        (= 'split tag)
        (list 'split
              (canonicalize-proof (second proof))
              (canonicalize-proof (nth proof 2)))

        (leaf-proof-tags tag)
        (list tag)

        (unary-proof-tags tag)
        (list tag (canonicalize-proof (second proof)))

        :else
        (mapv canonicalize-proof proof)))

    (seq? proof)
    (let [tag (first proof)]
      (cond
        (and (not (symbol? tag)) (single-coll? proof))
        (canonicalize-proof tag)

        (= 'split tag)
        (list 'split
              (canonicalize-proof (second proof))
              (canonicalize-proof (nth proof 2)))

        (leaf-proof-tags tag)
        (list tag)

        (unary-proof-tags tag)
        (list tag (canonicalize-proof (canonical-tail proof)))

        :else
        (map canonicalize-proof proof)))

    :else proof))

(defn- theorem-formula
  [fml]
  (if (skolemization-beneficial? fml)
    [(skolemize-nnf fml) true]
    [fml false]))

(defn proveo
  "Public equality-free first-order proof relation.

   The arity without fuel is the lean relation for direct relational use. The
   fuel arity intentionally keeps the bounded ADR-0024 relation so open
   branches can still be sliced without unbounded gamma descent."
  ([fml unexpanded lits env proof]
   (lean-prove-stateo fml unexpanded lits env proof))
  ([fml unexpanded lits env fuel proof]
   (prove-stateo fml unexpanded lits env fuel proof)))

(defn prove
  "Return up to `n` equality-free first-order proof terms for `fml`."
  ([fml] (prove fml 1))
  ([fml n]
   (let [[prepared skolemized?] (theorem-formula fml)]
     (doall
       (map (fn [proof]
              (let [canonical (canonicalize-proof proof)]
                (if skolemized?
                  (list 'skolemized canonical)
                  canonical)))
            (run n [proof]
              (proveo prepared '() '() '() proof))))))
  ([fml n fuel]
   (run n [proof]
     (proveo fml '() '() '() fuel proof))))
