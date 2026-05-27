;; ============================================================================
;; αleanTAP-E: Classical First-Order Logic Tableau Prover with Equality
;; ============================================================================
;;
;; A direct extension of αleanTAP (Near, Byrd, Friedman, ICLP 2008) with
;; the equality rules of Fitting (§5, "Tableaus for Logic Programming", 1994).
;;
;; The base prover implements classical semantic tableaux (Smullyan 1968):
;;   α-rule (conjunction), β-rule (disjunction), γ-rule (universal),
;;   δ-rule (existential), and complementary closure.
;;
;; The equality extension adds rules for weak Herbrand models, in which
;; every function symbol is injective and distinct function symbols have
;; disjoint ranges (Fitting, Definition 3.1):
;;
;;   Reflexivity    : (neq t t) closes immediately
;;   Free Closure   : (eq f(...) g(...)) with f ≠ g closes (Fitting §5.1-3)
;;   Eq/Neq closure : (eq t1 t2) with (neq t1 t2) already in lits closes
;;   One-One Rule   : (eq f(t...) f(u...)) decomposes into sub-equalities
;;   Paramodulation : rewrite pos/neg literals via branch equalities to close
;;   NEQ via eqs    : rewrite t1 → t2 via branch equalities to close (neq t1 t2)
;;
;; FORMULA GRAMMAR (NNF — all negations pushed to literals):
;;
;;   Fml → (and Fml Fml)            — conjunction   (α)
;;        | (or Fml Fml)             — disjunction   (β)
;;        | (forall (tie Nom Fml))   — universal     (γ)
;;        | (exists (tie Nom Fml))   — existential   (δ)
;;        | (pos Term)               — positive atom
;;        | (neg Term)               — negated atom
;;        | (eq Term Term)           — equality
;;        | (neq Term Term)          — disequality (negated equality)
;;
;; TERM GRAMMAR:
;;
;;   Term → (var Nom)               — variable (Nom from nominal logic)
;;         | (app Symbol Term*)     — function/constant application
;;         | (par Nom)              — δ-parameter (Skolem witness, atomic)
;;
;;   Constants are nullary: (app 'zero), (app 'nil), etc.
;;   Functions:             (app 's (app 'zero)), (app 'cons x xs), etc.
;;
;; PROOF STEPS:
;;
;;   (conj . prf)      — conjunction rule
;;   (split prf1 prf2) — disjunction rule (both branches close)
;;   (univ . prf)      — universal instantiation
;;   (exist . prf)     — existential witness introduction (δ-rule)
;;   (close)           — complementary closure: A and ¬A in lits
;;   (refl-close)      — reflexivity: (neq t t)
;;   (free-close)      — free closure: (eq f(...) g(...)), f ≠ g
;;   (eq-neq-close)    — eq/neq complement: (eq t1 t2) with (neq t1 t2) in lits
;;   (decompose . prf) — one-one decomposition: same-head eq → sub-equalities
;;   (para-close)      — paramodulation: rewrite via branch equalities
;;   (neq-eq-close)    — neq closed by rewriting t1→t2 via branch equalities
;;   (savefml . prf)   — literal saved, processing continues
;;
;; ============================================================================

(ns cljtap.alphaleantap-e
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer :all
             :rename {appendo logic-appendo
                      membero logic-membero}]
            [clojure.core.logic.nominal :refer [tie hash]]))

