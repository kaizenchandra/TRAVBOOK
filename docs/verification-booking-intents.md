# Execution evidence — durable booking intent and recovery

Executed locally on 2026-10-31, Asia/Kolkata. Identity mode: LOCAL_OIDC through Keycloak 26.8.1. Travel provider mode: MOCK only. No real reservation, charge, ticket, refund or AWS operation was performed.

## Delivered scope

An authenticated customer can continue from an owned, accepted, unexpired quote, supply travellers matching the original search categories, durably submit one intent, recover a lost acknowledgement, inspect validation progress and retrieve owned intent summaries through My Trips. The worker revalidates fresh price, material terms and inventory observations outside a transaction. Validation success reaches AWAITING_PAYMENT; supplier reservation is NOT_REQUESTED and payment is NOT_STARTED.

New implementation paths are [BookingService](../apps/api/src/main/java/com/travbook/booking/application/usecase/BookingService.java), [BookingWorker](../apps/api/src/main/java/com/travbook/booking/application/usecase/BookingWorker.java), [JdbcBookingStore](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/JdbcBookingStore.java), [booking UI](../apps/web/src/features/bookings/BookingViews.tsx), [V4](../apps/api/src/main/resources/db/migration/V4__durable_booking_intents.sql), [V5](../apps/api/src/main/resources/db/migration/V5__booking_workflow_definition_version.sql) and [OpenAPI](../contracts/openapi.json). The [ADR](adr/0003-durable-booking-intent.md), [runbook](runbooks/booking-intents.md) and [RTM](requirements/rtm.md) describe the boundaries and remaining work.

## Commands and actual outcomes

