# Answers API

This file covers `test/proflog/answers_test.clj`.

## Ground Enumeration

The bounded ground-term enumerator over the simple unary Peano language returns:

```clojure
[(app zero)
 (app s (app zero))
 (app s (app s (app zero)))
 (app s (app s (app s (app zero))))]
```

In decimal notation, that is:

```clojure
[0 1 2 3]
```

## Direct Symbolic Binding Export

For:

```clojure
p(x) :- x = zero
```

the open query:

```clojure
p(x)
```

exports:

```clojure
{:bindings [[x (app zero)]]
 :residuals []
 :proofs [(neq-close (eq-bind))]}
```

So the answer API returns the direct substitution `x = 0` without requiring
bounded ground enumeration.

## Residual Disequalities Survive Export

For the formula:

```clojure
x != 1 and 0 = 1
```

the proof closes through the contradiction `0 = 1`, but the exported answer
still preserves the surviving side condition:

```clojure
{:bindings [[x x]]
 :residuals [(neq x 1)]}
```

This is the current contract: symbolic answers keep residual constraints when
they remain semantically relevant.

## Duplicate Proofs Do Not Define Answer Cardinality

For the intentionally duplicated program:

```clojure
dup(x) :- x = zero
dup(x) :- x = zero
dup(x) :- x = s(zero)
```

the open query:

```clojure
dup(x)
```

should return the two unique answers `x = 0` and `x = 1`, even though the
first answer has two proof paths.

The current exporter now does two important things here:

- it keeps searching raw proof states until it has the requested number of
  unique answer records, rather than truncating first and merging afterward,
- the kernel now prunes saved disequalities that have already become false
  under the current substitution, so stale artifacts such as `neq(0, 0)` do
  not survive as residual constraints,
- it drops impossible residual artifacts such as `neq(0, 0)` instead of
  exporting them as if they were meaningful side conditions.

So the current records are:

```clojure
{:bindings [[x 0]]
 :residuals []}

{:bindings [[x 1]]
 :residuals [1 != 0, 1 != 0]}
```

The duplicated disequalities on the second answer are still redundant, but they
are semantically harmless. The important correction is that the later distinct
answer is no longer starved by earlier duplicate proof paths, and already-false
disequalities no longer leak out as residuals.

## Diagnostics: Raw Proof Growth Versus Unique Answers

The answer layer now also exposes a diagnostics helper:

```clojure
query-answer-diagnostics
```

For the duplicated `dup(x)` program above, the snapshots for raw limits
`[1 2 4]` show the difference between raw proof paths and unique exported
answers:

```clojure
{:raw-limit 1, :raw-count 1, :unique-count 1}
{:raw-limit 2, :raw-count 2, :unique-count 1}
{:raw-limit 4, :raw-count 3, :unique-count 2}
```

So the second raw proof is still just a duplicate witness for `x = 0`, and the
later distinct answer `x = 1` only appears once the raw stream is allowed to
grow past those duplicates.

The diagnostics now also summarize proof families, not just answer records. For
the same `dup(x)` slice at raw limit `4`, one productive stage reports:

```clojure
{:duplicate-exported-count 1
 :distinct-proof-signature-count 3
 :common-proof-signatures
 [{:count 1, :steps [...]}
  ...]}
```

So the helper can now separate:

- repeated exported answers,
- genuinely distinct raw proof families,
- and identical proof signatures.

## Direct Entry Policy

`query-answers` still keeps the original negated query and enters top-level
literal program calls directly in the kernel. `call-depth` is spent only on
recursive descendants below that entry boundary.

ADR-0013 changed what happens after export:

- the answer layer now canonicalizes internal proof vars to stable `_0`, `_1`,
  ... names,
- duplicate residual literals collapse before answer merge and ranking,
- alpha-equivalent symbolic frontiers merge instead of surfacing as separate
  answers,
