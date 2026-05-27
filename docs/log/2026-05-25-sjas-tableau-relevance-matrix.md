# SJAS Tableau Relevance Matrix

Date: 2026-05-25

Branch: `adr-0073-sjas-correspondence-program`

## Purpose

This note starts Track 2a of ADR-0073. It classifies intensional aspects of the
semantic-tableau deductive apparatus as relevant, probably irrelevant, or
unresolved for SJAS self-justification. It is not the final proof. It is the
working matrix that will determine what Track 2b must preserve when proving and
testing correspondence between Proflog kernel acceptance and the SJAS
arithmetized tableau proof predicate.

For Track 2b, a proper proof requires more than direct operational agreement or
informal inspection. The Proflog kernel relation and the SJAS tableau deductive
apparatus must be formalized in compatible terms so equivalence, preservation of
relevant measures, and any failure of equivalence can be checked rigorously. If
the current computational and logical specifications are not sufficient for a
direct proof, the program must lift the Proflog side, the SJAS side, or both
into a third-party formal verification tool or a common intermediate semantics.

The classifications below are grounded in the existing project notes:

- [Willard Tableaux Proof Encoding](2026-05-20-willard-tableau-proof-encoding.md)
- [SJAS Arithmetized Coding Research](2026-05-14-sjas-arithmetized-coding-research.md)
- [AAR-0070: SJAS Byte-Sequence Coding Audit](../aar/AAR-0070-sjas-byte-sequence-coding-audit.md)
- [AAR-0061: SJAS Full Arithmetic Proof Checking](../aar/AAR-0061-sjas-full-arithmetic-proof-checking.md)
- [SJAS Internalization and Proflog Correspondence Program](2026-05-25-sjas-internalization-correspondence-program.md)

## Initial Matrix

