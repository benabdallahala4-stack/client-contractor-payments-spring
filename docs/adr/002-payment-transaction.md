# ADR 002: One transaction with ordered PostgreSQL row locks

**Status:** Accepted

## Context

Two requests can pay the same job, or two different jobs can spend the same client balance. A transaction without locking can still make decisions from stale data.

## Decision

Use Spring's `TransactionTemplate` at `READ COMMITTED`. Lock the job row, then the contract row, then the client and contractor profile rows sequentially in ascending UUID order. Every lock is PostgreSQL `SELECT ... FOR UPDATE`. Check state from locked rows, update both profiles, mark the job paid, insert the payment, and insert the outbox event in the same transaction. PostgreSQL rolls back every change if any step fails. The transaction has a ten-second timeout and contains no remote calls.

## Options considered

| Option | Advantage | Cost |
| --- | --- | --- |
| Ordered row locks — chosen | Direct demonstration of `SELECT FOR UPDATE` | Hot profiles serialize |
| Serializable isolation | Broader anomaly detection | Requires retry policy and more explanation |
| In-memory mutex | Easy single-process example | Fails with multiple application instances |

## Consequences

One job is paid at most once, contract parties remain stable during payment, and balances cannot be overwritten by concurrent requests. Future balance-changing operations must follow `job → contract → profiles sorted by UUID`. Tests exercise these claims against PostgreSQL rather than an in-memory database or mocked repository.
