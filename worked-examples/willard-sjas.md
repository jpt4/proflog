# Willard SJAS Base-64 Coding and Substitution-Proof Profile Example

This example documents ADR-0058 through ADR-0071 and the focused regression in
`test/proflog/willard_sjas_test.clj`. It demonstrates the Willard-style SJAS
builder, binary U-grounding arithmetic, reflected axiom Godel-code terms, and
kernel-checked proof certificates. ADR-0062 made the self-consistency
demonstration non-vacuous; ADR-0063 then removed the remaining host-side
proof-target table by making `tableau-proof/3` consume compact base-64 code
terms rather than opaque formula labels. ADR-0064 adds the Level-1
`subst-prf/4` predicate, so generated `SelfCons1` formulas now use the
substitution-proof vocabulary rather than raw `tableau-proof/3`. ADR-0065
corrects the fixed-point substitution argument: Level-1 Group-3 now cites the
code of its own `Gamma_1(g)` skeleton, not the system code. ADR-0066 exposes
that finite substitution boundary as the `subst-code/2` object-language
relation. ADR-0067 adds a structural decoder so syntax, formula-class,
negation-pair, and identity-substitution predicates can inspect well-formed
formula codes that were not pre-enumerated as generated axioms. ADR-0068 lets
`tableau-proof/3` and `subst-prf/4` build proof targets from decoded theorem
codes as well. ADR-0069 replaces the generated substitution table with
structural diagonal substitution over decoded formula codes. ADR-0071 adds the
stronger U-Grounding code format, in which public formula, system, and proof
codes are binary numerals in the SJAS base language rather than generated
`code-N` constructors.

Run the focused regression:

```text
lein test-proflog-sjas
```

Current result:

```text
Ran 35 tests containing 221 assertions.
0 failures, 0 errors.
real 1687.83 s
```

The explicit slow fixed-point selector is also available:

```text
lein test-proflog-sjas-slow
;; Ran 5 tests containing 22 assertions.
;; real 746.91 s
```

## Hand-Written Intent

The object language now uses binary numeral constants:

```text
language:
  constants 0, 1
  total function symbols add, dbl, pred, sub, div, max, log, root, count
  relation symbols mult(x, y, z), leq(x, y), lt(x, y)

user beta:
  1 = 1

reflected program clause:
  demo(x) :- x = 1

external application clause:
  external-demo(x) :- x = 0
```

Larger numerals are terms, not constants. For example, the Clojure helper
`sjas/three` builds this object-language term:

```clojure
(sjas/numeral 3)
;; => (app add (app dbl (app 1)) (app 1))
```

The reflected `demo` clause is part of the generated finite SJAS basis. It is
encoded as a Group-2b user extension and changes the system id and Group-3
self-consistency formula. The external clause is ordinary Proflog code around
the SJAS; it can be queried, but it is not listed in `axiom-member` and does
not change the `SelfCons` claim.

## Frontend Builder

The source-facing builder accepts Clojure-readable prefix sections. Numeric
literals in this SJAS frontend lower to same-spelled object constants:

```clojure
(def source-system
  (sjas/system-source
    {:profile :willard-sjas-tableau0}
    (language
      (relations (demo 1)
                 (external-demo 1)))
    (beta
      (= 1 1))
    (reflected
      (|- (demo x)
          (= x 1)))
    (external
      (|- (external-demo x)
          (= x 0)))))
```

The lower-level builder also accepts backend formulas and clauses directly:

```clojure
(require '[proflog.ast :as ast]
         '[proflog.frontend :as frontend]
         '[proflog.query :as query]
         '[proflog.willard-sjas :as sjas])

(def beta
  (ast/eq-lit sjas/one sjas/one))

(def reflected-demo
  (ast/nom x
    (ast/clause 'demo [x]
      (ast/eq-lit (ast/var-term x) sjas/one))))

(def system
  (sjas/system
    {:profile :willard-sjas-tableau0
     :relations {'demo 1}
     :beta [beta]
     :reflected-clauses [reflected-demo]}))
```

The builder supplies stable formula-code terms, generated
`axiom-member(system, formula-code)` facts, Group-Zero through Group-3 records,
and the compiled program with the selected SJAS proof profile. A code is not a
hash-like constant; it is a first-order term of the shape
`(code-N b0 ... bN-1)`, where each `bi` is one base-64 byte written as a small
binary SJAS numeral.

