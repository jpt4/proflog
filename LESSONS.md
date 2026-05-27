# Lessons

- For TC demonstrations, distinguish representability from proof-search shape.
  ADR-0045 showed that the same compiled Minsky `step/2` semantics can make a
  five-step transfer practical when expressed as a finite conjunction of trace
  edges, even though the direct recursive `halts-in-steps/3` formulation remains
  non-viable under long time controls. Keep both: the shaped proof is a
  demonstration, and the direct recursive form is performance evidence.
- For symbolic rewriting demos, context rules are semantic surface area. The
  ADR-0046 SKI boolean-false row was not merely slow under root reduction; it
  required a left-spine application-context rule because the next redex is in
  function position. Add such rules as Proflog clauses with red tests, and
  record the search cost because answer-mode paths can slow down sharply.
- ADR-0047 tightened that lesson: right-argument contextual reduction is needed
  to prove the standard omega quine, but adding it to the default `step/2`
  relation made the SKI suite exceed a `900 s` guard. Isolate fuller search
  relations when they demonstrate expressiveness but materially broaden the
  ordinary proof-search branching factor.

## 2026-05-06

- Current-facing docs must distinguish historical probe failures from current
  capability boundaries. Earlier list-family notes saying that reverse,
  inverse append, or nested inverse append did not surface closed answers are
  still useful history, but ADR-35/41/43 changed the present interpretation:
  public `query-answers` now covers `reverse([a,b], r)` and all four
  `append(x,y,[a,b,c])` splits in focused rows, and the long-timeout raw matrix
  eventually reaches every catalog target.
- The same rule applies to group-verifier notes. The first GV probes correctly
  recorded greenfield weakness, then the hard-family overlay improved a named
  surface, and ADR-39 promoted a proof-producing equality-fragment profile.
  Current docs should lead with the promoted `profiled equality-fragment`
  evidence and keep overlay-only or timeout records clearly historical.
- A source map is not just navigational convenience on this project. Because
  Proflog's implementation intentionally separates source syntax, compiled IR,
  proof kernel, profiled layers, query status, answer export, and probes, a
  reader needs to know which namespace is semantic authority and which namespace
  is an ADR measurement or compatibility surface.

## 2026-04-29

- ADR-0027 removed the projected `subst-formulao` kernel-facing boundary by
  rewriting formula substitution as constructor-by-constructor logic goals. The
  key detail was to preserve binder shadowing relationally: `forall`,
  `once-forall`, and `exists` all call `remove-bindo` before recursing into the
  tied body.
- After that recovery, remaining `synthesis-modes-test` and
  `recursive-synthesis-test` failures should no longer be attributed to formula
  substitution projection. They now belong to search ordering, answer-overlay
  frontier handling, recursive proof control, or family-specific scheduling.
- A purity audit must check transitive kernel helpers, not only direct
  `project` calls in `kernel.clj`. `subst-formulao` lives in `proflog.subst`,
  but because the kernel calls it in quantifier, equality, disequality, atom,
  procedure-call, and branch-saving rules, its projected implementation creates
  a kernel-wide relationality boundary.
- ADR-0028 extended that audit to `kernel_support.clj`: saved-disequality
  maintenance is proof-state flow, not harmless host utility code. Projecting
  `neqs` or `sigma` can both throw in reverse/open branch-state modes and
  freeze stale decisions before later logic refinements. ADR-0028 initially
  left fuel stepping as a narrow accepted support projection because it is
  bounded operational bookkeeping over a scalar budget.
- ADR-0029 closed the fuel exception from ADR-0028. `fuel=nil` makes search
  unbounded but does not bypass `step-fuelo`; any non-closing branch-progress
  rule still calls the fuel relation. A transitive-purity audit therefore has
  to test open fuel on real kernel progress, not only direct helper calls.
- Core.logic finite-domain arithmetic can express numeric fuel stepping
  relationally, but the domains must reflect the arithmetic direction. Splitting
  current fuel as `1..Long/MAX_VALUE` and next fuel as
  `0..Long/MAX_VALUE - 1` preserves the largest valid step without causing
  `fd/+` to overflow.
- Forward-only wrappers can look harmless when all current success paths call
  them with ground-enough inputs. The reverse/partial loss appears in preimage
  modes: synthesizing a formula, environment, clause body, or recursive query
  shape from a substituted branch obligation.
