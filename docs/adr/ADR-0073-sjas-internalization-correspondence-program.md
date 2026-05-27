# ADR-0073: SJAS Internalization Correspondence Program

- Status: in progress
- Date: 2026-05-25
- Branch: `adr-0073-sjas-correspondence-program`
- AAR: pending

## Context

ADR-0072 moved the SJAS implementation away from generated formula, axiom, and
proof-target registries. Later ADR-0073/Track-1 work also replaced the
non-`sjas-axiom` `tableau-proof/3` and `subst-prf/4` shortcut through
`kernel/prove-programo` with a local proof-directed checker for the currently
generated certificate shapes. The stronger proof-machinery goal is still not
finished: the checker must either become a complete arithmetic/formal
semantic-tableau predicate, or every remaining bridge and compact proof
constructor must be justified by the correspondence proof required below.

The refined concern is not simply that the call is host-side. The concern is
whether the call collapses distinctions that SJAS self-reference needs to
preserve. Willard's Type-A/tableau systems rely on a formalized proof predicate
for a concrete deduction apparatus. If the Proflog kernel accepts exactly the
same relevant proof objects as the SJAS-arithmetized semantic-tableau predicate,
and preserves every relevant proof-tree and size invariant, the kernel path can
be a justified implementation bridge. If it preserves only theorem-level
extension, it is not enough: theorem equivalence can erase the intensional
features that make tableau SJAS differ from Hilbert-style or list-style
systems.

The development note
[SJAS Internalization and Proflog Correspondence Program](../log/2026-05-25-sjas-internalization-correspondence-program.md)
records the exchange that split the prior "full internalization" goal into
three coordinated tracks. A later refinement added a speculative reciprocal
Track 2c: formalize the Proflog kernel itself as a candidate deductive
apparatus `D_Proflog` and determine whether Willard-style SJAS results can be
adapted to `IS#_{D_Proflog}(beta)`.

## Decision

Continue the ADR-0072 arithmeticization work, but treat it as one track of a
larger program rather than the only possible route to a justified SJAS claim.

Track 1 is direct arithmeticization. The implementation will continue replacing
host-side staged readers, source registries, decoded proof-term validation, and
other predicate-application shortcuts with object-language relations over
encoded formulas, systems, substitutions, proof trees, axiom membership, and
branch closure. This remains the strongest implementation endpoint.

Track 2a is relevance analysis. Before a Proflog correspondence can justify any
remaining bridge, the project must determine which intensional aspects of the
semantic-tableau apparatus are necessary for SJAS self-justification. The
working hypothesis is that the tableau-induced tree structure, rule-induced
branching, closure discipline, proof-object inspectability, and lower-bound
proof-size discipline are relevant. Rule search scheduling, implementation
caching, and host data representation are expected to be irrelevant only when a
proof shows that they do not alter the accepted proof objects or the relevant
size/tree measures. Ambiguous extensions, including equality and
procedure-call/profile rules, remain unresolved until classified.

Track 2b is Proflog correspondence. This track depends on Track 2a. The project
must prove and test a bidirectional correspondence of the following shape:

```text
ProflogAccepts(P, S, F)
iff
SJAS_TableauProof(code(P), code(S), code(F))
```

The proof must preserve every intensional measure classified as relevant by
Track 2a, and must prove irrelevance for every implementation detail that is
ignored. Operational tests are required in addition to the proof; they do not
substitute for it.

"Proof" in this track means a proper formal or formalizable correspondence
argument, not an informal comparison of passing examples. At minimum, the
Proflog kernel proof relation must be specified in terms compatible with the
SJAS semantic-tableau specification, so both sides can be compared for
equivalence or the absence of equivalence can be identified precisely. If direct
examination of the implementation and SJAS specification cannot support that
level of rigor, one or both sides must be encoded into a third-party formal
verification setting, or both must be lifted into a common intermediate
semantics that admits rigorous equivalence checking.

