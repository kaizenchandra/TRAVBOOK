# Enterprise Travel Booking Backend
## Functional Requirements, Non-Functional Requirements, System Architecture & Development Roadmap

**Document version:** 1.0  
**Scope:** Flights, Trains, Buses, Hotels & Homestays, Holiday Packages, and Cabs  
**Target architecture:** Domain-oriented, AWS-hosted, enterprise-grade backend  
**Primary language:** Java 21  
**Database:** PostgreSQL  
**Deployment:** AWS  
**Target audience:** Solution Architects, Backend Engineers, DevOps/SRE, QA, Product Owners

---

# PART A — PRODUCT SCOPE AND REQUIREMENTS

## 1. Business Objectives

The backend shall:

1. Expose one consistent set of application APIs for heterogeneous travel services.
2. Aggregate search results from multiple authorized providers.
3. Normalize supplier-specific inventory, pricing, policies, and booking statuses.
4. Support discovery, quotation, booking, payment, confirmation, cancellation, and refunds.
5. Maintain accurate transaction and supplier records.
6. Recover safely from third-party failures.
7. Prevent duplicate bookings and unauthorized financial operations.
8. Scale independently for high-volume search and lower-volume financial transactions.
9. Provide operational visibility, reconciliation, support tooling, and auditable changes.
10. Enable new suppliers and product categories without major changes to core booking logic.

## 2. Scope and Priority

| Capability | Priority | Delivery model |
|---|---|---|
| Account and identity | P0 | Native integration with identity provider |
| Travel locations and reference data | P0 | Internal catalogs plus licensed reference data |
| Unified search and filtering | P0 | Supplier-backed read APIs |
| Flight reservations | P0 | Licensed flight provider/GDS |
| Train reservations | P0 | Authorized railway integration |
| Bus reservations | P0 | Licensed bus supplier |
| Hotel reservations | P0 | Licensed hotel inventory provider |
| Holiday packages | P0 | Contracted package provider or platform-managed inventory |
| Booking orchestration | P0 | Internal |
| Pricing and coupons | P0 | Internal plus supplier price rules |
| Payment and transaction management | P0 | Payment gateway integration |
| Cancellations and refunds | P0 | Internal orchestration plus provider |
| Notifications | P0 | Internal with communications provider |
| My Trips | P0 | Internal |
| Operational administration | P0 | Internal restricted APIs |
| Cabs | P1 | Licensed transport provider |
| Multi-product cart/trip bundles | P1 | Internal orchestration |
| Wallet, credits, and loyalty | P2 | Internal and/or partner |
| AI travel assistant | P2 | Governed AI service |
| Corporate travel | P2 | Enterprise account and policy modules |

A P0 business capability must not be represented as production-operational until its authorized supplier integration and complete lifecycle are verified.

## 3. User Roles

| Role | Permissions |
|---|---|
| Guest | Public search and discovery |
| Customer | Manage own profile, quotations, bookings, payments, refunds |
| Customer Support | View permitted booking details, initiate controlled support operations |
| Operations | Manage supplier incidents and reconciliation exceptions |
| Finance | View financial transactions, settlements, refunds, audit reports |
| Content Manager | Maintain catalog content and promotional configuration |
| Supplier Administrator | Manage authorized supplier connections and mappings |
| Platform Administrator | Manage controlled system configuration and access |
| Auditor | Read-only access to scoped audit evidence |

All privileged actions must be restricted server-side. Role checks alone are insufficient; resource ownership and transaction-specific permissions are also required.

---

# PART B — COMPLETE FUNCTIONAL REQUIREMENTS

## 4. Identity, Authentication & Customer Management

| ID | Requirement | Priority |
|---|---|---|
| AUTH-001 | Support anonymous searches and public catalog access. | P0 |
| AUTH-002 | Support authenticated customer registration and login. | P0 |
| AUTH-003 | Integrate with an OIDC-compliant identity provider. | P0 |
| AUTH-004 | Support mobile/email verification with OTP rate limits and expiry. | P0 |
| AUTH-005 | Validate token signature, audience, issuer, expiry, and authorization scopes. | P0 |
| AUTH-006 | Support secure session termination and refresh-token lifecycle. | P0 |
| AUTH-007 | Maintain customer profiles and contact information. | P0 |
| AUTH-008 | Maintain reusable traveller/guest profiles with consent. | P1 |
| AUTH-009 | Support scoped account-data retrieval, deletion, and consent management. | P0 |
| AUTH-010 | Prevent access to other customers' bookings and PII. | P0 |
| AUTH-011 | Audit privileged profile changes and authentication security events. | P0 |
| AUTH-012 | Support MFA for sensitive administrative operations. | P0 |
| AUTH-013 | Support protected account recovery. | P0 |

## 5. Master Data and Location Services

| ID | Requirement | Priority |
|---|---|---|
| LOC-001 | Maintain airport, railway station, bus stop, city, country, and region catalogs. | P0 |
| LOC-002 | Provide autocomplete using canonical identifiers and aliases. | P0 |
| LOC-003 | Normalize names from different suppliers to platform identifiers. | P0 |
| LOC-004 | Store timezones and valid geographic coordinates. | P0 |
| LOC-005 | Support multilingual labels where licensed/available. | P1 |
| LOC-006 | Maintain supplier-to-platform code mappings. | P0 |
| LOC-007 | Refresh provider/reference data without disrupting running searches. | P0 |
| LOC-008 | Support nearby airports/stations and alternative routes. | P1 |
| LOC-009 | Expose versioned location lookup and detail APIs. | P0 |

## 6. Common Travel Search & Aggregation

| ID | Requirement | Priority |
|---|---|---|
| SRCH-001 | Validate and normalize search requests per travel vertical. | P0 |
| SRCH-002 | Select eligible suppliers based on geography, capability, health, and configuration. | P0 |
| SRCH-003 | Execute independent supplier searches concurrently under bounded concurrency. | P0 |
| SRCH-004 | Apply supplier-specific timeouts, request budgets, and rate limits. | P0 |
| SRCH-005 | Normalize supplier results into canonical service-specific contracts. | P0 |
| SRCH-006 | Merge, rank, and deduplicate results where inventory identity can be established. | P0 |
| SRCH-007 | Support sorting, filtering, and pagination/cursors. | P0 |
| SRCH-008 | Return partial results with explicit supplier/freshness metadata. | P0 |
| SRCH-009 | Cache suitable read-only search and reference data with category-specific TTL. | P0 |
| SRCH-010 | Prevent cache stampedes and uncontrolled supplier fan-out. | P0 |
| SRCH-011 | Preserve original supplier offer identifiers and pricing references. | P0 |
| SRCH-012 | Refresh selected offers prior to creating a booking. | P0 |
| SRCH-013 | Support alternative dates/routes when no results exist. | P1 |
| SRCH-014 | Support saved searches and permitted fare alerts. | P1 |
| SRCH-015 | Instrument search success, latency, supplier coverage, and cache hit rate. | P0 |
| SRCH-016 | Prevent duplicate search page results across paginated responses. | P0 |

The search layer must not treat cached offers as guaranteed bookable inventory.

## 7. Flight Search and Booking

| ID | Requirement | Priority |
|---|---|---|
| FLT-001 | Search domestic/international one-way flights. | P0 |
| FLT-002 | Search round-trip flights. | P0 |
| FLT-003 | Support multicity flight search. | P1 |
| FLT-004 | Validate passenger categories, age bands, and supplier restrictions. | P0 |
| FLT-005 | Support cabin/class filters and applicable fare families. | P0 |
| FLT-006 | Display normalized itinerary, airline, timings, stops, and layovers. | P0 |
| FLT-007 | Aggregate applicable fare, taxes, fees, and baggage information. | P0 |
| FLT-008 | Present refundable/changeable conditions per selected fare. | P0 |
| FLT-009 | Support nearby-date pricing and fare calendars when available. | P1 |
| FLT-010 | Revalidate the exact selected flight offer. | P0 |
| FLT-011 | Support optional seats, baggage, meals, or ancillary services where available. | P1 |
| FLT-012 | Collect passenger names and supplier-required identity documents. | P0 |
| FLT-013 | Initiate provider reservation/ticketing under the provider's workflow. | P0 |
| FLT-014 | Store PNR, ticket references, and individual traveller/segment status. | P0 |
| FLT-015 | Support itinerary changes, cancellations, and eligible refunds. | P0 |
| FLT-016 | Process airline schedule changes and ticketing status updates. | P1 |
| FLT-017 | Support retry/reconciliation when the supplier result is unknown. | P0 |
| FLT-018 | Produce customer travel documents after actual confirmation. | P0 |
| FLT-019 | Preserve per-segment timezone information and date changes. | P0 |
| FLT-020 | Handle provider-specific booking expiry and ancillary restrictions. | P0 |

## 8. Train Search and Booking

