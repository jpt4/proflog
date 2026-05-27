# SJAS Macro Expansion and Lower-Bound Adequacy

Date: 2026-05-26

Branch: `adr-0073-sjas-correspondence-program`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This Track 2a note records a refinement to the proof-size and equality
classification. The earlier notes correctly identified generic
free-constructor equality as unresolved, but their statement of the size
obligation could be read too symmetrically. For SJAS self-justification, the
critical proof-size condition is a lower bound: the Proflog bridge must not
accept proof objects that are more compressed than the relevant SJAS
semantic-tableau proof objects.

The macro-expansion route is therefore preferable to fragment exclusion when
it can be proved. If a Proflog constructor expands to an ordinary SJAS tableau
subtree whose size is at least the relevant Proflog certificate size, or if the
constructor only adds closure work at branch tips without shortening the
underlying tableau proof, it need not violate the self-justification invariant.

## Lower-Bound Direction

Track 2b does not need to show that Proflog and the SJAS-side encoding use
identical byte lengths. It needs to show that Proflog acceptance does not
collapse the proof object below the lower-bound discipline used by Willard's
semantic-tableau argument.

For a Proflog certificate `P`, system `S`, and theorem `F`, the useful
direction is:

```text
ProflogAccepts(P, S, F)
implies
there exists an SJAS tableau tree T such that
  T proves F from S,
  T preserves the relevant root/branch/closure/rule structure,
  and size(T) is not smaller than the relevant lower-bound measure
  represented by P.
```

The dangerous failure is not that the expansion is larger. The dangerous
failure is that Proflog validates a small proof certificate that corresponds
only to an arbitrarily large or unrepresented SJAS tableau, without accounting
for that represented size in the self-referential proof predicate.

This direction matters because Willard's Conventional Tableaux Encoding
Requirement is anti-compression. Extra Proflog bookkeeping, larger expanded
trees, or tip-level closure subproofs are acceptable if they preserve the
ordinary tableau proof and do not create a smaller proof object for the
self-reference to quantify over.

## Equality Macro Implication

Generic free-constructor equality is still unresolved, but the preferred
resolution is now more precise:

1. If `eq-step`, `refl-close`, `neq-close`, `neq-rigid`, `neq-store`, and
   helper tags such as `free-close` expand to ordinary tableau reasoning over a
   stated equality/free-constructor theory, then they are acceptable even when
   the expansion increases tree size.
2. If an equality constructor acts only at the tips of already comparable
   tableau branches, and the corresponding SJAS branch closure can be
   represented by adding equality-closure nodes at those tips, it likely
   preserves the relevant self-justification invariant.
3. If equality state changes which non-tip formulas are available, reopens
   saved procedure calls, or substitutes through branch formulas in a way that
   avoids ordinary tableau work, then the macro proof must account for the
   whole expansion and its size.

Thus, the open equality question is not "does Proflog equality make proofs
larger?" Larger is usually harmless for the lower-bound argument. The open
question is whether any equality constructor gives Proflog a shorter accepted
proof than the corresponding SJAS arithmetized tableau predicate would allow.

## Procedure-Call Parallel

The same lower-bound orientation applies to procedure calls. A `neg-call` over
a reflected Group-2b clause may be adequate if it expands to:

1. cite the reflected axiom formula from `system-code`;
2. instantiate its universal binders;
3. apply ordinary implication/negation tableau rules;
4. continue with the subsidiary proof;
5. preserve or increase the relevant size measure.

The call constructor is dangerous only if it becomes a compact oracle for a
large reflected-axiom/tableau expansion without carrying a formal expansion or
lower-bound proof.

## Track 2a Classification Update

The refined classification is:

| Aspect | Classification | Reason |
|---|---|---|
| Macro expansion that increases tableau size | Probably acceptable under proof | SJAS needs anti-compression; larger expanded trees do not threaten the lower-bound invariant. |
| Tip-only closure rules | Probably acceptable under proof | If they only add closure evidence at branch tips and preserve the underlying tableau tree, they should not collapse relevant proof structure. |
| Macro rules with unbounded hidden subtrees | Relevant/high risk | They can produce compact proof certificates for large SJAS tableaux unless the expansion and size lower bound are formalized. |
| Equality-triggered calls and branch-state substitution | Unresolved/high risk | They may affect more than tip closure, so they require a full expansion proof or primitive-rule formalization. |

The practical Track 2b preference is: first try bounded macro expansion for
equality and procedure-call constructors; use fragment exclusion only when a
constructor cannot be expanded while preserving the relevant tableau structure
and lower-bound measure.
