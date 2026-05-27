# AAR-0015: Answer Overlay Extraction

- Date: 2026-04-24
- Related ADR: [ADR-0015](../adr/ADR-0015-answer-overlay-extraction.md)
- Outcome: complete

## What Happened

ADR-0015 completed the architectural split that ADR-0014 called for:

- `src/proflog/kernel.clj` is now the ordinary proof-search kernel only,
- `src/proflog/answer_overlay.clj` owns answer-mode execution,
- `src/proflog/answers.clj` routes through the overlay explicitly,
- and `src/proflog/kernel_support.clj` now carries the branch-state mechanics
  shared by both layers.

The branch also added boundary regressions on both sides:

- `test/proflog/answers_test.clj` proves that answer diagnostics route through
  the extracted overlay entry points,
- `test/proflog/query_test.clj` proves that ordinary `query-succeeds` /
  `query-fails` stay on the pure kernel path.

## What Worked

- The repo now has an executable pure-core / overlay boundary instead of an
  inferred one.
- The answer API behavior stayed stable under the extraction: existing answer
  regressions and the full extended suite remained green.
- Pulling shared proof support into `kernel_support.clj` materially improved the
  extraction quality. The overlay is no longer a second drifting copy of the
  proof-core utilities.

## What Did Not Work

- The overlay still contains its own `prove-stateo`, because existential export,
  residual call deferral, and call-depth-controlled recursive answer descent are
  still genuinely answer-specific behaviors.
- ADR-0015 does not by itself make the answer overlay unnecessary. It makes the
  separation testable and the next simplification opportunities more obvious.
- The branch does not solve the harder ADR-0014 family gaps such as `GV` or
  `FD`. It only makes it clearer which future changes belong to the proof core
  and which belong to the answer overlay.
- The heaviest extended namespaces were runtime-heavy during investigation, but
  a final clean rerun of `lein test-proflog-extended` passed before branch
  close-out.

## Follow-Up

- Treat `src/proflog/kernel_support.clj` as proof-core code. Changes there
  affect both the kernel and the answer overlay.
- Future simplification work should target the remaining overlay-only
  mechanisms, not re-litigate the already-shared proof support.
- The next likely architectural questions are:
  - can residual call handling be simplified without moving back into the
    kernel,
  - can answer-mode existential handling be narrowed further,
  - and can difficult legacy families be improved by pure-kernel work now that
    the kernel surface is isolated cleanly.