- and for the known list-family `append/3` and `reverse/2` queries,
  `query-answers` now intentionally reuses the ADR-0012 closed-answer
  materializer so the public API can return the closed list answer directly.

So the raw symbolic frontier is still visible in diagnostics, but the public
`query-answers` surface for those known list families is now the closed answer.

## Recursive Open Query: `even(x)`

Using the recursive parity program, the open query:

```clojure
even(x)
```

currently exports two families:

```clojure
{:bindings [[x 0]]
 :residuals []}

{:bindings [[x s(a_1)]]
 :residuals [s(a_1) != 0, not odd(a_1)]}
```

The first record is the direct base witness. The second is the recursive
symbolic family: numbers that are one successor above a value whose `odd`
branch still needs to fail.

## Open Query: `win(x)`

For the inline Nim program, the generic answer API returns symbolic winning
families rather than enumerating every numeral eagerly.

The first two current records are:

```clojure
{:bindings [[x s(a_1)]]
 :residuals [win(a_1)]}

{:bindings [[x s(s(a_1))]]
 :residuals [s(s(a_1)) != s(a_1), win(a_1)]}
```

So the answer API says:

- a one-step predecessor of a losing position is winning,
- a two-step predecessor is also winning, with the additional disequality
  witness recorded explicitly.

## Diagnostics: Reverse Frontier

For the open list query:

```clojure
reverse([a, b], r)
```

the diagnostics helper is still useful because it exposes the raw symbolic
frontier underneath the public closed answer surface. With the current direct
entry policy, one committed diagnostics slice is:

```clojure
{:raw-limit 1
 :raw-count 1
 :unique-count 1
 :sample-records
 [{:bindings [[r []]]
   :residuals
   [[a, b] != []
    not append(a_3, [a], [])
    not reverse([b], a_3)]}]}
```

This means the prover has exposed the first recursive frontier:

- it has recognized that `reverse([a, b], r)` cannot be using the empty-list
  base case,
- it has introduced an intermediate accumulator-like value `a_3`,
- and it has deferred the deeper obligations `reverse([b], a_3)` and
  `append(a_3, [a], [])`.

So the current greenfield behavior is not “no semantics at all.” The raw kernel
search can still expose the first symbolic reverse frontier through direct entry
descent. ADR-0013 did not delete that frontier; it changed the public answer
surface so `query-answers` can now return the closed `[b,a]` record directly for
this known list family.

## Worked Example: `query-answers reverse([a,b], r)`

At the public `query-answers` API, ADR-0013 now returns the closed reverse
answer directly for this known list family:

```clojure
{:bindings [[r [b,a]]]
 :residuals []
 :proofs []}
```

That closed record now comes from the intentionally reused ADR-0012 list-family
materializer. The older symbolic `r = []` and `r = [a]` frontiers still matter,
but they now belong to the raw-diagnostics explanation rather than to the first
public answer record.

Those symbolic frontier records still mean:

- current binding frontier,
- plus residual obligations that must still be discharged.

So:

- `r = []` means "the current branch has matched the outer reverse clause in a
  way that binds `r` to `[]`, but it still owes a recursive `reverse/2` call and
  an `append/3` call,"
- `r = [a]` means "one deeper descent has refined the frontier, but a recursive
  obligation still remains, so this is still not a closed answer."

The duplicated `not reverse([b], [])` residual in the deeper symbolic frontier
is real. It comes from distinct proof paths that collapse to the same exported
symbolic state before canonicalization and residual dedup remove exact repeats.

## Parameter Meanings

For this worked example, the important `query-answers` controls are:

- `call-depth`: recursive procedure-call descent budget below the surface query
  boundary. The top-level `reverse/2` query entry itself does not spend this
  budget.
- `fuel`: global proof-search step budget. Kernel steps that actually descend or
  expand formulas consume fuel; when fuel runs out, deeper search stops.
- `proof-limit`: number of unique exported answer records requested.
- `max-raw-proof-limit`: cap on how many raw kernel proof states the collector is
  allowed to sample while looking for those exported answers.

