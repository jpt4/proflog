# List-Kernel Matrix Long-Timeout Sweep

Date: 2026-05-03
Branch: `adr-0035-track-d-visited-continuation`

## Purpose

After ADR-0035 Track D, the promoted list-kernel matrix rows were green, but
`append-inverse-flat-longer` remained intentionally outside the default gate
because it is expensive. This sweep records whether every
`proflog.list-kernel-matrix-probe` catalog row eventually finds its target under
a longer per-case wrapper.

Each row was run as an isolated process:

```text
timeout -k 10s 900s lein probe-proflog-list-kernel-matrix <case-id>
```

The elapsed values below are the probe-reported `:elapsed-ms` values from inside
the Clojure process. The shell wall time also includes Lein startup.

## Result

Every catalog row returned `:target-found? true`.

| Case | Target result | Raw / proof count | Elapsed |
|---|---:|---:|---:|
| `append-forward-flat-2` | true | proof `1` | `1.838032 ms` |
| `append-forward-flat-3` | true | proof `1` | `2.458504 ms` |
| `append-forward-nested-2` | true | proof `1` | `2.606685 ms` |
| `append-forward-nested-3` | true | proof `1` | `2.709179 ms` |
| `append-output-flat` | `1 / 1` | raw `4` | `8554.98851 ms` |
| `append-output-nested` | `1 / 1` | raw `4` | `9501.999252 ms` |
| `append-suffix-flat` | `1 / 1` | raw `4` | `12208.797763 ms` |
| `append-prefix-flat` | `1 / 1` | raw `4` | `19010.470319 ms` |
| `append-suffix-nested` | `1 / 1` | raw `4` | `6492.120805 ms` |
| `append-inverse-flat` | `4 / 4` | raw `8` | `10108.126827 ms` |
| `append-inverse-flat-longer` | `5 / 5` | raw `32` | `509517.493191 ms` |
| `append-inverse-nested` | `3 / 3` | raw `8` | `13283.4934 ms` |
| `reverse-forward-flat-2` | true | proof `1` | `1.513665 ms` |
| `reverse-forward-flat-3` | true | proof `1` | `2.030605 ms` |
| `reverse-forward-nested-2` | true | proof `1` | `1.521756 ms` |
| `reverse-forward-nested-3` | true | proof `1` | `3.434683 ms` |
| `reverse-output-flat` | `1 / 1` | raw `4` | `16678.970067 ms` |
| `reverse-input-flat` | `1 / 1` | raw `4` | `70285.584748 ms` |
| `reverse-input-flat-longer` | `1 / 1` | raw `4` | `29548.093268 ms` |
| `reverse-output-nested` | `1 / 1` | raw `4` | `17447.036775 ms` |
| `reverse-output-nested-longer` | `1 / 1` | raw `4` | `22456.271974 ms` |
| `reverse-output-deep-nested-longer` | `1 / 1` | raw `4` | `21896.698837 ms` |
| `reverse-partial-output-tail` | `1 / 1` | raw `4` | `36453.91349 ms` |
| `reverse-partial-output-longer-tail` | `1 / 1` | raw `4` | `27811.89822 ms` |

## Interpretation

The full catalog is semantically reachable through the ordinary probe path after
ADR-0035. The heavy outlier is still `append-inverse-flat-longer`, which took
about `509.5 s` and consumed the configured `raw-limit 32` to find all five
split points. It should remain outside the default regression gate unless the
project explicitly accepts an eight-to-nine minute row.

The default promoted matrix remains the practical gate. The long-timeout sweep is
evidence of eventual closure, not evidence that every row is CI-suitable.
