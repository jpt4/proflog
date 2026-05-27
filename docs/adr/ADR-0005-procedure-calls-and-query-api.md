# ADR-0005: Procedure Calls And Query API

- Status: completed
- Date: 2026-04-18
- Branch: `adr-0005-calls-query`
- AAR: [AAR-0005](../aar/AAR-0005-procedure-calls-and-query-api.md)

## Context

Fitting's defining move is the Procedure Call Rule. The greenfield system is not Proflog until positive and negative calls, subsidiary tableaux, and top-level query status are implemented end-to-end.

## Decision

- Add a program layer that looks up compiled clauses and binds actual arguments to formal parameters.
- Implement positive and negative procedure calls as fresh subsidiary tableaux over the same program.
- Expose top-level success and failure via separate semidecision procedures and a fair operational race.

## Consequences

- The system crosses the line from theorem prover to logic programming language.
- Divergence remains operationally visible instead of being mislabeled as failure.
- Query helpers now make operational budgets explicit at the API boundary instead of burying them inside the kernel.
- Canonical Proflog examples become the baseline acceptance tests.

## Test Obligations

- `test/proflog/program_test.clj`
- `test/proflog/query_test.clj`

## Exit Criteria

- Positive and negative calls both work on declared-language atoms.
- Recursive and mutually recursive examples are covered.
- Fitting `P1` and `P2` run end-to-end.
- Query helpers distinguish success, failure, and unresolved search honestly.
