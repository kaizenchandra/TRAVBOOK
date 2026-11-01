# ADR 0013 — Receipt-backed operator recovery

Implement the P0 rail/hotel operational recovery boundary before partial-party cancellation
(P1 BE-REF-010) and additional hotel payment/amendment strategies. Real providers and valid
travel/check-in documents remain blocked on authorized onboarding; no credential means no
LIVE fallback. The requested partial cancellation, amendment and pay-at-property work remains
explicitly pending, not reclassified or claimed implemented.

AC-055: privileged signed role + recent MFA, redacted bounded queue, read-only audit.
AC-056: checkpoint/evidence-backed resumption with immutable idempotent audit, optimistic
versioning, frozen input and no force-status/ledger correction. Maximum three resumptions.

When a worker enters REVIEW, atomically save its prior immutable workflow checkpoint with
that review version. A privileged operator can request resumption only if the published
effect lookup returns a validated existing receipt, or the checkpoint is the read-only
VALIDATE step. Missing/contradictory evidence fails closed. The worker then uses its existing
lookup-first transitions; the operator cannot assert success, choose an amount or alter
passengers. Historical REVIEW without checkpoints remains blocked; do not infer lost state.

Shared recovery contracts expose redacted metadata only. Rail/hotel own their checkpoint,
audit and transaction; the operations context consumes their published recovery ports.
Role `operations_reconcile`, trusted `amr` MFA and auth_time within ten minutes are required
for reads and writes. No ordinary local customer receives that role, and no fixed claim is
used to impersonate completed MFA. Real IdP MFA setup remains an external configuration gate.

Partial cancellation needs item-level allocations, cumulative refund entitlements, retained
passenger eligibility and document revision. Amendments need quote/difference-payment and
atomic replacement/compensation. Pay-at-property needs a distinct supplier-backed deferred
payment state and cancellation liability. Do not reuse whole-booking cancellation or set a
captured-payment flag for these features.
