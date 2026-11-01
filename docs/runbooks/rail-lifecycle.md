# Owned rail lifecycle — local MOCK operation

The existing Compose deployment runs this slice with local OIDC and PostgreSQL. Do not
remove the named database volume. Apply additive Flyway V14 through the normal backend
startup; V1–V13 are unchanged. Rebuild backend/frontend together after contract changes.
No additional dependency or credential is needed. SANDBOX/LIVE remain fail-closed.

Start at `/trains`, choose a class/quota and check current availability, then continue
to MOCK passengers. Sign in through the existing local identity provider. Use fictional
passenger details. `/rail-tickets/quotes/new` contains only journey/class/quota/request
IDs; names/DOB never enter URLs, browser storage, logs or outbox events. The server stores
an immutable passenger snapshot with the owned quote. Review/accept the exact fare and
availability; the quote lasts at most the 60-second fresh observation. Start again if it
expires. Booking then revalidates before authorizing payment and reserving stock.

`/rail-tickets/bookings/{id}` and My Trips show independent booking, reservation, payment,
refund and workflow states. ISSUED requires a verified provider reservation and captured
MOCK payment. Reservation CONFIRMED/RAC/WAITLIST is separate; RAC/waitlist never guarantees
a confirmed berth. No berth numbers or real PNRs are invented. Owned `/rail-tickets/trips`
PNR lookup accepts only `MOCK-PNR-<uuid>`, including cancelled current states. Real 10-digit
PNR and running-status APIs remain unavailable.

A currently issued ticket can prepare/download a protected immutable UTF-8 summary.
It is labelled NOT VALID FOR TRAVEL, includes ticket status and no DOB, is bounded to
32 KiB and SHA-256 verified on download. All accesses enforce ownership; cancellation
immediately blocks downloads. This is not an authorized railway ticket or tax invoice.

## Synthetic policy and capacity

`MOCK-RAIL-V1`: ages 5–120 at departure; at least one adult 18+; max 6 passengers (synthetic
Tatkal max 4); same full fare for each passenger, no concessions; origin-only boarding;
optional unguaranteed berth preference; 2AC rejects MIDDLE. Under-five, other quotas,
intermediate boarding, charting, upgrades, individual passenger cancellation and real
provider refund rules remain unsupported. Existing Tatkal timing is fictional.

Available observations use their reported quantity; RAC and waitlist fixtures each allow
12 synthetic passenger allocations per service/date/class/quota. Stock keys use supplier,
train, route, departure, class and quota across search UUIDs. Reservation atomically checks
capacity; exhaustion rejects without downgrade, then voids authorization. Discovery is a
snapshot, not a hold. The final supplier operation checks fresh terms and stock.

Whole-booking cancellation before departure refunds the original base fares and retains
original simulated charges. The owned cancellation quote binds booking version and expires
within five minutes or at departure. Positive refunds start only after supplier cancellation.
Delayed settlement remains PENDING with an explicit provider reference; successful settlement
creates one balanced immutable reversal. This is a synthetic policy, not official railway law.

## Recovery and operations

`RAIL_TICKET_WORKER_ENABLED` defaults true; `RAIL_TICKET_POLL_MS` defaults to 300 ms; Compose forwards both variables. Worker activity
uses `travbook.railtickets.worker{mode=MOCK}`. Work is claimed with SKIP LOCKED, a 15-second
lease and fencing token. Local state/work/outbox commit atomically. Effective supplier and
payment calls run outside that transaction. Lookup stable receipts before effects; never
retry blindly after a timeout. Reservation/capture response-loss fixtures persist the effect
then throw, proving recovery. Failed capture cancels reservation and voids authorization.
The supplier simulator has 3-second transactions. Retries back off up to 8 seconds plus jitter,
max 5 claims per step (20 for pending refund status), then REVIEW. Expired leases are resumed
after restart; repeated crashes also consume this bounded attempt budget.

Do not force REVIEW to success, edit immutable inputs/ledger/receipts, or submit a second
payment to resolve uncertainty. Audited operator resolution is not implemented. Investigate
by booking/correlation/operation ID without exporting PII. Outbox events are committed and
visible in the owned timeline; external publication/consumer replay remains pending.
Browser polling stops on errors/terminal states or after 20 reads per book/cancel phase,
backs off to 8 seconds and pauses in the background. Refresh or reopen the owned quote to
recover a lost booking acknowledgement; one booking per quote prevents duplication.

Tests use isolated Testcontainers and actual child-JVM crashes. Browser tests use local
OIDC, validate HTTP schemas, and cancel only their own synthetic reservations through APIs.
No user database truncation is required. See [verification](../verification-rail-lifecycle.md).

## Current operator recovery extension

New REVIEW transitions now save an immutable checkpoint. Authorized operators with recent
MFA may request receipt-backed resumption through the [restricted recovery workflow](review-recovery.md).
This supersedes the earlier blanket “manual recovery unimplemented” limitation only for
this constrained path. Missing legacy checkpoints, contradictory/no receipt, arbitrary
status changes and financial adjustments remain blocked. Customer booking APIs are unchanged.
