# SJAS Substitution-Proof Boundary

The user objected that Proflog cannot claim a Willard-style self-justification
demonstration merely because it has a predicate named `tableau-proof`: the
predicate must operate over Godel codes for the sentences and proofs of the
system, not over host-side theorem targets or opaque labels.

That objection is correct. The ADR-0062 result is only a finite reflected
proof-substrate demonstration. ADR-0063 corrected the first representation
defect by replacing hash-derived formula labels and `:sjas/proof-targets` with
compact base-64 code terms. After ADR-0063, `tableau-proof/3` decodes a supplied
proof-code term, resolves a theorem-code term through the generated formula
decode relation, reconstructs the kernel refutation target, and checks the
decoded proof through the core Proflog tableau kernel.

ADR-0063 is still not the whole Willard Level-1 vocabulary. Willard's
`SelfCons_k(beta,d)` machinery is stated in terms of substitution-aware proof
predicates, including `Subst` and `SubstPrf`. A Level-1 Group-3 sentence using
raw `tableau-proof(system, x, p)` therefore remains too coarse even when `x` and
`p` are code terms.

ADR-0064 introduces `subst-prf/4`:

```text
subst-prf(system-code, substitution-code, theorem-code, proof-code)
```

For the current finite `IS#_D(beta)` implementation, this predicate checks
identity-substitution entries generated for the current closed system formulas
and then delegates to the same decoded kernel proof-check path as
`tableau-proof/3`. The Level-1 Group-3 generator now cites `subst-prf/4` rather
than raw `tableau-proof/3`.

This is a necessary correction, not a final fidelity claim. The remaining
representation gap is a general code-level `Subst` relation over arbitrary
formula codes, replacing the finite identity-substitution boundary. Until that
exists, the implementation should be described as a finite, code-term,
substitution-vocabulary-aligned SJAS substrate, not as a complete mechanization
of Willard's self-justification theorem.
