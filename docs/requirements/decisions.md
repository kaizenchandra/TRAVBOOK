# Source reconciliation

- Frontend and backend specs v1.0 are present and read in full, along with the master prompt, repository rules and architecture baseline. No source document is missing.
- Preserve each FE/BE ID separately, including apparently overlapping requirements; link them through acceptance scenarios rather than replacing source text. FE acceptance IDs are prefixed `FE-AC-` to avoid collision with integration scenarios.
- FE administration is P1 while BE operational safety is P0. Backend authorization, reconciliation and financial exception controls remain P0; editorial/admin experience retains its FE priority.
- FE pay-at-property is P1; BE-HTL-012 includes it at P0. Preserve both. Backend provider-aware payment terms must precede enabling that provider; the enhanced UI remains separately tracked.
- FE-HOME-01 requests all six services; cabs are P1. The shell can disclose all services, but incomplete services must be visibly unavailable and cannot present inert search/booking controls as completed functionality.
- Backend representative lifecycle includes refund states, but both master prompt and architecture require independent booking/payment/refund state machines. Separate persisted aggregates will govern future booking work.
- Phase numbering differs between FE, BE and master prompt. The master prompt's 0–9 sequence is the consolidated delivery sequence; original source phases remain recoverable.
- No supplier, payment or identity-provider contract/credential was supplied. MOCK flight discovery is enabled. SANDBOX/LIVE fail closed; no credential discovery or implicit live enablement. All booking/payment paths remain unimplemented.
- Source NFRs are proposed targets, never benchmark results. Prose constraints (privacy, railway authorization, accessibility, provider-specific fulfilment and all six AWS pillars) remain binding through their source sections even when no source ID exists.

# Acceptance scenarios for this increment

- **AC-001:** Airport query by city/name/code returns canonical IDs from PostgreSQL; keyboard selection and swapping preserve editable criteria.
- **AC-002:** A one-way or return search traverses Next.js → generated client → Spring inbound port → domain validation → bounded MOCK providers → PostgreSQL snapshot; fares are INR decimal strings with itinerary timezone and freshness.
- **AC-003:** Filters, stable pages, detail/back navigation and browser refresh preserve the search snapshot. An expired snapshot returns a recoverable conflict.
- **AC-004:** One provider outage returns explicit partial coverage; all providers failing returns 503, never a false empty inventory result. Empty successful searches remain distinct.
- **AC-005:** Repricing reports unchanged/changed/sold-out/expired distinctly. Changed fares display old/new totals and require acknowledgement. No reprice operation reserves or charges anything.
- **AC-006:** Invalid dates, routes, passenger categories, page sizes and malformed bodies are rejected server-side with safe problem responses and correlation IDs. Nonpublic routes fail closed.
- **AC-007:** At 390px and desktop widths, keyboard-operable search, result filtering and detail review have no horizontal overflow and pass automated accessibility checks. Automated checks are not full WCAG certification.

## Authentication and quotation increment

The earlier discovery baseline is extended with real LOCAL_OIDC protocol verification and MOCK supplier quotations. No external production identity/supplier contract is implied.

- **AC-008:** Anonymous discovery remains available; code/PKCE/state/nonce login through local Keycloak produces an HttpOnly rotated session, stable issuer/subject customer identity and no browser-stored tokens. Local/provider logout and expired-token rejection work.
- **AC-009:** Fresh provider pricing creates a customer-owned immutable quote with explicit version, money/policy/itinerary snapshot and bounded expiry. Changed prices require exact-version explicit acceptance, recorded separately. No booking, hold or payment is created.
- **AC-010:** Invalid signature/issuer/audience/expiry/scope and missing/invalid CSRF are rejected. Another customer cannot read or accept a quote and receives 404. Caller-supplied owner or total fields are rejected.
- **AC-011:** Concurrent equivalent command keys replay one persisted quote/receipt; changed payloads conflict. Different acceptance keys cannot duplicate the receipt. Commands/events/resources commit atomically. Refresh recovers a pending command identity; expiry prevents new acceptance without changing historical prices.
- **AC-012:** Uncorrelated OIDC callback fails closed; session-token expiry invalidates persisted sessions. V1 data survives V2/V3 upgrades; immutable records reject changes; event envelopes retain explicit MOCK metadata.

Quote creation request IDs are opaque retry identities scoped to customer/operation, not authorization credentials. They may be retained in URLs through reauthentication. Arbitrary redirect targets are not accepted. Automatic refresh-token retention is deliberately absent: reauthentication renews a session; broader refresh/revocation and account lifecycle requirements remain tracked.

