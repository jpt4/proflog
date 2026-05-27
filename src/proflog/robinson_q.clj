(ns proflog.robinson-q
  "Robinson arithmetic Q as Proflog data.

   Q is expressed over function symbols `zero`, `s/1`, `add/2`, and `mul/2`.
   The formulas here can be used in two different ways: as ordinary antecedent
   assumptions for the existing tableau kernel, or as the language selected by
   the `:robinson-q` deduction-modulo proof profile."
  (:require [proflog.ast :as ast]
            [proflog.language :as language]))

(def zero
  "The Q constant `zero` as a term."
  (ast/app-term 'zero))

(defn s
  "Construct the Q successor term."
  [term]
  (ast/app-term 's term))

(defn add
  "Construct a Q addition term."
  [left right]
  (ast/app-term 'add left right))

(defn mul
  "Construct a Q multiplication term."
  [left right]
  (ast/app-term 'mul left right))

(defn numeral
  "Construct the standard Q numeral for a non-negative host integer."
  [n]
  (when (neg? n)
    (throw (ex-info "Robinson Q numerals are non-negative"
                    {:n n})))
  (if (zero? n)
    zero
    (s (numeral (dec n)))))

(def one
  "The standard Q numeral one, `s(zero)`."
  (numeral 1))

(def two
  "The standard Q numeral two, `s(s(zero))`."
  (numeral 2))

(defn eq
  "Construct an equality formula."
  [left right]
  (ast/eq-lit left right))

(defn neq
  "Construct a disequality formula."
  [left right]
  (ast/neq-lit left right))

(def language
  "Robinson Q's ordinary first-order language."
  (language/language
    {:constants ['zero]
     :functions {'s 1
                 'add 2
                 'mul 2}
     :relations {}}))

(def profile-language
  "The same Q language with the deduction-modulo profile selected."
  (language/language
    {:constants ['zero]
     :functions {'s 1
                 'add 2
                 'mul 2}
     :relations {}
     :proof-profile :robinson-q}))

(defn- and*
  "Conjoin a finite collection of formulae."
  [formulae]
  (case (count formulae)
    0 (ast/true-form)
    1 (first formulae)
    (reduce ast/and-form formulae)))

(def q1
  "Q1: every successor is distinct from zero."
  (ast/nom x
    (ast/forall-form
      x
      (neq (s (ast/var-term x)) zero))))

(def q2
  "Q2: successor is injective."
  (ast/nom x y
    (ast/forall-form
      x
      (ast/forall-form
        y
        (ast/implies-form
          (eq (s (ast/var-term x)) (s (ast/var-term y)))
          (eq (ast/var-term x) (ast/var-term y)))))))

(def q3
  "Q3: every nonzero value has a predecessor."
  (ast/nom x y
    (ast/forall-form
      x
      (ast/implies-form
        (neq (ast/var-term x) zero)
        (ast/exists-form
          y
          (eq (ast/var-term x) (s (ast/var-term y))))))))

(def q4
  "Q4: right-zero addition."
  (ast/nom x
    (ast/forall-form
      x
      (eq (add (ast/var-term x) zero)
          (ast/var-term x)))))

(def q5
  "Q5: right-successor addition."
  (ast/nom x y
    (ast/forall-form
      x
      (ast/forall-form
        y
        (eq (add (ast/var-term x) (s (ast/var-term y)))
            (s (add (ast/var-term x) (ast/var-term y))))))))

(def q6
  "Q6: right-zero multiplication."
  (ast/nom x
    (ast/forall-form
      x
      (eq (mul (ast/var-term x) zero)
          zero))))

(def q7
  "Q7: right-successor multiplication."
  (ast/nom x y
    (ast/forall-form
      x
      (ast/forall-form
        y
        (eq (mul (ast/var-term x) (s (ast/var-term y)))
            (add (mul (ast/var-term x) (ast/var-term y))
                 (ast/var-term x)))))))

(def q3-add-one-predecessor
  "A Q3-dependent theorem: every nonzero value is one more than something.

   Q5 and Q4 reduce `add(y, s(zero))` to `s(y)`, so this theorem is Q3
   expressed through the addition symbols. It is used to test that the
   deduction-modulo profile can use Q3 inside a larger refutation, not only to
   prove Q3's own direct refutation shape.
   "
  (ast/nom x y
    (ast/forall-form
      x
      (ast/implies-form
        (neq (ast/var-term x) zero)
        (ast/exists-form
          y
          (eq (add (ast/var-term y) (s zero))
              (ast/var-term x)))))))

(def q3-contextual-successor-predecessor
  "A Q3-dependent theorem under a successor context.

   Q3 supplies `x = s(y)` for nonzero `x`; Q5 and Q4 reduce
   `add(y, s(zero))` to `s(y)`. The final equality then closes only if the Q3
   predecessor equality can be used under the outer successor on both sides:
   `s(add(y, s(zero))) = s(x)`.
   "
  (ast/nom x y
    (ast/forall-form
      x
      (ast/implies-form
        (neq (ast/var-term x) zero)
        (ast/exists-form
          y
          (eq (s (add (ast/var-term y) (s zero)))
              (s (ast/var-term x))))))))

(def add-right-two-successors
  "A symbolic Q conversion theorem for adding two on the right.

   Q5 peels each right-side successor and Q4 discharges the final right-zero
   case, so `add(x, s(s(zero)))` converts to `s(s(x))` without needing
   induction.
   "
  (ast/nom x
    (ast/forall-form
      x
      (eq (add (ast/var-term x) (numeral 2))
          (s (s (ast/var-term x)))))))

(def mul-right-two-normal-form
  "A symbolic Q conversion theorem for multiplying by two on the right.

   Q7 and Q6 reduce `mul(x, s(s(zero)))` to
   `add(add(zero, x), x)`. This intentionally leaves `add(zero, x)` in normal
   form: Q's defining equations recurse on the right argument, and Robinson Q
   has no induction principle proving `add(zero, x) = x` for all `x`.
   "
  (ast/nom x
    (ast/forall-form
      x
      (eq (mul (ast/var-term x) (numeral 2))
          (add (add zero (ast/var-term x))
               (ast/var-term x))))))

(def q3-add-two-successor
  "A Q3-dependent theorem combining predecessor equality with add-two conversion.

   For nonzero `x`, Q3 supplies `x = s(y)`. Q5/Q4 reduce
   `add(y, s(s(zero)))` to `s(s(y))`, which matches `s(x)` under that Q3
   predecessor equality.
   "
  (ast/nom x y
    (ast/forall-form
      x
      (ast/implies-form
        (neq (ast/var-term x) zero)
        (ast/exists-form
          y
          (eq (add (ast/var-term y) (numeral 2))
              (s (ast/var-term x))))))))

(defn prime-form
  "Build the corrected inline Robinson-Q primality formula for one term.

   This is a formula abbreviation, not an object-language `prime/1` relation.
   Q's language has function symbols and equality only. A future frontend may
   expose this as `is-prime(x) := ...`, but the helper must inline before the
   formula reaches the kernel.

   The two explicit disequalities are essential: the divisor condition alone
   would classify one as prime, so the abbreviation excludes both zero and one.
   "
  [term]
  (ast/nom y z
    (let [vy (ast/var-term y)
          vz (ast/var-term z)]
      (and* [(neq term zero)
             (neq term one)
             (ast/forall-form
               y
               (ast/forall-form
                 z
                 (ast/implies-form
                   (eq (mul vy vz) term)
                   (ast/or-form
                     (and* [(eq vy term)
                            (eq vz one)])
                     (and* [(eq vy one)
                            (eq vz term)])))))]))))

(def prime-other-than-two-has-no-two-factor
  "Corrected factor theorem for the inline Q primality abbreviation.

   The informal statement \"any prime number is not even\" must exclude two,
   because two is prime and even. This theorem keeps the user's original
   factor-variable shape while adding the required `x != two` premise:
   if a prime other than two factors as `mul(y, z) = x`, then neither factor is
   two.

   The proof is a first-order/equality consequence of `prime-form`; it does not
   require Q arithmetic conversion or Q3 predecessor synthesis.
   "
  (ast/nom x y z
    (let [vx (ast/var-term x)
          vy (ast/var-term y)
          vz (ast/var-term z)]
      (ast/forall-form
        x
        (ast/forall-form
          y
          (ast/forall-form
            z
            (ast/implies-form
              (and* [(prime-form vx)
                     (neq vx two)
                     (eq (mul vy vz) vx)])
              (and* [(neq vy two)
                     (neq vz two)]))))))))

(def prime-other-than-two-is-not-left-even
  "Divisibility-oriented correction of the prime/evenness statement.

   This states the left-factor version of evenness directly:
   a prime other than two has no `n` with `mul(two, n) = x`. Q by itself does
   not include multiplication commutativity, so the left-factor orientation is
   made explicit rather than hidden behind an informal word such as \"even\".

   Like `prime-other-than-two-has-no-two-factor`, this closes from the inline
   primality definition and equality reasoning; it is not a multiplication
   normal-form theorem.
   "
  (ast/nom x n
    (let [vx (ast/var-term x)
          vn (ast/var-term n)]
      (ast/forall-form
        x
        (ast/implies-form
          (and* [(prime-form vx)
                 (neq vx two)])
          (ast/forall-form
            n
            (neq (mul two vn) vx)))))))

(def axioms
  "The seven Robinson Q axiom formulas with stable labels."
  [[:q1 q1]
   [:q2 q2]
   [:q3 q3]
   [:q4 q4]
   [:q5 q5]
   [:q6 q6]
   [:q7 q7]])

(def axiom-formula
  "The conjunction Q1 and ... and Q7."
  (and* (map second axioms)))

(defn q-implies
  "Build the ordinary-theory theorem shape `Q1 and ... and Q7 -> theorem`."
  [theorem]
  (ast/implies-form axiom-formula theorem))

(def ordinary-program
  "An empty program over Q's ordinary language."
  (language/compile-program language []))

(def profile-program
  "An empty program over Q's `:robinson-q` profiled language."
  (language/compile-program profile-language []))
