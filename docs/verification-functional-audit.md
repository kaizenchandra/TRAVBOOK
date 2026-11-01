# Full functional requirements audit — 2026-11-01

Historical snapshot of OpenAPI 0.20/V23. Subsequent [customer profile delivery](verification-customer-profiles.md) implements FE-ACC-05 and BE-AUTH-007 through real local OIDC/PostgreSQL and consolidates the OpenAPI summary. The counts/index below intentionally preserve the audit-time state; use the current RTM and linked increment evidence for later changes.

## Verdict

**The full functional specification is not implemented end to end.** The repository contains integrated, durable MOCK journeys across all six travel categories. This is substantial working functionality, but it is not equivalent to completing every source requirement or operating a real booking platform. Missing product capabilities remain in addition to authorized-provider and AWS access blockers.

This audit compares both source specifications, the master prompt and architecture rules with the actual routes, OpenAPI, domain modules, migrations, tests and existing evidence. The complete [requirement-by-requirement audit index](requirements/functional-audit-index.md) retains every source identifier and priority. The [RTM](requirements/rtm.md) remains the cross-layer implementation map. Historical increment evidence is not treated as a fresh test run.

## Audit plan and scope

1. Validate source/RTM coverage and inspect customer, supplier, commerce and operations contracts.
2. Build the current source; execute backend, contract, frontend, browser and mocked infrastructure checks.
3. Exercise additional uncovered requirements and investigate failures without resetting retained data.
4. Record passing evidence separately from missing functionality, incomplete coverage and external blockers.

Local services use PostgreSQL, Java/Spring, Next.js and a loopback Keycloak OIDC provider. Supplier/payment effects and travel documents are MOCK. Operator browser tests use an isolated, cryptographically signed test issuer; ordinary local customer accounts are not granted operator roles. There is no Git repository in this workspace, so no revision/PR comparison is claimed.

## Source coverage and traceability finding

The source register contains **480 rows: 434 functional requirements, 16 frontend acceptance scenarios and 30 NFRs**. Prose constraints in the source documents also remain binding.

| Functional priority | Source rows | Recorded Verified | Recorded In progress | Recorded Not started |
| --- | ---: | ---: | ---: | ---: |
| P0 | 314 | 15 | 227 | 72 |
| P1 | 112 | 0 | 31 | 81 |
| P2 | 8 | 0 | 2 | 6 |
| Total functional | 434 | 15 | 260 | 159 |

These are **recorded RTM statuses, not a completion percentage or a certification of 15 entire production requirements**. Some source rows have only scoped MOCK evidence; some implemented pieces still lack a source mapping. For example, operator MFA checks exist although BE-AUTH-012 is unmapped. No row was promoted to Verified simply because a regression passed. The 16 source acceptance scenarios currently have five In progress and eleven Not started mappings; the 30 NFRs have eighteen In progress and twelve Not started mappings. Those mapping gaps are not evidence that every corresponding behavior is absent.

**Fixed audit defect:** the requirements generator applied overrides only inside an existing group. It silently dropped **48 standalone mappings**, including 31 P0 and 17 P1 rows for bus/holiday/cab discovery, hotel pay-at-property and amendments. [The resolver](../scripts/requirement-mappings.mjs) now applies all valid standalone overrides and rejects unknown source IDs. Four [regression tests](../scripts/requirement-mappings.test.mjs) verify this and are included in CI. Source texts, identifiers and priorities are unchanged. The generated RTM now contains the previously omitted evidence.

## Functional capability assessment

All supported behavior below is MOCK unless explicitly identified as local OIDC. The verification table records the results of the fresh execution.

