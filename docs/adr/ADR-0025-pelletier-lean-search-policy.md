# ADR-0025: Pelletier Lean Search Policy

- Status: completed
- Date: 2026-04-28
- Branch: `adr-0025-pelletier-lean-search-policy`
- AAR: [AAR-0025](../aar/AAR-0025-pelletier-lean-search-policy.md)
- Depends On: [ADR-0024](ADR-0024-pelletier-first-order-performance.md)

## Context

ADR-0024 added the first equality-free first-order layer and promoted
Pelletier Problems `[25 30 31 36 41]`. The remaining greenfield non-passers
are:

```clojure
[24 26 27 28 29 32 34 37 38 43 44 45 46]
```

The comparison uncovered three distinct execution facts.

First, the original upstream alphaleanTAP shape is not just "the same rules in
a smaller file." It uses:

- vector unification templates against tagged formulas,
- compact unary proof spines using `lcons`,
- gamma re-enqueue at the end of the current pending stack,
- exact complementary literal closure against the saved branch literals,
- a Skolemizing NNF pass in the original upstream Clojure/Scheme source.

Second, legacy EP contains useful search machinery but the wrong theorem-only
quantifier policy by default. Its beta-sibling lemma threading is useful, but
its single-use `once-forall` interpretation is for negated procedure-call
bodies, not for closed theorem proving.

Third, the ADR-0024 greenfield first-order component is semantically narrow
enough, but operationally still not lean enough. The proper-list proof tree and
fuel bookkeeping are visible costs, and some formulas need beta-sibling lemma
reuse or Skolemization to close promptly.

## Decision

ADR-0025 will keep the ADR-0024 first-order profile boundary but replace the
unbounded forward theorem path with a leaner equality-free first-order search
policy.

The new policy has three parts:

1. Add an alphaleanTAP-shaped relational first-order relation.
   - Keep the state narrow: formula, pending branch work, saved literals,
     lexical environment, lemma input/output, and proof.
   - Use vector unification templates in rule heads, matching upstream
     alphaleanTAP's operational shape while still accepting greenfield tagged
     list formulas.
   - Use compact unary proof spines internally, then canonicalize host-facing
     `prove` results back to ordinary tagged proof lists.
2. Add beta-sibling lemma threading for equality-free literals.
   - A closed branch contributes its closing literal to the lemma output.
   - The left beta branch's lemma output becomes the right beta branch's lemma
     input.
   - Lemma closure is generic and formula-shaped, not Pelletier-id shaped.
3. Add a forward-only Skolemization prepass for complex equality-free theorem
   calls.
   - This applies only to host-side `first-order/prove` / `kernel/prove`
     convenience calls.
   - Direct `first-order/proveo` remains the relational tableau over the
     formula it is given.
   - Bounded fuel calls keep the existing direct bounded relation so open
     branches still return under a fuel slice.

The Skolemization prepass is deliberately not part of the semantic kernel. It
is a theorem-proving search policy for already-normalized, equality-free,
call-free formulas. Reverse and partial use remains available through direct
relations.

## Consequences

The positive outcome should be complete Pelletier catalog closure without
problem-id dispatch, theorem-specific overlays, or compiled proof plans.

The main risk is proof-term shape drift. Internally compact proof spines are
worth using because they match the primary source and materially affect search
performance, but public `prove` results should remain canonical enough for
existing proof inspection helpers.

The second risk is using host-side Skolemization too broadly. To contain that
risk, explicit once-only and witness proof steps still appear when direct
relations or simple formulas use the direct tableau. Skolemized proof results
must be marked with a `skolemized` proof tag.

## Test Obligations

- Comparative report:
  - record the remaining 13 problems across ADR-0024 greenfield, upstream
    alphaleanTAP shape, legacy EP lessons, lean+lemma, and
    lean+lemma+Skolemization.
- First-order component tests:
  - every remaining Pelletier problem closes through generic first-order code.
  - the existing prompt and passing Pelletier selectors are updated honestly.
  - direct relational proof use still supports partial proof guidance.
  - bounded open branches still return without unbounded gamma descent.
- Dispatch and regression tests:
  - equality-bearing formulas stay on the full kernel.
  - program-bearing formulas stay on `prove-program`.
  - `lein test-proflog-fast`
  - `lein test-proflog-pelletier-prompt`
  - `lein test-proflog-pelletier`
  - the Pelletier comparison selector

## Exit Criteria

- All 46 Pelletier problems are either prompt passing or slow passing.
- `ported-too-slow-ids` is empty.
- The comparison/devisement report explains which execution differences were
  adopted and why.
- No Pelletier problem is solved by id-specific dispatch, theorem-specific
  overlay, or compiled proof plan.
