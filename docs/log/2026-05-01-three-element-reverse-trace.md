# Three-Element Reverse Input-Synthesis Trace

Date: 2026-05-01
Branch: `adr-0033-structural-answer-variable-recursion`

## Query

The interrogated row is the longer input-synthesis reverse case:

```clojure
reverse(r, [c,b,a])
```

Expected answer:

```clojure
r = [a,b,c]
```

In matrix terms this is `reverse-input-flat-longer`.

## Greenfield Ordinary Raw Path

The ordinary matrix probe now closes the row:

```text
timeout -k 10s 120s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer
```

Result summary:

```clojure
{:target-found? true
 :raw-count 4
 :exported-count 2
 :closed-count 1
 :elapsed-ms 17805.109942}
```

The closed binding is:

```clojure
r = (app cons (app a)
          (app cons (app b)
               (app cons (app c) (app null))))
```

## Raw Frontier Before ADR-33 Completion

Running the same first raw proof state with structural completion disabled
shows the exact frontier ADR-33 settles:

```clojure
{:bindings
 [[r (app cons a_1 a_2)]]
 :residuals
 [(neg (app append
            a_3
            (app cons a_1 (app null))
            [c,b,a]))
  (neg (app reverse a_2 a_3))]}
```

Conceptually, the ordinary answer overlay has learned only:

```text
r = cons(a_1, a_2)
append(a_3, [a_1], [c,b,a])
reverse(a_2, a_3)
```

The constructor demand is in the append residual. Solving it forces:

```text
a_1 = a
a_3 = [c,b]
```

Then the remaining reverse residual forces:

```text
a_2 = [b,c]
```

Substitution composes to:

```text
r = [a,b,c]
```

The ADR-33 completion hook adds a
`constructor-recursive-residual-settlement` proof term after the original raw
`query-neg-call-guarded-alt` proof.

## Constructor-Recursive Layer Check

The diagnostic constructor-recursive layer also closes the row directly:

```text
timeout -k 10s 180s lein probe-proflog-list-kernel-matrix reverse-input-flat-longer constructor-recursive
```

Result summary:

```clojure
{:target-found? true
 :closed-count 1
 :elapsed-ms 1750.003951}
```

This is expected: ADR-33's ordinary raw path is now reusing the same generic
guarded-constructor insight at the answer-export boundary.

## Legacy Prover

The analogous legacy query was run directly through `cljtap.alphaleantap-ep`
using the same append/reverse clauses as the legacy Y-list tests:

```clojure
(run 1 [r proof]
  (nom a1 a2 a3 ah at ar r1 r2 rh rt rrp
    (let [prog [... append/reverse clauses ...]]
      (proveo ['neg ['app 'reverse
                     r
                     [c,b,a]]]
              '() '() '() prog proof))))
```

Forced result summary:

```clojure
{:answer [a,b,c]
 :result-count 1
 :elapsed-ms 3280.77153
 :proof-root neg-proc-call
 :neg-proc-call 49
 :proof-symbol-count 518}
```

Legacy therefore closes the same input-synthesis query and returns:

```clojure
r = (app cons (app a)
          (app cons (app b)
               (app cons (app c) (app nul))))
```

## Interpretation

The three-element input-synthesis row does not reveal a semantic gap between
legacy and greenfield after ADR-33. Both produce the same answer.

The implementation difference is operational:

- legacy keeps the answer variable live inside one direct `proveo` path and the
  final proof root is `neg-proc-call`;
- greenfield first exports a symbolic procedural frontier, then ADR-33
  structurally completes that frontier before answer selection;
- the frontier is generic and constructor-driven, not list-symbol-specific.

The useful pressure remaining from this trace is performance and accounting.
Greenfield closes the row through the ordinary raw matrix path, but it does
more explicit frontier/export/completion bookkeeping than legacy's direct
proof path.
