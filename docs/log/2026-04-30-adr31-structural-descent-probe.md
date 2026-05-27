# 2026-04-30 ADR-0031 Structural Descent Probe

## Prototype

The structural-descent branch added a generic structural descent predicate over
constructor terms:

- a child call descends when it has the same relation symbol as an ancestor
  call;
- at least one corresponding child argument is a proper constructor subterm of
  the ancestor argument;
- the predicate does not name list relations or constructors.

The answer overlay had an opt-in call-credit path for probes. When enabled, it
carried an internal call stack from the query entry through recursive procedure
calls. When the normal answer `call-depth` budget was already exhausted, a
structurally descending same-relation child call could continue without
consuming more `call-depth`. Normal budgeted calls still ran first, and the
feature was disabled by default so public answer bounds stayed stable.

## Probe Results

The focused structural tests passed:

```text
lein test proflog.structural-descent-test
Ran 3 tests containing 6 assertions.
0 failures, 0 errors.
```

The existing CI-safe list matrix remained green:

```text
timeout 240s lein test proflog.list-kernel-matrix-test
Ran 2 tests containing 19 assertions.
0 failures, 0 errors.
```

With an intentionally exhausted answer call budget, multiple ADR-0031 append
matrix rows still closed through raw answer search:

```text
(matrix/run-case :append-output-flat {:call-depth 0})   => target-found? true
(matrix/run-case :append-output-nested {:call-depth 0}) => target-found? true
(matrix/run-case :append-prefix-flat {:call-depth 0})   => target-found? true
```

Representative reverse blockers did not close under the CI-safe raw settings:

```text
reverse(r, [b,a])                         => target-found? false
reverse([[a],[b],[c]], r)                 => target-found? false
reverse([a,b,c], cons(c, r))              => target-found? false
```

A broader synthesis regression check remained risky: `lein test
proflog.synthesis-modes-test` failed two tests on the prototype branch. That
keeps the branch from being mergeable as production answer-overlay behavior.

## Assessment

The call-stack and structural metric are generic and useful as a small
constructor-recursive primitive. They improved multiple append rows under
call-depth exhaustion without adding append/reverse/cons/null production
checks.

Do not merge the prototype as-is. Reverse-family synthesis remained blocked by
proof-search/frontier ordering after structurally descending reverse calls
reached dependent append obligations. A follow-up would need a broader
continuation or demand mechanism for dependent calls, not more relation-specific
descent checks.
