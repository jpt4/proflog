# AAR-0044: Turing Completeness Demonstration

- Date: 2026-05-07
- Related ADR: [ADR-0044](../adr/ADR-0044-turing-completeness-demonstration.md)
- Status: completed
- Branch: `adr-0044-turing-completeness`

## Result

ADR-0044 added `proflog.turing-completeness`, a reusable two-counter Minsky
machine interpreter written through the ADR-0010 frontend. The namespace does
not add a host-side machine evaluator. Clojure helpers construct
object-language terms for tests and examples, while transition and reachability
semantics are ordinary compiled Proflog clauses.

The implementation demonstrates the standard expressive-power argument:
two-counter machines are Turing-complete, a finite instruction table can be
encoded as Proflog facts, and the generic `run/2` relation is the reflexive
transitive closure of `step/2`. Therefore Proflog can represent computations of
a known minimal Turing-complete model.

## Verification

The opt-in suite is `lein test-proflog-turing-completeness`. It is intentionally
not part of the default fast or extended gates because the proof searches are
slow enough that future users should choose when to run them.

Promoted checks cover:

- forward kernel proofs for increment, zero-branch, and decrement-branch
  transition cases;
- bounded recursive execution for a second instruction table, proving the
  interpreter is not hard-coded to the transfer example;
- frontend answer evaluation that exports the final halting configuration for a
  three-step transfer run;
- frontend partial synthesis over an instruction-table relation;
- a source audit that the TC namespace does not call query or answer evaluators
  and does not define a host `step`, `run`, or `halts-in` function.

Runtime details are recorded in
[TEST_RUNTIME_BASELINE](../TEST_RUNTIME_BASELINE.md).

Final focused result:

```text
lein test-proflog-turing-completeness
Ran 6 tests containing 13 assertions.
0 failures, 0 errors.
elapsed_seconds 68.64
```

Standard gate result:

```text
lein test-proflog-fast
Ran 128 tests containing 414 assertions.
0 failures, 0 errors.
elapsed_seconds 85.62

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed_seconds 227.20
```

Follow-up diagnostic probes on 2026-05-07 clarified the runtime boundary:

- `recursive-transfer-3-steps` eventually succeeded in `783.72 s`;
- `open-predecessor-step` eventually returned four answer records in `645.66 s`;
- `direct-ground-three-step-trace` produced no proof before controlled stop at
  about thirty minutes;
- `recursive-transfer-5-steps` timed out after a `1800 s` wrapper.

The full record is in
[2026-05-07 ADR-0044 long Turing probes](../log/2026-05-07-adr44-long-turing-probes.md).

The follow-up commit gate passed:

```text
lein test-proflog-turing-completeness
Ran 6 tests containing 13 assertions.
0 failures, 0 errors.
elapsed_seconds 68.64

lein test-proflog-fast
Ran 128 tests containing 414 assertions.
0 failures, 0 errors.
elapsed_seconds 60.67

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed_seconds 184.10
```

## Shortcomings

The demonstration is definitive about representability, not about efficient
reachability. Follow-up probes show that some originally timed-out cases do
eventually return valid results, but only after ten-plus minute searches.
Deeper recursive traces and some direct proof formulations remain impractical.
The passing suite therefore uses individual `step/2` proofs, one bounded
recursive incrementer run, an explicit three-step transfer trace for answer
export, and instruction-relation partial synthesis.

This is the correct operational reading: Proflog can encode a Turing-complete
machine model in its kernel-level source language, but arbitrary machine
reachability remains undecidable and current bounded proof search can be
expensive even for small recursive traces.
