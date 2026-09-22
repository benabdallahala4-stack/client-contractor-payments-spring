# Hexagonal Payments and Outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Rebuild the Spring Boot job-payment service with hexagonal boundaries, concurrency-safe PostgreSQL transactions, and reliable Kafka publication through a transactional outbox.

**Architecture:** Pure domain and application packages expose use cases and ports. Web, JPA/PostgreSQL, and Kafka live in adapters; a transaction-scoped persistence adapter locks job, contract, and profiles in a deterministic order and atomically saves payment plus outbox event. A leased `SKIP LOCKED` publisher delivers versioned events to Kafka with at-least-once semantics.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, PostgreSQL 17, Flyway, Spring Kafka, Apache Kafka in KRaft mode, JUnit 5, Testcontainers-compatible Docker Compose integration environment.

**Spec:** `docs/superpowers/specs/2026-09-23-hexagonal-payments-outbox-design.md`

## Global Constraints

- Use only `Ala Ben Abdallah <benabdallahala4@gmail.com>` for Git author and committer identity.
- Keep the domain and application packages free of Spring, JPA, HTTP, and Kafka imports.
- Use PostgreSQL `READ COMMITTED` and lock `job → contract → profiles sorted by UUID`.
- Never call Kafka inside the payment transaction.
- Kafka delivery is at least once; all events have a stable `eventId`.
- Do not add Redis or route command-side reads to a replica.
- Use real PostgreSQL and Kafka for integration verification.
- The final `main` history contains one neutral root commit.

---

### Task 1: Define the domain and application boundaries

**Files:**
- Create: `src/main/java/com/example/contractpayments/{profile,contract,job,payment}/domain/*.java`
- Create: `src/main/java/com/example/contractpayments/{profile,contract,job,payment}/application/*.java`
- Test: `src/test/java/com/example/contractpayments/payment/domain/PaymentRulesTest.java`
- Test: `src/test/java/com/example/contractpayments/architecture/ArchitectureTest.java`

**Interfaces:**
- Produce `PayJobUseCase.pay(PayJobCommand): PaymentResult`.
- Produce `PaymentTransaction.pay(UUID jobId, IdempotencyKey key): PaymentResult`.
- Produce immutable records for `Profile`, `Contract`, `Job`, `Payment`, and `JobPaidEvent`.

- [x] Write `PaymentRulesTest` for key validation, insufficient funds, overflow, and replay/conflict semantics.
- [x] Write `ArchitectureTest` that scans compiled classes and rejects framework imports from `.domain.` and `.application.` packages.
- [x] Run the focused tests and confirm they fail because the new types do not exist.
- [x] Add the smallest domain records, value objects, errors, use-case interfaces, and ports that satisfy the tests.
- [x] Run the focused tests and the existing suite; keep behavior green.

### Task 2: Build persistence adapters and the locked payment transaction

**Files:**
- Create: `src/main/java/com/example/contractpayments/*/adapter/out/persistence/*.java`
- Create: `src/main/java/com/example/contractpayments/payment/adapter/out/persistence/JpaPaymentTransaction.java`
- Replace: `src/main/resources/db/migration/V1__create_domain.sql`
- Test: `src/test/java/com/example/contractpayments/payment/PaymentConcurrencyIT.java`

**Interfaces:**
- Consume `PaymentTransaction` and domain records from Task 1.
- Produce JPA repository queries `lockJob`, `lockContract`, and `lockProfile` using pessimistic write locks.
- Produce atomic persistence of profiles, job state, payment, and outbox event.

- [x] Write integration tests for twenty competing jobs sharing one limited client, concurrent duplicate payment, cross-job idempotency collision, reverse party lock order, and rollback after an injected failure.
- [x] Run the integration test and confirm failures show missing persistence adapters/outbox state.
- [x] Implement JPA entities and mappers separately from domain records.
- [x] Implement the transaction with isolation `READ_COMMITTED`, timeout, and exact lock order `job → contract → profiles sorted UUID`.
- [x] Add constraints and indexes for relationships, payment uniqueness, and outbox polling.
- [x] Run the concurrency integration tests and confirm all invariants.

### Task 3: Replace the web adapter

**Files:**
- Create: `src/main/java/com/example/contractpayments/*/adapter/in/web/*.java`
- Create: `src/main/java/com/example/contractpayments/shared/web/ApiExceptionHandler.java`
- Test: `src/test/java/com/example/contractpayments/web/PaymentApiIT.java`

**Interfaces:**
- Consume application use cases only.
- Preserve profile, contract, job, and payment REST behavior.
- Change the canonical payment route to `POST /jobs/{jobId}/payments` and keep response replay headers.

- [x] Write API tests for creation/read flows, validation, first payment `201`, replay `200`, and stable error codes.
- [x] Run API tests and confirm they fail against missing new adapters.
- [x] Implement one controller per capability, DTO mapping, Bean Validation, and centralized error translation.
- [x] Delete the old all-in-one controller and framework-coupled services after tests pass.
- [x] Run API and concurrency tests.

### Task 4: Implement leased outbox claiming and retry policy

