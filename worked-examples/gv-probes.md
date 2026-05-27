# Worked Example: Legacy GV Probes In Greenfield

Date: 2026-04-24
Branch: `adr-0014-generic-legacy-evaluation`

This note records the first greenfield probes against the legacy
group-verifier (`GV`) family.

## Purpose

The point of these probes is not just "does greenfield have a group-theory
story?" The narrower question is:

```text
Can greenfield resolve the exact legacy-style GV formulas, and if so, through
which semidecision surface?
```

That matters because the legacy prover's documented `GV` boundary was:

- some simple group axioms close,
- precomputed associativity on `Z₂` closes,
- full 7-universal associativity on `Z₂` does not,
- and the truth-side associativity probe on the non-group magma is also
  intractable.

## Probe Surface

The exploratory runner is:

```bash
lein probe-proflog-gv
```

Source: [src/proflog/gv_probe.clj](/home/jpt4/code/proflog/src/proflog/gv_probe.clj:1)

It rebuilds the legacy `GV` formulas in the greenfield AST and reports one of
three semidecision surfaces at a time:

- `status` -> `query-status`
- `succeeds` -> `query-succeeds-within`
- `fails` -> `query-fails-within`

Example commands:

```bash
timeout -k 10s 60s lein probe-proflog-gv z2-identity status 5000
timeout -k 10s 60s lein probe-proflog-gv z2-full-assoc-truth status 15000
timeout -k 10s 60s lein probe-proflog-gv z2-full-assoc-truth succeeds 15000
timeout -k 10s 60s lein probe-proflog-gv non-group-full-assoc status 15000
```

The shell `timeout` is the outer guard. The probe timeout is the inner
greenfield query budget.

## Results

Measured on `2026-04-24`:

- `z2-identity`, `status`, `5000 ms`
  - result: `:succeeds`
  - elapsed: about `6.1 s`

- `z2-closure`, `status`, `5000 ms`
  - result: `:unresolved`
  - elapsed: about `5.1 s`

- `z2-inverses`, `status`, `5000 ms`
  - result: `:unresolved`
  - elapsed: about `6.7 s`

- `z1-full-assoc-truth`, `status`, `15000 ms`
  - result: `:unresolved`
  - elapsed: about `23.0 s`

- `z2-precomputed-assoc-truth`, `status`, `5000 ms`
  - no result before the outer `60 s` timeout

- `z2-full-assoc-truth`, `status`, `15000 ms`
  - no result before the outer `60 s` timeout

- `z2-full-assoc-truth`, `succeeds`, `15000 ms`
  - no result before the outer `60 s` timeout

- `non-group-full-assoc`, `status`, `15000 ms`
  - no result before the outer `60 s` timeout

- `non-group-full-assoc`, `fails`, `15000 ms`
  - no result before the outer `60 s` timeout

Update on `2026-04-25`:

- the repo now has a dedicated exploratory selector:
  `lein test-proflog-hard-families`
- a new host-side optimization in
  [src/proflog/equality_fast_path.clj](/home/jpt4/code/proflog/src/proflog/equality_fast_path.clj:1)
  accelerates the shape "nested existentials over an equality / disequality
  conjunction"
- that optimization now lives behind the named non-default
  [src/proflog/hard_family_overlay.clj](/home/jpt4/code/proflog/src/proflog/hard_family_overlay.clj:1)
  surface rather than inside the kernel
- under that overlay, `z1-full-assoc-truth` now resolves as `:succeeds`
  under the `2000 ms` regression budget used in
  [test/proflog/legacy_hard_families_test.clj](/home/jpt4/code/proflog/test/proflog/legacy_hard_families_test.clj:1),
  while the pure query surface remains unresolved there

Update on `2026-05-06`:

- ADR-39 adds a profiled finite equality-fragment kernel component.
- The pure query surface now resolves the mandatory GV associativity rows with
  `profiled equality-fragment` proof evidence:
  - `z1-full-assoc-truth` succeeds,
  - `z2-precomputed-assoc-truth` succeeds,
  - `z2-full-assoc-truth` succeeds,
  - `non-group-precomputed-assoc` fails, and
  - `non-group-full-assoc` fails.
- The hard-family overlay remains a named compatibility surface, but it is no
  longer the only path for these GV results.

## Historical Interpretation And Current Update

This first `GV` slice did **not** show greenfield and legacy having different
overlapping strengths at the time it was measured.

At that time, it showed:

- greenfield can resolve the simplest `GV` identity case,
- but greenfield was weaker than legacy on the broader `GV` family,
- because even legacy-solvable cases such as `Z₂` precomputed associativity and
  `Z₁` full associativity did not resolve in the measured greenfield
  windows.

That last bullet is now historical. After the generic existential equality fast
path landed, `Z1` full associativity first moved into the "resolves on a named
overlay" column. ADR-39 then superseded that boundary by adding the
proof-producing equality-fragment profile, and ADR-40/42 kept the promoted GV
and finite-verifier rows green while tightening status consistency.

After ADR-39, the current architectural takeaway is:

- greenfield now has a distinct kernel-level `GV` capability for the promoted
  finite equality associativity rows,
- `Z2` full associativity and the non-group full associativity refutation are
  no longer open GV gaps in the promoted suite,
- the named overlay is retained as a historical and compatibility boundary,
- and future hard-family work should distinguish remaining raw-stream/list
  issues from the now-promoted finite equality-fragment verifier path.

## Source To Kernel Descent

The current promoted GV rows should be read through the profiled-kernel path in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md), not through the
old named hard-family overlay.

Source-level associativity is a finite quantified Proflog proposition:

```prolog
assoc() :- forall x. forall y. forall z.
             forall xy. forall yz. forall lhs. forall rhs.
               (mul(x, y, xy)
                and mul(y, z, yz)
                and mul(xy, z, lhs)
                and mul(x, yz, rhs))
               -> lhs = rhs.
```

The generated backend program contains ordinary finite clauses for the table
lookups and a nullary `assoc/0` clause for the proposition. The query formula
is:

```clojure
(pos (app assoc))
```

`query/query-status` sends that formula to `kernel/prove-program`. When the
expanded branch is a finite equality formula with no active procedure calls
left, the equality-fragment profile can close it and returns proof evidence
under `profiled equality-fragment`.

The important parameters are the compiled finite table program, the nullary
query formula, `n = 1`, the timeout or fuel budget, and the seven universal noms
introduced by the full associativity proposition. The tests audit that the
profile does not dispatch on GV names, which is why transition-system verifier
rows can use the same component.
