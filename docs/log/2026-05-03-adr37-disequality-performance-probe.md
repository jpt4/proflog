# ADR-37 Disequality Performance Probe

Worker G added a measurement-only harness for the first core.logic performance
triage target: disequality maintenance in `!=c`.

## Scope

The probe is intentionally narrow. It uses process-local `with-redefs` counters
around core.logic Vars and does not alter core.logic, Proflog proof search, or
the ADR-37 constraint overlay.

Files:

- `src/proflog/core_logic_disequality_probe.clj`
- `test/proflog/core_logic_disequality_probe_test.clj`

The harness measures whole-case wall time and per-Var call counts for:

- `!=`, `!=c`, `disunify`, `recover-vars`, and `recover-vars-from-term`;
- `walk*`, reification, `force-ans`;
- `unify`, `ext`, and occurs check;
- constraint scheduler functions; and
- `predc`, `fixc`, and `treec` for the ADR-37 overlay constraint path.

## Probe Cases

The runnable cases are:

- `:core-open-residual` - one open core.logic disequality residual.
- `:core-open-chain-small` - eight delayed disequality residuals over nested
  depth-four terms.
- `:core-open-chain-medium` - thirty-two delayed residuals over nested
  depth-five terms.
- `:core-violated-after-delay` - one delayed disequality falsified by a later
  binding.
- `:absento-open-tail` - ADR-37 `absento` over an open tail, exercising
  the deep `fixc`-based absence path plus disequality.
- `:proflog-saved-disequality-close` - a Proflog saved `neq` closes after a
  later equality.
- `:proflog-open-disequality` - a Proflog same-head symbolic `neq` remains
  open.

Run them with:

```bash
lein run -m proflog.core-logic-disequality-probe
lein run -m proflog.core-logic-disequality-probe core-open-chain-medium
```

## Current Finding

The likely bottleneck shape remains the one identified in the core.logic source
TODO: every runnable `!=c` step walks both sides with `walk*`, compares the
walked values, and then checks unifiability. The new harness makes that pressure
visible by case instead of relying only on broad ADR-32 list-family counters.

The measured split matters:

- Core.logic residual disequality cases do exercise `!=c`.
- The Proflog equality/disequality proof cases measured here are dominated by
  ordinary unification and do not materialize `!=c` residual maintenance.

That means a core.logic `!=c` patch is not yet justified for current Proflog
proof search. It may still be justified for ADR-37 overlay constraints or future
more-relational Proflog state, but only after a concrete Proflog case shows the
same residual `!=c` pressure as the synthetic core.logic cases.

## Recorded Run

Focused probe tests:

```bash
timeout -k 10s 180s lein test proflog.core-logic-disequality-probe-test
```

Result:

```text
Ran 5 tests containing 21 assertions.
0 failures, 0 errors.
```

Probe command:

```bash
/usr/bin/time -p timeout -k 10s 180s lein run -m proflog.core-logic-disequality-probe \
  core-open-residual \
  core-open-chain-medium \
  core-violated-after-delay \
  absento-open-tail \
  proflog-saved-disequality-close \
  proflog-open-disequality
```

Summary:

| case | elapsed ms | result | notable counts |
| --- | ---: | --- | --- |
| `:core-open-residual` | 55.551 | one residual answer | `!=c` 1, `walk*` 6, reify calls 8 |
| `:core-open-chain-medium` | 88.769 | one residual answer with 32 constraints | `!=c` 32, `run-constraint` 32, `disunify` 544, `recover-vars` 128, `recover-vars-from-term` 256, `walk*` 130, reify calls 161 |
| `:core-violated-after-delay` | 13.647 | zero answers | `!=c` 1, `run-constraint` 1, `walk*` 2 |
| `:absento-open-tail` | 18.530 | one delayed `fixc` answer | historical direct-`treec` overlay: `treec` 1, `!=` 2, `disunify` 4, `!=c` 0 |
| `:proflog-saved-disequality-close` | 4043.773 | one closed proof | unification category 9139 calls / 97.9%, `!=` 127, `!=c` 0, `walk*` 12 |
| `:proflog-open-disequality` | 483.511 | zero closed proofs | unification category 3636 calls / 99.1%, `!=` 26, `!=c` 0, `walk*` 0 |

Process wall time:

```text
real 37.71
user 34.13
sys 2.16
```

Focused Proflog equality/substitution guardrail:

```bash
/usr/bin/time -p timeout -k 10s 180s lein test proflog.equality-test proflog.subst-test
```

Result:

```text
Ran 19 tests containing 40 assertions.
0 failures, 0 errors.
real 40.78
```

I also tried the broader guardrail:

```bash
/usr/bin/time -p timeout -k 10s 240s lein test \
  proflog.core-logic-disequality-probe-test \
  proflog.existential-disequality-test \
  proflog.equality-test \
  proflog.subst-test
```

That run failed only in `proflog.existential-disequality-test`, where both 1000
ms `query-status` assertions returned `:unresolved` instead of `:succeeds`.
Rerunning that namespace alone under the same concurrent worker load produced
the same timeout-sensitive failure. This worker did not edit production code or
that test namespace, so the failure is recorded as ambient branch/load evidence
rather than a disequality probe regression.

## Patch Recommendation

Do not patch the core.logic engine yet.

The best next candidate is not a global semantic change; it is a benchmarked
prototype around `!=c`'s runnable-step maintenance. Two plausible micro-targets
need isolation:

- avoid repeated prefix variable recovery when the prefix has not changed; and
- try a conservative rigid-root entailment fast path before full deep `walk*`.

Either candidate must preserve observable residual disequality reification and
must beat this harness plus Proflog equality/disequality tests before it is
worth proposing as a core.logic overlay patch.