**Files:**
- Create: `src/main/java/com/example/contractpayments/outbox/application/*.java`
- Create: `src/main/java/com/example/contractpayments/outbox/adapter/out/persistence/*.java`
- Create: `src/main/java/com/example/contractpayments/configuration/OutboxProperties.java`
- Test: `src/test/java/com/example/contractpayments/outbox/OutboxClaimingIT.java`
- Test: `src/test/java/com/example/contractpayments/outbox/OutboxPublisherTest.java`

**Interfaces:**
- Produce `OutboxStore.claimBatch(now, batchSize, lease): List<OutboxMessage>`.
- Produce `OutboxStore.markPublished(eventId, now)` and `reschedule(eventId, availableAt, error)`.
- Consume `EventPublisher.publish(OutboxMessage)`.

- [x] Write failing tests showing parallel workers claim disjoint rows, expired leases are reclaimed, success is acknowledged, and failure is rescheduled with bounded exponential backoff.
- [x] Implement native PostgreSQL claim SQL with `FOR UPDATE SKIP LOCKED` inside a short transaction.
- [x] Implement the application publisher without Kafka dependencies and sanitize stored errors.
- [x] Configure a bounded Spring-managed executor and scheduling properties; do not create raw threads.
- [x] Run outbox unit and integration tests.

### Task 5: Add Kafka publication and Docker infrastructure

**Files:**
- Modify: `pom.xml`
- Modify: `compose.yaml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Create: `src/main/java/com/example/contractpayments/outbox/adapter/out/kafka/KafkaEventPublisher.java`
- Create: `src/main/java/com/example/contractpayments/configuration/KafkaConfiguration.java`
- Test: `src/test/java/com/example/contractpayments/outbox/KafkaPublicationIT.java`

**Interfaces:**
- Implement `EventPublisher` with topic `job-paid.v1` and key `jobId`.
- Serialize the exact version-1 event envelope from the specification.

- [x] Write a failing Kafka integration test that pays a job, runs the outbox publisher, consumes the event, and verifies `eventId`, version, IDs, and amount.
- [x] Add Spring Kafka and a single-node KRaft Kafka service with a health check.
- [x] Configure producer idempotence, `acks=all`, safe retries, JSON serialization, and typed topic settings.
- [x] Implement the Kafka adapter and topic bean.
- [x] Run Kafka publication and full integration tests.

### Task 6: Add operational visibility

**Files:**
- Create: `src/main/java/com/example/contractpayments/outbox/adapter/in/scheduling/OutboxScheduler.java`
- Create: `src/main/java/com/example/contractpayments/outbox/adapter/out/metrics/OutboxMetrics.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/contractpayments/outbox/OutboxSchedulerTest.java`

**Interfaces:**
- Consume `PublishOutboxBatch` from the scheduler.
- Expose pending count, oldest pending age, published count, failure count, and retry count through Micrometer.

- [x] Write failing tests for disabled scheduling, bounded dispatch, and metric changes on success/failure.
- [x] Implement scheduling through the managed executor and typed configuration.
- [x] Add structured identifiers to publisher logs and keep payloads out of logs.
- [x] Run focused tests and verify Actuator health/metrics configuration loads.

### Task 7: Rewrite documentation and ADRs

**Files:**
- Rewrite: `README.md`
- Rewrite: `docs/adr/001-domain-and-money.md`
- Rewrite: `docs/adr/002-payment-transaction.md`
- Rewrite: `docs/adr/003-idempotency-and-messaging.md`
- Create: `docs/adr/004-hexagonal-boundaries.md`
- Create: `docs/adr/005-cache-and-read-replicas.md`
- Create: `docs/event-contracts/job-paid.v1.json`

**Interfaces:**
- Document commands that run unchanged from a fresh clone.
- Document the stable Kafka event consumed by notification, invoicing, and analytics groups.

- [x] Add Mermaid component, ER, payment sequence, outbox state, and Kafka fan-out diagrams.
- [x] Label `READ COMMITTED` and every `FOR UPDATE` row in the payment sequence.
- [x] Explain at-least-once delivery, consumer deduplication, indexes, caching exclusions, replica consistency, and scaling limits.
- [x] Add the JSON event example and cross-link all ADRs.
- [x] Scan documentation for contradictions, placeholders, broken local links, and the unwanted company name.

### Task 8: Verify, collapse history, and publish

**Files:**
- Modify: `.github/workflows/ci.yml`
- Verify: all tracked source, tests, documentation, Git refs, and commit metadata.

**Interfaces:**
- CI starts PostgreSQL 17 and Kafka, then runs `./mvnw --batch-mode verify` on Java 21.
- The remote `main` branch receives one root commit.

- [x] Run `mvn clean verify` against fresh PostgreSQL and Kafka containers and inspect test totals.
- [x] Run clean-tree, diff, author/committer, forbidden-name, dependency-boundary, and Docker Compose validation checks.
- [x] Update CI and push a temporary verification commit; wait for GitHub CI to pass.
- [x] Create an orphan branch from the verified working tree, stage only intended files, and create one root commit with the verified user identity.
- [x] Confirm the root tree and commit metadata contain no forbidden name or attribution trailers.
- [x] Force-push with `--force-with-lease`, verify remote `main` has exactly one commit, and wait for final GitHub CI.
