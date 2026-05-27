# SJAS Proof-Code Byte Payloads

Date: 2026-05-27

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Purpose

This note records a Track 1/Track 2a cleanup of the SJAS proof-certificate
alphabet. Earlier reachability audits found proof evidence such as
`(sjas-code-arg 1 sjas-code-args-end)` and `(free-close)` in current SJAS proof
terms, while `proof-symbols` could not encode those tags. The code-reader case
was worse than a missing symbol: `sjas-code-arg` carries a concrete base-64
byte payload, and the proof-code byte layout had no payload form for that
small number.

A follow-up U-Grounding probe found the same grammar issue on actual
U-Grounding `tableau-proof/3` evidence: `sjas-ug-code-canonical-byte` appears
with a byte payload under the object-level U-Grounding code decoder.

## Change

The proof-code byte layout now has an explicit `proof-byte-tag`. A certificate
payload integer is accepted only when it is one base-64 byte, `0 <= n < 64`.
The proof decoder relation consumes that tag and reconstructs the same byte
payload in the decoded proof term.

The declared proof-symbol alphabet now also includes:

- `sjas-code-arg`
- `sjas-code-args-end`
- `free-close`
- `sjas-ug-code-canonical-byte`

The correspondence audit classifies `sjas-code-arg` and
`sjas-code-args-end` as relevant code-reading evidence. It also classifies
`sjas-ug-code-canonical-byte` as relevant U-Grounding code-reading evidence.
`free-close` is now encodable and classified, but remains unresolved: Track 2b
still must prove it as an admitted equality/free-constructor closure rule,
macro-expand it, prove it unreachable in the covered fragment, or exclude that
fragment.

## Boundary

This does not complete the proof-certificate grammar audit. The older proof-tag
inventory still lists other possible helper tags from equality, guarded calls,
U-Grounding bit peels, and profile header paths. This slice closes the
reachable compact-code-reader payload gap, closes the observed U-Grounding
canonical-byte payload gap, and gives `free-close` an explicit classification
point.

## Verification

- Red audit regression: `proof-term-audit-classifies-reachable-code-reader-and-free-closure-tags`
  initially reported `sjas-code-arg`, `sjas-code-args-end`, and `free-close` as
  unencodable/unclassified.
- Red encoder regression: `sjas-proof-codes-encode-byte-payload-evidence`
  initially failed because `proof-byte-tag` did not exist and the encoder could
  not serialize the byte payload.
- Green focused checks:
  - `sjas-proof-codes-encode-byte-payload-evidence`
  - `sjas-proof-code-decoder-round-trips-byte-payload-evidence`
  - `sjas-proof-codes-encode-u-grounding-canonical-byte-evidence`
  - `sjas-u-grounding-tableau-proof-checks-numeral-system-theorem-and-proof-codes`
  - `proof-symbol-audit-classifies-every-encoded-certificate-symbol`
  - `proof-term-audit-classifies-reachable-code-reader-and-free-closure-tags`
  - `proof-term-audit-classifies-u-grounding-canonical-byte-evidence`
- `lein test-vars proflog.sjas-correspondence-test`: 7 tests, 22 assertions
- `lein test-proflog-fast`: 153 tests, 575 assertions
- `lein test-proflog-extended`: 68 tests, 203 assertions
