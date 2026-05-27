# SJAS Symbol-Table Isomorphism Justification

Date: 2026-05-26

## Question

Current formula and system codes store finite symbol indexes. The runtime
resolves those indexes through `:sjas/symbol-index-entries`, a source-time
coding table in the active SJAS registry. This is not full structural
internalization of the language signature: the byte stream says "symbol 8",
not "the relation named `demo` of arity 1".

The question is whether this is an internalization failure like calling the
Proflog proof kernel, or whether the table can be justified as an irrelevant
nominal codebook up to signature isomorphism.

## Position

The finite symbol table is justified only as a fixed injective coding
convention, not as a self-contained system-code component. Under that
interpretation it is weaker than full signature internalization but does not
collapse a self-justification-relevant invariant, provided the following
conditions hold:

- the codebook is fixed for the whole decoded system, theorem code, proof code,
  and substitution code under evaluation;
- symbol indexes are used only as equality-preserving names for declared
  function and relation positions;
- arities and syntactic roles are preserved by the renaming;
- proof constructors, branch shape, closure rules, formula-code length, and
  proof-code size are not compressed by the nominal mapping;
- no SJAS predicate inspects the spelling or ordering of host symbols as a
  mathematical property.

This is analogous to a Gödel numbering convention. Arithmetic does not need to
know the glyph "demo"; it needs a stable code for one relation symbol and
rules that preserve occurrences of that code.

## Informal Proof

Let `Sigma` and `Sigma'` be two finite SJAS signatures with the same fixed
SJAS vocabulary and the same user-symbol arity profile. Let `pi : Sigma ->
Sigma'` be a bijection preserving symbol kind and arity.

Each coding context assigns a positive byte index to each symbol. For any term
or formula code `c` over `Sigma`, define `pi*(c)` by replacing every
application-symbol index for `s` with the index assigned to `pi(s)` and leaving
all formula tags, term tags, binder indexes, numeric payloads, code payloads,
and proof constructors unchanged.

Then:

1. Decoding commutes with renaming:
   `decode_Sigma(c) = F` iff `decode_Sigma'(pi*(c)) = pi(F)`.

2. System-code decoding commutes with renaming:
   beta formulas and reflected clause records in `S` decode to the `pi`-rename
   of the beta formulas and reflected clauses decoded from `pi*(S)`.

3. Tableau proof checking is syntax-directed over constructors, branch
   decomposition, reflected clause bodies, arithmetic closures, and
   complementary literals. None of those rules inspect host symbol spellings.
   They require only that equal relation/function occurrences remain equal and
   that arities are preserved.

4. Therefore, for every proof code `P`, theorem code `F`, and system code `S`
   in this fragment:

   ```text
   SJAS_TableauProof_T(S, F, P)
   iff
   SJAS_TableauProof_T'(pi*(S), pi*(F), P)
   ```

   The proof code is unchanged because proof constructors encode tableau rule
   structure, not object-language symbol names.

This proves irrelevance of nominal symbol spelling for the current proof
predicate fragment. It does not prove that the current byte string is a
self-contained standalone description of the language; it proves that the
external finite codebook is a conventional parameter whose admissible changes
are precisely signature isomorphisms.

## Residual Boundary

Full structural internalization would encode the finite signature in
`system-code`, including symbol kind and arity records, and decode application
symbols as internal numeric identifiers rather than resolving them through the
active registry. That would remove this proof obligation.

Until then, the implementation must treat arbitrary byte reuse under a
different codebook as invalid. Only recoding induced by a kind/arity-preserving
signature isomorphism is justified.

## Operational Evidence

The regression
`sjas-symbol-table-is-irrelevant-up-to-signature-isomorphism` builds two
Tableau-0 systems differing only by a user-symbol renaming. The renamed relation
receives a different finite symbol index, so the theorem and system codes are
not byte-identical. The generated proof certificates are byte-identical, and
`axiom-member/2` accepts the reflected clause axiom in both recoded systems by
decoding their respective `system-code` bytes. The test also checks that the
changed formula-code byte is precisely the application symbol index. This gives
operational evidence for the proof obligation: the admissible operation is
recoding by isomorphism, not treating symbol indexes as freestanding nominal
facts.
