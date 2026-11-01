# Mock airline, extras, check-in and payment APIs

Current customer UI: `/flight-services` now exposes seat/meal/baggage selection, quotes, reservation/payment/ticketing, check-in, mock document download and aftercare. Its booking history uses GET `/reservations` with owned cursor pages of20. This is still the separate fictional DEL–BOM fixture, not an ancillary adapter for every original flight offer. See [customer verification](../verification-customer-workflows.md) and [remaining work](../requirements/remaining-work.md).

Provider: **travbook-fictional-air**. Payment provider: **travbook-fictional-pay**.
Both are internal fictional test doubles. No real airline, GDS, bank, credential vault entry, ticket or boarding pass is implied.

## Local environment and access

Use the existing [Compose deployment](../../compose.yaml) and [local development guide](../../README.md). Enable `DEMO_APIS_ENABLED=true` and explicitly retain `INTEGRATION_MODE=MOCK`. Base URL:

- Direct backend: `http://127.0.0.1:8080/api/v1/demo/flight-provider`
- Existing frontend proxy: `http://127.0.0.1:3000/api/v1/demo/flight-provider`

`GET /capabilities` describes provider names, capabilities, local environment, authentication and exclusions. Provider credential-store reference is **null**: this mock API requires no provider secret. Existing OIDC configuration (`travbook.oidc-issuer`, audience and configured login/session) authenticates the customer. Do not invent AWS secret ARNs or put access tokens in source control. LIVE/SANDBOX fail closed, even if credentials are present.

GET needs `bookings:read`; POST needs `bookings:write`. Valid scoped bearer authentication or the existing customer session is supported. Cookie POST requests require the `csrfHeaderName`/`csrfToken` returned by `/auth/session`. Every POST also requires an `Idempotency-Key` matching `[A-Za-z0-9._:-]{16,128}`. IDs are owned by the authenticated issuer/subject customer, never caller-supplied ownership fields. Foreign IDs return 404. Responses use `Cache-Control: no-store` and correlation IDs. Rate and 16 KiB JSON request guards remain in effect.

All successful responses have this wrapper:

```json
{"mode":"MOCK","notice":"MOCK ONLY — no real funds or inventory. NOT VALID FOR TRAVEL.","data":{}}
```

The response-specific `data` contract is fully defined in [OpenAPI](../../contracts/openapi.json). Reproducible generated TypeScript types are in [schema.d.ts](../../apps/web/src/lib/api/schema.d.ts). The customer seat-picker consumes this API at `/flight-services`; original supplier offer checkout remains separate.

## Endpoint sequence

Paths below are relative to the base URL. POST responses are 200 unless otherwise shown; quote/reservation creation return 201, including equivalent replay.

| Method/path | Request / behavior |
| --- | --- |
| GET `/capabilities` | Environment, provider/payment names, authentication, supported/unsupported capabilities. |
| GET `/flights?date=YYYY-MM-DD` | One synthetic DEL–BOM departure at 18:00 Asia/Kolkata. Today through 90 days ahead, more than one hour before departure. |
| GET `/flights/{flightId}/seats` | 3 rows × 6 columns, available flags and exact prices. Row 3 exit seats require age 18+. 3F is blocked for crew. This is an observation, not a hold. |
| GET `/flights/{flightId}/ancillaries` | VEG/STANDARD meals, NONE/KG20 extra baggage and fixture-inclusive prices. Meal NONE is allowed at zero charge. |
| POST `/quotes` | Flight ID and 1–6 synthetic passenger selections; server determines all prices; 201 immutable version 1 quote. |
| GET `/quotes/{id}` | Retrieve original quote even after expiry. |
| POST `/reservations` | Accept exact quote version and total with consent; recheck expiry, atomically claim every seat, consume quote once; 201. |
| GET `/reservations/{id}` | Current reservation/payment/ticket/refund and checked-in passenger states. |
| GET `/reservations?after={id}` | Owned history, newest first, at most20items. Omit cursor for the first page. Foreign cursor404. |
| POST `/reservations/{id}/payments` | `{"outcome":"SUCCEED","consent":true}` or `DECLINE`. Charge only the stored quote amount, including extras. At most one successful capture; ten payment attempts maximum. No card/UPI/bank data. |
| POST `/reservations/{id}/tickets` | `{"outcome":"ISSUE","consent":true}` or `REJECT`. Requires captured payment. Rejection produces no ticket and does not erase the capture. |
| POST `/reservations/{id}/check-in` | `{"passengerIds":["P1"],"consent":true}`. Issued/paid ticket, owned slots and fixture check-in window required. |
| GET `/reservations/{id}/documents` | JSON mock ticket per slot, plus JSON mock boarding-pass summary for checked-in slots. All have `validForTravel:false`. Unpaid/rejected/cancelled records cannot obtain documents. |
| POST `/reservations/{id}/cancellations` | `{"consent":true}`. Before cutoff and before any passenger checks in. Full cancellation releases seats, voids tickets and makes captured funds refund PENDING. |
| POST `/reservations/{id}/refunds` | `{"consent":true}`. Completes the pending full simulated refund exactly once in local state. Separate refund reference and journal. |

