(ns proflog.kernel
  "Greenfield tableau kernel with explicit equality state.

   This namespace is the ordinary proof-search core, not the answer-export
   layer. For a reader coming from Fitting's 1994 Proflog paper, the guiding
   picture is:

   - `prove-stateo` is the branch-closing tableau relation,
   - connectives and quantifiers follow the usual alpha / beta / gamma / delta
     operational reading,
   - literals are handled against an explicit branch state rather than by
     destructive side effects,
   - equality is modeled by an explicit substitution `sigma` plus a symbolic
     disequality store `neqs`,
   - and procedure calls are just another tableau step when an atom is
     sufficiently inside the object language `L`.

   Relative to the legacy experimental prover, the major structural difference
   is that equality information is not carried implicitly by branch rewriting.
   Instead:

   - gamma-introduced free proof variables are represented as `(var nom)`,
   - delta witnesses are rigid parameters `(par nom)`,
   - positive equality extends the explicit substitution,
   - negative equality may be stored symbolically until later bindings force a
     contradiction,
   - and saved literals are rechecked after each new equality step."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [!= == all appendo conde fail fresh lcons membero run]]
            [clojure.core.logic.nominal :as nominal]
            [proflog.ast :as ast]
            [proflog.equality :as equality]
            [proflog.kernel.equality-fragment :as equality-fragment]
            [proflog.formula-profile :as formula-profile]
            [proflog.kernel.first-order :as first-order]
            [proflog.gamma :as gamma]
            [proflog.kernel.propositional :as propositional]
            [proflog.kernel-support :as support]
            [proflog.program :as program]
            [proflog.subst :as subst]))

;; Reading guide
;; -------------
;;
;; The kernel keeps exactly the branch-local data that Fitting's operational
;; presentation leaves implicit:
;;
;; - `fml` / `unexpanded`: the current formula and the remaining branch work,
;; - `lits`: saved positive / negative atoms already on the branch,
;; - `env`: lexical substitution for bound variables introduced by tableau
;;   quantifier rules,
;; - `proof-vars`: the noms introduced by gamma so we can distinguish proof-time
;;   instantiations from user-visible answer variables,
;; - `sigma`: the explicit free-constructor equality substitution,
;; - `neqs`: delayed disequalities that remain open for now,
;; - `prog`: the compiled Proflog program used by the Procedure Call Rule,
;; - `fuel`: bounded micro-step control for non-closing branch progress,
;; - `proof`: the proof term witnessing the branch closure.
;;
;; The companion `proflog.answer-overlay` namespace reuses the same underlying
;; machinery but adds answer-variable export, residual deferred calls, and
;; recursive call-depth control. This file intentionally stops short of those
;; answer-oriented concerns.

(declare prove-stateo
         close-agendao
         saved-call-closeso
         close-one-guarded-alternativeo
         close-guarded-negated-call-sequenceo)

(def ^:dynamic *recursive-prove-stateo*
  "Optional recursive proof dispatcher.

   The ordinary kernel leaves this unbound and recurses directly through
   `prove-stateo`. Operational layers such as ADR-0017 tabling may bind it to a
   wrapper relation so recursive branch calls are memoized without adding table
   management to the Fitting-style rule clauses below."
  nil)

(def ^:dynamic *theory-profile-closeo*
  "Optional theory-profile branch rule.

   Ordinary proof search leaves this unbound. A language-selected proof profile
   may bind it to a miniKanren relation that can close or advance the currently
   focused branch formula. This keeps theory extensions interleaved with the
   tableau kernel instead of making them query-time preprocessors."
  nil)

(defn- recursive-prove-stateo
  [& args]
  (apply (or *recursive-prove-stateo* prove-stateo) args))

(defn- theory-profile-closeo
  [& args]
  (if-let [closeo *theory-profile-closeo*]
    (apply closeo args)
    fail))

;; ---------------------------------------------------------------------------
;; Profiled branch interoperation
;; ---------------------------------------------------------------------------
;;
;; This is a narrow foreground/background tableau boundary. The full program
;; kernel remains responsible for equality, disequality, procedure calls, and
;; answer-oriented execution. When a residual branch is already isolated from
;; those concerns, the optimized proof-producing layer may close it and return
;; a subproof under an explicit `profiled` proof tag.

(def ^:private unknown-program-relations ::unknown-program-relations)

(defn- active-program-relations
  [prog]
  (cond
    (nil? prog)
    #{}

    (and (map? prog)
         (map? (:clauses prog)))
    (set (keys (:clauses prog)))

    :else
    unknown-program-relations))

(defn- known-active-relationso
  [active-relations]
  (if (= unknown-program-relations active-relations)
    fail
    (== :known-active-relations :known-active-relations)))

