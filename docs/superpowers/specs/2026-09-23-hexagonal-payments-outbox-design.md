# Hexagonal Job Payments and Transactional Outbox Design

**Status:** Approved for implementation
**Date:** 2026-09-23

## Purpose

Refactor the client–contractor job payment service into a focused hexagonal Spring Boot application. A successful job payment must remain correct under concurrent requests and must eventually publish one logical `JobPaid` event to Kafka for independent third-party consumers. The repository should teach transaction boundaries, PostgreSQL locking, idempotency, asynchronous delivery, and design trade-offs without pretending to be a complete production payment platform.

## Scope

The milestone includes:

- profiles with immutable `CLIENT` and `CONTRACTOR` roles;
- contracts between one client and one contractor;
- jobs with a positive amount in integer cents;
- an idempotent `POST /jobs/{jobId}/payments` use case;
- PostgreSQL transactions and deterministic pessimistic row locking;
- an atomic transactional outbox;
- Kafka publication with retries and recovery of abandoned claims;
- a versioned `JobPaid` event consumed by three independent consumer groups;
- integration tests using real PostgreSQL and Kafka;
- Mermaid diagrams and ADRs that explain the decisions.

Authentication, funding, refunds, disputes, multiple currencies, double-entry accounting, and implementations of the three external services are outside this milestone.

## Architecture

Code is organized by business capability and dependency direction:

```text
com.example.contractpayments
├── payment
│   ├── domain              # Payment, JobPaidEvent, domain rules
│   ├── application         # PayJob use case and input/output ports
│   └── adapter
│       ├── in.web          # HTTP requests, responses, error mapping
│       └── out.persistence # JPA entities, repositories, port adapters
├── profile                 # profile domain, use cases, adapters
├── contract                # contract domain, use cases, adapters
├── job                     # job domain, use cases, adapters
├── outbox
│   ├── application         # claim, publish, acknowledge, retry orchestration
│   └── adapter
│       ├── out.persistence # PostgreSQL outbox implementation
│       └── out.kafka       # Kafka event publisher
└── configuration           # Spring wiring and typed properties
```

The domain and application packages contain no Spring Data, JPA, HTTP, or Kafka types. Application services depend on small ports. Adapters translate between the ports and technology-specific representations. Spring owns transaction demarcation in the persistence adapter because the atomic operation spans several repositories and the outbox table.

This is a pragmatic hexagonal architecture, not a separate module for every class. Package-private adapter details and explicit constructor injection keep the boundaries visible without excessive abstraction.

## Payment command and idempotency

The endpoint requires an `Idempotency-Key` of 1–128 allowed characters. The application command contains `jobId` and `idempotencyKey`.

Successful payments persist both `job_id UNIQUE` and `idempotency_key UNIQUE`:

- the same key and same job returns the original payment with HTTP `200` and `Idempotency-Replayed: true`;
- the same key for another job returns HTTP `409`;
- another key for an already-paid job returns HTTP `409`;
- failed transactions do not reserve the key, so the caller can correct the problem and retry;
- database uniqueness is the final authority for races between application instances.

The payment result contains only committed state. No Kafka call happens on the HTTP transaction thread.

## Transaction, isolation, and exact lock order

The payment transaction uses PostgreSQL `READ COMMITTED`, an explicit timeout, and the following order:

1. Look up an existing payment by idempotency key without a lock for the fast replay path.
2. Lock the target `jobs` row with `SELECT ... FOR UPDATE`.
3. Recheck the idempotency key after the job lock.
4. Lock the job's `contracts` row with `SELECT ... FOR UPDATE` so contract status and parties cannot change during payment.
5. Sort the client and contractor profile UUIDs in ascending order.
6. Lock both `profiles` rows sequentially in that sorted order with `SELECT ... FOR UPDATE`.
7. Validate job, contract, client balance, and contractor balance ceiling from the locked rows.
8. Debit the client, credit the contractor, set the job to `PAID`, insert the payment, and insert the outbox event.
9. Commit all five state changes atomically.

The order `job → contract → profiles sorted by UUID` prevents payment code paths from acquiring the same resources in conflicting orders. Every future balance-changing use case must use the same profile order. PostgreSQL may still report deadlocks involving unrelated code, so deadlocks are surfaced as retryable failures rather than hidden by unbounded retries.

`READ COMMITTED` is sufficient because decisions are made only after acquiring row locks and uniqueness constraints enforce invariants. `SERIALIZABLE` was rejected because it adds transaction-level retry complexity without replacing explicit locks for this teaching goal.

## Transactional outbox

The `outbox_events` table contains:

- `id` UUID, also used as the stable event ID;
- `aggregate_type`, `aggregate_id`, `event_type`, and `event_version`;
- JSONB payload;
- `status` (`PENDING`, `PROCESSING`, `PUBLISHED`);
- `attempt_count`, `available_at`, `locked_until`, `last_error`;
- `created_at` and `published_at`.

The payment persistence adapter inserts `JobPaid.v1` into the outbox within the payment transaction. This removes the database/Kafka dual-write gap.

A scheduled worker uses a bounded task executor. Each worker claims a small batch in a short PostgreSQL transaction using `FOR UPDATE SKIP LOCKED`. Claiming changes rows to `PROCESSING`, increments `attempt_count`, and assigns a lease through `locked_until`. Kafka publishing occurs outside the claim transaction. Success changes the row to `PUBLISHED`; failure returns it to `PENDING` with bounded exponential backoff and a sanitized error. An expired `PROCESSING` lease becomes claimable again after a crashed publisher.

The publisher is horizontally scalable because `SKIP LOCKED` prevents active workers from claiming the same row. The batch size, worker count, polling interval, lease duration, and retry delay are typed configuration properties with conservative defaults.

