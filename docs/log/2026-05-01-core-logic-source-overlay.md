# 2026-05-01 Core.logic Source Overlay Deployment

## Context

The published 1.1.1 upgrade is compatible but does not close ADR-0032's carried
list-family failures. The next prerequisite is a repeatable way to load patched
`core.logic` source, rather than accidentally continuing to run the default
Maven jar.

## Implementation

Vendored the JVM source from the published 1.1.1 jar under:

```text
vendor/core.logic-1.1.1/src
```

Added a marker var to the vendored `clojure.core.logic` namespace:

```clojure
proflog-adr32-host-marker
```

Added the Leiningen profile:

```text
core-logic-source-overlay
```

That profile replaces the source path with the vendored source path before
Proflog's own `src`, and replaces dependencies with Clojure plus
`org.clojure/core.logic` 1.1.1. The jar remains present for metadata and any
classpath resources not supplied by the source overlay, but
`clojure/core/logic.clj` should resolve to the local file.

## Runtime Verification

Default jar:

```text
lein probe-core-logic-host

core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.0.1/core.logic-1.0.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic version: 1.0.1
core.logic marker: <none>
```

Published 1.1.1 jar:

```text
lein with-profile +core-logic-1.1.1 probe-core-logic-host

core.logic source: jar:file:/home/jpt4/.m2/repository/org/clojure/core.logic/1.1.1/core.logic-1.1.1.jar!/clojure/core/logic.clj
core.logic source-kind: maven-jar
core.logic version: 1.1.1
core.logic marker: <none>
```

Source overlay:

```text
lein with-profile +core-logic-source-overlay probe-core-logic-host

core.logic source: file:/home/jpt4/code/proflog/vendor/core.logic-1.1.1/src/clojure/core/logic.clj
core.logic source-kind: local-source
core.logic version: 1.1.1
core.logic marker: vendor/core.logic-1.1.1/src
```

The marker proves that the patched source was loaded.

## Verification

```text
lein with-profile +core-logic-source-overlay test-proflog-core-logic-host
  Ran 1 tests containing 5 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-source-overlay test-proflog-constructor-recursive
  Ran 6 tests containing 21 assertions.
  0 failures, 0 errors.

lein with-profile +core-logic-source-overlay test-proflog-fast
  Ran 117 tests containing 380 assertions.
  0 failures, 0 errors.
```

## Decision

The source-overlay lane is ready for local `core.logic` patch experiments.
Future host patches should be made under `vendor/core.logic-1.1.1/src`, run
through `+core-logic-source-overlay`, and compared against both the default
1.0.1 jar and the published 1.1.1 jar.