The repository also uses the phrase "raw proof limit" in diagnostics. The
relationship is:

- `query-answer-diagnostics` takes an explicit `raw-limit` and reports exactly
  what that one raw slice produced,
- `query-answers` grows an internal raw limit up to `max-raw-proof-limit`,
  merges duplicate exported answers, ranks them by completion, and returns up to
  `proof-limit` unique records.

## Why These Bindings And Residuals Appear

The symbolic `r = []` and `r = [a]` records are now best understood as
diagnostics slices, not as the first public `query-answers` result.

At `call-depth 0`, the top-level query can enter `reverse([a,b], r)` directly,
but it cannot spend any recursive budget below that entry call. So the raw
exported frontier is:

```clojure
{:bindings [[r []]]
 :residuals
 [[a,b] != []
  not append(a_3, [a], [])
  not reverse([b], a_3)]}
```

This says:

- `r` is currently `[]`,
- `[a,b] != []` records that the non-empty input cannot be the reverse base
  case,
- and the recursive reverse and append obligations are still open.

At `call-depth 1`, the raw frontier refines one step deeper:

```clojure
{:bindings [[r [a]]]
 :residuals
 [[a] != []
  [a,b] != []
  not reverse([b], [])
  not reverse([b], [])]}
```

This says:

- the intermediate structure now forces `r = [a]`,
- `[a] != []` records that the refined result is still not the append base case,
- `[a,b] != []` remains from the outer reverse decomposition,
- and the still-open recursive `reverse([b], [])` obligation appears twice
  because two proof paths collapse to the same symbolic frontier before
  canonicalized export.

So the progression from `[]` to `[a]` is not unsoundness. It is the raw
symbolic search exposing progressively refined open proof frontiers rather than
only fully closed substitutions.

## Worked Example: `query-answers append(x, y, [a,b,c])`

For the known inverse-append list family:

```clojure
append(x, y, [a, b, c])
```

the public `query-answers` API now returns the full closed split family
directly:

```clojure
{:bindings [[x []] [y [a,b,c]]]
 :residuals []
 :proofs []}

{:bindings [[x [a]] [y [b,c]]]
 :residuals []
 :proofs []}

{:bindings [[x [a,b]] [y [c]]]
 :residuals []
 :proofs []}

{:bindings [[x [a,b,c]] [y []]]
 :residuals []
 :proofs []}
```

That is again the ADR-0013 list-family completion policy at work. The raw
symbolic diagnostics remain useful underneath it, but the public answer surface
now matches the legacy split family for this known query shape.

## Stage Diagnostics: Where Search Goes Dry

The stronger harness is:

```clojure
query-stage-diagnostics
```

It sweeps stage `0`, `1`, `2`, ... up to the requested `call-depth`, runs one
or more raw-limit slices at each stage, and reports whether that stage is still
productive at all. The diagnostics also report the searched formula, the
remaining `unfold-depth` (now `0` in the default path), and the kernel
`call-depth` used at that stage.

For `reverse([a,b], r)` at the committed diagnostics slice used by
`answers_test.clj`, both stage `0` and stage `1` remain productive:

```clojure
{:stage 0, :productive? true, :raw-count 1, :unique-count 1}
{:stage 1, :productive? true, :raw-count 1, :unique-count 1}
```

That matters for one reason: the raw kernel search is still very much alive
under the public closed answer surface. ADR-0013 did not remove stage
diagnostics or claim that the kernel alone now cheaply materializes every known
reverse answer. Instead, the branch:

- kept the raw symbolic frontier visible for investigation,
- normalized and merged those frontiers more honestly,
- and then reused the ADR-0012 list-family materializer so the public answer
  API can return the closed answer directly.

## Bounded Ground Materialization

The non-generic helper can still materialize a concrete answer when requested.

Current example:

```clojure
query-ground-answers win(x) => [1]
```

