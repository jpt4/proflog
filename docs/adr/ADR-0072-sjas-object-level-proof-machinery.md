# ADR-0072: SJAS Object-Level Proof Machinery

- Status: in progress
- Date: 2026-05-21
- Branch: `adr-0072-sjas-object-proof-machinery`

## Context

ADR-0063 through ADR-0071 moved SJAS formula, system, and proof codes away from
opaque host labels and toward inspectable byte/base-64 and U-Grounding numeral
representations. The current implementation is non-vacuous: `tableau-proof/3`
and `subst-prf/4` consume code terms, decode proof certificates, and check the
decoded certificates through a local SJAS proof-directed checker for the
currently generated certificate shapes. That checker replaced the earlier
`kernel/prove-programo` shortcut, but it is not yet a paper-grade
arithmetization of the full semantic-tableau deductive apparatus.

The remaining objective is stricter. Host-side computation is acceptable at the
source-compilation boundary, just as Proflog source syntax is compiled into
kernel-recognizable formulae. But arithmetic coding or decoding needed while
applying an object-language predicate must be performed by kernel-level
relations. In particular, a program written in SJAS must be composable with the
proof predicate; that is not true if the proof predicate depends on host-only
lookups or host-only theorem-target reconstruction during its own evaluation.

The current implementation still has several object-level proof machinery
boundaries:

- the active system's proof antecedent is now reconstructed from `system-code`
  rather than selected from generated `:sjas/system-entries` metadata, and the
  decoded proof term is checked by a local proof-directed relation rather than
  by `kernel/prove-programo`; full completion still requires a formal
  arithmetic/tableau specification of that checker or a correspondence proof
  covering each accepted constructor;
- formal axiom citation for standard SJAS axiom groups no longer needs generated
  `axiom-member/2` facts: Group-0, Group-1, Tableau-0 Group-3, and Level-1
  Group-3 citations validate the encoded system header and decode the theorem
  formula; Group-2 beta citations and reflected Group-2b citations read the
  corresponding encoded sections from `system-code`. All of those paths still
  enter through the staged byte extractor, but generated `axiom-member/2` facts
  are no longer trusted by `sjas-axiom` proof-certificate checking;
- already-ground U-Grounding code terms use a deterministic Clojure entry
  shortcut before structural relation checking, which is acceptable only as an
  operational staging boundary if it does not become the semantic proof rule;
- validation that user-supplied beta axioms meet Willard's truth and formula
  class constraints remains delegated to the user.

## Decision

Implement object-level proof machinery in stages, keeping each stage covered by
tests that fail against the previous host boundary.

The first stage removes theorem-target registry shortcuts from proof-predicate
application. `tableau-proof/3` and `subst-prf/4` must always derive the negated
theorem target from the theorem-code bytes through the structural formula-code
decoder, including for generated axiom theorem codes. The proof evidence must
name this theorem-code decoding step so future regressions cannot silently
reintroduce generated theorem-target lookup.

The first stage also fixes the nominal boundary between source theorem queries
and structurally decoded theorem codes. Formula codes store binder indexes, not
arbitrary host nom identities. Source-side theorem queries therefore use the
same canonical code-nom table as the structural decoder, and the decoder builds
AST variable and quantifier nodes by enumerating that finite table with concrete
nom constants. Calling `nominal/tie` on a logic variable standing for a nom is
not sufficient: the resulting formula may print correctly after reification but
will not behave as the ground formula that proof certificates were generated
against.

The second stage removes the finite formula syntax registries. `wff/1`,
`delta-star-0-code/1`, `pi-star-1-code/1`, `sigma-star-1-code/1`, and
`neg-pair/2` no longer consult generated `:sjas/formula-*` or
`:sjas/neg-pair-entries` tables. They decode the supplied formula code into the
kernel's internal formula syntax, then apply the structural recognizer or
formula-complement relation. This is a narrower improvement than complete
object-level proof machinery: already-ground compact and U-Grounding code terms
can still enter through `ground-formal-code-term`, which extracts bytes in
Clojure before the relational formula grammar consumes those bytes. That
shortcut is host-side computation and remains a temporary operational boundary,
not a semantic account of reflection inside the SJAS object language.

