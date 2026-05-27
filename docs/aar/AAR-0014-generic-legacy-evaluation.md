# AAR-0014: Generic Legacy Unsatisfied Family Evaluation

- Date: 2026-04-25
- Related ADR: [ADR-0014](../adr/ADR-0014-generic-legacy-evaluation.md)
- Outcome: draft / ongoing

## What Happened

ADR-0014 changed the methodology for the remaining hard legacy families.

Instead of jumping directly to "optimize it" or "add a handler," the branch
made the repo classify unsatisfied queries by layer:

- raw pure-kernel stream,
- generic stream processing above that stream,
- specialty handling only if the first two layers were insufficient.

The branch produced concrete probe infrastructure and concrete results:

- `src/proflog/legacy_stream_probe.clj`
- `src/proflog/gv_probe.clj`
- `src/proflog/equality_fast_path.clj`
- `src/proflog/hard_family_overlay.clj`
- `test/proflog/legacy_hard_families_test.clj`
- `worked-examples/gv-probes.md`
- additions to `worked-examples/answers-api.md`
- new lessons about raw-stream presence versus extra-kernel recovery

It also produced a strong architectural result: the answer-mode flow was
important enough as a structural variable that ADR-0015 was opened to extract
it from the kernel.

## What Worked

- The repo now has a disciplined way to talk about hard legacy queries. This is
  a real improvement over undirected "performance tuning."
- The pure-core path is now explicitly treated as something that should be
  directly callable and separately measurable.
- The raw-kernel list probes made the current boundary concrete:
  the pure core is accessible, but it still does not generically surface full
  reverse/append synthesis parity within long measured slices.
- The initial `GV` probes were useful even though they were unfavorable. They
  showed that greenfield currently appears weaker than legacy on the first
  group-verifier slice, aside from the simple identity case.
- The branch now has one generic non-default overlay improvement, not a
  family-specific handler: `hard_family_overlay` uses the separate
  `equality_fast_path` namespace on the restricted shape
  "nested existentials over an equality / disequality conjunction" quickly
  enough that the named overlay resolves:
  - `Z₁` full associativity (`gv_assoc`) as `:succeeds`,
  - and one promoted representative `FD` query, `warm-unique`, also as
    `:succeeds`.
- The branch recorded a meaningful legacy/greenfield comparison result:
  legacy’s current list behavior depends materially on its projected
  `L-ground` guard and host-variable representation, which strengthens the case
  for keeping the greenfield proof core as structurally relational as possible.
- The branch also clarified a potential source of confusion:
  legacy paramodulation itself is mostly encoded relationally, but the legacy
  prover around it still uses `project` for substitution, L-groundness,
  parameter propagation, and budget control. So "legacy uses paramodulation"
  does not imply "legacy is fully pure end to end."

## What Did Not Work

- ADR-0014 has not yet closed the hard unsatisfied families.
- The branch still does not have a broad `FD` classification story; it has one
  promoted representative query, not a family closure.
- Historical note: at ADR-0014 closeout, the broader `GV` slice remained open.
  `Z₂` precomputed associativity, `Z₂` full associativity, and the non-group
  associativity probes were still the meaningful hard cases. ADR-39 later
  promoted these finite equality-fragment rows through the profiled kernel
  component.
- The raw pure-core list probes did not recover generic reverse/append parity
  within the measured long slices.

## Follow-Up

- ADR-0015 was the immediate structural follow-on: it extracted the separate
  answer-mode flow into an overlay so the pure core could be investigated more
  cleanly.
- ADR-0014 should continue from that cleaner boundary, starting with:
  - the remaining `GV` hard cases on the isolated pure kernel,
  - broader `FD` classification beyond the first representative query,
  - and a direct re-measurement of whether a future relational equality /
    paramodulation extension materially changes `Z₂` precomputed
    associativity or only the smaller equality-dominated cases.
- Keep this AAR as an interim report until the branch can classify at least one
  hard query end to end across:
  - raw stream,
  - generic recovery,
  - and specialty handling.
