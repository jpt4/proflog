# ADR-0003: Pure Relational Kernel

- Status: completed
- Date: 2026-04-18
- Branch: `adr-0003-kernel`
- AAR: [AAR-0003](../aar/AAR-0003-pure-relational-kernel.md)

## Context

The greenfield implementation is supposed to inherit αleanTAP's key architectural virtue: the prover itself is a pure relation with no baked-in mode restriction. That requires a fresh kernel rather than accreting more behavior onto the experimental monolith.

## Decision

- Build a new greenfield tableau kernel around a single proving relation and explicit proof terms.
- Keep connectives, quantifiers, and literal closure in this ADR; defer equality and program calls to later ADRs.
- Avoid silent dependence on impure operators in the default kernel.

## Consequences

- Baseline prover behavior can be validated independently of equality and procedure-call complexity.
- Later failures can be localized to the feature ADR that introduced them.
- Proof terms become a first-class deliverable instead of a debug afterthought.
- The new kernel now provides a clean proving relation over the greenfield AST
  without depending on the experimental `cljtap.*` namespace tree.

## Test Obligations

- `test/proflog/kernel_test.clj`
- `test/proflog/proof_test.clj`

## Exit Criteria

- The greenfield kernel closes branches for the baseline tableau fragment.
- Universal and existential handling work without global destructive rewriting.
- Proof objects are structured enough to distinguish the major tableau steps.
- At least one partially instantiated or backward-style example demonstrates preserved relational behavior.
