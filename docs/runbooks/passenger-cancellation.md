# Selected-passenger cancellation — MOCK rail

V16 is additive. Preserve the named PostgreSQL volume and all applied migrations. The
migration creates provider-owned passenger allocations, immutable per-operation receipts,
completed cancellation history and revisioned document storage. It copies existing completed
refund history and backfills original whole-party allocations; it does not alter charges,
refund amounts, original booking input or original policy.

Use the current rail booking screen to select active passengers, request a server quote,
review the refund and retained charges for that selection, and explicitly accept the optional
MOCK-RAIL-PARTIAL-V1 policy. This is an additional MOCK capability, not an official railway
rule or a silent rewrite of the original whole-party policy. Only original base fares for
selected passengers are refunded; their original simulated charges are retained. Remaining
passengers must include an adult unless the entire remaining party is cancelled.

One aftercare cycle must finish before another begins. Cancellation pending, supplier
cancellation and refund settlement remain separate. A partial cancellation leaves the booking
ISSUED for the remaining party; CONFIRMED/RAC/WAITLIST stays independent and never guarantees
a berth. The UI shows per-passenger state, each completed refund and cumulative completed
refunds. A new quote must be obtained for each subsequent selection; duplicates, already
cancelled indexes, out-of-range/fractional values and stale versions are rejected.

Supplier cancellation commits only selected allocation changes and a durable receipt under
stable operation ID derived from the accepted cancellation quote. Provider capacity decreases
by the cancelled count. Refund IDs are distinct per cancellation quote; immutable cumulative
entitlements and the shared capture ceiling prevent duplicate/over-refunds. Lookup precedes
retry after unknown responses. Existing bounded workers, leases/fencing, REVIEW and audited
receipt-backed recovery continue to apply. Legacy pending whole-party operations retain their
original operation IDs and entitlement.

V2 detail and owned PNR reads describe current passenger state and completed refund history.
Once a V2 cancellation starts, v1 detail/PNR/document/mutations return409 rather than omit the
passenger state. Existing whole-party v1 operations still work. Quote creation/acceptance and
initial booking remain on their existing contracts; only reads/aftercare/documents use v2.
My Trips summary does not expose passenger counts; its detail link uses v2.

Prepare a new MOCK summary after cancellation settles. Each summary contains active/cancelled
passenger labels and a stored revision, but no DOB. Previous revision downloads are denied
as soon as cancellation starts; after partial completion only the current revision is valid.
All summaries remain NOT VALID FOR TRAVEL. No externally valid ticket/PNR is created.

Inspect current state through owned APIs or authorized operator views; never change array
indexes, reset stock, edit history or adjust ledgers directly. A legitimate database failure
rolls back local state/work/events/history together. Missing/mismatched supplier receipts
remain pending/REVIEW and do not entitle a refund. Source migration compatibility must not be
implemented by disabling API JSON validation.

[Decision](../adr/0014-passenger-cancellation.md) · [Evidence](../verification-passenger-cancellation.md)

Still unsupported: partial-segment cancellation, live railway rules/authorization, hotel
amendment/pay-at-property strategies, and evidence-free legacy REVIEW reconstruction.