| Requirement families | Implemented integrated scope | Remaining source scope / gap |
| --- | --- | --- |
| FE-HOME, FE-SEARCH | Six linked services; contextual category pages; location lookup; dates/travellers; URL-backed results, filters and pagination; partial/error states | Complete shared search editing/state preservation, geolocation consent, recent/saved searches, wishlist, promotions and advanced comparison are not fully delivered. |
| FE-FL / BE-FLT | Domestic one-way/return discovery, exact reprice, immutable owned quote, traveller checkout, authorize/reserve/capture, recovery, My Trips, full cancellation/refund and protected MOCK summary | International coverage, full layover/terminal/fare-family/add-on choices, required filter/sort dimensions, authorized ticket issuance, segment changes and airline updates are incomplete. |
| FE-TR / BE-RAIL | Stations, classes/quotas, distinct RAC/waitlist, eligibility, accepted passenger quote, durable payment/reservation, owned MOCK PNR/summary, selected-passenger cancellation/refund | Official railway authentication/ticketing/quota rules, real PNR/running status, actual berth assignment and partial-segment changes are unavailable. |
| FE-BUS / BE-BUS | Search; synthetic seater/sleeper decks; seat-specific price/eligibility; boarding/drop; durable exclusive holds, reservation/payment, ticket summary, selected-seat refunds and linked replacement booking | Actual supplier layouts/inventory/tickets, live tracking/operator contacts and native provider rescheduling remain unavailable. Replacement is same provider/route/party with a separate full charge and original refund. |
| FE-HT / BE-HTL | Property/room search, occupancy, plans, total prices, quotes/acceptance, guests, prepaid/deferred bookings, voucher, cancellation/refunds; overlapping date/room/guest replacement | Licensed maps/reviews, live overbooking updates, actual check-in documents/property settlement, cross-property/method changes and amendments of non-refundable prepaid originals are incomplete. |
| FE-HOL / BE-HOL | Package catalog/itinerary, supported customization, owned quote/local enquiry, single-provider bundle booking, explicit 30/70 milestone plan, balance deadline/recovery, vouchers and optional-activity refunds | External enquiry fulfilment, vendor coordination, independent multi-provider partial fulfilment, arbitrary commercial milestones, shareable drafts and broader comparisons are incomplete. |
| FE-CAB / BE-CAB | Airport/local/outstation search and fares, capacity, quote/booking/payment/cancellation, persisted synthetic driver/vehicle assignment and stage updates | Actual routing/maps, licensed dispatch, real driver/contact information and live GPS are unavailable. Customer-triggered demo progression is not live tracking. |
| FE-ACC / BE-AUTH | Real local OIDC protocol, secure server session/logout, issuer/audience/scope checks, account-linked resources, ownership and privileged signed-MFA enforcement | Production registration/verification/recovery onboarding, OTP abuse/expiry flow, editable profile/contact, saved travellers, preferences, account export/deletion/consent lifecycle are incomplete. GET /me returns only customer ID and creation time. |
| BE-LOC / BE-SRCH | Canonical sample catalogs; context-specific bounded gateways; persisted snapshots; sorting/filtering/freshness and explicit supplier failures | Full licensed catalogs and cross-supplier mapping refresh, health/geography-based routing, cache stampede controls, broader deduplication/ranking and fare alerts are incomplete. |
| FE-PAY / BE-PRC / BE-OFF | Server-owned decimal-string/BigDecimal INR totals, immutable expiring quotes, explicit acceptance and sample fee/policy breakdowns | Coupons/discount eligibility, atomic redemption/limits, promotion administration, real tax/invoice rules and FX are absent or incomplete. No coupon API/UI exists. |
| BE-BKG | Owned idempotent intents, separate state machines, PostgreSQL workflows/fenced leases, atomic outboxes, contention, compensation, restart recovery, bounded status polling | External supplier workflows and wider component/ticket semantics still need provider-specific adapters; not every outbox is delivered to a downstream consumer. |
| BE-PAY | Persistent simulated authorization/capture/void/refund operations, balanced immutable journal, duplicate-effect prevention and unknown-outcome recovery | No hosted gateway collection, UPI/card/net-banking implementation, signed payment webhook endpoint or external settlement reconciliation. Credentials alone cannot enable these. |
| BE-REF | Policy/quote consent, cancellation, independent delayed refund state, partial selected-passenger/seat/activity entitlement, compensation/recovery | Real gateway/supplier refunds, arbitrary partial segments/rooms, refund notifications and settlement-based reconciliation remain incomplete. |
| FE-TRIP / BE-TRIP | Owned booking lists/details, current payment/reservation/refund state and protected versioned MOCK document downloads | Valid travel documents/invoices, searchable/filterable complete history, full retention/deletion policy, booking-linked support, reminders and rebooking are not fully delivered. |
| BE-NTF | Some source lifecycle events exist durably | No notification module/delivery provider, versioned template workflow, preferences, retries/deduplication or customer delivery proof. Outbox insertion is not notification delivery. |
| FE-ADM / BE-OPS | Constrained hotel/rail/journey REVIEW recovery, evidence checks, recent signed MFA, ownership, idempotency, immutable audit and selected legacy reconciliation | General supplier/credential/routing administration, support cases, financial approvals/adjustments, reports, dashboards/alerts and production operator provisioning remain incomplete. |
| BE-AN | Some operational metrics and privacy-limited lifecycle events | No complete funnel analytics, financial reconciliation exports, supplier/commercial dashboards or separate analytical workload. |
| FE-ENH and other P1/P2 | Limited category discovery/comparison and synthetic cab updates | Unified planning, saved-search alerts, loyalty, personalization, governed AI, collaborative planning, PWA/offline and other enhancements are not complete. |

