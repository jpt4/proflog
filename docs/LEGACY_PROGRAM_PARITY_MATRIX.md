# Legacy Program Parity Matrix

Date: 2026-05-06
Branch: `main`
Related ADRs:

- `docs/adr/ADR-0008-test-gap-closure.md`
- `docs/adr/ADR-0009-legacy-program-closure.md`
- `docs/adr/ADR-0035-relational-residual-continuation.md`
- `docs/adr/ADR-0038-fitting-program-kernel-evaluation.md`
- `docs/adr/ADR-0039-kernel-level-group-verification.md`
- `docs/adr/ADR-0040-legacy-subsumption-parity.md`

This matrix tracks end-to-end legacy program families against the current
greenfield suite. It deliberately excludes the legacy micro-regression sections
that are about raw tableau or equality rule behavior rather than named program
families.

## Status Key

- `Comparable`: a greenfield family exists at roughly the same semantic layer.
- `Partial`: a greenfield family exists, but legacy still covers materially
  deeper forward, failure, inverse, or recursive behavior.
- `Operational gap`: greenfield can reach the semantic target, but only through
  a focused/profiled path or with runtimes too high for the default gate.
- `Absent`: no comparable greenfield family exists yet.
- `Deferred`: explicitly tracked as future experimental work, not yet promoted.

## Matrix

