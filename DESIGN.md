# αleanTAP-EP: Implementing Fitting's Proflog in αleanTAP

## The Central Idea

Fitting's 1994 paper poses a deceptively simple question: what does it take to turn a tableau *theorem prover* into a *programming language*? His answer is a single new rule — the **Procedure Call Rule** — which bridges the two worlds by allowing a tableau branch to close not just by finding contradictions, but by *invoking a program definition*.

The result, Proflog, was proposed but never implemented. αleanTAP-EP is (to our knowledge) its first realization, and the fact that αleanTAP is a pure relation in nominal logic gives it capabilities Fitting only hinted at.

## The Procedure Call Rule

From Fitting's paper (Section 6), here is the rule verbatim:

> **Procedure Call Rule.** A tableau branch is closed if:
>
> 1. it contains a ground atom R(t₁,...,tₙ) of L, there is a clause R(x₁,...,xₙ) ← φ(x₁,...,xₙ) in P, and there exists a closed tableau for φ(t₁,...,tₙ);
>
> 2. it contains a negated ground atom ¬R(t₁,...,tₙ) of L, there is a clause R(x₁,...,xₙ) ← φ(x₁,...,xₙ) in P, and there exists a closed tableau for ¬φ(t₁,...,tₙ).

To understand why each part works, recall that a Proflog clause R(x) ← φ(x) is semantically biconditional: **R(t) is true iff φ(t) is true**, in the smallest supervaluation model.

**Part 1 (positive call):** R(t) sits on the branch, asserting it is the case. The subsidiary tableau closes on φ(t), establishing that φ(t) is unsatisfiable. But R(t) ↔ φ(t), so R(t) must also be false. Contradiction — the branch closes.

**Part 2 (negative call):** ¬R(t) sits on the branch, asserting R(t) is false. The subsidiary tableau closes on ¬φ(t), establishing that φ(t) is valid (always true). But R(t) ↔ φ(t), so R(t) must be true. Contradiction with ¬R(t) — the branch closes.

The critical structural feature: each procedure call spawns a **fresh, independent subsidiary tableau**. It does not inherit the calling branch's literals, unexpanded formulas, or environment. It carries only the program P (for recursive calls) and the instantiated clause body. This isolation is what makes the soundness proof work (Fitting, Section 7, Lemma 7.5).

## Architecture of a Procedure Call in αleanTAP-EP

Here is the anatomy of what happens when `proveo` encounters a literal that matches a program clause head:

```
proveo encounters literal (pos (app R t₁ t₂ ...))
  │
  ├─ 1. subst-lito replaces noms with their values from current env
  │
  ├─ 2. lookup-clauseo finds:  [R [a₁ a₂ ...] body]  in program
  │
  ├─ 3. bind-argso creates fresh env:  {a₁→t₁, a₂→t₂, ...}
  │
  └─ 4. SPAWN SUBSIDIARY TABLEAU:
         proveo body         ← clause body (or ¬body for negative call)
                '()          ← empty unexp   (fresh obligation)
                '()          ← empty lits    (no inherited context)
                call-env     ← param→arg bindings only
                program      ← same program  (recursion possible)
                prf          ← subsidiary proof term
         │
         ├─ The subsidiary tableau expands body using all rules:
         │   α (conjunction), β (disjunction), γ (universal),
         │   δ (existential), complementary closure, equality,
         │   AND further procedure calls (recursion!)
         │
         └─ If subsidiary closes → original branch closes
            If subsidiary doesn't close → try other closure methods
```

## What Had to Change

### 1. The `proveo` Signature Gains a `program` Argument

Old: `(proveo fml unexp lits env proof)`

New: `(proveo fml unexp lits env program proof)`

The program is threaded through all recursive calls unchanged. Every expansion rule (α, β, γ, δ, literal) passes `program` along. Subsidiary tableaux receive the same program, enabling recursion.

### 2. The δ-Rule (Existential Quantifier)

