# 2026-04-29 List-Family Kernel Search Plan

## Prompt

Devise a plan for purely relational, generic enhancements to the prover kernel,
such that the class of formulas tested by the list family pass. Branch to a new
ADR for this feature implementation.

## Context

ADR-0029 made `step-fuelo` relational using finite-domain constraints. That
repaired open-fuel reverse and partial synthesis modes, but it did not improve
the legacy-passing raw list proofs enough to close promptly:

- `append([a,b], [c], [a,b,c])` timed out under a 45 second targeted run.
- `reverse([a,b], [b,a])` timed out under a 45 second targeted run.

The historical diagnosis from AAR-0017 still applies: the answer surface has
compatibility materializers for known list-family answers, but the raw kernel
search remains much slower than legacy on multi-step ground recursive list
proofs.

## Plan

The new ADR is [ADR-0030: Relational Constructor Search Control](../adr/ADR-0030-relational-constructor-search.md).

The plan treats the list family as a benchmark for a generic class:
constructor-recursive definite programs with equality/disequality guards. It
forbids production special-casing of `append`, `reverse`, `member`, `cons`, or
`null`.

The proposed implementation sequence is:

1. Add a focused long-running raw-proof selector for the known list gap and one
   non-list constructor-recursive control case.
2. Add pure rigid constructor disequality discharge so permanently true
   disequalities such as `cons(a, t) != null` do not remain as delayed branch
   state.
3. Add structural agenda focusing so equality, rigid disequality, closure, and
   callable literals are tried before beta/gamma/delta branch generation, with
   the current fair scheduler preserved as fallback.
4. Add guarded procedure-call descent using generic body alternatives and
   equality/disequality guard saturation before recursive calls.
5. If necessary, add a pure call-stack descent preference for recursive calls
   whose walked arguments are proper constructor subterms of an ancestor call.

## Kernel-Purity Constraints

The implementation must keep the ADR-0027 through ADR-0029 purity gains:

- no executable `core.logic/project` in the ordinary kernel-facing path;
- no host-side list recognizer or proof oracle;
- reverse and partial synthesis tests stay green;
- profiled Pelletier layer interoperation continues to work;
- any optimization fails conservatively back to the ordinary full kernel.