## Kafka contract and delivery semantics

The producer publishes to `job-paid.v1`, keyed by `jobId` to preserve per-job partition ordering. Producer idempotence, acknowledgements `all`, and safe retry settings are enabled.

The JSON event envelope includes:

```json
{
  "eventId": "uuid",
  "eventType": "JobPaid",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 timestamp",
  "jobId": "uuid",
  "contractId": "uuid",
  "paymentId": "uuid",
  "clientId": "uuid",
  "contractorId": "uuid",
  "amountCents": 30000
}
```

Delivery is **at least once**. A crash after Kafka acknowledges but before the database marks the row published can produce a duplicate. Exactly-once claims are therefore avoided. Each third-party consumer must store and deduplicate `eventId` before applying side effects.

The architecture diagram shows three independent consumer groups—notifications, invoicing, and analytics—reading the same topic. They are external contracts, not implemented business services in this repository. A test probe consumes the event to verify the published schema and stable identifier.

## Database design and indexes

Primary keys and uniqueness constraints already provide indexes for direct identifiers. Add only indexes that serve demonstrated queries:

- `contracts(client_id)` and `contracts(contractor_id)` for relationship lookup;
- `jobs(contract_id)` and a partial index on open jobs if the API exposes that list;
- the existing unique indexes on `payments(job_id)` and `payments(idempotency_key)`;
- a partial outbox polling index on `(available_at, created_at) WHERE status IN ('PENDING', 'PROCESSING')`.

The outbox claim query is verified with `EXPLAIN` documentation. Speculative indexes are excluded because every index increases write cost and maintenance.

## Caching and read replicas

No cache participates in payment authorization, balance checks, job status, idempotency, or outbox state. Those reads require current primary-database state and row locks. Caching them could authorize an invalid payment or return stale idempotency results.

No read replica serves command-side reads. Asynchronous replicas can lag behind a successful payment. A future reporting adapter may route explicitly stale-tolerant endpoints, such as historical payment lists or analytics, to a replica. API responses that require read-after-write consistency continue to use the primary.

Cache-aside can be introduced later for immutable reference data or public contract metadata with explicit TTL and invalidation rules. Adding Redis now would demonstrate infrastructure rather than solve a measured problem, so it is deliberately excluded.

## Concurrency and Java threading

Application request concurrency is handled by Spring's managed server executor. The outbox uses a Spring-managed bounded `ThreadPoolTaskExecutor`; production code does not create raw threads.

Integration tests use Java `ExecutorService`, `CountDownLatch`, and timeouts to start competing requests together and prove:

- twenty jobs against a balance sufficient for ten produce exactly ten payments;
- one job submitted concurrently produces one payment and one outbox event;
- the same idempotency key replays one committed result;
- one idempotency key used for different jobs commits one winner and returns a conflict for the loser;
- reverse client/contractor relationships do not deadlock because profile locks are sorted;
- an injected failure after mutations rolls back balances, job, payment, and outbox;
- multiple outbox workers claim disjoint rows;
- a publish failure is retried, and an expired lease is recovered;
- the Kafka event has the documented versioned schema.

Tests use real PostgreSQL and Kafka from Docker Compose. Focused domain tests cover pure rules without Spring. Time-bounded awaits prevent a deadlock from hanging CI.

## Failure handling and observability

HTTP errors use stable machine-readable codes. Lock timeouts, deadlocks, and temporary database failures map to retryable service errors. Validation and invariant failures remain deterministic client errors.

Outbox metrics expose pending count, oldest pending age, publish successes, publish failures, and retry counts. Structured logs include `eventId`, `paymentId`, and `jobId` without event payloads or credentials. Spring Actuator reports database and Kafka readiness separately; Kafka unavailability does not roll back already committed payments.

Outbox rows are retained after publication for demonstration and audit. A production retention job would archive or delete old published rows in bounded batches.

## Diagrams and documentation

The README will contain:

1. a hexagonal component diagram with dependency direction;
2. an entity relationship diagram including the outbox;
3. a payment sequence diagram annotated with `READ COMMITTED` and each locked row;
4. an outbox claim/publish/retry diagram;
5. a Kafka fan-out diagram with three consumer groups;
6. a concise table explaining which paths may use cache or replicas.

ADRs will record the hexagonal boundary, payment locking/isolation decision, transactional outbox and at-least-once delivery, and the decision to exclude cache/read replicas from command paths.

## Repository history and authorship

After the implementation and verification are complete, create a new orphan root commit containing only the final neutral project and force-push it with `--force-with-lease`. Before pushing, scan the complete reachable tree, commit metadata, commit messages, tags, and branches for the unwanted company name. The new root commit must use only `Ala Ben Abdallah <benabdallahala4@gmail.com>` as author and committer and contain no co-author or generated-by trailers.

The repository remains private. The remote default branch is `main`. Rewriting history invalidates old commit hashes and existing clones must be re-cloned or reset to the new root.

## Acceptance criteria

- The source follows the described dependency direction and package boundaries.
- The payment transaction uses `READ COMMITTED` and the documented lock order.
- A committed payment atomically creates exactly one logical outbox event.
- Kafka delivery recovers from failures and documents at-least-once semantics.
- Concurrent integration tests prove balance, job, payment, idempotency, and outbox invariants.
- README diagrams explicitly label locked rows and isolation level.
- ADRs explain design choices and rejected alternatives.
- The main branch has one neutral root commit by the user's verified identity.
- The unwanted company name is absent from every reachable commit and tracked file.
- Maven verification and GitHub CI pass with PostgreSQL 17, Kafka, and Java 21.