## Query-Triggered Evaluation

Constructing an SJAS system does not itself search for proofs. It builds a
finite reflected theory plus an executable Proflog program. Evaluation begins
only when the caller asks a query.

A reflected-only program makes the user clause part of the SJAS basis:

```clojure
(def reflected-only
  (sjas/system-source
    {:profile :willard-sjas-tableau0}
    (language
      (relations (demo 1)))
    (beta
      (= 1 1))
    (reflected
      (|- (demo x)
          (= x 1)))))

(frequencies (map :group (:axioms reflected-only)))
;; => {:group-zero 2,
;;     :group-one 3,
;;     :group-two 1,
;;     :group-two-b 1,
;;     :group-three 1}
```

The system value above is only compiled data. A direct executable query starts
the ordinary Procedure Call Rule over the compiled program:

```clojure
(query/query-succeeds
  (:program reflected-only)
  (frontend/q (demo 1))
  1
  96)
;; => one proof
```

The SJAS theorem helper starts a different query: it asks the kernel to prove
the formula from the generated SJAS axiom basis. Because `demo` was reflected,
the same source clause is also represented as a Group-2b axiom formula.

```clojure
(sjas/query-succeeds
  reflected-only
  (frontend/q (demo 1))
  {:proof-limit 1
   :fuel 96})
;; => one proof
```

An external clause is still executable Proflog, but it is not part of the
reflected SJAS axiom basis:

```clojure
(def with-external
  (sjas/system-source
    {:profile :willard-sjas-tableau0}
    (language
      (relations (demo 1)
                 (external-demo 1)))
    (beta
      (= 1 1))
    (reflected
      (|- (demo x)
          (= x 1)))
    (external
      (|- (external-demo x)
          (= x 0)))))

(= (:system-code reflected-only) (:system-code with-external))
;; => true

(= (:code (:group-three reflected-only))
   (:code (:group-three with-external)))
;; => true
```

The external clause does not change the system code or the generated Group-3
self-consistency claim. It is still queryable as ordinary Proflog context:

```clojure
(query/query-succeeds
  (:program with-external)
  (frontend/q (external-demo 0))
  1
  96)
;; => one proof
```

It may also be used by ordinary procedure-call reasoning during an SJAS-wrapped
query, but it is not citeable by the internal `tableau-proof/3` predicate as an
`axiom-member` of this SJAS:

```clojure
(sjas/query-succeeds
  with-external
  (frontend/q (external-demo 0))
  {:proof-limit 1
   :fuel 96})
;; => one proof
```

## Group-2b Restrictions

Users do not add arbitrary Group-2b formulas directly in the current frontend.
They add `reflected` clauses, and the builder converts each clause

```text
head :- body
```

into the universally closed axiom formula:

```text
forall parameters. body -> head
```

Those reflected clauses are subject to the ordinary Proflog clause restrictions:

- the reflected extension is finite at system-construction time;
- clause heads are relation calls with variable-only parameters;
- function, constant, and relation symbols must be declared in the SJAS
  language or supplied by the SJAS base signature;
- arities must match their declarations;
- internal proof parameters such as `par` are not admissible in user source;
- source-level `:=` helpers may be used only as non-recursive inline
  abbreviations before a real `|-` clause is emitted.

The implementation does not currently prove that a reflected Group-2b clause is
true, conservative, or consistent before admitting it. It is a trusted finite
extension of the reflected system. Adding a false or explosive reflected clause
changes the theory, changes Group-3, and can make later queries succeed for the
wrong reason.

For Willard-aligned examples, reflected Group-2b clauses should therefore be
small, explicit, and kept within the intended arithmetic/proof-coding fragment.
Broader admissibility checks, such as rejecting nonconforming reflected
extensions before Group-3 generation, are a future hardening step rather than a
current guarantee.

## Composite: Beta Axiom vs Reflected Procedure

The `composite` examples below use a deliberately small witness definition:

```text
forall x. mult(2, 2, x) -> composite(x)
```

This proves that `4` is composite without turning the example into an open
factorization benchmark. The mathematically broader definition
`exists y z. y != 1 and z != 1 and mult(y,z,x)` is expressible, but the current
profile does not close that general factor-synthesis proof within the focused
test budget; an exploratory 120 s wrapper produced no result for that broader
form.

First, put the definition in Group 2 `beta`:

```clojure
(def beta-composite-system
  (sjas/system-source
    {:profile :willard-sjas-tableau0}
    (language
      (relations (composite 1)))
    (beta
      (forall [x]
        (implies
          (mult (dbl 1) (dbl 1) x)
          (composite x))))))

(frequencies (map :group (:axioms beta-composite-system)))
;; => {:group-zero 2,
;;     :group-one 3,
;;     :group-two 1,
;;     :group-three 1}
```

The theorem-level query succeeds because the Group 2 axiom can be used in the
generated SJAS basis:

```clojure
(sjas/query-succeeds
  beta-composite-system
  (frontend/q (composite (dbl (dbl 1))))
  {:proof-limit 1
   :fuel 64})
;; => one proof
```

The direct executable query does not succeed, because no `composite/1`
procedure clause exists:

```clojure
(query/query-succeeds
  (:program beta-composite-system)
  (frontend/q (composite (dbl (dbl 1))))
  1
  64)
;; => ()
```

Now put the same definition in `reflected`:

```clojure
(def reflected-composite-system
  (sjas/system-source
    {:profile :willard-sjas-tableau0}
    (language
      (relations (composite 1)))
    (reflected
      (|- (composite x)
          (mult (dbl 1) (dbl 1) x)))))

(frequencies (map :group (:axioms reflected-composite-system)))
;; => {:group-zero 2,
;;     :group-one 3,
;;     :group-two-b 1,
;;     :group-three 1}
```

The ordinary Procedure Call Rule can execute `composite/1`:

```clojure
(query/query-succeeds
  (:program reflected-composite-system)
  (frontend/q (composite (dbl (dbl 1))))
  1
  64)
;; => one proof
```

Because the clause is reflected as Group-2b, the SJAS theorem helper can also
prove the same claim from the reflected axiom basis:

```clojure
(sjas/query-succeeds
  reflected-composite-system
  (frontend/q (composite (dbl (dbl 1))))
  {:proof-limit 1
   :fuel 64})
;; => one proof
```

The executable version can also synthesize the answer:

```clojure
(ast/nom x
  (sjas/query-answers
    reflected-composite-system
    (ast/pos-lit (ast/app-term 'composite (ast/var-term x)))
    [x]
    {:proof-limit 1
     :fuel 64}))
;; first binding => x = (app dbl (app dbl (app 1)))
```

## Generated Kernel Shape

The base language contains only `0` and `1` as numeral constants:

```clojure
(contains? (:constants sjas/tableau0-profile-language) (symbol "0"))
;; => true

(contains? (:constants sjas/tableau0-profile-language) 'two)
;; => false
```

Multiplication is a relation, not a function:

```clojure
(get-in sjas/tableau0-profile-language [:functions 'mul])
;; => nil

(get-in sjas/tableau0-profile-language [:relations 'mult])
;; => 3
```

A generated system stores reflected axiom records:

```clojure
(map :group (:axioms system))
;; => (:group-zero :group-zero
;;     :group-one :group-one :group-one
;;     :group-two
;;     :group-two-b
;;     :group-three)
```

Group-1 arithmetic records are still reflected and code-addressable, but the
profile now treats U-grounding arithmetic as theory behavior. The theorem
helper therefore does not place Group-1 arithmetic equalities into every
ordinary branch as free-constructor equalities.

Generated code terms are visible at the object-language boundary:

```clojure
(require '[proflog.willard-sjas-code :as sjas-code])

(sjas-code/code-term? (:system-code system))
;; => true

(sjas-code/code-term-bytes (:code (:group-three system)))
;; => a base-64 byte vector for the generated Group-3 formula
```

ADR-0070 tightened this boundary: formula, system, and proof encoders now build
public `code-N` terms directly from byte strings. Natural-number views remain
available for diagnostics, but they are not used to reconstruct canonical
syntax/proof codes because base-64 natural conversion forgets trailing zero
bytes. This matters when a formula ends with an embedded code payload.

ADR-0071 adds a second public representation for the same byte strings:

```clojure
(def ug-system
  (sjas/system
    {:profile :willard-sjas-level1
     :code-format :u-grounding
     :relations {'demo 1}
     :beta [beta]
     :reflected-clauses [reflected-demo]}))

(sjas-code/u-grounding-code-term? (:system-code ug-system))
;; => true

(sjas-code/code-term? (:system-code ug-system))
;; => false

(some sjas-code/code-symbol-byte-count
      (keys (get-in ug-system [:language :functions])))
;; => nil
```