There are **141 documented HTTP operations across 135 paths**. The absence of coupon, saved-traveller, notification, wishlist, payment-webhook, settlement and analytics contracts agrees with the corresponding implementation gaps; endpoint count itself is not a completion metric.

## Important findings and next acceptance gates

### F1 — P0 commerce capabilities are absent, independently of provider access

FE-PAY-05/06, BE-PRC-005/006/011 and BE-OFF-001/002/004 require configured offers, eligibility, atomic redemption and rejection UX. There are no matching contract paths, domain modules or browser cases. These can be implemented and tested locally before onboarding a real provider. Acceptance must include concurrent last-redemption contention and preserving checkout state after rejection.

### F2 — P0 identity/privacy is only partially implemented

FE-ACC-03/05/09 and BE-AUTH-004/007/009/011/013 are not satisfied by OIDC login alone. [CustomerController](../apps/api/src/main/java/com/travbook/customer/adapter/in/web/CustomerController.java) exposes read-only identity metadata; [Account](../apps/web/src/features/account/Account.tsx) has no profile editor or account-data workflow. OTP/recovery needs approved IdP configuration; profile, consent and export/deletion orchestration also require application work and retention rules. [OperatorIdentity](../apps/api/src/main/java/com/travbook/platform/security/OperatorIdentity.java) already enforces recent signed MFA, so that part must not be reported as absent merely because its source mapping is missing.

### F3 — P0 notifications are not delivered

FE-TRIP-08, BE-REF-012 and BE-NTF-001–003/005–011 remain incomplete. There is no delivery adapter/worker/template/preference implementation for these outcomes. Existing outboxes and booking-history event delivery do not prove an email/SMS was sent. A deterministic delivery adapter with isolated retry/deduplication tests is possible without external access; actual sending requires an authorized provider.

### F4 — Financial operations/reporting are narrower than the source baseline

BE-PAY-006/013, BE-OPS-008/010/013 and BE-AN-001/002/004/005/007/009 are not complete. The [payment context](../apps/api/src/main/java/com/travbook/payments/) implements local simulated financial effects, not hosted collection, signature-verified callbacks, settlement import or approved adjustment/reporting workflows. Operator recovery intentionally cannot manufacture a supplier success or financial adjustment.

### F5 — Real travel and payments remain unimplemented integrations

BE-FLT-013/018, BE-RAIL-008/010, BE-BUS-003/013, BE-HTL-013, BE-HOL-007/012, BE-CAB-005/007/008, BE-PAY-002/003 and FE-TRIP-03 require authorized provider capabilities. [SearchConfiguration](../apps/api/src/main/java/com/travbook/search/config/SearchConfiguration.java) explicitly rejects SANDBOX/LIVE. This is correct fail-closed behavior, but supplying credentials is **not sufficient**: adapters, approved contracts, official policies and sandbox acceptance tests still need implementation. Mock summaries are not tickets, tax invoices or valid check-in vouchers. Synthetic cab progression is not GPS.

