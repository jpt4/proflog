# SJAS Profile Theory Rule Inventory

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note inventories the profile-specific branch rules installed by
`proflog.kernel.willard-sjas-profile/willard-sjas-theory-closeo`. It refines
the unresolved "profile-specific theory rules" row from
[SJAS Tableau Relevance Deep Dive](2026-05-25-sjas-tableau-relevance-deep-dive.md).

The key distinction is:

- some profile rules are object-language predicate evaluations that SJAS must
  preserve or internalize, such as arithmetic, syntax-code, substitution-code,
  and axiom membership;
- some profile rules are proof-predicate bridges, especially
  `tableau-proof/3` and `subst-prf/4`, whose current non-`sjas-axiom` paths
  still call the Proflog kernel;
- the outer profile wrapper remains probably irrelevant only after proving that
  the encoded system fixes the selected profile and wrapper erasure preserves
  the accepted proof relation.

## Current Rule Families

`willard-sjas-theory-closeo` tries these profile closures:

```clojure
sjas-eq-progresso
sjas-neq-closeo
sjas-neg-relation-closeo
sjas-syntax-code-closeo
sjas-subst-code-closeo
sjas-axiom-member-closeo
sjas-tableau-proof-closeo
sjas-subst-prf-closeo
```

These rules are invoked from the ordinary kernel as branch-closing or
branch-progress rules. Therefore they are not mere runtime scheduling details:
they can change whether a branch closes, what proof evidence is emitted, and
how much object-language work is visible in the certificate.

## Classification Matrix

| Rule family | Proof wrapper/tag | Classification | Track 2b obligation |
|---|---|---|---|
| Arithmetic equality progress | `(profiled willard-sjas-arithmetic (sjas-eq-progress ...))` | Relevant object-language arithmetic branch progress | Preserve or internalize the U-grounding equality proof and show the branch continuation corresponds to an SJAS tableau expansion or admitted theory rule. |
| Arithmetic disequality closure | `(profiled willard-sjas-arithmetic (sjas-equal ...))` over a negated equality | Relevant object-language arithmetic closure | Prove arithmetic equality evidence closes the negated equality at a branch tip, or expand it as an ordinary tableau closure plus arithmetic predicate proof. |
| Arithmetic relation closure | `(profiled willard-sjas-arithmetic (sjas-lt ...))`, `sjas-leq`, `sjas-mult`, etc. | Relevant object-language arithmetic closure | Preserve the relational arithmetic proof; if treated as a macro, prove it only adds branch-tip closure work and does not compress hidden tableau structure. |
| Syntax/formula-code predicates | `(profiled willard-sjas-code (...))` | Relevant syntax/code predicate evaluation | Preserve byte-reading, formula decoding, formula-class, and neg-pair evidence. Current proof-tag inventory shows some emitted code-reader tags are not yet in `proof-symbols`. |
| Structural substitution code | `(profiled willard-sjas-subst-code)` | Relevant Level-1 substitution predicate | Prove `subst-code/2` matches Willard's `Subst`, including binder shadowing, alpha-equivalence, and embedded code opacity. |
| Axiom membership | `(profiled willard-sjas-axiom-member ...)` | Relevant axiom-basis predicate | Prove system-code membership for Group-0, Group-1, Group-2 beta, reflected Group-2b, and Group-3 corresponds to the selected SJAS axiom basis. |
| Tableau proof predicate | `(profiled willard-sjas-proof-check proof-read theorem-read decoded-proof)` | Relevant high-risk bridge | For non-`sjas-axiom` certificates, replace the kernel call with object-level proof checking or prove the Proflog/SJAS correspondence preserving relevant tree and size measures. |
| Substitution proof predicate | `(profiled willard-sjas-subst-proof-check proof-read theorem-read decoded-proof)` | Relevant high-risk bridge | Same as tableau proof checking, plus the substitution source/theorem relation required by Level-1 fixed-point self-reference. |

## Legacy or Staging Tags

The encoded proof alphabet also contains symbols such as `willard-sjas-fact`,
`willard-sjas-theorem-code`, and `sjas-generated-axiom-member`. These are not
current preferred proof obligations:

- `sjas-generated-axiom-member` is contrary to the ADR-0072 direction when used
  as semantic evidence; tests already reject injected generated axiom facts in
  important paths.
- `willard-sjas-theorem-code` and `willard-sjas-fact` should be treated as
  staging or legacy proof vocabulary unless reachability evidence shows they
  appear in current proof-predicate certificates.

Track 2b should either prove these tags unreachable in the covered SJAS
fragment or classify them as object-language predicates, macro steps, or
implementation artifacts to be removed.

## Macro-Expansion Orientation

Following
[SJAS Macro Expansion and Lower-Bound Adequacy](2026-05-26-sjas-macro-expansion-lower-bound.md),
profile rules are not disqualified merely because they are macros. A
branch-tip arithmetic or syntax predicate closure can be adequate if it expands
to object-language work and preserves the lower-bound proof-size discipline.

The high-risk cases are rules that hide an unbounded proof tree or proof search
behind a compact tag. `willard-sjas-proof-check` and
`willard-sjas-subst-proof-check` were the primary examples while they decoded a
proof certificate and called `kernel/prove-programo` rather than checking the
proof-code tree object-level. The current implementation now uses a local
proof-directed checker for the generated certificate shapes, but the same
Track 2b risk remains until that checker is formalized against the
SJAS-specified tableau apparatus or every wrapper is justified by a
correspondence proof.

## Track 2a Conclusion

Profile-specific theory rules are relevant, but not all for the same reason.
Arithmetic, code, substitution-code, and axiom-membership closures are
object-language predicate work that likely belongs in the SJAS apparatus or in
bounded macro expansions. The proof-check and subst-proof-check wrappers are
the core remaining bridge: they must be internalized directly or justified by
the full Track 2b proof-and-test correspondence.