Here the formal code is one ordinary U-Grounding binary numeral built from
`0`, `1`, `dbl`, and `add`. The finite byte string is made injective by a final
sentinel byte, so `[1]` and `[1 0]` remain distinct after they are interpreted
as naturals:

```clojure
(let [code (sjas-code/bytes->u-grounding-code-term [1 0])]
  (sjas-code/u-grounding-code-term-bytes code))
;; => [1 0]
```

The syntax predicates consume those numeral codes directly:

```clojure
(let [formula (sjas/lt sjas/one sjas/two)
      code (sjas/formula-code ug-system formula)
      proof (first
              (query/query-succeeds
                (:program ug-system)
                (sjas/wff code)
                1
                160))]
(proof/contains-step? proof 'sjas-ug-code-bytes))
;; => true
```

When a code reaches `wff/1` through a logic binding, the relation-backed
fallback also records the radix-64 equation used to peel the byte stream:

```clojure
(ast/nom code-var
  (let [formula (ast/eq-lit sjas/one sjas/one)
        code (sjas/formula-code ug-system formula)
        code-term (ast/var-term code-var)
        proof (first
                (query/query-succeeds
                  (:program ug-system)
                  (ast/exists-form
                    code-var
                    (ast/and-form
                      (ast/eq-lit code-term code)
                      (sjas/wff code-term)))
                  1
                  220))]
    [(proof/contains-step? proof 'sjas-ug-code-byte-cons)
     (proof/contains-step? proof 'sjas-ug-code-mul64-shift)]))
;; => [true true]
```

The same representation is accepted by proof and substitution predicates:

```clojure
(let [beta-record (first (filter #(= :group-two (:group %))
                                 (:axioms ug-system)))
      certificate (sjas/proof-certificate
                    'sjas-axiom
                    {:code-format :u-grounding})]
  (query/query-succeeds
    (:program ug-system)
    (sjas/tableau-proof (:system-code ug-system)
                        (:code beta-record)
                        certificate)
    1
    200))
;; => one proof

(query/query-succeeds
  (:program ug-system)
  (sjas/subst-code (:selfcons-skeleton-code ug-system)
                   (:code (:group-three ug-system)))
  1
  240)
;; => one proof
```

The implementation deliberately does not use `project` inside the relation.
Already-ground public code terms are first destructured by root constructor to
avoid core.logic stack overflows while focusing very large numerals; the
resulting literal byte stream is then consumed by the structural formula/proof
relations. Open synthesis of large public code numerals is still a documented
operational boundary. The U-Grounding format is therefore the stronger semantic
demonstration path, while the compact `code-N` format remains the default
performance-oriented path.

The compiled program intentionally leaves `:sjas/proof-targets` nil. The proof
profile instead decodes `system-code`, `theorem-code`, and `proof-code` through
the generated code registry and the proof byte decoder. `SelfCons0` therefore
mentions a real contradiction code, not a magic table key; if an inconsistent
beta basis proves `false`, the resulting certificate can be checked against
`contradiction-code`.

## Arithmetic Evaluation

Closed arithmetic equations route through the SJAS profile:

```clojure
(query/query-succeeds
  (:program system)
  (ast/eq-lit (sjas/add-term (sjas/numeral 2)
                             (sjas/numeral 3))
              (sjas/numeral 5))
  1
  160)
;; => one profiled proof
```

The focused suite covers the U-grounding functions:

```text
add(2,3) = 5
dbl(6) = 12
pred(0) = 0
pred(5) = 4
sub(2,5) = 0
sub(7,3) = 4
div(7,0) = 7
div(7,3) = 2
max(4,9) = 9
log(1) = 0
log(8) = 3
root(10,2) = 4
root(8,3) = 2
count(13,4) = 3
```

It also checks graph/order relations beyond the old finite MVP facts:

```clojure
(query/query-succeeds
  (:program system)
  (sjas/mult (sjas/numeral 4) (sjas/numeral 3) (sjas/numeral 12))
  1
  160)
;; => one proof

(query/query-succeeds
  (:program system)
  (sjas/mult (sjas/numeral 4) (sjas/numeral 3) (sjas/numeral 11))
  1
  80)
;; => ()
```