;; ----------------------------------------------------------------------------
;; nom macro
;;
;; In core.logic.nominal 1.0.1, `nom` is a constructor function (not a macro).
;; We define our own macro wrapping the nominal namespace's `fresh`, which
;; creates Nom objects — rigid, globally unique atomic names.
;;
;;   (nom a goal)        — introduce fresh nom a, run goal in scope
;;   (nom a b c goal)    — introduce fresh noms a b c, run goal in scope
;; ----------------------------------------------------------------------------
(defmacro nom [& args]
  (let [syms (butlast args)
        body (last args)]
    `(clojure.core.logic.nominal/fresh [~@syms] ~body)))

;; ============================================================================
;; Part 1: Utility Relations
;; ============================================================================

(defn appendo [l s out]
  (conde
    [(== '() l) (== s out)]
    [(fresh [a d r]
       (== (lcons a d) l)
       (== (lcons a r) out)
       (appendo d s r))]))

(defn membero [x ls]
  (fresh [a d]
    (== (lcons a d) ls)
    (conde
      [(== a x)]
      [(membero x d)])))

;; ============================================================================
;; Part 2: Substitution
;;
;; Replaces (var nom) occurrences in terms and literals using an environment
;; (a list of [nom value] pairs). The γ-rule builds this environment as it
;; instantiates universal quantifiers.
;; ============================================================================

(defn lookupo [a env out]
  (fresh [rest]
    (conde
      [(== (lcons [a out] rest) env)]
      [(fresh [pair]
         (== (lcons pair rest) env)
         (lookupo a rest out))])))

(declare subst-term*o)

(defn subst-termo [t env out]
  (conde
    [(fresh [a]
       (== ['var a] t)
       (lookupo a env out))]
    [(fresh [p]
       (== ['par p] t)
       (== ['par p] out))]
    [(fresh [f args args-out]
       (== (lcons 'app (lcons f args)) t)
       (== (lcons 'app (lcons f args-out)) out)
       (subst-term*o args env args-out))]))

(defn subst-term*o [ts env out]
  (conde
    [(== '() ts) (== '() out)]
    [(fresh [t rest t-out rest-out]
       (== (lcons t rest) ts)
       (== (lcons t-out rest-out) out)
       (subst-termo t env t-out)
       (subst-term*o rest env rest-out))]))

(defn subst-lito [lit env out]
  (conde
    [(fresh [t t-out]
       (== ['pos t] lit) (== ['pos t-out] out)
       (subst-termo t env t-out))]
    [(fresh [t t-out]
       (== ['neg t] lit) (== ['neg t-out] out)
       (subst-termo t env t-out))]
    [(fresh [t1 t2 r1 r2]
       (== ['eq t1 t2] lit) (== ['eq r1 r2] out)
       (subst-termo t1 env r1)
       (subst-termo t2 env r2))]
    [(fresh [t1 t2 r1 r2]
       (== ['neq t1 t2] lit) (== ['neq r1 r2] out)
       (subst-termo t1 env r1)
       (subst-termo t2 env r2))]))

;; ============================================================================
;; Part 3: Equality Reasoning
;;
;; Implements Fitting §5 rules for weak Herbrand models.
;; ============================================================================

;; --- Free Closure ---
;;
;; In a weak Herbrand model, distinct function symbols of L have disjoint
;; ranges. So f(t1,...,tn) = g(u1,...,um) is unsatisfiable when f ≠ g.
;;
;; Soundness guard: both heads must be concrete Clojure symbols — not logic
;; variables, which could still unify with anything.
(defn free-closureo [t1 t2]
  (fresh [f g args1 args2]
    (== (lcons 'app (lcons f args1)) t1)
    (== (lcons 'app (lcons g args2)) t2)
    (!= f g)))

;; --- Collect Equality Pairs ---
;;
;; Gather all [lhs rhs] pairs from (eq ...) literals on the branch.
;; Both directions [t1 t2] and [t2 t1] are produced for each equality,
;; enabling left-to-right and right-to-left rewriting.
(defn collect-eqso [lits eqs]
  (conde
    [(== '() lits) (== '() eqs)]
    [(fresh [t1 t2 rest rest-eqs]
       (== (lcons ['eq t1 t2] rest) lits)
       (collect-eqso rest rest-eqs)
       (== (lcons [t1 t2] (lcons [t2 t1] rest-eqs)) eqs))]
    [(fresh [lit rest]
       (== (lcons lit rest) lits)
       (fresh [tag b]
         (conde
           [(== ['pos tag] lit)]
           [(== ['neg tag] lit)]
           [(== ['neq tag b] lit)]))
       (collect-eqso rest eqs))]))

;; --- Term Rewriting ---
;;
;; Rewrite an occurrence of lhs → rhs anywhere inside term t.
;; Non-deterministic: may rewrite at root or at any argument position.
(declare rewrite-term*o)

(defn rewrite-termo [t lhs rhs out]
  (conde
    [(== t lhs) (== out rhs)]
    [(fresh [f args args-out]
       (== (lcons 'app (lcons f args)) t)
       (== (lcons 'app (lcons f args-out)) out)
       (rewrite-term*o args lhs rhs args-out))]))

(defn rewrite-term*o [ts lhs rhs out]
  (fresh [t rest]
    (== (lcons t rest) ts)
    (conde
      [(fresh [t-out]
         (rewrite-termo t lhs rhs t-out)
         (== (lcons t-out rest) out))]
      [(fresh [rest-out]
         (== (lcons t rest-out) out)
         (rewrite-term*o rest lhs rhs rest-out))])))

;; Rewrite the term inside a pos/neg literal.
(defn rewrite-lito [lit lhs rhs out]
  (conde
    [(fresh [t t-out]
       (== ['pos t] lit) (== ['pos t-out] out)
       (rewrite-termo t lhs rhs t-out))]
    [(fresh [t t-out]
       (== ['neg t] lit) (== ['neg t-out] out)
       (rewrite-termo t lhs rhs t-out))]))

;; --- Paramodulation (Equality-Aware Membership) ---
;;
;; Check whether `target` is in `lits` after ≥0 rewriting steps using
;; equality pairs from `eqs`. Implements Fitting's Substitutivity Rule:
;; if t = u is on the branch and X is on the branch, X' (with t replaced
;; by u) is derivable.
(defn eq-membero [target lits eqs]
  (conde
    [(membero target lits)]
    [(fresh [pair lhs rhs rewritten]
       (membero pair eqs)
       (== [lhs rhs] pair)
       (rewrite-lito target lhs rhs rewritten)
       (eq-membero rewritten lits eqs))]))

;; --- NEQ Closure via Equalities ---
;;
;; Close (neq t1 t2) by showing t1 can be rewritten to t2 using branch
;; equalities, yielding the contradiction (neq t t).
(defn neq-closureo [t1 t2 eqs]
  (conde
    [(== t1 t2)]
    [(fresh [pair lhs rhs t1-rw]
       (membero pair eqs)
       (== [lhs rhs] pair)
       (rewrite-termo t1 lhs rhs t1-rw)
       (neq-closureo t1-rw t2 eqs))]))

;; --- One-One Decomposition ---
;;
;; Build a conjunction of pairwise sub-equalities from two argument lists.
;; f(t1,...,tn) = f(u1,...,un)  →  (and (eq t1 u1) ... (eq tn un))
;;
;; This corresponds to Fitting's One-One Rule: since f is injective,
;; f(t) = f(u) implies t = u.
(defn decompose-eq-argso [args1 args2 result]
  (conde
    [(fresh [a1 a2]
       (== (lcons a1 '()) args1)
       (== (lcons a2 '()) args2)
       (== ['eq a1 a2] result))]
    [(fresh [a1 a2 r1 r2 rest-eq]
       (== (lcons a1 r1) args1)
       (== (lcons a2 r2) args2)
       (fresh [_ __] (== (lcons _ __) r1))
       (decompose-eq-argso r1 r2 rest-eq)
       (== ['and ['eq a1 a2] rest-eq] result))]))

;; ============================================================================
;; Part 4: The Main Prover
;; ============================================================================

(defn proveo [fml unexp lits env proof]
  (conde

    ;; ========================================================================
    ;; α-rule: Conjunction
    ;; Push e2 onto the unexpanded stack; process e1 next.
    ;; Both must eventually close on the same branch.
    ;; ========================================================================
    [(fresh [e1 e2 prf]
       (== ['and e1 e2] fml)
       (== (lcons 'conj prf) proof)
       (proveo e1 (lcons e2 unexp) lits env prf))]

    ;; ========================================================================
    ;; β-rule: Disjunction
    ;; Split into two independent branches; both must close.
    ;; ========================================================================
    [(fresh [e1 e2 prf1 prf2]
       (== ['or e1 e2] fml)
       (== ['split prf1 prf2] proof)
       (proveo e1 unexp lits env prf1)
       (proveo e2 unexp lits env prf2))]

    ;; ========================================================================
    ;; γ-rule: Universal Quantifier
    ;; Bind the nom v to a fresh logic variable x.  Re-enqueue the formula
    ;; so it may be instantiated again with a different variable if needed.
    ;; ========================================================================
    [(nom v
       (fresh [x body unexp1 prf]
         (== ['forall (tie v body)] fml)
         (== (lcons 'univ prf) proof)
         (appendo unexp (list fml) unexp1)
         (proveo body unexp1 lits (lcons [v x] env) prf)))]

    ;; ========================================================================
    ;; δ-rule: Existential Quantifier
    ;; Introduce a single rigid Skolem witness: a fresh nom p wrapped as the
    ;; term (par p) — a dedicated parameter form, structurally distinct from
    ;; (app f ...) constructors.  Unlike the γ-rule, the formula is NOT
    ;; re-enqueued — existentials are witnessed exactly once.
    ;;
    ;; The witness (par p) is a ground, globally unique term that cannot
    ;; unify with other ground terms (distinct noms are not equal).  It CAN
    ;; unify with logic variables introduced by the γ-rule.  The (par p) form
    ;; does not match (lcons 'app ...), so free-closureo never fires on it.
    ;; ========================================================================
    [(nom v
       (nom p
         (fresh [body prf]
           (== ['exists (tie v body)] fml)
           (== (lcons 'exist prf) proof)
           (proveo body unexp lits (lcons [v ['par p]] env) prf))))]

    ;; ========================================================================
    ;; Literal cases
    ;; Substitute any environment bindings into fml, yielding the ground
    ;; literal `lit`.  Then try every applicable closure rule, or save lit
    ;; and continue with the next unexpanded formula.
    ;; ========================================================================
    [(fresh [lit]
       (subst-lito fml env lit)
       (conde

         ;; ---- Complementary closure ----------------------------------------
         ;; (pos t) closes with (neg t) in lits, and vice versa.
         [(fresh [t neg]
            (== ['close] proof)
            (conde
              [(== ['pos t] lit) (== ['neg t] neg)]
              [(== ['neg t] lit) (== ['pos t] neg)])
            (membero neg lits))]

         ;; ---- Reflexivity ---------------------------------------------------
         ;; (neq t t) is an immediate contradiction.
         ;; With free variables: t1 and t2 unify, revealing the self-inequality.
         [(fresh [t]
            (== ['neq t t] lit)
            (== ['refl-close] proof))]

         ;; ---- Free Closure (Fitting §5) ------------------------------------
         ;; (eq f(...) g(...)) with f ≠ g as concrete symbols of L.
         ;; Distinct function symbols have disjoint ranges in every weak
         ;; Herbrand model, so this equality is unsatisfiable.
         [(fresh [t1 t2]
            (== ['eq t1 t2] lit)
            (== ['free-close] proof)
            (free-closureo t1 t2))]

         ;; ---- Eq/Neq Complementary Closure ---------------------------------
         ;; (eq t1 t2) contradicts (neq t1 t2) or (neq t2 t1) already in lits.
         [(fresh [t1 t2]
            (== ['eq t1 t2] lit)
            (== ['eq-neq-close] proof)
            (conde
              [(membero ['neq t1 t2] lits)]
              [(membero ['neq t2 t1] lits)]))]

         ;; ---- One-One Decomposition (Fitting §5) ---------------------------
         ;; (eq (app f t1...tn) (app f u1...un)) with same concrete head f
         ;; and at least one argument.  By injectivity of f, decompose into
         ;; a conjunction of sub-equalities and continue proving that.
         [(fresh [f args1 args2 decomposed prf]
            (== ['eq (lcons 'app (lcons f args1))
                     (lcons 'app (lcons f args2))] lit)
            (fresh [_ __] (== (lcons _ __) args1))
            (decompose-eq-argso args1 args2 decomposed)
            (== (lcons 'decompose prf) proof)
            (proveo decomposed unexp lits env prf))]

         ;; ---- Paramodulation (Substitutivity) ------------------------------
         ;; (pos t) or (neg t): try to find the complement in lits after
         ;; rewriting using branch equalities. Implements Fitting's
         ;; Substitutivity Rule: if t = u and X are on the branch, X[t/u]
         ;; is derivable, potentially revealing a complementary literal.
         [(fresh [t neg eqs]
            (== ['para-close] proof)
            (conde
              [(== ['pos t] lit) (== ['neg t] neg)]
              [(== ['neg t] lit) (== ['pos t] neg)])
            (collect-eqso lits eqs)
            (eq-membero neg lits eqs))]

         ;; ---- NEQ closure via equalities -----------------------------------
         ;; (neq t1 t2): if branch equalities can rewrite t1 to t2 (or vice
         ;; versa), the inequality (neq t t) is obtained, which closes.
         [(fresh [t1 t2 eqs]
            (== ['neq t1 t2] lit)
            (== ['neq-eq-close] proof)
            (collect-eqso lits eqs)
            (neq-closureo t1 t2 eqs))]

         ;; ---- Save literal and continue ------------------------------------
         ;; No closure rule applies yet.  Save lit to lits and process the
         ;; next unexpanded formula from the stack.
         [(fresh [next rest prf]
            (== (lcons next rest) unexp)
            (== (lcons 'savefml prf) proof)
            (proveo next rest (lcons lit lits) env prf))]))]))

;; ============================================================================
;; Part 5: Top-Level Interface
;; ============================================================================

(defn prove
  "Find closed tableau(x) for formula `fml` (must be in NNF).
   Returns a list of up to n proof terms, empty if unprovable within n tries."
  ([fml] (prove fml 1))
  ([fml n]
   (run n [proof]
     (proveo fml '() '() '() proof))))
