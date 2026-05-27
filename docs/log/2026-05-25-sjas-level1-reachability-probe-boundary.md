# SJAS Level-1 Proof-Constructor Reachability Probe Boundary

Date: 2026-05-25

Branch: `adr-0073-sjas-correspondence-program`

## Purpose

This note follows up the remaining reachability item from
[SJAS Proof-Constructor Reachability Audit](2026-05-25-sjas-proof-constructor-reachability-audit.md):
inventory Level-1 `subst-prf/4` and fixed-point certificates. The result is a
negative process finding rather than a completed constructor inventory: the
obvious broad Level-1 theorem-proof probe is too expensive to use as an ad hoc
Track 2a evidence path.

## Attempted Probe

The probe tried to collect first proofs and run
`proflog.sjas-correspondence/audit-proof-term` for the Level-1 demo system's
Group-2 beta theorem and generated Group-3 theorem:

```bash
lein with-profile test trampoline run -m clojure.main -e \
  "(require '[proflog.willard-sjas-test :as t]
            '[proflog.willard-sjas :as sjas]
            '[proflog.sjas-correspondence :as corr])
   (let [demo-system @#'proflog.willard-sjas-test/demo-system
         system (demo-system :willard-sjas-level1)
         cases {:beta (:formula (first (filter #(= :group-two (:group %))
                                               (:axioms system))))
                :group3 (:formula (:group-three system))}
         result (into {}
                      (for [[k f] cases
                            :let [p (first (sjas/query-succeeds
                                             system f
                                             {:proof-limit 1
                                              :fuel 96}))]]
                        [k {:proof p
                            :audit (when p (corr/audit-proof-term p))}]))]
     (prn result))"
```

The command produced no constructor inventory after several minutes and was not
retained as a useful verifier. No OS-level `lein`, `clojure`, or `java` process
remained after the attempt. The broad probe is therefore recorded as an
impractical Track 2a route, not as evidence about which constructors are or are
not reachable.

## Why This Matters

Level-1 fixed-point proofs are relevant for the full SJAS program because the
Level-1 self-consistency sentence uses `subst-code/2` and `subst-prf/4`, not
only `tableau-proof/3`. The Track 2b correspondence theorem cannot restrict its
attention to Tableau-0 proof constructors unless the final claim is explicitly
limited to Tableau-0.

However, an expensive theorem-search probe is the wrong mechanism for the next
piece of Track 2a evidence. It conflates two questions:

1. Which constructors can occur in Level-1 proof certificates?
2. How expensive is it to find those certificates through the current query
   surface?

Track 2a needs the first answer. The failed broad probe mostly demonstrates the
second problem.

## Required Narrower Evidence Path

The next Level-1 reachability audit should use one of these narrower paths:

1. Start from already-tested Level-1 `sjas-axiom` `subst-prf/4` calls and audit
   the proof returned by proof-predicate validation, rather than asking
   `sjas/query-succeeds` to synthesize a Level-1 theorem proof first.
2. Add a slow, explicitly tagged reachability test that inventories only a
   bounded, preselected Level-1 certificate shape and is kept out of the fast
   suite.
3. Expose a helper that decodes a supplied proof certificate and reports
   constructor classifications without invoking the full theorem search path.

The third path is likely the best Track 2a/Track 1 bridge: it would make the
proof-code constructor inventory available independently of the expensive
kernel validation step, while still keeping the actual correspondence theorem
for Track 2b.

## Current Classification Impact

This probe does not change the existing Track 2a classification. It leaves
Level-1 substitution proof machinery as relevant and unresolved:

- `subst-code/2` and `subst-prf/4` remain relevant because they implement the
  Level-1 fixed-point substitution apparatus.
- The reachable constructor set for Level-1 non-`sjas-axiom` certificates
  remains unknown.
- Track 2b must either include Level-1 constructor reachability in its proof
  obligations or explicitly state a Tableau-0-only boundary.

## Narrower Follow-Up

A narrower supplied-certificate probe completed successfully:

```bash
timeout 240s lein with-profile test trampoline run -m clojure.main -e \
  "(require '[proflog.willard-sjas-test :as t]
            '[proflog.willard-sjas :as sjas]
            '[proflog.query :as query]
            '[proflog.sjas-correspondence :as corr])
   (let [demo-system @#'proflog.willard-sjas-test/demo-system
         system (demo-system :willard-sjas-level1)
         group3-record (:group-three system)
         cert (sjas/proof-certificate 'sjas-axiom)
         proofs (query/query-succeeds
                  (:program system)
                  (sjas/subst-prf (:system-code system)
                                  (:selfcons-skeleton-code system)
                                  (:code group3-record)
                                  cert)
                  1
                  220)
         p (first proofs)]
     (prn {:proof-count (count proofs)
           :first-proof p
           :audit (when p (corr/audit-proof-term p))}))"
```

It returned one validation proof:

```clojure
(profiled
  willard-sjas-level1
  (profiled
    willard-sjas-subst-proof-check
    (sjas-code-bytes)
    (willard-sjas-subst-code)
    sjas-axiom))
```

This is not a non-`sjas-axiom` theorem certificate; it is proof-predicate
validation for a supplied `sjas-axiom` certificate. It still gives useful
Track 2a evidence:

- the outer Level-1 wrapper is reachable and remains probably irrelevant after
  wrapper-erasure and profile-selection proof;
- `willard-sjas-subst-proof-check` is reachable and relevant;
- `willard-sjas-subst-code`, `sjas-code-bytes`, and `sjas-axiom` are reachable
  in Level-1 substitution-proof validation.

The executable audit was updated because this proof exposed a shape mismatch:
`willard-sjas-subst-proof-check` is a `(profiled ...)` form with
relation-specific payload arity, not the simple three-element wrapper shape.
`classify-profile-form` now accepts any profiled form with at least one payload
item and classifies it by the second element.
