# ADR 0002 — OIDC, ownership and immutable quotations

Accepted for this slice, 2026-10-31. Scope: BE-AUTH-003/005, customer identity foundation, BE-PRC-003/007/009/010/013 and FE account/quote review paths. Registration, OTP, recovery, profile editing and production identity onboarding remain distinct requirements.

Use Spring Security OAuth2 Login authorization code with PKCE, state and nonce, with PostgreSQL-backed HttpOnly SameSite=Lax sessions. Next.js proxies auth routes without owning credentials or tokens. Login return targets are a parsed offer UUID or the fixed account route. CSRF protects cookie mutations and logout; validated bearer clients use API scopes instead. Access/refresh tokens are discarded after authentication because this slice calls no user-specific provider APIs. Sessions stop being usable at ID-token expiry or the idle timeout; reauthentication replaces automatic token refresh. Logout invalidates the local session and invokes provider logout.

Local OIDC is explicit via the local-oidc profile and a loopback Keycloak container with test-only accounts; it is the real OIDC protocol, not a password bypass in the travel API. Production identity requires explicit issuer/client/origin configuration and HTTPS. Supplier operation remains MOCK regardless of identity mode. No deployment is authorized.

Customer identity is the verified (issuer, subject) pair, never email, a request owner ID or a browser-selected account. A unique database constraint resolves concurrent first logins to one customer. Pricing accesses the published customer and discovery ports, never their tables or concrete adapters.

Create a quote only after fresh supplier repricing. Snapshot itinerary, price components, policies, supplier reference, currency, mode, version and expiry. No quote update exists; later repricing creates a new quote ID. Acceptance is a separate immutable receipt for the exact quote version. Neither creation nor acceptance reserves, books or pays. The future booking command must enforce ownership, receipt/version, expiry and fresh inventory again.

Customer/operation/key idempotency records use canonical request fingerprints and PostgreSQL uniqueness. Supplier repricing is a read outside the transaction. Winning commands atomically store quote/receipt and full outbox envelope; concurrent duplicates replay the winning resource, changed payloads conflict, and expired original commands remain replayable. Separate quote-acceptance uniqueness prevents duplicate acceptance across different keys. Outbox publication and consumer replay are not implemented by this slice; durable unpublished events are not claimed as delivered notifications.

Quote rows and acceptance receipts reject UPDATE/DELETE through database triggers. Expiry and acceptance are derived/read separately; no expiry worker mutates historical prices. The runtime database role and managed datastore encryption need production infrastructure work. Local session records contain a framework-serialized security context, so database access is a privileged trust boundary.

References: [Spring OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html), [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Keycloak containers](https://www.keycloak.org/server/containers). Installed Next.js authentication and rewrite guides were read before frontend changes.

```mermaid
sequenceDiagram
    participant UI as Next.js browser
    participant API as Spring security / inbound ports
    participant IDP as Configured OIDC provider
    participant C as Customer application
    participant Q as Pricing application
    participant S as Published discovery / supplier port
    participant DB as PostgreSQL
    UI->>API: Start login with typed return target
    API-->>UI: Authorization redirect with state / nonce / PKCE
    UI->>IDP: Authenticate at provider
    IDP-->>API: Authorization-code callback through same-origin proxy
    API->>IDP: Exchange code with PKCE verifier; verify ID token
    API->>DB: Rotate and persist server session
    API-->>UI: HttpOnly session; return to intended quote route
    UI->>API: POST quote (offer ID, CSRF, idempotency key)
    API->>C: Resolve verified issuer + subject
    C->>DB: Find/create unique customer
    API->>Q: Create quotation for resolved identity
    Q->>S: Revalidate current offer outside DB transaction
    S-->>Q: Authoritative MOCK price / policies / validity
    Q->>DB: Atomically claim key + immutable quote + outbox
    API-->>UI: Quote snapshot with version and expiry
    UI->>API: Explicitly accept exact quote version
    Q->>DB: Owned quote check + expiry + immutable receipt / command / outbox
    API-->>UI: Acceptance receipt, no booking or payment
```

V2 adds the owned records and Spring Session schema. V3 repairs mode metadata on early unpublished development events and makes event contents immutable while leaving publication acknowledgement available for a future dispatcher. Quotation and acceptance remain separate immutable records; expiry is derived from the clock. No booking/payment state machine is implied by this diagram.
