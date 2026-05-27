# AAR-0068: SJAS Structural Theorem-Code Targets

- Date: 2026-05-14
- Related ADR: [ADR-0068](../adr/ADR-0068-sjas-structural-theorem-targets.md)
- Branch: `adr-0068-sjas-theorem-code-targets`
- Status: completed

## Outcome

ADR-0068 removes the generated-formula registry as the only way for SJAS proof
predicates to obtain a theorem target. `tableau-proof/3` and `subst-prf/4` now
have structural theorem-code routes:

1. Decode the public theorem code term into formula-code bytes.
2. Parse those bytes into the ADR-0067 internal formula tree.
3. Compute the NNF complement structurally.
4. Translate the complement into the ordinary Proflog kernel AST using a fixed
   code-index-to-nom map.
5. Run the existing kernel proof checker against
   `system-axiom-formula AND decoded-complement`.

Generated formula entries are still used first for generated Group axioms and
large generated self-reference formulas. The new structural route is what lets
non-generated theorem codes participate in proof checking.

## Evidence

Red evidence for `tableau-proof/3`:

```text
lein test :only proflog.willard-sjas-test/sjas-tableau-proof-checks-structural-non-generated-theorem-codes
Ran 1 tests containing 4 assertions.
1 failures, 0 errors.
real 65.90 s
```

Red evidence for `subst-prf/4`:

```text
lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-structural-non-generated-theorem-codes
Ran 1 tests containing 4 assertions.
1 failures, 0 errors.
real 19.75 s
```

Focused post-implementation runs:

```text
lein test :only proflog.willard-sjas-test/sjas-tableau-proof-checks-structural-non-generated-theorem-codes
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
real 111.13 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-structural-non-generated-theorem-codes
Ran 1 tests containing 4 assertions.
0 failures, 0 errors.
real 175.84 s
```

Explicit slow selector:

```text
lein test-proflog-sjas-slow
Ran 4 tests containing 16 assertions.
0 failures, 0 errors.
real 452.96 s
```

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 25 tests containing 182 assertions.
0 failures, 0 errors.
real 1947.15 s
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 129.32 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 287.48 s
```

## What Worked

- `tableau-proof(system, code(lt(1,2)), cert)` now succeeds when `cert` is the
  real kernel certificate produced by proving `lt(1,2)` from the active SJAS
  basis.
- The same certificate is rejected for `code(lt(2,1))`.
- `subst-prf(system, code(lt(1,2)), code(lt(1,2)), cert)` now succeeds through
  structural identity substitution plus structural theorem decoding.
- The same substitution-proof certificate is rejected when the theorem code is
  replaced by `code(lt(2,1))`.
- Existing generated axiom-citation, fixed-point, and proof-certificate tests
  remained green.

## Remaining Boundaries

- The implementation still reuses Proflog's kernel AST proof checker after
  decoding theorem codes. It does not yet implement a separate proof-list or
  Tab-1 theorem-reuse checker over code terms alone.
- The structural theorem tests are intentionally slow and marked `^:slow`.

Follow-up: ADR-0069 closed the non-identity substitution boundary by replacing
generated `subst-code` entries with structural diagonal formula-code
substitution.