The third stage makes beta axiom citation consume the beta section of
`system-code`. A proof certificate whose decoded proof is `sjas-axiom` now first
checks whether the theorem code's byte string is one of the encoded Group-2 beta
formulas. Only non-beta axiom citations fall back to generated axiom-member
metadata. This is deliberately partial: it internalizes the user-supplied beta
membership boundary enough for beta citation composition, but it does not yet
derive the full Group-0/1/2b/3 axiom basis from system-code and it still relies
on `ground-formal-code-term` to expose already-ground byte strings.

The fourth stage makes reflected Group-2b axiom citation consume the reflected
clause section of `system-code`. Reflected clause records encode relation index,
arity, and body formula bytes. The profile reconstructs the axiom formula
`forall x1 ... forall xn. body -> R(x1, ..., xn)` and compares it against the
theorem code modulo alpha-equivalence. This removes the generated
`axiom-member/2` dependency for user-supplied reflected clauses. At that stage
it still kept the same ground-byte extraction boundary as the beta path and
still left Group-0, Group-1, and Group-3 on generated fallback metadata.

The fifth stage makes fixed Group-0 and Group-1 axiom citation consume the
system-code header and decoded theorem formula instead of generated membership
facts. These axiom groups are fixed by the SJAS profile, so the system side only
needs to prove that the supplied code is an encoded SJAS system with a supported
profile tag. The theorem side decodes the formula code and compares it with the
fixed decoded axiom shapes. Compact formula codes canonicalize object numerals
as `num` payloads, so the first two Group-1 equations are recognized as their
canonical numeric code forms rather than as literal `add` and `dbl` syntax. This
removes generated fallback for Group-0 and Group-1, but still leaves Group-3,
ground-byte extraction, and code-level proof-tree checking open.

The sixth stage makes Tableau-0 Group-3 citation reconstruct the
self-consistency axiom from `system-code`. For compact systems the embedded
system and contradiction codes decode as `(code bytes)` terms; for U-Grounding
systems they decode as canonical `num` payloads with the code sentinel appended.
The profile accepts either representation and matches the decoded theorem
formula against `forall p. not tableau-proof(system-code, false-code, p)`. This
removes generated fallback for Tableau-0 self-consistency citations, but Level-1
Group-3 still requires an object-level reconstruction of the fixed-point
substitution skeleton before its generated fallback can be removed.

The seventh stage makes Level-1 Group-3 citation validate its fixed-point
skeleton from encoded code terms. The decoded final Group-3 formula must have the
Level-1 self-consistency shape. The embedded substitution-code argument is then
read as compact `(code bytes)` or U-Grounding sentinel `num` bytes, decoded as a
formula, and checked against the expected skeleton formula over the same
system-code term and a free `v0` placeholder. This removes generated fallback
for the remaining standard axiom group, while still leaving the ground byte
extractor, the generated fallback branch itself, and proof-tree checking for
later stages.

The eighth stage removes the generated `axiom-member/2` fallback from
`sjas-axiom` proof-certificate checking. A regression test injects a bogus
generated membership fact for the contradiction code and verifies that
`tableau-proof(system-code, false-code, sjas-axiom-code)` still fails. Removing
the fallback exposed a legitimate relational `subst-prf` path where code terms
arrive bound by core.logic rather than available to host ground extraction. The
staged byte reader therefore now falls back to the structural code relation when
host ground extraction fails, and relational axiom-citation paths walk code terms
through equality sigma before decoding. This preserves legitimate
proof-certificate composition without trusting generated axiom facts. It is
still not the final object-level reader: the host ground shortcut remains, and
ordinary `axiom-member/2` queries still closed from generated facts at this
stage.

The ninth stage removes the generated `axiom-member/2` fact path from ordinary
SJAS predicate evaluation. `axiom-member(system-code, formula-code)` queries now
route through the same decoded system-code membership relation used by
`sjas-axiom` proof certificates. A regression test injects a bogus generated
membership fact for the contradiction code and verifies that an ordinary
`axiom-member/2` query still fails. Ground `axiom-member/2` queries also enter
the SJAS direct-profile route so the implementation does not spend minutes
walking large public code terms before reaching the structural decoder. This is
progress, not completion: generated `axiom-member/2` registry fact metadata
still exists as a builder artifact, and the structural membership relation still
uses the staged ground-byte extractor for already-ground public codes.

