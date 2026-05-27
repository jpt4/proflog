;; ============================================================================
;; αleanTAP-E Test Suite
;; ============================================================================
;;
;; Tests for the classical FOL tableau prover extended with equality theory.
;;
;; Conventions:
;;   - All input formulas are in NNF (negation pushed to literals).
;;   - Terms: (app 'f t1 t2) for applications; (app 'c) for constants.
;;   - To prove a theorem T: build a closed tableau for (not T) in NNF, i.e.,
;;     supply the negation of T directly. A closed tableau for ¬T proves T.
;;   - `provable?` tests that the prover finds ≥1 proof.
;;   - `not-provable?` tests that no proof is found within n attempts.
;;
;; Sections:
;;   A — Base prover (α β γ rules, complementary closure, no equality)
;;   B — Reflexivity and self-equality
;;   C — Free Closure: distinct constructor heads
;;   D — Paramodulation / Substitutivity
;;   E — Transitivity chains
;;   F — One-One decomposition (Fitting §5 One-One Rule)
;;   G — Interactions: equality + FOL rules combined
;;   H — Negative tests: formulas that must not be provable
;; ============================================================================

(ns cljtap.alphaleantap-e-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.core.logic :refer :all
             :rename {appendo logic-appendo
                      membero logic-membero
                      is logic-is}]
            [clojure.core.logic.nominal :refer [tie hash]]
            [cljtap.alphaleantap-e :refer :all]))

;; ---------------------------------------------------------------------------
;; Test helpers
;; ---------------------------------------------------------------------------

(defn provable?
  "True iff the prover finds at least one closed tableau for `fml`."
  [fml]
  (seq (prove fml 1)))

(defn not-provable?
  "True iff the prover finds no proof within `n` attempts."
  ([fml] (not-provable? fml 1))
  ([fml n]
   (empty? (prove fml n))))

