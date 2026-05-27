# SJAS/Proflog Separation Witness

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

Related:
[Proflog Kernel as an SJAS Proof-Predicate Shortcut: Proof Attempt](2026-05-26-proflog-kernel-sjas-shortcut-proof-attempt.md)

## Question

Find an SJAS-definable formula that should fail to close under proper SJAS
deduction, but does close when proved by the current Proflog kernel.

## Witness System

Use a Tableau-0 SJAS language that declares a unary relation
`external-demo/1`, but does not include any beta axiom or reflected Group-2b
clause for it:

```clojure
(sjas/system-source
  {:profile :willard-sjas-tableau0}
  (language
    (relations (external-demo 1))))
```

Now run the Proflog kernel with the same declared language plus an external,
non-reflected runtime clause:

```clojure
(sjas/system-source
  {:profile :willard-sjas-tableau0}
  (language
    (relations (external-demo 1)))
  (external
    (|- (external-demo x)
        (= x 0))))
```

The two systems have the same SJAS `system-code`: `external` clauses are
ordinary Proflog runtime clauses, not part of `canonical-system-source`, and
therefore not part of the encoded SJAS axiom basis.

## Object Formula

The object theorem candidate is:

```text
external-demo(0)
```

In AST form:

```clojure
(pos (app external-demo (app 0)))
```

This formula is SJAS-definable in the finite language above: `external-demo/1`
is declared in the language and the formula has an SJAS formula code.

## Stronger Proof-Predicate Formula

The more diagnostic SJAS-definable formula is the proof-predicate atom:

```text
tableau-proof(S0, F0, P0)
```

where:

- `S0` is the SJAS system code for the language with `external-demo/1` and no
  reflected clause for it;
- `F0` is the SJAS formula code for `external-demo(0)`;
- `P0` is the proof-code term for the current Proflog theorem proof:

```clojure
(conj
  (neg-call
    (profiled willard-sjas-arithmetic
      (sjas-equal
        (sjas-read-zero)
        (sjas-read-zero)
        (sjas-bind-done)))))
```

In source notation:

```clojure
(sjas/tableau-proof S0 F0 P0)
```

This is an SJAS-base-vocabulary formula: it uses `tableau-proof/3` and code
terms.

## Why Proper SJAS Should Not Close It

Proper SJAS deduction for `S0` sees:

- Group-0 axioms;
- Group-1 arithmetic axioms;
- Group-3 self-consistency axiom;
- no Group-2 beta formula mentioning `external-demo`;
- no Group-2b reflected clause for `external-demo`.

Thus a semantic-tableau proof of `external-demo(0)` from `S0` has no axiom or
deduction rule that can close the branch containing `not external-demo(0)`.
The only way to close that branch is to use the Proflog external runtime clause

```text
external-demo(x) :- x = 0
```

but that clause is not in `S0`. A proper SJAS proof predicate whose axiom basis
is determined by `system-code` must reject `tableau-proof(S0,F0,P0)`.

## Why the Current Proflog Kernel Closes It

The current Proflog kernel closes the object theorem directly by the
Procedure Call Rule:

```text
external-demo(0)
```

The proof found by the probe was:

```clojure
(profiled willard-sjas-tableau0
  (neg-call
    (profiled willard-sjas-arithmetic
      (sjas-equal
        (sjas-read-zero)
        (sjas-read-zero)
        (sjas-bind-done)))))
```

When this is lifted to the theorem query from the generated SJAS axiom basis,
the proof is:

```clojure
(profiled willard-sjas-tableau0
  (conj
    (neg-call
      (profiled willard-sjas-arithmetic
        (sjas-equal
          (sjas-read-zero)
          (sjas-read-zero)
          (sjas-bind-done))))))
```

The current `tableau-proof/3` implementation then accepts the corresponding
proof code because its non-`sjas-axiom` branch decodes `P0`, reconstructs the
target, and calls `kernel/prove-programo` with the full Proflog program. That
full program contains the external clause.

## Probe Output

Direct Proflog closure:

```text
:same-system-code true
:formula (pos (app external-demo (app 0)))
:direct-proflog-count 1
:direct-proflog-proof
(profiled willard-sjas-tableau0
  (neg-call
    (profiled willard-sjas-arithmetic
      (sjas-equal
        (sjas-read-zero)
        (sjas-read-zero)
        (sjas-bind-done)))))
```

Strict system without the external clause:

```text
:strict-direct-count 0
:strict-direct-proof nil
```

Current proof-predicate shortcut with the theorem certificate:

```text
:formula (pos (app external-demo (app 0)))
:theorem-proof
(profiled willard-sjas-tableau0
  (conj
    (neg-call
      (profiled willard-sjas-arithmetic
        (sjas-equal
          (sjas-read-zero)
          (sjas-read-zero)
          (sjas-bind-done))))))
:proof-predicate-count 1
:proof-predicate-proof
(profiled willard-sjas-tableau0
  (profiled willard-sjas-proof-check
    (sjas-code-bytes)
    (willard-sjas-theorem-code (sjas-code-bytes))
    (conj
      (neg-call
        (profiled willard-sjas-arithmetic
          (sjas-equal
            (sjas-read-zero)
            (sjas-read-zero)
            (sjas-bind-done)))))))
```

## Interpretation

This is a concrete separation witness:

```text
proper-SJAS(S0) should not close external-demo(0)
Proflog-with-external-clause closes external-demo(0)
current tableau-proof(S0,F0,P0) also closes by shortcut
```

The witness does not require a complicated self-referential sentence. It uses
the simplest possible boundary: a formula whose relation symbol is declared and
encodable, but whose only proof is through a runtime clause that is deliberately
not reflected into the SJAS system code.

This confirms the Track 2b concern: if `kernel/prove-programo` is called with
more executable program information than the SJAS `system-code` contains, it can
accept proof certificates that a proper SJAS proof predicate should reject.
