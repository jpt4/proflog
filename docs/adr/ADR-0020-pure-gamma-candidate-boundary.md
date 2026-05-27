# ADR-0020: Pure Gamma Candidate Boundary

- Status: completed
- Date: 2026-04-27
- Branch: `adr-0020-0021-gamma-purity-regressions`
- AAR: [AAR-0020](../aar/AAR-0020-pure-gamma-candidate-boundary.md)

## Context

ADR-0019 added `proflog.gamma/closed-term-candidateo` so the kernel can use
bounded generated closed terms for Fitting's gamma rule. The implementation
used `core.logic/project` to delay reading `fuel` until run time.

That is an operational shortcut, not a relational rule. The kernel may be
called by impure wrappers, but if the kernel itself calls projected host code,
reverse and partial synthesis modes become order-dependent. In particular, a
goal that constrains `fuel` after asking for a gamma candidate can see a
different candidate set from the same logical constraints in the opposite
order.

The semantic core should instead receive any finite candidate set as explicit
proof-search state. Host code may compute that set before entering the kernel,
but the kernel-facing relation should only relate a term to a supplied finite
collection.

## Decision

- Remove `project` from `proflog.gamma`.
- Split closed-term generation from the relational candidate choice:
  - host-side generation may compute a finite vector of terms from language and
    fuel,
  - the kernel-facing relation should be a pure finite-membership relation over
    an already supplied candidate list.
- Thread the finite gamma-candidate set explicitly through kernel and
  answer-overlay proof state rather than recomputing it inside quantifier rule
  branches.
- Preserve the existing public proof/query APIs. Those APIs may compute the
  candidate set from the concrete program and fuel before invoking the relation.
- Add a regression that demonstrates the old order-dependence: constraining a
  depth/fuel variable after the projected candidate relation should not change
  the logical result. The replacement should avoid that mode by making the
  candidate collection explicit before entering the kernel-facing relation.

## Consequences

- The kernel no longer calls a projected gamma relation.
- Gamma candidate generation remains bounded and constructor-generic.
- Internal proof state grows by one finite collection, but the kernel rule text
  remains readable: instantiate with one supplied closed candidate.
- If a caller wants a different candidate policy for synthesis, it can supply a
  different finite collection explicitly rather than relying on hidden host
  inspection of logic variables.

## Exit Criteria

- No `project` use remains in `src/proflog/gamma.clj`.
- ADR-0019 closed-term gate tests still pass.
- A purity regression covers the old projected-order problem.
- `lein test-proflog-fast` passes.
