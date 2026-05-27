;; ============================================================================
;; αleanTAP-EP: A Declarative Logic Programming Language
;;              based on Tableau Methods, with Equality
;; ============================================================================
;;
;; This extends αleanTAP-E with Fitting's Procedure Call Rule, transforming
;; the theorem prover into a logic programming language in the style of
;; Proflog (Fitting, "Tableaux for Logic Programming", J. Automated
;; Reasoning 13, 1994).
;;
;; THE KEY IDEA (from Fitting's paper, Section 6):
;;
;;   In a standard tableau prover, a branch closes when it contains
;;   complementary literals A and ¬A.  Fitting's insight is to add a
;;   second way to close a branch: by *calling a program definition*.
;;
;;   A "program" P is a set of clauses  R(x₁,...,xₙ) ← φ(x₁,...,xₙ)
;;   where φ can be ANY first-order formula (not just Horn clauses!).
;;   The semantics is biconditional: R(t) holds iff φ(t) holds.
;;
;;   The PROCEDURE CALL RULE says a branch is closed if:
;;
;;     (1) It contains a positive atom R(t₁,...,tₙ) of L, there is a
;;         clause R(x) ← φ(x) in P, and there exists a closed P-tableau
;;         for φ(t₁,...,tₙ).
;;
;;         [R(t) is asserted, but φ(t) is unsatisfiable, contradicting
;;          the definition R(t) ↔ φ(t).]
;;
;;     (2) It contains a negated atom ¬R(t₁,...,tₙ) of L, there is a
;;         clause R(x) ← φ(x) in P, and there exists a closed P-tableau
;;         for ¬φ(t₁,...,tₙ).
;;
;;         [¬R(t) is asserted, but φ(t) is valid, contradicting
;;          the definition R(t) ↔ φ(t).]
;;
;;   Each procedure call spawns a SUBSIDIARY TABLEAU — a completely fresh
;;   proof obligation with its own branches, literals, and expansion
;;   stack.  The subsidiary tableau can itself invoke procedure calls,
;;   giving us recursion.
;;
;; WHY THIS MATTERS:
;;
;;   αleanTAP is already a pure relation (runs forwards and backwards).
;;   Adding Proflog-style procedure calls to a relational prover gives
;;   capabilities beyond either Prolog or standard theorem proving:
;;
;;   - Forward:  Given program + query, compute whether query succeeds
;;   - Backward: Given program + partial query, GENERATE succeeding queries
;;   - Sideways: Given partial program + query, SYNTHESIZE clause bodies
;;
;;   This is, to our knowledge, the first implementation of Proflog,
;;   and it inherits αleanTAP's declarative flexibility.
;;
;; FORMULA GRAMMAR (extended with existential quantifier):
;;
;;   Fml  → (and Fml Fml) | (or Fml Fml)
;;        | (forall (tie Nom Fml)) | (exists (tie Nom Fml))
;;        | Lit
;;   Lit  → (pos Term) | (neg Term) | (eq Term Term) | (neq Term Term)
;;   Term → (var Nom) | (app Symbol Term*) | (par Nom)
;;
;; PROGRAM CLAUSE:
;;
;;   Clause → [Symbol [Nom ...] Fml]
;;
;;   A clause is a vector: [rel-symbol [param-noms...] body-formula]
;;   The noms in the params list are formal parameters.  The body
;;   uses (var nomᵢ) to reference them.
;;
;;   Example:
;;     ['even [a]
;;      (or (eq (var a) (app zero))
;;          (exists (tie b (and (eq (var a) (app s (var b)))
;;                              (pos (app odd (var b)))))))]
;;
;; PROOF STEPS (new):
;;   (proc-call R . prf)      — positive procedure call on relation R
;;   (neg-proc-call R . prf)  — negative procedure call on relation R
;;   (subst-call R . prf)     — substitutivity-augmented positive proc call
;;   (neg-subst-call R . prf) — substitutivity-augmented negative proc call
;;   (witness . prf)          — existential witness introduction (δ rule)
;;   (free-close)             — free closure: distinct constructors clash
;;   (eq-neq-close)           — eq/neq complementary closure
;;   (decompose . prf)        — injectivity: decompose same-head eq into sub-eqs
;;   (para-free-close)        — paramodulated free closure: rewrite one side of eq via branch eqs to clash
;;   (eq-refl-close)          — neq closed via one-one derived equalities
;;
;; ============================================================================

(ns cljtap.alphaleantap-ep
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer :all :rename {appendo logic-appendo, membero logic-membero}]
            [clojure.core.logic.nominal :refer [tie hash]]))

