# SJAS Diagonalization Preclusion Evaluation

Date: 2026-05-20

This note evaluates the correctness of Willard's claim that Type-A
U-Grounding systems, using semantic tableaux deduction and no total
multiplication function, can preclude the usual destructive diagonalization
that drives second-incompleteness inconsistency for self-consistency axioms.

## Verdict

The argument is plausible and internally coherent as a theorem about a sharply
delimited configuration:

- the object language is Willard's U-Grounding-style arithmetic;
- addition is total, but multiplication is not a total function symbol;
- multiplication appears only as a relation or bounded/localized graph fact;
- the reflected deduction method is tree-like semantic tableaux, or only a
  very weak enrichment of it;
- proof objects use Willard's conventional tableaux encoding; and
- the beta axioms satisfy the relevant truth, norming, and boundedness
  hypotheses.

Within that configuration, the mechanism is not hand-waving about "too little
arithmetic." The negative second-incompleteness proof needs a short closed
tableau whose proof-code is small relative to the number it manipulates. Total
multiplication supplies that compression through a squaring chain; without it,
Willard's later positive argument supplies a U-height/open-branch invariant that
blocks such small closed tableaux.

The argument should not be reported as a general theorem that diagonalization
is impossible, that fixed points cannot be formed, or that multiplication is the
unique possible source of diagonal danger. The safer formulation is:

> In Willard's Type-A semantic-tableaux setting, the proof-compression step
> needed by the relevant second-incompleteness diagonalization is unavailable,
> and the replacement U-height invariant forces sufficiently small attempted
> closed tableaux to leave an open branch.

## Source Basis

The 2002 semantic-tableaux paper isolates the destructive step. Lemma 4.7 uses
total multiplication to create constants

```text
u_0 = 2
u_{i+1} = u_i^2
```

with the final value large enough to contradict the root formula, while the
proof fragment remains only logarithmic-logarithmic in the target bound. Lemma
4.8 then reuses those fragments in the diagonal proof. The paper's final
paragraph explicitly says that when multiplication is changed from total
function to three-place relation, Lemma 4.7 fails and the later proof stages
collapse.

Local references:

- `target/sjas-pdf-text/willard2002_semantic_tableaux_robinson_q_author_jsl2.txt`,
  lines 781-817.
- `target/sjas-pdf-text/willard2002_semantic_tableaux_robinson_q_author_jsl2.txt`,
  lines 859-997 and 1086-1128.
- `target/sjas-pdf-text/willard2002_semantic_tableaux_robinson_q_author_jsl2.txt`,
  lines 1678-1683.

The 2011 paper gives the positive Type-A replacement. Fact D.3 says that any
sufficiently small `Z`-based deduction tree for a normed U-Grounding extension
has an unclosed branch. Its proof uses the U-height of a conventionally encoded
tableau. Willard then states that adding a multiplication function collapses
this invariant because squaring growth permits tiny closed deduction trees
below the relevant threshold.

Local references:

- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2176-2229.
- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2237-2240 and 2276-2286.
- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 2378-2389.

The same 2011 paper warns against overclaiming. It acknowledges robust barriers
from Pudlak, Solovay, Nelson, Wilkie-Paris, Adamowicz-Zbierski, and Willard's
own Type-M tableaux results, and notes that SelfCons gives an unusually
compressed, axiom-like proof of consistency rather than an ordinary full-length
derivation.

Local reference:

- `target/sjas-pdf-text/willard2011_self_justifying_logics_arxiv_1108.6330.txt`,
  lines 1513-1527.

The 2018 paper further confirms that the boundary is proof-apparatus-sensitive.
Hilbert-style methods employ linear constrained cuts. Semantic tableaux
enrichments that make excluded-middle/cut-like reuse available can again fall
under second-incompleteness-style barriers. Willard says rank-zero and
rank-zero-plus enrichments remain compatible with self-justification, while
rank-2-or-higher enrichments are not generally safe.

Local references:

- `target/sjas-pdf-text/willard2018.txt`, lines 536-588.
- `target/sjas-pdf-text/willard2018.txt`, lines 637-650.

This accords with the broader proof-theoretic caution that second
incompleteness for weak theories depends not merely on syntactic
arithmetization, but on the adequacy of the proof predicate and derivability
conditions. See Richard Zach's summary of weak-theory issues and the Stanford
Encyclopedia entry on the intensional adequacy of consistency statements.

External references:

- Richard Zach, "The Second Incompleteness Theorem for Weak Theories",
  2005-05-30:
  `https://richardzach.org/2005/05/the-second-incompleteness-theorem-for-weak-theories/`
- Stanford Encyclopedia of Philosophy, "Did the Incompleteness Theorems Refute
  Hilbert's Program?", section on intensional adequacy:
  `https://plato.stanford.edu/entries/goedel/incompleteness-hilbert/`

## What Looks Correct

1. The claim is not that U-Grounding cannot encode syntax at all. Willard's
   systems still reason about codes, proof predicates, and translated
   reflection principles. The claim is narrower: the destructive proof cannot
   build the particular small closed tableau it needs.

2. The multiplication boundary is technically meaningful in the stated
   language. A total multiplication function allows term-level squaring chains.
   A three-place `Mult(x,y,z)` relation permits conditional reasoning over
   multiplication facts, but it does not itself provide the theorem
   `forall x y. exists z. Mult(x,y,z)`. Without that totality principle, the
   tableau cannot uniformly introduce the witnesses required by Lemma 4.7.

