# Bus, holiday and cab discovery verification

2026-10-31. Deterministic MOCK suppliers and LOCAL_OIDC for private holiday data. AC-067–070. Bus/holiday P0 and cab P1/P2 source priorities are retained in the 480-row register. This delivers initial discovery, selection, quotation and local-enquiry slices, not complete booking fulfilment for these verticals.

## Implemented paths

- Bus route/date/declared-age search → persisted filtered/sorted results → simulated seater/sleeper deck layout → ordered seats and boarding/drop points → authoritative fare recheck, changed-price feedback, age restrictions and lost-seat map recovery. Selection/boarding state survives immediate refresh. Checked prices update both the total and selected seat tiles. No hold or ticket is created.
- Holiday domestic/international catalogue → destination/theme/price/duration filters → day-wise itinerary and inclusions → OIDC-owned immutable party/customization quotation → consented, idempotent local enquiry → saved status and lost-acknowledgement recovery. Comfort/activity choices change final itinerary/inclusions and price. Direct-booking packages are quoted but cannot silently use the enquiry workflow. No external message is sent.
- Cab airport/local/outstation one-way/round-trip search → validated schedule/capacity/luggage → vehicle comparison → fare breakdown/extras and repricing. Distances are explicit fixture estimates; no real navigation, driver or reservation is supplied.

Each uses generated OpenAPI types, an inbound application port, domain validation, outbound ports and concrete MOCK/persistence adapters. New contexts cannot access existing fulfilment/payment internals under the added architecture rule. [ADR 0018](adr/0018-bus-holiday-cab-discovery.md), [runbook](runbooks/bus-holiday-cab-discovery.md), [OpenAPI 0.17.0](../contracts/openapi.json), [V20](../apps/api/src/main/resources/db/migration/V20__bus_holiday_cab_discovery.sql).

## Executed checks

| Exact command/check | Result |
| --- | --- |
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | **197 passed**, zero failures/errors/skips. Full backend regression after the new contexts and integration tests. |
| `sh scripts/maven.sh -f apps/api/pom.xml clean test -Dtest=MultimodalDiscoveryIntegrationTest,ArchitectureTest,MigrationUpgradeTest` | Final backend source: **27 passed** (10 new integration tests, 16 architecture, 1 migration), zero failures/errors/skips. Includes explicit package mode metadata and bounded-provider guard. |
| `npm run typecheck` | Passed. |
| `npm test` | 6 passed. |
| `npm run contract:generate`; `npm run contract:check` | Generated client matches OpenAPI. |
| `npm run contract:lint` | Passed with four existing documentation warnings: license metadata, local server URL and redirect-only login/logout responses. |
| `npm run contract:test` | 9 passed. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-before-discovery-three.json` | Additive compatibility passed against pre-increment 0.16.0. |
| `npm run requirements:generate`; `npm run requirements:check` | 480 original source requirements/scenarios preserved. |
| `docker compose build backend frontend`; final frontend builds | Java package and Next production builds passed. Original assets and application sources remain in the context; log/dump/backup extensions are excluded in `.dockerignore`. |
| `docker compose up -d --wait --wait-timeout 180` | Local API/web/PostgreSQL/identity healthy; V20 applied without resetting data. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/multimodal.spec.ts tests/discovery.spec.ts tests/hotels.spec.ts tests/trains.spec.ts` | **38 passed**, zero failures, 39.2 seconds. Includes 10 new desktop/mobile journeys and 28 flight/hotel/train discovery regressions; followed by the final feature rerun below. |

New integration tests cover exact price arithmetic, customized quoted itinerary, seat age/restriction/point validation, changed/lost seats, cab capacity and four journey modes, invalid round-trip duration, scheduling-cutoff expiry, partial/all-provider outage versus empty inventory, bounded worker/queue/deadline behavior, immutable owned quotes, cross-customer denial, concurrent equivalent enquiry retries, changed-key-payload conflict, expiry, provider workflow eligibility and rollback when outbox insertion fails. No payment accounts are created by these paths.