| ID | Requirement | Priority |
|---|---|---|
| RAIL-001 | Search trains by source, destination, and departure date. | P0 |
| RAIL-002 | Retrieve train number, route, timing, travel classes, and operating days. | P0 |
| RAIL-003 | Retrieve supplier-authoritative availability by class and quota. | P0 |
| RAIL-004 | Normalize Available, RAC, Waitlist, and other authorized statuses. | P0 |
| RAIL-005 | Display applicable fares and booking restrictions. | P0 |
| RAIL-006 | Validate railway passenger and quota restrictions. | P0 |
| RAIL-007 | Support boarding station and berth preferences where applicable. | P0 |
| RAIL-008 | Route bookings through an authorized railway ticketing integration. | P0 |
| RAIL-009 | Record PNR, journey, class, passenger, and ticket status. | P0 |
| RAIL-010 | Retrieve PNR status from an authorized data source. | P1 |
| RAIL-011 | Retrieve running-status information where permitted. | P1 |
| RAIL-012 | Support cancellations, refunds, and exception workflows according to provider rules. | P0 |
| RAIL-013 | Respect Tatkal/other quota opening rules when supported. | P1 |
| RAIL-014 | Support alternative trains, classes, stations, and dates. | P1 |
| RAIL-015 | Never guarantee confirmation based on waitlist probability. | P0 |

Railway booking must be gated on regulatory/commercial eligibility, integration approval, and permitted authentication flows. A railway information API is not equivalent to ticketing authorization.

## 9. Bus Search and Booking

| ID | Requirement | Priority |
|---|---|---|
| BUS-001 | Search buses by route and date. | P0 |
| BUS-002 | Normalize operator, bus type, duration, stops, amenities, and fare. | P0 |
| BUS-003 | Retrieve an actual supplier seat layout. | P0 |
| BUS-004 | Support seater/sleeper and multi-deck configuration. | P0 |
| BUS-005 | Track seat availability and supplier seat restrictions. | P0 |
| BUS-006 | Support selection of multiple seats according to rules. | P0 |
| BUS-007 | Calculate seat-specific prices and fees. | P0 |
| BUS-008 | Retrieve valid boarding and dropping points. | P0 |
| BUS-009 | Hold selected seats if the supplier supports a hold operation. | P0 |
| BUS-010 | Expire or release holds according to provider rules. | P0 |
| BUS-011 | Detect conflicts when seats are sold during checkout. | P0 |
| BUS-012 | Revalidate seats and fare before payment/booking. | P0 |
| BUS-013 | Issue provider-confirmed tickets and booking references. | P0 |
| BUS-014 | Support cancellation, rescheduling, and refunds where eligible. | P0 |
| BUS-015 | Process live tracking/boarding updates where available. | P1 |
| BUS-016 | Support operator-specific boarding instructions. | P1 |

A Redis lock may reduce internal contention but cannot reserve seats in a supplier's inventory. Only the supplier's supported hold/booking operation may establish that reservation.

## 10. Hotels & Homestays

| ID | Requirement | Priority |
|---|---|---|
| HTL-001 | Search properties by location, landmark, or property ID. | P0 |
| HTL-002 | Validate check-in, check-out, room count, and occupancy. | P0 |
| HTL-003 | Aggregate property information and available room offers. | P0 |
| HTL-004 | Normalize amenities, property types, meal plans, and room attributes. | P0 |
| HTL-005 | Support price, location, rating, and amenity filters. | P0 |
| HTL-006 | Retrieve full property descriptions, images, and policies. | P0 |
| HTL-007 | Return room-specific rates, inventory, taxes, fees, and inclusions. | P0 |
| HTL-008 | Validate room capacity and guest-age restrictions. | P0 |
| HTL-009 | Compare refundable and non-refundable rate plans. | P0 |
| HTL-010 | Revalidate selected room rates before booking. | P0 |
| HTL-011 | Support multiple rooms when permitted. | P0 |
| HTL-012 | Support pay-now and supplier-supported pay-at-property reservations. | P0 |
| HTL-013 | Create supplier-backed reservations and store confirmation numbers. | P0 |
| HTL-014 | Support cancellations, modifications, and refund eligibility. | P0 |
| HTL-015 | Process reservation updates and hotel overbooking exceptions. | P0 |
| HTL-016 | Support homestay-specific inventory and house rules. | P1 |
| HTL-017 | Maintain licensed review and rating information with source attribution. | P1 |
| HTL-018 | Store property location and geospatial search information. | P1 |

## 11. Holiday Packages

| ID | Requirement | Priority |
|---|---|---|
| HOL-001 | Maintain destinations and package catalogs. | P0 |
| HOL-002 | Store package variants, durations, themes, and inclusions. | P0 |
| HOL-003 | Retrieve package availability and travel dates. | P0 |
| HOL-004 | Return day-wise itineraries and accommodation/transfer information. | P0 |
| HOL-005 | Calculate passenger-based package quotations. | P0 |
| HOL-006 | Store inclusions, exclusions, eligibility, and cancellation policies. | P0 |
| HOL-007 | Support supplier-confirmed package bookings. | P0 |
| HOL-008 | Support quotation/enquiry workflows for non-instant-bookable packages. | P0 |
| HOL-009 | Support permitted hotel, activity, and transfer customization. | P1 |
| HOL-010 | Recalculate quotations after itinerary changes. | P1 |
| HOL-011 | Manage booking/payment milestones when required by the package contract. | P1 |
| HOL-012 | Generate confirmed itineraries and vouchers. | P0 |
| HOL-013 | Support cancellation and component-specific refund rules. | P0 |
| HOL-014 | Support package fulfilment and vendor coordination status. | P1 |
| HOL-015 | Support multi-component booking orchestration when a package is assembled dynamically. | P1 |
| HOL-016 | Support saved and shareable itinerary drafts. | P1 |

A package built from multiple independently booked components must track each component's state and cancellation conditions individually.

## 12. Cabs and Transfers

| ID | Requirement | Priority |
|---|---|---|
| CAB-001 | Search airport, local, and outstation transport. | P1 |
| CAB-002 | Calculate routes, pickup times, and vehicle requirements. | P1 |
| CAB-003 | Retrieve applicable cab categories and quotes. | P1 |
| CAB-004 | Show fare inclusions and extra charges. | P1 |
| CAB-005 | Confirm supplier-backed transport reservations. | P1 |
| CAB-006 | Support cancellations and applicable refunds. | P1 |
| CAB-007 | Retrieve vehicle/driver assignment information where supported. | P1 |
| CAB-008 | Support live tracking where the supplier permits it. | P2 |

## 13. Unified Pricing and Quotation Engine

| ID | Requirement | Priority |
|---|---|---|
| PRC-001 | Normalize supplier prices into a consistent money representation. | P0 |
| PRC-002 | Store monetary values with currency and precision; no floating-point calculations. | P0 |
| PRC-003 | Create immutable priced quotation snapshots. | P0 |
| PRC-004 | Calculate platform service fees and applicable taxes. | P0 |
| PRC-005 | Apply supplier-supported and platform-supported discounts. | P0 |
| PRC-006 | Validate coupon eligibility, limits, dates, and customer restrictions. | P0 |
| PRC-007 | Support time-limited offers and expiration. | P0 |
| PRC-008 | Provide detailed price breakdowns to clients. | P0 |
| PRC-009 | Bind pricing to a supplier offer and quote version. | P0 |
| PRC-010 | Detect price changes and require customer reconfirmation. | P0 |
| PRC-011 | Prevent coupon redemption exceeding configured limits. | P0 |
| PRC-012 | Support FX quotations with explicit rate source and validity where required. | P1 |
| PRC-013 | Record pricing rules applied for auditability. | P0 |
| PRC-014 | Preserve tax/fee components required for invoices and reconciliation. | P0 |

Prices must be calculated authoritatively on the backend, never accepted from a frontend-submitted total.

## 14. Booking Orchestration

| ID | Requirement | Priority |
|---|---|---|
| BKG-001 | Create a booking intent from a valid quotation. | P0 |
| BKG-002 | Require authenticated ownership and an idempotency key. | P0 |
| BKG-003 | Validate quote expiry, price, traveller details, and supplier offer. | P0 |
| BKG-004 | Start a durable, versioned booking workflow. | P0 |
| BKG-005 | Support supplier-specific hold, reserve, confirm, and ticket operations. | P0 |
| BKG-006 | Preserve separate internal booking and external supplier statuses. | P0 |
| BKG-007 | Preserve separate payment authorization/capture statuses. | P0 |
| BKG-008 | Enforce valid state transitions. | P0 |
| BKG-009 | Use optimistic concurrency or database locking for conflicting updates. | P0 |
| BKG-010 | Persist workflow state before external side effects. | P0 |
| BKG-011 | Recover incomplete workflows after process restarts. | P0 |
| BKG-012 | Safely handle supplier unknown/timeout responses. | P0 |
| BKG-013 | Reconcile supplier status before potentially duplicate external requests. | P0 |
| BKG-014 | Support compensation such as hold release, reservation cancellation, or refund. | P0 |
| BKG-015 | Never issue unverified booking confirmations. | P0 |
| BKG-016 | Associate booking references, supplier references, passengers, and components. | P0 |
| BKG-017 | Emit durable lifecycle events. | P0 |
| BKG-018 | Support manual exception resolution with audit trail. | P0 |
| BKG-019 | Prevent bookings that exceed traveller or supplier eligibility restrictions. | P0 |
| BKG-020 | Provide status polling for pending bookings. | P0 |

