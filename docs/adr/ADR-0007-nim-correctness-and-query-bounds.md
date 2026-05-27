# ADR-0007: Nim Correctness And Query Bounds

- Status: completed
- Date: 2026-04-18
- Branch: `adr-0007-nim-correctness-query-bounds`
- AAR: [AAR-0007](../aar/AAR-0007-nim-correctness-and-query-bounds.md)

## Context

ADR-0005 declared procedure calls and the top-level query API complete, but the
current greenfield implementation does not yet satisfy that claim on the
repository's flagship recursive program family.

The assessment on `codex/nim-feasibility-assessment` found three concrete gaps:

- the greenfield Nim coverage only checks one losing case, `win(3)`,
- the kernel can refute winning Nim positions such as `win(1)` because δ
  parameters are treated as constructor clashes instead of branch-local
  equality constraints,
- the wall-clock bounded query helpers are not reliable enough to act as the
  authoritative operational boundary.

Those are not optional optimizations. They are missing correctness and API
discipline within the scope ADR-0005 already claimed.

## Decision

- Restore Fitting-style plain procedure-call discipline by blocking ordinary
  calls whose walked arguments still contain unresolved internal `par` terms.
- Preserve the existing greenfield explicit equality state, but extend it so
  positive equality can record branch-local bindings for internal parameters
  when a proof identifies them with a concrete term or another already-walked
  term.
- Stop treating unresolved `par` terms as immediate free-constructor clashes.
  Constructor clash remains valid only when the walked terms really exhibit a
  constructor contradiction.
- Keep the bounded query helpers as API-layer operational tools, but replace the
  current forced-stop behavior with a boundary that returns predictably.
- Separate semantic regression checks from the bounded query API: Nim
  correctness should be asserted through direct semidecision proofs, while the
  bounded helpers are only responsible for returning operationally bounded
  answers such as `:unresolved` on timeout.
- Expand the flagship Nim coverage so ADR-0005's procedure-call claim is tested
  against both winning and losing positions, not only one representative loss.
- Document that the greenfield L-ground guard stays structural; any projected
  term inspection remains legacy-only and does not define the default kernel.

## Consequences

- ADR-0005 becomes true for the greenfield implementation in the place where it
  matters most: recursive program behavior.
- Equality and procedure calls now have a sharper contract: unresolved
  parameters block plain calls, while equality can still discharge that block by
  binding a parameter to an admissible walked term on the current branch.
- The query API remains operationally bounded without claiming that timeout is a
  semantic result.
- Query-boundary tests now split into two roles: direct proof checks for semantic
  Nim correctness and bounded-helper checks for timeout behavior.
- ADR-0006 should build on this corrected baseline rather than on the earlier,
  over-optimistic ADR-0005 exit claim.

## Task List

- Add failing greenfield tests for Nim winners `win(1)`, `win(2)`, and
  `win(4)`, plus losing positions `win(0)` and `win(3)`.
- Add unit tests showing unresolved `par` equality stays open while
  constructor-headed contradiction still closes.
- Add tests showing plain procedure calls are blocked on unresolved `par`
  arguments and reopened only after equality has walked those arguments back
  into the declared language.
- Add a bounded-query regression test that proves the helper returns within its
  budget instead of hanging the caller.
- Assert Nim winners and losers through direct success/failure semidecision
  checks rather than through bounded status races.
- Implement the kernel, equality, and query changes needed to satisfy those
  tests.
- Update ADR/AAR and execution documents so ADR-0005's deficiency is explicit.

## Test Obligations

- `test/proflog/equality_test.clj`
- `test/proflog/kernel_test.clj`
- `test/proflog/query_test.clj`

## Exit Criteria

- `win(0)` and `win(3)` are reported as losing positions, while `win(1)`,
  `win(2)`, and `win(4)` are reported as winning positions by the greenfield
  prover, with bounded query helpers treated as operational probes rather than
  semantic oracles.
- Unresolved `par` terms do not trigger plain procedure calls.
- Equality can still bind a `par` to a walked term on-branch when justified by
  a positive equality literal.
- Bounded query helpers return control to the caller on timeout without
  mislabeling timeout as semantic proof.
