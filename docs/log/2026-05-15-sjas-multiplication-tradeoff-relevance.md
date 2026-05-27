# SJAS Multiplication Tradeoff Relevance

Date: 2026-05-15

## Point To Preserve

The central interest of Willard-style SJAS is not merely that multiplication is
spelled as a three-place relation instead of a binary function symbol. The
interesting tradeoff is semantic and operational:

- the base language gives up a degree of expressive strength by not admitting
  multiplication as a total function;
- multiplication remains available only through a graph relation definable over
  the weaker U-grounding vocabulary, including division and other non-growth
  functions;
- that weakening is what permits the system to consistently assert an axiom
  stating its own consistency, for the selected consistency predicate and
  deduction apparatus.

Therefore, a Proflog SJAS implementation must make the missing total
multiplication function matter. It is not enough for the public language to
omit `mul/2` while the coding and proof machinery obtains its essential power
from a representation that never relies on the restricted arithmetic.

## Consequence For Proflog

The current finite ordinary-tableau `IS#_D(beta)` substrate demonstrates that
SJAS-style formula, substitution, and proof-code predicates can be made
executable inside Proflog. ADR-0070 also ensures those codes are inspectable
byte strings rather than hashes or lossy labels.

That is still not the full programming-language experiment. To observe the
effect of the SJAS tradeoff, future code-construction machinery must avoid
smuggling in the strength that Willard removes from the base language. In
particular:

- formula/proof code construction should eventually be expressed as
  object-language relations over the U-grounding coding vocabulary;
- operations corresponding to logical constructors, negation, substitution, and
  proof-step validation should be mediated by those relations;
- those relations should not require multiplication as a total function;
- if the implementation can perform all interesting arithmetized syntax work
  without depending on the absence of total multiplication, then it has not yet
  demonstrated the distinctive SJAS tradeoff.

## Evaluation Standard

The next SJAS milestone should be judged by whether the restriction has teeth:
a program using the SJAS profile should experience the limits and affordances
of having `mult/3` as a relation rather than `mul(x,y)` as a total function,
including in the arithmetized syntax and proof predicates. Otherwise Proflog
has an executable reflection profile, but not yet an executable demonstration
of the Willard tradeoff as a programming-language phenomenon.
