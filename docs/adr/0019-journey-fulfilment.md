# ADR 0019 — Durable bus, package and cab fulfilment

Status: accepted; scoped MOCK lifecycle verified. No live enablement.

A `journeys` booking context consumes published discovery/customer/payment contracts through outbound adapters. It owns immutable accepted quotes, passenger checkout, booking/payment/reservation/refund state, fenced durable work, command receipts, documents and outbox. Existing flight, hotel and rail aggregates/tables remain independent. No new framework or deployable service.

Bus: revalidate → durable exclusive seat hold → explicit customer payment → authorize/capture → consume hold/confirm. Cab: revalidate → explicit payment → category reservation; driver allocation remains unavailable. Holiday: a fresh owned quote resolves a supplier-bookable package or a previously owned enquiry into explicit MOCK supplier terms, requiring new acceptance. Legacy enquiry records are not silently fulfilled. Packages are preassembled single-provider bundles, with component confirmation/cancellation recorded atomically in the mock supplier; independently assembled multi-provider packages remain unfinished.

All new quotes expire within five minutes and the discovery freshness window. Payment revalidates selection and total; changed prices require a new quote. Bus holds expire at the quote deadline and are released on abandonment/failure/cancellation. Provider operations have stable IDs, immutable receipts and lookup-before-retry. PostgreSQL row/advisory locks enforce cross-search seat exclusivity. Remote calls run outside orchestration transactions. Unknown outcomes retain their checkpoint and become REVIEW after bounded retries; no inferred confirmation.

Cancellation uses an owned immutable expiring cancellation quote bound to booking version. Explicit sample policy: full refund before bus/cab departure minus two hours or holiday departure minus 24 hours; no cancellation through this adapter after that cutoff. All package component amounts reconcile to the captured total. Compensation refunds use the original captured account. Documents require confirmed provider receipt and are labelled MOCK / NOT VALID FOR TRAVEL. No real driver, PNR, ticket or check-in credential is invented.

AC-071: bus seat contention, expiry/release, accepted quotes/passenger constraints, payment, confirmed ticket, cancellation/refund.
AC-072: holiday direct/enquiry-origin fresh terms, package component fulfilment, voucher and full bundle cancellation/refund.
AC-073: cab category reservation, pickup terms/extras, voucher and cancellation/refund without invented driver assignment.
AC-074: ownership/CSRF, durable idempotency, lost acknowledgements, fenced recovery, payment-success/reservation-failure compensation, immutable outbox/document, migration preservation and browser integration.

Plan: contracts/domain → persistence/provider/workers → typed checkout/My Trips → failure/concurrency/migration tests → browser verification and evidence/RTM. Operational resolution UI for exhausted REVIEW, partial cancellations, rescheduling, package milestones/multi-provider orchestration and driver tracking remain separately recorded scope.

[Execution evidence](../verification-journey-fulfilment.md) records actual backend/browser/migration checks and unresolved supplier/operational scope.
