# ADR-0022: Pelletier Problem Replication

- Status: completed
- Date: 2026-04-27
- Branch: `adr-0022-pelletier-problems`
- AAR: [AAR-0022](../aar/AAR-0022-pelletier-problems.md)
- Depends On: [ADR-0021](ADR-0021-gamma-search-regression-repair.md)

## Context

Nada Amin's `namin/leanTAP` repository contains a Clojure `cljtap`
translation of `alphaleanTAP` and a `test-pelletier-problems` suite that
drives Pelletier Problems 1 through 46 through the theorem prover. The same
repository also carries the original Scheme `alphaleantap/test.scm` source.

This repository currently has only a small legacy-derived Pelletier slice in
`test/cljtap/alphaleantap_ep_test.clj`:

- Pelletier 1,
- Pelletier 2,
- Pelletier 18.

Those tests exercise the legacy `cljtap` namespace, not the greenfield
`proflog` kernel. The greenfield prover needs an explicit first-order theorem
benchmark suite independent of Proflog procedure-call examples, list-family
answer overlays, and hard-family special cases.

The replication should defend the kernel boundary. Pelletier tests are pure
theorem-proving benchmarks with no Proflog program clauses. They should enter
through the ordinary greenfield kernel/query surfaces and should not be solved
by a theorem-specific overlay.

## Decision

- Add a greenfield Pelletier test namespace:
  `test/proflog/pelletier_test.clj`.
- Treat `namin/leanTAP` as the upstream source of record for the target
  formulas:
  - `alphaleantap/test.scm`,
  - `cljtap/test/cljtap/test/alphaleantap.clj`.
- Port the upstream formulas into greenfield AST terms, preserving problem
  numbers and upstream grouping.
- Start with theoremhood tests, not proof-shape tests. A test passes when the
  greenfield prover finds a closed tableau for the NNF branch formula derived
  from the theorem through a small helper whose meaning is documented in the
  namespace.
- Keep the entire Pelletier suite separate from `test-proflog-fast` until the
  runtime profile is measured. Add a dedicated alias only after the initial
  port makes clear whether the suite is fast, extended, or partly exploratory.
- Record every upstream problem in a local catalog even if some are initially
  skipped, too slow, or not yet expressible. Missing entries should be visible
  as intentional gaps, not silently absent tests.

## Consequences

- This creates a broad pure-theorem benchmark for the greenfield kernel.
- It will expose whether prior gamma, delta, beta, scheduling, and fuel work is
  enough for classic first-order theorem-proving problems outside the Proflog
  program-call examples.
- Some problems may be expensive enough to require staged selectors or an
  extended alias rather than fast-suite inclusion.
- If a problem needs host-side materialization, family-specific recognition, or
  an overlay to pass, that is a kernel capability gap and should be recorded as
  such rather than hidden behind a helper.
- The implementation may need ergonomic formula builders for implication,
  equivalence, quantifier blocks, and NNF conversion, but those helpers must
  compile down to the existing `proflog.ast` forms instead of introducing a new
  semantic language.

## Test Obligations

- Add a local source catalog for Pelletier Problems 1 through 46 with stable
  problem ids.
- Add passing greenfield tests for the already mirrored legacy slice:
  - Pelletier 1,
  - Pelletier 2,
  - Pelletier 18.
- Add the first broader tranche from upstream:
  - propositional Problems 1 through 17,
  - first-order Problems 18 through 20.
- Add selectors or metadata that make it possible to run:
  - the prompt tranche,
  - the whole currently passing suite,
  - and any explicitly exploratory / long-running tranche separately.
- Add a short worked example or AAR table mapping upstream syntax to greenfield
  AST syntax for at least one propositional and one quantified problem.

## Exit Criteria

- `test/proflog/pelletier_test.clj` exists and is green for the committed
  Pelletier tranche.
- Every upstream Pelletier problem 1 through 46 has a local status:
  `ported-passing`, `ported-too-slow`, `not-yet-ported`, or
  `requires-kernel-work`.
- The branch records timing data for the passing tranche and states whether any
  part belongs in `test-proflog-fast`, `test-proflog-extended`, or a new
  dedicated alias.
- No Pelletier test is satisfied by adding a theorem-specific overlay or by
  weakening the pure kernel boundary.
- The ADR is updated to `completed`, an AAR is written, and the ADR/AAR indexes
  are current before merge to `greenfield`.

## Implementation Result

- Added `test/proflog/pelletier_test.clj`.
- Added dedicated aliases:
  - `lein test-proflog-pelletier-prompt`
  - `lein test-proflog-pelletier`
  - `lein test-proflog-pelletier-exploratory`
- Current catalog statuses:
  - `ported-passing`: Problems 1-11, 13-23, 33, 35, 39, 40, and 42.
  - `ported-too-slow`: Problems 24-32, 34, 36-38, 41, and 43-46.
  - `requires-kernel-work`: Problem 12.
- Problem 12 is propositional and was not promoted into the passing tranche
  after a fresh-process `120s` probe produced no proof. This is classified as
  kernel/search work rather than ordinary first-order slowness.
- The full `proflog.pelletier-test` namespace passed with 5 tests and 70
  assertions.
