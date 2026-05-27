# AAR-0016: Fair Agenda And Micro Fuel

- Date: 2026-04-26
- Related ADR: [ADR-0016](../adr/ADR-0016-fair-agenda-and-micro-fuel.md)
- Outcome: complete

## What Happened

ADR-0016 recovered and completed the interrupted fair-scheduling work:

- `proflog.kernel` now routes branch work through an explicit agenda relation,
- `proflog.answer-overlay` uses the same fair agenda discipline,
- `proflog.kernel-support/selecto` provides pure relational agenda selection,
- answer export canonicalization and ranking were adjusted for the less
  left-biased raw proof stream,
- and bounded proof search now charges micro-fuel for non-closing branch
  progress instead of only charging quantifier and procedure-call expansion.

The branch also reconstructed the missing chained ADRs:

- ADR-0016 for fair scheduling and micro-fuel,
- ADR-0017 for the expected tabling / canonical-state follow-up.

## What Worked

- The fair agenda made proof-stream ordering less dependent on the syntactic
  left side of an `and` chain.
- The answer layer now merges the additional alpha-equivalent symbolic
  frontiers surfaced by fairer scheduling.
- Micro-fuel now blocks non-immediate structural branch progress at `fuel 0`.
  The kernel regression
  `bounded-fuel-charges-structural-branch-progress` captures that contract.
- The kernel remains readable: tableaus are still implemented in
  `proflog.kernel`, while the scheduler primitive is a small relation in
  `proflog.kernel-support`.

## What Did Not Work

- Fair scheduling surfaces additional duplicate proof families. The answer
  layer handles the public API impact, but the duplicate-work cost remains.
  That is the concrete motivation for ADR-0017.
- A full `lein test-proflog-extended` run was attempted during recovery but
  overran in `proflog.list-programs-test`, which is already a historically slow
  namespace. Targeted list and answer regressions were run instead.
- ADR-0016 does not solve the remaining hard-family `GV` / `FD` gaps. The
  recovered ADR-0014 overlay remains a separate non-default path.

## Verification

- `lein test-proflog-fast`
- `lein test proflog.answers-test`
- `lein test proflog.legacy-hard-families-test`
- `lein test :only proflog.kernel-test/bounded-fuel-charges-structural-branch-progress`
- `lein test :only proflog.list-programs-test/reverse-empty-list-succeeds`
- `lein test :only proflog.list-programs-test/append-base-case-succeeds-and-exports-an-open-result`
- `lein test :only proflog.list-programs-test/reverse-singleton-list-succeeds`
- `lein test :only cljtap.alphaleantap-ep-test/test-O05-proof-has-para-close`
- `lein test :only cljtap.alphaleantap-ep-test/test-R04-para-free-close-proof-step`

## Follow-Up

- Proceed to ADR-0017 before trying to tune fair scheduling with ad hoc
  left-bias or committed choice.
- Keep tabling and canonical state reuse outside `proflog.kernel` as far as
  feasible.
- Re-run the full extended suite with an explicit outer timeout before merging
  ADR-0016, or split the slow list namespace further if it remains too costly
  for routine branch close-out.