## Durable booking-intent increment

- **AC-013:** Only an owned, accepted, unexpired exact quote version can create a new intent. Original adult/child/infant counts are retrieved through a published contract, copied durably and checked against each traveller's age on all departures. No client price/owner is accepted.
- **AC-014:** Atomically persist intent, command, workflow definition/version, reservation-not-requested state and outbox; return a stable 202 acknowledgement. Revalidate current price, policies and inventory outside the transaction; expose truthful pending/awaiting-payment/requote/unavailable/expired/review states.
- **AC-015:** Same-key equivalent retries and different-key identical quote submissions produce one intent. Changed payloads conflict. A lost acknowledgement/refresh recovers the existing intent without replaying traveller data; equivalent acknowledgements remain stable after expiry.
- **AC-016:** Scope/ownership/CSRF protect booking requirements, create, status and history. Other customers receive 404; cursor misuse does not reveal another account's records. Cached private traveller details disappear after a denied refresh. Summaries/events omit names and DOB.
- **AC-017:** Database-clock leases and fencing reject stale workers; persisted read-start markers precede calls; bounded retries/backoff/jitter lead to review instead of fabricated availability. An actual child JVM halted after a provider read recovers in a fresh JVM using the same database without duplicate state events.
- **AC-018:** Quote expiry during validation cannot commit an awaiting-payment result. Price/policy changes and explicit inventory loss have distinct outcomes. Additive V4/V5 upgrades preserve existing data; immutable input/event guards and architecture boundaries hold.

AWAITING_PAYMENT and a terminal VALIDATE_OFFER workflow mean validation finished only. No reservation/payment call, financial aggregate, confirmation document or full Phase 4 gate is claimed. P0 provider mutation/reconciliation/compensation work remains incomplete.

## Booking event delivery increment

- **AC-019:** Outbox insertion and delivery scheduling commit atomically, including additive backfill. Per-booking ordered claims, bounded retries and fencing protect publication. No publication marker exists before durable consumer acknowledgement.
- **AC-020:** Exact redelivery creates one immutable inbox/timeline row. Invalid schema/mode/content, conflicting identities/versions and illegal progression are quarantined; later versions remain blocked while unrelated bookings progress. Audited operator replay preserves original event identity.
- **AC-021:** An actual child JVM halted after consumer commit recovers in a fresh JVM on the same database. Lost acknowledgement and stale workers do not duplicate timeline entries or lose events; delivery attempts are bounded even for repeated crashes.
- **AC-022:** Owned paginated timeline API and desktop/mobile UI show delivered lifecycle history separately from authoritative status, with pending/attention, loading/error/retry and bounded polling. Unauthenticated, insufficient-scope and cross-owner reads fail closed; no traveller PII or internal diagnostic data appears in timeline payloads.

Denied timeline ownership/session reads also hide the surrounding cached private booking/traveller view; ordinary transient history outages leave authoritative booking status available. Both paths are covered by browser tests.

Local durable delivery is an explicit FRD section 32 transport adaptation, documented in ADR 0004. It does not imply an external broker, notification delivery, real supplier reservation or payment.

## Explicit MOCK checkout increment

- **AC-023:** Only an owned accepted unexpired quote with completed intent validation and explicit simulation consent starts checkout. Same-key and different-key equivalent concurrent submissions produce one durable workflow; changed scenarios conflict. V1 stays schema-compatible and fails closed after v2 checkout starts.
- **AC-024:** Revalidate → authorize → reserve → capture preserves separate states. Only captured payment plus supplier confirmation yields MOCK checkout confirmation. One capture produces two immutable balanced journal entries; no browser amount or owner is accepted.
- **AC-025:** Payment decline creates no reservation. Reservation rejection voids authorization. Capture rejection cancels the reservation before voiding authorization. Failed or unknown outcomes never imply successful travel or refund.
- **AC-026:** Persist UNKNOWN observations and operation identity/attempt/lease before effects. Lost responses and actual JVM failure recover through operation lookup; stale workers cannot commit. A local completion transaction failure leaves the independently committed receipt recoverable. Lookup errors cannot become definitive absence or trigger blind mutation. Exhaustion enters REVIEW.
- **AC-027:** Expiry before authorization/reservation/capture prevents new ineligible effects and compensates prior authorization/reservation; already committed capture is reconciled after expiry. HTTP/browser ownership, CSRF, typed contracts, mobile/keyboard, refresh and lost acknowledgement behavior pass. No real payment collection, documents or post-capture refund is claimed.