The tenth stage removes the stale generated `axiom-member/2` builder metadata.
The builder already emitted no generated axiom-member clauses, but it still
stored a finite `:sjas/fact-atoms` table in the registry from earlier proof
profiles. A red test now requires the registry to omit that table and verifies
that compiled programs do not contain an `axiom-member` procedure clause. The
injected-fact regressions still mutate the registry to prove that a stale or
manually introduced fact remains semantically ignored.

The eleventh stage tightens proof-certificate dispatch. A decoded `sjas-axiom`
certificate is now valid only through the axiom-citation branch; if structural
system-code membership fails, the query fails rather than falling through into
the generic kernel proof branch. This preserves the object-language meaning of
the certificate tag and avoids spending proof search on impossible host-shaped
kernel witnesses after an invalid axiom citation.

The twelfth stage isolates the U-Grounding arithmetic decoder used by axiom
citation. A red U-Grounding `tableau-proof/3` regression required the proof
evidence for ground system and theorem numerals to include the byte-cons
relation rather than the deterministic host byte shortcut. The first direct
removal attempt was semantically right but impractical because it materialized
and re-walked the complete bit list for large system numerals. The green
implementation uses a bounded kernel relation that peels six canonical
`0`/`1`/`dbl`/`add(_,1)` constructor bits per byte, records the fixed-radix
byte-cons step, and leaves the compact `code-N` constructor shortcut isolated to
the legacy compact representation. This is still not completion:
`ground-formal-code-term` remains in formula-code and substitution-code paths,
and compact codes are not the pure U-Grounding arithmetic representation.

The thirteenth stage removes the generated proof-antecedent registry. The
builder no longer stores `:sjas/system-entries`; theorem queries use canonical
code-nom binders at the source-compilation boundary so that proof predicates
can later reconstruct the same antecedent from encoded formula bytes.
`tableau-proof/3` and `subst-prf/4` decode the active `system-code`, rebuild the
Group-0, Group-2 beta, reflected Group-2b, and Group-3 theorem axiom sequence,
map each decoded internal formula to the double-negated antecedent shape used
by the kernel refutation, and left-conjoin that list in builder order. The
focused regressions explicitly remove any `:sjas/system-entries` key before
checking non-`sjas-axiom` certificates. This is progress, not completion:
Level-1 proof antecedent reconstruction still contains the fixed-point
skeleton check, so beta-style proof mismatch checks against the full Level-1
target are treated as slow/deferred until proof-code validation becomes
proof-tree-guided. The default malformed-code negatives now reject active
system-code terms before antecedent reconstruction; full rejection of arbitrary
bad decoded kernel proof terms remains part of the proof-tree checker stage.

The fourteenth stage makes compact formula-code syntax predicates expose their
constructor-byte scan in proof evidence. The previous compact reader was not a
generated formula registry, but it still hid host-side byte extraction behind a
bare `sjas-code-bytes` marker. `wff/1`, the formula-class predicates, and
`neg-pair/2` now use the structural compact-code relation directly, so compact
predicate proofs include nested `sjas-code-arg` evidence for each code byte.
This is deliberately scoped: compact theorem-code decoding inside generic
non-`sjas-axiom` proof-certificate checks still uses the deterministic staged
reader so the substantive self-consistency demonstration remains tractable, and
substitution target decoding still has its ground-code staging boundary. Those
remaining staged readers are host-side computation and must not be mistaken for
completed SJAS reflection.

The fifteenth stage removes one compact axiom-citation shortcut without forcing
large compact system codes through the same relation. A red beta-citation test
required `sjas-code-arg` evidence inside the `sjas-axiom` proof branch. Reading
both `system-code` and theorem formula code relationally exceeded the focused
test bound because compact system codes are large. The green implementation
keeps compact `system-code` byte exposure staged, but reads the smaller beta
theorem-code argument through the public object code-byte relation. Beta axiom
citations therefore expose compact constructor-byte evidence for the formula
code while leaving compact system-code staging as an explicit remaining
boundary.

The sixteenth stage applies the same formula-code-side internalization to fixed
Group-0 and Group-1 axiom citations. The system-code argument remains staged for
the same size reason as beta citation, but the cited fixed axiom formula code is
read through the public code-byte relation. The fixed-axiom citation proof now
contains `sjas-code-arg` evidence for Group-0 and Group-1 theorem-code bytes
without reintroducing generated axiom facts.

