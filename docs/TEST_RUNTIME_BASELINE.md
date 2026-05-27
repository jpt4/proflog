# Test Runtime Baseline

Date: 2026-04-23
Branch: `adr-0009-legacy-program-closure`

This document records the duration of the final successful iteration used to
promote a test into the committed greenfield suite. Timings are intentionally
kept as observed wall-clock measurements from the exact successful run that
justified the test.

The reverse/append answer-mode entries below are historical timings from the
pre-ADR-0011 hybrid staging policy. ADR-0011 later moved the default path to
direct kernel entry-call descent and remapped the relevant stage numbers; those
entries are kept here as branch-local runtime history until the new policy is
re-baselined explicitly.

Historical post-ADR-0011 notes from the direct-entry / completion-ranked path:

These notes predate ADR-0013 and ADR-0035. They are retained to explain why
later answer-overlay and residual-continuation work happened; they are not
current public `query-answers` capability claims.

- Nested suffix `append([[a,b]], z, [[a,b],[c]])` does not recover the concrete
  suffix at raw caps `8`, `16`, or `32`, but a longer exploratory probe showed
  the concrete answer surfacing first at `max-raw-proof-limit 64`.
- `reverse([a,b], r)` remained materially harder: a `>120 s` exploratory probe
  at `fuel 64`, `call-depth 3`, and raw budgets up to `64` still did not return
  an exported result slice before manual stop.

Current ADR-0013 note:

- The public `query-answers` surface for the known list-family `append/3` and
  `reverse/2` queries now reuses the ADR-0012 closed-answer materializer. The
  older reverse/append rows below therefore describe the pre-ADR-0013 raw
  symbolic behavior and timings, not the current public closed-answer surface.

Current ADR-0035 note:

- The promoted list-kernel matrix now reaches all catalog targets through the
  ordinary probe path under longer wrappers. The full sweep is recorded in
  [2026-05-03-list-kernel-matrix-long-timeout-sweep.md](log/2026-05-03-list-kernel-matrix-long-timeout-sweep.md).
  The practical default gate remains narrower because one row,
  `append-inverse-flat-longer`, took about `509.5 s` of Clojure-process
  elapsed time.

Current ADR-0040 note:

- The focused legacy-subsumption selector passed on 2026-05-06 with
  `Ran 3 tests containing 63 assertions`, `0 failures, 0 errors`, and
  `elapsed 120.54 s`.
- Passing per-row timings are recorded in
  [AAR-0040](aar/AAR-0040-legacy-subsumption-parity.md). The expensive row is
  the direct kernel Peano proof `PA10 forward 3 + 4 = 7` at `70586.114 ms`.
  Peano answer-mode parity rows use the constructor-recursive profile and close
  in milliseconds.

Current ADR-0042 note:

- The focused equality-fragment status selector passed on 2026-05-06 with
  `Ran 1 tests containing 16 assertions`, `0 failures, 0 errors`, and
  `elapsed 31.82 s`.
- The full kernel finite verifier suite passed after the proof-scoping fix with
  `Ran 4 tests containing 67 assertions`, `0 failures, 0 errors`, and
  `elapsed 135.63 s`.
- The ADR-42 commit gate also passed `lein test-proflog-fast` with
  `Ran 117 tests containing 381 assertions`, `0 failures, 0 errors`, and
  `elapsed 85.00 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 231.52 s`.
- After ADR-41 landed on top, `lein test-proflog-kernel-finite-verifiers` was
  rerun with `Ran 4 tests containing 67 assertions`, `0 failures, 0 errors`, and
  `elapsed 113.40 s`.

Current ADR-0057 note:

- The focused relation-backed equality-fragment selector passed on 2026-05-09
  with `Ran 5 tests containing 32 assertions`, `0 failures, 0 errors`, and
  `real 82.97 s` in an isolated run. The final concurrent commit-gate rerun
  passed with the same assertions and `real 198.56 s`.
- `lein probe-proflog-relational-equality-fragment` passed with `real 106.21 s`.
  The slowest relation-backed row was
  `nondeterministic-delta-deterministic` at `60515.029 ms`; all ADR-0039 GV and
  transition rows produced `profiled relational-equality-fragment` evidence.
- The final ADR-0057 gate also reran the existing finite verifier suite
  (`real 221.58 s`), Fitting program suite (`real 172.93 s`), fast suite
  (`real 196.31 s`), and extended suite (`real 319.86 s`), all with `0`
  failures and `0` errors.

Current ADR-0060 note:

- The focused Willard SJAS MVP selector `lein test-proflog-sjas` passed on
  2026-05-10 with `Ran 9 tests containing 61 assertions`, `0 failures`,
  `0 errors`, and `real 30.95 s`.
- The focused bounded-quantifier NNF selector
  `lein test :only proflog.normalize-test/to-nnf-lowers-sjas-bounded-quantifiers-through-leq-guards`
  passed with `Ran 1 tests containing 8 assertions`, `0 failures`, `0 errors`,
  and `real 6.75 s`.
- The focused frontend clause-emission selector
  `lein test :only proflog.frontend-test/frontend-can-emit-clauses-for-higher-level-builders`
  passed with `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  and `real 17.47 s`.
- The suite includes profile-language shape, Delta-star-0 / Pi-star-1 /
  Sigma-star-1 classifiers, generated Group-Zero through Group-3 metadata,
  reflected versus external clause boundary checks, source-facing builder
  lowering, finite `mult/3` forward and answer-mode examples, miniature
  certificate acceptance/rejection, selected profile proof evidence,
  source-route audit, and a Level-1 bounded contradiction probe at fuel `4`.
- The ADR-0060 commit gate passed `lein test-proflog-fast` with `Ran 145 tests
  containing 548 assertions`, `0 failures`, `0 errors`, and `real 89.21 s`,
  plus `lein test-proflog-extended` with `Ran 68 tests containing 203
  assertions`, `0 failures`, `0 errors`, and `real 255.14 s`. The extended
  gate was run concurrently with the earlier source-level fast gate; the final
  fast rerun was serial after adding the direct frontend regression.

Current ADR-0061 follow-up note:

- After adding reflected/external SJAS query-triggered examples and restoring
  ordinary procedure-call lookup for SJAS-annotated compiled programs,
  `lein test-proflog-sjas` passed on 2026-05-14 with `Ran 11 tests containing
  112 assertions`, `0 failures`, `0 errors`, and `real 15.40 s`.
- The same gate reran `lein test-proflog-fast` with `Ran 145 tests containing
  548 assertions`, `0 failures`, `0 errors`, and `real 71.06 s`, plus
  `lein test-proflog-extended` with `Ran 68 tests containing 203 assertions`,
  `0 failures`, `0 errors`, and `real 198.45 s`.
- The focused SJAS composite beta-versus-reflected examples passed with
  `lein test-proflog-sjas`, `Ran 12 tests containing 119 assertions`,
  `0 failures`, `0 errors`, and `real 34.17 s`.

Current ADR-0062 note:

- The initial non-vacuity red test failed because `SelfCons0` mentioned
  `contradiction-code` but the generated program had no proof target for that
  code. The targeted red run was
  `lein test :only proflog.willard-sjas-test/sjas-system-builder-generates-groups-and-reflected-boundary`,
  with `real 11.36 s`.
- After mapping `contradiction-code` to the theorem target for `false`, adding
  complement targets for `not-code(c)`, and extending proof-certificate encoding
  for nested generic kernel profile tags, `lein test-proflog-sjas` passed with
  `Ran 13 tests containing 125 assertions`, `0 failures`, `0 errors`, and
  `real 33.95 s`.
- The ADR-0062 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 100.47 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 227.65 s`.

