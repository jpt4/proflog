# 2026-04-29 Structural Profiler Purity Recovery

## Context

ADR-0026 added branch-level interoperation between the full Proflog kernel and
the optimized propositional / equality-free first-order layers. The first
working implementation kept the compiled program map out of `core.logic/project`,
but still used a projected guard for the residual branch profiler:

```clojure
(project [fml unexpanded lits sigma neqs]
  (if-let [profile (branch-profile fml unexpanded lits sigma neqs prog)]
    (== profile kind)
    fail))
```

That was an impurity in `proflog.kernel`. It walked the selected formula,
pending agenda, saved literals, equality substitution, and disequality store
out of logic space and then classified them with ordinary host predicates.

The immediate behavior was useful for the ADR-0026 tests, but the boundary was
architecturally wrong: the kernel had gained a non-relational term-shape
inspection point exactly where future partial, reverse, or proof-shape queries
should be able to leave structure uncommitted and continue through ordinary
kernel search.

## Recovery Principle

Keep finite host metadata separate from proof-state terms.

For ADR-0026, the compiled program is a lexical host value at the public
program-proof entry. It is acceptable for the handoff guard to derive the finite
set of active relation names from that host value:

```clojure
(active-program-relations prog)
```

What was not acceptable was projecting the branch state. The selected formula,
remaining agenda, saved literals, `sigma`, and `neqs` are proof-search state.
They must be checked with goals, not read with `project`.

## Conversion Pattern

The recovery replaced one host classifier with a family of small structural
relations.

The old projected profiler had four jobs:

- require empty equality state;
- require empty delayed disequality state;
- require a compound selected formula so literal-only branches stay on the full
  kernel first;
- classify the residual branch as pure propositional or equality-free
  first-order, while rejecting atoms whose relation name is active in the
  compiled program.

Each job became a logic goal:

```clojure
(== '() sigma)
(== '() neqs)
(compound-profile-entryo fml)
(pure-propositional-formulao fml active-relations)
(equality-free-first-order-formulao fml active-relations)
(formula-list-pure-propositionalo unexpanded active-relations)
(formula-list-equality-free-first-ordero lits active-relations)
```

Active program exclusion also moved from host set filtering to constraints.
Instead of collecting relation names from a projected formula and calling
`not-any?`, the structural atom relation checks the relation symbol and posts
disequality constraints against the finite active set:

```clojure
(defn- inactive-relationo
  [active-relations relation]
  (cond
    (= unknown-program-relations active-relations)
    fail

    (empty? active-relations)
    (== relation relation)

    :else
    (let [active-relation (first active-relations)
          remaining-relations (rest active-relations)]
      (all
        (!= relation active-relation)
        (inactive-relationo remaining-relations relation)))))
```

The recovered profiler is now a composition of goals:

```clojure
(defn- branch-profileo
  [fml unexpanded lits sigma neqs active-relations kind]
  (all
    (known-active-relationso active-relations)
    (== '() sigma)
    (== '() neqs)
    (compound-profile-entryo fml)
    (conde
      [(== 'propositional kind)
       ...]
      [(== 'first-order kind)
       ...])))
```

The handoff still remains conservative. Unknown program shape fails the guard.
Non-empty equality or disequality state fails the guard. Formula structure that
does not match the target profile fails the guard and lets the ordinary full
kernel continue.

## Design Notes

This was not a request to make every value relational. The compiled program map
is still a host compilation product. The important distinction is authority:

- host metadata may narrow a finite dispatch guard;
- proof-state terms must remain in logic space;
- if a branch cannot be classified structurally, the guard should fail rather
  than inspect it by projection.

The structural profiler also preserves ADR-0026's first-result boundary by
requiring a compound selected formula. Literal-only branches therefore still try
the full kernel's ordinary closure, equality, and procedure-call rules before a
background layer can be considered.

The first-order path additionally requires an actual first-order feature
through `branch-first-order-featureo`. This keeps purely propositional branches
from being redundantly classified by the broader equality-free first-order
relation.

## Reusable Checklist

Use this checklist when recovering from a projected classifier in the semantic
kernel:

1. Identify which inputs are finite host metadata and which are proof-state
   logic terms.
2. Keep host metadata lexical and explicit; do not smuggle proof-state terms
   through it.
3. Replace each host predicate with a small relation over the same shape.
4. Replace negative set-membership checks with disequality constraints when the
   forbidden set is finite.
5. Make unknown or partially unsupported host metadata fail conservatively.
6. Prefer false negatives over false positives: missed optimization is safer
   than delegating away branch state that still has semantic work to do.
7. Verify both behavior and purity: focused behavior tests, fast regression
   tests, and `rg -n "project" src/proflog/kernel.clj`.

## Verification

After the recovery:

- `rg -n "project" src/proflog/kernel.clj` returns no matches.
- `lein test proflog.kernel.dispatch-test proflog.pelletier-layering-test`
  passes with `7` tests and `39` assertions.
- `lein test-proflog-fast` passes with `105` tests and `349` assertions.
- The broader synthesis-oriented failures reproduce unchanged on clean `HEAD`,
  so they are baseline issues rather than fallout from the structural profiler.
