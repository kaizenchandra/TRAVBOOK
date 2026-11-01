# Rail lifecycle verification — 2026-10-31

Scope: **AC-051–054, LOCAL_OIDC + deterministic MOCK**. No real railway/gateway calls,
real PNR, authorized ticket, compliance claim or AWS deployment. Source requirements remain
In progress where approved provider rules, live ticketing or full functionality is absent.

Implemented: passenger/quota/boarding/berth eligibility; immutable owned fare quotations and
consent; durable authorize/reserve/capture and compensation; separate ISSUED vs ticket
CONFIRMED/RAC/WAITLIST; owned MOCK PNR; protected ticket summaries; cancellation/refund;
My Trips, bounded status updates and safe refresh. PostgreSQL V14 is additive. Customer and
payment contexts are accessed only through published contracts; ArchUnit enforces this.

## Executed checks

| Command/check | Actual outcome |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | Final run: **152 tests passed, 0 failures/errors/skips**, executable JAR built. Includes 13 new rail lifecycle integration tests, 14 architecture checks, migration upgrade and return-path tests. Final receipt-guard run completed 16:16 IST. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e` | **76 passed** (5.4m), desktop/mobile regression across flights, account/quotes, bookings, payment/refund/documents, hotels and trains. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- rail-tickets.spec.ts trains.spec.ts` | **16 passed** (1.5m) after final customer-facing progress wording and addition of invalid-age correction in the UI. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- rail-tickets.spec.ts --grep 'train selection, OIDC'` | **2 passed** (37.6s), final desktop/mobile full lifecycle smoke against the final receipt-guard API and polished web images. |
| `npm audit --audit-level=moderate` | **0 vulnerabilities**. |
| `npm run typecheck` | Passed on final frontend source/generated client. |
| `npm test` | **6 passed**. |
| `npm run contract:test` | **9 passed**. |
| `npm run contract:lint` | Valid; four existing warnings (license metadata, localhost server and redirect-only auth operations). |
| `npm run contract:generate`; `npm run contract:check` | Generated TypeScript matches OpenAPI 0.11.0, including new request examples. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-before-rail-lifecycle.json` | Existing 0.10.0 machine contracts retained; additive changes only. Baseline copied before edits; no Git metadata required. |
| `npm run requirements:generate`; `npm run requirements:check` | **480 original source requirements/scenarios** preserved with original priorities and updated mappings. |
| `docker compose config --quiet` | Passed; rail worker controls explicitly forwarded. |
| `docker compose build backend frontend`, then focused final image rebuilds | Java and production Next.js builds passed. Final local image IDs: API `71cf61f1fd5b`, web `38ccf6c26e7c`, linux/arm64. |
| `docker compose up -d --wait --wait-timeout 180`; `docker compose ps` | API, web, local identity and PostgreSQL healthy; existing named data volume preserved. |
| Editor diagnostics | No errors in new rail backend/frontend and integration test files. |
| CI YAML parse using existing `js-yaml` dependency | Passed. Hosted GitHub Actions was **not run**. CI now includes stored rail event-schema validation. |

## Integration evidence

The browser path runs real local OIDC authorization-code login, Next.js typed API calls,
Spring Security ownership/CSRF, rail application/domain/ports, PostgreSQL and durable MOCK
supplier/payment adapters. It is not a frontend-only response mock. Responses for quotes,
booking states and document metadata are checked against OpenAPI. A UI-only expired-quote
fixture tests display; backend clock-driven tests separately verify actual expiry rejection.

- Invalid passenger age is rejected through the UI/API and corrected without a poisoned
  idempotency key. Domain tests cover adult accompaniment, count/Tatkal, boarding, duplicate
  passengers, unsupported 2AC middle preference and Unicode names.
- Quote ownership, acceptance/version/expiry, immutable database guards and one booking per
  quote hold. Concurrent identical commands produce one workflow, one reservation and one
  capture. Changed payloads under the same key conflict. Lost browser acknowledgements are
  recovered from the quote's persisted booking reference.
- Canonical stock contention across three distinct search IDs admits only available
  capacity; cancellation releases stock. Expiry after authorization rejects reservation and
  voids payment. Authorization, reservation and capture rejections never issue a ticket.
- Actual child JVMs exit with code 29 immediately after committed RESERVE, CAPTURE, CANCEL and
  REFUND effects, then new processes resume from durable receipts without extra captures or
  refunds. Provider calls are asserted outside orchestration transactions. Lost-response
  fixtures, stale lease fencing, bounded unknown-outcome REVIEW and outbox rollback pass.
- Contradictory payment receipts and an unexpected supplier ticket state fail binding
  validation. A different ticket state cannot reach capture; the domain also enforces the
  accepted allocation category. No silent downgrade or unsupported upgrade is inferred.
- Issued CONFIRMED/RAC/WAITLIST remain distinct. Owned MOCK PNR reflects current cancellation
  state. Foreign PNR/quote/booking/document access returns 404. Missing CSRF is denied;
  switching account during document preparation hides cached private information.
- Documents are prepared only for currently issued tickets, explicitly invalid for travel,
  SHA-256 verified, contain no DOB and are blocked as soon as cancellation starts.
- Cancellation quotes bind version/expiry/departure and exact original entitlement. Base
  fare is refunded and charges retained under the synthetic policy. Duplicate/lost
  cancellation/refund responses and delayed settlement preserve separate statuses and one
  balanced reversing entry pair. Browser tests cancel only their own synthetic bookings.
- Desktop/mobile axe checks (WCAG 2/2.1/2.2 AA rule sets), keyboard consent/submit and no
  horizontal overflow passed. Generated desktop/mobile screenshots were visually reviewed.
  This is targeted accessibility evidence, not a full platform conformance certification.

## Database and event checkpoint

After the 76-test regression and 16-test focused retest, local rail fixtures had 42 DONE
workflows: 24 CANCELLED and 18 FAILED. Active stock over-allocation groups: 0. Capture journal:
48 entries, net INR 0.00. Refund journal:48 entries, net INR 0.00. Refund totals exceeding
captured booking totals: 0. Testcontainers additionally assert individual effect counts and
bounded refund entitlements. No local user data was truncated.

Executed event validation:

```sh
docker compose exec -T postgres psql -U travbook -d travbook -At -c \
  "select envelope::text from rail_ticket_outbox order by created_at,id" \
  | node scripts/validate-rail-events.mjs
```

**402 committed envelopes passed** the versioned schema at this checkpoint. Payloads contain
states only, not passenger data. Outbox persistence/timeline is verified; external transport
and consumer replay are not implemented for this context. Later smoke tests add fixtures;
these counts describe the checkpoint, not a permanent database invariant.

## Corrections and remaining limits

An initial browser run was interrupted before booking because the test used “Departure date”
instead of the existing “Journey date” accessible label. The first full backend run had one
migration-test failure: its old tail assertion expected one migration, now there were two.
The test now explicitly applies V13 and V14 separately and verifies old booking/search
sentinels and empty new lifecycle tables. Subsequent full runs passed. Adding OpenAPI examples
changed generated comments; regeneration restored the client drift check. No assertions were
removed or weakened. No known introduced failure remains in the executed checks.

Still unfinished/blocked: authorized railway authentication/ticketing/PNR and gateway
providers; official quota/child/concession/refund rules; partial passenger cancellations,
charting/upgrades/auto-refunds; live running status; notification transport; audited REVIEW
resolution; external outbox publication/replay; privacy retention implementation; production
load/security assessment, restore/DR evidence and AWS resources. Synthetic eligibility and
refund policies do not assert official railway requirements or regulatory compliance.
