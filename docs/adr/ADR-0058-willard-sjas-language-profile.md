# ADR-0058: Willard SJAS Language Profile Design

- Status: completed
- Date: 2026-05-10
- Branch: `adr-0058-sjas-profile-design`
- AAR: [AAR-0060](../aar/AAR-0060-willard-sjas-mvp.md)

## Context

The nested SJAS archive now contains a public-witness corpus for Dan Willard's
self-verifying and self-justifying axiom-system work. The implementation
question is how to translate that material into a Proflog language profile
without confusing three different layers:

- the object-language arithmetic whose symbols appear in formulas;
- the deduction apparatus whose consistency is being reflected;
- the metatheorem that the constructed system is actually consistent.

The detailed extraction note is
[Willard SJAS Profile Design Notes](../log/2026-05-10-willard-sjas-profile-design.md).

The existing Proflog architecture already has a generic proof-profile dispatch
layer and a kernel-interleaved theory hook from the Robinson-Q work. That is the
right extension point for SJAS. A new SJAS profile must not be a host-side proof
checker or a whole-formula preprocessor hidden behind the query API.

The purpose of this work is not only to reproduce a metamathematical
construction. It is to investigate the correspondence between Willard-style
logical restrictions and the computational behavior of an executable
`SJAS-lang`: what must be mechanized to make the axioms and deductive apparatus
run inside Proflog, and what kinds of programs become possible or impossible
once multiplication, reflection, proof coding, and bounded formula classes are
made executable rather than informal.

## Decision

Design the SJAS work as a staged Type-A semantic-tableaux profile sequence.

The first implementation pass should target the least complex corpus-faithful
mechanization: an ordinary semantic-tableaux `IS(A)`-style profile, tentatively
named `:willard-sjas-tableau0`. This pass does not add proof-list, Tab-k, or
Tab-1 theorem reuse. It reflects the earlier line where the self-consistency
axiom says there is no semantic-tableaux proof of contradiction from this
system.

The second pass should promote the profile to the Level-1 `ISD(A)` /
`IS#_D(beta)` line, named `:willard-sjas-level1`. This line may still select
plain semantic tableaux as the reflected deduction method `D`; Tab-1 theorem
reuse is an additional stronger apparatus, not a prerequisite for the first
executable SJAS-lang.

Both passes target the Willard line where:

- addition is available as a total function;
- multiplication is represented as a three-place relation, not a total
  function symbol;
- the base deduction apparatus is Fitting/Smullyan style semantic tableaux;
- stronger proof-list/Tab-1 reuse is optional and must be explicitly named when
  claimed;
- self-consistency is either Level-0-minus contradiction-freedom for the first
  `IS(A)`-style pass or Level-1 no-Pi-star-1/complement-pair consistency for
  the later `ISD(A)` / `IS#_D(beta)` pass.

Defer the Hilbert/theta-function line. It is mathematically important, but it is
less aligned with the current Proflog kernel and includes a conjectural premise
in Willard's 2016 presentation.

## Designed Architecture

Add a public namespace such as `proflog.willard-sjas` for corpus-derived data
and formula construction:

- `u-grounding-language`;
- `tableau0-profile-language`;
- `level1-profile-language`;
- U-grounding term builders;
- bounded quantifier constructors;
- Delta-star-0 / Pi-star-1 / Sigma-star-1 classifiers;
- ordinary-tableau `SelfCons0` and Level-1 `SelfCons1` formula construction;
- finite `IS#_D(beta)`-style system construction.

Add a kernel profile namespace such as `proflog.kernel.willard-sjas-profile`:

- relational U-grounding arithmetic normalization and graph checking;
- relational proof-certificate checking for miniature semantic-tableau proofs;
- optional Tab-1 proof-list checking;
- a `willard-sjas-theory-closeo` rule bound through
  `kernel/*theory-profile-closeo*`.

Extend `proflog.proof-profile/prove-program*` with:

```clojure
(defmethod prove-program* :willard-sjas-tableau0
  [_profile program formula proof-limit fuel]
  (willard-sjas-profile/prove-program program formula proof-limit fuel))

(defmethod prove-program* :willard-sjas-level1
  [_profile program formula proof-limit fuel]
  (willard-sjas-profile/prove-program program formula proof-limit fuel))
```

Proof terms must expose profile use:

```clojure
(profiled willard-sjas-tableau0 ...)
(profiled willard-sjas-level1 ...)
```

The profile may use host Clojure while translating source text into a kernel
formula representation, including assigning stable codes and constructing a
fixed-point formula. After translation, it must not use host Clojure to decide
bounded arithmetic truth, formula-class membership, or proof-certificate
validity.

## Authoring Model

Users should not hand-write the self-consistency axiom. They should write an
SJAS system through a thin SJAS-specific frontend that:

- accepts the selected profile, such as `:willard-sjas-tableau0`;
- accepts the user-visible U-grounding language extensions and ordinary
  Proflog relation clauses;
- accepts a finite beta list of user-supplied proper axioms for the first
  demonstrator;
- generates Group-Zero, Group-1, and Group-2;
- reserves stable formula identifiers for every generated and user-supplied
  axiom;
- generates Group-3 once as `SelfCons0` or `SelfCons1`;
- compiles a query entrypoint that proves from the generated SJAS axiom basis.

The frontend must make the reflected-system boundary explicit. In the default
"program in SJAS" mode, user beta axioms and any user program clauses intended
to be cited by the internal proof predicate are part of the generated reflected
system. They should be compiled as finite Group-2 data, or as a named finite
Group-2b extension, before Group-3 is generated. Changing these clauses changes
the system whose consistency is asserted, so it must also change the generated
Group-3 formula and its system identifier.

An optional external-application mode may let a fixed SJAS basis be reused by
ordinary Proflog code. In that mode, external clauses are not axioms of "this
system"; the reflected proof predicate must not cite them through
`axiom-member`, and `SelfCons0` / `SelfCons1` does not assert their consistency.
The first tutorial path should prefer the reflected mode, because it is the one
that corresponds to writing a program inside the SJAS rather than merely calling
an SJAS theory from outside.

Current Proflog programs do not have a general free-standing axiom-context slot.
They mostly expose relation clauses; examples such as Robinson Q and Pelletier
compose axioms into theorem formulas. SJAS therefore needs a new generated
system object or query wrapper that carries both the compiled Proflog clauses
and the generated axiom basis. Query execution can initially prove:

```text
Group-Zero and Group-1 and Group-2 and Group-3 -> user theorem
```

while the reflected proof predicate simultaneously reasons over generated
object-language facts such as:

```text
axiom-member(this-system, formula-code)
```

The `axiom-member` layer is new to SJAS. It is required because SJAS reasons
inside the object language about proofs from "this system"; ordinary Q proofs
do not need it because Q axioms are either external antecedents or trusted
profile rules selected by the Proflog prover.

## Axiom Group Placement

The Proflog language declaration only declares the SJAS signature. It names
symbols such as `zero`, `one`, U-grounding functions, order relations, `mult/3`,
and proof-coding predicates. It does not by itself assert that those symbols
behave correctly.

The SJAS builder must generate a named axiom basis and compile it into the
program being reflected:

- Group-Zero behavior lives as proper axioms and, where needed for execution,
  relation-backed definitions for the initial constants and U-grounding
  operations. The language declaration contains the symbols; the axiom group
  gives them their object-language behavior.
- Group-1 lives as the finite grounding/coding prelude: Pi-star-1 axioms and
  Proflog relations sufficient for Delta-star-0 arithmetic, order, syntax-code,
  and proof-code facts needed by the demonstrator.
- Group-2 should be finite in the first implementation. For an `IS#_D(beta)`
  demonstrator, beta is a finite list of Pi-star-1 axioms supplied as ordinary
  proper axioms. Reflected user program clauses belong here as finite beta data,
  or in a separately named finite Group-2b extension, when those clauses are to
  be available to the internal proof predicate. The infinite `ISD(A)` schema is
  a later generalization.
