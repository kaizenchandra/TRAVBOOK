# ADR 0017 — Owned hotel replacement workflows

Status: accepted; scoped MOCK implementation verified. BE-HTL-014 P0 / FE-TRIP-07 P1; AC-063–066.

Support overlapping dates and changes of guests, occupancy, room count and plans at the same property. Quote using availability excluding only this owned reservation. Bind immutable replacement quotes to booking/version so they cannot be consumed as independent bookings. Recheck guests at the new local check-in. Keep original snapshots and historical documents immutable.

For prepaid stays, require every current room to remain fully refundable. Explicitly quote a full replacement charge and a full previous-payment refund, alongside the net difference. This is a deliberate first strategy using existing immutable payment accounts; it is not a delta-only debit. Authorize/capture into a separate amendment account, then atomically replace supplier inventory. On rejection, refund the replacement account and retain the original stay. On success, refund the previous current account. Unknown outcomes use lookup/reconciliation; no cancellation or documents while financial adjustment is pending. Later cancellation refunds only the active payment account under the effective room policy. Deferred changes make no financial calls.

Introduce v3 booking/change contracts, V19 quote binding/append-only change history/provider guest revisions. V1/v2 reject changed resources before mutations; old deferred AMEND workflows remain readable/recoverable. A new change history keeps existing V18 amendment records byte-for-byte recoverable. Original guest input is never overwritten; current guest view comes from the successful change.

AC-063: bound replacement quotation/acceptance, overlaps and guest/room eligibility. AC-064: prepaid increase/decrease/same-price replacement, separate capture/refund history, current-account cancellation. AC-065: supplier/payment rejection, delayed/lost refunds, crash recovery, duplicate commands and inventory contention. AC-066: desktop/mobile customer UI, protected current document, ownership/legacy guards and migration preservation. [Executed verification](../verification-funded-hotels.md): backend, migration/architecture and desktop/mobile integrated gates passed.

Real providers, non-refundable original-stay changes, property/currency/payment-method changes, delta-only collection, external guest-document verification and operator identity provisioning remain unavailable. No infrastructure deployment.

```mermaid
sequenceDiagram
    actor Customer
    participant UI as Typed v3 client
    participant App as HotelStays / StayService
    participant DB as PostgreSQL
    participant Worker as Leased StayWorker
    participant Pay as PaymentOperations / PaymentRefunds
    participant Hotel as HotelReservations
    Customer->>UI: Review replacement rooms, guests and both money movements
    UI->>App: Create bound quote, accept, request change
    App->>DB: Atomic command, quote use, immutable inputs and outbox
    Worker->>Pay: Verify original account; authorize/capture separate replacement account
    Worker->>Hotel: Lookup or atomically replace owned allocation and guest manifest
    alt Supplier accepted
        Worker->>Pay: Refund previous active account
    else Supplier rejected
        Worker->>Pay: Refund replacement account
    end
    Worker->>DB: Persist independent status and fenced progress
    UI->>App: Bounded polling / safe refresh
    App-->>UI: Current terms/guests and payment/refund history
```
