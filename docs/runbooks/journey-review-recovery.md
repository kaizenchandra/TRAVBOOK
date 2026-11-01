# Journey REVIEW recovery

Applies to BUS, HOLIDAY and CAB through `/api/v2/operations/reviews/{kind}` and `/operations/reviews`. Hotel/rail v1 contracts remain unchanged. No live adapter or operator identity was provisioned by this increment.

An operator needs the existing signed `operations_reconcile` role and MFA evidence (`amr` and `auth_time`) within ten minutes. Customer accounts are denied. Never add an unsigned header, edit the local realm to fabricate MFA, or change booking state directly to bypass the gate.

1. Choose the service and inspect the redacted queue. Record case ID, version, separate payment/reservation/refund status and checkpoint. No passenger/contact data is returned.
2. Investigate the provider outage or uncertainty through an authorized channel. A supported effect requires both its immutable, correctly bound receipt and consistent current provider/payment state. A historical confirmation cannot override current cancellation. No receipt means no replay during operator resumption.
3. Select the investigation reason, consent to the exact case/version and resume. The server atomically records the operator fingerprint/action, restores the pre-review state, requeues durable work and appends a correlated event. Accepted does not mean confirmed.
4. Refresh evidence and the owned booking. The worker re-reads the original operation receipt, applies legal transitions and continues the already-authorized workflow. Payment capture alone does not produce a ticket. Confirmation/document creation and refunds remain governed by the original quote and entitlements.
5. Repeating the same action key returns the original action. Changed inputs conflict. Two operators cannot resume the same version. A maximum of three audited resumptions prevents endless loops.

V22 records checkpoints only when a new transition enters REVIEW. Earlier V21 reviews without checkpoints are deliberately unchanged and unresumable; prior status is not inferred. Journey v2 has no legacy force/terminal-correction endpoint. Original checkpoint, quote, traveller data and financial records remain immutable. Missing or contradictory evidence requires investigation, not SQL deletion or a new charge.

For local evidence, `JourneyReviewIntegrationTest` uses a separate Testcontainers database and signed test issuer. `JourneyOpsBrowserIT#desktopAndMobileRecoverAllJourneyKinds` drives the actual production frontend against that isolated API for all three categories on desktop/mobile. It does not create a privileged account in the retained local IdP. Existing `OpsBrowserIT` hotel/rail tests remain regression gates.

Migration/rollback: back up first, apply additive V22, preserve all retained data. On an issue, stop the worker and use a reviewed forward fix. Never remove immutable audit/checkpoint rows or delete PostgreSQL volumes. AWS/provider onboarding, partial cancellations/rescheduling, package payment milestones and cab assignment/tracking remain separate unfinished capabilities.
