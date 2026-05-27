# ADR-0018: Existential Disequality Witnesses

- Status: completed
- Date: 2026-04-26
- Branch: `adr-0018-existential-disequality-witnesses`
- AAR: [AAR-0018](../aar/AAR-0018-existential-disequality-witnesses.md)

## Context

ADR-0017 completed the first relational tabling layer, but the follow-up
legacy comparison exposed a smaller and more semantic priority than another
performance pass. The program:

```prolog
p(x) :- exists y. x != y
```

has a finite intended meaning over a language with constants `a` and `b`.
Both `p(a)` and `p(b)` should succeed, and the open query `p(x)` should export
the object-language answers `a` and `b`.

Legacy can produce a result for the open query, but it does so by leaking an
internal delta parameter. Its projected `l-ground-termo` admits an unbound logic
variable before a later binding turns that variable into `(par ...)`. That is
not a sound program answer.

Greenfield preserved the more important boundary: it kept proof-local delta
parameters out of public answers. At the start of this ADR, greenfield behavior
was nevertheless incomplete. It reported `p(a)` and `p(b)` as unresolved and
returned no answers for `p(x)` in the bounded public answer helpers.

The worked example is recorded in
`worked-examples/existential-disequality-witness.md`.

## Decision

- Make accurate greenfield evaluation of existential disequality witnesses the
  next implementation priority.
- Preserve the greenfield distinction between user-level answer variables and
  proof-local delta parameters. The fix must not imitate legacy's projected
  `l-ground-termo` behavior.
- Add a relational mechanism for finding admissible object-language witnesses
  when an existential disequality can be satisfied by terms in the declared
  language.
- Keep the kernel readable as the Fitting-style proof kernel. If bounded
  Herbrand enumeration or answer materialization is needed, it must be explicit
  and kept at an appropriate boundary rather than hidden as host-side control
  inside the kernel.
- Treat `(par ...)` as an internal proof artifact. It may appear in private
  branch state, but it must never become a public answer, residual witness, or
  exported program term.

## Consequences

- The next work item is semantic completeness for a small quantified
  disequality program, not another list-family performance pass.
- The implementation must choose a precise policy for finite-language and
  bounded-Herbrand witness search. Infinite languages require explicit bounds;
  finite constant-only languages should be decidable by direct object-language
  witness selection.
- Query status and answer export must agree. It is not enough for
  `query-ground-answers` to enumerate `a` and `b` if `query-status` still leaves
  the corresponding ground queries unresolved.
- The legacy result remains a negative comparison target: it demonstrates what
  greenfield must continue to reject, even after greenfield becomes complete on
  the object-language witnesses.

## Test Obligations

- Add a gatekeeping test for the language `{a, b}` and program
  `p(x) :- exists y. x != y`.
- Assert that `query-status` reports `:succeeds` for `p(a)` and `p(b)`.
- Assert that `query-fails` does not produce failure proofs for `p(a)` or
  `p(b)` under the same documented bounds.
- Assert that open answer evaluation returns exactly `a` and `b` under the
  documented finite-language or bounded-Herbrand answer policy.
- Assert that exported bindings, residuals, and public witnesses contain no
  `(par ...)` terms.
- Keep `test/proflog/legacy_impurity_test.clj` as the negative reference that
  proves legacy can still synthesize a spurious `(par ...)` answer.

## Exit Criteria

- The ADR-0018 gatekeeping tests pass without special-casing this one relation
  or this one program.
- `lein test-proflog-legacy-impurity` passes.
- `lein test-proflog-fast` passes.
- The worked example is updated with the new greenfield result.
- An AAR records the chosen witness-search boundary, remaining limits for
  infinite languages, and any effect on the list-family regressions where
  legacy still outperforms greenfield.
