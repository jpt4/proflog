# ADR-0068: SJAS Structural Theorem-Code Targets

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0068-sjas-theorem-code-targets`
- AAR: [AAR-0068](../aar/AAR-0068-sjas-structural-theorem-targets.md)

## Context

ADR-0067 made SJAS syntax predicates parse formula-code bytes structurally.
However, `tableau-proof/3` and the proof-checking branches of `subst-prf/4`
still rely on a finite bridge from theorem code to the Proflog kernel formula
that should be refuted while checking a proof certificate. That bridge is
adequate for generated Group axioms, but it weakens the claim that proof
predicates operate over Godel codes of sentences in the active SJAS language.

The next necessary step is to let `tableau-proof/3` use the structural formula
decoder for theorem targets that are not generated axioms. Once a theorem code
is decoded, the proof checker can construct the same kernel formula it already
uses for generated theorem targets:

```text
axiom-basis AND not(theorem)
```

The proof certificate is still a compact proof-code term decoded by the SJAS
profile, and the refutation still runs through the core Proflog proof kernel.
The change is specifically about removing finite formula-entry lookup as the
only way to turn a theorem code into a proof target.

## Decision

Add structural theorem-target routes to `tableau-proof/3` and `subst-prf/4`.

The route will:

- decode the theorem code with the ADR-0067 formula-code byte parser;
- compute the NNF complement of the decoded formula structurally;
- translate the decoded internal formula tree into the ordinary kernel AST,
  using a fixed finite map from code variable indexes to object-language noms;
- run the existing kernel proof checker against
  `system-axiom-formula AND decoded-complement`;
- preserve the generated axiom-citation route for `sjas-axiom` certificates;
- let `subst-prf/4` use the same structural theorem target when
  `subst-code/2` supplies a structural identity substitution;
- preserve generated formula entries as a fast/compatible route for existing
  Group axioms and complements.

This is still not a separate proof-list theorem-reuse implementation. It keeps
the current semantic-tableau proof checker, but the theorem sentence supplied to
that checker is now built from the theorem Godel code rather than from a finite
host formula registry.

## Consequences

- `tableau-proof/3` and `subst-prf/4` can check a certificate for a
  non-generated theorem code when the theorem itself is provable from the active
  SJAS basis.
- Generated Group axiom proofs remain valid and should not slow down by taking
  the structural route first.
- Structural theorem codes containing ordinary object-language formulas become
  first-class proof targets.
- Embedded code terms inside arbitrary theorem formulas remain more expensive;
  the implementation may keep generated entries as the preferred route for
  generated `SelfCons` formulas with large embedded code payloads.

## Test Obligations

- A red test must show that `tableau-proof(system, code(lt(1,2)), cert)` fails
  before implementation even though `lt(1,2)` is provable by the SJAS
  arithmetic profile and the code is structurally well formed.
- A red test must show the analogous identity-substitution
  `subst-prf(system, code(lt(1,2)), code(lt(1,2)), cert)` failure before the
  structural theorem route is applied there.
- After implementation, both tests must pass with real proof certificates
  generated from `sjas/query-succeeds`.
- A negative control must reject the same certificate when the theorem code is
  replaced by an unproved or different non-generated theorem code.
- Existing generated axiom, fixed-point, and structural syntax tests remain
  green.
- Passing focused, SJAS, fast, and extended suite runtimes are recorded.
