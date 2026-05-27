# ADR-0017: Relational Tabling And Canonical State

- Status: completed
- Date: 2026-04-26
- Branch: `adr-0017-relational-tabling`
- AAR: [AAR-0017](../aar/AAR-0017-relational-tabling-and-canonical-state.md)

## Context

ADR-0016 makes branch scheduling fairer by allowing the prover to choose among
pending goals instead of expanding the leftmost goal first. That improves
fairness, but it also increases the chance that equivalent parts of the proof
search tree are reached more than once.

The project already has several forms of canonicalization above the kernel:

- answer records merge alpha-equivalent exported frontiers,
- residual disequalities and calls are normalized before public export,
- and hard-family probes distinguish raw stream behavior from generic recovery.

Those are answer-surface tools, not proof-search tabling. They do not prevent
the kernel or overlay from repeatedly proving the same canonical state.

core.logic provides tabled goals, but using tabling directly inside
`proflog.kernel` would obscure the kernel's role as a readable implementation
of Fitting's tableau rules. Tabling should therefore be designed as a separate
relational layer that can be tested against the untabled kernel.

## Decision

- Add tabling and canonical state handling as a separate namespace, as far as
  feasible. The expected namespace is `proflog.tabling` unless implementation
  evidence suggests a more precise name.
- Keep `proflog.kernel` as the readable Fitting-style tableau kernel. The
  kernel may expose state-level relations needed by the tabled layer, but it
  should not contain table-management code.
- Treat tabling as an operational accelerator over positive proof search, not
  as a new source of semantic truth. Absence from a table is not failure unless
  a finite, explicitly documented search slice has been exhausted.
- Canonicalize table keys before lookup. At minimum, canonicalization must
  account for:
  - agenda formula order where order is not semantically meaningful,
  - saved literal order,
  - symbolic disequality order,
  - alpha-equivalent proof variables and delta parameters,
  - explicit equality substitution shape after walking,
  - the compiled program identity,
  - and answer-overlay state such as residual obligations and call-depth when
    the table is used above the answer layer.
- Keep the table derived and non-authoritative. A table entry must be
  reconstructible from ordinary relational proof search, and disabling tabling
  must preserve the answer set within the same fuel and call-depth bounds.
- Avoid global mutable caches in the semantic core. If core.logic tabled goals
  are used as an implementation substrate, wrap them behind the new tabling
  namespace and document the operational boundary explicitly.
- Do not let canonicalization leak internal `(par ...)` terms into public
  answers. ADR-0007 and ADR-0015 answer-boundary rules still apply.

## Consequences

- Fair scheduling from ADR-0016 can be made practical without returning to
  left-first control.
- The project gains a place to experiment with state memoization without
  making the kernel harder to read against Fitting's paper.
- Canonical keys are a semantic risk if they identify states that are only
  superficially similar. The first implementation must prefer conservative
  under-merging over unsound over-merging.
- Tabling can improve termination and duplicate-work behavior, but it also
  changes operational completeness properties. Every tabled surface must state
  what is tabled and under which bounds.
- This ADR does not authorize family-specific handlers. It authorizes a
  generic state-reuse layer that must be measured against the hard families.

## Test Obligations

- Add unit tests for canonical key stability across alpha-equivalent proof
  variables, reordered saved literals, reordered disequalities, and equivalent
  walked substitutions.
- Add negative tests proving non-equivalent states do not collapse to the same
  key.
- Add a tabled-vs-untabled equivalence test for a small recursive program under
  fixed fuel and call-depth bounds.
- Add a duplicate-state regression showing that a fair-agenda search reuses a
  canonical state rather than re-running the same subtree.
- Add at least one list-family or hard-family measurement demonstrating that
  tabling reduces repeated work without changing exported answers.

## Exit Criteria

- A separate tabling namespace exists and is documented.
- The kernel remains usable and readable without importing the tabling layer.
- Tabled and untabled runs agree on the covered bounded answer sets.
- Canonicalization tests cover alpha-equivalence and non-equivalence cases.
- `lein test-proflog-fast` passes.
- Relevant extended and hard-family probes either pass or are explicitly
  documented with measured remaining gaps.
