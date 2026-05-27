# ADR-0047: SKI Quine Evaluation

- Status: completed
- Date: 2026-05-08
- Branch: `adr-0047-ski-quine`
- AAR: [AAR-0047](../aar/AAR-0047-ski-quine-evaluation.md)

## Context

ADR-0046 added an SKI combinatory-logic demonstration with root reduction,
left-spine contextual reduction, bounded `eval-for/3`, and answer export. That
was enough for `SKK a`, `K`, `K I`, and ordinary boolean-style examples.

A standard self-reproducing SKI term is:

```text
omega = (S I I) (S I I)
```

It is not a terminating normal form. Instead, it is a positive-step loop: under
contextual SKI reduction it can reduce back to itself. With the ADR-0046
root-plus-left-spine relation, the first expansion reaches:

```text
(I (S I I)) (I (S I I))
```

Returning to the original term requires reducing the right argument of an
application as well as the function side. That is exactly the contextual
reduction intentionally left out of ADR-0046 for performance reasons.

## Decision

Keep the ADR-0046 `step/2` relation unchanged and add a separate
kernel-level `full-step/2` relation for focused examples that need
argument-position contextual reduction:

```text
full-step(before, after) :-
  step(before, after).

full-step(ap(function, argument), ap(reduced-function, argument)) :-
  full-step(function, reduced-function).

full-step(ap(function, argument), ap(function, reduced-argument)) :-
  full-step(argument, reduced-argument).
```

This is not a host evaluator. It is compiled Proflog source. The separation is
intentional: a direct attempt to add argument-position contextual reduction to
`step/2` made the existing SKI suite exceed a `900 s` guard.

Add source helpers for the `S I I` duplicator and `omega`, plus a promoted
focused test proving a positive-step self-reproduction trace:

```text
full-step(omega, expanded)
and full-step(expanded, left-contracted)
and full-step(left-contracted, omega)
```

That test demonstrates self-reproduction after a positive number of reductions,
not the trivial zero-step equality case. Direct `eval-for(3, omega, omega)` is
recorded as diagnostic evidence rather than promoted: it timed out under a
`240 s` guard before the full-context relation existed, and still timed out
under a `360 s` guard when argument-context reduction was added directly to
`step/2`.

## Exit Criteria

- Red test first shows `eval-for(3, omega, omega)` does not succeed under the
  ADR-0046 relation.
- Add right-argument context as a Proflog clause in a separate `full-step/2`
  relation.
- The quine trace test passes with procedure-call proof evidence.
- Existing SKI examples still pass.
- Source audit still rejects host-side SKI evaluators.
- Runtime and worked-example documentation record the result and the cost.
- AAR-0047 records whether the quine is viable as a regression or only a
  diagnostic.

## Non-Goals

- This ADR does not prove confluence, normalization, or arbitrary fixed-point
  behavior.
- This ADR does not add a lambda-to-SKI translator.
- This ADR does not make unbounded SKI evaluation practical.

## Risks

Fuller contextual reduction may substantially increase search. If the quine
passes but existing answer-mode rows become too slow, the right-context rule
may need to move behind a separate relation or focused selector in a later ADR.
