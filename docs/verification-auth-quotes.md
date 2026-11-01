# Execution evidence — OIDC, ownership and quotations

Historical quotation increment. The later durable-intent implementation and checks are recorded in [booking evidence](verification-booking-intents.md).

Executed locally on 2026-10-31 (Asia/Kolkata). Identity evidence is **LOCAL_OIDC** against Keycloak 26.8.1; supplier evidence is **MOCK**. A signed-JWT/JWKS test issuer is used for API negative cases. No production identity, travel supplier, payment gateway or AWS environment was operated.

## Delivered behavior

- Authorization code with PKCE S256, state and nonce; server-side JDBC session, HttpOnly/Lax cookie, login session rotation, token-expiry guard and local/provider logout. Access and refresh tokens are not retained after login or stored in the browser.
- Stable customer identity derived from verified issuer/subject; client-supplied owner IDs and totals are rejected. Quote read/acceptance is owner-scoped; another account receives 404.
- Fresh provider repricing followed by an immutable versioned quote with price/policy/itinerary/reference/expiry snapshot. Quote acceptance is a separate immutable exact-version receipt.
- Durable customer/operation/key idempotency with normalized fingerprints; concurrent equivalent requests replay one resource. Different keys cannot duplicate an acceptance. Quote/receipt, command and pricing event commit atomically, with provider calls outside the database transaction.
- V2 customer/session/quote tables and V3 mode/history guards; existing V1 discovery data preserved. Pricing event envelopes are stored and validated, **not published**.
- Integrated account, new-quote and quote-review pages; changed-price consent, refresh recovery, session-loss privacy, mobile layout and automated accessibility checks.

No inventory hold, booking, payment, ticket, cancellation or refund is created by these flows.

## Exact checks executed

