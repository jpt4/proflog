# AAR-0065: SJAS SelfCons Substitution Fixed Point

- Date: 2026-05-14
- Related ADR: [ADR-0065](../adr/ADR-0065-sjas-selfcons-substitution-fixpoint.md)
- Branch: `adr-0065-sjas-selfcons-subst-fixpoint`
- Status: completed

## Outcome

ADR-0065 corrects the Level-1 `SelfCons1` fixed-point shape. The generated
Group-3 sentence is now built from a skeleton `Gamma_1(g)`, the skeleton is
encoded, and the final sentence uses that skeleton code as the second argument
to `subst-prf/4`.

The finite `subst-prf/4` boundary now contains ordinary identity entries for
generated formulas plus the required fixed-point entry:

```text
system-code, selfcons-skeleton-code -> group-three-code
```

The branch also adds a formal `sjas-axiom` certificate. The proof profile
accepts that certificate only when the decoded theorem code is a generated
`axiom-member/2` fact for the active reflected system. This gives Group-3 an
object-level axiom-citation proof line without introducing a host-side theorem
oracle.

## Evidence

Red evidence:

```text
lein test :only proflog.willard-sjas-test/sjas-level1-group-three-uses-selfcons-skeleton-code
FAIL: skeleton code was absent and Group-3 used system-code as the substitution argument.

lein test :only proflog.willard-sjas-test/sjas-tableau-proof-accepts-axiom-citation-certificates
ERROR: Unsupported proof symbol in SJAS certificate {:symbol sjas-axiom}
real 11.26 s
```

An attempted generic Level-1 Group-3 proof was stopped after about `7m44s`
without a result. That probe showed that requiring the generic kernel to
rediscover a large proof of a generated axiom was the wrong operational test
for the proof predicate. The accepted proof object is now an explicit
axiom-citation certificate checked against `axiom-member/2`.

Focused selector reruns after implementation:

```text
lein test :only proflog.willard-sjas-test/sjas-tableau-proof-accepts-axiom-citation-certificates
Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
real 46.28 s

lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-selfcons-fixed-point-certificate
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 72.66 s

lein test-proflog-sjas-slow
Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
real 82.81 s
```

Focused SJAS gate:

```text
lein test-proflog-sjas
Ran 20 tests containing 162 assertions.
0 failures, 0 errors.
real 406.83 s
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
real 93.95 s

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 222.85 s
```

## What Worked

- Level-1 systems expose `:selfcons-skeleton-code` and
  `:selfcons-skeleton-formula`.
- Generated Level-1 Group-3 contains `subst-prf(system-code,
  selfcons-skeleton-code, x, p)` and the corresponding `y, q` call.
- `subst-prf(system-code, selfcons-skeleton-code, group3-code,
  sjas-axiom-certificate)` succeeds.
- The same `subst-prf/4` query rejects `system-code` in the substitution-code
  position.
- `tableau-proof/3` accepts `sjas-axiom` only for theorem codes listed in the
  generated `axiom-member/2` facts.
- Proof-code encoding now supports a wide proof-symbol form for symbols whose
  table index does not fit in one base-64 byte.

## Remaining Boundaries

- ADR-0066 followed up by exposing the finite generated substitution boundary
  as `subst-code/2`. A fully general code-level `Subst` evaluator over
  arbitrary formula-code variables remains open.
- The finite `IS#_D(beta)` substrate is now fixed-point shaped, but it is not a
  mechanized proof of Willard's external consistency-preservation theorem.
- Open proof-code synthesis remains outside the focused regression suite. Slow
  semantic tests are acceptable and marked, but this branch keeps the promoted
  proof predicate check finite and terminating.