Current ADR-0063 note:

- The focused SJAS arithmetized-coding gate `lein test-proflog-sjas` passed on
  2026-05-14 with `Ran 15 tests containing 143 assertions`, `0 failures`,
  `0 errors`, and `elapsed 4:47.84` while run in parallel with the fast and
  extended gates.
- The same gate had earlier isolated semantic verification at `elapsed 3:34.90`;
  the longer recorded gate above is the commit-reference timing because it ran
  under the final concurrent regression load.
- The ADR-0063 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `elapsed 2:07.41`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `elapsed 4:37.84`.

Current ADR-0064 note:

- The targeted red run for the SJAS substitution-proof predicate failed before
  implementation because `sjas/subst-prf` was undefined.
- After adding `subst-prf/4`, identity-substitution entries, and the profile
  branch closer, the focused selectors passed:
  `sjas-profile-languages-have-binary-u-grounding-shape` with `Ran 1 tests
  containing 36 assertions`, `0 failures`, `0 errors`;
  `sjas-level1-group-three-uses-substitution-proof-vocabulary` with `Ran 1 tests
  containing 3 assertions`, `0 failures`, `0 errors`; and
  `sjas-subst-prf-checks-identity-substitution-certificates` with `Ran 1 tests
  containing 4 assertions`, `0 failures`, `0 errors`.
- The focused SJAS substitution-proof gate `lein test-proflog-sjas` passed on
  2026-05-14 with `Ran 17 tests containing 152 assertions`, `0 failures`,
  `0 errors`, and `real 299.59 s`.
- The ADR-0064 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 96.78 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 219.78 s`.

Current ADR-0065 note:

- The structural fixed-point red test showed that Level-1 Group-3 used
  `system-code` rather than `selfcons-skeleton-code`; after implementation,
  `sjas-level1-group-three-uses-selfcons-skeleton-code` passed.
- A direct `sjas-axiom` certificate red test failed before implementation with
  `Unsupported proof symbol in SJAS certificate {:symbol sjas-axiom}` and
  `real 11.26 s`.
- An attempted generic Level-1 Group-3 proof was stopped after about `7m44s`
  without a result. The corrected route uses a formal axiom-citation proof
  checked through generated `axiom-member/2` facts.
- Focused post-implementation timings:
  `sjas-tableau-proof-accepts-axiom-citation-certificates` passed with
  `Ran 1 tests containing 2 assertions`, `0 failures`, `0 errors`,
  `real 46.28 s`; `sjas-subst-prf-checks-identity-substitution-certificates`
  passed with `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 87.70 s`; and
  `sjas-subst-prf-checks-selfcons-fixed-point-certificate` passed with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 72.66 s`.
- The explicit slow selector `lein test-proflog-sjas-slow` passed with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 82.81 s`.
- The focused SJAS fixed-point gate `lein test-proflog-sjas` passed with
  `Ran 20 tests containing 162 assertions`, `0 failures`, `0 errors`,
  `real 406.83 s`.
- The ADR-0065 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 93.95 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 222.85 s`.

Current ADR-0066 note:

- The targeted red run for the finite SJAS substitution relation failed before
  implementation because `sjas/subst-code` was undefined:
  `lein test :only proflog.willard-sjas-test/sjas-subst-code-relates-generated-substitution-codes`,
  `real 12.05 s`.
- Focused post-implementation timings:
  `sjas-subst-code-relates-generated-substitution-codes` passed with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 44.10 s`;
  `sjas-subst-prf-uses-substitution-code-independently-of-theorem-code` passed
  with `Ran 1 tests containing 1 assertions`, `0 failures`, `0 errors`,
  `real 25.84 s`; and
  `sjas-subst-prf-checks-identity-substitution-certificates` passed with
  `Ran 1 tests containing 5 assertions`, `0 failures`, `0 errors`,
  `real 188.78 s`.
- The explicit slow selector `lein test-proflog-sjas-slow` passed with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 91.34 s`.
- The focused SJAS substitution-relation gate `lein test-proflog-sjas` passed
  with `Ran 22 tests containing 169 assertions`, `0 failures`, `0 errors`,
  `real 561.14 s`.
- The ADR-0066 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 97.61 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 225.08 s`.

Current ADR-0067 note:

- The targeted structural code-decoder red run failed because the code for the
  valid non-generated formula `lt(1,2)` was absent from the generated SJAS
  formula registry:
  `lein test :only proflog.willard-sjas-test/sjas-structural-code-predicates-accept-non-generated-formula-codes`,
  `Ran 1 tests containing 5 assertions`, `4 failures`, `0 errors`,
  `real 14.80 s`.
- After implementing the structural formula-code byte decoder, the same focused
  selector passed with `Ran 1 tests containing 5 assertions`, `0 failures`,
  `0 errors`, `real 98.85 s`.
- Nearby focused regression selectors passed:
  `sjas-syntax-predicates-decode-formula-godel-codes` with `Ran 1 tests
  containing 6 assertions`, `real 23.44 s`;
  `sjas-subst-code-relates-generated-substitution-codes` with `Ran 1 tests
  containing 3 assertions`, `real 55.64 s`; and
  `sjas-subst-prf-uses-substitution-code-independently-of-theorem-code` with
  `Ran 1 tests containing 1 assertions`, `real 33.44 s`.
- The explicit slow selector `lein test-proflog-sjas-slow` passed with
  `Ran 2 tests containing 8 assertions`, `0 failures`, `0 errors`,
  `real 170.85 s`.
- The focused SJAS structural-decoder gate `lein test-proflog-sjas` passed with
  `Ran 23 tests containing 174 assertions`, `0 failures`, `0 errors`,
  `real 767.20 s`.
- The ADR-0067 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 129.36 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 299.24 s`.

