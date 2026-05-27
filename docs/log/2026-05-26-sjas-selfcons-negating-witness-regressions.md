# SJAS Self-Consistency Negating Witness Regressions

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

## Purpose

This note records the executable follow-up to the SJAS/Proflog separation
witness. The goal was to move from an arbitrary external relation
`external-demo/1` to formulas that touch the self-consistency proof predicates
themselves.

## Minimal External-Clause Regression

The minimal witness remains:

```text
external-demo(0)
```

for a system that declares `external-demo/1` but does not include any beta axiom
or reflected Group-2b clause for it. A runtime-only external clause can prove
that atom in ordinary Proflog, but it is not part of the encoded SJAS
`system-code`.

The new regression
`sjas-proof-predicates-ignore-external-runtime-clauses` first reproduced the
bug: non-`sjas-axiom` `tableau-proof/3` and `subst-prf/4` validation accepted a
certificate whose proof used the external runtime clause. The implementation now
stores a reflected-only compiled program in the SJAS registry and uses that
program when the proof-predicate bridge asks `kernel/prove-programo` to validate
a decoded certificate.

The important boundary after the fix is:

- ordinary host-side Proflog queries may still use `external` clauses;
- SJAS proof predicates may validate certificates only against reflected clauses
  represented by the encoded system source.

## Self-Consistency Negating Witness

For the Tableau-0 profile, the generated Group-3 axiom has the shape:

```text
forall p. not tableau-proof(S, false-code, p)
```

A negating witness instance is therefore:

```text
tableau-proof(S, false-code, P0)
```

where `S` is the generated system code and `P0` is a proof-code term. The new
regression
`sjas-tableau0-selfcons-negating-witness-separates-external-proflog` checks two
facts about this exact atom:

1. The generated SJAS Tableau-0 system rejects it.
2. Ordinary Proflog accepts the same atom if `tableau-proof/3` is supplied as an
   external runtime procedure.

This is the Gödel/self-consistency-targeted version of the external-clause
separation. It is decisive for the claim that external runtime procedures must
not be available to the SJAS proof predicate. It is not an endogenous proof that
the reflected Proflog kernel, restricted to the encoded system, proves the
negation of the self-consistency axiom.

## Level-1 Complement Candidate

For Level-1, the generated Group-3 fixed point uses `subst-prf/4` and its
complement is the true decisive target for simultaneous proof/complement proof
consistency. The smallest supplied-certificate probe checks:

```text
subst-prf(S, skeleton-code, code(not Group3), sjas-axiom)
```

The new regression
`sjas-subst-prf-rejects-selfcons-complement-axiom-certificate` confirms that
the current SJAS profile rejects this complement proof-predicate atom. Broader
attempts to search for a full Level-1 complement proof remain too expensive for
an ad hoc probe and did not produce an endogenous Proflog acceptance witness.

## Status

Completed:

- the minimal external-clause proof-predicate leak is fixed and covered;
- the Tableau-0 self-consistency negating witness is covered as an external
  Proflog/SJAS separation;
- the Level-1 complement axiom-certificate candidate is rejected.

Not completed:

- no endogenous reflected-only Level-1 counterexample has been found;
- full Track 2b still requires a correspondence proof for the kernel shortcut
  or a complete arithmeticized proof predicate.