;; In core.logic.nominal 1.0.1, `nom` is a constructor function, not a macro.
;; We define `nom` as a macro that introduces fresh nominal bindings using the
;; nominal namespace's `fresh` macro, which creates Nom objects (rigid names).
;;
;;   (nom a goal)         — introduce fresh nom `a`, then run goal
;;   (nom a b c goal)     — introduce fresh noms `a`, `b`, `c`, then run goal
;;   (nom a (nom p goal)) — nesting also works (expands inside-out)
(defmacro nom [& args]
  (let [syms (butlast args)
        body (last args)]
    `(clojure.core.logic.nominal/fresh [~@syms] ~body)))

;; ============================================================================
;; Part 1: Core Helper Relations
;; ============================================================================

(defn lookupo
  "Look up the value associated with nom `a` in environment `env`."
  [a env out]
  (fresh [first rest]
    (conde
      [(== (lcons [a out] rest) env)]
      [(== (lcons first rest) env)
       (lookupo a rest out)])))

(defn appendo
  "Relational list append."
  [ls s out]
  (conde
    [(== '() ls) (== s out)]
    [(fresh [a d r]
       (== (lcons a d) ls)
       (== (lcons a r) out)
       (appendo d s r))]))

(defn membero
  "Relational membership check using sound unification."
  [x ls]
  (fresh [a d]
    (== (lcons a d) ls)
    (conde
      [(== a x)]
      [(membero x d)])))

;; ============================================================================
;; Part 2: Substitution Relations
;; ============================================================================

(declare subst-term*o)

(defn subst-termo
  "Substitute values for tagged noms (var a) in a term, using environment.
   Projects fml for type dispatch.  Var case chains through par bindings
   for constraint propagation: after lookupo, projects result to check if
   it's a par with a propagated binding.  Single project per var substitution."
  [fml env out]
  (project [fml]
    (cond
      (and (vector? fml) (= (first fml) 'var))
      (fresh [val]
        (lookupo (second fml) env val)
        (project [val]
          (if (and (vector? val) (= (first val) 'par))
            ;; var resolved to (par p) — check for propagated par-eq binding
            (conda
              [(lookupo (second val) env out)]
              [(== val out)])
            ;; var resolved to non-par — pass through directly
            (== val out))))

      (and (vector? fml) (= (first fml) 'par))
      (== fml out)

      (sequential? fml)
      (fresh [f d r]
        (== (lcons 'app (lcons f d)) fml)
        (== (lcons 'app (lcons f r)) out)
        (subst-term*o d env r))

      :else
      (== fml out))))

(defn subst-term*o
  "Substitute across a list of terms."
  [tm* env out]
  (conde
    [(== '() tm*) (== '() out)]
    [(fresh [e1 e2 r1 r2]
       (== (lcons e1 e2) tm*)
       (== (lcons r1 r2) out)
       (subst-termo e1 env r1)
       (subst-term*o e2 env r2))]))

(defn subst-lito
  "Substitute in a literal: (pos t), (neg t), (eq t1 t2), (neq t1 t2)."
  [fml env out]
  (conde
    [(fresh [l r]
       (== ['pos l] fml)
       (== ['pos r] out)
       (subst-termo l env r))]
    [(fresh [l r]
       (== ['neg l] fml)
       (== ['neg r] out)
       (subst-termo l env r))]
    [(fresh [l1 l2 r1 r2]
       (== ['eq l1 l2] fml)
       (== ['eq r1 r2] out)
       (subst-termo l1 env r1)
       (subst-termo l2 env r2))]
    [(fresh [l1 l2 r1 r2]
       (== ['neq l1 l2] fml)
       (== ['neq r1 r2] out)
       (subst-termo l1 env r1)
       (subst-termo l2 env r2))]))

;; ============================================================================
;; Part 3: Equality Reasoning — Free Closure, Injectivity, Paramodulation
;; ============================================================================
;;
;; Extended from αleanTAP-E with Fitting's Section 5 rules for weak
;; Herbrand models.  In a weak Herbrand model, function symbols are
;; interpreted as FREE constructors:
;;
;;   (i)   DISJOINTNESS: If f ≠ g, then f(t₁,...,tₙ) ≠ g(s₁,...,sₘ)
;;         for all terms t, s.  Distinct constructors have disjoint ranges.
;;
;;   (ii)  INJECTIVITY (One-One):  f(t₁,...,tₙ) = f(s₁,...,sₙ) implies
;;         t₁ = s₁ ∧ ... ∧ tₙ = sₙ.  Constructors are one-to-one.
;;
;; These give us three new capabilities in the prover:
;;
;;   - FREE CLOSURE (clash): A literal (eq (app f ...) (app g ...))
;;     with f ≠ g closes the branch immediately.
;;
;;   - ONE-ONE DECOMPOSITION: An eq literal with the same head yields
;;     pairwise sub-equalities that are injected into the paramodulation
;;     machinery, enabling rewriting with derived equalities.
;;
;;   - SUBSTITUTIVITY FOR PROC CALLS: Branch equalities (including
;;     one-one derived ones) can rewrite a literal's arguments before
;;     firing a procedure call, bridging the gap between rigid δ-rule
;;     parameters and concrete constructor terms.
;;
;; Together these make Proflog programs with constructors (zero/s,
;; nil/cons, etc.) work correctly — the "expected limitation" is gone.

;; --- 3a. Free Closure ---

(defn free-closureo
  "Succeed if (eq t1 t2) is unsatisfiable in any weak Herbrand model.
   Two terms with different head constructor symbols can never be equal.

   With (par p) encoding, δ-parameters never appear inside (app ...) terms,
   so both s1 and s2 are always Clojure symbols. The relational disequality
   constraint (!= s1 s2) enforces that they are distinct — no project needed."
  [t1 t2]
  (fresh [s1 s2 a1 a2]
    (== (lcons 'app (lcons s1 a1)) t1)
    (== (lcons 'app (lcons s2 a2)) t2)
    (!= s1 s2)))

(defn different-lengtho
  "Succeed if two lists have different lengths. Purely relational.
   Used for arity-mismatch detection: f(a,b) vs f(a) have arg lists
   of length 2 vs 1."
  [l1 l2]
  (conde
    ;; l1 empty, l2 non-empty
    [(== l1 '())
     (fresh [h t] (== (lcons h t) l2))]
    ;; l1 non-empty, l2 empty
    [(fresh [h t] (== (lcons h t) l1))
     (== l2 '())]
    ;; both non-empty: recurse on tails
    [(fresh [h1 t1 h2 t2]
       (== (lcons h1 t1) l1)
       (== (lcons h2 t2) l2)
       (different-lengtho t1 t2))]))

(defn arity-mismatch-closureo
  "Succeed if (eq t1 t2) is unsatisfiable due to arity mismatch.
   Two terms with the SAME head symbol but different numbers of arguments
   are structurally distinct in any Herbrand model.

   Covers Fitting §5 Free Closure Rule cases 2 and 3:
     f(t₁,...,tₙ) = c      — function vs constant (same name, n>0 vs 0 args)
     f(t₁,...,tₙ) = f(u₁,...,uₖ) — same head, n ≠ k

   In standard FOL, function symbols have fixed arity, so f/0 and f/1
   are distinct symbols. Our implementation allows the same Clojure symbol
   with varying arg counts, so this check is needed explicitly."
  [t1 t2]
  (fresh [f args1 args2]
    (== (lcons 'app (lcons f args1)) t1)
    (== (lcons 'app (lcons f args2)) t2)
    (different-lengtho args1 args2)))

;; --- 3b. One-One Decomposition ---

(defn one-one-pairso
  "Given two argument lists from same-head terms, produce pairwise
   equality pairs (both directions) for use by the paramodulation engine.
   
   E.g., (app s (app zero)) = (app s (app p))
     args1 = [(app zero)],  args2 = [(app p)]
     pairs = [[(app zero) (app p)] [(app p) (app zero)]]"
  [args1 args2 pairs]
  (conde
    [(== '() args1) (== '() args2) (== '() pairs)]
    [(fresh [t u r1 r2 rest-pairs]
       (== (lcons t r1) args1)
       (== (lcons u r2) args2)
       (== (lcons [t u] (lcons [u t] rest-pairs)) pairs)
       (one-one-pairso r1 r2 rest-pairs))]))

;; --- 3c. Equality Decomposition (for injectivity expansion rule) ---
;;
;; Builds a conjunction of sub-equalities from paired argument lists.
;; Used by the decomposition rule in proveo to expand:
;;   (eq (app f t₁…tₙ) (app f s₁…sₙ)) → (and (eq t₁ s₁) … (eq tₙ sₙ))

(defn decompose-eq-argso
  "Build a conjunction formula of pairwise sub-equalities.
   
   [t₁]       [s₁]       → (eq t₁ s₁)
   [t₁ t₂]    [s₁ s₂]    → (and (eq t₁ s₁) (eq t₂ s₂))
   [t₁ t₂ t₃] [s₁ s₂ s₃] → (and (eq t₁ s₁) (and (eq t₂ s₂) (eq t₃ s₃)))
   
   Fails on empty argument lists (handled by refl-close at the eq level)
   and on mismatched arities (which is itself a free closure violation)."
  [args1 args2 result]
  (conde
    ;; Base case: single argument pair
    [(fresh [a1 a2]
       (== (lcons a1 '()) args1)
       (== (lcons a2 '()) args2)
       (== ['eq a1 a2] result))]
    ;; Recursive: first pair, then conjunction with rest
    [(fresh [a1 a2 r1 r2 rest-eq]
       (== (lcons a1 r1) args1)
       (== (lcons a2 r2) args2)
       ;; Ensure r1 is non-empty (otherwise base case matches)
       (fresh [_ __]
         (== (lcons _ __) r1))
       (decompose-eq-argso r1 r2 rest-eq)
       (== ['and ['eq a1 a2] rest-eq] result))]))

;; --- 3d. Collect equalities (with one-one decomposition) ---

(defn collect-eqso
  "Collect all usable equality pairs from branch literals.
   
   For each (eq t1 t2):
     - Always yields [t1 t2] and [t2 t1]                (top-level)
     - If same head: also yields [arg_i arg_j] pairs     (one-one rule)
   
   The one-one derived pairs are interleaved into the equality list
   so that the paramodulation machinery (eq-membero, rewrite-term-with-eqso)
   can use them for rewriting without any special-casing."
  [lits eqs]
  (conde
    [(== '() lits) (== '() eqs)]
    ;; Equality literal: extract top-level + one-one decomposed pairs
    [(fresh [l1 l2 rest-lits rest-eqs mid-eqs]
       (== (lcons ['eq l1 l2] rest-lits) lits)
       (collect-eqso rest-lits rest-eqs)
       ;; Top-level pair (both directions)
       (== (lcons [l1 l2] (lcons [l2 l1] mid-eqs)) eqs)
       (conde
         ;; Same head constructor: also inject one-one pairs
         [(fresh [f a1 a2 oo-pairs]
            (== (lcons 'app (lcons f a1)) l1)
            (== (lcons 'app (lcons f a2)) l2)
            (one-one-pairso a1 a2 oo-pairs)
            (appendo oo-pairs rest-eqs mid-eqs))]
         ;; Different heads or non-decomposable: just the rest
         [(== mid-eqs rest-eqs)]))]
    ;; Non-equality literal: skip
    [(fresh [lit rest-lits]
       (== (lcons lit rest-lits) lits)
       (fresh [tag _]
         (conde
           [(== ['pos tag] lit)]
           [(== ['neg tag] lit)]
           [(== ['neq tag _] lit)])
         (collect-eqso rest-lits eqs)))]))

;; --- 3e. Rewriting (paramodulation engine) ---

(declare rewrite-term*o)

(defn rewrite-termo [t lhs rhs out]
  (conde
    [(== t lhs) (== out rhs)]
    [(fresh [f args args-out]
       (== (lcons 'app (lcons f args)) t)
       (== (lcons 'app (lcons f args-out)) out)
       (rewrite-term*o args lhs rhs args-out))]))

(defn rewrite-term*o [terms lhs rhs out]
  (fresh [t1 rest r1 rest-out]
    (== (lcons t1 rest) terms)
    (conde
      [(rewrite-termo t1 lhs rhs r1)
       (== (lcons r1 rest) out)]
      [(== (lcons t1 rest-out) out)
       (rewrite-term*o rest lhs rhs rest-out)])))

(defn rewrite-lito [lit lhs rhs out]
  (conde
    [(fresh [t t-out]
       (== ['pos t] lit)
       (== ['pos t-out] out)
       (rewrite-termo t lhs rhs t-out))]
    [(fresh [t t-out]
       (== ['neg t] lit)
       (== ['neg t-out] out)
       (rewrite-termo t lhs rhs t-out))]))

;; --- 3f. Equality-aware membership (for closure) ---

(defn selecto
  "Non-deterministically select an element `x` from `lst`, with `rest`
   being `lst` with that one occurrence of `x` removed.
   Purely relational — uses only == and conde, no disequality constraints."
  [x lst rest]
  (conde
    [(fresh [t]
       (== (lcons x t) lst)
       (== t rest))]
    [(fresh [h t r]
       (== (lcons h t) lst)
       (== (lcons h r) rest)
       (selecto x t r))]))

(defn eq-membero
  "Check if `neg` (a literal) can be found in `lits` after zero or more
   rewrite steps using equality pairs from `remaining`.

   Non-deterministic: tries EVERY equality pair at each step via selecto,
   enabling multi-step rewriting chains (e.g., a→b→c via transitivity).

   Each equality pair is used at most once per chain (selecto removes the
   chosen pair from `remaining`), guaranteeing termination on cyclic equality
   sets without an arbitrary step limit."
  [neg lits remaining]
  (conde
    [(membero neg lits)]
    [(fresh [pair lhs rhs neg-rewritten rest]
       (selecto pair remaining rest)
       (== [lhs rhs] pair)
       (rewrite-lito neg lhs rhs neg-rewritten)
       (eq-membero neg-rewritten lits rest))]))

;; --- 3g. Substitutivity for terms (multi-argument) ---
;;
;; Rewrites one or more arguments of a term using equality pairs from
;; the collected set.  Each argument is independently rewritten (or not)
;; using a possibly different equality pair.
;;
;; This is critical for multi-argument constructors and relations:
;;   Branch has (eq (app zero) (app p1)) and (eq (app nil) (app p2))
;;   Literal: (pos (app member (app p1) (app p2)))
;;   Rewrite: p1 → zero, p2 → nil (independently)
;;   Proc call fires on member(zero, nil) instead of member(p1, p2)
;;
;; The `someo` variant guarantees at least one argument is actually
;; rewritten, preventing overlap with the plain procedure call rule.

(defn rewrite-args-maybeo
  "Rewrite zero or more arguments using equality pairs from `eqs`.
   Each argument is independently either kept or rewritten using one pair."
  [args eqs new-args]
  (conde
    [(== '() args) (== '() new-args)]
    [(fresh [a rest a-out rest-out]
       (== (lcons a rest) args)
       (== (lcons a-out rest-out) new-args)
       (conde
         ;; Rewrite this arg using one equality pair
         [(fresh [pair lhs rhs]
            (membero pair eqs)
            (== [lhs rhs] pair)
            (rewrite-termo a lhs rhs a-out))]
         ;; Keep this arg unchanged
         [(== a a-out)])
       (rewrite-args-maybeo rest eqs rest-out))]))

(defn rewrite-args-someo
  "Rewrite one or more arguments using equality pairs from `eqs`.
   Guarantees at least one argument is rewritten — if the current arg
   is kept unchanged, at least one later arg must be rewritten."
  [args eqs new-args]
  (fresh [a rest a-out rest-out]
    (== (lcons a rest) args)
    (== (lcons a-out rest-out) new-args)
    (conde
      ;; Rewrite this arg; rest get zero-or-more rewrites
      [(fresh [pair lhs rhs]
         (membero pair eqs)
         (== [lhs rhs] pair)
         (rewrite-termo a lhs rhs a-out))
       (rewrite-args-maybeo rest eqs rest-out)]
      ;; Keep this arg; must still rewrite at least one later
      [(== a a-out)
       (rewrite-args-someo rest eqs rest-out)])))

(defn rewrite-term-with-eqso
  "Rewrite term `t` by independently rewriting one or more of its
   arguments using equality pairs from `eqs`.
   
   Each argument position can use a different equality pair, enabling
   multi-argument terms like R(p₁, p₂) to have both arguments
   rewritten simultaneously.  At least one argument must be rewritten
   (guaranteed by rewrite-args-someo), preventing overlap with the
   plain procedure call rules."
  [t eqs out]
  (fresh [f args new-args]
    (== (lcons 'app (lcons f args)) t)
    (== (lcons 'app (lcons f new-args)) out)
    (rewrite-args-someo args eqs new-args)))

(defn eq-neq-closeo
  "Multi-step neq closure: rewrite t1 toward t2 using ONE OR MORE equality
   pairs from `eqs` until t1 becomes identical to t2.

   Requires at least one rewriting step — trivial t1==t2 reflexivity is
   handled by the refl-close rule and must not overlap here.  This
   separation prevents duplicate solutions in backward-running mode.

   This handles transitivity chains:
     Branch has a=b, b=c.  (neq a c) → rewrite a→b → rewrite b→c → (neq c c) → close.

   Also handles one-one derived pairs:
     Branch has s(a)=s(b).  (neq a b) → rewrite a→b via one-one → (neq b b) → close.

   Each equality pair is used at most once per chain (`remaining` shrinks at
   each step via selecto), guaranteeing termination on cyclic equality sets
   without an arbitrary step limit."
  [t1 t2 remaining]
  (fresh [pair lhs rhs t1-rw rest]
    (selecto pair remaining rest)
    (== [lhs rhs] pair)
    (rewrite-termo t1 lhs rhs t1-rw)
    (conde
      [(== t1-rw t2)]
      [(eq-neq-closeo t1-rw t2 rest)])))

;; --- 3h. Paramodulated free closure ---
;;
;; Detects transitive constructor clashes that require equality reasoning:
;;
;;   Branch: (eq (app a) (app p)) — saved to lits.
;;   Current eq: (eq (app b) (app p)).
;;   eqs include: [(app p), (app a)].
;;   Rewrite t2=(app p) → (app a): yields (eq (app b) (app a)).
;;   free-closureo fires: b ≠ a ✓
;;
;; Requires at least one rewriting step — direct clashes are already
;; handled by the free-close rule.  Each equality pair is used at most once
;; per chain (selecto shrinks remaining), preventing cycling.

(defn para-free-closeo
  "Paramodulated free closure: rewrite one side of (eq t1 t2) using branch
   equalities in one or more steps until the result clashes with the other
   side via free-closureo.

   Tries rewriting t1 or t2 independently at each step.  Recursion handles
   multi-step chains (e.g., p→q→a when branch has p=q and q=a).

   Each equality pair is used at most once per chain (selecto removes the
   chosen pair from `remaining`), guaranteeing termination without an
   arbitrary step limit.

   Soundness: every rewriting step uses only equalities already on the branch,
   and free-closureo's symbol? guard ensures only genuine constructor symbols
   (not δ-parameters) are treated as distinct."
  [t1 t2 remaining]
  (fresh [pair lhs rhs rest]
    (selecto pair remaining rest)
    (== [lhs rhs] pair)
    (conde
      ;; Rewrite t1 one step, then clash-check or recurse
      [(fresh [t1-rw]
         (rewrite-termo t1 lhs rhs t1-rw)
         (conde
           [(free-closureo t1-rw t2)]
           [(para-free-closeo t1-rw t2 rest)]))]
      ;; Rewrite t2 one step, then clash-check or recurse
      [(fresh [t2-rw]
         (rewrite-termo t2 lhs rhs t2-rw)
         (conde
           [(free-closureo t1 t2-rw)]
           [(para-free-closeo t1 t2-rw rest)]))])))

;; ============================================================================
;; Part 4: Formula Negation (NNF-preserving)
;; ============================================================================
;;
;; The negative procedure call (Part 2 of Fitting's rule) requires
;; computing ¬φ when the branch contains ¬R(t) and the clause is
;; R(x) ← φ(x).  We need to negate φ while staying in NNF.
;;
;; This is a pure relation: it runs both ways.

(defn negate-formulao
  "Compute the NNF negation of a formula.  Pure relation.
   
   ¬(and A B)           = (or ¬A ¬B)             — De Morgan
   ¬(or A B)            = (and ¬A ¬B)            — De Morgan
   ¬(forall (tie a P))  = (exists (tie a ¬P))    — quantifier dual
   ¬(exists (tie a P))  = (once-forall (tie a ¬P)) — quantifier dual (single-use instantiation)
   ¬(once-forall (tie a P)) = (exists (tie a ¬P)) — once-forall negation (same as forall)
   ¬(pos t)             = (neg t)                — literal negation
   ¬(neg t)             = (pos t)
   ¬(eq t1 t2)          = (neq t1 t2)
   ¬(neq t1 t2)         = (eq t1 t2)"
  [fml neg-fml]
  (conde
    ;; Conjunction ↔ Disjunction (De Morgan)
    [(fresh [a b na nb]
       (== ['and a b] fml)
       (== ['or na nb] neg-fml)
       (negate-formulao a na)
       (negate-formulao b nb))]

    ;; Disjunction ↔ Conjunction (De Morgan)
    [(fresh [a b na nb]
       (== ['or a b] fml)
       (== ['and na nb] neg-fml)
       (negate-formulao a na)
       (negate-formulao b nb))]

    ;; Universal ↔ Existential
    [(nom a
       (fresh [body neg-body]
         (== ['forall (tie a body)] fml)
         (== ['exists (tie a neg-body)] neg-fml)
         (negate-formulao body neg-body)))]

    ;; Existential ↔ Once-Universal (negated existential: instantiate once, no re-enqueue)
    [(nom a
       (fresh [body neg-body]
         (== ['exists (tie a body)] fml)
         (== ['once-forall (tie a neg-body)] neg-fml)
         (negate-formulao body neg-body)))]

    ;; Once-Universal → Existential (forward negation of once-forall)
    ;; Semantically once-forall x.P ≡ forall x.P, so ¬(once-forall x.P) = ∃x.¬P.
    ;; This branch is needed when a clause body contains a once-forall sub-formula
    ;; (e.g., a pre-expanded NNF body) and a neg-proc-call must negate it.
    [(nom a
       (fresh [body neg-body]
         (== ['once-forall (tie a body)] fml)
         (== ['exists (tie a neg-body)] neg-fml)
         (negate-formulao body neg-body)))]

    ;; Positive literal ↔ Negative literal
    [(fresh [t]
       (== ['pos t] fml)
       (== ['neg t] neg-fml))]
    [(fresh [t]
       (== ['neg t] fml)
       (== ['pos t] neg-fml))]

    ;; Equality ↔ Disequality
    [(fresh [t1 t2]
       (== ['eq t1 t2] fml)
       (== ['neq t1 t2] neg-fml))]
    [(fresh [t1 t2]
       (== ['neq t1 t2] fml)
       (== ['eq t1 t2] neg-fml))]))

;; ============================================================================
;; Part 5: Program Clause Lookup and Instantiation
;; ============================================================================
;;
;; A Proflog program is a list of clauses.  Each clause defines one
;; relation symbol.  Fitting requires at most one clause per relation
;; (Definition 2.1), which simplifies lookup.
;;
;; Clause representation:
;;
;;   A clause is a list:  [rel-symbol [nom₁ ... nomₙ] body-formula]
;;
;;   The noms in the params list are the formal parameters.  The body
;;   uses (var nomᵢ) to reference them.
;;
;; On a procedure call for R(t₁,...,tₙ):
;;   1. Look up the clause for R
;;   2. Create an env mapping each param nom to the corresponding arg term
;;   3. Launch a subsidiary proveo with this env and the clause body

(defn lookup-clauseo
  "Find the clause for relation symbol `rel` in program `prog`.
   Yields the parameter noms and body formula."
  [rel prog params body]
  (fresh [clause rest]
    (== (lcons clause rest) prog)
    (conde
      [(== [rel params body] clause)]
      [(fresh [other-rel _params _body]
         (== [other-rel _params _body] clause)
         ;; Only proceed if other-rel is NOT rel (no overlap)
         ;; In practice, Fitting requires one clause per rel,
         ;; so we just search the list.
         (lookup-clauseo rel rest params body))])))

(defn bind-argso
  "Create an environment mapping param noms to argument terms.
   (bind-argso [a b] [t1 t2] env) => env = [[a t1] [b t2]]
   
   The params are noms from the clause definition.
   The args are substituted terms from the call site."
  [params args env]
  (conde
    [(== '() params) (== '() args) (== '() env)]
    [(fresh [p ps a as rest-env]
       (== (lcons p ps) params)
       (== (lcons a as) args)
       (== (lcons [p a] rest-env) env)
       (bind-argso ps as rest-env))]))

;; ============================================================================
;; Part 5b: L-Groundness Guard (Fitting §6 Definition 6.1)
;; ============================================================================
;;
;; The procedure call rule may only fire on L-ground atoms — atoms whose
;; arguments contain no δ-parameters (par p).  These helpers implement
;; that check.  `project` is intentional and correct here: the guard is
;; one-directional (called with a walked term, never in synthesis mode for
;; the term itself) and must not unify unbound γ-rule logic variables with
;; anything.

(defn contains-par?
  "Returns true iff term t contains any (par ...) sub-term.
   Safe on LVar objects (bare logic variables from the γ-rule or synthesis)."
  [t]
  (cond
    (and (vector? t) (= (first t) 'par)) true
    (vector? t)      (some contains-par? (rest t))
    (sequential? t)  (some contains-par? t)
    :else            false))

(defn l-ground-termo
  "Succeeds iff term t is L-ground: contains no (par ...) sub-terms.
   Uses project — a one-directional meta-level check, not a structural relation."
  [t]
  (project [t]
    (if (contains-par? t) fail succeed)))

(defn l-ground-term*o
  "Succeeds iff every term in the list is L-ground."
  [terms]
  (conde
    [(== '() terms)]
    [(fresh [t rest]
       (== (lcons t rest) terms)
       (l-ground-termo t)
       (l-ground-term*o rest))]))

;; --- 5b. Par-Eq Constraint Propagation ---

(defn propagate-par-eqo
  "When an eq literal binds a δ-parameter to a concrete term, add the
   binding [par-nom, term] to env so that subsequent subst-termo calls
   resolve the par eagerly.  This is the core of constraint propagation:
   it turns 'deferred' par-vs-concrete clashes into immediate free-closures,
   pruning dead β-branches before they are explored.

   Only fires when exactly one side is (par p) and the other is not a par.
   Leaves env unchanged otherwise (both pars, neither par, etc.)."
  [t1 t2 env new-env]
  (project [t1 t2]
    (cond
      (and (vector? t1) (= (first t1) 'par)
           (not (and (vector? t2) (= (first t2) 'par))))
      (== new-env (lcons [(second t1) t2] env))

      (and (vector? t2) (= (first t2) 'par)
           (not (and (vector? t1) (= (first t1) 'par))))
      (== new-env (lcons [(second t2) t1] env))

      :else
      (== new-env env))))

;; ============================================================================
;; Part 6: The Main Prover — αleanTAP-EP (proveo)
;; ============================================================================
;;
;; proveo is now extended with:
;;   - A `program` argument (the Proflog program P)
;;   - The existential quantifier rule (δ rule)
;;   - Fitting's Procedure Call Rule (positive and negative)
;;
;; Arguments:
;;   fml     — current formula being expanded
;;   unexp   — stack of unexpanded formulas on this branch
;;   lits    — literals already on this branch
;;   env     — nom → value mappings (for ∀/∃ instantiation)
;;   program — the Proflog program (list of clauses)
;;   proof   — proof term recording steps taken

(defn proveo
  "Main tableau prover. Expands formula `fml` on a branch with unexpanded
   formulas `unexp`, literals `lits`, environment `env`, program `program`.
   Produces `proof` term.  Optional `gamma-budget` bounds γ-rule applications:
     nil  → unbounded (original behavior)
     0    → γ-rule disabled (must close with existing formulas)
     n>0  → n remaining γ-applications on this branch
   Optional `lem-in`/`lem-out` thread lemmas through the proof for reuse:
     At β-rule, left branch's lem-out feeds right branch's lem-in.
     At closure, the proved literal is added to lem-out.
     Subsidiary tableaux (proc calls) start with empty lemmas."
  ([fml unexp lits env program proof]
   (proveo fml unexp lits env program proof nil))
  ([fml unexp lits env program proof gamma-budget]
   (fresh [lem-out]
     (proveo fml unexp lits env program proof gamma-budget '() lem-out)))
  ([fml unexp lits env program proof gamma-budget lem-in lem-out]
  (conde
    ;; ================================================================
    ;; CONJUNCTION (α-rule): expand (and e1 e2)
    ;; ================================================================
    [(fresh [e1 e2 prf]
       (== ['and e1 e2] fml)
       (== (lcons 'conj prf) proof)
       (proveo e1 (lcons e2 unexp) lits env program prf gamma-budget lem-in lem-out))]

    ;; ================================================================
    ;; DISJUNCTION (β-rule): split (or e1 e2) into two branches
    ;; Both branches get the same remaining gamma-budget.
    ;; Lemmas thread left→right: left's lem-out feeds right's lem-in.
    ;; ================================================================
    [(fresh [e1 e2 prf1 prf2 lem-mid]
       (== ['or e1 e2] fml)
       (== ['split prf1 prf2] proof)
       (proveo e1 unexp lits env program prf1 gamma-budget lem-in lem-mid)
       (proveo e2 unexp lits env program prf2 gamma-budget lem-mid lem-out))]

    ;; ================================================================
    ;; UNIVERSAL QUANTIFIER (γ-rule): instantiate (forall (tie a body))
    ;; Generate fresh logic variable, bind a→x, re-enqueue formula.
    ;; BOUNDED: consumes 1 unit of gamma-budget.  When budget is 0,
    ;; this branch of the conde fails, forcing the prover to close
    ;; with existing formulas or try other rules.
    ;; ================================================================
    [(nom a
       (fresh [x body unexp1 prf new-budget]
         (== ['forall (tie a body)] fml)
         ;; Budget check: nil = unbounded, 0 = fail, n>0 = decrement
         (project [gamma-budget]
           (cond
             (nil? gamma-budget)   (== new-budget nil)
             (> gamma-budget 0)    (== new-budget (dec gamma-budget))
             :else                 fail))
         (== (lcons 'univ prf) proof)
         (appendo unexp (list fml) unexp1)
         (proveo body unexp1 lits (lcons [a x] env) program prf new-budget lem-in lem-out)))]

    ;; ================================================================
    ;; ONCE-UNIVERSAL (γ-rule, single-use): instantiate (once-forall (tie a body))
    ;; Produced by negate-formulao for negated existentials.
    ;; Differs from 'forall: does NOT re-enqueue — one instantiation per branch.
    ;; Does NOT consume gamma-budget (it's already single-use).
    ;; ================================================================
    [(nom a
       (fresh [x body prf]
         (== ['once-forall (tie a body)] fml)
         (== (lcons 'once-univ prf) proof)
         (proveo body unexp lits (lcons [a x] env) program prf gamma-budget lem-in lem-out)))]

    ;; ================================================================
    ;; EXISTENTIAL QUANTIFIER (δ-rule): witness (exists (tie a body))
    ;;
    ;; NEW in αleanTAP-EP.  Fitting's classical tableau δ-rule:
    ;; introduce a new parameter (Skolem constant) for the witness.
    ;;
    ;; In nominal logic, a fresh nom IS a new, globally unique name.
    ;; We create a fresh nom `p` and bind a → (par p) in env.
    ;; The (par p) form is a dedicated parameter term, structurally
    ;; distinct from (app f ...) constructor applications.  This
    ;; eliminates the need for any non-relational (project) guard
    ;; in free-closureo: (par p) simply does not match (lcons 'app ...).
    ;;
    ;; Unlike the γ-rule, we do NOT re-enqueue the formula:
    ;; an existential is used exactly once.
    ;; Does NOT consume gamma-budget.
    ;; ================================================================
    [(nom a
       (nom p  ;; fresh parameter — the Skolem witness
         (fresh [body prf]
           (== ['exists (tie a body)] fml)
           (== (lcons 'witness prf) proof)
           (proveo body unexp lits (lcons [a ['par p]] env) program prf gamma-budget lem-in lem-out))))]

    ;; ================================================================
    ;; LITERAL CASES (including Free Closure & Procedure Call Rules)
    ;; ================================================================
    ;; ================================================================
    ;; LITERAL CASES (Type-Dispatched)
    ;; ================================================================
    ;; Branches are grouped by literal type (pos/neg/neq/eq) so the
    ;; type-dispatch unification happens once per group instead of
    ;; once per branch.  Empty streams from non-matching groups are
    ;; transparent to mplus, so result ordering is preserved exactly.
    ;; ================================================================
    [(fresh [lit]
       (subst-lito fml env lit)
       (conde

         ;; ---- POS LITERAL GROUP ----
         [(fresh [tm]
            (== ['pos tm] lit)
            (conde
              ;; Complementary closure
              [(== ['close] proof)
               (membero ['neg tm] lits)
               (== lem-out (lcons ['pos tm] lem-in))]
              ;; Lemma closure (complement found in lemma list)
              [(== ['lem-close] proof)
               (membero ['neg tm] lem-in)
               (== lem-out (lcons ['pos tm] lem-in))]
              ;; Paramodulation closure
              [(fresh [eqs]
                 (== ['para-close] proof)
                 (collect-eqso lits eqs)
                 (eq-membero ['neg tm] lits eqs)
                 (== lem-out (lcons ['pos tm] lem-in)))]
              ;; Procedure call (Fitting §6, Part 1)
              ;; L-GROUND GUARD: soundness requirement for supervaluation
              ;; semantics (Fitting §6 Def 6.1, Theorem 7.2).
              [(fresh [R args params body call-env prf sub-lem]
                 (== (lcons 'app (lcons R args)) tm)
                 (l-ground-term*o args)
                 (lookup-clauseo R program params body)
                 (bind-argso params args call-env)
                 (== (lcons 'proc-call (lcons R prf)) proof)
                 (proveo body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out (lcons ['pos tm] lem-in)))]
              ;; Substitutivity-augmented procedure call
              [(fresh [R args params body call-env prf
                       new-tm new-args eqs sub-lem]
                 (== (lcons 'app (lcons R args)) tm)
                 (collect-eqso lits eqs)
                 (rewrite-term-with-eqso tm eqs new-tm)
                 (== (lcons 'app (lcons R new-args)) new-tm)
                 (lookup-clauseo R program params body)
                 (bind-argso params new-args call-env)
                 (== (lcons 'subst-call (lcons R prf)) proof)
                 (proveo body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out (lcons ['pos tm] lem-in)))]
              ;; Continue expansion (savefml)
              [(fresh [next unexp1 prf]
                 (== (lcons next unexp1) unexp)
                 (== (lcons 'savefml prf) proof)
                 (proveo next unexp1 (lcons lit lits) env program prf gamma-budget lem-in lem-out))]))]

         ;; ---- NEG LITERAL GROUP ----
         [(fresh [tm]
            (== ['neg tm] lit)
            (conde
              ;; Complementary closure
              [(== ['close] proof)
               (membero ['pos tm] lits)
               (== lem-out (lcons ['neg tm] lem-in))]
              ;; Lemma closure (complement found in lemma list)
              [(== ['lem-close] proof)
               (membero ['pos tm] lem-in)
               (== lem-out (lcons ['neg tm] lem-in))]
              ;; Paramodulation closure
              [(fresh [eqs]
                 (== ['para-close] proof)
                 (collect-eqso lits eqs)
                 (eq-membero ['pos tm] lits eqs)
                 (== lem-out (lcons ['neg tm] lem-in)))]
              ;; Procedure call (Fitting §6, Part 2)
              [(fresh [R args params body call-env neg-body prf sub-lem]
                 (== (lcons 'app (lcons R args)) tm)
                 (l-ground-term*o args)
                 (lookup-clauseo R program params body)
                 (bind-argso params args call-env)
                 (negate-formulao body neg-body)
                 (== (lcons 'neg-proc-call (lcons R prf)) proof)
                 (proveo neg-body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out (lcons ['neg tm] lem-in)))]
              ;; Substitutivity-augmented procedure call
              [(fresh [R args params body call-env neg-body prf
                       new-tm new-args eqs sub-lem]
                 (== (lcons 'app (lcons R args)) tm)
                 (collect-eqso lits eqs)
                 (rewrite-term-with-eqso tm eqs new-tm)
                 (== (lcons 'app (lcons R new-args)) new-tm)
                 (lookup-clauseo R program params body)
                 (bind-argso params new-args call-env)
                 (negate-formulao body neg-body)
                 (== (lcons 'neg-subst-call (lcons R prf)) proof)
                 (proveo neg-body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out (lcons ['neg tm] lem-in)))]
              ;; Continue expansion (savefml)
              [(fresh [next unexp1 prf]
                 (== (lcons next unexp1) unexp)
                 (== (lcons 'savefml prf) proof)
                 (proveo next unexp1 (lcons lit lits) env program prf gamma-budget lem-in lem-out))]))]

         ;; ---- NEQ LITERAL GROUP ----
         [(fresh [t1 t2]
            (== ['neq t1 t2] lit)
            (conde
              ;; Reflexivity closure
              [(== t1 t2)
               (== ['refl-close] proof)
               (== lem-out lem-in)]
              ;; NEQ closure via equality rewriting
              ;; Transitivity chains: a=b, b=c closes (neq a c) via a→b→c.
              [(fresh [eqs]
                 (== ['eq-refl-close] proof)
                 (collect-eqso lits eqs)
                 (eq-neq-closeo t1 t2 eqs)
                 (== lem-out lem-in))]
              ;; Continue expansion (savefml)
              [(fresh [next unexp1 prf]
                 (== (lcons next unexp1) unexp)
                 (== (lcons 'savefml prf) proof)
                 (proveo next unexp1 (lcons lit lits) env program prf gamma-budget lem-in lem-out))]))]

         ;; ---- EQ LITERAL GROUP ----
         [(fresh [t1 t2]
            (== ['eq t1 t2] lit)
            (conde
              ;; Free closure (Fitting §5 — Disjointness)
              ;; (eq (app f ...) (app g ...)) with f ≠ g: unsatisfiable.
              ;; Guard: f, g must be constructor symbols, not δ-parameters.
              [(== ['free-close] proof)
               (free-closureo t1 t2)
               (== lem-out lem-in)]
              ;; Arity mismatch closure (Fitting §5 — Free Closure cases 2/3)
              [(== ['arity-mismatch-close] proof)
               (arity-mismatch-closureo t1 t2)
               (== lem-out lem-in)]
              ;; EQ/NEQ complementary closure
              [(== ['eq-neq-close] proof)
               (conde
                 [(membero ['neq t1 t2] lits)]
                 [(membero ['neq t2 t1] lits)])
               (== lem-out lem-in)]
              ;; Injectivity decomposition (Fitting §5 — One-One)
              ;; eq(f(t₁…tₙ), f(s₁…sₙ)) → t₁=s₁ ∧ … ∧ tₙ=sₙ
              [(fresh [f args1 args2 decomposed prf]
                 (== (lcons 'app (lcons f args1)) t1)
                 (== (lcons 'app (lcons f args2)) t2)
                 (fresh [_ __]
                   (== (lcons _ __) args1))
                 (decompose-eq-argso args1 args2 decomposed)
                 (== (lcons 'decompose prf) proof)
                 (proveo decomposed unexp lits env program prf gamma-budget lem-in lem-out))]
              ;; Paramodulated free closure (transitive constructor clash)
              [(fresh [eqs]
                 (== ['para-free-close] proof)
                 (collect-eqso lits eqs)
                 (para-free-closeo t1 t2 eqs)
                 (== lem-out lem-in))]
              ;; EQ-triggered procedure call — positive
              [(fresh [R args params body call-env prf
                       tm new-tm new-args eqs sub-lem]
                 (membero ['pos tm] lits)
                 (== (lcons 'app (lcons R args)) tm)
                 (collect-eqso (lcons lit lits) eqs)
                 (rewrite-term-with-eqso tm eqs new-tm)
                 (== (lcons 'app (lcons R new-args)) new-tm)
                 (lookup-clauseo R program params body)
                 (bind-argso params new-args call-env)
                 (== (lcons 'eq-triggered-call (lcons R prf)) proof)
                 (proveo body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out lem-in))]
              ;; EQ-triggered procedure call — negative
              [(fresh [R args params body call-env neg-body prf
                       tm new-tm new-args eqs sub-lem]
                 (membero ['neg tm] lits)
                 (== (lcons 'app (lcons R args)) tm)
                 (collect-eqso (lcons lit lits) eqs)
                 (rewrite-term-with-eqso tm eqs new-tm)
                 (== (lcons 'app (lcons R new-args)) new-tm)
                 (lookup-clauseo R program params body)
                 (bind-argso params new-args call-env)
                 (negate-formulao body neg-body)
                 (== (lcons 'eq-triggered-neg-call (lcons R prf)) proof)
                 (proveo neg-body '() '() call-env program prf gamma-budget '() sub-lem)
                 (== lem-out lem-in))]
              ;; EQ-triggered NEQ closure
              [(fresh [n1 n2 eqs]
                 (membero ['neq n1 n2] lits)
                 (collect-eqso (lcons lit lits) eqs)
                 (eq-neq-closeo n1 n2 eqs)
                 (== ['eq-triggered-neq-close] proof)
                 (== lem-out lem-in))]
              ;; Continue expansion (savefml) — with par-eq constraint propagation
              [(fresh [next unexp1 prf new-env]
                 (== (lcons next unexp1) unexp)
                 (== (lcons 'savefml prf) proof)
                 (propagate-par-eqo t1 t2 env new-env)
                 (proveo next unexp1 (lcons lit lits) new-env program prf gamma-budget lem-in lem-out))]))]))])))

;; ============================================================================
;; Part 7: Top-Level Interface
;; ============================================================================

(defn- check-program!
  "Validate that a Proflog program conforms to Fitting's Definition 2.1:
   at most one clause per relation symbol of L (except equality).
   Throws IllegalArgumentException on violation."
  [program]
  (let [rels (map first program)
        dups (into {} (filter #(> (val %) 1) (frequencies rels)))]
    (when (seq dups)
      (throw (IllegalArgumentException.
               (str "Invalid Proflog program (Fitting Def 2.1): "
                    "duplicate clause(s) for relation(s) "
                    (pr-str (keys dups))
                    ". Each relation symbol may have at most one clause."))))))

(defn query-succeeds
  "A query A succeeds with program P if there is a closed P-tableau for ¬A.
   (Fitting, Definition 6.1)

   Returns proof(s) if the query succeeds, nil otherwise.
   Optional gamma-budget bounds γ-rule applications (nil = unbounded)."
  ([program query]
   (query-succeeds program query 1))
  ([program query n]
   (query-succeeds program query n nil))
  ([program query n gamma-budget]
   (check-program! program)
   (run n [proof]
     (fresh [neg-query]
       (negate-formulao query neg-query)
       (proveo neg-query '() '() '() program proof gamma-budget)))))

(defn query-fails
  "A query A fails with program P if there is a closed P-tableau for A.
   (Fitting, Definition 6.1)

   Returns proof(s) if the query fails, nil otherwise.
   Optional gamma-budget bounds γ-rule applications (nil = unbounded)."
  ([program query]
   (query-fails program query 1))
  ([program query n]
   (query-fails program query n nil))
  ([program query n gamma-budget]
   (check-program! program)
   (run n [proof]
     (proveo query '() '() '() program proof gamma-budget))))

(defn prove
  "Direct tableau proof: find closed P-tableau for formula.
   For backward compatibility with αleanTAP-E (empty program).
   Optional gamma-budget bounds γ-rule applications (nil = unbounded)."
  ([formula]
   (prove formula 1))
  ([formula n]
   (prove '() formula n))
  ([program formula n]
   (prove program formula n nil))
  ([program formula n gamma-budget]
   (check-program! program)
   (run n [proof]
     (proveo formula '() '() '() program proof gamma-budget))))

;; ============================================================================
;; Part 7b: Iterative Deepening Interface
;; ============================================================================
;;
;; Iterative deepening on γ-budget: try budget 1, 2, 4, 8, ... up to max-budget.
;; Returns the first successful result, or nil if no budget succeeds.
;;
;; This is SOUND at every depth (every proof found is valid) and COMPLETE
;; in the limit for finite domains: if a proof exists at some depth d,
;; iterative deepening will find it at budget ≥ d.
;;
;; The exponential budget sequence (1, 2, 4, 8, ...) ensures that the total
;; work is at most 2x the work of the final successful budget, since
;; 1 + 2 + 4 + ... + 2^k = 2^(k+1) - 1 < 2 * 2^k.

(defn query-succeeds-id
  "Iterative deepening: try query-succeeds with gamma-budget 1, 2, 4, ...
   up to max-budget.  Returns the first proof found, or nil.
   Default max-budget is 64."
  ([program query]
   (query-succeeds-id program query 64))
  ([program query max-budget]
   (loop [budget 1]
     (when (<= budget max-budget)
       (let [result (query-succeeds program query 1 budget)]
         (if (seq result)
           result
           (recur (* 2 budget))))))))

(defn query-fails-id
  "Iterative deepening: try query-fails with gamma-budget 1, 2, 4, ...
   up to max-budget.  Returns the first proof found, or nil.
   Default max-budget is 64."
  ([program query]
   (query-fails-id program query 64))
  ([program query max-budget]
   (loop [budget 1]
     (when (<= budget max-budget)
       (let [result (query-fails program query 1 budget)]
         (if (seq result)
           result
           (recur (* 2 budget))))))))

;; ============================================================================
;; Part 8: Program Construction Helpers
;; ============================================================================
;;
;; Building Proflog programs requires creating clauses with nominal
;; parameters.  These helpers make it more convenient.

(defmacro defclause
  "Define a Proflog clause using nom bindings.
   
   Usage:
     (defclause even-clause 'even [a]
       '(or (eq (var ~a) (app zero))
            (exists ...)))
   
   Expands to a clause vector [rel-symbol [noms...] body]."
  [name rel params & body]
  `(def ~name
     (let [~@(mapcat (fn [p] [p `(clojure.core.logic.nominal/nom (clojure.core.logic/lvar '~p))]) params)]
       [~rel [~@params] ~@body])))

;; ============================================================================
;; Part 9: Worked Examples
;; ============================================================================

(comment
  ;; ==========================================================================
  ;; EXAMPLE 1: Even and Odd (Fitting, Section 2, Program P1)
  ;; ==========================================================================
  ;;
  ;; Language L: constant 'zero, function 's (successor),
  ;;             relations 'even and 'odd (and equality)
  ;;
  ;; Fitting's original:
  ;;   even(x) ← x = 0 ∨ (∃y)[x = s(y) ∧ odd(y)]
  ;;   odd(x)  ← (∀y)[even(y) ⊃ ¬(x = y)]
  ;;
  ;; In our NNF representation, the odd clause body becomes:
  ;;   ∀y. ¬even(y) ∨ ¬(x = y)   [since (A ⊃ B) = (¬A ∨ B) in NNF]
  ;;   = ∀y. (neg (app even (var y))) ∨ (neq (var x) (var y))
  ;;
  ;; Note: The negative literal (neg (app even (var y))) triggers a
  ;; NEGATIVE procedure call — the prover will negate the even body and
  ;; try to refute it, effectively trying to prove even(y) is true,
  ;; which would contradict the assertion ¬even(y).
  ;;
  ;; For simplicity, we can also write odd more directly:
  ;;   odd(x) ← (∃y)[x = s(y) ∧ even(y)]

  ;; --- Using the simpler mutually recursive definition ---
  ;; (This is more natural for demonstration, though Fitting's
  ;;  original uses the ∀/⊃/¬ form to showcase full first-order logic)

  ;; The program must be built inside a `run` form so noms are properly
  ;; scoped within the logic monad.

  ;; Query: is even(s(s(zero))) true?
  ;; We expect SUCCESS because 2 is even.
  (run 1 [proof]
    (nom a b c d  ;; noms for clause params
      (let [even-clause ['even [a]
                         '(or (eq (var a) (app zero))
                              (exists (tie b (and (eq (var a) (app s (var b)))
                                                  (pos (app odd (var b)))))))]
            odd-clause  ['odd [c]
                         '(exists (tie d (and (eq (var c) (app s (var d)))
                                              (pos (app even (var d))))))]
            program     [even-clause odd-clause]
            ;; Query: even(s(s(zero)))
            ;; To check if this succeeds, we need a closed tableau for
            ;; ¬even(s(s(zero))), i.e., (neg (app even (app s (app s (app zero)))))
            query-negated '(neg (app even (app s (app s (app zero)))))]
        (proveo query-negated '() '() '() program proof))))

  ;; Query: is odd(s(s(s(zero)))) true?  (3 is odd — should succeed)
  ;; Query: is even(s(zero)) true?  (1 is not even — should fail or diverge)


  ;; ==========================================================================
  ;; EXAMPLE 2: Nim game (Fitting, Section 2, Program P2)
  ;; ==========================================================================
  ;;
  ;; win(x) ← (∃y)[(x = s(y) ∨ x = s(s(y))) ∧ ¬win(y)]
  ;;
  ;; You can lower the number by 1 or 2.  The player who reaches 0 loses.
  ;; win(n) means: if it's your turn and the number is n, you can win.
  ;;
  ;; win(0) = false   (no move, you lose)
  ;; win(1) = true    (move to 0, opponent loses)
  ;; win(2) = true    (move to 0, opponent can't respond)
  ;; win(3) = false   (move to 1 or 2, both winning for opponent)
  ;; win(4) = true    (move to 3, opponent in losing position)

  ;; Query: does win(s(s(s(zero)))) FAIL? (win(3) should be false)
  ;; We build a closed P2-tableau for win(s(s(s(zero)))).
  (run 1 [proof]
    (nom a b
      (let [win-clause ['win [a]
                         ['exists (tie b
                           ['and ['or ['eq ['var a] ['app 's ['var b]]]
                                      ['eq ['var a] ['app 's ['app 's ['var b]]]]]
                                 ['neg ['app 'win ['var b]]]])]]
            program     [win-clause]
            ;; A closed tableau for win(s(s(s(0)))) shows win(3) is false
            formula     ['pos ['app 'win ['app 's ['app 's ['app 's ['app 'zero]]]]]]]
        (proveo formula '() '() '() program proof))))


  ;; ==========================================================================
  ;; EXAMPLE 3: Backward running — generate even numbers
  ;; ==========================================================================
  ;;
  ;; Because αleanTAP-EP is a pure relation, we can ask:
  ;; "For which x does even(x) succeed?"
  ;;
  ;; (run 5 [x]
  ;;   (nom a b c d
  ;;     (let [program ...]
  ;;       (fresh [neg-query proof]
  ;;         (negate-formulao ['pos ['app 'even x]] neg-query)
  ;;         (proveo neg-query '() '() '() program proof)))))
  ;;
  ;; This should generate: (app zero), (app s (app s (app zero))), ...


  ;; ==========================================================================
  ;; EXAMPLE 4: Equality + Procedure Calls
  ;; ==========================================================================
  ;;
  ;; member(x, cons(x, _))      ← true
  ;; member(x, cons(_, rest))   ← member(x, rest)
  ;;
  ;; In Proflog style (one clause per relation):
  ;; member(x, l) ← (∃h)(∃t)[l = cons(h, t) ∧ (x = h ∨ member(x, t))]
  ;;
  ;; This combines equality reasoning with procedure calls naturally:
  ;; the equality l = cons(h, t) destructures the list, and then either
  ;; x = h (found it) or we recurse.
  )

;; ============================================================================
;; Part 10: Design Notes
;; ============================================================================
;;
;; THE ARCHITECTURE OF A PROCEDURE CALL
;; =====================================
;;
;; When proveo processes a literal (pos (app R args...)):
;;
;;   1. subst-lito replaces noms with their bound values in the current env
;;   2. The result is matched against (pos (app R args...))
;;   3. lookup-clauseo finds R's clause in the program: [R [params] body]
;;   4. bind-argso creates a fresh env: {param₁ → arg₁, ..., paramₙ → argₙ}
;;   5. proveo is called RECURSIVELY on `body` with:
;;        - EMPTY unexp  (fresh proof obligation, not continuation of branch)
;;        - EMPTY lits   (no inherited context — the subsidiary tableau
;;                         is independent)
;;        - FRESH env    (only the clause parameter bindings)
;;        - SAME program (recursive calls are possible)
;;   6. If the subsidiary proveo succeeds (body is unsatisfiable),
;;      the original branch is closed.
;;
;; For the negative case (neg (app R args...)):
;;   Steps 1-4 are the same.
;;   5. negate-formulao computes ¬body in NNF
;;   6. proveo is called on ¬body with fresh state
;;   7. If ¬body is unsatisfiable (body is valid), branch closes.
;;
;;
;; WHY SUBSIDIARY TABLEAUX START FRESH
;; ====================================
;;
;; This is a crucial design point.  The subsidiary tableau does NOT
;; inherit the current branch's literals or unexpanded formulas.
;; This matches Fitting's paper exactly: the Procedure Call Rule
;; says "there exists a closed tableau for φ(t)" — a complete,
;; self-contained tableau, not a continuation of the current one.
;;
;; This has deep consequences:
;;   - Procedure calls are MODULAR: the subsidiary proof is independent
;;   - No "spooky action at a distance" between branches
;;   - The soundness proof (Fitting, Section 7) depends on this isolation
;;
;; However, unification variables CAN flow between the calling and
;; subsidiary tableaux (via the argument terms).  This is how
;; procedure calls communicate results back: the subsidiary proof
;; may instantiate logic variables that appear in the caller's context.
;;
;;
;; THE δ-RULE (EXISTENTIAL QUANTIFIER)
;; ====================================
;;
;; αleanTAP originally only needed the γ-rule (∀) because input
;; formulas were pre-Skolemized.  Proflog clause bodies can contain
;; ∃ (as in the win and even examples), and more critically,
;; negate-formulao turns ∀ into ∃ (for negative procedure calls).
;; So the δ-rule is essential.
;;
;; Our δ-rule uses a fresh nom as the Skolem parameter, wrapped in
;; (app p) to make it a proper term.  The nom is globally unique,
;; satisfying the requirement that the parameter be "new" (not
;; occurring elsewhere on the branch).
;;
;; Key difference from the γ-rule:
;;   - γ (∀): introduces a LOGIC VARIABLE (can be unified later)
;;            and RE-ENQUEUES the formula for potential re-instantiation
;;   - δ (∃): introduces a FIXED PARAMETER (nom, cannot be unified)
;;            and does NOT re-enqueue (one witness suffices)
;;
;;
;; INTERACTION WITH EQUALITY
;; ==========================
;;
;; Equality and procedure calls interact in three important ways:
;;
;; 1. EQUALITY IN CLAUSE BODIES:
;;    Clause bodies can use (eq ...) and (neq ...) freely.
;;    The even/odd example uses x = 0 and x = s(y).
;;    The member example uses l = cons(h, t) for list destructuring.
;;    All equality rules (reflexivity, paramodulation, free closure)
;;    are available inside subsidiary tableaux.
;;
;; 2. FITTING'S FREE CLOSURE RULE (Section 5):
;;    Weak Herbrand models require that distinct function symbols have
;;    non-overlapping ranges and are injective.  This means:
;;      - 0 ≠ s(x)         for any x  (disjointness / clash)
;;      - f(x) ≠ g(y)      if f ≠ g   (different function symbols)
;;      - f(x) = f(y) → x = y         (injectivity / decomposition)
;;    Our implementation provides:
;;      (a) FREE CLOSURE (clash): (eq (app f ...) (app g ...)) with f ≠ g
;;          closes the branch immediately.  Proof step: 'free-close.
;;          SOUNDNESS GUARD: uses `project` to verify both heads are
;;          genuine Clojure symbols, not δ-parameter noms.  A nom
;;          represents an arbitrary domain element and could denote any
;;          term, so (eq (app nom_p) (app s x)) must NOT clash.
;;      (b) INJECTIVITY DECOMPOSITION: (eq (app f t₁..tₙ) (app f s₁..sₙ))
;;          with same head f EXPANDS into a conjunction of sub-equalities
;;          (and (eq t₁ s₁) ... (eq tₙ sₙ)).  This creates actual
;;          sub-formulas that enter the proof search, enabling cascading
;;          decomposition: f(g(a)) = f(g(b)) → g(a) = g(b) → a = b
;;          → free-close.  Proof step: 'decompose.
;;      (c) ONE-ONE PAIRS IN PARAMODULATION: The same injectivity principle
;;          also injects pairwise sub-equalities [tᵢ, sᵢ] into the
;;          rewriting engine via enhanced collect-eqso, enabling
;;          paramodulation and substitutivity to use derived equalities
;;          without explicit formula expansion.
;;      (d) NEQ CLOSURE: (neq t1 t2) on the branch, combined with
;;          one-one derived equalities, can rewrite t1 → t2 to yield
;;          (neq t t) → refl-close.  Proof step: 'eq-refl-close.
;;      (e) EQ/NEQ COMPLEMENTARY CLOSURE: When (eq t1 t2) is the current
;;          literal and (neq t1 t2) or (neq t2 t1) is already on the
;;          branch, the contradiction is detected directly.  This prevents
;;          order-dependent failures where the neq was processed first.
;;          Proof step: 'eq-neq-close.
;;
;; 3. SUBSTITUTIVITY-AUGMENTED PROCEDURE CALLS:
;;    When a δ-rule introduces a fresh parameter p and the branch has
;;    an equality like s(zero) = s(p), one-one decomposition yields the
;;    pair [zero, p].  If the current literal is (pos (app odd (app p))),
;;    substitutivity rewrites (app p) → (app zero) in the arguments,
;;    enabling a procedure call on odd(zero) instead of the rigid odd(p).
;;    Without this, subsidiary tableaux would receive parameters they
;;    cannot resolve.  Proof steps: 'subst-call, 'neg-subst-call.
;;
;;    Multi-argument support: for terms with multiple arguments like
;;    member(p₁, p₂), each argument is independently rewritable using
;;    a possibly different equality pair from the branch.  The relations
;;    rewrite-args-someo / rewrite-args-maybeo handle this by mapping
;;    over the argument list, with someo guaranteeing at least one
;;    argument is actually rewritten (preventing overlap with plain
;;    procedure call rules).  Essential for binary constructors like
;;    cons and multi-arity relations.
;;
;;
;; THREE-VALUED SEMANTICS AND DIVERGENCE
;; ======================================
;;
;; Fitting's Proflog uses three-valued supervaluation semantics:
;;   - true:  query succeeds (closed tableau for ¬A exists)
;;   - false: query fails    (closed tableau for A exists)
;;   - ⊥:     undefined      (neither tableau closes — infinite search)
;;
;; In our implementation, the ⊥ case manifests as non-termination of
;; the core.logic search.  This is analogous to how Prolog loops on
;; undefined queries.  The key example from Fitting:
;;
;;   p ← ¬p
;;
;; Neither p nor ¬p can be established — the procedure call creates
;; an infinite chain of subsidiary tableaux.  In our relational
;; setting, `run 1` will simply not return.
;;
;;
;; ON FITTING'S "GROUND ATOM OF L" RESTRICTION
;; =============================================
;;
;; Fitting requires that procedure calls apply only to ground atoms
;; of L (not Lpar — the language extended with parameters).  This
;; restriction is enforced by l-ground-term*o on the plain call rules.
;;
;; WHY THE GUARD IS A SOUNDNESS REQUIREMENT:
;;
;; The supervaluation biconditional R(t) ↔ φ(t) holds only for
;; ground terms t of L (Definition 3.5).  For a δ-parameter p
;; (element of L^par \ L), R(p) is UNCONSTRAINED — different weak
;; Herbrand models may assign R(p) different truth values.  The
;; supervaluation assigns R(p) = ⊥ (undefined).
;;
;; Without the guard, a procedure call on R(p) would treat R(p) ↔ φ(p)
;; as holding, which can produce results that are true in the free
;; Herbrand model (CWA) but NOT true in all weak Herbrand models
;; (supervaluation).  This violates Lemma 7.5 and breaks Theorem 7.2
;; (t_P = s_P).
;;
;; Concrete example: R(x) ← x=x.  Query: ∀x.R(x).
;;   Without guard: ∃x.¬R(x) → δ → ¬R(p) → neg-call fires →
;;     subsidiary for neq(p,p) → refl-close → closes → true.
;;   With guard: ¬R(p) → neg-call blocked (p not L-ground) → ⊥.
;;   Under s_P: ⊥ (models exist where R(d)=false for non-standard d).
;;   Under CWA: true (biconditional extends to all terms).
;;
;; LOGIC VARIABLES pass through the guard transparently: project
;; inspects the walked value and sees an LVar, which contains-par?
;; correctly classifies as not containing par.  This enables synthesis.
;;
;; SUBSTITUTIVITY-AUGMENTED call rules are exempt: they rewrite par
;; terms to L-ground terms before firing, so the call arguments are
;; L-ground by construction after rewriting.
;;
;; See `closed-world-assumption` branch for CWA variant (guard removed).
;;
;;
;; COMPARISON WITH PROLOG
;; =======================
;;
;; Proflog differs from Prolog in several fundamental ways:
;;
;;   PROLOG                          PROFLOG (αleanTAP-EP)
;;   ─────                           ──────
;;   Horn clauses only               Full first-order logic in bodies
;;   SLD-resolution                  Tableau expansion + procedure call
;;   Closed-world assumption         Open-world (supervaluation)
;;   Negation as failure             Classical negation (¬ in bodies)
;;   Definite clause semantics       Three-valued supervaluation model
;;   One direction (forward only)    Pure relation (forward, backward,
;;                                    sideways)
;;
;; The cost: Proflog is less efficient than Prolog, because full
;; first-order tableau expansion is more expensive than SLD-resolution.
;; The gain: Proflog is more expressive and more declarative.
;;
;; ============================================================================
