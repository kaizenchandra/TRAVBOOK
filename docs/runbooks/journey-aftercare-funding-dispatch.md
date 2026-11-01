# Journey aftercare, funding and dispatch — MOCK

[ADR 0021](../adr/0021-journey-aftercare-funding-dispatch.md), [OpenAPI 0.20](../../contracts/openapi.json), [verification](../verification-journey-aftercare.md).

## Start and preserve data

V23 adds capability terms, balances, accepted care quotes, workflow/action records, cancellation entitlements, replacement relationships, revised documents, recovery checkpoints/audit, a care outbox and provider demo records. It does not rewrite old quotes or bookings. Back up PostgreSQL before deployment; build the backend/frontend images and use the existing Compose start procedure. Never delete the retained volume or edit applied migrations.

`JOURNEY_CARE_WORKER_ENABLED=true` and `JOURNEY_CARE_POLL_MS=300` drive the separate aftercare/balance worker. The base journey worker and existing refund-settlement worker must also run. Stopping a worker preserves claims, receipts and payment history. Claims use 15-second fenced leases; ordinary unknown effects have five attempts, pending refund lookup twenty, then REVIEW. Replacement waiting is bounded by the replacement quote deadline plus 60 seconds. Customer polling is bounded and offers manual refresh; a closed browser does not stop processing.

## Versioned terms

New UI quotes use `/api/v2/journey-quotes` and immutable quote version 2 with explicit funding/capability terms. Old v1 HTTP contracts remain unchanged. V1 mutations/documents reject v2 booking inputs, preventing full-price legacy payment or refund paths from bypassing milestone/partial policies. V2 My Trips reads both versions; legacy bookings retain their original full-cancellation terms and do not gain new partial/rescheduling/dispatch rights.

All totals, due dates, entitlements and remaining services are backend-owned. Amounts are INR decimal strings backed by BigDecimal and immutable payment/refund journals. No frontend total is authoritative. Quote expiry/version/ownership and explicit consent are checked before durable acceptance.

## Partial cancellation

- Bus: select distinct active seats before the accepted cutoff. Seat order maps to the original passengers. A partial cancellation must leave at least one adult aged 18+. The server quotes the selected seat fares, releases only those provider seats, and refunds only those collected entitlements.
- Holiday: after full funding, the optional `activities` component may be cancelled separately. Accommodation/transfers remain together; cancelling all remaining components is also supported. This is a preassembled single-provider MOCK package, not arbitrary independent vendor cancellation.
- Cab: cancel the whole vehicle service; reducing passengers does not create a proportional vehicle refund.
- A cancellation quote is immutable, owned, version-bound, expires within two minutes and cannot outlive the supplier cutoff. Accepted action → quote linkage and refund payment IDs/amounts are immutable. Reused keys with changed payloads, stale versions, duplicate/inactive components and concurrent actions are rejected.
- Supplier confirmation precedes refund entitlement processing. Multiple funding accounts are refunded independently with stable operation IDs. The customer sees collected/refunded/pending/outstanding amounts and each action, even while the remaining booking stays confirmed.
- Revised documents contain only active bus passengers/seats. Cancelled holiday activity itineraries are removed. Old document views are hidden during cancellation and invalidated by booking version; downloads require current confirmation and funding.

## Bus rescheduling

Search a different departure on the same provider and normalized route, with the same passenger ages. Obtain and explicitly accept the replacement quote. The server preserves passenger/contact details and creates a linked replacement booking. The customer explicitly pays its full price; there is no silent acceptance of a changed fare.

The original remains intact while replacement inventory/payment is pending. Only a supplier-confirmed replacement triggers original cancellation and refund of the original collected amount. A declined, rejected or expired replacement leaves the original booking unchanged; any captured replacement money follows its own compensation workflow. Cancellation entitlement is frozen when the conditional request is accepted under the new MOCK terms. Original and replacement states, charges and refunds remain independently auditable. Pending aftercare serializes further changes, including cancellation of the replacement until original aftercare finishes.

This is a full-charge/full-refund replacement, not a supplier-native amendment or net-price adjustment. Partial/previously partially cancelled bookings, changed route/provider/party and cross-currency changes are not supported. A failed replacement can be retried with fresh terms once the previous operation is terminal.

## Holiday milestones

The customer chooses FULL or an optional 30% deposit / 70% balance plan. Deposit plans require departure more than 72 hours ahead; balance is due 48 hours before departure, displayed in the journey timezone. Decimal rounding is deterministic and component allocations conserve the exact total.

Deposit capture can produce a supplier reservation, but funding remains DEPOSIT_PAID and the final voucher is unavailable. Balance collection is a separate durable authorization/capture intent with an independently bound payment account. Source funding and current supplier reservation are verified first. A declined balance may be retried with a fresh intent; an unknown result must be reconciled, not blindly charged again.

Full cancellation before balance payment refunds only the collected deposit and cancels the unpaid remainder. Partial optional-activity cancellation requires full payment. After full funding, cancellation allocates the selected entitlement across the deposit and balance accounts without exceeding either capture. Missing the balance deadline durably triggers cancellation and a collected-funds refund under the accepted sample policy; no automatic balance charge is invented. Final vouchers require complete funding and supplier confirmation.

The sample provider honors the missed-balance cancellation entitlement if processing resumes after downtime. Real package contracts may have different deposit forfeiture/deadline rules and must be implemented through authorized adapters; do not reuse these sample rules as real commercial policy.

## Cab assignment and trip updates

Confirmed v2 MOCK cab reservations get a persisted synthetic driver/vehicle assignment. The owned dispatch endpoint returns stage, descriptive synthetic location, progress and freshness. It never supplies a real driver, contact phone, registration, GPS coordinate or route estimate presented as live tracking.

The explicit “Advance MOCK supplier demo” control moves ASSIGNED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED. Customer-scoped command receipts and expected versions prevent duplicate/regressive effects. Provider reservation locking serializes cancellation with progression. Terminal states stop automatic polling; stale updates are disclosed. Confirmed cancellation invalidates assignment/tracking access. Demo advancement changes only synthetic progress, never pickup dates, booking status, inventory or money. Dispatch commands retain immutable provider snapshots; no notification delivery is implied.

## Recovery and operations

New care REVIEW cases appear in the existing signed-role/recent-MFA operations queue under BUS/HOLIDAY/CAB. They retain exact pre-review action and booking checkpoints, refund entitlements and phase. Resumption requires matching current provider/payment evidence; a historical receipt cannot authorize a contradictory outcome. Three audited resumptions maximum, atomic audit/work/outbox updates, original idempotency keys and lease fencing remain enforced. Existing v1 hotel/rail and base journey recovery paths are preserved.

No manual status forcing, refund amount override, new payment instruction or operator role/MFA fabrication is supported. If a replacement is unresolved, resolve its underlying booking evidence before resuming original aftercare. Missing/mismatched evidence remains REVIEW. Use forward fixes and preserved evidence, not table deletion, receipt alteration or new keys to bypass uncertainty.

## Limits

Real supplier/payment integration, valid travel documents, operator IdP provisioning and AWS deployment remain blocked on authorized access/target metadata. Arbitrary holiday milestone schedules, partial accommodation/transfer cancellation, changed-party rescheduling, supplier-native amendments, real cab contacts/GPS/maps and downstream event notification delivery are not implemented. No production SLO, load, security-compliance or DR claim is made. Persisted MOCK fixtures are retained for inspection; failed test runs may leave legitimate partially cancelled demo bookings in My Trips.
