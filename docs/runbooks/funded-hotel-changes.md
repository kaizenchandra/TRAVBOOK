# Hotel room, guest and prepaid changes (MOCK)

See [ADR 0017](../adr/0017-funded-hotel-changes.md). New v3 endpoints operate under the existing OIDC booking scopes, ownership checks and cookie CSRF protections. Real property fulfilment, guest-document checks and funds movement are not available.

## Customer workflow

Open a confirmed hotel booking and use **Change dates, rooms or guests**. Choose new dates (overlap is supported), room count, adults/child ages and plans at the same property. Deferred stays support FLEX only. Prepaid changes require every current room to remain fully refundable. The backend verifies eligibility and quotes inventory excluding only this owned reservation. A bound quotation cannot be used to create a separate booking.

Review each room's policies and total. For prepaid stays the UI explicitly displays a **full replacement charge**, a **full previous-payment refund after success**, and the signed net price difference. This is not a delta-only payment. Enter the complete replacement guest manifest; ages at the new check-in must match every room. Confirming records quote acceptance and a durable, idempotent change request. Original guests, quote, payment and documents remain preserved.

The prepaid workflow validates, verifies current original funding, authorizes/captures a new separate payment account, and requests atomic supplier replacement. Acceptance activates the new terms/guests and refunds the previous current payment account. Rejection refunds the replacement account while preserving the original allocation/guests. Authorization failure creates no capture; capture failure voids its authorization. Deferred changes make no payment/refund calls.

Until adjustments settle, the booking remains PROCESSING (or REVIEW if uncertainty exhausts bounded recovery). Documents and cancellation are blocked. The UI shows separate change payment, supplier progress and refund status; capture alone never confirms replacement. Refreshing is safe after a lost response. Once complete, new voucher content uses current guests, rooms and dates. Full cancellation uses current cancellation terms and refunds only the active payment account; previous amendment accounts cannot be refunded twice.

## Persistence and recovery

V19 adds immutable `stay_change_quote` bindings, the provider's current guest-operation reference, and append/close guards for immutable change inputs/history. Existing V18 histories, quotes, vouchers and ledger entries are not rewritten. Missing `changes` on older booking JSON is normalized to an empty list solely in the persistence adapter. Quote creation/request uses the same work → booking lock order as aftercare; the replacement quote is consumed once. Financial and supplier calls execute outside orchestration transactions.

Each change has stable, distinct AUTHORIZE/CAPTURE/VOID/RESERVE/REFUND operation IDs. Fenced claims and lookup-first recovery prevent repeated effective operations after crashes. At most five operation attempts and twenty refund-status reads precede REVIEW; retries have bounded backoff. The provider locks the property, rechecks actual nightly capacity excluding only the current reservation, and commits allocations, current commercial terms, guest manifest reference and receipt atomically. It retains original allocation on rejection. An immutable provider operation stores the replacement guest manifest; it never enters logs/outbox.

Operator saved-checkpoint recovery remains protected by role and recent signed MFA. CHANGE_VALIDATE can be safely revalidated; effective steps require matching receipts. Unknown or inconsistent original funding stops before replacement authorization. Exhausted unresolved funding reviews cannot be force-confirmed by this increment. Legacy terminal-proof recovery rejects changed stays.

V1/V2 booking detail/mutation/document routes reject v3 changes before side effects. Summary and timeline contracts remain truthful through their string states. Current UI uses v3 aftercare; initial quote/booking creation remains compatible. New bound quotes link to the target booking so the standalone quotation UI cannot offer independent checkout.

## Operation and rollback

Drain/recreate old API workers before applying V19 with the new API image. Keep PostgreSQL data and existing immutable records. Older images do not understand change history/steps or active payment accounts; rollback after accepting changes requires compatible code or an independently verified recovery plan, not a down migration or payload editing. Local dumps are backups, not restore-drill evidence.

Current limitations: same property, INR and collection method; no changes from a non-refundable/expired original policy; maximum ten combined amendments/changes. There is no delta-only capture, real supplier amendment, property settlement, valid check-in voucher, real ticketing/PNR, production IdP/MFA provisioning or AWS deployment. Historical outbox publication and broader production release gates remain incomplete.
