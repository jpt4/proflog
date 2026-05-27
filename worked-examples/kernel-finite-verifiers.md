# Kernel Finite Verifier Examples

This page records the ADR-39 finite equality-fragment verifier examples. The
implementation is generic: it proves compiled call-free equality formulas and
does not dispatch on group-verifier or transition-system names.

Source:

- `src/proflog/kernel/equality_fragment.clj`
- `src/proflog/finite_transition_systems.clj`
- `test/proflog/kernel_finite_verifiers_test.clj`

Focused command:

```text
timeout -k 5s 360s lein test-proflog-kernel-finite-verifiers
```

Current local result:

```text
Ran 3 tests containing 51 assertions.
0 failures, 0 errors.
```

## Evaluation Process

Each public query is compiled as an ordinary Proflog nullary relation. The test
then asks the core query API for a proof of that proposition, or a proof of its
failure:

```clojure
(query/query-succeeds program query 1 16)
(query/query-fails program query 1 16)
(query/query-status program query
                    {:timeout-ms 20000
                     :proof-limit 1
                     :poll-ms 0})
```

At `kernel/prove-program`, the proof entry expands the nullary procedure call.
If the expanded body is a call-free equality fragment, the ADR-39 profile tries
to close that body and returns a proof under:

```clojure
(profiled equality-fragment ...)
```

Program propositions retain the ordinary procedure-call evidence around the
profiled proof:

```clojure
(pos-call (profiled equality-fragment ...))
(neg-call (profiled equality-fragment ...))
```

The profile is intentionally narrow. It accepts formulas built from `true`,
`false`, `eq`, `neq`, `and`, `or`, `forall`, `once-forall`, and `exists`.
Active procedure calls in the expanded body are rejected, so recursive Proflog
programs and non-equality reasoning continue through the full kernel.

The hard-family overlay is redefined to throw in the group-verifier tests, and
the equality-fragment implementation is audited for references to `gv-probe`,
`finite-transition-systems`, and `hard-family-overlay`.

## Group Verifier

The group verifier is defined by compiling a finite multiplication table and a
candidate associativity law into ordinary Proflog clauses. A full associativity
truth row has the shape:

```text
gv_assoc():
  forall x.
  forall y.
  forall z.
  forall xy.
  forall yz.
  forall lhs.
  forall rhs.
    not_mul(x, y, xy)
    or not_mul(y, z, yz)
    or not_mul(xy, z, lhs)
    or not_mul(x, yz, rhs)
    or lhs = rhs
```

For a group, every assignment that follows the table forces `lhs = rhs`, so the
positive proposition succeeds. For a non-group, the positive proposition fails
because there is a concrete finite assignment where the four table lookups hold
and `lhs` and `rhs` differ. The failure query is still proof-producing:
Proflog proves the negated proposition by finding and closing that finite
counterexample.

The mandatory ADR-39 group-verifier rows now close through the profiled kernel
component:

| Case | Query mode | Outcome | Proof evidence |
| --- | --- | --- | --- |
| Z1 full seven-universal associativity | forward truth | succeeds | `pos-call` + `profiled equality-fragment` |
| Z2 precomputed associativity | forward truth | succeeds | `pos-call` + `profiled equality-fragment` |
| Z2 full seven-universal associativity | forward truth | succeeds | `pos-call` + `profiled equality-fragment` |
| non-group precomputed associativity | refutation | fails | `neg-call` + `profiled equality-fragment` |
| non-group full seven-universal associativity | refutation | fails | `neg-call` + `profiled equality-fragment` |

The full rows are the important change. They were not treated as stretch goals:
ADR-39 cannot close unless both `Z2` full success and non-group full failure
remain green.

The focused test exercises the rows by asking both for status and for proof
evidence:

```clojure
(doseq [[scenario expected]
        [["z1-full-assoc-truth" :succeeds]
         ["z2-precomputed-assoc-truth" :succeeds]
         ["z2-full-assoc-truth" :succeeds]
         ["non-group-precomputed-assoc" :fails]
         ["non-group-full-assoc" :fails]]]
  (assert-gv-status scenario expected))
```

## Transition Systems

The non-GV examples use a larger DFA-like transition table, intentionally named
as a `delta` table in the examples:

- four states: `q0`, `q1`, `q2`, `q3`;
- three symbols: `a`, `b`, `c`;
- twelve complete deterministic transitions;
- one incomplete variant with a missing transition; and
- one nondeterministic variant with an extra conflicting transition.

The complete table is deliberately large enough to avoid a smoke-test-only
demonstration:

```clojure
[['q0 'a 'q1]
 ['q0 'b 'q2]
 ['q0 'c 'q3]
 ['q1 'a 'q1]
 ['q1 'b 'q3]
 ['q1 'c 'q0]
 ['q2 'a 'q3]
 ['q2 'b 'q0]
 ['q2 'c 'q2]
 ['q3 'a 'q0]
 ['q3 'b 'q1]
 ['q3 'c 'q2]]
```

The verified laws are:

```text
delta_total():
  every state/symbol pair has some target state

delta_deterministic():
  any two targets for the same state/symbol pair are equal
```

The source definitions compile those laws into quantified equality formulas.
In outline, totality is:

```text
forall q, sym.
  q not in states
  or sym not in symbols
  or exists target.
       target in states
       and delta(q, sym, target)
```

The incomplete variant removes `[q3 c q2]`, so the finite assignment
`q = q3`, `sym = c` refutes totality. Determinism is:

