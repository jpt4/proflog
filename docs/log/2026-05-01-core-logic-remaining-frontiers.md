# 2026-05-01 Core.logic Remaining Optimization Frontiers

## Context

ADR-0032 has rejected several small `core.logic` host patches:

- identical-after-walk short-circuiting in `unify`;
- structural-sharing returns from `ISeq` and `LCons` walking;
- a small `Choice.take*` lazy-tail simplification;
- batched `run-constraints*` dispatch; and
- a diagnostic global no-occurs-check mode.

Those results do not prove that Clojure's miniKanren implementation has no
remaining optimization frontiers. They only show that the tried micro-patches
were too narrow, unsound, or poorly aligned with the carried list-family
failures.

The count probe still gives useful direction: the carried `reverse-input-flat`
row is dominated by `walk*`/reification and unification call volume, with
tabling unused and stream choice construction small in the committed probe.

## Remaining Frontiers

### Specialized Vector Unification

`core.logic` has a generic sequential unification path. A host-side
`IPersistentVector` path could avoid seq allocation and walk vectors by index
when both terms are vectors of equal count.

This is only relevant if a Proflog or probe workload actually presents vectors
to `core.logic` at hot unification boundaries. Blindly converting relational
lists to vectors would be wrong: many Proflog programs require open tails and
logic-list structure. The viable experiment is therefore two-part:

- add a generic vector-specialized host path; and
- add or identify a vector-shell workload that exercises the path without
  changing list-family semantics.

### Walk/Reification Memoization or Fusion

The count probe's strongest signal is repeated walking and reification. A
promising host experiment is bounded per-call memoization inside `walk*` or a
fused walk/reify traversal that avoids walking the same acyclic subterms
repeatedly during one answer extraction.

The main risk is semantic unsoundness around logic variables, substitutions,
constraints, and nominal terms. Any cache must be local to a stable
substitution snapshot or otherwise prove that cache entries cannot outlive the
state they summarize.

### Sound Occurs-Check Reduction

The no-occurs-check diagnostic was faster but still did not close targets, and
global disabling is unsound. A remaining frontier is not "remove occurs check";
it is a local proof that a particular extension cannot produce a cycle, such as
when extending a fresh variable with an already-ground term.

This needs a soundness argument before it can become production code.

### Substitution and Constraint Store Representation

`core.logic` substitutions and stores are persistent Clojure data structures.
There may be larger representation opportunities such as path compression,
transient-backed construction windows, primitive-backed indexes, or faster
constraint-store lookup paths.

This is deeper substrate work than the current micro-patch loop. It should be
benchmarked against generic `core.logic` programs, not only Proflog's carried
matrix rows.

### Tabling and Indexing

Tabling and answer reuse remain generic miniKanren techniques, but the current
carried rows did not exercise the observed tabling internals. This does not
make tabling unimportant generally; it makes it a weaker immediate target for
the present ADR-0032 blockers unless Proflog changes its host calls to use
tabled relations or a new workload demonstrates missed reuse.

## Concurrent Experiment Plan

Two experiments can run concurrently because they can be evaluated on separate
branches and worktrees, then merged only if a branch demonstrates broad,
sound improvement:

- `adr32/core-logic-vector-unification`: specialize vector unification and
  construct a Proflog vector-shell or host-level workload that proves the path
  is exercised.
- `adr32/core-logic-walk-reify-memo`: try bounded walk/reification
  memoization or loop fusion while avoiding the already rejected sequence and
  logic-list structural-sharing patches.

Both experiments may touch `vendor/core.logic-1.1.1/src/clojure/core/logic.clj`,
so they should not share a working tree. The merge policy is to compare each
branch independently against the same ADR-0032 probes, then integrate only the
branch or pieces that improve more than a single named row without violating
generic `core.logic` semantics.

## Decision

Continue ADR-0032 with the two concurrent experiments above. Keep larger
substitution-store and tabling/indexing work registered as later options, but
do not start them until the vector and walk/reification probes either produce
actionable results or are rejected.
