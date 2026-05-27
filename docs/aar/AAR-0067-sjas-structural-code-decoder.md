# AAR-0067: SJAS Structural Formula-Code Decoder

- Date: 2026-05-14
- Related ADR: [ADR-0067](../adr/ADR-0067-sjas-structural-code-decoder.md)
- Branch: `adr-0067-sjas-code-decoder`
- Status: completed

## Outcome

ADR-0067 adds a structural decoder for SJAS formula-code byte streams inside
the Willard proof profile. The decoder reads the same compact public code terms
used by `tableau-proof/3`, parses formula and term tags, validates symbol
indexes against the active finite SJAS coding context, and builds an internal
syntax tree for code-level predicates.

The following predicates now accept well-formed formula codes that were not
pre-enumerated as generated Group axioms or complements:

- `wff/1`;
- `delta-star-0-code/1`, with the same structural relation available for
  Pi-star-1 and Sigma-star-1 formulas;
- `neg-pair/2` for ordinary NNF complement pairs;
- identity `subst-code(c,c)`.

The generated fixed-point substitution entry remains in place:

```text
selfcons-skeleton-code -> group-three-code
```

This keeps the Level-1 `SelfCons1` path intact while removing finite registry
enumeration as the only way to recognize syntax and identity substitution.

## Evidence

Red evidence:

```text
lein test :only proflog.willard-sjas-test/sjas-structural-code-predicates-accept-non-generated-formula-codes
Ran 1 tests containing 5 assertions.
4 failures, 0 errors.
real 14.80 s
```

The failing formula was `lt(1,2)`: a valid closed formula in the active SJAS
language but not one of the generated axiom/complement entries.

Focused post-implementation run:

```text
lein test :only proflog.willard-sjas-test/sjas-structural-code-predicates-accept-non-generated-formula-codes
Ran 1 tests containing 5 assertions.
0 failures, 0 errors.
real 98.85 s
```

Nearby regression selectors:

```text
lein test :only proflog.willard-sjas-test/sjas-syntax-predicates-decode-formula-godel-codes
Ran 1 tests containing 6 assertions.
0 failures, 0 errors.
real 23.44 s

lein test :only proflog.willard-sjas-test/sjas-subst-code-relates-generated-substitution-codes
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 55.64 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-uses-substitution-code-independently-of-theorem-code
Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
real 33.44 s
```

Explicit slow selector:

```text
lein test-proflog-sjas-slow
Ran 2 tests containing 8 assertions.
0 failures, 0 errors.
real 170.85 s
```

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 23 tests containing 174 assertions.
0 failures, 0 errors.
real 767.20 s
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 129.36 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 299.24 s
```

## What Worked

- The red test confirmed the previous implementation was registry-bound: the
  code for `lt(1,2)` was not generated, so `wff`, class recognition,
  `neg-pair`, and identity `subst-code` all failed.
- The structural decoder now validates code shape from bytes rather than from
  whole-formula entries.
- The finite active symbol table remains fixed at source-translation time, so
  structural code parsing is still relative to the declared SJAS language.
- Existing generated axiom and fixed-point substitution checks remained green.

## Remaining Boundaries

- `tableau-proof/3` still uses a theorem-code-to-kernel-formula bridge for
  proof targets. ADR-0067 improves syntax and substitution predicates, but a
  later ADR must let proof checking build theorem targets structurally from
  arbitrary theorem codes or replace that bridge with a fully code-level proof
  checker.
- The new semantic test is intentionally slow and marked `^:slow`; it is useful
  because it exercises object-language code predicates rather than a surface
  helper.

## Follow-Up

- ADR-0068 later added structural theorem-target routes for `tableau-proof/3`
  and `subst-prf/4`, closing the generated-formula-registry dependency for
  ordinary non-generated theorem codes. A fully code-level proof-list/Tab-1
  checker remains outside ADR-0068.
