(ns proflog.answer-overlay
  "Extracted answer-oriented overlay above the greenfield proof kernel.

   This namespace preserves the answer-mode execution path that used to live in
   `proflog.kernel`: exported answer vars, existential-as-variable behavior,
   residual deferred calls, and recursive answer-call budgeting. ADR-0015 moves
   that flow out of the ordinary proof kernel so the pure proof surface remains
   directly accessible.

   For a reader of Fitting's Proflog paper, this file should be read as:

   - the same tableau engine as `proflog.kernel`,
   - but reparameterized for open-query execution,
   - with explicit output of learned bindings for selected answer variables,
   - and with a relational notion of \"stop descending here and leave the rest
     as a symbolic obligation\".

   The key idea is that answer search is not a different logic. It is the same
   branch-closing machinery, plus extra exported state describing what the
   branch learned before it chose to stop unfolding recursive calls."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [== appendo conda conde fail fresh lcons membero project run]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.equality :as equality]
            [proflog.gamma :as gamma]
            [proflog.kernel-support :as support]
            [proflog.program :as program]
            [proflog.subst :as subst]))

(def ^:dynamic *theory-profile-closeo*
  "Optional answer-layer theory rule.

   The ordinary proof kernel already exposes a language-selected theory hook.
   Answer export mirrors the kernel but carries extra residual-answer state, so
   it needs a parallel hook if a profile is to support reverse and partial
   synthesis instead of only closed proof search."
  nil)

(defn- theory-profile-closeo
  [& args]
  (if-let [closeo *theory-profile-closeo*]
    (apply closeo args)
    fail))

;; Reading guide
;; -------------
;;
;; This namespace deliberately mirrors the kernel's shape so that the semantic
;; differences stay visible:
;;
;; - `sigma` / `neqs` mean the same thing as in the ordinary kernel,
;; - `residuals` is new and records deferred procedure-call obligations,
;; - `call-depth` is a bounded unfolding budget for recursive descendants below
;;   the query boundary,
;; - `existentials-as-vars?` switches the delta rule from rigid parameters to
;;   exportable object-language variables, which is what makes partial and
;;   reverse-mode answers possible.
;;
;; In effect, the ordinary kernel asks only:
;;
;;   "Can this branch be closed?"
;;
;; while the answer overlay asks:
;;
;;   "How far can this branch be closed, what bindings were learned for the
;;    designated answer variables, and which obligations remain if we stop
;;    recursive descent at the current answer budget?"

(declare prove-stateo
         close-agendao
         saved-call-closeso
         close-one-guarded-alternativeo
         close-guarded-negated-call-sequenceo
         prove-program-answero
         prove-program-query-entryo)

