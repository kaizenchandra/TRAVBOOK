# ADR 0026 — Shared travel-commerce visual redesign

The latest user request replaces the earlier spacious editorial landing-page direction with a close MakeMyTrip-style visual structure across the application. Public reference pages inspected: [homepage](https://www.makemytrip.com/), [flights](https://www.makemytrip.com/flights/) and [hotels](https://www.makemytrip.com/hotels/). The homepage was also inspected in the browser after dismissing its login promotion. Reference inspection was read-only; no account, booking or payment was submitted.

## Chosen implementation

- A navy backdrop, compact utility header, account action and raised white service navigation with large icons and active-category indication.
- Large segmented search cells, city names resolved from the existing location API, prominent blue gradient search action, compact filter/result cards and clearer fare hierarchy.
- Consistent surfaces, borders, form controls, typography, price summaries and status treatments across all 36 existing page templates. Desktop flight quote/reservation review has a sticky fare sidebar; mobile stacks content and retains reachable navigation.
- A single final design layer in `apps/web/src/app/travel-design.css`, composed after the existing feature styles. Shared shell/navigation uses the current route for selection; existing page-specific duplicate navigation is hidden. Existing form semantics and backend contracts are preserved.
- Original Travbook identity, existing original illustrations and licensed installed icons are retained. No MakeMyTrip logo, proprietary code, promotional photography, phone-authentication interface, fabricated offers or unsupported service buttons are copied. Public reference patterns inform structure; this is not a claim of pixel-identical branded reproduction.
- Visible MOCK notices, real resource ownership, explicit financial consent, OIDC sign-in, failure states and independent payment/ticket/refund state remain intact. No new dependency, backend change, migration, supplier effect or cloud resource is introduced.

## Traceability and verification

AC-097 covers shared public navigation and visual consistency across flights, hotels, trains, buses, holidays and cabs on desktop/mobile. AC-098 covers account, organizer, trips, flight seats, journey/stay/rail history, sandbox and restricted operations, including ownership/access-denied presentation. These support FE-HOME-01, FE-HOME-15, FE-AC-01 and the PRD's common design/responsive/accessibility standards; unrelated product gaps remain open.

The browser suites continue to exercise actual backend-backed flows, not replacement static mockups. Existing tests are updated only where the requested heading/navigation design changed; business assertions remain. Verification results and visual limits are recorded in [the redesign report](../verification-ui-redesign.md).
