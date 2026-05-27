# AAR-0008: Greenfield Test Gap Closure

- Date: 2026-04-25
- Related ADR: [ADR-0008](../adr/ADR-0008-test-gap-closure.md)
- Outcome: complete

## What Happened

ADR-0008 did not try to close every legacy-relative gap in one branch. It did
the more important thing first:

- created a maintained checklist in `docs/TEST_GAP_CLOSURE_CHECKLIST.md`,
- recorded reverse program synthesis as a feasibility question instead of an
  assumed capability,
- and forced the repo to classify important greenfield gaps as:
  - covered,
  - deferred,
  - or not yet feasible.

That framing proved correct. Much of the later work in ADR-0009 through
ADR-0015 is easier to understand because ADR-0008 turned "missing tests" into
named tracks with explicit scope.

## What Worked

- The checklist became a useful planning and review artifact. It still records
  both incorporated coverage and open items by semantic area.
- The branch made a narrower reverse-program-synthesis claim explicit instead of
  overstating full surface-program synthesis. That narrower boundary is now
  documented in `test/proflog/reverse_program_synthesis_test.clj` and in the
  checklist itself.
- `GV` and `FD` were not silently dropped. They were explicitly preserved as
  future experiments, which later enabled ADR-0014.

## What Did Not Work

- ADR-0008 did not itself close the whole checklist. That was never realistic.
  Some items stayed open and were intentionally pushed into later family- or
  answer-specific ADRs.
- The checklist still contains unchecked items. That is acceptable as long as
  those items remain explicit and are not mistaken for completed closure.

## Follow-Up

- ADR-0009 consumed ADR-0008's checklist framing and turned it into parity
  tracking plus worked examples.
- Future updates should continue to treat the checklist as a living record,
  not as a frozen historical artifact.
- If additional gap families are promoted later, record them in the checklist
  first before claiming closure work has begun.
