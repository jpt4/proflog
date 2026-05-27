# ADR-37 Constraint Port Assessment

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`

## Subagent State

All second-phase ADR-37 workers completed and pushed their scoped commits to
`adr-0037-core-logic-minikanren-enhancements`.

| Worker | Commit | Scope | Result |
| --- | --- | --- | --- |
| E | `b311dbe` | Generic relational map support | Clojure host maps are exact ground values only; canonical association-list relations are the practical open relational map substrate. |
| F | `652c42f` | L-ground/tree constraint probe | `treec` plus `predc` is useful for no-`par` absence, but too weak to replace strict production L-ground structural recognition. |
| G | `dd3d2dc` | Disequality performance probe | Synthetic residual disequality stresses `!=c`, but measured Proflog cases are dominated by ordinary unification; no engine patch is justified yet. |
| H | `5e8b643` | Relational fuel replacement tests | A relational-arithmetic `step-fuelo` replacement is semantically viable as an opt-in/profiled path, but direct reverse fuel synthesis exposes bit-list numerals. |

The agent handles were closed after their reports were consumed.

## Question

Should ADR-37 port faster-minikanren-style `symbolo`, `numbero`, and `absento`
constraints into this project's core.logic layer? If so, what additional
constraints or relations should Proflog be able to use?

## Current Overlay

`proflog.minikanren-constraints` currently provides:

```clojure
(defn symbolo [x]
  (predc x symbol? 'symbolo))

(defn numbero [x]
  (predc x number? 'numbero))

(defn absento [target term]
  (deep-absento target term))
```

`symbolo` and `numbero` are still compatibility type checks. `absento` is now a
project-owned deep absence constraint rather than a direct `treec` wrapper. It
keeps delayed checks over open subterms, supports open and compound targets,
rejects same-variable target/node aliases, traverses vector/list structures and
map keys as well as values, and reifies residuals in canonical `absento`
vocabulary.

This is now good enough as the shared ADR-36/37 absence layer. It is still not a
full faster-minikanren native constraint system because positive type
constraints are not yet normalized as disjoint domains.

The sharpest observable gap is conflicting type constraints:

```clojure
(run 1 [q]
  (mkc/symbolo q)
  (mkc/numbero q))
```

Current result:

```clojure
((_0 :- symbolo numbero))
```

The corresponding faster-minikanren `symbolo-numbero-tests.scm` cases expect no
answers for every ordering where the same variable is constrained as both a
symbol and a number.

The `absento` overlay now covers the most important generalized behavior for
this branch, including target variables and compound targets. It still differs
from faster-minikanren's native `absento` in narrower ways:

- duplicate and subsumed absence constraints are not normalized;
- type constraints do not discharge absence facts over atomic typed variables;
- the positive type constraints do not yet contribute disjointness facts that
  can simplify disequality or absence stores.

## Decision

Port the constraint semantics, not the faster-minikanren engine wholesale.

ADR-37 should keep the current overlay as a phase-1 bridge, but the next serious
implementation slice should be native-style type constraints in the project's
core.logic overlay:

1. A shared positive type-constraint primitive, exposed first as `symbolo`,
   `numbero`, and `stringo`.
2. Type/absence interaction strong enough to drop redundant absence facts over
   atomic typed variables.
3. Store normalization that deduplicates equivalent absence/type facts.

This should be implemented behind the ADR-37 overlay/vendor boundary. Do not
patch production Proflog proof search merely because the constraints exist.

## Why Native Type Constraints

`predc` is a delayed host predicate. It can reject a non-symbol ground value,
but it does not record the type domain on an open variable in a way that can
interact with other type facts, disequality, or absence.

The native type-constraint behavior ADR-37 needs is:

- fail immediately when incompatible type constraints target the same root
  variable;
- fail when a typed variable is unified with a ground value of the wrong type;
- preserve the positive type fact on open variables during reification;
- use disjoint type facts to discharge impossible disequalities, for example a
  symbolic variable disequal to a numeric variable;
- allow `absento` to drop redundant absence facts when the constrained term is
  known to be an atomic type that cannot contain the target.

Only positive infinite disjoint type domains should be accepted initially. That
matches the faster-minikanren constraint invariant and avoids unsound
interaction with disequality. Immediate candidates are:

- `symbolo`
- `numbero`
- `stringo`

Clojure-specific candidates such as `keywordo` may be useful later, but they
should only be added after the canonical miniKanren set is passing upstream
behavior tests.

## Why Native Absento

The current `treec` encoding is a useful approximation:

```clojure
(treec term
       (fn [node] (!= target node))
       'absento)
```

However, a faster-minikanren-style `absento` is not just "run disequality at
every node." It is a variable-indexed constraint:

- if the constrained term is a pair/tree, push absence into its parts;
- if the constrained term is an open variable, attach the absence fact to that
  variable;
- when the variable is later instantiated, either discharge, fail, or propagate
  the absence fact into discovered children;
- deduplicate equivalent absence facts;
- normalize residuals against disequality and type facts.

That behavior matters for Proflog because object-language terms often become
more specific after a tableau rule, equality substitution, or answer-overlay
selection has already placed constraints on them.

## Upstream Tests To Port

The next implementation slice should translate these faster-minikanren tests
before changing production code:

- `symbolo-tests.scm`
- `numbero-tests.scm`
- `stringo-tests.scm`
- `symbolo-numbero-tests.scm`
- `absento-tests.scm`
- `absento-closure-tests.scm`

Expected early failures are useful as the acceptance boundary for replacing the
current wrapper implementation. The most important first failing rows are the
conflicting `symbolo`/`numbero` cases and generalized `absento` rows where the
target itself is open or compound.

## Additional Constraints Useful To Proflog

### Generic, Proflog-Motivated

These belong in a reusable core.logic overlay or generic Proflog support layer
because they are not tied to Fitting's tableau vocabulary.

1. **Relational associative maps.**

   Use canonical association-list relations first:

   - lookup
   - key presence and absence
   - assoc/update
   - dissoc/remove
   - unique-key constraints

   Worker E showed that open Clojure persistent maps are not adequate: map
   unification is exact, there is no open map tail, and unknown-key lookup
   requires projection. Association lists are the immediate substrate.

2. **Context-aware recursive tree constraints.**

   Current `treec` can apply a constraint to every discovered node, but it does
   not carry grammar context or prove child-shape invariants. Worker F showed
   this is enough for no-`par` absence but not enough for full L-groundness.

   A useful future primitive would let a recursive constraint watch child
   positions while preserving a caller-supplied grammar relation.

3. **Walk-aware absence and occurs constraints.**

   Plain raw-tree `absento` is not a drop-in replacement for Proflog equality
   occurs checks because Proflog terms are interpreted through explicit `sigma`.
   A generic pattern is still useful: constrain absence over a term after a
   caller-supplied walk function has exposed the current representative.

4. **Relational arithmetic profile for small counters.**

   ADR-36/37 arithmetic should remain available as an opt-in path for fuel and
   other small counters where a hardcoded finite-domain interval would become
   observable in reverse or partial synthesis.

### Proflog-Local

These should not be added to generic core.logic. They depend on Proflog's object
language and tableau semantics.

1. **Object-language type relations.**

   Candidate names:

   - `proof-varo`
   - `answer-varo`
   - `rigid-paro`
   - `constructor-termo`
   - `object-termo`
   - `formulao`
   - `call-free-formulao`
   - `l-ground-termo`

   Existing production structural relations remain the reference. New names
   should wrap or factor those relations, not weaken them.

2. **Par-free and call-free absence helpers.**

   A Proflog-local helper may combine strict term-shape evidence with generic
   absence machinery. Worker F's result supports using `treec`-style absence
   only after term shape is already established.

3. **Equality-substitution-aware absence.**

   Candidate replacements for pieces of `absent-termo`, `absent-paro`,
   `occurs-termo`, and `unify-termo` should account for proof variables, rigid
   parameters, answer variables, and explicit `sigma` walking. These are
   Proflog-local constraints even if they use generic `absento` internally.

4. **Residual frontier demand classifiers.**

   Answer-overlay scheduling currently has concrete/projection boundaries for
   demanded residuals. A relation-level classifier might improve partial
   residual handling, but only if it preserves ADR-35 scheduler performance.

## Non-Goals For The Next Slice

- Do not port faster-minikanren's `set-var-val!` optimization into core.logic.
  It is an invasive substitution representation change and ADR-32/37 have not
  isolated a Proflog bottleneck that justifies it.
- Do not patch core.logic disequality yet. Worker G found real `!=c` pressure
  in synthetic cases, but not in the measured Proflog cases.
- Do not replace production `l-ground-termo` with `predc` or the current
  `treec` probe.
- Do not expose bit-list fuel as a public production API without an explicit
  fuel representation decision.

## Recommended Next Implementation Order

1. Translate the faster-minikanren symbolic constraint tests into Clojure under
   an ADR-37 probe namespace.
2. Implement native-style positive type constraints in the overlay and make
   `symbolo`, `numbero`, and `stringo` use them.
3. Implement generalized native-style `absento`.
4. Move the ADR-36 arithmetic tests from local shims to the overlay namespace.
5. Re-run relational arithmetic, fuel adapter, and focused Proflog equality
   guardrails.
6. Only then select one Proflog integration site, most likely equality
   substitution absence or a small relational-map boundary, for a production
   candidate patch.
