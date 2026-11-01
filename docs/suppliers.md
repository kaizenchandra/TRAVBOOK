# Supplier capability and integration matrix

OpenAPI0.24 customer integration: `/flight-services` consumes the fictional flight provider for the supported seat/extras/quote/payment/ticket/check-in/document/aftercare journey. Original sample-aster/horizon offers remain separate; those suppliers have not gained seat or check-in capabilities. No real provider access was added. [Customer evidence](verification-customer-workflows.md).

Current API-only addition: **travbook-fictional-air / travbook-fictional-pay**, local MOCK only, no onboarding or credential store. `/api/v1/demo/flight-provider` supports synthetic seat maps, meal/baggage catalogue and exact quote pricing, exclusive persistent reservations, simulated capture/decline, ticket issue/rejection, passenger check-in, full pre-check-in cancellation/refund and invalid-for-travel JSON summaries. Existing OIDC customer scopes and ownership protect the API; mutations have durable canonical idempotency, three-second local transactions and atomic events/journals. No timed holds, real PNR/airline handoff, webhook, external funds, valid boarding documents or adapter feeding existing customer checkout. [Contract/runbook](runbooks/mock-flight-provider.md), [tests/evidence](verification-flight-provider.md). Earlier provider tables below retain their historical increment context.

| Provider | Authorization / mode | Search / detail / reprice | Hold / reserve / ticket / status / cancel / refund | Limits and failure semantics | Evidence |
|---|---|---|---|---|---|
| sample-aster | Internal deterministic simulator; MOCK only | Yes / snapshot / yes | Hold/ticket/refund unavailable; durable MOCK reserve/confirmation/status/cancel compensation | Shared 8-worker/16-queue budget; 1.5 s deadline; no retries; original offer reference retained | DiscoveryTest, ApiIntegrationTest, browser suite |
| sample-horizon | Internal deterministic simulator; MOCK only | Yes / snapshot / yes | Hold/ticket/refund unavailable; durable MOCK reserve/confirmation/status/cancel compensation | Same budget; Goa intentionally unavailable; successful Jaipur empty; errors never become false zero inventory | Same tests |
| Flight GDS / licensed suppliers | No contract, onboarding or credentials supplied; SANDBOX/LIVE blocked | Not integrated | Not integrated | Must define auth, quotas, provider idempotency, timeout reconciliation, mapping, redaction and contract suite before enablement | Not run |
| Railway provider | Ticketing authorization/authentication flow not supplied | Not integrated | Not integrated | Information access is not ticketing authorization; RAC/waitlist and berth allocation remain provider-controlled | Not run |
| Bus / hotel / holiday / cab providers | No authorized access supplied | Not integrated | Not integrated | Seat holds, room occupancy, package enquiry/fulfilment and deferred payment require individual capabilities | Not run |
| External payment gateway | No gateway agreement or sandbox supplied | Not integrated | No intents, charges, callbacks or refunds | Hosted collection, signatures, replay defense, durable idempotency and settlement responsibilities required | Not run |

`INTEGRATION_MODE` defaults to `MOCK`; any other value prevents application startup. Presence of credentials cannot activate another mode. API responses, UI and persisted search snapshots retain MOCK labels. No real PNR or ticket is fabricated. MOCK confirmations/reference IDs are explicitly simulated and backed by deterministic provider receipt operations.

The sample tax is a deterministic 5% fixture and is not tax advice or a representation of applicable travel tax law. Sample second fares increase by ₹250 on reprice; fourth fares are unavailable. Real providers must replace these policies behind the same core ports and introduce contract-specific validation, not reuse these synthetic rules.

Identity is now verified separately against a loopback Keycloak 26.8.1 provider using authorization code and PKCE. This is real LOCAL_OIDC protocol evidence with public synthetic users, not a travel supplier integration or production identity deployment. Flight quotations consume the same MOCK repricing port and persist the returned components without claiming a reservation.

Booking intents now revalidate those MOCK references through the same published discovery capability. This is a read-only provider call, retried at most four times under persisted workflow control. Initial validation still performs no effects. The separate v2 MOCK checkout supports reservation, payment authorization/capture and cancel/void compensation; holds, ticketing, refunds and external providers remain unavailable. A valid accepted quote can outlive discovery-cache freshness, so worker validation fetches a new observation rather than equating cache expiry with lost inventory.

