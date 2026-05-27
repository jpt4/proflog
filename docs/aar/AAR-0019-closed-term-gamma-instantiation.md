# AAR-0019: Closed-Term Gamma Instantiation

- Date: 2026-04-27
- Related ADR: [ADR-0019](../adr/ADR-0019-closed-term-gamma-instantiation.md)
- Outcome: complete for bounded generated closed terms of `L`

## What Happened

ADR-0019 replaced the ADR-0018 nullary-only `once-forall` witness hook with a
constructor-generic gamma candidate generator in `proflog.gamma`.

The generator reads the declared function symbols and arities from the compiled
program language:

- arity-0 functions produce constants at depth `0`,
- positive-arity functions produce compound terms from smaller generated terms,
- each term appears first at its minimum constructor depth,
- enumeration order is stable by arity and symbol name,
- the current micro-fuel determines the generated depth, capped by
  `gamma/*closed-term-depth-cap*`.

The proof kernel and answer overlay now call this namespace through the thin
relation `closed-term-candidateo`. That relation projects only `fuel` at run
time. It deliberately does not project or walk the whole program map, avoiding
the ADR-0018 failure mode where projecting nominal program data could make the
proof relation unstable.

## What Worked

- The ADR-0019 gate namespace `proflog.closed-term-gamma-test` passes.
- `once-forall` can now use compound generated terms such as `s(zero)`,
  `s(s(zero))`, `node(leaf, leaf)`, and deeper generated binary trees.
- Ordinary `forall` can now refute over-small universal claims by eventually
  trying generated closed counterexample terms.
- The implementation is constructor-generic. There is no special Peano, list,
  or tree generator in the proof kernel.
- Symbolic answer mode is preserved. The answer overlay disables generated
  closed-term fallback when `existentials-as-vars?` is true, so existing
  `query-answers` frontiers are not prematurely materialized.

The ordinary `forall` rule keeps the historical fresh proof-variable path
before the generated-term fallback. Front-loading Herbrand enumeration caused
practical slowdowns in quantified program tests; using generated terms as a
fallback preserves old search behavior where unification is sufficient while
still recovering the new concrete-counterexample cases.

## Boundary

This completes the ADR scope for bounded generated closed terms of `L`. It does
not enumerate branch parameters from `Lpar`. No current gate requires parameter
candidate generation, and adding it would need a separate policy for which
branch-local parameters are admissible as public object-language candidates.

Procedure-call admissibility remains unchanged: atoms containing unresolved
`(par ...)` terms are still outside `L` and do not invoke program clauses.

## List-Family Effect

ADR-0019 does not close the raw recursive list-family performance gap. The
fast suite is green, and the constructor-generic gates now cover the semantic
gap that motivated this ADR, but the older extended list-family and recursive
integration namespaces remain expensive enough that full namespace runs were
interrupted during verification.

The ADR-0017 conclusion therefore still stands: legacy continues to outperform
greenfield on raw multi-step list proofs such as the documented `Y04` / `Y08`
families, while greenfield remains better disciplined semantically and on the
documented answer materialization surfaces.

## Verification

Passed:

- `lein test proflog.gamma-test`
- `lein test proflog.closed-term-gamma-test`
- `lein test-proflog-fast`
- `lein test proflog.answers-test`
- `lein test-proflog-legacy-impurity`
- `lein test proflog.quantified-programs-test`

Interrupted because they did not complete in a practical verification window:

- `lein test-proflog-extended`
- `lein test proflog.integration-families-test`
- `lein test proflog.list-programs-test`

## Follow-Up

- Keep generated closed-term search bounded and measured. Increasing
  `*closed-term-depth-cap*` should be treated as a search-cost decision, not a
  semantic no-op.
- If Fitting parity later requires `Lpar` terms as gamma candidates, design
  that as a separate, explicit extension rather than folding parameters into
  the current closed-`L` generator.
- Return to raw list-family proof performance separately. ADR-0019 fixed a
  semantic completeness gap, not the underlying multi-step recursive search
  cost.
