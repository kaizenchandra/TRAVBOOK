# Owned hotel lifecycle verification — 2026-10-31

Scope: AC-046–050. The enabled full-booking **MOCK pay-now** hotel path now covers owned quotation → acceptance → guests → durable reservation/payment → protected accommodation voucher → My Trips/history → cancellation quote/consent → supplier cancellation → refund tracking. This is not real hotel inventory, bank settlement, a valid check-in voucher or a production release.

## Delivered implementation

- `stays` domain/application/inbound/outbound/config boundaries, with published customer/hotel/payment contracts and automated architecture checks.
- Immutable quotes/acceptance/guest inputs; owner-scoped idempotency/fingerprints and one booking per quote.
- Fresh whole-room terms revalidation; per-night supplier stock; authorize/reserve/capture; cancel/void compensation; fenced leases, bounded retry/review and stable provider operation references.
- Per-room cancellation entitlement/deadlines, zero-refund handling, mixed-plan partial monetary refunds, delayed settlement and immutable reversing entries through existing payment interfaces.
- Owned no-store, bounded, hash-verified MOCK voucher and state-only activity timeline; hotel cards in My Trips; bounded UI polling, refresh/response-loss recovery and private data hiding on denied access.
- OpenAPI 0.10.0 with generated client; V12 lifecycle/stock tables plus additive V13 mode/resource/state/event guards. Applied migrations were not edited. Existing data is retained.

## Checks executed

