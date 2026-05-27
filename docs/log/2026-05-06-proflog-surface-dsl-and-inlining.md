# Proflog Surface DSL And Inlining Dependency

Date: 2026-05-06
Related ADR: [ADR-0010](../adr/ADR-0010-frontend-inlining-translation.md)

## Context

Current public Proflog programs are written through the Clojure AST constructor
API in `proflog.ast` and compiled by `proflog.language`. That is materially
better than asking users to write the raw tagged AST directly, but it is still
not the desired authoring experience.

The desired frontend is a DSL where a user can write Proflog at the REPL, then
compile and evaluate it through the existing language and kernel layers. For
example, source should eventually be able to express clauses such as:

```prolog
zero-only(x) :-
  forall y. (x != y or y = zero).
```

without requiring explicit constructor calls for every term, literal,
quantifier, and clause.

## Syntax Assessment

A Prolog-like clause syntax is the better primary user-facing style for
Fitting-style Proflog. It preserves the paper's organization:

- a program is a set of relation definitions;
- a clause has the shape `R(args) :- formula`;
- clause bodies may contain full first-order formulas, including equality,
  disequality, disjunction, implication, existential quantification, and
  universal quantification;
- the compiled program still reaches the Fitting-style kernel as formula data,
  not as ordinary Prolog execution.

This surface must be Prolog-like, not Prolog-semantic. Ordinary Prolog users
expect helper predicates and multiple clauses to be operationally harmless
refactorings in many common cases. Proflog cannot promise that generally:
Fitting's move-warning example shows that factoring an inline `win/1`
definition through an auxiliary `move/2` relation changes executability.

A miniKanren-like embedded DSL is also attractive for Clojure users. It could
look like relational code with `fresh`, `conde`, equality, and procedure calls,
and then compile to Proflog AST rather than execute as a normal miniKanren
relation. That makes answer and partial-synthesis workflows feel familiar.
However, miniKanren organizes programs around relations-as-goals, while
Fitting's Proflog organizes programs around first-order clause bodies evaluated
by a tableau procedure-call rule. Universal quantification, classical negation,
and Fitting's positive/negative procedure-call semantics are more naturally
visible in a Prolog-like FOL clause language.

The practical conclusion is:

- preserve Fitting/Proflog organization as the primary surface model;
- prefer a Fitting-style DSL for tutorial and REPL authoring;
- do not require Prolog's concrete infix notation: Polish/prefix forms are
  likely a better fit for Clojure interop and macro tooling;
- optionally offer a Clojure/miniKanren-flavored builder DSL as a convenience
  layer that still emits the same source clause representation;
- avoid presenting the surface as ordinary Prolog or ordinary miniKanren.

This distinction matters. Fitting uses Prolog-style terms and clause examples,
but Proflog does not need to adopt every surface convention of Prolog syntax.
Prefix notation can preserve the same first-order clause organization while
making Clojure interop substantially simpler: terms, formulas, definitional
helpers, and relations can be ordinary lists/forms with predictable macro
expansion instead of a separate infix parser for `=`, `!=`, `and`, `or`, `:-`,
and quantifier precedence. The surface should still be thin enough that the
program remains visible inside the parser/macro wrapper; it should not feel like
an `eval` form or like miniKanren's `run`.

Frontend language declarations should remain reusable values, matching the
backend's `proflog.language/language` boundary. A small inline language form may
be convenient sugar, but it must not be the only accepted frontend shape:
multiple source programs should be able to compile against one shared frontend
language declaration.

## Inlining Dependency

The DSL cannot be considered complete while helper-predicate inlining remains
unresolved. Users will naturally write readable factored programs, especially
in examples and at the REPL. If the frontend accepts those programs without
translation, warning, or rejection, it can silently expose the Fitting
procedure-call boundary as surprising behavior.

ADR-0010 is therefore a prerequisite for a serious DSL:

- eligible helper definitions need an explicit source-to-core inlining
  contract;
- inlining decisions must be traceable from user source to compiled formula;
- unsupported helper factoring must be rejected or warned about clearly;
- worked examples must compare the ergonomic source, translated core, and
  proof/query behavior.

The intended outcome is not to hide Proflog's semantics. It is to let users
write Proflog directly while preserving the fact that the executable program is
a Fitting-style first-order formula program, not a Prolog or miniKanren program
with different operational assumptions.

## Inlining Options

The inlining question has several possible resolutions:

- keep all helpers as ordinary Fitting procedure-call relations and warn when
  ergonomic factoring is known to change executability;
- add explicit inline helpers, where the source marks a helper as a formula
  abbreviation rather than a runtime relation;
- conservatively infer inline eligibility for nonrecursive, acyclic helpers;
- introduce two different named forms: one for definitional formula
  abbreviations and one for semantic procedure-call relations;
- support bounded recursive unfolding as an explicitly approximate profile;
- attempt proof-obligation-based inlining, where a helper is accepted only when
  the system can justify it as a definitional extension.

The current preferred direction is the two-kind predicate system, with explicit
definitional helpers as the first implementation step. The Proflog-level
definitional form should not be called `def`, because that collides mentally
with Clojure's top-level `def`. In concrete surface syntax the two forms can be
prefix operators:

```clojure
(def peano-language
  (language
    (constants zero)
    (functions (s 1))
    (relations (move 2)
               (win 1))))

(proflog peano-language
  (:= (move x y)
    (or (= x (s y))
        (= x (s (s y)))))

  (|- (win x)
    (exists [y]
      (and (move x y)
           (not (win y))))))
```

Here `:=` means source-level formula abbreviation, eligible for inlining before
`language/compile-program`, while `|-` means a real Proflog relation evaluated
through Fitting's procedure-call rule. The frontend must still be polarity-aware
when inlining calls under negation, capture-safe when substituting bodies, and
clear when recursive or otherwise unsafe helper definitions are rejected.
