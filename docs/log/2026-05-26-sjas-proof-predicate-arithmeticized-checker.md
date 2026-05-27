# SJAS Proof-Predicate Arithmeticized Checker

Date: 2026-05-26

## Context

ADR-0073 Track 1 called for closing the SJAS proof-predicate internalization gap
by replacing the non-`sjas-axiom` shortcut through `kernel/prove-programo`.
That shortcut decoded a proof-code tree and then asked the host Proflog proof
kernel to validate the decoded tree against
`(and system-axioms negated-theorem)`. It was useful as an executable
correspondence experiment, but it collapsed the intended SJAS virtualization of
the deductive apparatus.

## Change

`tableau-proof/3` and `subst-prf/4` now validate non-`sjas-axiom` certificates
through `sjas-proof-check-programo`, a local SJAS proof-check relation over
decoded formula and proof constructors. The proof-predicate paths no longer
call `kernel/prove-programo target ... decoded-proof`.

Compact theorem-code decoding inside `tableau-proof/3` and `subst-prf/4` now
uses the same object code-byte relation used by syntax predicates. Accepted
compact proof-predicate certificates therefore expose `sjas-code-arg` evidence
inside `willard-sjas-theorem-code` rather than the previous staged
`(sjas-code-bytes)` marker. Compact substitution source and target decoding
also use object byte reads.

The checker currently covers the proof constructors reached by existing SJAS
certificate generation:

- top-level `conj` followed by direct SJAS arithmetic closure for ground
  arithmetic theorems such as `1 = 1` and `lt(1,2)`;
- recursive tableau `conj` and `split`;
- `univ`, `once-univ`, and `witness`;
- complementary literal `close` and `savefml`;
- reflected `pos-call` and `neg-call` procedure calls, decoded from the
  `system-code` reflected-clause bytes rather than from compiled clause lists.

Two no-kernel regressions guard the main accepted paths by redefining
`kernel/prove-programo` to throw:

- simple arithmetic certificates for `tableau-proof/3` and identity
  `subst-prf/4`;
- reflected-clause `neg-call` certificates such as `demo(1)`.
- reflected-clause `neg-call` certificates with `:clause-list`,
  `:alternative-clause-list`, `:guarded-clause-list`, and
  `:sjas/reflected-program` removed, forcing procedure-call evidence to come
  from encoded `system-code`.

A source audit also rejects reintroducing the exact
`kernel/prove-programo target` proof-predicate route, the removed
`sjas-reflected-proof-program` bridge, `program/call-clauseo` inside the SJAS
profile, and the old staged compact theorem/substitution decoder names.

## Remaining Boundary

This removes the host proof-kernel validation shortcut from SJAS proof
predicates, but it is not yet a paper-grade proof that the checker is a complete
arithmetic formalization of Willard's tableau deductive apparatus. The reflected
procedure-call boundary is now system-code-driven for the currently generated
single-clause proof shapes. A stricter future stage still needs broader
coverage for grouped multi-clause alternatives and a proof that the decoded
clause-call relation preserves the relevant tableau intensional invariants.

The finite symbol-index table remains a source-preprocessing boundary. Formula
and system codes store relation/function indexes, not full structural names or
declarations. This is acceptable only if symbol identity is proven irrelevant
up to a fixed injective coding of the finite language, or if the language
signature itself is internalized into the system code. The current code treats
that table as nominal preprocessing metadata, so it remains a Track-1/Track-2b
obligation rather than completed U-Grounding internalization. The current
justification is recorded in
[SJAS Symbol-Table Isomorphism Justification](2026-05-26-sjas-symbol-table-isomorphism-justification.md):
the table is admissible as a fixed codebook only up to kind/arity-preserving
signature isomorphism, not as a freestanding nominal fact.

The 2026-05-27 public-code byte internalization step removed the isolated
U-Grounding substitution-side byte projection described in the original version
of this note. Substitution-code validation now decodes source and target
formula codes through the formal object relation and uses a fused
substitution-plus-alpha-equivalence relation for the diagonal check. A larger
demo system with reflected clauses still overflows core.logic while decoding
and comparing the 280-byte Group-3 target through the fully relational path,
so the remaining boundary is runtime tractability rather than host-side byte
projection. See
[SJAS Public Code Byte Internalization](2026-05-27-sjas-public-code-byte-internalization.md).

Operationally, the current checker is proof-directed and rejects unsupported
proof constructors instead of falling back to the host kernel.

A later proof-code alphabet slice also made compact code-reader proof evidence
encodable: `sjas-code-arg` and `sjas-code-args-end` are now proof symbols, and
the proof-code layout has an explicit byte payload tag. That lets the
constructor-byte evidence emitted by this checker round-trip through the
certificate grammar. The observed U-Grounding `sjas-ug-code-canonical-byte`
evidence is covered by the same payload mechanism. `free-close` is likewise
encodable but remains unresolved for correspondence proof purposes.

## Verification

- Red regression observed before implementation:
  `sjas-proof-predicates-check-simple-arithmetic-certificates-without-kernel-validator`
  failed because both proof predicates reached `kernel/prove-programo`.
- Red regression observed before procedure-call extension:
  `sjas-proof-predicates-check-reflected-clause-certificates-without-kernel-validator`
  failed because `neg-call` was not yet handled by the local checker.
- Red regression observed before system-code reflected-call lookup:
  `sjas-proof-predicates-check-reflected-calls-from-system-code` failed when
  compiled clause lists and the reflected-program registry entry were removed.
- Red regressions observed before compact theorem-code staging removal:
  `sjas-tableau-proof-checks-kernel-certificates` and
  `sjas-subst-prf-checks-identity-substitution-certificates` accepted proof
  predicates without `sjas-code-arg` evidence under `willard-sjas-theorem-code`.
- Red source audit observed before substitution staging cleanup:
  `sjas-profile-source-audit-rejects-host-proof-checker-route` found the old
  staged compact theorem and substitution decoder names.
- U-Grounding substitution boundary probe:
  direct object decoding of the Level-1 fixed-point substitution source failed
  at fuels 240, 500, 1000, and 2000 during the first implementation attempt.
  The 2026-05-27 follow-up fixed the U-Grounding sentinel handling and replaced
  the isolated byte projector with relation-backed source/target decoding plus
  fused substitution/alpha comparison.
- Green focused checks:
  - `sjas-proof-codes-encode-byte-payload-evidence`
  - `sjas-proof-code-decoder-round-trips-byte-payload-evidence`
  - `sjas-proof-codes-encode-u-grounding-canonical-byte-evidence`
  - `sjas-proof-predicates-check-simple-arithmetic-certificates-without-kernel-validator`
  - `sjas-proof-predicates-check-reflected-clause-certificates-without-kernel-validator`
  - `sjas-proof-predicates-check-reflected-calls-from-system-code`
  - `sjas-tableau-proof-checks-kernel-certificates`
  - `sjas-subst-prf-checks-identity-substitution-certificates`
  - `sjas-subst-code-relates-structural-substitution-codes`
  - `sjas-u-grounding-subst-code-computes-level1-fixed-point`
  - `sjas-symbol-table-is-irrelevant-up-to-signature-isomorphism`
  - `sjas-formal-codes-are-godel-byte-terms`
  - `sjas-profile-source-audit-rejects-host-proof-checker-route`
  - structural non-generated theorem-code proof-predicate checks
  - Group-3 self-consistency substantive proof check
- `git diff --check`
- `lein test-proflog-fast`: 152 tests, 570 assertions
- `lein test-proflog-extended`: 68 tests, 203 assertions
