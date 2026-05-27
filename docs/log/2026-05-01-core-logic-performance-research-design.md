# 2026-05-01 Core.logic Performance Research and Design

## Context

ADR-0031 closed with useful but incomplete list-family progress. Guarded IR and
the constructor-recursive sidecar layer proved that generic
constructor-recursive handling can close multiple blocked append/reverse rows,
but the ordinary raw answer path still misses the representative reverse and
partial synthesis failures recorded in AAR-0031.

ADR-0032 moves the next experiment below Proflog itself. The question is
whether generic `core.logic` host changes can reduce search, unification,
stream, tabling, or constraint overhead enough to improve those carried rows
without adding list-specific Proflog logic.

## Sources Reviewed

Local Proflog:

- `project.clj`
- `src/proflog/kernel.clj`
- `src/proflog/answer_overlay.clj`
- `src/proflog/kernel/constructor_recursive.clj`
- `src/proflog/list_kernel_matrix_probe.clj`
- `test/proflog/list_kernel_matrix_test.clj`
- `test/proflog/synthesis_modes_test.clj`

Local `core.logic` 1.0.1 source:

- `clojure/core/logic.clj`
- `clojure/core/logic/protocols.clj`
- `clojure/core/logic/nominal.clj`
- `clojure/core/logic/unifier.clj`

Upstream references:

- `https://github.com/clojure/core.logic`
- `https://repo.maven.apache.org/maven2/org/clojure/core.logic/`
- `https://clojure.github.io/core.logic/`

## Current Dependency and Runtime Loading

Proflog currently pins:

```clojure
[org.clojure/core.logic "1.0.1"]
```

Runtime verification before ADR-0032 edits showed:

```text
jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.0.1/core.logic-1.0.1.jar!/clojure/core/logic.clj
version=1.0.1
groupId=org.clojure
artifactId=core.logic
```

The extracted `clojure/` tree in the repo root matches the jar layout, but it
is not on the Leiningen source path. It is therefore useful for review but not
active at runtime.

Upstream now publishes a newer stable artifact than Proflog uses. The upstream
README reports `org.clojure/core.logic` 1.1.1 as the latest stable release, and
Maven Central lists both 1.1.0 and 1.1.1 after Proflog's current 1.0.1.

The first experiment should therefore be a dependency upgrade profile before a
custom patch. This separates "already fixed upstream" from "requires local
core.logic engineering."

## Hot Surfaces in Core.logic 1.0.1

### Unification

The central path is:

- `ext`
- `walk`
- `walk*`
- `unify`
- `LVar`'s `IUnifyTerms`
- `LCons`'s `IUnifyTerms`
- `unify-with-sequential*`

This matters because Proflog terms are mostly Clojure list structures such as
`(app f args)`, `(pos atom)`, and guarded-clause records containing nested
formula lists. Repeated `==` goals over these structures drive most proof
steps.

`LCons` already has an explicit sequence/lcons loop for unification, but its
`IWalkTerm` implementation is recursive and has a source TODO noting that a
non-stack-consuming implementation would require more design. Deep walked
constructor terms are therefore a plausible cost center, but any patch here
must preserve logical reification and occurs-check behavior.

### Goal Streams

`Choice`, `nil`, `Object`, and `clojure.lang.Fn` implement the stream
protocols:

- `IBind`
- `IMPlus`
- `ITake`

`conde` expands to nested `mplus*` and `bind*` forms. Proflog kernels use
large nested `conde` trees, so stream allocation and thunk forcing are plausible
cost centers. A generic change here would benefit any `core.logic` program, but
the semantic risk is high because fairness and answer ordering are observable.

### Constraints

`==` checks whether the constraint store is non-empty, temporarily tracks
changed variables in `:vs`, calls `unify`, and then conditionally runs
constraints. Proflog uses disequality heavily in kernel support and answer
residual maintenance, so changed-var tracking and constraint wake-up should be
measured before patching.

A plausible low-risk target is avoiding avoidable allocation on no-constraint
or no-change paths, but the default implementation already skips `:vs` setup
when `(:cs a)` is empty.

### Tabling

Core.logic tabling is implemented through `AnswerCache`, suspended streams,
`reuse`, and `subunify`. A local source comment already flags `subunify` and
tabled reification as candidates for consideration.

ADR-0031's answer-path tabling probe found duplicate exported answer records
rather than repeated raw proof families, so tabling is not the first likely
list-family unlock. It remains relevant for generic performance once profiling
shows repeated tabled calls or repeated answer-cache reification.

### Nominal Terms

Proflog uses nominal ties for quantifier bodies. Core.logic nominal handling
adds `Nom`, `Tie`, `hash`, `suspc`, and `swap-noms`. Pelletier and quantified
program tests exercise this path heavily. Any nominal patch should be driven by
profiling because nominal correctness is part of Proflog's object-language
binding model.

## Deployment Design

### Lane 1: Default

Keep the base dependency unchanged:

```clojure
[org.clojure/core.logic "1.0.1"]
```

The host probe should report the Maven jar and version 1.0.1.

### Lane 2: Published Upgrade

Add a Leiningen profile that replaces the base dependency vector with:

```clojure
[org.clojure/clojure "1.11.1"]
[org.clojure/core.logic "1.1.1"]
```

The profile must be verified with the host probe before performance results are
trusted.

### Lane 3: Patched Source Overlay

If the published upgrade does not materially improve the carried failures,
vendor the exact chosen upstream source under a `vendor/` path and add a
profile that places that source path before the dependency jar.

The patched source should include an explicit runtime marker namespace or var
so the host probe can distinguish "loaded from local source" from "still loaded
from Maven jar."

### Lane 4: Patched Artifact

If source overlay classpath behavior becomes brittle, install the patched host
as a local Maven coordinate such as:

```clojure
[local.proflog/core.logic "1.1.1-proflog-adr32-1"]
```

This lane should be used only after the source overlay proves a patch is worth
stabilizing.

## Measurement Plan

Initial smoke:

```text
lein probe-core-logic-host
lein test-proflog-fast
lein test-proflog-constructor-recursive
```

Upgrade smoke:

```text
lein with-profile +core-logic-1.1.1 probe-core-logic-host
lein with-profile +core-logic-1.1.1 test-proflog-fast
lein with-profile +core-logic-1.1.1 test-proflog-constructor-recursive
```

Targeted carried rows:

```text
timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-input-flat
timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-output-nested-longer
timeout 180s lein run -m proflog.list-kernel-matrix-probe reverse-partial-output-tail
timeout 180s lein test proflog.synthesis-modes-test
```

The same commands should be repeated under each host profile.

## Initial Hypotheses

1. A published upgrade may improve generic runtime cost but is unlikely by
   itself to change proof-search shape enough to close reverse synthesis rows.
2. The most plausible generic patch targets are unification/reification over
   nested list terms and stream scheduling allocation, not tabled answer reuse.
3. A speedup without changed answer shape is still valuable, but it does not
   satisfy ADR-0032 unless it enables the carried matrix rows to close under
   documented bounds.
4. If host changes cannot move the carried failures, the next architectural
   step is likely deeper integration of the constructor-recursive sidecar with
   ordinary branch-level dispatch rather than more host-language work.