MOCK checkout strategy for both fictional suppliers: AUTHORIZE_RESERVE_CAPTURE. `DurableMockSupplier` persists each reserve/cancel receipt with a stable operation ID; definitive status lookup precedes replay, operation locks avoid in-flight false absence, and the port rejects unapproved provider identities. `MockPaymentService` / `JdbcMockPayments` provide local authorize/capture/void receipts and balanced immutable capture entries, without real funds or a hosted gateway. Both adapters have three-second transaction timeouts; orchestration leases are 15 seconds with five attempts per step and safe unknown-outcome review. Sources contain no external credentials. Failure fixtures and actual crash recovery are verified in [checkout evidence](verification-mock-checkout.md).

Customer aftercare now supports full-booking cancellation and refund for exactly accepted MOCK-FULL-V1 / MOCK-NONE-V1 policies before departure. New searches expose these policies; original stored fares are not rewritten. Ambiguous legacy wording requires review. Durable customer-cancellation receipts and an independent MOCK refund settler support status lookup, lost responses and delayed completion. No external bank refund or partial-passenger cancellation is claimed. [Current evidence](verification-cancellation-refunds.md).

## Hotel observation providers

| Provider | Mode / authorization | Supported | Unsupported | Limits / recovery / evidence |
|---|---|---|---|---|
| `sample-lotus` | MOCK, internal fictional fixtures; no external credentials | Sample property/room search, per-room ages/capacity, whole-selection reprice, exact nightly/inclusion/cancellation metadata | Hold, owned quote, reservation, payment, cancellation, refund, voucher and live status | Shared bounded pool of 4 threads/8 queued tasks, 1.5s search/reprice budget, no retries; replay-safe read-only calls; [tests/evidence](verification-hotels.md) |
| `sample-cove` | MOCK, internal fictional fixtures; no external credentials | Same canonical capabilities with separate fixture property IDs | Same unavailable operations | Goa search fails explicitly for partial coverage; Shimla fails in both providers; rejected/timeout calls are not empty inventory |
| External hotel supplier | SANDBOX/LIVE blocked; no contract, authorization or credentials supplied | None | All external operations | Onboarding, licensed content, provider auth/quotas, canonical mapping, timeouts, idempotency/status reconciliation, redaction and contract tests required |

Hotel fixtures use synthetic 10% tax, INR 100 per-room stay fee, age/weekend supplements and deterministic FAMILY/STUDIO repricing scenarios; the flight 5% fixture described earlier does not apply to hotels. FLEX/VALUE share physical standard-room stock (two rooms), with a separate family-room pool. Rates never constitute holds. Anonymous snapshots store no guest names/DOB/payment data. Canonical hotel ports expose no provider SDK models. Property illustrations are original vectors; ratings are synthetic and guest-review count is zero. [Hotel runbook](runbooks/hotel-discovery.md).

## Railway observation providers

| Provider | Mode/access | Capabilities | Explicitly absent | Controls/evidence |
|---|---|---|---|---|
| `sample-meridian` / `sample-starlight` | MOCK only; fictional services, no credentials | Route/date search; operating days; per-class/per-quota availability; fresh fare observation | Passenger eligibility, authorized authentication, reserve/hold/ticket, PNR, live running status, payment/refund | Two threads/eight queued tasks, 1.5s bounded search/fresh-read budgets, no retries/effects; named provider metrics; [tests/evidence](verification-trains.md) |
| Authorized railway supplier | Not onboarded; SANDBOX/LIVE blocked | None | All real information/ticketing APIs | Commercial/regulatory access, permitted auth, official quota/passenger rules, idempotency, reconciliation and contract tests required |

MAO demonstrates partial failure, JP successful empty inventory and HWH total provider failure. First morning Sleeper changes AVAILABLE to RAC 3 on a fresh check; two-tier AC demonstrates WAITLIST. These are explicitly synthetic fixtures, not real train positions or predictions. The demonstration Tatkal window (08:00 IST previous day) is not an official opening rule. Fares are one-adult base plus INR 30 simulated fee; no tax/concession compliance claimed. Station facts come from the [official catalog references in ADR 0010](adr/0010-railway-discovery.md); no booking APIs or protected timetable data were accessed. No real PNR or ticket is fabricated.

## Hotel fulfilment capability update

The earlier hotel table describes the observation-only increment. `sample-lotus` / `sample-cove` now support a **durable local MOCK reservation/cancellation** adapter behind `HotelReservations`, including stable operation lookup, immutable receipts, per-night shared room pools and current stock-aware discovery/repricing. There is no provider-side hold: stock commits only on reserve. Standard FLEX/VALUE share two rooms; FAMILY has four. Provider receipt/stock transactions have a three-second timeout; no remote pricing call is made inside those transactions. Platform orchestration uses 15-second fenced leases and bounded lookup-first recovery.

