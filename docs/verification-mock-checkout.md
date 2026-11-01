# MOCK checkout verification — 2026-10-31

Implemented one integrated MOCK flight strategy: **revalidate → authorize → reserve → capture**, with operation-status reconciliation, cancel/void compensation and independently displayed checkout/payment/reservation states. This is a working local simulation, not a real supplier/gateway integration, ticket issuance, customer cancellation/refund feature, complete platform or production release.

## Delivered slice and traceability

AC-023–027 map to BE-BKG-005/007/013/014/015, BE-PAY-001/004/005/007/008/009/010/011/012/014/015/016 and FE-PAY-11/13, plus prior ownership/booking/status requirements. The [RTM](requirements/rtm.md) retains all 480 source requirements and priorities; broader requirements remain In progress. Missing hosted gateways, signed webhooks, refunds, other supplier strategies and operations are not reclassified as optional.

Frontend uses generated v2 contracts for owned booking detail/My Trips and explicitly consented checkout. `CheckoutPanel → useStartCheckout → CheckoutController → BookingCheckouts/CheckoutService → CheckoutStore` durably accepts one workflow. `RunCheckout → CheckoutWorker → CheckoutPayments/CheckoutSupplier` performs effects outside orchestration transactions. Booking accesses the payment context through `PaymentOperations`, with reverse coupling prohibited by ArchUnit. Payments owns immutable operation receipts and balanced capture journal entries. Provider fixtures persist independently so a local result-write failure cannot erase the effect or cause blind repetition.

Key implementation: [checkout domain](../apps/api/src/main/java/com/travbook/booking/domain/model/Checkout.java), [worker](../apps/api/src/main/java/com/travbook/booking/application/usecase/CheckoutWorker.java), [checkout store](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/JdbcCheckoutStore.java), [payment adapter/store](../apps/api/src/main/java/com/travbook/payments/adapter/out/persistence/JdbcMockPayments.java), [durable supplier](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/DurableMockSupplier.java), [UI](../apps/web/src/features/bookings/CheckoutPanel.tsx), [V7](../apps/api/src/main/resources/db/migration/V7__mock_checkout.sql), [OpenAPI](../contracts/openapi.json), [ADR](adr/0005-mock-checkout.md) and [operational runbook](runbooks/mock-checkout.md).

## Executed checks and results

| Exact command/check | Result |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=CheckoutIntegrationTest,MigrationUpgradeTest,ArchitectureTest test -q` | Initial 18 tests: one incorrect test expectation failed (anonymous POST lacked CSRF and correctly returned 403 before auth, rather than expected 401). Tests were corrected to separately assert missing-CSRF 403, anonymous GET 401 and invalid-bearer POST 401. Security behavior was not relaxed. |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | First full run: 65 tests passed. Final run after unknown-before-dispatch, timeout and rollback hardening: **66 tests passed**, zero failures/errors/skips, application packaged. |
| `npm run test:e2e` | **36 tests passed** through Next.js, Java, local PostgreSQL and real local Keycloak, desktop/mobile. |
| `npm run test:e2e --workspace apps/web -- checkout.spec.ts` | Final targeted recheck: **6 passed**, 55 seconds, against final checkout worker changes. |
| `npm run typecheck` | Passed |
| `npm test` | 6 unit tests passed |
| `npm run build` | Production Next.js build passed |
| `npm run contract:generate` / `npm run contract:check` | Generated client reproducible; no drift |
| `npm run contract:test` | 5 compatibility tests passed |
| `node scripts/contract-compatibility.mjs .tools/pre-checkout.openapi.json` | Additive schema/endpoint gate passed; intentional legacy behavioral guard separately documented in ADR/runbook |
| `npm run contract:lint` | Valid; four pre-existing warnings (private-license metadata, local server URL, login/logout redirect-only responses) |
| `npm run requirements:generate` / `npm run requirements:check` | 480 source rows retained and mappings synchronized |
| `docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from booking_checkout_outbox' \| node scripts/validate-checkout-events.mjs` | **122 persisted checkout events** matched schema v1 |
| Editor diagnostics for modified booking/payment/test/UI code | No errors after removing an unused test import |
| `npm audit --json` | **Failed: 3 moderate**, zero high/critical findings. Existing OpenAPI generator/minimatch development dependency chain remains unchanged; CI audit gate remains blocked. See [prior audit analysis](verification-booking-events.md). |