Answer mode and partial synthesis use the SJAS answer wrapper so the answer
overlay receives the same profile arithmetic hook:

```clojure
(ast/nom x
  (sjas/query-answers
    system
    (sjas/mult (ast/var-term x) (sjas/numeral 3) (sjas/numeral 12))
    [x]
    {:proof-limit 1
     :fuel 160}))
;; first binding => x = (app dbl (app dbl (app 1)))

(ast/nom z
  (sjas/query-answers
    system
    (ast/eq-lit (sjas/add-term (ast/var-term z) (sjas/numeral 3))
                (sjas/numeral 7))
    [z]
    {:proof-limit 1
     :fuel 160}))
;; first binding => z = 4
```

## Certificate Predicate

`tableau-proof/3` no longer accepts a miniature `mini-closed` placeholder or a
host theorem-target label. A certificate is a compact base-64 object-language
encoding of a Proflog kernel proof term:

```clojure
(let [beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
      beta-proof (first
                   (sjas/query-succeeds system
                                        (:formula beta-record)
                                        {:proof-limit 1
                                         :fuel 96}))
      certificate (sjas/proof-certificate beta-proof)]
  (query/query-succeeds
    (:program system)
    (sjas/tableau-proof (:system-code system)
                        (:code beta-record)
                        certificate)
    1
    160))
;; => one proof
```

The same certificate is rejected for the wrong theorem code, and an unrelated
`refl-close` certificate is rejected for the beta theorem. Internally the
profile decodes the proof-code term and calls `kernel/prove-programo` with that
decoded proof supplied as the proof term, so the checker reuses the existing
pure relational tableau kernel instead of a host-side proof oracle.

The proof predicate also supports a formal axiom-citation proof line:

```clojure
(let [beta-record (first (filter #(= :group-two (:group %)) (:axioms system)))
      certificate (sjas/proof-certificate 'sjas-axiom)]
  (query/query-succeeds
    (:program system)
    (sjas/tableau-proof (:system-code system)
                        (:code beta-record)
                        certificate)
    1
    96))
;; => one proof
```

This is not a host theorem lookup. The checker decodes the proof code and then
checks the generated object-language `axiom-member(system-code, theorem-code)`
facts for the active reflected system. The same `sjas-axiom` certificate is
rejected for `contradiction-code` in the consistent demo system.

ADR-0062/0063 add two self-justification checks over real code terms:

```clojure
(let [group3-proof (first
                     (sjas/query-succeeds
                       system
                       (:formula (:group-three system))
                       {:proof-limit 1
                        :fuel 96}))
      certificate (sjas/proof-certificate group3-proof)]
  (query/query-succeeds
    (:program system)
    (sjas/tableau-proof (:system-code system)
                        (:code (:group-three system))
                        certificate)
    1
    160))
;; => one proof
```

This proves the generated self-consistency sentence as a theorem of the generated
SJAS and validates the resulting certificate against the Group-3 formula code.
That proof is expected to be axiom-like: Group-3 is one of the proper axioms of
the reflected system.

The contradiction code is tested with an intentionally inconsistent control
system:

```clojure
(def inconsistent-system
  (sjas/system
    {:profile :willard-sjas-tableau0
     :beta [(ast/false-form)]}))

(let [certificate (sjas/proof-certificate 'sjas-axiom)]
  (query/query-succeeds
    (:program inconsistent-system)
    (sjas/tableau-proof (:system-code inconsistent-system)
                        (:contradiction-code inconsistent-system)
                        certificate)
    1
    160))
;; => one proof
```

The point of the control is not to recommend false beta axioms. It demonstrates
that the system's `:contradiction-code` denotes a real proof target: when the
reflected basis explicitly includes `false`, the profile can check an
object-language axiom citation for that contradiction code.

## Substitution-Proof Predicate

ADR-0064 adds the Level-1 proof predicate used by the generated Group-3
sentence:

```text
subst-prf(system-code, substitution-code, theorem-code, proof-code)
```

ADR-0069 makes `subst-code/2` a structural formula-code relation rather than a
generated substitution table. It decodes the source code, substitutes the
source's own quoted code term for free variable `v0`, respects binder
shadowing, and compares the target formula modulo bound-variable
alpha-renaming:

```text
subst-code(source-code, substituted-code)
```

The public predicate is still important because it gives the Level-1 formula
the right object-language vocabulary while remaining a kernel/profile relation
over object-language terms.