The hotel strategy is REVALIDATE → AUTHORIZE → RESERVE → CAPTURE, with CANCEL/VOID compensation. Existing published MOCK payment/refund ports supply ledger-backed financial fixtures. Full-booking cancellation supports per-room deadlines and mixed-plan partial monetary refunds. Owned MOCK accommodation vouchers are derived only from verified confirmation/capture, clearly invalid for check-in. No external provider voucher, pay-at-property, amendment, partial-room cancellation, guaranteed special request, live overbooking update or real bank refund is implied. [Lifecycle evidence](verification-hotel-lifecycle.md) · [runbook](runbooks/hotel-lifecycle.md).

## Rail fulfilment capability update

The railway observation table above describes the earlier discovery-only increment.
A separate `RailReservations` port now supports durable local MOCK reserve/cancel and
operation lookup for sample-meridian/starlight. It has no external credentials or network
integration. Synthetic passenger/boarding/quota policy, current-term checks, cross-search
capacity, immutable receipts and lookup-first recovery are implemented. Holds, actual berth
allocation, upgrades/charting and external ticketing remain absent. RAC/waitlist allocations
are never promoted to confirmed travel. Shared MOCK payment/refund ports provide balanced
ledger-backed effects. Owned PNR references start `MOCK-PNR-`; protected summaries state
NOT VALID FOR TRAVEL. No real railway PNR lookup, ticket, payment or refund is claimed.
Provider simulator transactions are bounded to3seconds; workflows use15-second fenced
leases, max5 attempts (20 for refund polling), backoff and REVIEW. Contract/recovery tests
and [evidence](verification-rail-lifecycle.md) cover these capabilities. Approved railway
commercial/authentication access and official policies remain mandatory before LIVE.

Operator recovery now reads existing hotel/rail receipts through the same internal ports
and resumes only a saved workflow checkpoint with immutable audit. This adds no provider
capability, real ticketing rights, gateway credentials or valid check-in document. See the
[recovery boundary](adr/0013-constrained-review-recovery.md).

## Current MOCK aftercare capabilities

Rail simulator passenger allocations now support selected-passenger cancellation and stable
per-cancellation receipt lookup. The optional MOCK-RAIL-PARTIAL-V1 quote preserves original
fare components and requires explicit consent; remaining children need an adult. Repeat
cancellations cannot cancel a passenger twice or refund beyond captured funds. Partial
segments, real railway rules and real tickets remain unsupported.

Hotel/rail simulators and the payment port expose current canonical state for restricted
legacy proof reconciliation. Historical receipts alone do not override current cancellation
or pending refunds. This adds no LIVE/SANDBOX capability, provider credentials, official
PNR rights or externally valid check-in document. Hotel amendment/pay-at-property strategies
are still unfinished.

## Deferred hotel capability (current increment)

The preceding hotel “unfinished” statement is historical. Sample-lotus/cove now approve MOCK FLEX pay-at-property terms: no deposit, no platform collection, full property amount and free cancellation before local check-in. A provider-owned atomic amendment operation supports non-overlapping dates at the same property with unchanged plans/occupancy. Stable operation IDs, immutable receipts, property-level stock locking and lookup recovery apply. Ten amendment requests maximum per booking. Prepaid/overlapping-date changes, guest changes, no-show charges and actual property settlement remain unavailable. No SANDBOX/LIVE access was added. [Runbook](runbooks/deferred-hotel-amendments.md).

## Overlapping and funded hotel replacements

Sample-lotus/cove now quote inventory relative to the owned reservation and atomically replace overlapping nightly allocations, room plans/counts and the guest manifest. The current guest manifest references an immutable provider operation. For prepaid replacement, every old room must remain fully refundable at fulfilment; new terms may have different policies. New capture is separate and old-account refund follows supplier acceptance; rejection compensates the replacement capture. Deferred replacements call no financial provider. Stable operation IDs, receipt lookup, property locks and expiry checks apply. Same property/INR/method only; real provider capabilities remain unverified. [ADR 0017](adr/0017-funded-hotel-changes.md).

## Bus, holiday and cab discovery adapters (current)