| Command/check | Actual result |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify -B` | **137 tests passed**, zero failures/errors/skips in the final clean, serial run; includes check-in cutoff, contradictory receipt and Unicode guest-name tests |
| Focused `StayIntegrationTest,StayReturnPathTest,ArchitectureTest,MigrationUpgradeTest` and hotel discovery regression runs | Passed after correcting the delayed-refund test clock sequence; no assertion removed |
| `npm run typecheck` | Passed |
| `npm test` | 6 frontend unit tests passed |
| `npm run contract:generate` / `npm run contract:check` | Generated types match |
| `node scripts/contract-compatibility.mjs /tmp/travbook-pre-stays.openapi.json` | Existing contract retained; additive changes only |
| `npm run contract:lint` | Valid; four existing license/localhost/redirect-only warnings |
| `npm run contract:test` | 9 compatibility/generator tests passed |
| `npm audit --audit-level=moderate` | Zero reported npm vulnerabilities; not an OS image scan |
| `npm run requirements:generate` / `npm run requirements:check` | 480 source IDs/priorities preserved, hotel lifecycle mappings added |
| `docker compose build backend frontend` plus final backend/frontend rebuilds | Built packaged Java/Next standalone images |
| `docker compose up -d --wait --wait-timeout 180` | Updated local stack healthy, persistent PostgreSQL retained |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- stays.spec.ts` | **8 passed** in 1.3 minutes: desktop/mobile full lifecycle, response-loss recovery, ownership, compensated failures, mixed/zero refunds |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e` | **68 passed** in 4.2 minutes: existing flight/hotel/train/auth/payment/refund journeys plus hotel lifecycle. Executed before the final isolated check-in-window tightening; final hotel-only retest follows below. |
| Final `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- stays.spec.ts` after cutoff/receipt/name/recovery tightening | **8 passed** in 1.4 minutes on final images; includes a deliberately lost browser cancellation acknowledgement as well as provider response loss |
| `docker compose exec -T postgres psql -U travbook -d travbook -At -c 'select envelope::text from stay_outbox order by created_at,id' \| node scripts/validate-stay-events.mjs` | **512** persisted lifecycle envelopes validated after final verification; guest payload fields forbidden |
| Editor diagnostics | No implementation/test errors reported after cleanup |

The first focused mixed-room delayed-refund test advanced its clock before the refund had been submitted, so it correctly remained pending. The test was fixed to assert durable refund submission before advancing the settlement clock; SUCCEEDED and ledger-count assertions remain. Subsequent targeted/full backend runs passed. Additional mode/binding guards were delivered in V13 because V12 had already been applied locally.

Final audit tightened the local check-in boundary: new quotes cap expiry at property check-in, and external authorization/reservation windows use the effective minimum of recorded expiry/check-in. Existing immutable history is not rewritten. A quote created at 13:59 for 14:00 check-in is tested across authorization and expiry: reservation rejects, authorization voids, no capture journal or active room allocation remains. Read models use the same effective expiry for historical quotes.

Receipt checks also bind operation ID, resource, mode, amount/currency, reference and accepted/result state before advancing. A contradictory captured receipt during authorization is rejected regardless of its accepted flag. An intermediate overlapping Maven invocation reused generated test classes and produced a NoSuchMethod failure; a newly added fixture also collided with an adjacent two-night test stay. No product assertions were removed: ordinary fixture dates now use disjoint three-day intervals, the explicit overlapping-stock contention test is unchanged, and the final Maven run used `clean verify` serially. Guest names now normalize Unicode NFC and support combining marks (including Indian-script names); punctuation-only/markup values remain invalid.

## Correctness evidence

Backend tests cover ownership/401/404 and consent, expired acceptance/booking, room-age validation, immutable quotation enforcement, simultaneous duplicate booking submission and changed-key-payload conflict, one reserve/capture, balanced entries, definitive provider/payment rejection, cancel/void compensation, lost reservation/capture responses, competing nightly stock with one winner, stock release, voucher ownership/hash/privacy, mixed-plan and zero refund, expiry/deadline boundaries, duplicate cancellation/refund safety, fenced stale completion, bounded unknown lookup → REVIEW, and transactional rollback when outbox insertion fails.

Real child JVMs terminate with exit 29 **after** RESERVE, CAPTURE, CANCEL and REFUND effects commit. The refund case settles the reversing entries before process termination. Restart/resume checks assert the expected booking/refund result and unchanged provider/capture/refund effect counts. These are process-loss tests, not in-memory object recreation. Provider effects are asserted to run outside orchestration transactions.

Browser tests exercise actual local Keycloak login with validated hotel return context; creation/acceptance of a private quote; guest checkout; current status; protected voucher download with SHA-256 and no DOB; cancellation with delayed/lost responses; My Trips; lost booking acknowledgement recovered after refresh; missing-CSRF rejection; foreign customer API denial and private guest-data hiding after an account switch; authorization/reservation/capture failures; and exact mixed/zero refund amounts. Tests clean up their successful hotel stays by invoking supported cancellation APIs, not by truncating data. Authentication network traces are disabled.

Mobile confirmation screen was visually inspected. Tested confirmation states pass axe WCAG 2.2 AA checks and horizontal-overflow checks on desktop/mobile. This is not a site-wide accessibility certification. The document is explicitly **NOT VALID FOR CHECK-IN** and its response is an authorized UTF-8 attachment rather than a real provider document/PDF.

Read-only database checks after full browser regression found 28 completed hotel workflows (16 cancelled, 12 failed), zero overallocated room-nights and zero over-refunds. The 32 hotel capture entries balanced to INR 0.00 and 24 reversing refund entries balanced to INR 0.00. These are correctness checks on test fixtures, not financial/production benchmarks. Test-created confirmed bookings were cleaned up through owned cancellation APIs; no history or database volume was deleted.

Final images are running locally with all four Compose services healthy, PostgreSQL at V13 and existing data preserved. Start at <http://127.0.0.1:3000/hotels>; hotel history is also in My Trips and `/stays/trips`. Final verification includes additional targeted reruns; earlier fixture counts above are historical checkpoints rather than an asserted fixed inventory.

Final database checkpoint after those reruns: 56 DONE workflows (32 CANCELLED, 24 FAILED); zero overallocated nights and zero over-refunds; 64 capture entries and 48 refund entries each net to INR 0.00. These totals include only MOCK hotel fixtures and their preserved history.

## Remaining release gates

The implemented MOCK lifecycle does not deliver in-place amendments, partial guest/room cancellations, pay-at-property, external provider overbooking/reaccommodation updates, official vouchers/invoices, external notification/event delivery or audited operator REVIEW resolution. These requirements retain their original priorities; source RTM rows stay In progress where broader scope remains. Recorded late arrival is not a guaranteed provider service.

No live supplier/gateway, production compliance, hosted CI run, managed secrets/database roles/encryption/retention, load/SLO/DR/restore evidence or AWS deployment is claimed. Existing shared payment/refund limitations remain applicable. See [ADR 0011](adr/0011-owned-hotel-lifecycle.md), [runbook](runbooks/hotel-lifecycle.md), [supplier matrix](suppliers.md), [event schema](../contracts/events/stays.v1.schema.json), and [RTM](requirements/rtm.md).
