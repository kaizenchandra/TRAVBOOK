# Customer flight and organizer delivery evidence

Scope: OpenAPI0.24, additive V27, [ADR0025](adr/0025-customer-flight-and-organizer.md), AC-094–096. Full-platform completion is **not** claimed; [remaining requirements](requirements/remaining-work.md) remain open.

## Delivered behavior

- `/flight-services`: authenticated interactive seat map, passenger age/eligibility, backend meal/baggage catalogue, exact quotation and acceptance, persisted reservation, separate mock payment and ticket outcomes, passenger/time-window check-in, protected JSON test-document download, full cancellation/refund and owned cursor-based history. Linked from homepage, offer details, account and My Trips. Existing original supplier offers are not silently converted to this fixture.
- `/account/travel`: owned, consented saved travellers, category shortlist, flight search criteria and preferences. Saved traveller DOB supplies age for the new flight selection; fresh searches go through existing discovery. Scoped export/clear actually reads/removes organizer fields, while bookings and financial records remain unchanged. External messaging is not enabled by preference storage.
- Mobile bottom navigation and recovery of unsubmitted non-personal flight search drafts across category navigation; removed stale flight copy claiming all mock checkout was unavailable.
- API additions: GET fictional-provider reservations (20-item owned cursor page), GET/PUT `/api/v1/me/travel-organizer`. All writes preserve backend ownership and version/idempotency decisions. Generated types remain the single frontend contract source.

## Verification status

Executed on 2026-11-01 using Java21, Spring Boot4.0.9, Next16.4.3, PostgreSQL17.6, local OIDC and explicit MOCK providers. No Git metadata exists in the workspace; no commit or push was performed.

| Executed check | Result |
| --- | --- |
| `sh scripts/maven.sh -f /tmp/travbook-completion/apps/api/pom.xml verify -B` | Final full run: **300 passed**, zero failures/errors/skips; packaged JAR. `/tmp/travbook-completion/full-backend-final.log`. |
| Same Maven command with `-Dtest=FlightProviderIntegrationTest,OrganizerIntegrationTest,ArchitectureTest,MigrationUpgradeTest` | Final focused run: **37 passed**. Includes13 provider cases,4 organizer cases,19 architecture rules and1 migration upgrade. These overlap the full 300, not337 unique tests. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/flight-services.spec.ts --output=/tmp/travbook-completion/browser-verified` | **6 passed**, desktop/mobile. Actual frontend → API → owned PostgreSQL/provider state; keyboard seat selection, lost payment response with same-key retry, separate ticket issuance, download, cancellation/refund/history, check-in and saved organizer/search draft. |
| Same browser command for `tests/discovery.spec.ts tests/quotations.spec.ts tests/customer-profile.spec.ts` | Initial26-case regression:23passed/3failed. Fourteen account/quotation cases passed; three discovery failures were resolved as described below. |
| Same browser command for `tests/discovery.spec.ts --output=/tmp/travbook-completion/discovery-final` | Final **12 discovery cases passed**, desktop/mobile, including keyboard-only flow and Axe checks. Across focused runs, all 32 distinct selected browser cases passed; the full existing platform browser suite was not rerun. |
| `node scripts/validate-flight-provider-contracts.mjs /tmp/travbook-completion/apps/api/target/flight-provider-http.jsonl /tmp/travbook-completion/apps/api/target/flight-provider-events.jsonl` | **38 actual HTTP responses** cover successful execution of all 15 provider operations;15 examples validate;98 persisted event envelopes validate with contiguous aggregate versions. Organizer HTTP/ownership/replay assertions are in OrganizerIntegrationTest. |
| `npm run contract:generate`, `npm run contract:check`, `npm run contract:lint` | Passed. Four pre-existing lint warnings remain: private license metadata, localhost URL, redirect-only login/logout. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-completion/baseline.openapi.json`; `npm run contract:test` | Additive compatibility with0.23 passed; **10 checker/generator tests passed**. Fixed the checker to recognize additive HTTP methods on existing slash-containing paths while still rejecting shared required constraints and changed existing methods. |
| `npm run typecheck`; `npm test`; `npm run build` | Passed;6 frontend helper tests. Final frontend also compiled in its Docker image after UI fixes. |
| `npm run requirements:generate`; `npm run requirements:check`; `npm run requirements:test` |480 source rows retained without drift;4 mapping tests passed. Source specifications/priorities were not edited. |
| `docker compose build backend frontend` and subsequent focused frontend/backend rebuilds; `docker compose up -d --wait --wait-timeout 180 backend frontend` | Passed; frontend, backend, Keycloak and PostgreSQL are running healthy. Last frontend rebuild includes mobile navigation and stale-page selection protection. |
| Read-only `fp_money_effect`/`fp_journal` balance check on retained local database |0 journal pair/count/amount violations. No real funds or settlement assertion. |
| Editor diagnostics and `diff -qr apps/api/src /tmp/travbook-completion/apps/api/src` | No reported changed-code errors; tested isolated backend source matches repository source. |