## 15. Payments

| ID | Requirement | Priority |
|---|---|---|
| PAY-001 | Create payment intents linked to immutable booking quotes. | P0 |
| PAY-002 | Support gateway-enabled UPI, cards, net banking, and other permitted methods. | P0 |
| PAY-003 | Use gateway-hosted or tokenized collection of sensitive payment details. | P0 |
| PAY-004 | Validate payment intent amount, currency, and ownership. | P0 |
| PAY-005 | Enforce payment operation idempotency. | P0 |
| PAY-006 | Verify signed gateway webhooks and deduplicate their event IDs. | P0 |
| PAY-007 | Handle authorized, captured, failed, pending, and refunded states. | P0 |
| PAY-008 | Support authorization/capture workflows where available. | P0 |
| PAY-009 | Retrieve authoritative gateway status after ambiguous callbacks/timeouts. | P0 |
| PAY-010 | Support void, full refund, and partial refund where applicable. | P0 |
| PAY-011 | Persist gateway reference IDs and transaction audit information. | P0 |
| PAY-012 | Prevent accidental second charges. | P0 |
| PAY-013 | Support reconciliation of gateway settlements against internal records. | P0 |
| PAY-014 | Maintain immutable financial transaction records and adjustments. | P0 |
| PAY-015 | Record failed payments without marking bookings confirmed. | P0 |
| PAY-016 | Support payment provider service degradation and manual investigation. | P0 |

## 16. Cancellation, Modification and Refunds

| ID | Requirement | Priority |
|---|---|---|
| REF-001 | Retrieve applicable cancellation terms from the confirmed booking. | P0 |
| REF-002 | Calculate a refund estimate using current policy and booking status. | P0 |
| REF-003 | Require authorized customer confirmation for cancellation. | P0 |
| REF-004 | Submit cancellation through the correct supplier adapter. | P0 |
| REF-005 | Support cancellation-pending and unknown supplier outcomes. | P0 |
| REF-006 | Record actual cancellation charges and final refundable amount. | P0 |
| REF-007 | Trigger gateway refund only when eligible and according to the settlement model. | P0 |
| REF-008 | Track requested, processing, successful, failed, and manual-review states. | P0 |
| REF-009 | Prevent duplicate refunds for the same entitlement. | P0 |
| REF-010 | Support partial-passenger and partial-segment cancellation where permitted. | P1 |
| REF-011 | Support provider-specific rescheduling and rebooking where permitted. | P1 |
| REF-012 | Notify customers when refund status materially changes. | P0 |
| REF-013 | Reconcile refund records against supplier and payment provider outcomes. | P0 |
| REF-014 | Escalate unresolved exceptions for manual review. | P0 |

## 17. My Trips and Documents

| ID | Requirement | Priority |
|---|---|---|
| TRIP-001 | List bookings associated with the authenticated user. | P0 |
| TRIP-002 | Return itinerary, travellers, supplier status, and payment status. | P0 |
| TRIP-003 | Retrieve valid tickets, hotel vouchers, and invoices. | P0 |
| TRIP-004 | Generate documents from authoritative confirmed data. | P0 |
| TRIP-005 | Restrict document access to authorized users. | P0 |
| TRIP-006 | Provide controlled downloads using short-lived signed links or authorized streaming. | P0 |
| TRIP-007 | Surface cancellation and refund timelines. | P0 |
| TRIP-008 | Support searchable historical bookings. | P1 |
| TRIP-009 | Preserve applicable document records according to retention policies. | P0 |

## 18. Notifications

| ID | Requirement | Priority |
|---|---|---|
| NTF-001 | Send booking lifecycle notifications. | P0 |
| NTF-002 | Send payment and refund status notifications. | P0 |
| NTF-003 | Support email and SMS through configured providers. | P0 |
| NTF-004 | Support push and WhatsApp where approved and opted into. | P1 |
| NTF-005 | Use centrally managed versioned templates. | P0 |
| NTF-006 | Queue delivery asynchronously. | P0 |
| NTF-007 | Retry transient delivery failures within bounded limits. | P0 |
| NTF-008 | Track notification status and provider references. | P0 |
| NTF-009 | Deduplicate critical lifecycle notifications. | P0 |
| NTF-010 | Respect user preferences and mandatory transactional requirements. | P0 |
| NTF-011 | Mask sensitive data in notification content. | P0 |

## 19. Offers, Loyalty and Personalization

| ID | Requirement | Priority |
|---|---|---|
| OFF-001 | Configure promotion rules, validity, scope, and usage limits. | P0 |
| OFF-002 | Validate offers atomically at booking time. | P0 |
| OFF-003 | Support customer-specific eligibility. | P1 |
| OFF-004 | Maintain offer redemption history. | P0 |
| OFF-005 | Support partner coupons and referral incentives. | P1 |
| OFF-006 | Maintain loyalty point balances and redemption records if implemented. | P2 |
| OFF-007 | Support saved preferences and recommendations with proper consent. | P1 |
| OFF-008 | Expose real inventory scarcity signals only when supported by fresh authoritative data. | P1 |
| OFF-009 | Support fare-drop alerts using legitimate historical/current prices. | P1 |

## 20. Administration, Suppliers, Finance and Support

| ID | Requirement | Priority |
|---|---|---|
| OPS-001 | Maintain supplier configuration and capability matrices. | P0 |
| OPS-002 | Configure supplier credentials securely. | P0 |
| OPS-003 | Manage supplier routing priorities and feature flags. | P0 |
| OPS-004 | Monitor supplier latency, availability, errors, and quotas. | P0 |
| OPS-005 | Search bookings by authorized identifiers. | P0 |
| OPS-006 | Inspect booking timelines and non-sensitive integration traces. | P0 |
| OPS-007 | Support controlled manual reconciliation and exception resolution. | P0 |
| OPS-008 | Provide settlement and refund reports. | P0 |
| OPS-009 | Support supplier commissions, fees, and payable/receivable calculations according to commercial contracts. | P1 |
| OPS-010 | Enforce approval workflows for sensitive financial adjustments. | P0 |
| OPS-011 | Audit administrative actions and retain change history. | P0 |
| OPS-012 | Manage support cases and associate them with bookings. | P1 |
| OPS-013 | Provide operational dashboards and alerts. | P0 |
| OPS-014 | Maintain operational runbooks and escalation references. | P0 |
| OPS-015 | Support manual booking-status correction only through audited, constrained recovery workflows. | P0 |

## 21. Analytics and Reporting

| ID | Requirement | Priority |
|---|---|---|
| AN-001 | Publish privacy-safe search and booking lifecycle events. | P0 |
| AN-002 | Report search volume, latency, failure rates, and supplier coverage. | P0 |
| AN-003 | Report booking conversion and abandonment. | P1 |
| AN-004 | Report payment success and reconciliation exceptions. | P0 |
| AN-005 | Report cancellation/refund performance. | P0 |
| AN-006 | Provide supplier-level operational and commercial dashboards. | P1 |
| AN-007 | Support approved financial reconciliation exports. | P0 |
| AN-008 | Support historical aggregates through a separate analytics workload. | P1 |
| AN-009 | Prevent analytics queries from degrading transactional database performance. | P0 |

---

# PART C — NON-FUNCTIONAL REQUIREMENTS

## 22. NFR Baseline and Service-Level Targets

All numerical targets below are **proposed acceptance targets** and must be confirmed against business demand, supplier contractual SLAs, load tests, and cost constraints.