Example quotation body (replace the date using a returned flight ID):

```json
{
  "flightId":"MOCK-FA-2030-01-03",
  "selections":[
    {"passengerId":"P1","age":30,"seat":"2A","meal":"VEG","baggage":"KG20"},
    {"passengerId":"P2","age":12,"seat":"2B","meal":"NONE","baggage":"NONE"}
  ]
}
```

This fixture totals **INR 10,050.00**: P1 base4,000 + tax200 + seat200 + meal250 + bag1,000; P2 base4,000 + tax200 + seat200. These are synthetic commercial/tax terms, not applicable airline fares or tax advice. Every individual component and the total are returned. Infants under 2, duplicate slots/seats, unsupported items and child-only parties are rejected. Use no real passenger PII in this test API.

Use the returned quote ID and exact server total when reserving:

```json
{
  "quoteId":"<returned quote UUID>",
  "quoteVersion":1,
  "acceptedTotal":{"amount":"10050.00","currency":"INR"},
  "consent":true
}
```

Quotes expire after ten minutes or at departure minus one hour, whichever comes first. Merely accepting a browser price never overrides the stored amount. A sold seat returns409 and requires a new choice/quote. Reservations are final fixture allocations, **not timed holds**; cancel unused reservations before the cutoff to release inventory. Changing seats/meals after reservation, multi-city/return itineraries, infant/assistance policies, rescheduling and partial cancellation are not implemented here.

The immutable quote also records `cancellationPolicy: MOCK-FP-FULL-BEFORE-CHECKIN-V1`. Cancellation enforces these accepted terms; an unknown stored policy fails closed for review instead of assuming a refund entitlement.

## Check-in and failure recovery

Check-in uses this local API, not an airline website or external handoff. It opens exactly 24 hours before departure and closes exactly 1 hour before departure. Request one or multiple synthetic slots. Repeating a checked-in slot under a different key conflicts; replay the original key or GET documents. Cancel after check-in is unsupported. No external boarding document is created.

For every mutation, persist the idempotency key before sending. On timeout or lost response, retry the **same** key/body. The original response is immutable, so it may show an older state after later actions; GET the returned reservation for current status. A changed body, reservation, outcome or selected slots with the same operation key returns409. Keys are scoped per verified customer and operation, with PAYMENT shared by SUCCEED/DECLINE and TICKET shared by ISSUE/REJECT.

For ticket rejection after capture: GET status → POST cancellation → observe refund PENDING → POST refund → observe REFUNDED/COMPLETED. Declined payment has no capture and can be retried with a new key. Successful capture does not automatically trigger reservation confirmation or ticket issuance. The local operations are atomic and synchronous; there is no background pending worker in this provider context and no unbounded polling requirement.

Errors use `application/problem+json`:400 malformed/unknown input fields;401 login required;403 missing scope/CSRF;404 unknown/foreign/disabled;409 expiry/seat/state/idempotency/price conflict;413 oversized body;422 semantic/consent failure;429 rate limit;503 database unavailable. Correlation ID accompanies responses. No raw provider stack traces, personal names or credential values are returned.

## Storage, operation and verification

V26 adds `fp_quote`, `fp_reservation`, `fp_flight`, `fp_seat`, `fp_command`, `fp_money_effect`, `fp_journal` and `fp_event`. Quote/command/money/journal/event histories are immutable. Journal constraints enforce exactly two entries balanced to each capture/refund. Events are atomically persisted but have no downstream notification publisher in this slice. These money records intentionally do not enter existing platform payment reports.

Three-second transactions and flight/reservation locks bound contention. No transaction wraps a remote call because no remote operation exists. On database failure, fix availability and retry the original key; never update states or delete receipts to manufacture success. Retained volumes must not be reset. Disable `DEMO_APIS_ENABLED` to remove these routes while retaining data.

Reproducible tests:

```sh
sh scripts/maven.sh -f apps/api/pom.xml test -Dtest=FlightProviderIntegrationTest,FlightProviderModeTest,ArchitectureTest,MigrationUpgradeTest
node scripts/validate-flight-provider-contracts.mjs apps/api/target/flight-provider-http.jsonl apps/api/target/flight-provider-events.jsonl
```

When the Java editor shares build outputs, use an isolated source/POM copy as recorded in the [verification report](../verification-flight-provider.md). API tests exercise an actual HTTP server, signed OIDC fixture tokens, Testcontainers PostgreSQL, seat races, owner checks, price/expiry gates, rollback, capture/refund journal and a separate JVM killed after commit. They are MOCK evidence, not authorized provider testing. Production integration, signed gateway callbacks, valid documents and the customer ancillary UI remain separate incomplete requirements.
