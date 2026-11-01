# MOCK cancellation/refund verification — 2026-10-31

Delivered an integrated full-booking MOCK cancellation/refund slice: accepted-policy quotation → explicit customer consent → durable supplier cancellation → independently pending/completed refund → current My Trips/status and document restrictions. No real supplier, bank transfer, provider ticket or AWS deployment is claimed.

## Scope and decisions

AC-031–035 map to BE-REF-001–009/013/014 and FE-TRIP-04/05/06. All 480 source requirements/scenarios and original priorities remain in the [RTM](requirements/rtm.md). Broader requirements stay In progress for real providers, partial cancellation, notifications, dispatch and audited operator resolution.

The original legacy policy is ambiguous about tax refunds; it is preserved and blocked from automatic cancellation quoting. New searches expose exact versioned full-total-refund or zero-total-refund fixture policies before purchase. These synthetic rules are not tax/legal advice. Cancellation quotes are immutable and expire after five minutes or first departure. One case per booking plus scoped keys prevents duplicate effects. Refunds cannot start before verified supplier cancellation, and positive pending amounts reserve captured entitlement against over-refund.

V3 detail/list reads explicitly combine historical checkout with current cancellation/refund. V2 retains its old schemas and fails closed with 409 for affected resources/pages. This is an intentional behavioral migration; the [ADR](adr/0007-mock-cancellation-refunds.md) and [runbook](runbooks/mock-cancellation-refunds.md) describe rollout constraints. Active aftercare blocks document access. Original capture entries/accounts remain unchanged; refunds append balanced reverse entries.

Key files: [Cancellation domain](../apps/api/src/main/java/com/travbook/booking/domain/model/Cancellation.java), [quote/policy](../apps/api/src/main/java/com/travbook/shared/domain/MockCancellationPolicy.java), [application service](../apps/api/src/main/java/com/travbook/booking/application/usecase/CancellationService.java), [worker](../apps/api/src/main/java/com/travbook/booking/application/usecase/CancellationWorker.java), [durable cancellation store](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/JdbcCancellationStore.java), [supplier adapter](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/DurableCancellationSupplier.java), [refund store](../apps/api/src/main/java/com/travbook/payments/adapter/out/persistence/JdbcMockRefunds.java), [V9](../apps/api/src/main/resources/db/migration/V9__cancellation_and_refunds.sql), [UI](../apps/web/src/features/bookings/CancellationPanel.tsx), and [OpenAPI](../contracts/openapi.json).

## Executed checks

