# ADR-0064: SJAS Substitution-Proof Predicate

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0064-sjas-subst-proof`
- AAR: [AAR-0064](../aar/AAR-0064-sjas-substitution-proof-predicate.md)

## Context

ADR-0063 removed the false proof-target-table boundary from the Willard SJAS
profile: formula, system, complement, and proof certificates are now compact
base-64 Godel-code terms, and `tableau-proof/3` decodes proof bytes before
calling the kernel.

The active Willard objective is broader. The research note for ADR-0063 records
that Willard's general `SelfCons_k(beta,d)` machinery is stated in terms of
`Neg_k`, `Prf^d_beta`, `ExPrf^d_beta`, `Subst`, and `SubstPrf^d_beta`. The
current Level-1 Group-3 sentence still uses raw `tableau-proof(system, x, p)`
for both sides of a complement pair. That is a useful finite checker, but it is
not yet the substitution-proof vocabulary used in the Willard fixed-point
presentation.

## Decision

Add an explicit `subst-prf/4` SJAS predicate:

```text
subst-prf(system-code, substitution-code, theorem-code, proof-code)
```

For the finite `IS#_D(beta)` substrate in this ADR, `subst-prf/4` is an explicit
proof-profile relation that delegates to the decoded `tableau-proof/3` checker
after consulting the generated substitution boundary for the current system. The
first implementation supports the identity substitution entries needed by the
current generated closed formulas and by Level-1 Group-3. It is deliberately a
separate predicate so later ADRs can replace the finite identity table with a
full code-level `Subst` decoder without changing the public Group-3 formula
shape again.

Update the Level-1 Group-3 generator to cite `subst-prf/4` instead of raw
`tableau-proof/3`:

```text
forall x y p q.
  not neg-pair(x,y)
  or not subst-prf(this-system, this-system, x, p)
  or not subst-prf(this-system, this-system, y, q)
```

`tableau-proof/3` remains the lower-level proof predicate for direct theorem
checking and the Tableau-0 profile.

## Consequences

- The Level-1 profile now exposes the Willard substitution-proof vocabulary at
  the object-language boundary.
- The implementation remains finite and conservative: it does not yet claim
  arbitrary code-level substitution over open formula-code variables.
- The next fidelity gap after this ADR is a full `Subst` relation over arbitrary
  base-64 formula codes, replacing the generated identity substitution entries.

## Test Obligations

- The SJAS language declares `subst-prf/4`.
- Level-1 Group-3 contains `subst-prf/4` calls and no raw `tableau-proof/3`
  calls in its consistency disjuncts.
- A valid beta proof certificate is accepted through `subst-prf/4` for the
  generated identity substitution entry.
- The same `subst-prf/4` query rejects an unrelated proof certificate and a wrong
  theorem code.
- Existing Tableau-0, Level-1, arithmetic, answer, and certificate tests continue
  to pass.

## Exit Criteria

- `lein test-proflog-sjas` passes and records runtime.
- Because this changes proof-profile branch rules and generated Group-3 formulas,
  `lein test-proflog-fast` and `lein test-proflog-extended` must pass before
  merge.
- The worked SJAS example, runtime baseline, ADR/AAR indexes, development log,
  and nachlass log describe the new substitution-proof boundary and its remaining
  limitation.