- ADR-0026's first branch profiler accidentally preserved a kernel impurity
  even after keeping the compiled program map out of `core.logic/project`: it
  still projected the selected formula, pending agenda, saved literals,
  `sigma`, and `neqs` before classifying a residual branch.
- The recovery pattern is to separate finite host metadata from proof-state
  terms. The compiled program can supply a lexical finite set of active
  relation names, but the branch formulas, saved literals, equality
  substitution, and disequality store must be checked with structural goals.
- Host shape predicates such as "pure propositional", "equality-free
  first-order", "compound entry", and "no active program atom" can be converted
  into small relations: match formula constructors with `==`, recurse over
  finite branch lists, require `sigma` and `neqs` to be `'()`, and express
  active-relation exclusion with disequality constraints against the finite
  active set.
- The right failure mode for a profiler guard is conservative failure. If a
  branch is open, structurally unsupported, equality-bearing, disequality-
  bearing, or mentions an active program relation, the optimized handoff should
  fail and leave the full kernel in control.
- This recovery is now written up as a reusable example:
  [Structural Profiler Purity Recovery](docs/log/2026-04-29-structural-profiler-purity-recovery.md).

## 2026-04-18

- In the greenfield kernel, positive equality and disequality do not have symmetric operational rules.
- `eq(t, u)` is modeled by occurs-checking unification over the current branch substitution. If the terms can be unified, the equality can extend branch state; if they structurally clash, the branch closes.
- `neq(t, u)` is not "unification fails." It is a constraint saying the terms must remain distinct.
- A disequality literal is only contradictory when the current branch state already forces its two sides equal. If the branch does not yet force equality, the disequality must be stored symbolically and rechecked after later equality bindings.
- Counterexample: `neq(succ(x), succ(a))` is satisfiable because `x` may be any term other than `a`. A `neq` rule that closes by finding a unifier would incorrectly choose `x = a` and treat a satisfiable formula as contradictory.
- Operational summary:
  equality -> unification
  disequality -> symbolic disequality constraint / disunification discipline

## 2026-04-22

- The failed `sorted2([1])` probe exposed an implementation bug in equality contradiction search, not in quantified sortedness itself.
- In [src/proflog/equality.clj](./src/proflog/equality.clj), `eq-contradiction-term*o` must propagate substitutions learned from earlier arguments into later argument comparisons.
- Counterexample: `cons(1, null) = cons(a, cons(b, t))` is contradictory only after first binding `a = 1` and then discovering `null = cons(b, t)`.
- Before the fix, same-head contradiction checking recursed on later arguments under the old substitution, so it missed constructor clashes that became visible only after an earlier `par-bind` or `eq-bind`.
- The consequence was broader than `sorted2`: any proof depending on decomposition plus later contradiction under a refined substitution could stay spuriously open.
- The regression that now locks this down is [test/proflog/equality_test.clj](./test/proflog/equality_test.clj), `decomposition-can-bind-earlier-arguments-before-finding-a-later-clash`.
- Generic `sorted/1` is naturally first-order in Proflog when written against a fixed comparator relation such as `le/2`.
- A comparator cannot currently be passed as an ordinary argument because the language and procedure-call rule are first-order: relation symbols are resolved from the declared program, while ordinary term arguments are not callable predicates.
- If the frontend ever wants syntax like `sorted_by(le, xs)`, that should compile to a first-order specialized or inlined form rather than pretending the kernel already supports higher-order predicate arguments.
- Compare this boundary explicitly against higher-order miniKanren designs before extending the language: the right question is not just ergonomics, but what semantic and operational commitments Proflog would inherit by admitting predicate-valued arguments or meta-calls.
- A residual like `neq(0, 0)` was not a meaningful exported constraint. It came from a saved disequality that the kernel had failed to prune after a later binding-producing close step forced its two sides equal.
- In answer mode this can happen because requested answer variables are allowed to behave like proof-time variables during `neq-close`, so a branch can bind an answer variable while older saved disequalities still mention it.
- The right invariant is: saved disequalities should remain genuinely open under the current substitution. Binding-producing kernel steps must therefore prune already-false disequalities instead of carrying them forward into `neqs-out`.
- The exporter still keeps a defensive top-level filter for obviously impossible residuals such as `neq(t, t)`, but that is a guardrail, not the main semantic fix.
- This still does not prove that every exported residual set is globally satisfiable. It closes the direct stale-disequality leak, not all possible multi-formula inconsistency patterns.
- `clojure.core.logic/run` result slices must be forced before timing or summarizing them. In the new diagnostics helper, leaving the raw slice lazy made the later export walk absorb the actual search cost and produced misleading timing attribution.
- Historical ADR-0013/0014 list-search measurements, later superseded as
  current capability claims by ADR-35 and ADR-43, were:
  `reverse([a,b], r)` at `call-depth 1` reaches one symbolic frontier quickly, but `call-depth 2` finds no first raw proof at the same fuel slice; `append(a,b,[a,b,c])` reaches the first two split families, then starts duplicating proof paths before the deeper splits surface.
