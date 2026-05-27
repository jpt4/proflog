# Pelletier Lean Search Policy Comparison

Date: 2026-04-28
Related ADR: [ADR-0025](adr/ADR-0025-pelletier-lean-search-policy.md)

## Scope

ADR-0025 starts from the 13 Pelletier problems left after ADR-0024:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```

The comparison is about execution policy, not formula identity. The new search
policy does not inspect Pelletier problem ids.

## Sources Compared

- Original upstream alphaleanTAP:
  - `https://github.com/namin/leanTAP/blob/master/alphaleantap/alphaleantap.scm`
  - `https://github.com/namin/leanTAP/blob/master/cljtap/src/cljtap/alphaleantap.clj`
  - `https://github.com/namin/leanTAP/blob/master/cljtap/src/cljtap/nnf.clj`
- Local alphaleanTAP-E:
  - `src/cljtap/alphaleantap_e.clj`
- Local legacy EP:
  - `src/cljtap/alphaleantap_ep.clj`
- Greenfield ADR-0024 first-order:
  - `src/proflog/kernel/first_order.clj`

## Execution Differences

| Difference | Upstream alphaleanTAP | Legacy EP | ADR-0024 greenfield | ADR-0025 decision |
|---|---|---|---|---|
| Formula templates | vector templates over tagged data | vector templates plus type dispatch | proper tagged-list templates | adopt vector templates in the lean relation |
| Proof shape | compact unary `lcons` spine | compact unary `lcons` spine | proper unary proof lists | use compact internally, canonicalize host `prove` results |
| Beta siblings | independent | left branch lemmas thread to right | independent | adopt equality-free lemma threading |
| Existentials | upstream NNF Skolemizes | runtime delta parameters | runtime delta parameters | use Skolemization only for complex forward theorem calls |
| `once-forall` | absent; ordinary classical quantifiers | single-use for procedure calls | repeatable in theorem layer | keep repeatable in theorem layer |
| Program semantics | none | procedure calls in the same prover | absent from first-order layer | keep program semantics out of first-order layer |

## Measured Outcome

Fresh REPL probes with a 5s per-problem cap showed this pattern:

| Problem | ADR-0024 greenfield | alphaleanTAP-shaped relation | + beta lemmas | + beta lemmas and Skolemization |
|---:|---|---|---|---|
| 24 | timeout | closes | closes | closes |
| 26 | timeout | timeout | timeout | closes |
| 27 | timeout | closes | closes | closes |
| 28 | timeout | closes | closes | closes |
| 29 | timeout | closes | closes | closes |
| 32 | timeout | closes | timeout | closes |
| 34 | timeout | timeout | closes | closes |
| 37 | timeout | closes | closes | closes |
| 38 | timeout | timeout | closes | closes |
| 43 | timeout | timeout | closes | closes |
| 44 | timeout | closes | closes | closes |
| 45 | timeout | timeout | closes | closes |
| 46 | timeout | timeout | timeout | closes |

The adopted policy is therefore the last column: lean alphaleanTAP-shaped
relational search, beta-sibling lemma threading, and a forward Skolemization
prepass for complex theorem calls.

## Design Notes

The vector templates are not a new AST representation. They are unification
templates in the relation, following the upstream source. Greenfield formulas
remain tagged lists.

Skolemization is intentionally host-side and forward-only. It is used by
`first-order/prove` and therefore by `kernel/prove` for equality-free theorem
calls. Direct relational callers still use `first-order/proveo` and receive
the formula they passed in.

The result is still generic: every adopted improvement is about formula shape
and proof-search state, not Pelletier problem identity.
