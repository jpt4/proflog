# SJAS Tableau Relevance Deep Dive

Date: 2026-05-25

Branch: `adr-0073-track2a-relevance`

ADR: [ADR-0073: SJAS Internalization Correspondence Program](../adr/ADR-0073-sjas-internalization-correspondence-program.md)

## Scope

This is the Track 2a follow-up to
[SJAS Tableau Relevance Matrix](2026-05-25-sjas-tableau-relevance-matrix.md).
It classifies which intensional features of the semantic-tableau deductive
apparatus must be preserved by a future Track 2b correspondence theorem between
Proflog proof acceptance and the SJAS arithmetized proof predicate.

The central hypothesis under review is:

- tableau-induced tree structure and size growth are relevant;
- rule-selection mechanics are irrelevant if accepted proof trees and size
  bounds are preserved.

The hypothesis is corroborated in that conditional form. The caveat is that
"the structure induced by the tableau method" includes more than raw tree
topology: it includes root and ancestor conditions, rule-induced child
structure, closure conditions, quantifier and witness policy, the permitted
equality/procedure/theory rules, and a non-compressing proof-code discipline.

## Evidence Reviewed

Primary local notes and AARs:

- [Willard Tableau Proof Encoding](2026-05-20-willard-tableau-proof-encoding.md)
- [SJAS Arithmetized Coding Research](2026-05-14-sjas-arithmetized-coding-research.md)
- [AAR-0070: SJAS Byte-Sequence Coding Audit](../aar/AAR-0070-sjas-byte-sequence-coding-audit.md)
- [AAR-0061: SJAS Binary Arithmetic and Proof Checking](../aar/AAR-0061-sjas-full-arithmetic-proof-checking.md)
- [SJAS Internalization and Proflog Correspondence Program](2026-05-25-sjas-internalization-correspondence-program.md)

Additional project witnesses consulted where they clarify current scope:

- [Willard SJAS Profile Design Notes](2026-05-10-willard-sjas-profile-design.md)
- [Willard SJAS Independent Agent Review Synthesis](2026-05-10-willard-sjas-agent-review-synthesis.md)
- [SJAS General Subst Code](2026-05-15-sjas-general-subst-code.md)
- [AAR-0069: SJAS General Formula-Code Substitution](../aar/AAR-0069-sjas-general-subst-code.md)
- [ADR-0072: SJAS Object-Level Proof Machinery](../adr/ADR-0072-sjas-object-level-proof-machinery.md)

Current code and tests reviewed:

- `src/proflog/willard_sjas.clj`
- `src/proflog/willard_sjas_code.clj`
- `src/proflog/kernel/willard_sjas_profile.clj`
- `src/proflog/kernel.clj`
- `src/proflog/kernel_support.clj`
- `src/proflog/equality.clj`
- `test/proflog/willard_sjas_test.clj`

## Evidence Labels

- Source-grounded: directly stated in the reviewed Willard/SJAS notes or ADRs.
- Code-grounded: directly visible in current implementation or tests.
- Inferred: reasoned from source-grounded facts, but not explicitly stated in
  the sources.
- Unresolved: existing sources or code review do not yet determine the
  classification.

## Current Implementation Observations

The current implementation is not a pure object-level proof-tree checker.
`src/proflog/kernel/willard_sjas_profile.clj` decodes proof-code bytes into a
Proflog proof term and, for non-`sjas-axiom` certificates, calls
`kernel/prove-programo` with that decoded proof term supplied. ADR-0072 records
this as a remaining boundary: theorem targets and axiom bases are increasingly
derived from codes, but proof-code trees are not yet checked object-level.

The proof-code layout is Proflog-specific. `src/proflog/willard_sjas_code.clj`
serializes proof symbols and nested proof lists as base-64 byte strings. Its
proof symbol table includes ordinary kernel constructors such as `conj`,
`split`, `univ`, `once-univ`, `witness`, `eq-step`, `neq-close`,
`refl-close`, `savefml`, `close`, `pos-call`, and `neg-call`, plus profiled
and SJAS-specific proof evidence such as `profiled`,
`willard-sjas-proof-check`, `willard-sjas-arithmetic`, and
`willard-sjas-subst-proof-check`.