The focused test exercises the path with a real certificate:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      beta-record (first (filter #(= :group-two (:group %))
                                 (:axioms level1-system)))
      beta-proof (first
                   (sjas/query-succeeds
                     level1-system
                     (:formula beta-record)
                     {:proof-limit 1
                      :fuel 96}))
      certificate (sjas/proof-certificate beta-proof)]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-prf (:system-code level1-system)
                    (:code beta-record)
                    (:code beta-record)
                    certificate)
    1
    160))
;; => one proof
```

The same test rejects an invalid theorem code, rejects `system-code` as a
substitution source, and rejects a malformed `refl-close` certificate for the
beta theorem. A structural check also verifies that the generated Level-1
Group-3 formula contains `neg-pair/2` and `subst-prf/4`, but no raw
`tableau-proof/3`.

ADR-0065 adds the fixed-point check. The generated Level-1 system exposes the
code of the skeleton `Gamma_1(g)`:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      group3-record (:group-three level1-system)]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-code (:selfcons-skeleton-code level1-system)
                     (:code group3-record))
    1
    96))
;; => one proof
```

The identity case is also a formula-code identity, not a system-code
placeholder:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      beta-record (first (filter #(= :group-two (:group %))
                                 (:axioms level1-system)))]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-code (:code beta-record)
                     (:code beta-record))
    1
    96))
;; => one proof
```

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      group3-record (:group-three level1-system)
      certificate (sjas/proof-certificate 'sjas-axiom)]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-prf (:system-code level1-system)
                    (:selfcons-skeleton-code level1-system)
                    (:code group3-record)
                    certificate)
    1
    220))
;; => one proof
```

The same query with `(:system-code level1-system)` in the substitution-code
position returns `()`. The point of the check is that `SelfCons1` is not merely
using a proof predicate with four arguments; it is using the fixed-point
substitution shape required by Willard's `Gamma_1(n)` construction.

The general substitution regression uses a formula that is not a generated
Group axiom:

```text
wff(v0)
```

