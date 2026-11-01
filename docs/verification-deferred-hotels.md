# Hotel pay-at-property and date-amendment evidence

2026-10-31; LOCAL_OIDC + deterministic MOCK only. Requirements: BE-HTL-012 / BE-HTL-014 P0, FE-HT-16 / FE-TRIP-07 P1. Source priorities remain unchanged. AC-060–062 are the scoped acceptance gates; broader requirement rows stay In progress.

## Delivered scope

Owned FLEX quotations explicitly support PAY_NOW or PAY_AT_PROPERTY. Deferred bookings validate and reserve without creating a payment account, authorization, capture or refund. Confirmed supplier state and DEFERRED payment remain separate. Cancellation before local check-in releases the property obligation with zero refund and zero platform retention.

A customer can accept an immutable replacement quote and request a non-overlapping date change for the same deferred stay/property/room plans/occupancy. Guest ages are rechecked. A stable, single-use quote and versioned command feed the leased worker; provider stock replacement and receipt commit atomically. Rejection retains the previous stay. Unknown responses reconcile by operation ID. Original quotes, closed amendment history, financial entries and voucher bytes remain immutable. Current vouchers use the effective quote revision; stale/pending access is blocked.

UI → generated v2 API → OIDC/CSRF/ownership → HotelStays/StayService → domain → StayStore/StayEffects → PostgreSQL and MOCK HotelReservations is exercised. V1 guarded mutations fail before side effects; previous pay-now schemas remain compatible. API 0.15.0, migration V18, [ADR 0016](adr/0016-deferred-hotel-amendments.md), [runbook](runbooks/deferred-hotel-amendments.md).

## Executed checks

| Command/check | Result |
| --- | --- |
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | 176 tests passed, 0 failures/errors/skips. Full backend regression before the final check-in guard and DTO metadata refinement. |
| `sh scripts/maven.sh -f apps/api/pom.xml clean test -Dtest=StayIntegrationTest,ArchitectureTest,MigrationUpgradeTest` | Final source: 35 passed, 0 failures/errors/skips (19 hotel integration, 15 architecture, 1 migration). Adds expired amendment and receipt-backed REVIEW recovery. |
| Real child-JVM AMEND crash within StayIntegrationTest | Worker halts after committed supplier replacement; restart recovers one receipt, unchanged original quote, revised current terms, no payment effects. |
| `npm run typecheck` | Passed. |
| `npm test` | 6 passed. |
| `npm run contract:generate`; `npm run contract:check` | Generated TypeScript client matches OpenAPI. |
| `npm run contract:lint` | Passed with 4 existing documentation warnings: license identifier, local server URL, login/logout redirect-only responses. |
| `npm run contract:test` | 9 passed. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-openapi-before-hotels.json` | Additive compatibility passed against pre-increment 0.14.0. |
| `npm run requirements:generate`; `npm run requirements:check` | 480 original source requirements/scenarios preserved. |
| `docker compose build backend frontend`; final `docker compose build frontend` | Production application images built, including Maven package and Next production build. |
| `docker compose up -d --wait --wait-timeout 180` | API, web, PostgreSQL and local identity healthy; loopback ports retained. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/stays.spec.ts --grep 'AC-060'` | 2 passed: desktop + mobile full deferred/amendment/voucher/cancellation journey. Includes real OIDC/API, schema checks, keyboard submission, refresh, lost acknowledgement/provider response, current voucher, zero-refund final state and axe scans (0 violations on tested screens). |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/stays.spec.ts tests/hotels.spec.ts` | Final clean run: **18 passed** (desktop/mobile discovery, pay-now regression and deferred amendment lifecycle), 0 failures, in 1.7 minutes. |
| `npm audit --omit=dev` | 0 production dependency vulnerabilities reported; this is not a complete security assessment. |

Development failures corrected: SQL JSON operator precedence in the amendment history guard; revision-bound voucher fingerprint exceeding the database field length; a Playwright exact-label selector that included nested option text (changed to the actual named combobox role). Initial browser run: 16 existing journeys passed and 2 new-selector failures; these failures are not counted as successful evidence.

## Retained local data and observed effects

Before V18, a restricted local `pg_dump --no-owner --no-acl` backup was taken. No restore drill was performed. Before/after aggregate payload checksums matched exactly for 99 hotel bookings, 99 quotes and 15 vouchers. Existing capture journal rows (474) and refund journal rows (316) were unchanged immediately after migration. No volume reset, truncation, cancellation of unrelated bookings or mutation of original financial records was used.

After the first successful desktop/mobile deferred tests: 2 deferred bookings, 2 amendment receipts, 4 immutable voucher revisions, **0 deferred payment operations**, **0 deferred capture entries**, and **0 overallocated property/night/room groups**. 1,058 persisted hotel outbox envelopes passed `scripts/validate-stay-events.mjs`; schema rejects guest fields. These are a local checkpoint, not production throughput or delivery evidence.

Final image identifiers: API `sha256:5c56e15b3a5d2a08dc1b4050a46d9fcd2611dd33a110fa0f7ac004fb0fe14309`; web `sha256:88bd28eab2d82ee58b6ab673f9c349a84ec70bd665f4e8582969e478810b5d63` (arm64). Both use the preserved local PostgreSQL volume with migration 18. The editor reported no errors in changed hotel/stay backend or frontend folders. Mobile screenshot reviewed: responsive single-column status/terms/history/actions; no horizontal overflow in the browser assertion.

## Explicit limits

Prepaid amendments with delta charges/refunds/compensation, overlapping dates, guest/room/property changes, partial hotel cancellation, deposits/no-show penalties and property collection/settlement are unfinished. Ten amendments maximum is a deliberate MOCK capability. Real booking/ticketing/PNR, payments, valid check-in documents and operator IdP/MFA provisioning still require authorized integrations. New code does not enable SANDBOX/LIVE, create AWS infrastructure or grant privileged users.

This increment does not claim full WCAG conformance, production security/compliance, SLOs, performance/load targets, disaster recovery or external event delivery. Existing operator browser gates were not rerun here; their prior evidence remains historical, while backend authorization/recovery regressions passed. See the runbook for forward migration, review recovery and rollback incompatibility with older application images after deferred/amended records exist.

Key implementation files: [Stay domain](../apps/api/src/main/java/com/travbook/stays/domain/model/Stay.java), [durable stay store](../apps/api/src/main/java/com/travbook/stays/adapter/out/persistence/JdbcStayStore.java), [provider amendment adapter](../apps/api/src/main/java/com/travbook/hotels/adapter/out/persistence/DurableMockHotelReservations.java), [v2 controller](../apps/api/src/main/java/com/travbook/stays/adapter/in/web/StayV2Controller.java), [customer UI](../apps/web/src/features/stays/StayViews.tsx), [OpenAPI](../contracts/openapi.json), [V18](../apps/api/src/main/resources/db/migration/V18__deferred_stay_amendments.sql).
