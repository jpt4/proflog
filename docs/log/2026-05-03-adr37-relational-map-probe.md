# ADR-37 relational map probe

Date: 2026-05-03
Branch: `adr-0037-core-logic-minikanren-enhancements`
Worker: E, generic relational map support

## Question

Some Proflog state is currently represented as association lists or concrete
host structures because core.logic does not provide a generic open relational
map abstraction. ADR-37 should distinguish Proflog-specific relations from
generic capabilities that Proflog happens to need. Relational map support is in
the second category: if it is useful for Proflog state, it is also useful for
other core.logic programs.

This worker prototyped map-like relations for:

- lookup
- key presence and absence
- assoc/update
- dissoc/remove
- uniqueness checking for canonical map-like association lists

## Prototype

Added `proflog.relational-maps-probe`.

The practical relations operate on association lists where each entry is a
two-element vector:

```clojure
'([:sigma sigma-state] [:neqs neqs-state])
```

The relations are:

- `entryo`
- `alist-lookupo`
- `alist-contains-keyo`
- `alist-absent-keyo`
- `alist-unique-keyso`
- `alist-assoco`
- `alist-updateo`
- `alist-dissoco`

The namespace also includes comparison helpers for Clojure persistent maps:

- `ground-map-lookupo`
- `ground-map-assoco`
- `ground-map-dissoco`

Those helpers use `project`; they are included to make the boundary visible,
not as a recommended relational substrate.

## Clojure Map Assessment

Clojure persistent maps are viable as exact ground values. core.logic can unify
map literals when the key set is exactly known, and a projected helper can read
or update a ground map.

They are not viable as open relational maps in this probe:

- map unification is exact, so `{:a q}` does not unify with `{:a 1 :b 2}`;
- there is no open map tail analogous to a logic list tail;
- unknown-key lookup does not enumerate map entries through host `contains?`;
- host `assoc`, `dissoc`, `contains?`, and `get` require projection when the
  map operation itself is not already represented relationally.

Therefore the practical first step is a canonical association-list
representation, optionally with a ground host-map conversion boundary outside
the core relation.

## Proflog-Motivated Uses

Potential Proflog state that could benefit if relational map support remains
viable:

- `sigma`, the explicit equality substitution. It is currently an association
  list in `proflog.equality`, and lookup/unbound checks are already relational.
  A shared map-like relation could consolidate that pattern and later support
  indexing.
- Disequality state and neqs indexes. If equality learns many disequalities,
  keyed indexes over proof variables or rigid parameters may reduce repeated
  scans, provided the relation stays open enough for synthesis.
- Program clause lookup. Compiled programs already use Clojure maps at the
  host layer, while relation-level lookup still operates over lists of guarded
  alternatives. A relational index could bridge those shapes without forcing
  projection at the point of proof search.
- Residual frontier indexes in the answer overlay. ADR-35 preserved performance
  by using concrete continuation machinery; a relational index would only be
  worth exploring at narrow guard/selection boundaries, not by rewriting the
  whole scheduler.
- Guard caches for Proflog-specific term classifiers, such as L-ground,
  par-free, constructor-demanded, or defined-call residual checks. These are
  Proflog-specific predicates, but the indexed associative state needed to
  remember their results could be generic.

## Decision

Use association lists as the ADR-37 relational map substrate for now.

Do not move production Proflog state to Clojure persistent maps for relational
operations unless the use site is explicitly a host boundary. Persistent maps
may still be the right representation for compiled, ground metadata; they are
not enough for reverse or partial proof-state synthesis without a lower-level
core.logic extension.

The next useful step is not a broad production rewrite. It is a benchmarked
prototype at one Proflog state boundary, most likely `sigma` lookup/unbound
checks or a small program-clause index, comparing:

- current hand-written association-list relations;
- the shared ADR-37 association-list map relations;
- a projected Clojure map fast path used only when the map and key are ground.

## Validation

```text
timeout -k 10s 120s lein test proflog.relational-maps-probe-test

Ran 4 tests containing 22 assertions.
0 failures, 0 errors.
```

A direct JVM repeat also passed under concurrent ADR-37 worker load:

```text
timeout -k 10s 180s java -cp ... clojure.main -e \
  "(require 'proflog.relational-maps-probe-test)
   (clojure.test/run-tests 'proflog.relational-maps-probe-test)"

Ran 4 tests containing 22 assertions.
0 failures, 0 errors.
```

One timed Leiningen repeat hit a 120s wrapper after printing only the namespace
header while other ADR-37 worker JVMs were active; the direct JVM repeat above
is the validation result for the relation code itself.
