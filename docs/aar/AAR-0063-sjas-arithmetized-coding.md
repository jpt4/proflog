# AAR-0063: SJAS Arithmetized Coding

- Date: 2026-05-14
- Related ADR: [ADR-0063](../adr/ADR-0063-sjas-arithmetized-coding.md)
- Branch: `adr-0063-sjas-arithmetized-coding`
- Status: completed

## Outcome

ADR-0063 replaced the remaining SJAS codebook boundary with inspectable
base-64 Godel-code terms. Generated system, theorem, complement, and proof
certificate codes are no longer hash-derived constants or entries in
`:sjas/proof-targets`; they are first-order code terms of the shape
`(code-N b0 ... bN-1)`, where each byte is a small binary SJAS numeral.

The `tableau-proof/3` profile now decodes a proof-code byte stream into a
kernel proof term, resolves the theorem code through the generated formula-code
decode relation, reconstructs the same refutation target used by ordinary
SJAS theorem queries, and calls the core proof kernel with the decoded proof.

## Evidence

Initial red evidence:

```text
lein test-proflog-sjas
FAIL / ERROR before implementation:
- no public `sjas/formula-code` code path for arithmetized formula codes;
- later stack overflow when large Godel numbers were exposed as deep binary
  `dbl/add` towers.
```

The first failure established that the old public surface still depended on
finite generated labels. The stack overflow then exposed an implementation
constraint: a faithful byte-coded Godel number must be represented compactly
enough for the kernel to validate and walk it.

Focused verification:

```text
lein test-proflog-sjas
Ran 15 tests containing 143 assertions.
0 failures, 0 errors.
elapsed 4:47.84
```

Regression gates:

```text
lein test-proflog-fast
Ran 145 tests containing 548 assertions.
0 failures, 0 errors.
elapsed 2:07.41
```

```text
lein test-proflog-extended
Ran 68 tests containing 203 assertions.
0 failures, 0 errors.
elapsed 4:37.84
```

## What Worked

- Compact `code-N` terms preserve the object-language code boundary while
  avoiding deep numeral-spine stack overflows.
- `wff/1`, formula-class predicates, and `neg-pair/2` initially operated from
  generated decode relations keyed by Godel-code terms rather than syntax fact
  atoms. ADR-0072 later removed those generated formula registries as a
  host-side shortcut; the predicates now structurally decode formula-code terms,
  although ground byte extraction remains a temporary operational boundary.
- `tableau-proof/3` rejects the wrong theorem code and malformed certificates,
  accepts real beta and Group-3 certificates, and accepts a real contradiction
  certificate for an intentionally inconsistent beta control.
- The focused suite still exercises forward arithmetic, answer mode, and partial
  synthesis over the SJAS arithmetic profile.

## Corrections During Implementation

- The first compact-code attempt still stored large generated facts in metadata
  branches reachable from ordinary arithmetic queries. Guarding those branches
  by predicate symbol before registry enumeration restored arithmetic behavior.
- Proof checking Group-3 initially failed because the registry stored
  `to-nnf(axiom-formula)`, while theorem-query refutation uses the operational
  double negation of the axiom formula. The registry now stores the exact
  refutation-side axiom formula used by the certificate-producing query.

## Remaining Boundaries

- This is still the finite `IS#_D(beta)` executable substrate. It does not prove
  Willard's external consistency-preservation theorem.
- The generated decode tables are finite source-boundary aids. They replace
  opaque labels, but they are not yet a fully internalized Delta-0 formula parser
  with general substitution over arbitrary open code variables.
- Open proof-code synthesis remains operationally expensive and is kept outside
  the focused suite.