Browser tests use actual local APIs/database/OIDC. Lost enquiry acknowledgement is injected only after `route.fetch()` commits a real server request; no fabricated business response is supplied. They assert generated schemas, current prices, booking-state boundaries, CSRF403, foreign-owner404, keyboard seat selection, refresh recovery, private enquiry status, zero axe violations in tested screens and no overflow at desktop/mobile plus768px/1024px widths. Mobile screenshots are visually reviewed.

Development defects corrected: a test-only clock bean name collided with the existing application bean; one JSX delimiter; test alert selectors initially also matched Next's route announcer; an asynchronous bus URL update could lose the boarding point on immediate reload. The latter now uses Next-supported native history replacement. Visual review also corrected stale selected-seat tile prices and the styling of available seats disabled only by a full party selection. Invalid blank age-list entries are rejected before they can become zero-age values. No business assertions or security rules were weakened.

## Preserved data and local evidence

A restricted `pg_dump --no-owner --no-acl` backup was taken before migration; no restore drill is claimed. Pre/post V20 hotel and rail booking payload checksums matched. Existing flight booking count263, capture-entry count554, refund-entry count384 and hotel voucher count37 were unchanged immediately after migration. This increment adds new tables; it does not rewrite original booking/financial records.

A post-browser checkpoint observed18 bus snapshots,19 cab snapshots,11 holiday quotes,7 local enquiries and18 holiday events. Capture/refund entries were still554/384, and cross-owner enquiry bindings were0. All18 event envelopes passed `scripts/validate-holiday-events.mjs`, whose schema forbids contact data. Final updated counts/images are recorded below. These are local correctness observations, not production performance or external delivery evidence.

## Remaining requirements and limits

Bus durable holds/expiry, passenger/contact checkout, reservations/payments, supplier-confirmed tickets, rescheduling/cancellation/refunds and tracking remain unfinished. Holidays still need actual direct/enquiry fulfilment, payments/milestones, multi-component orchestration, confirmed vouchers, cancellation/refund rules, transfer customization and shareable drafts. Cab reservations/payments/refunds, real routing and driver assignment/tracking remain unfinished at their source priorities. Source rows remain In progress or Not started; MOCK verification is not authorized supplier completion.

Real ticketing/PNR/payment/check-in documents and operator IdP/MFA require authorized integrations. New adapters fail closed for SANDBOX/LIVE. No AWS deployment, load/SLO achievement, full WCAG conformance, penetration assessment, automated retention or external outbox publication is claimed. Existing booking/operations browser suites were not rerun in this increment; their backend regressions passed in the full suite.

Key files: [buses](../apps/api/src/main/java/com/travbook/buses), [holidays](../apps/api/src/main/java/com/travbook/holidays), [cabs](../apps/api/src/main/java/com/travbook/cabs), [shared bounded adapter](../apps/api/src/main/java/com/travbook/shared/adapter/out/supplier/BoundedDiscovery.java), [bus UI](../apps/web/src/features/buses/BusViews.tsx), [holiday UI](../apps/web/src/features/holidays/HolidayViews.tsx), [cab UI](../apps/web/src/features/cabs/CabViews.tsx), [Docker exclusions](../.dockerignore).

Final local checkpoint: 24 bus snapshots,25 cab snapshots,15 holiday quotes,9 local enquiries and24 holiday events. All24 envelopes passed the strict holiday event schema; cross-owner enquiry bindings remain0. Capture/refund entry counts remain554/384 after all new journeys. Final arm64 images: API `sha256:5cc6ed00a16ea8a266f653665ff9440279abad352c7133cdaa52e3ce7f481bb3`; web `sha256:cf81918d6f4379e0d6476ab34f816cf15fdbabc86c56578d8d98c4ef7363cf8b`. Local services remain healthy. Final mobile seat screenshot confirms selected tile prices match the checked total and available-but-selection-limited seats retain their available appearance. All public holiday package views now carry explicit MOCK metadata.

Final feature rerun: `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/multimodal.spec.ts` — **10 passed**, zero failures, 12.7 seconds, after hiding prior bus/cab results when a new search fails. The added cab test verifies that outage errors do not leave stale fare cards visible. Final event validation: **30 envelopes**, contact fields forbidden. Final local counts:30 bus snapshots,31 cab snapshots,19 holiday quotes and11 local enquiries; financial-entry counts remain554/384.
