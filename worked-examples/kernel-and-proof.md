# Kernel And Proof Objects

This file covers:

- `test/proflog/kernel_test.clj`
- `test/proflog/proof_test.clj`

## Direct Complementary Closure

Query:

```clojure
p and not p
```

Representative proof term:

```clojure
(conj (savefml (close)))
```

The left conjunct is saved on the branch and the right conjunct closes against
it directly.

## Single-Use Universals Are Distinct From Ordinary Universals

Query:

```clojure
once-forall x. (value(x) and not value(x))
```

Representative proof term:

```clojure
(once-univ (conj (savefml (close))))
```

This is the operational difference introduced for executable negated
existentials: the proof records `once-univ`, not the ordinary `univ` step, and
the branch does not re-enqueue the quantifier indefinitely.

## Equality And Disequality Steps Stay Visible

Query:

```clojure
x != zero and x = zero
```

Representative proof term:

```clojure
(conj (neq-store (eq-step (eq-bind) (neq-close))))
```

So the proof tree preserves:

- when the disequality was stored,
- when a later equality updated the branch state,
- and when the stored disequality finally closed.

## Proof Objects Are Meant For Structural Inspection

`test/proflog/proof_test.clj` does not re-prove the semantics from scratch. It
checks that the proof object still exposes the major step tags the rest of the
system relies on:

- `conj` and `split` for the basic tableau shape,
- `univ`, `once-univ`, and `witness` for quantifier work,
- `savefml` for delayed branch closure,
- `pos-call` and `neg-call` for procedure calls,
- `eq-step`, `neq-store`, and `neq-close` for equality interaction.

That makes the proof object useful both for debugging and for worked examples
like the ones under this folder.

## Direct Kernel Descent

Unlike source-program examples, these tests start at the direct formula layer
from [Frontend To Kernel Descent](./frontend-to-kernel-descent.md). There is no
`pf/language`, no `language/compile-program`, and no Procedure Call Rule unless
the formula explicitly contains a program call in a `prove-program` test.

The direct complementary closure example is schematically:

```clojure
(kernel/prove
  (and (pos (app p))
       (neg (app p)))
  1
  fuel)
```

The four kernel parameters reduce to three here:

- `fml`: the formula whose branch should close;
- `n`: the requested number of proof terms;
- `fuel`: the admitted proof-search slice.

For program-bearing proof objects, the same proof vocabulary appears underneath
`kernel/prove-program`; the extra `prog` parameter supplies compiled relation
bodies for `pos-call` and `neg-call` steps. This is why the proof terms shown in
source-level examples can be compared directly with the proof terms in this
file.
