# Deferred hotel stays and amendments (MOCK)

This describes the retained V18/v2 workflow. The current v3 booking page also supports overlapping dates, room/guest changes and eligible prepaid replacements; see [funded hotel changes](funded-hotel-changes.md). The limitations below apply to this earlier workflow, not the new v3 path.

Scope: BE-HTL-012 / BE-HTL-014 P0; FE-HT-16 / FE-TRIP-07 P1. See [ADR 0016](../adr/0016-deferred-hotel-amendments.md). This is not a production payment method or an authorized supplier connection.

## Customer journey

1. Search hotels and select FLEX for every room. On “Your private hotel quote”, select “Pay at property”. Only approved MOCK FLEX terms support this choice.
2. Create and accept the owned quote. Enter fictional guests and reserve. The worker validates → reserves → confirms with payment DEFERRED. No platform payment account, authorization, capture or financial journal is created.
3. The booking shows the full amount due at property. DEFERRED does not mean the property collected payment. The protected voucher is explicitly invalid for real check-in.
4. To change dates, search the same property with the same room plans/occupancy and non-overlapping dates. Create and accept a fresh pay-at-property quote. Choose “Change an existing stay”, select the original booking and accept the replacement terms/amount.
5. The server checks ownership, booking version, quotation expiry/acceptance/use, guest ages and supplier capability. The durable amendment updates supplier stock atomically. Rejection preserves the existing reservation; unknown outcomes remain pending/REVIEW. Refresh or open the saved booking after a lost acknowledgement; do not create another reservation.
6. Preparing a voucher after a successful amendment creates an immutable revision for the new effective quote. Historical voucher bytes remain stored but customer downloads resolve only current confirmed terms. Pending amendments/cancellations block downloads.
7. Cancellation before the effective local check-in releases inventory and sets property amount due to zero. Refund and retained platform amounts are zero because the platform collected nothing.

## Recovery and invariants

The existing database worker owns fenced 15-second leases and bounded retries/backoff. AMEND uses a unique command UUID as its provider operation ID. Lookup precedes execution. The provider records an immutable receipt and replaces allocations within one property-locked transaction, with price/availability revalidation outside that transaction and a final stock check inside. PostgreSQL uniqueness prevents reuse of a quote for a booking and an amendment. Work → booking lock ordering avoids aftercare/worker inversion.

V18 backfills quote-use and voucher-revision metadata without changing original quote/booking payloads, voucher bytes or financial records. Old booking JSON lacking amendment history is normalized to an empty list only inside the persistence adapter. The original accepted booking quote stays immutable. At most ten amendment requests are allowed; closed history cannot be rewritten. For deployment, stop/drain old API workers, apply the new migration with the new image, then start workers. Old application versions do not understand DEFERRED/AMEND/history; rollback after new traffic requires compatible code or a restore of a verified backup, not a destructive down migration.

V2 hotel endpoints carry deferred and amended state. V1 detail/quotation/mutation/document routes reject modern resources before side effects. V1 summary/timeline schemas use string states and remain truthful. Saved-checkpoint REVIEW recovery can reconcile AMEND by its receipt. Legacy terminal-proof recovery deliberately rejects deferred/amended stays because it requires a captured account. No force-confirm, fabricated MFA, or manual financial adjustment is provided.

## Remaining capabilities

Prepaid date changes (delta charge/refund and compensation), overlapping dates, changes of property/room/guest, deposits/no-show penalties, real property collection/settlement and provider-issued check-in documents remain unsupported. External ticketing/PNR/payment providers and operator IdP/MFA provisioning remain required. No AWS resources are created.
