# Booking event delivery verification — 2026-10-31

Implemented the next booking-intent increment: atomic event scheduling/backfill, ordered local dispatch, immutable inbox/timeline receipts, bounded retry/quarantine, audited replay and owned history UI/API. This does not implement payment, supplier reservation, confirmation, cancellation/refund or a complete Phase 4/5 journey.

## Requirements and implemented path

AC-019–022 extend BE-BKG-017 and the existing FE-PAY-16 / FE-TRIP-02 / BE-TRIP-002 booking mapping. Original source IDs/priorities and all 480 register rows are retained. Broader source requirements remain In progress where unimplemented verticals or financial/provider behavior are still required.

`BookingStatus → BookingTimeline → useBookingTimeline → generated OpenAPI client → BookingHistoryController → BookingHistoryService → BookingEventInbox → PostgreSQL` is verified with real local OIDC ownership. The dispatcher path is `BookingEventScheduler → DispatchBookingEvents → BookingEventOutbox → BookingEventDelivery → RecordBookingEvent → BookingEventInbox`. Domain envelope/progression rules remain framework-free. The consumer's committed immutable history row is also its deduplication receipt. There is no external broker or provider mutation.

Key changes: [V6](../apps/api/src/main/resources/db/migration/V6__booking_event_delivery.sql), [event domain](../apps/api/src/main/java/com/travbook/booking/domain/event/BookingLifecycleEvent.java), [dispatcher](../apps/api/src/main/java/com/travbook/booking/application/usecase/BookingEventDispatcher.java), [outbox](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/JdbcBookingEventOutbox.java), [inbox](../apps/api/src/main/java/com/travbook/booking/adapter/out/persistence/JdbcBookingEventInbox.java), [timeline component](../apps/web/src/features/bookings/BookingTimeline.tsx), [OpenAPI](../contracts/openapi.json), [ADR](adr/0004-booking-event-delivery.md) and [runbook](runbooks/booking-event-delivery.md).

## Executed checks

| Exact command/check | Observed outcome |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=BookingEventIntegrationTest,MigrationUpgradeTest test -q` | Initial 8 focused tests passed before additional rollback/domain cases |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | 56 tests, zero failures/errors/skips; packaged Java application |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=BookingEventIntegrationTest,BookingEventDomainTest,ArchitectureTest test -q` | 18 tests passed after strict JSON type validation was added; includes 8 PostgreSQL/HTTP/process event tests, 2 domain tests and 8 architecture rules |
| `npm run test:e2e` | 30 desktop/mobile tests passed through real Next.js, Java, PostgreSQL and local Keycloak; automated axe/overflow assertions included |
| `npm run typecheck` | Passed |
| `npm test` | 6 tests passed |
| `npm run build` | Production Next.js build passed, including booking/trip routes |
| `npm run contract:generate` / `npm run contract:check` | Reproducible generated types; no drift |
| `npm run contract:test` | 5 compatibility tests passed |
| `node scripts/contract-compatibility.mjs .tools/pre-event-delivery.openapi.json` | Existing 0.3.0 contract retained; additive 0.4.0 timeline endpoint/schemas only |
| `npm run contract:lint` | Passed with four pre-existing warnings: private-license metadata, local server URL, login/logout redirect-only responses |
| `npm run requirements:generate` / `npm run requirements:check` | 480 source rows preserved and mappings synchronized |
| `docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from booking_outbox' \| node scripts/validate-booking-events.mjs` | 78 persisted local events validated against schema v1 after the full browser run |
| Local PostgreSQL consistency reads | All 78 delivery rows DELIVERED, 78 receipts, zero published events without receipts; migrations V1–V6 successful |
| Editor diagnostics for changed booking/test/UI sources | No errors reported |
| `npm audit` | **Failed: three moderate findings** in existing development-tool dependency chain; see below |

An initial attempt to pass `--grep` through the root npm wrapper was parsed as a test filename and reported “No tests found.” No application assertion failed in that attempt. The corrected full browser command executed 30 tests successfully.

