# Reverse Program Synthesis

This file covers `test/proflog/reverse_program_synthesis_test.clj`.

## Fixed Clause-Shape Synthesis Works

The current relational kernel can synthesize a contradictory compiled clause
body when the compiled program shape is already fixed.

The test asks for a program whose only clause is structurally:

```clojure
{:relation 'p
 :params   [x]
 :body     (eq f g)
 :negated-body true}
```

with the side condition `f != g`.

Then it runs the positive call:

```clojure
p(one)
```

and obtains the proof:

```clojure
(pos-call (free-close))
```

So the kernel can relationally discover a body that fails by constructor clash.

## The Current Boundary Is Still Narrow

The second test supplies an inconsistent compiled program:

```clojure
{:relation 'p
 :params [x]
 :body (eq zero one)
 :negated-body (eq zero one)}
```

Both directions close:

```clojure
p(one)      => (pos-call (free-close))
not p(one)  => (neg-call (free-close))
```

That is the current limitation: the internal compiled representation does not
yet enforce coherence between `:body` and `:negated-body`. The fixed-shape
reverse-synthesis result is real, but it is not yet a sound surface-program
synthesis contract.

Operationally, that is why this namespace belongs in the extended semantic
surface rather than in any user-facing claim about relational source-program
synthesis. It is a worked example of what the kernel can currently do with a
directly supplied compiled shape, not evidence that arbitrary surface clauses
can already be synthesized soundly.

## Backend To Kernel Descent

This example starts below the frontend described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md). There is no
Fitting-style source clause and no `pf/proflog` wrapper because the test
intentionally leaves part of the compiled program shape relational.

The synthesized object is a compiled clause entry:

```clojure
{:relation p
 :params [x]
 :body body
 :negated-body negated-body}
```

The query formula is ordinary:

```clojure
(pos (app p (app one)))
```

and the kernel entry is:

```clojure
(kernel/prove-program synthesized-program query 1 fuel)
```

The current proof is meaningful only at that backend boundary. It shows that
the relational kernel can discover a body such as `zero = one` that closes a
positive call. It does not yet provide a frontend synthesis contract, because
the backend representation can be made incoherent by choosing unrelated
`:body` and `:negated-body` formulae. A future source-level synthesis feature
would need to synthesize clauses before `language/compile-program` and let the
compiler derive the negated body.
