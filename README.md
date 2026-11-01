# Travbook

Current UI: a shared travel-commerce redesign across the 36 existing route templates, following public MakeMyTrip layout patterns with original Travbook branding. Includes raised service navigation, segmented search forms, consistent results/details/checkout panels, a desktop fare sidebar and responsive mobile navigation. Working MOCK flows and security boundaries are retained. [Design decision](docs/adr/0026-travel-commerce-redesign.md) · [Visual and workflow verification](docs/verification-ui-redesign.md).

An independent travel platform with **MOCK flight discovery, authenticated quotations, durable booking intents and recoverable simulated checkout**. OIDC sign-in is verified against a real local Keycloak provider. This is a development increment, not the complete booking platform or a production release.

New API-only increment: [fictional airline/payment provider](docs/runbooks/mock-flight-provider.md) with seat maps, meal/baggage quotations, durable exclusive reservations, separate payment/ticketing, passenger check-in, invalid-for-travel JSON summaries and full cancellation/refund. Enabled by `DEMO_APIS_ENABLED=true`; existing customer checkout does not yet consume it. [Contract](contracts/openapi.json) · [verification and exclusions](docs/verification-flight-provider.md).

Current customer increment: `/flight-services` now connects that provider to seat/meal/baggage selection, quotation, payment, ticketing, check-in, documents, aftercare and booking history. `/account/travel` adds saved travellers, category bookmarks, flight searches and preference storage, with scoped export/clear. Mobile navigation and unsubmitted flight drafts are retained. This is a separate clearly labelled MOCK flight fixture; legacy offer checkout is unchanged. [Current verification](docs/verification-customer-workflows.md) · [Remaining functionality](docs/requirements/remaining-work.md).

Implemented: airport lookup → one-way/round-trip search → filters/sorting/pagination → itinerary/fare review → OIDC sign-in → private quotation → acceptance → travellers → durable booking intent → recoverable validation → explicit MOCK authorization/reservation/capture → simulated confirmation or compensated failure → status/My Trips. The Next.js interface calls a Java 21/Spring Boot API through a generated OpenAPI client. PostgreSQL persists customers, sessions, immutable inputs/receipts, idempotency, leased workflows and outbox records. Two deterministic suppliers provide clearly simulated travel data.

**All travel fulfilment and money movement remain MOCK.** Flights, hotels, rail, buses, holidays and cabs have the scoped search/quotation/booking/payment/aftercare journeys documented in their runbooks. Hotels include eligible prepaid/deferred replacement flows; rail includes passenger-level cancellation; buses include seat holds and eligible replacements; holidays include supported deposit/balance funding; cabs expose synthetic dispatch. OIDC, profile editing and the new saved-travel organizer are actual platform persistence. No real tickets, bank refunds, official PNR, live GPS or valid check-in documents are available. Coupons now apply to fictional flight checkout, and lifecycle inbox/email/SMS delivery has durable MOCK receipts. Wider coupon coverage, full account privacy, notification operations/reminders, broader operational/reporting capabilities, advanced travel features and production/AWS release gates remain incomplete. See the [current remaining-work register](docs/requirements/remaining-work.md) and [RTM](docs/requirements/rtm.md) instead of treating passing tests as full product completion.

Hotel examples, API contracts, simulated pricing rules and limitations: [discovery runbook](docs/runbooks/hotel-discovery.md), [owned lifecycle runbook](docs/runbooks/hotel-lifecycle.md), [lifecycle architecture](docs/adr/0011-owned-hotel-lifecycle.md), and [current verification](docs/verification-hotel-lifecycle.md). Original property illustrations and synthetic ratings are labelled; no real reviews or popularity claims are presented.

Train examples, synthetic quota windows and authorization limits: [train runbook](docs/runbooks/train-discovery.md), [architecture decision](docs/adr/0010-railway-discovery.md), and [verification](docs/verification-trains.md). AVAILABLE is not a reservation; RAC/waitlist never guarantees a confirmed berth. No railway credentials or PNR are collected/generated.

## Run the complete local stack in Docker

```sh
docker compose up -d --build --wait --wait-timeout 180
```

Open <http://127.0.0.1:3000>. Compose builds the backend and frontend, starts PostgreSQL and local Keycloak, and preserves the existing PostgreSQL volume. This is a **local MOCK deployment**, with public fixture credentials and loopback-only HTTP ports. Stop native servers using these ports first. Use `docker compose stop` to stop services without removing data. Configuration, verification, restart and update instructions are in the [Compose runbook](docs/runbooks/local-compose.md).

## Run with native development servers

Prerequisites: Java 21, Maven 3.9+ (Maven 3.10.0 tested locally), Node 22+ (Node 24 tested), npm, and a running Docker-compatible engine. Commands below run from the repository root.

```sh
npm ci
docker compose up -d --wait postgres identity
sh scripts/maven.sh -f apps/api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local-oidc
```

In a second terminal:

