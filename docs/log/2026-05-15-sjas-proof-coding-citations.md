# SJAS Proof-Coding Citations

Date: 2026-05-15

## Question

The user asked whether Willard's SJAS papers discuss coding of formulae and,
equally importantly, coding of tableau proofs over which the system must reason.

## Finding

Yes. The proof-coding requirement is explicit in the literature, not an
inference from formula coding alone.

- Willard 2001, `Self-Verifying Axiom Systems, the Incompleteness Theorem and
  Related Reflection Principles`, defines `Prf_alpha(x,y)` as a bounded formula
  where `y` codes a proof, from `alpha`, of the sentence coded by `x`.
- The same paper specializes this to semantic tableaux through
  `SemPrf_alpha(x,y)`: `y` is the Godel number of a semantic-tableaux proof of
  the sentence coded by `x`. The Group-3 self-consistency axiom is the
  universal nonexistence of a `SemPrf` proof of `0=1`.
- Section 4 of the 2001 paper reviews semantic-tableaux proofs as closed
  candidate trees rooted at the negated theorem. Thus the quantified proof code
  denotes a proof tree, not just a theorem label.
- Willard 2009, `Some specially formulated axiomizations for I Sigma_0`, uses
  `Prf^D_alpha`, `ExPrf^D_alpha`, `Subst`, and `SubstPrf^D_alpha` in equations
  (32) and (33). This ties diagonal formula-code substitution to proof-code
  checking for a selected deduction method `D`.
- The 2009 Tab-k discussion describes sequences of theorem/proof pairs; under
  Tab-k, the proof components are semantic-tableaux proofs. Proflog explicitly
  defers that theorem-reuse proof-list apparatus for now.

## Implementation Consequence

For the finite `IS#_D(beta)` profile, Proflog should continue to treat
`tableau-proof(system-code, theorem-code, proof-code)` as a proof-code
predicate, not as a formula-code lookup. Its selected `D` is the ordinary
Proflog semantic-tableau kernel. Therefore the current proof-code object is a
byte encoding of the kernel's ordinary-tableau proof term.

This is sufficient for the selected finite ordinary-tableau implementation only
if the code terms are faithful byte strings. A source encoder must not pass
through a lossy natural-number normalization that collapses trailing zero bytes.
ADR-0070 records the resulting byte-sequence audit and fix.