The seventeenth stage removes stale side-table placeholders from compiled SJAS
program maps. Earlier stages removed the actual generated fact, formula, and
proof-target registries from predicate semantics, but the builder still attached
nil or empty top-level `:sjas/system-code`, `:sjas/fact-atoms`, and
`:sjas/proof-targets` keys for compatibility with an older program-view shape.
Compiled SJAS programs now carry only `:sjas/registry`, whose remaining payload
is source-preprocessing metadata such as the active `system-code`, code format,
and symbol-index entries. The generic procedure-call lookup relation accepts
that registry-only profile shape and still exposes the ordinary compiled clause
views.

The eighteenth stage removes the matching top-level metadata fallback from the
SJAS kernel profile. `sjas-active-systemo`, code-format selection, and
formula-code symbol-index lookup no longer read `:sjas/system-code`,
`:sjas/code-format`, or `:sjas/symbol-index-entries` directly from the compiled
program map. A manually edited program that drops `:sjas/registry` and
reintroduces those top-level keys is rejected by proof-predicate application.
This does not eliminate source preprocessing itself; it narrows the accepted
host metadata path to one explicit registry that can be audited separately from
object-level proof and coding relations.

The nineteenth stage replaces the non-`sjas-axiom` proof-predicate shortcut
through `kernel/prove-programo` with a local proof-directed checker for the
currently generated certificate shapes. `tableau-proof/3` and `subst-prf/4`
consume decoded proof constructors through `sjas-proof-check-programo` instead
of handing the decoded proof term back to the generic host proof validator.
Focused regressions redefine `kernel/prove-programo` to throw while validating
simple arithmetic certificates, reflected-clause certificates, and identity
`subst-prf/4` certificates.

The twentieth stage removes the reflected compiled-program side table from
proof-predicate validation. Reflected `pos-call` and `neg-call` proof
constructors now decode reflected clause records from active `system-code` and
bind their canonical parameters to focused call arguments. A regression strips
compiled clause lists and the old reflected-program registry entry before
validating a reflected `neg-call` certificate.

The twenty-first stage removes staged compact theorem/substitution code
readers from proof-predicate validation. Compact theorem-code reads inside
`tableau-proof/3` and `subst-prf/4` now expose `sjas-code-arg` constructor-byte
evidence, and compact substitution source/target codes use the same object
byte relation. Large U-Grounding Level-1 substitution still keeps an isolated
U-Grounding substitution-side ground-byte projection because the direct
relation was not tractable at the recorded fuel bounds and overflowed
core.logic's occurs check when walking the large substituted theorem numeral.

The twenty-second stage tightens the proof-code alphabet for reachable SJAS
proof evidence. The proof-code byte layout now includes an explicit byte
payload tag, so evidence such as `(sjas-code-arg 1 sjas-code-args-end)` can be
encoded and decoded as part of the certificate grammar. The same payload
mechanism covers observed U-Grounding canonical-byte evidence. `sjas-code-arg`,
`sjas-code-args-end`, and `sjas-ug-code-canonical-byte` are classified as
relevant code-reading evidence, while `free-close` is now encodable but remains
an unresolved equality/free-constructor closure rule for ADR-0073 Track 2b.

The twenty-third stage covers syntax-predicate proof evidence in the same
certificate alphabet. Representative `wff/1`, formula-class, and `neg-pair/2`
proofs now use encoded/classified `willard-sjas-code`, syntax relation tags,
and `sjas-neg-pair-structural` evidence. This closes a grammar gap for the
proof evidence emitted by object-code syntax predicates; it does not by itself
prove the full Track 2b correspondence theorem.

Later stages must internalize, in order:

1. removal or strict isolation of the remaining host ground-code shortcuts from
   compact system-code, U-Grounding Level-1 substitution-side decoding, and any
   remaining proof-certificate checking semantics;
2. formal completion of code-level tableau proof-tree checking against decoded
   formulas and axiom membership, either by direct arithmeticization of the
   local checker or by the ADR-0073 proof-and-test correspondence;
3. optional future validation that beta axioms are true and in the required
   Willard formula classes.

## Consequences

