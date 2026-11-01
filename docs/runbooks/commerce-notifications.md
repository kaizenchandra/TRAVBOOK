# Coupons and lifecycle notifications

## Local operation

Run the existing Compose stack after building the frontend/backend. V28/V29 are additive; back up retained PostgreSQL before migration. Do not reset volumes or rewrite financial histories. Compose enables `NOTIFICATION_WORKER_ENABLED=true`; native execution defaults false unless explicitly configured. `NOTIFICATION_POLL_MS` controls the interval (default 2000 ms). All provider effects are MOCK. No email address, phone number, gateway or airline credentials are needed.

Sign in, open `/account/travel` and enable optional email/SMS preferences. Open `/flight-services`, select a seat and request a quote with `TRAVEL10` (10% eligible base, maximum INR500, minimum INR1000) or `WELCOME15` (15%, maximum INR750, one use per customer). The server returns exact terms. Accept the quote and reserve. Reuse the same idempotency key for an ambiguous response; never generate new keys automatically for a possibly committed operation. A newly exhausted/changed/expired campaign can reject reservation; obtain a fresh quote. Existing quotes and financial records are not edited.

Open `/notifications` for owned lifecycle notices, bounded pagination and read markers. Delivery states are separate from booking/payment states. `SIMULATED_DELIVERED` means a local mock receipt exists; no message was sent. Historical in-app notices can be backfilled without optional-channel sends. A previous status is not the booking's current status; use its protected link.

## Recovery and inspection

- Provider writes are idempotent by delivery ID. After a worker crash, allow the 15-second lease to expire; the next worker first checks the saved provider receipt. Do not delete the receipt or audit history to force another delivery.
- `notification_delivery` holds current work; `notification_delivery_event` is immutable transition evidence. Three exhausted attempts lead to REVIEW. `lifecycle_rejected` contains malformed envelopes; `lifecycle_forward_retry` records up to five failed forwarding attempts. These require investigation; an operator resolution interface is not yet delivered. Do not label them successfully delivered or perform unaudited SQL resets.
- `lifecycle_forwarded` records producer acknowledgment only after durable notification acceptance. Replayed events deduplicate by source/event and fingerprint; aggregate version guards suppress stale state. Producer outboxes remain unchanged.
- Use metadata-only counts by source/state and the `travbook.notifications.*` meters. Do not log profile, authentication or traveller data. Monitor growing forwarding backlog, rejected events, exhausted retries and REVIEW work; production alarms are not yet deployed.
- Disable the notification worker to stop dispatch/forwarding during diagnosis. Existing notices remain readable. Disabling demo provider APIs removes coupon/provider HTTP entry points; it does not erase existing histories.

Provider contracts, credential references, mandatory-channel policy, contact verification, external delivery semantics, retention rules and operational approvals must be decided and tested before authorized integration. Coupon use is consumed at reservation and is not restored by payment failure or cancellation. That is the explicit current campaign policy, not an assumed industry rule.