Current ADR-0068 note:

- The targeted structural theorem-code red run for `tableau-proof/3` failed
  because `code(lt(1,2))` was not in the generated formula registry:
  `lein test :only proflog.willard-sjas-test/sjas-tableau-proof-checks-structural-non-generated-theorem-codes`,
  `Ran 1 tests containing 4 assertions`, `1 failures`, `0 errors`,
  `real 65.90 s`.
- The analogous red run for `subst-prf/4` failed before the structural theorem
  target route was added there:
  `lein test :only proflog.willard-sjas-test/sjas-subst-prf-checks-structural-non-generated-theorem-codes`,
  `Ran 1 tests containing 4 assertions`, `1 failures`, `0 errors`,
  `real 19.75 s`.
- Focused post-implementation selectors passed:
  `sjas-tableau-proof-checks-structural-non-generated-theorem-codes` with
  `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 111.13 s`; and
  `sjas-subst-prf-checks-structural-non-generated-theorem-codes` with
  `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 175.84 s`.
- The explicit slow selector `lein test-proflog-sjas-slow` passed with
  `Ran 4 tests containing 16 assertions`, `0 failures`, `0 errors`,
  `real 452.96 s`.
- The focused SJAS structural-theorem gate `lein test-proflog-sjas` passed with
  `Ran 25 tests containing 182 assertions`, `0 failures`, `0 errors`,
  `real 1947.15 s`.
- The ADR-0068 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 129.32 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 287.48 s`.

Current ADR-0069 note:

- The targeted general-substitution red run failed as intended:
  `lein test :only proflog.willard-sjas-test/sjas-subst-code-computes-general-formula-code-substitution`,
  `Ran 1 tests containing 6 assertions`, `3 failures`, `0 errors`,
  `real 71.95 s`. The failures showed generated substitution entries were
  still present, the non-identity `wff(v0)` substitution did not work, and the
  open-formula identity fallback was unsound.
- Focused post-implementation selectors passed:
  `sjas-subst-code-computes-general-formula-code-substitution` with
  `Ran 1 tests containing 6 assertions`, `0 failures`, `0 errors`,
  `real 166.54 s`; `sjas-subst-code-relates-structural-substitution-codes`
  with `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 222.93 s`; `sjas-subst-prf-uses-substitution-code-independently-of-theorem-code`
  with `Ran 1 tests containing 1 assertions`, `0 failures`, `0 errors`,
  `real 194.00 s`; `sjas-subst-prf-checks-identity-substitution-certificates`
  with `Ran 1 tests containing 5 assertions`, `0 failures`, `0 errors`,
  `real 335.45 s`; and
  `sjas-subst-prf-checks-selfcons-fixed-point-certificate` with `Ran 1 tests
  containing 3 assertions`, `0 failures`, `0 errors`, `real 434.16 s`.
- Diagnostic split timing for the fixed-point certificate showed
  `tableau-proof` at about `27.16 s`, valid skeleton `subst-prf` at about
  `205.88 s`, and invalid system-code rejection at about `187.58 s`. This is
  the expected cost of structural source-code decoding without generated
  substitution entries.
- The explicit slow selector `lein test-proflog-sjas-slow` passed with
  `Ran 5 tests containing 22 assertions`, `0 failures`, `0 errors`,
  `real 915.85 s`.
- The full SJAS gate `lein test-proflog-sjas` passed with `Ran 26 tests
  containing 188 assertions`, `0 failures`, `0 errors`, `real 2057.15 s`.
- The ADR-0069 regression gates passed with `lein test-proflog-fast`,
  `Ran 145 tests containing 548 assertions`, `0 failures`, `0 errors`,
  `real 143.16 s`, and `lein test-proflog-extended`, `Ran 68 tests containing
  203 assertions`, `0 failures`, `0 errors`, `real 349.26 s`.

Current ADR-0071 note:

- The first U-Grounding code-format red run failed before the encoder existed:
  `lein test :only proflog.willard-sjas-test/sjas-u-grounding-code-format-emits-numeral-codes-without-code-constructors`,
  `No such var: sjas-code/bytes->u-grounding-code-term`, `real 12.02 s`.
- A later proof-evidence audit red run showed the non-ground fallback was
  extensionally succeeding without preserving the `byte + 64 * tail` evidence:
  `sjas-u-grounding-bound-code-decoding-uses-byte-cons-relation` failed with
  `Ran 1 tests containing 3 assertions`, `2 failures`, `0 errors`,
  `real 22.97 s`.
- Focused post-implementation selectors passed:
  `sjas-u-grounding-code-format-emits-numeral-codes-without-code-constructors`
  with `Ran 1 tests containing 6 assertions`, `0 failures`, `0 errors`,
  `real 18.80 s`;
  `sjas-u-grounding-codes-preserve-trailing-zero-byte-sequences` with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 18.88 s`;
  `sjas-u-grounding-syntax-predicates-decode-numeral-codes` with
  `Ran 1 tests containing 6 assertions`, `0 failures`, `0 errors`,
  `real 177.65 s`;
  `sjas-u-grounding-bound-code-decoding-uses-byte-cons-relation` with
  `Ran 1 tests containing 3 assertions`, `0 failures`, `0 errors`,
  `real 38.18 s`;
  `sjas-u-grounding-tableau-proof-checks-numeral-system-theorem-and-proof-codes`
  with `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 20.27 s`; and
  `sjas-u-grounding-subst-code-computes-level1-fixed-point` with
  `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 20.04 s`.
- A full-gate regression exposed a stale compact-code contradiction target in
  the inconsistent-basis self-consistency demonstration. After switching that
  check to the system's `:contradiction-code`, the focused selector
  `sjas-selfcons-demonstration-uses-substantive-proof-targets` passed with
  `Ran 1 tests containing 4 assertions`, `0 failures`, `0 errors`,
  `real 138.76 s`.