(defn- inactive-relationo
  [active-relations relation]
  (cond
    (= unknown-program-relations active-relations)
    fail

    (empty? active-relations)
    (== relation relation)

    :else
    (let [active-relation (first active-relations)
          remaining-relations (rest active-relations)]
      (all
        (!= relation active-relation)
        (inactive-relationo remaining-relations relation)))))

(defn- nullary-atomo
  [atom active-relations]
  (fresh [relation]
    (== (list 'app relation) atom)
    (inactive-relationo active-relations relation)))

(defn- atomo
  [atom active-relations]
  (fresh [relation args]
    (== (lcons 'app (lcons relation args)) atom)
    (inactive-relationo active-relations relation)))

(declare pure-propositional-formulao
         equality-free-first-order-formulao
         first-order-feature-formulao
         formula-list-pure-propositionalo
         formula-list-equality-free-first-ordero
         formula-list-first-order-featureo)

(defn- compound-profile-entryo
  [formula]
  (conde
    [(fresh [left right]
       (== (list 'and left right) formula))]
    [(fresh [left right]
       (== (list 'or left right) formula))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'forall (nominal/tie binding-nom body)) formula)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'once-forall (nominal/tie binding-nom body)) formula)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'exists (nominal/tie binding-nom body)) formula)))]))

(defn- pure-propositional-formulao
  [formula active-relations]
  (conde
    [(== (list 'true) formula)]
    [(== (list 'false) formula)]
    [(fresh [atom]
       (== (list 'pos atom) formula)
       (nullary-atomo atom active-relations))]
    [(fresh [atom]
       (== (list 'neg atom) formula)
       (nullary-atomo atom active-relations))]
    [(fresh [left right]
       (== (list 'and left right) formula)
       (pure-propositional-formulao left active-relations)
       (pure-propositional-formulao right active-relations))]
    [(fresh [left right]
       (== (list 'or left right) formula)
       (pure-propositional-formulao left active-relations)
       (pure-propositional-formulao right active-relations))]))

(defn- equality-free-first-order-formulao
  [formula active-relations]
  (conde
    [(== (list 'true) formula)]
    [(== (list 'false) formula)]
    [(fresh [atom]
       (== (list 'pos atom) formula)
       (atomo atom active-relations))]
    [(fresh [atom]
       (== (list 'neg atom) formula)
       (atomo atom active-relations))]
    [(fresh [left right]
       (== (list 'and left right) formula)
       (equality-free-first-order-formulao left active-relations)
       (equality-free-first-order-formulao right active-relations))]
    [(fresh [left right]
       (== (list 'or left right) formula)
       (equality-free-first-order-formulao left active-relations)
       (equality-free-first-order-formulao right active-relations))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'forall (nominal/tie binding-nom body)) formula)
         (equality-free-first-order-formulao body active-relations)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'once-forall (nominal/tie binding-nom body)) formula)
         (equality-free-first-order-formulao body active-relations)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'exists (nominal/tie binding-nom body)) formula)
         (equality-free-first-order-formulao body active-relations)))]))

(defn- first-order-feature-formulao
  [formula]
  (conde
    [(fresh [atom relation arg rest]
       (== (list 'pos atom) formula)
       (== (lcons 'app (lcons relation (lcons arg rest))) atom))]
    [(fresh [atom relation arg rest]
       (== (list 'neg atom) formula)
       (== (lcons 'app (lcons relation (lcons arg rest))) atom))]
    [(fresh [left right]
       (== (list 'and left right) formula)
       (conde
         [(first-order-feature-formulao left)]
         [(first-order-feature-formulao right)]))]
    [(fresh [left right]
       (== (list 'or left right) formula)
       (conde
         [(first-order-feature-formulao left)]
         [(first-order-feature-formulao right)]))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'forall (nominal/tie binding-nom body)) formula)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'once-forall (nominal/tie binding-nom body)) formula)))]
    [(nominal/fresh [binding-nom]
       (fresh [body]
         (== (list 'exists (nominal/tie binding-nom body)) formula)))]))

(defn- formula-list-pure-propositionalo
  [formulas active-relations]
  (conde
    [(== '() formulas)]
    [(fresh [head tail]
       (== (lcons head tail) formulas)
       (pure-propositional-formulao head active-relations)
       (formula-list-pure-propositionalo tail active-relations))]))

(defn- formula-list-equality-free-first-ordero
  [formulas active-relations]
  (conde
    [(== '() formulas)]
    [(fresh [head tail]
       (== (lcons head tail) formulas)
       (equality-free-first-order-formulao head active-relations)
       (formula-list-equality-free-first-ordero tail active-relations))]))

(defn- formula-list-first-order-featureo
  [formulas]
  (fresh [head tail]
    (== (lcons head tail) formulas)
    (conde
      [(first-order-feature-formulao head)]
      [(formula-list-first-order-featureo tail)])))

(defn- branch-first-order-featureo
  [fml unexpanded lits]
  (conde
    [(first-order-feature-formulao fml)]
    [(formula-list-first-order-featureo unexpanded)]
    [(formula-list-first-order-featureo lits)]))

(defn- branch-profileo
  [fml unexpanded lits sigma neqs active-relations kind]
  (all
    (known-active-relationso active-relations)
    (== '() sigma)
    (== '() neqs)
    (compound-profile-entryo fml)
    (conde
      [(== 'propositional kind)
       (pure-propositional-formulao fml active-relations)
       (formula-list-pure-propositionalo unexpanded active-relations)
       (formula-list-pure-propositionalo lits active-relations)]
      [(== 'first-order kind)
       (equality-free-first-order-formulao fml active-relations)
       (formula-list-equality-free-first-ordero unexpanded active-relations)
       (formula-list-equality-free-first-ordero lits active-relations)
       (branch-first-order-featureo fml unexpanded lits)])))

(defn- profiled-closeo
  [fml unexpanded lits env sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (let [active-relations (active-program-relations prog)]
    (fresh [kind subproof next-fuel]
      (support/step-fuelo fuel next-fuel)
      (branch-profileo fml unexpanded lits sigma neqs active-relations kind)
      (== sigma sigma-out)
      (== neqs neqs-out)
      (conde
        [(== 'propositional kind)
         (== (list 'profiled 'propositional subproof) proof)
         (propositional/proveo fml unexpanded lits subproof)]
        [(== 'first-order kind)
         (== (list 'profiled 'first-order subproof) proof)
         (first-order/proveo fml unexpanded lits env subproof)]))))

;; Re-export the structural L-groundness relation here because procedure-call
;; admissibility is part of the kernel story from the paper's perspective.
(def l-ground-termo support/l-ground-termo)

(defn saved-call-closeso
  "Succeed when one saved atom becomes callable under the current equality
   substitution and its subsidiary tableau closes.

   This is the greenfield replacement for a large part of legacy
   equality-triggered paramodulation around procedure calls. Instead of
   rewriting saved literals syntactically, we:

   1. keep atoms on the branch in `lits`,
   2. walk them through the current equality substitution `sigma`,
   3. check whether the walked atom is now an admissible procedure call,
   4. and, if so, open the subsidiary tableau for the clause body.

   The important semantic point is that procedure-call completeness should
   depend on branch state, not on whether the enabling equality happened to be
   expanded before or after the atom was saved."
  [lits proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (conde
    ;; Saved positive atom. If equality has now walked its arguments into an
    ;; admissible L-ground shape, open the subsidiary tableau for the clause
    ;; body exactly as though the call had been available when the atom first
    ;; appeared.
    [(fresh [atom walked-atom relation args call-env body negated-body next-fuel subproof]
       (membero (list 'pos atom) lits)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clauseo prog walked-atom call-env body negated-body)
       (== (list 'eq-triggered-call subproof) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo body
                               '()
                               '()
                               call-env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               subproof))]
    ;; Saved negative atom. This is Fitting's "Part 2" procedure-call rule:
    ;; prove the NNF negation of the clause body rather than the body itself.
    [(fresh [atom walked-atom relation args call-env body negated-body next-fuel subproof]
       (membero (list 'neg atom) lits)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clauseo prog walked-atom call-env body negated-body)
       (== (list 'eq-triggered-neg-call subproof) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo negated-body
                               '()
                               '()
                               call-env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               subproof))]))

(defn- close-one-formulao
  "Close any formula from a finite relational list of alternatives."
  [formulas env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (conde
    [(fresh [formula rest subproof]
       (== (lcons formula rest) formulas)
       (== (list 'alt subproof) proof)
       (recursive-prove-stateo formula
                               '()
                               '()
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
    [(fresh [formula rest]
       (== (lcons formula rest) formulas)
       (close-one-formulao rest
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

(defn- guarded-alternative-fieldso
  "Expose the fields the kernel needs from compile-time guarded alternative IR."
  [guarded-alternative scope guards negated-calls negated-residuals negated-ordered-conjuncts]
  (fresh [formula
          negated-formula
          core
          conjuncts
          negated-conjuncts
          negated-guards
          calls
          residuals
          ordered-guards]
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
  "Instantiate leading existential alternative scope for negated call closure."
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
  "Close each formula in order, threading equality and disequality state."
  [formulas env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (conde
    [(== '() formulas)
     (== sigma sigma-out)
     (== neqs neqs-out)
     (== '(guarded-seq-done) proof)]
    [(fresh [formula subproof]
       (== (lcons formula '()) formulas)
       (== (list 'guarded-seq-last subproof) proof)
       (recursive-prove-stateo formula
                               '()
                               '()
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
    [(fresh [formula second-formula rest sigma-mid neqs-mid head-proof tail-proof]
       (== (lcons formula (lcons second-formula rest)) formulas)
       (== (list 'guarded-seq-step head-proof tail-proof) proof)
       (recursive-prove-stateo formula
                               '()
                               '()
                               env
                               proof-vars
                               sigma
                               sigma-mid
                               neqs
                               neqs-mid
                               prog
                               gamma-terms
                               fuel
                               head-proof)
       (close-formula-sequenceo
         (lcons second-formula rest)
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

(defn- saturate-eq-guardso
  "Unify equality guards through proof-local variables only."
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
  "Close the negation of one guarded alternative in guard-first order."
  [guarded-alternative env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
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
      [(fresh [sigma-mid sigma-after-calls neqs-after-calls guard-proof]
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
           prog
           gamma-terms
           fuel
           call-sequence-proof)
         (close-formula-sequenceo
           negated-residuals
           scoped-env
           scoped-proof-vars
           sigma-after-calls
           sigma-out
           neqs-after-calls
           neqs-out
           prog
           gamma-terms
           fuel
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
         prog
         gamma-terms
         fuel
         fallback-sequence-proof)])))

(defn- close-one-guarded-alternativeo
  "Close any guarded alternative from a finite relational list."
  [guarded-alternatives env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
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
         prog
         gamma-terms
         fuel
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
         prog
         gamma-terms
         fuel
         proof))]))

(defn- close-guarded-negated-call-sequenceo
  "Close guarded negative procedure calls without re-entering the full agenda.

   Guarded alternatives already classify these formulas as calls to program
   relations. Keeping their recursive descent on the guarded path avoids
   rechecking every generic tableau rule before each structurally smaller call."
  [formulas env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (conde
    [(== '() formulas)
     (== sigma sigma-out)
     (== neqs neqs-out)
     (== '(guarded-call-seq-done) proof)]
    [(fresh [formula rest lit atom walked-atom relation args
             call-env body negated-body alternatives negated-alternatives
             guarded-alternatives sigma-mid neqs-mid call-proof tail-proof]
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
       (close-one-guarded-alternativeo
         guarded-alternatives
         call-env
         proof-vars
         sigma
         sigma-mid
         neqs
         neqs-mid
         prog
         gamma-terms
         fuel
         call-proof)
       (close-guarded-negated-call-sequenceo
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

(defn close-agendao
  "Close one explicit pending-formula agenda under the ordinary kernel state.

   ADR-0016 introduces a fairer internal scheduler by making the branch work
   explicit as an agenda. `support/selecto` chooses one pending formula from
   that agenda relationally, and the rest of the tableau rules operate on that
   chosen formula plus the remaining pending work."
  [agenda lits env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (fresh [fml unexpanded]
    (support/selecto fml agenda unexpanded)
    (conde
    ;; ================================================================
    ;; Profiled branch handoff
    ;; ================================================================
    ;;
    ;; Once the residual branch is isolated from active program calls and
    ;; equality state, a specialized kernel layer may close it as a single
    ;; proof-producing background step.
    [(profiled-closeo fml unexpanded lits env sigma sigma-out neqs neqs-out prog gamma-terms fuel proof)]

    ;; ================================================================
    ;; Language-selected theory profile hook
    ;; ================================================================
    ;;
    ;; Deduction-modulo and other theory profiles can bind a relational branch
    ;; rule here. When unbound, this clause fails and the ordinary kernel rules
    ;; below run unchanged.
    [(theory-profile-closeo fml unexpanded lits env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof)]

    ;; ================================================================
    ;; Alpha rule: conjunction
    ;; ================================================================
    ;;
    ;; Fitting's tableau rule for `A and B` keeps one branch and requires both
    ;; conjuncts to close on that same branch. Operationally we prove the left
    ;; conjunct now and push the right conjunct onto the branch work stack.
    [(fresh [left right next-fuel prf]
       (== (list 'and left right) fml)
       (== (list 'conj prf) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo left
                               (lcons right unexpanded)
                               lits
                               env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))]

    ;; ================================================================
    ;; Beta rule: disjunction
    ;; ================================================================
    ;;
    ;; `A or B` splits the branch. Because the branch state is explicit, the
    ;; first sibling's output substitution and disequalities thread into the
    ;; second sibling. This makes the proof term read like a genuine sequence of
    ;; branch-closing obligations rather than two disconnected searches.
    [(fresh [left right next-fuel sigma-mid neqs-mid left-proof right-proof]
       (== (list 'or left right) fml)
       (== (list 'split left-proof right-proof) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo left
                               unexpanded
                               lits
                               env
                               proof-vars
                               sigma
                               sigma-mid
                               neqs
                               neqs-mid
                               prog
                               gamma-terms
                               next-fuel
                               left-proof)
       (recursive-prove-stateo right
                               unexpanded
                               lits
                               env
                               proof-vars
                               sigma-mid
                               sigma-out
                               neqs-mid
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               right-proof))]

    ;; ================================================================
    ;; Gamma rule: universal quantifier
    ;; ================================================================
    ;;
    ;; Universals are first instantiated with a fresh proof variable `(var
    ;; nom)`, preserving the historical search behavior for most quantified
    ;; programs. Bounded generated closed terms are a fallback path below for
    ;; cases where a concrete Herbrand counterexample is required.
    [(nominal/fresh [binding-nom]
       (nominal/fresh [free-var-nom]
         (fresh [body body-subst narrowed-env next-fuel prf]
           (== (list 'forall (nominal/tie binding-nom body)) fml)
           (== '() unexpanded)
           (== (list 'univ prf) proof)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (support/step-fuelo fuel next-fuel)
           (recursive-prove-stateo body-subst
                                   '()
                                   lits
                                   (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                   (lcons free-var-nom proof-vars)
                                   sigma
                                   sigma-out
                                   neqs
                                   neqs-out
                                   prog
                                   gamma-terms
                                   next-fuel
                                   prf))))]
    ;; Fitting's full gamma rule also permits closed terms from the object
    ;; language. The finite generation policy lives outside the kernel; the
    ;; kernel only asks for one candidate when the fresh-variable path is not
    ;; enough.
    [(nominal/fresh [binding-nom]
       (fresh [body body-subst narrowed-env witness-term next-fuel prf]
         (== (list 'forall (nominal/tie binding-nom body)) fml)
         (== '() unexpanded)
         (== (list 'univ prf) proof)
         (subst/remove-bindo binding-nom env narrowed-env)
         (subst/subst-formulao body narrowed-env body-subst)
         (support/call-free-formulao body-subst)
         (gamma/closed-term-candidateo gamma-terms witness-term)
         (support/step-fuelo fuel next-fuel)
         (recursive-prove-stateo body-subst
                                 '()
                                 lits
                                 (lcons [binding-nom witness-term] env)
                                 proof-vars
                                 sigma
                                 sigma-out
                                 neqs
                                 neqs-out
                                 prog
                                 gamma-terms
                                 next-fuel
                                 prf)))]
    ;; General gamma case: when there is already pending branch work, append the
    ;; original universal to the end so repeated instantiation remains possible.
    [(nominal/fresh [binding-nom]
       (nominal/fresh [free-var-nom]
         (fresh [body body-subst narrowed-env pending next-fuel prf]
           (== (list 'forall (nominal/tie binding-nom body)) fml)
           (== (list 'univ prf) proof)
           (appendo unexpanded (list fml) pending)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (support/step-fuelo fuel next-fuel)
           (recursive-prove-stateo body-subst
                                   pending
                                   lits
                                   (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                   (lcons free-var-nom proof-vars)
                                   sigma
                                   sigma-out
                                   neqs
                                   neqs-out
                                   prog
                                   gamma-terms
                                   next-fuel
                                   prf))))]
    ;; ================================================================
    ;; Once-forall: single-use universal
    ;; ================================================================
    ;;
    ;; This is not a primitive from Fitting's syntax; it is the NNF operational
    ;; form we obtain when negating an existential clause body for negative
    ;; procedure calls. Unlike gamma, it does not re-enqueue itself.
    [(nominal/fresh [binding-nom]
       (nominal/fresh [free-var-nom]
         (fresh [body body-subst narrowed-env next-fuel prf]
           (== (list 'once-forall (nominal/tie binding-nom body)) fml)
           (== (list 'once-univ prf) proof)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (support/step-fuelo fuel next-fuel)
           (recursive-prove-stateo body-subst
                                   unexpanded
                                   lits
                                   (lcons [binding-nom (ast/var-term free-var-nom)] env)
                                   (lcons free-var-nom proof-vars)
                                   sigma
                                   sigma-out
                                   neqs
                                   neqs-out
                                   prog
                                   gamma-terms
                                   next-fuel
                                   prf))))]
    [(nominal/fresh [binding-nom]
       (fresh [body body-subst narrowed-env witness-term next-fuel prf]
         (== (list 'once-forall (nominal/tie binding-nom body)) fml)
         (== (list 'once-univ prf) proof)
         (subst/remove-bindo binding-nom env narrowed-env)
         (subst/subst-formulao body narrowed-env body-subst)
         (support/call-free-formulao body-subst)
         (gamma/closed-term-candidateo gamma-terms witness-term)
         (support/step-fuelo fuel next-fuel)
         (recursive-prove-stateo body-subst
                                 unexpanded
                                 lits
                                 (lcons [binding-nom witness-term] env)
                                 proof-vars
                                 sigma
                                 sigma-out
                                 neqs
                                 neqs-out
                                 prog
                                 gamma-terms
                                 next-fuel
                                 prf)))]

    ;; ================================================================
    ;; Delta rule: existential quantifier
    ;; ================================================================
    ;;
    ;; Ordinary proof mode uses a rigid parameter `(par nom)` as the witness.
    ;; This matches the paper's delta-rule intuition: the witness is a fresh
    ;; but fixed element of the current branch, not a freely exportable answer
    ;; variable.
    [(nominal/fresh [binding-nom]
       (nominal/fresh [parameter-nom]
         (fresh [body body-subst narrowed-env next-fuel prf]
           (== (list 'exists (nominal/tie binding-nom body)) fml)
           (== (list 'witness prf) proof)
           (subst/remove-bindo binding-nom env narrowed-env)
           (subst/subst-formulao body narrowed-env body-subst)
           (support/step-fuelo fuel next-fuel)
           (recursive-prove-stateo body-subst
                                   unexpanded
                                   lits
                                   (lcons [binding-nom (ast/par-term parameter-nom)] env)
                                   proof-vars
                                   sigma
                                   sigma-out
                                   neqs
                                   neqs-out
                                   prog
                                   gamma-terms
                                   next-fuel
                                   prf))))]

    ;; ================================================================
    ;; Positive equality
    ;; ================================================================
    ;;
    ;; Free-constructor equality is handled in four phases:
    ;;
    ;; 1. immediate contradiction (`eq-contradictiono`),
    ;; 2. successful unification that falsifies a saved disequality,
    ;; 3. successful unification that makes saved complementary atoms unify,
    ;; 4. successful unification that makes a saved procedure call admissible,
    ;; 5. otherwise continue with the updated substitution.
    [(fresh [lit left right contradiction-proof]
       (subst/subst-formulao fml env lit)
       (== (list 'eq left right) lit)
       (equality/eq-contradictiono left right sigma contradiction-proof)
       (== sigma sigma-out)
       (== neqs neqs-out)
       (== contradiction-proof proof))]

    ;; New equality binding makes a previously saved disequality impossible.
    [(fresh [lit left right sigma-mid step-proof branch-proof]
       (subst/subst-formulao fml env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (equality/neq-violatedo neqs sigma-mid branch-proof)
       (== sigma-mid sigma-out)
       (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
       (== (list 'eq-step step-proof branch-proof) proof))]
    ;; New equality binding makes a saved positive and negative atom unify.
    [(fresh [lit left right sigma-mid step-proof branch-proof]
       (subst/subst-formulao fml env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (equality/contradictory-atomso lits sigma-mid sigma-out branch-proof)
       (support/prune-contradictory-neqso neqs sigma-out neqs-out)
       (== (list 'eq-step step-proof branch-proof) proof))]
    ;; New equality binding reopens a previously saved procedure call.
    [(fresh [lit left right sigma-mid step-proof branch-proof]
       (subst/subst-formulao fml env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (saved-call-closeso lits proof-vars sigma-mid sigma-out neqs neqs-out prog gamma-terms fuel branch-proof)
       (== (list 'eq-step step-proof branch-proof) proof))]
    ;; No immediate contradiction: keep the updated equality state and continue
    ;; with the next pending formula, provided the saved disequalities still
    ;; remain genuinely open under the new substitution.
    [(fresh [lit left right sigma-mid step-proof next rest next-fuel prf]
       (subst/subst-formulao fml env lit)
       (== (list 'eq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (== (lcons next rest) unexpanded)
       (== (list 'eq-step step-proof prf) proof)
       (support/stable-neqso neqs sigma-mid)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo next
                               rest
                               lits
                               env
                               proof-vars
                               sigma-mid
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))]

    ;; ================================================================
    ;; Negative equality
    ;; ================================================================
    ;;
    ;; `neq(t1, t2)` closes immediately only when the current substitution
    ;; already makes the two walked terms identical. Otherwise it either closes
    ;; by forcing equality through proof-local variables, or it is stored for
    ;; later rechecking after future equality steps.
    [(fresh [lit left right]
       (subst/subst-formulao fml env lit)
       (== (list 'neq left right) lit)
       (equality/same-termo left right sigma)
       (== sigma sigma-out)
       (== neqs neqs-out)
       (== '(refl-close) proof))]
    ;; The disequality can be contradicted if the branch is allowed to bind one
    ;; or more gamma-introduced proof variables. We explicitly reject closures
    ;; that would require binding user-level answer variables; only proof-time
    ;; variables may witness the contradiction here.
    [(fresh [lit left right sigma-mid new-bindings binding rest step-proof]
       (subst/subst-formulao fml env lit)
       (== (list 'neq left right) lit)
       (equality/unify-termo left right sigma sigma-mid step-proof)
       (appendo new-bindings sigma sigma-mid)
       (== (lcons binding rest) new-bindings)
       (support/proof-bindingso new-bindings proof-vars)
       (== sigma-mid sigma-out)
       (support/prune-contradictory-neqso neqs sigma-mid neqs-out)
       (== (list 'neq-close step-proof) proof))]
    ;; Constructor clashes are already true in the free-constructor theory.
    ;; Discharge them as successful branch progress rather than carrying them
    ;; as delayed symbolic obligations.
    [(fresh [lit left right next rest next-fuel prf]
       (subst/subst-formulao fml env lit)
       (== (list 'neq left right) lit)
       (support/rigid-different-termo left right sigma)
       (== (lcons next rest) unexpanded)
       (== (list 'neq-rigid prf) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo next
                               rest
                               lits
                               env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))]
    ;; Otherwise retain the disequality as a delayed symbolic obligation.
    [(fresh [lit left right next rest next-fuel prf]
       (subst/subst-formulao fml env lit)
       (== (list 'neq left right) lit)
       (== (lcons next rest) unexpanded)
       (== (list 'neq-store prf) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo next
                               rest
                               lits
                               env
                               proof-vars
                               sigma
                               sigma-out
                               (lcons [left right] neqs)
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))]

    ;; ================================================================
    ;; Positive atoms
    ;; ================================================================
    ;;
    ;; First try ordinary complementary closure against a saved negative atom.
    ;; Failing that, Fitting's Procedure Call Rule may open a subsidiary
    ;; tableau for the body of the matching clause. If neither applies yet, the
    ;; atom is saved on the branch for possible later equality-triggered use.
    [(fresh [lit atom]
       (subst/subst-formulao fml env lit)
       (== (list 'pos atom) lit)
       (support/complementary-lito lit lits sigma sigma-out proof)
       (support/prune-contradictory-neqso neqs sigma-out neqs-out))]
    ;; Positive procedure call: only admissible once equality has walked the
    ;; arguments into the object language `L`.
    [(fresh [lit atom walked-atom relation args call-env body negated-body next-fuel subproof]
       (subst/subst-formulao fml env lit)
       (== (list 'pos atom) lit)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clauseo prog walked-atom call-env body negated-body)
       (== (list 'pos-call subproof) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo body
                               '()
                               '()
                               call-env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               subproof))]
    ;; Save the positive atom if it cannot close or call immediately.
    [(fresh [lit atom next rest next-fuel prf]
       (subst/subst-formulao fml env lit)
       (== (list 'pos atom) lit)
       (== (lcons next rest) unexpanded)
       (== (list 'savefml prf) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo next
                               rest
                               (lcons lit lits)
                               env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))]

    ;; ================================================================
    ;; Negative atoms
    ;; ================================================================
    ;;
    ;; Symmetric to the positive case, except that the procedure call proves
    ;; the NNF negation of the clause body.
    [(fresh [lit atom]
       (subst/subst-formulao fml env lit)
       (== (list 'neg atom) lit)
       (support/complementary-lito lit lits sigma sigma-out proof)
       (support/prune-contradictory-neqso neqs sigma-out neqs-out))]
    ;; Negative procedure call: this is Fitting's Part 2 operationalized over
    ;; the compiled clause's precomputed `negated-body`.
    [(fresh [lit atom walked-atom relation args call-env body negated-body alternatives negated-alternatives guarded-alternatives subproof]
       (subst/subst-formulao fml env lit)
       (== (list 'neg atom) lit)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clause-with-guarded-alternativeso
         prog walked-atom call-env body negated-body alternatives negated-alternatives guarded-alternatives)
       (fresh [first-alternative second-alternative remaining-alternatives]
         (== (lcons first-alternative
                    (lcons second-alternative remaining-alternatives))
             alternatives))
       (== (list 'neg-call-guarded-alt subproof) proof)
       (close-one-guarded-alternativeo guarded-alternatives
                                       call-env
                                       proof-vars
                                       sigma
                                       sigma-out
                                       neqs
                                       neqs-out
                                       prog
                                       gamma-terms
                                       fuel
                                       subproof))]
    [(fresh [lit atom walked-atom relation args call-env body negated-body alternatives negated-alternatives subproof]
       (subst/subst-formulao fml env lit)
       (== (list 'neg atom) lit)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clause-with-alternativeso
         prog walked-atom call-env body negated-body alternatives negated-alternatives)
       (fresh [first-alternative second-alternative remaining-alternatives]
         (== (lcons first-alternative
                    (lcons second-alternative remaining-alternatives))
             alternatives))
       (== (list 'neg-call-alt subproof) proof)
       (close-one-formulao negated-alternatives
                           call-env
                           proof-vars
                           sigma
                           sigma-out
                           neqs
                           neqs-out
                           prog
                           gamma-terms
                           fuel
                           subproof))]
    [(fresh [lit atom walked-atom relation args call-env body negated-body next-fuel subproof]
       (subst/subst-formulao fml env lit)
       (== (list 'neg atom) lit)
       (equality/walk-atomo atom sigma walked-atom)
       (== (lcons 'app (lcons relation args)) walked-atom)
       (support/l-ground-term*o args)
       (program/call-clauseo prog walked-atom call-env body negated-body)
       (== (list 'neg-call subproof) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo negated-body
                               '()
                               '()
                               call-env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               subproof))]
    ;; Save the negative atom if it cannot yet close or call.
    [(fresh [lit atom next rest next-fuel prf]
       (subst/subst-formulao fml env lit)
       (== (list 'neg atom) lit)
       (== (lcons next rest) unexpanded)
       (== (list 'savefml prf) proof)
       (support/step-fuelo fuel next-fuel)
       (recursive-prove-stateo next
                               rest
                               (lcons lit lits)
                               env
                               proof-vars
                               sigma
                               sigma-out
                               neqs
                               neqs-out
                               prog
                               gamma-terms
                               next-fuel
                               prf))])))

(defn prove-stateo
  "Backward-compatible current-formula wrapper over the fair agenda kernel.

   Existing callers still pass one focused formula plus the rest of the branch
   work, but the internal engine now treats them as one explicit agenda."
  [fml unexpanded lits env proof-vars sigma sigma-out neqs neqs-out prog gamma-terms fuel proof]
  (close-agendao
    (lcons fml unexpanded)
    lits
    env
    proof-vars
    sigma
    sigma-out
    neqs
    neqs-out
    prog
    gamma-terms
    fuel
    proof))

(defn proveo
  "Public pure-kernel relation.

   This is the ordinary proof surface: it exposes only proof terms, not the
   intermediate equality substitution or delayed disequalities. In other words,
   the kernel is relational internally, but this wrapper deliberately hides the
   answer-oriented state that the overlay later exports explicitly."
  ([fml unexpanded lits env proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() nil proof)))
  ([fml unexpanded lits env fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out nil '() fuel proof))))

(defn prove-programo
  "Pure kernel relation with an explicit compiled program for procedure calls.

   This is the direct analogue of `proveo` when the tableau may invoke
   Proflog clauses through Fitting's Procedure Call Rule."
  ([fml unexpanded lits env prog proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml unexpanded lits env '() '() sigma-out '() neqs-out prog (gamma/closed-terms-for-fuel prog nil) nil proof)))
  ([fml unexpanded lits env prog fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml
                   unexpanded
                   lits
                   env
                   '()
                   '()
                   sigma-out
                   '()
                   neqs-out
                   prog
                   (gamma/closed-terms-for-fuel prog fuel)
                   fuel
                   proof)))
  ([fml unexpanded lits env prog gamma-terms fuel proof]
   (fresh [sigma-out neqs-out]
     (prove-stateo fml
                   unexpanded
                   lits
                   env
                   '()
                   '()
                   sigma-out
                   '()
                   neqs-out
                   prog
                   gamma-terms
                   fuel
                   proof))))

(defn prove
  "Return up to `n` proof terms closing the given greenfield formula.

   This is a convenience wrapper for theorem-proving style use: start with an
   empty branch state and ask core.logic for proof witnesses.

   Ground pure-propositional formulas enter the ADR-0023 propositional
   component first. Ground equality-free first-order formulas enter the
   ADR-0024 first-order component. Broader theorem formulas continue through
   the full Proflog kernel, and `prove-program` always remains on the
   program-aware full kernel."
  ([fml] (prove fml 1))
  ([fml n]
   (case (formula-profile/profile fml)
     :pure-propositional (propositional/prove fml n)
     :equality-free-first-order (first-order/prove fml n)
     (run n [proof]
       (proveo fml '() '() '() proof))))
  ([fml n fuel]
   (case (formula-profile/profile fml)
     :pure-propositional (propositional/prove fml n fuel)
     :equality-free-first-order (first-order/prove fml n fuel)
     (run n [proof]
       (proveo fml '() '() '() fuel proof)))))

(defn prove-program
  "Return up to `n` proof terms closing `fml` relative to `prog`.

  This keeps the program explicit and otherwise starts from the empty kernel
  state, mirroring the paper's use of a fixed Proflog program during proof
  search."
  ([prog fml n]
   (let [profiled (equality-fragment/prove-program-host prog fml n)]
     (if (seq profiled)
       profiled
       (run n [proof]
            (prove-programo fml '() '() '() prog proof)))))
  ([prog fml n fuel]
   (let [profiled (equality-fragment/prove-program-host prog fml n fuel)]
     (if (seq profiled)
       profiled
       (run n [proof]
            (prove-programo fml '() '() '() prog fuel proof))))))
