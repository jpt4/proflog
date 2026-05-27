# Sean fork AS SJAS → Proflog correlation

This worked example documents how Autarkic Systems Sean fork SJAS artifacts
(archived on branch `archive/sean-fork-full` of `jpt4/as`) map onto this
repository's Willard SJAS implementation.

## Encoding difference

The fork used a Python **tagged-prefix natural number** codebook (`language/formal_codebook.json`).
Proflog uses **base-64 byte codes** (`willard_sjas_code.clj`). Obligations align;
encoded integers are not portable byte-for-byte.

## Obligation mapping

| Sean fork ID / artifact | Proflog surface |
|-------------------------|-----------------|
| `AS-CONSISTENCY-LEVEL-1-TARGET` | `:willard-sjas-level1`, SelfCons1 |
| `AS-FIXED-POINT-SELFCONS1-DIAGONAL-SEED` | `subst-code/2` on selfcons skeleton |
| `AS-SUBSTITUTION-REPRESENTABILITY-DIAGONAL-SEED-WITNESS` | `sjas/subst-code` tests |
| `AS-SUBSTITUTION-GRAPH-DELTA0-TARGET` | `delta-star-0-code`, structural `subst-code` |
| `tableau-proof/3`, `subst-prf/4` | kernel profiles, `willard_sjas_test.clj` |
| Naive quotation fixed-point (fork obstruction) | **Not used** — ADR-0065+ diagonal route |

## Tests

`test/proflog/as_sean_fork_correlation_test.clj` re-expresses the fork's
*semantic* checkpoints as Proflog queries over Level-1 systems.

Run:

```text
lein test :as-sean-fork-correlation
```