- The stronger stage diagnostics sharpen that split:
  `reverse([a,b], r)` is dry at stage `2` for the current fuel slice, while `append(a,b,[a,b,c])` remains productive through stage `2`.
- For inverse append, the extra raw proof at stage `2` is not just a byte-for-byte proof duplicate. The diagnostics show `3` raw proofs, `3` distinct proof signatures, but only `2` unique exported answers.
- That means plain raw-proof deduplication is unlikely to recover the missing deeper append splits by itself. The more promising direction is answer-equivalence-aware search prioritization or branch-state canonicalization that can recognize when distinct proof families are converging to the same exported frontier.
- Reconstructing the next search stage from exported answer records was too lossy. The exported record keeps bindings, residuals, and proofs, but not the full branch-search context that produced them. In practice that broke constrained and composed queries such as `step(3,y) and y != 2` and `jump(x,0)`.
- The safer general policy is staged deepening over increasingly unfolded query formulas, with fallback to the deepest productive stage. That keeps the improvement structure-agnostic, preserves useful shallow symbolic frontiers when a deeper stage goes dry, and still prefers deeper refinements when they exist.

## 2026-04-24

- API-level parity is not the same thing as kernel-level closure. ADR-0013 let
  `query-answers` return the closed reverse / append list-family answers by
  reusing the ADR-0012 list-family materializer, while raw diagnostics still
  expose the symbolic reverse frontier underneath.
- Legacy list synthesis succeeds for a different operational reason: the legacy
  prover runs the open query directly inside `proveo`, and its projected
  `l-ground` guard rejects only terms containing `(par ...)`. Bare logic
  variables therefore pass the guard, so reverse and partial-mode `append`
  queries can descend and bind those variables inside the prover itself.
- For the current known list-family fast path, that means the closed answer is
  not being extracted from the raw kernel answer stream. The extra-kernel layer
  synthesizes the closed answer directly from extensional query shape, and the
  exported record carries empty `:proofs` for exactly that reason.
- Distinguish carefully between:
  - "the kernel can prove the ground instance semantically" and
  - "the generic answer stream exported the closed answer."
  For `reverse([a,b], r)`, the ground instance `reverse([a,b], [b,a])` is
  semantically reachable, but the current closed answer surface in
  `query-answers` comes from the list-family materializer rather than from a
  raw kernel answer-stream witness.
- When evaluating a hard relational family, the first question should be:
  "At which layer does the desired answer first exist?" Distinguish between:
  - raw kernel stream presence,
  - generic stream sifting/post-processing,
  - and specialty family handling.
- "Generic post-processing" should mean family-independent answer-stream work:
  alpha-equivalent merge, residual dedup, completion ranking, generic closed
  answer filtering, generic candidate replay, or fairer stream slicing. If the
  step needs to know that the query is about lists, groups, graphs, or any other
  specific theory, it is no longer generic post-processing.
- That classification is more useful than undirected runtime tuning. It tells
  the repo whether the next step is search fairness, answer-surface filtering,
  or an explicit architectural decision to add a family-specific evaluator.
- Family-specific materializers implicitly define supported structure families.
  They may be justified, but they should never be mistaken for evidence that
  Proflog has gained generic support for that data structure or theory.
- The project needs an explicit pure-core / overlay boundary. There should be a
  notion of `prove(<first-order formula with equality>)` that remains as close
  as possible to pure miniKanren tableau behavior, while bounded `fuel`,
  bounded `call-depth`, raw proof limits, answer ranking, closed-answer
  filtering, and family-specific materializers stay visible as configurable
  overlays above that core.
- `call-depth` is already capable of that style of access: the kernel treats
  `nil` as unbounded descent, just as `fuel=nil` means unbounded step budget.
  The current answer collectors are not equally open-ended: `proof-limit` and
  `max-raw-proof-limit` are still finite numeric controls in the answer layer.