- The final ADR-0071 gates passed with `lein test-proflog-sjas-slow`,
  `Ran 5 tests containing 22 assertions`, `0 failures`, `0 errors`,
  `real 722.20 s`; `lein test-proflog-sjas`, `Ran 35 tests containing
  221 assertions`, `0 failures`, `0 errors`, `real 1717.35 s`;
  `lein test-proflog-fast`, `Ran 145 tests containing 548 assertions`,
  `0 failures`, `0 errors`, `real 120.25 s`; and
  `lein test-proflog-extended`, `Ran 68 tests containing 203 assertions`,
  `0 failures`, `0 errors`, `real 254.93 s`.

Current LOPSTR+PPDP system-description verification note:

- On 2026-05-18, while preparing the LOPSTR+PPDP 2026 SJAS system-description
  draft, the focused SJAS gates were rerun against main. `lein
  test-proflog-sjas-slow` passed with `Ran 5 tests containing 22 assertions`,
  `0 failures`, `0 errors`, and `real 746.91 s`. `lein test-proflog-sjas`
  passed with `Ran 35 tests containing 221 assertions`, `0 failures`,
  `0 errors`, and `real 1687.83 s`. The paired standard gates also passed:
  `lein test-proflog-fast` with `Ran 145 tests containing 548 assertions`,
  `0 failures`, `0 errors`, and `real 83.45 s`; `lein test-proflog-extended`
  with `Ran 68 tests containing 203 assertions`, `0 failures`, `0 errors`,
  and `real 195.65 s`.

Current ADR-0041 note:

- The promoted constructor-recursive profile namespace passed on 2026-05-06 with
  `Ran 4 tests containing 21 assertions`, `0 failures, 0 errors`, and
  `elapsed 11.32 s`.
- The constructor-recursive gate, now including the promoted profile tests,
  passed with `Ran 10 tests containing 42 assertions`, `0 failures, 0 errors`,
  and `elapsed 39.97 s`.
- The ADR-40 legacy-subsumption selector passed after migrating Peano answer rows
  to the promoted profile with `Ran 3 tests containing 63 assertions`,
  `0 failures, 0 errors`, and `elapsed 50.37 s`.
- The ADR-41 final commit gate passed `lein test-proflog-fast` with
  `Ran 117 tests containing 381 assertions`, `0 failures, 0 errors`, and
  `elapsed 106.12 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 278.50 s`.

Current query-status boundary note:

- A red characterization pass on 2026-05-06 wired the new inconsistent-status
  assertion to an ordinary compiled `p(0)` program; it failed with
  `actual: :succeeds` and `elapsed 19.65 s`.
- The final characterization test
  `proflog.query-test/query-status-can-report-inconsistent-for-unsound-compiled-program`
  passed with `Ran 1 tests containing 3 assertions`, `0 failures, 0 errors`,
  and `elapsed 15.12 s`.
- The commit gate passed `lein test-proflog-fast` with
  `Ran 118 tests containing 384 assertions`, `0 failures, 0 errors`, and
  `elapsed 74.65 s`.

Current ADR-0043 documentation-refresh note:

- Historical exploratory runtime rows are now labelled as historical rather
  than current capability boundaries. The post-ADR-35 sweep is the current
  reachability reference for the raw list-kernel matrix.
- The current focused reverse answer row
  `proflog.answers-test/query-answers-use-call-depth-1-to-refine-the-direct-reverse-frontier`
  passed with `Ran 1 tests containing 2 assertions`, `0 failures, 0 errors`,
  and `elapsed 14.06 s`.
- The current focused inverse append row
  `proflog.answers-test/query-answers-prefer-the-first-concrete-inverse-append-split-over-symbolic-frontiers`
  passed with `Ran 1 tests containing 2 assertions`, `0 failures, 0 errors`,
  and `elapsed 10.91 s`.
- The ADR-43 commit gate passed `lein test-proflog-fast` with
  `Ran 118 tests containing 384 assertions`, `0 failures, 0 errors`, and
  `elapsed 67.73 s`.

Current ADR-0010 frontend note:

- The focused frontend selector passed on 2026-05-06 with
  `Ran 6 tests containing 21 assertions`, `0 failures, 0 errors`, and
  `elapsed 12.25 s`.
- The ADR-0010 frontend commit gate passed `lein test-proflog-fast` with
  `Ran 124 tests containing 405 assertions`, `0 failures, 0 errors`, and
  `elapsed 74.84 s`.
- The worked-example descent enrichment pass on 2026-05-06 reran both standard
  greenfield gates. `lein test-proflog-fast` passed with
  `Ran 124 tests containing 405 assertions`, `0 failures, 0 errors`, and
  `elapsed 75.22 s`. `lein test-proflog-extended` passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 204.88 s`.
- The tutorial refresh on 2026-05-07 reran both standard greenfield gates after
  documenting the ADR-0010 frontend, the then-open answer-query binder boundary,
  and ADR-0041 constructor-recursive profile.
  `lein test-proflog-fast` passed with
  `Ran 124 tests containing 405 assertions`, `0 failures, 0 errors`, and
  `elapsed 99.12 s`. `lein test-proflog-extended` passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 265.39 s`.
- The ADR-0010 answer-query binder pass on 2026-05-07 first failed red with
  `No such var: pf/answer-query` in `9.77 s`. After implementation,
  `lein test proflog.frontend-test` passed with
  `Ran 8 tests containing 27 assertions`, `0 failures, 0 errors`, and
  `elapsed 14.21 s`. The commit gate passed `lein test-proflog-fast` with
  `Ran 126 tests containing 411 assertions`, `0 failures, 0 errors`, and
  `elapsed 81.60 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 215.41 s`.
- The ADR-0010 `pf/run` answer-evaluator pass on 2026-05-07 first failed red
  with `No such var: pf/run` in `10.33 s`. After implementation,
  `lein test proflog.frontend-test` passed with
  `Ran 10 tests containing 30 assertions`, `0 failures, 0 errors`, and
  `elapsed 26.96 s`. The commit gate passed `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 180.26 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 610.08 s`. The fast and extended gates were run concurrently, so
  these wall times include resource contention from the paired run.
- The ADR-0010 worked-example and source-comment pass on 2026-05-07 kept the
  same test surface while making `pf/run` the default open-answer example form
  and adding reader-facing comments to the frontend / AST / language compiler
  layer. `lein test proflog.frontend-test` passed with
  `Ran 10 tests containing 30 assertions`, `0 failures, 0 errors`, and
  `elapsed 12.36 s`. The commit gate passed `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 73.39 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 206.75 s`. The fast and extended gates were run concurrently.

