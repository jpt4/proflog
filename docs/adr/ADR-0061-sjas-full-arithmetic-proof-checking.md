# ADR-0061: SJAS Binary Arithmetic and Proof Checking

- Status: completed
- Date: 2026-05-13
- Branch: `adr-0061-sjas-full-arithmetic-proof-checking`
- AAR: [AAR-0061](../aar/AAR-0061-sjas-full-arithmetic-proof-checking.md)

## Context

ADR-0060 deliberately shipped a small Willard-SJAS substrate. It generated a
finite axiom basis, exposed the `:willard-sjas-tableau0` and
`:willard-sjas-level1` proof profiles, and demonstrated reflected
self-consistency queries. That MVP also left two important shortcuts in place:

- finite named numerals, including `zero`, `one`, `two`, `three`, `four`, and
  `six`;
- finite `mult/3` and order facts plus a miniature `mini-closed` proof
  certificate predicate.

Those shortcuts are not adequate for the next SJAS goal. The Willard corpus
uses binary arithmetic for the U-grounding base. The later Type-A witnesses
name constants `"0"` and `"1"` and build larger numerals through ordinary
function composition over addition and doubling. They also include subtraction,
division, maximum, logarithm, root, and `count(x,j)`, where `count` is the
number of `1` bits among the rightmost `j` bits of `x`. The local 2001,
2005, 2011, and 2013 papers differ in some historical presentation details,
but they agree that the implemented SJAS language must not remain a finite
sample of hand-enumerated arithmetic facts.

The proof-certificate shortcut is likewise inadequate. Proflog already has a
pure relational tableau kernel and proof terms. The SJAS `tableau-proof/3`
relation should use that proof relation, or a structurally equivalent
kernel-level checker, rather than a one-case `mini-closed` placeholder.

## Decision

Implement ADR-0061 as the promotion of the ADR-0060 MVP to a binary
U-grounding and proof-checking SJAS profile.

Object-language numerals will use constants whose symbols are exactly
`"0"` and `"1"`. Clojure-facing convenience vars may still be named `zero`,
`one`, `two`, and so on, but those vars must expand to terms built from the
object constants and U-grounding function composition. Larger numbers are not
base constants.

The official U-grounding arithmetic layer will be relation-backed:

- `add/2` and `dbl/1` are total growth functions;
- `pred/1`, `sub/2`, `div/2`, `max/2`, `log/1`, `root/2`, and `count/2` are
  total non-growth functions using the later Type-A definitions;
- `mult/3`, `leq/2`, and `lt/2` are graph/order relations, not finite
  generated facts.

Implementation should reuse Proflog's translated miniKanren binary arithmetic
where it matches the target relation and add SJAS-specific relational wrappers
for the non-growth functions that are not already present.

The SJAS proof-certificate checker will replace the `mini-closed` placeholder.
For this ADR, "full checker" means full coverage of the current Proflog kernel
proof-term language needed to validate semantic-tableau proofs from the
generated SJAS basis. The checker may call the existing kernel relation with a
decoded certificate proof term; it must not call a host-side proof oracle,
special-purpose Clojure predicate, or equality-fragment host prover. This keeps
proof checking at the kernel relation boundary while avoiding a second tableau
implementation.

## Consequences

Existing examples that mention `zero` and `one` as Clojure helper names may
remain source-compatible, but the object language they build must now contain
`0` and `1` constants. Tests and worked examples should show the descent from
helpers to the tagged AST so the distinction is visible.

The SJAS profile will become slower than the MVP on some arithmetic examples,
because it will search arithmetic relations instead of selecting from a small
fact table. That cost is acceptable if timings are recorded and slow reverse or
partial-synthesis probes are kept out of the default fast suite.

The proof-certificate checker will initially validate Proflog kernel proof
terms, not a byte-for-byte formalization of every historical Willard proof-list
encoding variant. The AAR must state this boundary explicitly.

## Test Obligations

Tests must be written red before implementation. The minimum suite is:

- language tests showing that the SJAS base constants are `0` and `1`, and
  that larger numerals are function-composed terms rather than constants;
- arithmetic tests for forward, answer, and partial-synthesis modes across
  `add`, `dbl`, `pred`, `sub`, `div`, `max`, `log`, `root`, `count`, `mult`,
  `leq`, and `lt`;
- false arithmetic tests showing invalid equations and graph facts are not
  proved;
- certificate tests showing a real kernel proof certificate is accepted and
  malformed, wrong-theorem, and wrong-system certificates are rejected;
- route-audit tests rejecting `mini-closed`, `malformed`, finite `mult-facts`,
  finite `order-facts`, `prove-program-host`, and other host-checker shortcuts
  from the SJAS profile implementation;
- documentation tests or source assertions keeping the worked example aligned
  with the public API.

## Exit Criteria

- `lein test-proflog-sjas` passes and records runtimes for its focused checks.
- `lein test-proflog-fast` passes.
- `lein test-proflog-extended` passes before merge because proof-profile,
  equality, and query behavior are touched.
- The worked SJAS example and tutorial references describe binary numerals,
  relation-backed arithmetic, certificate checking, successful test outcomes,
  and remaining performance/faithfulness boundaries.
- Write an AAR for ADR-0061 before merging.