Final targeted rechecks: `npm run test:e2e --workspace apps/web -- bookings.spec.ts` passed all 8 desktop/mobile booking tests after the privacy fix below (28.5 seconds). `sh scripts/maven.sh -f apps/api/pom.xml -DskipTests package -q` rebuilt the final Java artifact successfully after the 18-test focused backend recheck; this packaging command did not rerun tests. Contract drift, additive compatibility, requirement synchronization and editor diagnostics were checked again successfully.

The final `npm run build` also passed after the privacy changes. After the focused browser runs, local consistency/schema checks reported **126 DELIVERED events, 126 immutable receipts, zero published-without-receipt anomalies**, and all 126 envelopes matching booking schema v1. API/web processes were stopped by Playwright; `lsof` found no listeners on 3000/8080. `docker compose stop identity postgres` completed; Compose confirmed both stopped, with the PostgreSQL named volume preserved. No Git repository exists, so no commit/PR or hosted CI execution was performed.

## Failure and recovery evidence

[BookingEventIntegrationTest](../apps/api/src/test/java/com/travbook/BookingEventIntegrationTest.java) verifies:

- Published metadata is absent until a receipt commits; the acknowledgement adapter refuses a missing receipt.
- Six concurrent deliveries of one event produce one immutable history row. Same-ID changed content and a missing predecessor cannot advance history.
- Per-booking ordering and fencing reject stale acknowledgement/retry writes; different bookings progress independently.
- Five transient failures or abandoned claims quarantine the event; successor versions cannot bypass it. Replay requires a diagnosed reason and creates an immutable audit record.
- Unsupported schema/mode, extra fields, mismatched metadata and numeric-string coercion are rejected. Stored errors use bounded codes, without raw exceptions or payloads.
- A deliberately failed publication transaction rolls back delivery status/publication while preserving the independently committed receipt. Recovery redelivers safely.
- A real child JVM commits the consumer receipt then halts with exit 24 before publisher acknowledgement. A fresh JVM resumes on the same Testcontainer after simulated lease expiry: one receipt, one publication marker, two attempts. This is process-recovery evidence, not an AWS DR drill.
- HTTP timeline requires valid scope and ownership; other customers receive 404, unauthenticated reads 401, insufficient scope 403, invalid pagination 422. Responses exclude names, DOB, owner/correlation/causation IDs.

[Browser tests](../apps/web/tests/bookings.spec.ts) verify the accepted quote → intent → validation → delivered history path, API schema, [1,2,3] ordered versions, refresh, My Trips, denied owner reads and stale-cache hiding. A browser-injected 503 tests the history error/retry UI while the authoritative booking status remains visible; that injected outage is UI-state evidence, not a supplier outage. Desktop/mobile screenshots were generated, the mobile screenshot was inspected, and automated accessibility assertions passed; this is not WCAG certification. OIDC/traveller network traces remain disabled.

The final browser case also injects contract-shaped PENDING and ATTENTION_REQUIRED responses to verify their explanatory UI. It then switches to Bob's real OIDC session while retaining Alice's cached view: a denied **history** refresh hides the entire private booking view, including travellers, instead of only clearing timeline entries. A successful owned status read is required to restore the private view. This supplements the existing denied **booking status** refresh case.

## Security gate and remaining work

The current npm registry audit flags `minimatch@5.1.9` via `openapi-typescript@7.13.0 → @redocly/openapi-core@1.34.22`. The [reviewed advisory](https://github.com/advisories/GHSA-g3hv-75wg-m249) lists versions below 11.0.1 as affected. Registry inspection found no newer compatible 5.x release or generator release. The installed Redocly 1.x caller uses the callable CommonJS API, so a blind minimatch 11 override is not a validated compatible fix; npm's suggested `--force` action would downgrade the generator to 6.7.6. No force update, audit suppression or audit-threshold reduction was applied. Dependencies/lockfile remain unchanged. The existing CI `npm audit --audit-level=moderate` gate will fail until an upstream-compatible upgrade or separately reviewed migration is verified. No production readiness/security clearance is claimed.

Pricing dispatch, external broker/notification delivery, payment/reservation/compensation, documents/refunds, remaining travel verticals, production DB roles, hosted CI, Terraform/AWS deployment, load/soak/restore/DR evidence and all unmeasured SLOs remain outstanding. The readonly attachment supplied this turn was zero bytes; no hidden requirements were inferred or edited. Source specifications remain unchanged.