Its source and target codes are built directly from canonical formula syntax:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      source-code (sjas-code/canonical-formula-code-term
                    (:coding-context level1-system)
                    '(pos (app wff (var v0))))
      target-code (sjas-code/canonical-formula-code-term
                    (:coding-context level1-system)
                    (list 'pos (list 'app 'wff source-code)))]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-code source-code target-code)
    1
    240))
;; => one proof
```

The identity query for the same open source now fails:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      source-code (sjas-code/canonical-formula-code-term
                    (:coding-context level1-system)
                    '(pos (app wff (var v0))))]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-code source-code source-code)
    1
    160))
;; => ()
```

A formula whose `v0` is bound by a quantifier remains an identity
substitution, demonstrating shadowing:

```text
forall v0. wff(v0)
```

ADR-0066 additionally checks that `subst-prf/4` uses the substitution code
independently of the theorem code:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      beta-record (first (filter #(= :group-two (:group %))
                                 (:axioms level1-system)))
      certificate (sjas/proof-certificate 'sjas-axiom)]
  (query/query-succeeds
    (:program level1-system)
    (sjas/subst-prf (:system-code level1-system)
                    (:selfcons-skeleton-code level1-system)
                    (:code beta-record)
                    certificate)
    1
    220))
;; => one proof
```

Here the substitution code maps to Group-3, while the theorem being proved is
the beta axiom. This matches the separation between `Subst(g,h)` and
`SubstPrf(g,t,p)`.

## Structural Formula-Code Predicates

ADR-0067 removes a registry-only behavior from the code predicates. A caller
can now construct the Godel code for a closed formula that is valid in the
active SJAS language but not one of the generated Group axioms, and the profile
will parse the code bytes directly.

The regression uses the formula:

```text
lt(1, 2)
```

In Clojure:

```clojure
(let [level1-system (demo-system :willard-sjas-level1)
      formula (sjas/lt sjas/one sjas/two)
      code (sjas/formula-code level1-system formula)
      complement-code (sjas/formula-code
                        level1-system
                        (normalize/negate-formula formula))]
  [(query/query-succeeds
     (:program level1-system)
     (sjas/wff code)
     1
     32)
   (query/query-succeeds
     (:program level1-system)
     (sjas/delta-star-0-code code)
     1
     32)
   (query/query-succeeds
     (:program level1-system)
     (sjas/neg-pair code complement-code)
     1
     48)
   (query/query-succeeds
     (:program level1-system)
     (sjas/subst-code code code)
     1
     48)])
;; => four non-empty proof result slices
```

This matters because `lt(1,2)` is not generated as a Group axiom. Before
ADR-0067, all four queries failed because the code was absent from the finite
formula registry. After ADR-0067, the profile decodes formula tags, term tags,
symbol indexes, arities, and complements from the code bytes themselves.

The test is marked `^:slow`; the focused isolated passing run took
`real 98.85 s`.

## Structural Theorem-Code Proof Targets

ADR-0068 applies the structural code route to proof targets. The same
non-generated arithmetic theorem can now be used as the theorem code for
`tableau-proof/3`:

```clojure
(let [system (demo-system :willard-sjas-tableau0)
      theorem (sjas/lt sjas/one sjas/two)
      theorem-code (sjas/formula-code system theorem)
      theorem-proof (first
                      (sjas/query-succeeds
                        system
                        theorem
                        {:proof-limit 1
                         :fuel 96}))
      certificate (sjas/proof-certificate theorem-proof)]
  (query/query-succeeds
    (:program system)
    (sjas/tableau-proof (:system-code system)
                        theorem-code
                        certificate)
    1
    180))
;; => one proof
```

The proof predicate no longer has to find `theorem-code` in the generated
formula registry. It decodes the theorem code, computes the complement of
`lt(1,2)`, converts that complement to a kernel formula, and asks the core
kernel to validate the supplied certificate against the usual
`axiom-basis AND not(theorem)` target.

The negative control replaces the theorem code with `lt(2,1)` and returns `()`
for the same certificate.

The substitution-proof predicate uses the same structural theorem route for
identity substitution:

```clojure
(let [system (demo-system :willard-sjas-level1)
      theorem (sjas/lt sjas/one sjas/two)
      theorem-code (sjas/formula-code system theorem)
      theorem-proof (first
                      (sjas/query-succeeds
                        system
                        theorem
                        {:proof-limit 1
                         :fuel 96}))
      certificate (sjas/proof-certificate theorem-proof)]
  (query/query-succeeds
    (:program system)
    (sjas/subst-prf (:system-code system)
                    theorem-code
                    theorem-code
                    certificate)
    1
    220))
;; => one proof
```

The focused passing runs took `real 111.13 s` for `tableau-proof/3` and
`real 175.84 s` for `subst-prf/4`.

## Bounded Contradiction Probe

The focused suite keeps the Level-1 contradiction probe bounded and concrete.
It checks that a chosen certificate candidate does not prove the generated
contradiction code and records the duration in the result map:

```clojure
(sjas/bounded-contradiction-probe system {:fuel 4 :proof-limit 1})
;; => {:result :not-found, :fuel 4, :proof-limit 1, :duration-ms ...}
```

Open proof-code synthesis remains a harder extended search problem. The focused
probe is a regression guard for the checker boundary, not evidence of Willard's
external consistency-preservation metatheorem.

## Shortcomings

- The proof-code encoding covers the current Proflog kernel proof-term language
  used by these examples. It is not a byte-for-byte formalization of every
  historical Willard proof-list encoding.
- Tab-1/proof-list theorem reuse is not implemented or claimed. The Level-1
  profile reflects plain semantic tableaux as the deduction method `D`.
- `subst-code/2` now computes diagonal substitution structurally for decoded
  formula codes. It is correct but slow for large formulas.
- Proof predicates now decode ordinary non-generated theorem codes into kernel
  targets, but the checker still reuses Proflog's kernel AST proof engine after
  decoding. There is not yet a separate proof-list/Tab-1 theorem-reuse checker
  operating wholly on code terms.
- The self-justification demonstration is non-vacuous at the proof-predicate
  boundary, but it is still a finite `IS#_D(beta)`-style executable substrate,
  not a mechanized proof of Willard's consistency-preservation theorem.
- The arithmetic and code profiles are relational, but some reverse and
  code-decoding modes are still operationally expensive. The slow selector now
  includes the fixed-point certificate, structural decoder, structural
  `tableau-proof/3`, structural `subst-prf/4`, and general `Subst` tests and
  passed with `real 915.85 s`; open certificate synthesis remains outside the focused
  suite.
- Passing bounded contradiction probes do not prove Willard's external
  consistency-preservation theorem.
