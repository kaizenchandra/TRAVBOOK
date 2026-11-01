# Customer profile verification — AC-083/084

Scope: FE-ACC-05 and BE-AUTH-007, [ADR 0022](adr/0022-owned-customer-profiles.md). Actual persisted account details through OIDC → API → customer use case/domain → PostgreSQL, not a simulated profile service. Travel suppliers/payments remain explicitly MOCK because authorized provider contracts/access have not been supplied. No external message, charge, booking, GPS feed or AWS deployment is enabled.

Implemented: owned read/edit/clear, consent, field validation, unverified contact metadata, expected-owner/version preconditions, durable safe replay, atomic metadata audit, private typed account editor, error/reload/retry behavior and additive V24. Existing GET `/me` and previous HTTP contracts remain compatible; OpenAPI is now 0.21.0.

## Acceptance and checks

| Check | Actual result |
| --- | --- |
| Focused Maven `clean test -Dtest=CustomerProfileIntegrationTest,ArchitectureTest,MigrationUpgradeTest,RequestGuardTest` in an isolated copy of the POM/source | **25 passed**: five profile integration, seventeen architecture, one migration and two request-guard tests. |
| `npm run typecheck`, `npm test`, `npm run build` | Passed; six existing frontend helper tests and optimized build. |
| `npm run contract:generate`, `npm run contract:check`, `node scripts/contract-compatibility.mjs /tmp/travbook-before-customer-profile.openapi.json` | Generated client matches; additive compatible changes. |
| `npm run contract:lint`, `npm run contract:test` | Passed; four existing lint warnings and nine contract/generator tests. |
| `npm run requirements:test` | Four mapping tests passed. |
| `sh scripts/maven.sh -f /tmp/travbook-profiles/apps/api/pom.xml clean verify -B` | **265 passed**, zero failures/errors/skips, including existing travel/payment/aftercare tests. Source/POM copy compared equal to the workspace; isolated output avoids editor/Maven interference. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/customer-profile.spec.ts --output=/tmp/travbook-profiles/profile-final` | **6 passed**: three desktop and three mobile paths through actual local OIDC/API/PostgreSQL. Includes keyboard/axe/overflow, persistence through logout/login, duplicate lost-response retry, stale-version rejection, invalid inputs/CSRF and account-switch isolation. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/quotations.spec.ts tests/bookings.spec.ts --output=/tmp/travbook-profiles/auth-regression` | **16 passed** existing desktop/mobile identity, quote and intent paths. |
| Final frontend rebuild and `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/customer-profile.spec.ts --output=/tmp/travbook-profiles/profile-final-format` | **6 passed** after the explicit month/timezone timestamp display; current frontend container healthy. |

The initial verification caught a typed-client header placement mismatch and Java test compilation issues (compound `var` declaration and missing annotation import). These were corrected before the successful runs; no assertions were removed. The request guard now also bounds chunked JSON PUT/PATCH requests at 16 KiB, with a regression test proving oversized bodies never reach controllers.

The first combined 22-case browser run passed 17 and failed five: four new negative cases used an unscoped alert selector that also matched Next.js's route announcer; one existing mobile quotation case encountered the configured rate limiter (429 “Please retry shortly”). New assertions now target the alert inside main content without weakening their expected messages. The six profile and sixteen existing cases passed in the separate invocations above. The rate limiter was not disabled, relaxed or reset. This is not a claim that a new full 120-case browser run or hosted CI was executed.

Backend tests prove owned isolation, signed-token authentication, expected-account rejection, canonical duplicate replay, current-profile replay after subsequent edits, immutable metadata history without contact-value copies, concurrent first/replacement updates, stale rejection, validation and rollback when the audit insert fails. The frontend does not optimistically claim success or persist profile fields to URLs/localStorage/sessionStorage.

## Migration and limits

A mode-0600 PostgreSQL backup is saved at `/tmp/travbook-profiles/before-v24.dump`. Tests use separate Testcontainers databases. `docker compose build backend frontend` and `docker compose up -d --wait --wait-timeout 180` succeeded; retained PostgreSQL reports V24 successfully applied. No existing volume/history was reset. Browser tests restore original profile fields through the normal API while preserving the new versions/audit. No production contact details or external credentials are required for local testing. The mobile profile screenshot was inspected; saved timestamps now use an unambiguous month and timezone label.

OpenAPI's summary was consolidated to remove contradictory historical claims that implemented hotel/rail/aftercare functionality was unavailable. Source requirement texts and earlier immutable accepted booking policies are unchanged. The [RTM](requirements/rtm.md) marks FE-ACC-05 and BE-AUTH-007 Verified for this scoped local OIDC/database implementation; it does not mark the whole identity/privacy module complete.

Contact verification, notifications, registration/recovery provisioning, account export/deletion, broader consent/retention automation, live travel/payment adapters, operational reports, GPS and AWS deployment remain unfinished. Provider names, authorized API contracts/credential-store references, production IdP and deployment target were requested; none has been supplied in this turn. No fake live reference or success is generated.