So the first small winning Nim position recovered by bounded materialization is
`1`.

## Closed-Answer Parity Mode

ADR-0012 still provides the separate helper:

```clojure
query-parity-answers
```

ADR-0013 changed the relationship between the two answer APIs:

- `query-answers` now reuses the same list-family materializer for recognized
  `append/3` and `reverse/2` queries, so for those known families the generic
  API and parity mode agree on the closed answer surface,
- `query-parity-answers` still remains the explicit closed-answer-only API and
  bounded fallback materializer for callers that want extensional behavior by
  contract.

Two boundaries still matter:

- parity mode requires fully empty residuals,
- and on the list-family fast path it leaves `:proofs` empty on purpose.

That is not a bug. The proof authority for these concrete list cases remains the
direct semantic regressions in `test/proflog/list_programs_test.clj`. The parity
mode is now less necessary for the known reverse/append list families, but it
still serves a distinct architectural role as the explicit closed-answer API.

## Worked Example: Classifying An Answer-Stream Problem

ADR-0014 needs a disciplined way to talk about hard legacy queries. The right
question is not just "does the API show the answer?" but:

```text
At which layer does the desired answer first exist?
```

For the current reverse list-family case:

```clojure
reverse([a,b], r)
```

the classification is:

### Layer 1: Raw Kernel Stream

The raw diagnostics still show symbolic frontiers such as:

```clojure
{:bindings [[r []]]
 :residuals
 [[a,b] != []
  not append(a_3, [a], [])
  not reverse([b], a_3)]}
```

and one step deeper:

```clojure
{:bindings [[r [a]]]
 :residuals
 [[a] != []
  [a,b] != []
  not reverse([b], [])
  not reverse([b], [])]}
```

So the raw kernel stream is productive, but the desired closed answer
`r = [b,a]` is not what the current raw answer export is surfacing here.

### Layer 2: Generic Post-Processing

Generic post-processing means family-independent answer-stream work such as:

- alpha-equivalent merge,
- residual dedup,
- completion ranking,
- closed-answer-only filtering,
- generic bounded replay of candidate answers,
- or fairer stream slicing.

For the current reverse list-family case, ADR-0013 improved this layer:

- duplicate residuals collapse,
- alpha-equivalent symbolic frontiers merge,
- and the answer surface is ranked more honestly.

But that generic post-processing is still not what produces the closed
`r = [b,a]` answer.

### Layer 3: Specialty Handling

The public `query-answers` answer:

```clojure
{:bindings [[r [b,a]]]
 :residuals []
 :proofs []}
```

comes from the recognized list-family materializer. It is synthesized above the
kernel from extensional query shape, which is why the record carries empty
`:proofs`.

That means:

- the current public parity result is not a raw kernel-stream witness,
- and it is not merely the result of generic stream sifting either,
- it is a specialty answer-layer completion policy for a known family.

### Important Distinction

That is different from saying the kernel knows nothing about the query.

For the same family, the repo separately shows that the ground semantic query:

```clojure
reverse([a,b], [b,a])
```

is provable. So there are two different claims:

1. the kernel can prove the ground instance semantically,
2. the generic open answer stream exported the closed answer directly.

For the current reverse list-family case:

- claim `1` is true,
- claim `2` is false,
- and the current public closed answer comes from specialty extra-kernel
  handling.

## Reusable ADR-0014 Template

For each promoted hard legacy query, ADR-0014 should record the result in this
shape:

```text
Query:
Desired answer:

Layer 1: Raw kernel stream
- present / late / absent within measured bounds
- exact budgets and first observed frontier

Layer 2: Generic post-processing
- enough / not enough
- which generic transformations were tried

Layer 3: Specialty handling
- unnecessary / still unnecessary / currently required
- if required, what family knowledge it used

Current conclusion:
- raw-stream present
- late but generically recoverable
- only recoverable by specialty handling
- or still absent
```

That template is meant to stop future hard-family work from collapsing into the
undifferentiated statement "the query is too slow."

