(ns proflog.kernel.equality-fragment
  "Profiled proof layer for call-free equality-bearing finite verification.

   This layer is intentionally generic: it knows about NNF equality formulas,
   explicit equality state, delayed disequalities, and finite gamma candidates.
   It does not know about group verification, transition systems, or any other
   benchmark family."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== all appendo conde fail fresh lcons run run*]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.equality :as equality]
            [proflog.gamma :as gamma]
            [proflog.kernel-support :as support]
            [proflog.normalize :as normalize]
            [proflog.program :as program]
            [proflog.subst :as subst]))

(declare prove-stateo
         equality-fragment-formula?
         select-formula)

(defn- formula-priority
  "Generic branch priority shared by the relational experiment and host engine.

   This is not a family-specific heuristic: it only prefers immediately
   closing equality work before branch-splitting and universal expansion."
  [formula]
  (case (ast/tag-of formula)
    false 0
    eq 1
    neq 2
    and 3
    exists 4
    true 5
    or 6
    forall 7
    once-forall 7
    8))

(def ^:dynamic *agenda-selecto*
  "Relation used by the equality-fragment prover to choose the next formula.

   The default preserves the original fair relational agenda selection. Direct
   experimental relation callers may bind this selector for ground finite
   agendas; ADR-0057's public parity route uses a separate finite driver so it
   can keep agenda ordering deterministic while still delegating term
   generation, equality, and disequality work to miniKanren relations."
  support/selecto)

(defn- priority-selecto
  "Select the highest-priority formula from a ground finite agenda.

   The selected proof work still happens in miniKanren; this selector only
   removes the large irrelevant search factor introduced by arbitrary agenda
   order. If a future caller supplies an open agenda, fall back to the original
   relational selector."
  [fml agenda rest]
  (if (seq? agenda)
    (let [[idx formula] (->> agenda
                             (map-indexed vector)
                             (sort-by (fn [[idx formula]]
                                        [(formula-priority formula) idx]))
                             first)
          agenda-vec (vec agenda)
          rest-list (apply list
                           (concat (subvec agenda-vec 0 idx)
                                   (subvec agenda-vec (inc idx))))]
      (all
        (== formula fml)
        (== rest-list rest)))
    (support/selecto fml agenda rest)))

(defn- continue-with-agendao
  [unexpanded env proof-vars sigma sigma-out neqs neqs-out gamma-terms fuel proof]
  (fresh [next rest]
    (== (lcons next rest) unexpanded)
    (prove-stateo next
                  rest
                  env
                  proof-vars
                  sigma
                  sigma-out
                  neqs
                  neqs-out
                  gamma-terms
                  fuel
                  proof)))

