# Constrained hotel/rail REVIEW recovery

Start at `/operations/reviews`. This is a restricted operations surface, not a customer
screen. Apply additive V15 with the normal API startup; do not edit V1–V14 or reset the
PostgreSQL volume. New REVIEW transitions atomically persist a checkpoint, frozen booking
state, work state and outbox. Historical REVIEW rows cannot resume a guessed step. The separate terminal-proof path below can resolve only independently proven outcomes.

## Identity prerequisite

The trusted OIDC issuer must issue these **signed** ID-token claims for browser sessions
(or access-token claims for authorized API clients):

- `travbook_roles` contains `operations_reconcile`.
- `amr` contains `mfa`, or both `pwd` and `otp`.
- `auth_time` is an instant/epoch second no older than ten minutes, at most30seconds future.

The backend still verifies issuer, signature, audience, expiry and principal through its
existing security configuration. Roles must come from administrator-controlled policy,
never a customer-editable profile field. MFA claims must attest an actually completed
approved challenge; never map a static “mfa=true” value. Generic local Alice/Bob users do
not have these privileges. This increment does not provision an IdP operator account or
claim a real MFA challenge was exercised. Missing/expired assurance denies reads and writes.
Cookie mutations require CSRF. Tokens/credentials must not be pasted into application forms.

## Allowed procedure

1. Select HOTEL or RAIL, inspect the bounded redacted review queue and exact booking/version.
2. Investigate the underlying supplier/payment outcome through authorized systems. Record
   one of the implemented reason codes; do not enter PII or tokens into the audit.
3. Explicitly request reconciliation. The backend independently looks up the existing
   operation receipt and validates its binding. For read-only VALIDATE there is no side
   effect to reconcile. Missing/mismatched receipts return409; nothing is repeated.
4. A valid request atomically restores the saved pending step, resets its bounded worker
   budget, appends immutable operator-fingerprinted audit and an outbox event, and returns202.
5. Refresh evidence or open the customer booking through authorized support procedures.
   Acceptance is not confirmation. The normal worker determines the verified outcome.

One booking/version can resume once. Equivalent operator/key retries return the original
audit acknowledgement; changed input conflicts. The frozen booking input, quote, original
cancellation entitlement and ledger cannot be edited. Three total resumptions per booking
are permitted, after which it remains blocked for escalation. There is no force-confirm,
force-refund, amount adjustment, replacement provider operation, or delete-history action.
Flight REVIEW recovery and legacy missing-checkpoint repair remain unimplemented.

The audit records actor fingerprint (SHA-256 issuer + separator + subject), booking,
resulting version, reason and timestamp; outbox causation points to the action ID. Use
approved IdP audit records to correlate the operator, not a reversible account identifier
in the UI. Checkpoints are private booking snapshots and retain personal data under the
same database access/retention boundary as bookings. Never export them for investigation;
use the redacted API projection. Retention/deletion automation remains a production gate.

## Verification and local use

`ReviewIntegrationTest` exercises signed roles/MFA, missing evidence, immutable audit,
concurrent operators, idempotency, rollback and recovery limits. `StayIntegrationTest`
verifies the hotel path. After building/running the web image:

```sh
sh scripts/maven.sh -f apps/api/pom.xml '-Dtest=OpsBrowserIT#desktopAndMobileUseRealMfaClaimProtectedApi' test
```

This explicit browser gate starts an isolated Testcontainers database and real test API.
It sends requests from the production Next UI to that API with short-lived signed test
provider identities, validates real responses, then checks actual persisted effects. It
uses two viewport sizes, keyboard controls and axe. It does not stub business responses or
claim real IdP enrollment/challenge verification. Test tokens stay in child-process
environment only; no browser trace or persistent storage is enabled.

[Execution evidence](../verification-review-recovery.md) · [decision](../adr/0013-constrained-review-recovery.md)

## Legacy terminal-proof path (V17)

For REVIEW without a checkpoint, the UI offers **Reconcile proven legacy outcome**. With
explicit consent, this checks current provider allocation/status and current captured and
committed/settled refund totals, then binds matching immutable effect receipts. It may record
fulfilled or cancelled/settled outcomes only. There is no guessed checkpoint or replayed
financial/supplier mutation. Missing/contradictory facts, passenger-change cases, unsupported
failed/voided outcomes and existing checkpoints are rejected. All effects remain MOCK.

Proof, immutable operator action, terminal booking/work state and outbox commit atomically.
Proof is linked to the action and includes canonical references, amounts and effect IDs;
no tokens or passenger fields are included. Repeating the same actor/key/input returns the
same acknowledgement. The three-action budget includes this path. It does not grant arbitrary
status or amount correction. Do not use it as a substitute for real provider authorization.

Run both explicit operator browser gates after starting the production web image:

```sh
sh scripts/maven.sh -f apps/api/pom.xml '-Dtest=OpsBrowserIT#desktopAndMobileUseRealMfaClaimProtectedApi+desktopAndMobileReconcileProvenLegacyOutcomes' test
```

[Legacy proof evidence](../verification-legacy-reconciliation.md) separates tested claim
consumption from unperformed real IdP MFA provisioning/challenge verification.
