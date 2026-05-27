# 2026-05-01 Core.logic 1.0.1 vs 1.1.1 Source Comparison

## Context

The published `core.logic` 1.1.1 profile is compatible and showed small timing
differences on ADR-0032 probes. Before treating those timings as implementation
evidence, the exact JVM source needed comparison against Proflog's pinned 1.0.1
artifact.

## Files Compared

Compared the extracted local 1.0.1 JVM source tree against the vendored 1.1.1
JVM source overlay:

```text
clojure/core/logic.clj
clojure/core/logic/protocols.clj
clojure/core/logic/nominal.clj
clojure/core/logic/unifier.clj
```

The only diff in `logic.clj` is Proflog's ADR-0032 overlay marker. The other
reviewed JVM source files had no diff.

The jar file lists are also the same:

```text
diff -u /tmp/corelogic-1.0.1-files.txt /tmp/corelogic-1.1.1-files.txt
```

No output was produced.

## Pom Differences

The POM changed:

- artifact version: `1.0.1` to `1.1.1`
- parent: `org.clojure/pom.contrib` `1.1.0` to `1.4.0`
- `clojure.version`: `1.6.0` to `1.11.4`
- SCM tag: `v1.0.1` to `v1.1.1`
- license and project URLs modernized from `http` to `https`

However, Proflog's profiles still run Clojure 1.11.1 because `project.clj`
declares it directly:

```text
lein trampoline run -m clojure.main -e "(println (clojure-version))"
  1.11.1

lein with-profile +core-logic-1.1.1 trampoline run -m clojure.main -e "(println (clojure-version))"
  1.11.1

lein with-profile +core-logic-source-overlay trampoline run -m clojure.main -e "(println (clojure-version))"
  1.11.1
```

## Conclusion

The published 1.1.1 upgrade remains useful as a deployment/current-artifact
lane, but it should not be credited as a source-level JVM performance
improvement. The small timing deltas recorded in the upgrade probe are best
treated as measurement variance unless repeated under a more controlled timing
surface.

For ADR-0032, the source-overlay lane is therefore the real host-engineering
surface. Any retained improvement needs to introduce a genuine generic
`core.logic` change and then demonstrate broad benefit against the default jar,
the 1.1.1 jar, and the patched overlay.