| Legacy family | Legacy refs | Greenfield refs | Status | Current note |
|---|---|---|---|---|
| Fitting `P1` even/odd | [X01-X05](../test/cljtap/alphaleantap_ep_test.clj) | [query_test.clj](../test/proflog/query_test.clj), [quantified_programs_test.clj](../test/proflog/quantified_programs_test.clj) | Comparable | Greenfield now covers direct and deeper quantified execution for the original `forall`-based odd clause. |
| Inline Nim `P2` | [J-family / nim-program](../test/cljtap/alphaleantap_ep_test.clj), [MV05-MV06](../test/cljtap/alphaleantap_ep_test.clj) | [query_test.clj](../test/proflog/query_test.clj), [answers_test.clj](../test/proflog/answers_test.clj), [nim_synthesis_test.clj](../test/proflog/nim_synthesis_test.clj) | Comparable | Greenfield covers direct truth/falsity, bounded answer behavior, and deeper winning-move witnesses. |
| Factored `move` warning | [move-program / MV01-MV06](../test/cljtap/alphaleantap_ep_test.clj) | [query_test.clj](../test/proflog/query_test.clj) | Comparable | Greenfield now distinguishes decidable ground `move/2` from the unresolved factored `win/1` cases, and contrasts both against the inline Nim program. |
| `member` list relation | [Q04-Q07](../test/cljtap/alphaleantap_ep_test.clj) | [list_programs_test.clj](../test/proflog/list_programs_test.clj) | Comparable | Greenfield now covers direct hit, recursive hit, empty-list failure, and non-member failure, which meets or exceeds the legacy semantic surface for `member`. |
| `append` / `reverse` list relations | [Y01-Y12](../test/cljtap/alphaleantap_ep_test.clj) | [list_programs_test.clj](../test/proflog/list_programs_test.clj), [synthesis_modes_test.clj](../test/proflog/synthesis_modes_test.clj), [answers_test.clj](../test/proflog/answers_test.clj), [parity_test.clj](../test/proflog/parity_test.clj), [AAR-0035](aar/AAR-0035-relational-residual-continuation.md) | Operational gap | Greenfield reaches the known closed answer targets through the answer overlay and long-timeout list-kernel matrix. The remaining gap is operational: some raw/default paths remain much slower than legacy and stay outside the fast gate. ADR-0035 records that every list-kernel catalog row eventually found its target, but rows such as `reverse-input-flat` still take tens of seconds. |
| Nested and deep `append` list families | [Y13-Y15](../test/cljtap/alphaleantap_ep_test.clj), [Z01-Z04](../test/cljtap/alphaleantap_ep_test.clj) | [list_programs_test.clj](../test/proflog/list_programs_test.clj), [synthesis_modes_test.clj](../test/proflog/synthesis_modes_test.clj), [parity_test.clj](../test/proflog/parity_test.clj), [2026-05-03 long-timeout sweep](log/2026-05-03-list-kernel-matrix-long-timeout-sweep.md) | Operational gap | Greenfield no longer lacks the semantic targets: the long-timeout sweep found every list-kernel catalog target. The practical gap is cost and default availability. `append-inverse-flat-longer` found all five splits but took about `509.5 s` and required raw limit `32`, so it remains a stress probe rather than a default regression. |
| Transitive closure `tc` | [TC01-TC06](../test/cljtap/alphaleantap_ep_test.clj) | [integration_families_test.clj](../test/proflog/integration_families_test.clj) | Comparable | Greenfield now covers direct edges, the recursive `a -> c` path, and the simple no-path cases on the inline small graph. |
| Peano `plus` | [PA01-PA23](../test/cljtap/alphaleantap_ep_test.clj) | [integration_families_test.clj](../test/proflog/integration_families_test.clj), [synthesis_modes_test.clj](../test/proflog/synthesis_modes_test.clj), [legacy_subsumption_test.clj](../test/proflog/legacy_subsumption_test.clj), [constructor_recursive_profile_test.clj](../test/proflog/kernel/constructor_recursive_profile_test.clj), [AAR-0040](aar/AAR-0040-legacy-subsumption-parity.md), [AAR-0041](aar/AAR-0041-relational-constructor-recursive-profile.md) | Comparable | ADR-40 adds focused PA12-PA20 answer, reverse, and partial-synthesis parity rows plus extended cases. ADR-41 promotes those answer rows onto `profiled constructor-recursive` proof records backed by the generic ADR-35 structural continuation engine, so they no longer call the diagnostic constructor-recursive sidecar directly. Ground forward proofs still use the ordinary kernel. |
| Quantified singleton and mixed clause bodies | [X-family](../test/cljtap/alphaleantap_ep_test.clj), quantified spec families below | [quantified_programs_test.clj](../test/proflog/quantified_programs_test.clj) | Comparable | Greenfield now executes direct quantified clause bodies and includes finite-domain specification families such as `sorted2`, `subset`, and `acyclic`. |
| Sortedness `sorted2` | [SO01-SO05](../test/cljtap/alphaleantap_ep_test.clj) | [quantified_programs_test.clj](../test/proflog/quantified_programs_test.clj) | Comparable | Greenfield now covers the legacy empty, singleton, sorted, unsorted, and two-element sorted cases for `sorted2`. |
| Subset relations | [SS01-SS03](../test/cljtap/alphaleantap_ep_test.clj) | [quantified_programs_test.clj](../test/proflog/quantified_programs_test.clj) | Comparable | Greenfield now covers the legacy true, false, and reflexive subset cases over the finite domain `{a, b, c}`. |
| Graph properties `acyclic` | [GP01-GP03](../test/cljtap/alphaleantap_ep_test.clj) | [quantified_programs_test.clj](../test/proflog/quantified_programs_test.clj) | Comparable | Greenfield now covers the acyclic `a→b→c` case and the two cyclic counterexamples `a→b→a` and `a→b→c→a`. |
| Group verifier `GV` | [GV01-GV09](../test/cljtap/alphaleantap_ep_test.clj) | [gv_probe.clj](../src/proflog/gv_probe.clj), [kernel_finite_verifiers_test.clj](../test/proflog/kernel_finite_verifiers_test.clj), [legacy_subsumption_test.clj](../test/proflog/legacy_subsumption_test.clj), [AAR-0039](aar/AAR-0039-kernel-level-group-verification.md), [AAR-0040](aar/AAR-0040-legacy-subsumption-parity.md) | Comparable | Supersedes the older ADR-0014 gap. ADR-39 promotes full GV associativity success/failure through the proof-producing equality-fragment profile, and ADR-40 adds identity, closure, and inverses parity plus larger `Z3` rows. The remaining concern is focused-suite cost, not absent greenfield coverage. |
| Finite-domain reasoning `FD` | [FD01-FD07](../test/cljtap/alphaleantap_ep_test.clj) | [fitting_programs.clj](../src/proflog/fitting_programs.clj), [fitting_programs_test.clj](../test/proflog/fitting_programs_test.clj), [legacy_subsumption_test.clj](../test/proflog/legacy_subsumption_test.clj), [kernel_finite_verifiers_test.clj](../test/proflog/kernel_finite_verifiers_test.clj), [AAR-0038](aar/AAR-0038-fitting-program-kernel-evaluation.md), [AAR-0040](aar/AAR-0040-legacy-subsumption-parity.md), [AAR-0042](aar/AAR-0042-equality-fragment-status-consistency.md) | Comparable | Supersedes the older absent entry. Greenfield covers finite-domain facts, disjointness, uniqueness, undefined totality, and ADR-40 extended disjointness/totality rows. ADR-42 corrects `warm-cool-disjoint` bounded status from `:inconsistent` to `:succeeds` by preventing branch-local universal witness rebinding in the equality-fragment profile. |

## Current Closure Status

There are no known remaining named legacy program-family gaps after ADR-40 when
focused/profiled greenfield selectors are counted. The remaining work is
operational:

1. Reduce list-family answer and proof-search runtimes enough to move the
   long-timeout stress rows into routine gates.
2. Decide whether the promoted constructor-recursive profile should be folded
   into the ordinary public `query-answers` default or remain an explicit focused
   profile for deep constructor-recursive answer enumeration.
3. Keep tightening default/public operational paths after profiled finite-domain
   proofs decline. ADR-42 fixed the known unsound `warm-cool-disjoint`
   `:inconsistent` status, but high-fuel ordinary fallback can still be too slow
   to use as a cheap negative assertion.
