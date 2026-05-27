# Language Namespace Spec

This document explains `proflog.language`: the layer that turns a small
surface Proflog language into the compiled program shape consumed by the proof
kernel and answer overlay.

The proof kernel relation is deliberately low level. It proves formulas against
an explicit compiled program. It does not know whether a symbol was declared by
the user, whether a clause came from source syntax, whether a negated clause
body is consistent with its positive body, or whether a guarded alternative is
the right one for a source clause. Those are language-layer obligations.

## Responsibility Boundary

`proflog.language` owns these responsibilities:

- normalize language declarations;
- validate terms, atoms, formulas, queries, and source clauses;
- reject proof-time-only terms at the user boundary;
- alpha-rename clause parameters so merged clauses cannot capture each other;
- normalize source formulas into negation normal form;
- merge multiple same-relation source clauses into one Fitting-style compiled
  clause;
- compute the positive and negative body views used by procedure calls;
- build alternative and guarded-alternative metadata used by the answer overlay
  and constructor-recursive layer.
- preserve optional proof-profile metadata so query evaluation can opt in to a
  language-selected theory layer.

It does not prove anything. Proof search starts later, in `proflog.kernel` or
`proflog.answer-overlay`.

## Surface Language

A language declaration is a Clojure map:

```clojure
{:constants ['zero 'null]
 :functions {'s 1
             'cons 2}
 :relations {'append 3
             'reverse 2}
 :proof-profile :robinson-q}
```

`language/language` normalizes and validates this declaration.

Constants are syntactic sugar for zero-arity functions. After normalization,
`'zero` and `'null` are present in `:functions` with arity `0`, while
`:constants` remains a set for documentation and ordering.

Function and relation symbols are separate namespaces. A symbol may not be both
a term constructor and a relation. A constant may not also be declared as a
positive-arity function.

`:proof-profile` is optional. When present, it must be a keyword. The default
query path treats an absent profile as `:default`, which calls the existing
program kernel. Profile keys such as `:robinson-q` are interpreted by
`proflog.proof-profile`, not by the language validator itself.

## AST Contract

The language layer validates the tagged-list AST from `proflog.ast`.

Terms:

```clojure
(var x)              ; object-language variable
(par a)              ; internal proof parameter, never legal at the surface
(app zero)           ; zero-arity function / constant
(app s (app zero))   ; positive-arity function application
```

Atoms are also `app` nodes, but their head symbol is checked against
`:relations` rather than `:functions`:

```clojure
(app reverse xs ys)
```

Formulas:

```clojure
(pos atom)
(neg atom)
(eq left right)
(neq left right)
(and left right)
(or left right)
(not body)           ; surface only, compiled away by NNF normalization
(implies a b)        ; surface only, compiled away by NNF normalization
(forall [x] body)
(exists [x] body)
(once-forall [x] body)
```

The concrete quantifier representation is a nominal tie, not a vector. The
examples above are schematic. In code, use `ast/forall-form`,
`ast/exists-form`, and `ast/once-forall-form`.

`par` terms are rejected by `validate-term`. They are proof-time rigid
parameters introduced by the kernel for universal instantiation. If a public
program or query can mention `par`, the source/proof boundary has leaked.

## Validation

Validation is structural and declaration-sensitive.

`validate-term` accepts:

- object variables;
- declared function applications with the exact declared arity;
- recursive subterms that also validate.

It rejects:

- undeclared function symbols;
- arity mismatches;
- malformed tagged forms;
- all `par` terms.

`validate-atom` requires an `app` whose head is a declared relation with the
right arity, then validates all atom arguments as terms.

`validate-formula` walks the formula tree. It accepts both surface forms
(`not`, `implies`) and NNF/core forms because compilation and tests may call it
on either side of normalization.

`validate-clause` additionally checks that:

- the clause relation is declared;
- the parameter count matches the declared relation arity;
- every clause parameter is a nominal `nom`;
- the body is a valid formula.

`validate-query` is currently just formula validation with query-facing naming.

## Source Clause Shape

A source clause is:

```clojure
{:relation 'append
 :params [xs ys zs]
 :body formula}
```

The surface may contain multiple clauses for the same relation:

```clojure
(clause 'p [x] body-1)
(clause 'p [x] body-2)
```

Fitting-style procedure lookup in the kernel wants one compiled clause per
relation. `compile-program` groups surface clauses by relation and turns each
group into one compiled clause whose body is the disjunction of the group.

## Alpha-Renaming

When clauses are merged, their parameters must not accidentally share source
noms. `clause-group->core-clause` creates fresh compiled parameters for the
relation arity:

```clojure
fresh-params = [p0 p1 ...]
```

Each source body is then rewritten from its original parameter noms to the
fresh compiled parameter noms. This means all alternatives for one relation
talk about the same compiled parameter vector, while source-local variables
inside each body remain scoped by their own quantifiers.

This is why `fresh-nom` exists in `language.clj`: it creates nominal variables
for compile-time alpha-renaming in ordinary Clojure code.

## Normalization

Each source body is converted to NNF with `normalize/to-nnf`.

After NNF normalization:

- `not` is gone;
- `implies` is gone;
- negation appears only as literal polarity or as the dual of equality,
  disequality, and quantifiers;
- negated existentials become `once-forall`, which is operationally a
  single-use universal in the tableau branch.

