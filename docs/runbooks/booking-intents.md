# Durable MOCK booking intent runbook

## Start and use

Use the existing [local setup](../../README.md): start PostgreSQL and the local identity container, then the API with the local-oidc profile and the web app. The booking worker is enabled by default; `BOOKING_WORKER_ENABLED=false` pauses automatic processing without deleting work.

The local identity realm now grants `bookings:read` and `bookings:write`. If an older development realm already exists, recreate **only** its disposable identity container:

```sh
docker compose up -d --wait postgres
docker compose up -d --force-recreate --wait identity
sh scripts/maven.sh -f apps/api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local-oidc
```

Start `npm run dev` separately. The test users keep their deterministic subject IDs, so PostgreSQL customer/quote ownership survives identity recreation. Identity sessions are reset. Do not recreate a real IdP or apply these public test credentials to a deployed environment. External OIDC clients need the two new scopes configured before enabling booking UI; no external provider was changed.

1. Search a simulated flight and create/accept an owned quotation.
2. Select **Add travellers and create intent**. The original adult/child/infant counts come from the published discovery contract.
3. Use fictional names and dates of birth. Review the displayed counts and explicit no-reservation/no-payment notice.
4. Submit once. A stable `202 Accepted` acknowledgement means the intent, workflow, reservation-not-requested record, command and outbox are durably committed.
5. Read the booking status. My Trips lists only the signed-in customer's intent summaries and uses keyset pagination. Traveller names/DOB appear only in the owned detail response.

## Meaning of states

| Booking state | Meaning / action |
|---|---|
| CREATED | Durable intent queued for validation |
| VALIDATING | A worker is checking the accepted terms; a retry may be scheduled |
| AWAITING_PAYMENT | Initial validation matched the quote; this status alone reserves/charges nothing. Subsequent MOCK checkout is tracked separately in v2 |
| REQUIRES_REQUOTE | Price or material terms changed; review and accept a new quote |
| INVENTORY_UNAVAILABLE | The provider explicitly reported unavailable inventory; choose another offer |
| EXPIRED | The accepted quote expired before durable acceptance/validation completion; obtain a new quote |
| MANUAL_REVIEW | Bounded read attempts were exhausted or the reference could not be reconciled; no reservation or charge has been made |

Workflow `terminal` means **this validation step** is finished, not that travel is fulfilled. An already validated intent remains historical AWAITING_PAYMENT if its quote later expires; any future payment/reservation use case must check expiry/eligibility and inventory again. This increment does not enable such an operation.

Reservation state is persisted as NOT_REQUESTED, with separate inventory-check state. Payment is explicitly NOT_STARTED because no payment aggregate or gateway operation exists. Do not manually manufacture references or promote a state to confirmed.

## Idempotency and input

- Require `Idempotency-Key` and ownership plus `bookings:write`. Browser cookies additionally require CSRF. The request contains only quote ID/version and travellers, never a browser total or owner ID.
- The quote must be owned, accepted at the exact version and unexpired. The immutable booking input copies the accepted terms, quote/receipt references and the original search category mix.
- Names are trimmed, internal whitespace collapsed and Unicode NFC normalized. Traveller order is significant in the canonical fingerprint. Names/DOB are not stored in URLs, localStorage or sessionStorage. The pending request UUID may be in the URL; it is not an authorization credential.
- The MOCK policy uses age at every departure: adult 12+, child 2–11, infant under 2, maximum age 120. Reject category changes over a return journey, mismatched counts and duplicate normalized entries. Real supplier eligibility is still an integration dependency.
- One quote creates at most one intent. Equivalent same-key retries return the original acknowledgement, even after expiry. A different key with the same quote/payload recovers that intent; changed payloads conflict. No existing traveller data is overwritten.
- Refresh after a lost acknowledgement reads the existing intent through booking requirements; it does not replay a mutation or invent confirmation. Unsaved traveller input must be re-entered after refresh if no operation was committed.

