# Travel UI redesign — verification and coverage

Scope: frontend presentation across all 36 existing page templates; [ADR0026](adr/0026-travel-commerce-redesign.md), AC-097/098. No API/domain/database/provider changes. The functional remaining-work register remains authoritative for unimplemented capabilities.

## Delivered design

The shared header, service hub, search fields, result/filter cards, detail pages, checkout and fare panels, account/organizer, trips, sandbox, operational review, navigation and footer now use a consistent travel-commerce visual system. The design closely follows public MakeMyTrip layout patterns while retaining Travbook identity, supported actions and explicit simulation labels. It is not a pixel-identical copy of every external page or private checkout state.

Airport city display resolves through the existing typed location lookup; underlying canonical airport selection, keyboard autocomplete and request validation remain unchanged. Flight quote/reservation fare summaries become a desktop sidebar and a mobile stacked section. No new nonfunctional promotional or provider buttons were introduced.

## Executed checks

Executed 2026-11-01. Evidence directory: `/tmp/travbook-ui/`. Backend unit/integration regression was not rerun for this presentation-only change; real frontend/API browser tests exercised the existing backend. There is no Git metadata in this workspace; no commit or push was performed.

| Command actually run | Outcome |
| --- | --- |
| `npm run typecheck`; `npm run build` | Passed. Subsequent final frontend image builds also passed the Next.js production compilation. |
| `npm test` |6 helper tests passed. |
| `npm run contract:check` |Generated API types match; no API contract changes. |
| `npm run requirements:generate`; `npm run requirements:check`; `npm run requirements:test` |480 source rows retained, no drift,4 mapping tests passed. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- --output=/tmp/travbook-ui/full-browser` |Full 140-case desktop/mobile suite: **139 passed,1 failed** because a retained booking already occupied the test's required bus seats. No visual or application assertion was removed. |
| `TRAVBOOK_REPLACEMENT_DAY_OFFSET=30 PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/journey-advanced.spec.ts --project=mobile --grep 'AC-079' --reporter=list --output=/tmp/travbook-ui/replacement-verified` |Previously blocked scenario passed with a free future date; full replacement/payment/refund/document assertions retained. All140 distinct selected scenarios therefore have passing evidence across the suite and rerun; this is not described as a clean140/140 single run. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/visual-design.spec.ts tests/discovery.spec.ts --reporter=list --output=/tmp/travbook-ui/final-checks` |**16 passed** on the final deployed image after accessible-label, narrow-screen autocomplete and mobile search spacing refinements. Includes WCAG2/2.1/2.2A/AA-tag checks,30 representative page/viewport combinations, autocomplete overflow and keyboard search flows. Counts overlap the full 140. |
| `docker compose build frontend`; `docker compose up -d --wait --wait-timeout 180 frontend` |Passed. Final frontend, existing backend, PostgreSQL and Keycloak are running healthy. |
| Editor diagnostics for changed presentation components/styles |No reported errors. |

The pre-existing seat conflict was confirmed with a read-only database query: L1/L2 were associated with a CONFIRMED reservation created at 2026-10-31 20:01:01 UTC, before this task. Those records were preserved. The replacement test now accepts an optional date offset for retained local databases; its default deterministic CI dates and business assertions remain unchanged.

An earlier isolated replacement rerun passed its test but its HTML reporter collided with a concurrently running visual suite's output directory. It was rerun successfully with the list reporter and a separate evidence directory. This was a test-artifact issue, not a booking failure.

Manual screenshot review covered final desktop/mobile home layouts and the desktop flight checkout, with additional public/private page screenshots from the visual suite. The new design preserves visible simulation notices, denied operator access, refund/payment status separation and existing data. External identity-provider pages and unavailable MakeMyTrip private flows were not reproduced or claimed verified.

## Coverage

All36 existing route templates inherit the shared shell and design layer:

- Home/flight results, flight details, quote creation/review, booking creation/detail, trips.
- Hotels/search/property/room selection, stay quotation/checkout/detail/trips.
- Trains/search/class availability and rail quotation/booking/trips.
- Buses/search/seats; holidays/packages/quotes/enquiries; cabs/search/detail.
- Shared bus/holiday/cab quote/booking/trips, flight seats/extras and aftercare.
- Account, saved organizer, API sandbox and restricted operational review.

The new visual suite explicitly visits 15 representative public/private routes at desktop/mobile sizes, asserting visible primary navigation, correct active service on public category pages, no horizontal document overflow, expected operator denial for ordinary customers and Axe WCAG2.2AA-tag checks. Screenshots support manual review. Existing workflow suites cover generated resource/detail routes and asynchronous states. These checks are not a complete manual accessibility certification or proof that external-provider functionality is implemented.

No retained data was reset. Existing local Compose backend, PostgreSQL and Keycloak are reused; the frontend image is rebuilt and replaced. Travel and money remain MOCK. Existing product gaps such as coupons, full privacy and notification delivery are not completed by visual changes.
