# ADR-0069: SJAS General Formula-Code Substitution

- Status: completed
- Date: 2026-05-15
- Branch: `adr-0069-sjas-general-subst-code`
- AAR: [AAR-0069](../aar/AAR-0069-sjas-general-subst-code.md)

## Context

ADR-0067 made `subst-code/2` structural only for identity substitution, while
ADR-0068 let proof predicates build theorem targets from decoded formula codes.
The remaining fidelity gap is the non-identity `Subst(g,h)` relation itself.

Willard's Level-1 fixed-point construction uses a code `g` for a one-free-
variable formula `Gamma_1(v)`, then relates it to the sentence obtained by
substituting the numeral/code for `g` into the free variable. Proflog currently
has the right public predicate and a generated fixed-point entry, but that
nontrivial substitution is not yet computed by a general code-level relation.

The old identity fallback is also too permissive for open formulas: if the
decoded source formula contains the designated free variable, `subst-code(c,c)`
should not succeed merely because `c` is well formed.

## Decision

Implement `subst-code/2` as a structural diagonal substitution over decoded
formula codes:

```text
subst-code(source-code, substituted-code)
```

The relation will:

- decode `source-code` into formula-code bytes and an internal syntax tree;
- build the internal code term for `source-code`;
- substitute that code term for free variable index `1` in the decoded formula;
- respect binder shadowing for quantifiers and bounded quantifiers;
- leave embedded code terms quoted rather than descending into their payload;
- decode `substituted-code` and require it to match the substituted syntax tree.
- compare decoded formulas modulo bound-variable alpha-renaming, because the
  Level-1 fixed-point skeleton reserves `v0` for the diagonal variable while
  the final Group-3 sentence is encoded with compact canonical binder names.

Generated `subst-code` entries should no longer be required for identity or
the Level-1 fixed-point entry. Source-time generated data may still exist for
finite system membership, symbol indexes, and `axiom-member/2`, but not as the
semantic implementation of `Subst`.

## Consequences

- `subst-code(c,c)` succeeds only when substituting variable index `1` does not
  change the decoded source formula.
- Nontrivial substitutions such as `wff(v0) -> wff(code(wff(v0)))` are computed
  by the proof profile.
- The generated `SelfCons` skeleton-to-Group-3 relation becomes derivable from
  the structural substitution relation.
- Slow tests are acceptable; correctness of the substitution relation is more
  important than default-suite speed.

## Test Obligations

- A red test must show that a non-generated open source formula code
  `code(wff(v0))` does not currently relate to the independently encoded target
  `code(wff(code(wff(v0))))`.
- A red test must show that `subst-code(code(wff(v0)), code(wff(v0)))`
  currently succeeds incorrectly through the identity fallback.
- After implementation, the nontrivial substitution succeeds and the incorrect
  identity case fails.
- Binder shadowing is tested: substituting into a formula whose variable index
  `1` is bound by a quantifier leaves its body unchanged.
- The Level-1 `selfcons-skeleton-code -> group-three-code` path still succeeds,
  preferably through the structural relation rather than generated substitution
  entries.
- Passing focused, SJAS slow, SJAS, fast, and extended suite runtimes are
  recorded.
