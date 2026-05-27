# ADR-0010: Ergonomic Front End And Prover-Amenable Inlining Translation

- Status: completed
- Date: 2026-04-22
- Branch: `adr-0010-dsl-quickstart-docs`
- AAR: [AAR-0010](../aar/AAR-0010-frontend-inlining-translation.md)

## Context

ADR-0009 closed the legacy factored `move` warning as a real greenfield
regression and worked example rather than leaving it as a legacy curiosity.

The result is now explicit:

- inline Nim remains directly executable,
- ground `move/2` remains directly executable,
- but factoring the move conditions through an auxiliary `move/2` predicate
  changes `win/1` executability.

This is expected per Section 8 of [LPTableaus.pdf](/home/jpt4/code/proflog/LPTableaus.pdf):
the paper says the factored presentation does not work as expected because
auxiliary predicates such as `move` are not semantically pinned down the way
equality is.

That creates a practical language-design problem. Factored helper predicates are
an ordinary ergonomic coding pattern. Users will naturally write them for:

- local naming and readability,
- reuse across clause bodies,
- separation of concerns in larger programs,
- worked examples and teaching material.

The current prover, however, is strongest on directly inlined equality and
quantifier structure. Leaving users to discover the semantic gap case by case is
not an acceptable long-term front-end story.

One concrete example is a generic sortedness definition over a fixed comparator
relation:

```prolog
sorted(l) :-
  l = null
  or exists x.
       l = cons(x, null)
  or exists x. exists y. exists t.
       l = cons(x, cons(y, t))
       and le(x, y)
       and sorted(cons(y, t)).

le(x, y) :-
  x = zero
  or exists x1. exists y1.
       x = s(x1)
       and y = s(y1)
       and le(x1, y1).
```

This is the kind of source program users should be able to write at the front
end even when the prover ultimately needs a more inlined or otherwise
translation-mediated core representation to execute it well.

The surface-syntax assessment in
[2026-05-06-proflog-surface-dsl-and-inlining.md](../log/2026-05-06-proflog-surface-dsl-and-inlining.md)
records the follow-on design constraint: a Proflog DSL should preserve
Fitting-style first-order clause organization rather than adopting ordinary
Prolog or miniKanren semantics, use visible parser/macro source forms such as
prefix `:=` and `|-`, keep language declarations reusable across frontend
programs, and treat the inlining/refusal boundary as a prerequisite for serious
REPL and tutorial authoring.

## Decision

- Introduce a distinct front-end translation phase before prover compilation.
- The front end may accept ergonomic helper predicates and rewrite eligible
  helper calls into a prover-amenable inlined representation before the program
  reaches the semantic kernel.
- This translation is a source-level convenience layer, not a change to the
  kernel's underlying semantics.
- Inlining eligibility must be explicit and justified. The initial scope should
  be restricted to helper predicates whose bodies can be substituted
  structurally into caller clause bodies without introducing recursive semantic
  ambiguity.
- The intended front-end scope includes ordinary factored helper definitions
  such as `sorted/1` over an auxiliary comparator `le/2`, provided the
  translator can explain and justify the resulting core form.
- The translator must preserve source-to-core traceability so errors, worked
  examples, and proof explanations can still refer back to user-facing source.
- When a helper predicate is not eligible for safe translation, the front end
  should reject it or warn clearly rather than silently compiling it into a
  semantically weaker prover program.

## Implementation

ADR-0010 is implemented by `proflog.frontend`.

- `language` is a macro that builds reusable frontend language declarations.
- `proflog` is a macro that translates visible prefix source forms into
  `proflog.ast` clauses and calls `proflog.language/compile-program`.
- `q` is a macro that translates visible source queries into backend formulas.
- `answer-query` is a macro that binds visible answer variables in a query and
  returns `{:query formula :answer-vars [noms...]}` for the existing answer
  APIs.
- `run` is a macro that evaluates the same answer-variable scope through
  `proflog.answers/query-answers` so ordinary open-answer examples do not need
  destructuring boilerplate.
- `(:= head body)` introduces a nonrecursive definitional helper and is inlined
  before backend compilation.
- `(|- head body)` introduces a real kernel-visible Proflog relation clause.
- Recursive definitional helpers are rejected with source-facing helper
  identifiers in diagnostic data.

## Query-Binder Addendum

The initial completed ADR-0010 frontend left open answer queries at the backend
boundary: examples still had to allocate answer noms with `ast/nom`, build a
query with `ast/var-term`, and pass the same noms to `answers/query-answers`.
That contradicted the frontend goal once tutorials started presenting programs
through `pf/proflog`.

The accepted frontend form is:

```clojure
(pf/run program [x y]
  (append x y (cons a (cons b null))))
;; => answer records
```

`run` establishes a frontend-visible binding scope for exported answer
variables and delegates to `answers/query-answers`. The lower-level
`answer-query` form remains available when a caller needs to feed the same
query into diagnostics or another answer API:

```clojure
(let [{:keys [query answer-vars]}
      (pf/answer-query [x y]
        (append x y (cons a (cons b null))))]
  (answers/query-answers program query answer-vars opts))
```

The form deliberately complements, rather than replaces, `q`:

- `(pf/q (p a))` remains the thin closed-query formula builder.
- `(pf/answer-query [x] (p x))` is the open-query builder for answer APIs.
- `(pf/run program [x] (p x) opts)` is the ergonomic open-answer evaluator.
- Bound answer variables emit `(var ...)` terms.
- Unbound symbols continue to emit nullary `app` constants.
- Duplicate or malformed answer binding vectors are rejected at macro
  expansion.

## Consequences

- The user-facing language can stay ergonomic without forcing the kernel to
  accept arbitrary auxiliary factoring as semantically harmless.
- The semantic boundary becomes clearer:
  the prover kernel remains conservative, while the front end owns ergonomic
  rewrites.
- Worked examples such as the factored `move` warning become design inputs for
  the compiler pipeline instead of isolated cautionary notes.
- The implementation grows a new stage and a new correctness obligation:
  source-to-core translation must be testable and explainable.
- Not every factored predicate should be inlined automatically. Some helpers may
  need annotations, restrictions, or explicit refusal.

## Test Obligations

- Add a front-end regression showing the ergonomic factored Nim source
  translates to the already-supported inline Nim core form and preserves the
  expected `win(0)` / `win(1)` behavior.
- Add unit tests that reject or warn on helper predicates that are outside the
  initial inlining contract.
- Add traceability tests proving translated programs still report source-facing
  locations or identifiers in diagnostics and worked-example exports.
- Add worked examples comparing ergonomic source, translated core form, and the
  resulting proof behavior.

## Exit Criteria

- A documented front-end translation contract exists for eligible helper
  predicates.
- The repository contains at least one end-to-end translated-family regression,
  using the factored Nim warning as the canonical motivating example.
- Unsafe or unsupported helper factoring is surfaced explicitly to the user
  instead of being compiled silently.
- Worked examples show both the ergonomic source shape and the translated
  prover-facing form.
