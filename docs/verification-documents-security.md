# Dependency security and MOCK document verification — 2026-10-31

Delivered AC-028–030: removed the existing three dependency-audit findings and added an integrated, owned MOCK confirmation-summary generation/download flow. This is not a real ticket, voucher, tax invoice, refund implementation or completed platform.

## Dependency repair

The installed generator remains openapi-typescript 7.13.0 with Redocly core 1.34.22. A scoped override installs the **actual minimatch 11.0.1 package**, the patched release listed in the [upstream advisory](https://github.com/advisories/GHSA-g3hv-75wg-m249). A [reproducible postinstall adaptation](../scripts/patch-redocly-minimatch.cjs) changes the single Redocly consumer from callable CommonJS import to the named export. It validates dependency versions and an exact source SHA-256, rejects unexpected upstream bytes, and is idempotent. Versions/advisories were not spoofed or suppressed; audit thresholds were not lowered.

A clean install reported **zero vulnerabilities**, with generated API types byte-identical to the pre-feature contract. Regression tests cover header-pattern matching (including non-matching hosts), nullable fields, escaped references, optional/required typing, repeat patching and unexpected-source refusal. The manifest/lockfile now pin the reviewed dependency chain. An isolated Redocly 2.72.1 replacement failed with an unexported ref-utils path and was not applied to the project. Registry lookup also confirmed no 2.6.1 core package; no assumption of that version was used in the fix.

## Document implementation

`BookingViews → TripDocuments → generated typed query/mutations → DocumentController → TripDocuments/DocumentService → ConfirmedJourneyPort / DocumentRenderer / DocumentStore` is connected through real browser/API/database tests. [BookingJourneyAdapter](../apps/api/src/main/java/com/travbook/trips/adapter/out/booking/BookingJourneyAdapter.java) consumes published booking ports; Trips does not access booking/payment private tables. Pure domain rules require a confirmed MOCK checkout, captured payment, confirmed supplier and MOCK references. A new architecture rule enforces this boundary.

[V8](../apps/api/src/main/resources/db/migration/V8__owned_mock_documents.sql) stores exact immutable text/hash/source-version metadata, durable commands and a privacy-safe creation event atomically. One document kind per owned booking and principal-scoped keys prevent duplicate generation. SHA-256 is checked on every load; UTF-8 content is bounded to 32KiB. [Download responses](../apps/api/src/main/java/com/travbook/trips/adapter/in/web/DocumentController.java) require ownership each time and use attachment/no-store/nosniff headers. User-controlled prices, references, owner and document bodies are rejected. Names and journey details remain private; DOB/tokens are omitted. Denied download access also hides the cached private booking view.

The [rendered summary](../apps/api/src/main/java/com/travbook/trips/adapter/out/format/PlainMockSummary.java) prominently says **MOCK CONFIRMATION SUMMARY — NOT A TICKET OR TAX INVOICE**. It records authoritative checkout references, timestamps, itinerary/timezones, traveller names, accepted price and fixture policies. Generated metadata and content persist across refresh; document creation does not change payment records. Events remain stored, not dispatched.

## Commands and results actually executed

| Command/check | Result |
|---|---|
| `npm install` | Reviewed overrides/lockfile applied; postinstall verified its exact source/dependencies; zero audit findings |
| `npm ci` | Clean reproducible installation and guarded patch succeeded; zero vulnerabilities. Optional macOS fsevents lifecycle-permission warning only; no added script permission granted |
| `npm ls minimatch @redocly/openapi-core openapi-typescript` | Confirmed generator 7.13.0 → core 1.34.22 → minimatch 11.0.1 |
| `npm audit --audit-level=moderate` | **Passed: zero vulnerabilities** |
| `npm run contract:test` | **9 tests passed**, including four generator/patch regression cases |
| `npm run contract:generate` / `npm run contract:check` | Reproducible generated client; no drift |
| `node scripts/contract-compatibility.mjs .tools/pre-documents.openapi.json` | Existing 0.5.0 schemas retained; additive 0.6.0 document APIs only |
| `npm run contract:lint` | Valid; four existing warnings for private-license metadata, localhost server and redirect-only auth responses |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=DocumentIntegrationTest,MigrationUpgradeTest,ArchitectureTest test -q` | 15 focused tests passed |
| `sh scripts/maven.sh -f apps/api/pom.xml verify -B` | **73 tests passed**, zero failures/errors/skips; packaged Java application |
| `npm run typecheck` | Passed |
| `npm test` | 6 tests passed |
| `npm run test:e2e` | **38 desktop/mobile tests passed**, 2.1 minutes; real Next.js, Java, PostgreSQL and local Keycloak |
| `npm run build` | Production Next.js build passed |
| `npm run requirements:generate` / `npm run requirements:check` | 480 original source requirements/scenarios retained; document mapping updated |
| `docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from trip_document_outbox' \| node scripts/validate-document-events.mjs` | Both persisted document creation events matched schema v1 |
| Editor diagnostics for changed Trips/test/UI/patch sources | No errors reported |

## Acceptance evidence

[DocumentIntegrationTest](../apps/api/src/test/java/com/travbook/DocumentIntegrationTest.java) uses signed JWTs, actual HTTP and isolated PostgreSQL. It proves authorization-only and reservation-before-capture states cannot issue a document; successful capture enables it; failed checkout remains blocked. Concurrent same-key and different-key requests yield one immutable document/event. Changed booking under the same key conflicts. Foreign customer, insufficient scope, unauthenticated and mismatched booking/document requests are rejected. Metadata excludes the body, and events exclude names/DOB/pricing text.

The same suite verifies UTF-8 byte count/hash/header correctness, stable repeat download, immutable history guards and rollback when event insertion fails: no body, command or event survives that failed transaction, and the original key can safely retry. Fixture booking inputs are constructed through core ports to focus these tests; the browser test below verifies the full public search/quote/traveller/checkout chain. [TripDocumentTest](../apps/api/src/test/java/com/travbook/TripDocumentTest.java) rejects tampered/unlabelled bodies and multi-byte content that exceeds the byte budget despite a smaller character count. V1–V8 migration checks preserve earlier data.

[Browser tests](../apps/web/tests/checkout.spec.ts) verify real sign-in → discovery → accepted quotation → traveller intent → MOCK checkout → prepare → actual downloaded file → refresh. They compare downloaded bytes with metadata hash/length, verify the non-ticket label and omission of DOB, deny missing-CSRF creation, and test Bob's denial both through HTTP and by switching the active session before clicking Download. The private view disappears on denial. Desktop/mobile automated accessibility checks pass; no full WCAG certification is claimed. The downloaded mobile text artifact was inspected. Browser auth/traveller traces remain disabled.

Preserved local PostgreSQL observations after browser verification: two MOCK summaries, two command records, two creation events, largest file 1,183 bytes, zero missing creation events, migrations V1–V8 successful. Prior booking/payment data is preserved. Testcontainer fixture truncation was never run against the local application database.

After inspecting the downloaded text, the renderer was refined to print departure/arrival in the saved journey's local timezone with explicit offset, rather than an equivalent UTC representation. Existing immutable files were preserved. `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=DocumentIntegrationTest,TripDocumentTest verify -B` then passed all six focused tests and repackaged the application; `npm run test:e2e --workspace apps/web -- --grep 'AC-029/030'` passed both desktop/mobile download cases (19.8 seconds), including `+05:30` assertions. Component keys bind mutation retry state to the current booking/customer. Typecheck, the nine contract/generator tests, additive compatibility, requirement/client drift and audit-zero checks passed again.

The final production frontend build passed after those refinements. Final local counts are four immutable summaries/commands/events (largest 1,209 bytes), with all four event envelopes validating against schema v1. No API listener remained on port 8080. A frontend development server (PID 9926, started before the browser verification commands) remained on port 3000; its ownership was not established, so it was left running. The browser runner permits reuse of existing servers. `docker compose stop identity postgres` completed with both services stopped and PostgreSQL data preserved. No Git repository exists, so no commit/PR was created.

## Remaining work

The source document requirements remain In progress for actual provider tickets/vouchers/invoices, other travel services and approved retention/deletion policy. Customer cancellation/refund, checkout/document event publication, reviewed operational recovery, real hosted gateway/webhook integration, remaining travel verticals and Terraform/AWS deployment are unfinished. Dependency audit zero is a point-in-time scan result, not production security/compliance evidence. No live supplier/payment operation, hosted CI execution or production SLO/DR benchmark is claimed.

See [ADR 0006](adr/0006-generator-security-and-mock-documents.md), [runbook](runbooks/mock-documents-and-generator.md), [plan](plan.md) and [RTM](requirements/rtm.md). The application entry point remains the existing [TravelApplication](../apps/api/src/main/java/com/travbook/TravelApplication.java); it did not need replacement.
