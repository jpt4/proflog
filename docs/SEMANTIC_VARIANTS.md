# Semantic Variants

Date: 2026-04-18

This document prevents operational shortcuts from silently becoming semantics.

## Baseline

The default greenfield baseline is:

- Fitting ground-core semantics for Proflog,
- αleanTAP-style pure relational proof search,
- finite-term free-constructor equality with occurs-checking unification,
- symbolic disequality rather than eager disunifier enumeration,
- mandatory language declarations,
- answer export restricted to the declared object language,
- proof evidence retained strongly enough to explain or replay closure.

## Variant Policy

| Variant | Default status | Allowed? | Documentation rule |
|---|---|---|---|
| closed-world or Clark-completion reading | not default | only as a named non-default profile | separate ADR and explicit user-facing label |
| no-check unification or `run-nc` behavior | forbidden as default | only as a separate theory/profile, if ever | separate ADR and explicit warning that theory changes |
| symbolic disequality store | default | yes | describe representation and recheck invariant |
| bounded disunifier enumeration | deferred | yes, if bounded and opt-in | document bounds and trigger conditions |
| derived congruence cache | deferred | yes, if rebuildable from authoritative state | document rebuild invariant and cache invalidation |
| tabling or memoization | deferred | yes | document which calls are tabled and what changes operationally |
| host projection inside the semantic kernel | not default | only with explicit ADR justification | explain why the boundary is still semantically safe |
| committed choice in the kernel | not default | only with explicit ADR justification | explain loss of relationality or completeness |
| proofless fast path | not default | only if baseline proof mode remains available | document what evidence is lost |

## Hard Constraints

- Internal parameters such as `par` are never admissible final answers.
- Language declarations define the export boundary for answer terms.
- Search divergence is not silently reported as semantic falsity.
- Any approximation added for performance must be visibly named in code and documentation.
- The greenfield kernel keeps L-groundness structural rather than projected.
- Any host projection used for term-shape inspection belongs to a named
  non-default or legacy path, not to the default kernel baseline.

## Review Trigger

Update this document whenever an ADR proposes:

- a new optimization,
- a new runtime mode,
- a new answer format,
- a new semantic reading of recursion, equality, or negation.
