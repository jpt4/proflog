# SJAS Equality and Disequality Relevance

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note refines the equality classification for the ADR-0073
correspondence program. The earlier relevance matrix grouped all equality-ish
proof symbols as unresolved. That was too coarse:

- SJAS arithmetic equality evidence such as `sjas-equal` and
  `sjas-eq-progress` is relevant object-language arithmetic work.
- Generic Proflog free-constructor equality and disequality constructors such
  as `eq-step`, `neq-close`, `neq-rigid`, `neq-store`, and `refl-close` remain
  unresolved proof-system extensions.
- Equality-triggered procedure calls remain unresolved and are coupled to both
  the equality calculus and the procedure-call classification.

The executable audit now reflects this split: `sjas-equal` and
`sjas-eq-progress` are classified as relevant SJAS arithmetic/code structure,
while the generic kernel equality constructors remain unresolved.

## Source Boundary

The reviewed Willard tableau definition establishes ordinary semantic-tableau
tree obligations: proper root, axiom/deduction ancestry, and closed
root-to-leaf branches. The listed deduction rules cover conjunction, negation,
disjunction, implication, existential, bounded existential, universal, and
bounded universal expansion. Local source:
`target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
lines 806-839.

Appendix C's `Deduction` predicate likewise names ordinary semantic-tableau
deductions from ancestors, and `Closure` is stated as a branch containing a
sentence and its negation. Local source:
`target/sjas-pdf-text/willard2001_self_verifying_axiom_systems_author_jsl1.txt`,
lines 3326-3378.

Those reviewed passages do not settle the exact equality calculus of the
selected SJAS deduction apparatus. Equality is present in the arithmetic object
language, but Proflog's implementation includes a richer Fitting-style
free-constructor equality layer. Track 2b must therefore decide whether that
layer is part of the selected `D`, a bounded macro over ordinary tableau
reasoning plus equality axioms, or outside the covered fragment.

## Current Proflog Facts

`proflog.equality` implements a constraint-style free-constructor equality
engine:

- branch equality knowledge is an explicit substitution `sigma`;
- unresolved disequalities are kept in a symbolic `neqs` store;
- `walko` and `walk*o` normalize terms through `sigma`;
- `eq-contradictiono` closes impossible equalities by occurs failure or
  distinct constructor roots;
- `unify-termo` extends `sigma` through reflexivity, variable binding,
  parameter binding, or same-head constructor decomposition;
- saved disequalities and complementary atoms are rechecked after equality
  progress.

`proflog.kernel` exposes that machinery in proof certificates:

- `eq-step` records positive equality progress and may close a saved
  disequality, close complementary saved atoms, reopen a saved procedure call,
  or continue with an updated substitution;
- `refl-close` closes a disequality when both sides are already the same under
  `sigma`;
- `neq-close` closes a disequality by forcing equality through proof-local
  variables;
- `neq-rigid` treats constructor clashes as discharged disequality progress;
- `neq-store` records a delayed symbolic disequality.

The SJAS profile has a separate arithmetic equality path. `sjas-equal` reads
U-grounding numeral terms through object-level arithmetic/byte relations,
compares their bit-list values, and binds pending variables to canonical
numeral terms. `sjas-eq-progress` consumes a true arithmetic equality and
continues the branch. This exists because ordinary free-constructor equality
would treat expressions such as `sub(2,1)` and `1` as different constructor
terms, while the SJAS U-grounding arithmetic interprets them as the same
number.

## Relevance Classification

| Aspect | Classification | Reason |
|---|---|---|
| `sjas-equal` arithmetic equality evidence | Relevant | It is object-language U-grounding arithmetic work and must be preserved or internalized; treating it as an unresolved generic equality shortcut hides a core SJAS predicate. |
| `sjas-eq-progress` arithmetic equality branch progress | Relevant | It is the profile rule that lets true arithmetic equality advance a tableau branch under SJAS arithmetic semantics. |
| Generic `eq-step` free-constructor equality | Unresolved/high risk | It changes branch state through `sigma`, can close saved obligations, and may reopen procedure calls; Track 2b must prove primitive status, bounded expansion, or exclusion. |
| `refl-close`, `neq-close`, `neq-rigid`, `neq-store` disequality machinery | Unresolved/high risk | These are not just search scheduling: they affect branch closure and delayed obligations. |
| Equality-triggered calls | Unresolved/high risk | They compose equality state with the procedure-call rule and inherit both sets of proof obligations. |
| Host representation of `sigma` and `neqs` as association lists | Probably irrelevant under proof | The concrete data structure is below the proof-object level if the formal branch-state relation is preserved. |

The key distinction is semantic: SJAS arithmetic equality is part of the
object-language arithmetic profile and is therefore relevant. Generic
free-constructor equality is an additional proof-theoretic mechanism whose
place in Willard's selected semantic-tableau apparatus is not yet established.

## Track 2b Obligations

Track 2b must choose and prove one of the following treatments for generic
equality/disequality constructors.

### Primitive-Rule Route

If the selected SJAS deduction apparatus includes Proflog/Fitting
free-constructor equality, the correspondence proof must formalize:

- explicit branch equality state or an equivalent formula-tree presentation;
- substitution walking and same-term checks;
- positive equality progress and branch-state threading;
- disequality storage and delayed rechecking;
- constructor clash and occurs-check closures;
- proof-local variable binding versus user answer-variable binding;
- interaction with procedure calls, especially equality-triggered calls.

### Macro-Expansion Route

If equality is not primitive in the selected SJAS `D`, each generic equality
constructor must expand into ordinary tableau reasoning over explicit equality
axioms or over a specified free-constructor theory. The expansion must preserve
branch closure and the proof-size lower bound described in
[SJAS Proof-Size Relevance](2026-05-26-sjas-proof-size-relevance.md).

### Fragment-Exclusion Route

The correspondence theorem may restrict the covered fragment so generic
`eq-step`, disequality constructors, and equality-triggered calls cannot appear
in accepted certificates. If this route is chosen, tests must enforce the
fragment boundary and any SJAS demonstrations relying on those constructors
must be labeled outside the correspondence claim.

## Immediate Audit Consequence

The executable Track 2a symbol audit has been refined:

- `sjas-equal` and `sjas-eq-progress` now classify as `:relevant` under
  `:sjas-code-and-arithmetic-structure`;
- `eq-step`, `eq-triggered-call`, `eq-triggered-neg-call`, `neq-close`,
  `neq-rigid`, `neq-store`, and `refl-close` remain `:unresolved`.

This makes the audit more precise without pretending the equality calculus has
been settled. The remaining Track 2a work is to collect reachability evidence
for generic equality and disequality constructors in actual SJAS certificates,
then decide whether they are primitive, macro-expandable, or excluded from the
first correspondence fragment.