| ID | Category | Proposed target |
|---|---|---|
| NFR-001 | Availability | Critical owned booking APIs: 99.95% monthly availability target |
| NFR-002 | Search availability | 99.9% monthly platform-level availability target, excluding transparent external-provider outages |
| NFR-003 | Internal read latency | p95 ≤ 200 ms for local database/cache-backed reads under defined load |
| NFR-004 | Search aggregation latency | p95 ≤ 3 s for typical searches, with supplier budget and partial results; measured separately from client rendering |
| NFR-005 | Booking-command acceptance | p95 ≤ 500 ms for durable acceptance where confirmation continues asynchronously |
| NFR-006 | Booking finalization | Supplier-dependent; enforce operation deadlines and status visibility rather than an artificial universal timeout |
| NFR-007 | Scalability | Demonstrate 5× normal peak load without data loss or financial correctness violations |
| NFR-008 | Initial capacity planning | Benchmark an illustrative 500 search requests/sec and 50 booking/payment command requests/sec; revise from measured traffic |
| NFR-009 | Concurrent transactions | Maintain correctness for conflicting booking/payment requests under concurrent load |
| NFR-010 | Idempotency | Repeated mutation requests with the same valid idempotency key must not create additional side effects |
| NFR-011 | Search degradation | Supplier timeouts must not indefinitely block other supplier results |
| NFR-012 | Data integrity | No unauthorized state transitions, duplicate financial postings, or untraceable confirmed bookings |
| NFR-013 | Backup | Automated backup and restore verification for critical persistent stores |
| NFR-014 | Disaster recovery | Proposed business-approved RTO ≤ 60 minutes and RPO ≤ 5 minutes for critical booking records |
| NFR-015 | Security | Strong authentication, authorization, encryption, secrets management, and auditable privileged operations |
| NFR-016 | Privacy | Data minimization, masking, consent, retention, and deletion controls |
| NFR-017 | Observability | Request traces, structured logs, business metrics, and actionable alerts |
| NFR-018 | Compatibility | Versioned APIs and backward-compatible event/schema evolution |
| NFR-019 | Deployability | Automated deployment, rollback, configuration validation, and zero-avoidable-downtime migrations |
| NFR-020 | Testability | Automated unit, integration, contract, concurrency, resilience, security, and end-to-end tests |
| NFR-021 | Maintainability | Clear module ownership, dependency rules, and architectural checks |
| NFR-022 | Cost control | Budgets, resource tagging, usage visibility, and per-service cost measurement |
| NFR-023 | Auditability | Trace financial/booking actions to an actor, request, state transition, and external reference |
| NFR-024 | Accessibility support | API errors and localization metadata sufficient for accessible clients |
| NFR-025 | Localization | ISO currencies, precise monetary calculations, IANA timezones, UTC timestamps, and supported locale metadata |
| NFR-026 | Data retention | Category-specific policies for customer, payment, ticket, and audit records, subject to legal review |
| NFR-027 | Operational recovery | Documented runbooks and tested recovery procedures for high-severity scenarios |
| NFR-028 | Financial reconciliation | Detect missing, mismatched, duplicate, or delayed settlement records; unresolved differences must surface to operations |
| NFR-029 | Cache correctness | Bookings/payments must not rely on non-authoritative cached state |
| NFR-030 | Supplier governance | Every live provider requires commercial authorization, security review, tested credentials, and sandbox validation |

### Reliability calculations and cautions

- A 99.95% monthly availability target permits approximately 22 minutes of downtime in a 30-day month.
- Search uptime and successful inventory availability are different metrics.
- A provider outage cannot be hidden by returning an empty result set as though no inventory exists.
- RTO and RPO refer to disaster recovery; Multi-AZ alone does not establish cross-region disaster recovery.
- Latency SLOs must distinguish platform time from supplier processing time.
- Published targets do not imply that they have already been achieved.

## 23. Security and Compliance Requirements

### Identity and access

- OIDC token validation at the application boundary.
- Role-based and resource-based authorization.
- Least-privilege IAM permissions.
- Separate credentials for production, staging, and development.
- Protected administrative endpoints with MFA and approval controls.

### Application security

- Strict input validation, length limits, and payload limits.
- SQL injection prevention through parameterized database access.
- Authentication and per-resource authorization on sensitive APIs.
- Per-user and per-IP rate limiting.
- Webhook signature verification and replay defense.
- Server-side request forgery controls in external integrations.
- Restrictive CORS and security headers.
- Dependency and container vulnerability scanning.
- Audit trails for booking and financial state changes.

### Data protection

- TLS for all client and provider communications.
- Encryption at rest using managed keys.
- KMS-managed encryption where required.
- Secrets Manager for credentials.
- No raw card data storage in the travel platform.
- Minimized passport/identity data handling, with controlled access and lifecycle.
- Sensitive-data masking in logs, traces, error responses, and analytics.
- Restricted support access to customer information.

### Regulatory review

Assess the applicability of:

- Indian personal data protection laws and implementing requirements.
- PCI DSS responsibilities for the selected payment model.
- Payment provider, RBI, and UPI ecosystem rules.
- Railway ticketing authorization and applicable IRCTC policies.
- Supplier-specific contractual and data-use requirements.
- Relevant tax invoicing, accounting, retention, and consumer-protection obligations.
- International travel-specific personal data and payment obligations, where applicable.

Do not mark any compliance certification complete without an actual legal, security, and provider review.

---

# PART D — DOMAIN AND ARCHITECTURE DESIGN

## 24. Bounded Contexts

| Bounded context | Responsibilities | Ownership |
|---|---|---|
| Identity & Customer | Authentication integration, customer profiles, permissions | Customer domain |
| Catalog & Location | Reference locations, property/package content | Catalog domain |
| Supplier Connectivity | Protocol translation and supplier capabilities | Integration domain |
| Search & Discovery | Search fan-out, normalization, ranking, caching | Discovery domain |
| Pricing & Offers | Quotes, fees, discounts, policy snapshots | Commercial domain |
| Booking | Booking state machine, traveller data, orchestration | Booking domain |
| Payments | Payment intents, gateway status, captures, refunds | Financial domain |
| Financial Ledger & Reconciliation | Money movement records, balancing, settlement differences | Financial domain |
| Trip Management | Booking views, documents, changes | Post-booking domain |
| Notifications | Transactional messages, templates, delivery | Communications domain |
| Operations | Recovery actions, supplier configuration, support | Operations domain |
| Analytics | Aggregates and operational/product reporting | Analytics domain |

### Architecture decision

Do not begin with one microservice per endpoint or entity.

Recommended first deployable boundaries:

1. **Travel API / Core Application:** Customer, catalog, quote, booking command, trips, and policies, separated internally by modules.
2. **Search Aggregator:** Independent scaling and supplier concurrency controls.
3. **Supplier Connectivity Workers:** Provider isolation, outbound rate limits, and asynchronous confirmations.
4. **Payment and Reconciliation Processing:** Isolated security/financial processing boundaries.
5. **Event and Notification Workers:** Background delivery and retries.

Initially, these may share a deployment where isolation is not required. Financial boundaries must still preserve authorization and transactional ownership.

Split modules into separately deployed services only when measurable scaling, security, ownership, or failure-isolation needs justify the added cost.

## 25. Hexagonal/Clean Architecture

Each domain module follows:

**Inbound Adapter → Application Use Case → Domain Model → Outbound Port → Infrastructure Adapter**

Suggested dependencies:

- Domain model depends on no Spring or AWS infrastructure.
- Application layer coordinates use cases and transactions.
- Persistence adapters implement repositories.
- Supplier adapters map between canonical domain models and external contracts.
- API adapters validate requests and translate responses/errors.
- No module directly manipulates another module's private persistence tables.

Suggested package structure:

- `domain/model`
- `domain/event`
- `domain/service`
- `application/command`
- `application/query`
- `application/port/in`
- `application/port/out`
- `adapter/in/web`
- `adapter/in/messaging`
- `adapter/out/persistence`
- `adapter/out/supplier`
- `adapter/out/payment`
- `config`

Prefer domain-based modules over a global `controller/service/repository` structure.

## 26. Canonical Supplier Interface

The integration layer shall expose a consistent internal capability model.

### Supplier operations

- `search(request)`
- `getOfferDetails(offerRef)`
- `reprice(offerRef, selection)`
- `hold(offerRef, passengers)` where supported
- `createReservation(request)`
- `confirmOrTicket(reservationRef)` where supported
- `getReservationStatus(reservationRef)`
- `cancel(reservationRef, request)`
- `getRefundEligibility(reservationRef)`
- `getRefundStatus(refundRef)`

Not every supplier supports every operation.

The capability matrix must declare:

- Supported product category.
- Geographic and currency coverage.
- Available search and booking operations.
- Whether inventory holds exist.
- Whether payment precedes or follows reservation.
- Ticketing versus reservation distinction.
- Cancellation and modification support.
- Whether status queries are supported.
- Provider idempotency capabilities.
- Provider quotas, timeout expectations, and retry conditions.
- Provider-specific authentication and permitted data storage.

### Anti-corruption layer

Supplier-specific statuses such as "ticketed", "confirmed", "waitlisted", "reserved", "on request", or "pending fulfilment" shall map to canonical statuses without discarding their original meaning.

Store the raw provider status/reference in a controlled record for investigation; do not expose unfiltered provider payloads containing PII or secrets.

---

# PART E — DATA ARCHITECTURE

## 27. Primary Database Design

Use PostgreSQL as the transactional system of record.

### Core entities