```text
forall q, sym, t1, t2.
  q not in states
  or sym not in symbols
  or t1 not in states
  or t2 not in states
  or not delta(q, sym, t1)
  or not delta(q, sym, t2)
  or t1 = t2
```

The nondeterministic variant adds `[q0 a q2]` to the complete table. The
finite assignment `q = q0`, `sym = a`, `t1 = q1`, `t2 = q2` refutes
determinism.

Promoted rows:

| Case | Query mode | Outcome | Proof evidence |
| --- | --- | --- | --- |
| complete deterministic `delta_total()` | forward truth | succeeds | `pos-call` + `profiled equality-fragment` |
| complete deterministic `delta_deterministic()` | forward truth | succeeds | `pos-call` + `profiled equality-fragment` |
| incomplete `delta_total()` | refutation | fails | `neg-call` + `profiled equality-fragment` |
| nondeterministic `delta_deterministic()` | refutation | fails | `neg-call` + `profiled equality-fragment` |

These examples exercise the same finite equality-fragment machinery as GV, but
they are not algebra/group examples.

The test also enforces the intended size floor:

```clojure
(is (>= (count (:states transition/complete-deterministic-spec)) 4))
(is (>= (count (:symbols transition/complete-deterministic-spec)) 3))
(is (>= (count (:transitions transition/complete-deterministic-spec)) 12))
```

This matters because the determinism row compares two full table lookups. The
successful complete case proves that every pair of matching transitions forces
the same target; the nondeterministic case proves that the same machinery can
find a conflicting pair.

## Correctness Checks

The focused verifier suite checks three kinds of evidence:

1. Status: each row returns the expected `:succeeds` or `:fails` status within
   the focused timeout.
2. Proof shape: every promoted row contains both `profiled` and
   `equality-fragment`, and no proof contains `hard-family-overlay`.
3. Genericity: the production equality-fragment source has no direct reference
   to the GV probes, the transition-system fixtures, or the hard-family
   overlay.

The Fitting suite also consumes the promoted GV rows, so these examples are not
only isolated ADR-39 probes. They are part of the tutorial-level catalog of
non-trivial Proflog programs.

## Shortcomings

The focused suite is intentionally slower than routine tests. The transition
determinism row is large enough to be meaningful, but it also dominates runtime
because it compares two full table lookups over a twelve-row delta relation.

The component is a finite equality-fragment prover, not a complete replacement
for the full relational kernel. General procedure-call recursion, open answer
synthesis, and non-equality first-order reasoning still belong to the existing
kernel and answer-overlay paths.

## ADR-0057 Relation-Backed Parity Route

ADR-0057 adds an opt-in relation-backed finite route:

```clojure
(equality-fragment/prove-program-relational program query 1 16 :succeeds)
(equality-fragment/prove-program-relational program query 1 16 :fails)
```

The last argument names the public query outcome being demonstrated. For
`:succeeds`, the route proves the negated query formula; for `:fails`, it proves
the query formula itself. Returned proof evidence is compact:

```clojure
(pos-call (profiled relational-equality-fragment relational-proof))
(neg-call (profiled relational-equality-fragment relational-proof))
```

The compact marker means the finite route found an internal relational proof,
but does not reify the whole equality-fragment proof tree. That matters for the
larger transition rows, where full proof trees are very large.

The ADR-0057 comparison probe passed with `real 106.21 s`:

| Row | Host ms | Relation-backed ms |
|---|---:|---:|
| `z1-full-assoc-truth` | `7.790` | `203.453` |
| `z2-precomputed-assoc-truth` | `14.162` | `83.748` |
| `z2-full-assoc-truth` | `19.938` | `667.185` |
| `non-group-precomputed-assoc` | `83.473` | `91.309` |
| `non-group-full-assoc` | `13.244` | `87.405` |
| `complete-delta-total` | `8283.238` | `6884.622` |
| `complete-delta-deterministic` | `151.795` | `4366.812` |
| `incomplete-delta-total` | `25.260` | `18.374` |
| `nondeterministic-delta-deterministic` | `7327.128` | `60515.029` |

The focused ADR-0057 selector passed with `Ran 5 tests containing 32
assertions`, `0 failures, 0 errors`, `real 82.97 s`. This establishes
completion parity with the production equality-fragment route, not uniform
duration parity.

## Source To Kernel Descent

These examples are the clearest promoted use of a profiled kernel component.
They still follow the frontend/backend descent described in
[Frontend To Kernel Descent](./frontend-to-kernel-descent.md).

Each verifier builds a normal compiled Proflog program. A group associativity
row, for example, is exposed as a nullary relation:

```prolog
assoc() :- forall x. forall y. forall z.
             finite-table-associativity-body(x, y, z).
```

A transition-system determinism row is the same shape:

```prolog
delta-deterministic() :- forall s. forall symbol.
                           forall t1. forall t2.
                             matching-delta-rows(s, symbol, t1, t2)
                             -> t1 = t2.
```

The prefix frontend equivalent would use `|-` for the nullary verifier
relation and inline finite table bodies with `:=` where those helpers are not
intended to be runtime procedure calls. The backend query for each row is:

```clojure
(pos (app verifier-name))
```

At `kernel/prove-program`, `prog` is the compiled finite table program, `fml`
is the nullary verifier query or its negation, `n` is the requested proof count,
and `fuel` is the focused-suite slice. The equality-fragment profile is allowed
only after procedure calls have expanded to finite equality/disequality
structure. Its proof tag is therefore evidence of a kernel-level finite formula
profile, not a group-specific or transition-specific evaluator.
