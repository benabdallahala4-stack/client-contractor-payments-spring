# ADR 004: Hexagonal boundaries around payment and messaging

**Status:** Accepted

## Context

HTTP, JPA, PostgreSQL, scheduling, and Kafka change for different reasons. Putting them in one service makes domain behavior hard to test and obscures the transaction boundary.

## Decision

Keep domain records and application ports free of framework imports. Inbound web adapters translate HTTP into application commands. Outbound persistence and Kafka adapters implement application ports. Spring configuration performs constructor-based wiring.

Use capability-first packages (`payment`, `job`, `contract`, `profile`, and `outbox`) with `domain`, `application`, and `adapter` beneath each capability. Avoid separate deployable modules until independent deployment or team ownership requires them.

## Consequences

Payment and outbox policies can be tested without Spring or Kafka. Technology code remains replaceable. The project has more small types, but dependency direction is explicit and an architecture test prevents framework imports from crossing inward.
