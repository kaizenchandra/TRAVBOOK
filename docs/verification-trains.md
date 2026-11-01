# Train discovery / availability verification — 2026-10-31

Scope: AC-042–045; first portions of FE-TR-01–07/15 and BE-RAIL-001–005/013/015. This implements station/search/class/quota observations and fresh fare review, **not** a complete train booking journey. Source priorities remain intact and partially covered RTM rows remain In progress.

## Delivered path

`/trains` / `/trains/[id]` → typed query/mutation hooks → generated OpenAPI client → same-origin proxy → TrainController → DiscoverTrains/TrainDiscovery → Rail domain → bounded RailSupplier / PostgreSQL ports and adapters. V11 adds six station rows, saved search snapshots and journey indexes without changing V1–V10 or existing business records. No new service boundary, broker, cache or dependency was introduced.

All schedules, train numbers, names, availability counts, fares and quota windows are explicitly MOCK. Public factual station identifiers have [official source references](adr/0010-railway-discovery.md). No real railway API/credentials, ticket, PNR, berth allocation, passenger validation or payment was invoked. Existing flight and hotel implementations remain separate.

## Executed checks

| Exact command/check | Result |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | **121 passed**, zero failures/errors/skips; includes 5 railway domain/provider tests, 5 railway HTTP/PostgreSQL tests, an additional architecture rule and V10→V11 preservation in the migration test |
| `npm run contract:generate` / `npm run contract:check` | OpenAPI 0.9.0 client generated, no drift |
| `node scripts/contract-compatibility.mjs /tmp/travbook-pre-rail.openapi.json` | Passed against pre-rail contract; additive changes only |
| `npm run contract:lint` | Valid, four pre-existing license/local-server/redirect warnings |
| `npm run typecheck` | Passed |
| `npm test` | 6 frontend unit tests passed |
| `npm run contract:test` | 9 compatibility/generator tests passed |
| `npm audit --audit-level=moderate` | Zero reported npm vulnerabilities |
| `npm run requirements:generate` / `npm run requirements:check` | All 480 source requirements/scenarios and their priorities retained |
| `docker compose build backend frontend` | Java and Next standalone images built |
| `docker compose up -d --wait --wait-timeout 180` | Updated local stack healthy; V11 applied, named database volume retained |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- trains.spec.ts` | **8 passed** in 9.2s against containers, desktop/mobile |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e` | **60 passed** in 2.7 minutes against production containers: all 52 existing flight/auth/quote/booking/checkout/refund/hotel scenarios plus 8 train scenarios |
| Editor diagnostics | No reported errors in railway implementation/tests |

Backend evidence covers canonical station resolution; invalid/equal stations and date window; operating days/overnight offsets; AVAILABLE/RAC/WAITLIST separation; exact synthetic quota opening and departure boundaries; exact base/fee/total arithmetic; changed status/price; repeat-read stability; bounded provider search/fresh-read timeouts; partial vs empty vs total outage; class/status filter conjunction; missing/unsupported parameters; denied PNR/booking routes; persisted snapshot reconstruction; rollback without partial rows; expiry across dependent endpoints; and zero booking/payment rows from railway operations.

Browser evidence covers keyboard autocomplete/swap, preserved filters and refresh, class/status matrix, current class/quota reads, status/fare changes, selection invalidation, retained class/quota after refresh, visible synthetic quota/authorization restrictions, partial/empty/error recovery, schema conformance, no horizontal overflow and axe WCAG 2.2 AA checks for tested states. Expired-current-observation UI is tested by injecting a past `validUntil` into a real response; backend elapsed-clock expiry is independently tested through actual HTTP/PostgreSQL with an injected clock. This is not site-wide accessibility certification.

The mobile results screenshot was visually inspected: stacked class cells, textual status/counts, one-adult prices, route/overnight dates and clear MOCK/ticketing limits. Screenshot evidence is produced by the browser suite. No assertion was weakened to pass verification.

Final local state: all four Compose services are running and healthy. <http://127.0.0.1:3000/trains> is available for review. PostgreSQL is at V11 with existing data retained; no volume reset or historical booking/policy rewrite occurred. Verification added synthetic railway observations and existing regression fixtures through the supported APIs.

## Remaining work and limits

Passenger and quota eligibility, boarding/berth preferences, owned immutable quotations, approved authentication/ticketing, durable reservation/payment/ticket state, PNR, running status, cancellation/refund and reconciliation remain unfinished. The mock Tatkal opening/horizon is explicitly not an official rule implementation. Endpoint-only routes do not claim complete intermediate station coverage. Real providers require authorization and contract testing.

Physical search retention cleanup, production identity/edge controls, load/SLO/restore/DR evidence, managed observability and AWS remain pending. Hosted CI was not run here; the existing full Maven and Playwright jobs discover these tests automatically. No production readiness or regulatory compliance is claimed. See [ADR](adr/0010-railway-discovery.md), [runbook](runbooks/train-discovery.md), [supplier matrix](suppliers.md) and [RTM](requirements/rtm.md).
