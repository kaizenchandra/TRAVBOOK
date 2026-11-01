# Coupon checkout and lifecycle notification evidence

Executed 2026-11-01. OpenAPI 0.25, additive V28/V29, [ADR0027](adr/0027-coupon-redemption-and-lifecycle-notifications.md), AC-099/100. This is a verified increment, **not full-platform completion**. All provider effects remain MOCK; no real email/SMS, payment or reservation is claimed. The [480-row RTM](requirements/rtm.md) preserves original requirements/priorities, earlier evidence and remaining gaps.

## Delivered paths

- `/flight-services` → generated v2 quote client → authenticated controller → FlightProvider/CouponClaims → published Coupons → policy/award → PostgreSQL. Invalid coupon input retains the selected seat. Exact gross, discount, net and consumption policy are displayed before explicit acceptance. Coupon redemption, seat allocation and reservation commit together. Payment/refund use the discounted server total. The original quote API and old quotes remain compatible.
- Producer-owned lifecycle outboxes → eight `LifecycleSource` feeds → ProcessNotifications → durable owned inbox/channel jobs → preference recheck → idempotent mock gateway receipt → fenced delivery completion. `/notifications` shows historical state, current delivery outcomes, protected booking links, owned pagination and read markers. No traveller/contact data enters the notification projection.
- Campaign checks include scope, dates, policy version, minimum spend, customer eligibility and customer/global limits. Coupons are currently available only for fictional flight checkout. Global usage is serialized at reservation commitment; quotes do not guarantee remaining campaign capacity. Cancellation/payment failure do not restore consumed uses, as disclosed to the customer.

## Executed checks

| Exact command/check | Actual result |
| --- | --- |
| `sh scripts/maven.sh -f /tmp/travbook-commerce/apps/api/pom.xml verify -B` | Full isolated-source run: **312 passed**, zero failed/errors/skips; packaged JAR. This preceded the final internal scope argument/check. Log: `/tmp/travbook-commerce/full-backend.log`. |
| `sh scripts/maven.sh -f /tmp/travbook-commerce/apps/api/pom.xml -Dtest=CommerceIntegrationTest,FlightProviderIntegrationTest,ArchitectureTest,MigrationUpgradeTest verify -B` | Final current-source run: **45 passed**: 9 commerce, 13 provider, 22 architecture and 1 migration. Overlaps the full312; do not add counts. Log: `/tmp/travbook-commerce/final-current.log`. |
| `diff -qr apps/api/src /tmp/travbook-commerce/apps/api/src` | No source differences after final focused tests. Isolated build avoids editor/compiler races; no test assertions removed. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- tests/commerce.spec.ts tests/flight-services.spec.ts --reporter=list --output=/tmp/travbook-commerce/browser` | **8 passed**, desktop/mobile. Real local OIDC/browser → Next → Java → retained PostgreSQL → mock provider. Log: `/tmp/travbook-commerce/browser.log`. |
| `node scripts/validate-flight-provider-contracts.mjs /tmp/travbook-commerce/apps/api/target/flight-provider-http.jsonl /tmp/travbook-commerce/apps/api/target/flight-provider-events.jsonl` |15 examples,39 HTTP responses across all16 v1 provider operations,98 persisted provider events passed. New v2 quote/promotion/notification schemas are also asserted in the browser scenario. |
| `docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from coupon_outbox order by id' \| node scripts/validate-coupon-events.mjs` |2 actual committed coupon envelopes passed versioned schema, unique event/award and causation checks. Added the same gate to Compose CI. Hosted CI was not executed. |
| `npm run contract:generate`; `npm run contract:check`; `npm run contract:lint`; `node scripts/contract-compatibility.mjs /tmp/travbook-commerce/baseline.openapi.json` | Generated types match; OpenAPI valid; additive compatibility with0.24 passes. Four existing warnings remain: license metadata, local server and redirect-only login/logout responses. |
| `npm run contract:test`; `npm test`; `npm run requirements:test` |10 compatibility/generator,6 frontend helper and4 mapping tests passed. |
| `npm run requirements:generate`; `npm run requirements:check` |480 source rows retained without drift. Source text/priorities unchanged. New mappings remain In progress where full-source functionality exceeds this scope. |
| `npm run typecheck`; `npm run build`; `docker compose build backend frontend` | Passed. Latest images include final coupon scope check and frontend refinements. |
| `docker compose up -d --wait --wait-timeout 180 backend frontend`; `docker compose ps` | Passed; all four local services healthy. V28/V29 applied from V27, retained data preserved. |
| Read-only lifecycle/delivery inspection | All8 feeds forwarded events;0 quarantined envelopes and0 forwarding retries at inspection.20 channel jobs:17 SIMULATED_DELIVERED,3 SUPPRESSED. These are local receipts, not external delivery proof. |
| `git diff --check`; editor diagnostics for changed code | Passed/no reported errors. No commit or push performed by this task. |

The commerce integration tests assert authoritative discounted totals and net refunds, immutable old quotes, usage/policy rejection, concurrent final global use, reservation/event-failure rollback, owned notification/read access, event replay/fingerprint conflict, stale aggregate versions, preference withdrawal, historical no-send behavior, malformed-envelope quarantine and transaction rollback. A separate JVM commits a mock notification receipt and halts; lease expiry/recovery preserves one provider effect and rejects stale completion. Unknown outcomes exhaust three attempts into REVIEW without a fabricated receipt.

Browser scenarios additionally cover invalid-code recovery, accepted discount, refresh during checkout, explicit payment/ticket/refund actions, opted-in delivery, read-marker CSRF/ownership, and existing seat/meals/check-in/saved-traveller flows. Axe scans and viewport-overflow checks pass on the tested inbox states. Screenshots are in the browser output directory. This is scoped accessibility evidence, not complete WCAG certification.

## Failures, migration and limits

An initial integration run found a duplicate test clock bean name. The test-specific bean was renamed; final tests pass. There are no unresolved failures in the selected final checks. No full platform browser rerun, fresh dependency audit, cloud/real-provider test, throughput benchmark, manual accessibility audit, restore drill or hosted CI execution is claimed for this increment.

Before migration, a private mode0600 PostgreSQL custom-format backup was taken at `/tmp/travbook-commerce/before-v28.dump` (4,164,266 bytes). V28/V29 passed disposable migration-upgrade tests and applied successfully to the retained V27 local database. No volume reset, financial-history rewrite or destructive rollback occurred. Services remain running for review. A backup file alone is not restore/DR evidence.

Remaining work includes coupon use in other category checkouts and promotion administration; external message adapters/contact verification, reminders, editable/localized templates and notification REVIEW/dead-letter operations; full privacy/retention, broader settlement/reporting/support, remaining travel capability breadth, P1/P2 and production/AWS gates. These are explicit [unfinished requirements](requirements/remaining-work.md), not assumed complete because mocks exist. See the [operational runbook](runbooks/commerce-notifications.md) before enabling or recovering these workers.