| Aspect | Initial classification | Basis | Proof or implementation obligation |
|---|---|---|---|
| Proof object is a finite tree, not a Hilbert list | Relevant | The Willard proof-encoding note records semantic-tableaux proofs as tree-shaped byte strings, contrasted with Hilbert-style list proofs. | Track 2b must translate Proflog proof terms to and from finite tableau trees, not merely theorem outcomes. |
| Branching induced by tableau decomposition rules | Relevant | The proof-encoding note records that semantic tableaux branch when parent nodes contain formulas of the applicable tableau forms. | The correspondence proof must show that each accepted branch split maps to the SJAS tableau rule with the same relevant child structure. |
| Root formula and axiom/deduction ancestry | Relevant | The proof-encoding note records that a proof has a proper root, axioms, deductions from ancestors, and branch closure. | Proflog proof certificates must preserve the root target, ancestor relation, and rule ancestry needed by the SJAS predicate. |
| Branch closure by formula and negation | Relevant | The proof-encoding note records closed root-to-leaf branches as part of `SemPrf`. | Track 1 should internalize closure over encoded formula codes; Track 2b must prove Proflog closure corresponds to that code-level closure. |
| Inspectable formula/proof/system byte encoding | Relevant | ADR-0063 through ADR-0071 and AAR-0070 replaced opaque labels with byte/base-64 and U-Grounding codes, including trailing-zero preservation. | Correspondence cannot use hashes or compressed opaque witnesses unless a proof shows they preserve the required natural code lower bounds and inspectability. |
| Lower-bound proof-size discipline | Relevant | The Willard proof-encoding note records the conventional tableaux encoding requirement that proof encodings grow at least proportionally to function-symbol occurrences. | Track 2b must preserve the size measure used by SJAS or prove an allowed bounded translation that does not collapse the self-referential proof-size facts. |
| Exact historical byte layout | Probably irrelevant | The proof-encoding note says Willard does not prescribe a unique byte layout; Proflog's layout can differ if it remains natural, tree-shaped, inspectable, and not over-compressed. | Provide a bounded-translation argument between Proflog's encoding and the SJAS notation used in the proof, preserving relevant size lower bounds. |
| Rule-selection order and search scheduling | Probably irrelevant | The refined program hypothesizes that the mechanism for selecting which applicable rule to try is not the SJAS-relevant fact if the same proof trees are accepted. | Prove scheduler irrelevance: different search orders may find proof trees in different time, but must not change accepted proof-tree codes within the formal predicate. |
| Runtime caching, tabling, agenda strategy, and host data structures | Probably irrelevant | These are implementation choices below the proof-object level. The existing notes treat host staging as acceptable only at source-compilation or performance boundaries. | Prove they are observationally irrelevant to the accepted code-level proof relation, or remove them from semantic proof paths. |
| Proof term layout inside Proflog certificates | Unresolved | The current implementation validates decoded Proflog proof terms through `kernel/prove-programo`; the layout may be tree-shaped but must be audited against SJAS needs. | Audit Proflog proof constructors and define the translation used by Track 2b. Add tests for positive and negative constructor cases. |
| Equality and disequality profile rules | Unresolved and high risk | Proflog has equality support beyond a bare propositional/first-order tableau. Derived equality closures could compress proof trees or add rule power. | Determine whether the SJAS deduction method includes these rules. If it does, formalize their tree expansion; if not, restrict correspondence claims to equality-free fragments or prove conservativity. |
| Procedure-call and profile-interleaved theory rules | Unresolved and high risk | SJAS reflected clauses and proof predicates run inside Proflog's program/profile machinery. Kernel interleaving may hide rule applications not present in the target SJAS tableau predicate. | Decide whether each profile rule is part of the encoded SJAS proof system, a macro expandable to tableau steps, or outside the correspondence boundary. |
| Quantifier instantiation and witness policy | Unresolved and high risk | Tableau proof size can depend on gamma/witness expansion policy. Existing Proflog work includes performance-sensitive gamma handling. | Classify the relevant witness objects and prove that Proflog's quantifier steps correspond to the SJAS encoded deduction rules without hidden compression. |
| Beta truth and formula-class validation | Unresolved and deferred | ADR-0072 still records user-supplied beta truth and class validation as delegated to the user. | The proof machinery correspondence can proceed with beta membership as an assumption, but full SJAS soundness eventually needs a validation or explicit trust boundary. |
| Mechanism of applying a rule once selected | Conditional | The user's hypothesis is plausible only if application mechanism does not alter child nodes, closure facts, or size. | Treat as irrelevant only after proving that each implementation rule application produces the same relevant child structure as the SJAS tableau rule. |
| Failure to close self-referential proof trees | Relevant outcome, not by itself sufficient | The refined discussion distinguishes operational adequacy from internalization adequacy. Matching failures are evidence, but not proof of the arithmetized predicate. | Negative operational tests are required, but must be paired with the Track 2b proof. |
| Formal specification medium for correspondence proof | Required meta-obligation | A proper correspondence proof needs compatible formalizations of the Proflog kernel and SJAS tableau specification, or a shared third-party/common-intermediate formalization. | Choose and document the proof medium before claiming Track 2b complete; examples include direct structural semantics over both proof relations or encoding one/both sides in a formal verification tool. |

## Working Assessment

The current evidence corroborates the user's suspicion in a narrow form:
tableau-induced tree structure, rule-induced branching, branch closure, and
proof-size/encoding discipline appear to be the relevant intensional measures.
Rule search scheduling and host runtime mechanics are probably irrelevant when
they do not change which proof trees are accepted or how large those accepted
trees are under the SJAS measure.

The suspicion is not yet safe as a completed conclusion. The unresolved cases
are precisely the places where Proflog may differ from a plain semantic
tableau: equality, disequality, procedure-call expansion, profile-interleaved
theory rules, quantifier instantiation, and decoded proof-certificate layout.
Those features can change proof tree shape or compress many object-level steps
into one implementation step. They must be classified before Track 2b can claim
that a Proflog kernel call preserves the relevant SJAS invariant.

## Immediate Next Questions

1. What exact Proflog proof constructors can appear in the non-`sjas-axiom`
   proof certificates accepted by `tableau-proof/3` and `subst-prf/4`?
2. Which of those constructors are primitive SJAS tableau rules, which are
   macro-expandable into bounded tableau subtrees, and which are outside the
   intended deduction method?
3. Does any accepted Proflog certificate compress a proof subtree in a way that
   violates the SJAS proof-size lower-bound discipline?
4. For equality, procedure calls, and profile-specific theory rules, should the
   correspondence be restricted to the current finite SJAS proof fragment or
   extended with explicit arithmetized rules?
5. Is a direct proof over the current Proflog and SJAS specifications rigorous
   enough, or must one or both sides be translated into a third-party formal
   verification setting before Track 2b can be completed?
