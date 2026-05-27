# SJAS Completion Audit

Objective audited: implement Willard's finite `IS#_D(beta)`-style SJAS profile
for a standard first-order logic with equality tableau deductive apparatus on
top of Proflog, with arithmetized syntax/proof codes and recorded research.

## Satisfied Requirements

- Research and citation: `docs/log/2026-05-14-sjas-arithmetized-coding-research.md`
  records the Willard 2011 Definition D.1 / footnote 23 coding requirement: a
  natural proof encoding is acceptable when it is not too efficient, with at
  least `5J` bits for `J` function-symbol occurrences.
- Arithmetized codes: ADR-0063 replaced opaque formula labels and
  `:sjas/proof-targets` with compact base-64 code terms for formulas, systems,
  and proof certificates.
- Proof-code checking: `tableau-proof/3` decodes supplied proof-code bytes into
  Proflog proof terms and checks them through the core kernel.
- Self-consistency non-vacuity: ADR-0062 and later ADRs check real
  contradiction and Group-3 proof targets, not a literal placeholder.
- Level-1 substitution vocabulary: ADR-0064 introduced `subst-prf/4`, and
  generated Level-1 Group-3 uses `subst-prf/4` rather than raw
  `tableau-proof/3`.
- Fixed-point substitution: ADR-0065 generates the `Gamma_1(g)` skeleton code
  and uses that skeleton code in the final Group-3 sentence.
- Public substitution relation: ADR-0066 exposes `subst-code/2` separately from
  `subst-prf/4`.
- Structural syntax predicates: ADR-0067 parses formula-code bytes for `wff/1`,
  formula-class predicates, `neg-pair/2`, and identity `subst-code/2`, including
  non-generated formula codes such as `lt(1,2)`.
- Structural theorem targets: ADR-0068 lets `tableau-proof/3` and `subst-prf/4`
  check certificates for non-generated theorem codes by decoding the theorem
  code, computing its complement, translating that decoded complement to the
  kernel AST, and validating the certificate through the core tableau kernel.
- Test evidence: `docs/TEST_RUNTIME_BASELINE.md` records red and green focused
  timings plus the passing SJAS, fast, and extended suite timings for ADR-0063
  through ADR-0068.

## Current Scope

The implementation is a finite `IS#_D(beta)` substrate with `D` represented by
Proflog's core first-order/equality tableau kernel. It does not claim the later
Tab-1/proof-list theorem-reuse apparatus. That omission is consistent with the
selected ordinary-tableau deduction apparatus and remains documented in the
worked example and AARs.

Generated finite data remains where it represents finite system membership:

- `axiom-member/2` facts for the active finite basis;
- the active system code and finite symbol-index table;
- the Level-1 fixed-point `selfcons-skeleton-code -> group-three-code`
  substitution entry.

Those generated boundaries are source-translation data for the finite
`IS#_D(beta)` system, not proof-time host theorem lookup. Ordinary syntax
recognition, identity substitution, theorem target construction, proof-code
decoding, and arithmetic proof steps now operate through kernel/profile
relations over object-language terms.

## Remaining Non-Goals

- No Tab-1/proof-list theorem-reuse profile has been implemented.
- Open proof-code synthesis remains operationally expensive and is not promoted
  as a default regression.

These are documented as non-goals or later possible extensions, not blockers
for the finite ordinary-tableau `IS#_D(beta)` demonstration requested here.

## ADR-0069 Follow-Up

ADR-0069 removes the non-identity substitution gap recorded in this audit.
`subst-code/2` now decodes formula codes and computes diagonal substitution
structurally, including the Level-1 fixed-point skeleton-to-Group-3 case. The
remaining non-goals are Tab-1/proof-list theorem reuse and open proof-code
synthesis.
