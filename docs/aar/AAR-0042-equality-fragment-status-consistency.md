# AAR-0042: Equality-Fragment Status Consistency

Date: 2026-05-06
ADR: [ADR-0042](../adr/ADR-0042-equality-fragment-status-consistency.md)
Status: completed

## Summary

ADR-0042 corrected the `warm-cool-disjoint` `query-status` result from
`:inconsistent` to `:succeeds`. The cause was not Proflog supervaluation
semantics. It was an equality-fragment proof-scoping bug: a split branch could
close by binding the same universal proof variable differently in each disjunct.

The deterministic equality-fragment engine now carries proof-variable
requirements alongside proof terms. Split proofs are accepted only when the
requirements from both branches merge against the incoming equality state. This
preserves real shared counterexamples, such as `forall x. x != red`, while
rejecting branch-local counterexamples that require incompatible witnesses.

## What Changed

- `proflog.kernel.equality-fragment/close-branch-result` returns proof terms with
  proof-variable requirements.
- Disjunction merges branch requirements before emitting
  `eq-frag-host-split`.
- Equality and disequality closure paths propagate requirements created by
  proof-variable bindings.
- `close-branch` remains the public proof-term wrapper used by
  `prove-program-host`.
- `proflog.kernel-finite-verifiers-test` now covers the `warm-cool-disjoint`
  status, direct equality-fragment failure-side non-closure, and positive
  controls where a real shared counterexample still refutes a universal formula.

## Verification

Focused ADR-0042 selector:

```text
timeout -k 5s 180s lein test :only proflog.kernel-finite-verifiers-test/equality-fragment-status-does-not-rebind-universal-witness-per-branch
Ran 1 tests containing 16 assertions.
0 failures, 0 errors.
elapsed 31.82 s
```

Promoted finite-verifier suite:

```text
timeout -k 5s 240s lein test-proflog-kernel-finite-verifiers
Ran 4 tests containing 67 assertions.
0 failures, 0 errors.
elapsed 135.63 s
```

Commit gates:

```text
timeout -k 5s 900s lein test-proflog-fast
Ran 117 tests containing 381 assertions.
0 failures, 0 errors.
elapsed 85.00 s

timeout -k 5s 900s lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed 231.52 s
```

Post-ADR-0041 confirmation:

```text
timeout -k 5s 240s lein test-proflog-kernel-finite-verifiers
Ran 4 tests containing 67 assertions.
0 failures, 0 errors.
elapsed 113.40 s
```

An nREPL probe also measured the corrected public status:

```text
(query/query-status (fitting/finite-domain-program)
                    (ast/pos-lit (ast/app-term 'warm-cool-disjoint))
                    {:timeout-ms 20000 :proof-limit 1 :poll-ms 0})
;; => :succeeds
;; elapsed 526.023196 ms
```

## Remaining Notes

The equality-fragment profile now declines the unsound failure proof quickly, but
the ordinary public `query-fails` fallback can still be expensive at larger fuel
slices. Tests therefore assert public `query-status` for the user-visible result
and inspect `prove-program-host` directly for the profile-specific non-closure.

This leaves no known finite-domain status inconsistency from ADR-40. It does not
claim that every public high-fuel negative probe is cheap.
