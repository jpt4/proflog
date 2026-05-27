# AAR-0049: Robinson Q3 Case-Split Profile Rule

- Date: 2026-05-08
- Related ADR: [ADR-0049](../adr/ADR-0049-robinson-q3-case-split-profile.md)
- Outcome: complete

## What Happened

ADR-0049 extended the `:robinson-q` proof profile so Q3 proves under both
Robinson Q versions.

The ordinary version was already viable:

```clojure
(query/query-succeeds rq/ordinary-program (rq/q-implies rq/q3) 1 32)
```

The profiled version now closes the negated Q3 branch:

```text
exists x. x != zero and once-forall y. x != s(y)
```

by a structural profile rule. The proof evidence is:

```clojure
(profiled robinson-q
  ((q3-case-split predecessor-or-zero (var x) (var y)))
  (q3-close))
```

This is a theory-profile proof of Q3, not a host evaluator and not an
arithmetic rewrite.

## What Worked

The red test first showed the current profile could not prove Q3:

```text
expected: profile-proof
actual: nil
```

After adding the Q3 branch matcher and proof tag,
`lein test-proflog-robinson-q` passed with:

```text
Ran 6 tests containing 48 assertions.
0 failures, 0 errors.
wall 8.89 s
```

The comparison probe now includes Q3 and passed with `wall 7.83 s`.

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `8.527 ms` | 32 | `2.278 ms` |
| `Q7` | 32 | `2.931 ms` | 16 | `1.631 ms` |
| `add(1, zero) = 1` | 48 | `2.457 ms` | 16 | `2.394 ms` |
| `mul(2, zero) = zero` | 48 | `2.732 ms` | 16 | `0.776 ms` |
| `add(1, 2) = 3` | 64 | `2.469 ms` | 16 | `0.716 ms` |
| `mul(2, 2) = 4` | 96 | `3.089 ms` | 16 | `1.074 ms` |

The ADR-0049 commit gate ran fast and extended concurrently. Fast passed with:

```text
Ran 134 tests containing 462 assertions.
0 failures, 0 errors.
wall 73.22 s
```

Extended passed with:

```text
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
wall 200.61 s
```

## What Did Not Work

The Q3 rule is intentionally focused. It recognizes the exact refutation shape
generated when proving Q3 and returns auditable proof evidence. It does not
perform arbitrary predecessor synthesis, and it does not add Q3 as a rewrite
rule.

This keeps ADR-0048's operational caution intact: unrestricted
predecessor-or-zero case splitting can easily create irrelevant or unbounded
search.

## Follow-Up

- If future Q proofs need branch-local use of the predecessor-or-zero case
  split for formulas beyond Q3 itself, design that as a separate relevance- and
  fuel-controlled theory-rule ADR.
- Keep `q3-case-split` proof evidence separate from `q-rewrite` evidence so
  proof consumers can audit which Robinson-Q theory mechanisms were trusted.
- ADR-0050 later moved the Q3 case split from a host-side formula recognizer
  into a kernel-interleaved theory rule.
