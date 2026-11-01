# Owned hotel lifecycle — local MOCK operations

Use fictional guest details only. Start/update with the [Compose guide](local-compose.md). Flyway applies additive V12 and V13; preserve the existing PostgreSQL volume and prior migrations. Open <http://127.0.0.1:3000/hotels>.

## Customer journey

1. Search dates/rooms and choose one room plan per requested room. **Get an owned hotel quote** rechecks current prices under an authenticated account. Sign-in returns to the selected quote request, retaining only resource IDs.
2. Review the immutable total, per-room taxes/fees, meals and refund deadlines; explicitly accept. A quote expires within the provider/search window and before check-in. Acceptance is not a hold or booking.
3. Enter every guest's name and date of birth, matching room adults/child ages at check-in. Optional late arrival is recorded as a request and is not guaranteed. Choose a demonstration outcome and consent to MOCK reservation/payment.
4. Booking is persisted before work runs. The worker revalidates terms, authorizes, reserves rooms and captures. View separate verified states. Refresh safely; an existing booking is recoverable from its quote even if the creation response was lost.
5. Once confirmed, prepare/download the protected MOCK accommodation voucher. Repeating preparation returns the original immutable artifact. It is **not valid for check-in** and contains no guest DOB.
6. Request a cancellation quote. Refund entitlement sums each refundable room's full tax/fee-inclusive total before its deadline; non-refundable rooms retain their amounts. Review and consent before cancelling all rooms.
7. Supplier cancellation can finish while refund is PENDING. Wait for SUCCEEDED or NOT_DUE; a pending refund is not completed merely because the room is cancelled. Hotel cards are available in My Trips and `/stays/trips`.

Cancellation quotes are capped at five minutes, refund cutoffs and local check-in; refresh expired quotes explicitly. In-place changes and partial-room cancellation are not enabled. The current provider supports pay-now only; do not imply pay-at-property support. Existing legacy flight policies are unchanged.

## Deterministic recovery fixtures

| Fixture | Expected outcome |
|---|---|
| SUCCESS | Revalidate → authorize → reserve → capture → confirmed |
| AUTH_DECLINED | Failed, payment DECLINED, no reservation/capture |
| RESERVATION_REJECTED | Authorization VOIDED, no confirmed reservation or capture |
| CAPTURE_DECLINED | Confirmed supplier reservation cancelled; authorization voided; failed booking |
| RESERVATION_RESPONSE_LOST / CAPTURE_RESPONSE_LOST | Provider receipt lookup resumes exactly the saved operation; no extra reserve/capture |
| Delayed refund | Supplier cancellation completes; separate five-second MOCK settlement delay |
| Lost cancellation/refund responses | Stable operation references recover committed outcomes; no duplicate refund |
| Overlapping standard rooms | FLEX and VALUE compete for the same two-room nightly pool; one conflicting reservation fails safely |

Fixture capacities are per property/per night. Stock is released after supplier cancellation, including capture-failure compensation. Search/quote never holds stock. Live supplier rate limits, holds, cancellation rules, authentication and document formats need separately authorized adapters; all unsupported integration modes fail closed.

## Durable worker diagnosis

`STAY_WORKER_ENABLED=false` pauses hotel orchestration without deleting work. `STAY_POLL_MS` defaults to 300ms. Existing `REFUND_WORKER_ENABLED=false` pauses the shared MOCK settlement worker. Application restart retains quotes, JDBC login sessions, commands, provider receipts, allocations, workflow leases, accounting records and vouchers.

Read-only diagnosis in the local fixture database:

```sh
docker compose exec -T postgres psql -U travbook -d travbook -c "select status,count(*) from stay_booking group by status;"
docker compose exec -T postgres psql -U travbook -d travbook -c "select state,attempts,count(*) from stay_work group by state,attempts order by state,attempts;"
docker compose exec -T postgres psql -U travbook -d travbook -c "select property_id,night,room_group,sum(quantity) from hotel_room_allocation where active group by property_id,night,room_group order by property_id,night;"
docker compose exec -T postgres psql -U travbook -d travbook -At -c "select envelope::text from stay_outbox order by created_at,id" | node scripts/validate-stay-events.mjs
```

Avoid dumping guest-bearing payloads, sessions or tokens. Lifecycle outbox payloads are state-only. `travbook.stays.worker` counts processed claims; existing supplier/payment/refund metrics supplement diagnosis. Full dashboards/alerts remain pending.

RUNNING leases recover after 15 seconds using database time. External operations are looked up before retry, with stable IDs derived from booking/step. Attempts are bounded and back off. REVIEW means an unresolved outcome requiring reconciliation, not authorization to create another payment or force database statuses. Do not clear leases or alter financial/provider receipts in production. There is no general operator resolution API yet; isolate the case and obtain provider/ledger evidence before implementing an audited resolution.

Vouchers are blocked once cancellation/review is active; prior downloaded files cannot be recalled and remain explicitly MOCK. No email/SMS, live funds, external provider reservation, valid hotel check-in document, production compliance or AWS deployment is claimed. Current evidence and remaining gates are in [verification](../verification-hotel-lifecycle.md) and [ADR 0011](../adr/0011-owned-hotel-lifecycle.md).

## Current operator recovery extension

New REVIEW transitions now save an immutable checkpoint. Authorized operators with recent
MFA may request receipt-backed resumption through the [restricted recovery workflow](review-recovery.md).
This supersedes the earlier blanket “manual recovery unimplemented” limitation only for
this constrained path. Missing legacy checkpoints, contradictory/no receipt, arbitrary
status changes and financial adjustments remain blocked. Customer booking APIs are unchanged.