| Command/check | Observed outcome |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=MigrationUpgradeTest,ArchitectureTest test -q` | 11 tests passed; V1–V9 migration chain preserves sentinel records |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=RefundIntegrationTest test -q` | Initial nine refund tests passed |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | Final **85 tests passed**, zero failures/errors/skips; Java artifact packaged. Earlier runs passed 84 before the final pending-reference regression test was added |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=RefundIntegrationTest,ArchitectureTest verify -B` | Intermediate 21 focused tests passed |
| `npm run test:e2e` | **44 desktop/mobile tests passed** after v3 migration, including real local OIDC, Java and PostgreSQL |
| `npm run test:e2e --workspace apps/web -- --project=desktop --grep 'AC-031/033/035'` | Deliberate diagnostic run reproduced a React duplicate sibling-key warning after a console assertion was added; one test failed on that assertion |
| `npm run test:e2e --workspace apps/web -- --grep 'AC-031\|AC-032'` | After fixing the keys, **all six aftercare browser tests passed** (1.3 minutes), including the retained render-error assertion |
| `npm run typecheck` | Passed |
| `npm test` | Six unit tests passed |
| `npm run contract:generate` / `npm run contract:check` | Generated client reproducible, no drift |
| `npm run contract:test` | Nine compatibility/generator tests passed |
| `node scripts/contract-compatibility.mjs .tools/pre-refunds.openapi.json` | Existing machine schemas retained, additive contracts; explicit legacy conflict behavior documented separately |
| `npm run contract:lint` | Valid with four existing warnings: private-license metadata, localhost and redirect-only login/logout responses |
| `npm run build` | Production frontend build passed; rerun after sibling-key fix recorded at handoff |
| `npm audit --audit-level=moderate` | **Zero vulnerabilities**; previous guarded dependency repair remains intact |
| `npm run requirements:generate` / `npm run requirements:check` | 480 source rows retained and mappings synchronized |
| Editor diagnostics for changed domain/application/UI/tests | No errors reported |

## Financial and recovery evidence

[RefundIntegrationTest](../apps/api/src/test/java/com/travbook/RefundIntegrationTest.java) contains twelve scenarios with real HTTP, signed JWTs and isolated PostgreSQL:

- Unsupported legacy policy, foreign owner, insufficient scope, missing authentication, false consent, client-supplied amounts and expired quotes are refused. No refund state is created by quoting or by rejected acceptance.
- Concurrent equivalent quote/cancellation keys produce one quote/case. Different equivalent keys recover the same ACK; changed scenario conflicts. ACK replay remains stable after expiry. Legacy v2 detail/list refuses to hide aftercare.
- Supplier cancellation is durably verified before refund submission. A CANCELLED case remains PENDING until the independent refund settler commits. Zero entitlement becomes NOT_DUE without a refund transaction.
- Captured payment state/history remains unchanged. Positive refund success has exactly two immutable balanced reverse entries. Incomplete journal completion is rejected at transaction commit.
- Pending commitments count toward the capture ceiling. Two concurrent 300.00 refunds against a 420.00 capture produce one accepted commitment and one rejection. Equivalent normalized money replays; key reuse with changed amount fails.
- Lost supplier and refund responses recover by operation lookup without duplicate effects. The delayed fixture cannot settle before its due time. Positive refund completion continues after cancellation-quote expiry once cancellation is established.
- Supplier lookup outage causes no cancellation mutation or refund; exhausted attempts enter cancellation REVIEW. Refund lookup outage preserves CANCELLED while refund enters REVIEW without issuing a new payment operation.
- A pending provider receipt arriving on the last allowed attempt retains its actual reference when escalating to REVIEW.
- Stale orchestration claims cannot commit. Settlement leases that expire during injected journal latency roll back journal writes and stop after five claims. Unknown outcomes never become false success.
- Actual child JVMs halt after supplier cancellation commit (exit 27) and after refund/journal commit (exit 28), before local workflow completion. Fresh JVMs recover from persisted state/receipts after test-only lease expiry, with one supplier cancellation and one refund per booking. Calls are asserted outside orchestration transactions. These are local recovery tests, not AWS DR drills.

Fixtures construct owned booking inputs through core ports for focused financial tests. [Browser cases](../apps/web/tests/checkout.spec.ts) cover the full public discovery → quote → traveller → checkout → cancellation/refund path, with keyboard consent, missing-CSRF denial, delayed refund visibility, zero-refund policy, lost browser acknowledgement, lost provider response, refresh/My Trips, cross-customer denial and protected-document blocking. They verify actual API schemas and amounts. Automated axe/overflow checks passed on desktop/mobile; this is not full WCAG certification. Auth/traveller traces remain disabled.

Visual inspection revealed a duplicate key shared by the checkout and cancellation sibling components. The console regression reproduced it, component-specific keys fixed it, and the final six-case run passed without render errors. No assertions were removed to pass. The original 44-case functional run did not monitor that warning; this limitation and correction are recorded explicitly.

## Local evidence and remaining gates

After the full v3 browser run, the preserved application database contained twelve verified supplier cancellations: eight CANCELLED/SUCCEEDED positive refunds and four CANCELLED/NOT_DUE cases. Eight refund aggregates had sixteen reversing journal entries with net debit-minus-credit 0.00. The over-refunded-account query returned zero. Sixty aftercare and sixteen refund events validated using:

```sh
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from booking_aftercare_outbox' | node scripts/validate-aftercare-events.mjs
docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from payment_refund_outbox' | node scripts/validate-refund-events.mjs
```

Further diagnostic/final browser runs append additional distinct MOCK bookings; final counts are recorded below. No local financial history was truncated. V9 is additive; earlier documents and quote policies remain immutable.

Final checks after the retained-reference and rendering fixes: the packaged backend passed all 85 tests; all six focused aftercare browser cases passed with render-error assertions; production frontend build, typecheck, contract/requirement drift and audit-zero checks passed. The final mobile screenshot was inspected without the duplicate-key issue badge. Final local counts: 19 CANCELLED cases (13 SUCCEEDED refunds, six NOT_DUE), 13 refund records, 26 reversing journal entries with net 0.00, and zero over-refunded accounts. All 97 aftercare and 26 refund event envelopes validated against their schemas.

Teardown: PostgreSQL and identity services were stopped with the volume/data preserved. Final checks found no listeners on ports 3000 or 8080. No Git metadata exists, so no commit/PR or hosted CI execution was performed. AWS was not provisioned.

New aftercare/refund outboxes are stored but not dispatched. Legacy policy review, partial passenger/segment cancellation, customer retries after rejection, audited REVIEW resolution, late outcomes after exhausted review, notifications, real providers/documents, remaining travel verticals, Terraform/AWS, production role separation and load/SLO/restore/DR evidence remain unfinished. All new effects are MOCK. No compliance, real payout, production readiness or end-to-end exactly-once claim is made.
