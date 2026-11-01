# Local API sandbox verification — AC-085–089

Scope: the user requested dummy APIs for missing providers and supporting services. [ADR 0023](adr/0023-local-api-sandbox.md) and the [runbook](runbooks/local-api-sandbox.md) define the limits. No real provider, external message, privacy erasure, charge, GPS or AWS operation is claimed.

Implemented HTTP/typed UI integration: `/sandbox` plus the authenticated `/api/v1/demo` catalog, durable operations/history/status, coupon previews, synthetic cab GPS, privacy preview, payment lookup, financial report and local deployment descriptor. Existing travel/payment booking APIs remain authoritative for their simulated lifecycles. OpenAPI 0.22 is additive; V25 preserves prior data.

## Executed checks

| Check | Result |
| --- | --- |
| Initial architecture/migration Maven run in isolated source copy | 19 passed: 18 architecture tests and one sequential migration test through V25. |
| Focused `clean test -Dtest=DemoIntegrationTest,DemoModeTest,ArchitectureTest,MigrationUpgradeTest` | 31 passed. Includes ten integration cases, two mode-gating cases, architecture and migration. |
| TypeScript, OpenAPI generated-client check/lint, compatibility against `/tmp/travbook-before-demo-apis.openapi.json`, optimized Next.js build | Passed. Four existing contract lint warnings retained. |
| `sh scripts/maven.sh -f /tmp/travbook-demo/apps/api/pom.xml clean verify -B` | **278 passed**, zero failures/errors/skips before the final receipt-review and route-allowlist hardening. |
| Final `clean test -Dtest=DemoIntegrationTest,DemoModeTest,ArchitectureTest,MigrationUpgradeTest` | **33 passed** against the final core/adapter and authorization changes: twelve simulator integration cases, two mode cases, eighteen architecture checks and one migration test. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/sandbox.spec.ts --output=/tmp/travbook-demo/browser-results` | **10 passed** (five desktop/five mobile), including actual HTTP cab booking/payment/refund integration, all new response schema checks, keyboard/axe/mobile width and private-cache hiding on account switch. |
| Final deployed backend and `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/sandbox.spec.ts --output=/tmp/travbook-demo/final-browser` | **10 passed again** after the final scoped authorization and outbound GPS adapter changes. Mobile screenshot visually inspected. |
| `terraform -chdir=infra/aws fmt -check -recursive`, `validate`, `test` | Passed; **five mocked provider tests**. No account plan/apply, STS call or cloud deployment. |
| `npm run contract:test`, `npm test`, `npm run requirements:test` | Nine contract/generator tests, six frontend helper tests and four requirement-mapping tests passed. |
| All eleven new OpenAPI schema examples compiled and validated with Ajv | Passed; generated TypeScript remains compatible with previous APIs. |
| `docker compose config --quiet`, `docker compose build backend frontend`, `docker compose up -d --wait --wait-timeout 180` | Passed; V25 applied successfully on the retained local database. Final backend rebuild incorporates scoped method allowlisting, exhausted-uncertainty review and the GPS provider port. |
| `docker compose exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 -U travbook -d travbook -c "select envelope::text from demo_event order by operation_id,version" \| node scripts/validate-simulation-events.mjs` | **60 committed simulator events validated** after the final browser run; no contact fields permitted. |

The integration cases verify ownership and scopes, explicit consent/unknown fields, stable duplicate command IDs, bounded retries/permanent failure, concurrency, event rollback, cursor ownership/pagination, profile preservation, coupon ownership/expiry/unchanged original price, exact local capture/refund totals, foreign payment denial and synthetic GPS stages/staleness/cancellation. A real child JVM commits a provider receipt and exits 33 before platform completion; an expired lease recovers the single receipt and rejects the old fence. No test asserts an invented external success.

Two initial test compilation issues (ambiguous Profile import and package-private configuration access) were corrected; no assertions were removed. The current source also binds simulator provider receipts to owner as well as kind/scenario.

Additional final tests cover an acknowledgement lost on the last attempt (lookup only, no fourth effect), untrusted receipt binding reaching terminal review without a false success, and HEAD/POST not bypassing a GET-only financial authorization rule. Static coordinate fixtures are isolated behind an outbound port. Editor diagnostics report no errors on the new application/adapter, UI and CI files. No dependency was added.

The full 278-test run and final 33-test run overlap; they are executed counts, not 311 unique tests or evidence that all original requirements are complete. CI now explicitly enables dummy APIs for its local Java/browser run and validates simulator events after Compose E2E; hosted CI has not been executed here. Existing broad browser suites from earlier increments are historical evidence, not a new full-platform browser run in this increment.

## Data and release limits

A restricted PostgreSQL backup is at `/tmp/travbook-demo/before-v25.dump`. Testcontainers use isolated databases; retained local history is preserved. Dummy operations retain audit events and receipts. Browser privacy simulation does not delete profiles or accounts; coupon preview does not change immutable quote/payment totals.

Browser-created cab fixtures are cancelled/refunded through existing owned APIs; history is retained. The simulator has no external delivery endpoint or real recipient field. No actual account-data deletion, cloud resources or external payment effects occurred. Raw local test logs/results are under `/tmp/travbook-demo/`.

Both authoritative source specifications retain their original hashes. The final isolated Maven source tree matches the workspace API source. No Git repository is present, so no commit/PR or base-branch diff is claimed. Local API/web/identity/PostgreSQL services are healthy; all 480 source rows remain in the generated RTM. Partial source capabilities remain In progress, not production Verified.

Production messaging, notification triggers/preferences/templates, coupon redemption/discounted checkout, complete account-data rights workflows, external settlement reports, live GPS/provider APIs and AWS deployment remain incomplete. Do not promote those P0/P1/P2 source requirements to Verified because a dummy endpoint responds. The local API sandbox is an explicitly simulated development capability.