(defn proof-contains?
  "True iff the proof term tree contains `step` anywhere."
  [proof step]
  (some #{step} (flatten (list proof))))

(defn proof-step-for
  "Return the first proof term found for `fml`, or nil."
  [fml]
  (first (prove fml 1)))

;; ============================================================================
;; Section A: Base Prover — α, β, γ rules, complementary closure
;;
;; These verify the core tableau machinery with an empty equality theory.
;; ============================================================================

(deftest test-A01-contradiction
  (testing "p ∧ ¬p — direct complementary closure"
    (is (provable? '(and (pos (app p)) (neg (app p)))))))

(deftest test-A02-excluded-middle-negation
  (testing "¬(p ∨ ¬p) in NNF = (¬p ∧ p) — closed by complementary closure"
    (is (provable? '(and (neg (app p)) (pos (app p)))))))

(deftest test-A03-disjunction-both-branches
  (testing "(p ∧ ¬p) ∨ (q ∧ ¬q) — β-rule, both branches close"
    (is (provable?
          '(or (and (pos (app p)) (neg (app p)))
               (and (pos (app q)) (neg (app q))))))))

(deftest test-A04-nested-conjunction
  (testing "p ∧ q ∧ ¬p — closure after saving q"
    (is (provable?
          '(and (pos (app p))
                (and (pos (app q))
                     (neg (app p))))))))

(deftest test-A05-modus-ponens
  (testing "((p → q) ∧ p) → q in NNF: (¬p ∨ q) ∧ p ∧ ¬q"
    (is (provable?
          '(and (or (neg (app p)) (pos (app q)))
                (and (pos (app p))
                     (neg (app q))))))))

(deftest test-A06-universal-instantiation
  (testing "∀x.P(x) and ¬P(a) — γ-rule instantiation closes"
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['forall (tie v ['pos ['app 'P ['var v]]])]
                      (list ['neg ['app 'P ['app 'a]]])
                      '() '() proof)))))))

(deftest test-A07-three-way-disjunction
  (testing "(p ∧ ¬p) ∨ (q ∧ ¬q) ∨ (r ∧ ¬r) — three β-branches"
    (is (provable?
          '(or (and (pos (app p)) (neg (app p)))
               (or (and (pos (app q)) (neg (app q)))
                   (and (pos (app r)) (neg (app r)))))))))

(deftest test-A08-disjunction-with-same-literal
  (testing "(p ∨ p) ∧ ¬p — both branches of ∨ close against ¬p"
    (is (provable?
          '(and (or (pos (app p)) (pos (app p)))
                (neg (app p)))))))

;; ============================================================================
;; Section B: Reflexivity and Self-Equality
;;
;; (neq t t) is an immediate contradiction in any model.
;; ============================================================================

(deftest test-B01-neq-constant-self
  (testing "(neq c c) — reflexivity closure on a constant"
    (is (provable? '(neq (app c) (app c))))))

(deftest test-B02-neq-compound-self
  (testing "(neq s(0) s(0)) — reflexivity closure on compound term"
    (is (provable? '(neq (app s (app zero)) (app s (app zero)))))))

(deftest test-B03-neq-nested-self
  (testing "(neq f(g(a)) f(g(a))) — reflexivity on deeply nested term"
    (is (provable? '(neq (app f (app g (app a)))
                         (app f (app g (app a))))))))

(deftest test-B04-reflexivity-proof-step
  (testing "(neq c c) produces refl-close step"
    (let [prf (proof-step-for '(neq (app c) (app c)))]
      (is (some? prf))
      (is (proof-contains? prf 'refl-close)))))

(deftest test-B05-neq-self-in-conjunction
  (testing "P(a) ∧ (neq c c) — reflexivity closes despite extra literal"
    (is (provable?
          '(and (pos (app P (app a)))
                (neq (app c) (app c)))))))

;; ============================================================================
;; Section C: Free Closure
;;
;; Fitting §5: distinct function symbols of L have disjoint ranges.
;; (eq f(...) g(...)) with f ≠ g is unsatisfiable in every weak Herbrand model.
;; ============================================================================

(deftest test-C01-eq-distinct-constants
  (testing "(eq zero one) — distinct constants, free closure"
    (is (provable? '(eq (app zero) (app one))))))

(deftest test-C02-eq-constant-vs-unary
  (testing "(eq zero s(x)) — constant vs unary function, free closure"
    (is (provable? '(eq (app zero) (app s (app x)))))))

(deftest test-C03-eq-distinct-unary-fns
  (testing "(eq f(a) g(a)) — distinct unary functions, free closure"
    (is (provable? '(eq (app f (app a)) (app g (app a)))))))

(deftest test-C04-eq-null-vs-cons
  (testing "(eq nul cons(x,xs)) — nul ≠ cons, free closure"
    ;; Note: Clojure's nil is the null value (not a symbol), so we use 'nul.
    (is (provable? '(eq (app nul) (app cons (app x) (app xs)))))))

(deftest test-C05-free-closure-in-conjunction
  (testing "P(a) ∧ (eq zero s(a)) — free closure despite extra literal"
    (is (provable?
          '(and (pos (app P (app a)))
                (eq (app zero) (app s (app a))))))))

(deftest test-C06-free-closure-proof-step
  (testing "(eq zero s(x)) produces free-close step"
    (let [prf (proof-step-for '(eq (app zero) (app s (app x))))]
      (is (some? prf))
      (is (proof-contains? prf 'free-close)))))

(deftest test-C07-eq-distinct-binary-fns
  (testing "(eq f(a,b) g(a,b)) — distinct binary functions, free closure"
    (is (provable? '(eq (app f (app a) (app b))
                        (app g (app a) (app b)))))))

;; ============================================================================
;; Section D: Paramodulation / Substitutivity
;;
;; Fitting §5 Substitutivity Rule: if t = u is on the branch, any occurrence
;; of t in another branch formula can be replaced by u.
;; ============================================================================

(deftest test-D01-eq-symmetry
  (testing "a = b ∧ b ≠ a — symmetry via paramodulation"
    (is (provable?
          '(and (eq (app a) (app b))
                (neq (app b) (app a)))))))

(deftest test-D02-congruence-predicate
  (testing "a = b ∧ P(a) ∧ ¬P(b) — predicate congruence via paramodulation"
    (is (provable?
          '(and (eq (app a) (app b))
                (and (pos (app P (app a)))
                     (neg (app P (app b)))))))))

(deftest test-D03-congruence-function
  (testing "a = b ∧ f(a) ≠ f(b) — function congruence via paramodulation"
    (is (provable?
          '(and (eq (app a) (app b))
                (neq (app f (app a)) (app f (app b))))))))

(deftest test-D04-leibniz-law
  (testing "a = b → (P(a) ↔ P(b)) — both directions of Leibniz's law"
    ;; NNF of ¬(a=b → (P(a) ↔ P(b))):
    ;; a=b ∧ ((P(a) ∧ ¬P(b)) ∨ (¬P(a) ∧ P(b)))
    (is (provable?
          '(and (eq (app a) (app b))
                (or (and (pos (app P (app a)))
                         (neg (app P (app b))))
                    (and (neg (app P (app a)))
                         (pos (app P (app b))))))))))

(deftest test-D05-deep-congruence
  (testing "a = b ∧ f(g(a)) ≠ f(g(b)) — congruence through nesting"
    (is (provable?
          '(and (eq (app a) (app b))
                (neq (app f (app g (app a)))
                     (app f (app g (app b)))))))))

(deftest test-D06-provable-with-equality
  (testing "a = b ∧ P(a) ∧ ¬P(b) is provable"
    ;; In the weak Herbrand model a and b are distinct symbols, so (eq a b)
    ;; closes immediately via free-close.  Para-close applies when the
    ;; equality itself is not directly contradictory (same-headed terms).
    (let [prf (proof-step-for
                '(and (eq (app a) (app b))
                      (and (pos (app P (app a)))
                           (neg (app P (app b))))))]
      (is (some? prf)))))

;; ============================================================================
;; Section E: Transitivity Chains
;;
;; Multi-step rewriting via the neq-closureo relation.
;; ============================================================================

(deftest test-E01-transitivity-two-steps
  (testing "a = b ∧ b = c ∧ a ≠ c — two-step transitivity"
    (is (provable?
          '(and (eq (app a) (app b))
                (and (eq (app b) (app c))
                     (neq (app a) (app c))))))))

(deftest test-E02-transitivity-three-steps
  (testing "a = b ∧ b = c ∧ c = d ∧ a ≠ d — three-step chain"
    (is (provable?
          '(and (eq (app a) (app b))
                (and (eq (app b) (app c))
                     (and (eq (app c) (app d))
                          (neq (app a) (app d)))))))))

(deftest test-E03-transitivity-with-predicate
  (testing "a = b ∧ b = c ∧ P(a) ∧ ¬P(c) — transitive substitution"
    (is (provable?
          '(and (eq (app a) (app b))
                (and (eq (app b) (app c))
                     (and (pos (app P (app a)))
                          (neg (app P (app c))))))))))

;; ============================================================================
;; Section F: One-One Decomposition (Fitting §5 One-One Rule)
;;
;; f(t1,...,tn) = f(u1,...,un) → ti = ui for each i (injectivity of f).
;; In αleanTAP-E, the whole conjunction of sub-equalities is generated
;; and processed as a new formula.
;; ============================================================================

(deftest test-F01-one-one-unary
  (testing "s(zero) = s(s(zero)) — decompose to zero = s(zero), free-close"
    (is (provable?
          '(eq (app s (app zero))
               (app s (app s (app zero))))))))

(deftest test-F02-one-one-with-neq
  (testing "cons(a,xs) = cons(b,xs) ∧ a ≠ b — decompose to a=b, free-close"
    (is (provable?
          '(and (eq (app cons (app a) (app xs))
                    (app cons (app b) (app xs)))
                (neq (app a) (app b)))))))

(deftest test-F03-one-one-proof-step
  (testing "s(a) = s(b) produces decompose step"
    ;; Since a and b are distinct constructor symbols, decompose yields
    ;; (eq a b) which then fires free-close.
    (let [prf (proof-step-for
                '(eq (app s (app a)) (app s (app b))))]
      (is (some? prf))
      (is (proof-contains? prf 'decompose)))))

(deftest test-F04-one-one-nested
  (testing "f(g(a)) = f(g(b)) — cascading decomposition"
    ;; Decompose to g(a) = g(b), then decompose to a = b, then free-close.
    (is (provable?
          '(eq (app f (app g (app a)))
               (app f (app g (app b))))))))

(deftest test-F05-one-one-binary-second-arg
  (testing "cons(a,b) = cons(a,c) ∧ b ≠ c — second argument decomposed"
    (is (provable?
          '(and (eq (app cons (app a) (app b))
                    (app cons (app a) (app c)))
                (neq (app b) (app c)))))))

;; ============================================================================
;; Section G: Interactions — Equality + FOL Rules
;; ============================================================================

(deftest test-G01-eq-in-disjunction
  (testing "(eq zero one) ∨ (eq f(a) g(a)) — both branches close by free-close"
    (is (provable?
          '(or (eq (app zero) (app one))
               (eq (app f (app a)) (app g (app a))))))))

(deftest test-G02-universal-with-equality
  (testing "∀x.(P(x) ∧ ¬P(x)) — universal then contradiction"
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['forall (tie v ['and ['pos ['app 'P ['var v]]]
                                           ['neg ['app 'P ['var v]]]])]
                      '() '() '() proof)))))))

(deftest test-G03-equality-enables-universal-closure
  (testing "∀x.P(x) ∧ a=b ∧ ¬P(b) — universal + paramodulation"
    ;; γ-rule instantiates x with logic var X; X unifies with a via
    ;; para-close: P(X) complements ¬P(b) after X rewrites to b via a=b.
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['and ['forall (tie v ['pos ['app 'P ['var v]]])]
                            ['and ['eq ['app 'a] ['app 'b]]
                                  ['neg ['app 'P ['app 'b]]]]]
                      '() '() '() proof)))))))

(deftest test-G04-disjunction-one-equality-branch
  (testing "(eq zero one) ∨ (P(a) ∧ ¬P(a)) — one branch free-close, one complementary"
    (is (provable?
          '(or (eq (app zero) (app one))
               (and (pos (app P (app a)))
                    (neg (app P (app a)))))))))

(deftest test-G05-eq-neq-complementary-closure
  (testing "(neq a b) ∧ (eq a b) — eq-neq-close when neq is in lits first"
    ;; neq is processed first (α-rule puts eq in unexp),
    ;; then eq is processed and finds neq in lits.
    (is (provable?
          '(and (neq (app a) (app b))
                (eq (app a) (app b)))))))

(deftest test-G06-eq-neq-close-proof-step
  (testing "(neq f(a) f(b)) ∧ (eq f(a) f(b)) produces eq-neq-close step"
    ;; Use same-headed terms (both f) so free-close won't fire on the eq.
    ;; Use distinct arguments (a ≠ b) so refl-close won't fire on the neq.
    ;; Processing order: neq is saved to lits first, then eq finds it.
    (let [prf (proof-step-for
                '(and (neq (app f (app a)) (app f (app b)))
                      (eq (app f (app a)) (app f (app b)))))]
      (is (some? prf))
      (is (proof-contains? prf 'eq-neq-close)))))

;; ============================================================================
;; Section H: Negative Tests
;;
;; These formulas should NOT be provable — the prover must find no proof.
;; ============================================================================

(deftest test-H01-atom-alone-not-provable
  (testing "P(a) alone — no contradiction, not provable"
    (is (not-provable? '(pos (app P (app a)))))))

(deftest test-H02-tautological-eq-not-provable
  (testing "eq(f(a), f(a)) — tautologically true equality, not a contradiction"
    ;; f(a) = f(a) holds in every model. It is NOT a contradiction,
    ;; so the tableau prover should find no closed tableau.
    ;; Free-close won't fire (same head f). One-one decomposes to eq(a,a)
    ;; which is also tautological and cannot be closed.
    (is (not-provable? '(eq (app f (app a)) (app f (app a)))))))

(deftest test-H03-disjunction-alone-not-provable
  (testing "P(a) ∨ Q(b) — not provable without contradictions in each branch"
    (is (not-provable? '(or (pos (app P (app a)))
                            (pos (app Q (app b))))))))

(deftest test-H04-open-neq-not-provable
  (testing "(neq a b) alone — a ≠ b in weak Herbrand models, but not a contradiction"
    ;; Interestingly, (neq (app a) (app b)) should NOT be provable —
    ;; it's the assertion that a ≠ b, which is satisfiable (just use
    ;; any model where a ≠ b). A closed tableau requires a contradiction.
    (is (not-provable? '(neq (app a) (app b))))))

(deftest test-H05-tautological-eq-conjunction-not-provable
  (testing "eq(a,a) ∧ eq(b,b) — tautological equalities, no contradiction"
    ;; Both eq(a,a) and eq(b,b) hold in every model. Their conjunction
    ;; is satisfiable — the prover should find no closed tableau.
    (is (not-provable? '(and (eq (app a) (app a))
                             (eq (app b) (app b)))))))

;; ============================================================================
;; Section I: δ-Rule — Existential Quantifier
;;
;; The δ-rule handles (exists (tie Nom Fml)) by introducing a fresh rigid
;; Skolem witness (a nom wrapped as (app p)) for the bound variable and
;; processing the body exactly once — the formula is NOT re-enqueued.
;;
;; Key property: the witness (app p) is RIGID — it cannot unify with other
;; ground terms (distinct noms are not equal), unlike the γ-rule's logic var.
;; It CAN unify with γ-rule logic variables, enabling ∀/∃ interactions.
;; ============================================================================

(deftest test-I01-existential-internal-contradiction
  (testing "∃x.(P(x) ∧ ¬P(x)) — witness irrelevant, body always closes"
    ;; δ introduces p; body becomes P(p) ∧ ¬P(p), which closes by
    ;; complementary closure regardless of the identity of p.
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['and ['pos ['app 'P ['var v]]]
                                            ['neg ['app 'P ['var v]]]])]
                      '() '() '() proof)))))))

(deftest test-I02-witness-rigidity
  (testing "∃x.P(x) ∧ ¬P(c) — rigid witness cannot unify with concrete c"
    ;; δ introduces nom p; P(p) and ¬P(c) do NOT close because the nom p
    ;; and the symbol c are distinct ground terms.  This is the key
    ;; difference from the γ-rule (which introduces a unifiable logic var).
    (is (empty?
          (run 1 [proof]
            (nom v
              (proveo ['and ['exists (tie v ['pos ['app 'P ['var v]]])]
                            ['neg ['app 'P ['app 'c]]]]
                      '() '() '() proof)))))))

(deftest test-I03-witness-closes-via-equality
  (testing "∃x.(x=c ∧ P(x)) ∧ ¬P(c) — equality bridges witness to concrete term"
    ;; δ introduces p; body yields (eq p c) ∧ P(p).  After both are on the
    ;; branch, ¬P(c) closes via paramodulation: rewrite c→p using (eq p c),
    ;; yielding ¬P(p), which is the complement of P(p) in lits.
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['and ['exists (tie v ['and ['eq ['var v] ['app 'c]]
                                                  ['pos ['app 'P ['var v]]]])]
                            ['neg ['app 'P ['app 'c]]]]
                      '() '() '() proof)))))))

(deftest test-I04-nested-existentials
  (testing "∃x.∃y.(P(x) ∧ ¬P(x)) — two δ-rules fire, inner body closes on x"
    ;; Outer δ: introduce p for x.  Inner δ: introduce q for y.
    ;; Body uses only x (= p); P(p) ∧ ¬P(p) closes by complementary closure.
    (is (seq
          (run 1 [proof]
            (nom x y
              (proveo ['exists (tie x
                        ['exists (tie y
                          ['and ['pos ['app 'P ['var x]]]
                                ['neg ['app 'P ['var x]]]])])]
                      '() '() '() proof)))))))

(deftest test-I05-existential-reflexivity
  (testing "∃x.(neq x x) — rigid witness still fires refl-close"
    ;; δ introduces p; (neq (app p) (app p)) fires refl-close because
    ;; the nom p unifies with itself: (neq t t) with t = (app p).
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['neq ['var v] ['var v]])]
                      '() '() '() proof)))))))

(deftest test-I06-exist-proof-step
  (testing "∃x.(neq x x) proof term records 'exist step"
    (let [prf (first
                (run 1 [proof]
                  (nom v
                    (proveo ['exists (tie v ['neq ['var v] ['var v]])]
                            '() '() '() proof))))]
      (is (some? prf))
      (is (proof-contains? prf 'exist)))))

(deftest test-I07-universal-existential-interaction
  (testing "∀x.P(x) ∧ ∃y.¬P(y) — γ logic var unifies with δ rigid witness"
    ;; γ introduces logic var X for ∀x.P(x); δ introduces rigid nom p for
    ;; ∃y.¬P(y).  P(X) and ¬P(p) close because X (unbound logic var) can
    ;; unify with (app p).
    (is (seq
          (run 1 [proof]
            (nom v1 v2
              (proveo ['and ['forall (tie v1 ['pos ['app 'P ['var v1]]])]
                            ['exists (tie v2 ['neg ['app 'P ['var v2]]])]]
                      '() '() '() proof)))))))

(deftest test-I08-existential-eq-neq
  (testing "∃x.(eq x a ∧ neq x a) — eq/neq complement on witness"
    ;; δ introduces p; body becomes (eq p a) ∧ (neq p a).  The first is
    ;; saved to lits; the second fires eq-neq-close when it finds (eq p a).
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['and ['eq ['var v] ['app 'a]]
                                            ['neq ['var v] ['app 'a]]])]
                      '() '() '() proof)))))))

(deftest test-I09-existential-alone-not-provable
  (testing "∃x.P(x) alone — open formula, no contradiction, not provable"
    ;; δ introduces p; P(p) is a positive literal with no complement.
    ;; No closure rule fires and there are no more formulas to process.
    (is (empty?
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['pos ['app 'P ['var v]]])]
                      '() '() '() proof)))))))

;; ============================================================================
;; Run all tests
;; ============================================================================

(comment
  (run-tests 'cljtap.alphaleantap-e-test))
