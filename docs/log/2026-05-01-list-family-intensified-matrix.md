# Intensified List-Family Matrix

Date: 2026-05-01
Branch: `adr-0033-structural-answer-variable-recursion`

## Purpose

After ADR-33 closed the inherited ADR-31 list-family rows, the matrix was
expanded to interrogate the same structural residual-completion path with
deeper nesting, longer lists, and multiple answer variables.

## Added Rows

Default matrix regression additions:

- `append-inverse-flat`: `append(x, y, [a,b,c])`, two answer variables and all
  four split points;
- `reverse-input-flat-longer`: `reverse(r, [c,b,a])`, a longer version of the
  carried input-synthesis row;
- `reverse-output-deep-nested-longer`:
  `reverse([[[a]],[[b]],[[c]]], r)`;
- `reverse-partial-output-longer-tail`:
  `reverse([a,b,c,a], cons(a, r))`.

Probe catalog additions outside the default regression:

- `append-inverse-flat-longer`: `append(x, y, [a,b,c,a])`, two answer variables
  and five split points, configured with `raw-limit 32`.

## Results

The default intensified matrix passed:

```text
timeout -k 10s 360s lein test proflog.list-kernel-matrix-test
```

Result:

```text
Ran 2 tests containing 23 assertions.
0 failures, 0 errors.
```

Focused probe results:

- `reverse-output-deep-nested-longer` found its target through the ordinary raw
  matrix path.
- `reverse-partial-output-longer-tail` found its target through the ordinary
  raw matrix path and is now covered by the default matrix regression.
- `reverse-input-flat-longer` found its target through the ordinary raw matrix
  path in a single-case run. The earlier timeout was from an overloaded
  parallel stress batch, not the isolated row behavior.
- `append-inverse-flat-longer` found all five split targets when run with
  `raw-limit 32`, but it is deliberately not part of the default matrix test
  because it is a heavier stress row.

## Interpretation

ADR-33's structural completion generalizes beyond the exact inherited rows:
it handles longer reverse input synthesis, deeper nested reverse output, and a
longer partial reverse output without list-symbol dispatch.

The remaining stress signal is now the length-4 inverse append row. It closes
at a higher raw limit, but is too heavy to promote into the default matrix
without making the ordinary test gate much slower.