| Entity / table | Essential fields |
|---|---|
| customers | id, external_identity_id, status, created_at |
| customer_profiles | customer_id, contact details, consent metadata |
| travellers | id, customer_id, encrypted identity fields |
| suppliers | id, type, capability metadata, status |
| supplier_mappings | supplier_id, external_code, canonical_id |
| locations | id, type, code, name, timezone, coordinates |
| offers | id, supplier_id, supplier_offer_ref, expiry, version |
| quotations | id, customer_id, type, currency, total, expires_at, status |
| quote_items | quote_id, product_type, offer_ref, price components, policies |
| bookings | id, customer_id, quote_id, status, version, created_at |
| booking_items | id, booking_id, supplier_id, product_type, status |
| booking_travellers | booking_item_id, traveller_id, supplier passenger reference |
| supplier_reservations | id, booking_item_id, external_ref, raw_status, mapped_status |
| workflow_instances | id, booking_id, step, state, deadline, version |
| workflow_actions | id, workflow_id, operation_key, status, attempts |
| payment_intents | id, booking_id, amount, currency, status, provider_ref |
| payment_transactions | id, payment_intent_id, gateway_txn_ref, status, amount |
| refunds | id, booking_id, payment_txn_id, amount, status |
| refund_items | refund_id, booking_item_id, entitlement, supplier_ref |
| financial_entries | id, transaction_id, account_code, debit, credit, currency |
| coupons | id, rules, usage limits, validity |
| coupon_redemptions | id, coupon_id, customer_id, booking_id |
| idempotency_records | actor_id, operation, key, request_hash, result_ref, expiry |
| outbox_events | id, aggregate_id, version, event_type, payload, publish_state |
| processed_events | consumer_id, event_id, processed_at |
| notifications | id, booking_id, template_id, status, provider_ref |
| documents | id, booking_id, storage_key, type, status |
| audit_events | id, actor, action, resource, timestamp, outcome |
| reconciliation_cases | id, related_ref, mismatch_type, state, resolution |

These are logical tables; final normalization and indexes follow actual domain transaction needs.

## 28. Database Integrity Requirements

- UUID or another globally unique identifier strategy.
- Unique constraints on applicable external transaction references.
- Unique idempotency scope `(actor_id, operation, key)`.
- Optimistic versioning for concurrent booking state updates.
- Explicit foreign keys and check constraints.
- Transactional creation of booking state and outbox events.
- No automatic cascade deletion of financial audit data.
- Immutable financial postings with corrective entries.
- Indexed ownership, status, supplier reference, and creation time.
- Partition very large event/audit tables only when operationally justified.
- Flyway or equivalent migrations with backward-compatible rollout.
- Separate read-models/analytics stores for heavy reporting.
- Connection-pool sizing based on actual database capacity.

### Financial ledger

For platform-controlled money movements, use double-entry accounting records where appropriate.

Each financial transaction must balance in its currency. Never change an existing posted entry to conceal a correction; create linked reversing or adjustment entries.

External supplier settlement records and payment gateway settlements must reconcile to recorded platform obligations.

### Money and time

- Java `BigDecimal` for money, with explicit currency, rounding, and minor-unit rules.
- PostgreSQL `NUMERIC` or integer minor units as suitable.
- Java `Instant`/`OffsetDateTime` for event timestamps.
- Local itinerary times accompanied by the relevant IANA timezone.
- No monetary calculations using `double` or `float`.

---

# PART F — BOOKING, PAYMENT AND CONSISTENCY

## 29. Booking State Machine

A representative booking lifecycle:

| State | Meaning |
|---|---|
| CREATED | Booking intent accepted and persisted |
| VALIDATING | Quote/traveller/supplier checks running |
| AWAITING_PAYMENT | Waiting for user/payment provider |
| PAYMENT_PENDING | Payment outcome not yet final |
| PAYMENT_CONFIRMED | Payment authorization/capture condition satisfied |
| RESERVATION_PENDING | Supplier reservation operation in progress |
| CONFIRMATION_PENDING | Supplier result is unknown or incomplete |
| CONFIRMED | Supplier-required booking/ticket confirmation verified |
| FAILED | Booking cannot complete and recovery/compensation is tracked |
| CANCELLATION_PENDING | Supplier cancellation in progress |
| CANCELLED | Supplier cancellation verified |
| REFUND_PENDING | A refund is in progress |
| PARTIALLY_REFUNDED | Some entitled funds have been refunded |
| REFUNDED | Required refunds completed |

A single flat enum is insufficient for every supplier and multi-item booking.

Maintain separate but coordinated state machines for:

- Booking aggregate.
- Each supplier reservation / itinerary item.
- Payment transaction.
- Refund transaction.
- Orchestration workflow.

A booking may simultaneously have a supplier confirmation pending and a captured payment. Never collapse these into a misleading success state.

## 30. End-to-End Booking Workflow

### Step 1 — Search

1. Client submits normalized search criteria.
2. Search service validates request.
3. Eligible supplier adapters are selected.
4. Calls execute concurrently under bounded provider budgets.
5. Results are normalized, filtered, and returned.
6. Result references include supplier identity and freshness.

### Step 2 — Reprice

1. Client selects an offer.
2. Backend resolves the supplier offer reference.
3. Backend obtains current availability and pricing.
4. Platform fees/taxes/discounts are calculated.
5. An immutable quotation is persisted with expiry.
6. Price/policy changes are presented to the user.

### Step 3 — Create booking intent

1. Verify user ownership and authorization.
2. Validate traveller details and quote.
3. Validate the idempotency key.
4. Create booking intent and workflow record in one database transaction.
5. Write a corresponding outbox event.
6. Return a stable booking ID.

### Step 4 — Supplier hold/payment decision

The workflow selects a supplier-specific path:

- **Hold-first:** Hold inventory, initiate authorized payment, then confirm/ticket.
- **Payment-first:** Process payment as required, then place supplier booking; compensate if unsuccessful.
- **Reservation-without-immediate-charge:** Create a qualifying reservation and follow supplier payment terms.
- **Enquiry-based:** Store quote/enquiry without claiming confirmed inventory.

There is no universally correct payment/hold order across flights, hotels, buses, and railways.

### Step 5 — Payment

1. Create payment intent idempotently.
2. Initialize payment using the gateway.
3. Receive verified payment result or signed callback.
4. Record authoritative payment state.
5. Resume the workflow safely.
6. Query gateway status when the result is unknown.

### Step 6 — Supplier confirmation

1. Invoke the permitted supplier booking/ticketing operation.
2. Use supplier idempotency/reference mechanisms where supported.
3. Persist external identifiers.
4. Query supplier status on ambiguous timeouts.
5. Mark the relevant booking item confirmed only when verified.
6. Trigger compensating recovery when confirmation fails.

### Step 7 — Completion

1. Persist final status.
2. Write durable lifecycle event.
3. Update My Trips read models.
4. Generate valid travel documents.
5. Send notifications asynchronously.
6. Reconcile payment and supplier obligations.

### Mandatory exception paths

- Payment authorized, supplier fails: void or refund according to gateway/supplier capability.
- Payment captured, supplier unknown: retain pending state and reconcile; never blindly retry a non-idempotent supplier booking.
- Supplier confirmed, local response lost: recover using supplier reservation reference and status lookup.
- Supplier confirmed, document generation fails: keep booking confirmed; retry document generation.
- Multi-component package partially confirmed: compensate eligible components or escalate for supported fulfilment.
- Cancellation accepted, refund delayed: track separately; do not declare refunded until verified.

## 31. Idempotency Contract

For financial or booking mutations:

- Require an `Idempotency-Key` header.
- Scope it to customer/account, operation, and request identity.
- Persist a deterministic hash of the normalized request.
- Return the original operation/result for valid same-key retries.
- Reject reuse of the same key with materially different payload.
- Ensure concurrent first requests cannot both execute the same side effect.
- Retain records for a documented window exceeding relevant retry/reconciliation periods.
- Use provider-side idempotency where supported.
- Resolve unknown external outcomes before generating any new external attempt.

At-least-once message delivery and client retries must not cause duplicate business operations.

## 32. Transactional Outbox and Event Processing

Database state changes and event creation must be committed atomically.

### Producer behavior

1. Update booking and workflow state.
2. Insert the outbox event in the same PostgreSQL transaction.
3. Commit.
4. An asynchronous publisher delivers the event.
5. Mark the outbox record published only after broker acknowledgement.

### Consumer behavior

1. Validate event envelope and schema version.
2. Detect previously processed event IDs.
3. Perform idempotent processing.
4. Commit business changes and processing marker transactionally where possible.
5. Acknowledge the broker message.
6. Retry only transient failures.
7. Quarantine non-recoverable messages in a DLQ with investigation tooling.

### Event envelope

Every business event should contain:

- `eventId`
- `eventType`
- `schemaVersion`
- `aggregateId`
- `aggregateVersion`
- `occurredAt`
- `correlationId`
- `causationId`
- `producer`
- `payload`

Suggested events:

- `BookingCreated`
- `QuoteRevalidated`
- `InventoryHoldCreated`
- `PaymentAuthorized`
- `PaymentCaptured`
- `PaymentFailed`
- `SupplierReservationConfirmed`
- `SupplierReservationUnknown`
- `BookingConfirmed`
- `BookingFailed`
- `CancellationRequested`
- `SupplierCancellationConfirmed`
- `RefundInitiated`
- `RefundCompleted`
- `BookingReconciliationRequired`

