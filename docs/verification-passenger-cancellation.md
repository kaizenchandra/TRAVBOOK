# Passenger cancellation verification — 2026-10-31

AC-057/058 is implemented in LOCAL_OIDC + deterministic MOCK. This does not provide real
railway cancellation rights, partial-segment changes, real PNRs or valid travel documents.
Original booking terms are immutable; the selected-passenger policy is separately quoted
and explicitly accepted. BE-REF-010 remains P1 and broader provider/segment scope incomplete.

## Executed evidence

| Command/check | Actual result |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | Final combined source: **172 passed**, no failures/errors/skips; executable JAR built. |
| `sh scripts/maven.sh -f apps/api/pom.xml -Dtest=PassengerCancellationIntegrationTest clean test` | Final exact-index parser check: **6 passed**; rejects fractions, accepts integral values such as 1.0. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- passenger-cancellation.spec.ts rail-tickets.spec.ts stays.spec.ts` | Final images: **20 passed** (3.5m), desktop/mobile customer journeys. |
| Explicit `OpsBrowserIT` checkpoint + legacy method gates | **2 tests passed**, four real browser/API/database journeys; details in [legacy evidence](verification-legacy-reconciliation.md). |
| `npm run typecheck`; production Next build | Passed. |
| `npm test`; `npm run contract:test` | **6** frontend tests and **9** contract/generator tests passed. |
| `npm run contract:generate`; `npm run contract:check`; `npm run contract:lint` | Types match OpenAPI 0.14.0; lint valid with four existing warnings. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-before-partial.json` | Existing 0.12.0 contract retained; additive v2 aftercare and operator paths. |
| `npm run requirements:generate`; `npm run requirements:check` | **480** original source requirements/scenarios retained with priorities. |
| Docker image builds and `docker compose up -d --wait --wait-timeout 180` | Passed; V16/V17 applied to preserved named PostgreSQL volume; four local services healthy. Final API image `8f12d1382438`, web `f0f2ca4bd514` (linux/arm64). |
| `npm audit --audit-level=moderate` | **0 npm vulnerabilities**. |
| Editor diagnostics | No errors reported in changed rail/hotel/operations backend and frontend features. |

The existing quote/booking creation flow remains v1. Current detail, PNR, aftercare and
revisioned document clients use v2. The browser tests use real local OIDC and the deployed
Next/Spring/PostgreSQL stack, not stubbed success responses. They verify a partial cancellation,
remaining tickets, delayed/lost responses, revised hash-checked document, rejection of a
selection leaving a child alone, and final cancellation/refund of the remaining party.
The API additionally verifies changed-key payload rejection and simultaneous equivalent
commands returning one result. Server data determines every entitlement and total.

Backend tests verify two sequential refunds, exact allocation release, duplicate/stale
selection rejection, concurrent competing quotes, owner isolation, v1 guards, delayed
settlement and actual child-JVM exits after supplier cancellation/refund commit. Existing
operation IDs are retained for legacy pending whole-party effects. A dedicated V15→V16
migration fixture verifies that old booking JSON, original entitlement/reference and inactive
allocations survive without creating a payment/refund operation. Explicit persistence readers
supply only the additive legacy fields; API creator/unknown-field validation remains strict.

The document revision is returned from its immutable stored record. Downloads check both
current booking eligibility and revision. V2 booking state and completed refund history are
read under one booking lock. Original quotes and prior results are never overwritten.

## Corrections found during verification

- Strict persistence deserialization initially rejected old records missing the new passenger
  fields. Explicit adapter compatibility fixed that; defaulting was not enabled globally.
- The first browser run found secondary-button hover contrast 4.47:1. Hover text was darkened;
  unchanged axe assertions passed on desktop/mobile afterward.
- An exact-index parser revision exposed a Java type-use annotation/import error in a clean
  container build. The import was corrected and clean tests/builds rerun. Fractional indexes
  cannot be truncated, while valid integer-valued JSON numbers remain accepted.

No assertion was removed or weakened; no known introduced failure remains in the checks above.
Targeted axe/keyboard/responsive evidence is not a whole-platform conformance certification.

## Local database checkpoint after final customer tests

Rail fixtures: 71 CANCELLED, 42 FAILED; no active fixture left by these journeys. Duplicate
per-passenger supplier cancellation groups: 0. Overallocated active services: 0. Refund totals
exceeding captured payment amounts: 0. Capture journal 142 entries, net INR 0.00; refund journal
166 entries, net INR0.00. Counts include preserved earlier MOCK fixtures; no local truncation.

The following command validated **1160** stored rail lifecycle envelopes with no passenger
payload fields:

```sh
docker compose exec -T postgres psql -U travbook -d travbook -At -c \
  "select envelope::text from rail_ticket_outbox order by created_at,id" \
  | node scripts/validate-rail-events.mjs
```

Remaining requested work: hotel amendments/pay-at-property; unproven legacy/flight recovery;
authorized supplier/gateway integrations, real IdP operator/MFA provisioning and valid check-in
or travel documents. Partial-segment cancellation, notifications, external event transport,
production load/DR/security gates and AWS remain incomplete. No real charge, reservation,
refund, compliance result or production readiness is claimed.