The ordinary kernel in `src/proflog/kernel.clj` is tableau-shaped but
operationally rich. It selects pending formulas from an explicit agenda,
expands alpha/beta/gamma/delta-style rules, stores literals, threads equality
state, delays disequalities, opens subsidiary tableaux for procedure calls, and
allows profile-level branch rules to close or advance focused formulas.

The SJAS tests already protect some intensional boundaries. Examples include
proof-code byte-string preservation, a lower-bound sanity check over encoded
proof symbols, theorem-code structural decoding, axiom-membership
reconstruction from system code, rejection of injected generated
`axiom-member/2` facts, and U-Grounding code-byte evidence in selected paths.
These tests are operational evidence; they are not yet the Track 2b
correspondence theorem.

## Classification Matrix

| Aspect | Classification | Evidence status | Reasoning | Track 2b obligation |
|---|---|---|---|---|
| Tree shape of proof objects | Relevant | Source-grounded and code-grounded | The proof-encoding note records semantic-tableaux proofs as tree-shaped byte strings, with ancestor, sibling, leaf, root, closure, and deduction predicates feeding `SemPrf`. Proflog proof terms are nested proof constructors, but not yet audited as a one-to-one tableau tree notation. | Define a tree extraction relation from each accepted Proflog proof certificate to an SJAS tableau tree. Prove root, child, ancestor, leaf, and branch-path preservation in both correspondence directions. |
| Proper root and theorem target | Relevant | Source-grounded and code-grounded | Willard-style `SemPrf` is a proof of a coded sentence from a coded axiom system. Current `tableau-proof/3` reconstructs the negated theorem target from theorem-code before calling the kernel. | Prove that the Proflog target assembled as `(axiom antecedent) and (negated theorem)` is exactly the target required by the SJAS tableau predicate for the same system and theorem codes. Include negative cases for wrong theorem and wrong system codes. |
| Branching rules and child structure | Relevant | Source-grounded and code-grounded | The sources identify semantic tableaux by rule-induced branching. Current kernel `split` is the visible beta branch constructor; `conj`, `univ`, `once-univ`, and `witness` are single-branch constructors. | For every accepted branching constructor, prove the child count and child formulas match the selected SJAS tableau rule. For non-branching rules, prove they preserve the branch continuation expected by the SJAS tree. |
| Closure rules | Relevant, with equality subcases unresolved | Source-grounded and code-grounded | Willard proof encoding requires every root-to-leaf branch to close. Current Proflog includes complementary literal closure, `false` closure, disequality/equality closures, and profile-level arithmetic or code predicate closure. | Separate primitive SJAS closure rules from Proflog-derived closures. Prove complementary and false closure directly. For equality, arithmetic, syntax, and profile closures, either add them to the encoded SJAS rule set, macro-expand them into bounded tableau subtrees, or restrict correspondence to fragments where they cannot appear. |
| Proof size and natural encoding growth | Relevant | Source-grounded and code-grounded | The Willard notes record the Conventional Tableaux Encoding Requirement: ordinary semantic-tableau proof encodings must not be over-compressed. AAR-0070 and tests verify byte-string preservation and a proof-symbol lower-bound sanity check, but not a full Willard `J`-function-symbol proof. | Define the exact size measure used by the correspondence. Prove that encoding and translation preserve the lower bound, or preserve it within an allowed constant/linear factor. Add negative tests or proof lemmas against hash-like and macro-compressed certificates. |
| Inspectable formula/system/proof byte encoding | Relevant | Source-grounded and code-grounded | ADR-0063 through ADR-0072 replaced opaque labels and generated registries with byte/base-64 or U-Grounding code terms in many paths. Willard's proof predicate needs inspectable syntax and proof codes, not opaque host labels. | Prove every code inspected by the proof predicate is either read object-level or justified by a representation-preserving staging lemma. Track 2b cannot appeal to uninspectable host labels for formulas, systems, substitutions, or proof constructors. |
| Exact historical byte layout | Irrelevant if bounded translation is proved | Source-grounded and inferred | Willard permits any natural semantic-tableau proof encoding satisfying the conventional size requirement. Proflog need not copy the 2001 curly-brace byte layout byte for byte. | Provide a bounded, computable translation between Proflog's proof-code grammar and the SJAS proof-tree grammar used in the theorem. Prove exact byte differences do not change tree shape, rule structure, closure facts, or size lower bounds. |
| Sequence arity and trailing-zero preservation | Relevant representation invariant | Source-grounded and code-grounded | AAR-0070 shows that byte strings must remain byte strings; losing trailing zeroes is a lossy sequence normalization even when it is not a hash. | Include byte-sequence injectivity in the encoding lemma. Prove public code terms preserve byte count and payload order for proof, formula, and system codes used by `SemPrf` or its Proflog counterpart. |
| Rule-selection order and search scheduling | Irrelevant under preservation side conditions | Inferred and code-grounded | Search order, agenda selection, and branch scheduling are not part of Willard's stated proof object. They matter only if they change which proof trees are accepted or if scheduling/fuel leaks into the proof predicate. Current Proflog uses `support/selecto`, rule ordering, and optional fuel. | Prove scheduler irrelevance: for a fixed proof certificate, validation depends on rule constructors and branch states, not on the order in which the implementation searches for a certificate. State fuel as an external resource bound, or prove the formal predicate is the unbounded relation approximated by sufficiently high fuel. |
| Mechanism of applying a selected rule | Irrelevant only after child-structure proof | Inferred | The user's hypothesis is right if "application mechanism" means runtime implementation details. It is not right if the mechanism changes child formulas, witness terms, closure state, or proof-size accounting. | For each rule constructor, prove the implementation's state transition yields the same encoded child formulas and branch data as the SJAS rule. Only then may the lower-level application mechanics be ignored. |
| Runtime caching, tabling, host data structures, and evaluator choice | Probably irrelevant | Inferred | These are below the proof-object level when they do not appear in proof certificates or alter accepted certificates. | Prove observational irrelevance relative to the accepted proof-code relation. If any optimization emits `profiled` proof nodes or closes branches as a macro step, classify that emitted constructor separately rather than treating it as invisible runtime detail. |
| Propositional and first-order profiled background closers | Unresolved, high risk | Code-grounded | `profiled-closeo` can close residual propositional or equality-free first-order branches under one `profiled` proof node. This may be a harmless macro or may compress many tableau nodes into one proof step. | Either disable these constructors in SJAS proof certificates, expand them into ordinary tableau subtrees with bounded size accounting, or include them as explicit primitive rules in the encoded SJAS apparatus. |
| Equality and disequality rules | Unresolved, high risk | Code-grounded; source incomplete | Equality is present in the object language, but current Proflog equality machinery is richer than bare complementary closure: it threads a substitution, stores disequalities, closes constructor clashes, and can trigger saved calls. The reviewed notes do not fully settle which equality tableau rules Willard's selected `D` includes. | Identify the equality calculus of the selected SJAS `D`. Prove each Proflog equality proof constructor is primitive, macro-expandable with bounded overhead, or outside the correspondence boundary. If outside, restrict Track 2b to equality-free or equality-limited fragments and prove the restriction is enforced. |
| Procedure-call rules | Unresolved, high risk | Source-grounded and code-grounded | The design notes align Proflog with Fitting/Smullyan tableaux and mention subsidiary tableaux for procedure calls. However, Willard's finite beta basis is an axiom system; reflected clauses may need to be treated as encoded axioms rather than as a meta-level Procedure Call Rule. | Decide whether positive/negative procedure calls are part of the selected `D`, an admissible macro over reflected Group-2b axioms, or outside the SJAS proof predicate. Prove `pos-call`, `neg-call`, guarded alternatives, and equality-triggered calls preserve tree and size requirements or exclude them from accepted SJAS certificates. |
| Profile-specific theory rules | Unresolved, high risk | Code-grounded | The Willard SJAS profile interleaves arithmetic, syntax-code, substitution-code, axiom-membership, `tableau-proof`, and `subst-prf` relations as branch rules. Some are part of the object language being reflected; others are implementation support for proof checking. | Build a rule inventory for every `profiled willard-sjas-*` constructor. For each, classify it as an object-language predicate evaluation step, an SJAS primitive proof rule, a macro-expandable proof subtree, or an implementation artifact that must not appear in the formal proof certificate. |
| Quantifier instantiation and witness policy | Relevant, implementation policy unresolved | Source-grounded and code-grounded | Willard's tableau specification includes universal, existential, bounded universal, and bounded existential expansions. Current Proflog uses fresh proof variables for gamma, a closed-term fallback, rigid parameters for delta, and `once-forall` as an internal NNF form for negated existentials. These choices affect proof shape and size. | Define the SJAS quantifier rule policy, including fresh-variable, closed-term, parameter, and bounded-quantifier witnesses. Prove Proflog `univ`, `once-univ`, and `witness` constructors correspond without hidden compression or unsound witness reuse. |
| Substitution and `subst-prf` apparatus | Relevant | Source-grounded and code-grounded | The arithmetized coding note records `Subst` and `SubstPrf` as required for Level-1 fixed-point self-reference. ADR-0069 makes general substitution structural over formula codes, but `subst-prf/4` still delegates non-`sjas-axiom` proof validation to the kernel. | Prove the substitution-code relation used by Track 2b matches the SJAS `Subst` relation, including alpha-equivalence, quantifier shadowing, and embedded code opacity. Then prove `subst-prf` checks a proof of the supplied theorem code under the substituted source condition, not a different added-premise relation. |
| Beta axiom membership | Relevant to proof predicate | Source-grounded and code-grounded | For finite `IS#_D(beta)`, the proof predicate is from beta plus fixed groups. Current axiom citations decode beta and reflected sections from `system-code` and tests reject injected generated membership facts. | Prove system-code membership for Group-0, Group-1, Group-2 beta, reflected Group-2b, and Group-3 corresponds to the SJAS axiom basis used by the proof predicate. |
| Beta truth and formula-class validation | Relevant to full self-justification; deferred for current proof-machinery correspondence | Source-grounded and unresolved | ADR-0072 records validation that user beta axioms meet Willard truth and formula-class constraints as delegated to the user. Track 2b can prove proof-predicate correspondence under a valid-beta assumption, but the external Willard-style self-justification claim needs that assumption discharged or explicit. | State beta validity as a formal precondition for the correspondence, or implement and prove validators for truth and formula class restrictions. Record which claims are only "relative to accepted beta" until this is resolved. |
| Proof-certificate layout and constructor grammar | Unresolved but probably layout-irrelevant after audit | Code-grounded and inferred | Proflog's proof certificates are nested symbol/list payloads encoded as bytes. The exact layout may be irrelevant, but the constructor grammar is not: unsupported or macro-compressed constructors can change proof shape and size. | Enumerate all proof constructors reachable in SJAS non-`sjas-axiom` certificates. For each constructor, provide a primitive-rule, macro-expansion, or exclusion proof. Add positive and negative constructor coverage tests for representative certificates. |
| Failure to close self-referential proof trees | Relevant outcome, insufficient alone | Inferred from ADR-0073 discussion | Matching non-closure of contradiction probes is operational evidence, but the self-consistency sentence quantifies over the encoded proof predicate. Outcome equivalence alone can erase proof-object and size facts. | Keep negative operational tests, but pair them with the object-level or correspondence proof showing the tested non-closure is non-existence of an accepted proof code under the selected SJAS predicate. |