(defn saved-call-closeso
  "Succeed when one saved atom becomes callable under the current equality
   substitution and its subsidiary tableau closes.

   This makes procedure-call completeness depend on the branch state, not on
   whether the enabling equality literal happened to be expanded before or
   after the atom was saved.

   In answer mode there is one extra choice beyond the ordinary kernel:

   - if `call-depth` still permits recursive descent, actually run the call;
   - otherwise, when symbolic existential export is enabled, keep the walked
     atom as a residual obligation instead of losing it."
  [lits proof-vars sigma sigma-out neqs neqs-out residuals residuals-out prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (let [can-descend? (or (nil? call-depth) (pos? call-depth))
        next-call-depth (support/next-call-depth call-depth)
        ;; Deferral is only meaningful in answer mode with symbolic existential
        ;; export and an actual program to call. In pure theorem-proving mode
        ;; there is nothing to export as a residual frontier.
        defer-calls? (and existentials-as-vars? prog)]
    (conde
      ;; Saved positive call: equality has now walked the atom into a callable
      ;; L-ground shape, so consume one unit of recursive descendant budget and
      ;; open the subsidiary tableau.
      [(fresh [atom walked-atom relation args call-env body negated-body next-fuel subproof]
         (membero (list 'pos atom) lits)
         (equality/walk-atomo atom sigma walked-atom)
         (== (lcons 'app (lcons relation args)) walked-atom)
         (support/l-ground-term*o args)
         (program/call-clauseo prog walked-atom call-env body negated-body)
         (== (list 'eq-triggered-call subproof) proof)
         (== residuals residuals-out)
         (if can-descend?
           (support/step-fuelo fuel next-fuel)
           fail)
         (prove-stateo body
                       '()
                       '()
                       call-env
                       proof-vars
                       sigma
                       sigma-out
                       neqs
                       neqs-out
                       residuals
                       residuals-out
                       prog
                       gamma-terms
                       next-fuel
                       next-call-depth
                       existentials-as-vars?
                       subproof))]
      ;; If we are in symbolic answer mode but have chosen not to descend, the
      ;; positive saved atom itself becomes part of the exported answer
      ;; frontier.
      [(if defer-calls?
         (fresh [atom]
           (membero (list 'pos atom) lits)
           (== sigma sigma-out)
           (== neqs neqs-out)
           (== (lcons (list 'pos atom) residuals) residuals-out)
           (== '(eq-triggered-residual-call) proof))
         fail)]
      ;; Saved negative atom can likewise be exported as a deferred obligation.
      [(if defer-calls?
         (fresh [atom]
           (membero (list 'neg atom) lits)
           (== sigma sigma-out)
           (== neqs neqs-out)
           (== (lcons (list 'neg atom) residuals) residuals-out)
           (== '(eq-triggered-residual-neg-call) proof))
         fail)]
      ;; Saved negative call: run the subsidiary tableau for the NNF negation of
      ;; the clause body if recursive budget still permits descent.
      [(fresh [atom walked-atom relation args call-env body negated-body next-fuel subproof]
         (membero (list 'neg atom) lits)
         (equality/walk-atomo atom sigma walked-atom)
         (== (lcons 'app (lcons relation args)) walked-atom)
         (support/l-ground-term*o args)
         (program/call-clauseo prog walked-atom call-env body negated-body)
         (== (list 'eq-triggered-neg-call subproof) proof)
         (== residuals residuals-out)
         (if can-descend?
           (support/step-fuelo fuel next-fuel)
           fail)
         (prove-stateo negated-body
                       '()
                       '()
                       call-env
                       proof-vars
                       sigma
                       sigma-out
                       neqs
                       neqs-out
                       residuals
                       residuals-out
                       prog
                       gamma-terms
                       next-fuel
                       next-call-depth
                       existentials-as-vars?
                       subproof))])))

(defn- guarded-alternative-fieldso
  "Expose the fields the answer overlay needs from guarded alternative IR."
  [guarded-alternative scope guards negated-calls negated-residuals negated-ordered-conjuncts]
  (fresh [formula
          negated-formula
          core
          conjuncts
          negated-conjuncts
          negated-guards
          calls
          residuals]
    (== {:formula formula
         :negated-formula negated-formula
         :scope scope
         :core core
         :conjuncts conjuncts
         :negated-conjuncts negated-conjuncts
         :guards guards
         :negated-guards negated-guards
         :calls calls
         :negated-calls negated-calls
         :residuals residuals
         :negated-residuals negated-residuals
         :negated-ordered-conjuncts negated-ordered-conjuncts}
        guarded-alternative)))

(defn- open-existential-guarded-scopeo
  "Instantiate leading existential guarded scope as answer-visible variables."
  [scope env proof-vars env-out proof-vars-out proof]
  (conde
    [(== '() scope)
     (== env env-out)
     (== proof-vars proof-vars-out)
     (== '(guarded-scope-done) proof)]
    [(nominal/fresh [free-var-nom]
       (fresh [binding-nom rest narrowed-env tail-proof]
         (== (lcons {:quantifier 'exists
                     :binding-nom binding-nom}
                    rest)
             scope)
         (subst/remove-bindo binding-nom env narrowed-env)
         (== (list 'guarded-scope-exists tail-proof) proof)
         (open-existential-guarded-scopeo
           rest
           (lcons [binding-nom (ast/var-term free-var-nom)] narrowed-env)
           (lcons free-var-nom proof-vars)
           env-out
           proof-vars-out
           tail-proof)))]))

(defn- close-formula-sequenceo
  "Close each residual formula in order while preserving answer state."
  [formulas env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out
   prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (conde
    [(== '() formulas)
     (== sigma sigma-out)
     (== neqs neqs-out)
     (== residuals residuals-out)
     (== '(guarded-residual-seq-done) proof)]
    [(fresh [formula subproof]
       (== (lcons formula '()) formulas)
       (== (list 'guarded-residual-seq-last subproof) proof)
       (prove-stateo formula
                     '()
                     '()
                     env
                     proof-vars
                     sigma
                     sigma-out
                     neqs
                     neqs-out
                     residuals
                     residuals-out
                     prog
                     gamma-terms
                     fuel
                     call-depth
                     existentials-as-vars?
                     subproof))]
    [(fresh [formula second-formula rest sigma-mid neqs-mid residuals-mid head-proof tail-proof]
       (== (lcons formula (lcons second-formula rest)) formulas)
       (== (list 'guarded-residual-seq-step head-proof tail-proof) proof)
       (prove-stateo formula
                     '()
                     '()
                     env
                     proof-vars
                     sigma
                     sigma-mid
                     neqs
                     neqs-mid
                     residuals
                     residuals-mid
                     prog
                     gamma-terms
                     fuel
                     call-depth
                     existentials-as-vars?
                     head-proof)
       (close-formula-sequenceo
         (lcons second-formula rest)
         env
         proof-vars
         sigma-mid
         sigma-out
         neqs-mid
         neqs-out
         residuals-mid
         residuals-out
         prog
         gamma-terms
         fuel
         call-depth
         existentials-as-vars?
         tail-proof))]))

(defn- saturate-eq-guardso
  "Unify equality guards while preserving residual answer state."
  [guards env proof-vars sigma sigma-out neqs proof]
  (conde
    [(== '() guards)
     (== sigma sigma-out)
     (== '(guard-saturation-done) proof)]
    [(fresh [guard rest lit left right sigma-mid new-bindings step-proof tail-proof]
       (== (lcons guard rest) guards)
       (subst/subst-formulao guard env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (appendo new-bindings sigma sigma-mid)
       (support/proof-bindingso new-bindings proof-vars)
       (support/stable-neqso neqs sigma-mid)
       (== (list 'guard-eq step-proof tail-proof) proof)
       (saturate-eq-guardso
         rest
         env
         proof-vars
         sigma-mid
         sigma-out
         neqs
         tail-proof))]))

(defn- close-guarded-negated-alternativeo
  "Close one guarded alternative in answer mode after guard saturation."
  [guarded-alternative env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out
   prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (fresh [scope
          guards
          negated-calls
          negated-residuals
          negated-ordered-conjuncts
          scoped-env
          scoped-proof-vars
          scope-proof
          call-sequence-proof
          residual-sequence-proof
          fallback-sequence-proof]
    (guarded-alternative-fieldso
      guarded-alternative
      scope
      guards
      negated-calls
      negated-residuals
      negated-ordered-conjuncts)
    (conde
      [(fresh [sigma-mid sigma-after-calls neqs-after-calls residuals-after-calls guard-proof]
         (== (list 'guarded-neg-alt-saturated
                   scope-proof
                   guard-proof
                   call-sequence-proof
                   residual-sequence-proof)
             proof)
         (open-existential-guarded-scopeo
           scope
           env
           proof-vars
           scoped-env
           scoped-proof-vars
           scope-proof)
         (saturate-eq-guardso
           guards
           scoped-env
           scoped-proof-vars
           sigma
           sigma-mid
           neqs
           guard-proof)
         (close-guarded-negated-call-sequenceo
           negated-calls
           scoped-env
           scoped-proof-vars
           sigma-mid
           sigma-after-calls
           neqs
           neqs-after-calls
           residuals
           residuals-after-calls
           prog
           gamma-terms
           fuel
           call-depth
           existentials-as-vars?
           call-sequence-proof)
         (close-formula-sequenceo
           negated-residuals
           scoped-env
           scoped-proof-vars
           sigma-after-calls
           sigma-out
           neqs-after-calls
           neqs-out
           residuals-after-calls
           residuals-out
           prog
           gamma-terms
           fuel
           call-depth
           existentials-as-vars?
           residual-sequence-proof))]
      [(== (list 'guarded-neg-alt scope-proof fallback-sequence-proof) proof)
       (open-existential-guarded-scopeo
         scope
         env
         proof-vars
         scoped-env
         scoped-proof-vars
         scope-proof)
       (close-formula-sequenceo
         negated-ordered-conjuncts
         scoped-env
         scoped-proof-vars
         sigma
         sigma-out
         neqs
         neqs-out
         residuals
         residuals-out
         prog
         gamma-terms
         fuel
         call-depth
         existentials-as-vars?
         fallback-sequence-proof)])))

(defn- close-one-guarded-alternativeo
  "Close any guarded alternative from a finite relational list in answer mode."
  [guarded-alternatives env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out
   prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (conde
    [(fresh [guarded-alternative rest subproof]
       (== (lcons guarded-alternative rest) guarded-alternatives)
       (== (list 'guarded-alt subproof) proof)
       (close-guarded-negated-alternativeo
         guarded-alternative
         env
         proof-vars
         sigma
         sigma-out
         neqs
         neqs-out
         residuals
         residuals-out
         prog
         gamma-terms
         fuel
         call-depth
         existentials-as-vars?
         subproof))]
    [(fresh [guarded-alternative rest]
       (== (lcons guarded-alternative rest) guarded-alternatives)
       (close-one-guarded-alternativeo
         rest
         env
         proof-vars
         sigma
         sigma-out
         neqs
         neqs-out
         residuals
         residuals-out
         prog
         gamma-terms
         fuel
         call-depth
         existentials-as-vars?
         proof))]))

(defn- close-guarded-negated-call-sequenceo
  "Close guarded negative procedure calls or export them as residuals."
  [formulas env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out
   prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (let [can-descend? (or (nil? call-depth) (pos? call-depth))
        next-call-depth (support/next-call-depth call-depth)
        defer-calls? (and existentials-as-vars? prog)]
    (conde
      [(== '() formulas)
       (== sigma sigma-out)
       (== neqs neqs-out)
       (== residuals residuals-out)
       (== '(guarded-call-seq-done) proof)]
      [(fresh [formula rest lit atom walked-atom relation args
               call-env body negated-body alternatives negated-alternatives
               guarded-alternatives next-fuel sigma-mid neqs-mid residuals-mid
               call-proof tail-proof]
         (== (lcons formula rest) formulas)
         (subst/subst-formulao formula env lit)
         (== (list 'neg atom) lit)
         (equality/walk-atomo atom sigma walked-atom)
         (== (lcons 'app (lcons relation args)) walked-atom)
         (support/l-ground-term*o args)
         (program/call-clause-with-guarded-alternativeso
           prog
           walked-atom
           call-env
           body
           negated-body
           alternatives
           negated-alternatives
           guarded-alternatives)
         (fresh [first-alternative second-alternative remaining-alternatives]
           (== (lcons first-alternative
                      (lcons second-alternative remaining-alternatives))
               alternatives))
         (== (list 'guarded-call-seq-step
                   (list 'neg-call-guarded-alt call-proof)
                   tail-proof)
             proof)
         (if can-descend?
           (support/step-fuelo fuel next-fuel)
           fail)
         (close-one-guarded-alternativeo
           guarded-alternatives
           call-env
           proof-vars
           sigma
           sigma-mid
           neqs
           neqs-mid
           residuals
           residuals-mid
           prog
           gamma-terms
           next-fuel
           next-call-depth
           existentials-as-vars?
           call-proof)
         (close-guarded-negated-call-sequenceo
           rest
           env
           proof-vars
           sigma-mid
           sigma-out
           neqs-mid
           neqs-out
           residuals-mid
           residuals-out
           prog
           gamma-terms
           fuel
           call-depth
           existentials-as-vars?
           tail-proof))]
      [(if (and defer-calls? (not can-descend?))
         (fresh [formula rest lit tail-proof]
           (== (lcons formula rest) formulas)
           (subst/subst-formulao formula env lit)
           (== (list 'guarded-call-seq-defer tail-proof) proof)
           (close-guarded-negated-call-sequenceo
             rest
             env
             proof-vars
             sigma
             sigma-out
             neqs
             neqs-out
             (lcons lit residuals)
             residuals-out
             prog
             gamma-terms
             fuel
             call-depth
             existentials-as-vars?
             tail-proof))
         fail)])))

(defn close-agendao
  "Close one explicit pending-formula agenda under the answer-export state.

   This is the answer-layer analogue of `proflog.kernel/close-agendao`: the
   branch work is explicit as an agenda, and `support/selecto` exposes the next
   pending obligation as a relational search choice rather than fixing a
   leftmost expansion order."
  [agenda lits env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (fresh [fml unexpanded]
    (support/selecto fml agenda unexpanded)
    (let [can-descend? (or (nil? call-depth) (pos? call-depth))
          next-call-depth (support/next-call-depth call-depth)
          ;; Only open-query / answer-mode execution wants residual deferred
          ;; calls. Ordinary proof search should either descend or fail.
          defer-calls? (and existentials-as-vars? prog)]
      (conde
      ;; Profile-specific theory closure comes first, just as it does in the
      ;; ordinary kernel. A successful hook closes the current branch and may
      ;; export bindings through `sigma-out`.
      [(theory-profile-closeo fml
                              unexpanded
                              lits
                              env
                              proof-vars
                              sigma
                              sigma-out
                              neqs
                              neqs-out
                              residuals
                              residuals-out
                              prog
                              gamma-terms
                              fuel
                              call-depth
                              existentials-as-vars?
                              proof)]

      ;; α-rule: both conjuncts must close on the same branch, so the sibling
      ;; conjunct is pushed onto the branch work stack. Equality-triggered saved
      ;; literal closure handles the important order-insensitive case where a
      ;; later equality unlocks an earlier saved atom.
      [(fresh [left right next-fuel prf]
         (== (list 'and left right) fml)
         (== (list 'conj prf) proof)
         (support/step-fuelo fuel next-fuel)
         (prove-stateo left
                       (lcons right unexpanded)
                       lits
                       env
                       proof-vars
                       sigma
                       sigma-out
                       neqs
                       neqs-out
                       residuals
                       residuals-out
                       prog
                       gamma-terms
                       next-fuel
                       call-depth
                       existentials-as-vars?
                       prf))]

    ;; β-rule: both branches must close under one compatible proof state. The
    ;; resulting substitution threads from the first sibling into the second.
    [(fresh [left right next-fuel sigma-mid neqs-mid residuals-mid left-proof right-proof]
            (== (list 'or left right) fml)
            (== (list 'split left-proof right-proof) proof)
            (support/step-fuelo fuel next-fuel)
            (prove-stateo left
                          unexpanded
                          lits
                          env
                          proof-vars
                          sigma
                          sigma-mid
                          neqs
                          neqs-mid
                          residuals
                          residuals-mid
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          left-proof)
            (prove-stateo right
                          unexpanded
                          lits
                          env
                          proof-vars
                          sigma-mid
                          sigma-out
                          neqs-mid
                          neqs-out
                          residuals-mid
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          right-proof))]

    ;; γ-rule: ordinary proof mode first instantiates a universal with an
    ;; explicit free variable term, preserving historical answer-search order.
    ;; Bounded closed terms are available below as a fallback in non-symbolic
    ;; proof mode. Symbolic answer mode keeps the free-var behavior only, so
    ;; answer export is not prematurely materialized.
    [(nominal/fresh [binding-nom]
                    (nominal/fresh [free-var-nom]
                                   (fresh [body body-subst narrowed-env next-fuel prf]
                                          (== (list 'forall (nominal/tie binding-nom body)) fml)
                                          (== '() unexpanded)
                                          (== (list 'univ prf) proof)
                                          (subst/remove-bindo binding-nom env narrowed-env)
                                          (subst/subst-formulao body narrowed-env body-subst)
                                          (support/step-fuelo fuel next-fuel)
                                          (prove-stateo body-subst
                                                        '()
                                                        lits
                                                        (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                                        (lcons free-var-nom proof-vars)
                                                        sigma
                                                        sigma-out
                                                        neqs
                                                        neqs-out
                                                        residuals
                                                        residuals-out
                                                        prog
                                                        gamma-terms
                                                        next-fuel
                                                        call-depth
                                                        existentials-as-vars?
                                                        prf))))]
    [(if existentials-as-vars?
       fail
       (nominal/fresh [binding-nom]
                      (fresh [body body-subst narrowed-env witness-term next-fuel prf]
                             (== (list 'forall (nominal/tie binding-nom body)) fml)
                             (== '() unexpanded)
                             (== (list 'univ prf) proof)
                             (subst/remove-bindo binding-nom env narrowed-env)
                             (subst/subst-formulao body narrowed-env body-subst)
                             (support/call-free-formulao body-subst)
                             (gamma/closed-term-candidateo gamma-terms witness-term)
                             (support/step-fuelo fuel next-fuel)
                             (prove-stateo body-subst
                                           '()
                                           lits
                                           (lcons [binding-nom witness-term] env)
                                           proof-vars
                                           sigma
                                           sigma-out
                                           neqs
                                           neqs-out
                                           residuals
                                           residuals-out
                                           prog
                                           gamma-terms
                                           next-fuel
                                           call-depth
                                           existentials-as-vars?
                                           prf))))]
    ;; General gamma case with explicit re-enqueueing of the universal.
    [(nominal/fresh [binding-nom]
                    (nominal/fresh [free-var-nom]
                                   (fresh [body body-subst narrowed-env pending next-fuel prf]
                                          (== (list 'forall (nominal/tie binding-nom body)) fml)
                                          (== (list 'univ prf) proof)
                                          (appendo unexpanded (list fml) pending)
                                          (subst/remove-bindo binding-nom env narrowed-env)
                                          (subst/subst-formulao body narrowed-env body-subst)
                                          (support/step-fuelo fuel next-fuel)
                                          (prove-stateo body-subst
                                                        pending
                                                        lits
                                                        (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                                        (lcons free-var-nom proof-vars)
                                                        sigma
                                                        sigma-out
                                                        neqs
                                                        neqs-out
                                                        residuals
                                                        residuals-out
                                                        prog
                                                        gamma-terms
                                                        next-fuel
                                                        call-depth
                                                        existentials-as-vars?
                                                        prf))))]
    ;; Single-use universal: instantiate once on the current branch without
    ;; re-enqueueing. This is the NNF operational form produced by negating an
    ;; existential clause body for procedure-call execution.
    [(nominal/fresh [binding-nom]
                    (nominal/fresh [free-var-nom]
                                   (fresh [body body-subst narrowed-env next-fuel prf]
                                          (== (list 'once-forall (nominal/tie binding-nom body)) fml)
                                          (== (list 'once-univ prf) proof)
                                          (subst/remove-bindo binding-nom env narrowed-env)
                                          (subst/subst-formulao body narrowed-env body-subst)
                                          (support/step-fuelo fuel next-fuel)
                                          (prove-stateo body-subst
                                                        unexpanded
                                                        lits
                                                        (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                                        (lcons free-var-nom proof-vars)
                                                        sigma
                                                        sigma-out
                                                        neqs
                                                        neqs-out
                                                        residuals
                                                        residuals-out
                                                        prog
                                                        gamma-terms
                                                        next-fuel
                                                        call-depth
                                                        existentials-as-vars?
                                                        prf))))]
    [(if existentials-as-vars?
       fail
       (nominal/fresh [binding-nom]
                      (fresh [body body-subst narrowed-env witness-term next-fuel prf]
                             (== (list 'once-forall (nominal/tie binding-nom body)) fml)
                             (== (list 'once-univ prf) proof)
                             (subst/remove-bindo binding-nom env narrowed-env)
                             (subst/subst-formulao body narrowed-env body-subst)
                             (support/call-free-formulao body-subst)
                             (gamma/closed-term-candidateo gamma-terms witness-term)
                             (support/step-fuelo fuel next-fuel)
                             (prove-stateo body-subst
                                           unexpanded
                                           lits
                                           (lcons [binding-nom witness-term] env)
                                           proof-vars
                                           sigma
                                           sigma-out
                                           neqs
                                           neqs-out
                                           residuals
                                           residuals-out
                                           prog
                                           gamma-terms
                                           next-fuel
                                           call-depth
                                           existentials-as-vars?
                                           prf))))]

    ;; δ-rule: instantiate an existential exactly once with a rigid internal
    ;; parameter in ordinary proof search. Answer export instead introduces a
    ;; fresh object-language variable so existential structure can remain
    ;; symbolic and continue constraining open queries relationally.
    ;;
    ;; This one switch is the main reason the answer overlay cannot be reduced
    ;; to "just call the ordinary proof wrapper backwards". Open-query answer
    ;; search needs existential witnesses that remain visible as symbolic output
    ;; variables, not rigid internal parameters.
    [(if existentials-as-vars?
       (nominal/fresh [binding-nom]
                      (nominal/fresh [free-var-nom]
                                     (fresh [body body-subst narrowed-env next-fuel prf]
                                            (== (list 'exists (nominal/tie binding-nom body)) fml)
                                            (== (list 'witness prf) proof)
                                            (subst/remove-bindo binding-nom env narrowed-env)
                                            (subst/subst-formulao body narrowed-env body-subst)
                                            (support/step-fuelo fuel next-fuel)
                                            (prove-stateo body-subst
                                                          unexpanded
                                                          lits
                                                          (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                                          (lcons free-var-nom proof-vars)
                                                          sigma
                                                          sigma-out
                                                          neqs
                                                          neqs-out
                                                          residuals
                                                          residuals-out
                                                          prog
                                                          gamma-terms
                                                          next-fuel
                                                          call-depth
                                                          existentials-as-vars?
                                                          prf))))
       (nominal/fresh [binding-nom]
                      (nominal/fresh [parameter-nom]
                                     (fresh [body body-subst narrowed-env next-fuel prf]
                                            (== (list 'exists (nominal/tie binding-nom body)) fml)
                                            (== (list 'witness prf) proof)
                                            (subst/remove-bindo binding-nom env narrowed-env)
                                            (subst/subst-formulao body narrowed-env body-subst)
                                            (support/step-fuelo fuel next-fuel)
                                            (prove-stateo body-subst
                                                          unexpanded
                                                          lits
                                                          (lcons [binding-nom (ast/par-term parameter-nom)] env)
                                                          proof-vars
                                                          sigma
                                                          sigma-out
                                                          neqs
                                                          neqs-out
                                                          residuals
                                                          residuals-out
                                                          prog
                                                          gamma-terms
                                                          next-fuel
                                                          call-depth
                                                          existentials-as-vars?
                                                          prf)))))]

    ;; Positive equality closes immediately when the two terms cannot denote
    ;; the same free-constructor object.
    [(fresh [lit left right contradiction-proof]
            (subst/subst-formulao fml env lit)
            (== (list 'eq left right) lit)
            (equality/eq-contradictiono left right sigma contradiction-proof)
            (== sigma sigma-out)
            (== neqs neqs-out)
            (== residuals residuals-out)
            (== contradiction-proof proof))]

    ;; Otherwise positive equality extends the branch substitution. That new
    ;; information can close the branch either by violating a saved disequality
    ;; or by making two saved complementary atoms unify.
    [(fresh [lit left right sigma-mid step-proof branch-proof]
            (subst/subst-formulao fml env lit)
            (== (list 'eq left right) lit)
            (equality/unify-termo left right sigma sigma-mid step-proof)
            (equality/neq-violatedo neqs sigma-mid branch-proof)
            (== sigma-mid sigma-out)
            (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
            (== residuals residuals-out)
            (== (list 'eq-step step-proof branch-proof) proof))]
    ;; Equality may also wake a saved procedure call, which is particularly
    ;; important in open queries: a previously symbolic atom may become
    ;; callable only after enough branch equalities have accumulated.
    [(fresh [lit left right sigma-mid step-proof branch-proof]
            (subst/subst-formulao fml env lit)
            (== (list 'eq left right) lit)
            (equality/unify-termo left right sigma sigma-mid step-proof)
            (equality/contradictory-atomso lits sigma-mid sigma-out branch-proof)
            (support/prune-contradictory-neqso neqs sigma-out neqs-out)
            (== residuals residuals-out)
            (== (list 'eq-step step-proof branch-proof) proof))]
    [(fresh [lit left right sigma-mid step-proof branch-proof]
            (subst/subst-formulao fml env lit)
            (== (list 'eq left right) lit)
            (equality/unify-termo left right sigma sigma-mid step-proof)
            (saved-call-closeso lits proof-vars sigma-mid sigma-out neqs neqs-out residuals residuals-out prog gamma-terms fuel call-depth existentials-as-vars? branch-proof)
            (== (list 'eq-step step-proof branch-proof) proof))]
    [(fresh [lit left right sigma-mid step-proof next rest next-fuel prf]
            (subst/subst-formulao fml env lit)
            (== (list 'eq left right) lit)
            (equality/unify-termo left right sigma sigma-mid step-proof)
            (== (lcons next rest) unexpanded)
            (== (list 'eq-step step-proof prf) proof)
            (support/stable-neqso neqs sigma-mid)
            (support/step-fuelo fuel next-fuel)
            (prove-stateo next
                          rest
                          lits
                          env
                          proof-vars
                          sigma-mid
                          sigma-out
                          neqs
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          prf))]

    ;; Negative equality closes only once its two walked sides are forced equal.
    ;; Otherwise it is stored symbolically and rechecked after later bindings.
    [(fresh [lit left right]
            (subst/subst-formulao fml env lit)
            (== (list 'neq left right) lit)
            (equality/same-termo left right sigma)
            (== sigma sigma-out)
            (== neqs neqs-out)
            (== residuals residuals-out)
            (== '(refl-close) proof))]
    [(fresh [lit left right sigma-mid new-bindings binding rest step-proof]
            (subst/subst-formulao fml env lit)
            (== (list 'neq left right) lit)
            (equality/unify-termo left right sigma sigma-mid step-proof)
            (appendo new-bindings sigma sigma-mid)
            ;; A disequality closes when equality can force its two sides
            ;; equal by instantiating one or more branch-local proof variables.
            ;; Recursive constructor shapes such as pair/list disequalities may
            ;; require multiple such bindings on the same step.
            (== (lcons binding rest) new-bindings)
            (support/proof-bindingso new-bindings proof-vars)
            (== sigma-mid sigma-out)
            (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
            (== residuals residuals-out)
            (== (list 'neq-close step-proof) proof))]
    ;; If the disequality remains open, we preserve it as part of the symbolic
    ;; answer state rather than discarding it. Later exported answers will turn
    ;; this store into explicit residual disequality formulas.
    [(fresh [lit left right next rest next-fuel prf]
            (subst/subst-formulao fml env lit)
            (== (list 'neq left right) lit)
            (== (lcons next rest) unexpanded)
            (== (list 'neq-store prf) proof)
            (support/step-fuelo fuel next-fuel)
            (prove-stateo next
                          rest
                          lits
                          env
                          proof-vars
                          sigma
                          sigma-out
                          (lcons [left right] neqs)
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          prf))]

    ;; Positive and negative atoms close against a saved complementary atom if
    ;; their walked arguments can be unified. If no direct complement exists,
    ;; the Procedure Call Rule may close the branch through a fresh subsidiary
    ;; tableau over the compiled clause body.
    [(fresh [lit atom]
            (subst/subst-formulao fml env lit)
            (== (list 'pos atom) lit)
            (support/complementary-lito lit lits sigma sigma-out proof)
            (support/prune-contradictory-neqso neqs sigma-out neqs-out)
            (== residuals residuals-out))]
    ;; In answer mode, prefer consuming remaining call-depth budget before
    ;; materializing a residual call frontier. The defer branches stay available
    ;; and still win once `call-depth` reaches zero.
    ;;
    ;; This realizes a bounded approximation of recursive answer search:
    ;;
    ;; - if descent is still allowed, keep proving the subsidiary tableau;
    ;; - if descent is no longer allowed, keep the atom itself as a symbolic
    ;;   residual obligation for the caller.
    [(fresh [lit atom walked-atom relation args call-env body negated-body next-fuel subproof]
            (subst/subst-formulao fml env lit)
            (== (list 'pos atom) lit)
            (equality/walk-atomo atom sigma walked-atom)
            (== (lcons 'app (lcons relation args)) walked-atom)
            (support/l-ground-term*o args)
            (program/call-clauseo prog walked-atom call-env body negated-body)
            (== (list 'pos-call subproof) proof)
            (== residuals residuals-out)
            (if can-descend?
              (support/step-fuelo fuel next-fuel)
              fail)
            (prove-stateo body
                          '()
                          '()
                          call-env
                          proof-vars
                          sigma
                          sigma-out
                          neqs
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          next-call-depth
                          existentials-as-vars?
                          subproof))]
    ;; Deferral with more branch work still pending: save the current atom into
    ;; the residual frontier and continue with the rest of the current branch.
    [(if defer-calls?
       (fresh [lit atom next rest next-fuel prf]
              (subst/subst-formulao fml env lit)
              (== (list 'pos atom) lit)
              (== (lcons next rest) unexpanded)
              (== (list 'defer-call prf) proof)
              (support/step-fuelo fuel next-fuel)
              (prove-stateo next
                            rest
                            lits
                            env
                            proof-vars
                            sigma
                            sigma-out
                            neqs
                            neqs-out
                            (lcons lit residuals)
                            residuals-out
                            prog
                            gamma-terms
                            next-fuel
                            call-depth
                            existentials-as-vars?
                            prf))
       fail)]
    ;; Deferral when this atom is the last remaining branch task: export it
    ;; directly as the whole residual frontier.
    [(if defer-calls?
       (fresh [lit atom]
              (subst/subst-formulao fml env lit)
              (== (list 'pos atom) lit)
              (== sigma sigma-out)
              (== neqs neqs-out)
              (== (lcons lit residuals) residuals-out)
              (== '(defer-call) proof))
       fail)]
    [(fresh [lit atom]
            (subst/subst-formulao fml env lit)
            (== (list 'neg atom) lit)
            (support/complementary-lito lit lits sigma sigma-out proof)
            (support/prune-contradictory-neqso neqs sigma-out neqs-out)
            (== residuals residuals-out))]
    [(fresh [lit atom walked-atom relation args call-env body negated-body
             alternatives negated-alternatives guarded-alternatives next-fuel subproof]
            (subst/subst-formulao fml env lit)
            (== (list 'neg atom) lit)
            (equality/walk-atomo atom sigma walked-atom)
            (== (lcons 'app (lcons relation args)) walked-atom)
            (support/l-ground-term*o args)
            (program/call-clause-with-guarded-alternativeso
              prog
              walked-atom
              call-env
              body
              negated-body
              alternatives
              negated-alternatives
              guarded-alternatives)
            (fresh [first-alternative second-alternative remaining-alternatives]
              (== (lcons first-alternative
                         (lcons second-alternative remaining-alternatives))
                  alternatives))
            (== (list 'neg-call-guarded-alt subproof) proof)
            (if can-descend?
              (support/step-fuelo fuel next-fuel)
              fail)
            (close-one-guarded-alternativeo
              guarded-alternatives
              call-env
              proof-vars
              sigma
              sigma-out
              neqs
              neqs-out
              residuals
              residuals-out
              prog
              gamma-terms
              next-fuel
              next-call-depth
              existentials-as-vars?
              subproof))]
    ;; Negative-call version of the same bounded descent / symbolic deferral
    ;; choice.
    [(fresh [lit atom walked-atom relation args call-env body negated-body next-fuel subproof]
            (subst/subst-formulao fml env lit)
            (== (list 'neg atom) lit)
            (equality/walk-atomo atom sigma walked-atom)
            (== (lcons 'app (lcons relation args)) walked-atom)
            (support/l-ground-term*o args)
            (program/call-clauseo prog walked-atom call-env body negated-body)
            (== (list 'neg-call subproof) proof)
            (== residuals residuals-out)
            (if can-descend?
              (support/step-fuelo fuel next-fuel)
              fail)
            (prove-stateo negated-body
                          '()
                          '()
                          call-env
                          proof-vars
                          sigma
                          sigma-out
                          neqs
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          next-call-depth
                          existentials-as-vars?
                          subproof))]
    ;; Defer the negative call but keep working through other pending formulas.
    [(if defer-calls?
       (fresh [lit atom next rest next-fuel prf]
              (subst/subst-formulao fml env lit)
              (== (list 'neg atom) lit)
              (== (lcons next rest) unexpanded)
              (== (list 'defer-call prf) proof)
              (support/step-fuelo fuel next-fuel)
              (prove-stateo next
                            rest
                            lits
                            env
                            proof-vars
                            sigma
                            sigma-out
                            neqs
                            neqs-out
                            (lcons lit residuals)
                            residuals-out
                            prog
                            gamma-terms
                            next-fuel
                            call-depth
                            existentials-as-vars?
                            prf))
       fail)]
    ;; Defer the negative call as the final residual frontier.
    [(if defer-calls?
       (fresh [lit atom]
              (subst/subst-formulao fml env lit)
              (== (list 'neg atom) lit)
              (== sigma sigma-out)
              (== neqs neqs-out)
              (== (lcons lit residuals) residuals-out)
              (== '(defer-call) proof))
       fail)]
    ;; If no immediate closure or call step applies, the atom is saved on the
    ;; branch exactly as in the ordinary kernel so that later equality can
    ;; reopen it.
    [(fresh [lit atom next rest next-fuel prf]
            (subst/subst-formulao fml env lit)
            (== (list 'pos atom) lit)
            (== (lcons next rest) unexpanded)
            (== (list 'savefml prf) proof)
            (support/step-fuelo fuel next-fuel)
            (prove-stateo next
                          rest
                          (lcons lit lits)
                          env
                          proof-vars
                          sigma
                          sigma-out
                          neqs
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          prf))]
    ;; Negative saved-literal case.
    [(fresh [lit atom next rest next-fuel prf]
            (subst/subst-formulao fml env lit)
            (== (list 'neg atom) lit)
            (== (lcons next rest) unexpanded)
            (== (list 'savefml prf) proof)
            (support/step-fuelo fuel next-fuel)
            (prove-stateo next
                          rest
                          (lcons lit lits)
                          env
                          proof-vars
                          sigma
                          sigma-out
                          neqs
                          neqs-out
                          residuals
                          residuals-out
                          prog
                          gamma-terms
                          next-fuel
                          call-depth
                          existentials-as-vars?
                          prf))]))))

(defn prove-stateo
  "Backward-compatible current-formula wrapper over the fair answer agenda.

   Existing callers still pass one focused formula plus the remaining pending
   branch work, but internally the answer layer now treats them as one agenda
   and schedules the next obligation relationally."
  [fml unexpanded lits env proof-vars sigma sigma-out neqs neqs-out residuals residuals-out prog gamma-terms fuel call-depth existentials-as-vars? proof]
  (close-agendao
    (lcons fml unexpanded)
    lits
    env
    proof-vars
    sigma
    sigma-out
    neqs
    neqs-out
    residuals
    residuals-out
    prog
    gamma-terms
    fuel
    call-depth
    existentials-as-vars?
    proof))

(declare close-structural-atomo
         close-structural-neg-call-sequenceo)

(defn- prefilter-structural-guardso
  "Reject guarded alternatives whose guards cannot hold before descending into
   their recursive calls.

   Equality guards are saturated relationally and may extend `sigma`.
   Disequality guards are accepted only when constructor structure makes them
   rigidly true under the current substitution. Symbolic disequalities remain
   outside this narrow prefilter until the raw continuation grows an explicit
   guard-level disequality store."
  [guards env proof-vars sigma sigma-out neqs proof]
  (conde
    [(== '() guards)
     (== sigma sigma-out)
     (== '(structural-residual-guard-prefilter-done) proof)]
    [(fresh [guard rest lit left right sigma-mid new-bindings step-proof tail-proof]
       (== (lcons guard rest) guards)
       (subst/subst-formulao guard env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (appendo new-bindings sigma sigma-mid)
       (support/proof-bindingso new-bindings proof-vars)
       (support/stable-neqso neqs sigma-mid)
       (prefilter-structural-guardso
         rest
         env
         proof-vars
         sigma-mid
         sigma-out
         neqs
         tail-proof)
       (== (list 'structural-residual-guard-prefilter-eq
                 step-proof
                 tail-proof)
           proof))]
    [(fresh [guard rest lit left right tail-proof]
       (== (lcons guard rest) guards)
       (subst/subst-formulao guard env lit)
       (== (list 'neq left right) lit)
       (support/rigid-different-termo left right sigma)
       (prefilter-structural-guardso
         rest
         env
         proof-vars
         sigma
         sigma-out
         neqs
         tail-proof)
       (== (list 'structural-residual-guard-prefilter-neq-rigid
                 '(rigid-different)
                 tail-proof)
           proof))]))

(defn- close-structural-guarded-alternativeo
  "Close one guarded alternative in residual-continuation mode.

   This is intentionally narrower than full answer-mode proof search: it
   performs guarded scope opening, equality-guard saturation, recursive
   negative-call closure, and residual formula closure without offering the
   answer-mode deferral branches that created the exported frontier in the
   first place."
  [guarded-alternative env proof-vars sigma sigma-out neqs neqs-out
   prog gamma-terms fuel proof]
  (fresh [scope
          guards
          negated-calls
          negated-residuals
          negated-ordered-conjuncts
          scoped-env
          scoped-proof-vars
          sigma-after-guards
          sigma-after-calls
          neqs-after-calls
          scope-proof
          prefilter-proof
          call-proof
          residual-proof]
    (guarded-alternative-fieldso
      guarded-alternative
      scope
      guards
      negated-calls
      negated-residuals
      negated-ordered-conjuncts)
    (== (list 'structural-residual-guarded-alt
              scope-proof
              (list 'structural-residual-guard-prefilter
                    prefilter-proof)
              call-proof
              residual-proof)
        proof)
    (open-existential-guarded-scopeo
      scope
      env
      proof-vars
      scoped-env
      scoped-proof-vars
      scope-proof)
    (prefilter-structural-guardso
      guards
      scoped-env
      scoped-proof-vars
      sigma
      sigma-after-guards
      neqs
      prefilter-proof)
    (close-structural-neg-call-sequenceo
      negated-calls
      scoped-env
      scoped-proof-vars
      sigma-after-guards
      sigma-after-calls
      neqs
      neqs-after-calls
      prog
      gamma-terms
      fuel
      call-proof)
    (close-formula-sequenceo
      negated-residuals
      scoped-env
      scoped-proof-vars
      sigma-after-calls
      sigma-out
      neqs-after-calls
      neqs-out
      '()
      '()
      prog
      gamma-terms
      fuel
      nil
      false
      residual-proof)))

(defn- close-structural-one-guarded-alternativeo
  [guarded-alternatives env proof-vars sigma sigma-out neqs neqs-out
   prog gamma-terms fuel proof]
  (conde
    [(fresh [guarded-alternative rest subproof]
       (== (lcons guarded-alternative rest) guarded-alternatives)
       (== (list 'structural-residual-alt subproof) proof)
       (close-structural-guarded-alternativeo
         guarded-alternative
         env
         proof-vars
         sigma
         sigma-out
         neqs
         neqs-out
         prog
         gamma-terms
         fuel
         subproof))]
    [(fresh [guarded-alternative rest]
       (== (lcons guarded-alternative rest) guarded-alternatives)
       (close-structural-one-guarded-alternativeo
         rest
         env
         proof-vars
         sigma
         sigma-out
         neqs
         neqs-out
         prog
         gamma-terms
         fuel
         proof))]))

(defn- close-structural-atomo
  [atom proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (fresh [walked-atom relation args call-env body negated-body
          alternatives negated-alternatives guarded-alternatives
          next-fuel subproof]
    (equality/walk-atomo atom sigma walked-atom)
    (== (lcons 'app (lcons relation args)) walked-atom)
    (support/l-ground-term*o args)
    (program/call-clause-with-guarded-alternativeso
      prog
      walked-atom
      call-env
      body
      negated-body
      alternatives
      negated-alternatives
      guarded-alternatives)
    (support/step-fuelo fuel next-fuel)
    (== (list 'structural-residual-call walked-atom subproof) proof)
    (close-structural-one-guarded-alternativeo
      guarded-alternatives
      call-env
      proof-vars
      sigma
      sigma-out
      neqs
      neqs-out
      prog
      gamma-terms
      next-fuel
      subproof)))

(defn- close-structural-neg-call-sequenceo
  [formulas env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (conde
    [(== '() formulas)
     (== sigma sigma-out)
     (== neqs neqs-out)
     (== '(structural-residual-call-seq-done) proof)]
    [(fresh [formula rest lit atom sigma-mid neqs-mid head-proof tail-proof]
       (== (lcons formula rest) formulas)
       (subst/subst-formulao formula env lit)
       (== (list 'neg atom) lit)
       (== (list 'structural-residual-call-seq-step
                 head-proof
                 tail-proof)
           proof)
       (close-structural-atomo
         atom
         proof-vars
         sigma
         sigma-mid
         neqs
         neqs-mid
         prog
         gamma-terms
         fuel
         head-proof)
       (close-structural-neg-call-sequenceo
         rest
         env
         proof-vars
         sigma-mid
         sigma-out
         neqs-mid
         neqs-out
         prog
         gamma-terms
         fuel
         tail-proof))]))

(defn- close-structural-residual-sequenceo
  "Close an exported negative-call residual frontier through the continuation
   agenda.

   This is the ADR-0035 replacement for the host-side constructor-recursive
   settlement step. The relation consumes only negative calls whose atoms are
   callable in the current substitution and then reuses guarded program IR,
   relational equality, and answer-overlay proof vocabulary."
  [frontier proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (close-structural-neg-call-sequenceo
    frontier
    '()
    proof-vars
    sigma
    sigma-out
    neqs
    neqs-out
    prog
    gamma-terms
    fuel
    proof))

(defn continue-structural-residualso
  "Relationally close a structural residual frontier before answer export.

   `frontier` is the raw residual list produced by answer-mode proof search.
   On success the frontier has been discharged, while `sigma` and `neqs` are
   threaded to their continued outputs. `continuation-fuel` is intentionally
   separate from answer-mode call depth and from the proof branch that produced
   the frontier. The proof term is tagged with answer-overlay vocabulary so
   public answer records do not depend on the diagnostic constructor-recursive
   sidecar."
  [frontier proof-vars sigma sigma-out neqs neqs-out prog continuation-fuel proof]
  (fresh [subproof]
    (== (list 'structural-residual-continuation
              (list 'structural-residual-continuation-agenda
                    (list 'structural-residual-continuation-fuel
                          continuation-fuel)
                    subproof))
        proof)
    (close-structural-residual-sequenceo
      frontier
      proof-vars
      sigma
      sigma-out
      neqs
      neqs-out
      prog
      (gamma/closed-terms-for-fuel prog continuation-fuel)
      continuation-fuel
      subproof)))

(defn- continuation-term-vars
  [term]
  (case (ast/tag-of term)
    var #{(second term)}
    par #{}
    app (reduce into #{} (map continuation-term-vars (nnext term)))
    #{}))

(defn- continuation-formula-vars
  [formula]
  (case (ast/tag-of formula)
    true #{}
    false #{}
    pos (continuation-term-vars (second formula))
    neg (continuation-term-vars (second formula))
    eq (into (continuation-term-vars (second formula))
             (continuation-term-vars (nth formula 2)))
    neq (into (continuation-term-vars (second formula))
              (continuation-term-vars (nth formula 2)))
    and (into (continuation-formula-vars (second formula))
              (continuation-formula-vars (nth formula 2)))
    or (into (continuation-formula-vars (second formula))
             (continuation-formula-vars (nth formula 2)))
    forall (disj (continuation-formula-vars (:body (second formula)))
                 (:binding-nom (second formula)))
    once-forall (disj (continuation-formula-vars (:body (second formula)))
                      (:binding-nom (second formula)))
    exists (disj (continuation-formula-vars (:body (second formula)))
                 (:binding-nom (second formula)))
    #{}))

(defn- fast-continuation-lookup-guarded-clause
  [program relation]
  (some (fn [clause]
          (when (= relation (:relation clause))
            clause))
        (:guarded-clause-list program)))

(defn- fast-continuation-defined-relation?
  [program relation]
  (boolean (fast-continuation-lookup-guarded-clause program relation)))

(defn- live-continuation-subst-map
  [sigma]
  (into {}
        (map (fn [[binding-nom term]]
               [binding-nom term]))
        sigma))

(defn- live-continuation-walk-term
  [sigma term]
  (let [sigma-map (if (map? sigma)
                    sigma
                    (live-continuation-subst-map sigma))]
    (letfn [(walk [term]
              (case (ast/tag-of term)
                var (if-let [value (get sigma-map (second term))]
                      (walk value)
                      term)
                par term
                app (apply ast/app-term
                           (second term)
                           (map walk (nnext term)))
                term))]
      (walk term))))

(defn- live-continuation-defined-negative-call?
  [program formula]
  (and (= 'neg (ast/tag-of formula))
       (let [atom (second formula)]
         (and (= 'app (ast/tag-of atom))
              (fast-continuation-defined-relation? program (second atom))))))

(defn- live-continuation-constructor-demand?
  [sigma formula]
  (let [atom (second formula)]
    (boolean
      (some (fn [term]
              (= 'app (ast/tag-of (live-continuation-walk-term sigma term))))
            (nnext atom)))))

(defn- live-continuation-demanded-negative-call?
  [program sigma formula]
  (and (live-continuation-defined-negative-call? program formula)
       (live-continuation-constructor-demand? sigma formula)))

(defn- live-continuable-frontier?
  [program sigma frontier]
  (and (seq frontier)
       (every? #(live-continuation-defined-negative-call? program %) frontier)
       (some #(live-continuation-constructor-demand? sigma %) frontier)))

(defn- demanded-negative-callo
  "Relation-adjacent scheduler guard for the concrete live-state boundary.

   The surrounding selector is relational (`conde` + `appendo`) and does not
   score or name predicates. The only host check here is the existing structural
   demand classifier over the projected live `sigma` and one concrete residual."
  [program sigma formula]
  (project [sigma formula]
    (if (live-continuation-demanded-negative-call? program sigma formula)
      (== true true)
      fail)))

(defn- prioritize-structural-residual-frontiero
  "Relationally prefer a constructor-demanded residual as the next continuation
   obligation.

   This keeps the selector generic: it never dispatches on relation or
   constructor names. If the frontier already starts with a demanded residual,
   it preserves the original order. Otherwise it uses `appendo` to select a
   demanded residual from later in the frontier and moves that one obligation
   to the front while preserving the relative order of the remaining residuals."
  [program sigma frontier ordered-frontier proof]
  (conde
    [(fresh [head tail]
       (== (lcons head tail) frontier)
       (demanded-negative-callo program sigma head)
       (== frontier ordered-frontier)
       (== '(structural-residual-priority-head-demand) proof))]
    [(fresh [prefix selected suffix rest]
       (appendo prefix (lcons selected suffix) frontier)
       (demanded-negative-callo program sigma selected)
       (appendo prefix suffix rest)
       (== (lcons selected rest) ordered-frontier)
       (== '(structural-residual-priority-promote-demanded) proof))]))

(declare fast-schedule-live-structural-frontier)

(defn schedule-structural-residual-frontiero
  "Narrow raw live-state scheduler for structural residual continuation.

   The scheduler sits in the answer overlay after ordinary answer-mode search
   has produced a live residual frontier but before answer export. It either
   continues a structurally productive negative-call frontier through the
   guarded-IR continuation agenda or leaves the frontier available for
   diagnostics/export. The guard is intentionally conservative so ordinary
   proof search and the kernel rules remain visible.

   `proof-fuel` is the fuel used by the proof branch that produced the
   frontier. `continuation-fuel` is a separate contract for the residual
   continuation agenda; when it is exhausted, the scheduler exports the
   frontier instead of falling back to ordinary answer-mode defer/export
   branches."
  [frontier _proof-vars sigma sigma-out neqs neqs-out residuals-out prog proof-fuel continuation-fuel proof]
  (conda
    [(fresh [ordered-frontier priority-proof]
       (prioritize-structural-residual-frontiero
         prog
         sigma
         frontier
         ordered-frontier
         priority-proof)
       (project [frontier ordered-frontier sigma neqs]
         (if (live-continuable-frontier? prog sigma frontier)
           (if-let [{continued-sigma :sigma
                     continued-neqs :neqs
                     frontier-count :frontier-count}
                    (fast-schedule-live-structural-frontier
                      prog
                      sigma
                      neqs
                      ordered-frontier
                      continuation-fuel)]
             (fresh []
               (== continued-sigma sigma-out)
               (== continued-neqs neqs-out)
               (== '() residuals-out)
               (== (list 'structural-residual-scheduler-continue
                         priority-proof
                         (list 'structural-residual-continuation
                               (list 'structural-residual-continuation-agenda
                                     (list 'structural-residual-continuation-fuel
                                           continuation-fuel)
                                     (list 'structural-residual-frontier-closed
                                           frontier-count
                                           (list 'structural-residual-proof-fuel
                                                 proof-fuel)))))
                   proof))
             fail)
           fail)))]
    [(== sigma sigma-out)
     (== neqs neqs-out)
     (== frontier residuals-out)
     (== '(structural-residual-scheduler-export) proof)]))

(defn prove-program-answer-scheduledo
  "Program answer relation with raw live-state residual scheduling enabled."
  ([fml unexpanded lits env answer-vars prog sigma-out neqs-out residuals-out fuel call-depth proof]
   (prove-program-answer-scheduledo
     fml
     unexpanded
     lits
     env
     answer-vars
     prog
     sigma-out
     neqs-out
     residuals-out
     fuel
     call-depth
     fuel
     proof))
  ([fml unexpanded lits env answer-vars prog sigma-out neqs-out residuals-out fuel call-depth continuation-fuel proof]
   (fresh [sigma-mid neqs-mid residuals-mid base-proof schedule-proof]
     (prove-program-answero
       fml
       unexpanded
       lits
       env
       answer-vars
       prog
       sigma-mid
       neqs-mid
       residuals-mid
       fuel
       call-depth
       base-proof)
     (schedule-structural-residual-frontiero
       residuals-mid
       answer-vars
       sigma-mid
       sigma-out
       neqs-mid
       neqs-out
       residuals-out
       prog
       fuel
       continuation-fuel
       schedule-proof)
     (== (list 'answer-residual-scheduler base-proof schedule-proof) proof))))

(defn prove-program-query-entry-scheduledo
  "Top-level program query-entry relation with residual scheduling enabled."
  ([lit answer-vars prog sigma-out neqs-out residuals-out fuel call-depth proof]
   (prove-program-query-entry-scheduledo
     lit
     answer-vars
     prog
     sigma-out
     neqs-out
     residuals-out
     fuel
     call-depth
     fuel
     proof))
  ([lit answer-vars prog sigma-out neqs-out residuals-out fuel call-depth continuation-fuel proof]
   (fresh [sigma-mid neqs-mid residuals-mid base-proof schedule-proof]
     (prove-program-query-entryo
       lit
       answer-vars
       prog
       sigma-mid
       neqs-mid
       residuals-mid
       fuel
       call-depth
       base-proof)
     (schedule-structural-residual-frontiero
       residuals-mid
       answer-vars
       sigma-mid
       sigma-out
       neqs-mid
       neqs-out
       residuals-out
       prog
       fuel
       continuation-fuel
       schedule-proof)
     (== (list 'answer-residual-scheduler base-proof schedule-proof) proof))))

(defn- fast-continuation-guarded-alternative-vars
  [guarded]
  (reduce into
          (set (map :binding-nom (:scope guarded)))
          (concat
            (map continuation-formula-vars (:guards guarded))
            (map continuation-formula-vars (:calls guarded))
            (map continuation-formula-vars (:residuals guarded)))))

(defn- fast-continuation-fresh-var
  [nom]
  (symbol (str "src$" (hash nom) "$" (gensym))))

(defn- fast-continuation-freshen-term
  [renaming term]
  (case (ast/tag-of term)
    var (ast/var-term (get renaming (second term) (second term)))
    par term
    app (apply ast/app-term
               (second term)
               (map #(fast-continuation-freshen-term renaming %) (nnext term)))
    term))

(defn- fast-continuation-freshen-formula
  [renaming formula]
  (case (ast/tag-of formula)
    true formula
    false formula
    pos (ast/pos-lit (fast-continuation-freshen-term renaming (second formula)))
    neg (ast/neg-lit (fast-continuation-freshen-term renaming (second formula)))
    eq (ast/eq-lit (fast-continuation-freshen-term renaming (second formula))
                   (fast-continuation-freshen-term renaming (nth formula 2)))
    neq (ast/neq-lit (fast-continuation-freshen-term renaming (second formula))
                     (fast-continuation-freshen-term renaming (nth formula 2)))
    and (ast/and-form (fast-continuation-freshen-formula renaming (second formula))
                      (fast-continuation-freshen-formula renaming (nth formula 2)))
    or (ast/or-form (fast-continuation-freshen-formula renaming (second formula))
                    (fast-continuation-freshen-formula renaming (nth formula 2)))
    exists (let [tied (second formula)]
             (ast/exists-form (get renaming (:binding-nom tied)
                                   (:binding-nom tied))
                              (fast-continuation-freshen-formula
                                renaming
                                (:body tied))))
    forall (let [tied (second formula)]
             (ast/forall-form (get renaming (:binding-nom tied)
                                   (:binding-nom tied))
                              (fast-continuation-freshen-formula
                                renaming
                                (:body tied))))
    once-forall (let [tied (second formula)]
                  (ast/once-forall-form
                    (get renaming (:binding-nom tied) (:binding-nom tied))
                    (fast-continuation-freshen-formula renaming (:body tied))))
    formula))

(defn- fast-continuation-freshen-guarded-alternative
  [params guarded]
  (let [renaming (into {}
                       (map (fn [nom]
                              [nom (fast-continuation-fresh-var nom)]))
                       (into (set params)
                             (fast-continuation-guarded-alternative-vars guarded)))]
    {:params (mapv #(get renaming %) params)
     :scope (mapv (fn [entry]
                    (update entry :binding-nom #(get renaming % %)))
                  (:scope guarded))
     :guards (mapv #(fast-continuation-freshen-formula renaming %)
                   (:guards guarded))
     :calls (mapv #(fast-continuation-freshen-formula renaming %)
                  (:calls guarded))
     :residuals (mapv #(fast-continuation-freshen-formula renaming %)
                      (:residuals guarded))}))

(defn- fast-continuation-walk-term
  [sigma term]
  (case (ast/tag-of term)
    var (if-let [value (get sigma (second term))]
          (recur sigma value)
          term)
    par term
    app (apply ast/app-term
               (second term)
               (map #(fast-continuation-walk-term sigma %) (nnext term)))
    term))

(defn- fast-continuation-canonical-id
  [ctx category prefix nom]
  (let [mapping-key (keyword (str (name category) "s"))
        next-key (keyword (str "next-" (name category)))]
    (if-let [canonical (get-in ctx [mapping-key nom])]
      [ctx canonical]
      (let [idx (get ctx next-key 0)
            canonical [prefix idx]]
        [(-> ctx
             (assoc-in [mapping-key nom] canonical)
             (assoc next-key (inc idx)))
         canonical]))))

(defn- fast-continuation-canonical-var
  [ctx nom]
  (fast-continuation-canonical-id ctx :var :var nom))

(defn- fast-continuation-canonical-par
  [ctx nom]
  (fast-continuation-canonical-id ctx :par :par nom))

(defn- fast-continuation-canonical-term
  [ctx term]
  (case (ast/tag-of term)
    var (fast-continuation-canonical-var ctx (second term))
    par (fast-continuation-canonical-par ctx (second term))
    app (let [[ctx args]
              (reduce (fn [[ctx args] arg]
                        (let [[ctx arg-key]
                              (fast-continuation-canonical-term ctx arg)]
                          [ctx (conj args arg-key)]))
                      [ctx []]
                      (nnext term))]
          [ctx (into [:app (second term)] args)])
    [ctx [:term term]]))

(defn- fast-continuation-call-key
  [atom]
  (second (fast-continuation-canonical-term {} atom)))

(defn- fast-continuation-l-ground-term?
  "True when a walked continuation call contains only object-language terms."
  [term]
  (case (ast/tag-of term)
    var false
    par false
    app (every? fast-continuation-l-ground-term? (nnext term))
    false))

(defn- fast-continuation-enter-call
  [state atom]
  (if-not (fast-continuation-l-ground-term? atom)
    [state nil '(structural-residual-visited-open-call)]
    (let [call-key (fast-continuation-call-key atom)]
      (when-not (contains? (:active-calls state #{}) call-key)
        [(update state :active-calls (fnil conj #{}) call-key)
         call-key
         '(structural-residual-visited-enter)]))))

(defn- fast-continuation-leave-call
  [state call-key]
  (if call-key
    (update state :active-calls disj call-key)
    state))

(defn- fast-continuation-occurs?
  [sigma binding-nom term]
  (let [term (fast-continuation-walk-term sigma term)]
    (case (ast/tag-of term)
      var (= binding-nom (second term))
      app (boolean
            (some #(fast-continuation-occurs? sigma binding-nom %)
                  (nnext term)))
      false)))

(declare fast-continuation-unify-term
         fast-continuation-rigid-different?
         fast-continuation-solve-formula
         fast-continuation-solve-atom)

(defn- fast-continuation-unify-term-list
  [sigma left right]
  (cond
    (and (empty? left) (empty? right))
    [sigma '(structural-residual-args-done)]

    (or (empty? left) (empty? right))
    nil

    :else
    (when-let [[sigma head-proof]
               (fast-continuation-unify-term sigma (first left) (first right))]
      (when-let [[sigma tail-proof]
                 (fast-continuation-unify-term-list sigma (rest left) (rest right))]
        [sigma (list 'structural-residual-args head-proof tail-proof)]))))

(defn- fast-continuation-bind-var
  [sigma binding-nom value]
  (when-not (fast-continuation-occurs? sigma binding-nom value)
    [(assoc sigma binding-nom value) '(structural-residual-bind)]))

(defn- fast-continuation-unify-term
  [sigma left right]
  (let [left (fast-continuation-walk-term sigma left)
        right (fast-continuation-walk-term sigma right)]
    (cond
      (= left right)
      [sigma '(structural-residual-refl)]

      (= 'var (ast/tag-of left))
      (fast-continuation-bind-var sigma (second left) right)

      (= 'var (ast/tag-of right))
      (fast-continuation-bind-var sigma (second right) left)

      (and (= 'app (ast/tag-of left))
           (= 'app (ast/tag-of right))
           (= (second left) (second right)))
      (when-let [[sigma arg-proof]
                 (fast-continuation-unify-term-list
                   sigma
                   (nnext left)
                   (nnext right))]
        [sigma (list 'structural-residual-decompose arg-proof)])

      :else
      nil)))

(defn- fast-continuation-rigid-different-list?
  [sigma left right]
  (cond
    (and (empty? left) (empty? right)) false
    (or (empty? left) (empty? right)) true
    :else (or (fast-continuation-rigid-different? sigma (first left) (first right))
              (fast-continuation-rigid-different-list?
                sigma
                (rest left)
                (rest right)))))

(defn- fast-continuation-rigid-different?
  [sigma left right]
  (let [left (fast-continuation-walk-term sigma left)
        right (fast-continuation-walk-term sigma right)]
    (and (= 'app (ast/tag-of left))
         (= 'app (ast/tag-of right))
         (or (not= (second left) (second right))
             (fast-continuation-rigid-different-list?
               sigma
               (nnext left)
               (nnext right))))))

(defn- fast-continuation-solve-eq-guard
  [state guard]
  (let [[_ left right] guard]
    (when-let [[sigma proof]
               (fast-continuation-unify-term (:subst state) left right)]
      [(assoc state :subst sigma)
       (list 'structural-residual-guard guard proof)])))

(defn- fast-continuation-solve-neq-guard
  [state guard]
  (let [[_ left right] guard]
    (when (fast-continuation-rigid-different? (:subst state) left right)
      [state (list 'structural-residual-guard guard
                   '(structural-residual-rigid-neq))])))

(defn- fast-continuation-step-fuel
  [state]
  (let [fuel (:fuel state)]
    (cond
      (nil? fuel) state
      (pos? fuel) (update state :fuel dec)
      :else nil)))

(defn- fast-continuation-solve-sequence
  [program state formulas]
  (if (empty? formulas)
    (list [state '(structural-residual-seq-done)])
    (for [[head-state head-proof]
          (fast-continuation-solve-formula program state (first formulas))
          [tail-state tail-proof]
          (fast-continuation-solve-sequence program head-state (rest formulas))]
      [tail-state
       (list 'structural-residual-seq-step head-proof tail-proof)])))

(defn- fast-continuation-solve-guards
  [state guards]
  (if (empty? guards)
    (list [state '(structural-residual-guards-done)])
    (when-let [[state head-proof]
               (case (ast/tag-of (first guards))
                 eq (fast-continuation-solve-eq-guard state (first guards))
                 neq (fast-continuation-solve-neq-guard state (first guards))
                 nil)]
      (for [[state tail-proof]
            (fast-continuation-solve-guards state (rest guards))]
        [state (list 'structural-residual-guards-step
                     head-proof
                     tail-proof)]))))

(defn- fast-continuation-solve-formula
  [program state formula]
  (case (ast/tag-of formula)
    true (list [state '(structural-residual-true)])
    false '()
    eq (if-let [[state proof] (fast-continuation-solve-eq-guard state formula)]
         (list [state proof])
         '())
    neq (if-let [[state proof] (fast-continuation-solve-neq-guard state formula)]
          (list [state proof])
          '())
    and (for [[left-state left-proof]
              (fast-continuation-solve-formula program state (second formula))
              [right-state right-proof]
              (fast-continuation-solve-formula
                program
                left-state
                (nth formula 2))]
          [right-state (list 'structural-residual-and left-proof right-proof)])
    or (concat
         (fast-continuation-solve-formula program state (second formula))
         (fast-continuation-solve-formula program state (nth formula 2)))
    pos (let [atom (fast-continuation-walk-term (:subst state) (second formula))]
          (when (fast-continuation-defined-relation? program (second atom))
            (fast-continuation-solve-atom program state atom)))
    neg (let [atom (fast-continuation-walk-term (:subst state) (second formula))]
          (when (fast-continuation-defined-relation? program (second atom))
            (for [[state proof]
                  (fast-continuation-solve-atom program state atom)]
              [state (list 'structural-residual-neg-call formula proof)])))
    '()))

(defn- fast-continuation-bind-params
  [state params args]
  (loop [state state
         params params
         args args
         proofs []]
    (cond
      (and (empty? params) (empty? args))
      [state (list 'structural-residual-bind-params proofs)]

      (or (empty? params) (empty? args))
      nil

      :else
      (when-let [[sigma proof]
                 (fast-continuation-unify-term
                   (:subst state)
                   (ast/var-term (first params))
                   (first args))]
        (recur (assoc state :subst sigma)
               (rest params)
               (rest args)
               (conj proofs proof))))))

(defn- fast-continuation-solve-alternative
  [program state params args guarded]
  (let [guarded (fast-continuation-freshen-guarded-alternative params guarded)]
    (when-let [[state bind-proof]
               (fast-continuation-bind-params state (:params guarded) args)]
      (for [[guard-state guard-proof]
            (fast-continuation-solve-guards state (:guards guarded))
            [call-state call-proof]
            (fast-continuation-solve-sequence
              program
              guard-state
              (:calls guarded))
            [residual-state residual-proof]
            (fast-continuation-solve-sequence
              program
              call-state
              (:residuals guarded))]
        [residual-state
         (list 'structural-residual-alt
               bind-proof
               guard-proof
               call-proof
               residual-proof)]))))

(defn- fast-continuation-solve-atom
  [program state atom]
  (let [atom (fast-continuation-walk-term (:subst state) atom)]
    (when-let [[state call-key visited-proof]
               (fast-continuation-enter-call state atom)]
      (when-let [state (fast-continuation-step-fuel state)]
        (let [relation (second atom)
              args (vec (nnext atom))
              {:keys [params guarded-alternatives]}
              (fast-continuation-lookup-guarded-clause program relation)]
          (when guarded-alternatives
            (mapcat
              (fn [guarded]
                (for [[state proof]
                      (fast-continuation-solve-alternative
                        program
                        state
                        params
                        args
                        guarded)]
                  [(fast-continuation-leave-call state call-key)
                   (list 'structural-residual-call
                         atom
                         visited-proof
                         proof)]))
              guarded-alternatives)))))))

(defn- fast-continuation-self-binding?
  [[binding-nom term]]
  (= term (ast/var-term binding-nom)))

(defn- fast-continuation-binding-subst
  [bindings]
  (into {}
        (keep (fn [[binding-nom term]]
                (when-not (fast-continuation-self-binding? [binding-nom term])
                  [binding-nom term])))
        bindings))

(defn- fast-continuation-walk-binding
  [sigma [binding-nom term]]
  [binding-nom (fast-continuation-walk-term sigma term)])

(defn- fast-continuation-settle-negative-residual
  [program state formula]
  (when (= 'neg (ast/tag-of formula))
    (let [atom (fast-continuation-walk-term (:subst state) (second formula))]
      (when (fast-continuation-defined-relation? program (second atom))
        (for [[state proof]
              (fast-continuation-solve-atom program state atom)]
          [state (list 'structural-residual-neg-residual formula proof)])))))

(defn- fast-continuation-settle-residual-sequence
  [program state formulas]
  (if (empty? formulas)
    (list [state '(structural-residuals-done)])
    (for [[head-state head-proof]
          (fast-continuation-settle-negative-residual
            program
            state
            (first formulas))
          [tail-state tail-proof]
          (fast-continuation-settle-residual-sequence
            program
            head-state
            (rest formulas))]
      [tail-state
       (list 'structural-residuals-step head-proof tail-proof)])))

(defn- fast-continuation-walk-neq
  [sigma [left right]]
  [(fast-continuation-walk-term sigma left)
   (fast-continuation-walk-term sigma right)])

(defn- fast-continuation-live-sigma-out
  [original-sigma sigma]
  (let [original-noms (mapv first original-sigma)
        original-nom-set (set original-noms)
        walked-original (map (fn [[binding-nom term]]
                               [binding-nom
                                (fast-continuation-walk-term sigma term)])
                             original-sigma)
        new-bindings (->> sigma
                          (remove (fn [[binding-nom _]]
                                    (contains? original-nom-set binding-nom)))
                          (sort-by (comp pr-str first))
                          (map (fn [[binding-nom term]]
                                 [binding-nom
                                  (fast-continuation-walk-term sigma term)])))]
    (apply list (concat walked-original new-bindings))))

(defn- fast-schedule-live-structural-frontier
  [program sigma neqs frontier fuel]
  (let [initial-state {:subst (live-continuation-subst-map sigma)
                       :fuel fuel}]
    (first
      (for [[state _proof]
            (fast-continuation-settle-residual-sequence
              program
              initial-state
              frontier)]
        {:sigma (fast-continuation-live-sigma-out sigma (:subst state))
         :neqs (apply list
                      (map #(fast-continuation-walk-neq (:subst state) %)
                           neqs))
         :frontier-count (count frontier)}))))

(defn- fast-continue-exported-structural-records
  [program record fuel limit]
  (let [initial-state {:subst (fast-continuation-binding-subst (:bindings record))
                       :fuel fuel}]
    (mapv
      (fn [[state _proof]]
        (assoc record
               :bindings (mapv #(fast-continuation-walk-binding
                                   (:subst state)
                                   %)
                                (:bindings record))
               :residuals []
               :proofs (conj (vec (:proofs record))
                             (list 'structural-residual-continuation
                                   (list 'structural-residual-frontier-closed
                                         (count (:residuals record)))))))
      (take limit
            (fast-continuation-settle-residual-sequence
              program
              initial-state
              (:residuals record))))))

(defn- fast-continue-exported-structural-record
  [program record fuel]
  (first (fast-continue-exported-structural-records program record fuel 1)))

(defn continue-exported-structural-recordo
  "Fast relational entry point for exported structural residual continuation.

   Public answer export calls this relation after raw proof-state projection.
   At that point `program`, `record`, and `fuel` are concrete, so this relation
   can run a deterministic guarded-IR continuation without asking core.logic to
   walk the full compiled program as a projected value. The result remains an
   answer-overlay proof object tagged with `structural-residual-continuation`
   and does not depend on the constructor-recursive diagnostic sidecar."
  [program record record-out fuel]
  (if-let [continued (fast-continue-exported-structural-record
                       program
                       record
                       fuel)]
    (== record-out continued)
    fail))

(defn continue-exported-structural-records
  "Return up to `limit` structural continuations for an exported record.

   This is the enumerating companion to
   `continue-exported-structural-recordo`. It reuses the same generic guarded-IR
   continuation engine and proof vocabulary, but returns concrete records for
   profiled answer paths that need more than the first continuation."
  [program record fuel limit]
  (fast-continue-exported-structural-records program record fuel limit))

(defn proveo
  "Public five-argument kernel relation.

   Existing callers see the same surface signature, but each branch now starts
   with an empty equality substitution and empty disequality store.

   Unlike the answer-exporting entry points below, this wrapper does not
   designate answer variables and therefore behaves like ordinary proof search
   even though it threads residual state internally."
  ([fml unexpanded lits env proof]
   (fresh [sigma-out neqs-out residuals-out]
          (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out '() residuals-out nil '() nil nil false proof)))
  ([fml unexpanded lits env fuel proof]
   (fresh [sigma-out neqs-out residuals-out]
          (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out '() residuals-out nil '() fuel nil false proof))))

(defn prove-answero
  "Kernel relation with explicit exported answer variables.

   `answer-vars` are top-level free noms whose bindings may be learned during
   proof search and returned through `sigma-out`. Residual disequalities and
   deferred call obligations are returned through `neqs-out` and
   `residuals-out`.

   This is the answer-overlay analogue of asking the ordinary kernel for a
   proof witness, except that now we also keep the symbolic frontier visible."
  ([fml unexpanded lits env answer-vars sigma-out neqs-out residuals-out proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out nil '() nil 1 true proof))
  ([fml unexpanded lits env answer-vars sigma-out neqs-out residuals-out fuel proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out nil '() fuel 1 true proof))
  ([fml unexpanded lits env answer-vars sigma-out neqs-out residuals-out fuel call-depth proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out nil '() fuel call-depth true proof)))

(defn prove-programo
  "Kernel relation with an explicit compiled program for procedure calls.

   This mirrors `proflog.kernel/prove-programo` but preserves the answer-layer
   plumbing so that the same internal machine can also serve the exported
   answer-entry surfaces below."
  ([fml unexpanded lits env prog proof]
   (fresh [sigma-out neqs-out residuals-out]
          (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out '() residuals-out prog (gamma/closed-terms-for-fuel prog nil) nil nil false proof)))
  ([fml unexpanded lits env prog fuel proof]
   (fresh [sigma-out neqs-out residuals-out]
          (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out '() residuals-out prog (gamma/closed-terms-for-fuel prog fuel) fuel nil false proof))))

(defn prove-program-answero
  "Kernel relation for query-answer export with explicit answer variables.

   This is the most direct answer-search analogue of `prove-programo`: keep the
   compiled program explicit, keep answer vars explicit, and expose the learned
   substitution plus residual frontier."
  ([fml unexpanded lits env answer-vars prog sigma-out neqs-out residuals-out proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out prog (gamma/closed-terms-for-fuel prog nil) nil 1 true proof))
  ([fml unexpanded lits env answer-vars prog sigma-out neqs-out residuals-out fuel proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out prog (gamma/closed-terms-for-fuel prog fuel) fuel 1 true proof))
  ([fml unexpanded lits env answer-vars prog sigma-out neqs-out residuals-out fuel call-depth proof]
   (prove-stateo fml unexpanded lits env answer-vars '() sigma-out '() neqs-out '() residuals-out prog (gamma/closed-terms-for-fuel prog fuel) fuel call-depth true proof)))

(defn prove-program-query-entryo
  "Kernel relation for top-level literal query-answer export relative to `prog`.

   The entry procedure call itself does not consume `call-depth`; that staged
   budget is reserved for recursive descendants below the query boundary.

   This is the operational bridge from the user-facing query API to the
   internal tableau engine. The top-level query atom is treated specially:

   - validate it as an immediate program call,
   - open the matching clause body or its NNF negation,
   - and start the subsidiary tableau with answer export enabled from the
     outset.

   That is why `query-answers` can talk about recursive descendants below the
   query boundary rather than charging the root query atom itself against the
   `call-depth` budget."
  ([lit answer-vars prog sigma-out neqs-out residuals-out proof]
   (prove-program-query-entryo lit answer-vars prog sigma-out neqs-out residuals-out nil 0 proof))
  ([lit answer-vars prog sigma-out neqs-out residuals-out fuel proof]
   (prove-program-query-entryo lit answer-vars prog sigma-out neqs-out residuals-out fuel 0 proof))
  ([lit answer-vars prog sigma-out neqs-out residuals-out fuel call-depth proof]
   (let [gamma-terms (gamma/closed-terms-for-fuel prog fuel)]
     (conde
       ;; Positive top-level query atom: open the clause body directly. Because
       ;; this is the query boundary, the root call itself does not decrement the
       ;; recursive answer-call budget.
       [(fresh [atom relation args call-env body negated-body subproof]
          (== (list 'pos atom) lit)
          (== (lcons 'app (lcons relation args)) atom)
          (support/l-ground-term*o args)
          (program/call-clauseo prog atom call-env body negated-body)
          (== (list 'query-pos-call subproof) proof)
          (prove-stateo body
                        '()
                        '()
                        call-env
                        answer-vars
                        '()
                        sigma-out
                        '()
                        neqs-out
                        '()
                        residuals-out
                        prog
                        gamma-terms
                        fuel
                        call-depth
                        true
                        subproof))]
       [(fresh [atom relation args call-env body negated-body
                alternatives negated-alternatives guarded-alternatives subproof]
          (== (list 'neg atom) lit)
          (== (lcons 'app (lcons relation args)) atom)
          (support/l-ground-term*o args)
          (program/call-clause-with-guarded-alternativeso
            prog
            atom
            call-env
            body
            negated-body
            alternatives
            negated-alternatives
            guarded-alternatives)
          (fresh [first-alternative second-alternative remaining-alternatives]
            (== (lcons first-alternative
                       (lcons second-alternative remaining-alternatives))
                alternatives))
          (== (list 'query-neg-call-guarded-alt subproof) proof)
          (close-one-guarded-alternativeo
            guarded-alternatives
            call-env
            answer-vars
            '()
            sigma-out
            '()
            neqs-out
            '()
            residuals-out
            prog
            gamma-terms
            fuel
            call-depth
            true
            subproof))]
       ;; Negative top-level query atom: open the precomputed NNF negation of the
       ;; clause body, matching Fitting's Part 2 call rule.
       [(fresh [atom relation args call-env body negated-body subproof]
          (== (list 'neg atom) lit)
          (== (lcons 'app (lcons relation args)) atom)
          (support/l-ground-term*o args)
          (program/call-clauseo prog atom call-env body negated-body)
          (== (list 'query-neg-call subproof) proof)
          (prove-stateo negated-body
                        '()
                        '()
                        call-env
                        answer-vars
                        '()
                        sigma-out
                        '()
                        neqs-out
                        '()
                        residuals-out
                        prog
                        gamma-terms
                        fuel
                        call-depth
                        true
                        subproof))]))))

(defn prove
  "Return up to `n` proof terms closing the given greenfield formula.

   This convenience wrapper is mainly useful when exploring the answer overlay
   as a proof engine in its own right. It still runs with answer-specific state
   present internally, but no answer vars are designated for export."
  ([fml] (prove fml 1))
  ([fml n]
   (run n [proof]
        (proveo fml '() '() '() proof)))
  ([fml n fuel]
   (run n [proof]
        (proveo fml '() '() '() fuel proof))))

(defn prove-program
  "Return up to `n` proof terms closing `fml` relative to `prog`.

   This is the answer-overlay companion to the ordinary kernel's
   `prove-program`: program calls are available, but no answer bindings are
   explicitly exported unless one of the `*-answero` relations is used."
  ([prog fml n]
   (run n [proof]
        (prove-programo fml '() '() '() prog proof)))
  ([prog fml n fuel]
   (run n [proof]
        (prove-programo fml '() '() '() prog fuel proof))))
