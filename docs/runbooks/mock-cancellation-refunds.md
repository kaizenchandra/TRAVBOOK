# MOCK cancellation and refunds

This slice supports one full-booking cancellation before first departure, for captured/confirmed MOCK flights only. No real supplier/gateway or bank refund is invoked. See [ADR 0007](../adr/0007-mock-cancellation-refunds.md) and [verification](../verification-cancellation-refunds.md).

## Policy and customer flow

New MOCK searches expose exact versioned fixture policies: the first two fares per provider use `MOCK-FULL-V1` (refund the entire quoted total including simulated taxes); the other two use `MOCK-NONE-V1` (retain the entire total). The existing sold-out fourth-fare fixture still applies. Policies appear before quote acceptance and payment. Original offers, quotes and paid records are not rewritten; repricing retains their original policy. Legacy prose, including the old ambiguous non-refundable fare text, requires policy review and cannot produce an automatic cancellation quote. These rules are synthetic, not legal/tax guidance.

After confirmed checkout, select **Request cancellation quote**. The backend verifies ownership, original accepted policy, future first departure and current MOCK supplier reservation/terms. It returns an immutable quote with total, refund and retained amounts, version 1, expiring after five minutes or departure. Generating a quote has no cancellation effect. Explicitly accept the displayed amounts and confirm the whole-booking cancellation. A 202 acknowledgement means durable acceptance only.

The worker first cancels with the supplier. Verified cancellation then establishes either a positive refund entitlement or `NOT_DUE`. A positive refund is durably submitted as PENDING; an independent MOCK settlement worker posts the refund. The customer can see **CANCELLED + PENDING**, followed by **CANCELLED + SUCCEEDED**. Zero entitlement never creates a gateway refund or a fake successful zero refund.

| State | Interpretation |
|---|---|
| Cancellation PENDING | Supplier outcome is not yet durably verified; do not assume cancelled |
| Cancellation CANCELLED | Supplier cancellation receipt verified; refund tracked separately |
| Cancellation REJECTED | Verified non-execution/refusal or expired unexecuted quote; no refund requested |
| Cancellation REVIEW | Unknown supplier outcome exhausted the bounded budget; no refund inferred |
| Refund NOT_REQUESTED | Supplier cancellation not verified |
| Refund PENDING | Entitlement established; submission/settlement still in progress or unknown |
| Refund SUCCEEDED | Provider refund aggregate and complete balanced journal committed |
| Refund NOT_DUE | Accepted zero-refund policy; no refund transaction exists |
| Refund REVIEW | Cancellation may be final but refund outcome needs operational resolution |

Rejected cases cannot currently be replaced by another customer request; REVIEW resolution and retries after rejection require an audited operational capability that remains unimplemented. Do not bypass that restriction by changing keys or directly modifying state.

## API and compatibility

- `POST /api/v1/bookings/{id}/cancellation-quotes`: bookings:write, owned confirmed checkout, cookie CSRF and idempotency key; body contains only full-booking scope.
- `POST /api/v1/bookings/{id}/cancellations`: same protection; exact quote ID/version, explicit consent and MOCK demonstration scenario. Client amounts/owner fields are rejected.
- `GET /api/v1/bookings/{id}/refunds`: bookings:read plus ownership; zero/one positive refund case. No pagination is needed for the one supported whole-booking case.
- `GET /api/v3/bookings/{id}` and `/api/v3/me/bookings`: current independent cancellation/refund state alongside historical validation/checkout data. Frontend reads v3. Polling budgets reset for each new persisted validation/checkout/cancellation operation, stop after 20 reads or terminal/review/error, back off to eight seconds and pause in the background.

V1/v2 schemas are retained. Once a cancellation exists, legacy v2 detail reads (and any list page containing such a case) return 409 with a v3 direction. This deliberate fail-closed behavior prevents older clients from showing a cancelled trip as currently confirmed. Regenerate/update clients before enabling aftercare. Do not deploy a mix of old APIs that lack the guard against an active cancellation database. Existing v1 intent lists describe validation history only.

Active, cancelled or unresolved cancellation blocks document generation/download through the published booking contract. A definitive rejected case retains the original reservation and can access its historical summary. Previously downloaded files cannot be recalled; all summaries already state they are immutable MOCK snapshots, not tickets.

## Durability, money and recovery

V9 is additive. Booking owns cancellation quotes/cases/commands/work/outbox and the customer-cancellation provider fixture. Payments owns refund aggregates, settlement metadata, reversing journal and outbox. Booking calls the published payment refund port; it does not read payment tables directly.

