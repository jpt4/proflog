# AAR-0050: Kernel-Interleaved Robinson Q Theory Rules

- Status: completed
- Date: 2026-05-08
- Related ADR: [ADR-0050](../adr/ADR-0050-kernel-interleaved-robinson-q-theory.md)

## Summary

ADR-0050 corrected the architecture of the `:robinson-q` profile. ADR-0048 and
ADR-0049 made Robinson Q demonstrable, but they did so by normalizing whole
formulas before proof search and by recognizing Q3 with a host-side structural
matcher. This pass replaced that with a kernel-bound theory hook.

`proflog.kernel/close-agendao` now has an optional dynamic theory-rule hook.
The default kernel behavior is unchanged when the hook is unbound. The
`:robinson-q` profile binds that hook to a miniKanren relation, then calls the
ordinary kernel relation. Q conversion and Q3 closure now fire when ordinary
proof search selects the relevant branch formula.

## What Changed

- Added `kernel/*theory-profile-closeo*` as a generic optional branch rule.
- Refactored `proflog.kernel.robinson-q-profile` from host-side whole-formula
  preprocessing into relational term normalization plus branch-close rules.
- Replaced the Q3 whole-formula recognizer with branch-state rules:
  `q3-zero-storeo` records the exposed `x != zero` obligation, and
  `q3-case-splito` closes the later `x != s(y)` branch.
- Added tests requiring Q3 proofs to include ordinary `witness`, `once-univ`,
  and `neq-store` evidence around `q3-case-split`.
- Added tests requiring Q7 proofs to include ordinary `witness` evidence around
  `q-rewrite`.
- Added a source audit rejecting the old `q-normalize-formula` and
  `q3-predecessor-refutation?` host-side proof path.

## Proof Shape

The profiled Q7 proof now has ordinary kernel quantifier work around the theory
closure:

```clojure
(witness
  (witness
    (profiled robinson-q
      (q-convert-close ... q-rewrite ...))))
```

The profiled Q3 proof now shows the branch being built by the kernel before the
Q3 theory rule closes it:

```clojure
(witness
  (conj
    (neq-store
      (once-univ
        (profiled robinson-q
          (q3-case-split predecessor-or-zero
                         (par a_0)
                         (var a_1)))))))
```

This is the key success criterion: Q is no longer hidden behind a query-time
preprocessor.

## Debug Findings

Two implementation details were important enough to record:

1. `core.logic/run` returns a lazy sequence. The first kernel-hook draft bound
   `kernel/*theory-profile-closeo*` around `run`, returned the lazy sequence,
   and therefore realized proofs after the dynamic binding had been unwound.
   The symptom was that direct calls to `robinson-q-theory-closeo` worked, but
   all profiled query proofs were empty. The fix is to `doall` the bounded proof
   sequence inside the dynamic binding.
2. A disequality such as `(!= (app s predecessor) y-out)` does not mean
   "not any successor" when `predecessor` is fresh; it can be satisfied by
   constraining that fresh variable. The Q normalizer now uses a positive
   `q-neutral-right-termo` recognizer for normalized right arguments that are
   variables, parameters, or applications whose root is not `zero` or `s`.

## Results

Focused Robinson Q gate:

```text
lein test-proflog-robinson-q
Ran 9 tests containing 64 assertions.
0 failures, 0 errors.
real 13.06
```

Robinson Q comparison probe:

```text
lein probe-proflog-robinson-q
real 10.87
```

Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `7.800 ms` | 32 | `2189.978 ms` |
| `Q7` | 32 | `2.707 ms` | 16 | `382.799 ms` |
| `add(1, zero) = 1` | 48 | `2.296 ms` | 16 | `14.692 ms` |
| `mul(2, zero) = zero` | 48 | `3.165 ms` | 16 | `10.914 ms` |
| `add(1, 2) = 3` | 64 | `2.798 ms` | 16 | `52.573 ms` |
| `mul(2, 2) = 4` | 96 | `2.580 ms` | 16 | `239.788 ms` |

Standard gates passed concurrently:

```text
lein test-proflog-fast
Ran 137 tests containing 478 assertions.
0 failures, 0 errors.
real 80.66

lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
real 197.40
```

## Assessment

ADR-0050 satisfies the architectural correction. The Q profile now behaves like
a kernel theory component: it is invoked by branch search and interleaves with
ordinary tableau rules. The proof objects show that interleaving directly.

The cost is visible. The old profile was much faster because it normalized the
formula on the host before the kernel ran. The new profile spends time inside
relational proof search. For the current Q examples, that cost is acceptable:
the focused suite remains small, and the profile is a demonstration of the
deduction-modulo architecture rather than a production arithmetic solver.

## Remaining Limits

- `q-normal-termo` is directional from a known branch term to a normal form. It
  is not a public reverse arithmetic synthesizer.
- Q3 remains a focused predecessor-or-zero branch rule. It is not a general
  predecessor synthesis procedure. The theorem-theoretic rationale for a full
  Q3 rule is recorded in
  [Robinson Q3 Full Rule Rationale](../log/2026-05-08-robinson-q3-full-rule-rationale.md).
  ADR-0051 later addressed the motivating larger refutation with
  `q3-predecessor-intro`.
- The theory hook is generic, but no second external theory profile has been
  ported to it yet.
- Performance is slower than ADR-0048/0049's host preprocessor. Future theory
  work should consider deterministic normalization inside the relation,
  congruence caching, or a proof-checked conversion certificate if larger Q
  examples become important.