Backend evidence includes owner/expected-account denial, version conflicts, same-key replay returning current data, concurrent duplicate protection, private-value clearing without personal snapshots in receipts, invalid date/list/search/path rejection, rollback when audit persistence fails, immutable financial/event history, owned cursor isolation and paging, seat contention, expiry and price guards, bounded payment attempts, CSRF/session expiry and crash-after-commit recovery. Application/domain dependencies continue to pass ArchUnit.

Browser checks include Axe WCAG2.2AA-tag scans on tested states, viewport overflow checks and screenshots; these are scoped accessibility evidence, not a complete manual WCAG certification. Current-time check-in executed during the open fixture window locally. The test also asserts the disabled closed-window branch when run during that window elsewhere. Checked-in mock reservations and audit/financial history are retained, not reset for test convenience.

### Failures corrected

- V27 initially contained a malformed SQL dollar-quote delimiter. Disposable migration checks caught it; corrected before retained-data migration.
- A new22-date provider history test overlapped dates allocated by other tests, yielding4 legitimate seat-contention errors in the first300-test run. Isolated each test's fixture date range; all original inventory assertions remained and the final full 300passed.
- Meal-control exact label and hidden desktop-navigation selectors caused initial browser timeouts. Tests now address visible accessible controls and the available home link; no acceptance assertion was removed.
- Discovery tests still expected obsolete copy claiming all booking/payment was disabled. They now assert the correct warning that no real money or valid travel ticket is issued.
- Regression testing exposed selectable previous-page offer links during placeholder loading. The UI now disables those links until the requested page is available; the sold-out and changed-price assertions pass.
- The compatibility checker treated slash-containing OpenAPI path names as nested path-item levels and incorrectly rejected the added GET history operation. JSON-pointer escaping and method-specific additive handling fix that false positive; new tests ensure existing security/parameter constraints remain protected.

Not executed: full platform browser suite, production/authorized supplier tests, hosted CI, load/soak/DR/restore benchmarks, regulatory validation or AWS deployment. No critical unresolved failure was found in the selected final checks; broad remaining product functionality is still incomplete.

## Operational notes

The local stack was initially stopped. PostgreSQL/Keycloak were started for integration verification and a mode0600 custom-format backup was taken at `/tmp/travbook-completion/before-v27.dump` (3,668,481bytes). It contains the retained application data; treat it as private. Docker images were built and additive V26/V27 applied successfully from the prior V25 database. No volume reset, financial-history rewrite, external-provider call or AWS deployment was performed.

Compose already enables the MOCK demo provider. Disable `DEMO_APIS_ENABLED` to remove provider APIs; the real platform-owned organizer remains available under OIDC. API/runtime state and provider restrictions remain in the [provider runbook](runbooks/mock-flight-provider.md).

No blanket statement that all functional requirements, regulatory obligations, production SLOs or provider integrations are satisfied is supported by this evidence. Coupons, full privacy, notification workers, broader administration/reporting and many enhancement/NFR gates remain actual implementation work.
