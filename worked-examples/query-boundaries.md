# Query Boundaries

This file covers the expanded regressions in `test/proflog/query_extended_test.clj`.

The key point of this namespace is contractual, not cosmetic: the bounded
query helpers are operational probes over finite search slices. They are not
semantic oracles and they are not hard real-time promises.

## Bounded Success Probe Can Return No Result

Query:

```clojure
query-succeeds-within(p2-program, win(0), 1, 25)
```

Current result:

```clojure
()
```

This does not mean `win(0)` is false. It means no success proof was completed
within the admitted search slice.

## Bounded Failure Probe Can Return No Result

Query:

```clojure
query-fails-within(p2-program, win(1), 1, 25)
```

Current result:

```clojure
()
```

Again, this is a timeout-shaped operational result, not a semantic conclusion.

## Easy Proofs Still Surface

The same helpers do return proofs when the obligation is shallow enough:

```clojure
query-succeeds-within(status-program, p(0), 1, 500)
=> (neg-call (refl-close))

query-fails-within(status-program, p(1), 1, 500)
=> (pos-call (free-close))
```

So the helper contract is:

- return proofs when an easy slice succeeds,
- return `()` when the slice budget is exhausted,
- and never confuse that empty operational result with semantic falsity or
  semantic truth.

## Source To Kernel Descent

The helper examples use the same compiled programs described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). The extra
parameters are operational budgets.

A bounded success probe:

```clojure
(query/query-succeeds-within p2-program (pf/q (win zero)) 1 25)
```

repeatedly calls:

```clojure
(query/query-succeeds p2-program query 1 fuel)
```

with increasing finite fuel until either a proof appears or the wall-clock
deadline has passed. The query formula still descends normally:

```clojure
(pf/q (win zero))
=> (pos (app win (app zero)))
```

and `query/query-succeeds` still asks `kernel/prove-program` to close the
negated query. The important distinction is that `timeout-ms` bounds the
iterative probing loop, while `fuel` bounds each individual proof-search slice.
An empty result from these helpers means "no proof in this slice," not "the
opposite semantic status was proved."