αleanTAP originally only needed the γ-rule (∀) because inputs were pre-Skolemized. Proflog clause bodies contain ∃ (e.g., Fitting's `win(x) ← ∃y[...]`), and `negate-formulao` turns ∀ into ∃ (for negative calls). The δ-rule is therefore essential.

Our δ-rule exploits nominal logic: a fresh nom is a globally unique name — precisely what Fitting calls a "parameter." We encode the Skolem witness as `(par p)` — a dedicated term form structurally distinct from `(app f ...)` constructor applications:

```clojure
;; δ-rule: (exists (tie a body))
[(nom a
   (nom p  ;; fresh parameter
     (fresh [body prf]
       (== ['exists (tie a body)] fml)
       (== (lcons 'witness prf) proof)
       (proveo body unexp lits
               (lcons [a ['par p]] env)  ;; a → (par p)
               program prf))))]
```

The `(par p)` term form is part of the term grammar:
- `(var nom)` — variable reference, substituted by γ/δ environment lookup
- `(app symbol term*)` — constructor/function application (symbol is a Clojure symbol)
- `(par nom)` — Skolem parameter, introduced by δ-rule, globally unique and rigid

This distinction matters for `subst-termo` (which passes `(par p)` through unchanged) and for `free-closureo` (which only matches `(lcons 'app ...)` terms — so `(par p)` can never trigger free closure, eliminating the need for any non-relational guard).

`subst-termo` uses `project`-based type dispatch to handle four cases: `(var nom)` → environment lookup; `(par p)` → pass-through; `(app f ...)` (LCons) → recursive substitution across args; raw logic variable (synthesis target from `run`) → pass-through unchanged. The fourth case is essential for program and query synthesis: without it, a synthesis-target LVar placed in a formula term position would be incorrectly consumed by the `(var nom)` branch. The `project` is sound here because `subst-termo` is always called in forward mode (formula → substituted formula), never in reverse.

Key differences from the γ-rule:

| | γ-rule (∀) | δ-rule (∃) |
|---|---|---|
| Introduces | Logic variable (unifiable) | Nom/parameter (rigid) |
| Re-enqueues formula? | Yes (may need multiple instances) | No (one witness suffices) |
| In proof term | `univ` | `witness` |

### 3. `negate-formulao` — NNF-Preserving Negation

The negative procedure call needs ¬φ for a clause body φ. Since our prover works in NNF, we push negation inward:

```
¬(and A B)          = (or ¬A ¬B)
¬(or A B)           = (and ¬A ¬B)
¬(forall a.P)       = (exists a.¬P)      ← this is why we need δ
¬(exists a.P)       = (forall a.¬P)
¬(pos t)            = (neg t)
¬(neg t)            = (pos t)
¬(eq t₁ t₂)        = (neq t₁ t₂)
¬(neq t₁ t₂)       = (eq t₁ t₂)
```

Because this is a pure relation, it also works backwards: given a negated formula, synthesize the original. This supports αleanTAP-EP's backward-running capability.

### 4. `lookup-clauseo` and `bind-argso`

These are straightforward relational helpers. `lookup-clauseo` searches the program list for a clause matching a given relation symbol. `bind-argso` zips clause parameter noms with actual argument terms to produce an environment.

### 5. Two New `conde` Clauses in `proveo`'s Literal Processing

The procedure call rule adds two new ways to close a branch when processing a literal, alongside the existing complementary closure, reflexivity, and paramodulation.

**The L-groundness guard** (Fitting §6 Definition 6.1): the plain call rules may only fire on ground atoms of L — atoms whose arguments contain no δ-parameters `(par p)`. The `l-ground-term*o` guard enforces this. It uses `project` to inspect argument terms without unifying them, so unbound γ-rule logic variables (which are not par-terms) pass through transparently. Substitutivity-augmented call rules (which rewrite `(par p)` to ground terms *before* firing) are exempt from this guard.

**Why the guard is essential for supervaluation soundness:** The biconditional R(t) ↔ φ(t) holds only for ground terms t of L (Definition 3.5), not for δ-parameters in L^par. A parameter p can denote a "non-standard" domain element where R(p) is unconstrained by the program. Without the guard, the system could prove ∀x.R(x) for R(x)←x=x (via neg-call on R(p), subsidiary neq(p,p) closes by refl), but this is ⊥ under supervaluation because some weak Herbrand models have non-standard d with R(d)=false. The guard blocks this, preserving Theorem 7.2 (t_P = s_P). The `closed-world-assumption` branch removes this guard, implementing CWA/Clark completion semantics instead.

```clojure
;; Positive procedure call (plain — L-ground args required)
[(fresh [R args params body call-env prf]
   (== ['pos (lcons 'app (lcons R args))] lit)
   (l-ground-term*o args)                ;; Fitting §6 Def 6.1: no (par p) in args
   (lookup-clauseo R program params body)
   (bind-argso params args call-env)
   (== (lcons 'proc-call (lcons R prf)) proof)
   (proveo body '() '() call-env program prf))]

;; Negative procedure call (plain — L-ground args required)
[(fresh [R args params body call-env neg-body prf]
   (== ['neg (lcons 'app (lcons R args))] lit)
   (l-ground-term*o args)                ;; Fitting §6 Def 6.1: no (par p) in args
   (lookup-clauseo R program params body)
   (bind-argso params args call-env)
   (negate-formulao body neg-body)
   (== (lcons 'neg-proc-call (lcons R prf)) proof)
   (proveo neg-body '() '() call-env program prf))]
```

## Fitting's Examples, Traced

### Nim (Program P2)

```
win(x) ← ∃y. (x = s(y) ∨ x = s(s(y))) ∧ ¬win(y)
```

**Query: does win(3) fail?** We build a P-tableau for `win(s(s(s(0))))`.

```
proveo (pos (app win (app s (app s (app s (app zero))))))
  │
  └─ POSITIVE PROC-CALL on 'win:
     └─ subsidiary: proveo body[x := s(s(s(0)))]
        = (exists (tie b (and (or (eq s³(0) s(b)) (eq s³(0) s²(b)))
                              (neg (app win (var b))))))
        │
        ├─ δ-rule: introduce witness parameter p for b
        │  env: {b → (app p)}
        │
        ├─ α-rule (and): expand conjunction
        │
        ├─ β-rule (or): split on x = s(y) vs x = s(s(y))
        │
        ├─ Left branch: s³(0) = s(p)  →  p = s²(0)  →  ¬win(s²(0))
        │   └─ NEGATIVE PROC-CALL on 'win:
        │      └─ subsidiary: proveo ¬body[x := s²(0)]
        │         = (forall (tie c (or (and (neq s²(0) s(c)) (neq s²(0) s²(c)))
        │                              (pos (app win (var c))))))
        │         │
        │         ├─ γ-rule: instantiate c with logic variable
        │         │  ... eventually c unifies with 0
        │         │
        │         └─ s²(0) = s²(0) is reflexively true
        │            → win(0) must hold for this branch
        │            → POSITIVE PROC-CALL on 'win with 0
        │              → body[x:=0] has (0 = s(y) ∨ 0 = s²(y))
        │              → both disjuncts closed by Free Closure (0 ≠ s(...))
        │              → subsidiary closes → win(0) is false → branch closes
        │
        └─ Right branch: similar, reaching ¬win(s(0))
           └─ ... eventually closes similarly
```

The proof term records the entire call tree, showing how recursive procedure calls bottom out at `win(0)`.

### Even/Odd (Program P1)

Fitting's P₁ (paper §2):
```
even(x) ← x = 0 ∨ (∃y)[x = s(y) ∧ odd(y)]
odd(x)  ← (∀y)[even(y) ⊃ ¬(x = y)]
```

The `odd` clause uses a universal quantifier and implication, showcasing the full first-order expressiveness of Proflog. For the trace below we use the equivalent mutually-recursive formulation (simpler to follow; Fitting notes the two are interchangeable in the supervaluation model):
```
even(x) ← x = 0 ∨ ∃y. (x = s(y) ∧ odd(y))
odd(x)  ← ∃y. (x = s(y) ∧ even(y))
```

**Query: does even(s(s(0))) succeed?** We build a closed P-tableau for `¬even(s²(0))`, which is `(neg (app even (app s (app s (app zero)))))`.

```
proveo (neg (app even (app s (app s (app zero)))))
  │
  └─ NEGATIVE PROC-CALL on 'even:
     Need closed tableau for ¬body[x := s²(0)]
     ¬(s²(0)=0 ∨ ∃y.(s²(0)=s(y) ∧ odd(y)))
     = (s²(0)≠0 ∧ ∀y.(s²(0)≠s(y) ∨ ¬odd(y)))
     │
     ├─ α: expand conjunction
     ├─ s²(0) ≠ 0: stays on branch (true by free closure)
     ├─ γ: instantiate y with logic variable v
     │  ∀y.(s²(0)≠s(y) ∨ ¬odd(y)) → s²(0)≠s(v) ∨ ¬odd(v)
     │
     ├─ β: split
     │   ├─ Left:  s²(0) ≠ s(v)  — but v can unify with s(0)
     │   │   → s(s(0)) = s(s(0)) — reflexivity closes via refl-close
     │   │   Wait: neq, so it becomes (neq s²(0) s(v))
     │   │   If v = s(0): (neq s²(0) s²(0)) → refl-close!
     │   │
     │   └─ Right: ¬odd(v)
     │       v unifies with s(0)
     │       (neg (app odd (app s (app zero))))
     │       └─ NEGATIVE PROC-CALL on 'odd:
     │          ¬body[x := s(0)] = ¬∃y.(s(0)=s(y) ∧ even(y))
     │          = ∀y.(s(0)≠s(y) ∨ ¬even(y))
     │          │
     │          ├─ γ: instantiate y with v₂
     │          ├─ β: split
     │          │   ├─ s(0)≠s(v₂): if v₂=0, refl-close
     │          │   └─ ¬even(v₂): v₂=0
     │          │       (neg (app even (app zero)))
     │          │       └─ NEGATIVE PROC-CALL on 'even:
     │          │          ¬(0=0 ∨ ∃y.(0=s(y) ∧ odd(y)))
     │          │          = (0≠0 ∧ ∀y.(0≠s(y) ∨ ¬odd(y)))
     │          │          → 0≠0 → refl-close!
     │          │          subsidiary CLOSES → even(0) is true
     │          │          → ¬even(0) is contradicted → branch closes
     │          ...
     └─ All branches close → query succeeds: even(s²(0)) is true ✓
```

## Interaction with Equality

Equality and procedure calls interact naturally in αleanTAP-EP because both are available in subsidiary tableaux:

**Equality in clause bodies.** Clauses like `member(x,l) ← ∃h.∃t. l = cons(h,t) ∧ (x=h ∨ member(x,t))` use equality for pattern matching / destructuring — the same role `=` plays in Prolog unification, but expressed declaratively.

**Fitting's Free Closure Rule (implemented).** Weak Herbrand models require that distinct constructors produce distinct values: `0 ≠ s(x)` and `f(x) ≠ g(y)` when f ≠ g, and that constructors are injective: `f(t) = f(s)` implies `t = s`. Our implementation provides six rules that fully realize Fitting's Section 5:

1. **Free Closure (disjointness/clash):** `(eq (app f ...) (app g ...))` with f ≠ g closes the branch immediately (proof step `free-close`). The implementation uses the purely relational disequality constraint `(!= f g)` — no non-relational `project` is needed because δ-parameters are encoded as `(par p)` (not `(app p)`), so they structurally cannot match the `(lcons 'app ...)` pattern in `free-closureo`.

2. **Injectivity Decomposition (formula expansion):** `(eq (app f t₁..tₙ) (app f s₁..sₙ))` with same head f is expanded into a conjunction `(and (eq t₁ s₁) ... (eq tₙ sₙ))` which is then processed as a new formula (proof step `decompose`). This cascades: `f(g(a)) = f(g(b))` → `g(a) = g(b)` → `a = b` → free-close. The `(par p)` encoding means δ-parameters never appear as heads of `(app ...)` terms, so no `project` guard is needed to distinguish constructor symbols from parameters.

3. **One-One Pairs in Paramodulation:** The same injectivity principle also injects pairwise sub-equality pairs `[tᵢ, sᵢ]` into the rewriting engine via enhanced `collect-eqso`, enabling paramodulation and substitutivity to use derived equalities without explicit formula expansion.

4. **Eq/Neq Complementary Closure:** When `(eq t₁ t₂)` is the current literal and `(neq t₁ t₂)` or `(neq t₂ t₁)` is already on the branch, the contradiction is detected directly (proof step `eq-neq-close`). This prevents order-dependent failures where the neq was processed first and saved before the eq arrived.

5. **NEQ Closure via Equality Rewriting:** `(neq t₁ t₂)` on the branch, combined with one-one derived equalities, can be rewritten so that t₁ becomes t₂, yielding `(neq t t)` → reflexivity closure (proof step `eq-refl-close`).

6. **Substitutivity-Augmented Procedure Calls:** When branch equalities (including one-one pairs) can rewrite a literal's argument terms, the rewriting is applied before firing the procedure call (proof steps `subst-call`, `neg-subst-call`). Each argument position is independently rewritable using a different equality pair, via `rewrite-args-someo` / `rewrite-args-maybeo`. This handles both unary cases (e.g., `odd(p)` → `odd(zero)`) and multi-argument cases (e.g., `member(p₁, p₂)` → `member(a, cons(b, nil))` when both args have known equalities on the branch). At least one argument must be actually rewritten, preventing overlap with plain procedure call rules.

**Equality + recursion.** The combination is powerful: a recursive clause can use equality to destructure its arguments and equality reasoning to close base cases, while procedure calls provide the recursive step. This is essentially what makes Proflog a programming language rather than just a prover.

## Comparison: Prolog vs Proflog (αleanTAP-EP)

| Dimension | Prolog | Proflog |
|---|---|---|
| **Clause bodies** | Conjunctions of atoms (Horn) | Any first-order formula |
| **Proof engine** | SLD-resolution | Tableau expansion + procedure call |
| **Negation** | Negation-as-failure (extralogical) | Classical ¬ in bodies (logical) |
| **Semantics** | Minimal Herbrand model | Smallest supervaluation model |
| **World assumption** | Closed (CWA) | Open (non-standard elements possible) |
| **Equality** | Built-in unification | Tableau equality rules (paramodu­lation, free closure) |
| **Directionality** | Forward only (input → output) | Pure relation (forward, backward, sideways) |
| **Undefinedness** | Loops (operational) | ⊥ (semantic third truth value) |

The cost of Proflog's generality is efficiency — full tableau expansion is more expensive than SLD-resolution. As Fitting notes in Section 8, practical use would require finding "the most natural compromises" — much as Prolog was extracted from the abstract logic programming paradigm by accepting incompleteness for efficiency.

## New Proof Steps

| Step | Meaning |
|---|---|
| `(proc-call R . prf)` | Positive call: R(t) on branch, closed subsidiary for body(t) |
| `(neg-proc-call R . prf)` | Negative call: ¬R(t) on branch, closed subsidiary for ¬body(t) |
| `(subst-call R . prf)` | Substitutivity-augmented positive proc call (args rewritten) |
| `(neg-subst-call R . prf)` | Substitutivity-augmented negative proc call (args rewritten) |
| `(witness . prf)` | Existential witness introduction (δ-rule) |
| `(free-close)` | Free closure: distinct constructors clash |
| `(eq-neq-close)` | Eq/neq complementary closure |
| `(decompose . prf)` | Injectivity decomposition: same-head eq → sub-equalities |
| `(eq-refl-close)` | Neq closed via one-one derived equalities |

These nest naturally with existing steps (`conj`, `split`, `univ`, `close`, `refl-close`, `para-close`, `savefml`), producing proof terms that record the entire call tree including recursive invocations.

## Open Questions from Fitting's Paper

Fitting explicitly leaves several questions open (Section 8). Our implementation addresses some and inherits others:

**"What restrictions on the language will lead to efficiency without losing naturalness?"** — This remains the central practical question. One natural restriction: limit clause bodies to the `∧`/`∨`/`¬`/`∃` fragment (no ∀ in bodies), which avoids the γ-rule's re-enqueueing in subsidiary tableaux.

**Free variables in queries.** Fitting notes that free-variable queries require ensuring that answers are terms of L, not of L^par (the parameter-extended language). In our setting, core.logic's reification handles this: the `run` interface reports logic variable bindings, and noms introduced by δ-rules will appear as distinct symbols if they leak into answers, which is the correct behavior.

**Disunification with free variables (implemented, tested).** Fitting identifies the systematic generation of disunifiers as "perhaps the most intractable implementation issue." Our implementation handles this through the **free-close + decompose cascade**: when `(eq t1 t2)` appears on a branch and one or both sides contain free (logic) variables, `free-closureo` generates root-level disunifiers by binding LVars to `(app s ...)` terms with `(!= s1 s2)` constraints, while the decompose rule generates same-head cases that recurse into sub-equalities at deeper positions. core.logic's `!=` constraint propagation automatically merges disunification constraints from multiple `(eq ...)` literals on the same branch. The non-deterministic search (`conde`) explores both free-close and decompose branches fairly, generating disunifiers at every depth level — root clash, depth-1, depth-2, etc. — bounded by term depth on the concrete side. This is the "incomplete but efficient mechanism" Fitting recommends (§8): it handles all structural disunification cases systematically, while the sources of incompleteness (enumeration of all possible arities for LVar-headed sub-terms) are well understood. Section DI of the test suite (25 tests) verifies the cascade across unit tests, Proflog program contexts, synthesis mode, multiple constraints, universally quantified bodies, interaction with equality rewriting, and both-LVar edge cases.

**Modal and many-valued extensions.** Fitting suggests that replacing classical tableaux with modal or many-valued ones could yield modal logic programming languages "as a uniform mechanism." αleanTAP-EP's architecture — a generic `proveo` relation parameterized by rules and a program — would naturally support this.

## Demonstrated Capabilities

**Backward running (tested).** Because proveo is a pure relation, queries with logic variable arguments generate satisfying values. For example, `(run 3 [x] ...)` with `color(x) ← x=red ∨ x=green ∨ x=blue` yields all three colors. With the even/odd program, `(run 1 [x] ...)` for `even(x)` yields `(app zero)`. The mechanism: `refl-close` on `(neq X (app zero))` unifies X with `(app zero)`, closing the branch and reporting the binding.

**Program synthesis (tested).** Placing a logic variable as the entire `program` argument synthesizes a program that proves the query — `proveo` finds the clause structure. Similarly, placing logic variables inside clause bodies synthesizes the body terms. Joint synthesis (`run 1 [prog q]` with both program and query unbound) finds a mutually consistent (program, query) pair. These modes exercise the relational core in full reverse: `lookup-clauseo`, `bind-argso`, `negate-formulao`, `subst-termo`, and all closure rules compose correctly when inputs are unbound. The `project`-based dispatch in `subst-termo` is the key enabler: it passes synthesis-target LVars through unchanged rather than consuming them as `(var nom)` forms.

**Deep synthesis with procedure calls (tested).** Query and program LVars flow through recursive procedure call chains of arbitrary depth (Sections V and W of the test suite). The L-groundness guard is transparent to these LVars: `project` inspects the walked value and sees an `LVar` object, which `contains-par?` correctly classifies as ground. Tests V13 and V14 specifically verify that program-LVar arguments (not just query-LVar or γ-rule-LVar arguments) pass the guard at every nesting level.

**List membership (tested).** `member(x, l) ← ∃h.∃t. l = cons(h,t) ∧ (x=h ∨ member(x,t))` exercises binary constructors, nested existentials (two δ-rules per call), and recursive procedure calls through list structure. Tested for head and recursive membership, empty-list failure.

**Nim game, full (tested).** `win(x) ← ∃y.((x=s(y) ∨ x=s(s(y))) ∧ ¬win(y))` exercises mutual recursion through the negative procedure call. win(0) fails, win(1) and win(2) succeed, win(3) fails — all verified. The correctness proof relies on the visited-set termination guarantee (see below).

**List programs — append and reverse (tested, Section Y).** `append(a1,a2,a3)` and `reverse(r1,r2)` are encoded as single OR-body clauses per Fitting's Definition 2.1. Each Prolog two-clause definition becomes `or(base-case, ∃-recursive-case)`. The empty-list constant is `(app nul)` — the symbol `'nul` rather than `'nil`, because Clojure evaluates `'nil` to the null value rather than a symbol. "Succeeds" tests use neg-call with L-ground concrete arguments; the closed tableau is found via refl-close on `or(neq(nul,nul), neq([x],[x]))` in the negated base case without even reaching the negated recursive case. "Fails" tests use pos-call; the base branch closes via free-close (`eq([a],nul)` — distinct constructors), and the recursive branch closes via free-close (`eq(nul,cons(par_h,par_t))`). Synthesis tests (Y05 for append, Y10 for reverse) place a logic variable as the result argument; the L-groundness guard passes it (project→LVar→contains-par?=false), and the recursive neg-call chain binds it via refl-close. **NNF ordering rule applies:** equality constraints must precede pos-calls in every AND-chain (see Section X / Fitting's P1 note below).

**Disunification with free variables (tested, Section DI).** When `(eq t1 t2)` appears on a branch and one or both sides contain free logic variables (from γ-rule instantiation or synthesis queries), the free-close + decompose cascade generates disunifiers at every depth. For `(eq (app f (app a)) y)` where `y` is an LVar: free-close generates root-level clashes (`y` has head ≠ `f`), decompose generates depth-1 clashes (`y = f(b)` where `b` has head ≠ `a`), and deeper decomposition continues recursively. Tests verify: root/depth-1/depth-2 disunifiers (DI05–DI08), concrete forward proofs (DI09–DI13), synthesis with single and multiple disunification constraints (DI14–DI19), interaction with universally quantified bodies (DI20–DI21), combined equality rewriting and disunification (DI22–DI23), and both-LVar edge cases (DI24–DI25).

**Adversarial analysis (tested, Section ADV).** Systematic adversarial testing probed the gap between Fitting's logical specification and the computational implementation. Key findings:

- *NNF disjunct ordering (AV1) — RESOLVED*: `A ∨ B ≡ B ∨ A` in classical logic, but the α-rule processes conjuncts left-to-right after negation. The original subst-call rules only handled the case where `pos`/`neg` is the current literal and equalities are in `lits`. The **eq-triggered procedure call** rules (eq-triggered-call, eq-triggered-neg-call) handle the reverse: when the current literal is `['eq t1 t2]` and a `pos`/`neg` literal is saved in `lits`, the rule uses `membero` to find the saved literal, `collect-eqso` (including the current eq) to gather equality pairs, `rewrite-term-with-eqso` to rewrite the saved literal's args, then fires the procedure call. This makes proof search commutative w.r.t. eq/pos ordering. Tests ADV01–ADV04 all pass. The issue does NOT arise with `∃` bodies: `once-forall` binds to an LVar (not par), and the β-split produces independent branches (ADV04b/04c).

- *Arity mismatch (AV2)*: `f(a)` and `f()` are distinct terms in Herbrand semantics, but `free-closureo` checks head symbols only (same `f` → fails) and `decompose-eq-argso` requires paired argument lists (mismatched lengths → fails). Neither rule fires on `(eq f(a) f())`. This is formally out of spec (Fitting assumes fixed arities per symbol) but represents a validation gap (ADV05–ADV07).

- *once-forall completeness (AV4)*: No completeness gap was found. In subsidiary tableaux, a single LVar instantiation always suffices because the β-rule shares the substitution map — the LVar can unify to whatever value closes both branches. If the two branches require different bindings, neither `once-forall` nor genuine `forall` can close (it's a fundamental tableau limitation, not a once-forall issue). Tests ADV08–ADV09c verify this.

- *para-free-close transitivity*: The implementation's `para-free-closeo` rule is more powerful than initially expected. For `R ← ∃y.(y=a ∧ y=b)` (false when a≠b), pos-call correctly proves R false: `eq(par p, a)` saved to lits, then `eq(par p, b)` is rewritten via para-free-close to `eq(a, b)`, which free-closes (ADV09c).

- *Parameter structural isolation*: `(par p)` cannot participate in `free-closureo` or `decompose-eq-argso` (it's `['par nom]`, not `(lcons 'app ...)`). This is sound and correct: parameters are constants of L^par (not L), and Fitting's Free Closure Rule only applies to constants of L. Two distinct δ-parameters CAN denote the same domain element (e.g., `∃x.∃y. x=y` is satisfiable — take x=y=a), so `eq(par p, par q)` must NOT close. Tests ADV12/12b verify this.

**General substitutivity (Fitting §5 — resolved, Section SUB).** Fitting's Substitutivity Rule applies to ANY formula on the branch, not just pos/neg literals. The implementation achieves full coverage through specialized rules: `rewrite-lito` handles pos/neg; `para-free-closeo` handles eq-side rewriting for constructor clash; `eq-neq-closeo` handles neq-side rewriting toward reflexivity; `rewrite-term-with-eqso` handles procedure call argument rewriting. The **eq-triggered-neq-close** rule completes the coverage by handling the case where a neq literal is saved to `lits` before the relevant equality arrives: when the current literal is `['eq t1 t2]` and lits contains `['neq n1 n2]`, the rule uses `collect-eqso (lcons lit lits)` to gather equality pairs (including the current eq) and `eq-neq-closeo` to rewrite the neq's args toward reflexivity. Tests SUB01–SUB04 verify eq/neq ordering independence, nested decomposition, and transitive parameter chains.

**Fitting's original P1 — ∀-based odd clause (tested, Section X).** Fitting's §2 gives `odd(x) ← (∀y)[even(y) ⊃ ¬(x = y)]`. In NNF this is `∀y.(¬even(y) ∨ x≠y)`. Both disjunct orderings now work correctly thanks to the eq-triggered procedure call rules: when the α-rule processes `pos(even(par p))` before `eq(x,y)`, the pos literal is saved to `lits` by savefml, and when `eq(x,y)` arrives as the current literal, the eq-triggered-call rule finds the saved pos literal, rewrites `par p → ground`, and fires the procedure call. The commuted form `∀y.(x≠y ∨ ¬even(y))` also works via the original subst-call path. Tests ADV01–ADV04 verify both orderings succeed.

## Equality Rewriting Termination

The equality reasoning helpers `eq-membero`, `eq-neq-closeo`, and `para-free-closeo` require multi-step rewriting chains (e.g., a=b ∧ b=c closes (neq a c) via a→b→c). The original implementation used a Peano-numeral depth bound (6 steps) to prevent infinite cycling on bidirectional equality pairs like `[(t₁,t₂),(t₂,t₁)]`.

The current implementation replaces the depth bound with a **visited-set** approach via the `selecto` relation. `selecto x lst rest` non-deterministically picks an element `x` from `lst` and returns `rest` as the remainder with that one occurrence removed. The rewriting functions pass a `remaining` list of equality pairs; each step removes the used pair via `selecto`. Since `remaining` strictly shrinks at each recursive call, termination is guaranteed without any fixed step limit.

Consequences:
- **No false negatives from depth cutoff**: arbitrarily long transitivity chains are now provable. Tests S01 and S02 verify 7-step chains (which exceeded the old 6-step limit).
- **Win(1) and win(2) now terminate**: the Nim game's proof search previously diverged due to the depth limit interacting with the γ-rule's re-enqueueing. With `selecto`, the search finds the refl-close path immediately, and both win(1) and win(2) complete in under 1ms.
- **No spurious cycles**: each equality pair is used at most once per chain, so bidirectional pairs cannot create infinite loops.

## References

1. Fitting, M. "Tableaux for Logic Programming." *J. Automated Reasoning* 13, 175–188 (1994).
2. Near, Byrd, Friedman. "αleanTAP: A Declarative Theorem Prover for First-Order Classical Logic." ICLP 2008.
3. Fitting, M. *First-Order Logic and Automated Theorem Proving.* Springer, 1990/1996.
4. Fitting, M. "Partial Models and Logic Programming." *Theoretical Computer Science* 48, 229–255 (1987).
5. Smullyan, R.M. *First-Order Logic.* Springer, 1968.
6. Van Fraassen, B. "Singular Terms, Truth-Value Gaps, and Free Logic." *J. Philosophy* 63, 481–485 (1966).