The container engine was initially stopped. Two Compose attempts reported a missing OrbStack socket; starting the installed OrbStack app restored the engine. Database tests then ran with real isolated PostgreSQL Testcontainers; none were replaced by an in-memory database or skipped. The supplied readonly attachment was zero bytes and was not edited. No Git metadata exists; no commit, PR, hosted CI or AWS deployment was performed.

## Correctness and recovery evidence

[CheckoutIntegrationTest](../apps/api/src/test/java/com/travbook/CheckoutIntegrationTest.java) has nine scenarios covering:

- Real HTTP ownership/consent/body validation/scopes/CSRF boundary; concurrent same-key and different-key start requests produce one checkout and stable acknowledgement. Changed scenarios conflict. Equivalent retries survive expiry. Legacy detail fails closed after checkout begins.
- Authorization alone leaves RUNNING with no supplier reservation; supplier confirmation alone leaves RUNNING with no capture journal. Only verified MOCK capture plus supplier confirmation transitions to CONFIRMED.
- Immutable capture journal has one debit and one credit per captured payment, equal to the server quote. Database guards reject financial history/input changes. An identical payment operation returns its receipt; changed amounts under the same operation ID are rejected.
- Authorization decline, supplier rejection and capture rejection preserve distinct outcomes. Reservation rejection voids authorization; capture rejection cancels before voiding. Neither path creates capture journal entries or claims a refund.
- Reservation/capture response-loss fixtures commit their provider receipt then throw. The worker reports UNKNOWN and queries the receipt before advancing. A captured result arriving after quote expiry is recovered without a second capture or wrongful compensation.
- Expiry before authorization prevents effects; expiry after authorization voids; expiry after reservation but before capture cancels then voids. Fresh policy changes after authorization also void without reserving.
- Provider lookup errors never become “not found” and never trigger mutation. Five failures lead to REVIEW with UNKNOWN preserved, not fabricated decline or confirmation.
- Stale lease holders cannot commit. Provider calls occur outside orchestration transactions. A deliberately failed local outbox/completion transaction preserves the provider authorization receipt, rolls back the local success transition, and recovers without another authorization.
- An actual child JVM halts with exit 25 after a durable supplier reservation but before local completion. The persisted observation is UNKNOWN. A fresh JVM resumes after test-only simulated lease expiry, retrieves the same receipt and finishes with one supplier effect and one balanced capture. This is local process recovery, not an AWS DR drill.

[Browser scenarios](../apps/web/tests/checkout.spec.ts) cover keyboard consent, missing-CSRF rejection, successful checkout, schema conformance, refresh, My Trips, supplier/capture rejection and compensation, and an intentionally lost browser acknowledgement combined with a real MOCK supplier lost-response fixture. The page recovers the existing workflow without resubmitting it. Existing denied-owner/history-refresh tests still pass. Trace capture is disabled for authenticated/traveller flows. Desktop/mobile overflow and automated axe checks passed; the mobile confirmation screenshot was inspected. This is not complete WCAG certification.

## Preserved local database observations

After the full and focused browser runs:

- 16 MOCK checkouts: 8 CONFIRMED/CAPTURED/CONFIRMED, 4 FAILED/VOIDED/CANCELLED, 4 FAILED/VOIDED/REJECTED.
- 16 payment accounts: 8 CAPTURED and 8 VOIDED.
- 16 immutable journal rows, net debit-minus-credit **0.00**.
- 36 distinct payment operation receipts and 20 distinct supplier operation receipts.
- Zero checkout confirmations missing captured payment or confirmed supplier observation.
- All migrations V1–V7 successful, preserving previous local data.
- 122 privacy-safe checkout lifecycle outbox events. They are **stored, not dispatched**; existing validation-event delivery remains independent.

Teardown: Playwright stopped its API/web processes; no listeners remained on ports 3000/8080. `docker compose stop identity postgres` completed and both containers were confirmed stopped. The PostgreSQL named volume, previous data, new MOCK receipts and migrations were preserved. The existing OrbStack engine was not globally shut down. Requirement/client drift and additive compatibility checks passed again at handoff.

## Remaining gates and next work

Add checkout-event dispatch, protected MOCK documents, customer cancellation/refund with immutable entitlement and captured-payment reconciliation, and audited resolution of REVIEW cases. Other supplier sequences, package partial fulfilment, real hosted/tokenized gateways, verified webhooks, settlement reconciliation, remaining travel verticals, external notification transport and AWS infrastructure remain incomplete. The v2 list has bounded per-item lookups that require query consolidation/load evidence before scale claims. Production runtime DB roles, encrypted managed storage, SLO/load/restore/DR/security gates and the npm audit issue remain unresolved. No real funds, reservations, compliance or production readiness are claimed.