## Assessment of the User Hypothesis

The hypothesis is corroborated in its intended direction:

1. Tableau-induced tree structure is relevant. Willard's proof predicate is not
   merely theorem-level provability; it inspects a proof object with root,
   ancestor, deduction, and closure structure.
2. Size growth is relevant. The conventional encoding requirement is explicitly
   an anti-compression constraint, and Proflog's byte-code work exists largely
   to avoid hash-like or lossy representations.
3. Rule-selection mechanics are probably irrelevant. The sources reviewed do
   not make proof search order, agenda selection, caching, or host evaluator
   strategy part of the mathematical proof object.

The hypothesis must be narrowed as follows:

1. Rule selection is irrelevant only for a fixed accepted proof relation. If a
   scheduling or fuel policy changes which certificates are accepted, it has
   become semantic and cannot be ignored.
2. "How a rule gets applied" is irrelevant only below the level of resulting
   child formulas, branch state, witnesses, closure state, and size accounting.
3. Current Proflog proof terms contain equality, procedure-call, and profiled
   theory constructors. Those may be valid parts of the selected apparatus, but
   they cannot be assumed irrelevant until Track 2b expands or excludes them.

## Minimum Track 2b Proof Obligations

Track 2b should not try to prove one monolithic theorem first. It should build
the correspondence from the following smaller obligations:

1. Proof-code grammar: define the accepted SJAS proof-code grammar and the
   Proflog proof-code grammar, then enumerate their constructor mapping.
2. Tree extraction: prove each accepted Proflog certificate denotes a finite
   tableau tree with preserved root, child, ancestor, leaf, and closure facts.
3. Rule correspondence: prove each constructor's premises and children match
   the selected SJAS tableau rule or an explicitly bounded macro expansion.
4. Axiom basis correspondence: prove decoded system-code membership equals the
   fixed SJAS groups plus beta and reflected clauses intended by the builder.
5. Substitution correspondence: prove `subst-code/2` and `subst-prf/4` match
   the Level-1 `Subst` and `SubstPrf` relations, including binder shadowing and
   alpha-equivalence.
6. Size preservation: prove the translation between Proflog certificates and
   SJAS proof trees preserves the conventional lower bound and does not hide
   arbitrarily large subtrees in one constructor.
7. Scheduler irrelevance: prove search order, caching, and agenda mechanics do
   not affect fixed-certificate validation. State finite fuel as an evaluator
   bound rather than part of the mathematical predicate, unless the proof
   intentionally formalizes bounded proof search.
8. Extension decision: for equality, procedure calls, profiled propositional or
   first-order closers, arithmetic profile steps, and syntax-code/substitution
   profile steps, prove primitive status, macro-expandability, or exclusion.
