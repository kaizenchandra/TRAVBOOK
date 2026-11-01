# Fictional flight-provider verification

Scope: OpenAPI0.23, additive V26, [ADR0024](adr/0024-fictional-flight-provider.md), local fictional airline/payment API. This is an API test-double increment; existing customer flight checkout has not been wired to these endpoints and no seat-picker UI is delivered.

## Acceptance register

| ID | Source requirement linkage (priorities unchanged) | Gate |
| --- | --- | --- |
| AC-090 | FE-FL-13 P0; FE-FL-14 P1; BE-FLT-011 P1 | Seat/meal/baggage catalogue → exact immutable itemized quote → explicit acceptance → exclusive reservation; stale quote/price/seat/eligibility failures do not allocate inventory. |
| AC-091 | BE-FLT-013/014/017/018 P0; BE-PAY-005 P0 | Separate payment/ticket states; idempotent capture; ticket rejection; restart/response-loss recovery; labelled owned JSON mock ticket. |
| AC-092 | FE-FL-19 P1 (provider-facing subset only) | API check-in needs ticket/payment/window/owned passenger; each mock boarding summary explicitly invalid for travel. Customer check-in links/UI remain unfinished. |
| AC-093 | BE-FLT-015 P0; BE-REF-001/008 P0 | Full pre-check-in cancellation releases inventory; captured money independently transitions PENDING refund → full simulated refund, duplicate-safe. |

## Execution

Executed 2026-11-01 against disposable Testcontainers PostgreSQL17.6 and actual Spring HTTP servers. There is no Git metadata in this workspace. Existing local Compose services were stopped at inspection and remain stopped; the retained application PostgreSQL volume was not modified or migrated.

| Check actually executed | Outcome |
| --- | --- |
| `sh scripts/maven.sh -f /tmp/travbook-flight-provider/apps/api/pom.xml clean verify -B` | **294 passed**, zero failed/errors/skipped; packaged JAR. Full regression snapshot before the final cancellation-policy field, strict decimal validation and added cookie/CSRF check. |
| `sh scripts/maven.sh -f /tmp/travbook-flight-provider-final/apps/api/pom.xml verify -B -Dtest=FlightProviderIntegrationTest,FlightProviderModeTest,ArchitectureTest,MigrationUpgradeTest` | **34 passed**, zero failed/errors/skipped; final source and JAR. 12 provider integration cases, 2 mode checks, 19 architecture rules, 1 migration upgrade. Counts overlap the full run; they are not 328 unique tests. |
| `node scripts/validate-flight-provider-contracts.mjs /tmp/travbook-flight-provider-final/apps/api/target/flight-provider-http.jsonl /tmp/travbook-flight-provider-final/apps/api/target/flight-provider-events.jsonl` | **36 actual HTTP responses**, all **14 operations** with successful responses; **15 examples** valid; **54 persisted events** schema-valid with contiguous aggregate versions. |
| `npm run contract:generate`, `npm run contract:check`, `npm run contract:lint` | Passed. Four existing lint warnings remain: private license metadata, localhost server and redirect-only login/logout. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-flight-provider/baseline.openapi.json` | Additive only relative to pre-increment OpenAPI0.22. |
| `npm run contract:test` | 9 passed. |
| `npm run typecheck`, `npm test`, `npm run build` | Passed; 6 frontend helper tests; Next.js production build passed. Generated contract regenerated and typechecked again after final examples; no customer UI changed. |
| `npm run requirements:generate`, `npm run requirements:check`, `npm run requirements:test` | 480 source rows preserved, no drift, 4 mapping tests passed. Source requirement text/priorities unchanged. |
| `node --check scripts/validate-flight-provider-contracts.mjs`; editor diagnostics for changed Java and validation script | Passed; no reported errors. |
| `docker compose config --quiet` | Passed. No Compose application startup, image deployment, retained-data migration or AWS operation executed. |

Isolated copies avoid output collisions with the Java editor: new directories under `/tmp/travbook-flight-provider[/final]/apps/api` received the project POM and source tree. Final `diff -qr` confirmed the final isolated source matches repository source. Initial compilation found a nested type-annotation placement and test checked-exception/captured-variable errors; corrected before the passing runs. A documentation-example count check initially failed; added the missing standalone selection/flight examples and reran successfully. No assertions were removed to pass.

The tests verify HTTP catalogue → itemized quote → exact acceptance → exclusive database allocation → separate capture/ticket → time-window passenger check-in → protected invalid-for-travel JSON documents. A separate path tests payment decline, ticket rejection after capture, cancellation and full refund. They assert persisted effects and balanced journal amounts, not just successful status codes.

Additional gates passed: owner and scope denial; untrusted extra fields; cookie POST requires CSRF; persisted session expiry; 16 KiB request-size rejection; synthetic passenger and exit-row eligibility; child/adult and unique selection rules; quote expiry and price/version rejection; two competing seat reservations produce exactly one winner; duplicate concurrent reservation/payment commands produce one effect; different payload under the same key conflicts; immutable original replay versus current GET; at most ten payment attempts; outbox insertion failure rolls back capture, receipt and journals; journal constraints reject incomplete/unbalanced effects; immutable history rejects edits; unknown source integration modes and disabled configuration fail closed.

An actual separate JVM committed a capture then exited with code 34 via `Runtime.halt`, before responding. Retrying the original operation in the surviving application returned the persisted receipt; one money effect and aggregate versions 1/2 remained. This proves recovery for an atomic **local mock-provider** operation, not distributed real-provider payment or ticketing recovery.

Not run: new browser seat-selection E2E (no such UI in this scope), full browser regression, authorized supplier/gateway tests, hosted CI, Terraform changes/apply, live check-in, performance/DR benchmarks. Neither local MOCK verification nor a green build establishes production readiness.

## Enable the delivered API locally

On the next local start, `docker compose up -d --build --wait --wait-timeout 180` builds this source and applies additive V26 through Flyway while preserving the volume. Existing Compose configuration already sets `DEMO_APIS_ENABLED=true` and `INTEGRATION_MODE=MOCK`; no provider secret is required. Sign in through local OIDC or supply an appropriately scoped test token, then use the [runbook's API sequence](runbooks/mock-flight-provider.md). This command is a documented next step, **not an executed deployment in this increment**.

## Remaining limits

No real airline/GDS/payment credentials, booking access, legitimate ticket/PNR, airline handoff or valid boarding pass. No consumer seat-selection/check-in screen or adapter connecting this provider contract to existing platform bookings. No timed hold expiry, infant/special-assistance handling, post-reservation ancillary changes, partial cancellation, webhook payment verification, external settlement, notification consumer, load/soak/DR evidence or live integration. The customer-facing source requirements remain In progress even when the scoped mock API acceptance tests pass.
