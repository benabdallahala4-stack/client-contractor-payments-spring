# ADR 005: Keep cache and replicas outside command paths

**Status:** Accepted

## Context

Payment authorization requires current balances, job state, contract parties, idempotency records, and row locks. Caches and asynchronous replicas can return stale state.

## Decision

Read and lock command state on the PostgreSQL primary. Do not cache balances, paid status, idempotency, or outbox state. Do not route payment or read-after-write requests to a replica.

A future reporting adapter may use a replica for endpoints that explicitly tolerate lag. Cache-aside may be added for immutable reference data or measured public-metadata traffic only after defining TTL, invalidation, and consistency behavior.

## Consequences

The critical path remains simple and correct. The primary is the initial scaling limit; connection pooling, short transactions, indexes, and horizontal outbox workers are used before adding consistency-sensitive infrastructure.
