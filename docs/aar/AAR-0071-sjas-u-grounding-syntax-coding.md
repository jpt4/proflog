# AAR-0071: SJAS U-Grounding Syntax Coding

- Related ADR: [ADR-0071](../adr/ADR-0071-sjas-u-grounding-syntax-coding.md)
- Branch: `adr-0071-sjas-u-grounding-syntax-coding`
- Date: 2026-05-17
- Status: completed

## Summary

ADR-0071 adds an opt-in `:code-format :u-grounding` mode for Willard SJAS
systems. In that mode, formula, system, and proof codes are ordinary
U-Grounding binary numerals over `0`, `1`, `dbl`, and `add`; the public
language no longer declares generated `code-N` constructors for formal codes.

The byte sequence is encoded as a sentinel-terminated base-64 natural. The
profile consumes that numeral by repeated six-bit byte pops, and the relational
decoder reconstructs the byte-cons equation `n = byte + 64 * tail`. This keeps
the arithmetized syntax demonstration tied to the U-Grounding arithmetic
vocabulary and makes Willard's missing total multiplication function matter for
the proof-coding layer, not only for user arithmetic examples.

## Implementation Notes

- `proflog.willard-sjas-code` now has `bytes->u-grounding-code-term`,
  `u-grounding-code-term-bytes`, and formal-code helpers for compact versus
  U-Grounding code formats.
- `proflog.willard-sjas` threads `:code-format` through systems, formula-code
  construction, proof certificates, system codes, Group-3 generation, and the
  generated SJAS registry.
- `proflog.kernel.willard-sjas-profile` accepts both compact `code-N` terms and
  U-Grounding numeral codes in syntax predicates, `tableau-proof/3`,
  `subst-code/2`, and `subst-prf/4`.
- The attempted `project` shortcut was removed. Ground public codes use a
  deterministic Clojure entry check that destructures the already-ground
  `dbl`/`add` term one byte at a time, then passes literal byte streams into
  the same structural relations. This avoids core.logic stack overflows when a
  fresh agenda/formula variable is unified with very large U-Grounding numerals;
  it is not exposed as a public proof rule and does not synthesize answers.
- A sentinel bug was fixed during implementation: payload byte `63` is legal.
  Only byte `63` with zero tail is the terminator.

## Red-Green Evidence

The first focused red test failed before the encoder existed:

```text
lein test :only proflog.willard-sjas-test/sjas-u-grounding-code-format-emits-numeral-codes-without-code-constructors
Execution error: No such var: sjas-code/bytes->u-grounding-code-term
real 12.02 s
```

During the green phase, `subst-code` over the Level-1 Group-3 fixed point also
exposed the large-code operational boundary. `ground-u-grounding-code-term-bytes`
initially returned nil for Group-3 because a payload byte equaled the sentinel,
and the fallback relation then overflowed while walking the huge code term.
After fixing the sentinel condition and adding direct focused entry for
ground SJAS code predicates, the fixed-point query passed.

A later audit found that the non-ground fallback route had lost proof evidence
for the radix equation even though it succeeded extensionally. A regression was
added to bind a U-Grounding code through equality before calling `wff/1`; it
failed until the fallback decoder preserved `sjas-ug-code-byte-cons` and
`sjas-ug-code-mul64-shift` proof steps:

```text
lein test :only proflog.willard-sjas-test/sjas-u-grounding-bound-code-decoding-uses-byte-cons-relation
Ran 1 tests containing 3 assertions.
2 failures, 0 errors.
real 22.97 s
```

The final full SJAS gate then exposed a legacy compact-code assumption in the
inconsistent-basis self-consistency demonstration. The test was still obtaining
the contradiction certificate by searching for a proof of `false` from the
whole generated axiom formula at fuel `96`, and then passing the historical
global compact `contradiction-code`. The corrected route uses the system's own
`:contradiction-code` and a checked `sjas-axiom` certificate for the reflected
`false` beta axiom. The focused selector passed after that correction:

```text
lein test :only proflog.willard-sjas-test/sjas-selfcons-demonstration-uses-substantive-proof-targets
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
real 138.76 s
```

Focused final selectors:

```text
lein test :only proflog.willard-sjas-test/sjas-u-grounding-code-format-emits-numeral-codes-without-code-constructors
Ran 1 tests containing 6 assertions.
0 failures, 0 errors.
real 18.80 s

lein test :only proflog.willard-sjas-test/sjas-u-grounding-codes-preserve-trailing-zero-byte-sequences
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 18.88 s

lein test :only proflog.willard-sjas-test/sjas-u-grounding-syntax-predicates-decode-numeral-codes
Ran 1 tests containing 6 assertions.
0 failures, 0 errors.
real 177.65 s

lein test :only proflog.willard-sjas-test/sjas-u-grounding-bound-code-decoding-uses-byte-cons-relation
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 38.18 s

lein test :only proflog.willard-sjas-test/sjas-u-grounding-tableau-proof-checks-numeral-system-theorem-and-proof-codes
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
real 20.27 s

lein test :only proflog.willard-sjas-test/sjas-u-grounding-subst-code-computes-level1-fixed-point
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
real 20.04 s
```

## Full Verification

Final suite timings are recorded in
[TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md). The full SJAS gate passed
with `Ran 35 tests containing 221 assertions`, `0 failures`, `0 errors`, and
`real 1717.35 s`. The explicit slow selector passed with `Ran 5 tests
containing 22 assertions`, `0 failures`, `0 errors`, and `real 722.20 s`.
The final fast and extended regression gates passed with `real 120.25 s` and
`real 254.93 s`, respectively.

## Files Changed

- `src/proflog/willard_sjas_code.clj`
- `src/proflog/willard_sjas.clj`
- `src/proflog/kernel/willard_sjas_profile.clj`
- `test/proflog/willard_sjas_test.clj`
- `docs/adr/ADR-0071-sjas-u-grounding-syntax-coding.md`
- `worked-examples/willard-sjas.md`
- `docs/TEST_RUNTIME_BASELINE.md`
- `LOG.md`
- ADR/AAR indexes

## Remaining Boundaries

- The public U-Grounding code format is ground-entry oriented. Open synthesis
  of large public code numerals remains operationally unsupported.
- The ground entry shortcut is an implementation boundary, not an additional
  SJAS theorem rule. It prevents core.logic from stack-overflowing while
  focusing already-ground code terms, then delegates to the structural byte and
  formula/proof decoders.
- Compact `code-N` terms remain the default performance profile. The
  U-Grounding format is the semantically stronger demonstration path.
