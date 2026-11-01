# ADR 0012 — Owned MOCK rail ticket lifecycle

Accepted for implementation, 2026-10-31. Real railway ticketing remains access-blocked.

A separate `railtickets` context consumes published rail discovery, customer and payment
contracts. It owns immutable passenger-priced quotes, consent, durable booking workflows,
protected ticket summaries and cancellations. Discovery remains independent of finance.
PostgreSQL owns commands, fenced leases, snapshots, provider simulator receipts and outbox.
No new framework, broker or deployable service is introduced.

Use authorize → reserve → capture with explicit supplier cancel/payment void compensation.
Lookup a stable operation receipt before retrying unknown effects. Booking ISSUED means
supplier reservation and captured MOCK payment; reservation CONFIRMED/RAC/WAITLIST stays
separate. Neither ISSUED, RAC nor WAITLIST promises a confirmed berth. No prediction or
silent waitlist promotion. Only explicit provider confirmation can change ticket state.

`MOCK-RAIL-V1` is a synthetic policy, not an assertion of railway rules: ages 5–120 at
journey departure, at least one adult 18+, max six passengers (four in synthetic Tatkal),
full same fare per passenger, no concessions, origin-only boarding, optional berth request
(not assignment), no middle berth preference for 2AC. Children under five and other quotas
are unsupported. Quotes expire at the earliest fresh observation/search/departure boundary.
Revalidation rejects price/status/position changes. Supplier receipts must match the accepted allocation category; an unexpected category stops capture and requires reconciliation. Provider stock uses stable train/date/
class/quota identity across searches and atomic bounded allocation. No overselling or
silent downgrade. Whole-booking cancellation before departure refunds base fare, retaining
simulated fees; original policy and entitlement are immutable. Partial passenger cancellation,
charting, auto-refunds, upgrades and live running status remain unimplemented.

MOCK PNRs are visibly invalid for real travel (`MOCK-PNR-` plus UUID). Owned PNR lookup
reads durable current state, not a public railway lookup. Documents carry an explicit
NOT VALID FOR TRAVEL warning, ticket status and no DOB. Real ticketing/authentication,
PNR, railway cancellation rules and gateway operations require authorized adapters; no
railway credentials are collected. Existing runtime guard rejects SANDBOX/LIVE.

AC-051: eligibility, owned quote/acceptance and explicit changed terms.
AC-052: idempotent durable reservation/payment, contention and recovery/compensation.
AC-053: protected issued summary and owned MOCK PNR; RAC/waitlist never guaranteed.
AC-054: cancellation entitlement, delayed refund, duplicate/lost-response recovery.

Implementation reuses proven workflow mechanics without coupling rail to hotel aggregates.
An eventual common durable workflow engine requires further evidence; do not refactor
working hotel/flight financial paths merely to reduce similar SQL in this increment.
External outbox publication and audited operator REVIEW resolution remain pending.