### F6 — Required UX and travel breadth remain incomplete

FE-FL-09/10/11/13 require more than the implemented nonstop/airline filters and price/duration/departure sort. Full price/time-range filtering, arrival/recommended sorting, fare families and terminal/layover data are not delivered. HOME/SEARCH prose also requires preserving in-progress search input when switching service. The [flight homepage](../apps/web/src/features/flights/FlightSearch.tsx) still says booking/payment are not enabled even though MOCK checkout exists; this is stale user-facing copy. The top-level OpenAPI description likewise contains contradictory historical “not enabled” statements followed by newer implemented capabilities. Per-operation contracts and current tests are more precise; these summaries need consolidation.

**Reproduced requirement failure:** in a fresh Chromium context, set the flight Departure field to `2027-01-29`, select Trains in the service hub, then select Explore flights in the primary navigation. The field returns to `2026-11-02` (the default) instead of retaining the edited date. This violates the frontend homepage interaction rule “Search information must not silently disappear while a user is editing it.” Submitted search IDs have restoration tests, but they do not cover an unsubmitted cross-service draft. This exploratory check is separate from the existing regression suite and remains an open product defect.

### F7 — Traceability and test coverage must not be mistaken for full acceptance

The generator defect is fixed, but mappings remain progressive and some summaries describe earlier increments. Tests named AC-001 etc. are implementation scenarios, **not automatically the same as source FE-AC-01 etc.** Source FE-AC-08 (coupon rejection) has no implemented flow despite tests with other AC-008 labels. The frontend unit suite covers formatting and API error helpers, not a full React component/form suite. Some browser cases deliberately intercept a response to test an error/expired UI; authoritative expiry/ownership/state decisions require the separate backend tests.

### Original frontend acceptance scenarios

This table links the source scenarios by meaning rather than by matching similarly numbered implementation test names. “Covered” describes the test's intended scoped coverage; fresh execution results below determine whether that suite passed.

| Source scenario | Implementation evidence / limitation |
| --- | --- |
| FE-AC-01 service switching | Six Next.js service links and category forms exist. Cross-service edited draft loss is reproduced above; no complete acceptance claim. |
| FE-AC-02 search context | `discovery.spec.ts`: submitted search, filter, detail, back/refresh context. |
| FE-AC-03 return date | `discovery.spec.ts`, backend discovery/API validation; departure/return constraints. |
| FE-AC-04 seat pricing | `multimodal.spec.ts`, `MultimodalDiscoveryIntegrationTest`: server seat-specific totals. |
| FE-AC-05 contention | `multimodal.spec.ts`, `journey-lifecycle.spec.ts`, `JourneyIntegrationTest`: lost seats, holds and shared inventory. Actual supplier contention remains blocked. |
| FE-AC-06 room comparison | `hotels.spec.ts`, `stays.spec.ts`, `HotelIntegrationTest`/`StayIntegrationTest`: policies, occupancy and total. |
| FE-AC-07 customization | `multimodal.spec.ts`: supported MOCK comfort/activity changes and authoritative quote. Broader vendor-dependent customization remains incomplete. |
| FE-AC-08 coupon rejection | **Not implemented; no matching UI/API/test.** |
| FE-AC-09 revalidation | Discovery/quotation/hotel/rail/journey tests cover changed/expired/unavailable selections and explicit acceptance in supported flows. |
| FE-AC-10 captured but supplier pending | Independent states and recovery are implemented; API/integration failure paths and checkout UI assertions cover portions. A dedicated held-in-pending browser acceptance case for every vertical is not established. |
| FE-AC-11 duplicate submission | Backend concurrency/idempotency tests; checkout and aftercare lost-acknowledgement/refresh browser cases. No real gateway proof. |
| FE-AC-12 unavailable supplier | Flight/hotel/rail/multimodal browser and API partial/outage cases. |
| FE-AC-13 verified urgency | Explicit MOCK labels and fixture/current inventory checks; no commercial-data validation or exhaustive content audit. |
| FE-AC-14 accessibility | Keyboard and axe checks on selected desktop/mobile journeys; incomplete manual assistive-technology/all-component evidence. |
| FE-AC-15 responsive checkout | Chromium mobile-emulated booking cases and overflow assertions; not every supported viewport/physical browser tested. |
| FE-AC-16 demo separation | UI/API mode labels, invalid-for-travel document assertions and rejected SANDBOX/LIVE configuration tests. |