3. Semantic tableaux are genuinely different from Hilbert-style proofs for this
   purpose. Hilbert proofs and proof systems with cut-like lemma reuse can
   compress intermediate results. Tree-like tableaux must carry their branch
   expansions explicitly, so proof length and proof-code size become central.

4. Fact D.3 is the right kind of positive obstruction. It does not merely say
   "there is no multiplication"; it says that all sufficiently small
   conventionally encoded U-Grounding deduction trees retain a contradiction-free
   branch. That directly contradicts the existence of the small closed tableau
   needed by the diagonal proof.

## Fragile Dependencies

The argument depends on several premises that a mechanized Proflog profile must
not silently violate:

1. **Proof objects must remain tree-like enough.** If Proflog uses proof DAGs,
   theorem caches, memoized subproof citation, or proof-list reuse as part of
   the reflected certificate, the U-height argument may no longer apply. Such
   sharing can behave like a cut or Hilbert-style intermediate theorem.

2. **The proof predicate must reflect the same deduction apparatus being used.**
   It is not enough to run a host evaluator that can certify proofs faster than
   the object-level `tableau-proof` relation can represent them.

3. **Numeral and proof-code encodings must not smuggle in compression.** A
   compressed numeral syntax, hash code, base64 atom, or host-side symbol table
   is harmless for external engineering only if the object theory cannot use it
   as a fast arithmetic constructor inside proofs.

4. **Finite beta clauses must not encode an implicit total multiplication
   schema.** It is safe to use finite, true `Mult` facts or conditional
   relational multiplication axioms. It is not safe to install a schema or proof
   profile that lets arbitrary multiplication witnesses be generated on demand.

5. **The result is not independent of the chosen consistency notion.** Willard's
   SelfCons variants are tied to semantic tableaux, level restrictions, and
   sometimes translated reflection. A different proof predicate may fall back
   under ordinary second-incompleteness barriers.

## Counterarguments

I do not currently see a direct refutation of Willard's exact theorem under its
own hypotheses. The strongest counterarguments are scope counterarguments:

1. **Against an overgeneral statement of the result.** If the claim is
   "removing total multiplication precludes diagonalization," it is too strong.
   What matters is not multiplication as such but the absence of a proof
   compression mechanism strong enough to build the short closed tableau. A
   different total fast-growth primitive, compressed proof certificate, or
   aggressive proof-sharing rule could reintroduce the problem without naming
   multiplication as a total function.

2. **Against proof-format invariance.** Willard's positive Fact D.3 depends on
   conventional tableaux encoding and U-height. A proof system that stores
   tableau branches as a DAG, or admits reusable closed subtableaux, may no
   longer satisfy the same size lower bound. This does not disprove Willard's
   semantic-tableaux result, but it does refute any claim that the result
   transfers automatically to all cut-free-looking or tableau-inspired proof
   systems.

3. **Against coding-neutrality.** A hash-like formula code can serve as an
   external Proflog implementation key, but it cannot by itself support
   Willard-style arithmetized syntax. Conversely, a richer object-level coding
   convention that includes compressed constructors could undermine the
   U-height proof. The coding convention is therefore part of the theorem, not
   an incidental implementation detail.

4. **Against arbitrary beta extension.** For a fixed finite system, one could
   try to preinstall enough true multiplication graph facts to simulate the
   dangerous squaring chain. This is not obviously a counterexample because the
   diagonal target shifts with the encoded system and because Willard's norming
   hypotheses constrain the usable facts. It is, however, a concrete stress test
   for any executable SJAS implementation: finite reflected clauses must be
   audited for proof-compressing consequences, not merely for local truth.

## Best Candidate Counterexample Program

The most promising way to attack the theorem is not to search for a hidden
fixed point; fixed points are not the relevant bottleneck. The attack should
target Fact D.3's size invariant.

A counterexample family would define a Type-A-looking system whose reflected
deduction method or object-language constructors can build a closed tableau
with:

```text
proof-code < (a/b)^4
all branches closed
```

while still avoiding an explicit total multiplication function. Plausible
routes include:

- a proof-DAG certificate format with shared closed subtrees;
- a proof-list/tableau hybrid with reusable intermediate theorems outside
  Willard's safe rank-zero envelope;
- a total fast-growth constructor other than multiplication;
- a numeral-code constructor that gives the object theory compressed access to
  very large numerals; or
- finite beta facts chosen to simulate the exact squaring chain required by the
  system's own diagonal sentence.

If any such construction satisfies the same advertised language and beta
conditions, it would be a genuine counterexample. If it requires adding cut-like
reuse, total fast growth, or hidden compressed numerals, it instead confirms
Willard's deeper point: the true boundary is proof-certificate compression, with
total multiplication being the canonical arithmetic source of that compression
in the U-Grounding setting.

## Consequences For Proflog

Proflog should describe the SJAS result as an executable exploration of a
specific Willard profile, not as a generic diagonalization escape hatch.

The implementation should keep these distinctions explicit:

- ordinary tableau certificates versus proof-list/Tab-k reuse;
- host-side source translation versus object-level arithmetized syntax;
- external formula identifiers versus object-level numeral constructors;
- relational `Mult` facts versus multiplication totality;
- finite beta extensions versus schemas that generate arbitrary witnesses.

The paper should therefore avoid saying "Proflog implements a system in which
diagonalization is impossible." It should say that Proflog mechanizes a
Willard-style semantic-tableaux profile whose intended metatheory blocks the
specific proof-compression step used by the destructive diagonal argument, and
that the implementation is designed to expose where that boundary is gained or
lost.