The compiled clause stores both:

```clojure
:body         normalized-positive-body
:negated-body (normalize/negate-formula normalized-positive-body)
```

This invariant is critical. The proof kernel and `proflog.program` lookup
relations trust the compiled map. They do not recompute or check that
`:negated-body` is the logical negation of `:body`.

## Compiled Program Shape

`compile-program` returns:

```clojure
{:language lang
 :clauses {...}
 :clause-list (...)
 :alternative-clause-list (...)
 :guarded-clause-list (...)}
```

`:clauses` is a map from relation symbol to the full compiled clause.

`:clause-list` is the compact relational view used by ordinary procedure-call
lookup:

```clojure
{:relation rel
 :params params
 :body body
 :negated-body negated-body}
```

`:alternative-clause-list` keeps top-level disjunct alternatives separate for
answer-mode search and diagnostics:

```clojure
{:relation rel
 :params params
 :body body
 :negated-body negated-body
 :alternatives (...)
 :negated-alternatives (...)}
```

`:guarded-clause-list` adds guarded alternatives. This is the richest language
IR and is the main bridge from source syntax to the constructor-recursive and
answer-frontier work:

```clojure
{:relation rel
 :params params
 :body body
 :negated-body negated-body
 :alternatives (...)
 :negated-alternatives (...)
 :guarded-alternatives (...)}
```

## Alternatives

After clause bodies are normalized, top-level disjunctions are flattened:

```clojure
(or a (or b c)) => [a b c]
```

Those flattened alternatives are used to preserve executable branch structure
without forcing the kernel to rediscover it by repeatedly decomposing the same
formula tree.

Multiple source clauses and explicit source disjunctions both become
alternatives. From the kernel's point of view, they are just different ways the
relation body can hold.

## Guarded Alternatives

A guarded alternative is a compile-time analysis of one executable alternative.
It partitions the leading conjunction into:

- `:scope` - leading quantifiers stripped as metadata;
- `:guards` - equality and disequality formulas;
- `:calls` - positive or negative calls to relations defined by the same
  compiled program;
- `:residuals` - everything else.

For example, an append step clause has this source idea:

```clojure
exists head tail rest.
  xs = cons(head, tail)
  and zs = cons(head, rest)
  and append(tail, ys, rest)
```

The guarded alternative records:

- scope: `head`, `tail`, `rest`;
- guards: `xs = cons(head, tail)`, `zs = cons(head, rest)`;
- calls: `append(tail, ys, rest)`;
- residuals: none.

This is not a proof. It is executable metadata. It lets later layers saturate
constructor-visible guards, then decide whether a recursive call is productive
before exporting a residual frontier.

Each guarded alternative also stores negated views:

- `:negated-formula`;
- `:negated-guards`;
- `:negated-calls`;
- `:negated-residuals`;
- `:negated-ordered-conjuncts`.

Negative procedure-call closure uses these views to prove that a call cannot
hold by closing all guarded alternatives.

## Demand Ordering

`guarded-alternative-demand-score` gives alternatives with visible recursive
demand a chance to run before less informative branches. The current code keeps
call order inside a conjunction unchanged because producer-before-consumer
order matters for relations such as reverse followed by append.

The important distinction:

- alternatives may be reordered by compile-time demand metadata;
- calls inside one conjunction keep source order.

This avoids moving a consumer call before the call that creates its symbolic
input.

## Relationship To The Kernel

The ordinary kernel consumes compiled clauses through `proflog.program`. That
namespace performs relational lookup and parameter binding, but it does not
validate compiled-program invariants.

For a positive procedure call, the kernel looks up:

```clojure
:body
```

For a negative procedure call, it looks up:

```clojure
:negated-body
```

The answer overlay uses the same compiled program plus richer answer state:
answer variables, equality substitution, disequality store, residual frontier,
fuel, call depth, and proof metadata.

The constructor-recursive layer consumes `:guarded-clause-list`. It is still
generic: it does not know about `append`, `reverse`, `cons`, or `null`. It only
sees constructors, equality/disequality guards, and calls to defined relations.

## Public Boundary Rule

The public path is:

```text
declaration -> language/language
clauses -> language/compile-program
query -> language/validate-query
compiled program + query -> query/answers/kernel APIs
```

The unsafe path is constructing compiled program maps manually and passing them
directly to low-level proof relations. That can violate invariants such as
`:negated-body = negate(:body)`. The kernel may then prove against the invalid
compiled map because it is operating below the language boundary.

Therefore:

- public APIs should validate and compile through `proflog.language`;
- tests that intentionally synthesize compiled programs must constrain the
  compiled map if they expect valid source programs;
- kernel reverse-mode experiments over compiled maps are not the same thing as
  Proflog source-program synthesis unless the language invariants are included.

## Mental Model

Read `proflog.language` as a compiler, not as a prover.

The source programmer writes formulas and clauses in the object language. The
language namespace checks that the syntax belongs to the declared signature,
then compiles it into several synchronized views:

- a direct clause view for ordinary proof search;
- an alternatives view for answer search and diagnostics;
- a guarded view for constructor-sensitive recursive descent.

The kernel is allowed to stay small because the language namespace has already
done the linguistic work: symbol discipline, arity discipline, alpha-renaming,
normalization, and source-to-IR factoring.