One case per booking plus scoped key/fingerprint records prevent duplicate cancellation. Same-request retries recover the original acknowledgement even after expiry. Changed quote/scenario conflicts. Stable operation IDs derive from persisted case ID and step. Every possibly effective call first looks up that ID; an exception is unknown, never authoritative absence. MOCK provider receipt queries serialize with in-flight mutations. No orchestration transaction spans a provider call.

The supplier checks the accepted quote deadline, original reservation and unchanged policy before a new cancellation. Expired, definitively unexecuted cancellation is rejected. If an earlier effect already committed, recovery uses its receipt even after quote expiry. Refund execution after verified cancellation is not stopped by subsequent cancellation-quote expiry.

Pending and successful refund amounts both consume captured entitlement. Payment-account row locks and database constraints serialize competing requests and reject over-refund, wrong owner/currency or uncaptured payment. Immutable unique entitlement/operation bindings prevent duplicate payouts. Completed refunds append two reverse debit/credit entries; original capture rows and captured account history are not changed. Deferred constraints require complete, balanced journals at commit.

Cancellation work uses 15-second database-clock leases, fencing, at most five provider attempts per step and bounded 1/2/4/8-second backoff plus jitter. A sixth recovery claim performs no provider call and records REVIEW. The local MOCK refund settler persists a separate bounded settlement attempt and lease before its transaction; its journal, success state and completion event commit atomically. Expired settlement leases roll back uncommitted journal writes; at most five settlement claims are allowed. JDBC transactions have three-second timeouts.

`BOOKING_CANCELLATION_ENABLED=false` pauses cancellation orchestration. `REFUND_WORKER_ENABLED=false` pauses MOCK settlement independently. Existing validation, checkout and event workers retain their own flags. Restart without deleting data to recover leased work. The REFUND_DELAYED fixture becomes due five seconds after acceptance; that is a test fixture, not a bank or production refund SLA.

Real JVM tests halt after supplier cancellation commit and after refund/journal commit, then recover in a fresh JVM with the same database and no duplicate effects. Test-only lease expiry changes occur only in isolated Testcontainers. Late outcomes after REVIEW currently need audited operator resolution; the application does not automatically reopen terminal review cases.

## Operations and remaining gates

Use restricted operator access; omit snapshots/PII from diagnostics:

```sql
SELECT c.id,c.cancellation_status,c.refund_status,w.state,w.attempts,w.next_attempt_at,w.lease_until
FROM booking_cancellation c JOIN booking_cancellation_work w ON w.cancellation_id=c.id
WHERE w.state NOT IN ('DONE') ORDER BY w.next_attempt_at LIMIT 50;

SELECT operation_id,payment_id,status,settlement_attempts,ready_at,settlement_lease
FROM payment_refund WHERE status='PENDING' ORDER BY ready_at LIMIT 50;

SELECT p.id,p.amount,coalesce(sum(r.amount),0) AS committed_refunds
FROM payment_account p LEFT JOIN payment_refund r ON r.payment_id=p.id
GROUP BY p.id,p.amount HAVING coalesce(sum(r.amount),0)>p.amount;
```

The last query must return zero rows. Exhausted pending settlement or REVIEW requires investigation; do not delete commands, reset attempts, alter immutable quotes or insert corrective payouts blindly. Pending entitlement remains reserved. A reviewed correction/resolution API, privileged MFA and production role separation are still required.

`travbook.cancellation.ticks` and `travbook.refund.settlement` have bounded outcome/mode labels. Production alert routing, lag dashboards and full settlement reconciliation remain pending. Cancellation/refund outboxes are atomic and schema-versioned but are not dispatched yet; notifications are not claimed. Partial-passenger cancellation, variable penalty windows, real integrations, provider-issued documents, other travel verticals and Terraform/AWS deployment remain unfinished. No compliance, production availability, load or DR target is asserted.

```mermaid
sequenceDiagram
  participant U as Owned customer UI
  participant B as Booking cancellation workflow
  participant S as Durable MOCK supplier
  participant P as Payments refund aggregate
  participant W as MOCK settlement worker
  U->>B: Request immutable cancellation quote
  U->>B: Accept exact amounts (idempotent)
  B->>S: Lookup stable operation; cancel only if absent and eligible
  S-->>B: Verified cancellation receipt
  Note over B: CANCELLED, refund PENDING or NOT_DUE
  B->>P: Lookup/submit positive entitlement
  P-->>B: PENDING receipt
  W->>P: Atomic reversing journal + SUCCEEDED + outbox
  B->>P: Reconcile same operation ID
  P-->>B: Verified completed refund
  B-->>U: CANCELLED + SUCCEEDED (MOCK)
```
