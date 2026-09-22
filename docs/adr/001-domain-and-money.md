# ADR 001: Profiles, contracts, jobs, and integer cents

**Status:** Accepted

## Context

The interview example involves a client paying a contractor for a job under a contract. Generic accounts hide why a payment is allowed and whether it has already happened.

## Decision

Represent client and contractor as profiles with immutable roles and demo balances. A contract has one client and one contractor. Each job belongs to one active contract, has a positive amount, and starts open. A payment belongs to exactly one job. PostgreSQL foreign keys and CHECK constraints reinforce the relationships; Java validates role assignments when creating a contract.

Store money as integer minor units in a single implicit currency with a 2,147,483,647-cent ceiling. API validation and database constraints reject negative balances, invalid amounts, and receiver overflow.

## Options considered

| Option | Advantage | Cost |
| --- | --- | --- |
| Integer cents and one currency — chosen | Exact arithmetic and simple JSON | Explicit limit and no FX support |
| Decimal money with currency code | Closer to a multi-currency system | Scale, rounding, and FX policy beyond this lesson |
| Bare account transfers | Smallest schema | Does not show contract/job rules |

## Consequences

The model is specific enough to explain payment authorization from domain state while staying small. It does not model user sign-in, contract negotiation, taxes, funding, refunds, or a real financial ledger. Opening balances exist only for local demonstrations.