Prefer ordering by booking aggregate ID where ordering matters. Version checks must guard against stale or out-of-order consumer updates.

## 33. Resilience Rules

- Define an end-to-end request deadline.
- Allocate explicit per-supplier timeout budgets.
- Retry only classified transient failures.
- Use bounded exponential backoff with jitter.
- Honor supplier rate limits and `Retry-After` where applicable.
- Use circuit breakers for unstable integrations.
- Apply bulkheads and concurrency limits by supplier.
- Use separate worker pools for potentially slow external I/O.
- Do not retry payment captures or supplier booking calls indiscriminately.
- Use durable scheduled recovery for long-running transactions.
- Provide manual investigation queues for unresolved uncertainty.
- Reject or defer work before queues and databases are overwhelmed.

Do not use a long database transaction while waiting for supplier network calls.

---

# PART G — API AND EVENT CONTRACT DESIGN

## 34. REST API Catalog

All paths are illustrative and should be published through OpenAPI.

### Customer and identity

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/me` | Current customer profile |
| PATCH | `/api/v1/me` | Update profile |
| GET | `/api/v1/me/travellers` | List saved travellers |
| POST | `/api/v1/me/travellers` | Add traveller |
| DELETE | `/api/v1/me/travellers/{id}` | Remove saved traveller |

### Master data

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/locations` | Search location directory |
| GET | `/api/v1/locations/{id}` | Retrieve location |
| GET | `/api/v1/destinations` | Destination catalogs |

### Flights

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/flights/search` | Search flights |
| GET | `/api/v1/flights/offers/{id}` | Retrieve offer |
| POST | `/api/v1/flights/offers/{id}/reprice` | Revalidate offer |
| GET | `/api/v1/flights/fare-calendar` | Optional fare calendar |

### Trains

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/trains/search` | Search trains |
| GET | `/api/v1/trains/{trainId}/availability` | Retrieve class availability |
| GET | `/api/v1/trains/pnr/{pnr}` | Authorized PNR enquiry |
| GET | `/api/v1/trains/{trainId}/status` | Authorized running status |

### Buses

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/buses/search` | Search buses |
| GET | `/api/v1/buses/trips/{id}` | Trip details |
| GET | `/api/v1/buses/trips/{id}/seats` | Seat layout |
| POST | `/api/v1/buses/trips/{id}/holds` | Supplier-supported seat hold |

### Hotels

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/hotels/search` | Search properties |
| GET | `/api/v1/hotels/{id}` | Property detail |
| GET | `/api/v1/hotels/{id}/rooms` | Room offers |
| POST | `/api/v1/hotels/offers/{id}/reprice` | Revalidate selected rate |

### Holidays

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/holidays/packages` | Package discovery |
| GET | `/api/v1/holidays/packages/{id}` | Package details |
| POST | `/api/v1/holidays/quotes` | Create package quote |
| POST | `/api/v1/holidays/enquiries` | Submit enquiry |
| PATCH | `/api/v1/holidays/quotes/{id}` | Modify eligible quote |

### Booking and payments

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/quotes` | Create common quotation |
| GET | `/api/v1/quotes/{id}` | Read quotation |
| POST | `/api/v1/bookings` | Create idempotent booking intent |
| GET | `/api/v1/bookings/{id}` | Read booking status |
| POST | `/api/v1/bookings/{id}/payment-intents` | Start payment |
| GET | `/api/v1/payments/{id}` | Retrieve payment status |
| POST | `/api/v1/bookings/{id}/cancellation-quotes` | Quote cancellation |
| POST | `/api/v1/bookings/{id}/cancellations` | Request cancellation |
| GET | `/api/v1/bookings/{id}/refunds` | Retrieve refund state |
| GET | `/api/v1/me/bookings` | List customer's bookings |
| GET | `/api/v1/bookings/{id}/documents` | Retrieve authorized documents |
| POST | `/api/v1/webhooks/payments/{provider}` | Verified payment provider callback |

### Contract conventions

- Use `application/problem+json` for standardized errors.
- Use documented machine-readable error codes.
- Include trace/correlation IDs.
- Use `202 Accepted` for durably accepted but incomplete asynchronous work.
- Use `409 Conflict` for booking-state conflicts and relevant quote/seat contention.
- Use `422 Unprocessable Content` for semantically invalid submitted selections where appropriate.
- Use `429 Too Many Requests` for rate limiting.
- Use `503 Service Unavailable` for unavailable dependencies when a meaningful response cannot be produced.
- Never return `200 OK` with a fabricated success payload for an unsuccessful supplier operation.

Version APIs and keep compatible fields optional/additive when possible. Apply explicit deprecation and migration processes for breaking changes.

---

# PART H — AWS CLOUD ARCHITECTURE

## 35. AWS Landing Zone and Networking

### AWS accounts

Use separate AWS accounts for:

- Shared security/logging.
- Development.
- Test/staging.
- Production.
- Dedicated security or shared infrastructure services where warranted.

Establish AWS Organizations guardrails and centralized policies.

### Networking

- Route 53 for DNS.
- CloudFront for suitable public delivery and edge caching.
- AWS WAF for application-layer protection.
- ALB or API Gateway for ingress according to chosen service architecture.
- Private application subnets.
- Private database and cache subnets.
- Controlled egress for supplier integrations.
- VPC endpoints where useful.
- Multi-AZ deployment for critical production services.
- Network segmentation and least-privilege security groups.
- Avoid exposing databases and internal worker endpoints directly to the internet.

## 36. AWS Service Selection

| Workload | Suggested AWS service | Why |
|---|---|---|
| Domain APIs | ECS/Fargate | Managed container deployment with limited cluster operations |
| Kubernetes workloads | EKS | Use when Kubernetes portability/customization is an explicit requirement |
| Transactional data | RDS PostgreSQL / Aurora PostgreSQL | ACID consistency and relational integrity |
| Cache | ElastiCache Redis | Fast ephemeral read caching and coordination |
| Async tasks | SQS | Durable queues and independently scalable workers |
| Business event routing | EventBridge | Event routing with managed integrations |
| Kafka workloads | Amazon MSK | Needed for mature Kafka/event streaming requirements |
| Long-running workflow | Application workflow engine or AWS Step Functions | Durable step execution and recovery |
| Object storage | S3 | Documents and controlled assets |
| Identity | Cognito / external IdP | Managed authentication |
| Secrets | Secrets Manager | Centralized secret lifecycle |
| Encryption | KMS | Key management |
| Observability | CloudWatch + OpenTelemetry | Metrics, logs, traces, alarms |
| Threat monitoring | GuardDuty / Security Hub / CloudTrail | Security findings and auditing |
| CI/CD | CodePipeline / GitHub Actions | Automated builds and releases |
| Infrastructure | Terraform | Declarative provisioning |

### Selection guidance

Prefer SQS/EventBridge for straightforward booking workflows, notifications, and background jobs.

Use Kafka/MSK when there is a demonstrated need for event replay, sustained multi-consumer event streaming, partitioned processing, or established organizational Kafka operations.

Do not deploy both MSK and multiple unrelated queue systems by default. Each messaging platform creates operational and consistency responsibilities.

## 37. Deployment Topology

Suggested production footprint:

- At least two Availability Zones.
- Stateless APIs with multiple running tasks.
- Horizontal scaling for API and search workers.
- Database Multi-AZ configuration.
- High-availability cache configuration where needed.
- Durable queues and dead-letter queues.
- Separate background worker scaling.
- S3 versioning and appropriate document retention.
- Centralized secrets and encryption.
- Automated database backups with restore testing.
- Approved cross-region disaster recovery strategy for critical data.

An additional region should be introduced only with a justified RTO/RPO requirement and a tested failover/reconciliation design.

---

# PART I — AWS WELL-ARCHITECTED SIX PILLARS

## 38. Operational Excellence

**Objective:** Run and improve the application safely.

Required practices:

- Infrastructure as Code.
- Automated CI/CD.
- Production change management.
- Canary or rolling deployments with health checks.
- Feature flags and emergency rollback.
- Standardized structured logging and tracing.
- Service ownership and on-call rotations.
- Operational metrics and SLO/error-budget reviews.
- Runbooks for supplier, payment, database, and messaging failures.
- Post-incident reviews with corrective actions.

**Evidence to collect**

- Successful pipeline runs.
- Deployment and rollback histories.
- Dashboards and alert tests.
- Completed incident drills.
- Updated operational runbooks.

## 39. Security

**Objective:** Protect customer data, financial operations, and systems.

Required practices:

- Least-privilege IAM.
- Multi-account security boundaries.
- Encryption in transit and at rest.
- Centralized secrets and key management.
- Threat modeling for booking/payment workflows.
- WAF, rate limits, authentication, and authorization.
- Vulnerability scans, dependency checks, and code security analysis.
- Secure event/webhook processing.
- Data retention and privacy enforcement.
- Security incident detection and response.