| Command / inspection | Outcome | Coverage |
|---|---|---|
| `docker compose up -d --wait postgres identity` | Passed | Local PostgreSQL and real Keycloak healthy |
| `docker compose up -d --force-recreate --wait identity` | Passed after fixture correction | Reimported synthetic realm; no external account affected |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -q` | Passed: **33 backend tests**, zero failures/errors/skips; package built | 7 domain/discovery, 6 discovery API/persistence, 7 architecture, 8 quotation/security/session, 1 migration upgrade, 4 fail-closed mode cases |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=MigrationUpgradeTest test -q` | Passed after expanding upgrade assertions | V1 → V2 → V3, existing search/catalog preservation, event mode backfill, immutable event content and permitted publication timestamp |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=QuotationIntegrationTest test -q` | Passed: **8 focused tests** after final security refinement | Includes case-insensitive bearer authentication on protected mutations, signed-token negatives, ownership, expiry, contention, history and JDBC sessions |
| `sh scripts/maven.sh -f apps/api/pom.xml -DskipTests package -q` | Final packaging after the passing checks; tests intentionally not rerun in this packaging step | Rebuilds the executable artifact from the final sources; not counted as another test execution |
| `npm run contract:generate` | Passed | OpenAPI-generated TypeScript contracts updated |
| `npm run contract:check` | Passed | No generated-client drift |
| `node scripts/contract-compatibility.mjs .tools/pre-auth.openapi.json` | Passed | Additive comparison against the saved prior discovery contract (ignored local baseline) |
| `npm run contract:test` | Passed: **4 tests** | Existing API/security restrictions cannot be removed; additive definitions allowed |
| `npm run contract:lint` | Passed, **4 warnings** | Private-license metadata, local server URL, and two intentional redirect-only auth operations. No invented 2xx response was added to hide these warnings |
| `npm run requirements:generate` and `npm run requirements:check` | Passed | 480 source requirements/scenarios retained; implemented slice mappings updated |
| `npm run typecheck` | Passed | Frontend and tests |
| `npm test` | Passed: **6 unit tests** | Existing formatting and safe API error behavior |
| `npm run build` | Passed | Optimized Next.js build including account/new-quote/quote-review routes |
| `npm run test:e2e` | Passed: **22 tests** | Full desktop/mobile discovery + HTTP schema + real OIDC/quote journeys |
| `npm run test:e2e --workspace apps/web -- quotations.spec.ts` | Passed: **8 focused tests** | OIDC, CSRF, cross-account isolation, quote review/acceptance, logout, refresh/session loss and invalid callback |
| `npm run test:e2e --workspace apps/web -- quotations.spec.ts -g 'AC-011'` | Passed: **2 focused tests** | Preserves pending command and returns to the original quote after signing in again, desktop and mobile |
| `npm audit` | Passed: **0 vulnerabilities reported** | npm packages only; no claim about Java/container or penetration-test coverage |
| VS Code Problems | No errors found | Final application/test diagnostics |
| Mobile accepted-quote screenshot and axe scans | Passed for inspected views | No horizontal overflow; explicit totals/conditions/receipt and MOCK labels. Not full WCAG certification |

Persisted local pricing envelopes were also checked using:

```sh
set -o pipefail
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from pricing_outbox' | node scripts/validate-pricing-events.mjs
```

The final validation covered **22 persisted events**, all matching schema v1. It reads only pricing envelopes, not customer/session tokens. Local output reports are regenerated under `apps/api/target/surefire-reports/` and `apps/web/playwright-report/`; authentication tests disable network traces. The last focused browser run replaces the local HTML report with its two cases; the preceding full run reported all 22 tests passing.

## Cross-layer acceptance evidence

| Scenario | Actual path / assertions | Mode |
|---|---|---|
| AC-008 — sign-in and logout | Browser → same-origin auth proxy → Spring OAuth2 client → Keycloak → callback → rotated persisted session → customer port/JDBC. PKCE/state/nonce, HttpOnly/Lax, no browser token storage, logout and fresh provider login verified | LOCAL_OIDC |
| AC-009 — immutable quotes | NewQuote → typed POST → Quotations → Customers + RevalidateSelection → published discovery port → MOCK supplier → atomic pricing persistence/outbox; QuoteReview accepts exact version and reloads unchanged quote plus receipt | LOCAL_OIDC + MOCK |
| AC-010 — authorization | Real signed invalid issuer/audience/expiry/signature and missing scope rejected; cookie requests without/wrong CSRF rejected; Alice/Bob read/accept attempts isolated; owner/total extra fields rejected | Signed test issuer + LOCAL_OIDC |
| AC-011 — safe retries | Six concurrent create requests return identical quote, one command/event; concurrent same/different acceptance keys return one receipt/event; changed payload conflicts; pending request URL survives refresh; session loss hides private data | MOCK + PostgreSQL |
| AC-012 — expiry and migration | Mutable domain-clock quote expiry/late replay tests; persisted OIDC session rejected at ID-token expiry; invalid callback fails; V1 data survives migrations; immutable rows reject changes | Testcontainers + LOCAL_OIDC |

The scoped verified source rows are recorded in the RTM. Broader registration/OTP/profile/MFA/account-deletion, full token lifecycle, booking ownership and financial requirements are not marked complete by these tests.

## Failures found and corrected

- The initial local realm import defined quotation scopes but not `profile`, so requesting that unused scope caused Keycloak to reject login. Scope requests were minimized to `openid` and quotation permissions; corrected real browser flows passed.
- A malformed JSX callback delimiter was fixed before the successful frontend checks.
- Spring Session's concrete JdbcSession type is package-private. The persisted-session test now uses the public generic SessionRepository contract, preserving the expiry/restore assertions.
- Early unpublished development outbox rows lacked mode inside the envelope despite having a mode column. V3 safely copies that authoritative metadata and guards subsequent event-content changes; actual stored envelopes validate.
- The root npm forwarding syntax did not narrow the first browser run to desktop; that run exercised both projects. Subsequent focused runs use the workspace command with `--` correctly. No passing check was inferred from an unexecuted command.

## Not executed / remaining risks

- External production OIDC onboarding, provider-specific scopes/key rotation/MFA, registration/OTP/recovery, refresh-token retention/rotation and profile/consent/deletion flows remain incomplete. Local automatic renewal requires reauthentication.
- No full API process-crash/restart drill with a logged-in browser was run. JDBC session serialization/restoration and expiry were tested through real HTTP and PostgreSQL; future deployment recovery still needs its own drill.
- Pricing outbox dispatch, consumer deduplication/replay, booking/reservation/payment/refund state machines and financial reconciliation are not implemented.
- Current quotations store total travellers/cabin and an exact original offer reference. The next booking slice must preserve/check passenger category/age eligibility and validate submitted travellers against pricing; no booking commitment is safe merely because an acceptance receipt exists.
- Terraform/AWS deployment, managed database encryption/runtime least privilege, TLS ingress, Java/container vulnerability scanning, penetration tests, load/soak, backup/restore and DR were not executed.
- CI configuration is updated but hosted Actions was not run. Git metadata remains absent, so no commit/PR or base-branch CI evidence is claimed.

The next implemented vertical slice should be durable booking intent/workflow recovery from an owned, accepted, unexpired quote, followed by explicit MOCK reservation/payment and post-booking paths. All other source priorities remain unchanged.

Handoff: Playwright stopped its API/web processes after verification; `docker compose stop identity postgres` stopped the local identity/database services. The PostgreSQL volume is preserved. Restart with the documented local-oidc commands or VS Code tasks after bringing up Compose.
