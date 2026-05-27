# SJAS Fuel Relevance

Date: 2026-05-25

Branch: `adr-0073-sjas-correspondence-program`

## Purpose

This note classifies finite `fuel` for ADR-0073 Track 2a. Fuel appears
throughout the Proflog kernel and SJAS proof profile, but Willard's semantic
tableau proof predicate is a relation over proof objects and coded formulas,
not a bounded evaluator run. Track 2b therefore needs an explicit treatment of
fuel before stating a correspondence theorem.

## Code Evidence

`src/proflog/kernel_support.clj` defines `step-fuelo` as bounded proof-search
micro-fuel:

```clojure
(defn step-fuelo
  [fuel next-fuel]
  (conde
    [(== fuel nil)
     (== next-fuel nil)]
    [(!= fuel nil)
     (!= next-fuel nil)
     ...]))
```

The comments state that `nil` is unbounded, while finite integer fuel is a
positive-to-nonnegative countdown. Immediate closures may still succeed at low
fuel, but non-closing branch progress consumes fuel.

`src/proflog/willard_sjas.clj` exposes fuel through `sjas/query-succeeds` as a
caller option:

```clojure
(query/query-succeeds
  (:program system)
  (theorem-query system formula)
  proof-limit
  fuel)
```

`src/proflog/kernel/willard_sjas_profile.clj` also passes the same fuel into
proof-predicate validation for non-`sjas-axiom` certificates:

```clojure
(kernel/prove-programo target '() '() '() prog '() fuel decoded-proof)
```

The same pattern occurs in `subst-prf/4`.

## Classification

Fuel is an implementation/evaluator parameter, not a relevant SJAS proof-object
feature. The proof tree, rule constructors, branch closure, formula codes,
system codes, substitution codes, and proof-code size discipline are the
candidate SJAS-relevant intensional facts. A host-supplied finite fuel bound is
not itself a node, rule, branch, or code in the Willard proof predicate.

However, fuel is not operationally irrelevant in the current implementation. A
valid decoded proof term can fail to validate under too small a finite fuel
bound because the kernel cannot consume enough non-closing branch steps. The
formal correspondence must not silently identify:

```text
kernel accepts under this particular finite fuel
```

with:

```text
the unbounded Proflog proof relation accepts
```

## Track 2b Obligation

Track 2b should state one of the following theorem forms explicitly.

Preferred unbounded form:

```text
ProflogAccepts(P, S, F)
iff
exists fuel_sufficient. ProflogAccepts_fuel(P, S, F, fuel_sufficient)
iff
SJAS_TableauProof(code(P), code(S), code(F))
```

In this form, finite fuel is a semi-decision/search resource. It is useful for
tests, but not part of the object predicate.

Alternative bounded form:

```text
ProflogAccepts_fuel(P, S, F, n)
iff
SJAS_TableauProof_bounded(code(P), code(S), code(F), n')
```

This is only appropriate if ADR-0073 intentionally studies bounded proof
search. It would require a separate relevance analysis for how `n` and `n'`
relate to proof tree size and rule applications.

## Testing Consequence

Operational tests should continue to use finite fuel so they terminate. Those
tests demonstrate that a sufficient bound exists for the checked examples. They
do not prove non-existence of a proof unless the test is paired with a formal
bounded-search theorem or an independent proof that the unbounded relation
cannot accept.

Negative SJAS self-consistency probes therefore remain evidence, not proof, of
the self-justification invariant until Track 2b supplies the proof relation and
fuel treatment.