```sh
npm run dev
```

Open <http://127.0.0.1:3000>. API health is <http://127.0.0.1:8080/actuator/health>. VS Code has **Travbook: API** (local OIDC) and **Travbook: Web** tasks in [tasks.json](.vscode/tasks.json). Start the `postgres` and `identity` Compose services first, leaving application containers stopped while using native servers. For public-only discovery, omit the Java profile; private routes then remain unavailable without a configured issuer.

Sign-in uses local test users `alice` / `alice-local-only` and `bob` / `bob-local-only`. These are explicitly public, synthetic fixtures; never deploy them. The [identity/quotation runbook](docs/runbooks/auth-and-quotes.md) explains configuration, expiry, CSRF, logout, retry behavior and production prerequisites.

After accepting a quote, add fictional traveller details and create an intent. Its PostgreSQL-backed worker checks the current offer; My Trips shows the saved intent and separate payment/reservation states. After validation, explicit MOCK consent can start checkout. The [booking runbook](docs/runbooks/booking-intents.md) covers recovery, retries and state meanings. `BOOKING_WORKER_ENABLED=false` pauses processing without deleting work. For an existing development identity container, recreate only `identity` to import the added booking scopes; customer IDs and PostgreSQL data are preserved.

The booking page also shows durably delivered intent history, with explicit lag and review states. A local outbox dispatcher and immutable consumer receipts survive lost acknowledgements without duplicate history. `BOOKING_EVENTS_ENABLED=false` pauses that independent worker. V6 preserves existing data and queues prior unpublished booking events. See the [event delivery runbook](docs/runbooks/booking-event-delivery.md) for diagnosis, metrics and audited operator replay.

The sample database listens only on loopback port 54329; the API and frontend use loopback ports 8080 and 3000. The local-only database password in [compose.yaml](compose.yaml) is a disposable development value, not a deployed credential. Shell environment variables override defaults; the Java process does not automatically load the root example file. Configure Next.js overrides through the shell or an ignored `apps/web/.env.local`.

On macOS [the Maven launcher](scripts/maven.sh) resolves installed Java 21. Git could not run during initial discovery because Apple command-line tools were missing; no Git metadata existed in the supplied workspace. Files have not been committed or pushed.

## Try the deterministic scenarios

| Action | Expected result |
|---|---|
| Delhi → Mumbai, tomorrow | Eight sample options across two providers |
| Choose a return date | Two directions with a combined total |
| Filter non-stop | Four matching options |
| Delhi → Goa | Four options and an explicit partial-provider warning |
| Any airport → Jaipur | Successful empty search, with recovery guidance |
| Check first/third fare from a provider | Unchanged price |
| Check second fare from a provider | ₹250 increase; explicit acknowledgement |
| Check fourth fare from a provider | Sold-out conflict |
| Reopen search after 15 minutes | Expired search; submit fresh criteria |

Every itinerary, airline, allowance, tax and policy is synthetic. Airport names/codes are a small hand-curated sample of public factual identifiers. No proprietary media, fares or code were copied. The public repricing operation is read-only. Authenticated quote creation performs another current-price check and persists a versioned snapshot; its validity is capped by the provider observation and departure. Acceptance stores an immutable receipt. Neither action creates a hold, charge or booking.

## Verify

```sh
npm run requirements:check
npm run contract:lint
npm run contract:check
npm run contract:test
sh scripts/maven.sh -f apps/api/pom.xml verify
npm run typecheck
npm test
npm run build
npm exec -- playwright install chromium
npm run test:e2e
npm audit
```

Backend tests start isolated PostgreSQL Testcontainers and must fail if Docker is unavailable; no in-memory database substitution or skipped persistence suite. Signed-token tests serve a local test JWKS. Browser tests use real Keycloak authorization-code/PKCE login, the frontend, API and local PostgreSQL; Playwright starts native apps if needed. Keep `postgres` and `identity` running. For the full container stack, set `PLAYWRIGHT_EXTERNAL_SERVERS=1` to test the built images without launching native apps. Run build and browser tests sequentially. Auth tests disable network traces to avoid recording authorization codes/session cookies.

Change [OpenAPI](contracts/openapi.json) first, then run `npm run contract:generate`. Never hand-edit [generated types](apps/web/src/lib/api/schema.d.ts). The compatibility gate conservatively rejects changes to existing machine contracts; future version transitions require a reviewed migration. GitHub Actions is configured in [verify.yml](.github/workflows/verify.yml), but no hosted CI execution is claimed.

See [current booking verification](docs/verification-booking-intents.md), [prior identity/quote evidence](docs/verification-auth-quotes.md), [delivery plan](docs/plan.md), [booking ADR](docs/adr/0003-durable-booking-intent.md), [supplier matrix](docs/suppliers.md), [security and six-pillar review](docs/security-and-operations.md), and [booking runbook](docs/runbooks/booking-intents.md).

