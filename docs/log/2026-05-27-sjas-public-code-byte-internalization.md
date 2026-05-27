# SJAS Public Code Byte Internalization

Date: 2026-05-27

## Context

ADR-0073 Track 1 requires the SJAS proof predicates and substitution
machinery to operate over arithmetized code inside the SJAS object-language
relations. The previous checker had already removed the host
`kernel/prove-programo` proof-validator shortcut, but public compact and
U-Grounding code reads still had deterministic host byte projectors for
already-ground terms.

That boundary mattered because `system-code`, theorem-code, proof-code, and
substitution-code arguments are precisely the numerals over which SJAS
self-reference quantifies. Projecting their byte strings outside the relation
made the public code reader an extrasystemic registry lookup rather than an
inspectable arithmetic step.

## Change

The deterministic public-code byte projectors were removed. Compact public
code terms and U-Grounding numeral code terms now pass through
`sjas-formal-code-byteso`, so accepted reads expose object-level code-byte or
U-Grounding byte evidence.

For long `system-code` and Group-3 formula-code arguments, the profile uses
`sjas-public-code-bytes-summaryo`. This relation still evaluates
`sjas-formal-code-byteso`; it only compresses the returned proof payload to the
code-format marker after the semantic byte relation has succeeded. That avoids
exponential core.logic proof reification without reintroducing host-side byte
projection.

The U-Grounding decoder also now treats byte `63` as the sentinel only when it
appears with zero tail. This matches
`willard_sjas_code/u-grounding-code-value->bytes`, where byte `63` may appear
inside the payload and only the final sentinel terminates the numeral.

Substitution-code validation no longer has a U-Grounding ground-byte boundary.
`sjas-subst-code-anyo` decodes source and target formula codes through the
formal object relation, then checks the diagonal substitution by a fused
substitution-plus-alpha-equivalence relation. The fused relation avoids
materializing a large intermediate substituted formula with repeated quoted
code payloads while preserving the same structural comparison obligation.

## Remaining Boundary

The minimal Level-1 U-Grounding substitution relation now succeeds without the
old host byte projector. The larger demo system that includes reflected clauses
can still overflow core.logic while decoding and comparing the 280-byte Group-3
target formula through the fully relational path. That is a runtime
evaluability boundary in the current Proflog/core.logic implementation, not a
reintroduced semantic shortcut.

The broader Track 1 goal is therefore advanced but not complete. Remaining
obligations include broader proof-constructor coverage, quantified proof of
the finite signature/codebook boundary, and enough runtime tractability to
exercise larger SJAS instances through the fully internalized relations.

## Verification

- Red source audit before implementation:
  `sjas-profile-source-audit-rejects-host-proof-checker-route` found the
  deterministic public-code and U-Grounding byte projectors.
- Red U-Grounding substitution regression before the sentinel fix:
  byte value `63` inside payload was incorrectly rejected as a sentinel.
- Green focused checks:
  - `sjas-profile-source-audit-rejects-host-proof-checker-route`
  - `sjas-tableau-proof-accepts-axiom-citation-certificates`
  - `sjas-subst-code-relates-structural-substitution-codes`
  - `sjas-u-grounding-syntax-predicates-decode-numeral-codes`
  - `sjas-u-grounding-subst-code-computes-level1-fixed-point`
  - `sjas-u-grounding-tableau-proof-checks-numeral-system-theorem-and-proof-codes`
  - `sjas-tableau-proof-cites-level1-group-three-from-system-code`
  - `sjas-tableau-proof-cites-tableau0-group-three-from-system-code`
  - `sjas-proof-predicates-check-simple-arithmetic-certificates-without-kernel-validator`
  - `sjas-proof-predicates-check-reflected-clause-certificates-without-kernel-validator`
  - `sjas-proof-predicates-check-reflected-calls-from-system-code`
  - `sjas-subst-prf-checks-selfcons-fixed-point-certificate`
  - `sjas-subst-code-computes-general-formula-code-substitution`
- `git diff --check`
- `lein test-proflog-fast`: 153 tests, 575 assertions
- `lein test-proflog-extended`: 68 tests, 203 assertions
