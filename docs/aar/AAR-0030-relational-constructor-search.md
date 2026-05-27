# AAR-0030: Relational Constructor Search Control

- Date: 2026-04-29
- Related ADR: [ADR-0030](../adr/ADR-0030-relational-constructor-search.md)
- Outcome: completed

## What Happened

ADR-0030 recovered the raw constructor-recursive proof class that remained
too slow after ADR-0029. The motivating list-family proofs now close through
the ordinary program kernel:

- `append([a,b], [c], [a,b,c])`
- `reverse([a,b], [b,a])`

The implementation stayed generic. No production code names `append`,
`reverse`, `member`, `cons`, or `null`.

## Mechanisms

Three generic changes moved the result.

First, `kernel_support.clj` now distinguishes rigid constructor disequality
from ordinary "different for now" disequality. Constructor head clashes,
arity clashes, and recursively rigid argument clashes are permanently true in
the free-constructor theory and can be discharged by the kernel under a
`neq-rigid` proof tag. Symbolic pairs such as `x != a` still remain delayed.

Second, compiled clauses now retain their top-level alternatives alongside the
ordinary `:body` and `:negated-body`. The program layer exposes those
alternatives relationally through `call-clause-with-alternativeso`.

Third, negative procedure calls can use the alternative view without changing
the logical meaning of calls. A negative call may close by refuting one
negated top-level alternative (`neg-call-alt`).

This is the important call-local search control. For constructor-recursive
programs, a false base alternative fails quickly and the recursive guarded
alternative is tried; at the base frontier, the true base alternative closes
before the recursive alternative can diverge.

## Backtrack

A broader global agenda-focusing selector was evaluated during the branch and
removed before commit. It was relational, but in fresh-process measurements it
made list proofs slower and interacted poorly with nominal single-use
universals. The retained design is narrower: focus at the procedure-call
boundary where the compiler has already exposed a finite, semantically
meaningful alternative list.

## Results

Focused ADR-0030 selector:

- `lein test-proflog-constructor-recursive`
- `Ran 3 tests containing 4 assertions.`
- `0 failures, 0 errors.`

Raw list targets in fresh test processes:

- `append([a,b], [c], [a,b,c])`
  - `timeout 90s lein test :only proflog.list-programs-test/append-two-step-ground-case-succeeds`
  - `0:30.83 real`
- `reverse([a,b], [b,a])`
  - `timeout 120s lein test :only proflog.list-programs-test/reverse-two-element-list-succeeds`
  - `0:39.81 real`

The same focused selector includes a non-list Peano-style recursive `peel/2`
program to guard against list-specific search control.

## Verification

- `lein test proflog.language-test proflog.program-test proflog.kernel-test proflog.constructor-recursive-kernel-test`
  - `Ran 31 tests containing 63 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.subst-test proflog.kernel-test proflog.reverse-program-synthesis-test`
  - `Ran 31 tests containing 53 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test proflog.proof-test`
  - `Ran 12 tests containing 50 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-fast`
  - `Ran 115 tests containing 368 assertions.`
  - `0 failures, 0 errors.`
- `lein test-proflog-constructor-recursive`
  - `Ran 3 tests containing 4 assertions.`
  - `0 failures, 0 errors.`
- `lein test proflog.list-programs-test`
  - `Ran 11 tests containing 19 assertions.`
  - `0 failures, 0 errors.`
- `rg -n "project" src/proflog/kernel.clj src/proflog/kernel_support.clj src/proflog/subst.clj`
  - only prose references remain; there is no executable `project` in the
    ordinary kernel-facing files.

Extended-suite note: `proflog.recursive-synthesis-test`,
`proflog.nim-synthesis-test`, and `proflog.synthesis-modes-test` still fail
on this branch, but the same failures reproduce at the clean ADR-0030 branch
point before this implementation. They remain outside this ADR's exit gate.

Post-completion matrix: a raw-kernel append/reverse matrix was added after the
initial AAR to characterize longer, nested, reverse, and partial modes. It
confirms that ADR-0030's improvement is concentrated in two-step ground
constructor-recursive closure; longer ground cases and raw answer-mode
synthesis still need a follow-up design. See
[List Kernel Test Matrix](../log/2026-04-29-list-kernel-test-matrix.md).

Substantive reassessment: ADR-0030 remains technically closed, but the raw
matrix shows that its result is too narrow to count as family-level completion.
The spirit of the objective is therefore carried forward into
[ADR-0031](../adr/ADR-0031-list-family-kernel-generalization.md).

## Follow-Up

The raw target proofs are now bounded and practical, but still slower than the
legacy reference timings recorded in AAR-0017. Further improvement should focus
on reducing per-step core.logic overhead in guarded alternatives or making the
answer overlay share the same call-local alternative discipline.