Next P0 work includes checkout/document event dispatch, real provider document formats, broader cancellation/refund policies and audited operational resolution of review cases. Additional provider strategies and hosted gateway/webhook integration remain incomplete. Booking events now have local durable delivery; pricing event dispatch and external broker adapters remain pending. Supplier onboarding blocks SANDBOX/LIVE operation, not independent mock development. See the [event delivery evidence](docs/verification-booking-events.md).

MOCK checkout uses v2 read/start contracts to preserve legacy validation schemas. See the [checkout runbook](docs/runbooks/mock-checkout.md), [ADR 0005](docs/adr/0005-mock-checkout.md) and [verification](docs/verification-mock-checkout.md). `BOOKING_CHECKOUT_ENABLED=false` pauses its durable worker. Legacy v1 detail reads return an explicit conflict once checkout starts, rather than claiming payment never started.

Confirmed MOCK checkouts now support **Prepare MOCK summary → Download MOCK summary** with ownership checks and immutable UTF-8 content. These are explicitly not tickets, vouchers or tax invoices. See the [summary/security runbook](docs/runbooks/mock-documents-and-generator.md) and [verification](docs/verification-documents-security.md). The prior three npm audit findings are resolved: the lockfile uses actual minimatch 11.0.1, with a guarded postinstall compatibility adaptation for Redocly. Keep the postinstall step and generator regression tests when running `npm ci`.

Supported new MOCK policies can now be cancelled from an owned booking: request a cancellation quote, review refund/retained amounts, explicitly confirm, and track cancellation separately from refund. Legacy ambiguous policies fail closed. Current views use v3; v2 reads conflict once cancellation exists. [Cancellation/refund runbook](docs/runbooks/mock-cancellation-refunds.md) and [evidence](docs/verification-cancellation-refunds.md) explain pending outcomes, immutable financial records and remaining review gates. `BOOKING_CANCELLATION_ENABLED` and `REFUND_WORKER_ENABLED` independently control workers.

Train discovery now continues into an owned **MOCK rail lifecycle**: passenger eligibility,
quotation/consent, durable reservation/payment, explicit CONFIRMED/RAC/WAITLIST, protected
summaries, owned MOCK PNR and cancellation/refund. Start at `/trains`; My Trips includes rail.
Nothing is valid for real travel. [Runbook](docs/runbooks/rail-lifecycle.md),
[architecture](docs/rail-lifecycle-architecture.md) and [evidence](docs/verification-rail-lifecycle.md)
record synthetic policies, V14 migration, recovery and outstanding provider authorization.

Restricted hotel/rail operations are available at `/operations/reviews`: inspect redacted
REVIEW cases and request receipt-backed, audited reconciliation. Ordinary local customer
accounts are intentionally denied; approved operator role and recent genuine MFA claims
are required. No force-confirm or financial adjustment is exposed. See the
[recovery runbook](docs/runbooks/review-recovery.md) and [verification](docs/verification-review-recovery.md).

Rail bookings now support selected-passenger cancellation under an explicitly accepted MOCK
policy, cumulative refund tracking and revisioned summaries through v2 aftercare APIs.
[Runbook](docs/runbooks/passenger-cancellation.md) · [verification](docs/verification-passenger-cancellation.md).
Restricted operators can also record a proven terminal legacy hotel/rail outcome without
inventing a missing checkpoint or creating new financial effects; unknown cases stay in
REVIEW. [Legacy evidence](docs/verification-legacy-reconciliation.md). Restricted MOCK hotel
amendments and pay-at-property are available through the [hotel change workflows](docs/runbooks/funded-hotel-changes.md).
Real provider integrations/documents and production operator IdP/MFA provisioning remain externally gated.

## Local dummy API sandbox

Sign in at `/account`, then open **API sandbox** (`/sandbox`). It provides durable dummy
email/SMS and privacy requests, coupon previews, synthetic cab coordinates, owned MOCK
payment/ledger reports and an explicitly undeployed local target descriptor. Existing
travel/payment booking APIs are reused. Compose enables `DEMO_APIS_ENABLED=true`; outside
Compose it is off by default. No supplier, gateway, messaging, GPS or AWS credential is
needed for these simulations. Nothing sends a real message, applies a coupon to checkout,
erases an account, tracks a real vehicle or provisions AWS.

[API catalog and operation guide](docs/runbooks/local-api-sandbox.md) ·
[Verification and remaining limits](docs/verification-local-api-sandbox.md).

## Coupon checkout and lifecycle inbox

OpenAPI 0.25 / V28–V29 add immutable promotional flight quotes, reservation-time usage limits, discounted payment/refund, owned `/notifications` and preference-aware mock EMAIL/SMS receipts across eight lifecycle feeds. See the [runbook](docs/runbooks/commerce-notifications.md), [ADR](docs/adr/0027-coupon-redemption-and-lifecycle-notifications.md) and [verification](docs/verification-commerce-notifications.md). No external message or live provider effect is performed.
