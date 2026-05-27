# ADR-0067: SJAS Structural Formula-Code Decoder

- Status: completed
- Date: 2026-05-14
- Branch: `adr-0067-sjas-code-decoder`
- AAR: [AAR-0067](../aar/AAR-0067-sjas-structural-code-decoder.md)

## Context

ADR-0063 replaced hash-like formula labels with inspectable base-64 Godel-code
terms, and ADR-0066 exposed the Level-1 substitution boundary as
`subst-code/2`. The remaining fidelity gap is that several SJAS predicates still
consult finite generated registries:

- `wff/1`;
- `delta-star-0-code/1`, `pi-star-1-code/1`, and `sigma-star-1-code/1`;
- `neg-pair/2`;
- identity cases of `subst-code/2`.

Those tables are correct for generated Group-0 through Group-3 axioms, but they
do not show that the predicates consume the code grammar itself. A caller can
construct the Godel code for a well-formed formula in the active language, yet
the current profile rejects it unless the formula happened to be one of the
finite generated axiom/complement entries.

This matters for the SJAS objective because Willard's self-reference is about
arithmetized syntax and proof predicates, not merely about a finite host-side
codebook. `tableau-proof/3` may still need a finite bridge from theorem code to
kernel formula while proof checking is implemented through Proflog's existing
AST kernel, but syntax recognition, formula-class recognition, negation-pair
recognition, and closed identity substitution can be structural over the code
bytes now.

## Decision

Add a kernel-level structural decoder for the formula-code byte grammar already
defined by `proflog.willard-sjas-code`.

The decoder will:

- read public compact code terms through the existing `sjas-code-byteso`
  relation;
- parse formula and term tags, counted argument lists, relation/function symbol
  indexes, variable indexes, parameter indexes, embedded code terms, and bounded
  quantifier payloads;
- classify decoded formulas as Delta-star-0, Pi-star-1, or Sigma-star-1 from
  the byte grammar;
- recognize complement code pairs by structurally decoding the left code to a
  formula, constructing the ordinary negation complement, and checking the right
  code against that complement;
- accept identity `subst-code(source, source)` for any structurally well-formed
  closed formula code in the active system's coding context;
- preserve the generated Level-1 fixed-point substitution entry
  `selfcons-skeleton-code -> group-three-code`.

Generated registries remain permitted where they express finite system
membership, such as `axiom-member/2` and the current theorem-code-to-kernel-AST
bridge used by `tableau-proof/3`. They should no longer be the only route for
formula syntax, formula classes, negation pairs, or closed identity
substitution.

## Consequences

- The SJAS profile demonstrates more of the arithmetized syntax layer
  relationally, not by finite host enumeration.
- User-constructed formula codes in the system's declared language can be
  inspected by SJAS syntax predicates even when they are not generated axioms.
- Slow tests are acceptable and will be marked with `^:slow` when they exercise
  semantic obligations rather than default-regression ergonomics.
- The remaining larger boundary is still theorem proof checking for arbitrary
  theorem codes: `tableau-proof/3` must eventually decode an arbitrary theorem
  code into a kernel formula, or replace the kernel-AST bridge with a fully
  code-level proof checker.

## Test Obligations

- A red test must show that `wff(non-generated-formula-code)` fails before the
  structural decoder.
- A red test must show that `neg-pair(non-generated-code,
  complement-code)` fails before the structural decoder.
- A red test must show that `subst-code(non-generated-code,
  non-generated-code)` fails before the structural identity route.
- Post-implementation, the same tests must pass through the SJAS profile.
- Existing generated axiom, fixed-point, and proof-certificate tests remain
  green.
- Passing focused, SJAS, fast, and extended suite runtimes are recorded.
