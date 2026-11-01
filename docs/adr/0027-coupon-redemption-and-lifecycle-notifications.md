# ADR 0027 — Coupon redemption and lifecycle notifications

Status: accepted for local MOCK operation. OpenAPI 0.25; additive V28/V29.

## Decisions

The offers context owns campaign rules, immutable awards and usage. The fictional flight context calls the published `Coupons` port through `CouponClaims`; it does not access offers tables. A promotional quote is a new immutable quote. Discounts affect eligible base fares, not taxes or extras. Currency calculations remain decimal and authoritative on the backend. Campaign scope, dates, policy version, owner, minimum spend, cap and usage are checked before commitment.

Redemption joins the reservation's short local transaction. A locked campaign row serializes global/customer limits; uniqueness binds an award to one reservation. Seat allocation, redemption, receipt and outbox either commit together or roll back. Payment/refund use the discounted total. Cancellation/payment failure do not restore usage; customers see that rule before acceptance. Initial campaigns apply only to `FLIGHT_EXTRAS`, not every platform checkout. Campaign administration and wider category integration remain future work.

Each producer supplies its own `LifecycleSource` adapter over its own outbox and published safe projection. The notifications context receives `LifecycleSignal` and never reads another context's booking tables. Eight feeds cover flight intents/checkout/aftercare/extras, hotels, rail and journey booking/aftercare. Forwarding acknowledgments, deduplication fingerprints and version guards are durable. Malformed projections are quarantined; failed forwarding has five bounded attempts.

The notification transaction creates an owned in-app notice and optional EMAIL/SMS jobs together. Version-1 templates contain only safe state labels and protected booking links. In-app transactional history remains available regardless of optional channel preferences. Events older than the migration epoch receive in-app history only, avoiding a mass historical dispatch. Preferences are rechecked before new sends. Provider receipt lookup precedes retries or suppression so a previously committed effect remains truthful.

The local adapter records immutable `MOCK-NOTIFICATION-…` receipts in an independent transaction and makes no network call. Workers use fenced 15-second claims, at most three attempts and explicit REVIEW for unresolved outcomes. This supports process-loss recovery without claiming external delivery. Real providers must implement receipt/status semantics and remain disabled until authorized. No extra broker, cache or deployable service is introduced.

## Consequences and limits

Campaign-row serialization prioritizes usage correctness; capacity is unbenchmarked. Notification reads and polling are bounded but have no achieved latency SLO. Local durable queues do not imply exactly-once delivery. Notification REVIEW/forwarding dead-letter operator actions, reminder scheduling, editable/localized templates and external providers are unfinished. Full privacy retention/erasure must account for these records. No production compliance or infrastructure deployment is claimed.

See [acceptance and evidence](../verification-commerce-notifications.md), [runbook](../runbooks/commerce-notifications.md) and [remaining requirements](../requirements/remaining-work.md).
