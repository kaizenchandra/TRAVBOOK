# ADR 0014 — Explicit optional passenger cancellation (MOCK)

AC-057: owned selected-passenger quotation, explicit supplementary policy consent and
remaining-party eligibility. AC-058: durable per-passenger supplier cancellation, cumulative
refund safety, revisioned summaries, duplicate/lost-response recovery and compatibility.

Original travel quote and whole-party policy remain immutable. V2 offers an additional,
explicitly accepted MOCK-RAIL-PARTIAL-V1 cancellation option: cancel selected active original
passenger indexes, refund their original base fares, retain their original charges. Remaining
passengers must include an adult unless all are cancelled. This is not an official railway
policy. One cancellation/refund cycle completes before another may start.

Supplier-owned passenger allocations are atomic with cancellation receipts and capacity.
Each accepted cancellation quote defines distinct stable supplier/refund operation IDs.
Completed cancellation records are immutable; cumulative refunds cannot exceed captured
funds and each passenger can be cancelled once. New summaries include current passenger
status and carry a revision; old revisions cannot be downloaded after cancellation begins.

V1 remains compatible for original whole-party bookings. Once a V2 cancellation begins,
legacy detail/PNR/document/mutations fail closed with409 rather than omit passenger state.
V2 reads support both old and new bookings. My Trips summaries contain no passenger list and
remain truthful; detail clients upgrade toV2. Existing pending legacy effects retain their
original operation IDs and entitlements. No applied migration is edited.

Hotel amendments/pay-at-property, legacy REVIEW reconstruction and real providers remain
separate work; this slice does not manufacture missing supplier evidence or rights.

Legacy persistence compatibility is explicit in RailRecordReader: only the additive
passenger indexes and document revision are defaulted; general JSON/API creator validation
stays strict. For old completed supplier cancellations, all original passengers remain
cancelled. V16 copies previously persisted completed refund history without changing the
booking, entitlement or financial journal. The update guard treats an absent vs null legacy
scope as equivalent but still rejects entitlement changes. V2 details read state and refund
history under the booking lock; document metadata uses its stored revision, not a second
potentially newer booking read.
