# ADR-0030: Relational Constructor Search Control

- Status: completed
- Date: 2026-04-29
- Branch: `adr-0030-relational-constructor-search`
- AAR: [AAR-0030](../aar/AAR-0030-relational-constructor-search.md)
- Depends On: [ADR-0029](ADR-0029-relational-fuel-purity.md)

## Context

ADR-0027 through ADR-0029 recovered transitive relational purity for the
ordinary kernel path: formula substitution, saved disequality maintenance, the
ADR-0026 branch profiler, and fuel stepping are now structural relations rather
than `core.logic/project` boundaries.

That recovery did not solve the raw list-family search gap. The historically
legacy-passing raw proofs still do not close promptly in greenfield:

- `append([a,b], [c], [a,b,c])`
- `reverse([a,b], [b,a])`

On the ADR-0029 worktree both focused selectors timed out under a 45 second
slice. The result is recorded in
[AAR-0029](../aar/AAR-0029-relational-fuel-purity.md).

The important scope distinction is that the public answer surface has special
materializers for known list-family parity cases. This ADR is not about that
answer overlay. It is about the raw program prover being able to close the
same class of formulas generically: recursive definite programs over free
constructors, where each recursive case is guarded by constructor equalities
and disequalities.

The list program is the current benchmark for that class, but the fix must not
mention `append`, `reverse`, `member`, `cons`, or `null` in production kernel
code.

## Decision

Implement generic, purely relational search-control enhancements in the kernel
for constructor-recursive programs.

The implementation has three retained layers. Each layer is generic and
structural; every optimization fails conservatively back to the existing kernel
rules. A broader agenda-focusing layer was evaluated during implementation and
not retained; the backtrack is recorded in the AAR.

### 1. Focused List-Kernel Measurement

Add a small long-running selector for the raw constructor-recursive proof
class. It should run outside `test-proflog-fast` and include at least:

- `append([a,b], [c], [a,b,c])`;
- `reverse([a,b], [b,a])`;
- one non-list constructor-recursive control case, such as Peano-style
  predecessor or addition, to keep the work generic.

The selector should report wall-clock behavior and proof-shape tags. Its
purpose is to prevent accidental "success" through answer-materialization or
list-specific code.

### 2. Rigid Constructor Disequality Discharge

Add a pure relation for disequalities that are permanently true under the
current branch state.

Current structural saved-disequality maintenance distinguishes "already same"
from "different for now". That is correct for symbolic pairs such as `x != a`,
which may become contradictory after a later equality binds `x`. It is too
weak for constructor clashes such as:

```clojure
cons(a, tail) != null
```

Those pairs are rigidly true in a free-constructor theory and should be
discarded as successful branch progress, not stored as delayed obligations.
The relation must be structural over walked terms:

- distinct constructor heads are rigidly different;
- different constructor arities are rigidly different;
- constructor arguments recurse only when the heads match;
- unresolved proof variables are not rigidly different from constructors,
  because later equality may bind them;
- parameters and distinct noms remain subject to the existing object-language
  discipline.

This should reduce the dead state carried through negated clause alternatives
without changing equality semantics.

### 3. Guarded Procedure-Call Descent

Add generic procedure-call support for guarded alternatives.

Compiled program metadata should preserve the ordinary `:body` and
`:negated-body` fields, but may add a structural view of each relation body as
top-level disjunctive alternatives. This is not indexing by relation-specific
knowledge. It is call-local search control over the already declared
free-constructor program syntax.

For a negative procedure call, the kernel may close by refuting one negated
top-level alternative before falling back to the ordinary negated body. This
lets false base alternatives fail quickly and lets guarded recursive
alternatives descend without changing the logical meaning of procedure calls.

### 4. Evaluated But Not Retained: Relational Agenda Focusing

A global prioritized selector was explored as an implementation tactic. It
remained relational, but fresh-process measurements made the list proofs slower
and exposed poor interaction with nominal single-use universals. The completed
ADR therefore keeps the old fair `selecto` entry point and places the search
control at the procedure-call boundary, where the compiler has exposed a
finite alternative list.

## Constraints

- No production kernel code may special-case list relation or constructor
  names.
- No new executable `core.logic/project` may be added to the ordinary
  kernel-facing path.
- The targeted reverse and partial synthesis regressions from ADR-0027 through
  ADR-0029 must remain green. Broader extended synthesis-mode failures that
  predate this branch remain outside this ADR's exit gate.
- Pelletier profiled-layer interoperation must continue to work.
- Answer-overlay materializers may remain as compatibility surfaces, but they
  must not be the reason the new raw proof regressions pass.

## Test Obligations

- Add a focused failing selector before implementation for:
  - `append([a,b], [c], [a,b,c])`;
  - `reverse([a,b], [b,a])`.
- Add generic unit regressions for rigid constructor disequality discharge:
  - constructor clash is skipped rather than stored;
  - symbolic `x != a` remains delayed;
  - later equality can still make a symbolic saved disequality contradictory.
- Add a non-list constructor-recursive program test so the implementation is
  demonstrably generic.
- Keep these existing regressions green:
  - `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test`
  - `lein test-proflog-fast`

## Exit Criteria

- The focused raw list selector closes `append([a,b], [c], [a,b,c])` and
  `reverse([a,b], [b,a])` inside a documented bounded run.
- The proof path uses the ordinary kernel, not `query-answers` list
  materializers.
- The implementation contains no list-specific production code.
- `rg -n "project" src/proflog/kernel.clj src/proflog/kernel_support.clj src/proflog/subst.clj`
  still reports no executable projection.
- An AAR records which generic mechanism moved the needle: rigid disequality
  discharge, agenda focusing, guarded procedure-call descent, or call-stack
  descent preference.
