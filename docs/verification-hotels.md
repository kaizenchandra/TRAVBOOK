# Hotel discovery / selection verification — 2026-10-31

Scope: AC-038–041, first hotel portions of FE-HT-01–03/05–08/11–14/17, BE-HTL-001–011 and FE-AC-06. This is search → compare/select → revalidate, **not** a completed hotel reservation/payment journey. Source P0 requirements remain retained and partially implemented rows remain In progress in the RTM.

## Implementation and contract

New hotel-owned domain, application ports/use case, two bounded deterministic supplier adapters, PostgreSQL observation adapter, controller/views and additive V10. OpenAPI 0.8.0 generates frontend types; existing API contracts remain compatible. `/hotels` and `/hotels/[id]` connect through hooks and the same-origin Next proxy to Java/PostgreSQL/provider observations. Rates, age eligibility, room pools and policy decisions are server-owned. The UI only formats values and manages selection/review state.

All properties, amenities, ratings, media, prices and room stock are explicit MOCK fixtures. Original SVG illustrations are labelled as illustrations; no licensed reviews are claimed. No guest data, hotel booking, reservation, charge or voucher is created. Existing flight workflows and PostgreSQL records are preserved.

## Executed checks

| Check / exact command | Result |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | Final backend run: **110 passed**, zero failures/errors/skips. Includes 6 hotel domain/supplier/use-case tests, 6 hotel HTTP/PostgreSQL tests, an additional architecture rule, and V9→V10 preservation in the existing migration test. |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=HotelDiscoveryTest test -B` | 6 passed again after test-only unused-import/null-analysis cleanup; not counted as additional unique tests |
| `npm run contract:generate` / `npm run contract:check` | Typed client generated; drift check passed |
| `node scripts/contract-compatibility.mjs /tmp/travbook-pre-hotels.openapi.json` | Passed against the pre-hotel contract snapshot: additive changes only |
| `npm run contract:lint` | Valid; four pre-existing license/local-server/redirect warnings remain. New path ambiguity was fixed, not suppressed. |
| `npm run typecheck` | Passed |
| `npm test` | 6 existing frontend unit tests passed |
| `npm run contract:test` | 9 compatibility/generator tests passed |
| `npm audit --audit-level=moderate` | Zero npm vulnerabilities reported |
| `npm run requirements:generate` / `npm run requirements:check` | 480 source IDs/priorities retained; hotel mapping added |
| `docker compose build backend frontend` / final `docker compose build frontend` | Built Java API and Next standalone images; production build/type compilation passed |
| `docker compose up -d --wait --wait-timeout 180` | Updated local stack healthy; database at V10, named volume preserved |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- hotels.spec.ts` | **8 passed** in 7.7s against built containers, desktop/mobile. Includes multi-room/child-age search, filters/refresh/back, gallery, keyboard room selection, immediate refresh preserving both rooms, tax-inclusive repricing, changed-rate acknowledgement, sold-out recovery, partial/empty/outage paths, HTTP schema checks, no horizontal overflow and axe WCAG 2.2 AA checks. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e` | **52 passed** in 2.7 minutes against production containers: all 44 existing desktop/mobile flight/auth/quote/booking/checkout/document/refund scenarios plus 8 hotel scenarios |
| Editor diagnostics for changed hotel implementation/tests | No remaining diagnostics reported |

Backend checks assert precise nightly/age/tax/fee calculations; cancellation cutoff boundary; shared standard-room stock; changed FAMILY rates; unavailable STUDIO rates; mixed/duplicate/missing-room rejection; price injection rejection; partial vs total outage vs empty; supplier search/reprice time budgets; unchanged original observations; transactional rollback; fresh repository reconstruction; expired snapshot rejection across all read/reprice paths; and zero hotel-induced booking/payment rows.

## Defects caught before handoff

- First test compilation caught an invalid compound `var` declaration; corrected before tests ran.
- Missing required query context could fall through the common exception handler to 500; now returns the existing 400 problem contract.
- Lint caught structurally ambiguous hotel search/detail routes; saved-search read is now `/api/v1/hotel-searches/{id}`.
- Browser tests caught delayed radio feedback and an immediate-refresh race losing room 2. Selection now updates immediately and persists synchronously through Next's supported native history API; refresh assertions remain in the suite.
- Select-label and global alert selectors were corrected to use actual accessible roles/names and scope errors to the application main region; no pricing, error or accessibility assertions were removed.
- Failed new searches hide prior results rather than presenting old availability under new input.

A desktop results screenshot was visually inspected: readable search/occupancy fields, distinct sample-provider/property cards, full-stay pricing, clear MOCK labels and original illustrations. Browser accessibility and responsive checks are separately recorded in the final results below.

The mobile comparison/revalidation screenshot was also inspected: stacked rate cards, per-room occupancy/policies, selected-state markers and an all-room final total remain readable without horizontal overflow. Automated accessibility checks are evidence for these tested states, not a site-wide conformance certification. The expired-observation browser check injects an old `validUntil` into a real response to test UI hiding; backend expiry is independently exercised with an injected clock and actual HTTP/PostgreSQL reads.

Final local state: backend, frontend, identity and PostgreSQL are running and healthy. Hotel UI is available at <http://127.0.0.1:3000/hotels>. V10 is applied successfully; existing PostgreSQL data is retained. Verification created synthetic search observations and existing flight regression fixtures through supported APIs; no database reset was performed. Hosted CI was not executed here; its existing full Maven/Playwright commands include the new tests automatically.

## Limits and remaining P0 work

Hotel owned immutable quotations/acceptance, guest details/special requests, durable reservations/stock contention, supplier confirmation, payment sequencing, vouchers, cancellation/modification/refunds and overbooking recovery remain unfinished. Pay-at-property support requires provider capability and retains its backend P0 priority. Sample lexical lookup does not establish a complete supplier catalog; popularity/recommendation ranking, licensed reviews/maps and whole-property rentals remain pending at their source priorities.

No external supplier/sandbox, tax/legal policy validation, AWS deployment, physical snapshot-retention schedule, load/SLO/DR/restore benchmark or production readiness was verified. A repricing result never enters the existing flight booking flow and is never labelled as a booking or hold. See [ADR 0009](adr/0009-hotel-discovery-and-rate-selection.md), [runbook](runbooks/hotel-discovery.md), [supplier matrix](suppliers.md) and [RTM](requirements/rtm.md).
