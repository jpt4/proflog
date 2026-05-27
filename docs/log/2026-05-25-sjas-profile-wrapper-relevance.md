# SJAS Profile Wrapper Relevance

Date: 2026-05-25

Branch: `adr-0073-sjas-correspondence-program`

## Purpose

The proof-constructor reachability audit showed that representative
non-`sjas-axiom` certificates always contain `profiled` wrappers. This note
refines their Track 2a relevance classification. The important point is that
`profiled` is overloaded: some occurrences are certificate annotations that can
probably be erased, while others mark genuine profile-level branch rules whose
subproofs must be preserved.

## Code Evidence

`src/proflog/kernel/willard_sjas_profile.clj` wraps every proof returned by the
SJAS profile entrypoint:

```clojure
(defn- wrap-proof
  [profile proof]
  (list 'profiled (profile-symbol profile) proof))
```

For a Tableau-0 system this produces:

```clojure
(profiled willard-sjas-tableau0 <kernel-proof>)
```

The same namespace also defines:

```clojure
(defn strip-profile-wrapper
  [proof]
  (if (and (seq? proof)
           (= 'profiled (first proof))
           (= 3 (count proof)))
    (nth proof 2)
    proof))
```

This supports the classification that the outer `profiled
willard-sjas-tableau0` or `profiled willard-sjas-level1` wrapper is probably an
irrelevant certificate annotation, provided Track 2b proves the selected
profile is already determined by the encoded system and that stripping the
wrapper preserves the proof tree, proof target, and size accounting up to the
allowed coding translation.

The same file uses `profiled willard-sjas-arithmetic` differently:

```clojure
(== (list 'profiled 'willard-sjas-arithmetic relation-proof) proof)
```

This is emitted by SJAS branch-closing rules such as
`sjas-neg-relation-closeo` and `sjas-neq-closeo`. It is not merely an outer
presentation wrapper. The wrapper label may be erasable, but the underlying
arithmetic relation proof is a genuine object-language computation or theory
closure that Track 2b must preserve or internalize.

`src/proflog/kernel.clj` also has generic background closers that return:

```clojure
(profiled propositional <subproof>)
(profiled first-order <subproof>)
```

Those sidecars require known active program relations. The SJAS profile
entrypoint first calls `hide-sjas-clauses-from-generic-sidecars`, which sets the
host `:clauses` map to nil for the proof run. In that state
`active-program-relations` returns the sentinel `unknown-program-relations`, and
`known-active-relationso` makes the generic sidecar path fail. Therefore the
generic propositional/first-order sidecars are not expected in ordinary SJAS
profile proof certificates produced by `sjas/query-succeeds`.

## Classification Refinement

| Constructor pattern | Track 2a classification | Reason | Track 2b obligation |
|---|---|---|---|
| `(profiled willard-sjas-tableau0 p)` | Probably irrelevant wrapper | It is added after proof search by `wrap-proof` and can be removed by `strip-profile-wrapper`. | Prove wrapper erasure preserves the accepted proof relation and that the encoded system fixes the selected profile. |
| `(profiled willard-sjas-level1 p)` | Probably irrelevant wrapper | Same as Tableau-0, but for the Level-1 profile. | Same erasure proof, with Level-1 substitution proof vocabulary included in the profile-selection invariant. |
| `(profiled willard-sjas-arithmetic p)` | Relevant closure marker with probably irrelevant label | The label wraps an actual SJAS arithmetic/equality relation proof emitted by branch-closing rules. | Prove the label is erasable only after proving `p` is an accepted object-level arithmetic or equality closure proof in the SJAS apparatus. |
| `(profiled willard-sjas-code p)` | Relevant syntax/code closure marker with probably irrelevant label | The label wraps structural code-reading and syntax predicate evidence. | Prove `p` corresponds to the object-level code predicate being evaluated and preserves byte/constructor evidence. |
| `(profiled willard-sjas-proof-check ...)` | Relevant proof-predicate marker | This wraps the current proof-code/theorem-code check, including the non-`sjas-axiom` kernel bridge. | Not erasable until the proof-tree checker or correspondence theorem accounts for the wrapped proof-check relation. |
| `(profiled willard-sjas-subst-proof-check ...)` | Relevant substitution-proof marker | Same bridge issue as `tableau-proof/3`, with the substitution source/theorem relation included. | Prove `subst-prf/4` correspondence or replace the bridge with object-level checking. |
| `(profiled propositional p)` / `(profiled first-order p)` | Probably excluded from current SJAS profile proofs | The SJAS entrypoint hides `:clauses`, which makes generic sidecar eligibility fail. | Prove exclusion for all SJAS proof-predicate validation paths, or else provide bounded macro expansions if a future path permits these constructors. |

## Consequences

The symbol-level audit is intentionally conservative when it marks `profiled`
and profile markers as unresolved. Track 2a now needs a path-sensitive
classification:

- outer SJAS profile wrappers are probably irrelevant annotations;
- profile labels on arithmetic/code/proof-check closures identify relevant
  object-language work and cannot be discarded alone;
- generic sidecar wrappers are probably excluded from current SJAS proof
  certificates, but that exclusion should become a test or proof obligation.

The next Track 2a implementation-oriented step should make the executable
audit path-sensitive enough to distinguish these cases. The current
symbol-level helper is still useful because it prevents newly encoded
constructors from entering the alphabet without any classification at all.

## Executable Follow-Up

The executable audit now has a path-sensitive `classify-profile-form` helper in
`src/proflog/sjas_correspondence.clj`. It classifies concrete
`(profiled kind subproof)` forms as:

- `:probably-irrelevant` for outer SJAS profile annotations;
- `:relevant` for SJAS arithmetic, code, axiom-membership, proof-check, and
  substitution-proof wrappers;
- `:probably-excluded` for generic propositional and first-order sidecars.

The symbol-level audit remains conservative because the bare symbol
`profiled` is not enough to know which case applies.
