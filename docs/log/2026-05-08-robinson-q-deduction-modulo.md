# Robinson Q And Deduction Modulo Notes

Date: 2026-05-08

## Context

The discussion considered how Robinson arithmetic Q could be represented in
Proflog and how its axioms might become tableau-level theory rules.

Robinson Q is a first-order theory over equality with term-forming symbols:

```text
zero
s/1
add/2
mul/2
```

In Proflog that means the language declaration should put those symbols in the
function/constant namespace, not the relation namespace:

```clojure
(pf/language
  (constants zero)
  (functions (s 1)
             (add 2)
             (mul 2))
  (relations))
```

The formulas Q1-Q7 are then ordinary first-order formulas over equality. In a
plain tableau treatment, proving Q7 from Q is trivial if Q7 is among the
assumptions: the proof is a tableau closure for `Q7 and not Q7`, not a
computation of multiplication.

## Functions And Relations

The key distinction remains:

- function symbols build terms and have no proof procedure of their own;
- relation symbols create atoms that the Procedure Call Rule can expand through
  compiled Proflog clauses;
- quantified variables range over object-language terms, not over functions or
  relations.

Therefore `mul(x, s(y))` as a function term does not execute. It can only be
reasoned about through equality axioms or theory conversion. A relational
arithmetic predicate such as `times(x, y, z)` would be a different operational
encoding.

## Deduction Modulo Shape

A possible `:robinson-q` profile would promote some Q axioms from ordinary
formulas into theory machinery:

```text
add(x, zero) -> x
add(x, s(y)) -> s(add(x, y))
mul(x, zero) -> zero
mul(x, s(y)) -> add(mul(x, y), x)
```

Q1 and Q2 align with constructor equality behavior:

```text
s(x) != zero
s(x) = s(y) -> x = y
```

Q3 is different:

```text
forall y. y = zero or exists x. s(x) = y
```

It is not a terminating rewrite rule. It is a predecessor-or-zero case split:

```text
t = zero
or
t = s(p)    p fresh
```

That case split would need relevance and fuel controls because it can easily
create infinite or irrelevant predecessor search, especially in the presence of
nonstandard Q models.

## Proof Of An Axiom Formula Under The Profile

If Q7 is encoded as the conversion rule:

```text
mul(x, s(y)) -> add(mul(x, y), x)
```

and the user asks the prover to prove the formula:

```text
forall x y. mul(x, s(y)) = add(mul(x, y), x)
```

then the proof is no longer "Q7 is an axiom". It is a theory-conversion proof:

1. open the universal quantifiers with arbitrary parameters `a` and `b`;
2. reduce the left side by the `mul-succ` rewrite:

   ```text
   mul(a, s(b)) -> add(mul(a, b), a)
   ```

3. compare the normalized sides by reflexive equality;
4. record a proof step such as `(q-rewrite mul-succ)`, followed by equality
   reflexivity.

Equivalently, a refutation tableau for the negation:

```text
exists x y. mul(x, s(y)) != add(mul(x, y), x)
```

would instantiate witnesses, normalize the disequality modulo the Q rewrite,
and close because it becomes:

```text
add(mul(a, b), a) != add(mul(a, b), a)
```

The proof remains nontrivial in the proof object because it uses an explicit
theory conversion step. But it is not a derivation from Q7 as an assumption;
Q7 has been moved into definitional equality for the profile.

## Proof-Object Sketches

For the positive theorem presentation:

```text
Goal:
  forall x y. mul(x, s(y)) = add(mul(x, y), x)
```

a proof object should look like a quantifier/equality proof with an explicit
Q-conversion step:

```text
(forall-intro a)
(forall-intro b)
(q-rewrite mul-succ
  :from mul(a, s(b))
  :to   add(mul(a, b), a))
(eq-reflexive add(mul(a, b), a))
```

The exact Clojure data shape can differ, but the important audit property is
that the proof term exposes both:

- the universal-parameter introductions; and
- the trusted `mul-succ` theory conversion used before reflexivity.

For the refutation-tableau presentation, the prover starts from the negated
theorem:

```text
exists x y. mul(x, s(y)) != add(mul(x, y), x)
```

A proof object could record:

```text
(exists-witness a)
(exists-witness b)
(q-rewrite mul-succ
  :from mul(a, s(b))
  :to   add(mul(a, b), a))
(close-neq-reflexive add(mul(a, b), a))
```

In ordinary first-order tableau terms, this is closure of a branch containing a
disequality whose two sides are equal modulo the Q conversion relation. In
deduction-modulo terms, it is closure by convertibility plus equality
reflexivity.

This proof is "more than Q7 is an axiom" in the narrow sense that the proof
object contains a conversion trace. It is still not a derivation of Q7 from
weaker arithmetic principles; it is a check that the theorem formula is
convertibly reflexive under the active `:robinson-q` profile.

## Design Boundary

Promoting Q axioms to rules changes proof meaning. Ordinary formulas produce
assumption-driven tableau proofs. Deduction modulo produces proofs modulo a
trusted conversion relation. If Proflog implements this, proof objects must
record theory steps explicitly so users can audit which arithmetic conversions
were used.

## ADR-0048 Implementation Obligation

The follow-up implementation track is ADR-0048. It must implement both readings
instead of choosing one prematurely:

- Q as ordinary formulas passed in the antecedent of an implication;
- Q as an opt-in `:robinson-q` proof profile using deduction-modulo conversion
  for terminating `add` and `mul` equations.

The opt-in mechanism itself must be generic. A language should be able to
select a proof profile, and query execution should dispatch through that
profile without embedding Robinson-Q-specific conditionals at each call site.

The common comparison suite should include formulas that both paths can prove,
record runtimes for each path, and document where the two readings diverge. In
particular, Q7 under the ordinary path is proof from an assumption, while Q7
under `:robinson-q` is proof by conversion plus reflexive equality. Q3 remains
outside the initial conversion profile because its predecessor-or-zero shape is
a controlled case split rather than a terminating rewrite.

ADR-0048 was completed the same day. The promoted common comparison covers Q7,
`add(1, zero) = 1`, `mul(2, zero) = zero`, `add(1, 2) = 3`, and
`mul(2, 2) = 4` through both paths. The focused selector passed with
`Ran 5 tests containing 42 assertions`, and the standard fast/extended gates
passed after the proof-profile dispatch change.

ADR-0049 then added Q3 to the profiled comparison without turning it into a
rewrite. The ordinary path proves `Q1 and ... and Q7 -> Q3`; the profiled path
recognizes the refutation branch:

```text
exists x. x != zero and once-forall y. x != s(y)
```

and records:

```text
q3-case-split predecessor-or-zero
```

inside the `profiled robinson-q` proof. This closes Q3 itself while preserving
the earlier warning that unrestricted predecessor-or-zero splitting needs a
separate relevance and fuel policy.
