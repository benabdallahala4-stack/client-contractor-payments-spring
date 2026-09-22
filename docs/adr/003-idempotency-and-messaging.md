# ADR 003: Database idempotency and transactional outbox

**Status:** Accepted

## Context

Clients retry when they lose a response after commit. A simple lookup can race, especially when the same key is sent for different jobs. Event delivery adds another failure boundary.

## Decision

Require an `Idempotency-Key` for job payment. Store it with a committed payment under a unique PostgreSQL constraint, along with a unique `job_id`. Return the same payment on a matching retry; reject a changed job with `409`. Recheck after waiting on locks. A key collision across jobs is resolved by the database: the loser rolls back and reads the winning committed payment before returning a conflict.

Insert a versioned `JobPaid` outbox row inside the payment transaction. Separate workers claim rows with `FOR UPDATE SKIP LOCKED`, publish them to Kafka, and record acknowledgment. Claims use leases so another worker can recover after a crash. Failures return to pending with bounded exponential backoff.

Delivery is at least once. A crash after Kafka acknowledgment but before the database update can create a duplicate, so every event has a stable `eventId` and consumers must deduplicate it before side effects.

## Options considered

| Option | Advantage | Cost |
| --- | --- | --- |
| Payment uniqueness — chosen | Atomic with job and balances | Failed attempts are not cached |
| Separate request table | Can retain in-progress and failed responses | More states and recovery rules |
| Publish directly after commit | Minimal code | A crash can lose the event |
| Transactional outbox — chosen | Removes the database/Kafka dual-write gap | Requires a worker, retry state, and consumer deduplication |

## Consequences

Successful keys and published outbox rows are retained in this demo; failed payment attempts release their key. Kafka downtime does not roll back a committed payment. Operators monitor backlog age and consumers remain idempotent.
