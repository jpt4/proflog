# ADR-0062: Non-Vacuous SJAS Self-Justification Demonstration

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0062-sjas-self-justification-demonstration`
- AAR: [AAR-0062](../aar/AAR-0062-sjas-self-justification-demonstration.md)

## Context

ADR-0060 and ADR-0061 built the Willard SJAS substrate: finite reflected
systems, generated Group-3 formulas, binary U-grounding arithmetic, and
kernel-checked `tableau-proof/3` certificates.

A review of the generated `SelfCons0` sentence exposed a remaining semantic
gap. The object formula correctly said:

```text
forall p. not tableau-proof(this-system, contradiction-code, p)
```

but `contradiction-code` was only a literal object-language symbol. It had no
entry in `:sjas/proof-targets`, so a `tableau-proof/3` query against it failed
because the checker could not find a theorem target, not because the supplied
certificate failed to prove a real contradiction.

The local Willard corpus requires more. The 2001 paper defines Group-3 for
`IS(A)` as a single sentence saying that there is no semantic-tableaux proof of
`0 = 1` from `IS(A)` itself. The 2013/2014 analytic-tableaux paper phrases the
same self-reference as no proof, under deduction method `D`, from Group-0,
Group-1, Group-2, and "this sentence" looking at itself. Definition 5.1's
finite `IS#_D(beta)` form explicitly says the "I am" fragment changes when the
finite beta basis changes.

In Proflog's tableau API, theorem proving is represented by closing the negated
query. The clean contradiction theorem target is therefore `false`: to prove
`false` from the generated axiom basis, the checker must close the axiom basis
itself. If the basis is consistent, no proof should be found; if the basis
contains an explicit false axiom, the same checker should validate a real
contradiction certificate.

The Level-1 profile has a parallel non-vacuity risk. `SelfCons1` quantifies over
complementary theorem codes. If `not-code(c)` has no concrete target, then proof
checks for complements fail by lookup instead of by tableau proof checking.

## Decision

ADR-0062 makes SJAS self-justification non-vacuous at the generated proof-target
boundary.

The system builder will add these generated proof-target entries:

- `contradiction-code` maps to the theorem target for `false` from the generated
  SJAS axiom basis;
- every generated axiom formula code `c` keeps its existing theorem target;
- every complement code `not-code(c)` maps to the theorem target for the NNF
  complement of that formula.

The ordinary `tableau-proof/3` checker remains unchanged in spirit: it decodes a
concrete proof certificate and calls the kernel relation with the decoded proof
term supplied. No host-side proof oracle, shortcut consistency checker, or
special-case answer for contradiction is introduced.

This ADR still does not claim Tab-1 proof-list reuse or Willard's external
consistency-preservation metatheorem. It claims a non-vacuous executable
demonstration for the implemented ordinary-tableau `SelfCons0` and plain
tableau Level-1 substrate.

## Consequences

- A consistent generated system can continue to include and prove its own
  Group-3 sentence as an object-language axiom.
- A malformed or wrong contradiction certificate is rejected because it does not
  close the concrete contradiction target, not because the contradiction code is
  unknown.
- An intentionally inconsistent control system can produce a contradiction
  certificate, and `tableau-proof(this-system, contradiction-code, cert)` must
  accept it. That is the regression proving the predicate is substantive.
- Level-1 complement probes can inspect concrete complement targets, even when
  open proof-code synthesis remains too expensive for the focused suite.

## Test Obligations

Tests must be red before implementation and then pass:

- the builder must expose a proof target for `contradiction-code`;
- that target must equal the theorem target for `false` from the generated SJAS
  basis;
- the builder must expose proof targets for `not-code(c)` complement codes;
- a consistent demo system must prove its generated Group-3 sentence and the
  generated certificate must validate against the Group-3 theorem code;
- an inconsistent control system with an explicit false beta axiom must generate
  a real contradiction proof, encode it as a certificate, and validate it
  through `tableau-proof(system, contradiction-code, cert)`.

## Exit Criteria

- `lein test-proflog-sjas` passes and records runtime.
- `lein test-proflog-fast` passes and records runtime.
- `lein test-proflog-extended` is run before merge because the generated
  proof-target boundary feeds proof search and negation behavior.
- The worked SJAS example, runtime baseline, ADR/AAR indexes, and development log
  describe the non-vacuous self-justification demonstration and its remaining
  limits.
