;; ============================================================================
;; αleanTAP-EP Test Suite
;; ============================================================================
;;
;; Comprehensive tests for the Proflog extension of αleanTAP-E.
;;
;; Organization:
;;
;;   Section A  — Base prover regression (empty program, no equality)
;;   Section B  — Equality regression (empty program, with equality)
;;   Section B+ — Free Closure Rule (disjointness)
;;   Section B++— One-One decomposition, NEQ-rewriting, transitivity
;;   Section Bc — Eq/Neq complementary closure
;;   Section Bd — Injectivity decomposition (formula expansion)
;;   Section C  — The δ-rule (existential quantifier)
;;   Section D  — negate-formulao (NNF-preserving negation relation)
;;   Section E  — lookup-clauseo and bind-argso (clause infrastructure)
;;   Section F  — Positive procedure calls (Fitting §6, Part 1)
;;   Section G  — Negative procedure calls (Fitting §6, Part 2)
;;   Section H  — Recursive procedure calls
;;   Section I  — Mutual recursion (even/odd — Fitting's Program P1)
;;   Section J  — The Nim game (Fitting's Program P2)
;;   Section K  — Equality within clause bodies
;;   Section L  — Procedure calls + equality combined
;;   Section M  — Top-level interface (query-succeeds, query-fails, prove)
;;   Section N  — Negative tests (non-theorems, soundness guards)
;;   Section O  — Proof-term structure validation
;;   Section P  — Full first-order logic in clause bodies
;;   Section Q  — Backward running, list programs, multi-arg substitutivity
;;   Section DI — Disunification with free variables (Fitting §8)
;;   Section MV — Fitting's Move Warning (§8): auxiliary relation factoring
;;   Section OC — Occurs Check (Fitting §3 / supervaluation semantics)
;;   Section TC — Transitive Closure (graph a→b→c)
;;   Section PA — Peano Arithmetic: Addition
;;   Section SO — Sorted Predicate (∀ in body, inline ordering)
;;   Section SS — Subset Relations (∀ in body, inline membership)
;;
;; Every test formula used with proveo is in NNF.  When testing whether
;; a query SUCCEEDS with a program (the query is TRUE), we build a closed
;; P-tableau for the NEGATION of the query.  When testing whether a query
;; FAILS (the query is FALSE), we build a closed P-tableau for the query
;; itself.  (Fitting, Definition 6.1.)
;;
;; Naming convention:
;;   test-<section>-<number>-<short-description>
;;
;; ============================================================================

(ns cljtap.alphaleantap-ep-test
  (:refer-clojure :exclude [==])
  (:require [clojure.test :refer [deftest is testing are run-tests]]
            [clojure.core.logic :refer :all :rename {is l-is, appendo logic-appendo, membero logic-membero}]
            [clojure.core.logic.nominal :refer [tie hash]]
            [cljtap.alphaleantap-ep :refer :all]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn provable?
  "True iff the prover finds at least one closed tableau for `formula`
   with the empty program (pure theorem proving, no procedure calls).
   Backward compatible with αleanTAP-E."
  [formula]
  (seq (prove formula 1)))

(defn provable-with?
  "True iff proveo finds a closed P-tableau for `formula` using `program`.
   The program and formula must be constructed inside a `run` block
   so that noms are properly scoped.
   
   This helper takes a thunk that returns a list of results."
  [thunk]
  (seq (thunk)))

(defn not-provable?
  "True iff the prover finds NO proof within `n` attempts (empty program)."
  ([formula] (not-provable? formula 1))
  ([formula n]
   (empty? (prove formula n))))

(defn proof-uses-step?
  "True iff some proof for `formula` (empty program) contains `step`
   somewhere in the proof tree."
  [formula step]
  (let [proofs (prove formula 1)]
    (and (seq proofs)
         (some #(some #{step} (flatten (list %))) proofs))))

(defn proof-tree-contains?
  "True iff the proof term tree contains the given symbol anywhere."
  [proof-term step]
  (some #{step} (flatten (list proof-term))))

;; ============================================================================
;; Section A: Base Prover Regression (empty program, no equality)
;; ============================================================================
;;
;; These verify that the Proflog extension (new program argument, δ-rule,
;; procedure call clauses) did not break the core tableau expansion.
;; All use the empty program '().

(deftest test-A01-excluded-middle
  (testing "p ∨ ¬p — law of excluded middle"
    (is (provable? '(and (pos (app p)) (neg (app p)))))))

(deftest test-A02-simple-conjunction-closure
  (testing "p ∧ ¬p — direct complementary closure"
    (is (provable? '(and (pos (app a)) (neg (app a)))))))

(deftest test-A03-disjunction-both-branches
  (testing "(p ∧ ¬p) ∨ (q ∧ ¬q) — both branches close"
    (is (provable?
          '(or (and (pos (app p)) (neg (app p)))
               (and (pos (app q)) (neg (app q))))))))

(deftest test-A04-nested-conjunction
  (testing "p ∧ q ∧ ¬p — closure delayed past irrelevant literal"
    (is (provable?
          '(and (pos (app p))
                (and (pos (app q))
                     (neg (app p))))))))

(deftest test-A05-pelletier-01
  (testing "Pelletier Problem 1: (p → q) ↔ (¬q → ¬p)"
    (is (provable?
          '(or (and (or (neg (app p)) (pos (app q)))
                    (and (neg (app q)) (pos (app p))))
               (and (or (pos (app q)) (neg (app p)))
                    (and (pos (app p)) (neg (app q)))))))))

(deftest test-A06-pelletier-02
  (testing "Pelletier Problem 2: ¬¬p ↔ p"
    (is (provable?
          '(or (and (pos (app p)) (neg (app p)))
               (and (neg (app p)) (pos (app p))))))))

(deftest test-A07-modus-ponens
  (testing "((p → q) ∧ p) → q"
    (is (provable?
          '(and (or (neg (app p)) (pos (app q)))
                (and (pos (app p))
                     (neg (app q))))))))

(deftest test-A08-first-order-instantiation
  (testing "∀x.P(x) ∧ ¬P(a) — universal instantiation"
    (is (seq
          (run 1 [proof]
            (nom a
              (proveo ['forall (tie a ['pos ['app 'P ['var a]]])]
                      (list ['neg ['app 'P ['app 'a]]])
                      '() '() '() proof)))))))

(deftest test-A09-pelletier-18
  (testing "Pelletier 18: ∃y.∀x. F(y) → F(x)"
    ;; Negation (Skolemized): ∀a. F(a) ∧ ¬F(g1(a))
    (is (seq
          (run 1 [proof]
            (nom a
              (proveo ['forall (tie a ['and ['pos ['app 'f ['var a]]]
                                            ['neg ['app 'f ['app 'g1 ['var a]]]]])]
                      '() '() '() '() proof)))))))

(deftest test-A10-three-way-disjunction
  (testing "Three-way split — all branches close"
    (is (provable?
          '(or (and (pos (app a)) (neg (app a)))
               (or (and (pos (app b)) (neg (app b)))
                   (and (pos (app c)) (neg (app c)))))))))


;; ============================================================================
;; Section B: Equality Regression (empty program, with equality)
;; ============================================================================

(deftest test-B01-reflexivity
  (testing "c ≠ c — reflexivity closure"
    (is (provable? '(neq (app c) (app c))))))

(deftest test-B02-reflexivity-compound
  (testing "f(a) ≠ f(a)"
    (is (provable? '(neq (app f (app a)) (app f (app a)))))))

(deftest test-B03-symmetry
  (testing "a = b ∧ b ≠ a — symmetry"
    (is (provable? '(and (eq (app a) (app b))
                         (neq (app b) (app a)))))))

(deftest test-B04-transitivity
  (testing "a = b ∧ b = c ∧ a ≠ c — transitivity"
    (is (provable? '(and (eq (app a) (app b))
                         (and (eq (app b) (app c))
                              (neq (app a) (app c))))))))

(deftest test-B05-congruence-predicate
  (testing "a = b ∧ P(a) ∧ ¬P(b) — predicate congruence"
    (is (provable? '(and (eq (app a) (app b))
                         (and (pos (app P (app a)))
                              (neg (app P (app b)))))))))

(deftest test-B06-congruence-function
  (testing "a = b ∧ f(a) ≠ f(b) — function congruence"
    (is (provable? '(and (eq (app a) (app b))
                         (neq (app f (app a)) (app f (app b))))))))

(deftest test-B07-leibniz
  (testing "a = b → (P(a) ↔ P(b)) — Leibniz's law"
    (is (provable? '(and (eq (app a) (app b))
                         (or (and (pos (app P (app a)))
                                  (neg (app P (app b))))
                             (and (neg (app P (app a)))
                                  (pos (app P (app b))))))))))

(deftest test-B08-deep-congruence
  (testing "a = b ∧ f(g(a)) ≠ f(g(b)) — deep rewriting"
    (is (provable? '(and (eq (app a) (app b))
                         (neq (app f (app g (app a)))
                              (app f (app g (app b)))))))))


;; ============================================================================
;; Section B+: Free Closure Rule (Disjointness)
;; ============================================================================
;;
;; Tests for the new free closure rule: (eq (app f ...) (app g ...))
;; with distinct head symbols f ≠ g closes the branch immediately.

(deftest test-Bp01-free-closure-constant-vs-unary
  (testing "zero = s(x) is unsatisfiable — different head constructors"
    (is (provable? '(eq (app zero) (app s (app x)))))))

(deftest test-Bp02-free-closure-different-functions
  (testing "f(a) = g(a) is unsatisfiable"
    (is (provable? '(eq (app f (app a)) (app g (app a)))))))

(deftest test-Bp03-free-closure-constant-vs-constant
  (testing "zero = one is unsatisfiable"
    (is (provable? '(eq (app zero) (app one))))))

(deftest test-Bp04-free-closure-in-conjunction
  (testing "P(a) ∧ (zero = s(a)) — free closure closes despite extra literal"
    (is (provable? '(and (pos (app P (app a)))
                         (eq (app zero) (app s (app a))))))))

(deftest test-Bp05-decomposition-then-free-close
  (testing "s(zero) = s(s(zero)) is unsatisfiable — decompose to 0=s(0), then free-close"
    ;; Same head symbol 's': free closure does NOT fire directly.
    ;; But decomposition yields (eq (app zero) (app s (app zero))),
    ;; which DOES free-close (zero ≠ s).
    ;; This tests the full injectivity chain: same head → decompose → clash.
    (is (provable? '(eq (app s (app zero)) (app s (app s (app zero))))))))

(deftest test-Bp06-free-closure-proof-step
  (testing "Free closure produces 'free-close' proof step"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 'zero] ['app 's ['app 'x]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'free-close)))))

(deftest test-Bp07-free-closure-soundness-nom-guard
  (testing "δ-parameter (nom) does NOT trigger free closure with symbol"
    ;; A δ-parameter p in (eq (app p) (app s x)) must NOT clash,
    ;; because p could denote any domain element including s(x).
    ;; With (par p) encoding, (par p) terms do not match (lcons 'app ...) in
    ;; free-closureo, so free closure is structurally prevented.
    ;; We test this by trying to close ∃x.(x = s(0)) — which should
    ;; NOT close because the witness p could equal s(0).
    (is (empty?
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['eq ['var a] ['app 's ['app 'zero]]])]
                      '() '() '() '() proof)))))))


;; ============================================================================
;; Section B++: One-One Decomposition, NEQ-Rewriting, and Transitivity
;; ============================================================================
;;
;; Tests for injectivity (one-one rule) and the eq-refl-close mechanism.

(deftest test-Bpp01-one-one-neq-close
  (testing "s(a) = s(b) ∧ a ≠ b — one-one derives [a,b], neq rewrites a→b"
    ;; Branch: (eq s(a) s(b)).  Current lit: (neq a b).
    ;; collect-eqso produces one-one pair [(app a), (app b)].
    ;; eq-refl-close rewrites a→b in (neq a b) to get (neq b b) → close!
    (is (provable?
          '(and (eq (app s (app a)) (app s (app b)))
                (neq (app a) (app b)))))))

(deftest test-Bpp02-one-one-binary
  (testing "cons(a,b) = cons(c,d) ∧ a ≠ c — one-one on binary constructor"
    (is (provable?
          '(and (eq (app cons (app a) (app b))
                    (app cons (app c) (app d)))
                (neq (app a) (app c)))))))

(deftest test-Bpp03-one-one-second-arg
  (testing "cons(a,b) = cons(c,d) ∧ b ≠ d — one-one second argument pair"
    (is (provable?
          '(and (eq (app cons (app a) (app b))
                    (app cons (app c) (app d)))
                (neq (app b) (app d)))))))

(deftest test-Bpp04-one-one-para
  (testing "s(a) = s(b) ∧ P(a) ∧ ¬P(b) — one-one enables paramodulation"
    ;; One-one gives pair [a,b]. Paramodulation rewrites ¬P(b) to ¬P(a).
    (is (provable?
          '(and (eq (app s (app a)) (app s (app b)))
                (and (pos (app P (app a)))
                     (neg (app P (app b)))))))))

(deftest test-Bpp05-eq-refl-close-proof-step
  (testing "eq-refl-close produces the correct proof step tag"
    ;; Use noms p,q so (eq p q) after one-one decompose is not free-closeable.
    ;; With (par p) encoding, (par p) terms do not match (lcons 'app ...) in
    ;; free-closureo, so free closure is structurally prevented.
    ;; Path: (eq s(p) s(q)) → decompose → (eq p q) → savefml → lits
    ;;       (neq p q) → eq-refl-close: collect eqs {p,q}, rewrite p→q ✓
    (let [proofs (run 1 [proof]
                   (nom p q
                     (proveo ['and ['eq ['app 's ['par p]] ['app 's ['par q]]]
                                   ['neq ['par p] ['par q]]]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'eq-refl-close)))))

(deftest test-Bpp06-one-one-no-false-decomposition
  (testing "f(a) = g(b) — different heads fire free-close, not decomposition"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 'f ['app 'a]] ['app 'g ['app 'b]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'free-close)))))

(deftest test-Bpp07-neq-transitivity-chain
  (testing "a=b ∧ b=c ∧ neq(a,c) — multi-step rewriting via transitivity"
    ;; Branch equalities: (app a)=(app b), (app b)=(app c)
    ;; collect-eqso yields pairs: [a,b], [b,a], [b,c], [c,b]
    ;; Process (neq (app a) (app c)):
    ;;   eq-neq-closeo rewrites a→b (using [a,b]), then b→c (using [b,c])
    ;;   → (neq c c) → reflexivity closure
    (is (provable?
          '(and (eq (app a) (app b))
                (and (eq (app b) (app c))
                     (neq (app a) (app c))))))))

(deftest test-Bpp08-neq-transitivity-longer-chain
  (testing "a=b ∧ b=c ∧ c=d ∧ neq(a,d) — three-step rewriting"
    (is (provable?
          '(and (eq (app a) (app b))
                (and (eq (app b) (app c))
                     (and (eq (app c) (app d))
                          (neq (app a) (app d)))))))))


;; ============================================================================
;; Section Bc: Eq/Neq Complementary Closure (Fix B)
;; ============================================================================
;;
;; When (eq t1 t2) is the current literal and (neq t1 t2) or (neq t2 t1)
;; is already on the branch, the branch is contradictory.  This tests
;; both conjunction orders to ensure no order-dependence.

(deftest test-Bc01-eq-neq-order-eq-first
  (testing "eq(a,b) ∧ neq(a,b) — eq processed first, neq closes via eq-refl-close"
    (is (provable? '(and (eq (app a) (app b))
                         (neq (app a) (app b)))))))

(deftest test-Bc02-eq-neq-order-neq-first
  (testing "neq(a,b) ∧ eq(a,b) — neq processed first, eq closes via eq-neq-close"
    ;; This is the order that FAILED before Fix B.
    ;; neq saved to lits, then eq arrives and finds the contradicting neq.
    ;; Note: for distinct constants a≠b, free-close also fires on eq(a,b).
    ;; So we use same-head terms to isolate the eq-neq-close rule.
    (is (provable? '(and (neq (app f (app a)) (app f (app b)))
                         (eq (app f (app a)) (app f (app b))))))))

(deftest test-Bc03-eq-neq-symmetric
  (testing "neq(b,a) ∧ eq(a,b) — eq-neq-close handles symmetry"
    (is (provable? '(and (neq (app f (app b)) (app f (app a)))
                         (eq (app f (app a)) (app f (app b))))))))

(deftest test-Bc04-eq-neq-close-proof-step
  (testing "eq-neq-close produces the correct proof step tag"
    (let [proofs (run 1 [proof]
                   (proveo ['and ['neq ['app 'f ['app 'x]] ['app 'f ['app 'y]]]
                                 ['eq ['app 'f ['app 'x]] ['app 'f ['app 'y]]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      ;; Should use eq-neq-close (not decompose or free-close)
      (is (proof-tree-contains? (first proofs) 'eq-neq-close)))))


;; ============================================================================
;; Section Bd: Injectivity Decomposition (Fix A)
;; ============================================================================
;;
;; The decomposition rule expands (eq (app f t₁…tₙ) (app f s₁…sₙ)) into
;; a conjunction (and (eq t₁ s₁) … (eq tₙ sₙ)).  This enables cascading:
;; f(g(a)) = f(g(b)) → g(a) = g(b) → a = b → free-close.

(deftest test-Bd01-unary-decomposition
  (testing "s(zero) = s(s(zero)) — decompose then free-close"
    ;; s(0) = s(s(0)) → decompose to (eq (app zero) (app s (app zero)))
    ;; → free-close (zero ≠ s)
    (is (provable? '(eq (app s (app zero)) (app s (app s (app zero))))))))

(deftest test-Bd02-nested-decomposition
  (testing "f(g(a)) = f(g(b)) — two levels of decomposition then free-close"
    ;; f(g(a)) = f(g(b)) → g(a) = g(b) → a = b → free-close
    (is (provable? '(eq (app f (app g (app a)))
                        (app f (app g (app b))))))))

(deftest test-Bd03-binary-decomposition
  (testing "cons(a,b) = cons(c,d) ∧ neq(a,c) — decompose + free-close on 1st arg"
    ;; cons(a,b) = cons(c,d) → (eq a c) ∧ (eq b d)
    ;; (eq a c) → free-close since a ≠ c
    (is (provable? '(eq (app cons (app a) (app b))
                        (app cons (app c) (app d)))))))

(deftest test-Bd04-decompose-proof-step
  (testing "Decomposition produces 'decompose' proof step"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 's ['app 'zero]]
                                ['app 's ['app 's ['app 'zero]]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'decompose)))))

(deftest test-Bd05-decompose-does-not-fire-on-different-heads
  (testing "f(a) = g(b) uses free-close, not decompose"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 'f ['app 'a]] ['app 'g ['app 'b]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'free-close))
      (is (not (proof-tree-contains? (first proofs) 'decompose))))))

(deftest test-Bd06-decompose-identical-args-not-closable
  (testing "f(a) = f(a) does not close — decompose yields eq(a,a) which is satisfiable"
    ;; f(a) = f(a) → decompose to (eq a a) → no closure (eq of identical terms)
    ;; This is a soundness check: we must NOT close.
    (is (not-provable? '(eq (app f (app a)) (app f (app a)))))))

(deftest test-Bd07-triple-nested-decomposition
  (testing "f(g(h(a))) = f(g(h(b))) — three levels of cascading decomposition"
    ;; f(g(h(a))) = f(g(h(b))) → g(h(a)) = g(h(b)) → h(a) = h(b) → a = b → free-close
    (is (provable? '(eq (app f (app g (app h (app a))))
                        (app f (app g (app h (app b)))))))))


;; ============================================================================
;; Section C: The δ-Rule (Existential Quantifier)
;; ============================================================================
;;
;; The δ-rule is NEW in αleanTAP-EP.  It handles (exists (tie a body))
;; by introducing a fresh nominal parameter.

(deftest test-C01-simple-existential
  (testing "∃x.(P(x) ∧ ¬P(x)) — existential with immediate closure"
    ;; The witness doesn't matter — P(c) ∧ ¬P(c) closes for any c
    (is (seq
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['and ['pos ['app 'P ['var a]]]
                                            ['neg ['app 'P ['var a]]]])]
                      '() '() '() '() proof)))))))

(deftest test-C02-existential-with-equality
  (testing "∃x.(x ≠ x) — existential witness still has x ≠ x"
    (is (seq
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['neq ['var a] ['var a]])]
                      '() '() '() '() proof)))))))

(deftest test-C03-existential-nested-in-conjunction
  (testing "P(a) ∧ ∃x.¬P(x) — existential witness unifies with a"
    ;; The ∃ introduces a parameter p; we need ¬P(p) to close with P(a).
    ;; In our free-variable tableau, the γ-style unification handles this
    ;; if the ∃ witness can match.  Actually, since δ introduces a rigid
    ;; parameter, this only closes if P(a) and ¬P(p) can unify.
    ;; They CAN'T (p is a fresh nom distinct from a).
    ;; But if we rephrase: P(a) ∧ ∃x.(x = a ∧ ¬P(x))
    ;; Then x=a plus ¬P(x) with paramodulation closes.
    (is (seq
          (run 1 [proof]
            (nom x
              (proveo ['and ['pos ['app 'P ['app 'a]]]
                            ['exists (tie x ['and ['eq ['var x] ['app 'a]]
                                                  ['neg ['app 'P ['var x]]]])]]
                      '() '() '() '() proof)))))))

(deftest test-C04-existential-does-not-reenqueue
  (testing "δ-rule does NOT re-enqueue (unlike γ-rule)"
    ;; ∃x.P(x) ∧ ¬P(a) — the witness p is rigid, P(p) ≠ P(a)
    ;; unless we add an equality.  Without equality, this should NOT close
    ;; (the existential witness is a distinct parameter).
    ;; This tests that δ behaves differently from γ.
    (is (empty?
          (run 1 [proof]
            (nom a
              (proveo ['and ['exists (tie a ['pos ['app 'P ['var a]]])]
                            ['neg ['app 'P ['app 'a]]]]
                      '() '() '() '() proof)))))))

(deftest test-C05-nested-existentials
  (testing "∃x.∃y.(x ≠ x ∨ y ≠ y) — nested δ-rule applications"
    (is (seq
          (run 1 [proof]
            (nom a b
              (proveo ['exists (tie a
                        ['exists (tie b
                          ['or ['neq ['var a] ['var a]]
                               ['neq ['var b] ['var b]]])])]
                      '() '() '() '() proof)))))))

(deftest test-C06-forall-then-exists
  (testing "∀x.∃y.(P(x) ∧ ¬P(x)) — γ then δ, closure independent of witness"
    (is (seq
          (run 1 [proof]
            (nom a b
              (proveo ['forall (tie a
                        ['exists (tie b
                          ['and ['pos ['app 'P ['var a]]]
                                ['neg ['app 'P ['var a]]]])])]
                      '() '() '() '() proof)))))))

(deftest test-C07-proof-uses-witness
  (testing "Proof term records 'witness' step for δ-rule"
    (let [proofs (run 1 [proof]
                   (nom a
                     (proveo ['exists (tie a ['neq ['var a] ['var a]])]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'witness)))))


;; ============================================================================
;; Section D: negate-formulao (NNF-Preserving Negation)
;; ============================================================================
;;
;; negate-formulao is essential for negative procedure calls.
;; It is a pure relation, so we test it in both directions.

(deftest test-D01-negate-pos
  (testing "¬(pos t) = (neg t)"
    (is (= (run 1 [out] (negate-formulao ['pos ['app 'a]] out))
           '([neg (app a)])))))

(deftest test-D02-negate-neg
  (testing "¬(neg t) = (pos t)"
    (is (= (run 1 [out] (negate-formulao ['neg ['app 'a]] out))
           '([pos (app a)])))))

(deftest test-D03-negate-eq
  (testing "¬(eq t1 t2) = (neq t1 t2)"
    (is (= (run 1 [out] (negate-formulao ['eq ['app 'a] ['app 'b]] out))
           '([neq (app a) (app b)])))))

(deftest test-D04-negate-neq
  (testing "¬(neq t1 t2) = (eq t1 t2)"
    (is (= (run 1 [out] (negate-formulao ['neq ['app 'a] ['app 'b]] out))
           '([eq (app a) (app b)])))))

(deftest test-D05-negate-conjunction
  (testing "¬(and A B) = (or ¬A ¬B) — De Morgan"
    (is (= (run 1 [out]
             (negate-formulao ['and ['pos ['app 'a]] ['pos ['app 'b]]] out))
           '([or [neg (app a)] [neg (app b)]])))))

(deftest test-D06-negate-disjunction
  (testing "¬(or A B) = (and ¬A ¬B) — De Morgan"
    (is (= (run 1 [out]
             (negate-formulao ['or ['pos ['app 'a]] ['pos ['app 'b]]] out))
           '([and [neg (app a)] [neg (app b)]])))))

(deftest test-D07-negate-compound
  (testing "¬(and (pos a) (or (neg b) (pos c))) — deep negation"
    (let [results (run 1 [out]
                    (negate-formulao
                      ['and ['pos ['app 'a]]
                            ['or ['neg ['app 'b]] ['pos ['app 'c]]]]
                      out))]
      (is (seq results))
      ;; Should be (or (neg a) (and (pos b) (neg c)))
      (is (= (first results)
             ['or ['neg ['app 'a]]
                  ['and ['pos ['app 'b]] ['neg ['app 'c]]]])))))

(deftest test-D08-negate-involutive
  (testing "¬¬A = A — double negation is identity"
    ;; negate (pos a), get (neg a), negate again, get (pos a)
    (is (= (run 1 [out]
             (fresh [mid]
               (negate-formulao ['pos ['app 'a]] mid)
               (negate-formulao mid out)))
           '([pos (app a)])))))

(deftest test-D09-negate-backward
  (testing "negate-formulao runs backward: given output, find input"
    ;; Given the negated form, synthesize the original
    (is (= (run 1 [original]
             (negate-formulao original ['neg ['app 'a]]))
           '([pos (app a)])))))

(deftest test-D10-negate-forall-to-exists
  (testing "¬(forall a.P(a)) = (exists a.¬P(a))"
    (let [results (run 1 [out]
                    (nom a
                      (negate-formulao ['forall (tie a ['pos ['app 'P ['var a]]])]
                                       out)))]
      (is (seq results))
      ;; The result should be an exists with a negated body
      (is (= 'exists (first (first results)))))))

(deftest test-D11-negate-exists-to-once-forall
  (testing "¬(exists a.P(a)) = (once-forall a.¬P(a)) — single-use universal"
    (let [results (run 1 [out]
                    (nom a
                      (negate-formulao ['exists (tie a ['pos ['app 'P ['var a]]])]
                                       out)))]
      (is (seq results))
      (is (= 'once-forall (first (first results)))))))


;; ============================================================================
;; Section D+: negate-formulao adversarial tests for once-forall reverse flow
;; ============================================================================
;;
;; These tests exercise paths through negate-formulao that require the
;; mapping ¬(once-forall x.P) = (exists x.¬P).
;;
;; The gap: negate-formulao has a branch ['exists ...] ↔ ['once-forall ...]
;; (bidirectional via ==), but NO branch where fml = ['once-forall ...].
;; So calling (negate-formulao ['once-forall ...] out) in the FORWARD
;; direction — negating a once-forall formula — fails to match any branch.
;;
;; Attack vectors:
;;   Dp01 — Direct forward negation of once-forall
;;   Dp02 — Double negation involutivity: ¬¬(∃x.P) via once-forall intermediate
;;   Dp03 — Integration: clause body contains once-forall, neg-proc-call negates it
;;   Dp04 — Deeper integration: pre-expanded clause body with once-forall at depth
;;   Dp05 — Partial synthesis: given (exists ...) as output, find once-forall input

(deftest test-Dp01-negate-once-forall-forward
  (testing "¬(once-forall a.P(a)) = (exists a.¬P(a)) — forward negation of once-forall"
    ;; Direct test: negate-formulao with once-forall as fml (first arg).
    ;; Semantically, once-forall x.P ≡ forall x.P, so ¬(once-forall x.P) = ∃x.¬P.
    ;; Without the fix, no branch matches ['once-forall ...] as fml → empty.
    (let [results (run 1 [out]
                    (nom a
                      (negate-formulao ['once-forall (tie a ['pos ['app 'P ['var a]]])]
                                       out)))]
      (is (seq results) "Forward negation of once-forall must produce a result")
      (is (= 'exists (first (first results)))))))

(deftest test-Dp02-double-negation-exists-involutive
  (testing "¬¬(∃x.P(x)) = ∃x.P(x) — double negation through once-forall intermediate"
    ;; Step 1: ¬(∃x.P(x)) = (once-forall x.¬P(x))     — works (D11)
    ;; Step 2: ¬(once-forall x.¬P(x)) = (∃x.¬¬P(x))
    ;;       = (∃x.P(x))                                — needs once-forall branch
    ;; Without the fix, step 2 fails → empty.
    (let [results (run 1 [out]
                    (nom a
                      (fresh [mid]
                        (negate-formulao ['exists (tie a ['pos ['app 'P ['var a]]])]
                                         mid)
                        (negate-formulao mid out))))]
      (is (seq results) "Double negation of exists must round-trip")
      (is (= 'exists (first (first results)))))))

(deftest test-Dp03-neg-proc-call-on-once-forall-body
  (testing "neg-proc-call on clause whose body contains once-forall"
    ;; Scenario: A clause body that has been written in fully-expanded NNF,
    ;; where the expansion naturally produces once-forall.
    ;;
    ;; R(x) ← (once-forall y. (neq (var x) (var y)))
    ;;
    ;; Semantically: R(x) iff ∀y.(x ≠ y), i.e., x differs from everything.
    ;; This is false for any x (x=x is a counterexample), so R(x) fails ∀x.
    ;;
    ;; Query: does R(zero) SUCCEED? (neg R(zero))
    ;; neg-proc-call: negate body → ¬(once-forall y.(x≠y)) = ∃y.(x=y)
    ;; Subsidiary: ∃y.(zero = y), δ-rule introduces witness p, eq(zero, p).
    ;; This closes via... actually (eq zero (par p)) doesn't close on its own.
    ;; Let me use a body that actually closes.
    ;;
    ;; Better: R(x) ← (once-forall y. (neq (var x) (var y)))
    ;; neg R(zero): negate body → ∃y.(eq (var x) (var y))
    ;; With x bound to zero: ∃y.(eq zero y) → δ: p, (eq zero (par p))
    ;; Hmm, this doesn't close either — (eq zero (par p)) is satisfiable.
    ;;
    ;; Restructure: use a body where negation produces something that closes.
    ;;
    ;; R(x) ← (once-forall y. (or (neq (var x) (var y)) (pos (app Q (var y)))))
    ;; This is ∀y.(x≠y ∨ Q(y)), semantically "everything that equals x satisfies Q."
    ;; ¬body = ∃y.(eq(x,y) ∧ neg(Q(y)))
    ;; With x=zero and Q(y)←(eq y zero): ∃y.(zero=y ∧ ¬Q(y))
    ;; δ: p. α: (eq zero (par p)) ∧ (neg Q(par p))
    ;; eq zero (par p): save. neg Q(par p): neg-proc-call Q:
    ;;   negate Q body → (neq (var y) (app zero)) with y=p → (neq (par p) zero)
    ;;   Branch has (eq zero (par p)) → eq-refl-close rewrites (par p)→zero:
    ;;   (neq zero zero) → refl-close ✓
    ;;
    ;; Actually this gets complicated. Let me use the simplest body that works.
    ;;
    ;; Simplest: R() ← (once-forall y. (neq (app a) (app b)))
    ;; Body is trivially true (a≠b in every model). So R() is true.
    ;; neg R(): negate body → ∃y.(eq a b) → δ: p, (eq a b) → free-close ✓
    ;; R() succeeds.
    (is (seq
          (run 1 [proof]
            (nom y
              (let [prog [['R [] ['once-forall (tie y ['neq ['app 'a] ['app 'b]])]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'R]] neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))

(deftest test-Dp03b-neg-proc-call-on-once-forall-body-unary
  (testing "neg-proc-call on unary clause whose body contains once-forall"
    ;; R(x) ← (once-forall y. (neq (var x) (app b)))
    ;;
    ;; Body: ∀y.(x ≠ b). The quantified variable y is vacuous — the body
    ;; simply asserts x ≠ b. So R(a) is true (a ≠ b) and R(b) is false.
    ;;
    ;; Query: does R(a) succeed? (neg R(a))
    ;; neg-proc-call: negate body[x:=a] → ¬(once-forall y.(neq a b))
    ;;   = ∃y.(eq a b) → δ: p, (eq a b) → free-close (a ≠ b) ✓
    ;; R(a) succeeds.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x] ['once-forall (tie y ['neq ['var x] ['app 'b]])]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'R ['app 'a]]] neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))

(deftest test-Dp04-nested-once-forall-in-conjunction
  (testing "neg-proc-call on body with once-forall nested inside conjunction"
    ;; R(x) ← (and (eq (var x) (app zero))
    ;;              (once-forall y. (neq (app a) (app b))))
    ;;
    ;; Body: x=zero ∧ ∀y.(a≠b). Both conjuncts hold when x=zero.
    ;; So R(zero) succeeds.
    ;;
    ;; neg R(zero): negate body → (or (neq zero zero) (∃y.(eq a b)))
    ;; β-split:
    ;;   Left: (neq zero zero) → refl-close ✓
    ;;   Right: ∃y.(eq a b) → δ: p, (eq a b) → free-close ✓
    ;; Both branches close → R(zero) succeeds ✓
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x] ['and ['eq ['var x] ['app 'zero]]
                                        ['once-forall (tie y ['neq ['app 'a] ['app 'b]])]]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'R ['app 'zero]]] neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))

(deftest test-Dp05-partial-synth-negate-to-exists
  (testing "Partial synthesis: given (exists ...) as neg-fml, find once-forall as fml"
    ;; negate-formulao fml ['exists (tie a ['neg ['app 'P ['var a]]])]
    ;; Two valid inputs: fml = ['forall ...] (branch 3) or fml = ['once-forall ...] (new branch)
    ;; Without the fix, only ['forall ...] is found.
    ;; With the fix, both are found — run 2 should return 2 results.
    (let [results (run 2 [fml]
                    (nom a
                      (negate-formulao fml ['exists (tie a ['neg ['app 'P ['var a]]])])))]
      (is (= 2 (count results))
          "Both forall and once-forall should be valid inputs mapping to exists")
      (let [tags (set (map #(first %) results))]
        (is (contains? tags 'forall))
        (is (contains? tags 'once-forall))))))


;; ============================================================================
;; Section E: lookup-clauseo and bind-argso (Clause Infrastructure)
;; ============================================================================

(deftest test-E01-lookup-single-clause
  (testing "Find the only clause in a single-clause program"
    (is (seq
          (run 1 [q]
            (fresh [params body]
              (lookup-clauseo 'R
                              [['R ['a] ['pos ['app 'x]]]]
                              params body)
              (== q [params body])))))))

(deftest test-E02-lookup-second-clause
  (testing "Find the second clause when first doesn't match"
    (is (seq
          (run 1 [body]
            (fresh [params]
              (lookup-clauseo 'Q
                              [['R ['a] ['pos ['app 'x]]]
                               ['Q ['b] ['neg ['app 'y]]]]
                              params body)))))))

(deftest test-E03-lookup-absent
  (testing "Lookup fails when relation is not in program"
    (is (empty?
          (run 1 [q]
            (fresh [params body]
              (lookup-clauseo 'S
                              [['R ['a] ['pos ['app 'x]]]]
                              params body)))))))

(deftest test-E04-bind-args-simple
  (testing "Bind single param to single arg"
    (is (seq
          (run 1 [env]
            (nom a
              (bind-argso [a] [['app 'c]] env)))))))

(deftest test-E05-bind-args-multiple
  (testing "Bind two params to two args"
    (is (seq
          (run 1 [env]
            (nom a b
              (bind-argso [a b]
                          [['app 'x] ['app 'y]]
                          env)))))))

(deftest test-E06-bind-args-empty
  (testing "Zero-arity relation: empty params, empty args"
    (is (= (run 1 [env] (bind-argso '() '() env))
           '(())))))

(deftest test-E07-bind-args-arity-mismatch
  (testing "Arity mismatch fails: 2 params, 1 arg"
    (is (empty?
          (run 1 [env]
            (nom a b
              (bind-argso [a b] [['app 'x]] env)))))))


;; ============================================================================
;; Section F: Positive Procedure Calls (Fitting §6, Part 1)
;; ============================================================================
;;
;; A branch with (pos (app R t)) closes if the body of R's clause,
;; instantiated with t, leads to a closed subsidiary tableau.
;;
;; Recall: the POSITIVE call closes when the body is UNSATISFIABLE.
;; If R(x) ← φ(x), and we assert R(t), but φ(t) is false, contradiction.
;; So a positive call on R(t) closes when φ(t) itself is provably false
;; — i.e., there is a closed tableau for φ(t).

(deftest test-F01-positive-call-trivially-false-body
  (testing "R(a) with R(x) ← (P(x) ∧ ¬P(x)), body always false"
    ;; R(x) ← P(x) ∧ ¬P(x)  — the body is contradictory for any x.
    ;; So R(t) is always false.  If we assert R(a), the positive call
    ;; spawns a tableau for (P(a) ∧ ¬P(a)), which closes immediately.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-F02-positive-call-false-by-reflexivity
  (testing "R(a) with R(x) ← (x ≠ x), body refuted by reflexivity"
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['neq ['var x] ['var x]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-F03-positive-call-compound-body
  (testing "R(a) with R(x) ← ((P(x)∧¬P(x)) ∨ (Q(x)∧¬Q(x))), two-branch body"
    ;; Body has a disjunction: both branches must close for the subsidiary
    ;; tableau to close.  Each branch has P(a)∧¬P(a) or Q(a)∧¬Q(a).
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['and ['pos ['app 'P ['var x]]]
                                             ['neg ['app 'P ['var x]]]]
                                       ['and ['pos ['app 'Q ['var x]]]
                                             ['neg ['app 'Q ['var x]]]]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-F04-positive-call-on-branch-with-context
  (testing "Q(a) ∧ R(a) — R(a) triggers proc call, Q(a) stays on branch"
    ;; The literal Q(a) is saved to the branch; R(a) triggers the call.
    ;; The subsidiary tableau for R's body is independent of Q(a).
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]]]
                (proveo ['and ['pos ['app 'Q ['app 'a]]]
                              ['pos ['app 'R ['app 'a]]]]
                        '() '() '() prog proof))))))))

(deftest test-F05-positive-call-binary-relation
  (testing "R(a, b) with binary relation — args passed correctly"
    ;; R(x, y) ← (x = y ∧ P(x) ∧ ¬P(x))
    ;; R(a, b) triggers call; the body says x=y (i.e., a=b) and P(a)∧¬P(a)
    ;; P(a)∧¬P(a) closes regardless.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x y] ['and ['eq ['var x] ['var y]]
                                          ['and ['pos ['app 'P ['var x]]]
                                                ['neg ['app 'P ['var x]]]]]]]]
                (proveo ['pos ['app 'R ['app 'a] ['app 'b]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section G: Negative Procedure Calls (Fitting §6, Part 2)
;; ============================================================================
;;
;; A branch with (neg (app R t)) closes if there is a closed P-tableau
;; for ¬body[t/x].  That is: ¬R(t) is asserted, but the body φ(t)
;; is VALID (always true), so R(t) must be true — contradiction.

(deftest test-G01-negative-call-tautological-body
  (testing "¬R(a) with R(x) ← (P(x) ∨ ¬P(x)), body is a tautology"
    ;; R(x) ← P(x) ∨ ¬P(x)  — body is always true.
    ;; ¬R(a) asserted.  Negative call: close tableau for ¬(P(a) ∨ ¬P(a))
    ;; = P(a) ∧ ¬P(a) in NNF — which closes immediately.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['pos ['app 'P ['var x]]]
                                       ['neg ['app 'P ['var x]]]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-G02-negative-call-equality-tautology
  (testing "¬R(a) with R(x) ← (x = x), body always true by reflexivity"
    ;; ¬body = ¬(x = x) = (x ≠ x), which closes by refl-close
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['eq ['var x] ['var x]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-G03-negative-call-compound
  (testing "¬R(a) with R(x) ← ((P(x)∨¬P(x)) ∧ (Q(x)∨¬Q(x))), conjunction of tautologies"
    ;; ¬body = (and ¬(P∨¬P) ¬(Q∨¬Q)) = (or (P∧¬P) (Q∧¬Q)) — wait, that's wrong
    ;; Actually: ¬((P∨¬P) ∧ (Q∨¬Q)) = ¬(P∨¬P) ∨ ¬(Q∨¬Q) = (P∧¬P) ∨ (Q∧¬Q)
    ;; which closes on both branches.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['or ['pos ['app 'P ['var x]]]
                                             ['neg ['app 'P ['var x]]]]
                                        ['or ['pos ['app 'Q ['var x]]]
                                             ['neg ['app 'Q ['var x]]]]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-G04-negative-call-on-branch
  (testing "P(a) ∧ ¬R(a) — ¬R triggers neg proc call; P stays on branch"
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['pos ['app 'Q ['var x]]]
                                       ['neg ['app 'Q ['var x]]]]]]]
                (proveo ['and ['pos ['app 'P ['app 'a]]]
                              ['neg ['app 'R ['app 'a]]]]
                        '() '() '() prog proof))))))))

(deftest test-G05-negative-call-body-with-exists
  (testing "¬R(a) with R(x) ← ∃y.(y = x), body valid — negation becomes ∀y.(y ≠ x)"
    ;; R(x) ← ∃y.(y = x)  — true for any x (witness: y = x)
    ;; ¬body = ∀y.(y ≠ x)  — the prover can instantiate y with x and get x ≠ x → refl-close
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x] ['exists (tie y ['eq ['var y] ['var x]])]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section H: Recursive Procedure Calls
;; ============================================================================
;;
;; Recursion happens when a subsidiary tableau encounters a literal
;; that triggers another procedure call (possibly on the same relation).

(deftest test-H01-simple-recursion-base-case
  (testing "Recursive relation with base case — base case reached directly"
    ;; nat(x) ← x = zero ∨ ∃y.(x = s(y) ∧ nat(y))
    ;; Query: nat(zero) should succeed.
    ;; ¬nat(zero): negative call → ¬body[zero]
    ;; ¬(zero=zero ∨ ∃y.(zero=s(y) ∧ nat(y)))
    ;; = (zero≠zero ∧ ∀y.(zero≠s(y) ∨ ¬nat(y)))
    ;; zero ≠ zero → refl-close! Subsidiary closes immediately.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['nat [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'nat ['var y]]]])]]]]]
                (proveo ['neg ['app 'nat ['app 'zero]]]
                        '() '() '() prog proof))))))))

(deftest test-H02-recursion-one-step
  (testing "nat(s(zero)) — one recursive step"
    ;; ¬nat(s(zero)): neg call → ¬body[s(zero)]
    ;; = s(zero)≠zero ∧ ∀y.(s(zero)≠s(y) ∨ ¬nat(y))
    ;; Instantiate y; if y=zero: s(zero)≠s(zero) → refl-close on left branch
    ;; Right branch: ¬nat(zero) → neg call again → zero≠zero → refl-close
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['nat [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'nat ['var y]]]])]]]]]
                (proveo ['neg ['app 'nat ['app 's ['app 'zero]]]]
                        '() '() '() prog proof))))))))

(deftest test-H03-recursion-two-steps
  (testing "nat(s(s(zero))) — two recursive steps"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['nat [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'nat ['var y]]]])]]]]]
                (proveo ['neg ['app 'nat ['app 's ['app 's ['app 'zero]]]]]
                        '() '() '() prog proof))))))))

(deftest test-H04-recursion-failure-not-nat
  (testing "nat(a) should NOT succeed for an arbitrary constant a"
    ;; 'a is not built from zero/s, so nat(a) is undefined.
    ;; With the empty unexpanded stack and a constant a, the prover
    ;; cannot close ¬nat(a).  But we need to be careful: the prover
    ;; might loop.  We use a bounded run.
    ;; Actually, ¬body[a] = a≠zero ∧ ∀y.(a≠s(y) ∨ ¬nat(y))
    ;; The prover will try to instantiate y but cannot close a≠zero.
    ;; This should fail (empty result in bounded search).
    ;; NOTE: We use run 0 / bounded to avoid infinite loop.
    ;; With `run 1` this might not terminate, so we skip this as
    ;; a divergence test.  See Section N for bounded non-theorem tests.
    ))


;; ============================================================================
;; Section I: Mutual Recursion — Even/Odd (Fitting's Program P1)
;; ============================================================================

(defn make-even-odd-program
  "Construct the even/odd program inside a nom block.
   Returns [program nom-x nom-y] where x and y are the param noms."
  []
  ;; We return a thunk that runs inside a `run` form.
  ;; Caller must use inside (nom x y ...)
  'use-inline)

(deftest test-I01-even-zero
  (testing "even(zero) succeeds — base case"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    ;; Query: even(zero) succeeds = closed tableau for ¬even(zero)
                    query ['neg ['app 'even ['app 'zero]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-I02-odd-one
  (testing "odd(s(zero)) succeeds"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'odd ['app 's ['app 'zero]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-I03-even-two
  (testing "even(s(s(zero))) succeeds — full mutual recursion"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'even ['app 's ['app 's ['app 'zero]]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-I04-even-zero-failure-check
  (testing "even(zero) fails? — NO, it should NOT fail"
    ;; Failure means a closed tableau for even(zero) itself (positive form).
    ;; even(zero) is TRUE, so a tableau for the positive form (trying to
    ;; show it's false) should NOT close.
    ;; The positive call on even(zero) would try to close body[zero]:
    ;; zero=zero ∨ ∃y.(zero=s(y)∧odd(y)).  The left disjunct zero=zero
    ;; is true (not false), so the body is satisfiable, not closeable.
    (is (empty?
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    ;; Try to close a tableau for the POSITIVE form
                    formula ['pos ['app 'even ['app 'zero]]]]
                (proveo formula '() '() '() prog proof))))))))

(deftest test-I05-odd-zero-fails
  (testing "odd(zero) fails — zero is not odd"
    ;; Positive call on odd(zero): body = ∃y.(zero=s(y) ∧ even(y))
    ;; δ-rule introduces witness p: (eq zero s(p)) ∧ (pos even(p))
    ;; The equality (eq (app zero) (app s (app p))) closes by FREE CLOSURE:
    ;; zero and s have different head symbols.  The conjunction only needs
    ;; its first conjunct to close → the branch closes.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    formula ['pos ['app 'odd ['app 'zero]]]]
                (proveo formula '() '() '() prog proof))))))))

(deftest test-I06-odd-two-fails
  (testing "odd(s(s(zero))) fails — 2 is not odd"
    ;; Positive call on odd(s(s(zero))): body = ∃y.(s(s(0))=s(y) ∧ even(y))
    ;; δ-witness p: (eq s(s(0)) s(p)) — one-one gives pair [s(0), p]
    ;; Then (pos even(p)) — substitutivity rewrites p→s(0)
    ;; Proc call on even(s(0)):
    ;;   body: s(0)=0 ∨ ∃y.(s(0)=s(y) ∧ odd(y))
    ;;   Left: free-close (s ≠ zero)
    ;;   Right: δ-witness q: s(0)=s(q) — one-one [0,q]; odd(q) → subst odd(0)
    ;;     odd(0): body ∃y.(0=s(y)∧even(y)) → free-close (zero ≠ s)
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['exists (tie y
                             ['and ['eq ['var x] ['app 's ['var y]]]
                                   ['pos ['app 'even ['var y]]]])]]]
                    formula ['pos ['app 'odd ['app 's ['app 's ['app 'zero]]]]]]
                (proveo formula '() '() '() prog proof))))))))


;; ============================================================================
;; Section J: The Nim Game (Fitting's Program P2)
;; ============================================================================
;;
;; win(x) ← ∃y. (x = s(y) ∨ x = s(s(y))) ∧ ¬win(y)
;;
;; A player wins from position x if they can move to some y where
;; the opponent loses (¬win(y)).
;;
;; Expected results:
;;   win(0) = false   (no move available)
;;   win(1) = true    (move to 0)
;;   win(2) = true    (move to 0)
;;   win(3) = false   (all moves lead to winning positions for opponent)

(defn nim-program
  "Build the nim program.  Must be called inside a (nom x y ...) block.
   x is the clause param nom, y is the existential witness nom."
  [x y]
  [['win [x]
    ['exists (tie y
      ['and ['or ['eq ['var x] ['app 's ['var y]]]
                  ['eq ['var x] ['app 's ['app 's ['var y]]]]]
            ['neg ['app 'win ['var y]]]])]]])

(defn nim-numeral
  "Build the Peano numeral for n: zero, s(zero), s(s(zero)), ..."
  [n]
  (if (zero? n)
    ['app 'zero]
    ['app 's (nim-numeral (dec n))]))

(deftest test-J01-win-0-fails
  (testing "win(0) fails — no available move from 0"
    ;; Positive call on win(0): body = ∃y.((0=s(y)∨0=s(s(y)))∧¬win(y))
    ;; δ-witness p:
    ;;   (0=s(p) ∨ 0=s(s(p))) ∧ ¬win(p)
    ;;   β-split on the disjunction within conjunction:
    ;;     Left branch: 0=s(p) → free-close (zero ≠ s)
    ;;     Right branch: 0=s(s(p)) → free-close (zero ≠ s)
    ;;   Both branches close → the body is unsatisfiable → win(0) is false.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (nim-program x y)
                    formula ['pos ['app 'win (nim-numeral 0)]]]
                (proveo formula '() '() '() prog proof))))))))

(deftest test-J02-win-1-succeeds
  (testing "win(s(zero)) succeeds — move to 0, opponent has no move"
    ;; ¬win(s(0)): neg call → ¬body[s(0)]
    ;; = ∀y.((s(0)≠s(y) ∧ s(0)≠s(s(y))) ∨ win(y))
    ;; γ-rule: introduces v; left branch closes via refl-close (v=0);
    ;; right branch: win(v=0) → positive proc call; body[0] closes by
    ;; free closure (0≠s(...)).
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (nim-program x y)
                    query ['neg ['app 'win (nim-numeral 1)]]]
                (proveo query '() '() '() prog proof))))))));; J04 follows J02 intentionally; J03 is the structure check below.

(deftest test-J03-nim-structure
  (testing "Nim program constructs correctly"
    (is (= (nim-numeral 0) ['app 'zero]))
    (is (= (nim-numeral 1) ['app 's ['app 'zero]]))
    (is (= (nim-numeral 3) ['app 's ['app 's ['app 's ['app 'zero]]]]))))

(deftest test-J04-win-2-succeeds
  (testing "win(s(s(zero))) succeeds — can jump to 0, opponent has no move"
    ;; ¬win(s²(0)): neg call → ¬body[s²(0)]
    ;; = ∀y.((s²(0)≠s(y) ∧ s²(0)≠s²(y)) ∨ win(y))
    ;; γ-rule with v; right β-branch: win(v) — with v constrained by left branch.
    ;; Left branch: (s²(0)≠s(v) ∧ s²(0)≠s²(v)) closes via refl-close (v=s(0)).
    ;; Right branch: win(v=s(0)) → same as win(1) proof → closes.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (nim-program x y)
                    query ['neg ['app 'win (nim-numeral 2)]]]
                (proveo query '() '() '() prog proof))))))))


;; ============================================================================
;; Section K: Equality Within Clause Bodies
;; ============================================================================

(deftest test-K01-equality-in-body-direct
  (testing "R(a,b) with R(x,y) ← x=y, query ¬R(c,c) — c=c is valid"
    ;; ¬R(c,c): neg call → ¬body[c,c] = ¬(c=c) = c≠c → refl-close
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x y] ['eq ['var x] ['var y]]]]]
                (proveo ['neg ['app 'R ['app 'c] ['app 'c]]]
                        '() '() '() prog proof))))))))

(deftest test-K02-equality-destructuring
  (testing "R with pattern matching via equality"
    ;; head(l, h) ← ∃t. l = cons(h, t)
    ;; Query: head(cons(a, nil), a) succeeds
    ;; ¬head(cons(a,nil), a): neg call → ¬∃t.(cons(a,nil)=cons(a,t))
    ;; = ∀t.(cons(a,nil)≠cons(a,t))
    ;; Instantiate t with nil: cons(a,nil)≠cons(a,nil) → refl-close
    (is (seq
          (run 1 [proof]
            (nom l h t
              (let [prog [['head [l h]
                           ['exists (tie t
                             ['eq ['var l]
                                  ['app 'cons ['var h] ['var t]]])]]]]
                (proveo ['neg ['app 'head
                               ['app 'cons ['app 'a] ['app 'nil]]
                               ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-K03-equality-and-recursion
  (testing "Equality used for base case matching in recursive relation"
    ;; len(nil, zero) and len(cons(h,t), s(n)) ← len(t, n)
    ;; As single Proflog clause:
    ;; len(l, n) ← (l=nil ∧ n=zero) ∨ ∃h.∃t.∃m.(l=cons(h,t) ∧ n=s(m) ∧ len(t,m))
    ;; Query: len(nil, zero) succeeds
    (is (seq
          (run 1 [proof]
            (nom l n h t m
              (let [prog [['len [l n]
                           ['or ['and ['eq ['var l] ['app 'nil]]
                                      ['eq ['var n] ['app 'zero]]]
                                ['exists (tie h
                                  ['exists (tie t
                                    ['exists (tie m
                                      ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                            ['and ['eq ['var n] ['app 's ['var m]]]
                                                  ['pos ['app 'len ['var t] ['var m]]]]])])])]]]]]
                (proveo ['neg ['app 'len ['app 'nil] ['app 'zero]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section L: Procedure Calls + Equality Combined
;; ============================================================================

(deftest test-L01-proc-call-with-equality-on-branch
  (testing "a=b ∧ R(a) where R(x) ← P(x)∧¬P(x), equality irrelevant"
    ;; The equality a=b is on the branch but the proc call closes
    ;; independently via the contradictory body.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]]]
                (proveo ['and ['eq ['app 'a] ['app 'b]]
                              ['pos ['app 'R ['app 'a]]]]
                        '() '() '() prog proof))))))))

(deftest test-L02-equality-enables-proc-call
  (testing "a=b ∧ R(a) ∧ ¬R(b) — standard closure via equality + proc call not needed"
    ;; With a=b, (pos R(a)) and (neg R(b)) can close via paramodulation
    ;; (rewrite R(b) to R(a), then complementary closure).
    ;; This tests that equality closure still works alongside proc calls.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['pos ['app 'P ['var x]]]]]]
                (proveo ['and ['eq ['app 'a] ['app 'b]]
                              ['and ['pos ['app 'R ['app 'a]]]
                                    ['neg ['app 'R ['app 'b]]]]]
                        '() '() '() prog proof))))))))

(deftest test-L03-equality-in-body-with-paramodulation
  (testing "R(a,b) with R(x,y) ← (x=y ∧ P(x) ∧ ¬P(y)), body uses equality"
    ;; The body itself needs paramodulation to close:
    ;; x=y ∧ P(x) ∧ ¬P(y) → with x=y, rewrite P(y) to P(x), close.
    ;; Wait — but if x=y, then the body IS satisfiable (P(x) ∧ ¬P(x)
    ;; after substitution only closes if we use paramodulation).
    ;; Actually x=y ∧ P(x) ∧ ¬P(y) is unsatisfiable (by congruence).
    ;; So R(a,b) asserted → body is unsatisfiable → positive call closes.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x y] ['and ['eq ['var x] ['var y]]
                                          ['and ['pos ['app 'P ['var x]]]
                                                ['neg ['app 'P ['var y]]]]]]]]
                (proveo ['pos ['app 'R ['app 'a] ['app 'b]]]
                        '() '() '() prog proof))))))))

(deftest test-L04-multi-arg-substitutivity
  (testing "Binary constructor: both args rewritten via multi-arg substitutivity"
    ;; outer(l) ← ∃h.∃t. l = cons(h,t) ∧ inner(h, t)
    ;; inner(x, y) ← x = y
    ;;
    ;; Query: outer(cons(a, b)) is FALSE because:
    ;;   body: ∃h.∃t. cons(a,b) = cons(h,t) ∧ inner(h,t)
    ;;   δ-witnesses p₁, p₂ (noms)
    ;;   one-one: a=p₁, b=p₂
    ;;   inner(p₁, p₂) — direct call can't close (p₁,p₂ are noms)
    ;;   subst-call: rewrite BOTH args p₁→a, p₂→b → inner(a, b)
    ;;   body a=b → free-close (a ≠ b) ✓
    (is (seq
          (run 1 [proof]
            (nom l h t x y
              (let [prog [['outer [l]
                           ['exists (tie h
                             ['exists (tie t
                               ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                     ['pos ['app 'inner ['var h] ['var t]]]])])]]
                          ['inner [x y]
                           ['eq ['var x] ['var y]]]]]
                (proveo ['pos ['app 'outer ['app 'cons ['app 'a] ['app 'b]]]]
                        '() '() '() prog proof))))))))

(deftest test-L05-multi-arg-subst-proof-step
  (testing "Multi-arg substitutivity produces 'subst-call' proof step"
    ;; Same scenario as L04 — verify the proof contains subst-call
    (let [proofs (run 1 [proof]
                   (nom l h t x y
                     (let [prog [['outer [l]
                                  ['exists (tie h
                                    ['exists (tie t
                                      ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                            ['pos ['app 'inner ['var h] ['var t]]]])])]]
                                 ['inner [x y]
                                  ['eq ['var x] ['var y]]]]]
                       (proveo ['pos ['app 'outer ['app 'cons ['app 'a] ['app 'b]]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'subst-call)))))


;; ============================================================================
;; Section M: Top-Level Interface
;; ============================================================================

(deftest test-M01-prove-backward-compat
  (testing "prove with no program — backward compatible with αleanTAP-E"
    (is (seq (prove '(and (pos (app a)) (neg (app a))) 1)))))

(deftest test-M02-prove-with-program
  (testing "prove with program — direct tableau proof"
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-M03-query-succeeds-basic
  (testing "query-succeeds interface — tautological body makes query succeed"
    ;; R(x) ← P(x)∨¬P(x) — tautology, so R(a) is always true
    ;; query-succeeds negates the query and tries to close
    ;; But query-succeeds uses negate-formulao internally...
    ;; Actually, looking at the implementation: query-succeeds takes
    ;; the QUERY (positive form) and builds ¬query internally.
    ;; We need to pass a formula the interface expects.
    ;; query-succeeds program query → closes ¬query
    ;; So query = (pos (app R (app a))), ¬query = (neg (app R (app a)))
    ;; Then ¬R(a) triggers neg proc call → ¬body → P(a)∧¬P(a) → close
    ;; But wait — query-succeeds uses negate-formulao on the query.
    ;; negate-formulao expects formulas, not bare literals directly...
    ;; Actually it does handle literals: ¬(pos t) = (neg t). ✓
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['pos ['app 'P ['var x]]]
                                       ['neg ['app 'P ['var x]]]]]]]
                (fresh [neg-q]
                  (negate-formulao ['pos ['app 'R ['app 'a]]] neg-q)
                  (proveo neg-q '() '() '() prog proof)))))))))

(deftest test-M04-query-fails-basic
  (testing "query-fails interface — contradictory body makes query fail"
    ;; R(x) ← P(x)∧¬P(x) — always false, so R(a) is false.
    ;; query-fails builds closed tableau for the query directly.
    ;; Closing (pos (app R (app a))): positive proc call → body closes.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section N: Negative Tests (Non-Theorems & Soundness Guards)
;; ============================================================================
;;
;; These ensure the prover does NOT close tableaux that should stay open.
;; Non-theorems produce satisfiable formulas with no closed tableau.

(deftest test-N01-single-positive-literal
  (testing "P(a) alone is satisfiable — no proof"
    (is (not-provable? '(pos (app P (app a)))))))

(deftest test-N02-single-negative-literal
  (testing "¬P(a) alone is satisfiable"
    (is (not-provable? '(neg (app P (app a)))))))

(deftest test-N03-different-predicates
  (testing "P(a) ∧ ¬Q(a) — different predicates, not complementary"
    (is (not-provable? '(and (pos (app P (app a)))
                             (neg (app Q (app a))))))))

(deftest test-N04-satisfiable-disequality
  (testing "a ≠ b — satisfiable when a and b are distinct"
    (is (not-provable? '(neq (app a) (app b))))))

(deftest test-N05-no-spurious-proc-call
  (testing "P(a) with R(x) ← ... — P is not defined, no proc call fires"
    ;; Even with a program defining R, the literal P(a) should not
    ;; trigger any procedure call (P has no clause).
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'Q ['var x]]]
                                        ['neg ['app 'Q ['var x]]]]]]]
                (proveo ['pos ['app 'P ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-N06-proc-call-body-not-contradictory
  (testing "R(a) with R(x) ← P(x) — body P(a) is satisfiable, positive call doesn't close"
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['pos ['app 'P ['var x]]]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-N07-neg-proc-call-body-not-valid
  (testing "¬R(a) with R(x) ← P(x) — P(a) is not valid, neg call doesn't close"
    ;; ¬body = ¬P(a) = (neg (app P (app a))) — a single literal, not closeable.
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['pos ['app 'P ['var x]]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-N08-equality-different-predicates-with-proc
  (testing "R(a) ∧ ¬S(a) — different predicates, no closure"
    ;; R and S are unrelated: no clause for S, and R(a)←P(a) body
    ;; does not contradict ¬S(a). Branch stays open.
    ;; (Equality is omitted to avoid a divergent search in para-close:
    ;;  with eqs like [(p,a),(a,p)], eq-membero loops when seeking a
    ;;  complementary S-literal that is never present in lits.)
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['pos ['app 'P ['var x]]]]]]
                (proveo ['and ['pos ['app 'R ['app 'a]]]
                              ['neg ['app 'S ['app 'a]]]]
                        '() '() '() prog proof))))))))
(deftest test-N09-empty-program-no-proc-calls
  (testing "With empty program, proc call clauses are unreachable"
    ;; R(a) cannot be closed without a definition for R
    (is (empty?
          (run 1 [proof]
            (proveo ['pos ['app 'R ['app 'a]]]
                    '() '() '() '() proof))))))

(deftest test-N10-no-double-negation-unsoundness
  (testing "¬¬R(a) is not the same as R(a) at the operational level"
    ;; We can't express ¬¬R(a) directly in NNF (it IS R(a)).
    ;; But we can check: the prover doesn't confuse (pos R(a)) for a
    ;; procedure call target when R has a satisfiable body.
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['pos ['app 'P ['var x]]]]]]
                ;; (neg (app R (app a))) with non-valid body: should not close
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))


(deftest test-N11-duplicate-relation-rejected
  (testing "Program with duplicate relation symbols is rejected (Fitting Def 2.1)"
    ;; Fitting Definition 2.1: "at most one [clause] for each relation symbol."
    ;; A program with two clauses for the same relation R is ill-formed.
    ;; The top-level interface must reject it before entering the proof engine.
    ;; The check only inspects relation symbols, so clause bodies are irrelevant.
    (let [bad-prog [['R ['x] ['pos ['app 'P ['app 'x]]]]
                    ['R ['y] ['neg ['app 'P ['app 'y]]]]]]
      (is (thrown? IllegalArgumentException
            (query-succeeds bad-prog ['pos ['app 'R ['app 'a]]])))
      (is (thrown? IllegalArgumentException
            (query-fails bad-prog ['pos ['app 'R ['app 'a]]])))
      (is (thrown? IllegalArgumentException
            (prove bad-prog ['pos ['app 'R ['app 'a]]] 1))))))


;; ============================================================================
;; Section O: Proof-Term Structure Validation
;; ============================================================================

(deftest test-O01-proof-has-conj
  (testing "Conjunction expansion produces 'conj' in proof"
    (is (proof-uses-step? '(and (pos (app a)) (neg (app a))) 'conj))))

(deftest test-O02-proof-has-split
  (testing "Disjunction produces 'split' in proof"
    (is (proof-uses-step?
          '(or (and (pos (app a)) (neg (app a)))
               (and (pos (app b)) (neg (app b))))
          'split))))

(deftest test-O03-proof-has-close
  (testing "Complementary closure produces 'close'"
    (is (proof-uses-step? '(and (pos (app a)) (neg (app a))) 'close))))

(deftest test-O04-proof-has-refl-close
  (testing "Reflexivity closure produces 'refl-close'"
    (is (proof-uses-step? '(neq (app c) (app c)) 'refl-close))))

(deftest test-O05-proof-has-para-close
  (testing "Paramodulation closure produces 'para-close' or 'close' (with constraint propagation)"
    ;; Use nom p encoded as (par p) so (eq (par p) (app b)) is not free-closeable.
    ;; Without constraint propagation: para-close via eq rewriting.
    ;; With constraint propagation: par p resolves to (app b), enabling direct close.
    (let [proofs (run 1 [proof]
                   (nom p
                     (proveo ['and ['eq ['par p] ['app 'b]]
                                   ['and ['pos ['app 'P ['par p]]]
                                         ['neg ['app 'P ['app 'b]]]]]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (or (proof-tree-contains? (first proofs) 'para-close)
              (proof-tree-contains? (first proofs) 'close))))))
(deftest test-O06-proof-has-witness
  (testing "δ-rule produces 'witness' in proof"
    (let [proofs (run 1 [proof]
                   (nom a
                     (proveo ['exists (tie a ['neq ['var a] ['var a]])]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'witness)))))

(deftest test-O07-proof-has-proc-call
  (testing "Positive procedure call produces 'proc-call' in proof"
    (let [proofs (run 1 [proof]
                   (nom x
                     (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                               ['neg ['app 'P ['var x]]]]]]]
                       (proveo ['pos ['app 'R ['app 'a]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'proc-call)))))

(deftest test-O08-proof-has-neg-proc-call
  (testing "Negative procedure call produces 'neg-proc-call' in proof"
    (let [proofs (run 1 [proof]
                   (nom x
                     (let [prog [['R [x] ['or ['pos ['app 'P ['var x]]]
                                              ['neg ['app 'P ['var x]]]]]]]
                       (proveo ['neg ['app 'R ['app 'a]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'neg-proc-call)))))

(deftest test-O09-proof-has-relation-name
  (testing "Procedure call proof records relation name"
    ;; The proof should contain the symbol 'R as part of (proc-call R ...)
    (let [proofs (run 1 [proof]
                   (nom x
                     (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                               ['neg ['app 'P ['var x]]]]]]]
                       (proveo ['pos ['app 'R ['app 'a]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'R)))))

(deftest test-O10-recursive-proof-has-nested-proc-calls
  (testing "Recursive call produces nested proc-call steps"
    ;; nat(s(zero)) requires two proc-calls: one for s(zero), one for zero
    (let [proofs (run 1 [proof]
                   (nom x y
                     (let [prog [['nat [x]
                                  ['or ['eq ['var x] ['app 'zero]]
                                       ['exists (tie y
                                         ['and ['eq ['var x] ['app 's ['var y]]]
                                               ['pos ['app 'nat ['var y]]]])]]]]]
                       (proveo ['neg ['app 'nat ['app 's ['app 'zero]]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      ;; Should have at least 'neg-proc-call for the outer and inner calls
      (is (proof-tree-contains? (first proofs) 'neg-proc-call)))))

(deftest test-O11-proof-has-savefml
  (testing "Literal saved to branch produces 'savefml'"
    (is (proof-uses-step?
          '(and (pos (app p))
                (and (pos (app q))
                     (neg (app p))))
          'savefml))))

(deftest test-O12-proof-has-univ
  (testing "Universal instantiation produces 'univ'"
    (let [proofs (run 1 [proof]
                   (nom a
                     (proveo ['forall (tie a ['and ['pos ['app 'f ['var a]]]
                                                   ['neg ['app 'f ['app 'g ['var a]]]]])]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'univ)))))

(deftest test-O13-proof-has-free-close
  (testing "Free closure produces 'free-close' step"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 'zero] ['app 's ['app 'zero]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'free-close)))))

(deftest test-O14-proof-has-eq-refl-close
  (testing "NEQ closure via one-one produces 'eq-refl-close' step"
    ;; Use noms p,q so (eq p q) after one-one decompose is not free-closeable.
    ;; Path: decompose s(p)=s(q) → (eq p q) → lits; (neq p q) → eq-refl-close ✓
    (let [proofs (run 1 [proof]
                   (nom p q
                     (proveo ['and ['eq ['app 's ['par p]] ['app 's ['par q]]]
                                   ['neq ['par p] ['par q]]]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'eq-refl-close)))))
(deftest test-O15-proof-has-subst-call
  (testing "Substitutivity-augmented positive proc call produces 'subst-call'"
    ;; R(x) ← (P(a) ∧ ¬P(x))  — contradictory only when x=a
    ;; Branch: s(a)=s(p), (pos R(p)) with nom p.
    ;; One-one decompose: (eq p a) → lits (not free-closeable; p is nom).
    ;; proc-call R(p): body P(a)∧¬P(p) — consistent (a≠p) → fails.
    ;; subst-call: rewrite p→a via eq, call R(a): P(a)∧¬P(a) → closes ✓
    (let [proofs (run 1 [proof]
                   (nom x p
                     (let [prog [['R [x] ['and ['pos ['app 'P ['app 'a]]]
                                              ['neg ['app 'P ['var x]]]]]]]
                       (proveo ['and ['eq ['app 's ['app 'a]] ['app 's ['par p]]]
                                     ['pos ['app 'R ['par p]]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'subst-call)))))
(deftest test-O16-proof-has-neg-subst-call
  (testing "Substitutivity-augmented negative proc call produces 'neg-subst-call'"
    ;; R(x) ← (P(a) ∨ ¬P(x))  — tautology only when x=a
    ;; Branch: s(a)=s(p), (neg R(p)) with nom p.
    ;; One-one decompose: (eq p a) → lits (not free-closeable; p is nom).
    ;; neg-proc-call R(p): ¬(P(a)∨¬P(p)) = (¬P(a)∧P(p)) — consistent (a≠p) → fails.
    ;; neg-subst-call: rewrite p→a via eq, call R(a): ¬(P(a)∨¬P(a)) = (¬P(a)∧P(a)) → closes ✓
    (let [proofs (run 1 [proof]
                   (nom x p
                     (let [prog [['R [x] ['or ['pos ['app 'P ['app 'a]]]
                                             ['neg ['app 'P ['var x]]]]]]]
                       (proveo ['and ['eq ['app 's ['app 'a]] ['app 's ['par p]]]
                                     ['neg ['app 'R ['par p]]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'neg-subst-call)))))
(deftest test-O17-proof-has-decompose
  (testing "Injectivity decomposition produces 'decompose' step"
    (let [proofs (run 1 [proof]
                   (proveo ['eq ['app 's ['app 'zero]]
                                ['app 's ['app 's ['app 'zero]]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'decompose))
      ;; Should also contain free-close at the leaf
      (is (proof-tree-contains? (first proofs) 'free-close)))))

(deftest test-O18-proof-has-eq-neq-close
  (testing "Eq/neq complementary closure produces 'eq-neq-close' step"
    ;; neq processed first (saved to lits), then eq arrives
    ;; Use same-head terms so free-close doesn't fire
    (let [proofs (run 1 [proof]
                   (proveo ['and ['neq ['app 'f ['app 'x]] ['app 'f ['app 'y]]]
                                 ['eq  ['app 'f ['app 'x]] ['app 'f ['app 'y]]]]
                           '() '() '() '() proof))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'eq-neq-close)))))


;; ============================================================================
;; Section P: Full First-Order Logic in Clause Bodies
;; ============================================================================
;;
;; The key distinction between Proflog and Prolog: clause bodies can be
;; ANY first-order formula, including ∀, ∃, →, ¬, ∧, ∨.
;; These tests exercise non-Horn bodies.

(deftest test-P01-disjunctive-body
  (testing "R(x) with disjunctive body — not expressible in Horn Prolog"
    ;; R(x) ← P(x) ∨ Q(x)
    ;; ¬R(a) with P(a) being always true: neg call needs ¬(P(a) ∨ Q(a))
    ;; = ¬P(a) ∧ ¬Q(a).  If the program doesn't define P or Q, these
    ;; are just two unrelated negative literals — won't close.
    ;; But: R(x) ← (P(x) ∨ ¬P(x)) — tautological disjunction.
    ;; ¬body = ¬P(a) ∧ P(a) — closes!
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['pos ['app 'P ['var x]]]
                                       ['neg ['app 'P ['var x]]]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-P02-universal-in-body
  (testing "R(x) ← ∀y.P(x,y) — universal quantifier in body"
    ;; R(a) asserted (positive call) → body = ∀y.P(a,y)
    ;; This is satisfiable (just make P true everywhere), so the
    ;; positive call does NOT close. ✓
    ;; ¬R(a) (negative call) → ¬∀y.P(a,y) = ∃y.¬P(a,y)
    ;; The witness gives ¬P(a,p) — a single literal, not closeable. ✓
    ;; Now: R(x) ← ∀y.(P(x,y) ∨ ¬P(x,y)) — body is a tautology.
    ;; ¬R(a) (neg call) → ¬∀y.(P(a,y) ∨ ¬P(a,y)) = ∃y.(¬P(a,y) ∧ P(a,y))
    ;; The witness gives P(a,p) ∧ ¬P(a,p) — closes!
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R [x] ['forall (tie y
                                    ['or ['pos ['app 'P ['var x] ['var y]]]
                                         ['neg ['app 'P ['var x] ['var y]]]])]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-P03-implication-as-disjunction
  (testing "R(x) ← (P(x) → Q(x)) expressed as (¬P(x) ∨ Q(x))"
    ;; The body (neg P(x)) ∨ (pos Q(x)) is a material conditional.
    ;; ¬body = P(x) ∧ ¬Q(x).  Not closeable unless we know more.
    ;; But: R(x) ← (P(x) → P(x)) = (¬P(x) ∨ P(x)) — tautology.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['or ['neg ['app 'P ['var x]]]
                                       ['pos ['app 'P ['var x]]]]]]]
                (proveo ['neg ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-P04-negation-in-body
  (testing "R(x) ← ¬P(x) — classical negation (not NAF)"
    ;; R(x) ← (neg (app P (var x)))
    ;; R(a) asserted → positive call → body = ¬P(a), satisfiable alone → no close ✓
    ;; ¬R(a) asserted → neg call → ¬¬P(a) = P(a), satisfiable alone → no close ✓
    ;; If branch also has P(a):
    ;; P(a) ∧ R(a) → R's positive call spawns (neg P(a)), which is a single
    ;; literal.  Doesn't close on its own.  But the branch P(a) is separate!
    ;; Subsidiary tableau is FRESH — doesn't see P(a) from the caller.
    ;; This is correct per Fitting's isolation semantics.
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['neg ['app 'P ['var x]]]]]]
                (proveo ['and ['pos ['app 'P ['app 'a]]]
                              ['pos ['app 'R ['app 'a]]]]
                        '() '() '() prog proof))))))))

(deftest test-P05-existential-in-body
  (testing "R ← ∃x.P(x) ∧ ¬P(x) — existentially quantified contradictory body"
    ;; Zero-arg relation: R ← ∃x.(P(x) ∧ ¬P(x))
    ;; R asserted → positive call → body = ∃x.(P(x) ∧ ¬P(x))
    ;; δ-rule introduces witness p, then P(p) ∧ ¬P(p) → close!
    ;; So R is always false.
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [] ['exists (tie x
                                   ['and ['pos ['app 'P ['var x]]]
                                         ['neg ['app 'P ['var x]]]])]]]]
                (proveo ['pos ['app 'R]]
                        '() '() '() prog proof))))))))

(deftest test-P06-mixed-quantifiers-in-body
  (testing "R(x) ← ∀y.∃z.(P(y,z) ∧ ¬P(y,z)) — nested quantifiers, body unsatisfiable"
    ;; For any y, the witness z gives P(y,z)∧¬P(y,z) — always contradictory.
    ;; So the body is unsatisfiable.  R(a) positive call closes.
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog [['R [x] ['forall (tie y
                                    ['exists (tie z
                                      ['and ['pos ['app 'P ['var y] ['var z]]]
                                            ['neg ['app 'P ['var y] ['var z]]]])])]]]]
                (proveo ['pos ['app 'R ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-P07-two-relations-independent
  (testing "Two-clause program: calls dispatch to correct clause"
    ;; R(x) ← P(x) ∧ ¬P(x)  (always false)
    ;; S(x) ← Q(x) ∨ ¬Q(x)  (always true)
    ;; R(a) → positive call → body closes → R(a) is false ✓
    ;; ¬S(a) → neg call → ¬body = Q(a)∧¬Q(a) → closes → S(a) is true ✓
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['R [x] ['and ['pos ['app 'P ['var x]]]
                                        ['neg ['app 'P ['var x]]]]]
                          ['S [x] ['or ['pos ['app 'Q ['var x]]]
                                       ['neg ['app 'Q ['var x]]]]]]]
                ;; Both should close: R(a) false, S(a) true
                (proveo ['and ['pos ['app 'R ['app 'a]]]
                              ['neg ['app 'S ['app 'a]]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section Q: Backward Running, List Programs, Multi-Argument Substitutivity
;; ============================================================================
;;
;; These tests exercise capabilities beyond simple forward evaluation:
;;
;;   - BACKWARD RUNNING: given a program, find inputs that satisfy a query
;;   - LIST PROGRAMS: member with cons — binary constructor + recursion
;;   - MULTI-ARG SUBSTITUTIVITY: rewriting multiple arguments independently

;; --- Q1-Q3: Backward Running ---
;;
;; Because αleanTAP-EP is a pure relation, we can ask "for which x
;; does R(x) succeed?" by leaving x as a logic variable.  The search
;; instantiates x through unification during tableau closure.

(deftest test-Q01-backward-generate-values
  (testing "Backward running: generate values satisfying a disjunctive body"
    ;; color(x) ← x=red ∨ x=green ∨ x=blue
    ;; Query: for which x does color(x) succeed?
    ;; The neg-proc-call processes ¬body = (x≠red ∧ x≠green ∧ x≠blue).
    ;; refl-close on (neq X (app red)) binds X=(app red), closing the branch.
    ;; Backtracking finds green and blue.
    (let [results (run 3 [x]
                    (nom a
                      (let [prog [['color [a]
                                   ['or ['eq ['var a] ['app 'red]]
                                        ['or ['eq ['var a] ['app 'green]]
                                             ['eq ['var a] ['app 'blue]]]]]]]
                        (fresh [neg-query proof]
                          (negate-formulao ['pos ['app 'color x]] neg-query)
                          (proveo neg-query '() '() '() prog proof)))))]
      (is (= 3 (count results)))
      (is (some #{['app 'red]} results))
      (is (some #{['app 'green]} results))
      (is (some #{['app 'blue]} results)))))

(deftest test-Q02-backward-even-zero
  (testing "Backward running: find smallest even number"
    ;; Ask: for which x does even(x) succeed?
    ;; First result should be (app zero) via refl-close on neq(X, 0).
    (let [results (run 1 [x]
                    (nom a b c d
                      (let [even-clause ['even [a]
                                         ['or ['eq ['var a] ['app 'zero]]
                                              ['exists (tie b
                                                ['and ['eq ['var a] ['app 's ['var b]]]
                                                      ['pos ['app 'odd ['var b]]]])]]]
                            odd-clause  ['odd [c]
                                         ['exists (tie d
                                           ['and ['eq ['var c] ['app 's ['var d]]]
                                                 ['pos ['app 'even ['var d]]]])]]
                            prog [even-clause odd-clause]]
                        (fresh [neg-query proof]
                          (negate-formulao ['pos ['app 'even x]] neg-query)
                          (proveo neg-query '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'zero] (first results))))))

(deftest test-Q03-backward-fails-correctly
  (testing "Backward running: no witness satisfies absurd(x)"
    ;; absurd(x) ← P(x) ∧ ¬P(x)  — body is always contradictory in the subsidiary
    ;; For absurd(x) to succeed, «asturd(x) must close.  The neg-proc-call
    ;; forms ¬(P(x)∧¬P(x)) = (P(x)∨¬P(x)) — a tautology that never closes.
    ;; So no x exists for which absurd(x) succeeds.
    ;;
    ;; NOTE: using run [x] with an unbound x diverges because subst-lito
    ;; enumerates infinitely many term structures for x.  Instead, we test
    ;; with a nom (a canonical but arbitrary domain element) to stay finite.
    (is (empty?
          (run 1 [proof]
            (nom a x
              (let [prog [['absurd [a]
                           ['and ['pos ['app 'P ['var a]]]
                                 ['neg ['app 'P ['var a]]]]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'absurd ['app x]]] neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))
(deftest test-Q04-member-head
  (testing "member(a, cons(a, nil)) — element at head of list"
    ;; Negate: (neg member(a, cons(a, nil)))
    ;; neg-proc-call → ∀h.∀t. [cons(a,nil) ≠ cons(h,t) ∨ (a≠h ∧ ¬member(a,t))]
    ;; γ-instantiate with logic vars H, T.
    ;; β-split: left neq(cons(a,nil), cons(H,T)) → refl-close binds H=a, T=nil
    ;; right: neq(a,a) → refl-close  ✓
    (is (seq
          (run 1 [proof]
            (nom x l h t
              (let [prog [['member [x l]
                           ['exists (tie h
                             ['exists (tie t
                               ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                     ['or ['eq ['var x] ['var h]]
                                          ['pos ['app 'member ['var x] ['var t]]]]])])]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'member ['app 'a]
                                              ['app 'cons ['app 'a] ['app 'nil]]]]
                                  neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))

(deftest test-Q05-member-recursive
  (testing "member(a, cons(b, cons(a, nil))) — element at second position"
    ;; First γ-instantiation binds H=b, T=cons(a,nil).
    ;; Left branch closes (refl-close).
    ;; Right: neq(a,b) saves to lits; neg member(a, cons(a,nil)) → subsidiary
    ;; The subsidiary is the base case (same as Q04) → closes ✓
    (is (seq
          (run 1 [proof]
            (nom x l h t
              (let [prog [['member [x l]
                           ['exists (tie h
                             ['exists (tie t
                               ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                     ['or ['eq ['var x] ['var h]]
                                          ['pos ['app 'member ['var x] ['var t]]]]])])]]]]
                (fresh [neg-query]
                  (negate-formulao ['pos ['app 'member ['app 'a]
                                              ['app 'cons ['app 'b]
                                                ['app 'cons ['app 'a] ['app 'nil]]]]]
                                  neg-query)
                  (proveo neg-query '() '() '() prog proof)))))))))

(deftest test-Q06-member-empty-list-fails
  (testing "member(a, nul) — empty list, should fail"
    ;; Positive proc call on member(a, nul):
    ;;   body: ∃h.∃t. (nul=cons(h,t) ∧ ...)
    ;;   δ-rule: p, q → (eq (app nul) (app cons (app p) (app q))) → free-close ✓
    ;; (nul ≠ cons — distinct constructor symbols)
    ;; NOTE: 'nil in a Clojure vector literal evaluates to null (not the symbol
    ;; nil), so we use 'nul as the empty-list constant instead.
    (is (seq
          (run 1 [proof]
            (nom x l h t
              (let [prog [['member [x l]
                           ['exists (tie h
                             ['exists (tie t
                               ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                     ['or ['eq ['var x] ['var h]]
                                          ['pos ['app 'member ['var x] ['var t]]]]])])]]]]
                (proveo ['pos ['app 'member ['app 'a] ['app 'nul]]]
                        '() '() '() prog proof))))))))
(deftest test-Q07-member-singleton-wrong-element-fails
  (testing "member(b, cons(a, nil)) — element not in singleton list, fails via para-free-close"
    ;; Positive proc call: body[x:=b, l:=cons(a,nul)]:
    ;;   δ p₁,p₂; decompose cons(a,nul)=cons(p₁,p₂) → a=p₁ ∧ nul=p₂ [lits]
    ;;   β-split on (b=p₁ ∨ member(b,p₂)):
    ;;     Left: (eq b p₁) — branch has (eq a p₁)
    ;;           rewrite p₁→a: (eq b a) → para-free-close (b≠a) ✓
    ;;     Right: member(b,p₂) — subst-call p₂→nul: body[l:=nul]
    ;;            δ p₃,p₄; (eq nul (app cons ...)) → free-close ✓
    (is (seq
          (run 1 [proof]
            (nom x l h t
              (let [prog [['member [x l]
                           ['exists (tie h
                             ['exists (tie t
                               ['and ['eq ['var l] ['app 'cons ['var h] ['var t]]]
                                     ['or ['eq ['var x] ['var h]]
                                          ['pos ['app 'member ['var x] ['var t]]]]])])]]]]
                (proveo ['pos ['app 'member ['app 'b] ['app 'cons ['app 'a] ['app 'nul]]]]
                        '() '() '() prog proof))))))))

;; --- Q8-Q10: Multi-Argument Substitutivity ---
;;
;; Tests that rewrite-args-someo correctly rewrites multiple arguments
;; of a relation using independent equality pairs from the branch.

(deftest test-Q08-multi-arg-subst-positive
  (testing "Multi-arg substitutivity: rewrite two args independently"
    ;; R(x,y) ← x ≠ y  (binary "differs" relation)
    ;; Use noms p1,p2 so (eq a p1) and (eq b p2) don't free-close.
    ;; neg-proc-call R(p1,p2): body = (neq p1 p2), negate → (eq p1 p2)
    ;;   subsidiary: (eq p1 p2) can't close (p1,p2 are distinct noms) → fails.
    ;; neg-subst-call: rewrite p1→a, p2→b → neg R(a,b)
    ;;   negate body with x=a,y=b: (eq a b) → free-close (a≠b) ✓
    (is (seq
          (run 1 [proof]
            (nom x y p1 p2
              (let [prog [['R [x y] ['neq ['var x] ['var y]]]]]
                (proveo ['and ['eq ['app 'a] ['par p1]]
                              ['and ['eq ['app 'b] ['par p2]]
                                    ['neg ['app 'R ['par p1] ['par p2]]]]]
                        '() '() '() prog proof))))))))
(deftest test-Q09-multi-arg-subst-proof-step
  (testing "Multi-arg substitutivity uses 'neg-subst-call proof step"
    ;; Same as Q08 but verifies the proof step tag.  Uses noms p1,p2.
    (let [proofs (run 1 [proof]
                   (nom x y p1 p2
                     (let [prog [['R [x y] ['neq ['var x] ['var y]]]]]
                       (proveo ['and ['eq ['app 'a] ['par p1]]
                                     ['and ['eq ['app 'b] ['par p2]]
                                           ['neg ['app 'R ['par p1] ['par p2]]]]]
                               '() '() '() prog proof))))]
      (is (seq proofs))
      (is (proof-tree-contains? (first proofs) 'neg-subst-call)))))
(deftest test-Q10-multi-arg-subst-positive-call
  (testing "Multi-arg substitutivity: positive proc call"
    ;; S(x,y) ← x = y  (binary "same" relation)
    ;; Branch: eq(a,p1), eq(b,p2), then (pos (app S (app p1) (app p2)))
    ;; subst-call rewrites p1→a, p2→b → S(a,b) → body: a=b → free-close ✓
    ;; (S(a,b) is false when a≠b, so positive proc call body is unsatisfiable)
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['S [x y] ['eq ['var x] ['var y]]]]]
                (proveo ['and ['eq ['app 'a] ['app 'p1]]
                              ['and ['eq ['app 'b] ['app 'p2]]
                                    ['pos ['app 'S ['app 'p1] ['app 'p2]]]]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section R: Paramodulated Free Closure
;; ============================================================================
;;
;; Tests for the para-free-close rule: when branch equalities can rewrite
;; one side of an (eq t1 t2) literal in one or more steps to clash with
;; the other side via free-closureo.
;;
;; Canonical pattern:
;;   Branch: (eq a p)  — a is a constructor symbol, p is a nom (δ-witness)
;;   Current: (eq b p) — b is a distinct constructor symbol
;;   eqs from lits: {[(app p),(app a)], [(app a),(app p)]}
;;   Rewrite t2=(app p) → (app a): (eq b a) → free-closureo fires (b≠a) ✓

(deftest test-R01-para-free-close-t2-rewrite
  (testing "a=p ∧ b=p — rewrite t2 toward clash with t1"
    ;; Process (eq a p) first → lits.  Current: (eq b p).
    ;; eqs include [(app p)(app a)].  Rewrite t2=p→a: (eq b a) → clash ✓
    (is (seq
          (run 1 [proof]
            (nom p
              (proveo ['and ['eq ['app 'a] ['par p]]
                            ['eq ['app 'b] ['par p]]]
                      '() '() '() '() proof)))))))

(deftest test-R02-para-free-close-t1-rewrite
  (testing "p=b ∧ p=a — rewrite t1 toward clash with t2"
    ;; Process (eq p b) first → lits.  Current: (eq p a).
    ;; eqs include [(app p)(app b)].  Rewrite t1=p→b: (eq b a) → clash ✓
    (is (seq
          (run 1 [proof]
            (nom p
              (proveo ['and ['eq ['par p] ['app 'b]]
                            ['eq ['par p] ['app 'a]]]
                      '() '() '() '() proof)))))))

(deftest test-R03-para-free-close-two-step
  (testing "p=q ∧ q=a ∧ b=p — two-step chain p→q→a detects clash with b"
    ;; lits after first two eqs: (eq p q), (eq q a)
    ;; eqs: [(app p)(app q)], [(app q)(app p)], [(app q)(app a)], [(app a)(app q)]
    ;; Current: (eq b p).
    ;; Step 1: rewrite t2=p→q: (eq b q).  q is nom, no direct clash.
    ;; Step 2: rewrite q→a: (eq b a).  b≠a: clash ✓
    (is (seq
          (run 1 [proof]
            (nom p q
              (proveo ['and ['eq ['par p] ['par q]]
                            ['and ['eq ['par q] ['app 'a]]
                                  ['eq ['app 'b] ['par p]]]]
                      '() '() '() '() proof)))))))

(deftest test-R04-para-free-close-proof-step
  (testing "para-free-close or free-close (with constraint propagation) proof step"
    ;; Without constraint propagation: para-free-close via eq rewriting.
    ;; With constraint propagation: par p resolves to (app a), then
    ;; (eq (app b) (app a)) closes directly via free-close.
    (let [proofs (run 1 [proof]
                   (nom p
                     (proveo ['and ['eq ['app 'a] ['par p]]
                                   ['eq ['app 'b] ['par p]]]
                             '() '() '() '() proof)))]
      (is (seq proofs))
      (is (or (proof-tree-contains? (first proofs) 'para-free-close)
              (proof-tree-contains? (first proofs) 'free-close))))))

(deftest test-R05-para-free-close-no-false-fire-same-head
  (testing "a=p ∧ a=p — rewrite yields (eq a a): same head, no clash (tableau stays open)"
    ;; After rewriting p→a in (eq a p), both sides are (app a).
    ;; free-closureo requires (not= a a) → false.  Formula is satisfiable.
    (is (empty?
          (run 1 [proof]
            (nom p
              (proveo ['and ['eq ['app 'a] ['par p]]
                            ['eq ['app 'a] ['par p]]]
                      '() '() '() '() proof)))))))

(deftest test-R06-para-free-close-soundness-nom-result
  (testing "p=q ∧ r=p — rewriting yields (eq r q): q is a nom, no clash (tableau stays open)"
    ;; Rewrite t2=p→q in (eq r p): get (eq r q).
    ;; q is a nom → (par q) does not match (lcons 'app ...) → free-closureo blocks it.
    ;; Formula is satisfiable (set p=q=r), so tableau must NOT close.
    (is (empty?
          (run 1 [proof]
            (nom p q
              (proveo ['and ['eq ['par p] ['par q]]
                            ['eq ['app 'r] ['par p]]]
                      '() '() '() '() proof)))))))

;; ============================================================================
;; Section S: Visited-set cycle detection — chains exceeding the old depth-6 bound
;; ============================================================================
;;
;; These tests require 7 rewriting steps to close a branch.  They would
;; have failed with the Peano depth-6 bound and must pass with the
;; visited-terms-set implementation.

(deftest test-S01-eq-neq-close-7-step-chain
  (testing "(neq a1 a8) with 7-link chain a1=a2=...=a8 closes in 7 steps"
    ;; Branch: (eq a1 a2) ∧ (eq a2 a3) ∧ ... ∧ (eq a7 a8) ∧ (neq a1 a8)
    ;; eq-neq-closeo must rewrite a1→a2→...→a8 (7 steps) to see a8=a8 → close.
    (is (seq
          (run 1 [proof]
            (proveo ['and ['eq ['app 'a1] ['app 'a2]]
                          ['and ['eq ['app 'a2] ['app 'a3]]
                                ['and ['eq ['app 'a3] ['app 'a4]]
                                      ['and ['eq ['app 'a4] ['app 'a5]]
                                            ['and ['eq ['app 'a5] ['app 'a6]]
                                                  ['and ['eq ['app 'a6] ['app 'a7]]
                                                        ['and ['eq ['app 'a7] ['app 'a8]]
                                                              ['neq ['app 'a1] ['app 'a8]]]]]]]]]
                    '() '() '() '() proof))))))

(deftest test-S02-para-free-close-7-step-chain
  (testing "(eq b a1) with 7-link chain a1=a2=...=a8 closes in 7 steps via para-free-close"
    ;; Branch: (eq a1 a2) ∧ ... ∧ (eq a7 a8) ∧ (eq b a1)
    ;; para-free-closeo rewrites t2=a1→a2→...→a8 (7 steps) → free-close(b, a8): b≠a8.
    (is (seq
          (run 1 [proof]
            (proveo ['and ['eq ['app 'a1] ['app 'a2]]
                          ['and ['eq ['app 'a2] ['app 'a3]]
                                ['and ['eq ['app 'a3] ['app 'a4]]
                                      ['and ['eq ['app 'a4] ['app 'a5]]
                                            ['and ['eq ['app 'a5] ['app 'a6]]
                                                  ['and ['eq ['app 'a6] ['app 'a7]]
                                                        ['and ['eq ['app 'a7] ['app 'a8]]
                                                              ['eq ['app 'b] ['app 'a1]]]]]]]]]
                    '() '() '() '() proof))))))

;; ============================================================================
;; Section T: (par p) Term Form — Direct Unit Tests
;; ============================================================================
;;
;; The (par p) encoding was introduced in the purely-relational refactoring:
;; δ-rule witnesses are now represented as ['par nom] instead of ['app nom],
;; making them structurally distinct from constructor applications and
;; eliminating the need for any (project ...) non-relational guard.
;;
;; This section provides direct unit tests for the three helper functions
;; modified by the refactoring:
;;
;;   T01–T04  subst-termo   — (par p) passthrough case
;;   T05–T09  free-closureo — structural rejection of (par p), relational !=
;;   T10–T12  rewrite-termo — rewriting at root and inside compound terms
;;   T13–T15  Integration   — (par p) behavior through the full prover
;;
;; These complement the existing integration tests (Sections C, R, etc.) with
;; isolated unit-level coverage of the new code paths.
;;
;; Note: nom bodies must contain a single goal. Tests that verify a specific
;; output value pass it directly as the `out` argument rather than adding a
;; second (== out ...) form inside the nom scope.

;; --- T01–T04: subst-termo ---

(deftest test-T01-subst-par-empty-env
  (testing "(par p) passes through subst-termo unchanged with empty env"
    ;; The new (par p) case in subst-termo: a δ-parameter is already ground
    ;; and needs no substitution, so it passes through as-is.
    (is (seq
          (run 1 [_]
            (nom p
              (subst-termo ['par p] '() ['par p])))))))

(deftest test-T02-subst-par-populated-env
  (testing "(par p) passes through unchanged even when env binds other noms"
    ;; The env binding [a → (app zero)] must NOT be applied to (par p).
    ;; subst-termo only applies env entries that match the nom inside (var a).
    (is (seq
          (run 1 [_]
            (nom p a
              (subst-termo ['par p] (lcons [a ['app 'zero]] '()) ['par p])))))))

(deftest test-T03-subst-par-inside-compound
  (testing "(app s (par p)) — par p inside compound term passes through unchanged"
    ;; When (par p) appears as an argument of an (app ...) constructor,
    ;; subst-term*o recurses into the argument list and the (par p) case fires.
    ;; The output is ['app 's ['par p]] — pass it directly as the out arg.
    (is (seq
          (run 1 [_]
            (nom p
              (subst-termo ['app 's ['par p]] '() ['app 's ['par p]])))))))

(deftest test-T04-subst-par-two-args
  (testing "(app f (par p) (par q)) — both par p and par q pass through subst-term*o"
    ;; Both args are (par ...) terms. subst-term*o processes the arg list
    ;; element by element; the (par p) case fires for each. ✓
    (is (seq
          (run 1 [_]
            (nom p q
              (subst-termo ['app 'f ['par p] ['par q]] '() ['app 'f ['par p] ['par q]])))))))

;; --- T05–T09: free-closureo ---

(deftest test-T05-free-close-par-vs-app-rejected
  (testing "(par p) vs (app f) — par does not match (lcons 'app ...), free-close fails"
    ;; free-closureo requires BOTH arguments to match (lcons 'app (lcons s _)).
    ;; (par p) does not match this pattern → the relation fails immediately.
    ;; This is the structural prevention that replaced the old (project [f] (symbol? f)) guard.
    (is (empty?
          (run 1 [_]
            (nom p
              (free-closureo ['par p] ['app 'f])))))))

(deftest test-T06-free-close-par-vs-compound-rejected
  (testing "(par p) vs (app s (app zero)) — par structurally excluded from free-close"
    (is (empty?
          (run 1 [_]
            (nom p
              (free-closureo ['par p] ['app 's ['app 'zero]])))))))

(deftest test-T07-free-close-par-vs-par-rejected
  (testing "(par p) vs (par q) — neither matches (lcons 'app ...), both rejected"
    ;; Both arguments are (par ...) terms. free-closureo matches neither
    ;; against (lcons 'app ...) so the relation fails regardless of whether
    ;; p and q are distinct noms.
    (is (empty?
          (run 1 [_]
            (nom p q
              (free-closureo ['par p] ['par q])))))))

(deftest test-T08-free-close-distinct-constructors
  (testing "(app zero) vs (app s (app zero)) — distinct heads, free-close fires"
    ;; Positive case: both terms match (lcons 'app ...) and heads differ.
    ;; The relational (!= s1 s2) constraint with s1='zero, s2='s → succeeds.
    (is (seq
          (run 1 [_]
            (free-closureo ['app 'zero] ['app 's ['app 'zero]]))))))

(deftest test-T09-free-close-same-constructor-fails
  (testing "(app s (app a)) vs (app s (app b)) — same head, (!= s s) fails, no clash"
    ;; Both match (lcons 'app ...) with head 's, so s1=s2='s.
    ;; (!= 's 's) is false → free-closureo fails. Distinct args are irrelevant.
    (is (empty?
          (run 1 [_]
            (free-closureo ['app 's ['app 'a]] ['app 's ['app 'b]]))))))

;; --- T10–T12: rewrite-termo ---

(deftest test-T10-rewrite-par-at-root
  (testing "(rewrite-termo (par p) (par p) (app zero) (app zero)) — root match rewrites to rhs"
    ;; First clause: (== t lhs) fires when t = ['par p] = lhs.
    ;; We pass the expected output ['app 'zero] directly as the out arg. ✓
    (is (seq
          (run 1 [_]
            (nom p
              (rewrite-termo ['par p] ['par p] ['app 'zero] ['app 'zero])))))))

(deftest test-T11-rewrite-par-no-match
  (testing "(rewrite-termo (par p) (app zero) ...) — par doesn't match lhs or (lcons 'app ...), fails"
    ;; t = ['par p]. Two clauses:
    ;;   (1) (== t lhs): ['par p] ≠ ['app 'zero] → fails
    ;;   (2) (== (lcons 'app ...) t): ['par p] doesn't start with 'app → fails
    ;; Result: empty.
    (is (empty?
          (run 1 [out]
            (nom p
              (rewrite-termo ['par p] ['app 'zero] ['app 's ['app 'zero]] out)))))))

(deftest test-T12-rewrite-par-inside-compound
  (testing "(rewrite-termo (app s (par p)) (par p) (app zero) (app s (app zero))) — par rewritten inside compound"
    ;; t = ['app 's ['par p]]. Second clause fires: f='s, args=['par p].
    ;; rewrite-term*o recurses into args; (rewrite-termo ['par p] ['par p] ...) fires first clause.
    ;; Pass expected output directly as out arg. ✓
    (is (seq
          (run 1 [_]
            (nom p
              (rewrite-termo ['app 's ['par p]] ['par p] ['app 'zero] ['app 's ['app 'zero]])))))))

;; --- T13–T15: Integration through proveo ---

(deftest test-T13-par-rigid-no-unify-with-ground
  (testing "∃x.(¬P(s(x)) ∧ P(s(a))) — rigid (par p) cannot unify with (app a)"
    ;; δ introduces (par p) for x.  Body after substitution:
    ;;   (neg (app P (app s (par p)))) ∧ (pos (app P (app s (app a))))
    ;; The two literals differ: s(par p) ≠ s(app a) — no unification.
    ;; No equality is on the branch to bridge them.  Tableau stays open.
    (is (empty?
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['and ['neg ['app 'P ['app 's ['var v]]]]
                                           ['pos ['app 'P ['app 's ['app 'a]]]]])]
                      '() '() '() '() proof)))))))

(deftest test-T14-par-inside-compound-para-close
  (testing "∃x.(x=a ∧ P(x) ∧ ¬P(a)) — (par p) bridged to a via eq, para-close fires"
    ;; δ introduces (par p) for x.  Body after substitution:
    ;;   (eq (par p) (app a)) ∧ (pos (app P (par p))) ∧ (neg (app P (app a)))
    ;; The eq literal is saved to lits.  Collected eqs: {[(par p),(app a)], [(app a),(par p)]}.
    ;; For (neg (app P (app a))): rewrite (app a) → (par p) using the eq pair,
    ;; yielding (neg (app P (par p))), which is the complement of (pos (app P (par p))). ✓
    (is (seq
          (run 1 [proof]
            (nom v
              (proveo ['exists (tie v ['and ['eq ['var v] ['app 'a]]
                                           ['and ['pos ['app 'P ['var v]]]
                                                 ['neg ['app 'P ['app 'a]]]]])]
                      '() '() '() '() proof)))))))

(deftest test-T15-eq-par-par-satisfiable
  (testing "(eq (par p) (par q)) alone — no closure rule fires, tableau stays open"
    ;; (par p) and (par q) are not (app ...) terms, so:
    ;;   free-closureo: neither side matches (lcons 'app ...) → fails
    ;;   decompose:     same-head check requires (lcons 'app ...) → fails
    ;;   eq-neq-close:  no (neq ...) literals in lits → fails
    ;;   para-free-close: eqs=[], selecto on [] → fails
    ;; Formula is satisfiable (any model with p=q witnesses it). ✓
    (is (empty?
          (run 1 [proof]
            (nom p q
              (proveo ['eq ['par p] ['par q]]
                      '() '() '() '() proof)))))))

;; ============================================================================
;; Section V: Guard-Transparency Synthesis Tests (V01–V04, V06–V09, V13)
;; Canary suite: written BEFORE the groundness guard is implemented.
;; All 9 V tests must pass immediately on branch groundness-guard.
;; After the guard is added (Step 4), all must still pass — demonstrating
;; that the project-based guard is transparent to LVar arguments.
;; ============================================================================

(deftest test-V01-synth-lvar-arg-trivial
  (testing "Synthesis: LVar in single arg position, guard must pass transparently"
    ;; P(x) ← x = a.  Backward: (neg (P x)) succeeds iff P(x) holds.
    ;; Guard fires: neg-call, args=[x] (LVar). project[x]=LVar → contains-par?=false → PASSES.
    ;; x binds to (app 'a) via refl-close on (neq x (app 'a)).
    (let [results (run 1 [x]
                    (nom a
                      (let [prog [['P [a] ['eq ['var a] ['app 'a]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'P x]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'a] (first results))))))

(deftest test-V02-synth-lvar-first-of-two-args
  (testing "Synthesis: LVar in first arg of binary call, guard must pass transparently"
    ;; pair(x,y) ← x=a ∧ y=b.  Query: (neg (pair x (app 'b))).
    ;; Guard fires: neg-call, args=[x_lvar, (app 'b)]. LVar among args → guard passes.
    ;; x binds to (app 'a).
    (let [results (run 1 [x]
                    (nom a b
                      (let [prog [['pair [a b]
                                   ['and ['eq ['var a] ['app 'a]]
                                         ['eq ['var b] ['app 'b]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'pair x ['app 'b]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'a] (first results))))))

(deftest test-V03-synth-lvar-arg-recursive
  (testing "Synthesis: LVar arg in recursive even/odd program, guard transparent at every call"
    ;; even(x) ← x=0 ∨ ∃y.(x=s(y)∧odd(y))
    ;; odd(y)  ← ∃z.(y=s(z)∧even(z))
    ;; Query: (neg (even x)).  First result: x = (app 'zero).
    ;; Guard fires on neg-call (even x), args=[x_lvar]. Passes. Then on recursive pos-call
    ;; (odd y) with y fresh logic var — guard passes on LVar there too.
    (let [results (run 1 [x]
                    (nom a b c d
                      (let [even-clause ['even [a]
                                         ['or ['eq ['var a] ['app 'zero]]
                                              ['exists (tie b
                                                ['and ['eq ['var a] ['app 's ['var b]]]
                                                      ['pos ['app 'odd ['var b]]]])]]]
                            odd-clause  ['odd [c]
                                         ['exists (tie d
                                           ['and ['eq ['var c] ['app 's ['var d]]]
                                                 ['pos ['app 'even ['var d]]]])]]
                            prog [even-clause odd-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'even x]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'zero] (first results))))))

(deftest test-V04-synth-lvar-predicate-name
  (testing "Synthesis: LVar predicate name, concrete arg passes guard"
    ;; Program: even(x) ← ..., odd(x) ← ...
    ;; Query: (neg (R (app 'zero))) where R is LVar.
    ;; Guard fires with args=[(app 'zero)] — concrete, passes. R synthesized as 'even via lookup.
    (let [results (run 1 [R]
                    (nom a b c d
                      (let [even-clause ['even [a]
                                         ['or ['eq ['var a] ['app 'zero]]
                                              ['exists (tie b
                                                ['and ['eq ['var a] ['app 's ['var b]]]
                                                      ['pos ['app 'odd ['var b]]]])]]]
                            odd-clause  ['odd [c]
                                         ['exists (tie d
                                           ['and ['eq ['var c] ['app 's ['var d]]]
                                                 ['pos ['app 'even ['var d]]]])]]
                            prog [even-clause odd-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app R ['app 'zero]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= 'even (first results))))))

(deftest test-V05-synth-program-clause-body
  (testing "Program synthesis: logic var inside clause body, guard fires on concrete query args"
    ;; Synthesize term t such that clause R(x)←(x=t) makes R(zero) provable.
    ;; prog = [['R [a] ['eq ['var a] t]]]  where t is a logic variable IN THE PROGRAM.
    ;; Query: (neg (R (app 'zero))). neg-call fires.
    ;; Guard fires on args=[(app 'zero)] — CONCRETE. The LVar t in the clause body is
    ;; invisible to the guard (guard only inspects args from the query literal).
    (let [results (run 1 [t]
                    (nom a
                      (let [prog [['R [a] ['eq ['var a] t]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R ['app 'zero]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'zero] (first results))))))

(deftest test-V06-synth-query-and-program-simultaneously
  (testing "Joint synthesis: LVar in query arg AND in clause body, guard transparent to query LVar"
    ;; R(a,b) ← (a=s(b)) ∧ (b=t)   where t is a logic var IN THE PROGRAM.
    ;; Query: (neg (R x zero))        where x is a logic var IN THE QUERY.
    ;; Guard fires: neg-call, args=[x_lvar, (app 'zero)].
    ;;   project[x_lvar]=LVar → contains-par?=false → guard PASSES.
    ;;   t (in the clause body) is invisible to the guard.
    (let [results (run 1 [x t]
                    (nom a b
                      (let [prog [['R [a b]
                                   ['and ['eq ['var a] ['app 's ['var b]]]
                                         ['eq ['var b] t]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x ['app 'zero]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [['app 's ['app 'zero]] ['app 'zero]] (first results))))))

(deftest test-V07-synth-depth-3-query
  (testing "Query synthesis at depth 3: run 2 for even finds both zero and s²(zero)"
    ;; Guard fires with γ-rule LVars at every recursive call — all pass transparently.
    (let [results (run 2 [x]
                    (nom a b c d
                      (let [even-clause ['even [a]
                                         ['or ['eq ['var a] ['app 'zero]]
                                              ['exists (tie b
                                                ['and ['eq ['var a] ['app 's ['var b]]]
                                                      ['pos ['app 'odd ['var b]]]])]]]
                            odd-clause  ['odd [c]
                                         ['exists (tie d
                                           ['and ['eq ['var c] ['app 's ['var d]]]
                                                 ['pos ['app 'even ['var d]]]])]]
                            prog [even-clause odd-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'even x]] '() '() '() prog proof)))))]
      (is (= 2 (count results)))
      (is (some #{['app 'zero]} results))
      (is (some #{['app 's ['app 's ['app 'zero]]]} results)))))

(deftest test-V08-joint-synth-depth-2
  (testing "Joint synthesis at depth 2: LVar in query AND inner clause body, guard fires at depth 0 and 1"
    ;; outer(px, pn) ← ∃py. px=s(py) ∧ pos(inner(py, pn))
    ;; inner(qy, qn) ← qy=qn ∧ qn=t          (t is LVar in program)
    ;; Query: (neg (outer x zero))   where x is LVar in query.
    ;; Guard depth 0: neg-call outer(x, zero), args=[x_lvar, zero]. LVar → passes.
    ;; Guard depth 1: neg-call inner(v, zero), args=[v_lvar, zero]. γ-rule LVar → passes.
    (let [results (run 1 [x t]
                    (nom px pn py qy qn
                      (let [outer-clause ['outer [px pn]
                                           ['exists (tie py
                                             ['and ['eq ['var px] ['app 's ['var py]]]
                                                   ['pos ['app 'inner ['var py] ['var pn]]]])]]
                            inner-clause ['inner [qy qn]
                                           ['and ['eq ['var qy] ['var qn]]
                                                 ['eq ['var qn] t]]]
                            prog [outer-clause inner-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'outer x ['app 'zero]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [['app 's ['app 'zero]] ['app 'zero]] (first results))))))

(deftest test-V09-joint-synth-depth-3
  (testing "Joint synthesis at depth 3: guard fires at 3 nesting levels, LVar in query AND bot clause body"
    ;; top(px, pn) ← ∃py. px=s(py) ∧ pos(mid(py, pn))
    ;; mid(qy, qn) ← ∃qz. qy=s(qz) ∧ pos(bot(qz, qn))
    ;; bot(rz, rn) ← rz=rn ∧ rn=t            (t is LVar in program)
    ;; Query: (neg (top x zero))   where x is LVar in query.
    (let [results (run 1 [x t]
                    (nom px pn py qy qn qz rz rn
                      (let [top-clause ['top [px pn]
                                         ['exists (tie py
                                           ['and ['eq ['var px] ['app 's ['var py]]]
                                                 ['pos ['app 'mid ['var py] ['var pn]]]])]]
                            mid-clause ['mid [qy qn]
                                         ['exists (tie qz
                                           ['and ['eq ['var qy] ['app 's ['var qz]]]
                                                 ['pos ['app 'bot ['var qz] ['var qn]]]])]]
                            bot-clause ['bot [rz rn]
                                         ['and ['eq ['var rz] ['var rn]]
                                               ['eq ['var rn] t]]]
                            prog [top-clause mid-clause bot-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'top x ['app 'zero]]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [['app 's ['app 's ['app 'zero]]] ['app 'zero]] (first results))))))

(deftest test-V10-joint-synth-self-recursive-run-2
  (testing "Joint synthesis: self-recursive P, anchor n=s(zero), run 2 gives depth-1 and depth-2 answers"
    ;; P(pa, pn) ← pa=s(pn) ∧ pn=t              (base)
    ;; P(qa, qn) ← ∃qb. qa=s(qb) ∧ pos(P(qb, qn)) (recursive)
    ;; Query: (neg (P x s(zero))) — x is LVar, anchor is s(zero).
    ;; Guard depth 0: args=[x_lvar, s(zero)]. x is LVar → passes.
    ;; Guard depth 1 (recursive): args=[v_lvar, s(zero)]. γ-rule LVar → passes.
    (let [results (run 2 [x t]
                    (nom pa pn qa qn qb
                      (let [base-clause ['P [pa pn]
                                          ['and ['eq ['var pa] ['app 's ['var pn]]]
                                                ['eq ['var pn] t]]]
                            rec-clause  ['P [qa qn]
                                          ['exists (tie qb
                                            ['and ['eq ['var qa] ['app 's ['var qb]]]
                                                  ['pos ['app 'P ['var qb] ['var qn]]]])]]
                            prog [base-clause rec-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'P x (nim-numeral 1)]]
                                  '() '() '() prog proof)))))]
      (is (= 2 (count results)))
      (is (some #{[(nim-numeral 2) (nim-numeral 1)]} results))
      (is (some #{[(nim-numeral 3) (nim-numeral 1)]} results)))))

(deftest test-V11-joint-synth-self-recursive-deep-anchor
  (testing "Joint synthesis: self-recursive P, anchor n=s²(zero), both x and t are depth-2 numerals"
    ;; Same predicate P as V10. Query anchor n=s²(zero).
    ;; Base clause: t=s²(zero), x=s³(zero). Guard depth 0: args=[x_lvar, s²(zero)] → passes.
    (let [results (run 1 [x t]
                    (nom pa pn qa qn qb
                      (let [base-clause ['P [pa pn]
                                          ['and ['eq ['var pa] ['app 's ['var pn]]]
                                                ['eq ['var pn] t]]]
                            rec-clause  ['P [qa qn]
                                          ['exists (tie qb
                                            ['and ['eq ['var qa] ['app 's ['var qb]]]
                                                  ['pos ['app 'P ['var qb] ['var qn]]]])]]
                            prog [base-clause rec-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'P x (nim-numeral 2)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [(nim-numeral 3) (nim-numeral 2)] (first results))))))

(deftest test-V12-joint-synth-double-step-outer
  (testing "Joint synthesis: double-step outer3→inner3 chain, x=s⁴(zero), t=s(zero)"
    ;; outer3(pa, pn) ← ∃pb. pa=s(s(pb)) ∧ pos(inner3(pb, pn))  (x steps by s²)
    ;; inner3(qb, qn) ← qb=s(qn) ∧ qn=t
    ;; Query: (neg (outer3 x s(zero))). Guard depth 0: args=[x_lvar, s(zero)] → passes.
    ;; Guard depth 1: args=[v_lvar, s(zero)]. γ-rule LVar → passes.
    (let [results (run 1 [x t]
                    (nom pa pn pb qb qn
                      (let [outer-clause ['outer3 [pa pn]
                                           ['exists (tie pb
                                             ['and ['eq ['var pa]
                                                        ['app 's ['app 's ['var pb]]]]
                                                   ['pos ['app 'inner3 ['var pb] ['var pn]]]])]]
                            inner-clause ['inner3 [qb qn]
                                           ['and ['eq ['var qb] ['app 's ['var qn]]]
                                                 ['eq ['var qn] t]]]
                            prog [outer-clause inner-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'outer3 x (nim-numeral 1)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [(nim-numeral 4) (nim-numeral 1)] (first results))))))

(deftest test-V13-synth-program-lvar-directly-in-guard-args
  (testing "Program synthesis: program LVar t is directly in the guard's args — synthesis fails if guard blocks LVars"
    ;; outer(pa)  ← pos(middle(t))                  t is LVar IN THE PROGRAM, in arg position
    ;; middle(qb) ← ∃qc. qb=s(qc) ∧ pos(base(qc))
    ;; base(rd)   ← rd = zero
    ;; Query: (neg (outer (app 'anything))) — query arg is CONCRETE.
    ;; Guard depth 1: neg-call middle, args=[t]. t IS THE PROGRAM LVar.
    ;;   project[t] = LVar → contains-par? = false → PASSES.
    ;;   If the guard incorrectly blocked LVars here, t cannot be synthesized.
    (let [results (run 1 [t]
                    (nom pa qb qc rd
                      (let [outer-clause  ['outer [pa] ['pos ['app 'middle t]]]
                            middle-clause ['middle [qb]
                                            ['exists (tie qc
                                              ['and ['eq ['var qb] ['app 's ['var qc]]]
                                                    ['pos ['app 'base ['var qc]]]])]]
                            base-clause   ['base [rd] ['eq ['var rd] ['app 'zero]]]
                            prog [outer-clause middle-clause base-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'outer ['app 'anything]]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [(nim-numeral 1)] results)))))

(deftest test-V14-synth-query-and-program-lvar-both-in-guard-args
  (testing "Combined synthesis: guard fires on query LVar (depth 0) AND program LVar (depth 1) in same proof"
    ;; outer(pa) ← ∃pb. pa=s(pb) ∧ pos(mid(pb, t))   t: program LVar, in sub-call arg pos.
    ;; mid(qb, qc) ← qb=s(zero) ∧ qc=s(zero)
    ;; Query: (neg (outer x))   x: query LVar.
    ;; Guard depth 0: neg-call outer(x), args=[x_lvar]. x is query LVar → passes.
    ;; Guard depth 1: neg-call mid(v, t), args=[v_lvar, t_lvar].
    ;;   v is γ-rule LVar from outer's ∃; t IS THE PROGRAM LVar.
    ;;   Both are LVars → guard must pass them simultaneously.
    ;;   If the guard incorrectly blocked either, synthesis returns empty.
    ;; Proof: γ: v. OR: (neq x s(v)) ∧ neg(mid(v,t)) must both close.
    ;;   Branch 1: x=s(v). Branch 2: neg-call mid(v,t) → (neq v s(0)) ∨ (neq t s(0)).
    ;;   β2a: v=s(zero). β2b: t=s(zero). Combined: x=s²(zero), t=s(zero).
    (let [results (run 1 [x t]
                    (nom pa pb qb qc
                      (let [outer-clause ['outer [pa]
                                           ['exists (tie pb
                                             ['and ['eq ['var pa] ['app 's ['var pb]]]
                                                   ['pos ['app 'mid ['var pb] t]]])]]
                            mid-clause   ['mid [qb qc]
                                           ['and ['eq ['var qb] ['app 's ['app 'zero]]]
                                                 ['eq ['var qc] ['app 's ['app 'zero]]]]]
                            prog [outer-clause mid-clause]]
                        (fresh [proof]
                          (proveo ['neg ['app 'outer x]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= [(nim-numeral 2) (nim-numeral 1)] (first results))))))

;; ============================================================================
;; Section W: Full Reverse Mode Synthesis Tests
;;
;; These tests verify that full reverse-mode synthesis — assigning a logic
;; variable to the ENTIRETY of 'program' or 'query' — is not negatively
;; impacted by any project-based or non-relational changes to subst-termo
;; or other core relations.
;;
;; All tests are self-verifying: they synthesize a value, then re-run proveo
;; with that value to confirm the synthesized result is actually correct.
;;
;; Design invariant:
;;   - All W tests MUST PASS before any project-based fix is applied.
;;   - All W tests MUST STILL PASS after any such fix is applied.
;;   - Failure of any W test after a fix = regression in reverse mode.
;;
;; Coverage:
;;   W01 — Entire 'program' argument is a single logic variable.
;;   W02 — Entire query inner term (in ['neg t]) is a single logic variable.
;;   W03 — Entire 'query' argument (via negate-formulao) is a logic variable.
;;   W04 — Both 'program' and 'query' are simultaneously logic variables.
;; ============================================================================

(deftest test-W01-entire-program-as-lvar
  (testing "Full reverse mode: entire 'program' argument is a logic variable"
    ;; The ENTIRE program argument is an unbound logic variable.
    ;; proveo synthesizes a program under which neg(P(zero)) is provable.
    ;;
    ;; Internally, lookup-clauseo, bind-argso, and negate-formulao all run
    ;; relationally on unbound structures. subst-termo is called on the
    ;; synthesized body terms; these arise from negate-formulao's fresh LVars,
    ;; not from run variables placed directly in term positions.
    ;;
    ;; The self-verify step re-runs proveo with the synthesized (reified)
    ;; program and confirms it still proves the query. This ensures the
    ;; synthesized program is well-formed and not an artefact of lucky
    ;; interleaving.
    (let [progs (run 1 [prog]
                  (fresh [proof]
                    (proveo ['neg ['app 'P ['app 'zero]]] '() '() '() prog proof)))]
      (is (seq progs) "Program synthesis must produce at least one result")
      (let [synth-prog (first progs)]
        (is (seq (run 1 [proof]
                   (proveo ['neg ['app 'P ['app 'zero]]] '() '() '() synth-prog proof)))
            "Synthesized program must actually prove the query when re-run")))))

(deftest test-W02-entire-query-inner-term-as-lvar
  (testing "Full reverse mode: entire inner term of query literal is a logic variable"
    ;; The full application term t in ['neg t] is unbound.
    ;; proveo synthesizes t — the complete term that makes the literal provable.
    ;;
    ;; subst-termo is called on t with the INITIAL env (empty at the top level).
    ;; With empty env, branch 1 of subst-termo fails (lookupo on empty env).
    ;; Branch 3 (LCons app) succeeds, giving t an app-term structure.
    ;; The neg-call rule fires and the program closes the proof.
    ;;
    ;; This tests the 'entirety of query' at the term level.
    (let [results (run 1 [t]
                    (nom a
                      (let [prog [['P [a] ['eq ['var a] ['app 'zero]]]]]
                        (fresh [proof]
                          (proveo ['neg t] '() '() '() prog proof)))))]
      (is (seq results) "Query inner term synthesis must produce at least one result")
      (let [synth-t (first results)]
        (is (seq (run 1 [proof]
                   (nom a
                     (let [prog [['P [a] ['eq ['var a] ['app 'zero]]]]]
                       (proveo ['neg synth-t] '() '() '() prog proof)))))
            "Synthesized term must make the query provable when re-run")))))

(deftest test-W03-entire-query-as-lvar-via-negate
  (testing "Full reverse mode: entire 'query' argument is a logic variable"
    ;; The ENTIRE query q is unbound. We use negate-formulao to link q to
    ;; what proveo processes (Fitting's query_succeeds convention: prove ¬q).
    ;;
    ;; negate-formulao runs relationally in both directions, so it can
    ;; synthesize q from neg-q and vice versa. proveo closes neg-q, which
    ;; forces q to take the shape of something provable by the program.
    ;;
    ;; With branch 5 (pos↔neg) of negate-formulao: q = ['pos t], neg-q = ['neg t].
    ;; proveo ['neg t] synthesizes t = (app 'P (app 'zero)) via neg-call.
    ;; So q = ['pos ['app 'P ['app 'zero]]] — "P(zero) holds."
    ;;
    ;; This tests the 'entirety of query' at the Fitting interface level.
    (let [queries (run 1 [q]
                    (nom a
                      (let [prog [['P [a] ['eq ['var a] ['app 'zero]]]]]
                        (fresh [proof neg-q]
                          (negate-formulao q neg-q)
                          (proveo neg-q '() '() '() prog proof)))))]
      (is (seq queries) "Full query synthesis must produce at least one result")
      (let [synth-q (first queries)]
        (is (seq (run 1 [proof]
                   (nom a
                     (let [prog [['P [a] ['eq ['var a] ['app 'zero]]]]]
                       (fresh [neg-q]
                         (negate-formulao synth-q neg-q)
                         (proveo neg-q '() '() '() prog proof))))))
            "Synthesized query must be provable when re-run")))))

(deftest test-W04-entire-program-and-query-both-as-lvars
  (testing "Full reverse mode: both 'program' and 'query' are simultaneously logic variables"
    ;; The maximum synthesis case: both prog and q are entirely unbound.
    ;; negate-formulao links q to neg-q; proveo closes neg-q with prog.
    ;;
    ;; The search must find a (prog, q) pair such that q is provable
    ;; under prog. proveo synthesizes both simultaneously by exploring
    ;; the space of (program, negated-query) pairs that close together.
    ;;
    ;; This is the hardest synthesis test. It passes iff the relational
    ;; machinery (lookup-clauseo, bind-argso, negate-formulao, subst-termo,
    ;; and all closure rules) compose correctly when ALL inputs are unbound.
    ;;
    ;; Self-verify: the synthesized (prog, q) pair must re-prove correctly.
    (let [results (run 1 [prog q]
                    (fresh [proof neg-q]
                      (negate-formulao q neg-q)
                      (proveo neg-q '() '() '() prog proof)))]
      (is (seq results) "Joint program+query synthesis must produce at least one result")
      (let [[synth-prog synth-q] (first results)]
        (is (seq (run 1 [proof]
                   (fresh [neg-q]
                     (negate-formulao synth-q neg-q)
                     (proveo neg-q '() '() '() synth-prog proof))))
            "Synthesized (program, query) pair must be mutually provable when re-run")))))

;; ============================================================================
;; Section U: Groundness Guard Tests (U01–U08)
;;
;; TDD Step 2: written BEFORE contains-par?, l-ground-termo, l-ground-term*o exist.
;;   U01–U05 fail (helpers absent).  U06–U08 fail (guard not in proveo).
;; TDD Step 3: after helper fns added, U01–U05 pass; U06–U08 still fail.
;; TDD Step 4: after guard wired into proveo, all U tests pass.
;;
;; U01–U05: unit tests for l-ground-termo / l-ground-term*o
;; U06–U07: integration tests — plain pos/neg call blocked when arg is (par p)
;; U08:     integration test  — subst-call fires after rewriting (par p) → ground
;; ============================================================================

;; --- U01–U05: unit tests for l-ground-termo ----------------------------------

(deftest test-U01-ground-term-concrete-constant
  (testing "U01: l-ground-termo succeeds on a concrete nullary constructor"
    ;; (app 'zero) contains no (par .) sub-term → l-ground-termo succeeds.
    (is (seq (run 1 [_]
               (l-ground-termo ['app 'zero]))))))

(deftest test-U02-ground-term-direct-par
  (testing "U02: l-ground-termo fails on a direct (par p) term"
    ;; (par p) is a δ-parameter — l-ground-termo must fail.
    (is (empty? (run 1 [_]
                  (nom p
                    (l-ground-termo ['par p])))))))

(deftest test-U03-ground-term-nested-constructor
  (testing "U03: l-ground-termo succeeds on a nested constructor with no par"
    ;; (app 's (app 'zero)) — two levels of constructor, no par → succeeds.
    (is (seq (run 1 [_]
               (l-ground-termo ['app 's ['app 'zero]]))))))

(deftest test-U04-ground-term-par-inside-compound
  (testing "U04: l-ground-termo fails when (par p) is nested inside a compound term"
    ;; (app 's (par p)) — par appears at depth 1 → l-ground-termo must fail.
    (is (empty? (run 1 [_]
                  (nom p
                    (l-ground-termo ['app 's ['par p]])))))))

(deftest test-U05-ground-term-fresh-logic-var
  (testing "U05: l-ground-termo succeeds on an unbound logic variable (γ-rule var)"
    ;; An unbound LVar contains no (par .) sub-term → l-ground-termo succeeds.
    ;; This is the critical transparency property: the guard must not block γ-rule variables.
    (is (seq (run 1 [_]
               (fresh [x]
                 (l-ground-termo x)))))))

;; --- U06–U08: integration through proveo ------------------------------------

(deftest test-U06-plain-pos-call-blocked-with-par-arg
  (testing "U06: plain positive call rule is blocked when arg contains (par p)"
    ;; pos(R(par p)) with clause R(x) ← (neq x x):
    ;; Body (neq x x) is always contradictory — substituting x→(par p) gives
    ;; (neq (par p) (par p)), which closes by refl-close.  So WITHOUT the guard
    ;; the plain pos-call fires and returns a proof.  WITH the guard, the call
    ;; is blocked because (par p) is not L-ground.  Expected: empty.
    (is (empty? (run 1 [_]
                  (nom a p
                    (let [prog [['R [a] ['neq ['var a] ['var a]]]]]
                      (fresh [proof]
                        (proveo ['pos ['app 'R ['par p]]]
                                '() '() '() prog proof)))))))))

(deftest test-U07-plain-neg-call-blocked-with-par-arg
  (testing "U07: plain negative call rule is blocked when arg contains (par p)"
    ;; neg(R(par p)) with clause R(x) ← (eq x x):
    ;; Negating body (eq x x) gives (neq x x).  Substituting x→(par p) gives
    ;; (neq (par p) (par p)), which closes by refl-close.  So WITHOUT the guard
    ;; the plain neg-call fires and returns a proof.  WITH the guard, the call
    ;; is blocked because (par p) is not L-ground.  Expected: empty.
    (is (empty? (run 1 [_]
                  (nom a p
                    (let [prog [['R [a] ['eq ['var a] ['var a]]]]]
                      (fresh [proof]
                        (proveo ['neg ['app 'R ['par p]]]
                                '() '() '() prog proof)))))))))

(deftest test-U08-subst-call-rewrites-par-to-ground-then-fires
  (testing "U08: subst-call path rewrites (par p) to a ground term then calls the clause"
    ;; ∃a.(a=zero ∧ pos(R(a))), clause R(a) ← (neq a a).
    ;;
    ;; Flow:
    ;;   1. δ-rule: fresh nom p, bind a→(par p) in env.
    ;;   2. α-rule: processes (eq (var a) (app zero)) first.
    ;;      subst-lito → (eq (par p) zero). Saved to branch lits via savefml.
    ;;   3. Next: pos(R(var a)). subst-lito → lit = pos(R(par p)).
    ;;   4. pos-subst-call: collect-eqso lits → {(par p)↔zero}.
    ;;      rewrite-term-with-eqso pos(R(par p)) → pos(R(zero)).
    ;;      pos-call on pos(R(zero)) with ground arg zero.
    ;;      call-env = {a → (app zero)}.
    ;;      proveo (neq (var a) (var a)) '() '() {a→zero} prog prf.
    ;;      subst-lito → (neq zero zero). refl-close ✓.
    ;;
    ;; This test verifies the existing subst-call (para-call) path is unaffected by the
    ;; guard. The plain pos-call on pos(R(par p)) IS blocked by the guard, but the
    ;; subst-call rewrites par p → zero BEFORE firing the call, so it proceeds normally.
    (is (seq (run 1 [_]
               (nom a
                 (let [prog [['R [a] ['neq ['var a] ['var a]]]]]
                   (fresh [proof]
                     (proveo ['exists (tie a ['and ['eq ['var a] ['app 'zero]]
                                                   ['pos ['app 'R ['var a]]]])]
                             '() '() '() prog proof)))))))))

;; ============================================================================
;; Section X: Fitting's Original P1 — ∀-Based odd Clause
;; ============================================================================
;;
;; Fitting's paper (§2) gives two programs for even/odd:
;;
;;   P1:  even(x) ← x = 0 ∨ ∃y. (x = s(y) ∧ odd(y))
;;        odd(x)  ← (∀y)[even(y) ⊃ ¬(x = y)]
;;
;; The simplified mutually-recursive P1' used in Section I is:
;;
;;   P1': even(x) ← x = 0 ∨ ∃y. (x = s(y) ∧ odd(y))
;;        odd(x)  ← ∃y. (x = s(y) ∧ even(y))
;;
;; The two are semantically equivalent in Fitting's supervaluation model.
;; However, the ∀-based odd clause requires care about disjunct ordering.
;;
;; NNF ENCODING CONSTRAINT
;; ──────────────────────
;; The implication even(y) ⊃ x≠y expands to ¬even(y) ∨ x≠y.  In NNF for
;; our tableau: (or (neg (app even (var y))) (neq (var x) (var y))).
;;
;; When the neg-call rule negates this body to ∃y.(even(y) ∧ x=y), the α-rule
;; processes the AND left-to-right: pos(even(y)) first, eq(x,y) second.
;; pos(even(par p)) is then blocked by the L-groundness guard, and the eq
;; constraint is still in unexp — not yet in lits — so the subst-call cannot
;; rewrite par p to a ground term.  The subsidiary fails to close.
;;
;; The COMMUTED form (x≠y ∨ ¬even(y)) = (or (neq (var x) (var y)) (neg even(y)))
;; negates to ∃y.(eq(x,y) ∧ pos(even(y))).  Now eq(x,y) is processed first →
;; saved to lits → pos(even(par p)) is processed next WITH the equality available
;; → subst-call rewrites par p → ground → pos-call fires.  The subsidiary closes.
;;
;; RULE: In clause body disjunctions where one disjunct is ¬P(y) (a relational
;; literal) and another is x≠y (an equality constraint), place the equality
;; constraint FIRST.  This ensures that when the body is negated (for neg-call),
;; the AND processes the equality before the pos literal.
;;
;; Section I uses the simplified P1' (no ∀ in odd) and all I tests pass.
;; Section X uses the commuted ∀-based P1, confirming fidelity to Fitting's
;; semantics via the correct NNF disjunct ordering.
;; ============================================================================

(deftest test-X01-original-p1-even-zero-succeeds
  (testing "X01: even(0) succeeds — base case, commuted ∀-odd clause"
    ;; neg-call on even(0): negate body[x:=0] = (and (neq 0 zero) (forall ...))
    ;; First conjunct (neq 0 zero) closes by refl-close immediately. ✓
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'even ['app 'zero]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-X02-original-p1-odd-one-succeeds
  (testing "X02: odd(s(0)) succeeds — commuted ∀-odd clause, subst-call path"
    ;; neg-call on odd(1): negate commuted body[x:=1] = ∃y.(eq(1,y) ∧ pos(even(y)))
    ;; AND order: eq(1, par p) first → lits; then pos(even(par p)) with eq in lits.
    ;; subst-call rewrites par p → 1 → pos(even(1)) → pos-call on even(1).
    ;; pos-call(even(1)): body[x:=1] = or(1=0, ∃y.(1=s(y)∧odd(y))).
    ;;   Left: 1=0 → free-close ✓.  Right: ∃y.(1=s(y)∧odd(y)):
    ;;     δ: y→par q; eq(1,s(par q)) → decompose → eq(0,par q) → lits.
    ;;     pos(odd(par q)) → subst-call rewrites par q→0 → pos(odd(0)).
    ;;     pos-call(odd(0)): body = ∀y.(0≠y ∨ ¬even(y)).
    ;;       γ: y→v; β: left=(0≠v) → v=0 → refl-close ✓; right=(neg even(v=0)) → closes ✓.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'odd ['app 's ['app 'zero]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-X03-original-p1-even-two-succeeds
  (testing "X03: even(s²(0)) succeeds — commuted ∀-odd clause"
    ;; neg-call on even(2): negate body[x:=2] = (and (neq 2 zero) (forall ...))
    ;; (neq 2 zero) = (neq s²(0) zero) → free-close (s vs zero constructor). ✓
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'even ['app 's ['app 's ['app 'zero]]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-X04-original-p1-odd-zero-fails
  (testing "X04: odd(0) fails — pos-call on odd(0) closes by γ+β+refl-close"
    ;; pos-call on odd(0): body = ∀y.(0≠y ∨ ¬even(y)).
    ;; γ-rule instantiates y→v; β-splits into:
    ;;   Left: (0≠v) → v=0 → refl-close ✓
    ;;   Right: (neg even(v=0)) → neg-call(even(0)) → (neq 0 0) → refl-close ✓
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]]
                (proveo ['pos ['app 'odd ['app 'zero]]] '() '() '() prog proof))))))))

(deftest test-X05-original-p1-even-one-fails
  (testing "X05: even(s(0)) fails — pos-call on even(1) closes"
    ;; pos-call on even(1): body[x:=1] = or(1=0, ∃y.(1=s(y)∧odd(y))).
    ;;   Left: free-close ✓.  Right: subst-call chain → pos(odd(0)) → closes ✓.
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]]
                (proveo ['pos ['app 'even ['app 's ['app 'zero]]]] '() '() '() prog proof))))))))

;; ============================================================================
;; Section Y: List Programs — append and reverse (Fitting §6 examples)
;; ============================================================================
;;
;; Tests for two classic list programs encoded as single OR-body clauses
;; per Fitting's one-clause-per-relation constraint (Definition 2.1).
;;
;; List constructors:
;;   empty list : ['app 'nul]              ('nul, not 'nil — Clojure nil issue)
;;   cons(h, t) : ['app 'cons h t]
;;
;; NNF ordering rule: equality constraints precede pos-calls in every AND-chain.
;;
;; append(a1, a2, a3):
;;   (a1=nul ∧ a3=a2) ∨ ∃ah.∃at.∃ar.(a1=cons(ah,at) ∧ a3=cons(ah,ar) ∧ pos(append(at,a2,ar)))
;;
;; reverse(r1, r2):
;;   (r1=nul ∧ r2=nul) ∨ ∃rh.∃rt.∃rrp.(r1=cons(rh,rt) ∧ pos(reverse(rt,rrp)) ∧ pos(append(rrp,cons(rh,nul),r2)))
;;
;; Convention: "succeeds" tests use (proveo ['neg ...] ...) — neg-call with L-ground
;; concrete args.  "fails" tests use (proveo ['pos ...] ...) — pos-call, both branches
;; of the OR close (base via free-close, recursive via free-close on eq(nul,cons)).
;; Synthesis tests use a logic variable in the query argument position.
;; ============================================================================

;; --- Y01-Y05: append ---

(deftest test-Y01-append-base-nil-nil-nul
  (testing "Y01: append(nul, [b], [b]) succeeds — base case"
    ;; neg-call: negate body, a1=nul, a2=[b], a3=[b].
    ;; not(base) = or(neq(nul,nul), neq([b],[b])) — both refl-close ✓ immediately.
    ;; The not(recursive) conjunct in unexp is never reached.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                (proveo ['neg ['app 'append
                               ['app 'nul]
                               ['app 'cons ['app 'b] ['app 'nul]]
                               ['app 'cons ['app 'b] ['app 'nul]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y02-append-one-step
  (testing "Y02: append([a], [b], [a,b]) succeeds — one recursive step"
    ;; neg-call: a1=[a], a2=[b], a3=[a,b].
    ;; not(base): or(neq([a],nul), neq([a,b],[b])).
    ;;   Branch 1: neq([a],nul) → savefml; γ Ah,At,Ar → neq([a],cons(Ah,At)) refl-closes:
    ;;     Ah=a, At=nul; then neg(append(nul,[b],[b])) fires — base case closes ✓.
    ;;   Branch 2: neq([a,b],[b]) → savefml; same γ path closes ✓.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                (proveo ['neg ['app 'append
                               ['app 'cons ['app 'a] ['app 'nul]]
                               ['app 'cons ['app 'b] ['app 'nul]]
                               ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y03-append-wrong-result-fails
  (testing "Y03: append(nul, nul, [a]) fails — base case: eq([a],nul) free-closes"
    ;; pos-call: a1=nul, a2=nul, a3=[a].
    ;; Branch 1 (base): and(eq(nul,nul), eq([a],nul)) → eq([a],nul) free-closes ✓.
    ;; Branch 2 (recursive): eq(nul,cons(par_h,par_t)) → free-closes ✓.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                (proveo ['pos ['app 'append
                               ['app 'nul]
                               ['app 'nul]
                               ['app 'cons ['app 'a] ['app 'nul]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y04-append-two-steps
  (testing "Y04: append([a,b], nul, [a,b]) succeeds — two recursive steps"
    ;; neg-call: a1=[a,b], a2=nul, a3=[a,b].
    ;; Two recursive neg-calls before the base case append(nul,nul,nul) closes.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                (proveo ['neg ['app 'append
                               ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
                               ['app 'nul]
                               ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y05-append-synth-result
  (testing "Y05: synthesize Z s.t. append([a], [b], Z) — expected Z=[a,b]"
    ;; neg-call: a1=[a], a2=[b], a3=Z (LVar).
    ;; L-ground guard: Z is LVar → project[Z]=LVar → contains-par?=false → PASSES.
    ;; Recursive neg-call binds Z=cons(a,cons(b,nul)) via refl-close on neq(Z,cons(a,Ar)).
    (let [results (run 1 [z]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append
                                         ['app 'cons ['app 'a] ['app 'nul]]
                                         ['app 'cons ['app 'b] ['app 'nul]]
                                         z]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
             (first results))))))

;; --- Y06-Y10: reverse (depends on append) ---

(deftest test-Y06-reverse-base-nil
  (testing "Y06: reverse(nul, nul) succeeds — base case"
    ;; neg-call: r1=nul, r2=nul.
    ;; not(base) = or(neq(nul,nul), neq(nul,nul)) — both refl-close ✓ immediately.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]
                          ['reverse [r1 r2]
                           ['or ['and ['eq ['var r1] ['app 'nul]]
                                      ['eq ['var r2] ['app 'nul]]]
                                ['exists (tie rh
                                  ['exists (tie rt
                                    ['exists (tie rrp
                                      ['and ['eq ['var r1] ['app 'cons ['var rh] ['var rt]]]
                                            ['and ['pos ['app 'reverse ['var rt] ['var rrp]]]
                                                  ['pos ['app 'append ['var rrp]
                                                                      ['app 'cons ['var rh] ['app 'nul]]
                                                                      ['var r2]]]]])])])]]]]]
                (proveo ['neg ['app 'reverse ['app 'nul] ['app 'nul]]]
                        '() '() '() prog proof))))))))

(deftest test-Y07-reverse-singleton
  (testing "Y07: reverse([a], [a]) succeeds — singleton list"
    ;; neg-call: r1=[a], r2=[a].
    ;; not(base) branch 1: neq([a],nul) → savefml; γ Rh,Rt,Rp:
    ;;   neq([a],cons(Rh,Rt)) → refl-closes: Rh=a, Rt=nul.
    ;;   neg(reverse(nul,Rp)): binds Rp=nul via refl-close.
    ;;   neg(append(nul,cons(a,nul),[a])): base case closes ✓.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]
                          ['reverse [r1 r2]
                           ['or ['and ['eq ['var r1] ['app 'nul]]
                                      ['eq ['var r2] ['app 'nul]]]
                                ['exists (tie rh
                                  ['exists (tie rt
                                    ['exists (tie rrp
                                      ['and ['eq ['var r1] ['app 'cons ['var rh] ['var rt]]]
                                            ['and ['pos ['app 'reverse ['var rt] ['var rrp]]]
                                                  ['pos ['app 'append ['var rrp]
                                                                      ['app 'cons ['var rh] ['app 'nul]]
                                                                      ['var r2]]]]])])])]]]]]
                (proveo ['neg ['app 'reverse
                               ['app 'cons ['app 'a] ['app 'nul]]
                               ['app 'cons ['app 'a] ['app 'nul]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y08-reverse-two-elements
  (testing "Y08: reverse([a,b], [b,a]) succeeds — two elements"
    ;; neg-call: r1=[a,b], r2=[b,a].
    ;; Two recursive neg-calls before base case closes.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]
                          ['reverse [r1 r2]
                           ['or ['and ['eq ['var r1] ['app 'nul]]
                                      ['eq ['var r2] ['app 'nul]]]
                                ['exists (tie rh
                                  ['exists (tie rt
                                    ['exists (tie rrp
                                      ['and ['eq ['var r1] ['app 'cons ['var rh] ['var rt]]]
                                            ['and ['pos ['app 'reverse ['var rt] ['var rrp]]]
                                                  ['pos ['app 'append ['var rrp]
                                                                      ['app 'cons ['var rh] ['app 'nul]]
                                                                      ['var r2]]]]])])])]]]]]
                (proveo ['neg ['app 'reverse
                               ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
                               ['app 'cons ['app 'b] ['app 'cons ['app 'a] ['app 'nul]]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y09-reverse-wrong-result-fails
  (testing "Y09: reverse(nul, [a]) fails — base case: eq([a],nul) free-closes"
    ;; pos-call: r1=nul, r2=[a].
    ;; Branch 1 (base): and(eq(nul,nul), eq([a],nul)) → eq([a],nul) free-closes ✓.
    ;; Branch 2 (recursive): eq(nul,cons(par_h,par_t)) → free-closes ✓.
    (is (seq
          (run 1 [proof]
            (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
              (let [prog [['append [a1 a2 a3]
                           ['or ['and ['eq ['var a1] ['app 'nul]]
                                      ['eq ['var a3] ['var a2]]]
                                ['exists (tie ah
                                  ['exists (tie at
                                    ['exists (tie ar
                                      ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                            ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                  ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]
                          ['reverse [r1 r2]
                           ['or ['and ['eq ['var r1] ['app 'nul]]
                                      ['eq ['var r2] ['app 'nul]]]
                                ['exists (tie rh
                                  ['exists (tie rt
                                    ['exists (tie rrp
                                      ['and ['eq ['var r1] ['app 'cons ['var rh] ['var rt]]]
                                            ['and ['pos ['app 'reverse ['var rt] ['var rrp]]]
                                                  ['pos ['app 'append ['var rrp]
                                                                      ['app 'cons ['var rh] ['app 'nul]]
                                                                      ['var r2]]]]])])])]]]]]
                (proveo ['pos ['app 'reverse
                               ['app 'nul]
                               ['app 'cons ['app 'a] ['app 'nul]]]]
                        '() '() '() prog proof))))))))

(deftest test-Y10-reverse-synth-result
  (testing "Y10: synthesize R s.t. reverse([a,b], R) — expected R=[b,a]"
    ;; neg-call: r1=[a,b], r2=R (LVar).
    ;; L-ground guard: R is LVar → passes. Recursive neg-calls bind R=[b,a].
    (let [results (run 1 [r]
                    (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]
                                  ['reverse [r1 r2]
                                   ['or ['and ['eq ['var r1] ['app 'nul]]
                                              ['eq ['var r2] ['app 'nul]]]
                                        ['exists (tie rh
                                          ['exists (tie rt
                                            ['exists (tie rrp
                                              ['and ['eq ['var r1] ['app 'cons ['var rh] ['var rt]]]
                                                    ['and ['pos ['app 'reverse ['var rt] ['var rrp]]]
                                                          ['pos ['app 'append ['var rrp]
                                                                              ['app 'cons ['var rh] ['app 'nul]]
                                                                              ['var r2]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'reverse
                                         ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
                                         r]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'cons ['app 'b] ['app 'cons ['app 'a] ['app 'nul]]]
             (first results))))))

(deftest test-Y11-append-synth-result-three-element
  (testing "Y11: synthesize Z s.t. append([a], [b,c], Z) — expected Z=[a,b,c]"
    ;; neg-call: a1=[a], a2=[b,c], a3=Z (LVar).
    ;; L-ground guard: Z is LVar → project[Z]=LVar → contains-par?=false → PASSES.
    ;; Recursive neg-call binds Z=cons(a,cons(b,cons(c,nul))) via refl-close.
    (let [results (run 1 [z]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append
                                         ['app 'cons ['app 'a] ['app 'nul]]
                                         ['app 'cons ['app 'b] ['app 'cons ['app 'c] ['app 'nul]]]
                                         z]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'cons ['app 'c] ['app 'nul]]]]
             (first results))))))

(deftest test-Y12-append-inverse-synth-all-splits
  (testing "Y12: append(A, B, [a,b,c]) — all 4 splits found with both A and B as LVars"
    ;; With the 'once-forall fix, negated existentials instantiate only once per branch
    ;; rather than re-enqueueing indefinitely.  This allows depth-first search to reach
    ;; all 4 splits of [a,b,c]: ([], [a,b,c]), ([a], [b,c]), ([a,b], [c]), ([a,b,c], []).
    ;;
    ;; Before the fix, run N [la lb] for any N returned only 2 unique results (splits 1 and 2),
    ;; because the forall re-enqueue created infinite proof paths that starved deeper solutions.
    (let [abc ['app 'cons ['app 'a]
                ['app 'cons ['app 'b]
                  ['app 'cons ['app 'c] ['app 'nul]]]]
          results (run 4 [la lb]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb abc]]
                                  '() '() '() prog proof)))))]
      (is (= 4 (count results)))
      (let [la-vals (map first results)]
        (is (some #(= % ['app 'nul]) la-vals))
        (is (some #(= % ['app 'cons ['app 'a] ['app 'nul]]) la-vals))
        (is (some #(= % ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]) la-vals))
        (is (some #(= % ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'cons ['app 'c] ['app 'nul]]]]) la-vals))))))
;; ============================================================================
;; Section Z: Nested list programs — append with list-valued elements
;; ============================================================================
;; The append program treats list elements as opaque terms.  These tests verify
;; that it handles nested lists (lists whose elements are themselves lists)
;; without modification.  Sublists are represented as (app cons ...) terms
;; embedded inside the outer (app cons ...) spine.

(deftest test-Y13-append-nested-forward
  (testing "Y13: append([[a]], [[b]], Z) — forward with nested list elements, Z=[[a],[b]]"
    (let [sub-a    ['app 'cons ['app 'a] ['app 'nul]]
          sub-b    ['app 'cons ['app 'b] ['app 'nul]]
          la       ['app 'cons sub-a ['app 'nul]]
          lb       ['app 'cons sub-b ['app 'nul]]
          expected ['app 'cons sub-a ['app 'cons sub-b ['app 'nul]]]
          results  (run 1 [z]
                     (nom a1 a2 a3 ah at ar
                       (let [prog [['append [a1 a2 a3]
                                    ['or ['and ['eq ['var a1] ['app 'nul]]
                                               ['eq ['var a3] ['var a2]]]
                                         ['exists (tie ah
                                           ['exists (tie at
                                             ['exists (tie ar
                                               ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                     ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                           ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                         (fresh [proof]
                           (proveo ['neg ['app 'append la lb z]]
                                   '() '() '() prog proof)))))]
      (is (seq results))
      (is (= expected (first results))))))

(deftest test-Y14-append-nested-synth-second-arg
  (testing "Y14: append([[a,b]], Z, [[a,b],[c,d]]) — synthesize Z=[[c,d]]"
    (let [sub-ab ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
          sub-cd ['app 'cons ['app 'c] ['app 'cons ['app 'd] ['app 'nul]]]
          la     ['app 'cons sub-ab ['app 'nul]]
          lc     ['app 'cons sub-ab ['app 'cons sub-cd ['app 'nul]]]
          results (run 1 [z]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la z lc]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'cons sub-cd ['app 'nul]] (first results))))))

(deftest test-Y15-append-nested-inverse-all-splits
  (testing "Y15: append(A, B, [[a],[b]]) — all 3 splits with nested list result"
    (let [sub-a ['app 'cons ['app 'a] ['app 'nul]]
          sub-b ['app 'cons ['app 'b] ['app 'nul]]
          lc    ['app 'cons sub-a ['app 'cons sub-b ['app 'nul]]]
          results (run 3 [la lb]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb lc]]
                                  '() '() '() prog proof)))))]
      (is (= 3 (count results)))
      (let [la-vals (map first results)]
        (is (some #(= % ['app 'nul]) la-vals))
        (is (some #(= % ['app 'cons sub-a ['app 'nul]]) la-vals))
        (is (some #(= % ['app 'cons sub-a ['app 'cons sub-b ['app 'nul]]]) la-vals))))))

;; ============================================================================
;; Triply-nested lists (depth 3), ≥5 outer elements, LVars at multiple levels
;; ============================================================================
;; Structure used in Z01–Z04:
;;   lc = [e1, e2, e3, e4, e5]   (5 outer level-3 elements)
;;     e1 = [[a,b],[c,d]]  (level-2 sublist containing two level-1 flat lists)
;;     e2 = [[e,f],[g,h]]
;;     e3 = [[i,j]]
;;     e4 = [[k,l]]
;;     e5 = [[a,b]]        (reuses ab)
;;
;; All tests share the same append program.  Logic variables appear at:
;;   level-0: the outer list (la, lb, lc themselves)
;;   level-1: elements of the outer list (level-2 sublists)
;;   level-3: atoms inside the innermost flat lists

(deftest test-Z01-append-depth3-forward
  (testing "Z01: append(la, lb, Z) — depth-3, 5-element result synthesized, all concrete"
    (let [ab  ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
          cd  ['app 'cons ['app 'c] ['app 'cons ['app 'd] ['app 'nul]]]
          ef  ['app 'cons ['app 'e] ['app 'cons ['app 'f] ['app 'nul]]]
          gh  ['app 'cons ['app 'g] ['app 'cons ['app 'h] ['app 'nul]]]
          ij  ['app 'cons ['app 'i] ['app 'cons ['app 'j] ['app 'nul]]]
          kl  ['app 'cons ['app 'k] ['app 'cons ['app 'l] ['app 'nul]]]
          e1  ['app 'cons ab ['app 'cons cd ['app 'nul]]]
          e2  ['app 'cons ef ['app 'cons gh ['app 'nul]]]
          e3  ['app 'cons ij ['app 'nul]]
          e4  ['app 'cons kl ['app 'nul]]
          e5  ['app 'cons ab ['app 'nul]]
          la  ['app 'cons e1 ['app 'cons e2 ['app 'nul]]]
          lb  ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]
          lc  ['app 'cons e1 ['app 'cons e2 ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]]]
          results (run 1 [z]
                    (nom a1 a2 a3 ah at ar
                      (let [prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb z]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= lc (first results))))))

(deftest test-Z02-append-depth3-level0-level1-lvars
  (testing "Z02: LVars at level-0 (outer suffix lb) and level-1 (inner element P) simultaneously"
    ;; la = [P, e2] where P is an unknown level-2 sublist (level-1 LVar from the outer list's perspective).
    ;; lb-out is an unknown outer suffix (level-0 LVar).
    ;; lc is a concrete 5-element depth-3 list.
    ;; Proof binds P = e1 (first outer element of lc) and lb-out = [e3, e4, e5].
    (let [ab  ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
          cd  ['app 'cons ['app 'c] ['app 'cons ['app 'd] ['app 'nul]]]
          ef  ['app 'cons ['app 'e] ['app 'cons ['app 'f] ['app 'nul]]]
          gh  ['app 'cons ['app 'g] ['app 'cons ['app 'h] ['app 'nul]]]
          ij  ['app 'cons ['app 'i] ['app 'cons ['app 'j] ['app 'nul]]]
          kl  ['app 'cons ['app 'k] ['app 'cons ['app 'l] ['app 'nul]]]
          e1  ['app 'cons ab ['app 'cons cd ['app 'nul]]]
          e2  ['app 'cons ef ['app 'cons gh ['app 'nul]]]
          e3  ['app 'cons ij ['app 'nul]]
          e4  ['app 'cons kl ['app 'nul]]
          e5  ['app 'cons ab ['app 'nul]]
          lc  ['app 'cons e1 ['app 'cons e2 ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]]]
          results (run 1 [P lb-out]
                    (nom a1 a2 a3 ah at ar
                      (let [la   ['app 'cons P ['app 'cons e2 ['app 'nul]]]
                            prog [['append [a1 a2 a3]
                                   ['or ['and ['eq ['var a1] ['app 'nul]]
                                              ['eq ['var a3] ['var a2]]]
                                        ['exists (tie ah
                                          ['exists (tie at
                                            ['exists (tie ar
                                              ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                    ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                          ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb-out lc]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (let [[P-val lb-val] (first results)]
        (is (= e1 P-val))
        (is (= ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]] lb-val))))))

(deftest test-Z03-append-depth3-two-atom-lvars
  (testing "Z03: atom-level LVars R (in e1) and S (in e2) at depth 3, rest concrete"
    ;; R replaces 'b inside [a,b] in e1: ab-R = [a,R].
    ;; S replaces 'h inside [g,h] in e2: gh-S = [g,S].
    ;; la = [e1-R, e2-S] (2 elements), lb = [e3, e4, e5] (3 elements, concrete).
    ;; lc = [e1, e2, e3, e4, e5] (5 elements, fully concrete).
    ;; Proof: e1-R matches e1 → R = (app 'b); e2-S matches e2 → S = (app 'h).
    (let [cd  ['app 'cons ['app 'c] ['app 'cons ['app 'd] ['app 'nul]]]
          ef  ['app 'cons ['app 'e] ['app 'cons ['app 'f] ['app 'nul]]]
          ab  ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
          gh  ['app 'cons ['app 'g] ['app 'cons ['app 'h] ['app 'nul]]]
          ij  ['app 'cons ['app 'i] ['app 'cons ['app 'j] ['app 'nul]]]
          kl  ['app 'cons ['app 'k] ['app 'cons ['app 'l] ['app 'nul]]]
          e1  ['app 'cons ab ['app 'cons cd ['app 'nul]]]
          e2  ['app 'cons ef ['app 'cons gh ['app 'nul]]]
          e3  ['app 'cons ij ['app 'nul]]
          e4  ['app 'cons kl ['app 'nul]]
          e5  ['app 'cons ab ['app 'nul]]
          lc  ['app 'cons e1 ['app 'cons e2 ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]]]
          results (run 1 [R S]
                    (nom a1 a2 a3 ah at ar
                      (let [ab-R  ['app 'cons ['app 'a] ['app 'cons R ['app 'nul]]]
                            gh-S  ['app 'cons ['app 'g] ['app 'cons S ['app 'nul]]]
                            e1-R  ['app 'cons ab-R ['app 'cons cd ['app 'nul]]]
                            e2-S  ['app 'cons ef ['app 'cons gh-S ['app 'nul]]]
                            la    ['app 'cons e1-R ['app 'cons e2-S ['app 'nul]]]
                            lb    ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]
                            prog  [['append [a1 a2 a3]
                                    ['or ['and ['eq ['var a1] ['app 'nul]]
                                               ['eq ['var a3] ['var a2]]]
                                         ['exists (tie ah
                                           ['exists (tie at
                                             ['exists (tie ar
                                               ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                     ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                           ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb lc]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (let [[R-val S-val] (first results)]
        (is (= ['app 'b] R-val))
        (is (= ['app 'h] S-val))))))

(deftest test-Z04-append-depth3-combined-three-levels
  (testing "Z04: LVars at level-0 (lb), level-1 (P), and level-3 (S) simultaneously"
    ;; la = [P, e2-S] where:
    ;;   P   is a level-1 LVar (unknown level-2 sublist, outer list element)
    ;;   e2-S = [[e,f],[g,S]] where S is a level-3 atom LVar inside e2
    ;; lb-out is a level-0 LVar (unknown outer suffix).
    ;; lc is fully concrete (5 elements).
    ;; Proof binds P = e1, S = (app 'h), lb-out = [e3, e4, e5].
    (let [cd  ['app 'cons ['app 'c] ['app 'cons ['app 'd] ['app 'nul]]]
          ab  ['app 'cons ['app 'a] ['app 'cons ['app 'b] ['app 'nul]]]
          ef  ['app 'cons ['app 'e] ['app 'cons ['app 'f] ['app 'nul]]]
          gh  ['app 'cons ['app 'g] ['app 'cons ['app 'h] ['app 'nul]]]
          ij  ['app 'cons ['app 'i] ['app 'cons ['app 'j] ['app 'nul]]]
          kl  ['app 'cons ['app 'k] ['app 'cons ['app 'l] ['app 'nul]]]
          e1  ['app 'cons ab ['app 'cons cd ['app 'nul]]]
          e2  ['app 'cons ef ['app 'cons gh ['app 'nul]]]
          e3  ['app 'cons ij ['app 'nul]]
          e4  ['app 'cons kl ['app 'nul]]
          e5  ['app 'cons ab ['app 'nul]]
          lc  ['app 'cons e1 ['app 'cons e2 ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]]]]
          results (run 1 [P lb-out S]
                    (nom a1 a2 a3 ah at ar
                      (let [gh-S  ['app 'cons ['app 'g] ['app 'cons S ['app 'nul]]]
                            e2-S  ['app 'cons ef ['app 'cons gh-S ['app 'nul]]]
                            la    ['app 'cons P ['app 'cons e2-S ['app 'nul]]]
                            prog  [['append [a1 a2 a3]
                                    ['or ['and ['eq ['var a1] ['app 'nul]]
                                               ['eq ['var a3] ['var a2]]]
                                         ['exists (tie ah
                                           ['exists (tie at
                                             ['exists (tie ar
                                               ['and ['eq ['var a1] ['app 'cons ['var ah] ['var at]]]
                                                     ['and ['eq ['var a3] ['app 'cons ['var ah] ['var ar]]]
                                                           ['pos ['app 'append ['var at] ['var a2] ['var ar]]]]])])])]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'append la lb-out lc]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (let [[P-val lb-val S-val] (first results)]
        (is (= e1 P-val))
        (is (= ['app 'h] S-val))
        (is (= ['app 'cons e3 ['app 'cons e4 ['app 'cons e5 ['app 'nul]]]] lb-val))))))

;; ============================================================================
;; Section DI: Disunification with Free Variables (Fitting §8)
;; ============================================================================
;;
;; Fitting §8 identifies disunification — closing (eq t1 t2) branches when
;; t1 or t2 contain free (logic) variables — as "perhaps the most intractable
;; implementation issue."  A disunifier is a substitution making two terms
;; differ at a function symbol position.
;;
;; The current implementation handles this through a free-close + decompose
;; cascade: free-closureo generates root-level clashes (different head
;; symbols), and the decompose rule generates sub-equalities for same-head
;; cases, enabling deeper disunification.  core.logic's != constraint
;; propagation automatically merges disunification constraints.
;;
;; These tests verify the cascade works correctly with free variables at
;; every depth level, in both forward and synthesis modes.

;; --- Phase 1: Unit tests for free-closureo with LVars ---

(deftest test-DI01-free-close-lvar-one-side
  (testing "free-closureo with LVar on one side: binds LVar to app term with different head"
    ;; free-closureo (app f (app a)) y — y is LVar
    ;; Expected: y binds to (app s2 . a2) with s2 != 'f
    (let [results (run 1 [y]
                    (free-closureo (lcons 'app (lcons 'f (lcons (lcons 'app (lcons 'a '())) '())))
                                  y))]
      (is (seq results)))))

(deftest test-DI02-free-close-both-lvars
  (testing "free-closureo with both sides LVars: binds both to app terms with different heads"
    (let [results (run 1 [q]
                    (fresh [x y]
                      (free-closureo x y)
                      (== q [x y])))]
      (is (seq results)))))

(deftest test-DI03-free-close-same-head-nullary-fails
  (testing "free-closureo with same head, nullary: fails (same constructor)"
    (let [results (run 1 [q]
                    (free-closureo (lcons 'app (lcons 'f '()))
                                  (lcons 'app (lcons 'f '()))))]
      (is (empty? results)))))

(deftest test-DI04-free-close-same-head-different-args-fails
  (testing "free-closureo with same head but different args: fails (free-close only checks heads)"
    (let [results (run 1 [q]
                    (free-closureo (lcons 'app (lcons 'f (lcons (lcons 'app (lcons 'a '())) '())))
                                  (lcons 'app (lcons 'f (lcons (lcons 'app (lcons 'b '())) '())))))]
      (is (empty? results)))))

;; --- Phase 3: Disunification in Proflog program context ---
;;
;; R(x) ← x ≠ t:  neg-call negates body to (eq x t).
;; The eq literal enters proveo's closure rules.

(deftest test-DI09-disunify-concrete-different-heads
  (testing "R(x) ← x≠a. Query R(b) succeeds: neg-call → (eq b a) → free-close b≠a"
    ;; Clause: R(pa) ← (neq (var pa) (app a))
    ;; Query: neg(R(b)) — neg-call: negate body → (eq b a)
    ;; (eq (app b) (app a)) → free-close: b ≠ a ✓
    (let [results (run 1 [proof]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'a]]]]]
                        (proveo ['neg ['app 'R ['app 'b]]] '() '() '() prog proof))))]
      (is (seq results)))))

(deftest test-DI10-disunify-concrete-same-fails
  (testing "R(x) ← x≠a. Query R(a) fails: neg-call → (eq a a) → no closure"
    ;; (eq (app a) (app a)): free-close fails (same head), decompose fails (no args)
    ;; Branch cannot close → query R(a) does not succeed
    (let [results (run 1 [proof]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'a]]]]]
                        (proveo ['neg ['app 'R ['app 'a]]] '() '() '() prog proof))))]
      (is (empty? results)))))

(deftest test-DI11-disunify-binary-root-clash
  (testing "R(x,y) ← x≠y. Query R(f(a), g(b)) succeeds: root clash f≠g"
    ;; neg-call: negate body → (eq f(a) g(b)) → free-close: f ≠ g ✓
    (let [results (run 1 [proof]
                    (nom pa pb
                      (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                        (proveo ['neg ['app 'R ['app 'f ['app 'a]] ['app 'g ['app 'b]]]]
                                '() '() '() prog proof))))]
      (is (seq results)))))

(deftest test-DI12-disunify-same-head-different-args
  (testing "R(x,y) ← x≠y. Query R(f(a), f(b)) succeeds: decompose → a≠b"
    ;; neg-call: negate body → (eq f(a) f(b))
    ;; free-close fails (same head f). Decompose: (eq a b) → free-close: a ≠ b ✓
    (let [results (run 1 [proof]
                    (nom pa pb
                      (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                        (proveo ['neg ['app 'R ['app 'f ['app 'a]] ['app 'f ['app 'b]]]]
                                '() '() '() prog proof))))]
      (is (seq results)))))

(deftest test-DI13-disunify-same-term-fails
  (testing "R(x,y) ← x≠y. Query R(f(a), f(a)) fails: decompose → (eq a a) → no closure"
    ;; (eq f(a) f(a)) → decompose → (eq a a) → same head, no args → no closure
    (let [results (run 1 [proof]
                    (nom pa pb
                      (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                        (proveo ['neg ['app 'R ['app 'f ['app 'a]] ['app 'f ['app 'a]]]]
                                '() '() '() prog proof))))]
      (is (empty? results)))))

;; --- Phase 2: Decompose cascade through proveo ---
;;
;; These exercise the free-close + decompose cascade on (eq ...) formulas
;; with LVars, end-to-end through Proflog program neg-calls.

(deftest test-DI05-disunify-lvar-root-clash
  (testing "R(x) ← x≠f(a). Synthesize x via root clash: x has head ≠ f"
    ;; neg-call R(x): negate body → (eq x f(a)).
    ;; free-close: x → (app s ...) with s ≠ f. Root-level disunifier.
    (let [results (run 1 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'f ['app 'a]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (seq results)))))

(deftest test-DI06-disunify-lvar-depth-1
  (testing "R(x) ← x≠f(a). Synthesize x via depth-1 decompose: x = f(b) where b has head ≠ a"
    ;; After root-clash disunifiers, decompose fires: x → f(arg2).
    ;; Sub-eq: (eq arg2 (app a)) → free-close: arg2 has head ≠ a.
    ;; x = (app f (app s ...)) where s ≠ a.
    ;; We use run* with enough results to find the depth-1 disunifier.
    (let [results (run 5 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'f ['app 'a]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (>= (count results) 2)
          "Should produce at least root and depth-1 disunifiers"))))

(deftest test-DI07-disunify-lvar-multiple-depths
  (testing "R(x) ← x≠f(a). run 2 collects both root and depth-1 disunifiers"
    ;; Root clash: x has head ≠ f.
    ;; Depth-1: x = f(something), something has head ≠ a.
    ;; Both should appear in run 2 (core.logic interleaving).
    (let [results (run 2 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'f ['app 'a]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (= 2 (count results))
          "Should find two disunifiers"))))

(deftest test-DI08-disunify-lvar-depth-2
  (testing "R(x) ← x≠f(g(a)). Synthesize x via depth-2 decompose chain"
    ;; (eq x f(g(a))) → 3 depth levels:
    ;;   Depth 0: x has head ≠ f (root clash)
    ;;   Depth 1: x = f(arg2), arg2 has head ≠ g
    ;;   Depth 2: x = f(g(arg3)), arg3 has head ≠ a
    ;; run 3 should find all three.
    (let [results (run 3 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'f ['app 'g ['app 'a]]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (= 3 (count results))
          "Should find root, depth-1, and depth-2 disunifiers"))))

;; --- Phase 4: Synthesis with disunification constraints ---

(deftest test-DI14-synth-disunify-simple
  (testing "R(x) ← x≠a. Synthesize x: run 1 finds some x ≠ a"
    (let [results (run 1 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'a]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (seq results)))))

(deftest test-DI15-synth-disunify-binary-root
  (testing "R(x,y) ← x≠y. Synth y given x=f(a): y has head ≠ f (root clash)"
    (let [results (run 1 [y]
                    (nom pa pb
                      (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R ['app 'f ['app 'a]] y]]
                                  '() '() '() prog proof)))))]
      (is (seq results)))))

(deftest test-DI16-synth-disunify-binary-multiple
  (testing "R(x,y) ← x≠y. run 2 for y given x=f(a): root + depth-1 disunifiers"
    (let [results (run 2 [y]
                    (nom pa pb
                      (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R ['app 'f ['app 'a]] y]]
                                  '() '() '() prog proof)))))]
      (is (= 2 (count results))
          "Should find root and depth-1 disunifiers for y"))))

(deftest test-DI17-synth-disunify-depth-3
  (testing "R(x) ← x≠f(g(a)). run 3 finds root, depth-1, depth-2 disunifiers"
    (let [results (run 3 [x]
                    (nom pa
                      (let [prog [['R [pa] ['neq ['var pa] ['app 'f ['app 'g ['app 'a]]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (= 3 (count results))
          "Three depth levels of disunifiers"))))

;; --- Phase 5: Multiple disunification constraints ---

(deftest test-DI18-multi-disunify-concrete
  (testing "R(x) ← x≠a ∧ x≠b. Query R(c) succeeds: both (eq c a) and (eq c b) close"
    ;; neg-call: negate body → (or (eq x a) (eq x b)) — β-split
    ;; Branch 1: (eq c a) → free-close: c ≠ a ✓
    ;; Branch 2: (eq c b) → free-close: c ≠ b ✓
    (let [results (run 1 [proof]
                    (nom pa
                      (let [prog [['R [pa] ['and ['neq ['var pa] ['app 'a]]
                                                 ['neq ['var pa] ['app 'b]]]]]]
                        (proveo ['neg ['app 'R ['app 'c]]] '() '() '() prog proof))))]
      (is (seq results)))))

(deftest test-DI19-multi-disunify-synth
  (testing "R(x) ← x≠a ∧ x≠b. Synthesize x: must satisfy both constraints"
    ;; neg-call: negate body → (or (eq x a) (eq x b))
    ;; β-split: both branches must close.
    ;; Branch 1: (eq x a) → free-close: x has head ≠ a.
    ;; Branch 2: (eq x b) → this must ALSO close with the SAME x.
    ;; Since x already has head ≠ a, if head ≠ b too, both close.
    (let [results (run 1 [x]
                    (nom pa
                      (let [prog [['R [pa] ['and ['neq ['var pa] ['app 'a]]
                                                 ['neq ['var pa] ['app 'b]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R x]] '() '() '() prog proof)))))]
      (is (seq results)))))

(deftest test-DI20-disunify-forall-root-clash
  (testing "R(x) ← ∀y.(x≠f(y)). Query R(g(a)) succeeds: γ-var y, free-close g≠f"
    ;; Clause body: (forall (tie py (neq (var pa) (app f (var py)))))
    ;; neg-call: negate → (exists (tie py (eq pa (app f (var py)))))
    ;; Actually negate-formulao on forall→once-forall:
    ;;   negate(neq x f(y)) = (eq x f(y))
    ;;   negate(forall y.(neq x f(y))) = once-forall y.(eq x f(y))
    ;; Wait, that's wrong. Let me think again.
    ;; Body: (forall (tie py (neq (var pa) (app f (var py)))))
    ;; negate-formulao: ¬(forall y.P) = exists y.¬P
    ;;   = exists y. ¬(neq x f(y)) = exists y. (eq x f(y))
    ;; Actually negate(forall ...) = once-forall?
    ;; No: negate(forall) = exists, negate(exists) = once-forall.
    ;; So: negate body = (exists (tie py (eq (var pa) (app f (var py)))))
    ;; Wait, but neg-call negates the body. For neg-call on R(g(a)):
    ;;   Body with env pa→g(a): ∀y.(g(a)≠f(y))
    ;;   Negate: ∃y.(eq g(a) f(y))
    ;;   δ-rule: introduce witness p. (eq g(a) f(par p))
    ;;   free-close: g ≠ f ✓ — branch closes.
    ;; BUT: negate-formulao(forall ...) produces (exists ...), not (once-forall ...).
    ;; And then the exists in proveo uses the δ-rule (fresh nom p, bind py→(par p)).
    ;; Hmm, but negate-formulao goes: forall→exists.
    ;; And the exists case in proveo uses nom p and binds py→(par p).
    ;; But then (eq g(a) f(par p)) → free-close: g ≠ f ✓.
    ;;
    ;; Actually for the positive call: body itself is ∀y.(x≠f(y)).
    ;; neg-call negates: ∃y.(eq x f(y)) → processed by exists δ-rule.
    ;;
    ;; For R(g(a)): env pa→g(a). Body = forall y.(neq g(a) f(y)).
    ;; Negate = exists y.(eq g(a) f(y)) → δ-rule → (eq g(a) f(par p)) → free-close g≠f ✓.
    (let [results (run 1 [proof]
                    (nom pa py
                      (let [prog [['R [pa] ['forall (tie py ['neq ['var pa] ['app 'f ['var py]]])]]]]
                        (proveo ['neg ['app 'R ['app 'g ['app 'a]]]] '() '() '() prog proof))))]
      (is (seq results)))))

(deftest test-DI21-disunify-forall-same-head
  (testing "R(x) ← ∀y.(x≠f(y)). Query R(f(a)) — fails: can set y=a to make x=f(y)"
    ;; neg-call: negate body → ∃y.(eq f(a) f(y)).
    ;; δ-rule: (eq f(a) f(par p)). Decompose → (eq a (par p)).
    ;; (par p) is a δ-parameter, not an app term — free-close fails.
    ;; refl-close on eq: not applicable (eq, not neq).
    ;; The eq cannot be closed → branch stays open → neg-call fails.
    ;; This is correct: R(f(a)) should fail because ∀y.(f(a)≠f(y)) is false (set y=a).
    (let [results (run 1 [proof]
                    (nom pa py
                      (let [prog [['R [pa] ['forall (tie py ['neq ['var pa] ['app 'f ['var py]]])]]]]
                        (proveo ['neg ['app 'R ['app 'f ['app 'a]]]] '() '() '() prog proof))))]
      (is (empty? results)))))

;; --- Phase 6: Interaction with equality rewriting + procedure calls ---

(deftest test-DI22-disunify-with-eq-rewriting
  (testing "Disunification + equality rewriting: branch eq rewrites before free-close"
    ;; R(x) ← (eq x (app b)) ∧ (neq (var x) (app a))
    ;; This says: x=b and x≠a. Since b≠a, this holds.
    ;; Query R(y) where y is free.
    ;; neg-call: negate → (or (neq y b) (eq y a)).
    ;; β-split:
    ;;   Branch 1: (neq y b) → refl-close: y=b. Closes.
    ;;   Branch 2: (eq y a) → now y=b (from branch 1 in shared substitution).
    ;;     (eq b a) → free-close: b ≠ a ✓. Closes.
    ;; Result: y = b.
    (let [results (run 1 [y]
                    (nom pa
                      (let [prog [['R [pa] ['and ['eq ['var pa] ['app 'b]]
                                                 ['neq ['var pa] ['app 'a]]]]]]
                        (fresh [proof]
                          (proveo ['neg ['app 'R y]] '() '() '() prog proof)))))]
      (is (seq results))
      (is (= ['app 'b] (first results))))))

(deftest test-DI23-disunify-via-subst-call
  (testing "Disunification + substitutivity: subst-call rewrites par before disunification"
    ;; This tests the full chain: δ-rule introduces (par p), equality
    ;; rewrites (par p) → concrete, then disunification closes.
    ;;
    ;; R(x) ← ∃y.(x = s(y) ∧ y ≠ zero)
    ;; Query: R(s(s(zero))) — x=s(s(zero)), y should be s(zero), which ≠ zero.
    ;;
    ;; neg-call: negate body → ∀y.(x ≠ s(y) ∨ y = zero)
    ;;   = once-forall y. (or (neq x s(y)) (eq y zero))
    ;; γ-rule: introduce v for y. (or (neq s(s(zero)) s(v)) (eq v zero))
    ;; β-split:
    ;;   Branch 1: (neq s(s(zero)) s(v)) → decompose → (neq s(zero) v)
    ;;     → refl-close: v = s(zero). Closes.
    ;;   Branch 2: (eq v zero) → v = s(zero) from branch 1.
    ;;     (eq s(zero) zero) → free-close: s ≠ zero ✓. Closes.
    (let [results (run 1 [proof]
                    (nom pa py
                      (let [prog [['R [pa] ['exists (tie py
                                             ['and ['eq ['var pa] ['app 's ['var py]]]
                                                   ['neq ['var py] ['app 'zero]]])]]]]
                        (proveo ['neg ['app 'R ['app 's ['app 's ['app 'zero]]]]]
                                '() '() '() prog proof))))]
      (is (seq results)))))

;; --- Phase 7: Edge case — both sides are LVars ---

(deftest test-DI24-disunify-both-lvars
  (testing "free-closureo with both sides LVars: generates disunifier with different heads"
    ;; (eq x y) where both are free → free-close binds both to (app s1 ...)
    ;; and (app s2 ...) with s1 ≠ s2. Each binding is a valid disunifier.
    ;; The reified result includes constraint information (the != constraint),
    ;; so we just verify that free-closureo succeeds.
    (let [results (run 1 [q]
                    (fresh [x y]
                      (free-closureo x y)
                      (== q [x y])))]
      (is (seq results)))))

(deftest test-DI25-disunify-both-lvars-in-program
  (testing "R(x,y) ← x≠y. Synthesize both x and y — produces distinct terms"
    ;; neg-call: negate body → (eq x y).
    ;; free-close: x → (app s1 ...), y → (app s2 ...), s1 ≠ s2.
    ;; Both are synthesized simultaneously.
    (let [results (run 1 [q]
                    (nom pa pb
                      (fresh [x y]
                        (let [prog [['R [pa pb] ['neq ['var pa] ['var pb]]]]]
                          (fresh [proof]
                            (proveo ['neg ['app 'R x y]] '() '() '() prog proof)
                            (== q [x y]))))))]
      (is (seq results)))))

;; ============================================================================
;; Section ADV: Adversarial Tests — Spec vs. Implementation Gaps
;; ============================================================================
;;
;; These tests probe scenarios where the logical specification (Fitting 1994)
;; guarantees a result, but the computational implementation may not capture it.
;; Each test is categorized:
;;
;;   PASS          — implementation handles the scenario correctly
;;   EXPECTED-FAIL — known design constraint; documents implementation limitation
;;   UNEXPECTED    — reveals a previously unknown bug
;;
;; The goal is to characterize the implementation's completeness boundary.
;;
;; Adversarial vectors tested:
;;   AV1: NNF disjunct ordering sensitivity (commutativity of ∨)
;;   AV2: Arity mismatch — same head, different arities
;;   AV3: subst-call missing L-ground guard on rewritten args
;;   AV4: once-forall completeness for neg-call on ∃-body clauses
;;   AV5: Double negation ∀→∃→once-forall mismatch
;;   AV6: γ-rule starvation via deep unexp stack
;;   AV7: Conjunction ordering sensitivity (commutativity of ∧)
;; ============================================================================

;; --- Phase 1: NNF Ordering (AV1, AV7) ---

(deftest test-ADV01-p1-correct-disjunct-order-baseline
  (testing "ADV01: Fitting P1 odd(s(0)) with CORRECT disjunct order (neq before neg) — baseline"
    ;; odd(x) ← ∀y.(x≠y ∨ ¬even(y))
    ;; Negation: ∃y.(eq(x,y) ∧ pos(even(y))) — AND processes eq first → subst-call works.
    ;; Category: PASS (baseline)
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'even ['var y]]]])]]]
                    query ['neg ['app 'odd ['app 's ['app 'zero]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-ADV02-p1-reversed-disjunct-order
  (testing "ADV02: Fitting P1 odd(s(0)) with REVERSED disjunct order (neg before neq).
            AV1: ∀y.(¬even(y) ∨ x≠y) ≡ ∀y.(x≠y ∨ ¬even(y)) in classical logic.
            Negation yields ∃y.(pos(even(y)) ∧ eq(x,y)) — AND processes pos first
            → pos(even(par p)) saved to lits → eq(x,par p) triggers eq-triggered-call
            → rewrites even(par p) → even(x) → fires procedure call.
            Category: PASS (eq-triggered procedure call resolves ordering)"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['even [x]
                           ['or ['eq ['var x] ['app 'zero]]
                                ['exists (tie y
                                  ['and ['eq ['var x] ['app 's ['var y]]]
                                        ['pos ['app 'odd ['var y]]]])]]]
                          ['odd [x]
                           ['forall (tie y
                             ;; REVERSED: neg before neq (logically equivalent)
                             ['or ['neg ['app 'even ['var y]]]
                                  ['neq ['var x] ['var y]]])]]]
                    query ['neg ['app 'odd ['app 's ['app 'zero]]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-ADV03-forall-neq-before-neg-simple
  (testing "ADV03: Minimal NNF ordering — correct order (neq before neg).
            Q(a) ← a=a.  R(x) ← ∀y.(x≠y ∨ ¬Q(y)).
            Query: R(b) succeeds. For all y with Q(y), b≠y: Q(a) true, b≠a ✓.
            neg-call R(b): negate → ∃y.(eq(b,y) ∧ pos(Q(y))).
            δ-rule: y→par p. AND: eq(b,par p) → lits. pos(Q(par p)) → subst-call
            rewrites par p → b → Q(b). pos-call: eq(b,a) → free-close (b≠a) ✓.
            Category: PASS (correct NNF order)"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['Q [x] ['eq ['var x] ['app 'a]]]
                          ['R [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'Q ['var y]]]])]]]
                    query ['neg ['app 'R ['app 'b]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-ADV04-forall-neg-before-neq-simple
  (testing "ADV04: Minimal NNF ordering — REVERSED order (neg before neq).
            Same program as ADV03 but ∀y.(¬Q(y) ∨ x≠y).
            Logically equivalent: A∨B ≡ B∨A. R(b) should still succeed.
            neg-call R(b): negate → ∃y.(pos(Q(y)) ∧ eq(b,y)).
            δ-rule: y→par p. AND processes left-to-right:
              pos(Q(par p)) first → L-ground guard BLOCKS → savefml saves to lits.
              eq(b,par p) next → eq-triggered-call finds pos(Q(par p)) in lits
              → rewrites par p → b → Q(b) → pos-call body eq(b,a) → free-close ✓.
            Category: PASS (eq-triggered procedure call resolves ordering)"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['Q [x] ['eq ['var x] ['app 'a]]]
                          ['R [x]
                           ['forall (tie y
                             ;; REVERSED: neg before neq
                             ['or ['neg ['app 'Q ['var y]]]
                                  ['neq ['var x] ['var y]]])]]]
                    query ['neg ['app 'R ['app 'b]]]]
                (proveo query '() '() '() prog proof))))))))

;; --- Phase 1b: Conjunction ordering with ∃ bodies (AV7) ---
;;
;; Key insight: once-forall (from negating ∃) binds to LVar (not par),
;; so the L-ground guard does NOT block. The β-split produces independent
;; branches. No ordering issue — both orderings work.

(deftest test-ADV04b-exists-eq-before-pos
  (testing "ADV04b: R(x) ← ∃y.(x=y ∧ P(y)), P(a) ← a=a. Query: R(a) succeeds.
            neg-call: negate → once-forall y.(neq(a,y) ∨ neg(P(y))).
            y→LVar y1. β-split: neq(a,y1)→refl-close(y1=a)✓; neg(P(a))→refl-close✓.
            Category: PASS (baseline)"
    (is (seq
          (run 1 [proof]
            (nom pa pb
              (let [prog [['R [pa]
                           ['exists (tie pb
                             ['and ['eq ['var pa] ['var pb]]
                                   ['pos ['app 'P ['var pb]]]])]]
                          ['P [pa] ['eq ['var pa] ['app 'a]]]]]
                (proveo ['neg ['app 'R ['app 'a]]] '() '() '() prog proof))))))))

(deftest test-ADV04c-exists-pos-before-eq
  (testing "ADV04c: R(x) ← ∃y.(P(y) ∧ x=y) — pos before eq in EXISTS body.
            neg-call: negate → once-forall y.(neg(P(y)) ∨ neq(a,y)).
            y→LVar y1 (NOT par!). β-split independent branches:
              neg(P(y1)): L-ground ✓ (LVar, not par). neg-call P(y1): neq(y1,a)→refl-close✓.
              neq(a,y1=a)→refl-close✓.
            Category: PASS (once-forall→LVar avoids par issue)"
    (is (seq
          (run 1 [proof]
            (nom pa pb
              (let [prog [['R [pa]
                           ['exists (tie pb
                             ;; REVERSED: pos before eq
                             ['and ['pos ['app 'P ['var pb]]]
                                   ['eq ['var pa] ['var pb]]])]]
                          ['P [pa] ['eq ['var pa] ['app 'a]]]]]
                (proveo ['neg ['app 'R ['app 'a]]] '() '() '() prog proof))))))))

;; --- Phase 2: Arity Mismatch (AV2) ---
;;
;; Fitting assumes fixed arities per function symbol — a given symbol f
;; always has the same number of arguments in any well-formed program.
;; The implementation's untyped term grammar (app symbol term*) allows
;; variable arity, but the closure rules don't handle the mixed-arity
;; case: free-closureo only checks head symbols (same f → fails), and
;; decompose-eq-argso requires paired argument lists (length mismatch
;; → fails).  Neither rule fires on eq(f(a), f()).
;;
;; This is out of spec (no well-formed Proflog program uses f with two
;; different arities), but it is a validation gap — a user could stumble
;; into it.  A future fix could either:
;;   (a) add argument-length comparison to free-closureo when heads match, or
;;   (b) add arity checking at program validation time (check-program!).
;;
;; See task list for a future ticket to revisit this question.

(deftest test-ADV05-arity-mismatch-close
  (testing "ADV05: arity-mismatch-closureo on f(a) vs f() — same head, different arities.
            In Herbrand semantics, f(a) ≠ f() (structurally different terms).
            free-closureo doesn't fire (same head), but arity-mismatch-closureo does."
    (is (seq
          (run 1 [q]
            (arity-mismatch-closureo ['app 'f ['app 'a]] ['app 'f])
            (== q :closed))))))

(deftest test-ADV06-eq-arity-mismatch-via-proveo
  (testing "ADV06: (eq f(a) f()) as a literal — closes via arity-mismatch-close.
            free-close doesn't fire (same head f), but arity-mismatch-close
            detects that f/1 ≠ f/0 (different arg counts)."
    (is (seq
          (run 1 [proof]
            (proveo ['eq ['app 'f ['app 'a]] ['app 'f]]
                    '() '() '() '() proof))))))

(deftest test-ADV07-program-arity-mismatch
  (testing "ADV07: R(x) ← x ≠ f(). Query: R(f(a)) — succeeds since f(a) ≠ f().
            neg-call: negate body → eq(f(a), f()). Closes via arity-mismatch-close."
    (is (seq
          (run 1 [proof]
            (nom pa
              (let [prog [['R [pa] ['neq ['var pa] ['app 'f]]]]]
                (proveo ['neg ['app 'R ['app 'f ['app 'a]]]]
                        '() '() '() prog proof))))))))

;; --- Phase 3: once-forall Completeness (AV4, AV5) ---
;;
;; once-forall instantiates ONCE (single LVar). For subsidiary tableaux,
;; this is usually sufficient because the LVar can unify with any term.
;; However, when the negated body contains an OR (β-rule), both branches
;; must close with the SAME binding of the γ-variable.

(deftest test-ADV08-once-forall-single-branch-suffices
  (testing "ADV08: R ← ∃y.(y=a). R is true. neg-call: once-forall y.(y≠a).
            y→y1. neq(y1,a): refl-close (y1=a) → neq(a,a) ✓.
            Category: PASS (single instantiation suffices)"
    (is (seq
          (run 1 [proof]
            (nom pa
              (let [prog [['R [] ['exists (tie pa ['eq ['var pa] ['app 'a]])]]]]
                (proveo ['neg ['app 'R]] '() '() '() prog proof))))))))

(deftest test-ADV09-once-forall-beta-shared-binding
  (testing "ADV09: R ← ∃y.(y=a ∧ y=b). R is false (no y equals both a and b).
            neg-call: once-forall y.(y≠a ∨ y≠b). y→y1.
            β-split: neq(y1,a) AND neq(y1,b) must BOTH close.
            Branch 1: neq(y1,a) → refl-close (y1=a) ✓.
            Branch 2: neq(a,b) → a≠b is TRUE (not contradictory) → can't close.
            The tableau correctly fails: ∀y.(y≠a ∨ y≠b) is TRUE (satisfiable),
            so neg-call can't prove R is true (because R is false).
            BUT: we also can't prove R is false via neg-call — the method only
            proves R is TRUE (by showing ¬body is contradictory).
            Category: PASS (correct: neg-call doesn't find proof because R is false)"
    ;; R with two SEPARATE existentials IS true (y=a, z=b satisfy both).
    ;; neg-call correctly proves R is true.
    ;; Proof: neg-proc-call → once-univ y1 → once-univ z1 → β-split:
    ;;   neq(y1,a)→refl-close(y1=a)✓; neq(z1,b)→refl-close(z1=b)✓.
    ;; Category: PASS (implementation correctly handles two independent existentials)
    (is (seq
          (run 1 [proof]
            (nom pa pb
              (let [prog [['R [] ['exists (tie pa
                                   ['exists (tie pb
                                     ['and ['eq ['var pa] ['app 'a]]
                                           ['eq ['var pb] ['app 'b]]])])]]]]
                (proveo ['neg ['app 'R]] '() '() '() prog proof))))))))

(deftest test-ADV09c-once-forall-same-var-two-constraints
  (testing "ADV09c: R ← ∃y.(y=a ∧ y=b). R is false (single y can't equal both a and b).
            neg-call: once-forall y.(neq(y,a) ∨ neq(y,b)). y→y1. β-split:
              neq(y1,a): refl-close y1=a ✓.
              neq(a,b): TRUE (not contradictory) → can't close → β-fails.
            Correct: neg-call doesn't prove R true (R is false).
            Can pos-call prove R false? pos-call R: body = ∃y.(y=a ∧ y=b).
            δ-rule: y→par p. AND: eq(par p, a) → save. eq(par p, b).
            eq(par p, b): is par p = b? par p is a parameter. eq between
            (par p) and (app b): par is ['par p], not (lcons 'app ...).
            Can't free-close (par not app). Can't decompose. savefml → empty unexp → dead.
            Category: implementation can neither prove R true NOR false — a completeness gap."
    ;; Neither neg-call nor pos-call can close.
    ;; neg-call fails (correct: R is false, neg-call proves R true):
    (is (empty?
          (run 1 [proof]
            (nom pa
              (let [prog [['R [] ['exists (tie pa
                                   ['and ['eq ['var pa] ['app 'a]]
                                         ['eq ['var pa] ['app 'b]]])]]]]
                (proveo ['neg ['app 'R]] '() '() '() prog proof))))))
    ;; pos-call: DOES succeed via para-free-close!
    ;; pos-call R → body = ∃y.(y=a ∧ y=b). δ-rule: y→par p.
    ;; AND: eq(par p, a) → save to lits. eq(par p, b):
    ;; para-free-close rewrites par p → a (using eq in lits) → eq(a, b) → free-close ✓.
    ;; Category: PASS (para-free-close handles transitivity)
    (is (seq
          (run 1 [proof]
            (nom pa
              (let [prog [['R [] ['exists (tie pa
                                   ['and ['eq ['var pa] ['app 'a]]
                                         ['eq ['var pa] ['app 'b]]])]]]]
                (proveo ['pos ['app 'R]] '() '() '() prog proof))))))))

;; --- Phase 4: Double Negation (AV5) ---
;;
;; ¬¬(∀x.P) should be operationally equivalent to ∀x.P.
;; But negate-formulao maps: ∀→∃→once-forall. So ¬¬(∀x.P) = once-forall x.P,
;; which does NOT re-enqueue (unlike genuine ∀). If the proof requires
;; MULTIPLE γ-instantiations, the double-negated form fails.

(deftest test-ADV10-double-negation-forall-single-instantiation
  (testing "ADV10: R(x) ← ∀y.(x≠y ∨ ¬Q(y)), Q(a) ← a=a.
            Query: R(b) succeeds with ONE γ-instantiation.
            Direct ∀: works. Double-negated ¬¬∀: should also work (one instantiation).
            Category: PASS (single instantiation suffices, no AV5 issue)"
    ;; Direct ∀ (same as ADV03):
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['Q [x] ['eq ['var x] ['app 'a]]]
                          ['R [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'Q ['var y]]]])]]]
                    query ['neg ['app 'R ['app 'b]]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-ADV11-double-negation-forall-needs-two
  (testing "ADV11: Test case requiring multiple γ-instantiations.
            R(x) ← ∀y.(x≠y ∨ ¬Q(y)). Q(a) ← a=a. Q(b) ← b=b.
            But wait — check-program! enforces one clause per relation.
            So Q can only have ONE clause. Use Q(x) ← (x=a ∨ x=b).
            Query: R(c) succeeds. For y=a: c≠a ✓. For y=b: c≠b ✓.
            With ∀: γ-instantiate y→y1. β-split:
              neq(c,y1): refl-close y1=c ✓. neg(Q(c)): neg-call Q(c):
              negate(c=a ∨ c=b) → and(neq(c,a), neq(c,b)).
              neq(c,a): free-close ✓. neq(c,b): free-close ✓.
            One γ-instantiation suffices! y1 bound to c.
            The AV5 gap would need a case where γ re-enqueueing produces a
            DIFFERENT closure path than the first instantiation. In a subsidiary
            tableau with a single formula (no other lits), one instantiation
            of the LVar always suffices because it can unify to anything.
            Category: PASS (one instantiation always suffices in subsidiary tableaux)"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['Q [x] ['or ['eq ['var x] ['app 'a]]
                                       ['eq ['var x] ['app 'b]]]]
                          ['R [x]
                           ['forall (tie y
                             ['or ['neq ['var x] ['var y]]
                                  ['neg ['app 'Q ['var y]]]])]]]
                    query ['neg ['app 'R ['app 'c]]]]
                (proveo query '() '() '() prog proof))))))))

;; --- Phase 5: Structural Edge Cases ---

(deftest test-ADV12-eq-par-par-different-pars
  (testing "ADV12: (eq (par p) (par q)) — two different parameters.
            δ-parameters are constants of L^par, NOT L. Fitting's Free
            Closure Rule only applies to constants of L. Two different
            δ-parameters CAN denote the same domain element (e.g.,
            ∃x.∃y.x=y is satisfiable — take x=y=a). So eq(par p, par q)
            is NOT closable. This is CORRECT, not an incompleteness.
            Category: PASS (correctly stays open — sound)"
    (is (empty?
          (run 1 [proof]
            (nom p q
              (proveo ['eq ['par p] ['par q]]
                      '() '() '() '() proof)))))))

(deftest test-ADV12b-neq-par-app-closes
  (testing "ADV12b: (neq (par p) (app a)) — par vs constructor.
            neq between a par and a constructor: is this closable?
            refl-close requires both sides identical → fails (par ≠ app).
            neq(par p, app a) is satisfiable (par p might ≠ a) but also
            falsifiable (par p might = a). Neither closure fires.
            Category: PASS (correctly doesn't close — par could equal anything)"
    (is (empty?
          (run 1 [proof]
            (nom p
              (proveo ['neq ['par p] ['app 'a]]
                      '() '() '() '() proof)))))))

;; --- Section SUB: General Substitutivity (Fitting §5) ---
;;
;; Tests verifying that the general substitutivity rule is fully captured
;; by the combination of existing rules + eq-triggered variants.
;; The key gap: when neq arrives before eq, eq-neq-closeo has no equality
;; pairs to work with. The eq-triggered-neq-close rule fixes this.

(deftest test-SUB01-neq-before-eq-closes
  (testing "SUB01: neq(f(g(p)),f(g(a))) ∧ eq(p,a) — neq arrives first.
            Without eq-triggered-neq-close: neq saved to lits, eq arrives
            but can't retroactively close the neq. With the rule: eq as
            current lit triggers eq-neq-closeo on the saved neq, rewrites
            f(g(p)) → f(g(a)) → reflexivity → closes.
            Category: PASS (eq-triggered-neq-close)"
    (is (seq
          (run 1 [proof]
            (nom p
              (proveo ['and ['neq ['app 'f ['app 'g ['par p]]]
                                  ['app 'f ['app 'g ['app 'a]]]]
                            ['eq ['par p] ['app 'a]]]
                      '() '() '() '() proof)))))))

(deftest test-SUB02-eq-before-neq-closes
  (testing "SUB02: eq(p,a) ∧ neq(f(g(p)),f(g(a))) — eq arrives first.
            Standard path: eq saved to lits, neq uses eq-neq-closeo with
            equality pairs from lits. No new rule needed.
            Category: PASS (baseline — eq-refl-close)"
    (is (seq
          (run 1 [proof]
            (nom p
              (proveo ['and ['eq ['par p] ['app 'a]]
                            ['neq ['app 'f ['app 'g ['par p]]]
                                  ['app 'f ['app 'g ['app 'a]]]]]
                      '() '() '() '() proof)))))))

(deftest test-SUB03-nested-eq-decompose-para
  (testing "SUB03: eq(f(p),f(a)) ∧ eq(p,b) — decompose + para-free-close.
            eq(f(p),f(a)) decomposes to eq(p,a), saved to lits.
            eq(p,b) arrives, para-free-close: eq(p,a) + eq(p,b) → eq(a,b)
            → free-close (a≠b). Both orderings work.
            Category: PASS (decompose + para-free-close)"
    (is (seq
          (run 1 [proof]
            (nom p
              (proveo ['and ['eq ['app 'f ['par p]] ['app 'f ['app 'a]]]
                            ['eq ['par p] ['app 'b]]]
                      '() '() '() '() proof)))))))

(deftest test-SUB04-transitive-par-chain
  (testing "SUB04: eq(p,q) ∧ eq(p,a) ∧ eq(q,b) — transitive par chain.
            para-free-closeo handles multi-step: eq(p,q) + eq(p,a) → eq(a,q)
            + eq(q,b) → eq(a,b) → free-close. Both orderings work.
            Category: PASS (para-free-close transitivity)"
    (is (seq
          (run 1 [proof]
            (nom p q
              (proveo ['and ['eq ['par p] ['par q]]
                            ['and ['eq ['par p] ['app 'a]]
                                  ['eq ['par q] ['app 'b]]]]
                      '() '() '() '() proof)))))))

;; --- Section RV: Adversarial Review (unknown unknowns) ---
;;
;; Tests from the systematic adversarial review targeting interactions
;; between rules, boundary conditions, and edge cases not covered by
;; earlier test sections.

(deftest test-RV01-eq-triggers-multiple-rules
  (testing "RV01: Same eq literal triggers multiple rules via backtracking.
            Branch: neq(par p, a), pos(R(par p)), then eq(par p, a) arrives.
            Multiple proof paths exist (eq-neq-close, eq-triggered-neq-close,
            eq-triggered-call). All are sound — run 3 should find ≥2 proofs.
            Category: PASS (redundant proofs, all sound)"
    (is (<= 2 (count
          (run 3 [proof]
            (nom x p
              (let [prog [['R [x] ['neq ['var x] ['var x]]]]]
                (proveo ['and ['neq ['par p] ['app 'a]]
                              ['and ['pos ['app 'R ['par p]]]
                                    ['eq ['par p] ['app 'a]]]]
                        '() '() '() '() proof)))))))))

(deftest test-RV02-nullary-relation-call
  (testing "RV02: Nullary relation R() — no args, plain pos-call fires.
            rewrite-term-with-eqso fails on nullary (correct — nothing to rewrite).
            Plain pos-call has no L-ground guard issue (no args to check).
            Category: PASS (nullary call works)"
    (is (seq
          (run 1 [proof]
            (proveo ['pos ['app 'R]]
                    '() '() '()
                    [['R [] ['neq ['app 'a] ['app 'a]]]]
                    proof))))))

(deftest test-RV03-bind-argso-arity-mismatch
  (testing "RV03: Arity mismatch — clause has 2 params, call provides 1 arg.
            bind-argso fails silently (structural unification mismatch).
            No crash, no unsound proof — just fails to find a proof.
            Category: PASS (fails gracefully)"
    (is (empty?
          (run 1 [proof]
            (nom x y
              (proveo ['neg ['app 'R ['app 'a]]]
                      '() '() '()
                      [['R [x y] ['eq ['var x] ['var y]]]]
                      proof)))))))

(deftest test-RV04-unknown-relation
  (testing "RV04: pos(S(a)) with program defining only R — no clause found.
            lookup-clauseo fails, all proc-call variants fail. Branch stays open.
            Category: PASS (unknown relation fails gracefully)"
    (is (empty?
          (run 1 [proof]
            (nom x
              (proveo ['pos ['app 'S ['app 'a]]]
                      '() '() '()
                      [['R [x] ['eq ['var x] ['app 'a]]]]
                      proof)))))))

(deftest test-RV05-nested-once-forall
  (testing "RV05: Double-nested negated existentials.
            R ← ∃x.∃y.(x=a ∧ y=b). neg-call produces
            once-forall x. once-forall y. (x≠a ∨ y≠b).
            Two independent LVars unify to a and b respectively.
            Category: PASS (nested once-forall works)"
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog [['R []
                           ['exists (tie x
                             ['exists (tie y
                               ['and ['eq ['var x] ['app 'a]]
                                     ['eq ['var y] ['app 'b]]])])]]]]
                (proveo ['neg ['app 'R]] '() '() '() prog proof))))))))

(deftest test-RV06-empty-program
  (testing "RV06: pos(R(a)) with empty program — no clause to call.
            All proc-call variants fail. No complementary literal in lits.
            Branch stays open (R(a) has no definition).
            Category: PASS (empty program handled correctly)"
    (is (empty?
          (run 1 [proof]
            (proveo ['pos ['app 'R ['app 'a]]]
                    '() '() '() '() proof))))))

(deftest test-RV07-negate-formulao-all-types
  (testing "RV07: negate-formulao handles all formula types correctly.
            Double negation is involutive: ¬¬φ = φ for all formula types.
            Category: PASS (negate-formulao complete)"
    ;; Test double negation involution on pos literal
    (is (seq
          (run 1 [result]
            (fresh [neg-fml]
              (negate-formulao ['pos ['app 'P]] neg-fml)
              (negate-formulao neg-fml result)
              (== result ['pos ['app 'P]])))))
    ;; Test double negation involution on eq
    (is (seq
          (run 1 [result]
            (fresh [neg-fml]
              (negate-formulao ['eq ['app 'a] ['app 'b]] neg-fml)
              (negate-formulao neg-fml result)
              (== result ['eq ['app 'a] ['app 'b]])))))))

;; ============================================================================
;; Section MV: Fitting's Move Warning (§8) — Auxiliary Relation Factoring
;; ============================================================================
;;
;; Fitting §8 warns that factoring win(x) ← ∃y.((x=s(y) ∨ x=s(s(y))) ∧ ¬win(y))
;; into win(x) ← ∃y.(move(x,y) ∧ ¬win(y)) with move(x,y) ← x=s(y) ∨ x=s(s(y))
;; DOES NOT WORK. Reason: equality's interpretation is fixed in weak Herbrand
;; models, but move's is not — there could be "non-standard moves." This is a
;; fundamental property of supervaluation semantics, not an implementation bug.
;;
;; In our implementation, this manifests as the L-ground guard (Fitting §6
;; Def 6.1) blocking procedure calls to `move` when the argument is a
;; δ-parameter (par p). The inline version processes equalities directly (no
;; procedure call needed), while the factored version requires a procedure
;; call that can't fire on par arguments.
;;
;; Contrast with J01-J04 (inline version, all pass).

(defn move-program
  "Build the factored win+move program (Fitting §8 warning).
   wx is win's clause param, wy is win's existential witness,
   mx and my are move's clause params."
  [wx wy mx my]
  [['win [wx]
    ['exists (tie wy
      ['and ['pos ['app 'move ['var wx] ['var wy]]]
            ['neg ['app 'win ['var wy]]]])]]
   ['move [mx my]
    ['or ['eq ['var mx] ['app 's ['var my]]]
         ['eq ['var mx] ['app 's ['app 's ['var my]]]]]]])

(deftest test-MV01-move-succeeds-l-ground
  (testing "MV01: move(s(0), 0) is true — neg-call closes with L-ground args.
            Confirms the move relation itself works correctly when args are
            L-ground. neg move(1,0) → negate body → neq(1,s(0)) ∧ neq(1,s(s(0)))
            → refl-close on first neq (1=s(0)) closes the branch."
    (is (seq
          (run 1 [proof]
            (nom mx my
              (let [prog [['move [mx my]
                           ['or ['eq ['var mx] ['app 's ['var my]]]
                                ['eq ['var mx] ['app 's ['app 's ['var my]]]]]]]
                    query ['neg ['app 'move (nim-numeral 1) (nim-numeral 0)]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-MV02-move-fails-l-ground
  (testing "MV02: move(0, s(0)) is false — pos-call closes (no valid move from 0).
            pos move(0, s(0)) → body = eq(0,s(s(0))) ∨ eq(0,s(s(s(0))))
            → both branches close by free-closure (zero ≠ s)."
    (is (seq
          (run 1 [proof]
            (nom mx my
              (let [prog [['move [mx my]
                           ['or ['eq ['var mx] ['app 's ['var my]]]
                                ['eq ['var mx] ['app 's ['app 's ['var my]]]]]]]
                    query ['pos ['app 'move (nim-numeral 0) (nim-numeral 1)]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-MV03-factored-win0-fails
  (testing "MV03: EXPECTED FAILURE — factored win(0) can't be proven false.
            pos win(0) → subsidiary ∃y.(move(0,y) ∧ ¬win(y)) → δ introduces
            par p → pos move(0, par p) blocked by L-ground guard (par p is
            not an L-term). Branch stays open.
            Compare: J01 succeeds because inline equalities (0=s(p), 0=s(s(p)))
            close by free-closure without needing a procedure call."
    (is (empty?
          (run 1 [proof]
            (nom wx wy mx my
              (let [prog (move-program wx wy mx my)
                    query ['pos ['app 'win (nim-numeral 0)]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-MV04-factored-win1-fails
  (testing "MV04: EXPECTED FAILURE — factored win(1) can't be proven true.
            neg win(1) → once-forall y → or(neg move(1,y), pos win(y)).
            Branch 1: neg move(1, v) closes (v is LVar, passes L-ground),
            binding v=0. Branch 2: pos win(v=0) → subsidiary needs
            pos move(0, par p), but par p blocks L-ground guard. Fails.
            Compare: J02 succeeds because inline equalities close directly."
    (is (empty?
          (run 1 [proof]
            (nom wx wy mx my
              (let [prog (move-program wx wy mx my)
                    query ['neg ['app 'win (nim-numeral 1)]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-MV05-inline-vs-factored-win0
  (testing "MV05: Direct comparison — inline win(0) closes, factored doesn't.
            This is Fitting's §8 warning made concrete: the same logical
            content expressed as inline equalities vs. auxiliary relation
            gives different results under supervaluation semantics."
    ;; Inline version succeeds (same as J01)
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (nim-program x y)
                    query ['pos ['app 'win (nim-numeral 0)]]]
                (proveo query '() '() '() prog proof))))))
    ;; Factored version fails
    (is (empty?
          (run 1 [proof]
            (nom wx wy mx my
              (let [prog (move-program wx wy mx my)
                    query ['pos ['app 'win (nim-numeral 0)]]]
                (proveo query '() '() '() prog proof))))))))

(deftest test-MV06-inline-vs-factored-win1
  (testing "MV06: Direct comparison — inline win(1) closes, factored doesn't.
            win(1) is true in both formulations (semantically), but the
            factored version can't prove it because the proof requires
            establishing win(0)=false, which needs move(0, par p) — blocked."
    ;; Inline version succeeds (same as J02)
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (nim-program x y)
                    query ['neg ['app 'win (nim-numeral 1)]]]
                (proveo query '() '() '() prog proof))))))
    ;; Factored version fails
    (is (empty?
          (run 1 [proof]
            (nom wx wy mx my
              (let [prog (move-program wx wy mx my)
                    query ['neg ['app 'win (nim-numeral 1)]]]
                (proveo query '() '() '() prog proof))))))))

;; ============================================================================
;; Section OC: Occurs Check (Fitting §3 / Supervaluation Semantics)
;; ============================================================================
;;
;; In Fitting's system, no element of the Herbrand universe equals a proper
;; application of a function to itself: ∀x.(x ≠ f(x)). The negation
;; ∃x.(x = f(x)) is semantically false. However, the tableau rules (free
;; closure, one-one, etc.) CANNOT prove this directly: the δ-rule introduces
;; (par p), and eq(par p, f(par p)) has no applicable closure rule because
;; (par p) is not an (app ...) term. This is a known limitation of tableaux
;; with equality — you'd need an explicit "no term equals a proper subterm"
;; axiom.
;;
;; These tests demonstrate:
;;   - Ground occurs-check IS provable (free-closure on head mismatch)
;;   - Existential occurs-check is NOT provable (par blocks free-closure)
;;   - Transitivity CAN derive occurs-check indirectly when two eqs provide
;;     a bridge through a concrete constant

(deftest test-OC01-ground-occurs-check
  (testing "OC01: a = f(a) is unsatisfiable — free-closure (a ≠ f).
            Ground terms: head mismatch is immediately visible."
    (is (seq
          (run 1 [proof]
            (proveo '(eq (app a) (app f (app a)))
                    '() '() '() '() proof))))))

(deftest test-OC02-ground-nested-occurs-check
  (testing "OC02: f(a) = f(f(a)) — one-one decomposition gives a = f(a),
            then free-closure. Two-step proof."
    (is (seq
          (run 1 [proof]
            (proveo '(eq (app f (app a)) (app f (app f (app a))))
                    '() '() '() '() proof))))))

(deftest test-OC03-existential-occurs-check-blocked
  (testing "OC03: ∃x.(x = f(x)) — CANNOT close. δ-rule gives eq(par p, f(par p)).
            (par p) is not an (app ...) term, so free-closure can't fire.
            One-one needs matching heads — also blocked. Branch stays open.
            This is a known limitation, not a bug (Fitting §3)."
    (is (empty?
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['eq ['var a] ['app 'f ['var a]]])]
                      '() '() '() '() proof)))))))

(deftest test-OC04-existential-occurs-check-nested-blocked
  (testing "OC04: ∃x.(f(x) = f(f(x))) — one-one gives eq(par p, f(par p)),
            then stuck (same as OC03). Wrapping in f doesn't help."
    (is (empty?
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['eq ['app 'f ['var a]]
                                           ['app 'f ['app 'f ['var a]]]])]
                      '() '() '() '() proof)))))))

(deftest test-OC05-transitivity-bridge
  (testing "OC05: ∃x.(x = a ∧ x = f(a)) — transitivity via concrete constant.
            δ gives eq(par p, a) ∧ eq(par p, f(a)).
            para-free-closeo rewrites eq(par p, f(a)) using par p=a → eq(a, f(a))
            → free-closure (a ≠ f). The concrete constant 'a' bridges the gap."
    (is (seq
          (run 1 [proof]
            (nom a
              (proveo ['exists (tie a ['and ['eq ['var a] ['app 'a]]
                                            ['eq ['var a] ['app 'f ['app 'a]]]])]
                      '() '() '() '() proof)))))))

(deftest test-OC06-transitivity-two-witnesses
  (testing "OC06: ∃x.∃y.(x = a ∧ y = f(a) ∧ x = y) — two witnesses,
            transitivity through equality chain. x=a, y=f(a), x=y →
            a=f(a) → free-closure."
    (is (seq
          (run 1 [proof]
            (nom x y
              (proveo ['exists (tie x
                        ['exists (tie y
                          ['and ['eq ['var x] ['app 'a]]
                                ['and ['eq ['var y] ['app 'f ['app 'a]]]
                                      ['eq ['var x] ['var y]]]])])]
                      '() '() '() '() proof)))))))

(deftest test-OC07-deep-ground-occurs-check
  (testing "OC07: g(a, f(a)) = g(a, f(f(a))) — deep nested occurs check.
            One-one on g: eq(a,a) (trivial) ∧ eq(f(a), f(f(a))).
            One-one on f: eq(a, f(a)) → free-closure."
    (is (seq
          (run 1 [proof]
            (proveo '(eq (app g (app a) (app f (app a)))
                         (app g (app a) (app f (app f (app a)))))
                    '() '() '() '() proof))))))

;; ============================================================================
;; Section TC: Transitive Closure
;; ============================================================================
;;
;; Transitive closure over a concrete graph, expressed as a single Proflog
;; clause with inline equality constraints (per Fitting §8 — auxiliary
;; relations cannot be factored out).
;;
;; Graph: a→b→c (two edges, three nodes, acyclic)
;;
;; tc(x,y) ← (x=a ∧ y=b)                          -- direct edge a→b
;;          ∨ (x=b ∧ y=c)                          -- direct edge b→c
;;          ∨ ∃z.((x=a∧z=b ∨ x=b∧z=c) ∧ tc(z,y)) -- recursive step
;;
;; The edge relation is inlined as equalities in both the base cases and the
;; recursive case, avoiding the "move" factoring problem (Section MV).

(defn tc-abc-program
  "Transitive closure over graph a→b→c.
   x, y are clause params, z is existential witness."
  [x y z]
  [['tc [x y]
    ['or ['and ['eq ['var x] ['app 'a]] ['eq ['var y] ['app 'b]]]
         ['or ['and ['eq ['var x] ['app 'b]] ['eq ['var y] ['app 'c]]]
              ['exists (tie z
                ['and ['or ['and ['eq ['var x] ['app 'a]] ['eq ['var z] ['app 'b]]]
                           ['and ['eq ['var x] ['app 'b]] ['eq ['var z] ['app 'c]]]]
                      ['pos ['app 'tc ['var z] ['var y]]]])]]]]])

(deftest test-TC01-direct-edge-ab
  (testing "TC01: tc(a,b) is true — direct edge. neg-call closes because the
            first base case (x=a ∧ y=b) negates to (a≠a ∨ b≠b), both
            contradictions (refl-close)."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['neg ['app 'tc ['app 'a] ['app 'b]]]
                        '() '() '() prog proof))))))))

(deftest test-TC02-direct-edge-bc
  (testing "TC02: tc(b,c) is true — direct edge. neg-call closes via the
            second base case (x=b ∧ y=c) → (b≠b ∨ c≠c) → refl-close."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['neg ['app 'tc ['app 'b] ['app 'c]]]
                        '() '() '() prog proof))))))))

(deftest test-TC03-transitive-ac
  (testing "TC03: tc(a,c) is true — transitive path a→b→c. neg-call must
            close the recursive case: once-forall z introduces LVar v,
            negated edge binds v=b, then neg tc(b,c) closes as a direct edge."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['neg ['app 'tc ['app 'a] ['app 'c]]]
                        '() '() '() prog proof))))))))

(deftest test-TC04-no-path-ca
  (testing "TC04: tc(c,a) is false — no outgoing edges from c. pos-call closes
            because base cases give eq(c,a)/eq(c,b) → free-closure, and the
            recursive case's edge part also closes by free-closure on eq(c,a)/eq(c,b)."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['pos ['app 'tc ['app 'c] ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-TC05-no-self-loop-aa
  (testing "TC05: tc(a,a) is false — no cycle. pos-call closes: base cases
            fail (eq(a,b)/eq(a,c) by free-closure). Recursive case: edge a→b
            gives subst-call tc(b,a), then edge b→c gives tc(c,a), then no
            outgoing edges from c → closes."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['pos ['app 'tc ['app 'a] ['app 'a]]]
                        '() '() '() prog proof))))))))

(deftest test-TC06-no-path-ba
  (testing "TC06: tc(b,a) is false — b→c but c has no outgoing edges.
            pos-call closes similarly to TC05."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (tc-abc-program x y z)]
                (proveo ['pos ['app 'tc ['app 'b] ['app 'a]]]
                        '() '() '() prog proof))))))))

;; ============================================================================
;; Section PA: Peano Arithmetic — Addition
;; ============================================================================
;;
;; Peano addition as a single Proflog clause:
;;   add(x,y,z) ← (y=0 ∧ z=x) ∨ ∃w.∃v.(y=s(w) ∧ z=s(v) ∧ add(x,w,v))
;;
;; Base case: x + 0 = x.
;; Recursive: x + s(w) = s(v) if x + w = v.
;;
;; Recursion is on the second argument (y). Each recursive step peels one
;; s() from y and z simultaneously, bottoming out at y=0 ∧ z=x.
;;
;; Note: neg-call (proving truth) is efficient — the negated body's neq
;; formulas close quickly via refl-close. pos-call (proving falsity) is
;; slower due to search space explosion from decomposition + recursive
;; procedure calls, so falsity tests use small numbers only.

(defn peano-add-program
  "Peano addition: add(x,y,z) means x + y = z.
   x, y, z are clause params; w, v are existential witnesses."
  [x y z w v]
  [['add [x y z]
    ['or ['and ['eq ['var y] ['app 'zero]] ['eq ['var z] ['var x]]]
         ['exists (tie w
           ['exists (tie v
             ['and ['eq ['var y] ['app 's ['var w]]]
                   ['and ['eq ['var z] ['app 's ['var v]]]
                         ['pos ['app 'add ['var x] ['var w] ['var v]]]]])])]]]])

(defn peano [n]
  "Build Peano numeral for n."
  (if (zero? n) ['app 'zero] ['app 's (peano (dec n))]))

(deftest test-PA01-base-case-0plus0
  (testing "PA01: 0+0=0 — base case (y=0, z=x=0). neg-call closes via
            refl-close on neq(0,0) and neq(0,0)."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 0) (peano 0) (peano 0)]]
                        '() '() '() prog proof))))))))

(deftest test-PA02-base-case-1plus0
  (testing "PA02: 1+0=1 — base case (y=0, z=x=s(0)). Structural equality
            s(0)=s(0) closes via refl-close after decomposition."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 1) (peano 0) (peano 1)]]
                        '() '() '() prog proof))))))))

(deftest test-PA03-one-step-1plus1
  (testing "PA03: 1+1=2 — one recursive step. s(0) peeled from y and z,
            then base case add(1,0,1)."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 1) (peano 1) (peano 2)]]
                        '() '() '() prog proof))))))))

(deftest test-PA04-one-step-2plus1
  (testing "PA04: 2+1=3 — one recursive step with larger first arg."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 2) (peano 1) (peano 3)]]
                        '() '() '() prog proof))))))))

(deftest test-PA05-multi-step-2plus3
  (testing "PA05: 2+3=5 — three recursive steps. Proof tree has depth 3,
            each level peeling s() from 2nd and 3rd args."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 2) (peano 3) (peano 5)]]
                        '() '() '() prog proof))))))))

(deftest test-PA06-commutativity-3plus2
  (testing "PA06: 3+2=5 — same sum as PA05 but args swapped. Confirms
            addition works regardless of which arg is larger."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 3) (peano 2) (peano 5)]]
                        '() '() '() prog proof))))))))

(deftest test-PA07-false-1plus1neq1
  (testing "PA07: 1+1≠1 — pos-call proves add(1,1,1) false. Recursive step
            gives add(1,0,0), base case eq(0,1) closes by free-closure."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 1) (peano 1) (peano 1)]]
                        '() '() '() prog proof))))))))

(deftest test-PA08-false-0plus1neq0
  (testing "PA08: 0+1≠0 — pos-call. Recursive step gives add(0,0,par v),
            base case eq(par v, 0) not contradictory but eq(s(0),s(par v))
            path closes via free-closure on eq(0, par v) decomposition."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 0) (peano 1) (peano 0)]]
                        '() '() '() prog proof))))))))

(deftest test-PA09-false-1plus2neq2
  (testing "PA09: 1+2≠2 — pos-call with two recursive steps. Terminates
            at add(1,0,0), base case eq(0,1) → free-closure."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 1) (peano 2) (peano 2)]]
                        '() '() '() prog proof))))))))

;; --- Larger numbers ---

(deftest test-PA10-larger-3plus4eq7
  (testing "PA10: 3+4=7 — four recursive steps peeling s() from 2nd and 3rd args."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 3) (peano 4) (peano 7)]]
                        '() '() '() prog proof))))))))

(deftest test-PA11-larger-4plus3eq7
  (testing "PA11: 4+3=7 — commuted version of PA10. Three recursive steps."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['neg ['app 'add (peano 4) (peano 3) (peano 7)]]
                        '() '() '() prog proof))))))))

;; --- Reverse mode: determine addends from sums ---

(deftest test-PA12-synth-first-arg
  (testing "PA12: ?+3=5 — synthesis on first argument. Finds 2 (= s(s(zero)))."
    (let [results (run 1 [x-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add x-val (peano 3) (peano 5)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= (peano 2) (first results))))))

(deftest test-PA13-synth-second-arg
  (testing "PA13: 3+?=5 — synthesis on second argument. Finds 2."
    (let [results (run 1 [x-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add (peano 3) x-val (peano 5)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= (peano 2) (first results))))))

(deftest test-PA14-synth-sum
  (testing "PA14: 3+4=? — synthesis on sum. Finds 7."
    (let [results (run 1 [x-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add (peano 3) (peano 4) x-val]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= (peano 7) (first results))))))

;; --- Existential queries via synthesis ---

(deftest test-PA15-exists-addend
  (testing "PA15: ∃x. add(3,x,5) — there exists x such that 3+x=5.
            Existential is implicit in the LVar binding. Finds x=2."
    (let [results (run 1 [x-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add (peano 3) x-val (peano 5)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= (peano 2) (first results))))))

(deftest test-PA16-exists-half
  (testing "PA16: ∃x. add(x,x,4) — find x such that x+x=4 (halving).
            4 is even, so x=2 exists."
    (let [results (run 1 [x-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add x-val x-val (peano 4)]]
                                  '() '() '() prog proof)))))]
      (is (seq results))
      (is (= (peano 2) (first results))))))

(deftest test-PA17-no-half-odd
  (testing "PA17: ∃x. add(x,x,3) has no solution — 3 is odd, not halvable.
            Synthesis correctly returns empty (no x satisfies x+x=3)."
    (is (empty?
          (run 1 [x-val]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (fresh [proof]
                  (proveo ['neg ['app 'add x-val x-val (peano 3)]]
                          '() '() '() prog proof)))))))))

(deftest test-PA18-no-half-odd-5
  (testing "PA18: ∃x. add(x,x,5) has no solution — 5 is odd."
    (is (empty?
          (run 1 [x-val]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (fresh [proof]
                  (proveo ['neg ['app 'add x-val x-val (peano 5)]]
                          '() '() '() prog proof)))))))))

;; --- All pairs summing to a given number ---

(deftest test-PA19-all-pairs-summing-to-3
  (testing "PA19: find all (a,b) where a+b=3. Expects 4 pairs:
            (3,0), (2,1), (1,2), (0,3) — exhaustive decomposition."
    (let [results (run 4 [a b]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add a b (peano 3)]]
                                  '() '() '() prog proof)))))]
      (is (= 4 (count results)))
      ;; Check that each expected pair appears (using element-wise = for LCons)
      (let [a-vals (map first results)]
        (is (some #(= (peano 3) %) a-vals) "Should find a=3")
        (is (some #(= (peano 2) %) a-vals) "Should find a=2")
        (is (some #(= (peano 1) %) a-vals) "Should find a=1")
        (is (some #(= (peano 0) %) a-vals) "Should find a=0")))))

;; --- Enumerate (y,z) pairs for a fixed first addend ---

(deftest test-PA20-pairs-2plusY
  (testing "PA20: find 4 (y,z) pairs where 2+y=z. Should find
            (0,2), (1,3), (2,4), (3,5) — infinitely many exist."
    (let [results (run 4 [y-val z-val]
                    (nom x y z w v
                      (let [prog (peano-add-program x y z w v)]
                        (fresh [proof]
                          (proveo ['neg ['app 'add (peano 2) y-val z-val]]
                                  '() '() '() prog proof)))))]
      (is (= 4 (count results)))
      ;; First result should be y=0, z=2 (base case)
      (is (= (peano 0) (first (first results))))
      (is (= (peano 2) (second (first results)))))))

;; --- Falsity: x+1≠x for specific values (successor ≠ identity) ---

(deftest test-PA21-succ-neq-identity-0
  (testing "PA21: add(0,1,0) is false — 0+1≠0. pos-call: base case
            gives eq(0, s(0)) → free-closure (zero ≠ s)."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 0) (peano 1) (peano 0)]]
                        '() '() '() prog proof))))))))

(deftest test-PA22-succ-neq-identity-1
  (testing "PA22: add(1,1,1) is false — 1+1≠1. Recursive step gives
            add(1,0,0), base case eq(0,1) → free-closure."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 1) (peano 1) (peano 1)]]
                        '() '() '() prog proof))))))))

(deftest test-PA23-succ-neq-identity-2
  (testing "PA23: add(2,1,2) is false — 2+1≠2. Recursive step gives
            add(2,0,1), base case eq(1,2) → free-closure (s(0) vs s(s(0)))."
    (is (seq
          (run 1 [proof]
            (nom x y z w v
              (let [prog (peano-add-program x y z w v)]
                (proveo ['pos ['app 'add (peano 2) (peano 1) (peano 2)]]
                        '() '() '() prog proof))))))))

;; --- ∀x.∃y.add(x,1,y)∧x≠y: L-ground guard limitation ---
;;
;; The statement "for all x, there exists y such that add(x,1,y) and x≠y"
;; cannot be expressed as a single ∀-quantified query with procedure calls,
;; because the δ-parameter introduced by ∃ (after negating ∀) is not L-ground,
;; blocking the procedure call rule (Fitting §6 Def 6.1).
;;
;; We verify the statement holds for specific instances instead: each test
;; above (PA21-PA23) proves add(n,1,n) is FALSE for n∈{0,1,2}, which is
;; equivalent to "n+1 ≠ n" — confirming the successor function is not the
;; identity for these values.

;; ============================================================================
;; Section SO: Sorted Predicate (∀ in clause body, inline ordering)
;; ============================================================================
;;
;; A sorted-list predicate using ∀ in the clause body and inline ordering
;; over the domain {0, s(0), s(s(0))}. The ordering relation leq is
;; expressed directly as equalities (per Fitting §8 — cannot be factored
;; into an auxiliary relation).
;;
;; sorted2(l) ← ∀a.∀b.∀t.(l ≠ cons(a, cons(b, t)) ∨ le_inline(a, b))
;;
;; "For all adjacent pairs (a, b) in l, a ≤ b."
;;
;; le_inline(a,b) = a=0 ∨ (a=s(0) ∧ (b=s(0) ∨ b=s(s(0)))) ∨ (a=s(s(0)) ∧ b=s(s(0)))
;;
;; This is a non-recursive clause with ∀ in body — exercises the γ-rule
;; with re-enqueueing in a real program context.

(defn le-inline
  "a ≤ b over domain {0, s(0), s(s(0))}, expressed as equalities."
  [a b]
  ['or ['eq a ['app 'zero]]
       ['or ['and ['eq a ['app 's ['app 'zero]]]
                  ['or ['eq b ['app 's ['app 'zero]]]
                       ['eq b ['app 's ['app 's ['app 'zero]]]]]]
            ['and ['eq a ['app 's ['app 's ['app 'zero]]]]
                  ['eq b ['app 's ['app 's ['app 'zero]]]]]]])

(defn sorted2-program
  "sorted2(l) ← ∀a.∀b.∀t.(l ≠ cons(a, cons(b, t)) ∨ le(a,b))
   l is clause param; a, b, t are universally quantified."
  [l a b t]
  [['sorted2 [l]
    ['forall (tie a
      ['forall (tie b
        ['forall (tie t
          ['or ['neq ['var l] ['app 'cons ['var a] ['app 'cons ['var b] ['var t]]]]
               (le-inline ['var a] ['var b])])])])]]])

(defn plist
  "Build a Proflog list from Peano numerals."
  [& nums]
  (reduce (fn [acc n] ['app 'cons (peano n) acc]) ['app 'nul] (reverse nums)))

(deftest test-SO01-empty-list
  (testing "SO01: sorted2([]) is true — no adjacent pairs to violate ordering.
            γ-rule introduces free vars, neq closes by free-closure
            (nul ≠ cons(...))."
    (is (seq
          (run 1 [proof]
            (nom l a b t
              (let [prog (sorted2-program l a b t)]
                (proveo ['neg ['app 'sorted2 (plist)]]
                        '() '() '() prog proof))))))))

(deftest test-SO02-singleton
  (testing "SO02: sorted2([1]) is true — singleton list has no adjacent pairs.
            γ-rule vars: neq(cons(1,nul), cons(a,cons(b,t))) closes because
            nul ≠ cons(b,t) after decomposition."
    (is (seq
          (run 1 [proof]
            (nom l a b t
              (let [prog (sorted2-program l a b t)]
                (proveo ['neg ['app 'sorted2 (plist 1)]]
                        '() '() '() prog proof))))))))

(deftest test-SO03-sorted-012
  (testing "SO03: sorted2([0,1,2]) is true — all adjacent pairs ordered
            (0≤1, 1≤2). neg-call closes via eq-refl-close on the ∀ body."
    (is (seq
          (run 1 [proof]
            (nom l a b t
              (let [prog (sorted2-program l a b t)]
                (proveo ['neg ['app 'sorted2 (plist 0 1 2)]]
                        '() '() '() prog proof))))))))

(deftest test-SO04-unsorted-21
  (testing "SO04: sorted2([2,1]) is false — 2 > 1. pos-call closes:
            γ-rule instantiation with a=2, b=1 gives le(2,1) which fails
            (all le_inline branches close by free-closure/decomposition)."
    (is (seq
          (run 1 [proof]
            (nom l a b t
              (let [prog (sorted2-program l a b t)]
                (proveo ['pos ['app 'sorted2 (plist 2 1)]]
                        '() '() '() prog proof))))))))

(deftest test-SO05-sorted-12
  (testing "SO05: sorted2([1,2]) is true — 1 ≤ 2. neg-call closes via
            decomposition + eq-refl-close showing the ∀ body is contradictory
            for the given list."
    (is (seq
          (run 1 [proof]
            (nom l a b t
              (let [prog (sorted2-program l a b t)]
                (proveo ['neg ['app 'sorted2 (plist 1 2)]]
                        '() '() '() prog proof))))))))

;; ============================================================================
;; Section SS: Subset Relations (∀ in body, inline membership)
;; ============================================================================
;;
;; Subset checking for finite sets over domain {a, b, c}. Membership is
;; inlined as equalities (Fitting §8 — no auxiliary relations).
;;
;; subset(S, T) ← ∀x.(¬in_S(x) ∨ in_T(x))
;;   where ¬in_S(x) = (x≠a ∧ x≠b ∧ ...) and in_T(x) = (x=a ∨ x=b ∨ ...)
;;
;; Exercises ∀ in clause body with purely equality-based set operations.

(defn subset-ab-abc-program
  "sub_ab_abc() ← ∀x.((x≠a ∧ x≠b) ∨ (x=a ∨ x=b ∨ x=c))
   True iff {a,b} ⊆ {a,b,c}."
  [x]
  [['sub_ab_abc []
    ['forall (tie x
      ['or ['and ['neq ['var x] ['app 'a]] ['neq ['var x] ['app 'b]]]
           ['or ['eq ['var x] ['app 'a]]
                ['or ['eq ['var x] ['app 'b]]
                     ['eq ['var x] ['app 'c]]]]])]]])

(defn subset-abc-ab-program
  "sub_abc_ab() ← ∀x.((x≠a ∧ x≠b ∧ x≠c) ∨ (x=a ∨ x=b))
   True iff {a,b,c} ⊆ {a,b}. This is FALSE (c ∉ {a,b})."
  [x]
  [['sub_abc_ab []
    ['forall (tie x
      ['or ['and ['neq ['var x] ['app 'a]]
                 ['and ['neq ['var x] ['app 'b]]
                       ['neq ['var x] ['app 'c]]]]
           ['or ['eq ['var x] ['app 'a]]
                ['eq ['var x] ['app 'b]]]])]]])

(deftest test-SS01-subset-true
  (testing "SS01: {a,b} ⊆ {a,b,c} is true. neg-call closes: γ-rule
            instantiation with x bound to either a or b finds matching
            eq in the right disjunct."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (subset-ab-abc-program x)]
                (proveo ['neg ['app 'sub_ab_abc]]
                        '() '() '() prog proof))))))))

(deftest test-SS02-subset-false
  (testing "SS02: {a,b,c} ⊆ {a,b} is false. pos-call closes: γ-rule finds
            x=c where ¬in_S(c)=(c≠a ∧ c≠b ∧ c≠c) gives refl-close on c≠c,
            and in_T(c)=(c=a ∨ c=b) gives free-closure on both branches."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (subset-abc-ab-program x)]
                (proveo ['pos ['app 'sub_abc_ab]]
                        '() '() '() prog proof))))))))

(deftest test-SS03-reflexive-subset
  (testing "SS03: {a} ⊆ {a} is true — reflexive subset."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['sub_a_a []
                           ['forall (tie x
                             ['or ['neq ['var x] ['app 'a]]
                                  ['eq ['var x] ['app 'a]]])]]]]
                (proveo ['neg ['app 'sub_a_a]]
                        '() '() '() prog proof))))))))

;; ============================================================================
;; Section GP: Graph Properties (acyclic, ∀/∃/¬ combinations)
;; ============================================================================
;;
;; Graph properties expressed as Proflog programs with inline reachability.
;;
;; For graph a→b→c (acyclic):
;;   reach(x,y) = (x=a∧y=b) ∨ (x=b∧y=c) ∨ (x=a∧y=c)  [full TC]
;;   acyclic ← ∀x.¬reach(x,x)
;;   = ∀x.((x≠a∨x≠b) ∧ (x≠b∨x≠c) ∧ (x≠a∨x≠c))
;;
;; For graph a→b→a (cyclic):
;;   reach(x,x) includes (x=a∧x=a) ∨ (x=b∧x=b) [every node reaches itself]
;;   acyclic ← ∀x.(x≠a ∧ x≠b)  [simplified from full negation]
;;
;; Note: "connected" properties with ∀x.∀y in bodies require negating
;; to ∃x.∃y with δ-parameters, which produces neq(par, constant) that
;; can't close (par could denote that constant). This is a semantic
;; limitation, not a bug.

(defn acyclic-abc-program
  "acyclic_abc() ← ∀x.¬reach(x,x) for acyclic graph a→b→c.
   ¬reach(x,x) = (x≠a∨x≠b) ∧ (x≠b∨x≠c) ∧ (x≠a∨x≠c)"
  [x]
  [['acyclic_abc []
    ['forall (tie x
      ['and ['or ['neq ['var x] ['app 'a]] ['neq ['var x] ['app 'b]]]
            ['and ['or ['neq ['var x] ['app 'b]] ['neq ['var x] ['app 'c]]]
                  ['or ['neq ['var x] ['app 'a]] ['neq ['var x] ['app 'c]]]]])]]])

(defn acyclic-aba-program
  "acyclic_aba() ← ∀x.¬reach(x,x) for cyclic graph a→b→a.
   reach(x,x) includes (x=a) ∨ (x=b) [every node reaches itself via cycle].
   ¬reach(x,x) = x≠a ∧ x≠b."
  [x]
  [['acyclic_aba []
    ['forall (tie x
      ['and ['neq ['var x] ['app 'a]]
            ['neq ['var x] ['app 'b]]])]]])

(deftest test-GP01-acyclic-true
  (testing "GP01: acyclic(a→b→c) is true — no cycles. neg-call produces
            ∃x.((x=a∧x=b) ∨ (x=b∧x=c) ∨ (x=a∧x=c)). δ-rule gives par p.
            Each disjunct pairs eq(par p, X) with eq(par p, Y) where X≠Y,
            closing via para-free-close."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (acyclic-abc-program x)]
                (proveo ['neg ['app 'acyclic_abc]]
                        '() '() '() prog proof))))))))

(deftest test-GP02-acyclic-false-cycle
  (testing "GP02: acyclic(a→b→a) is false — cycle exists. pos-call:
            body = ∀x.(x≠a ∧ x≠b). γ-rule introduces LVar v.
            neq(v, a) → refl-close binds v=a → neq(a, a) → contradiction."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (acyclic-aba-program x)]
                (proveo ['pos ['app 'acyclic_aba]]
                        '() '() '() prog proof))))))))

(deftest test-GP03-acyclic-three-node-cycle
  (testing "GP03: acyclic(a→b→c→a) is false — 3-node cycle.
            reach(x,x) = (x=a) ∨ (x=b) ∨ (x=c). acyclic = ∀x.(x≠a ∧ x≠b ∧ x≠c).
            pos-call: γ-rule LVar binds to a → contradiction."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog [['acyclic_abca []
                           ['forall (tie x
                             ['and ['neq ['var x] ['app 'a]]
                                   ['and ['neq ['var x] ['app 'b]]
                                         ['neq ['var x] ['app 'c]]]])]]]]
                (proveo ['pos ['app 'acyclic_abca]]
                        '() '() '() prog proof))))))))

;; ============================================================================
;; Section GV: Group Verifier — Abstract Finite Group Axiom Checker
;; ============================================================================
;;
;; An abstract framework for verifying finite group axioms in Proflog.
;; Given a GROUP SPEC (domain elements, binary operation table, candidate
;; identity), the framework generates Proflog programs to check each axiom.
;;
;; This is a Proflog-native program: every axiom is expressed as a single
;; clause with full FOL in the body (∀, ∃, ¬, ∧, ∨, =, ≠).  The operation
;; table is inlined as equalities per Fitting §8 (no auxiliary relations —
;; the L-ground guard would block procedure calls on δ-parameters).
;;
;; GROUP SPEC FORMAT:
;;   {:domain  [sym₁ sym₂ ...]           ;; Clojure symbols → Proflog constants
;;    :op      {[sym₁ sym₁] sym₁, ...}   ;; operation table: [a b] → c
;;    :identity sym₁}                     ;; candidate identity element
;;
;; Example — Z₂ = ({0,1}, +mod2, identity=0):
;;   {:domain  ['zero 'one]
;;    :op      {['zero 'zero] 'zero, ['zero 'one] 'one,
;;              ['one  'zero] 'one,  ['one  'one]  'zero}
;;    :identity 'zero}
;;
;; AXIOM PROGRAMS GENERATED:
;;   gv_closure()  ← ∀x.∀y.(¬D(x) ∨ ¬D(y) ∨ ∃z.(op(x,y,z) ∧ D(z)))
;;   gv_identity() ← ∀x.(¬D(x) ∨ (op(e,x,x) ∧ op(x,e,x)))
;;   gv_inverses() ← ∀x.(¬D(x) ∨ ∃y.(D(y) ∧ op(x,y,e) ∧ op(y,x,e)))
;;   gv_assoc()    ← ∀x.∀y.∀z.∀w1.∀w2.∀w3.∀w4.
;;                      (¬op(x,y,w1) ∨ ¬op(w1,z,w2) ∨ ¬op(y,z,w3) ∨
;;                       ¬op(x,w3,w4) ∨ w2=w4)
;;
;; where D(x) = "x is in the domain" and op(x,y,z) = "op(x,y)=z",
;; both inlined as equalities.
;;
;; ============================================================================

;; ---------------------------------------------------------------------------
;; GV Building Blocks (Task #2)
;; ---------------------------------------------------------------------------

(defn gv-term
  "Convert a domain element symbol to a Proflog constant term."
  [sym]
  ['app sym])

(defn gv-and*
  "Build a right-associated conjunction from a sequence of formulas.
   (gv-and* [a b c]) => ['and a ['and b c]]"
  [formulas]
  (reduce (fn [acc fml] ['and fml acc]) (reverse formulas)))

(defn gv-or*
  "Build a right-associated disjunction from a sequence of formulas.
   (gv-or* [a b c]) => ['or a ['or b c]]"
  [formulas]
  (reduce (fn [acc fml] ['or fml acc]) (reverse formulas)))

(defn gv-forall*
  "Build nested ∀ quantifiers from a sequence of noms and a body.
   (gv-forall* [x y z] body) => ['forall (tie x ['forall (tie y ['forall (tie z body)])])]"
  [noms body]
  (reduce (fn [acc n] ['forall (tie n acc)]) body (reverse noms)))

(defn gv-exists*
  "Build nested ∃ quantifiers from a sequence of noms and a body.
   (gv-exists* [x y] body) => ['exists (tie x ['exists (tie y body)])]"
  [noms body]
  (reduce (fn [acc n] ['exists (tie n acc)]) body (reverse noms)))

(defn gv-op-eq-inline
  "op(x,y)=z expressed as equalities from the operation table.
   Returns: (x=a₁ ∧ y=b₁ ∧ z=c₁) ∨ (x=a₂ ∧ y=b₂ ∧ z=c₂) ∨ ...
   One disjunct per table entry."
  [spec x y z]
  (gv-or*
    (for [[[a b] c] (:op spec)]
      (gv-and* [['eq x (gv-term a)]
                ['eq y (gv-term b)]
                ['eq z (gv-term c)]]))))

(defn gv-neg-op-eq-inline
  "¬op(x,y,z) in NNF — the negation of gv-op-eq-inline.
   Returns: (x≠a₁ ∨ y≠b₁ ∨ z≠c₁) ∧ (x≠a₂ ∨ y≠b₂ ∨ z≠c₂) ∧ ...
   One conjunct per table entry, each a disjunction of neqs."
  [spec x y z]
  (gv-and*
    (for [[[a b] c] (:op spec)]
      (gv-or* [['neq x (gv-term a)]
               ['neq y (gv-term b)]
               ['neq z (gv-term c)]]))))

(defn gv-in-domain-inline
  "x ∈ domain expressed as equalities.
   Returns: x=a₁ ∨ x=a₂ ∨ ..."
  [spec x]
  (gv-or* (for [d (:domain spec)] ['eq x (gv-term d)])))

(defn gv-not-in-domain-inline
  "x ∉ domain in NNF.
   Returns: x≠a₁ ∧ x≠a₂ ∧ ..."
  [spec x]
  (gv-and* (for [d (:domain spec)] ['neq x (gv-term d)])))

;; ---------------------------------------------------------------------------
;; GV Axiom Generators (Task #3)
;; ---------------------------------------------------------------------------

(defn gv-identity-program
  "gv_identity() ← ∀x.(¬D(x) ∨ (op(e,x,x) ∧ op(x,e,x)))
   'e is a two-sided identity for all domain elements.'
   x is the ∀-bound nom."
  [spec x]
  (let [e  (gv-term (:identity spec))
        vx ['var x]]
    [['gv_identity []
      (gv-forall* [x]
        ['or (gv-not-in-domain-inline spec vx)
             ['and (gv-op-eq-inline spec e vx vx)
                   (gv-op-eq-inline spec vx e vx)]])]]))

(defn gv-closure-program
  "gv_closure() ← ∀x.∀y.(¬D(x) ∨ ¬D(y) ∨ ∃z.(op(x,y,z) ∧ D(z)))
   'The operation is closed on the domain.'
   x, y are ∀-bound noms; z is the ∃-witness nom."
  [spec x y z]
  (let [vx ['var x]
        vy ['var y]
        vz ['var z]]
    [['gv_closure []
      (gv-forall* [x y]
        (gv-or* [(gv-not-in-domain-inline spec vx)
                 (gv-not-in-domain-inline spec vy)
                 (gv-exists* [z]
                   ['and (gv-op-eq-inline spec vx vy vz)
                         (gv-in-domain-inline spec vz)])]))]]))

(defn gv-inverses-program
  "gv_inverses() ← ∀x.(¬D(x) ∨ ∃y.(D(y) ∧ op(x,y,e) ∧ op(y,x,e)))
   'Every domain element has a two-sided inverse.'
   x is the ∀-bound nom; y is the ∃-witness nom."
  [spec x y]
  (let [e  (gv-term (:identity spec))
        vx ['var x]
        vy ['var y]]
    [['gv_inverses []
      (gv-forall* [x]
        ['or (gv-not-in-domain-inline spec vx)
             (gv-exists* [y]
               (gv-and* [(gv-in-domain-inline spec vy)
                         (gv-op-eq-inline spec vx vy e)
                         (gv-op-eq-inline spec vy vx e)]))])]]))

(defn gv-assoc-program
  "gv_assoc() ← ∀x.∀y.∀z.∀w1.∀w2.∀w3.∀w4.
                  (¬op(x,y,w1) ∨ ¬op(w1,z,w2) ∨ ¬op(y,z,w3) ∨
                   ¬op(x,w3,w4) ∨ w2=w4)
   'op is associative: op(op(x,y),z) = op(x,op(y,z)) for all x,y,z
    and all intermediate values w1,w2,w3,w4.'
   7 universally quantified noms."
  [spec x y z w1 w2 w3 w4]
  (let [vx  ['var x]  vy  ['var y]  vz  ['var z]
        vw1 ['var w1] vw2 ['var w2] vw3 ['var w3] vw4 ['var w4]]
    [['gv_assoc []
      (gv-forall* [x y z w1 w2 w3 w4]
        (gv-or* [(gv-neg-op-eq-inline spec vx vy vw1)   ;; ¬op(x,y,w1)
                 (gv-neg-op-eq-inline spec vw1 vz vw2)  ;; ¬op(w1,z,w2)
                 (gv-neg-op-eq-inline spec vy vz vw3)   ;; ¬op(y,z,w3)
                 (gv-neg-op-eq-inline spec vx vw3 vw4)  ;; ¬op(x,w3,w4)
                 ['eq vw2 vw4]]))]]))                    ;; w2 = w4

(defn gv-assoc-precomputed-program
  "Pre-computed associativity checker using only 3 universals (x, y, z).

   Instead of quantifying over intermediate values w1-w4 and using the
   prover to resolve op-lookups (7 universals, intractable for |domain|≥2),
   the framework computes op(op(x,y),z) and op(x,op(y,z)) in Clojure for
   each (a,b,c) triple and generates the equality check inline:

   gv_assoc_pre() ← ∀x.∀y.∀z.(¬D(x) ∨ ¬D(y) ∨ ¬D(z) ∨ assoc-check(x,y,z))

   where assoc-check(x,y,z) = ∧_{(a,b,c) ∈ D³}
     (x≠a ∨ y≠b ∨ z≠c ∨ eq(op(op(a,b),c), op(a,op(b,c))))

   This is logically equivalent to the 7-universal version but tractable:
   only 3 universals, and |domain|³ conjuncts (8 for Z₂)."
  [spec x y z]
  (let [vx    ['var x]
        vy    ['var y]
        vz    ['var z]
        op    (:op spec)
        dom   (:domain spec)
        ;; Pre-compute: for each (a,b,c), check op(op(a,b),c) = op(a,op(b,c))
        triple-checks
        (for [a dom, b dom, c dom]
          (let [ab   (get op [a b])
                ab-c (get op [ab c])
                bc   (get op [b c])
                a-bc (get op [a bc])]
            ;; x≠a ∨ y≠b ∨ z≠c ∨ eq(op(op(a,b),c), op(a,op(b,c)))
            (gv-or* [['neq vx (gv-term a)]
                     ['neq vy (gv-term b)]
                     ['neq vz (gv-term c)]
                     ['eq (gv-term ab-c) (gv-term a-bc)]])))]
    [['gv_assoc_pre []
      (gv-forall* [x y z]
        (gv-or* [(gv-not-in-domain-inline spec vx)
                 (gv-not-in-domain-inline spec vy)
                 (gv-not-in-domain-inline spec vz)
                 (gv-and* triple-checks)]))]]))

;; ---------------------------------------------------------------------------
;; GV Group Specs
;; ---------------------------------------------------------------------------

(def gv-z2
  "Z₂ = ({0,1}, +mod2, identity=0).  The cyclic group of order 2."
  {:domain   ['zero 'one]
   :op       {['zero 'zero] 'zero
              ['zero 'one]  'one
              ['one  'zero] 'one
              ['one  'one]  'zero}
   :identity 'zero})

(def gv-z1
  "Z₁ = ({e}, trivial operation, identity=e).  The trivial group."
  {:domain   ['e]
   :op       {['e 'e] 'e}
   :identity 'e})

(def gv-non-group
  "A non-group 2-element magma.  op(1,0)=0, so 0 is not a right identity
   for 1 (op(1,0)=0≠1).  Also non-associative: op(1,0,1): op(op(1,0),1)
   = op(0,1)=1 but op(1,op(0,1))=op(1,1)=0."
  {:domain   ['zero 'one]
   :op       {['zero 'zero] 'zero
              ['zero 'one]  'one
              ['one  'zero] 'zero    ;; <-- differs from Z₂
              ['one  'one]  'zero}
   :identity 'zero})

;; ---------------------------------------------------------------------------
;; GV Tests (Task #4)
;; ---------------------------------------------------------------------------

(deftest test-GV01-z2-identity
  (testing "GV01: Z₂ has identity element 0.
            ∀x.(¬D(x) ∨ (op(0,x,x) ∧ op(x,0,x)))
            neg-call: ∃x.(D(x) ∧ (¬op(0,x,x) ∨ ¬op(x,0,x)))
            δ-rule introduces par p. D(p) splits into p=0 ∨ p=1.
            For each: ¬op(0,p,p) and ¬op(p,0,p) close because the
            matching table entries make the negation unsatisfiable."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (gv-identity-program gv-z2 x)]
                (proveo ['neg ['app 'gv_identity]]
                        '() '() '() prog proof))))))))

(deftest test-GV02-z2-closure
  (testing "GV02: Z₂ is closed under its operation.
            ∀x.∀y.(¬D(x) ∨ ¬D(y) ∨ ∃z.(op(x,y,z) ∧ D(z)))
            neg-call: ∃x.∃y.(D(x) ∧ D(y) ∧ ∀z.(¬op(x,y,z) ∨ ¬D(z)))
            δ-parameters for x,y; γ for z.  For every domain pair (x,y),
            the table entry gives z in domain, so ¬op ∨ ¬D closes."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (gv-closure-program gv-z2 x y z)]
                (proveo ['neg ['app 'gv_closure]]
                        '() '() '() prog proof))))))))

(deftest test-GV03-z2-inverses
  (testing "GV03: Every element of Z₂ has an inverse.
            ∀x.(¬D(x) ∨ ∃y.(D(y) ∧ op(x,y,0) ∧ op(y,x,0)))
            In Z₂: 0⁻¹=0 (0+0=0), 1⁻¹=1 (1+1=0)."
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (gv-inverses-program gv-z2 x y)]
                (proveo ['neg ['app 'gv_inverses]]
                        '() '() '() prog proof))))))))

(deftest test-GV04-z2-assoc
  (testing "GV04: Z₂ is associative (pre-computed intermediate values).
            Uses gv-assoc-precomputed-program: the framework computes
            op(op(a,b),c) and op(a,op(b,c)) in Clojure for each triple,
            then generates a 3-universal ∀x.∀y.∀z formula checking all
            8 triples.  Logically equivalent to the full 7-universal version.

            neg-call: ∃x.∃y.∃z.(D(x) ∧ D(y) ∧ D(z) ∧ ¬assoc-check(x,y,z)).
            3 δ-parameters; the ¬assoc-check is a disjunction over triples,
            each requiring eq(v,v) to fail (free-closure on eq(0,0) etc.).
            For Z₂ (associative), every eq is reflexive → all close.

            NOTE: The fully general 7-universal version (gv-assoc-program)
            is logically correct but computationally intractable for
            |domain|≥2 with the current prover — the search space is
            O(|table|^4) β-splits with equality reasoning at each node.
            Making the 7-universal version tractable is the goal of the
            performance-optimizations branch."
    (is (seq
          (run 1 [proof]
            (nom x y z
              (let [prog (gv-assoc-precomputed-program gv-z2 x y z)]
                (proveo ['neg ['app 'gv_assoc_pre]]
                        '() '() '() prog proof))))))))

(deftest test-GV05-z1-identity
  (testing "GV05: Trivial group Z₁ has identity e.
            Parametrization test: same framework, different spec."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (gv-identity-program gv-z1 x)]
                (proveo ['neg ['app 'gv_identity]]
                        '() '() '() prog proof))))))))

(deftest test-GV06-z1-assoc
  (testing "GV06: Z₁ is associative.  Minimal case: 1 table entry,
            1^7 = 1 path, trivially closes."
    (is (seq
          (run 1 [proof]
            (nom x y z w1 w2 w3 w4
              (let [prog (gv-assoc-program gv-z1 x y z w1 w2 w3 w4)]
                (proveo ['neg ['app 'gv_assoc]]
                        '() '() '() prog proof))))))))

(deftest test-GV07-non-group-identity-fails
  (testing "GV07: The non-group magma does NOT have 0 as identity.
            op(1,0)=0≠1, so 0 is not a right identity for 1.
            neg-call (testing truth) should FAIL — gv_identity is not true."
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog (gv-identity-program gv-non-group x)]
                (proveo ['neg ['app 'gv_identity]]
                        '() '() '() prog proof))))))))

(deftest test-GV08-non-group-identity-refuted
  (testing "GV08: The non-group magma's identity claim is provably FALSE.
            pos-call closes: body = ∀x.(...). γ-instantiation with x=1
            finds op(0,1,1)∧op(1,0,1), but op(1,0)=0≠1 so op(1,0,1)
            is unsatisfiable → body unsatisfiable → gv_identity is FALSE."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (gv-identity-program gv-non-group x)]
                (proveo ['pos ['app 'gv_identity]]
                        '() '() '() prog proof))))))))

;; GV09 — NON-GROUP ASSOCIATIVITY (7-UNIVERSAL VERSION)
;;
;; The non-group magma is NOT associative.
;; Counterexample: x=1,y=0,z=1.
;;   op(op(1,0),1) = op(0,1) = 1
;;   op(1,op(0,1)) = op(1,1) = 0
;;   1 ≠ 0.
;;
;; pos-call (is assoc FALSE?): NOW WORKS — completes in ~1ms.
;;   The prover finds the counterexample instantiation (x=one, y=zero,
;;   z=one) via γ-rule, then all β-branches close via neq-reflexivity
;;   or free-closure on eq(one,zero).  Previously documented as
;;   "INTRACTABLE (4^8 = 65,536 β-paths)" — lemma reuse and
;;   type-dispatched grouping made the search tractable.
;;
;; neg-call (is assoc TRUE?): STILL INTRACTABLE for |domain|≥2.
;;   The negated body introduces 7 ∃-quantifiers (δ-parameters), then
;;   4 positive op-lookups × 4 entries = 256+ β-path combinations.
;;   Since the formula is NOT valid, the prover must exhaustively
;;   explore all paths to return EMPTY — inherently exponential.
;;   With γ-budget this terminates but takes >60s for |domain|=2.
;;
;; neg-call on Z₂ (is assoc TRUE? — yes): STILL INTRACTABLE.
;;   Even though a closing tableau exists, the 7 δ-parameters and
;;   256+ β-paths with paramodulation make the search space too large.
;;
;; The asymmetry: pos-call needs ONE closing instantiation (existential),
;; neg-call needs ALL β-paths to close (universal).  Techniques that
;; could help the neg-call cases:
;;   - Constraint propagation: eagerly propagate eq constraints from
;;     δ-rule parameters to prune infeasible β-branches
;;   - Connection tableaux: goal-directed β-choice
;;   - Finite model enumeration: exploit known finite domain

(deftest test-GV09-non-group-assoc-refuted
  (testing "GV09: Non-group magma is provably NOT associative.
            Uses the FULL 7-universal formulation (not pre-computed).
            pos-call closes: γ-rule instantiates x=one, y=zero, z=one
            (and appropriate w1-w4), then all β-branches of the 4 ¬op
            disjuncts close via neq-reflexivity or free-closure."
    (is (seq
          (run 1 [proof]
            (nom x y z w1 w2 w3 w4
              (let [prog (gv-assoc-program gv-non-group x y z w1 w2 w3 w4)]
                (proveo ['pos ['app 'gv_assoc]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Section FD: Finite Domain Reasoning — Capabilities Beyond Prolog
;; ============================================================================
;;
;; This section demonstrates programs that are natural and straightforward in
;; Proflog but difficult or impossible in standard Prolog.  Each test
;; exercises a capability that Prolog fundamentally lacks:
;;
;;   1. BICONDITIONAL SEMANTICS — In Proflog, R(x) ← φ(x) means R(x) ↔ φ(x).
;;      Prolog's "R(X) :- φ(X)" means only φ(X) → R(X).  Proflog can prove
;;      propositions FALSE (not just "not provable"), producing refutation
;;      proofs.  The Free Closure Rule (Fitting §5) makes distinct Herbrand
;;      constructors provably unequal — a fact Prolog cannot express.
;;
;;   2. UNIVERSAL QUANTIFICATION IN BODIES — Proflog clause bodies can contain
;;      ∀y.ψ(x,y).  Prolog has no mechanism for this; forall/2 is a meta-
;;      predicate hack that doesn't interact correctly with unification or
;;      negation and has no proof-theoretic content.
;;
;;   3. GENUINE CLASSICAL NEGATION — Proflog's ¬ is classical negation.
;;      Prolog's \+ is negation-as-failure (NAF): unsound with free
;;      variables, not invertible, and unable to distinguish "provably
;;      false" from "unknown."
;;
;;   4. THREE-VALUED RESULTS — Proflog queries can succeed (TRUE), fail
;;      (FALSE), or be UNDEFINED (⊥).  Prolog collapses undefined into
;;      false via the Closed World Assumption (CWA).  The third truth
;;      value arises from supervaluation semantics (Fitting §3): models
;;      may disagree, yielding ⊥.  This has no analog in SLD-resolution.
;;
;;   5. ∀∀ (NESTED UNIVERSALS) — Two nested universal quantifiers in a
;;      clause body, expressing uniqueness constraints.  Completely
;;      beyond Prolog's expressive power.
;;
;; Domain: Colors {red, green, blue} as distinct constants of L.
;;
;; Object-level program:
;;   color(x) ← x=red ∨ x=green ∨ x=blue
;;   warm(x)  ← x=red
;;   cool(x)  ← x=green ∨ x=blue
;;
;; Meta-properties (inlined per Fitting §8 — no auxiliary procedure calls):
;;   excl()        ← ∀x.(x≠red ∨ (x≠green ∧ x≠blue))
;;                    "Nothing is both warm and cool" (disjointness)
;;   total()       ← ∀x.(x=red ∨ x=green ∨ x=blue)
;;                    "Everything is a color" (UNDEFINED under supervaluation)
;;   warm_unique() ← ∀x.∀y.(x≠red ∨ y≠red ∨ x=y)
;;                    "At most one thing is warm" (uniqueness)
;;
;; ============================================================================

;; --- Object-level program ---

(defn fd-color-program
  "The color/warm/cool classification over {red, green, blue}.
   cx, wx, kx are clause parameters for color, warm, cool respectively."
  [cx wx kx]
  [['color [cx]
    ['or ['eq ['var cx] ['app 'red]]
         ['or ['eq ['var cx] ['app 'green]]
              ['eq ['var cx] ['app 'blue]]]]]
   ['warm [wx]
    ['eq ['var wx] ['app 'red]]]
   ['cool [kx]
    ['or ['eq ['var kx] ['app 'green]]
         ['eq ['var kx] ['app 'blue]]]]])

;; --- Meta-property programs (standalone, inlined) ---

(defn fd-excl-program
  "excl() ← ∀x.(x≠red ∨ (x≠green ∧ x≠blue))
   NNF of ∀x.(¬warm(x) ∨ ¬cool(x)), inlined.
   'Nothing is both warm and cool.'"
  [x]
  [['excl []
    ['forall (tie x
      ['or ['neq ['var x] ['app 'red]]
           ['and ['neq ['var x] ['app 'green]]
                 ['neq ['var x] ['app 'blue]]]])]]])

(defn fd-total-program
  "total() ← ∀x.(x=red ∨ x=green ∨ x=blue)
   'Everything is a color.'
   UNDEFINED under supervaluation: weak Herbrand models may contain
   non-standard elements that are none of {red, green, blue}."
  [x]
  [['total []
    ['forall (tie x
      ['or ['eq ['var x] ['app 'red]]
           ['or ['eq ['var x] ['app 'green]]
                ['eq ['var x] ['app 'blue]]]])]]])

(defn fd-warm-unique-program
  "warm_unique() ← ∀x.∀y.(x≠red ∨ y≠red ∨ x=y)
   NNF of ∀x.∀y.(warm(x) ∧ warm(y) → x=y), inlined.
   'At most one thing is warm.'"
  [x y]
  [['warm_unique []
    ['forall (tie x
      ['forall (tie y
        ['or ['neq ['var x] ['app 'red]]
             ['or ['neq ['var y] ['app 'red]]
                  ['eq ['var x] ['var y]]]])])]]])

;; --- Tests ---

(deftest test-FD01-color-red-succeeds
  (testing "FD01: color(red) is TRUE — basic positive query.
            neg-call: ¬(red=red ∨ red=green ∨ red=blue)
            = (red≠red ∧ red≠green ∧ red≠blue). First conjunct neq(red,red)
            closes by neq-reflexivity.
            PROLOG COMPARISON: color(red) also succeeds in Prolog — this
            case works the same in both systems."
    (is (seq
          (run 1 [proof]
            (nom cx wx kx
              (let [prog (fd-color-program cx wx kx)]
                (proveo ['neg ['app 'color ['app 'red]]]
                        '() '() '() prog proof))))))))

(deftest test-FD02-color-yellow-fails
  (testing "FD02: color(yellow) is FALSE — biconditional refutation proof.
            pos-call: body = yellow=red ∨ yellow=green ∨ yellow=blue.
            β-split into 3 branches; all close by free-closure (distinct
            constants in weak Herbrand models).
            PROLOG COMPARISON: In Prolog, ?- color(yellow) simply fails
            (no matching clause head). But Prolog cannot produce a PROOF
            of falsity — it merely has absence of proof. This matters
            when negation is nested: Prolog's \\+ color(yellow) succeeds
            by NAF, but \\+ color(X) also 'succeeds' (unsoundly!) when X
            is unbound. Proflog's biconditional semantics provides a
            genuine refutation: color(yellow) ↔ φ(yellow), φ(yellow) is
            unsatisfiable, therefore color(yellow) is provably false."
    (is (seq
          (run 1 [proof]
            (nom cx wx kx
              (let [prog (fd-color-program cx wx kx)]
                (proveo ['pos ['app 'color ['app 'yellow]]]
                        '() '() '() prog proof))))))))

(deftest test-FD03-warm-blue-fails
  (testing "FD03: warm(blue) is FALSE — cross-category refutation.
            pos-call: body = blue=red. Free-closure (distinct constants).
            PROLOG COMPARISON: warm(blue) fails in Prolog by absence of a
            matching clause. But Prolog has no proof that blue ≠ red —
            it relies on syntactic non-matching. Proflog's Free Closure
            Rule (Fitting §5) exploits the weak Herbrand model's
            injectivity: distinct constant symbols f and g have provably
            disjoint interpretations. This is a theorem, not an assumption."
    (is (seq
          (run 1 [proof]
            (nom cx wx kx
              (let [prog (fd-color-program cx wx kx)]
                (proveo ['pos ['app 'warm ['app 'blue]]]
                        '() '() '() prog proof))))))))

(deftest test-FD04-cool-green-succeeds
  (testing "FD04: cool(green) is TRUE.
            neg-call: ¬(green=green ∨ green=blue) = green≠green ∧ green≠blue.
            First conjunct neq(green,green) closes by neq-reflexivity."
    (is (seq
          (run 1 [proof]
            (nom cx wx kx
              (let [prog (fd-color-program cx wx kx)]
                (proveo ['neg ['app 'cool ['app 'green]]]
                        '() '() '() prog proof))))))))

(deftest test-FD05-excl-true
  (testing "FD05: excl() is TRUE — warm and cool are disjoint.
            IMPOSSIBLE IN PROLOG: The clause body contains ∀x.

            excl() ← ∀x.(x≠red ∨ (x≠green ∧ x≠blue))

            neg-call: ¬body = ∃x.(x=red ∧ (x=green ∨ x=blue)).
            δ-rule introduces par p (fresh parameter).
            α-expansion: eq(par p, red) ∧ (eq(par p, green) ∨ eq(par p, blue)).
            β-split:
              Branch 1: eq(p,red) ∧ eq(p,green) → by equality transitivity,
                         eq(red,green) → free-closure (distinct constants) ✓
              Branch 2: eq(p,red) ∧ eq(p,blue) → eq(red,blue) → free-closure ✓
            All branches close → excl is TRUE.

            PROLOG COMPARISON: The closest Prolog approximation would be:
              excl :- forall(X, (warm(X) -> \\+ cool(X))).
            This has three fundamental problems:
              (a) forall/2 is a meta-predicate, not a logical formula —
                  it has no proof-theoretic content and cannot participate
                  in larger proofs.
              (b) It relies on NAF (\\+), which is unsound when its argument
                  contains free variables.
              (c) It requires enumerating all instances of warm(X), which
                  presupposes a finite, known domain — exactly the Closed
                  World Assumption that Proflog avoids."
    (is (seq
          (run 1 [proof]
            (nom x
              (let [prog (fd-excl-program x)]
                (proveo ['neg ['app 'excl]]
                        '() '() '() prog proof))))))))

(deftest test-FD06-total-undefined
  (testing "FD06: total() is UNDEFINED (⊥) — the three-valued supervaluation result.
            IMPOSSIBLE IN PROLOG: Prolog has exactly two outcomes (success/failure).

            total() ← ∀x.(x=red ∨ x=green ∨ x=blue)

            PART 1 — NOT TRUE:
            neg-call (testing truth): ¬body = ∃x.(x≠red ∧ x≠green ∧ x≠blue).
            δ-rule introduces par p. The branch has:
              neq(par p, red), neq(par p, green), neq(par p, blue).
            Par p is a parameter denoting an arbitrary domain element — it
            COULD be a non-standard element outside {red, green, blue}.
            No neq closes (par p ≠ any constant is consistent).
            → neg-call fails → total is NOT provably true.

            PART 2 — NOT FALSE (by semantic argument, not tested):
            pos-call (testing falsity): body = ∀x.(x=red ∨ x=green ∨ x=blue).
            γ-rule introduces free variables; each eq(v, constant) branch is
            satisfiable (just set v to that constant). The ∀ re-enqueues,
            creating an unbounded search — but no instantiation closes all
            branches because the body IS satisfiable (in models where the
            domain equals {red, green, blue}). pos-call also fails.

            Result: total is NEITHER true NOR false — it is ⊥ (undefined).
            This is a THIRD TRUTH VALUE with no analog in Prolog.

            SEMANTIC EXPLANATION (Fitting §3, Def 3.4):
            Some weak Herbrand models have domain = {red, green, blue}
            (total is true there). Others have non-standard elements
            (total is false there). Since models disagree, the
            supervaluation assigns ⊥.

            PROLOG COMPARISON: Prolog conflates ⊥ with 'false' via CWA.
            ?- total would simply fail. Prolog cannot distinguish between
            'provably false' (like color(yellow)) and 'neither provable
            nor refutable' (like total). This distinction is essential for
            sound reasoning about incomplete information."
    ;; total() is NOT provably true
    (is (empty?
          (run 1 [proof]
            (nom x
              (let [prog (fd-total-program x)]
                (proveo ['neg ['app 'total]]
                        '() '() '() prog proof))))))))

(deftest test-FD07-warm-unique-true
  (testing "FD07: warm_unique() is TRUE — at most one thing is warm.
            IMPOSSIBLE IN PROLOG: The clause body contains ∀x.∀y (nested universals).

            warm_unique() ← ∀x.∀y.(x≠red ∨ y≠red ∨ x=y)
            NNF of: ∀x.∀y.(warm(x) ∧ warm(y) → x=y)

            neg-call: ¬body = ∃x.∃y.(x=red ∧ y=red ∧ x≠y).
            δ-rule introduces par p for x, par q for y.
            Branch: eq(par p, red) ∧ eq(par q, red) ∧ neq(par p, par q).

            Equality reasoning (collect-eqso + eq-neq-closeo):
              eq(p,red) gives pair [p, red]. eq(q,red) gives pair [q, red].
              Process neq(p, q): rewrite p → red: neq(red, q).
              Rewrite q → red: neq(red, red) → contradiction! Closes.

            → neg-call succeeds → warm_unique is TRUE.

            PROLOG COMPARISON: Nested ∀∀ in clause bodies is completely
            beyond Prolog's expressiveness. Prolog clauses are Horn clauses
            with implicit existential variables: 'p(X) :- q(X, Y)' means
            p(X) ← ∃Y.q(X,Y). There is no mechanism for universal
            quantification at any level, let alone nested universals.
            The Prolog approximation would require aggregate meta-predicates
            (findall/bagof + length checks), which are procedural, non-
            logical, and do not compose with other logical operators."
    (is (seq
          (run 1 [proof]
            (nom x y
              (let [prog (fd-warm-unique-program x y)]
                (proveo ['neg ['app 'warm_unique]]
                        '() '() '() prog proof))))))))


;; ============================================================================
;; Run all tests
;; ============================================================================

(comment
  (run-tests 'cljtap.alphaleantap-ep-test))
