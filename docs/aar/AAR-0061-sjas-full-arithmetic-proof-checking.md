# AAR-0061: SJAS Binary Arithmetic and Proof Checking

- Date: 2026-05-13
- Related ADR: [ADR-0061](../adr/ADR-0061-sjas-full-arithmetic-proof-checking.md)
- Branch: `adr-0061-sjas-full-arithmetic-proof-checking`
- Status: completed

## Outcome

ADR-0061 replaced the ADR-0060 finite SJAS arithmetic/certificate shortcuts
with a binary U-grounding profile and a kernel-backed proof-certificate
checker.

The public SJAS builder now uses object-language numeral constants `0` and
`1`. Clojure helper vars such as `sjas/two` remain, but they expand to composed
terms over `dbl` and `add` rather than to object constants named `two`,
`three`, and so on.

The profile namespace `proflog.kernel.willard-sjas-profile` now supplies
kernel-interleaved SJAS theory rules for:

- arithmetic equality and disequality over binary numerals;
- `mult/3`, `leq/2`, and `lt/2`;
- generated finite coding facts such as `axiom-member/2`;
- structural proof-code decoding and `tableau-proof/3` validation by calling
  `kernel/prove-programo` with the decoded proof term supplied.

The answer overlay now has an optional theory hook parallel to the ordinary
kernel hook. `proflog.willard-sjas/query-answers` binds that hook so SJAS
arithmetic can participate in answer and partial-synthesis modes.

## Evidence

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 11 tests containing 110 assertions.
0 failures, 0 errors.
real 51.22
```

Regression gates, run after the focused SJAS suite:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 236.58
```

```text
lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 572.25
```

The focused suite now covers:

- binary object constants `0` and `1`;
- canonical composed helper numerals;
- closed U-grounding equations for `add`, `dbl`, `pred`, `sub`, `div`, `max`,
  `log`, `root`, and `count`;
- graph/order relations `mult`, `leq`, and `lt`;
- invalid arithmetic equations and graph facts;
- answer synthesis for missing multiplicands;
- partial synthesis for `add(z,3)=7`;
- kernel-checked proof certificates accepted for the right theorem code and
  rejected for wrong theorem and malformed certificate cases;
- a source audit against `mini-closed`, `malformed`, finite `mult-facts`,
  finite `order-facts`, and host proof checker shortcuts.

## Follow-up: Reflected and External Query Boundary

On 2026-05-14, the SJAS worked example was expanded to distinguish system
construction from query-triggered evaluation. That exposed a regression in the
ordinary procedure-call path for SJAS systems: `sjas/system` attached profile
metadata to the compiled program map, but `proflog.program` lookup expected the
exact unannotated map shape. Generated SJAS fact closures still worked, while
ordinary reflected and external user clauses were not queryable through the
Procedure Call Rule.

The fix keeps procedure-call lookup relational but admits the SJAS-annotated
compiled-program shape. The focused test suite now asserts that reflected
clauses remain executable procedure clauses and that external clauses are
queryable outside the reflected basis.

```text
lein test-proflog-sjas
Ran 11 tests containing 112 assertions.
0 failures, 0 errors.
real 15.40
```

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 71.06
```

```text
lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 198.45
```

## Follow-up: Composite as Axiom vs Procedure

The worked example now includes paired `composite/1` systems to document when a
definition belongs in Group 2 rather than Group-2b. The beta version adds
`forall x. mult(2,2,x) -> composite(x)` as a Group-2 axiom. It proves
`composite(4)` through `sjas/query-succeeds`, but direct Procedure Call Rule
evaluation has no executable `composite/1` clause and returns no proof. The
reflected version adds the same definition as a clause, so it is executable,
reflected as Group-2b, theorem-usable, and answer-capable for `x = 4`.

The broader existential factorization definition of `composite` is expressible,
but an exploratory 120 s wrapper did not return a result for the general
`exists y z. y != 1 and z != 1 and mult(y,z,x)` version. The promoted example
therefore stays bounded and records the current operational boundary.

```text
lein test-proflog-sjas
Ran 12 tests containing 119 assertions.
0 failures, 0 errors.
real 34.17
```

## Implementation Notes

The generic gamma closed-term fallback was too expensive for the enlarged SJAS
signature. The SJAS proof profile now calls the kernel entrypoint that accepts
an explicit empty gamma source. This avoids materializing depth-2 Herbrand
terms over proof-code constructors before focused proof search begins.

Group-1 arithmetic records remain reflected and code-addressable, but the
theorem helper no longer places those arithmetic equalities into every ordinary
theorem antecedent. Arithmetic is now profile theory behavior. Leaving
Group-1 equations as ordinary positive free-constructor equalities allowed the
generic equality rule to misread true arithmetic equations such as
`sub(2,1)=1` as constructor clashes.

The bounded contradiction probe now checks a concrete certificate candidate.
Open proof-code synthesis asks the checker to enumerate possible certificates
and remains an extended search problem, not a focused timing smoke test.

## Documentation

Updated:

- [Willard SJAS Binary Profile Example](../../worked-examples/willard-sjas.md);
- README SJAS pointer;
- [Proflog Greenfield User Guide](../USER_GUIDE.md);
- worked example index.

## Remaining Boundaries

- The proof-code encoding covers current Proflog kernel proof terms used by
  these examples. It is not a byte-for-byte implementation of every historical
  Willard proof-list encoding.
- Tab-1/proof-list theorem reuse is still not implemented.
- Open proof-certificate synthesis is not practical in the focused suite.
- Passing bounded contradiction probes do not establish Willard's external
  consistency-preservation metatheorem.
