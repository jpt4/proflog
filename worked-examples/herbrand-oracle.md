# Herbrand Oracle

This file covers `test/proflog/oracle/herbrand_test.clj`.

The namespace uses a tiny fully ground signature:

```clojure
zero
one
succ(zero)
succ(one)
```

and compares the kernel against a direct structural oracle.

## Equality Oracle

For every pair of ground terms `left`, `right`, the test checks:

```clojure
kernel proves left = right
```

if and only if the direct structural comparison says the two ASTs are
different.

That may look inverted at first glance, but the greenfield kernel returns
proofs of closure. So a proof for `left = right` means the equality formula is
false on that ground pair.

## Disequality Oracle

Dually, the test checks:

```clojure
kernel proves left != right
```

if and only if the two ground terms are structurally identical.

Again the interpretation is closure-oriented: a proof for the disequality
formula means the disequality was refuted.

## Why This Matters

This is a small but valuable oracle test. It does not depend on any user
programs, quantifiers, or recursive search. It simply says that on fully
ground terms, the kernel's equality and disequality behavior agrees with plain
structural identity over the AST.

## Direct Formula Descent

This file is deliberately below the Proflog source layer documented in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). There is no
program and no query wrapper. Each oracle case builds a direct equality or
disequality formula:

```clojure
(ast/eq-lit left right)
(ast/neq-lit left right)
```

and calls:

```clojure
(kernel/prove formula 1 fuel)
```

The parameters are the two ground AST terms inside `formula`, `n = 1`, and the
fuel slice. The expected result is closure-oriented: a proof of `left = right`
means that equality is impossible for those ground terms, while a proof of
`left != right` means the disequality is impossible. The structural host oracle
is used only as an external correctness comparator for those fully ground
formulas, not as part of Proflog evaluation.
