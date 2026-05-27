# 2026-04-29 Kernel Support Disequality Purity Gap

## Prompt

In ADR-27, we enhanced the transitive relational purity of the proof kernel by
converting subst.clj procedures using `project` into structural relations,
while preserving all extant performance. Now, in kernel_support.clj, it has
become known that several procedures called from kernel.clj also use `project`;
those regarding fuel use are reasonable, for now, but others like
prune-contradictory-neqso and stable-neqso are questionable - what reverse and
partial synthesis tests will the kernel fail to pass, due to lack of relational
purity in these called procedures?

## Response

The tests that expose this are narrower than the current list/Peano synthesis
failures. They target branch-state synthesis around `neqs` and `sigma`, because
`prune-contradictory-neqso` and `stable-neqso` project those values at
[kernel_support.clj:162](../../src/proflog/kernel_support.clj:162) and
[kernel_support.clj:172](../../src/proflog/kernel_support.clj:172).

The kernel failures I found are:

1. **Reverse `neqs` synthesis through the equality-continuation rule**

`stable-neqso` is called before continuing after a non-closing equality at
[kernel.clj:679](../../src/proflog/kernel.clj:679). If `neqs` is left
relationally open, the projected helper throws instead of synthesizing a stable
store.

```clojure
(run 1 [neqs]
  (fresh [sigma-out neqs-out]
    (kernel/prove-stateo
      (ast/eq-lit (ast/app-term 'a) (ast/app-term 'a))
      (list (ast/neg-lit (ast/app-term 'p)))
      (list (ast/pos-lit (ast/app-term 'p)))
      '() '() '() sigma-out neqs neqs-out nil '() nil
      (list 'eq-step '(eq-refl) '(close)))))
```

Current result: exception, `Don't know how to create ISeq from:
clojure.core.logic.LCons`.

2. **Partial `sigma` synthesis through post-closure pruning**

`prune-contradictory-neqso` is called after complementary literal closure at
[kernel.clj:757](../../src/proflog/kernel.clj:757) and
[kernel.clj:813](../../src/proflog/kernel.clj:813). If `sigma` is open,
projection throws instead of allowing a compatible state such as empty sigma.

```clojure
(run 1 [sigma]
  (fresh [sigma-out neqs-out]
    (kernel/prove-stateo
      (ast/neg-lit (ast/app-term 'p))
      '()
      (list (ast/pos-lit (ast/app-term 'p)))
      '() '()
      sigma
      sigma-out
      (list [(ast/var-term x) (ast/app-term 'a)])
      neqs-out
      nil '() nil
      '(close))))
```

Current result: exception, `Don't know how to create ISeq from:
clojure.core.logic.LVar`.

3. **Partial term synthesis leaves stale contradictory disequalities after
pruning**

This is the order-dependence bug: `project` sees `term` before the later `==`,
so pruning keeps a disequality that becomes reflexive.

```clojure
(run* [neqs-out]
  (fresh [term sigma-out]
    (kernel/prove-stateo
      (ast/neg-lit (ast/app-term 'p))
      '()
      (list (ast/pos-lit (ast/app-term 'p)))
      '() '() '() sigma-out
      (list [term (ast/app-term 'a)])
      neqs-out
      nil '() nil
      '(close))
    (== term (ast/app-term 'a))))
```

Expected under relational pruning: `'(())`.

Current result: `(([(app a) (app a)]))`.

4. **`stable-neqso` allows an equality continuation that should be rejected
after later refinement**

Same ordering problem, now through the equality continuation guard. The branch
continues while the disequality is apparently stable, then the term is refined
to make it contradictory.

```clojure
(run* [neqs-out]
  (fresh [term sigma-out]
    (kernel/prove-stateo
      (ast/eq-lit (ast/app-term 'a) (ast/app-term 'a))
      (list (ast/neg-lit (ast/app-term 'p)))
      (list (ast/pos-lit (ast/app-term 'p)))
      '() '() '() sigma-out
      (list [term (ast/app-term 'a)])
      neqs-out
      nil '() nil
      (list 'eq-step '(eq-refl) '(close)))
    (== term (ast/app-term 'a))))
```

Expected: no answer. Current result: `(([(app a) (app a)]))`.

So the actionable failing class is: **reverse/partial `prove-stateo` tests
where `neqs`, `sigma`, or terms inside saved disequalities are not fully known
before the support helper runs**. Public `proveo`/`prove-programo` still hide
most of this because they seed `sigma` and `neqs` as empty lists, but the
kernel relation itself is not relationally pure across those branch-state
arguments.
