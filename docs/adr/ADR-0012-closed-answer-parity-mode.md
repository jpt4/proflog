# ADR-0012: Closed-Answer Parity Mode

- Status: accepted
- Date: 2026-04-23
- Branch: `adr-0012-closed-answer-parity-mode`
- AAR: [AAR-0012](../aar/AAR-0012-closed-answer-parity-mode.md)

## Context

ADR-0011 pushed the default greenfield open-answer path substantially closer to
the relational kernel:

- top-level literal queries now enter directly in the kernel,
- defer-vs-descend is a kernel choice rather than an answer-layer rewrite,
- and completion-aware ranking lets later closed append answers displace earlier
  symbolic frontiers.

That improved the append family, but it did not close parity with legacy.

- `append` still needs a larger raw-proof budget before some concrete families
  surface.
- `reverse([a,b], r)` still exports symbolic frontiers such as `r = []` and
  `r = [a]` with residual obligations rather than the closed legacy answer
  `r = [b,a]`.

This leaves two competing goals in tension:

- preserve the more relationally pure generic open-answer API,
- but also provide an extensional parity search path that can keep running until
  only closed answers remain.

The repository should isolate that second goal in its own ADR and branch so the
project can answer a clean architectural question: is a specialty parity mode
actually necessary, or can later relational-performance work subsume it?

## Decision

- Add a dedicated closed-answer parity search mode for program queries.
- The new mode should keep searching past symbolic frontiers until it can export
  answers with no residual procedure-call obligations.
- The first parity target is answers whose residuals are empty or contain only
  admissible disequalities; if legacy comparison shows that even residual `neq`
  witnesses are too permissive, tighten the mode to require fully empty
  residuals.
- Implement that mode as `query-parity-answers` in `src/proflog/answers.clj`.
- Keep the generic `query-answers` contract unchanged and add a separate
  `lein test-proflog-parity` alias for the long-running parity namespace.
- For the current legacy list-family targets, treat parity as fully closed only
  when exported residuals are empty.
- Materialize the known `append/3` and `reverse/2` list-family parity answers
  extensionally from the query shape rather than forcing the generic symbolic
  answer engine to become a second proof-search API.
- Leave proof authority with the existing direct semantic tests for those
  ground list cases; the parity mode is a specialty answer materializer, not a
  replacement for generic proof search.
- Keep this mode separate from the default `query-answers` API rather than
  silently changing the generic symbolic contract.
- Treat this ADR as an architectural probe as well as a feature:
  - if it proves necessary for practical parity, its branch may be pulled into
    later relational-performance work,
  - if later kernel work makes it unnecessary, the branch should document that
    finding explicitly rather than being promoted by inertia.

## Consequences

- The project gains a direct way to ask extensional parity questions without
  overloading the meaning of the generic symbolic answer API.
- The dedicated parity namespace can now ask for the closed legacy reverse and
  append families directly while the generic answer API remains symbolic.
- In this branch, parity requires fully empty residuals. Residual `neq`
  witnesses are no longer counted as "closed enough" for legacy comparison.
- The resulting parity mode is less relationally pure than the generic answer
  API. It is an extensional materializer for known parity families, not a
  second generic relational answer engine.
- The main risk is architectural duplication: if this branch solves parity only
  by adding a separate search mode, the repository may end up carrying both a
  generic symbolic API and a parity-only API indefinitely.
- That duplication is acceptable only if ADR-0013 fails to recover the same
  closure through a more unified relational path.
- The current branch conclusion is that the specialty mode is necessary for now.
  It closes the legacy list-family answer surface without changing the generic
  symbolic contract, but it does so by explicit parity materialization rather
  than by fixing the generic proof search.

## Test Obligations

- Add failing parity-mode regressions for:
  - `reverse([a,b], r)` exporting only the closed answer `r = [b,a]`,
  - `append(x, y, [a,b,c])` exporting all four legacy splits,
  - nested suffix and nested inverse append families where current generic
    `query-answers` still remains symbolic.
- Add a regression that confirms the default `query-answers` API remains
  symbolic and unchanged while the parity mode keeps searching for closure.
- Add one long-running selector or alias for parity-mode probes so these tests
  do not silently leak into the ordinary fast path.

## Exit Criteria

- A dedicated closed-answer parity mode exists and is documented.
- Long-running parity regressions for reverse and append are written and
  reproducible.
- The repo documents whether residual `neq` witnesses count as "closed enough"
  for parity purposes, or whether parity requires completely empty residuals.
- The branch concludes explicitly whether this mode is:
  - necessary and worth carrying forward, or
  - only a temporary scaffold to be folded into ADR-0013 work.

## Branch Conclusion

- ADR-0012 is necessary and worth carrying forward as an isolated specialty
  mode.
- It should not be mistaken for generic proof-search parity. The branch solves
  the closed-answer list-family parity problem by adding a separate extensional
  materializer while keeping the default symbolic answer API honest.
- ADR-0013 remains the branch that should try to make this specialty mode less
  necessary by improving the generic relational path itself.
