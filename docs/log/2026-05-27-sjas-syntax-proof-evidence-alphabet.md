# SJAS Syntax Proof Evidence Alphabet

Date: 2026-05-27

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Purpose

This note records a Track 1/Track 2a cleanup for proof evidence emitted by the
SJAS syntax predicates: `wff/1`, formula-class predicates, and `neg-pair/2`.
These predicates already read compact formula-code bytes through object-level
`sjas-code-arg` evidence, but their returned proof terms still contained
symbols outside the declared proof-code alphabet:

- `willard-sjas-code`
- `wff`
- `delta-star-0-code`
- `pi-star-1-code`
- `sigma-star-1-code`
- `neg-pair`
- `sjas-neg-pair-structural`

## Change

Those symbols are now in the SJAS proof-symbol alphabet and are classified as
relevant code/syntax evidence in the correspondence audit. The existing
`sjas-syntax-predicates-decode-formula-godel-codes` regression now also checks
that representative `wff`, `delta-star-0-code`, and `neg-pair` proof terms
have no unencodable or unclassified symbols. It also verifies that a successful
`wff` proof term can be passed through `sjas/proof-certificate`.

This does not prove that every syntax predicate rule is a completed
correspondence theorem. It closes the narrower grammar gap: current syntax
predicate evidence is no longer outside the proof-code vocabulary merely
because it names the syntax predicate or structural neg-pair proof step.

## Verification

- Red focused regression:
  `sjas-syntax-predicates-decode-formula-godel-codes` initially reported
  `willard-sjas-code`, `wff`, `delta-star-0-code`, `neg-pair`, and
  `sjas-neg-pair-structural` as unencodable/unclassified, and
  `sjas/proof-certificate` threw on `willard-sjas-code`.
- Green focused checks:
  - `sjas-syntax-predicates-decode-formula-godel-codes`
  - `lein test-vars proflog.sjas-correspondence-test`: 7 tests, 22 assertions
- `lein test-proflog-fast`: 153 tests, 575 assertions
- `lein test-proflog-extended`: 68 tests, 203 assertions
