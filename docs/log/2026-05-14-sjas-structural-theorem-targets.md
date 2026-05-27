# SJAS Structural Theorem-Code Targets

ADR-0068 closes the next boundary after ADR-0067. Syntax predicates could parse
formula codes structurally, but proof predicates still needed finite generated
formula entries to build kernel proof targets. A valid theorem code not present
in the generated registry could satisfy `wff/1` and `subst-code/2` yet fail
inside `tableau-proof/3`.

The regression theorem was:

```text
lt(1,2)
```

That formula is provable by the SJAS arithmetic profile, and its code is a
well-formed formula-code term in the active SJAS language. Before ADR-0068,
both proof-predicate checks failed:

```text
tableau-proof(system-code, code(lt(1,2)), certificate) => no proof
subst-prf(system-code, code(lt(1,2)), code(lt(1,2)), certificate) => no proof
```

After ADR-0068, both predicates:

- decode the theorem code bytes structurally;
- compute the theorem complement using the same NNF negation structure as the
  Proflog normalizer;
- translate the decoded complement into the kernel AST with canonical noms; and
- ask the existing kernel to check the supplied proof certificate against
  `axiom-basis AND not(theorem)`.

The negative controls replace the theorem code with `code(lt(2,1))`; both
proof predicates reject the same certificate for that different theorem.

This still does not add proof-list theorem reuse or a Tab-1 checker. It removes
the finite theorem-target registry as a necessary condition for ordinary
theorem-code proof checking, while retaining the core Proflog tableau kernel as
the checker that validates decoded certificates.