- The first long raw-kernel list probes sharpened the historical ADR-0014
  boundary:
  - `reverse([a,b], r)` with `fuel=nil` and `call-depth=1` exhausts without
    exporting `[b,a]`,
  - `reverse([a,b], r)` with `fuel=nil` and `call-depth=2`, `3`, `4`, and `nil`
    reaches raw limits `1`, `2`, and `4`, then fails to finish the next slice
    within fifteen minutes,
  - `append([a], [b,c], z)` with `fuel=nil` and `call-depth=nil` exhausts
    without exporting `z = [a,b,c]`,
  - `append(x, y, [a,b,c])` with `fuel=nil` and `call-depth=nil` exports the
    correct base split immediately, but not the deeper three closed legacy
    splits within the measured fifteen-minute slice.
- The durable lesson was not "the pure core cannot be accessed at all." The
  pure-core path was accessible, but raw kernel/export slices could lag far
  behind public answer and focused-profile capabilities.
- The kernel comparison against legacy is now concrete enough to use as an
  architectural input. The biggest structural differences are:
  - greenfield carries explicit `sigma` / disequality / residual state in the
    kernel,
  - legacy leans on equality rewriting, paramodulation, and lemma threading,
  - legacy runs open synthesis through the ordinary prover path,
  - greenfield has a separate answer-mode flow with explicit recursive-descent
    budgeting and residual deferral.
- That comparison does not yet prove the separate answer-mode flow is the only
  cause of the current gaps, but it is one of the clearest major differences
  and now deserves to be treated as an architectural variable, not just an
  implementation detail.
- The first greenfield `GV` probes are worse than legacy, not just different.
  Using the exact legacy-style group-verifier formulas in greenfield:
  - `Z₂` identity resolves as `:succeeds`,
  - `Z₂` closure and inverses stay `:unresolved` at a `5000 ms` status probe,
  - `Z₁` full 7-universal associativity stays `:unresolved` at `15000 ms`,
  - `Z₂` precomputed associativity and full 7-universal associativity both fail
    to return before an external `60 s` timeout,
  - the non-group full associativity probe also fails to return before the same
    external bound.
- That means the current evidence does *not* yet show different overlapping
  capability slices between legacy and greenfield on the group-verifier family.
  The initial evidence shows greenfield to be strictly weaker on the first `GV`
  slice, aside from the simple identity success.
- If that conclusion survives the next ADR-14 probe pass, then a more
  substantial architectural revision becomes justified, including pushing
  answer-oriented behavior back out of the kernel and treating it as an
  explicit overlay.
- A legacy "pure `l-ground`" experiment needs to be interpreted against the
  legacy term representation, not just against the abstract guard definition.
  The legacy prover still passes bare core.logic variables through procedure
  calls after substitution, so replacing the projected guard with a structural
  tagged-term check does more than suppress reverse-mode synthesis.
- In a local experiment, changing legacy `l-ground-termo` to reject bare
  core.logic vars caused all targeted recursive list cases to collapse:
  `test-Y01`, `test-Y02`, `test-Y07`, `test-Y08`, `test-Y10`, `test-Y11`, and
  `test-Y12` all failed, while the original projected guard restored them.
- That means legacy's stronger performance on the list family is not just
  "search quality plus an impure synthesis escape hatch." Its current recursive
  procedure-call behavior materially depends on the projected guard admitting
  post-substitution host variables as L-ground.
- So the right comparison is not "legacy with one small impurity removed." A
  truly structural guard is incompatible with the way legacy currently embeds
  object-level openness into host-level logic vars. To make that guard pure
  without collapsing recursion, the term representation itself would have to
  change toward tagged object-language variables, as in greenfield.
- ADR-0015 confirmed that the most useful kernel/overlay boundary is not just
  "move the public answer entry points out." The boundary also needs one shared
  support layer for the proof-state mechanics that are genuinely common to both
  paths: L-groundness, complementary literal closure, disequality pruning,
  proof-variable-only disequality closure, and fuel stepping.
- That shared support is now explicit in `src/proflog/kernel_support.clj`.
  This matters architecturally because it leaves the pure kernel and answer
  overlay with one semantic definition of the proof core rather than two
  drifting copies.
- The safest regression boundary after the extraction is:
  - `query-succeeds` / `query-fails` stay on the pure kernel path,
  - `query-answers` / diagnostics stay on the answer overlay path.
  Both sides now have narrow routing regressions, which is better than
  inferring the boundary from namespace structure alone.