| Exact command / action | Outcome | Scope |
|---|---|---|
| `docker compose up -d --wait postgres` | Passed | Preserved local database volume restarted |
| `docker compose up -d --force-recreate --wait identity` | Passed | Only disposable local identity service recreated to import booking scopes; deterministic test subjects retained |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=BookingIntegrationTest test -q` | Passed: 8 tests | Real HTTP/PostgreSQL ownership, idempotency, expiry, leased validation and child-JVM recovery |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=RequestGuardTest test -q` | Passed: 1 test | API exhaustion cannot consume login budget; both budgets still reject excess and refill within bounds |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -q` | Final pass: **46 tests**, 0 failures/errors/skips; executable package built | 6 discovery API, 7 discovery/domain, 8 quote/security/session, 8 architecture, 4 mode guards, 3 booking domain, 8 booking integration, 1 rate guard, 1 migration upgrade |
| `npm run contract:generate` | Passed | Generated client updated from OpenAPI 0.3.0 |
| `npm run contract:check` | Passed | No generated-client drift |
| `node scripts/contract-compatibility.mjs .tools/pre-booking.openapi.json` | Passed after compatible optional-parameter handling was added | Existing discovery/quote contracts preserved; ignored local baseline captured before this slice |
| `npm run contract:test` | Passed: **5 tests** | Removed/narrowed/required fields or parameters and relaxed existing security are still rejected; optional parameter additions are allowed |
| `npm run contract:lint` | Passed with **4 warnings** | Private-license/local URL metadata and intentional redirect-only auth operations; no schema errors |
| `npm run requirements:generate` / `npm run requirements:check` | Passed | 480 source requirements/scenarios retained; implemented mappings updated without changing priorities |
| `npm run typecheck` | Passed | Frontend and tests |
| `npm test` | Passed: **6 unit tests** | Existing safe error/formatting checks |
| `npm run build` | Passed | Next.js production build includes booking form/status and My Trips routes |
| `npm run test:e2e --workspace apps/web -- bookings.spec.ts` | Passed: **6 tests** | New desktop/mobile booking journeys, HTTP schemas, lost-ack recovery, CSRF/ownership and cached-private-data suppression |
| `npm run test:e2e` | Final pass: **28 tests** | Full discovery + OIDC/quotes + booking regression, desktop/mobile, real API/database/IdP |
| `npm audit` | Passed: **0 vulnerabilities reported** | npm advisory scope only; not a Java/container security assessment |
| VS Code Problems | No errors found | Final code diagnostics |
| Mobile booking-status screenshot / axe / overflow assertions | Passed for inspected views | Separate reservation/payment status, readable totals and explicit MOCK/no-effect notices; no full WCAG certification claim |

Persisted booking events were validated using:

```sh
set -o pipefail
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from booking_outbox' | node scripts/validate-booking-events.mjs
```

**54 persisted events matched booking schema v1.** Events contain no names or DOB. They remain unpublished; schema validation is not evidence of message delivery. Generated Surefire and Playwright reports are under their usual ignored output directories and can be reproduced from these commands.

## Acceptance paths and assertions

| Scenario | Verified path / result |
|---|---|
| AC-013 — eligible intent | NewBooking → typed client → BookingController → BookingIntents/BookingService → published customer/quote/search ports → immutable booking input. Wrong owner, acceptance, version, expiry and age mix rejected. Names normalize deterministically; birthday category changes on return are rejected |
| AC-014 — durable validation | PostgreSQL commits intent, workflow, command, reservation state and event together. Provider read begins only after persisted attempt marker and occurs outside a DB transaction. Matching fresh observation moves booking to AWAITING_PAYMENT and validation to SUCCEEDED, while reservation/payment remain unstarted |
| AC-015 — no duplicate intent | Six concurrent same-key requests returned identical 202 acknowledgements. Different keys for the same normalized quote/travellers recovered that same intent. Changed payloads conflicted. One intent/workflow/reservation/event was persisted at creation; replay after expiry remained stable. Browser refresh after simulated lost acknowledgement recovered the saved intent without resubmitting names/DOB |
| AC-016 — privacy and access | Other customer status/create denied; list/cursor ownership enforced; cookie mutations require CSRF. Summaries/events omit traveller names/DOB. A browser test replaces the session with another test user's session while retaining the query cache; a denied refresh removes the prior private details from the DOM |
| AC-017 — durable recovery | Real child JVM halted after successful MOCK provider read, before local completion. Database retained VALIDATING/leased state and read-start marker. Test expired the lease to simulate elapsed downtime, then a fresh child JVM resumed. Exactly two read observations, two persisted read attempts, one terminal transition and three lifecycle events (created/started/awaiting-payment); reservation/payment remained unstarted |
| AC-018 — safe failures / upgrades | Old fencing token cannot commit after a newer claim. Four possible read attempts exhaust to MANUAL_REVIEW. Quote expiry during provider read blocks success; a persistence-boundary expiry rolls back inserted intent/command. Price, policy and unavailable outcomes stay distinct. V1→V2→V3→V4→V5 upgrade tests retain earlier data; input/command/event guards and architecture isolation pass |

The crash test does not sleep to represent an outage. It performs a genuine abrupt JVM exit, then expires the lease in its isolated Testcontainer to represent elapsed downtime. This is worker recovery evidence, not a production infrastructure/DR or effective supplier-operation recovery drill. Read calls can safely repeat; reservation/payment retries require their own provider idempotency and reconciliation controls.

## Defects discovered and resolved

- The migration regression expected one remaining migration after V2; the new steps were made explicit and existing-data assertions retained. No assertions were removed to hide a failure.
- Test application contexts are now closed after their class so scheduled work does not keep querying a stopped Testcontainer. The worker is disabled in tests that drive it manually.
- The compatibility guard initially treated adding optional login parameters as breaking. It now compares parameters by identity, preserves existing constraints, and rejects removed/narrowed/newly required parameters; regression cases cover both compatible and incompatible changes.
- The first expanded full browser run passed 27 cases but hit the old shared process budget during mobile reauthentication. API and identity budgets are now independent and both remain bounded. A new unit test proves exhaustion/isolation/refill; the full 28-case rerun passed.
- Cached private booking/quote details are hidden after a denied refresh, including a tested account-switch scenario.
- Editor-reported method-reference nullability and an unused test import were corrected; final diagnostics are clear.

## Limits and next work

- No supplier hold/reservation/ticket or payment/refund operation exists. Validation-terminal means this step ended; it is not booking confirmation. An already validated intent may have an expired quote later; future commitment must recheck it.
- No outbox publisher/consumer replay, effective-call unknown-outcome reconciliation, compensation, ledger, tickets, cancellation/refunds or operations repair API was implemented.
- Real supplier rules, live credentials, production identity/MFA, encryption/runtime DB least privilege, retention/deletion, Java/container security scans, distributed rate limits, load/soak, backup/restore, DR and AWS deployment remain gates. No performance or availability target is claimed achieved.
- Existing source priorities are preserved; this increment does not complete the full Phase 4/5 gates or the full platform.
- The workspace still has no Git metadata, so no commit, PR or hosted CI execution is claimed. Original authoritative input documents were preserved.

Next: explicit MOCK payment/reservation sequencing with durable provider operation references, separate financial/reservation state, reconciliation and compensation, followed by complete confirmation/document/post-booking recovery.

Handoff: Playwright stopped its API/web processes after verification. `docker compose stop identity postgres` stopped the local identity/database services; the PostgreSQL volume and recorded data are preserved. Restart with the documented local setup.