Track 2c is Proflog-as-D formalization. It is speculative and reciprocal to
Track 2b. Instead of asking whether Proflog acceptance corresponds to
Willard's selected semantic-tableau `D`, it asks whether the Proflog kernel can
itself be specified as a deductive apparatus `D_Proflog` and whether the SJAS
literature's results can be adapted to `IS#_{D_Proflog}(beta)`. This route
requires a stable mathematical semantics for the Proflog kernel, including its
proof states, branch agendas, equality/disequality machinery, procedure calls,
profile rules, proof certificates, and proof-size measure. It does not license
using "whatever the implementation currently accepts" as `D`.

## Consequences

- ADR-0072 remains useful and active: each removed host shortcut reduces the
  proof burden of any eventual correspondence theorem.
- A kernel call is not accepted merely because Proflog is sound for the same
  formulas. It is accepted only if the relevant proof-object correspondence is
  established.
- A theorem-level "Proflog proves exactly what SJAS proves" result is
  insufficient if it loses proof-tree, closure, encoding, or proof-size facts
  relevant to SJAS self-reference.
- Some implementation work should wait for the relevance matrix. Code that
  would preserve irrelevant details only for their own sake is not required,
  while code that hides relevant structure must be replaced or justified by a
  bridge theorem.
- The correspondence work may identify Proflog implementation details that need
  explicit proof-term instrumentation or tests even when no user-visible theorem
  result changes.
- Track 2c, if pursued, has its own burden: define `D_Proflog` as a formal
  calculus and adapt the relevant Willard proof obligations to that calculus.
  It is not a weaker version of Track 2b.

## Test Obligations

Tests must be red before implementation and then pass for any code slice.
Documentation-only slices must still pass `git diff --check` before commit.

- Track 1 tests must expose the specific host shortcut being removed and show
  that the relevant predicate still succeeds or fails through object-level
  relations after the shortcut is gone.
- Track 2a must maintain a relevance matrix that names each intensional aspect,
  classifies it as relevant, irrelevant, or unresolved, cites the project or
  Willard source for that classification when available, and records the proof
  obligation created by the classification.
- Track 2b must include both a written proof artifact and operational tests.
  The tests must exercise representative proof objects in both directions of
  the correspondence and must include negative cases for features classified as
  relevant.
- The Track 2b proof artifact must state the formal semantics used for the
  Proflog kernel side and for the SJAS tableau side. It must justify why those
  semantics are compatible enough for equivalence checking, or document the
  selected third-party formalization/common intermediate language used to make
  the comparison rigorous.
- Track 2c artifacts must state the formal semantics of `D_Proflog`, the proof
  certificate grammar, the proof-size/lower-bound measure, and which
  Proflog-specific rules are primitives, bounded macros, irrelevant runtime
  details, or excluded features.
- For semantic implementation commits, use the focused SJAS practice recorded
  in `AGENTS.md`: exact selectors first, then `lein test-vars <namespace>` or
  `lein test-proflog-sjas-focused` for expensive SJAS namespaces. Broad gates
  remain `lein test-proflog-fast` and `lein test-proflog-extended`; use the
  opaque `lein test-proflog-sjas` gate only when it adds signal or as a final
  full namespace confirmation if its runtime is acceptable.

## Exit Criteria

- The remaining ADR-0072 host paths are audited and either internalized or
  justified by the Track 2b correspondence proof.
- The Track 2a relevance matrix is complete enough to classify every proof
  object, rule, encoding, closure, search, and size feature relied on by the
  current Proflog SJAS implementation.
- A written correspondence proof is supplied for the Proflog kernel and the
  SJAS-specified semantic-tableau predicate, preserving all relevant measures
  and proving irrelevance for ignored implementation details.
- The proof is based on compatible formal specifications of both sides, or on a
  third-party/common-intermediate formalization capable of checking the claimed
  equivalence.
- Operational tests demonstrate the correspondence over the implemented proof
  machinery, including representative positive and negative cases.
- The implementation passes `lein test-proflog-fast`,
  `lein test-proflog-extended`, and `lein test-proflog-sjas`; any deliberately
  deferred slow probes are recorded under `docs/log/` with rationale.
- The AAR records which route justified each remaining bridge: direct
  arithmeticization, proved-and-tested correspondence, adopted
  Proflog-as-D formalization, or explicitly deferred future work approved by
  the user.
