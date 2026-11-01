# OIDC and quotation runbook

## Local operation

Start the local-only identity provider and existing database, then the apps:

```sh
docker compose up -d --wait postgres identity
sh scripts/maven.sh -f apps/api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local-oidc
```

In another terminal run `npm run dev`. Open <http://127.0.0.1:3000/account>. The VS Code API task now selects the local-oidc profile; start Compose first. Use the exact loopback host `127.0.0.1`, not `localhost`, because issuer, callback and cookies are bound to the configured origin.

The Keycloak test realm has **only synthetic local accounts**:

| Username | Local fixture password |
|---|---|
| alice | alice-local-only |
| bob | bob-local-only |

These values are public test fixtures, not deployed credentials. Keycloak is loopback-only, uses its development database, and has no administrator bootstrap password in this repository. Its test-user subject IDs are deterministic across recreations. Recreating the identity container discards its sessions; never use this local profile or import in a shared/deployed environment. Sign-up, password recovery, email/SMS verification and MFA are not implemented in this realm.

Choose a flight, select **Get a personal quote**, sign in if necessary, then explicitly create the quote. Review itinerary, original/current total, taxes, policies and expiry. Check acceptance and select **Accept this quote**. Refresh retrieves the same immutable snapshot and receipt. Acceptance creates no inventory hold, booking, ticket or payment.

## Identity and sessions

- `/auth/login` accepts only parsed `offerId`, `requestId`, or `quoteId` UUIDs to return to known local routes. `offerId` and `quoteId` are mutually exclusive; arbitrary return URLs are not used.
- Spring handles authorization code, PKCE S256, state and nonce. Keycloak enforces PKCE. Browser JavaScript sees neither access nor refresh tokens; server code discards them after authentication.
- PostgreSQL-backed `TRAVBOOK_SESSION` is HttpOnly and SameSite=Lax; Secure is mandatory for non-local login. Plain HTTP/non-Secure cookies are allowed only in the explicit loopback local profile.
- Idle session timeout is 15 minutes. A session is also rejected at its ID-token expiry. No refresh token is retained; sign in again to renew authentication. Invalid-token responses are safe `application/problem+json` errors.
- `/auth/session` returns authentication availability and a CSRF token, never identity-provider tokens. Fetch a new token after login. Quote mutations and the logout form require CSRF for cookie sessions. Bearer APIs require valid signature, issuer, API audience, expiry and quote scopes.
- Logout is a CSRF-protected POST followed by OIDC provider logout. Local session state is invalidated first. A provider outage may prevent provider-wide logout, but does not restore the local session.
- Pricing uses a resolved customer keyed by verified issuer/subject. Another customer's quotation and acceptance route return 404, matching an unknown ID. Customer UUIDs are never accepted as mutation input.
- Auth browser tests disable network tracing to avoid retaining authorization codes/session cookies. Screenshots and test fixtures must never use real customer identities.

## External OIDC configuration (not yet externally verified)

Use the `oidc` profile, not `local-oidc`. Set `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `WEB_ORIGIN` and `OIDC_AUDIENCE` from your authorized provider/configuration system; keep `SESSION_COOKIE_SECURE=true`. Register exactly `WEB_ORIGIN/login/oauth2/code/travbook` and the logout return `WEB_ORIGIN/account?loggedOut=true`. Enable authorization code with PKCE, `openid`, `quotes:read`, `quotes:write`, and access-token audience `travbook-api` (or configured equivalent).

The supplied external-client configuration uses confidential client authentication; provider-specific onboarding, client scopes, key rotation, MFA, account recovery and claims policy require separate validation. Missing configuration or invalid discovery must fail startup. Credentials being present cannot change supplier mode from MOCK. HTTPS ingress, private networking, encrypted managed database/secrets and a restricted runtime database role remain deployment prerequisites. No AWS deployment has been performed.

## Quotes, retries and events

- `POST /api/v1/quotes` accepts only `offerId` and an `Idempotency-Key`. Repricing happens outside the database transaction; the winning snapshot, command record and event commit atomically.
- A quote is version 1, immutable, customer-owned, MOCK, and expires no later than the provider observation/departure. Later pricing produces a new quote ID, never updates the old row. GET still returns expired snapshots with `expired=true` for review.
- Acceptance records exact quote/version consent in a separate immutable receipt. Expired or mismatched versions fail with 409. Duplicate keys with equivalent payloads return the original resource; changed payloads conflict. Different keys cannot create multiple receipts for one quote.
- A successful command remains replayable after expiry. Replay does not revive a quote. The future booking operation must check owner, acceptance/version, expiry and inventory independently.
- The quote creation request UUID is retained in the URL while pending. It is an idempotency identity scoped to the authenticated customer, not an authorization secret. Refresh/retry does not automatically submit a mutation.
- Keep command records with their quote history; no automatic key eviction. Retention/deletion policy and approved archival are still required before personal/live data.
- `pricing_outbox` stores schema-versioned `QuotationCreated` and `QuotationAccepted` envelopes. Acceptance uses the receipt as a separate aggregate; both immutable aggregates have version 1. V3 adds mode metadata to early unpublished local events and protects event contents against mutation. Publication/consumer delivery is **not implemented**. A nonempty unpublished outbox is expected; do not mark it delivered manually.

Validate persisted local envelopes without exporting identity/session records:

```sh
set -o pipefail
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from pricing_outbox' | node scripts/validate-pricing-events.mjs
```

## Migrations, rollback and diagnosis

V2 adds customer, Spring Session, immutable quotation/acceptance, command and pricing outbox tables. V3 adds missing mode metadata to unpublished local envelopes and guards event history. V1 discovery tables remain unchanged. Migration upgrade tests preserve existing V1 data; database triggers block quote/receipt/command alteration. Application rollback can retain additive tables; do not edit applied migration files or delete history to make rollback appear successful.

401: reauthenticate and recover the same pending request/quote. 403: obtain a fresh CSRF token or investigate missing scopes; do not disable CSRF. 404: unknown or not owned. 409: inspect stable error code; reprice with a new command for expiry, retain the original key for an uncertain outcome, and never reuse a key with changed payload. 503: identity/provider/database unavailable; do not fall back to a fabricated account or quote.

Spring Session rows include a serialized security context/ID token. Treat the database as a privileged, encrypted trust boundary; production IAM/database access and encryption are not established by local tests. Stop local validation apps and `docker compose stop identity postgres` when finished; PostgreSQL volume is retained.
