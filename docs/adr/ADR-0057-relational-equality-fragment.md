# ADR-0057: Relational Equality-Fragment Experiment

- Status: completed
- Date: 2026-05-09
- Branch: `adr-0057-relational-equality-fragment`
- AAR: [AAR-0057](../aar/AAR-0057-relational-equality-fragment.md)
- Depends On:
  - [ADR-0020](ADR-0020-pure-gamma-candidate-boundary.md)
  - [ADR-0039](ADR-0039-kernel-level-group-verification.md)
  - [ADR-0042](ADR-0042-equality-fragment-status-consistency.md)
  - [ADR-0055](ADR-0055-ski-relational-routing.md)

## Context

The equality-fragment profile is now the main remaining kernel profile whose
successful production path is not implemented purely as miniKanren relations.

`proflog.kernel.equality-fragment` contains two layers:

- a relation-shaped prover (`proveo`, `prove-stateo`, and `close-agendao`) that
  mirrors the normal proof kernel's equality, disequality, quantifier, and
  branch rules; and
- a deterministic finite proof engine (`prove-program-host`, `prove-host`, and
  `close-branch-result`) that ADR-0039 introduced to make full finite verifier
  rows practical.

The host engine is generic and proof-producing. It does not dispatch on group
verifier names, transition-system names, multiplication tables, or other
example-family identifiers. It nevertheless performs semantic work outside the
logic engine: it selects formulas, walks substitutions, unifies terms, merges
proof-variable requirements, and asks `proflog.gamma/closed-terms-for-fuel` to
pre-materialize a bounded collection of closed terms before search begins.

ADR-0020 removed `project` from the kernel-facing gamma candidate relation by
making `closed-term-candidateo` finite membership over an explicit candidate
collection. That improved the ordinary kernel boundary, but it left the
candidate collection itself as host-generated data. The equality-fragment host
engine depends on the same boundary, so replacing only `prove-program-host`
would still leave the finite verifier route partly non-relational.

This ADR therefore treats the experiment as two linked questions:

1. Can the equality-fragment proof engine be implemented as a practical
   miniKanren relation?
2. Can the closed-term gamma candidate enumeration used by that engine also be
   descended into miniKanren, rather than precomputed by host enumeration?

The motivation is semantic surface-area reduction. The more of Proflog's proof
behavior is expressed as relations over the compiled formula representation,
the less confidence in kernel behavior depends on separate assumptions about
deterministic host-side proof procedures.

## Decision

Add an experimental, opt-in relational equality-fragment profile before
considering any production replacement of the ADR-0039 host engine.

The experiment must implement a path that:

- closes equality-fragment proof obligations through relations only after the
  source program has been compiled into the backend language, AST, and program
  representation;
- includes a relational closed-term gamma enumerator, not merely relational
  membership over a host-generated candidate vector;
- emits auditable proof terms under a distinct marker such as
  `profiled relational-equality-fragment`, so production host-engine evidence
  and experimental relational evidence are not confused;
- preserves the existing production `profiled equality-fragment` path until
  the experiment has evidence strong enough to justify promotion; and
- records both correctness and timing evidence against the current host engine.

The current finite-domain `step-fuelo` representation is not reopened by this
ADR. Fuel remains whatever the production kernel currently accepts. The
experiment is about the equality-fragment proof engine and the gamma term
enumerator it consumes, not about changing the public fuel API.

## Experimental Relational Boundary

For this ADR, "purely relational equality-fragment" means the experimental
proof route must not call the deterministic equality-fragment engine or its
host-only helpers while proving a formula. In particular, route tests must
forbid the experimental path from calling:

- `prove-program-host`;
- `prove-host`;
- `close-branch-result`;
- host unification helpers such as `unify-term`, `terms-same?`, and
  `rigid-different?`; and
- `gamma/closed-terms-for-fuel` for candidate materialization.

Some host work remains legitimate at compile/setup boundaries:

- frontend syntax may compile to backend AST forms;
- a language declaration may be normalized into an ordered constructor fact
  list stored in the compiled program; and
- a public entry helper may choose the experimental profile explicitly.

Those setup steps do not prove or refute a formula. The experimental proof
itself must search through miniKanren relations over the compiled objects.

## Relational Gamma Design

The experiment should add a relational gamma candidate layer alongside the
current host enumerator.

The current `closed-terms-up-to-depth` policy is:

- depth zero contains nullary constructors;
- depth `n + 1` contains applications of positive-arity constructors to terms
  generated up to depth `n`, requiring at least one argument from exact depth
  `n`; and
- candidates are ordered and capped to keep search finite.

The relational variant should preserve the same intended finite language while
representing the signature as logic-friendly data. A reasonable initial design
is:

- compile the language's function declarations into an ordered constructor fact
  list such as `([zero 0] [s 1] [node 2])`;
- provide `constructor-facto` as relational membership over that list;
- provide `closed-termo` or `closed-term-up-to-deptho` to relate a term to the
  constructor facts and a bounded depth budget;
- provide an exact-depth helper so compound terms are not duplicated at every
  larger depth unless the experiment proves the simpler version is faster; and
- provide an optional relational count budget so high-branching languages can
  remain finite without host-side `take`.

The first implementation should prefer clarity over cleverness. If a fully
depth-stratified relation is too slow, the branch should record that result and
may introduce a second candidate relation with a different fair enumeration
order, provided the difference is documented and tested.

## Equality-Fragment Design

The relational prover should start from the existing relation-shaped layer in
`proflog.kernel.equality-fragment`, because it already expresses the intended
branch rules. The experiment should determine whether that layer can replace
the host path when paired with relational gamma enumeration and targeted search
control.

Likely implementation steps:

1. Add a separate namespace or clearly separated section for the experimental
   profile, so production host code remains easy to audit.
