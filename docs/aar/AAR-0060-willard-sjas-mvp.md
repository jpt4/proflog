# AAR-0060: Willard SJAS MVP Implementation

- Date: 2026-05-10
- Related ADR: [ADR-0060](../adr/ADR-0060-willard-sjas-mvp.md)
- Outcome: completed

## What Happened

ADR-0060 implemented the MVP Willard SJAS-lang substrate described by ADR-0058
and the ADR-0059 independent review.

The new public namespace `proflog.willard-sjas` exposes:

- U-grounding language declarations;
- `:willard-sjas-tableau0` and `:willard-sjas-level1` profile languages;
- term/formula helpers for the finite MVP arithmetic and proof-coding
  vocabulary;
- bounded quantifier constructors plus Delta-star-0, Pi-star-1, and
  Sigma-star-1 classifiers;
- bounded-quantifier AST recognition and NNF lowering through ordinary `leq`
  relation guards;
- a finite `system` builder that generates Group-Zero through Group-3,
  stable formula codes, `axiom-member` clauses, reflected Group-2b user
  entries, external clauses, and an executable compiled program;
- a source-facing `system-source` macro that accepts `language`, `beta`,
  `reflected`, and `external` sections in prefix frontend style;
- `query-succeeds` for proving a formula from the generated SJAS axiom basis;
- a bounded contradiction probe that records fuel, proof limit, and duration.

The new profile namespace `proflog.kernel.willard-sjas-profile` registers the
two SJAS profiles through `proflog.proof-profile`. It routes proof search
through the ordinary program-aware kernel and wraps proof terms as:

```clojure
(profiled willard-sjas-tableau0 ...)
(profiled willard-sjas-level1 ...)
```

The generated program carries relation-backed MVP predicates for arithmetic
graph facts, axiom membership, formula-code class facts, complement pairs,
closed branches, and miniature `tableau-proof/3` certificates. The profile
source audit rejects `prove-program-host`, `host-proof`, and whole-formula
shortcuts on this route.

Documentation was added or updated:

- [Willard SJAS MVP Example](../../worked-examples/willard-sjas.md);
- [README](../../README.md);
- [Proflog Greenfield User Guide](../USER_GUIDE.md);
- [Test Runtime Baseline](../TEST_RUNTIME_BASELINE.md);
- ADR and AAR indexes.

## Completion Audit

| Requirement | Evidence |
|---|---|
| Public SJAS builder namespace | `src/proflog/willard_sjas.clj` |
| Source-facing frontend builder | `sjas-source-builder-accepts-prefix-program-sections` and `proflog.frontend/clauses` |
| Reusable frontend clause emission | `proflog.frontend-test/frontend-can-emit-clauses-for-higher-level-builders` |
| Proof profile namespace | `src/proflog/kernel/willard_sjas_profile.clj` |
| Profile dispatch for both keys | `src/proflog/proof_profile.clj` methods for `:willard-sjas-tableau0` and `:willard-sjas-level1` |
| U-grounding language with `mult/3` and no `mul/2` | `sjas-profile-languages-have-u-grounding-shape` |
| Delta-star-0 / Pi-star-1 / Sigma-star-1 classifiers | `sjas-formula-classifiers-cover-bounded-and-unbounded-shapes` |
| Bounded-quantifier NNF lowering | `proflog.normalize-test/to-nnf-lowers-sjas-bounded-quantifiers-through-leq-guards` |
| Representative U-grounding proof | `sjas-arithmetic-and-mult-graph-run-through-the-compiled-program` proves `add(zero, zero) = zero` from Group-1 |
| `mult/3` forward, answer, and partial synthesis modes | Same test proves `mult(two, three, six)` and synthesizes missing left/right multiplicands for `mult(_, two, four)` and `mult(two, _, four)` |
| Valid and invalid miniature certificate behavior | `sjas-proof-certificates-are-relational-program-facts-not-host-checks` |
| Generated Group-Zero through Group-3 | `sjas-system-builder-generates-groups-and-reflected-boundary` |
| Beta consequence and generated self-consistency query through both profiles | `sjas-tableau0-and-level1-query-generated-axioms-through-selected-profile` |
| Reflected versus external program semantics | `sjas-system-builder-generates-groups-and-reflected-boundary` verifies reflected changes alter system/group-three codes and external-only changes do not |
| Bounded contradiction probe with timing | `sjas-level1-bounded-contradiction-probe-records-timing` |
| Route audit against host proof checker shortcuts | `sjas-profile-source-audit-rejects-host-proof-checker-route` |
| Worked example and tutorial material | `worked-examples/willard-sjas.md`, README, and user guide updates |

## Verification

Focused selector:

```text
lein test-proflog-sjas
Ran 9 tests containing 61 assertions.
0 failures, 0 errors.
real 30.95 s
```

Focused bounded-quantifier lowering selector:

```text
lein test :only proflog.normalize-test/to-nnf-lowers-sjas-bounded-quantifiers-through-leq-guards
Ran 1 tests containing 8 assertions.
0 failures, 0 errors.
real 6.75 s
```

Focused frontend clause-emission selector:

```text
lein test :only proflog.frontend-test/frontend-can-emit-clauses-for-higher-level-builders
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 17.47 s
```

Standard gates, run concurrently:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 89.21 s
```

```text
lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 255.14 s
```

The extended gate was run concurrently with the earlier source-level fast gate;
the final fast rerun was serial after adding the direct frontend regression.

## What Worked

- The reflected/external program boundary is now executable and tested. A
  reflected user clause changes the generated system id and Group-3 code;
  external application code does not.
- The builder keeps users away from hand-written `SelfCons` and
  `axiom-member` facts while still exposing those generated artifacts for
  inspection.
- Profile evidence is visible without introducing a host-side theorem prover.
- The MVP gives Proflog a concrete SJAS-lang surface for experimentation with
  finite reflected systems.

## What Did Not Work

- The certificate checker is intentionally miniature. It demonstrates the
  relation-backed shape and rejects malformed certificate terms, but it is not
  a complete semantic-tableau proof-certificate validator.
- Tab-1/proof-list theorem reuse is not implemented or claimed. The Level-1
  MVP uses plain semantic tableaux as the reflected deduction method `D`.
- U-grounding arithmetic is finite and demonstrative. It includes enough graph
  facts and Group-1 equations for the tests and worked example, not a full
  implementation of every Willard grounding function.
- The bounded contradiction probe returning `:not-found` is only an operational
  boundary check. It is not evidence of Willard's external
  consistency-preservation metatheorem.

## Follow-Up

- A future ADR should replace the miniature certificate predicate with a fuller
  relation-backed tableau certificate checker before claiming stronger proof
  reflection.
- A future ADR should decide whether to implement Tab-1/proof-list theorem
  reuse and then update the `:willard-sjas-level1` claim accordingly.
- A future ADR should expand U-grounding arithmetic beyond the finite MVP graph
  facts if SJAS programs need broader numeric computation.
