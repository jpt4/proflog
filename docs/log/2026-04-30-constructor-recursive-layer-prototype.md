# 2026-04-30 Constructor-Recursive Layer Prototype

## Context

ADR-31 asks whether constructor-recursive residuals can be closed by a generic,
proof-producing layer analogous in spirit to the propositional and first-order
background components, without adding list-family checks to the ordinary
kernel.

## Prototype

This branch adds `proflog.kernel.constructor-recursive`, a bounded prototype
that consumes the existing guarded-clause IR:

- alternatives are freshened per call;
- equality guards are saturated by generic free-constructor unification;
- disequality guards only succeed on rigid constructor differences;
- positive defined calls inside clause bodies recurse through the same guarded
  IR;
- exported residual settlement only discharges negative defined-call
  residuals and leaves unsupported residuals explicit;
- successful steps emit `constructor-recursive-*` proof tags.

The layer does not inspect `append`, `reverse`, `cons`, `null`, or any other
relation or constructor name.

## Probe Result

The focused test namespace demonstrates the same layer across:

- a non-list Peano-style `peel/2` constructor-recursive program;
- branch-local settlement of deferred `reverse/2` and `append/3` negative
  residuals;
- multiple ADR-31 matrix rows, including append inverse splits, reverse input
  synthesis, reverse nested output synthesis, and reverse output-tail partial
  synthesis.

The matrix probe now has an opt-in mode:

```sh
lein run -m proflog.list-kernel-matrix-probe reverse-input-flat constructor-recursive
```

Example result from this branch:

```clojure
{:id :reverse-input-flat
 :layer :constructor-recursive
 :target-found? true
 :found-target-count 1
 :closed-count 1}
```

## Remaining Boundary

This is a sidecar proof layer, not a replacement for the ordinary tableau
kernel. The existing raw matrix still measures ordinary/raw behavior, and
`proflog.list-kernel-matrix-test` still timed out under a 240 second wrapper on
this branch. The prototype is therefore useful evidence that a generic guarded
constructor-recursive layer can close multiple residual families, but it is not
yet wired as a conservative branch-level dispatcher inside ordinary proof
search.