9. Beta validity boundary: either validate beta truth/formula-class conditions
   or state the correspondence and self-justification claim as conditional on a
   valid beta basis.

## Unresolved Research Questions

1. Which proof constructors actually appear in accepted non-`sjas-axiom`
   certificates generated by the current SJAS suite, and which are merely
   possible because they are in `proof-symbols`?
2. What exact equality calculus is intended by the selected Willard/Fitting
   semantic-tableau `D` for this implementation?
3. Should Proflog procedure calls be part of `D`, or should reflected clauses be
   expanded as ordinary axiom applications inside the tableau proof predicate?
4. Can `profiled` propositional and first-order closures be expanded with a
   bounded size proof, or must the SJAS proof predicate reject those proof
   constructors?
5. How should finite `fuel` be handled in a formal statement of
   `ProflogAccepts(P,S,F) iff SJAS_TableauProof(code(P),code(S),code(F))`?
6. Is the current proof-symbol lower-bound sanity test strong enough to support
   the Willard `J`-function-symbol lower-bound claim, or is a stronger
   code-level size lemma needed?
7. Which beta validation route will the project choose: user-supplied trust
   boundary, object-level formula-class validators plus external truth
   assumptions, or a narrower demonstration claim?

## Track 2a Conclusion

The relevant core is now clearer: tree shape, rule-induced child structure,
closure, axiom basis, substitution vocabulary, inspectable proof/formula/system
codes, and proof-size growth must be preserved. Exact byte layout, search
scheduling, and host runtime mechanics are irrelevant only after proving they
do not change the accepted proof-code relation or the size measure.

The correspondence track remains blocked on the unresolved extension inventory:
equality, disequality, procedure calls, profiled closure, quantifier witness
policy, beta validation, and proof-certificate constructor layout. These are
not objections to Proflog as an implementation; they are the precise proof
obligations needed before a kernel call can be treated as a theorem-backed
virtualization of the SJAS deductive apparatus rather than a trusted shortcut.
