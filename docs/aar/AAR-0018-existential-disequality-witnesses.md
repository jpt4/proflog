# AAR-0018: Existential Disequality Witnesses

- Date: 2026-04-26
- Related ADR: [ADR-0018](../adr/ADR-0018-existential-disequality-witnesses.md)
- Outcome: complete for finite nullary object-language witnesses

## What Happened

ADR-0018 promoted the program:

```prolog
p(x) :- exists y. x != y
```

from a legacy impurity comparison into a greenfield gatekeeping regression.
The implemented fix adds an explicit object-language instantiation choice for
`once-forall`, the internal NNF form produced when a negative procedure call
negates an existential clause body.

For a finite constant-only language `{a, b}`, the pure proof path can now
instantiate the universal with the opposite declared constant. That makes
`p(a)` close through `a = b` and `p(b)` close through `b = a`, without ever
binding a user answer to `(par ...)`.

## What Worked

- `test/proflog/existential_disequality_test.clj` now gates the semantic
  distinction directly.
- `query-status` reports `:succeeds` for both `p(a)` and `p(b)`.
- `query-succeeds` for `p(a)` returns:

```clojure
((neg-call (once-univ (free-close))))
```

- `query-fails` for `p(a)` remains empty.
- `query-ground-answers` and `query-parity-answers` return exactly `a` and
  `b` at nullary depth / size `0`.
- `test/proflog/legacy_impurity_test.clj` remains green as the negative
  reference: legacy can still synthesize a `(par ...)` answer, while
  greenfield rejects that boundary violation.

## Boundary

The fix is intentionally narrow. Kernel-level `once-forall` now considers
declared nullary object-language terms. It does not perform unbounded Herbrand
constructor expansion inside the kernel.

The generic symbolic `query-answers` API also remains unchanged for this
program and still returns `[]`. That is deliberate: existing answer tests rely
on `query-answers` preserving symbolic frontiers and residual calls instead of
turning every open query into concrete enumeration. Concrete finite answer
materialization remains explicit through `query-ground-answers` and
`query-parity-answers`.

## List-Family Effect

ADR-0018 does not change the ADR-0017 list-family conclusion. Legacy still
outperforms greenfield on raw multi-step list proofs such as `Y04` and `Y08`,
while greenfield remains strong on the documented answer-surface materializers.
This branch fixed a semantic witness gap, not the raw list proof performance
gap.

## Verification

- `lein test proflog.existential-disequality-test`
- `lein test proflog.kernel-test proflog.query-test proflog.existential-disequality-test`
- `lein test proflog.answers-test`
- `lein test-proflog-legacy-impurity`
- `lein test-proflog-fast`

## Follow-Up

- Constructor-depth witness search in the pure proof path is implemented by
  [ADR-0019](../adr/ADR-0019-closed-term-gamma-instantiation.md); do not
  silently import whole-Herbrand enumeration into `proflog.kernel`.
- Keep generic symbolic `query-answers` separate from explicit finite
  materializers unless a later ADR decides to change that public contract.
- Return to the raw list-family performance gap as a separate problem; this
  witness fix does not materially affect it.
