# AAR-0052: Unified Robinson Q3 Theory Rule

- Status: completed
- Date: 2026-05-09
- Related ADR: [ADR-0052](../adr/ADR-0052-unified-robinson-q3-theory-rule.md)

## Summary

ADR-0052 replaces the incremental Q3 branch closers in the `:robinson-q`
profile with one unified predecessor-equality rule.

The rule still requires ordinary tableau search to expose a saved nonzero
obligation:

```text
x != zero
```

When the current branch formula is a disequality and the branch has an active
proof-local universal variable `v`, the profile may use Q3 as the trusted
temporary equality:

```text
x = s(v)
```

It then walks and Q-normalizes both sides of the active disequality under that
temporary equality. The branch closes only if the two sides are already the
same. This makes direct Q3, add-one Q3, and successor-context Q3 uses one proof
mechanism instead of three syntactic shortcuts.

## What Changed

- Replaced `q3-case-splito` and `q3-predecessor-intro-closeo` with
  `q3-predecessor-equality-closeo`.
- Replaced old Q3 proof tags with one tag:
  `q3-predecessor-equality`.
- Added `rq/q3-contextual-successor-predecessor`:

```text
forall x. x != zero -> exists y. s(add(y, s(zero))) = s(x)
```

- Added regression coverage proving the contextual theorem under ordinary Q and
  under `:robinson-q`.
- Added a negative guard for the false theorem:

```text
forall x. x != zero -> exists y. x = s(s(y))
```

- Extended the Robinson Q timing probe with the contextual theorem.
- Updated current-facing documentation to describe the unified rule.

## Proof Shape

Direct Q3 now uses the same proof marker as larger Q3 consequences:

```clojure
(witness
  (conj
    (neq-store
      (once-univ
        (profiled robinson-q
          (q3-predecessor-equality
            predecessor-or-zero
            (par a_0)
            (var a_1)
            (par-bind)
            (q-convert-close
              (q-normal-par)
              (q-normal-s (q-normal-var)))))))))
```

The contextual theorem closes with the same Q3 marker, but its conversion proof
shows the outer successor context and Q5/Q4 normalization:

```clojure
(q3-predecessor-equality
  predecessor-or-zero
  (par a_0)
  (var a_1)
  (par-bind)
  (q-convert-close
    (q-normal-s
      (q-normal-add
        (q-normal-var)
        (q-normal-s (q-normal-zero))
        (q-rewrite :add-succ ...)
        (q-normal-s
          (q-normal-add
            (q-normal-var)
            (q-normal-zero)
            (q-rewrite :add-zero ...)))))
    (q-normal-s (q-normal-par))))
```

## Results

Red TDD run before implementation:

```text
lein test-proflog-robinson-q
Ran 12 tests containing 88 assertions.
15 failures, 0 errors.
real 36.46
```

Focused Robinson Q gate after implementation:

```text
lein test-proflog-robinson-q
Ran 12 tests containing 88 assertions.
0 failures, 0 errors.
real 22.24
```

Robinson Q comparison probe:

```text
lein probe-proflog-robinson-q
real 11.37
```

Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `8.389 ms` | 32 | `1957.861 ms` |
| `Q7` | 32 | `3.704 ms` | 16 | `297.713 ms` |
| `add(1, zero) = 1` | 48 | `2.491 ms` | 16 | `11.719 ms` |
| `mul(2, zero) = zero` | 48 | `3.396 ms` | 16 | `11.465 ms` |
| `add(1, 2) = 3` | 64 | `2.256 ms` | 16 | `46.196 ms` |
| `mul(2, 2) = 4` | 96 | `3.221 ms` | 16 | `232.891 ms` |
| `q3-add-one-predecessor` | 64 | `2.266 ms` | 48 | `545.513 ms` |
| `q3-contextual-successor-predecessor` | 16 | `2.035 ms` | 16 | `762.420 ms` |

Standard gates:

```text
lein test-proflog-fast
Ran 140 tests containing 502 assertions.
0 failures, 0 errors.
real 100.89

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 241.21
```

## Assessment

ADR-0052 removes the Q3-specific clutter introduced by the incremental ADR-0049
through ADR-0051 path. The profile now has one kernel-interleaved Q3 rule,
which is a better deduction-modulo fit: Q4-Q7 remain conversion rules, and Q3
is a trusted non-rewrite branch rule that supplies a predecessor equality only
when the active universal branch can immediately use it.

The rule is intentionally conservative. It does not generate arbitrary
predecessor terms and does not use post-Q3 unification to force a deeper
successor shape. The false double-predecessor guard documents that boundary.

## Remaining Limits

- This is still not a complete arithmetic decision procedure.
- Iterated predecessor proofs that require deriving new nonzero premises, such
  as proving every nonzero non-one value has a double predecessor, remain
  outside the promoted profile.
- Q3 remains a trusted theory rule of the selected `:robinson-q` proof profile,
  not a theorem derived from Q1, Q2, and Q4-Q7.