- Group-3 is the generated self-consistency fixed-point formula. It is an
  ordinary proper axiom of the reflected SJAS, not an unlabelled host-side rule.
  Its code for "this system" must include Group-Zero, Group-1, Group-2, any
  reflected Group-2b user extension, and the Group-3 formula itself. It is
  therefore generated per reflected system, not shared across systems with
  different beta/program clauses.

Predicates for coding logical statements as SJAS-arithmetic terms are declared
in the language and defined in the Group-1/coding prelude. Examples include
`wff`, `pi-star-1-code`, `neg-pair`, `axiom-member`, `subst-proof`, and
`tableau-proof`. The profile may accelerate these only through auditable
relational theory rules; it must not replace them with a host proof checker.

## Consequences

- This gives Proflog a clear route to demonstrate a nontrivial Willard-style
  self-justifying axiom system while preserving the distinction between formal
  proof execution and external metatheory.
- The first profile is intentionally modest. It should demonstrate an
  `IS(A)`-style ordinary-tableau system before claiming Level-1, Tab-1, or full
  ISD(A).
- The formula classifier and proof-certificate checker are likely to be the
  main performance risks.
- Documentation must state that bounded contradiction probes do not prove
  consistency. Willard's actual consistency-preservation theorem remains a
  mathematical metatheorem unless separately mechanized.

## Test Obligations

The implementation ADR that promotes this design must start with failing tests
for each item below.

- Language tests show that the profile language contains U-grounding functions
  and `mult/3`, but no `mul/2` multiplication function.
- Classifier tests cover positive and negative Delta-star-0, Pi-star-1, and
  Sigma-star-1 examples.
- Arithmetic tests exercise forward, answer, and partial-synthesis modes for
  representative U-grounding operations and the `mult/3` graph relation.
- Proof-certificate tests accept valid miniature tableau proofs and reject
  malformed or open-branch certificates.
- If Tab-1 is claimed, proof-list tests enforce the intermediate theorem class
  restriction.
- The first finite SJAS demonstrator exposes its generated ordinary-tableau
  self-consistency axiom and proves at least one ordinary beta consequence
  through the selected profile.
- The later Level-1 demonstrator proves or exposes its generated `SelfCons1`
  statement through the selected profile.
- Authoring tests show that a user can create an SJAS system by supplying
  profile choice, beta axioms, and ordinary Proflog clauses, without manually
  constructing Group-3 or object-language axiom membership.
- Boundary tests show that changing a reflected beta axiom or reflected program
  clause changes the generated system id and Group-3 formula, while changing an
  external-application clause does not.
- Bounded contradiction probes try to find simultaneous Pi-star-1/complement
  proofs and record their outcomes and timings.
- Source audits reject host proof checkers and whole-formula proof-time
  normalizers in the promoted profile path.

## Exit Criteria

- The design note remains linked from this ADR and from `LOG.md`.
- A future implementation ADR can follow this record without re-reviewing the
  Willard corpus from scratch.
- The staged implementation targets, profile names, proof routes,
  source/proof-time boundary, test obligations, axiom-group placement, and
  known shortcomings are explicit.

## After Action Summary

ADR-0060 implemented the MVP SJAS-lang substrate described here. The completed
artifact adds `proflog.willard-sjas`,
`proflog.kernel.willard-sjas-profile`, the focused `lein test-proflog-sjas`
selector, worked examples, runtime records, and [AAR-0060](../aar/AAR-0060-willard-sjas-mvp.md).

The implementation intentionally remains an MVP: it includes finite generated
axiom bases, profile evidence, formula-class checks, finite arithmetic graph
facts, and miniature relation-backed proof-certificate predicates. It does not
claim Tab-1 theorem reuse, a complete tableau certificate checker, full
U-grounding arithmetic, or Willard's external consistency-preservation
metatheorem.