Current ADR-0044 Turing-completeness note:

- Red TDD check on 2026-05-07 failed before implementation with
  `Could not locate proflog/turing_completeness` and `elapsed 9.34 s`.
- The explicit opt-in TC suite is `lein test-proflog-turing-completeness`; it
  is not part of `test-proflog-fast` or `test-proflog-extended`.
- The full TC suite passed with
  `Ran 6 tests containing 13 assertions`, `0 failures, 0 errors`, and
  `elapsed 68.64 s` after adding the long-probe identifier smoke test.
- The ADR-0044 commit gate also passed `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 85.62 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 227.20 s`. The fast and extended gates were run concurrently.
- Focused passing rows: step branch proofs `31.46 s`, second instruction-table
  bounded run `26.51 s`, frontend transfer answer export `73.66 s`, frontend
  instruction partial synthesis `21.68 s`, and source audit `9.85 s`.
- Exploratory runtime boundaries retained for future search-control work:
  transfer `halts-in-steps` probes for sampled multi-step transfers timed out
  inside `180 s` wrappers, direct ground three-step transfer trace timed out
  inside a `180 s` wrapper, and open one-step predecessor synthesis over
  `step/2` timed out inside a `180 s` wrapper. These were not promoted to the
  passing suite.
- Follow-up long probes on 2026-05-07 refined those boundaries:
  `recursive-transfer-3-steps` succeeded with one proof in `elapsed 783.72 s`;
  `open-predecessor-step` returned four answer records in `elapsed 645.66 s`;
  `direct-ground-three-step-trace` produced no proof before controlled stop at
  about thirty minutes; and `recursive-transfer-5-steps` timed out with status
  `124` after a `1800 s` wrapper. See
  [2026-05-07 ADR-0044 long Turing probes](log/2026-05-07-adr44-long-turing-probes.md).
- The follow-up commit gate passed `lein test-proflog-turing-completeness` with
  `Ran 6 tests containing 13 assertions`, `0 failures, 0 errors`, and
  `elapsed 68.64 s`; `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 60.67 s`; and `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 184.10 s`.

Current ADR-0045/0046 Turing-completeness performance note:

- ADR-0045 added a trace-shaped formula helper for known finite Minsky runs.
  The helper builds a conjunction of compiled `step/2` calls and optional
  `halt-config/1`; it does not execute a machine transition on the host.
- The direct recursive five-step transfer remained non-viable in ADR-0044:
  `recursive-transfer-5-steps` timed out after a `1800 s` wrapper. The
  trace-shaped five-step transfer now closes through the kernel with
  `Ran 1 tests containing 1 assertions`, `0 failures, 0 errors`, and
  `elapsed 58.89 s`.
- The full ADR-0045 namespace passed with
  `Ran 2 tests containing 4 assertions`, `0 failures, 0 errors`, and
  `elapsed 55.02 s`. A post-docstring focused alias rerun passed with the same
  assertion count and `elapsed 47.70 s`.
- ADR-0046 added an independent SKI combinatory-logic TC demonstration. Its
  focused rows passed with these timings: root reductions `31.33 s`, `SKK a`
  bounded evaluation `44.29 s`, boolean true `20.09 s`, boolean false
  `45.29 s`, answer-mode `SKK a` export `206.87 s`, and source audit
  `15.80 s`.
- The full ADR-0046 namespace passed with
  `Ran 6 tests containing 12 assertions`, `0 failures, 0 errors`, and
  `elapsed 225.50 s`.
- The aggregate `lein test-proflog-turing-completeness` selector now includes
  ADR-0044, ADR-0045, and ADR-0046. It passed with
  `Ran 14 tests containing 29 assertions`, `0 failures, 0 errors`, and
  `elapsed 328.17 s`. A post-merge completion-audit rerun on `main` passed with
  the same assertion count and `elapsed 308.91 s`.
- The ADR-0045/0046 commit gate passed `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 69.66 s`, plus `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 195.92 s`.

Current ADR-0047 SKI quine note:

- Direct `eval-for(3, omega, omega)` was attempted first against the ADR-0046
  relation and timed out inside a `240 s` wrapper.
- Adding argument-position contextual reduction directly to `step/2` made the
  focused quine trace pass in `37.13 s`, but made the full SKI suite time out
  inside a `900 s` wrapper. That implementation was rejected.
- The accepted design keeps ADR-0046 `step/2` unchanged and adds a separate
  `full-step/2` relation for focused full-context traces.
- The focused quine trace
  `ski-omega-quine-reproduces-itself-through-a-guided-trace` passed with
  `Ran 1 tests containing 1 assertions`, `0 failures, 0 errors`, and
  `elapsed 95.44 s`.
- The full SKI selector `lein test-proflog-combinatory-logic` passed after the
  quine addition with `Ran 7 tests containing 13 assertions`,
  `0 failures, 0 errors`, and `elapsed 301.98 s`.
- The aggregate `lein test-proflog-turing-completeness` selector passed after
  the quine addition with `Ran 15 tests containing 30 assertions`,
  `0 failures, 0 errors`, and `elapsed 438.34 s`.
- The ADR-0047 fast gate passed `lein test-proflog-fast` with
  `Ran 128 tests containing 414 assertions`, `0 failures, 0 errors`, and
  `elapsed 96.41 s`.
- The ADR-0047 extended gate passed `lein test-proflog-extended` with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `elapsed 237.72 s`.

Current ADR-0055 SKI routing note:

- The red route check first failed in `9.59 s` because the previous SKI test
  helpers entered `query/query-succeeds` and `answers/query-answers`.
- The promoted route check
  `ski-evaluation-does-not-route-through-public-or-profiled-shortcuts` passed
  with `Ran 1 tests containing 5 assertions`, `0 failures, 0 errors`, and
  `real 29.14 s`.
- The promoted answer row
  `ski-answer-mode-exports-a-reduced-term` passed with
  `Ran 1 tests containing 2 assertions`, `0 failures, 0 errors`, and
  `real 49.32 s`.
- The full SKI selector `lein test-proflog-combinatory-logic` passed with
  `Ran 8 tests containing 18 assertions`, `0 failures, 0 errors`, and
  `real 176.02 s`.
