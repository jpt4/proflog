(ns proflog.equality
  "Constraint-style free-constructor equality helpers for the greenfield kernel.

   For a reader of Fitting's Proflog paper, this namespace is the equality
   theory that sits underneath the tableau rules.

   The guiding semantic picture is a free-constructor term algebra with two
   explicit kinds of object-language unknown:

   - `(var nom)` for gamma-introduced proof variables,
   - `(par nom)` for rigid delta parameters.

   Instead of encoding equality by rewriting branch literals in place, the
   greenfield prover keeps an explicit substitution `sigma` and asks the
   relations in this file to do four jobs:

   1. walk terms through the current equality knowledge,
   2. detect immediate equality contradictions,
   3. extend the equality substitution when two terms can be unified,
   4. and re-check saved atoms or disequalities against the refined branch
      state.

   The result is still relational, but it is more algebraic than the legacy
   branch-rewriting presentation: congruence comes from shared branch state
   rather than from paramodulating formulas themselves."
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer [!= == conde fresh lcons membero]]))

;; Reading guide
;; -------------
;;
;; The relations below are easiest to read in four layers:
;;
;; 1. explicit substitution operations (`lookupo`, `walko`, `walk*o`),
;; 2. occurs / absence checks used to keep bindings sound,
;; 3. structural comparison and contradiction detection,
;; 4. unification and saved-branch rechecks.
;;
;; This file does not know anything about connectives or quantifier rules. It
;; only knows how terms, atoms, and symbolic disequalities behave once the
;; tableau kernel asks an equality question.

;; ---------------------------------------------------------------------------
;; Explicit substitution lookup
;; ---------------------------------------------------------------------------
;;
;; `sigma` and the disequality environment are ordinary association lists. We
;; keep lookup relational so the equality engine can still run in partially
;; specified directions rather than immediately collapsing into host-side map
;; operations.

(defn lookupo
  "Relational lookup in an explicit substitution or disequality environment.

   This is the low-level association-list lookup used throughout the equality
   engine. It is intentionally tiny and relational: one branch succeeds when
   the head binding matches, and the other searches the tail."
  [binding-nom env value]
  (fresh [rest]
    (conde
      [(== (lcons [binding-nom value] rest) env)]
      [(fresh [pair]
         (== (lcons pair rest) env)
         (lookupo binding-nom rest value))])))

