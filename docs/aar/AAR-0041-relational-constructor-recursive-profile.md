# AAR-0041: Relational Constructor-Recursive Profile

Date: 2026-05-06
ADR: [ADR-0041](../adr/ADR-0041-relational-constructor-recursive-profile.md)
Status: completed

## Summary

ADR-0041 promotes constructor-recursive answer enumeration from the diagnostic
`proflog.kernel.constructor-recursive` sidecar into
`proflog.kernel.constructor-recursive-profile`. The promoted profile emits
ordinary integrated proof records shaped as:

```text
(profiled constructor-recursive ...)
```

The implementation reuses ADR-0035 structural residual continuation over
compiled guarded Proflog IR. It does not dispatch on relation names, constructor
names, or test ids, and ADR-40 Peano answer rows no longer call the old
diagnostic sidecar directly.

## What Changed

- Added `proflog.kernel.constructor-recursive-profile/query-records`.
- Added an enumerating
  `proflog.answer-overlay/continue-exported-structural-records` companion to the
  existing single-record continuation relation.
- Added `proflog.kernel.constructor-recursive-profile-test`, covering:
  - non-list Peano `peel/2`;
  - Peano `plus/3` forward, reverse, partial, no-answer, and enumeration modes;
  - list `append/3` and `reverse/2`; and
  - source audits against the old diagnostic sidecar and family dispatch.
- Updated `proflog.legacy-subsumption-test` so PA12-PA20 use the promoted profile
  rather than `constructor-recursive/query-records`.

## Verification

Focused promoted-profile namespace:

```text
timeout -k 5s 180s lein test proflog.kernel.constructor-recursive-profile-test
Ran 4 tests containing 21 assertions.
0 failures, 0 errors.
elapsed 11.32 s
```

Constructor-recursive gate:

```text
timeout -k 5s 240s lein test-proflog-constructor-recursive
Ran 10 tests containing 42 assertions.
0 failures, 0 errors.
elapsed 39.97 s
```

ADR-40 parity selector after migration:

```text
timeout -k 5s 900s lein test-proflog-legacy-subsumption
Ran 3 tests containing 63 assertions.
0 failures, 0 errors.
elapsed 50.37 s
```

Final regression gates:

```text
timeout -k 5s 900s lein test-proflog-fast
Ran 117 tests containing 381 assertions.
0 failures, 0 errors.
elapsed 106.12 s

timeout -k 5s 900s lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed 278.50 s
```

## Remaining Notes

The promoted profile is still an explicit focused profile, not the default
`query-answers` path. That preserves the ordinary answer API's current symbolic
frontier behavior while giving deep constructor-recursive answer rows integrated
profile evidence.

The old diagnostic sidecar remains useful for historical comparison and
long-timeout probes, but ADR-40 parity no longer depends on it. A future cleanup
can retire or shrink the diagnostic namespace once no probe tooling relies on its
prototype proof vocabulary.