(defn close-agendao
  "Close one call-free equality-fragment branch.

   The universal cases deliberately try finite closed candidates before the
   proof-variable fallback. That makes finite verifier counterexamples visible
   without teaching the layer about a particular verifier family."
  [agenda env proof-vars sigma sigma-out neqs neqs-out gamma-terms fuel proof]
  (fresh [fml unexpanded]
    (*agenda-selecto* fml agenda unexpanded)
    (conde
      ;; Conjunction: both conjuncts remain on the same branch.
      [(fresh [left right next-fuel prf]
         (== (list 'and left right) fml)
         (== (list 'eq-frag-conj prf) proof)
         (support/step-fuelo fuel next-fuel)
         (prove-stateo left
                       (lcons right unexpanded)
                       env
                       proof-vars
                       sigma
                       sigma-out
                       neqs
                       neqs-out
                       gamma-terms
                       next-fuel
                       prf))]

      ;; Disjunction: each branch must close.
      [(fresh [left right next-fuel sigma-mid neqs-mid left-proof right-proof]
         (== (list 'or left right) fml)
         (== (list 'eq-frag-split left-proof right-proof) proof)
         (support/step-fuelo fuel next-fuel)
         (if (gamma/candidate-source-empty? gamma-terms)
           (all
             (prove-stateo left
                           unexpanded
                           env
                           proof-vars
                           sigma
                           sigma-mid
                           neqs
                           neqs-mid
                           gamma-terms
                           next-fuel
                           left-proof)
             (prove-stateo right
                           unexpanded
                           env
                           proof-vars
                           sigma-mid
                           sigma-out
                           neqs-mid
                           neqs-out
                           gamma-terms
                           next-fuel
                           right-proof))
           (fresh [left-sigma-out left-neqs-out right-sigma-out right-neqs-out]
             (prove-stateo left
                           unexpanded
                           env
                           proof-vars
                           sigma
                           left-sigma-out
                           neqs
                           left-neqs-out
                           gamma-terms
                           next-fuel
                           left-proof)
             (prove-stateo right
                           unexpanded
                           env
                           proof-vars
                           sigma
                           right-sigma-out
                           neqs
                           right-neqs-out
                           gamma-terms
                           next-fuel
                           right-proof)
             (== sigma sigma-out)
             (== neqs neqs-out))))]

      ;; False is an immediate closed branch.
      [(== (list 'false) fml)
       (== sigma sigma-out)
       (== neqs neqs-out)
       (== '(eq-frag-false-close) proof)]

      ;; True contributes no contradiction and only progresses if more work
      ;; remains on the branch.
      [(fresh [next-fuel prf]
         (== (list 'true) fml)
         (== (list 'eq-frag-skip-true prf) proof)
         (support/step-fuelo fuel next-fuel)
         (continue-with-agendao unexpanded
                                env
                                proof-vars
                                sigma
                                sigma-out
                                neqs
                                neqs-out
                                gamma-terms
                                next-fuel
                                prf))]

      ;; Universal / once-universal finite candidate path. A single concrete
      ;; counterexample is enough to close a false universal verifier law.
      [(nominal/fresh [binding-nom]
         (fresh [body body-subst narrowed-env witness-term next-fuel prf]
           (== (list 'forall (nominal/tie binding-nom body)) fml)
           (== (list 'eq-frag-univ-candidate prf) proof)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (gamma/closed-term-candidateo gamma-terms witness-term)
           (support/step-fuelo fuel next-fuel)
           (prove-stateo body-subst
                         unexpanded
                         (lcons [binding-nom witness-term] env)
                         proof-vars
                         sigma
                         sigma-out
                         neqs
                         neqs-out
                         gamma-terms
                         next-fuel
                         prf)))]
      [(nominal/fresh [binding-nom]
         (fresh [body body-subst narrowed-env witness-term next-fuel prf]
           (== (list 'once-forall (nominal/tie binding-nom body)) fml)
           (== (list 'eq-frag-once-univ-candidate prf) proof)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (gamma/closed-term-candidateo gamma-terms witness-term)
           (support/step-fuelo fuel next-fuel)
           (prove-stateo body-subst
                         unexpanded
                         (lcons [binding-nom witness-term] env)
                         proof-vars
                         sigma
                         sigma-out
                         neqs
                         neqs-out
                         gamma-terms
                         next-fuel
                         prf)))]

      ;; Proof-variable fallback keeps the layer useful outside finite
      ;; constant-only counterexample search. The full kernel remains available
      ;; when this incomplete profiled path does not close.
      [(if (gamma/candidate-source-empty? gamma-terms)
         (nominal/fresh [binding-nom]
           (nominal/fresh [free-var-nom]
             (fresh [body body-subst narrowed-env next-fuel prf]
               (== (list 'forall (nominal/tie binding-nom body)) fml)
               (== (list 'eq-frag-univ-var prf) proof)
               (subst/remove-bindo binding-nom env narrowed-env)
               (subst/subst-formulao body narrowed-env body-subst)
               (support/step-fuelo fuel next-fuel)
               (prove-stateo body-subst
                             unexpanded
                             (lcons [binding-nom (ast/var-term free-var-nom)] env)
                             (lcons free-var-nom proof-vars)
                             sigma
                             sigma-out
                             neqs
                             neqs-out
                             gamma-terms
                             next-fuel
                             prf))))
         fail)]
      [(if (gamma/candidate-source-empty? gamma-terms)
         (nominal/fresh [binding-nom]
           (nominal/fresh [free-var-nom]
             (fresh [body body-subst narrowed-env next-fuel prf]
               (== (list 'once-forall (nominal/tie binding-nom body)) fml)
               (== (list 'eq-frag-once-univ-var prf) proof)
               (subst/remove-bindo binding-nom env narrowed-env)
               (subst/subst-formulao body narrowed-env body-subst)
               (support/step-fuelo fuel next-fuel)
               (prove-stateo body-subst
                             unexpanded
                             (lcons [binding-nom (ast/var-term free-var-nom)] env)
                             (lcons free-var-nom proof-vars)
                             sigma
                             sigma-out
                             neqs
                             neqs-out
                             gamma-terms
                             next-fuel
                             prf))))
         fail)]

      ;; Existential: ordinary delta with a rigid parameter.
      [(nominal/fresh [binding-nom]
         (nominal/fresh [parameter-nom]
           (fresh [body body-subst narrowed-env next-fuel prf]
             (== (list 'exists (nominal/tie binding-nom body)) fml)
             (== (list 'eq-frag-witness prf) proof)
             (subst/remove-bindo binding-nom env narrowed-env)
             (subst/subst-formulao body narrowed-env body-subst)
             (support/step-fuelo fuel next-fuel)
             (prove-stateo body-subst
                           unexpanded
                           (lcons [binding-nom (ast/par-term parameter-nom)] env)
                           proof-vars
                           sigma
                           sigma-out
                           neqs
                           neqs-out
                           gamma-terms
                           next-fuel
                           prf))))]

      ;; Positive equality closes immediately only when impossible.
      [(fresh [lit left right contradiction-proof]
         (subst/subst-formulao fml env lit)
         (== (list 'eq left right) lit)
         (equality/eq-contradictiono left right sigma contradiction-proof)
         (== sigma sigma-out)
         (== neqs neqs-out)
         (== (list 'eq-frag-eq-contradiction contradiction-proof) proof))]
      [(fresh [lit left right sigma-mid step-proof branch-proof]
         (subst/subst-formulao fml env lit)
         (== (list 'eq left right) lit)
         (equality/unify-termo left right sigma sigma-mid step-proof)
         (equality/neq-violatedo neqs sigma-mid branch-proof)
         (== sigma-mid sigma-out)
         (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
         (== (list 'eq-frag-eq-step step-proof branch-proof) proof))]
      [(fresh [lit left right sigma-mid step-proof next-fuel prf]
         (subst/subst-formulao fml env lit)
         (== (list 'eq left right) lit)
         (equality/unify-termo left right sigma sigma-mid step-proof)
         (support/stable-neqso neqs sigma-mid)
         (== (list 'eq-frag-eq-step step-proof prf) proof)
         (support/step-fuelo fuel next-fuel)
         (continue-with-agendao unexpanded
                                env
                                proof-vars
                                sigma-mid
                                sigma-out
                                neqs
                                neqs-out
                                gamma-terms
                                next-fuel
                                prf))]

      ;; Negative equality closes when already false, or when proof-local
      ;; variables can be bound to make it false. Otherwise it progresses only
      ;; if more branch work remains.
      [(fresh [lit left right]
         (subst/subst-formulao fml env lit)
         (== (list 'neq left right) lit)
         (equality/same-termo left right sigma)
         (== sigma sigma-out)
         (== neqs neqs-out)
         (== '(eq-frag-refl-close) proof))]
      [(fresh [lit left right sigma-mid new-bindings binding rest step-proof]
         (subst/subst-formulao fml env lit)
         (== (list 'neq left right) lit)
         (equality/unify-termo left right sigma sigma-mid step-proof)
         (appendo new-bindings sigma sigma-mid)
         (== (lcons binding rest) new-bindings)
         (support/proof-bindingso new-bindings proof-vars)
         (== sigma-mid sigma-out)
         (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
         (== (list 'eq-frag-neq-close step-proof) proof))]
      [(fresh [lit left right next-fuel prf]
         (subst/subst-formulao fml env lit)
         (== (list 'neq left right) lit)
         (support/rigid-different-termo left right sigma)
         (== (list 'eq-frag-neq-rigid prf) proof)
         (support/step-fuelo fuel next-fuel)
         (continue-with-agendao unexpanded
                                env
                                proof-vars
                                sigma
                                sigma-out
                                neqs
                                neqs-out
                                gamma-terms
                                next-fuel
                                prf))]
      [(fresh [lit left right next-fuel prf]
         (subst/subst-formulao fml env lit)
         (== (list 'neq left right) lit)
         (== (list 'eq-frag-neq-store prf) proof)
         (support/step-fuelo fuel next-fuel)
         (continue-with-agendao unexpanded
                                env
                                proof-vars
                                sigma
                                sigma-out
                                (lcons [left right] neqs)
                                neqs-out
                                gamma-terms
                                next-fuel
                                prf))])))

(defn prove-stateo
  [fml unexpanded env proof-vars sigma sigma-out neqs neqs-out gamma-terms fuel proof]
  (close-agendao
    (lcons fml unexpanded)
    env
    proof-vars
    sigma
    sigma-out
    neqs
    neqs-out
    gamma-terms
    fuel
    proof))

(defn proveo
  "Public equality-fragment proof relation used by the profiled kernel layer."
  ([fml unexpanded env gamma-terms proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded env '() '() sigma-out '() neqs-out gamma-terms nil proof)))
  ([fml unexpanded env gamma-terms fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded env '() '() sigma-out '() neqs-out gamma-terms fuel proof))))

(defn prove
  ([fml gamma-terms]
   (prove fml gamma-terms 1 nil))
  ([fml gamma-terms n fuel]
   (run n [proof]
     (proveo fml '() '() gamma-terms fuel proof))))

(defn prove-program-relationalo
  "Opt-in ADR-0057 equality-fragment relation for one program formula.

   This entry uses the relation-shaped equality-fragment prover above and a
   relational gamma source descriptor. It deliberately avoids the deterministic
   host engine below. Callers receive a distinct proof marker so experimental
   relational evidence cannot be mistaken for the production host-backed
   `profiled equality-fragment` path."
  [prog fml gamma-source fuel proof]
  (fresh [subproof]
    (conde
      [(fresh [atom env body negated-body]
         (== (list 'pos atom) fml)
         (== (list 'pos-call
                   (list 'profiled
                         'relational-equality-fragment
                         'relational-proof))
             proof)
         (program/call-clauseo prog atom env body negated-body)
         (proveo body '() env gamma-source fuel subproof))]
      [(fresh [atom env body negated-body]
         (== (list 'neg atom) fml)
         (== (list 'neg-call
                   (list 'profiled
                         'relational-equality-fragment
                         'relational-proof))
             proof)
         (program/call-clauseo prog atom env body negated-body)
         (proveo negated-body '() env gamma-source fuel subproof))]
      [(== (list 'profiled
                 'relational-equality-fragment
                 'relational-proof)
           proof)
       (proveo fml '() '() gamma-source fuel subproof)])))

(defn- relation-first
  "Run one miniKanren relation query and return its first reified value."
  [goal]
  (first (run 1 [q] (goal q))))

(defn- relation-succeeds?
  "True when a miniKanren goal has at least one answer."
  [goal]
  (boolean
    (seq
      (run 1 [q]
        goal
        (== q true)))))

(defn- relational-candidates
  "Materialize candidates by running the ADR-0057 relational gamma source.

   This is intentionally not `gamma/closed-terms-for-fuel`: candidate terms are
   generated by `closed-term-candidateo` from constructor facts. The finite
   driver needs the concrete sequence only to try universal witnesses in a
   deterministic order."
  [gamma-source]
  (run* [candidate]
    (gamma/closed-term-candidateo gamma-source candidate)))

(defn- relational-call-target
  "Resolve a ground program call from the compiled program map.

   The ADR-0057 finite driver previously used `program/call-clauseo` here, but
   that forced core.logic to reify very large transition-system clause bodies
   before proof search. This lookup is generic compiled-program setup, not a
   proof of the formula; equality, disequality, and gamma work still run
   through relational operations below."
  [prog query-formula]
  (when (#{'pos 'neg} (ast/tag-of query-formula))
    (let [atom (second query-formula)]
      (when (= 'app (ast/tag-of atom))
        (let [relation (second atom)
              args (nnext atom)
              clause (get-in prog [:clauses relation])]
          (when clause
            (let [env (map (fn [param arg]
                             [param arg])
                           (:params clause)
                           args)]
              {:tag (ast/tag-of query-formula)
               :env env
               :body (if (= 'pos (ast/tag-of query-formula))
                       (:body clause)
                       (:negated-body clause))})))))))

(def ^:private relation-eq-contradiction*
  (memoize
    (fn [left right sigma]
      (relation-first
        (fn [q]
          (equality/eq-contradictiono left right sigma q))))))

(defn- relation-eq-contradiction
  [left right sigma]
  (relation-eq-contradiction* left right sigma))

(def ^:private relation-unify*
  (memoize
    (fn [left right sigma]
      (relation-first
        (fn [q]
          (fresh [sigma-out proof]
            (equality/unify-termo left right sigma sigma-out proof)
            (== q [sigma-out proof])))))))

(defn- relation-unify
  [left right sigma]
  (relation-unify* left right sigma))

(def ^:private relation-same?*
  (memoize
    (fn [left right sigma]
      (relation-succeeds?
        (equality/same-termo left right sigma)))))

(defn- relation-same?
  [left right sigma]
  (relation-same?* left right sigma))

(def ^:private relation-rigid-different?*
  (memoize
    (fn [left right sigma]
      (relation-succeeds?
        (support/rigid-different-termo left right sigma)))))

(defn- relation-rigid-different?
  [left right sigma]
  (relation-rigid-different?* left right sigma))

(def ^:private relation-neq-violated*
  (memoize
    (fn [neqs sigma]
      (relation-first
        (fn [q]
          (equality/neq-violatedo neqs sigma q))))))

(defn- relation-neq-violated
  [neqs sigma]
  (relation-neq-violated* neqs sigma))

(def ^:private relation-prune-neqs*
  (memoize
    (fn [neqs sigma]
      (or
        (relation-first
          (fn [q]
            (support/prune-contradictory-neqso neqs sigma q)))
        '()))))

(defn- relation-prune-neqs
  [neqs sigma]
  (relation-prune-neqs* neqs sigma))

(def ^:private relation-stable-neqs?*
  (memoize
    (fn [neqs sigma]
      (relation-succeeds?
        (support/stable-neqso neqs sigma)))))

(defn- relation-stable-neqs?
  [neqs sigma]
  (relation-stable-neqs?* neqs sigma))

(defn- relational-fresh-par
  []
  (ast/par-term (gensym "rel-eq-frag-par-")))

(declare close-branch-relational-result)

(defn- close-next-relational-result
  [agenda env sigma neqs candidates]
  (when (seq agenda)
    (close-branch-relational-result agenda env sigma neqs candidates)))

(defn- close-branch-relational-result
  "Deterministic finite driver backed by relational term operations.

   This mirrors the generic equality-fragment proof rules but delegates term
   equality, disequality, substitution, program lookup, and gamma candidates to
   existing miniKanren relations. It is intentionally separate from the
   ADR-0039 host engine so route guards can forbid that engine while these
   finite rows still complete."
  [agenda env sigma neqs candidates]
  (when (seq agenda)
    (let [[formula rest-agenda] (select-formula (vec agenda))]
      (case (ast/tag-of formula)
        and
        (when-let [proof (close-branch-relational-result
                           (conj rest-agenda (second formula) (nth formula 2))
                           env
                           sigma
                           neqs
                           candidates)]
          (list 'rel-eq-frag-conj proof))

        or
        (when-let [left-proof (close-branch-relational-result
                                (conj rest-agenda (second formula))
                                env
                                sigma
                                neqs
                                candidates)]
          (when-let [right-proof (close-branch-relational-result
                                   (conj rest-agenda (nth formula 2))
                                   env
                                   sigma
                                   neqs
                                   candidates)]
            (list 'rel-eq-frag-split left-proof right-proof)))

        false
        '(rel-eq-frag-false-close)

        true
        (when-let [proof (close-next-relational-result rest-agenda
                                                       env
                                                       sigma
                                                       neqs
                                                       candidates)]
          (list 'rel-eq-frag-skip-true proof))

        exists
        (let [tied (second formula)
              narrowed-env (subst/remove-binding env (:binding-nom tied))
              body (subst/subst-formula
                     (:body tied)
                     (cons [(:binding-nom tied) (relational-fresh-par)]
                           narrowed-env))]
          (when-let [proof (close-branch-relational-result
                             (conj rest-agenda body)
                             env
                             sigma
                             neqs
                             candidates)]
            (list 'rel-eq-frag-witness proof)))

        forall
        (some (fn [candidate]
                (let [tied (second formula)
                      narrowed-env (subst/remove-binding env (:binding-nom tied))
                      body (subst/subst-formula
                             (:body tied)
                             (cons [(:binding-nom tied) candidate]
                                   narrowed-env))]
                  (when-let [proof (close-branch-relational-result
                                     (conj rest-agenda body)
                                     env
                                     sigma
                                     neqs
                                     candidates)]
                    (list 'rel-eq-frag-univ-candidate candidate proof))))
              candidates)

        once-forall
        (some (fn [candidate]
                (let [tied (second formula)
                      narrowed-env (subst/remove-binding env (:binding-nom tied))
                      body (subst/subst-formula
                             (:body tied)
                             (cons [(:binding-nom tied) candidate]
                                   narrowed-env))]
                  (when-let [proof (close-branch-relational-result
                                     (conj rest-agenda body)
                                     env
                                     sigma
                                     neqs
                                     candidates)]
                    (list 'rel-eq-frag-once-univ-candidate candidate proof))))
              candidates)

        eq
        (let [lit (subst/subst-formula formula env)
              left (second lit)
              right (nth lit 2)]
          (if-let [contradiction-proof (relation-eq-contradiction left right sigma)]
            (list 'rel-eq-frag-eq-contradiction contradiction-proof)
            (when-let [[sigma-mid step-proof] (relation-unify left right sigma)]
              (if-let [bad-neq-proof (relation-neq-violated neqs sigma-mid)]
                (list 'rel-eq-frag-eq-step step-proof bad-neq-proof)
                (when (relation-stable-neqs? neqs sigma-mid)
                  (when-let [proof (close-next-relational-result
                                     rest-agenda
                                     env
                                     sigma-mid
                                     (relation-prune-neqs neqs sigma-mid)
                                     candidates)]
                    (list 'rel-eq-frag-eq-step step-proof proof)))))))

        neq
        (let [lit (subst/subst-formula formula env)
              left (second lit)
              right (nth lit 2)]
          (cond
            (relation-same? left right sigma)
            '(rel-eq-frag-refl-close)

            (relation-rigid-different? left right sigma)
            (when-let [proof (close-next-relational-result rest-agenda
                                                           env
                                                           sigma
                                                           neqs
                                                           candidates)]
              (list 'rel-eq-frag-neq-rigid proof))

            :else
            (when-let [proof (close-next-relational-result rest-agenda
                                                           env
                                                           sigma
                                                           (cons [left right] neqs)
                                                           candidates)]
              (list 'rel-eq-frag-neq-store proof))))))))

(defn- prove-formula-relational-finite
  [formula env candidates]
  (when (equality-fragment-formula? formula)
    (close-branch-relational-result [formula] env '() '() candidates)))

(defn- prove-program-relational-finite
  [prog formula gamma-source]
  (let [candidates (relational-candidates gamma-source)]
    (if-let [{:keys [tag env body]} (relational-call-target prog formula)]
      (when (prove-formula-relational-finite body env candidates)
        (list (if (= 'pos tag) 'pos-call 'neg-call)
              (list 'profiled
                    'relational-equality-fragment
                    'relational-proof)))
      (when (prove-formula-relational-finite formula '() candidates)
        (list 'profiled
              'relational-equality-fragment
              'relational-proof)))))

(defn- query-obligation
  "Return the tableau formula corresponding to the requested query outcome."
  [query expected]
  (case expected
    :succeeds (normalize/negate-formula query)
    :fails query
    (throw (ex-info "Unknown relational equality-fragment expectation"
                    {:expected expected}))))

(defn prove-program-relational
  "Return up to `n` opt-in relational equality-fragment proofs.

   `expected` follows the public query-status vocabulary: `:succeeds` proves
   the negated query, and `:fails` proves the query itself. Keeping this helper
   opt-in lets ADR-0057 collect correctness and timing evidence without
   replacing the production equality-fragment host engine by default."
  ([prog query n expected]
   (prove-program-relational prog query n nil expected))
  ([prog query n fuel expected]
   (let [formula (query-obligation query expected)
         gamma-source (gamma/relational-candidate-source prog fuel)]
     (take n
           (if-let [proof (prove-program-relational-finite prog
                                                           formula
                                                           gamma-source)]
             [proof]
             '())))))

;; ---------------------------------------------------------------------------
;; Deterministic finite proof engine
;; ---------------------------------------------------------------------------
;;
;; The relation above is useful as a specification-shaped proof layer, but the
;; ADR-0039 verifier rows need aggressive finite branch pruning. The host
;; engine below remains generic over equality-fragment formulas and emits proof
;; terms; it does not inspect benchmark ids or evaluate source-level tables.

(declare close-branch-result
         equality-fragment-formula?
         unify-term
         terms-same?)

(defn equality-fragment-formula?
  [formula]
  (case (ast/tag-of formula)
    true true
    false true
    eq true
    neq true
    and (and (equality-fragment-formula? (second formula))
             (equality-fragment-formula? (nth formula 2)))
    or (and (equality-fragment-formula? (second formula))
            (equality-fragment-formula? (nth formula 2)))
    forall (equality-fragment-formula? (:body (second formula)))
    once-forall (equality-fragment-formula? (:body (second formula)))
    exists (equality-fragment-formula? (:body (second formula)))
    false))

(defn- bindable-term?
  [term]
  (#{'var 'par} (ast/tag-of term)))

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

(defn- bind-term
  [term value sigma]
  (cons [(second term) value] sigma))

(defn- unify-term-list
  [left-args right-args sigma]
  (loop [sigma sigma
         left-args (seq left-args)
         right-args (seq right-args)
         proofs []]
    (cond
      (and (nil? left-args) (nil? right-args))
      {:status :ok
       :sigma sigma
       :proof (into '(eq-frag-host-args) proofs)}

      (or (nil? left-args) (nil? right-args))
      {:status :contradiction
       :proof '(eq-frag-host-arity-clash)}

      :else
      (let [step (unify-term (first left-args) (first right-args) sigma)]
        (if (= :ok (:status step))
          (recur (:sigma step)
                 (next left-args)
                 (next right-args)
                 (conj proofs (:proof step)))
          step)))))

(defn unify-term
  [left right sigma]
  (let [left-root (support/walk-term-pure left sigma)
        right-root (support/walk-term-pure right sigma)
        left-tag (ast/tag-of left-root)
        right-tag (ast/tag-of right-root)]
    (cond
      (support/same-walked-term? left-root right-root)
      {:status :ok :sigma sigma :proof '(eq-frag-host-eq-refl)}

      (= 'var left-tag)
      (if (occurs-binding? 'var (second left-root) right-root sigma)
        {:status :contradiction :proof '(eq-frag-host-occurs-close)}
        {:status :ok
         :sigma (bind-term left-root right-root sigma)
         :proof '(eq-frag-host-eq-bind)})

      (= 'var right-tag)
      (if (occurs-binding? 'var (second right-root) left-root sigma)
        {:status :contradiction :proof '(eq-frag-host-occurs-close)}
        {:status :ok
         :sigma (bind-term right-root left-root sigma)
         :proof '(eq-frag-host-eq-bind)})

      (= 'par left-tag)
      (if (occurs-binding? 'par (second left-root) right-root sigma)
        {:status :contradiction :proof '(eq-frag-host-occurs-close)}
        {:status :ok
         :sigma (bind-term left-root right-root sigma)
         :proof '(eq-frag-host-par-bind)})

      (= 'par right-tag)
      (if (occurs-binding? 'par (second right-root) left-root sigma)
        {:status :contradiction :proof '(eq-frag-host-occurs-close)}
        {:status :ok
         :sigma (bind-term right-root left-root sigma)
         :proof '(eq-frag-host-par-bind)})

      (and (= 'app left-tag) (= 'app right-tag))
      (if (and (= (second left-root) (second right-root))
               (= (count (nnext left-root))
                  (count (nnext right-root))))
        (let [subproof (unify-term-list (nnext left-root)
                                        (nnext right-root)
                                        sigma)]
          (if (= :ok (:status subproof))
            (assoc subproof :proof (list 'eq-frag-host-decompose
                                         (:proof subproof)))
            subproof))
        {:status :contradiction
         :proof '(eq-frag-host-free-close)})

      :else
      {:status :contradiction
       :proof '(eq-frag-host-free-close)})))

(defn terms-same?
  [left right sigma]
  (support/same-walked-term?
    (support/walk-term-pure left sigma)
    (support/walk-term-pure right sigma)))

(defn- rigid-different?
  [left right sigma]
  (let [left-root (support/walk-term-pure left sigma)
        right-root (support/walk-term-pure right sigma)
        left-tag (ast/tag-of left-root)
        right-tag (ast/tag-of right-root)]
    (and (= 'app left-tag)
         (= 'app right-tag)
         (or (not= (second left-root) (second right-root))
             (not= (count (nnext left-root))
                   (count (nnext right-root)))
             (some true?
                   (map #(rigid-different? %1 %2 sigma)
                        (nnext left-root)
                        (nnext right-root)))))))

(defn- violated-neq
  [neqs sigma]
  (some (fn [[left right]]
          (when (terms-same? left right sigma)
            [left right]))
        neqs))

(defn- prune-neqs
  [neqs sigma]
  (remove (fn [[left right]]
            (terms-same? left right sigma))
          neqs))

(defn- proof-bindings?
  [old-sigma new-sigma proof-vars]
  (let [new-count (- (count new-sigma) (count old-sigma))
        new-bindings (take new-count new-sigma)
        proof-var-set (set proof-vars)]
    (and (pos? new-count)
         (every? (fn [[binding-nom _]]
                   (contains? proof-var-set binding-nom))
                 new-bindings))))

(defn- select-formula
  [agenda]
  (let [[idx formula]
        (->> agenda
             (map-indexed vector)
             (sort-by (fn [[idx formula]]
                        [(formula-priority formula) idx]))
             first)]
    [formula (vec (concat (subvec agenda 0 idx)
                          (subvec agenda (inc idx))))]))

(defn- fresh-host-par
  []
  (ast/par-term (gensym "eq-frag-par-")))

(defn- fresh-host-var
  []
  (let [sym (gensym "eq-frag-var-")]
    [(ast/var-term sym) sym]))

(defn- l-ground-term?
  [term]
  (case (ast/tag-of term)
    par false
    var true
    app (every? l-ground-term? (nnext term))
    true))

(defn- l-ground-args?
  [args]
  (every? l-ground-term? args))

(defn- proof-result
  ([proof]
   {:proof proof
    :requirements []})
  ([proof requirements]
   {:proof proof
    :requirements (vec requirements)}))

(defn- wrap-proof
  [result f]
  (when result
    (assoc result :proof (f (:proof result)))))

(defn- proof-var-requirements
  [old-sigma new-sigma proof-vars]
  (->> proof-vars
       (keep (fn [binding-nom]
               (let [term (ast/var-term binding-nom)
                     old-term (support/walk-term-pure term old-sigma)
                     new-term (support/walk-term-pure term new-sigma)]
                 (when-not (support/same-walked-term? old-term new-term)
                   [binding-nom new-term]))))
       vec))

(defn- merge-requirements
  [sigma proof-vars & requirement-lists]
  (loop [merged-sigma sigma
         requirements (seq (mapcat identity requirement-lists))]
    (if-not requirements
      (proof-var-requirements sigma merged-sigma proof-vars)
      (let [[binding-nom value] (first requirements)
            step (unify-term (ast/var-term binding-nom) value merged-sigma)]
        (when (= :ok (:status step))
          (recur (:sigma step) (next requirements)))))))

(defn- close-next-result
  [agenda env proof-vars sigma neqs gamma-terms]
  (when (seq agenda)
    (close-branch-result agenda env proof-vars sigma neqs gamma-terms)))

(defn close-branch-result
  [agenda env proof-vars sigma neqs gamma-terms]
  (when (seq agenda)
    (let [[formula rest-agenda] (select-formula (vec agenda))]
      (case (ast/tag-of formula)
        and
        (wrap-proof
          (close-branch-result (conj rest-agenda
                                     (second formula)
                                     (nth formula 2))
                               env
                               proof-vars
                               sigma
                               neqs
                               gamma-terms)
          #(list 'eq-frag-host-conj %))

        or
        (let [left-result (close-branch-result (conj rest-agenda (second formula))
                                               env
                                               proof-vars
                                               sigma
                                               neqs
                                               gamma-terms)
              right-result (when left-result
                             (close-branch-result (conj rest-agenda (nth formula 2))
                                                  env
                                                  proof-vars
                                                  sigma
                                                  neqs
                                                  gamma-terms))
              requirements (when (and left-result right-result)
                             (merge-requirements sigma
                                                 proof-vars
                                                 (:requirements left-result)
                                                 (:requirements right-result)))]
          (when requirements
            (proof-result
              (list 'eq-frag-host-split
                    (:proof left-result)
                    (:proof right-result))
              requirements)))

        false
        (proof-result '(eq-frag-host-false-close))

        true
        (wrap-proof
          (close-next-result rest-agenda
                             env
                             proof-vars
                             sigma
                             neqs
                             gamma-terms)
          #(list 'eq-frag-host-skip-true %))

        exists
        (let [tied (second formula)
              narrowed-env (subst/remove-binding env (:binding-nom tied))
              body (subst/subst-formula
                     (:body tied)
                     (cons [(:binding-nom tied) (fresh-host-par)]
                           narrowed-env))]
          (wrap-proof
            (close-branch-result (conj rest-agenda body)
                                 env
                                 proof-vars
                                 sigma
                                 neqs
                                 gamma-terms)
            #(list 'eq-frag-host-witness %)))

        forall
        (let [tied (second formula)
              narrowed-env (subst/remove-binding env (:binding-nom tied))]
          (or
            (some (fn [candidate]
                    (let [body (subst/subst-formula
                                 (:body tied)
                                 (cons [(:binding-nom tied) candidate]
                                       narrowed-env))]
                      (wrap-proof
                        (close-branch-result (conj rest-agenda body)
                                             env
                                             proof-vars
                                             sigma
                                             neqs
                                             gamma-terms)
                        #(list 'eq-frag-host-univ-candidate candidate %))))
                  gamma-terms)
            (when-not (seq gamma-terms)
              (let [[term binding-nom] (fresh-host-var)
                    body (subst/subst-formula
                           (:body tied)
                           (cons [(:binding-nom tied) term]
                                 narrowed-env))]
                (wrap-proof
                  (close-branch-result (conj rest-agenda body)
                                       env
                                       (cons binding-nom proof-vars)
                                       sigma
                                       neqs
                                       gamma-terms)
                  #(list 'eq-frag-host-univ-var %))))))

        once-forall
        (let [tied (second formula)
              narrowed-env (subst/remove-binding env (:binding-nom tied))]
          (or
            (some (fn [candidate]
                    (let [body (subst/subst-formula
                                 (:body tied)
                                 (cons [(:binding-nom tied) candidate]
                                       narrowed-env))]
                      (wrap-proof
                        (close-branch-result (conj rest-agenda body)
                                             env
                                             proof-vars
                                             sigma
                                             neqs
                                             gamma-terms)
                        #(list 'eq-frag-host-once-univ-candidate candidate %))))
                  gamma-terms)
            (when-not (seq gamma-terms)
              (let [[term binding-nom] (fresh-host-var)
                    body (subst/subst-formula
                           (:body tied)
                           (cons [(:binding-nom tied) term]
                                 narrowed-env))]
                (wrap-proof
                  (close-branch-result (conj rest-agenda body)
                                       env
                                       (cons binding-nom proof-vars)
                                       sigma
                                       neqs
                                       gamma-terms)
                  #(list 'eq-frag-host-once-univ-var %))))))

        eq
        (let [lit (subst/subst-formula formula env)
              left (second lit)
              right (nth lit 2)
              step (unify-term left right sigma)]
          (case (:status step)
            :contradiction
            (proof-result (list 'eq-frag-host-eq-contradiction (:proof step)))

            :ok
            (if-let [bad-neq (violated-neq neqs (:sigma step))]
              (proof-result
                (list 'eq-frag-host-eq-step
                      (:proof step)
                      (list 'eq-frag-host-neq-violated bad-neq))
                (proof-var-requirements sigma (:sigma step) proof-vars))
              (when-let [result (close-next-result rest-agenda
                                                   env
                                                   proof-vars
                                                   (:sigma step)
                                                   (prune-neqs neqs (:sigma step))
                                                   gamma-terms)]
                (let [requirements (merge-requirements
                                      sigma
                                      proof-vars
                                      (proof-var-requirements sigma
                                                              (:sigma step)
                                                              proof-vars)
                                      (:requirements result))]
                  (when requirements
                    (proof-result
                      (list 'eq-frag-host-eq-step (:proof step) (:proof result))
                      requirements)))))))

        neq
        (let [lit (subst/subst-formula formula env)
              left (second lit)
              right (nth lit 2)]
          (cond
            (terms-same? left right sigma)
            (proof-result '(eq-frag-host-refl-close (refl-close)))

            (let [step (unify-term left right sigma)]
              (and (= :ok (:status step))
                   (proof-bindings? sigma (:sigma step) proof-vars)
                   step))
            (let [step (unify-term left right sigma)]
              (proof-result
                (list 'eq-frag-host-neq-close (:proof step))
                (proof-var-requirements sigma (:sigma step) proof-vars)))

            (rigid-different? left right sigma)
            (wrap-proof
              (close-next-result rest-agenda
                                 env
                                 proof-vars
                                 sigma
                                 neqs
                                 gamma-terms)
              #(list 'eq-frag-host-neq-rigid %))

            :else
            (wrap-proof
              (close-next-result rest-agenda
                                 env
                                 proof-vars
                                 sigma
                                 (cons [left right] neqs)
                                 gamma-terms)
              #(list 'eq-frag-host-neq-store %))))))))

(defn close-branch
  [agenda env proof-vars sigma neqs gamma-terms]
  (some-> (close-branch-result agenda env proof-vars sigma neqs gamma-terms)
          :proof))

(defn prove-host
  "Return one proof term when `formula` closes as a finite equality fragment."
  [formula env gamma-terms]
  (when (equality-fragment-formula? formula)
    (close-branch [formula] env '() '() '() gamma-terms)))

(defn- host-call-target
  [prog query-formula]
  (when (#{'pos 'neg} (ast/tag-of query-formula))
    (let [atom (second query-formula)
          relation (second atom)
          args (nnext atom)
          clause (get-in prog [:clauses relation])]
      (when (and clause
                 (l-ground-args? args))
        (let [env (map (fn [param arg]
                         [param arg])
                       (:params clause)
                       args)]
          {:tag (ast/tag-of query-formula)
           :env env
           :body (if (= 'pos (ast/tag-of query-formula))
                   (:body clause)
                   (:negated-body clause))})))))

(defn prove-program-host
  "Return up to `n` profiled equality-fragment proofs for one program query."
  ([prog query-formula n]
   (prove-program-host prog query-formula n nil))
  ([prog query-formula n fuel]
   (let [gamma-terms (gamma/closed-terms-for-fuel prog fuel)]
     (if-let [{:keys [tag env body]} (host-call-target prog query-formula)]
       (if-let [proof (prove-host body env gamma-terms)]
         (take n [(list (if (= 'pos tag) 'pos-call 'neg-call)
                        (list 'profiled 'equality-fragment proof))])
         '())
       (if-let [proof (prove-host query-formula '() gamma-terms)]
         (take n [(list 'profiled 'equality-fragment proof)])
         '())))))