## Worked Example: Pure-Core Raw Stream Probes

ADR-0014 also needs a way to bypass the public answer overlays entirely and ask
what the raw kernel/export path can produce on its own. The helper for that is:

```clojure
proflog.legacy-stream-probe
```

It does three deliberate things:

- bypasses `query-answers`,
- bypasses the list-family fast path,
- and calls the raw kernel/export path directly with `fuel=nil`.

Example commands:

```bash
timeout -k 30s 900s lein run -m proflog.legacy-stream-probe reverse-open 1 128
timeout -k 30s 900s lein run -m proflog.legacy-stream-probe reverse-open nil 64
timeout -k 30s 900s lein run -m proflog.legacy-stream-probe append-forward nil 64
timeout -k 30s 900s lein run -m proflog.legacy-stream-probe append-inverse nil 128
```

Here the parameters mean:

- probe name: one fixed query family shape,
- `call-depth`: recursive descent budget below the top-level query entry, with
  `nil` meaning unbounded descent,
- max raw limit: the doubling schedule cap `1, 2, 4, ...`.

Measured results on `2026-04-24`:

- `reverse([a,b], r)`, `fuel=nil`, `call-depth=1`:
  - exhausts by raw limit `8`,
  - emits `5` raw proof states and `4` unique exported symbolic records,
  - never exports the closed witness `r = [b,a]`.
- `reverse([a,b], r)`, `fuel=nil`, `call-depth=2`, `3`, `4`, and `nil`:
  - reaches raw limits `1`, `2`, and `4`,
  - exports no closed witness,
  - and the next raw slice fails to complete within the fifteen-minute timeout.
- `append([a], [b,c], z)`, `fuel=nil`, `call-depth=nil`:
  - exhausts by raw limit `8`,
  - emits `6` raw proof states and `3` unique exported records,
  - never exports the closed witness `z = [a,b,c]`.
- `append(x, y, [a,b,c])`, `fuel=nil`, `call-depth=nil`:
  - exports the correct base split `x = [], y = [a,b,c]` immediately,
  - still has not exported the other three closed legacy splits by raw limit
    `8`,
  - and the next raw slice fails to complete within the fifteen-minute timeout.

So this probe runner answers a narrower question than the public APIs do:

- not "can the repo show the closed answer somehow?",
- but "can the pure-core raw kernel/export path surface the closed witness
  under these measured bounds?"

For the current list-family reverse / append cases, the answer remains:

- raw pure-core access: yes,
- full closed synthesis parity from that raw stream within the measured slice:
  no.

## Source To Kernel Descent

The answer API examples use the same frontend/backend descent as the quickstart,
then switch to the answer-overlay entry point described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

For a source query:

```prolog
reverse([a, b], r)?
```

the frontend `run` form keeps `r` as an answer variable rather than a constant
and evaluates through the public answer API:

```clojure
(pf/run list-program [r]
  (reverse (cons a (cons b null)) r)
  {:fuel fuel
   :call-depth call-depth
   :max-raw-proof-limit raw-limit})
```

The schematic query formula produced inside that call is:

```clojure
(pos
  (app reverse
       (app cons (app a) (app cons (app b) (app null)))
       (var r)))
```

For diagnostics, the lower-level builder exposes the query and exported-variable
vector without evaluating:

```clojure
(pf/answer-query [r]
  (reverse (cons a (cons b null)) r))
;; => {:query ...
;;     :answer-vars [r]}
```

The exported variable vector is part of the API contract: only those logic
variables are turned into public answer bindings.
Residuals are compiled formulas or calls that remain after the admitted proof
slice, and proof terms show how each record was obtained.

When this file distinguishes raw-kernel streams, generic post-processing, and
specialty materializers, it is documenting a real implementation boundary. The
source program has already become compiled Proflog formulae in all three cases;
the difference is how much answer export and family-specific materialization is
allowed after the proof states are produced.
