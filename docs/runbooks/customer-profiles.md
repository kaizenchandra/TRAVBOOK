# Owned customer profiles

This service stores actual customer-provided profile fields in PostgreSQL through verified OIDC identity. It has no simulator/provider adapter and does not depend on travel inventory mode. Local verification uses the existing local Keycloak test identities. This is not evidence of production IdP provisioning or a live travel integration.

## Operation

- Open `/account`, sign in, edit the optional display name/email/phone, explicitly consent and save. Empty strings clear fields. Reload or sign in again to read the saved profile.
- GET `/api/v1/me/profile` returns version zero and empty fields if nothing has been saved.
- PUT the complete fields, `expectedCustomerId`, `expectedVersion`, `consent: true` and an `Idempotency-Key`. Cookie clients also send the current CSRF token from `/auth/session`.
- The authenticated issuer/subject is the sole owner authority. `expectedCustomerId` is only a precondition that prevents a stale form crossing accounts. A changed account returns `409 PROFILE_IDENTITY_CONFLICT` with no write.
- A newer saved version returns `409 PROFILE_VERSION_CONFLICT`. Reload deliberately before making a new edit. Do not automatically retry against a newer version.
- If a save response is lost, retry the exact body and key. The response returns the original `appliedVersion` and the current profile. It does not retain or replay historical contact values. Changed-body reuse returns `409 IDEMPOTENCY_CONFLICT`.
- Invalid contact/consent/key returns 422, malformed/unknown JSON fields 400, missing identity 401, invalid cookie CSRF 403, oversized JSON 413, rate limit 429 and database failure 503. Error responses do not echo contact values.

Email and phone remain **unverified**. They do not change the login identity, request password resets, update booked passenger details, authorize notifications or confer any role. Contact verification and messaging need approved integrations and separate consent rules. Account export/deletion and lawful retention policies remain separate incomplete requirements; clearing profile fields is not account deletion.

## Persistence and migration

Additive V24 creates `customer_profile`, `customer_profile_command` and `customer_profile_audit`. It does not rewrite existing customers/bookings or create profile values for existing accounts. Take the usual restricted backup before application rollout; Flyway applies the migration on startup. Older application versions can run alongside the added tables but do not expose the editor. Do not drop tables on rollback.

Writes lock only the owning customer row, run under a three-second local transaction timeout, and commit profile/version, consented request fingerprint/receipt and metadata-only audit atomically. Audit records contain field names, version, customer/action/correlation IDs and time; no previous/new contact values. There is no outbound event or email delivery associated with this endpoint. Existing domain architecture boundaries remain in force.

Repeated commands are retained for replay; the receipt stores a fingerprint rather than a body/PII snapshot. Do not delete receipts or audits manually to resolve a conflict. Investigate request correlation, current version and exact operation receipt. Do not print profile values in operational logs.

The current single-instance API rate guard is not a production distributed/per-account abuse-control solution. Production database encryption, access roles, retention, backup restore and approved HTTPS/OIDC deployment still need their release evidence.

## Verification

See [current evidence](../verification-customer-profiles.md). CustomerProfileIntegrationTest uses isolated PostgreSQL and signed tokens; browser tests use actual local OIDC and API/database requests. Browser fixtures restore their original fields through the normal authenticated API and preserve all audit/command history. No database reset or physical profile deletion is performed.
