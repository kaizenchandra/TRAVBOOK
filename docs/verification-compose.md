# Local Compose deployment verification — 2026-10-31

Scope: AC-036/037 and the local portion of NFR-019. Four-service local stack, actual Keycloak OIDC and deterministic MOCK suppliers/payments; no AWS resources or live reservations/charges. The pre-existing PostgreSQL named volume remains intact; V1–V9 validated without a new migration.

## Implemented path

Browser → Next standalone production server → internal API rewrite → Java 21/Spring Boot → published application ports → PostgreSQL/deterministic supplier/payment adapters. Browser identity requests use the canonical localhost issuer; API backchannel requests use Compose service DNS and continue validating the canonical issuer. See [ADR](adr/0008-local-compose-application.md), [runbook](runbooks/local-compose.md), [Compose](../compose.yaml) and [traceability](requirements/rtm.md).

## Executed checks

| Command/check | Result |
|---|---|
| `docker compose config --quiet` | Passed |
| `docker compose build backend frontend` | Both images built from the workspace and pinned official bases; clean Linux npm install applied the guarded generator patch |
| `docker compose build backend` after redirect fix | Passed; final backend image includes fix |
| `docker compose up -d --wait --wait-timeout 180` / updated image with timeout 120 | All four services healthy; loopback publications and retained database verified |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | Final run: 97 tests, zero failures/errors/skips, package successful; includes 12 new startup/redirect checks and existing PostgreSQL/architecture/ownership/recovery coverage |
| `npm run typecheck` | Passed |
| `npm test` | 6 tests passed |
| `npm run contract:check` | Generated types match unchanged contract |
| `npm run contract:lint` | Valid; four pre-existing warnings (license metadata, localhost server and redirect-only login/logout operations) |
| `npm run contract:test` | 9 tests passed |
| `npm audit --audit-level=moderate` | Zero reported npm vulnerabilities; this is not an OS image scan |
| `npm run requirements:generate` / `npm run requirements:check` | All 480 source requirements/scenarios preserved; NFR-019 mapping added and remains In progress |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- --project=desktop --grep 'AC-008/009'` | 1 passed against rebuilt containers: OIDC/PKCE, immutable quote, acceptance, accessibility and IdP logout |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e` | 44 passed in 2.5 minutes: desktop/mobile discovery, OIDC ownership/CSRF/logout, quotations, durable intent/history, MOCK checkout/failure recovery, protected summaries and cancellation/refunds; includes accessibility/keyboard checks |
| `node scripts/verify-compose-recovery.mjs` | Passed: app containers restarted; JDBC session and owned My Trips/booking recovered; original commands replayed to the same IDs; SQL before/after showed exactly one reservation, one capture, two journal entries and net INR 0.00 |
| `node --check scripts/verify-compose-recovery.mjs` / editor diagnostics for changed deployment files | Passed / no reported errors |

Runtime inspected locally: Linux arm64, API UID/GID 10001, frontend UID/GID 1000. API and web images use read-only roots with explicit temporary storage in Compose. Base digests support additional architectures, but local amd64 execution is not claimed. Hosted GitHub Actions configuration was updated with a separate Compose build/browser/restart job; that hosted job has not run here.

All four services were healthy after the restart check and are left running at <http://127.0.0.1:3000> for local review. `docker compose stop` stops them while retaining PostgreSQL data. No historical policy or business row was deleted/reset. Verification added synthetic bookings and related receipts through supported APIs.

## Defect found during verification

The first container browser run failed at sign-in because the servlet expanded `/oauth2/authorization/travbook` to the internal `http://backend:8080` host. That run was stopped. The controller now uses the startup-validated browser origin and a regression test rejects influence from spoofed forwarded headers. The focused real OIDC browser test passed after rebuilding. An initial focused npm command forwarded flags incorrectly and selected no tests; the corrected workspace command above ran successfully. Neither failed attempt is counted as passing evidence.

## Remaining limits

No database restore, disaster recovery, load/soak, zero-downtime rollout, image vulnerability/signature assessment, production identity, managed secrets, production least-privilege database role or AWS deployment was performed. Local fixture passwords/HTTP/Keycloak development mode are not deployable production controls. This slice does not resolve ambiguous historical policies, REVIEW cases or delayed external outcomes. Real integrations/documents, remaining travel verticals and AWS remain unfinished. Source NFR targets and full NFR-019 completion are not claimed.