The v1 detail behavioral guard is deliberate even though the new schemas/endpoints are additive. Payment scope uses the existing bookings:write permission for explicitly simulated checkout; real gateway collection will require a separately reviewed authorization/security contract. All non-MOCK runtime modes remain disabled. Checkout-event dispatch and broader source P0 requirements stay In progress.

## Dependency security and protected MOCK summaries

- **AC-028:** Clean dependency installation uses actual patched minimatch 11.0.1 with a source-hash/version-guarded consumer adaptation. Generated client remains byte-identical before API additions; matching, nullable/reference generation, idempotence and unexpected-source rejection tests pass. No audit suppression or forced generator downgrade.
- **AC-029:** Only an owned confirmed MOCK checkout with captured payment and supplier confirmation can generate the fixed summary kind. The server owns content. Concurrent equivalent keys and different-key retries produce one immutable document/creation event; changed booking under the same key conflicts. Transaction failure rolls back document, command and event together.
- **AC-030:** Owned metadata/download APIs and desktop/mobile UI provide an actual UTF-8 attachment with checked SHA-256/byte length, no-store/nosniff and an explicit not-a-ticket/not-an-invoice label. Foreign customers and mismatched booking/document IDs cannot retrieve it. DOB/tokens are absent. Refresh recovers existing metadata; denied downloads hide cached private booking data.

These are MOCK summaries, not completion of all FE-TRIP-03 / BE-TRIP-003 provider ticket/voucher/invoice formats. Broader document retention, refunds, event publication and AWS remain unfinished.

## MOCK cancellation and refunds

- **AC-031:** Closed explicit policy versions determine total refund/retained amounts. Preserve original accepted policy; ambiguous legacy prose is blocked. Only an owned confirmed captured booking with current supplier eligibility and future first departure can obtain an immutable expiring cancellation quote.
- **AC-032:** Exact-version explicit consent, CSRF, ownership and scoped durable keys protect cancellation. Concurrent same/different-key equivalent requests create one case; changed quote/scenario conflicts. Expired new acceptance fails; equivalent accepted-command replays remain stable.
- **AC-033:** Verified supplier cancellation precedes positive refund submission. Cancellation may be CANCELLED while refund is PENDING. Zero entitlement becomes NOT_DUE without a transaction. Immutable reverse journal entries balance and cannot exceed captured funds including pending commitments.
- **AC-034:** Lookup-before-mutation, stable operation IDs, database-clock leases/fencing and bounded attempts handle lost responses and actual JVM loss after supplier or refund commit. Stale settlement rolls back journal writes. Unknown exhaustion enters REVIEW without inventing completion or blindly paying again.
- **AC-035:** V3 UI/API presents current cancellation/refund alongside historical checkout; legacy v2 detail/list fails closed for affected cases. Document generation/download is blocked for pending/cancelled/review cases. Desktop/mobile quote/consent/delay/refresh/lost acknowledgement and ownership checks pass.

Only full-booking MOCK policies are enabled. Partial-passenger cancellation remains P1; real providers, notifications, event dispatch and audited operator resolution remain incomplete. No old fare policy is silently reinterpreted and no refund of real money is claimed.

## Local Compose application deployment — AC-036–037

- **AC-036:** Backend/frontend images run as non-root with healthy dependencies and host loopback publications, retaining PostgreSQL data. Local Compose OIDC separates public issuer/browser endpoints from internal backchannel access without disabling issuer/signature/audience/PKCE/CSRF/ownership validation. Invalid topology and mixed integration modes fail closed; native and production identity guards remain intact. Sign-in must never redirect to the internal service hostname or a client-supplied forwarded host.
- **AC-037:** Existing integrated desktop/mobile journeys pass against production container images. Restarting API/frontend preserves the JDBC login, owned booking, My Trips and durable idempotency; replay causes no second MOCK reservation/capture, and persisted capture entries balance. This is application restart evidence, not database restore/DR/zero-downtime or production readiness.

These scenarios contribute to NFR-019 deployability; the full source requirement remains In progress. See [ADR 0008](../adr/0008-local-compose-application.md), [runbook](../runbooks/local-compose.md) and [evidence](../verification-compose.md). Legacy policy review and audited operational resolution remain pending; no historical policy or financial record is rewritten by deployment.

## Hotel discovery and rate selection — AC-038–041

