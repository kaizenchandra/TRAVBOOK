# Journey fulfilment verification — AC-071–074

Delivered and verified **MOCK** lifecycle slices for buses, cabs and preassembled holiday packages. No authorized external operation, regulatory compliance, performance benchmark or production readiness is claimed.

## Delivered behavior and boundaries

- Owned immutable versioned quotations, explicit acceptance of final terms, passenger/contact checkout and recoverable booking intent. Reuse after expiry or with changed idempotency payloads is rejected.
- Bus: PostgreSQL-exclusive seat holds across searches, current seat-map occupancy, ordered age eligibility, hold expiry/release, revalidation before payment, confirmed boarding ticket and full cancellation/refund.
- Holiday: direct packages and enquiry-origin fresh supplier terms require new acceptance. Preassembled single-provider component confirmation, itemized voucher and full component refund. Original local enquiries remain unchanged and do not themselves imply fulfilment.
- Cab: prepaid category reservation, pickup/return/extras disclosure, voucher and full refund. No invented driver/registration/tracking.
- Separate booking/payment/reservation/refund states; explicit MOCK authorize/capture; void/release on failure and compensation refund after capture followed by reservation rejection.
- Durable fenced worker claims, lookup-before-effect, immutable provider receipts bound to quote/passengers, restart/lost-acknowledgement recovery, mismatched receipt → bounded REVIEW without false ticket.
- Owner-protected immutable, SHA-256-verified text documents created only with supplier confirmation; unavailable after cancellation starts. Versioned state-only transactional outbox; downstream publication/notifications remain unfinished.

Implementation: [Journey domain](../apps/api/src/main/java/com/travbook/journeys/domain/model/Journey.java), [use cases](../apps/api/src/main/java/com/travbook/journeys/application/usecase), [ports/adapters](../apps/api/src/main/java/com/travbook/journeys), [typed frontend](../apps/web/src/features/journeys), [OpenAPI 0.18](../contracts/openapi.json), [V21](../apps/api/src/main/resources/db/migration/V21__journey_fulfilment.sql), [ADR](adr/0019-journey-fulfilment.md), [diagrams](journey-lifecycle-architecture.md), [RTM](requirements/rtm.md).

## Checks actually executed

| Exact command | Result / scope |
| --- | --- |
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | **211 passed**, zero failed/errors/skipped. Full backend regression, compilation and executable packaging. |
| `sh scripts/maven.sh -f apps/api/pom.xml clean test -Dtest=JourneyIntegrationTest,MultimodalDiscoveryIntegrationTest,ArchitectureTest,MigrationUpgradeTest,StayReturnPathTest` | **44 passed**, zero failed/errors/skipped after final validation/diagnostic fixes: 13 lifecycle, 10 discovery, 17 architecture, one sequential migration, three login-return tests. |
| `sh scripts/maven.sh -f apps/api/pom.xml clean test -Dtest=JourneyIntegrationTest` | **13 passed** after strengthening the seat test to execute two concurrent workers. Actual child JVM exit 29 after committed capture; recovery creates only one balanced capture pair. |
| `npm run contract:generate` / `npm run contract:check` | Generated client matches OpenAPI; no handwritten client types. |
| `npm run contract:lint` | Passed with four pre-existing warnings (license identifier, localhost server, redirect-only login/logout). |
| `npm run contract:test` | **9 passed** compatibility/generator tests. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-openapi-before-journeys.json` | Existing APIs retained; additive changes only. |
| `npm run requirements:generate` / `npm run requirements:check` | **480** source requirements/scenarios preserved and checked; priorities/original identifiers retained. |
| `npm run typecheck` / `npm test` / `npm run build` | Passed; **6** existing frontend unit tests; Next production build includes four new journey routes. |
| `docker compose config --quiet` / `docker compose build backend frontend` / `docker compose up -d --wait --wait-timeout 180` | Passed; API/web/identity/PostgreSQL healthy. Frontend rebuilt once more for a heading hierarchy adjustment. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/journey-lifecycle.spec.ts` | **10 passed**, desktop/mobile, then repeated successfully against final backend images. UI → OIDC → API → PostgreSQL/provider/payment; contract schemas, refresh recovery, lost booking/effect acknowledgements, confirmation, downloads/hash, ownership/CSRF, My Trips, cancellation and refund, capture-success/reservation-failure compensation. Axe checks and width checks passed. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/multimodal.spec.ts tests/discovery.spec.ts tests/hotels.spec.ts tests/trains.spec.ts` | **38 passed**, desktop/mobile discovery regression. |
| `docker compose exec -T postgres psql -U travbook -d travbook -Atc "select envelope::text from journey_outbox order by aggregate_id,aggregate_version" \| node scripts/validate-journey-events.mjs` | **236** envelopes validated after two full lifecycle browser runs; state-only schema rejects contact fields. Additional final visual-run events are recorded below. |

`PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/journey-lifecycle.spec.ts --grep 'bus OIDC' --output=/tmp/travbook-final-title-browser`: **2 passed** after the final heading adjustment, including accessibility, responsive layout, document and refund checks. Desktop bus and mobile bus/cab screenshots were visually inspected.

These are distinct verification runs, not additive unique-test counts. Earlier failures were corrected: the seat race test wrongly assumed which equal-time contender wins, and a new refund test queried a non-existent journal column instead of joining by operation ID. Assertions now verify exactly one winner and the real immutable ledger relationship. No failing final checks remain in the runs above. Editor diagnostics report no errors in the new backend/frontend contexts.

## Retained local data and deployment

A restricted PostgreSQL custom-format backup was taken before V21. The migration is additive; no old migration or retained record was reset. Before/after checksums matched exactly:

| Retained data | Count | Payload checksum |
| --- | ---: | --- |
| Hotel bookings | 155 | `215a4ab5b88cf362ace066f6f86f41d4` |
| Rail bookings | 113 | `a7395ed111d9fbcc7257b44fdcf45a9b` |
| Existing holiday quotes | 19 | `6dddc1087432ce1f8d200c01ff0138c6` |

After the first ten browser journeys: eight cancelled and two compensated failed bookings, all ten workflows DONE, zero held bus seats, twenty capture journal entries and twenty refund journal entries. Final retained totals after reruns: 18 cancelled and four compensated failed bookings, all 22 workflows DONE, zero active seats, 44 capture entries and 44 refund entries. Final event validation passed for **262** envelopes. All fictional records are intentionally retained.

Local arm64 images: API `sha256:9370d2e93151f788d53c90b721674729d32c3250efd995817f235b1903bbb76a`; final web `sha256:cce0043eae05e5842f6a2642c9f105f9b88ecebc937709935e1b6ec0c3ddd710`. Open `http://127.0.0.1:3000`. Public local Alice/Bob fixtures remain ordinary customer accounts; no operator role/MFA was fabricated. Worker environment switches are documented in `.env.example` and Compose.

## Remaining scope and evidence limits

Real suppliers/payment gateways/valid travel documents and operator IdP/MFA still require authorization/onboarding. Partial cancellations, bus rescheduling, cab driver assignment/tracking, package milestones and independent multi-provider fulfilment are not implemented. Exhausted journey REVIEW cases retain evidence but have no privileged resolution endpoint yet. No new downstream event consumer/notification delivery, AWS deployment, hosted CI execution, load/soak, production security assessment or restore/DR drill was performed. Dependency audit findings were not reassessed; no dependency was added. RTM remains In progress where source requirements exceed the verified MOCK scope.

See the [runbook](runbooks/journey-fulfilment.md) for local operation, recovery boundaries and safe forward-fix guidance.
