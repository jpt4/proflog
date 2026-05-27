# 2026-04-29 Tableau Foreground/Background Lessons

## Context

The kernel-layer interoperation design raised a literature question: whether
tableau provers already have a foreground/background or frontend/backend
architecture for combining components with different capabilities.

The relevant line of work is theory reasoning for semantic tableaux. A general
foreground tableau reasoner owns branch construction, while specialized
background reasoners close or extend branches for domains such as equality,
arithmetic, set theory, or other theories.

## Sources

- Beckert and Pape, "Incremental Theory Reasoning Methods for Semantic
  Tableaux": <https://formal.kastel.kit.edu/beckert/pub/Incremental_Theory_Reasoning.pdf>
- Beckert, "Semantic Tableaux with Equality":
  <https://formal.kastel.kit.edu/~beckert/pub/Semantic_Tableaux_with_Equality.pdf>
- Tinelli, "Cooperation of Background Reasoners in Theory Reasoning by Residue
  Sharing": <https://homepage.divms.uiowa.edu/~tinelli/papers/Tin-FTP-00.pdf>
- Beckert and Haehnle, "Deduction by Combining Semantic Tableaux and Integer
  Programming": <https://publikationen.bibliothek.kit.edu/153896/760045>
- Etableau CASC system description:
  <https://tptp.org/CASC/J10/SystemDescriptions.html#Etableau_0.2>

## Notes

The strongest fit for Proflog is total branch-level delegation. A foreground
tableau branch exposes a key or residual obligation; a background component
either proves that branch unsatisfiable or declines. That is exactly the shape
needed for greenfield's propositional and first-order layers: they should close
purified branch states, not act only as host-side theorem wrappers.

Partial theory reasoning is a later design space. In that setting the background
component returns residues or consequences that become new foreground tableau
work. That could eventually model learned lemmas or theory consequences, but it
is broader than the first interoperation step.

Incremental background reasoning is directly relevant. The literature notes that
deciding when to call a background reasoner is itself hard. Reusing information
from early or failed calls reduces the cost of conservative early delegation. In
greenfield terms, this suggests memoizing profile-delegation outcomes by a
normalized residual branch and proof-state fingerprint.

Substitution discipline is the core soundness concern. In free-variable
tableaux, a closing substitution discovered by a background reasoner must be
visible to the surrounding tableau. Greenfield should begin with delegated
closures that do not produce new full-kernel equality or disequality state; if a
future layer returns bindings, those bindings must become an explicit state
transition rather than an opaque side effect.

Locality is a practical guard. Etableau sends local tableau branches to E's
saturation machinery. The analogous greenfield guard is: delegate only when the
residual branch is isolated from program calls, equality state, delayed
disequalities, and answer-export state.

Proof objects are not optional. Kernel purity remains paramount, so a delegated
branch closure must produce a checkable subproof or a proof term whose trusted
boundary is explicit. The interoperation proof shape should therefore preserve
the delegated component and its proof, for example:

```clojure
(profiled first-order subproof)
(profiled propositional subproof)
```

This is the main distinction between the greenfield design and an opaque oracle:
the optimized layer is a proof-producing relation inside the kernel architecture.
