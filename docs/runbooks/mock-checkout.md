# MOCK checkout and recovery

This increment enables deterministic **AUTHORIZE_RESERVE_CAPTURE** for sample-aster and sample-horizon. It uses local durable payment/supplier simulators. No real provider, money, inventory, PNR or ticket is involved. Read [ADR 0005](../adr/0005-mock-checkout.md) and [verification](../verification-mock-checkout.md).

## Local operation

Use the [existing local setup](../../README.md). Flyway V7 adds checkout/work/command/outbox tables, supplier fixtures, payment accounts/receipts and a balanced immutable capture journal. Previous schemas/data remain intact. `INTEGRATION_MODE=MOCK` is mandatory; SANDBOX/LIVE fail closed. `BOOKING_CHECKOUT_ENABLED=true` runs checkout claims; false pauses them without erasing durable work. Validation and validation-event delivery keep their independent switches.

Search → create and accept a quote → add fictional travellers → create an intent → await offer validation → choose a demonstration outcome → explicitly consent → **Start MOCK checkout**. Refresh recovers the existing checkout after a lost acknowledgement. No sensitive payment fields are collected. One checkout per booking intent prevents a new key or browser refresh from starting another workflow; a changed scenario conflicts. The customer cannot reset review/failed checkout by reusing a new key.

| Demonstration | Expected final observations |
|---|---|
| SUCCESS | Supplier CONFIRMED, payment CAPTURED, checkout CONFIRMED, two balanced MOCK journal entries |
| PAYMENT_DECLINED | Payment DECLINED, supplier NOT_REQUESTED, checkout FAILED; no journal |
| RESERVATION_REJECTED | Supplier REJECTED, payment VOIDED, checkout FAILED; no journal |
| RESERVATION_RESPONSE_LOST | Provider receipt commits before response loss; lookup recovers it, then capture proceeds if still eligible |
| CAPTURE_DECLINED | Supplier CANCELLED before payment VOIDED; checkout FAILED; no journal |
| CAPTURE_RESPONSE_LOST | Capture and balanced journal commit before response loss; lookup recovers capture, without another charge |

UNKNOWN is persisted before a potentially effective call, including between a crash and recovery. It can mean the call is starting or its result has not yet been durably observed. It never means no effect occurred. REVIEW means the bounded attempt budget is exhausted or an incompatible outcome was observed; preserve provider references and investigate. It does not mean declined/refunded/cancelled. A successful payment alone does not confirm the checkout.

The quote and authorization deadline is the immutable quote expiry. Eligibility and fresh supplier terms are checked before authorization/new reservation. Expiry before authorization fails without an effect; expiry before a new reservation voids an existing authorization; expiry after reservation but before capture cancels then voids. An already committed capture is reconciled after expiry, not cancelled or charged again merely because its response arrived late.

## API/version compatibility

`POST /api/v2/bookings/{id}/checkout` requires `bookings:write`, ownership, CSRF for session cookies, an idempotency key, scenario and consent. The body accepts neither owner nor amount. A stable 202 ACK is acceptance, not fulfilment. Equivalent same-key retries recover the original ACK even after expiry; different keys with the same booking/scenario recover one checkout. Scope/key/body/ownership negatives are tested.

`GET /api/v2/bookings/{id}` and `/api/v2/me/bookings` require `bookings:read`. Separate validation and checkout state are explicit. Before checkout, the v1 detail response remains unchanged. Once checkout exists, `/api/v1/bookings/{id}` returns 409 BOOKING_CONFLICT directing callers to v2; it cannot truthfully continue promising constant NOT_STARTED payment metadata. Existing v1 list status is historical **validation** status, not fulfilment. The current frontend consumes v2 and keeps bounded/background-paused polling. Terminal success/failure/review stops automatic reads; refresh is available. UI does not infer success from a gateway return URL.

This is an intentional behavioral version transition despite additive OpenAPI schemas. Do not roll back to an application that lacks the v1 guard after starting checkouts, or run old and new API versions against active checkouts in an unplanned rolling deployment. Pause workers and forward-fix, or ship a compatible read-only v2/legacy guard patch first. Keep V7 data/migrations; never roll financial state back by deleting rows.

## Durability and provider contract

Each step persists its operation identity, UNKNOWN observation where applicable, claim attempt and fencing token before I/O. IDs derive deterministically from checkout ID and step. Workflow definition 1 is selected explicitly. A 15-second database-clock lease can be reclaimed; stale workers cannot commit observations. Each step permits at most five provider attempts. Retriable uncertainty schedules 1/2/4/8-second delays with deterministic jitter; a sixth recovered claim performs no provider call and enters review. Provider/local JDBC transactions have a three-second timeout; pool acquisition remains bounded by existing configuration.

Every effect first queries its operation ID. A lookup exception is unknown, never absence. Only a definitive empty lookup permits a safely idempotent mutation. MOCK provider lookups/mutations serialize per operation ID with transaction-scoped PostgreSQL advisory locks, so lookups do not incorrectly report absence while a prior local effect transaction is in progress. Provider mutation and immutable receipt commit together; the orchestration completion uses a **different** transaction. No orchestration transaction encloses provider execution. A local completion failure leaves the receipt recoverable. The actual process-crash test halts after supplier commit and resumes in another JVM; this is not a production DR benchmark.

Payments owns its account, operation receipts and capture journal. Booking accesses only the published payment application port. Capture uses two immutable equal debit/credit entries with deferred balance/completeness checks; payment identity, owner, amount, currency, expiry and legal state transitions are guarded. These are synthetic accounting fixtures, not a settlement ledger or regulatory compliance evidence. Supplier cancellation here is compensation before a successful capture, not a customer post-booking cancellation/refund feature.

Checkout state and privacy-safe lifecycle events commit atomically in `booking_checkout_outbox`. [CheckoutStateRecorded v1](../../contracts/events/checkout-v1.schema.json) carries separate states and no traveller details. This new outbox is retained but **not dispatched yet**; existing intent validation events still use the durable local consumer. Payment/cancellation notifications and external event transport remain pending.

## Diagnostics and unresolved work

```sql
SELECT c.status,c.step,c.payment_status,c.reservation_status,w.state,w.attempts,
       w.next_attempt_at,w.lease_until,c.id
FROM booking_checkout c JOIN booking_checkout_work w ON w.checkout_id=c.id
WHERE c.status IN ('RUNNING','REVIEW') ORDER BY w.next_attempt_at LIMIT 50;

SELECT payment_id,count(*),sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) AS balance
FROM payment_journal GROUP BY payment_id;
```

Use authorized operator access and do not dump quote/traveller snapshots or authentication sessions. `travbook.booking.checkout.ticks` counts processed/storage-failure outcomes without customer labels. Backlog/review alerts, production dashboard routing and supplier/gateway settlement reconciliation are not implemented. Review currently needs diagnosis, not an unsafe automatic replay or a direct SQL status edit. No privileged customer-facing repair button is provided. Do not clear keys, reset attempts or force confirmation; an audited operational resolution capability remains P0 work.

The v2 list uses bounded pagination but still performs per-item checkout/ownership lookups; load testing and query consolidation remain a performance gate. Document generation/download, customer cancellation/refund, payment webhooks/hosted collection, alternative supplier strategies, real integrations, AWS deployment and full P0 platform completion remain outstanding. Stop validation services with `docker compose stop identity postgres`, preserving the named volume. Never delete the volume as a recovery procedure.
