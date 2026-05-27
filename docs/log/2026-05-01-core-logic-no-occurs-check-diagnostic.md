# 2026-05-01 Core.logic No Occurs-Check Diagnostic

## Context

The ADR-0032 count probe showed heavy `occurs-check` and `walk*` call volume on
the carried `reverse-input-flat` row. To separate runtime cost from answer
shape, this diagnostic temporarily changed the vendored source overlay's
`run` and `run*` macros to use `:occurs-check false`.

This is not a production candidate. Disabling occurs check globally is unsound
for a generic logic engine unless a narrower proof obligation establishes that
the path cannot construct cyclic terms.

## Runtime Verification

The temporary overlay marker was:

```text
core.logic marker: vendor/core.logic-1.1.1/src no-occurs-check-diagnostic
```

The source change was reverted after collecting the measurements.

## Carried Row Results

Diagnostic no-occurs-check overlay:

| Row | Target Found | Closed Count | Raw Count | Elapsed |
| --- | --- | ---: | ---: | ---: |
| `reverse-input-flat` | false | 0 | 4 | 16477 ms |
| `reverse-output-nested-longer` | false | 0 | 4 | 4809 ms |
| `reverse-partial-output-tail` | false | 0 | 4 | 8525 ms |

Commands:

```text
lein with-profile +core-logic-source-overlay probe-core-logic-host
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-input-flat
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-output-nested-longer
timeout 180s lein with-profile +core-logic-source-overlay probe-proflog-list-kernel-matrix reverse-partial-output-tail
```

## Decision

Do not retain any no-occurs-check production path.

The diagnostic confirms that occurs-check contributes real runtime cost, but it
does not change the carried answer shape and does not close any target. ADR-32's
remaining blocker is therefore not just occurs-check overhead. Further work
should focus on why the raw answer frontier exports before the intended reverse
bindings are reached, or on a sound, local occurs-check reduction only after a
specific cyclic-term impossibility argument exists.