### F8 — Production/NFR evidence is still incomplete

Mocked Terraform tests validate configuration only. No AWS deployment/real IAM/IdP/RDS TLS test, load/soak benchmark, restore/DR drill, penetration test, manual screen-reader audit, actual iOS/Safari/Firefox test or real financial reconciliation was performed. Chromium mobile emulation and automated axe checks are useful scoped evidence, not full WCAG 2.2 AA certification. All numerical SLO/RTO/RPO targets remain proposed. A clean npm audit does not scan Java dependencies or container images.

## Fresh verification execution

| Executed command / check | Result and limits |
| --- | --- |
| `sh scripts/maven.sh -f /tmp/travbook-audit-20261101/apps/api/pom.xml clean verify -B` | **259 passed**, zero failures/errors/skips. Includes 17 architecture tests, domain/application/API, Testcontainers migrations/concurrency/rollback, ownership, provider-receipt and subprocess recovery checks. |
| `sh scripts/maven.sh -f /tmp/travbook-audit-20261101/apps/api/pom.xml test -B '-Dtest=OpsBrowserIT#desktopAndMobileUseRealMfaClaimProtectedApi+desktopAndMobileReconcileProvenLegacyOutcomes,JourneyOpsBrowserIT#desktopAndMobileRecoverAllJourneyKinds,AdvancedJourneyOpsBrowserIT#desktopAndMobileRecoverPartialCancellation'` | **4 passed** test methods, exercising **12 desktop/mobile operator paths** against isolated signed-identity APIs. These are additional explicitly selected IT methods, not part of default Surefire discovery. |
| `npm run requirements:test` | **4 passed** generator regression tests; newly added to CI. |
| `npm run requirements:generate` and `npm run requirements:check` | **480 rows retained and checked**, including previously dropped standalone mappings. Independent source-table scan found all 186 frontend and 278 backend tabular IDs; plus 16 frontend source scenarios. |
| `npm run contract:check` | Generated TypeScript types match OpenAPI. |
| `npm run contract:lint` | Passed with **four existing warnings**: private license metadata, localhost server, redirect-only login/logout. |
| `npm run contract:test` | **9 passed** compatibility/generator tests. No base-branch compatibility run: this workspace has no Git repository. HTTP contract was not changed in this audit. |
| `npm run typecheck`, `npm test`, `npm run build` | Passed; **6 frontend helper tests** in two files; optimized Next.js build succeeded. This is not full React component-test coverage. |
| `npm audit --json` | Exit 0; **0 reported vulnerabilities** across 213 dependency entries. Earlier reports of three moderate findings are historical; no dependency upgrade was performed here. Java dependencies and images were not vulnerability-scanned by this command. |
| `docker compose build backend frontend`; `docker compose up -d --wait --wait-timeout 180` | Passed, local stack healthy. A separate byte comparison confirmed **737/737 application class/resource entries** in the running API match the current-source tested JAR. |
| `terraform -chdir=infra/aws fmt -check -recursive`; `terraform -chdir=infra/aws validate`; `terraform -chdir=infra/aws test` | Passed; **5 mocked AWS plan tests**. No AWS account call, real plan or deployment. |
| Editor diagnostics on changed scripts, package manifest and CI | No reported errors. |
| Exploratory Chromium navigation probe | **Failed product expectation:** unsubmitted departure date lost across service switching; reproduction above. |
| Persisted outbox queries → the eleven existing `scripts/validate-*-events.mjs` validators | **8,258 event envelopes passed**; table/count breakdown below. This proves schema conformity of committed snapshots, not downstream publication/delivery. |
| `docker compose exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 -U travbook -d travbook < scripts/audit-financial-integrity.sql` | Read-only repeatable-read snapshot: **411 captured accounts, 334 completed refunds; zero violations** for balanced/exact capture journals, balanced/exact completed-refund journals, cumulative entitlement, owner/currency. Includes retained historical MOCK records. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- --output=/tmp/travbook-audit-20261101/browser-results` | **114 passed in 12.2 minutes**: 57 desktop and 57 mobile Chromium cases, zero retries/failures/skips. Actual local UI → HTTP API → PostgreSQL/MOCK adapters, with deliberate browser interception in selected error/recovery cases as described above. |
| `node scripts/verify-compose-recovery.mjs` | **Passed AC-037**: application containers restarted, JDBC login session and owned booking survived, My Trips displayed it, repeated booking/checkout keys returned the same operations, and one reservation/one capture/two balanced journal entries remained unchanged. PostgreSQL/identity were not reset. |

The backend run uses a byte-for-byte copy of the current POM/source tree under `/tmp/travbook-audit-20261101/apps/api` to avoid the previously observed editor/Maven collision on the shared output directory. This does not change application source. Testcontainers uses isolated PostgreSQL databases. Browser tests use the existing local MOCK Compose stack and may append synthetic records or cancel their own fixtures; retained booking history and data volumes are preserved.

The source copy was compared against the workspace after the backend run: no POM/source differences. Logs and raw browser artifacts from this audit are under `/tmp/travbook-audit-20261101/`; the committed report/index retain the review conclusions. One mobile checkout screenshot was visually inspected in addition to the existing browser assertions. No automated retry was configured for the browser suite.

### Complete customer browser suite

| Test file | Desktop passed | Mobile passed |
| --- | ---: | ---: |
| [bookings](../apps/web/tests/bookings.spec.ts) | 4 | 4 |
| [checkout](../apps/web/tests/checkout.spec.ts) | 7 | 7 |
| [journey aftercare/milestones/dispatch](../apps/web/tests/journey-advanced.spec.ts) | 4 | 4 |
| [journey lifecycle](../apps/web/tests/journey-lifecycle.spec.ts) | 5 | 5 |
| [bus/holiday/cab discovery](../apps/web/tests/multimodal.spec.ts) | 5 | 5 |
| [selected rail passengers](../apps/web/tests/passenger-cancellation.spec.ts) | 2 | 2 |
| [OIDC and quotations](../apps/web/tests/quotations.spec.ts) | 4 | 4 |
| [rail ticket lifecycle](../apps/web/tests/rail-tickets.spec.ts) | 4 | 4 |
| [hotel lifecycle/amendments](../apps/web/tests/stays.spec.ts) | 7 | 7 |
| [HTTP schema contracts](../apps/web/tests/contract.spec.ts) | 1 | 1 |
| [flight discovery](../apps/web/tests/discovery.spec.ts) | 6 | 6 |
| [hotel discovery](../apps/web/tests/hotels.spec.ts) | 4 | 4 |
| [train discovery](../apps/web/tests/trains.spec.ts) | 4 | 4 |
| Total | 57 | 57 |

The [financial audit SQL](../scripts/audit-financial-integrity.sql) performs only reads and can be repeated against a local MOCK database. Its zero-violation check does not prove supplier settlement, absence of every possible duplicate effect, or production reconciliation; those require additional external evidence. All counts are observations at execution time, not fixed inventory/test expectations.

### Persisted event evidence

Executed using `docker compose exec -T postgres psql -U travbook -d travbook -At -v ON_ERROR_STOP=1 -c 'select envelope::text from <table> order by id'`, with stdout passed directly to `node scripts/validate-<kind>-events.mjs` through `execFileSync`. Table names came from the fixed list below; no payloads or credentials were printed. Each query saw a committed snapshot during the browser run, including retained historical events.

| Table | Validator kind | Validated envelopes |
| --- | --- | ---: |
| pricing_outbox | pricing | 700 |
| booking_outbox | booking | 864 |
| booking_checkout_outbox | checkout | 1,430 |
| trip_document_outbox | document | 40 |
| booking_aftercare_outbox | aftercare | 277 |
| payment_refund_outbox | refund | 659 |
| stay_outbox | stay | 1,655 |
| rail_ticket_outbox | rail | 1,275 |
| holiday_outbox | holiday | 98 |
| journey_outbox | journey | 918 |
| journey_care_outbox | journey-care | 342 |

### Mandatory cross-layer scenario coverage

The following separates implemented test coverage from missing or partial acceptance. Browser results are subject to the final suite outcome above. Tests run with deterministic provider outcomes and local OIDC, not authorized supplier sandboxes.

| Master prompt scenario | Evidence / limit |
| --- | --- |
| 1 Flight search and selection | Discovery/quotation browser + API tests. |
| 2 Train search/class availability | Train browser and rail API tests. |
| 3 Bus search/seat selection | Multimodal and journey lifecycle browser/API tests. |
| 4 Hotel search/room selection | Hotel discovery and stay lifecycle browser/API tests. |
| 5 Holiday discovery/quotation | Multimodal and journey lifecycle browser/API tests. |
| 6 Valid quotation/checkout | Owned quote and booking tests across enabled MOCK verticals. |
| 7 Expired quotation | Backend rejects actual expiry; some browser cases inject expired responses to check UI. |
| 8 Price changed before payment | Reprice/acceptance and final-validation API/domain tests; changed-price browser scenarios. |
| 9 Inventory exhausted | Lost-seat/room and capacity tests, scoped to local provider pools. |
| 10 Duplicate booking | Concurrent backend requests and lost-acknowledgement browser recovery. |
| 11 Duplicate payment callback | **Not implemented / not tested:** no signed gateway webhook endpoint. Local payment-operation deduplication is a different contract. |
| 12 Payment failure | Local authorization/capture rejection, compensation and browser failure states. |
| 13 Payment captured/supplier pending | Independent states and recovery have integration coverage; dedicated all-vertical pending browser acceptance is incomplete. |
| 14 Supplier confirmation after timeout | Persisted simulated operation receipts, response-loss/status reconciliation and recovery; not an actual supplier network timeout test. |
| 15 Confirmed booking | MOCK supplier receipt + payment policy + protected document gates. |
| 16 My Trips status | Owned browser/API lists and detail links. |
| 17 Cancellation | Full and supported selected-passenger/seat/activity paths. |
| 18 Refund status | Delayed, partial, cumulative/duplicate entitlement and compensation tests; no bank settlement. |
| 19 Unauthorized access | OIDC ownership/scope/CSRF checks; foreign booking/PNR/document denied; stale private view hidden. |
| 20 Partial supplier outage | Discovery browser/API cases distinguish partial/error/empty results. |
| 21 Mobile booking | Chromium iPhone-sized emulation, not physical-device/Safari verification. |
| 22 Keyboard booking | Selected keyboard journeys and axe checks, not an exhaustive all-control manual audit. |
| 23 Checkout refresh | Stable owned identifiers and lost acknowledgement tests. |
| 24 Worker restart | Real child JVM exits after committed mock effects and lease-based recovery in isolated PostgreSQL tests. |
| 25 Event replay/reconciliation | Booking-history delivery/deduplication/order/quarantine/replay tests and constrained operations recovery. Delivery/consumption is not implemented for all lifecycle outboxes. |

## Release decision

Do not mark the complete platform, all P0 requirements, SANDBOX/LIVE integrations or AWS rollout complete. Prioritize missing provider-independent P0 capabilities (profile/privacy, coupons and notification delivery), complete source-to-test mappings, then implement approved external adapters and financial reconciliation. Apply production and NFR gates separately from passing local MOCK regressions.

Audit changes are limited to this report/index, the corrected RTM generator and its tests/CI gate, regenerated RTM, the read-only financial-audit query, and the delivery-plan entry. No application feature, API schema, provider integration or database migration was changed. The uncovered cross-service draft-loss defect and stale copy remain open. Local application services are running after verification; all retained PostgreSQL history is preserved.