- **AC-038:** Search by supported sample property/location fields with 1–30 nights, 1–4 room configurations and child ages. Persist search/offer indexes atomically; refresh/back restores criteria and filters; invalid dates, occupancy and missing context fail safely. No hotel booking is inferred.
- **AC-039:** Property details/illustration gallery and per-room rate comparisons display capacity, nightly amounts, meals, exact cancellation deadlines and tax/fee-inclusive stay totals. One distinct offer per room must belong to one property/provider/search. Flight passenger categories and itinerary policies must not leak into the hotel context.
- **AC-040:** Reprice all selected rooms through their supplier within a bounded budget. Price/policy changes need explicit UI review; selection changes clear prior review, expired results require recheck, and exhausted room types return recoverable conflicts. Provider effects, charges and bookings remain zero. Repeated reads never overwrite the original observation.
- **AC-041:** Partial provider failure, full outage and successful empty results remain distinct. PostgreSQL snapshot reconstruction, rollback, freshness and deterministic shared-room inventory rules are verified independently from UI tests. Desktop/mobile and keyboard-accessible controls target WCAG 2.2 AA.

Source conflict: FE-HT-16 is P1; BE-HTL-012 remains P0. Keep supplier payment capability P0 and conditionally expose the corresponding UI only when supported. This slice does not implement pay-at-property or a hotel reservation. Source hotel requirements remain In progress where only discovery/selection portions are covered; guest, quotation, booking, voucher and post-booking P0 requirements are not reclassified.

## Train discovery/availability — AC-042–045

- **AC-042:** Canonical station autocomplete and route/date/quota search preserve criteria/filter state across refresh/back. Validate unknown/equal stations, unsupported classes/quotas and date bounds; atomically persist snapshots/indexes. Dates and overnight journeys retain Asia/Kolkata context.
- **AC-043:** Class matrix shows AVAILABLE, RAC, WAITLIST, UNAVAILABLE and NOT_OPEN with text as well as color. Fares identify one-adult synthetic base/fees/total. No PNR, guaranteed berth, prediction probability or confirmed ticket is inferred from observations.
- **AC-044:** Fresh provider reads bind the selected journey/class/quota, detect changed availability/fare, and expire after at most 60 seconds, capped by search/departure. Changed selections, failures and stale observations hide old checks. Exact opening boundaries are tested for explicitly synthetic quota rules only.
- **AC-045:** Partial outage, all-provider failure and successful empty inventory are distinct. Bounded deadlines, rollback, repository reconstruction, expiry and absence of booking/payment effects are tested; desktop/mobile accessibility/keyboard and responsive states are verified separately.

Real passenger/quota restrictions, official Tatkal rules, approved railway authentication/ticketing, PNR/running status and aftercare remain unfinished. BE-RAIL-013 stays P1; its synthetic boundary fixture is not official-rule delivery. No railway credentials are captured. See [ADR](../adr/0010-railway-discovery.md) and [evidence](../verification-trains.md).

## Owned hotel lifecycle — AC-046–050

- **AC-046:** Owned immutable, expiring/versioned hotel quotations reprice current rooms; explicit consent is recorded transactionally. Idempotency is owner/operation scoped, conflicting keys fail and ownership is enforced on all reads/mutations. OIDC return allows only known internal hotel paths.
- **AC-047:** Room-specific guest dates/names are validated against quoted adults/child ages; consented input is immutable. One durable booking per quote survives duplicate/concurrent submission and browser refresh/lost acknowledgements without retaining guest data in browser storage/URLs.
- **AC-048:** Revalidate → authorize → reserve → capture preserves independent states. Nightly stock contention cannot overbook; reservation/capture failures compensate through cancel/void. Fenced leases, stable effect IDs, bounded retries and lookup recover actual process loss after provider commits. Unknown outcomes reach REVIEW without inventing success.
- **AC-049:** Only a currently confirmed owned stay can produce/retrieve an immutable hash-verified MOCK accommodation voucher; no DOB/token is included. Foreign customer access and post-cancellation download are denied. My Trips and private history show saved authoritative state.
- **AC-050:** Cancellation quotes bind booking version, each room's deadline and explicit refund entitlement. Supplier cancellation precedes positive refunds. Mixed plans can refund part of the monetary total while cancelling all rooms; zero entitlement creates no refund. Delayed/repeated/lost responses and process loss do not duplicate reversing entries or release stock twice.

All enabled effects are MOCK. In-place amendments, partial room/guest cancellation, pay-at-property, live provider updates/overbooking remediation, production vouchers, notifications and audited REVIEW resolution remain unfinished/gated, never reclassified. See [hotel lifecycle ADR](../adr/0011-owned-hotel-lifecycle.md), [runbook](../runbooks/hotel-lifecycle.md), and [evidence](../verification-hotel-lifecycle.md).

## Owned rail lifecycle — AC-051–054

