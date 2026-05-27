# ADR-0052: Unified Robinson Q3 Theory Rule

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0052-final-q3-deduction-modulo`
- AAR: [AAR-0052](../aar/AAR-0052-unified-robinson-q3-theory-rule.md)

## Context

ADR-0048 introduced Robinson Q as both ordinary first-order assumptions and an
opt-in `:robinson-q` proof profile. ADR-0049, ADR-0050, and ADR-0051 then added
Q3 support incrementally:

- `q3-case-split` for Q3's own direct refutation;
- kernel-interleaved `neq-store` state for `x != zero`;
- `q3-predecessor-intro` for the add-one predecessor theorem.

The 2026-05-09 expressivity audit showed that this incremental approach still
misses a small valid Q theorem:

```text
forall x. x != zero -> exists y. s(add(y, s(zero))) = s(x)
```

Ordinary Q-as-antecedent proves this theorem at fuel 16, but the profile
returns no proof through fuel 384. The issue is architectural: the profile has
separate branch closers for separate syntactic shapes instead of one rule that
uses Q3 as a predecessor equality inside the current Q-congruence problem.

The research basis is the same deduction-modulo boundary recorded in
ADR-0048. Dowek's survey describes deduction modulo as replacing axioms with
reduction rules and performing proof steps modulo the congruence induced by
those reductions:

- <https://arxiv.org/abs/1501.06523>
- <https://arxiv.org/pdf/1501.06523>

Dowek and Werner's arithmetic-as-theory-modulo work is evidence that arithmetic
can be organized as computation plus proof rules rather than ordinary axioms:

- <https://arxiv.org/abs/2310.10326>

Robinson Q itself keeps Q3 as the non-computational predecessor-or-zero axiom;
the Isabelle AFP Robinson Arithmetic formalization records Q as a first-order
theory with arithmetic-specific axioms:

- <https://www.isa-afp.org/browser_info/current/AFP/Robinson_Arithmetic/document.pdf>

## Decision

Replace the two Q3 branch closers with one unified Q3 theory rule.

The rule is still branch-local and relevance-controlled:

1. A branch must already contain a saved nonzero obligation `x != zero`.
2. The active formula must be a disequality.
3. The rule chooses one active proof-local universal variable `v` as Q3's
   predecessor witness for `x`.
4. It extends the temporary equality state with the trusted Q3 equality
   `x = s(v)`.
5. It walks and Q-normalizes both sides of the active disequality under that
   temporary equality.
6. It closes only if the normalized sides are already identical under the
   temporary equality state.

This is not unrestricted predecessor synthesis. The rule never invents a
fresh predecessor detached from the current universal branch obligation. It
also does not use ordinary unification after the Q3 equality to force a proof:
after `x = s(v)` and Q conversion, the active disequality must be reflexive.

The proof evidence becomes a single Q3 marker:

```clojure
(profiled robinson-q
  (q3-predecessor-equality
    predecessor-or-zero
    x
    v
    q3-equality-proof
    q-left-proof
    q-right-proof))
```

This marker replaces both `q3-case-split` and `q3-predecessor-intro` in current
profile proofs.

## Consequences

- Q3 is represented once in the profile, as a trusted predecessor equality
  used to close a modulo-congruence branch.
- The direct Q3 theorem, the add-one predecessor theorem, and the contextual
  successor theorem use the same rule.
- The source no longer accumulates one-off Q3 closers for each syntactic shape.
- The rule remains incomplete for arbitrary arithmetic consequences of Q; that
  is acceptable for ADR-0052 because unrestricted predecessor generation would
  be unsound and operationally explosive in this kernel. The acceptance boundary
  is single-use Q3 closure against the active universal disequality.

## Test Obligations

- A red test must show that the contextual theorem is ordinary-provable but not
  yet profile-provable.
- The final profile proof for direct Q3 must contain
  `q3-predecessor-equality` rather than `q3-case-split`.
- The final profile proof for `q3-add-one-predecessor` must contain
  `q3-predecessor-equality` and `q-rewrite`.
- The final profile proof for the contextual theorem must contain
  `q3-predecessor-equality` and `q-rewrite`.
- A negative guard must show the rule does not prove the false theorem
  `forall x. x != zero -> exists y. x = s(s(y))` at the focused bound.
- A source audit must reject the old Q3 closer names in
  `src/proflog/kernel/robinson_q_profile.clj`.
- The focused Robinson Q suite, fast gate, extended gate, and comparison probe
  must pass, with runtimes recorded.

## Exit Criteria

- The Q profile has one Q3 closure implementation.
- All existing Q profile examples still prove.
- The contextual Q3 theorem proves under `:robinson-q`.
- The implementation stays inside the kernel theory hook and remains relational
  Clojure/core.logic code, not host-side theorem recognition.
- Documentation, worked examples, runtime baselines, ADR index, execution plan,
  and AAR records are current.

## After Action Summary

ADR-0052 completed the unified Q3 rule. The current profile implementation has
one Q3 closure relation, `q3-predecessor-equality-closeo`, and proof evidence
now uses `q3-predecessor-equality` for direct Q3, add-one Q3, and the
contextual successor theorem. The focused selector, standard fast and extended
gates, and comparison probe passed; see
[AAR-0052](../aar/AAR-0052-unified-robinson-q3-theory-rule.md) for timings and
remaining limits.
