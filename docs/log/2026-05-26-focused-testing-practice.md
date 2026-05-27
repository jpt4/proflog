# Focused Testing Practice for Resource-Heavy Suites

Date: 2026-05-26

## Problem

`lein test-proflog-sjas` can run for many minutes without emitting progress
because ordinary `clojure.test` reports only after a namespace completes. That
is a poor default while doing active semantic work: a slow advancing proof check
looks the same as a stuck run, and stopping it often forces the same expensive
tests to be repeated.

## Practice

Use three testing layers:

1. Run the exact red/green selector for the code being changed:

   ```text
   lein test :only proflog.willard-sjas-test/<test-var>
   ```

2. For resource-heavy namespaces, run var-by-var progress testing:

   ```text
   lein test-vars proflog.willard-sjas-test
   lein test-proflog-sjas-focused
   ```

   These commands execute the same test vars but print `:TEST` and `:DONE`
   timing lines around each var. This is the default SJAS verification path
   during development.

3. Keep `lein test-proflog-fast` and `lein test-proflog-extended` as the normal
   broad regression gates. Use the opaque `lein test-proflog-sjas` namespace
   gate only after focused progress shows the suite can finish within the
   current runtime envelope, or when a final ordinary namespace confirmation is
   specifically needed.

If an individual focused var exceeds its expected runtime, stop and investigate
that var. Do not restart a full namespace run just to rediscover the same slow
point.

## Rationale

The focused practice preserves strict red-green testing while reducing wasted
latency. It also produces better evidence: the log can name which exact
semantic property is slow, passed, failed, or regressed, rather than reporting
only that a large namespace eventually timed out or was killed.

This does not weaken the verification standard. It changes the default order:
focused evidence first, broad fast/extended gates in parallel where useful, and
opaque namespace gates only when they add signal.
