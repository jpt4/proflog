# AAR-0020: Pure Gamma Candidate Boundary

- Date: 2026-04-27
- Related ADR: [ADR-0020](../adr/ADR-0020-pure-gamma-candidate-boundary.md)
- Outcome: completed

## What Happened

ADR-0019's `proflog.gamma/closed-term-candidateo` used `core.logic/project`
to read `fuel` from inside the kernel path. That made the candidate relation
order-dependent: constraining `fuel` after asking for candidates could produce
a different result from constraining it before the candidate goal.

ADR-0020 split the operation into two layers:

- `closed-terms-for-fuel` remains host-side finite generation from a concrete
  compiled program and fuel value.
- `closed-term-candidateo` is now only finite membership over a supplied
  candidate collection.

The ordinary kernel, answer overlay, and tabling wrapper now thread that finite
candidate collection as explicit proof-search state. Public wrappers preserve
their existing arities by computing the candidate collection before entering
the proof relation.

## What Worked

- `src/proflog/gamma.clj` no longer contains `project`.
- `proflog.closed-term-gamma-test/kernel-uses-explicit-gamma-candidates`
  exercises the kernel with explicit generated candidates.
- `proflog.gamma-test/candidate-relation-uses-explicit-finite-candidates`
  records the new boundary: candidate choice is pure membership, not host-side
  inspection of logic variables.
- The tabled kernel key includes non-empty gamma candidate state, while empty
  candidate state preserves prior tabling keys.

## Verification

- `rg -n "project" src/proflog/gamma.clj` returns no matches.
- `lein test proflog.gamma-test`
- `lein test proflog.closed-term-gamma-test`
- `lein test-proflog-fast`

## Follow-Up

ADR-0020 deliberately does not remove existing projected fuel handling from
`kernel-support/step-fuelo` or the ADR-0017 tabling wrapper. Those are older
operational boundaries and should be evaluated separately if reverse execution
requires fully relational fuel synthesis.
