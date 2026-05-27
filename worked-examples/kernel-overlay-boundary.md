# Kernel / Overlay Boundary

ADR-0015 separates the greenfield implementation into two operational layers:

- the pure proof kernel in `src/proflog/kernel.clj`,
- the answer-oriented overlay in `src/proflog/answer_overlay.clj`.

The split is not just a namespace preference. It is now part of the executable
contract.

## Pure Query Path

`query-succeeds` and `query-fails` stay on the pure kernel path:

```clojure
(query/query-succeeds program query 1)
=> uses kernel/prove-program

(query/query-fails program query 1)
=> uses kernel/prove-program
```

These helpers do not call the answer overlay. The routing regression is
`direct-query-probes-stay-on-the-pure-kernel-path` in
`test/proflog/query_test.clj`.

This is the path to trust when asking:

```text
Can the ordinary prover close this formula on its own?
```

## Answer Path

`query-answers` and the answer diagnostics use the extracted overlay:

```clojure
(answers/query-answer-diagnostics program query [x] opts)
=> uses answer-overlay/prove-program-query-entryo
   for top-level literal program queries

(answers/query-answer-diagnostics program composite-query [x] opts)
=> uses answer-overlay/prove-program-answero
   for the general answer path
```

These regressions live in `test/proflog/answers_test.clj`:

- `query-answer-diagnostics-route-literal-program-queries-through-the-answer-overlay-entry`
- `query-answer-diagnostics-route-composite-program-queries-through-the-general-answer-overlay`

This is the path to trust when asking:

```text
What symbolic bindings and residual obligations can the answer surface export?
```

## Shared Support

The proof core is not duplicated between those layers anymore. Shared branch
mechanics now live in `src/proflog/kernel_support.clj`:

- structural `L-ground` relations,
- complementary literal closure,
- disequality pruning,
- proof-variable-only disequality closure,
- and bounded fuel stepping.

That means ADR-0015 did two different things:

1. removed answer-mode entry points from the ordinary kernel surface,
2. kept one semantic definition of the proof core underneath both layers.

The practical reading is:

- kernel differences should now be investigated in `kernel.clj`,
- answer export / residual / call-depth behavior should be investigated in
  `answer_overlay.clj`,
- and support-layer changes should be treated as proof-core changes because
  they affect both.

## Source To Kernel Descent

The boundary described here begins after the source program has already followed
the descent in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). Both paths
receive the same compiled `program` and backend `query` formula.

For a ground status probe:

```clojure
(query/query-succeeds program query 1 fuel)
```

the call descends to:

```clojure
(kernel/prove-program program (normalize/negate-formula query) 1 fuel)
```

For an answer probe:

```clojure
(answers/query-answer-diagnostics program query [x] opts)
```

the call descends to an answer-overlay relation that carries the same compiled
program and formula plus exported variables, call depth, residual state, and raw
proof limits.

That means the split is not about source syntax. It is about result shape:
kernel status asks whether a formula closes; answer overlay asks which public
variables and residual obligations can be exported from proof states.