- **AC-051:** Passenger names/DOB, accompanying adult, synthetic count/quota/berth/boarding rules are validated before server pricing. Immutable customer-owned passenger quotes bind class/quota/fare/observed availability/policy/expiry. Explicit versioned consent precedes booking. Changed inputs under an idempotency key conflict; foreign access is hidden; stale quotes cannot create effects. OIDC return paths contain no PII and reject arbitrary destinations.
- **AC-052:** One durable booking per accepted quote; revalidate → authorize → reserve → capture, with cancel/void compensation. Canonical service/date/class/quota stock survives distinct searches and contention. Stable provider IDs, lookup-first recovery, bounded claims, fencing and atomic state/work/outbox prevent duplicate effects after actual process loss. Unknown outcomes reach REVIEW.
- **AC-053:** ISSUED requires captured payment and provider allocation. CONFIRMED/RAC/WAITLIST reservation states remain distinct; no prediction or guaranteed berth. Only owned MOCK-PNR lookup is supported. Protected hash-verified summaries are explicitly invalid for travel, exclude DOB and become inaccessible when cancellation starts. My Trips and timelines expose current states without leaking passengers across customers.
- **AC-054:** Synthetic versioned whole-booking cancellation before departure refunds base fares and retains charges. Owned expiry/version-bound quotes and explicit consent precede supplier cancellation and refund. Delayed/lost responses, repeated commands and actual process loss cannot duplicate reversing entries or release capacity twice.

Real ticketing and PNR (BE-RAIL-008/010, FE-TR-10/13) remain externally blocked; these MOCK
acceptance scenarios do not satisfy provider authorization. BE-RAIL-010 and FE-TR-10 remain
P1. No invented official railway age/quota/tax/refund rules; unsupported cases are rejected.
[ADR 0012](../adr/0012-owned-rail-lifecycle.md), [runbook](../runbooks/rail-lifecycle.md),
[execution evidence](../verification-rail-lifecycle.md).

## Controlled REVIEW recovery — AC-055–056

- **AC-055:** Hotel/rail queues and audit projections require a verified operator role and signed recent MFA claims. Ordinary users, insufficient roles and stale/missing MFA are denied. No customer/passenger fields or credentials enter the UI/audit projection. Actual IdP role/MFA provisioning remains an external gate.
- **AC-056:** A current REVIEW version with an atomic saved checkpoint can resume only after independent lookup validates its existing provider receipt (or the saved step is read-only validation). State/work/audit/outbox commit together, conflicting/concurrent commands are guarded, input/entitlement/ledger stay immutable, and three resumption attempts cap operator retries. Unknown or contradictory evidence and historical missing checkpoints remain blocked. The worker determines final status; an operator cannot force it.

The next independent business-model work remains BE-REF-010 partial-passenger/segment
cancellation (P1), BE-HTL-012 supplier-supported pay-at-property (P0, FE-HT-16 P1), and
BE-HTL-014 modifications (P0). No functionality or priorities were silently removed.
Real ticketing/PNR/check-in documents/live payments remain blocked on approved providers.

## Selected passenger cancellation and legacy outcomes — AC-057–059

- **AC-057:** Owned selected-passenger cancellation quotes require explicit acceptance of the optional MOCK-RAIL-PARTIAL-V1 terms. Reject invalid, fractional, duplicate, already-cancelled or ineligible selections; remaining children must have an accompanying adult. Preserve original booking input and whole-party policy. Integer values such as1.0 remain valid without truncating fractions.
- **AC-058:** Supplier-owned passenger allocations, selection-scoped stable operation IDs, immutable completed refund history and the captured-fund ceiling prevent duplicate or cumulative excess refunds. One cycle finishes before another. Partial cancellation leaves remaining tickets issued with their actual MOCK class status. Current documents show passenger states and reject obsolete revisions. V1 reads/mutations fail closed after V2 changes; old pending whole-party effects retain their original IDs/entitlements. Process-loss, migration and cross-layer tests verify this boundary.
- **AC-059:** Authorized role/recent-MFA operators may reconcile legacy reviews only when current supplier state/allocation and captured/refunded totals agree with matching immutable receipts. Fulfilment and completed cancellation/refund are supported; missing/contradictory/unsupported evidence remains REVIEW. No prior step is guessed, no checkpoint is fabricated and no new supplier/financial effect is executed. Proof, audit, state/work and outbox commit atomically under version/idempotency guards.

BE-REF-010 remains P1 and incomplete for partial segments/real providers. Hotel pay-at-property
and modifications remain explicitly unfinished at their original priorities. No real IdP MFA
provisioning, ticketing, PNR, bank payment or valid check-in document is asserted.
