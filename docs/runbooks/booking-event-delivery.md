# Booking event delivery and intent history

This local MOCK consumer records booking-intent activity. It does not reserve travel, charge customers, notify external recipients or publish to a broker. Pricing outbox delivery remains pending. See [ADR 0004](../adr/0004-booking-event-delivery.md).

## Configuration and ownership

V6 is additive: delivery metadata, immutable inbox/timeline receipts, replay audit, atomic scheduling trigger, and a backfill of existing booking outbox work. Do not modify V1–V5 or erase the local PostgreSQL volume. On startup Flyway applies V6 before workers run. Existing unpublished events are processed in aggregate-version order, even when the booking quote has since expired; history delivery never performs booking validation or revives eligibility.

`BOOKING_EVENTS_ENABLED=true` enables the dispatcher; false pauses delivery without losing queued work. `BOOKING_WORKER_ENABLED` independently controls offer validation. Restart the API after changing environment configuration. The dispatcher performs one delivery per 250ms tick by default, has a 15-second lease and at most five claims per replay generation. Retries back off approximately 1/2/4/8 seconds plus up to 250ms deterministic jitter; the fifth failed claim quarantines work. A fifth crash is quarantined when its lease expires and a worker next examines it. These are local defaults, not throughput claims. The default scheduler is shared; a slow validation task can delay history, which the UI discloses.

Customer history: `GET /api/v1/bookings/{id}/timeline?afterVersion=0&size=20`, `bookings:read`, owned resources only. Ordered ascending by aggregate version, maximum 50 entries per page; `nextAfterVersion` is an exclusive cursor. Empty history can be valid while delivery is pending. `bookingVersion` is authoritative at the read snapshot, `deliveredVersion` is the contiguous consumer prefix. `CAUGHT_UP` refers to that prefix, not payment or supplier confirmation. A lost publisher acknowledgement can coexist with a complete timeline. `ATTENTION_REQUIRED` means quarantined delivery exists; the customer UI exposes no event internals or diagnostic bodies.

## Diagnose without disclosing PII

Use an authorized database connection. These reads contain event IDs/statuses only. The application runtime does not expose replay endpoints or broad admin access.

```sql
SELECT d.event_id,o.aggregate_id,o.aggregate_version,d.status,d.attempts,
       d.generation,d.queued_at,d.next_attempt_at,d.lease_until,d.failure_code
FROM booking_event_delivery d JOIN booking_outbox o ON o.id=d.event_id
WHERE d.status <> 'DELIVERED'
ORDER BY d.queued_at LIMIT 50;

SELECT count(*) AS published_without_receipt
FROM booking_outbox o LEFT JOIN booking_timeline_event t ON t.event_id=o.id
WHERE o.published_at IS NOT NULL AND t.event_id IS NULL;
```

The second query must be zero in normal operation. Pre-V6 application code never published booking events; manually pre-marked events with no receipt are an anomaly, not evidence of delivery. Investigate them rather than clearing markers or inventing history.

Micrometer instruments: `travbook.booking.events` with bounded outcome/mode labels; `travbook.booking.events.pending`, `.quarantined`, `.oldest.seconds` gauges sampled every ten seconds. Pending includes quarantined and blocked successors; age starts when delivery metadata is queued (migration time for backfilled history). Counter outcomes include DELIVERED, RETRY, DEAD, STALE, STORAGE_FAILURE and HEALTH_READ_FAILURE. No event/customer IDs, envelopes, names or exception text appear as labels. A health-read failure leaves the last gauge sample unchanged: monitor its counter too. Authenticated production scraping/CloudWatch routing and alarms remain unconfigured. Proposed operator alerts: any quarantine, repeated storage failures, or oldest pending age above 60 seconds; thresholds require workload measurement.

`INVALID_ENVELOPE`, `EVENT_CONFLICT`, `ORDER_GAP` and `INVALID_TRANSITION` are permanent failures. Diagnose source/consumer compatibility and predecessor receipts before replay. `DELIVERY_UNAVAILABLE` is retryable up to the limit; `ATTEMPTS_EXHAUSTED` indicates recovered process losses exhausted the budget. Only that booking's later events are blocked; other aggregates can proceed.

## Replay after diagnosis

The database-owner function is revoked from PUBLIC. Production must separate migration/owner/runtime/operator roles; the current local database uses one development owner and does not claim production least privilege. Grant function execution only under an approved operational role design. It is SECURITY INVOKER, not an elevation mechanism.

After correcting a compatible consumer/storage failure, use psql bound variables with a real quarantined event ID and an incident reference. Avoid PII/secrets in actor/reason. This records an immutable audit row and resets a bounded generation, preserving the source event ID/body and prior consumer receipt.

```sql
-- In psql, set event_id, operator and reason to reviewed incident values first.
SELECT replay_booking_event(:'event_id'::uuid, :'operator', :'reason');
```

The function only accepts DEAD work and a reason of 10–500 characters. If a permanently malformed source cannot be handled under a reviewed compatibility strategy, leave it quarantined. Do not edit immutable event content, skip aggregate versions, delete inbox receipts, force publication, or fabricate success. There is no general-purpose schema-repair tool in this increment.

## Recovery and rollback

Stop the process at any point; an abandoned claim becomes eligible after its database-clock lease expires. Delivery occurs outside publisher transactions. Consumer receipt/projection commit atomically; an identical redelivery is a no-op. Stale fencing tokens cannot acknowledge or schedule retries. Source event bodies, timeline receipts and replay audits reject normal UPDATE/DELETE. A database administrator with DDL/TRUNCATE privileges can bypass these guards; restrict those privileges before production.

If UI/API changes must be rolled back, disable the event dispatcher and roll back application code; keep additive V6 tables/triggers/data. Old booking creation still schedules work via the trigger. Do not roll back migrations destructively. The fixed v1 event consumer must quarantine unsupported schema versions until an intentional compatible migration is deployed.

Local stop preserves state: stop API/web validation processes, then `docker compose stop identity postgres`. Never use volume deletion for recovery. Actual crash/restart and rollback tests run against isolated Testcontainers, with lease expiry advanced in the fixture to simulate elapsed downtime; they are not production DR evidence.

```mermaid
sequenceDiagram
  participant B as Booking transaction
  participant O as PostgreSQL outbox/delivery
  participant W as Dispatcher
  participant C as Local consumer port
  participant I as Immutable inbox/timeline
  B->>O: Atomic state + event + READY metadata
  W->>O: Claim oldest unacknowledged aggregate event; persist attempt/lease
  W->>C: Deliver outside publisher transaction
  C->>I: Lock aggregate; validate/deduplicate/order; commit receipt
  C-->>W: Durable acknowledgement
  W->>O: Fenced publication transaction
  Note over W,I: Crash after receipt commit causes safe identical redelivery
```