2. Thread relational gamma state through the equality-fragment relation instead
   of a precomputed candidate vector.
3. Keep proof-variable requirement handling from ADR-0042 semantically intact:
   disjunctive branch proofs must agree on shared proof-variable bindings
   instead of rebinding universal witnesses independently per branch.
4. Add a program-query entry helper that can be explicitly selected by tests
   and probes without changing `kernel/prove-program` defaults.
5. Only after the opt-in route passes correctness and timing gates, decide
   whether `kernel/prove-program` should try the relational profile before, in
   parallel with, or instead of the host profile.

The experiment must not reintroduce family-specific dispatch. It must remain
generic over call-free equality formulas and compiled Proflog languages.

## Test Obligations

The first tests for this ADR must fail before implementation. They should be
focused enough to prove absence and then presence of the new behavior.

Minimum red tests:

- a route-guard test proving the experimental entry point does not exist yet,
  then proving it returns `profiled relational-equality-fragment` evidence;
- a route-guard test that redefines `prove-program-host`,
  `gamma/closed-terms-for-fuel`, and deterministic host helpers to throw while
  the experimental relational route runs;
- gamma tests showing relational enumeration of constants, unary terms, binary
  terms, depth bounds, and count/fairness bounds;
- equality-fragment tests for direct equality, disequality, conjunction,
  disjunction, universal candidate use, existential parameters, and the
  ADR-0042 shared-witness bug;
- program-entry tests for both positive and negative calls over ground
  equality-fragment arguments; and
- genericity audits showing no group-verifier, transition-system, finite-domain
  example, or hard-family overlay names appear in the experimental production
  path.

The promoted finite verifier checks must include:

- at least one group-verifier success row;
- at least one group-verifier failure row;
- at least one transition-system totality or determinism success row; and
- at least one transition-system totality or determinism failure row.

The full ADR-0039 row set remains the promotion target, not the initial smoke
gate:

- `Z1` full associativity succeeds;
- `Z2` precomputed associativity succeeds;
- `Z2` full associativity succeeds;
- non-group precomputed associativity fails;
- non-group full associativity fails;
- complete deterministic transition totality succeeds;
- complete deterministic transition determinism succeeds;
- incomplete transition totality fails; and
- nondeterministic transition determinism fails.

## Performance Evaluation

Correctness is not sufficient. The existing host engine exists because the
ordinary relation-shaped equality-fragment path was too slow for the promoted
finite verifier rows.

The branch must therefore add a focused timing probe that runs the same formulas
through:

- the current host equality-fragment engine;
- the new relational equality-fragment engine with host gamma forbidden; and
- the ordinary full kernel fallback when practical.

The probe should record:

- elapsed wall time;
- whether proof evidence was found;
- proof markers;
- gamma depth and count bounds;
- fuel;
- whether the row was success or refutation; and
- whether the result is accepted, too slow, unresolved, or timed out.

"Plausibly acceptable performance" for this experiment means:

- all smoke rows complete in bounded focused tests rather than only in ad hoc
  REPL probes;
- at least one nontrivial promoted ADR-0039 row from each family completes
  under the relational route;
- timeouts are recorded as data, not silently skipped; and
- the AAR can make a defensible recommendation about promotion, partial use, or
  rejection.

If the relational route is correct but materially slower than the host route,
the branch may still merge the experimental code as an opt-in probe, but it
must not replace the production profile by default.

## Required Commands

During implementation, add a focused selector such as:

```text
lein test-proflog-relational-equality-fragment
```

The minimum verification before completing the implementation ADR is:

```text
lein test-proflog-relational-equality-fragment
lein test-proflog-kernel-finite-verifiers
lein test-proflog-fitting-programs
lein test-proflog-fast
lein test-proflog-extended
```

If the focused relational finite verifier rows are long-running, keep them out
of the fast suite and record their passing or timeout durations in
`docs/TEST_RUNTIME_BASELINE.md`.

## Exit Criteria

ADR-0057 is complete when:

- an opt-in relational equality-fragment profile exists and emits distinct
  proof evidence;
- the opt-in route can prove and refute call-free equality-fragment formulas
  without calling the deterministic equality-fragment host engine;
- relational gamma term enumeration is implemented and tested for constants,
  unary constructors, binary constructors, depth bounds, and finite count or
  fairness control;
- route guards prove the experimental path does not call
  `gamma/closed-terms-for-fuel` during proof;
- ADR-0042's shared-witness discipline remains covered under the relational
  path;
- representative GV and transition-system rows pass through the relational
  profile with timing data;
- the full ADR-0039 finite verifier row set is either promoted under the
  relational profile or explicitly classified with measured shortcomings;
- public documentation states whether equality-fragment remains production
  host-backed, becomes relational by default, or exposes both profiles; and
- an AAR records correctness, performance, semantic-surface impact, and the
  promotion decision.

## Non-Goals

This ADR does not require:

- changing the public fuel representation;
- replacing `kernel/prove-program` default dispatch before evidence is in;
- proving completeness for all first-order formulas with equality;
- supporting procedure-call recursion inside the equality fragment; or
- removing the host engine before a relational replacement has comparable
  coverage and documented performance.

## Risks

The likely failure mode is performance. Relational gamma enumeration can
increase branching before the equality-fragment branch search even starts, and
universal finite verifier formulas can multiply that branching quickly.

The second risk is accidentally weakening ADR-0042. Any relational branch split
must preserve shared proof-variable requirements across sibling branches. A
slower sound relation is preferable to a fast relation that can recreate
`:inconsistent` status results.

The third risk is semantic drift. The relational variant must prove the same
compiled formula representation as the host engine; it must not become a second
source-language evaluator or a group-verifier-specific verifier.