## Worker recovery

Definition version 1 has a single VALIDATE_OFFER step. The worker polls PostgreSQL every 500 ms, claiming one due/abandoned job with SKIP LOCKED and a 15-second **database-clock** lease. Each claim receives a fresh fencing token; final updates check it and the live lease again. Input/state changes, workflow outcome, inventory-check state and booking events share a transaction.

A persisted read-start marker precedes provider I/O. No database transaction spans the read. A crash leaves committed work recoverable after lease expiry. Stale workers cannot overwrite a newer attempt. There are at most four possible provider reads per workflow; failures schedule 1/2/4-second backoff plus deterministic per-intent jitter up to 250 ms. General backoff is capped at 8.25 seconds. Read uncertainty is not translated into zero inventory or a fabricated reservation failure.

On API/worker restart, leave PostgreSQL intact and start the normal application. Do not reset attempt counters, expire leases manually, delete command records or edit immutable inputs. Test-only lease expiry injection exists solely in isolated Testcontainers to simulate elapsed downtime after an actual child-JVM crash.

Safe diagnostics omit traveller payloads:

```sql
SELECT b.status, w.status AS workflow_status, w.definition_version,
       count(*) AS intents, max(w.attempts) AS max_attempts
FROM booking_intent b JOIN booking_workflow w ON w.booking_id=b.id
GROUP BY b.status,w.status,w.definition_version;

SELECT count(*) AS overdue
FROM booking_workflow
WHERE (status IN ('READY','WAITING_RETRY') AND next_attempt_at < now()-interval '30 seconds')
   OR (status='RUNNING' AND lease_until < now()-interval '30 seconds');
```

Check database health and worker enablement if work remains overdue. `travbook.booking.validation.ticks` tracks processed claims/storage failures without high-cardinality customer labels. Prometheus remains protected by the current security boundary; no production alarm/dashboard claim is made. Unknown/corrupt persisted input or database failures require operator investigation; this slice does not implement an administrative repair endpoint.

## Events, migration and limits

V4 adds booking input, command, workflow, reservation-check and outbox tables. V5 persists workflow definition version 1. Prior discovery/identity/quote data and migrations are preserved. Booking inputs and commands cannot be rewritten/deleted through normal row mutations; mutable status must increment its version. Outbox content is immutable. V6 adds local durable delivery and owned history; see the [event runbook](booking-event-delivery.md).

Events are `BookingCreated`, `BookingValidationStarted`, `BookingAwaitingPayment`, `BookingValidationRetryScheduled`, `BookingValidationRejected` and `BookingManualReviewRequired`. They contain IDs, versions, statuses and correlation/causation metadata, never traveller names/DOB. They are stored atomically and delivered to the local durable history consumer; pricing/external event delivery remains pending.

```sh
set -o pipefail
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from booking_outbox' | node scripts/validate-booking-events.mjs
```

The API request limiter retains a 240-token burst/4 tokens per second application budget and a separate 120-token burst/4 tokens per second identity budget, so API traffic cannot consume login capacity. Both are process-wide; distributed per-client enforcement and capacity sizing remain production work. The UI stops automatic status polling after 20 reads, backs off to eight seconds, pauses in the background and stops at validation-terminal states. Manual refresh remains available.

This initial validation workflow has no provider effects. The subsequent [MOCK checkout increment](mock-checkout.md) adds authorize/reserve/capture and cancel/void compensation; ticketing, refunds and production infrastructure remain pending. Before those effects are added, preserve supplier-specific sequencing and independently persisted financial/reservation state, provider idempotency, unknown-outcome reconciliation and compensation. Do not reuse read-only retry rules for effective provider calls.

The local database role is intentionally for development. Managed encryption, least-privilege runtime permissions (including denying TRUNCATE/DDL), retention/deletion policy and security review are prerequisites before real traveller data or deployment. Stop local services with `docker compose stop identity postgres`; the PostgreSQL volume remains.
