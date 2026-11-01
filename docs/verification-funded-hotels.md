# Hotel overlapping, guest/room and prepaid change evidence

2026-10-31. LOCAL_OIDC + deterministic MOCK. BE-HTL-014 P0 and FE-TRIP-07 P1; AC-063–066. Source rows remain In progress because authorized suppliers and broader modification policies are not complete.

## Delivered and exercised

The booking page requests an owned, version-bound quote for changed dates, plans, room count, occupancy and guests at the same property. Inventory excludes only that owned reservation, permitting overlapping dates without false sold-out results. Bound quotes cannot create independent bookings. Guest ages/room occupancy are checked again at replacement check-in. Original inputs and closed history are immutable; current documents use the effective quote and guests.

For fully-refundable prepaid stays, the UI discloses the full replacement charge, full previous-payment refund after success and signed net difference. A separate payment account is captured before atomic supplier replacement. Success refunds the previous current account; rejection refunds the replacement account and retains the original reservation/guests. Pending/unknown adjustment blocks cancellation and documents. Subsequent cancellation refunds only the current account under the effective policy. Deferred changes have no financial effects. No actual payments or check-in documents are produced.

Browser → generated v3 client → OIDC/CSRF/ownership → HotelStays/StayService → Stay/StayChange → ports → PostgreSQL/MOCK provider/payment adapters is verified. V1/V2 mutation/detail/document guards protect changed resources. Initial booking/quotation APIs remain compatible. [ADR 0017](adr/0017-funded-hotel-changes.md), [runbook](runbooks/funded-hotel-changes.md), [OpenAPI 0.16.0](../contracts/openapi.json), [V19](../apps/api/src/main/resources/db/migration/V19__funded_hotel_changes.sql).

## Executed checks

| Exact command/check | Result |
| --- | --- |
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | **186 passed**, zero failures/errors/skips. Full backend regression, including original funding proof and real child-JVM capture/reservation/refund crash recovery. |
| `sh scripts/maven.sh -f apps/api/pom.xml clean test -Dtest=StayIntegrationTest,ArchitectureTest,MigrationUpgradeTest` | Final source after nullable deferred funding metadata and editor diagnostic cleanup: **44 passed** (28 hotel integration, 15 architecture, 1 migration), zero failures/errors/skips. |
| `npm run typecheck` | Passed. |
| `npm test` | 6 passed. |
| `npm run contract:generate`; `npm run contract:check` | Generated client matches contract. |
| `npm run contract:lint` | Passed with the same four documentation warnings (license, local server URL, redirect-only login/logout). |
| `npm run contract:test` | 9 passed. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-before-change-v3.json` | Additive compatibility passed against pre-increment 0.15.0. |
| `npm run requirements:generate`; `npm run requirements:check` | 480 source requirements/scenarios preserved. |
| `docker compose build backend frontend`; final `docker compose build frontend` | API Maven package and Next production image builds passed. |
| `docker compose up -d --wait --wait-timeout 180` | Backend/frontend/PostgreSQL/local identity healthy, loopback exposure retained. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/stays.spec.ts --grep 'AC-063|AC-065'` | **4 passed**, desktop/mobile successful replacement and rejected replacement. Real local OIDC/API/persistence; room plan/count and guest changes; charge/refund disclosure; lost acknowledgement; delayed/lost provider responses; current voucher content; keyboard submit, no horizontal overflow, axe scans with zero violations in tested review screens. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/stays.spec.ts tests/hotels.spec.ts` | Final clean run: **22 passed**, desktop/mobile discovery, pay-now/deferred lifecycle, earlier amendments and new prepaid/guest/room changes; zero failures, 3.0 minutes. |

Backend tests assert authoritative state, allocation quantities and payment/refund counts, not just HTTP success. They cover higher/lower/same-price and guest-only changes, overlapping dates, room removal/addition, concurrent identical commands, post-capture inventory loss, authorization/capture rejection, compensated supplier rejection, pending refund gating, current-account cancellation, non-refundable-policy refusal, bound quote reuse, ownership and inconsistent original funding. Real child processes halt after CHANGE_CAPTURE, CHANGE_RESERVE or CHANGE_REFUND commits; restart recovers once by receipt.

Development failures corrected: nested Java enum type-use annotation placement; a crash test's frozen clock lagged the separate process's refund timestamp and exhausted its artificial immediate retries. Advancing the fixture to the actual later clock resolved it; production refund settlement continues to use its normal clock and bounded retry policy. Earlier failed checks are not passing evidence.

## Data preservation and local evidence

Before V19, a restricted local `pg_dump --no-owner --no-acl` backup was taken. No restore drill is claimed. Immediate pre/post migration checksums matched exactly for **131 hotel bookings**, **135 quotations** and **27 vouchers**. Capture journal entries (**506**) and refund journal entries (**340**) were unchanged. No existing volume reset, truncation or payload/financial-history rewrite was performed.

Final local checkpoint: 8 replacement changes; 0 over-refunded accounts; 0 unbalanced capture operations; 0 unbalanced refund operations; 0 overallocated property/night/room groups; 0 cross-reservation guest-operation bindings. All 1,530 persisted hotel lifecycle envelopes passed `scripts/validate-stay-events.mjs` with no guest fields permitted. These are local correctness checks, not load or external delivery evidence.

Final arm64 image identifiers: API `sha256:47d4695422f6c2fc9defb8db551532f2392a54da8ed7aa0268116d424bd2fbf1`; web `sha256:36a77b10c6200627a38118f377591fd7b9baba156449877b0782f9e74ae3583b`. V19 is applied and local services remain healthy. Mobile screenshot was visually reviewed: single-column responsive status/history/actions and labeled room-edit controls; existing independent styling retained. The editor reports no errors in the changed hotel/stay backend and frontend folders.

## Remaining scope

Only same-property, INR, same-collection-method changes are enabled. Original prepaid policies must permit a full refund; non-refundable or expired-policy modifications are refused. Funding uses full replacement capture plus separate refund, not net-only debit. Real ticketing, PNR, payment/refund settlement, supplier amendments, valid check-in documents and operator IdP/MFA provisioning still require authorized integrations. No AWS resources, production SLOs, regulatory compliance, full WCAG conformance or restore/DR readiness are claimed. Event publication beyond the existing local outbox remains unfinished. Existing operator browser gates were not rerun; backend security/recovery regressions passed.

Key files: [StayChange](../apps/api/src/main/java/com/travbook/stays/domain/model/StayChange.java), [Stay](../apps/api/src/main/java/com/travbook/stays/domain/model/Stay.java), [StayEffectAdapter](../apps/api/src/main/java/com/travbook/stays/adapter/out/commerce/StayEffectAdapter.java), [JdbcStayStore](../apps/api/src/main/java/com/travbook/stays/adapter/out/persistence/JdbcStayStore.java), [provider adapter](../apps/api/src/main/java/com/travbook/hotels/adapter/out/persistence/DurableMockHotelReservations.java), [StayV3Controller](../apps/api/src/main/java/com/travbook/stays/adapter/in/web/StayV3Controller.java), [ChangePanel](../apps/web/src/features/stays/ChangePanel.tsx).
