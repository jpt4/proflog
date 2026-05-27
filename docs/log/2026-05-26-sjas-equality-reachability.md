# SJAS Equality Constructor Reachability

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note records a focused reachability probe for generic
free-constructor equality and disequality constructors in current SJAS proof
terms. It follows
[SJAS Equality and Disequality Relevance](2026-05-26-sjas-equality-relevance.md),
which classified SJAS arithmetic equality as relevant and generic kernel
equality as unresolved.

The probe confirms that generic equality is not merely an encodable possibility:
`refl-close` is reachable in ordinary SJAS theorem certificates over declared
non-arithmetic function symbols. It also exposes a proof-certificate layout gap:
the kernel can return `free-close` in an SJAS proof term, but
`proflog.willard-sjas-code/proof-code-bytes` currently cannot encode
`free-close` as an SJAS certificate symbol.

2026-05-27 update: `free-close` is now an encoded SJAS proof symbol and is
classified as unresolved in the executable correspondence audit. This closes
the narrow grammar gap for the tag, but not the Track 2b obligation to prove
its primitive, macro-expanded, unreachable, or excluded status.

## Probe Commands

The first probe used a Tableau-0 system with two extra unary function symbols,
then queried a reflexive equality theorem and a rigid constructor-clash
disequality theorem:

```bash
timeout 180s lein with-profile test trampoline run -m clojure.main -e \
  "(require '[proflog.willard-sjas :as sjas]
            '[proflog.ast :as ast]
            '[proflog.sjas-correspondence :as corr])
   (let [system (sjas/system {:profile :willard-sjas-tableau0
                              :functions {'mark 1 'other 1}})
         mark-one (ast/app-term 'mark sjas/one)
         other-one (ast/app-term 'other sjas/one)
         cases {:refl (ast/eq-lit mark-one mark-one)
                :rigid-neq (ast/neq-lit mark-one other-one)}
         result (into {}
                      (for [[k f] cases
                            :let [p (first (sjas/query-succeeds
                                             system f
                                             {:proof-limit 1
                                              :fuel 96}))]]
                        [k {:proof p
                            :audit (when p (corr/audit-proof-term p))}]))]
     (prn result))"
```

The second probe checked whether `free-close` is encodable by the current proof
byte layout:

```bash
lein with-profile test trampoline run -m clojure.main -e \
  "(require '[proflog.willard-sjas-code :as code])
   (try
     (prn (code/proof-code-bytes '(free-close)))
     (catch clojure.lang.ExceptionInfo e
       (prn {:error (.getMessage e)
             :data (ex-data e)})))"
```

## Results

The reflexive equality theorem returned:

```clojure
(profiled willard-sjas-tableau0
  (conj
    (refl-close)))
```

The audit classified `conj` as relevant, `refl-close` as unresolved, and the
outer `willard-sjas-tableau0` profile wrapper as probably irrelevant under a
future erasure proof.

The constructor-clash disequality theorem returned:

```clojure
(profiled willard-sjas-tableau0
  (conj
    (free-close)))
```

The audit reported `free-close` under `:unclassified-symbols`, because it is
not in `proflog.willard-sjas-code/proof-symbols`. Direct encoding confirmed the
gap:

```clojure
{:error "Unsupported proof symbol in SJAS certificate"
 :data {:symbol free-close}}
```

An additional beta-equality probe with the false beta formula
`mark(1) = other(1)` also returned a proof containing `free-close`; that case
is less semantically useful because the beta basis is inconsistent, but it
confirms the same proof-symbol gap.

## Consequences

This reachability evidence sharpens the equality classification:

- `refl-close` is reachable in current SJAS theorem certificates and therefore
  must be handled by Track 2b if the correspondence covers non-arithmetic
  equality.
- `free-close` is reachable in current SJAS theorem proof terms. It is now
  encodable as an SJAS proof certificate symbol, but remains an unresolved
  equality/free-constructor closure rule for Track 2b.
- The existing proof-symbol classification audit covers the declared encoded
  certificate alphabet, but it does not prove that the alphabet covers every
  proof symbol the kernel can emit on SJAS theorem paths.

Track 2b should not claim a correspondence over all current SJAS theorem proofs
until one of these routes is chosen:

1. For encoded reachable equality internal tags such as `free-close`, prove
   their place in the selected equality calculus after classification.
2. Change proof production so generic equality subproofs are represented only
   by already-declared certificate constructors with formally specified
   payloads.
3. Restrict the first correspondence fragment to exclude generic
   free-constructor equality proofs, and add tests that enforce the exclusion.

The first option should not be done piecemeal. `free-close` suggests a broader
audit is needed for equality helper tags such as occurs-check closure,
unification reflexivity/binding/decomposition tags, and atom-closure tags that
may appear below `eq-step`, `neq-close`, or complementary literal closure.

## Track 2a Update

Generic equality/disequality is now evidence-backed as reachable, at least for
`refl-close` and `free-close`. The current executable audit deliberately leaves
both `refl-close` and `free-close` unresolved; `free-close` is now part of the
declared SJAS certificate alphabet. The next equality-related Track 2a task is
a complete kernel-proof-tag inventory: compare all proof tags the kernel can
emit on SJAS paths against `proof-symbols`, then classify each as primitive,
macro-expandable, excluded, or missing from the certificate grammar.