- This ADR deliberately does not treat the existing finite registry substrate as
  sufficient. It is a staging aid, not the end state.
- The proof predicates will become slower as registry shortcuts are removed.
  Correctness and composability with SJAS programs are the priority.
- The current branch may ship multiple commits before the full objective is
  complete. The active goal remains open until all proof machinery needed by
  object-language predicates is internalized or explicitly marked as a future
  enhancement approved by the user.

## Test Obligations

Tests must be red before implementation and then pass:

- `tableau-proof/3` over a generated theorem code returns proof evidence that
  includes structural theorem-code decoding, not only proof-code decoding;
- `subst-prf/4` over a generated theorem code returns proof evidence that
  includes structural theorem-code decoding;
- existing structural non-generated theorem-code tests continue to pass;
- formula syntax predicates succeed without generated formula/class/neg-pair
  registries;
- beta axiom citation proof evidence includes a system-code beta membership
  step, not only generated axiom-member metadata;
- reflected Group-2b axiom citation proof evidence includes a system-code
  reflected-clause membership step, not only generated axiom-member metadata;
- fixed Group-0 and Group-1 axiom citation proof evidence includes a decoded
  system/profile membership step, not generated axiom-member metadata;
- Tableau-0 Group-3 axiom citation proof evidence includes a decoded
  system/profile membership step, not generated axiom-member metadata;
- Level-1 Group-3 axiom citation proof evidence includes a decoded skeleton
  membership step, not generated axiom-member metadata;
- injected generated `axiom-member/2` facts are ignored by `sjas-axiom`
  proof-certificate checking;
- injected generated `axiom-member/2` facts are ignored by ordinary
  `axiom-member/2` query evaluation;
- ground U-Grounding proof-predicate axiom citations expose object-level
  byte-cons evidence for system/theorem code decoding, not only deterministic
  host byte extraction;
- generated proof-antecedent registries are absent, and non-`sjas-axiom`
  `tableau-proof/3` and `subst-prf/4` checks reconstruct the finite antecedent
  from `system-code`;
- ill-typed theorem/source code arguments remain rejected before expensive
  proof-target reconstruction;
- compact formula-code syntax predicate proofs include `sjas-code-arg` evidence
  for constructor-byte decoding instead of a bare staged `sjas-code-bytes`
  marker;
- representative syntax predicate proofs have no unencodable or unclassified
  proof symbols and can be passed through `sjas/proof-certificate`;
- beta `sjas-axiom` citations expose `sjas-code-arg` evidence for compact
  theorem-code decoding while compact system-code staging remains documented;
- fixed Group-0 and Group-1 `sjas-axiom` citations expose `sjas-code-arg`
  evidence for compact theorem-code decoding while compact system-code staging
  remains documented;
- compiled SJAS programs expose only the source-preprocessing registry metadata
  needed by the active profile, and generic procedure-call lookup still works
  for the registry-only compiled-program shape;
- proof predicates reject manually reintroduced top-level SJAS source metadata
  when the source-preprocessing registry is absent;
- non-`sjas-axiom` proof predicates validate currently generated proof
  certificates without reaching `kernel/prove-programo`;
- reflected `pos-call`/`neg-call` proof evidence is recovered from encoded
  `system-code`, not a reflected compiled-program side table;
- compact theorem-code and compact substitution-code reads inside proof
  predicates expose `sjas-code-arg` evidence rather than staged byte markers;
- proof-code encoding and decoding round-trip byte payload evidence such as
  `(sjas-code-arg 1 sjas-code-args-end)`;
- U-Grounding proof evidence containing `sjas-ug-code-canonical-byte` remains
  encodable and classified by the correspondence audit;
- focused tests record whether the remaining proof-predicate path still uses
  generated system/axiom registries, so later work can remove them.

## Exit Criteria

- `lein test :only proflog.willard-sjas-test/<new-focused-tests>` passes for
  each completed slice.
- Before merging this ADR, focused SJAS testing must show progress var by var,
  and any final opaque SJAS namespace run must either pass within the accepted
  runtime envelope or be explicitly recorded as deferred with rationale.
- Before declaring the full active goal complete, a completion audit must show
  that each proof-predicate coding/decoding operation needed during predicate
  application is kernel-level and object-code driven, with only source
  preprocessing left on the host side.