**Evidence to collect**

- IAM reviews.
- Threat-model results.
- Security testing.
- Vulnerability reports and remediation.
- Access and audit logs.
- Backup encryption verification.

## 40. Reliability

**Objective:** Maintain correct service behavior despite failures.

Required practices:

- Multi-AZ architecture.
- Automated recovery and scaling.
- Durable booking state.
- Idempotent commands and consumers.
- Transactional outbox.
- Supplier-specific timeouts, retries, and circuit breakers.
- Payment/booking reconciliation.
- Chaos and failure-injection tests.
- Restore testing and disaster recovery exercises.
- Defined RTO, RPO, and SLOs.

**Evidence to collect**

- Load and failover reports.
- Database restore results.
- Supplier timeout simulations.
- Verified duplicate-payment prevention tests.
- Disaster recovery exercise reports.

## 41. Performance Efficiency

**Objective:** Meet latency and throughput targets with appropriately sized resources.

Required practices:

- Separate read-intensive search from transactional workloads.
- Bounded parallel supplier calls.
- Cache appropriate reference and search data.
- Optimize database queries and indexing.
- Tune connection pools and thread concurrency.
- Use asynchronous processing for slow workflows.
- Measure p50/p95/p99 latencies.
- Perform regular load and capacity tests.
- Use managed services and compute types that match workload characteristics.

**Evidence to collect**

- Load-test reports.
- Query plans.
- Capacity benchmarks.
- p95/p99 dashboards.
- Supplier request-budget metrics.

## 42. Cost Optimization

**Objective:** Meet business SLOs at a sustainable operating cost.

Required practices:

- Resource tagging by environment and product.
- AWS Budgets and cost alerts.
- Rightsize services from actual utilization.
- Auto-scale search and workers independently.
- Control paid supplier requests through caching and budgets.
- Apply storage lifecycle policies.
- Avoid idle overprovisioned clusters and redundant platforms.
- Evaluate compute commitments after steady-state patterns are known.
- Measure cloud cost per search and per confirmed booking.

**Evidence to collect**

- Monthly cost reports.
- Cost-allocation dashboards.
- Idle resource reports.
- Cost-per-transaction trends.
- Optimization decisions linked to SLO impact.

## 43. Sustainability

**Objective:** Reduce unnecessary compute, storage, and network consumption.

Required practices:

- Use autoscaling and appropriately sized infrastructure.
- Eliminate wasteful high-frequency polling.
- Deduplicate and consolidate unnecessary supplier requests.
- Cache suitable data to reduce redundant computation.
- Use event-driven workloads where they lower idle resource consumption.
- Apply data retention/lifecycle policies.
- Efficiently compress and transfer appropriate payloads.
- Review region, compute, and storage efficiency.
- Retire unused environments and stale resources.

**Evidence to collect**

- Compute utilization trends.
- Avoided redundant provider calls.
- Storage lifecycle compliance.
- Idle-resource reduction.
- Capacity and efficiency improvements.

---

# PART J — IMPLEMENTATION ENGINEERING STANDARDS

## 44. Java and Spring Boot Standards

- Java 21 language and runtime baseline.
- Supported Spring Boot release with pinned dependency versions.
- Constructor injection.
- DTO validation at request boundaries.
- Domain objects independent of Spring infrastructure.
- Typed provider exception mapping.
- `@Transactional` only around local data operations.
- Separate external network calls from database transactions.
- Explicit optimistic locking and state-transition guards.
- Centralized RFC 9457-style problem responses where supported by the stack.
- Explicit outbound HTTP timeouts and connection management.
- Resilience4j or equivalent where required.
- OpenAPI for HTTP APIs.
- Flyway for schema migrations.
- Testcontainers for integration testing.
- No sensitive details in exception messages or logs.

### Dependency management

Use the Spring Boot dependency BOM and approved platform dependency set.

Do not select library versions independently without checking compatibility. Pin dependencies, document upgrade policy, and run regression tests during upgrades.

### Virtual threads

Java virtual threads may simplify supplier fan-out, but they do not eliminate:

- API rate limits.
- Connection pool limits.
- Database capacity constraints.
- Memory pressure.
- Supplier concurrency restrictions.

Use explicit semaphores, bulkheads, and request budgets even with virtual threads.

## 45. Repository Structure

Suggested initial structure:

- `travel-platform/`
  - `apps/api/`
  - `apps/search-worker/`
  - `apps/booking-worker/`
  - `apps/reconciliation-worker/`
  - `modules/customer/`
  - `modules/catalog/`
  - `modules/supplier/`
  - `modules/search/`
  - `modules/pricing/`
  - `modules/booking/`
  - `modules/payment/`
  - `modules/refund/`
  - `modules/trips/`
  - `modules/notification/`
  - `modules/operations/`
  - `platform/security/`
  - `platform/observability/`
  - `contracts/openapi/`
  - `contracts/events/`
  - `infrastructure/terraform/`
  - `deploy/`
  - `tests/integration/`
  - `tests/e2e/`
  - `tests/performance/`
  - `docs/adr/`
  - `docs/runbooks/`

The `platform` modules must remain thin. They must not become a shared dumping ground for domain entities and business logic.

Use architecture tests to prevent invalid cross-module dependencies.

## 46. Testing Strategy

| Test type | Required coverage |
|---|---|
| Unit | Domain validation, pricing rules, state transitions |
| Application integration | Use-case orchestration and database transactions |
| API contract | OpenAPI compatibility and error contracts |
| Supplier contract | Provider mapping and negative responses using sandbox/stubs |
| Database | Migrations, uniqueness, optimistic locking, transaction rollback |
| Messaging | Duplicate delivery, replay, ordering, DLQ, schema versions |
| Security | Authorization, token handling, IDOR, webhook replay |
| Concurrency | Duplicate requests, competing seat selection, refund races |
| Resilience | Timeouts, circuit breakers, gateway outage, restart recovery |
| Financial | Duplicate charges, mismatched settlements, partial refunds |
| End-to-end | Search → quote → booking → payment → confirmation → cancellation |
| Performance | Peak load, burst tests, soak tests, dependency degradation |
| Recovery | Backup restore, AZ failure, worker crash, pending transaction reconciliation |

### Mandatory regression scenarios

1. Same booking idempotency key submitted concurrently.
2. Different keys attempting to book the same locally controlled capacity.
3. Payment callback delivered multiple times and out of order.
4. Gateway timeout followed by successful payment status.
5. Supplier timeout after reservation creation.
6. Duplicate Kafka/SQS event.
7. Orchestrator crash after provider response but before local completion.
8. Booking confirmed with notification delivery failure.
9. Cancellation accepted but refund pending.
10. Partial refund followed by duplicate refund request.
11. Quote expires during checkout.
12. Supplier price increases between search and booking.
13. Cached offer exists but supplier inventory is exhausted.
14. Database failover during booking processing.
15. Consumer processes an older aggregate event after a newer one.
16. Unauthorized customer attempts to retrieve another customer's ticket.
17. A circuit breaker opens during a supplier outage.
18. No inventory available from a subset of providers.
19. Schema evolves while old consumers are still deployed.
20. Reconciliation detects payment captured without confirmed booking.

---

# PART K — DEVELOPMENT PHASES AND DELIVERY PLAN

## 47. Phase 0 — Discovery and Commercial Integration Readiness

**Goal:** Establish feasibility before implementing real ticketing.

Activities:

- Finalize product scope and vertical priorities.
- Identify licensed suppliers.
- Review API/SDK contracts, rate limits, test environments, and commercial terms.
- Validate railway ticketing eligibility.
- Identify supported payment gateway models.
- Define booking-state and supplier capability matrices.
- Establish initial traffic assumptions and NFRs.
- Create threat model and architecture decision records.

**Deliverables**

- Approved architecture baseline.
- Supplier integration matrix.
- API capability gaps.
- Risk register.
- Initial SLO targets.
- Prioritized product backlog.

**Exit gate:** All P0 supplier-dependent features have an approved integration path or are explicitly labeled blocked/demo-only.

## 48. Phase 1 — Platform Foundation

**Goal:** Establish secure, observable backend infrastructure.

Activities:

- Initialize the Java/Spring multi-module project.
- Configure CI/CD and quality gates.
- Provision AWS environments through Terraform.
- Establish authentication integration.
- Configure database, cache, messaging, and secrets.
- Implement tracing and standard error contracts.
- Add database migration infrastructure.
- Establish API and event contract tooling.

**Deliverables**

- Deployable backend foundation.
- Health/readiness endpoints.
- Authentication middleware.
- Core database connectivity.
- Observability dashboards.
- Automated builds and basic security scanning.

**Exit gate:** The foundation deploys reproducibly, passes security smoke tests, and can be rolled back.

## 49. Phase 2 — Supplier Integration and Search

**Goal:** Make the platform useful for discovery.

Activities:

- Implement location/reference data service.
- Implement supplier interface and capability matrix.
- Create provider simulators and contract tests.
- Implement concurrent search aggregation.
- Add filtering, normalization, and pagination.
- Add caching and supplier throttling.
- Implement flight, bus, hotel, and train search adapters.
- Implement package discovery.

**Deliverables**

- Search APIs for all core verticals.
- Standardized results.
- Supplier dashboards and error metrics.
- Provider integration contract tests.

**Exit gate:** Search APIs meet agreed latency and degradation behavior under load using authorized test providers.

## 50. Phase 3 — Pricing, Quotes and Booking Foundation

**Goal:** Create durable booking intent workflows.

Activities:

- Build quotation and pricing modules.
- Add supplier price revalidation.
- Add traveller/guest validation.
- Build booking aggregates and state machines.
- Implement idempotency records.
- Implement durable orchestration and outbox.
- Add supplier reservation abstractions.
- Build recovery and compensation workflows.

**Deliverables**

- Quote APIs.
- Booking intent APIs.
- State transitions.
- Outbox/event pipeline.
- Synthetic end-to-end booking workflow.

**Exit gate:** Concurrent requests, process restarts, and simulated supplier failures do not create duplicate or untraceable bookings.

## 51. Phase 4 — Payments and Real Booking Integration

**Goal:** Complete the first fully operational travel booking journey.

Activities:

- Integrate payment gateway sandbox.
- Add signed webhook validation.
- Implement payment status resolution.
- Implement one authorized supplier booking flow.
- Add confirmation and ticket document handling.
- Add failure recovery and reconciliation.
- Validate payment/booking state consistency.

**Deliverables**

- Working payment and booking workflow.
- Supplier-backed confirmation.
- Customer trip retrieval.
- Failure/retry/reconciliation tests.

**Exit gate:** A transaction can be completed and recovered end to end without duplicate charging or fabricated confirmation.

**Recommended first vertical:** Bus booking or a straightforward hotel provider with reliable sandbox support, because a complete seat/room booking lifecycle provides early validation of core workflows.

The specific first vertical should ultimately be chosen based on available commercial access and the quality of supplier sandbox support.

## 52. Phase 5 — Remaining Booking Verticals

**Goal:** Extend common booking foundations across travel services.

Activities:

- Integrate flights and fare-family/ticketing rules.
- Integrate additional bus operators.
- Integrate hotels and room-rate plans.
- Integrate holiday package booking.
- Implement railway ticketing through an approved path.
- Support supplier-specific reservation and payment sequence variants.
- Expand exception and compensation handling.

**Deliverables**

- End-to-end P0 booking for every approved vertical.
- Consistent booking status contracts.
- Supplier-specific test suites.

**Exit gate:** Each production-enabled vertical independently passes booking, cancellation, failure, and financial reconciliation tests.

## 53. Phase 6 — Cancellations, Refunds and Operations

**Goal:** Complete post-booking and financial operations.

Activities:

- Cancellation eligibility and quotation.
- Supplier cancellation adapters.
- Full/partial refund orchestration.
- Payment/settlement reconciliation.
- My Trips and document retrieval.
- Customer notifications.
- Operations dashboards and exception queues.
- Restricted manual recovery tools.

**Deliverables**

- Complete booking lifecycle.
- Reconciliation jobs.
- Refund workflows.
- Support and operational interfaces.

**Exit gate:** Booking, payment, refund, and supplier reference records reconcile and are recoverable after simulated failures.

## 54. Phase 7 — Production Hardening

**Goal:** Establish evidence-based production readiness.

Activities:

- Load tests and capacity tuning.
- Security and penetration testing.
- Disaster recovery and backup restore drills.
- Chaos/fault injection tests.
- API compatibility reviews.
- SLO dashboard and alert validation.
- Well-Architected review across all six pillars.
- Cost modeling and capacity adjustments.
- Pilot rollout with controlled traffic.

**Deliverables**

- Signed-off readiness evidence.
- SLO monitoring.
- Recovery runbooks.
- Final risk register.
- Operational escalation process.

**Exit gate:** Business, security, reliability, and engineering owners approve launch based on evidence—not merely successful happy-path tests.

## 55. Phase 8 — Enhanced Travel Platform

**Goal:** Differentiate the product.

Activities:

- Cabs integration.
- Multi-product itinerary/cart.
- Saved searches and fare alerts.
- Loyalty and customer incentives.
- Advanced search recommendations.
- Personalized travel planning.
- Corporate travel features where needed.
- Analytics-driven supplier routing.
- Governed AI assistant integration.

**Exit gate:** Advanced features meet established correctness, privacy, cost, and reliability requirements.

---

# PART L — DELIVERY GOVERNANCE AND ACCEPTANCE

## 56. Sprint and Delivery Structure

Suggested team streams:

| Stream | Focus |
|---|---|
| Architecture/Platform | AWS landing zone, IaC, security, deployment, observability |
| Travel Supply | Supplier adapters, mapping, contract tests, rate limits |
| Commerce/Booking | Quotes, pricing, orchestration, traveller validation |
| Financial | Payments, refunds, reconciliation, ledger |
| Customer Experience APIs | Profiles, My Trips, documents, notifications |
| QA/SRE | Automation, chaos testing, performance, recovery, release validation |

Delivery can proceed in two-week iterations, with vertical integrations introduced incrementally.

Do not assign fixed calendar dates before supplier onboarding, staffing, and integration scope are confirmed.

## 57. Definition of Done

A backend feature is complete only when:

1. Functional acceptance criteria pass.
2. Relevant authorization checks exist.
3. Domain invariants are enforced.
4. Required APIs/events have versioned contracts.
5. Success, retry, timeout, and permanent failure cases are tested.
6. Transaction and idempotency rules are verified.
7. Database migrations are compatible with rollout.
8. Metrics, tracing, and safe logging exist.
9. Security and dependency scans pass according to release policy.
10. Runbook changes are documented where needed.
11. Capacity/performance targets are checked for affected paths.
12. The feature is proven against a valid provider sandbox or explicitly labeled simulated.

## 58. Production Release Acceptance

**Functional**

- Each enabled vertical has verified search, pricing, booking, and post-booking flows.
- All P0 requirements are implemented or approved with explicit scope exclusions.
- The system distinguishes confirmed, pending, failed, and refunded states accurately.

**Financial integrity**

- Duplicate requests cannot create unintended additional bookings or payments.
- Unknown payment and supplier outcomes can be reconciled.
- Refund and settlement records are traceable.

**Reliability**

- Core SLO targets are demonstrated under realistic load.
- Process and infrastructure failures are recoverable.
- Backup restores and DR procedures are tested.

**Security**

- Sensitive APIs are protected.
- Provider credentials are securely managed.
- Financial/webhook operations are verified.
- Privacy and relevant compliance assessments are completed.

**Operations**

- Alerting, dashboards, on-call ownership, and runbooks are active.
- Supplier failures have defined escalation paths.
- Exception queues and manual recovery procedures are controlled and audited.

---

# 59. Critical Architecture Decisions

| Decision | Recommended default | Revisit when |
|---|---|---|
| Modular monolith vs microservices | Domain-modular core with isolated high-risk/high-scale workers | Organizational or performance evidence supports a split |
| Compute | ECS/Fargate | Kubernetes requirements justify EKS |
| Database | PostgreSQL | Workload proves a need for alternative persistence |
| Search cache | Redis with bounded TTL | Cache costs or consistency risks outweigh gains |
| Broker | SQS/EventBridge | Kafka-specific event streaming is required |
| Distributed consistency | Durable orchestration, outbox, reconciliation | Supplier contract requires a different supported protocol |
| Concurrency | Bounded async/virtual-thread supplier fan-out | Measurements identify a superior approach |
| Multi-region | Single-region Multi-AZ with tested DR strategy | RTO/RPO and business continuity justify cross-region operations |
| Supplier strategy | Licensed adapters and contract tests | Provider access or commercial requirements change |
| Financial accounting | Auditable immutable transaction/ledger model | Business settlement ownership clarifies a narrower design |

---

# 60. Final Technical Outcome

The backend must reliably support:

**Customer Search → Supplier Aggregation → Offer Revalidation → Price Quote → Booking Intent → Supplier Reservation/Payment → Supplier Confirmation → Ticket/Voucher → Trip Management → Cancellation/Refund → Settlement Reconciliation**

The system should be:

- Domain-oriented rather than dependent on external API shapes.
- Secure by default.
- Idempotent on all financially consequential operations.
- Strongly consistent inside local transaction boundaries.
- Explicitly eventually consistent across external providers.
- Recoverable after crashes, lost responses, and delayed events.
- Measurable and observable.
- Scalable for read-heavy search traffic.
- Maintainable through clear bounded contexts and contract tests.
- AWS Well-Architected across all six pillars.
- Honest about booking state and supplier availability.

**Architectural success criterion:** Every financially consequential action must be authorized, uniquely identifiable, durable, observable, and recoverable to a verified business outcome.
