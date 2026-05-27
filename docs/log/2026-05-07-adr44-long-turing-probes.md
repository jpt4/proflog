# ADR-0044 Long Turing Probe Results

Date: 2026-05-07
Branch: `adr-0044-long-probes`

These probes were run after the TC worked example was challenged for viability.
They are not promoted tests. They are diagnostic evidence about current proof
search behavior for less-guided recursive and reverse formulations of the
two-counter machine interpreter.

## Probe Surface

The reproducible entrypoint is:

```text
lein probe-proflog-turing-completeness <probe-id>
```

Probe identifiers:

```text
recursive-transfer-3-steps
recursive-transfer-5-steps
direct-ground-three-step-trace
open-predecessor-step
```

## Results

### Recursive Transfer, Three Steps

Command:

```text
timeout -k 5s 900s /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness recursive-transfer-3-steps
```

Output:

```clojure
{:starting-probe "recursive-transfer-3-steps"}
{:result :succeeds,
 :proof-count 1,
 :first-proof-tag neg-call,
 :probe "recursive-transfer-3-steps",
 :elapsed-ms 773835.238895}
elapsed_seconds 783.72
```

Assessment: viable but far too slow for regression. The 180s short timeout was
an operational bound, not a semantic impossibility.

### Open Predecessor Step

Command:

```text
timeout -k 30s 7200s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness open-predecessor-step
```

Output:

```clojure
{:starting-probe "open-predecessor-step"}
{:result :answers,
 :answer-count 4,
 :answers
 [{:bindings [[<nom:before__8960>
               (app cfg (app l1) (app s (app zero)) (app s (app zero)))]],
   :residuals [],
   :proof-count 3}
  {:bindings [[<nom:before__8960>
               (app cfg (var _0) (app zero) (app s (app s (app zero))))]],
   :residuals [(neg (app inc0 (var _0) (app l0)))],
   :proof-count 2}
  {:bindings [[<nom:before__8960>
               (app cfg (var _0)
                    (app s (app zero))
                    (app s (app s (app s (app zero)))))]],
   :residuals [(neg (app decjz1 (var _0) (app l0) (var _1)))],
   :proof-count 10}
  {:bindings [[<nom:before__8960>
               (app cfg (var _0)
                    (app s (app s (app zero)))
                    (app s (app s (app zero))))]],
   :residuals [(neg (app decjz0 (var _0) (app l0) (var _1)))],
   :proof-count 1}],
 :probe "open-predecessor-step",
 :elapsed-ms 637275.435493}
elapsed_seconds 645.66
```

Assessment: viable but slow. The first answer is the concrete predecessor
`cfg(l1, s(zero), s(zero))`; later records expose symbolic residual alternatives.
The promoted test remains the smaller instruction-relation partial synthesis
because it exercises the mode without forcing this expensive reverse step path.

### Direct Ground Three-Step Trace

Command:

```text
timeout -k 30s 7200s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness direct-ground-three-step-trace
```

Output before stop:

```clojure
{:starting-probe "direct-ground-three-step-trace"}
```

Assessment: no proof returned before controlled stop at about thirty minutes.
This is not a semantic failure: the same three-step computation has a passing
answer-mode trace. The result shows strong formulation sensitivity in current
proof search.

### Recursive Transfer, Five Steps

Command:

```text
timeout -k 30s 1800s nice -n 10 /usr/bin/time -f 'elapsed_seconds %e' \
  lein probe-proflog-turing-completeness recursive-transfer-5-steps
```

Output:

```clojure
{:starting-probe "recursive-transfer-5-steps"}
```

The process exited with timeout status `124` after the 1800s wrapper.

Assessment: not viable as a current test. This is diagnostic evidence that
recursive TC traces need proof-search performance work before deeper transfer
runs can be treated as practical demonstrations.

## Conclusion

The TC demonstration is more than theoretical: promoted tests and two long
diagnostic probes produce valid kernel/answer results. The current performance
frontier is nevertheless sharp. Search over the same finite computation can be
viable in one formulation and impractical in another. Future work should improve
recursive trace search, conjunction scheduling, and answer-mode residual
continuation before deeper two-counter runs become regression tests.