(defn unboundo
  "Succeed when `binding-nom` does not appear in `env`.

   This is the explicit-substitution analogue of asking whether a variable is
   still free at the root of the current equality state."
  [binding-nom env]
  (conde
    [(== '() env)]
    [(fresh [other value rest]
       (== (lcons [other value] rest) env)
       (!= other binding-nom)
       (unboundo binding-nom rest))]))

(declare walko walk*o walk-term*o walk-atomo
         absent-termo absent-term*o
         absent-paro absent-par*o
         occurs-termo occurs-term*o
         same-termo same-term*o
         unify-termo unify-term*o
         eq-contradictiono eq-contradiction-term*o)

;; ---------------------------------------------------------------------------
;; Walking terms through the current equality state
;; ---------------------------------------------------------------------------
;;
;; Fitting's rules are easiest to state on already-normalized terms:
;; constructor clashes, reflexivity, and complementary atom closure all depend
;; on what the branch currently knows about equalities. The "walk" relations
;; provide that normalization relative to the explicit substitution `sigma`.

(defn walko
  "Walk the root of `term` through the explicit equality substitution `sigma`.

   Only the root is normalized here:

   - a bound `(var ...)` or `(par ...)` is followed recursively through
     `sigma`,
   - an unbound variable or parameter is left at the root,
   - and constructor applications are returned unchanged at the root.

   This is the usual miniKanren-style walk operation, specialized to the
   Proflog term language."
  [term sigma out]
  (conde
    ;; Gamma variable already bound in the equality substitution.
    [(fresh [binding-nom value]
       (== (list 'var binding-nom) term)
       (lookupo binding-nom sigma value)
       (walko value sigma out))]
    ;; Gamma variable still free at the root.
    [(fresh [binding-nom]
       (== (list 'var binding-nom) term)
       (unboundo binding-nom sigma)
       (== term out))]
    ;; Delta parameter already bound by equality.
    [(fresh [binding-nom value]
       (== (list 'par binding-nom) term)
       (lookupo binding-nom sigma value)
       (walko value sigma out))]
    ;; Delta parameter still rigid at the root.
    [(fresh [binding-nom]
       (== (list 'par binding-nom) term)
       (== term out))]
    ;; Constructor applications are already at a stable root.
    [(fresh [head args]
       (== (lcons 'app (lcons head args)) term)
       (== term out))]))

(defn walk*o
  "Deeply walk a term through `sigma`, normalizing all reachable bindings.

   Where `walko` normalizes only the root, `walk*o` recursively normalizes the
   whole term. This is what the kernel uses before checking L-groundness or
   before reopening saved calls under new equalities."
  [term sigma out]
  (fresh [root]
    (walko term sigma root)
    (conde
      ;; Unbound gamma variable.
      [(fresh [binding-nom]
         (== (list 'var binding-nom) root)
         (== root out))]
      ;; Unbound rigid parameter.
      [(fresh [binding-nom]
         (== (list 'par binding-nom) root)
         (== root out))]
      ;; Constructor term: recursively normalize all arguments.
      [(fresh [head args args-out]
         (== (lcons 'app (lcons head args)) root)
         (== (lcons 'app (lcons head args-out)) out)
         (walk-term*o args sigma args-out))])))

(defn walk-term*o
  "Deep walk over an argument list.

   This is the list-valued companion to `walk*o`."
  [terms sigma out]
  (conde
    [(== '() terms) (== '() out)]
    [(fresh [head tail head-out tail-out]
       (== (lcons head tail) terms)
       (== (lcons head-out tail-out) out)
       (walk*o head sigma head-out)
       (walk-term*o tail sigma tail-out))]))

(defn walk-atomo
  "Deep walk over one atomic application.

   Procedure calls and complementary literal closure operate on whole atoms, so
   this relation normalizes every argument of an atomic application."
  [atom sigma out]
  (fresh [head args args-out]
    (== (lcons 'app (lcons head args)) atom)
    (== (lcons 'app (lcons head args-out)) out)
    (walk-term*o args sigma args-out)))

;; ---------------------------------------------------------------------------
;; Occurs / absence checks
;; ---------------------------------------------------------------------------
;;
;; The greenfield equality engine keeps separate soundness checks for proof
;; variables and rigid parameters. Both are needed:
;;
;; - proof variables require an occurs check to avoid cyclic substitutions,
;; - rigid parameters require an analogous "absent parameter" check so delta
;;   witnesses do not collapse into terms that already contain themselves.

(defn absent-termo
  "Succeed when `(var binding-nom)` does not occur anywhere in `term`.

   This is the proof-variable occurs-check phrased positively as absence."
  [binding-nom term sigma]
  (fresh [root]
    (walko term sigma root)
    (conde
      ;; Different free proof variable at the root.
      [(fresh [other]
         (== (list 'var other) root)
         (!= other binding-nom))]
      ;; Parameters never witness a proof-variable occurrence.
      [(fresh [parameter-nom]
         (== (list 'par parameter-nom) root))]
      ;; Recurse structurally through constructor arguments.
      [(fresh [head args]
         (== (lcons 'app (lcons head args)) root)
         (absent-term*o binding-nom args sigma))])))

(defn absent-term*o
  "Succeed when `binding-nom` is absent from every term in `terms`.

   List-valued companion to `absent-termo`."
  [binding-nom terms sigma]
  (conde
    [(== '() terms)]
    [(fresh [head tail]
       (== (lcons head tail) terms)
       (absent-termo binding-nom head sigma)
       (absent-term*o binding-nom tail sigma))]))

(defn absent-paro
  "Succeed when `(par binding-nom)` does not occur anywhere in `term`.

   This is the delta-parameter analogue of `absent-termo`. A parameter is
   rigid, but it still must not be bound to a term that already contains it."
  [binding-nom term sigma]
  (fresh [root]
    (walko term sigma root)
    (conde
      ;; Proof variables cannot contain a rigid parameter at the root.
      [(fresh [other]
         (== (list 'var other) root))]
      ;; Different rigid parameter at the root.
      [(fresh [other]
         (== (list 'par other) root)
         (!= other binding-nom))]
      ;; Recurse structurally through constructor arguments.
      [(fresh [head args]
         (== (lcons 'app (lcons head args)) root)
         (absent-par*o binding-nom args sigma))])))

(defn absent-par*o
  "Succeed when `(par binding-nom)` is absent from every term in `terms`.

   List-valued companion to `absent-paro`."
  [binding-nom terms sigma]
  (conde
    [(== '() terms)]
    [(fresh [head tail]
       (== (lcons head tail) terms)
       (absent-paro binding-nom head sigma)
       (absent-par*o binding-nom tail sigma))]))

(defn occurs-termo
  "Succeed when `(var binding-nom)` occurs somewhere inside `term`.

   Negative use of the proof-variable occurs check. `eq-contradictiono` uses
   this to recognize an impossible equality immediately."
  [binding-nom term sigma]
  (fresh [root]
    (walko term sigma root)
    (conde
      [(== (list 'var binding-nom) root)]
      [(fresh [head args]
         (== (lcons 'app (lcons head args)) root)
         (occurs-term*o binding-nom args sigma))])))

(defn occurs-term*o
  "Succeed when `(var binding-nom)` occurs in one of the `terms`.

   List-valued companion to `occurs-termo`."
  [binding-nom terms sigma]
  (fresh [head tail]
    (== (lcons head tail) terms)
    (conde
      [(occurs-termo binding-nom head sigma)]
      [(occurs-term*o binding-nom tail sigma)])))

;; ---------------------------------------------------------------------------
;; Structural equality and contradiction detection
;; ---------------------------------------------------------------------------
;;
;; Once terms are walked, the kernel needs three slightly different notions:
;;
;; - `same-termo`: already equal under the current substitution,
;; - `eq-contradictiono`: impossible to make equal in the free algebra,
;; - `unify-termo`: extend `sigma` so they do become equal.

(defn same-termo
  "Structural equality on walked terms without introducing new bindings.

   This is the read-only equality test used for reflexive disequality closure
   and for recognizing when a saved disequality has become false."
  [left right sigma]
  (fresh [left-root right-root]
    (walko left sigma left-root)
    (walko right sigma right-root)
    (conde
      ;; Same free proof variable.
      [(fresh [binding-nom]
         (== (list 'var binding-nom) left-root)
         (== (list 'var binding-nom) right-root))]
      ;; Same rigid parameter.
      [(fresh [binding-nom]
         (== (list 'par binding-nom) left-root)
         (== (list 'par binding-nom) right-root))]
      ;; Same constructor head and equal arguments.
      [(fresh [head left-args right-args]
         (== (lcons 'app (lcons head left-args)) left-root)
         (== (lcons 'app (lcons head right-args)) right-root)
         (same-term*o left-args right-args sigma))])))

(defn same-term*o
  "Structural equality on walked term lists.

   List-valued companion to `same-termo`."
  [left right sigma]
  (conde
    [(== '() left) (== '() right)]
    [(fresh [left-head left-tail right-head right-tail]
       (== (lcons left-head left-tail) left)
       (== (lcons right-head right-tail) right)
       (same-termo left-head right-head sigma)
       (same-term*o left-tail right-tail sigma))]))

(defn eq-contradictiono
  "Succeed with a proof tag when `left = right` is impossible under `sigma`.

   This relation encodes the free-constructor contradiction side of Fitting's
   equality treatment:

   - occurs failure for proof variables,
   - constructor clash for different heads,
   - recursive decomposition for same-head applications until an impossible
     sub-equation is found."
  [left right sigma proof]
  (fresh [left-root right-root]
    (walko left sigma left-root)
    (walko right sigma right-root)
    (conde
      ;; Proof-variable occurs contradiction: x = f(...x...) is impossible.
      [(fresh [binding-nom]
         (== (list 'var binding-nom) left-root)
         (occurs-termo binding-nom right-root sigma)
         (== '(occurs-close) proof))]
      [(fresh [binding-nom]
         (== (list 'var binding-nom) right-root)
         (occurs-termo binding-nom left-root sigma)
         (== '(occurs-close) proof))]
      ;; Distinct constructor heads can never denote the same free-algebra
      ;; element.
      [(fresh [left-head left-args right-head right-args]
         (== (lcons 'app (lcons left-head left-args)) left-root)
         (== (lcons 'app (lcons right-head right-args)) right-root)
         (!= left-head right-head)
         (== '(free-close) proof))]
      ;; Same constructor head: descend pairwise into the arguments until a
      ;; contradictory sub-equation is found.
      [(fresh [head left-args right-args subproof]
         (== (lcons 'app (lcons head left-args)) left-root)
         (== (lcons 'app (lcons head right-args)) right-root)
         (eq-contradiction-term*o left-args right-args sigma subproof)
         (== (list 'decompose subproof) proof))])))

(defn eq-contradiction-term*o
  "Find the first contradictory argument pair in two application argument lists.

   This is the argument-list companion to `eq-contradictiono`. It either finds
   an immediate arity mismatch or recursively scans corresponding arguments
   until some deeper contradiction emerges."
  [left right sigma proof]
  (conde
    ;; Left argument list longer than right: arity mismatch / constructor
    ;; mismatch at the argument-list level.
    [(fresh [head tail]
       (== (lcons head tail) left)
       (== '() right)
       (== '(free-close) proof))]
    ;; Right argument list longer than left.
    [(fresh [head tail]
       (== '() left)
       (== (lcons head tail) right)
       (== '(free-close) proof))]
    ;; Compare the leading arguments. Either they are immediately
    ;; contradictory, or earlier equalization of those arguments enables a
    ;; later contradiction in the tails.
    [(fresh [left-head left-tail right-head right-tail]
       (== (lcons left-head left-tail) left)
       (== (lcons right-head right-tail) right)
       (conde
         [(eq-contradictiono left-head right-head sigma proof)]
         [(fresh [sigma-mid head-proof tail-proof]
            ;; A contradiction can emerge only after earlier arguments bind
            ;; proof-time variables or parameters. For example,
            ;; cons(1, null) = cons(a, cons(b, t)) becomes contradictory only
            ;; after binding a = 1 and then seeing null = cons(b, t).
            (unify-termo left-head right-head sigma sigma-mid head-proof)
            (eq-contradiction-term*o left-tail right-tail sigma-mid tail-proof)
            (== (list 'args head-proof tail-proof) proof))]))]))

;; ---------------------------------------------------------------------------
;; Unification and branch-state consequences
;; ---------------------------------------------------------------------------
;;
;; `unify-termo` is the positive equality workhorse: it extends `sigma` just
;; enough to make two terms equal in the free term algebra.

(defn unify-termo
  "Extend `sigma` so `left` and `right` become equal in the free term algebra.

   The proof tags distinguish the operational reason for success:

   - `eq-refl`: already equal after walking,
   - `eq-bind`: bind a proof variable,
   - `par-bind`: bind a rigid parameter,
   - `decompose`: same-head constructor terms unify argumentwise."
  [left right sigma sigma-out proof]
  (fresh [left-root right-root]
    (walko left sigma left-root)
    (walko right sigma right-root)
    (conde
      ;; Nothing new to learn.
      [(same-termo left-root right-root sigma)
       (== sigma sigma-out)
       (== '(eq-refl) proof)]
      ;; Bind a proof variable on the left, provided the occurs check passes.
      [(fresh [binding-nom]
         (== (list 'var binding-nom) left-root)
         (absent-termo binding-nom right-root sigma)
         (== (lcons [binding-nom right-root] sigma) sigma-out)
         (== '(eq-bind) proof))]
      ;; Symmetric proof-variable binding.
      [(fresh [binding-nom]
         (== (list 'var binding-nom) right-root)
         (absent-termo binding-nom left-root sigma)
         (== (lcons [binding-nom left-root] sigma) sigma-out)
         (== '(eq-bind) proof))]
      ;; Bind a rigid parameter on the left, provided the parameter-absence
      ;; check passes.
      [(fresh [binding-nom]
         (== (list 'par binding-nom) left-root)
         (absent-paro binding-nom right-root sigma)
         (== (lcons [binding-nom right-root] sigma) sigma-out)
         (== '(par-bind) proof))]
      ;; Symmetric parameter binding.
      [(fresh [binding-nom]
         (== (list 'par binding-nom) right-root)
         (absent-paro binding-nom left-root sigma)
         (== (lcons [binding-nom left-root] sigma) sigma-out)
         (== '(par-bind) proof))]
      ;; Same constructor head: unify the arguments pairwise.
      [(fresh [head left-args right-args subproof]
         (== (lcons 'app (lcons head left-args)) left-root)
         (== (lcons 'app (lcons head right-args)) right-root)
         (unify-term*o left-args right-args sigma sigma-out subproof)
         (== (list 'decompose subproof) proof))])))

(defn unify-term*o
  "Pairwise unification across argument lists.

   This is the structural decomposition step for same-head constructor
   equalities."
  [left right sigma sigma-out proof]
  (conde
    ;; Two nullary argument lists unify without changing the substitution.
    [(== '() left)
     (== '() right)
     (== sigma sigma-out)
     (== '() proof)]
    ;; Otherwise unify the heads, then continue recursively through the tails.
    [(fresh [left-head left-tail right-head right-tail sigma-mid head-proof tail-proof]
       (== (lcons left-head left-tail) left)
       (== (lcons right-head right-tail) right)
       (unify-termo left-head right-head sigma sigma-mid head-proof)
       (unify-term*o left-tail right-tail sigma-mid sigma-out tail-proof)
       (== (list 'args head-proof tail-proof) proof))]))

(defn atom-unifyo
  "Unify two atomic applications of the same relation symbol.

   Complementary literal closure in the kernel delegates here after it has
   already established that the positive and negative literals mention the same
   relation symbol."
  [left right sigma sigma-out proof]
  (fresh [head left-args right-args arg-proof]
    (== (lcons 'app (lcons head left-args)) left)
    (== (lcons 'app (lcons head right-args)) right)
    (unify-term*o left-args right-args sigma sigma-out arg-proof)
    (== (list 'atom-close arg-proof) proof)))

(defn neq-violatedo
  "Succeed when one saved disequality has become false under `sigma`.

   The kernel uses this after every successful positive equality step. If any
   stored `neq` pair now walks to the same term on both sides, the branch
   closes."
  [neqs sigma proof]
  (fresh [left right rest]
    (conde
      ;; Head disequality has collapsed to reflexivity.
      [(== (lcons [left right] rest) neqs)
       (same-termo left right sigma)
       (== '(neq-close) proof)]
      ;; Otherwise keep scanning the remaining symbolic disequalities.
      [(== (lcons [left right] rest) neqs)
       (neq-violatedo rest sigma proof)])))

(defn contradictory-atomso
  "Succeed when saved positive and negative atoms now unify under `sigma`.

   This is the equality-aware version of complementary branch closure: the
   literals need not have been syntactically complementary when they were
   saved, only unifiable after walking through the current branch equalities."
  [lits sigma sigma-out proof]
  (fresh [left-atom right-atom atom-proof]
    (conde
      ;; Positive saved earlier, negative saved elsewhere on the branch.
      [(membero (list 'pos left-atom) lits)
       (membero (list 'neg right-atom) lits)]
      ;; Symmetric ordering.
      [(membero (list 'neg left-atom) lits)
       (membero (list 'pos right-atom) lits)])
    (atom-unifyo left-atom right-atom sigma sigma-out atom-proof)
    (== atom-proof proof)))
