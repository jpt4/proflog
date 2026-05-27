# AAR-0038: Fitting Program Kernel Evaluation

- Date: 2026-05-05
- Related ADR: [ADR-0038](../adr/ADR-0038-fitting-program-kernel-evaluation.md)
- Outcome: completed with a kernel-backed evaluation catalog and explicit GV frontiers

## What Happened

ADR-0038 added `proflog.fitting-programs`, a source-level catalog for
paper-facing Proflog examples. The catalog builds programs with the public AST
and language compiler, then evaluates them through proof-kernel-backed query
surfaces or the raw list-kernel matrix.

The promoted catalog covers:

- Fitting P1 even/odd;
- Fitting P2 Nim, including `win(4)`;
- Fitting's move-warning auxiliary-relation factoring;
- finite-domain true, false, and unresolved examples;
- append/reverse list rows from the raw list-kernel matrix; and
- group-verifier associativity frontiers.

## What Worked

The focused ADR-38 tests disable `proflog.hard-family-overlay/query-status` and
`constructor-recursive/settle-record` while evaluating the catalog. That proves
the promoted outcomes do not depend on the named hard-family overlay or the old
post-export constructor-recursive sidecar.

True and false outcomes carry ordinary proof evidence from the kernel or
answer-overlay path. List rows use `proflog.list-kernel-matrix-probe/run-case`,
which bypasses public list materializers and checks raw proof/answer results
against target bindings.

## What Remains

Historical note: at ADR-38 closeout, full group-verifier associativity was
still a proof-search frontier. ADR-38 did not hide that behind the hard-family
overlay. The catalog recorded both precomputed Z2 associativity and full Z1
associativity as bounded unresolved kernel frontiers with explicit
classifications.

ADR-39 later promotes those GV rows, plus Z2 full associativity and non-group
associativity refutations, through a profiled finite equality-fragment kernel
component. See [AAR-0039](AAR-0039-kernel-level-group-verification.md).

Deeper P1/P2 rows such as P1 `odd(3)` and larger Nim positions are also too
expensive for the default ADR-38 test gate. They should be promoted only after a
new search-control improvement makes them practical through the same kernel
surface.

## Verification

Focused verification:

```text
timeout -k 5s 480s lein test proflog.fitting-programs-test
  Ran 5 tests containing 59 assertions.
  0 failures, 0 errors.
```

`proflog.fitting-programs-test` remains a dedicated ADR gate rather than part of
`test-proflog-extended`, because its raw list-kernel proof-search slice is
intentionally more expensive than routine answer/API regression tests.

The final branch verification also ran:

```text
lein test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.

lein test-proflog-extended
  Ran 68 tests containing 203 assertions.
  0 failures, 0 errors.

lein test-proflog-hard-families
  Ran 3 tests containing 3 assertions.
  0 failures, 0 errors.
```

## Follow-Up

After ADR-39, the highest-value remaining frontiers are:

- promote deeper P1/P2 rows once their proof paths are bounded enough for a
  test gate; and
- keep finite-domain and list-family examples in the ADR-38 catalog as
  regression anchors whenever proof-search control changes.