| Provider profiles | Implemented MOCK capabilities | Not enabled |
| --- | --- | --- |
| sample-roadway / sample-nightline | Route/date search; canonical stops, seater/sleeper decks and restrictions; fresh selected-seat fare checks | Holds, reservations, tickets, payments, refunds, rescheduling and tracking |
| sample-atlas / sample-roam | Domestic/international catalogue, departure dates, itinerary/terms; supported comfort/activity quote inputs | Supplier fulfilment, actual enquiries/callbacks, milestones, cancellation/refunds and valid vouchers |
| sample-transfer / sample-drive | Airport/local/outstation categories, schedule/capacity/luggage checks, fixture distance/bundle/extras and fare refresh | Real maps, reservations, payment/refunds, driver assignment and tracking |

All lack authorized SANDBOX/LIVE onboarding and fail closed outside MOCK. Profile calls share bounded4-thread/8-queue execution and a1500ms read budget; coverage distinguishes failed providers from healthy empty responses. No provider side-effect operation is retried because this increment exposes none. Holiday enquiry is an owned local record with a transactional outbox, not provider dispatch. Contract, expiry, partial/outage and browser evidence is linked in [verification](verification-bus-holiday-cab.md).

## MOCK journey fulfilment (ADR 0019)

The bus, holiday and cab discovery profiles now feed the journey booking context through public contracts. `JdbcJourneySupplier` is a separate durable provider simulator; authorization for SANDBOX/LIVE is still absent. Bus capability: exclusive timed seat hold, hold validation, reserve/confirm, status by stable operation ID, release and full cancel. Cab: prepaid category reserve/confirm and full cancel; no driver/vehicle identity or tracking. Holiday: preassembled single-provider package offer, direct or explicitly requested enquiry-origin booking terms, atomic component confirmation/cancellation; no independent multi-provider orchestration or payment milestones.

All provider operations use fixed operation IDs, fingerprint-bound immutable receipts, PostgreSQL serialization and lookup-before-retry. Local transactions have a three-second timeout and workers a 15-second fenced lease. Unknown effects retry with bounded jitter/backoff then REVIEW; no blind non-idempotent retries. Monetary mutations use published MOCK payment/refund ports with balanced immutable journals. Sample cancellation policies are newly quoted and explicitly accepted; legacy planning/enquiry terms are never silently converted. Full refund before the stated cutoff; unsupported rescheduling/partial cancellation is not advertised. Ticket/voucher text is always MOCK and invalid for actual travel. Contract/failure evidence: [journey verification](verification-journey-fulfilment.md).

## Versioned journey enhancement capabilities

V2 MOCK booking terms explicitly enable bus seat-level cancellation with remaining-adult eligibility and linked replacement bookings (same provider/route/party, separate charge/refund; not native amendments). Preassembled holiday bundles optionally accept 30% deposit and 70% balance, with a fixed 48-hour deadline and final-document funding gate; after full payment, optional activities can be refunded independently. Cab reservations retain whole-vehicle pricing/cancellation and now expose persisted synthetic assignment/progression with no real driver/contact/GPS claims. Older accepted terms are not upgraded.

All effective provider/payment operations retain stable IDs and exact receipts. Partial releases affect only the original booking's selected allocations. Funding-account refund allocations are immutable, cumulative and bounded by captured amounts. Demo commands are owned, versioned, idempotent and forward-only; they never authorize money or change booking dates. Current cancellation invalidates dispatch visibility. Supported constraints, recovery budgets, timeout behavior, test scenarios and residual access limitations: [runbook](runbooks/journey-aftercare-funding-dispatch.md), [verification](verification-journey-aftercare.md).

## Requested local dummy API suite

The [API sandbox](runbooks/local-api-sandbox.md) now exposes authenticated HTTP tests without provider credentials. Existing travel/payment APIs remain the booking authorities. A separate local delivery adapter records dummy EMAIL/SMS and privacy-request receipts with durable deduplication and bounded recovery; it never contacts an external channel or erases data. Synthetic GPS coordinates derive from existing owned cab dispatch stages. Coupon previews do not redeem or change accepted prices; financial summaries read actual owned MOCK ledger records and explicitly say NOT_RECONCILED. The deployment descriptor reports a LOCAL_COMPOSE fixture and NOT_DEPLOYED, not AWS success. These capabilities add no SANDBOX/LIVE provider authorization, valid travel documents or production completion. [Evidence](verification-local-api-sandbox.md).

OpenAPI0.25: MOCK notification gateway implements durable delivery-ID idempotency and status lookup using immutable local receipts. Three bounded attempts, 15-second fenced leases and REVIEW for unknown outcomes. No external email/SMS is sent; [runbook](runbooks/commerce-notifications.md). Coupons are platform-owned rules applied only to fictional flight quotes/reservations; they do not assert supplier discounts.
