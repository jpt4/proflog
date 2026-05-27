# ADR-0019: Closed-Term Gamma Instantiation

- Status: completed
- Date: 2026-04-27
- Branch: `adr-0019-closed-term-gamma-instantiation`
- AAR: [AAR-0019](../aar/AAR-0019-closed-term-gamma-instantiation.md)

## Context

ADR-0018 fixed a narrow gap in the translation of Fitting's tableau rules: a
`once-forall` formula produced by negating an existential clause body can now
instantiate with declared nullary object-language terms. That was enough for:

```prolog
p(x) :- exists y. x != y
```

over the finite language `{a, b}`. Proving `p(a)` now chooses `y = b`, closes
`a = b` by free-constructor mismatch, and never exports an internal `(par ...)`
answer.

The remaining issue is broader. Fitting's paper gives the gamma rule as:

```text
gamma
gamma(t)  for any closed term t of Lpar
```

The paper also says that this raw rule is not suitable for implementation as
stated, because the prover must decide which closed terms to try. The
greenfield implementation followed the standard automation route Fitting
mentions: introduce free variables and later use unification to close branches.
That is practical, but ADR-0018 showed it is incomplete when a proof needs a
specific closed term to force a constructor clash or disequality failure.

The project therefore needs an explicit design for fair, bounded closed-term
gamma instantiation.

## Decision

- Add a generic closed-term candidate generator for gamma-style instantiation.
  The expected implementation boundary is a small namespace such as
  `proflog.gamma` or `proflog.term-generation`, plus a thin relation called
  from the kernel.
- Do not add family-specific or constructor-specific generation code. A single
  mechanism should inspect the declared language:
  - nullary function symbols produce constants,
  - n-ary function symbols produce compound terms from smaller generated terms,
  - declared order gives stable enumeration order,
  - constructor depth or constructor size supplies the finite bound for each
    search slice.
- Treat ordinary constants as arity-0 constructors. Lists, Peano numerals, and
  user-defined tree terms should all fall out of the same generator when their
  constructors are declared in the language. No separate `list`, `nat`, or
  family-specific generator is authorized by this ADR.
- Extend gamma and `once-forall` instantiation so they can fairly choose:
  - the existing free-variable instantiation path,
  - generated closed terms of `L` up to the current bound,
  - and, if needed for Fitting parity, already-introduced branch parameters
    from `Lpar`.
- Do not let the generator weaken the procedure-call boundary. Atoms containing
  unresolved `(par ...)` terms remain outside `L` and must not invoke program
  clauses.
- Do not let generated terms weaken answer export. Public answers, residuals,
  and user-visible witnesses must still be in `L` unless a later ADR explicitly
  defines a different semantic profile.
- Keep the implementation bounded and fair. The full rule is a semidecision
  procedure in practice: each finite fuel/depth slice tries a finite set of
  terms, while iterative deepening increases the bound.

## Constructor Generation

Compound constructors do not need separate support one-by-one. The language
already records function symbols and arities. A generic generator can produce
closed terms by depth:

```text
depth 0: all nullary functions
depth n+1: f(t1, ..., tk) for every declared k-ary f
           where each ti has depth <= n
           and at least one ti has depth exactly n
```

The same idea can be phrased by constructor size instead of depth. Either way,
the generator is data-driven by the language declaration. A program that
declares:

```clojure
{:constants ['null 'zero]
 :functions {'cons 2
             's 1}}
```

automatically gets candidates such as `null`, `zero`, `s(zero)`,
`cons(null, null)`, and larger terms as the bound increases.

This is intentionally the same shape as the bounded answer materializers, but
the kernel-facing generator must be designed for proof search rather than
public answer enumeration.

## Namespace Boundary

A new namespace should make the kernel more transparent, not less.

Fitting's rule belongs in `proflog.kernel` as a small, readable clause:
"instantiate this gamma formula with one admissible candidate term." The
operational machinery for enumerating those candidates is not the paper's
logical rule; it is the implementation strategy needed because the paper
explicitly warns that unrestricted closed-term choice is not directly
automatable.

Putting constructor enumeration, depth accounting, ordering, and parameter
policy inline in `proflog.kernel` would obscure the direct tableau translation.
Keeping that machinery in a focused namespace preserves two review surfaces:

- `proflog.kernel` remains the readable Fitting-style rule interpreter.
- `proflog.gamma` / `proflog.term-generation` documents the finite search
  policy that makes Fitting's gamma rule operational.

The boundary should stay thin. The kernel should not delegate semantic control
to an opaque optimizer; it should call a documented relation whose contract is
"produce one closed gamma candidate under these bounds."

## Consequences

- The prover can recover more of Fitting's full gamma behavior without adding
  one-off handlers for lists, numerals, or particular tests.
- Search cost will grow quickly for languages with high-arity constructors.
  Bounds, ordering, duplicate elimination, and fuel accounting are part of the
  feature, not optional performance tuning.
- Existing symbolic answer behavior must be protected. ADR-0018 showed that
  concrete term generation inside answer mode can accidentally collapse
  symbolic residual frontiers.
- This may interact with ADR-0017 tabling. Generated terms increase the number
  of reachable states, so canonicalization and duplicate-state reuse should be
  measured rather than assumed.
- The implementation remains a semidecision procedure. If a proof requires a
  term deeper than the current bound, the bounded slice may remain unresolved.

## Test Obligations

- Add the ADR-0019 gate namespace
  `test/proflog/closed_term_gamma_test.clj`. It should fail before the
  constructor-generic gamma generator is implemented.
- Cover proof-level behavior through public query APIs, not by testing a
  family-specific helper:
  - `exists y. y != zero` succeeds only when gamma can generate `s(zero)`,
  - `exists y. y != zero and y != s(zero)` succeeds only when gamma can
    generate `s(s(zero))`,
  - `forall y. y = zero or y = s(zero)` fails only when gamma can generate
    the compound counterexample `s(s(zero))`,
  - `exists t. t != leaf` succeeds only when gamma can generate
    `node(leaf, leaf)`,
  - `exists t. t != leaf and t != node(leaf, leaf)` succeeds only when gamma
    can generate a deeper binary tree.
- Add unit tests for the generic term generator:
  - constants at depth `0`,
  - unary constructors such as Peano `s/1`,
  - binary constructors such as `cons/2`,
  - mixed signatures,
  - stable declaration ordering,
  - no duplicate terms across depth slices.
- Add kernel tests proving `once-forall` can close with a compound generated
  term, not only a nullary constant.
- Add an ordinary `forall` regression showing repeated gamma instantiation can
  eventually try deeper closed terms under iterative deepening.
- Add negative tests proving procedure calls still reject atoms containing
  unresolved `(par ...)` terms.
- Add answer-boundary tests proving generated closed terms do not leak
  internal parameters and do not break existing symbolic `query-answers`
  frontiers.

## Exit Criteria

- A generic closed-term generator exists and is documented.
- No constructor family has a special-purpose generator in the proof kernel.
- Gamma and `once-forall` can instantiate from generated closed terms under an
  explicit finite bound.
- The ADR-0018 existential disequality gate remains green.
- `lein test-proflog-fast` passes.
- Relevant answer and list-family tests either pass or have documented
  measurements explaining any changed search profile.
