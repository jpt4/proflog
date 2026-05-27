# SJAS Pi-Star-1 Beta and Relational Multiplication Clarification

This note records the 2026-05-19 discussion about how Willard's finite
`IS#_D(beta)` condition should be read for Proflog's SJAS frontend and beta
validation.

## Exchange Scope

The exchange answered four linked questions:

- which Willard papers discuss `IS#_D(beta)`;
- whether beta axioms must literally be authored in `Pi*1` form or may be
  authored in a convenient notation and lowered into `Pi*1`;
- how `Pi*1` differs from ordinary `Pi1`;
- whether universally quantifying over the arguments of the `Mult` relation
  implicitly asserts multiplication totality.

The immediate source for the finite-beta construction is Willard's 2013/2014
analytic-tableaux paper, Section 5. Definition 5.1 introduces `IS#_D(beta)` by
letting beta be a finite set of axioms that have `Pi*1` encodings, and Theorem
5.2 states the consistency-preservation result for semantic tableaux and
`Tab-1` when beta is a set of true `Pi*1` axioms. This is downstream of the
same paper's Section 4 discussion of `IS_D(A)`.

## Source Reading

The 2013/2014 tableaux paper defines `IS#_D(beta)` with beta as a finite set of
axioms that have `Pi*1` encodings. In the theorem immediately following that
definition, beta is described as a set of `Pi*1` axioms. The best reading is
therefore:

- user-facing or mathematical beta claims may be written in a more convenient
  notation;
- before inclusion in the finite Group-2 basis, the actual installed axiom must
  be a checked `Pi*1` encoding in the SJAS/U-Grounding language;
- the frontend must reject, quarantine, or mark unsupported any beta source form
  that cannot be lowered to the admitted `Pi*1` fragment.

This is important for the generated system code: Group-2 must be the encoded
finite axiom basis that the internal proof predicate and generated Group-3
formula cite. It is not sufficient to accept arbitrary formulas merely because
they are informally equivalent to some possible `Pi*1` statement.

## Pi1 Versus Pi*1

`Pi1` is the ordinary arithmetical-hierarchy class of universal closures over a
bounded/decidable matrix, usually in a conventional arithmetic language that may
include total function symbols such as multiplication.

`Pi*1` is Willard's corresponding class for the restricted SJAS language. It is
a universal closure over a `Delta*0` matrix in the U-Grounding language. The
critical distinction is that multiplication is not a total term-forming function
symbol in this language. Multiplication is available only as a relation, such as
`Mult(x,y,z)`, and the system must not gain the totality axiom
`forall x y. exists z. Mult(x,y,z)` as part of the U-Grounding fragment.

## Universal Quantification over Mult Arguments

Universal quantification over arguments to the `Mult` relation is not itself a
totality assertion. A formula such as:

```text
forall x y. Mult(x,x,y) -> P(x,y)
```

means that for every `x` and candidate `y`, if `y` is related to `x` and `x` by
`Mult`, then `P(x,y)` holds. It does not assert that such a `y` exists.

The totality principle would instead have the existential shape:

```text
forall x. exists y. Mult(x,x,y)
```

or more generally:

```text
forall x y. exists z. Mult(x,y,z)
```

That existential assertion is the dangerous one for Willard's Type-A /
U-Grounding discipline. Conditional universal uses of `Mult`, by contrast, let
the system reason about multiplication facts when a graph fact is available
without turning multiplication into a total function internally.

The concise reading is: for all candidate inputs and outputs, if it happens
that the `Mult` graph relates them, the consequent must hold. The formula does
not require that the graph relation hold for every input pair.

Externally, in the intended standard model, `Mult` is still interpreted as the
actual multiplication graph. The restriction is about what the SJAS can assert
or prove internally, not about whether multiplication has its ordinary intended
meaning outside the system.

## Proflog Design Consequence

For Proflog's SJAS beta checker, the accepted target shape should allow
conditional relational multiplication, for example:

```text
forall x y z. Mult(x,y,z) -> Phi(x,y,z)
```

but reject or explicitly route outside the SJAS-safe profile formulas that
assert multiplication totality:

```text
forall x y. exists z. Mult(x,y,z)
```

The frontend may provide syntactic sugar, but the compiled Group-2 axiom must
be the checked `Pi*1` encoding that preserves this distinction.