- The aggregate `lein test-proflog-turing-completeness` selector passed with
  `Ran 16 tests containing 35 assertions`, `0 failures, 0 errors`, and
  `real 273.27 s`.

Current ADR-0048 Robinson Q note:

- Red TDD check on 2026-05-08 failed before implementation with
  `Could not locate proflog/robinson_q` and `Tests failed`.
- The focused Robinson Q selector passed with
  `Ran 5 tests containing 42 assertions`, `0 failures, 0 errors`, and
  `wall 8.94 s`.
- The focused language/frontend/query regression selector passed after adding
  proof-profile dispatch with `Ran 22 tests containing 68 assertions`,
  `0 failures, 0 errors`, and `wall 25.54 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `wall 7.82 s`. Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q7` | 32 | `7.639 ms` | 16 | `2.144 ms` |
| `add(1, zero) = 1` | 48 | `2.064 ms` | 16 | `2.899 ms` |
| `mul(2, zero) = zero` | 48 | `2.855 ms` | 16 | `1.487 ms` |
| `add(1, 2) = 3` | 64 | `3.283 ms` | 16 | `1.234 ms` |
| `mul(2, 2) = 4` | 96 | `4.675 ms` | 16 | `1.451 ms` |
- The ADR-0048 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 133 tests containing 456 assertions`, `0 failures, 0 errors`, and
  `wall 75.27 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `wall 197.59 s`.

Current ADR-0049 Robinson Q3 note:

- Red TDD check on 2026-05-08 proved the boundary: ordinary Q3 proof existed,
  but the profile proof was `nil`, producing 4 focused failures in
  `proflog.robinson-q-test/q3-is-proved-by-ordinary-assumptions-and-profile-case-split`.
