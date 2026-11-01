# Proven legacy outcome reconciliation — 2026-10-31

AC-059 adds a distinct operator path for hotel/rail REVIEW records without saved checkpoints.
It records a proven terminal MOCK outcome; it never guesses a lost step or issues a new
reservation, cancellation, capture, void or refund instruction.

Current supplier allocation and current payment/refund totals must agree with matching
immutable effect receipts. Fulfilment requires all original units still allocated, captured
funds and no refund/cancellation intent. Cancellation requires the original accepted whole-party
entitlement, zero active supplier units and exactly the verified settled refund (or zero due).
Owner, resource, amount, currency, mode and references are bound. Missing, contradictory,
partial-passenger, failed/voided or otherwise unsupported cases remain REVIEW. Existing
checkpoints must use the checkpoint path; the three-action budget cannot be bypassed.

## Actual verification

- Final `sh scripts/maven.sh -f apps/api/pom.xml clean verify`: **172 passed**, no failures,
  errors or skipped tests. Includes legacy rail fulfilment, current-cancellation contradiction,
  pending-then-settled refund, immutable proof/audit and outbox rollback; hotel fulfilled and
  zero-refund cancellation cases; all prior financial/architecture/migration tests.
- `sh scripts/maven.sh -f apps/api/pom.xml '-Dtest=OpsBrowserIT#desktopAndMobileUseRealMfaClaimProtectedApi+desktopAndMobileReconcileProvenLegacyOutcomes' test`: **2 passed**, each exercising
  desktop/mobile widths. Production Next UI requests reached a real isolated Spring API and
  PostgreSQL using signed test-provider identities. Responses were not fabricated. Audit/proof,
  final state, single capture/reservation, keyboard/axe and denied refresh were verified.
- Final customer rail/partial/hotel browser regression: **20 passed**. Typecheck, production
  images, generated-client drift, additive contract compatibility and original requirement
  register checks passed. See [combined evidence](verification-passenger-cancellation.md).
- CI YAML syntax parsed; both operator browser gates are configured. Hosted CI was not run.

The browser gates validate consumption of trusted role/MFA claims, not actual IdP MFA
challenge/enrollment. No production/local privileged IdP account was provisioned. Ordinary
Alice/Bob customer accounts remain denied. Test tokens stay ephemeral and failure logging
redacts their values; no browser trace is collected for the operator gate.

V17 stores immutable canonical proof linked to the immutable operator action. Proof/state/work/
outbox commit atomically under the existing work→booking lock order. Idempotent replay returns
the same acknowledgement after completion. Financial journals, original inputs and original
entitlements are unchanged. The current-state read ports are provider-owned and bounded by
local transaction timeouts; operations never reads another context's financial tables.

Only MODE MOCK is implemented. A historical successful reserve receipt is insufficient when
current provider state says cancelled. A supplier cancellation is insufficient while the
refund is pending. Unproven outcomes remain blocked, not converted to failure/success.
Real gateway/supplier reconciliation, flight legacy workflows, arbitrary adjustments and
approval workflows remain outside this boundary.

[Decision](adr/0015-legacy-terminal-reconciliation.md) · [Operator runbook](runbooks/review-recovery.md)
