# AAR-0070: SJAS Byte-Sequence Coding Audit

- Related ADR: [ADR-0070](../adr/ADR-0070-sjas-byte-sequence-coding-audit.md)
- Branch: `adr-0070-sjas-tableau-proof-coding-audit`
- Date: 2026-05-15
- Status: completed

## Summary

ADR-0070 tightened the SJAS code representation after rechecking Willard's
proof-coding requirements. The selected finite `IS#_D(beta)` profile remains
ordinary semantic tableaux as implemented by the Proflog kernel, with Tab-1 and
Tab-k proof-list theorem reuse deferred. The proof predicate is still a
code-level predicate over theorem and proof certificate terms, not a host-side
formula lookup.

The implementation now preserves byte strings directly when constructing
formula, system, and proof code terms. The previous source-boundary path
converted bytes to a natural and then back to bytes; that canonicalized away
trailing zero bytes. That was not a hash, but it was still a lossy sequence
normalization. Since Willard-style syntax/proof coding is a sequence coding,
that boundary had to be fixed.

## Red-Green Evidence

The new focused regression failed before the implementation change:

```text
lein test :only proflog.willard-sjas-test/sjas-formula-codes-preserve-trailing-zero-embedded-code-payloads

FAIL ... this regression must exercise a formula code whose final byte is zero
expected: (= 0 (last formula-bytes))
  actual: (not (= 0 1))

FAIL ... expected structural `wff/1` decoding
actual: ()
```

After the encoder fix:

```text
lein test :only proflog.willard-sjas-test/sjas-formula-codes-preserve-trailing-zero-embedded-code-payloads
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.

lein test :only proflog.willard-sjas-test/sjas-byte-codes-preserve-sequence-length-and-trailing-zeroes
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.

lein test :only proflog.willard-sjas-test/sjas-proof-codes-are-byte-strings-with-symbol-bit-lower-bound
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
```

## Full Verification

```text
lein test-proflog-sjas-slow
Ran 5 tests containing 22 assertions.
0 failures, 0 errors.
real 16m40.470s

lein test-proflog-sjas
Ran 29 tests containing 195 assertions.
0 failures, 0 errors.
real 32m32.713s

lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 2m37.518s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 5m33.947s
```

## Files Changed

- `src/proflog/willard_sjas_code.clj`
- `src/proflog/willard_sjas.clj`
- `test/proflog/willard_sjas_test.clj`
- `docs/adr/ADR-0070-sjas-byte-sequence-coding-audit.md`
- `docs/log/2026-05-15-sjas-proof-coding-citations.md`
- `worked-examples/willard-sjas.md`
- `LOG.md`
- ADR/AAR indexes

## Remaining Boundaries

- Proflog still selects pure ordinary semantic tableaux for `D`; Tab-1/Tab-k
  proof-list theorem reuse is not implemented here.
- Proof certificates encode Proflog kernel ordinary-tableau proof terms. They
  are not byte-for-byte copies of every historical Willard proof-tree notation,
  but they are inspectable byte strings checked by the kernel proof relation.
- Open proof-code synthesis remains operationally expensive. Ground proof-code
  checking is covered by the SJAS suite.