- After adding the `q3-case-split` profile rule, `lein test-proflog-robinson-q`
  passed with `Ran 6 tests containing 48 assertions`, `0 failures, 0 errors`,
  and `wall 8.89 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `wall 7.83 s`. Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `8.527 ms` | 32 | `2.278 ms` |
| `Q7` | 32 | `2.931 ms` | 16 | `1.631 ms` |
| `add(1, zero) = 1` | 48 | `2.457 ms` | 16 | `2.394 ms` |
| `mul(2, zero) = zero` | 48 | `2.732 ms` | 16 | `0.776 ms` |
| `add(1, 2) = 3` | 64 | `2.469 ms` | 16 | `0.716 ms` |
| `mul(2, 2) = 4` | 96 | `3.089 ms` | 16 | `1.074 ms` |
- The ADR-0049 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 134 tests containing 462 assertions`, `0 failures, 0 errors`, and
  `wall 73.22 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `wall 200.61 s`.

Current ADR-0050 kernel-interleaved Robinson Q note:

- Red TDD checks on 2026-05-08 proved the architecture gap: the old profile
  proofs did not contain ordinary kernel `witness`, `once-univ`, or `neq-store`
  evidence around the Q theory steps, and a source audit still found the old
  host-side `q-normalize-formula` and `q3-predecessor-refutation?` proof path.
- After adding `kernel/*theory-profile-closeo*` and refactoring
  `:robinson-q` into a kernel-bound miniKanren theory rule,
  `lein test-proflog-robinson-q` passed with
  `Ran 9 tests containing 64 assertions`, `0 failures, 0 errors`, and
  `real 13.06 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `real 10.87 s`. Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `7.800 ms` | 32 | `2189.978 ms` |
| `Q7` | 32 | `2.707 ms` | 16 | `382.799 ms` |
| `add(1, zero) = 1` | 48 | `2.296 ms` | 16 | `14.692 ms` |
| `mul(2, zero) = zero` | 48 | `3.165 ms` | 16 | `10.914 ms` |
| `add(1, 2) = 3` | 64 | `2.798 ms` | 16 | `52.573 ms` |
| `mul(2, 2) = 4` | 96 | `2.580 ms` | 16 | `239.788 ms` |
- The ADR-0050 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 137 tests containing 478 assertions`, `0 failures, 0 errors`, and
  `real 80.66 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `real 197.40 s`.

Current ADR-0051 full Robinson Q3 rule note:

- Red TDD check on 2026-05-08 proved the missing full-Q3 behavior: ordinary
  Q-as-antecedent proved `q3-add-one-predecessor`, but the profiled path
  returned `nil`, producing 6 focused failures in
  `proflog.robinson-q-test/full-q3-profile-rule-proves-add-one-predecessor-theorem`.
- After adding `q3-predecessor-intro`, `lein test-proflog-robinson-q` passed
  with `Ran 10 tests containing 73 assertions`, `0 failures, 0 errors`, and
  `real 14.96 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `real 12.01 s`. Per-row in-process timings:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `Q3` | 32 | `7.851 ms` | 32 | `2218.523 ms` |
| `Q7` | 32 | `2.916 ms` | 16 | `446.169 ms` |
| `add(1, zero) = 1` | 48 | `2.835 ms` | 16 | `19.881 ms` |
| `mul(2, zero) = zero` | 48 | `3.764 ms` | 16 | `12.010 ms` |
| `add(1, 2) = 3` | 64 | `3.800 ms` | 16 | `61.401 ms` |
| `mul(2, 2) = 4` | 96 | `4.026 ms` | 16 | `342.546 ms` |
| `q3-add-one-predecessor` | 64 | `2.761 ms` | 48 | `649.812 ms` |
- The ADR-0051 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 138 tests containing 487 assertions`, `0 failures, 0 errors`, and
  `real 89.92 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `real 226.94 s`.

Current ADR-0052 unified Robinson Q3 rule note:

- Red TDD check on 2026-05-09 proved the remaining Q3 boundary: ordinary
  Q-as-antecedent proved the contextual successor theorem, but the profiled
  path returned no proof and the proof-shape/source-audit expectations still
  found the old Q3 closers. The focused selector failed with
  `Ran 12 tests containing 88 assertions`, `15 failures, 0 errors`, and
  `real 36.46 s`.
- After replacing `q3-case-splito` and `q3-predecessor-intro-closeo` with the
  unified `q3-predecessor-equality-closeo`, `lein test-proflog-robinson-q`
  passed with `Ran 12 tests containing 88 assertions`, `0 failures, 0 errors`,
  and `real 22.24 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `real 11.37 s`. Per-row in-process timings:

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
- The ADR-0052 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 140 tests containing 502 assertions`, `0 failures, 0 errors`, and
  `real 100.89 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `real 241.21 s`.

Current ADR-0053 Robinson Q theorem examples note:

- Red TDD check on 2026-05-09 added tests for three public theorem examples
  before adding the catalog definitions. The focused selector failed with
  `No such var: rq/add-right-two-successors` and `real 10.83 s`.
- After adding the theorem catalog entries and proof checks,
  `lein test-proflog-robinson-q` passed with
  `Ran 13 tests containing 109 assertions`, `0 failures, 0 errors`, and
  `real 22.66 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `real 14.80 s`. Per-row in-process timings for the new examples:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `add-right-two-successors` | 64 | `2.289 ms` | 16 | `87.787 ms` |
| `mul-right-two-normal-form` | 96 | `2.595 ms` | 16 | `133.538 ms` |
| `q3-add-two-successor` | 64 | `3.175 ms` | 32 | `1174.210 ms` |
- The ADR-0053 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 141 tests containing 523 assertions`, `0 failures, 0 errors`, and
  `real 98.85 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `real 228.07 s`.

Current ADR-0054 Robinson Q prime/evenness note:

- Red TDD check on 2026-05-09 added corrected prime/evenness tests before the
  public helpers existed. The focused selector failed with
  `No such var: rq/prime-other-than-two-has-no-two-factor` and `real 8.74 s`.
- After adding the corrected inline prime formula helpers and preserving the
  equality-fragment sidecar under the `:robinson-q` profile,
  `lein test-proflog-robinson-q` passed with
  `Ran 15 tests containing 123 assertions`, `0 failures, 0 errors`, and
  `real 20.69 s`.
- The reproducible comparison probe `lein probe-proflog-robinson-q` passed with
  `real 12.27 s`. Per-row in-process timings for the new corrected examples:

| Formula | Ordinary Q fuel | Ordinary elapsed | Profile fuel | Profile elapsed |
|---|---:|---:|---:|---:|
| `prime-other-than-two-has-no-two-factor` as Q antecedent | 128 | `4.470 ms` | 128 | `4.385 ms` |
| `prime-other-than-two-is-not-left-even` as Q antecedent | 128 | `1.855 ms` | 128 | `2.140 ms` |

- The theorem-only `:robinson-q` query for
  `prime-other-than-two-has-no-two-factor` at fuel 128 did not finish inside a
  `timeout -k 5s 60s` wrapper: `real 60.07 s`.
- The ADR-0054 commit gate ran fast and extended concurrently. Fast passed with
  `Ran 143 tests containing 537 assertions`, `0 failures, 0 errors`, and
  `real 84.57 s`. Extended passed with
  `Ran 68 tests containing 203 assertions`, `0 failures, 0 errors`, and
  `real 200.09 s`.

## Committed Test Iterations

| Test var | Namespace | Query family | Final successful runtime | Notes |
|---|---|---|---:|---|
| `decomposition-can-bind-earlier-arguments-before-finding-a-later-clash` | `proflog.equality-test` | `exists a,b,t. [1] = cons(a, cons(b, t))` | `422.261319 ms` | Regression for contradiction discovered only after an earlier parameter binding during equality decomposition. |
| `factored-move-warning-leaves-small-win-positions-unresolved` | `proflog.query-test` | Ground `move/2` plus factored-vs-inline `win/1` | `4226.645269 ms` | Direct proof search still decides ground `move/2`; bounded status leaves factored `win(0)` and `win(1)` unresolved. |
| `acyclic-quantified-spec-distinguishes-acyclic-and-cyclic-small-graphs` | `proflog.quantified-programs-test` | `acyclic-abc`, `acyclic-aba`, `acyclic-abca` | `2400.870986 ms` | Inline graph-property quantifiers prove the acyclic graph and refute the cyclic ones. |
| `sorted2-quantified-spec-distinguishes-small-sorted-and-unsorted-lists` | `proflog.quantified-programs-test` | `sorted2` over `[]`, `[1]`, `[0,1,2]`, `[2,1]`, `[1,2]` | `14.79 s` | Covers the restored legacy empty, singleton, sorted, unsorted, and two-element sorted cases after the equality fix. |
| `subset-quantified-spec-handles-true-false-and-reflexive-cases` | `proflog.quantified-programs-test` | `sub-ab-abc`, `sub-abc-ab`, `sub-a-a` | `2154.439012 ms` | Quantified finite-domain subset specification closes both true cases and refutes the false one. |
| `query-answers-collect-unique-answers-beyond-duplicate-proof-paths` | `proflog.answers-test` | duplicate `dup(x)` proofs for `0` before distinct answer `1` | `7.03 s` | Answer search now collects unique records while the kernel prunes stale disequalities before they can surface as `neq(0, 0)`. |
| `query-answer-diagnostics-reports-raw-vs-unique-growth` | `proflog.answers-test` | duplicate `dup(x)` diagnostics across raw limits `1`, `2`, `4` | `16.66 s` | Diagnostics helper now forces each raw slice eagerly so search time is measured honestly before export/merge analysis. |
| `query-stage-diagnostics-summarize-proof-families` | `proflog.answers-test` | duplicate `dup(x)` stage diagnostics at first unfolded stage | `20.16 s` | The stronger harness now reports duplicate exported answers separately from distinct proof signatures. |
| `query-answer-diagnostics-can-explain-a-recursive-symbolic-frontier` | `proflog.answers-test` | `reverse([a,b], r)` diagnostics at `call-depth 1` | `17.67 s` | Captures the first symbolic frontier as `r = []` plus deferred `reverse/append` obligations. |
| `query-stage-diagnostics-distinguish-productive-and-dry-reverse-stages` | `proflog.answers-test` | `reverse([a,b], r)` stage sweep across depths `0`, `1`, `2` | `95.98 s` | Confirms stage `1` is productive while stage `2` is completely dry at `fuel 32`, `raw-limit 1`. |
| `query-answers-use-call-depth-1-to-refine-the-direct-reverse-frontier` | `proflog.answers-test` | `reverse([a,b], r)` at `call-depth 1`, `fuel 64`, `max-raw-proof-limit 64` | `14.06 s` | Current public `query-answers` returns the closed answer `r = [b,a]` with no residuals while diagnostics still expose the raw symbolic frontier. |
| `query-answers-prefer-the-first-concrete-inverse-append-split-over-symbolic-frontiers` | `proflog.answers-test` | `append(a, b, [a,b,c])` inverse query at `call-depth 1` | `10.91 s` | Current public `query-answers` returns all four closed inverse splits with empty residuals. |
| `member-empty-list-fails` | `proflog.list-programs-test` | `member(a, [])` | `565.030374 ms` | Immediate constructor-clash failure after opening the existential list shape. |
| `append-two-step-ground-case-succeeds` | `proflog.list-programs-test` | `append([a, b], [c], [a, b, c])` | `154219.489533 ms` | Required fuel `256`; semantically closed but expensive. |
| `append-forward-query-binds-a-three-element-result` | `proflog.list-programs-test` | `append([a], [b, c], z)` | `68873.149268 ms` | Concrete three-element result exported at call-depth `2`; shallow `neq` residuals remain. |
| `reverse-two-element-list-succeeds` | `proflog.list-programs-test` | `reverse([a, b], [b, a])` | `276769.773115 ms` | Required fuel `256`; recursive reverse remains materially slower than append. |
| `append-nested-forward-query-binds-the-concrete-result` | `proflog.list-programs-test` | `append([[a]], [[b]], z)` | `41655.620203 ms` | Concrete nested binding exported at call-depth `2`; shallow `neq` residuals remain. |
| `append-nested-suffix-query-binds-the-concrete-second-argument` | `proflog.list-programs-test` | `append([[a, b]], z, [[a, b], [c]])` | `26539.838541 ms` | Concrete nested suffix exported at call-depth `2`; shallow `neq` residuals remain. |

## Historical Exploratory Runtime Boundaries

These rows are retained as branch-local history from earlier answer-mode and
raw-export probes. They are not the current capability boundary when a later
section or focused test row contradicts them. In particular, ADR-35 and ADR-43
supersede the older reverse, inverse-append, and nested-append "no closed answer
yet" readings for current public `query-answers` or long-timeout matrix
reachability.

| Probe | Final successful runtime | Result | Operational note |
|---|---:|---|---|
| `append(xs, ys, [a, b, c])` diagnostics, `call-depth 2`, `raw-limit 1` | `28915.464495 ms` | 1 unique answer | Only the base split `([], [a,b,c])` is visible at the first raw frontier. |
| `append(xs, ys, [a, b, c])` diagnostics, `call-depth 2`, `raw-limit 2` | `41559.381232 ms` | 2 unique answers | The first recursive split `([a], [b,c])` appears, but no deeper split yet. |
| `append(xs, ys, [a, b, c])` diagnostics, `call-depth 2`, `raw-limit 4` | `53524.490474 ms` | 3 raw proofs, still only 2 unique answers | The third raw proof is a duplicate witness for the second split family. |
| `append(xs, ys, [a, b, c])` stage diagnostics, stages `0..2`, raw-limits `1,2` | `99704.194281 ms` | Stage `0` defer-call only; stages `1` and `2` both productive | Stage `1` reaches a symbolic recursive cons-family, and stage `2` concretizes it into the second split `([a],[b,c])`. |
| `append(xs, ys, [a, b, c])` diagnostics, `call-depth 2`, `raw-limit 4`, proof-family summary | `49118.794415 ms` | 3 raw proofs, 3 distinct proof signatures, 2 unique answers | The third raw proof is not an identical proof duplicate; it is a distinct proof family collapsing to the same exported second answer. |
| `append(xs, ys, [a, b, c])` `query-answers`, `call-depth 2` | `35258.2583 ms` | Returned 2 answer records | The stage policy now reaches the base split and the first recursive split family in one API call. |
| `reverse([a, b], r)` diagnostics, `call-depth 1`, `raw-limit 1` | `1815.796755 ms` | 1 symbolic frontier | Exports `r = []` with deferred `reverse([b], a_3)` and `append(a_3, [a], [])` obligations. |
| `reverse([a, b], r)` stage diagnostics, stages `0..2`, raw-limit `1` | `73709.59746 ms` | Stage `0` defer-call, stage `1` first recursive frontier, stage `2` dry | The reverse gap is currently a dry deeper stage, not just duplicate answer export. |
| `reverse([a, b], r)` diagnostics, `call-depth 2`, `raw-limit 1` | `54681.940331 ms` | 0 raw proofs | The first fully unfolded raw proof does not appear at this fuel slice. |
| `reverse([a, b], r)` `query-answers`, `call-depth 2` | `35910.784284 ms` | Returned 2 fallback symbolic frontier records | Historical pre-ADR-43 row. Current focused coverage now returns `r = [b,a]` through public `query-answers` at `call-depth 1`. |
| Nested `append(x, y, [[a], [b]])` split enumeration | `>180000 ms` | No result before manual stop | Historical row. The post-ADR-35 long-timeout matrix later found `append-inverse-nested` `3 / 3` at raw `8` in `13283.4934 ms`. |
| Depth-3 forward `append(left, right, z)` answer synthesis | `>360000 ms` | No result before manual stop | Historical row from the pre-ADR-35/41 answer-export boundary. Keep as a record of that failed exploratory slice, not as a current general claim about all focused or parity paths. |

## Post-ADR-0035 Long-Timeout List-Kernel Sweep

These rows are not routine gate timings. They record eventual reachability for
the full `proflog.list-kernel-matrix-probe` catalog after ADR-0035 structural
residual continuation.

| Probe | Successful runtime | Result | Operational note |
|---|---:|---|---|
| Full list-kernel catalog, isolated `900 s` wrappers | varies by row | every catalog row returned `:target-found? true` | Most rows returned within tens of seconds. |
| `append-inverse-flat-longer` | `509517.493191 ms` | all `5 / 5` splits found at raw `32` | Heavy outlier; keep outside default regression gates unless that cost is explicitly accepted. |
| Slow reverse and partial reverse rows | `21896.698837 ms` to `70285.584748 ms` | target found | Reverse rows are now eventually reachable, but still too expensive to treat as cheap smoke tests. |

## Legacy Reference Runs

| Legacy test | Final successful runtime | Result |
|---|---:|---|
| `test-Y10-reverse-synth-result` | `27.20 s` | `reverse([a,b], R)` returned `R = [b,a]`. |
| `test-Y12-append-inverse-synth-all-splits` | `28.02 s` | `append(A, B, [a,b,c])` returned all 4 splits. |
| `test-Y15-append-nested-inverse-all-splits` | `17.76 s` | `append(A, B, [[a],[b]])` returned all 3 nested splits. |
| `test-Z04-append-depth3-combined-three-levels` | `17.06 s` | Combined level-0/1/3 depth-3 append synthesis succeeded. |
