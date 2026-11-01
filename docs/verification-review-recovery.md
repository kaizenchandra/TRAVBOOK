# Hotel/rail operator recovery verification — 2026-10-31

Delivered AC-055/056: restricted redacted queue/detail UI, trusted role/recent-MFA enforcement,
checkpoint/receipt-backed resumption, immutable operator audit and transactional state/work/
outbox updates. This is constrained recovery of an existing customer-approved workflow,
not arbitrary status correction or financial adjustment. Source requirements remain In
progress outside this implemented boundary.

## Actual execution

| Check executed | Outcome |
|---|---|
| `sh scripts/maven.sh -f apps/api/pom.xml clean verify` | **160 passed**, no failures/errors/skips. Includes six new review tests, added hotel recovery case, 15 architecture rules and V1→V15 upgrade preservation. Executable JAR built. |
| `sh scripts/maven.sh -f apps/api/pom.xml '-Dtest=OpsBrowserIT#desktopAndMobileUseRealMfaClaimProtectedApi' test` | **One explicit integration test passed**, exercising two real browser/API/database journeys at desktop/mobile widths. Actual resume, persisted audit, single reservation and single capture asserted. |
| `PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e --workspace apps/web -- rail-tickets.spec.ts stays.spec.ts` | **16 passed** (2.7m): customer rail/hotel lifecycle, OIDC, ownership, payment compensation, documents, cancellation/refunds and mobile regression. |
| `npm test` | **6 passed**. |
| `npm run typecheck` and production Next build | Passed. |
| `npm run contract:test` | **9 passed**. |
| `npm run contract:generate`; `npm run contract:check` | Generated OpenAPI 0.12.0 client matches. |
| `npm run contract:lint` | Valid, four pre-existing metadata/localhost/redirect warnings. |
| `node scripts/contract-compatibility.mjs /tmp/travbook-before-ops.json` | Additive compatibility with the saved 0.11.0 contract passed. |
| `npm run requirements:generate`; `npm run requirements:check` | **480 original source requirements/scenarios preserved**, including P0/P1 priorities. |
| `npm audit --audit-level=moderate` | **0 vulnerabilities**. |
| `docker compose build backend frontend`, final frontend rebuild, `docker compose up -d --wait --wait-timeout 180` | Passed; all four local services healthy, V15 applied without resetting the named PostgreSQL volume. |
| Editor diagnostics; CI YAML parse using existing `js-yaml` | No reported errors; CI syntax parsed. Hosted CI was not executed. |

The browser recovery gate uses the production Next UI and a real isolated Spring API and
PostgreSQL instance. Browser requests are forwarded to that API with short-lived signed
test-provider identities; business responses are not stubbed. This validates trusted claim
consumption, not real IdP MFA enrollment/challenge. An authorized IdP operator account and
its actual MFA policy have **not** been provisioned. Ordinary local users remain denied.
The browser tests also verify keyboard consent/submit, axe AA rules, responsive overflow,
OpenAPI response validation and immediate redaction after a denied refresh. This is targeted
accessibility evidence, not a whole-platform conformance claim.

## Correctness/security evidence

- Missing authentication, ordinary customers, missing operator role, missing MFA and stale
  MFA are denied. No request body/header supplies the actor identity or bypasses privilege.
- A committed supplier receipt lost to the original worker is recovered once. Hotel and
  rail final states are produced by their existing workers; capture entries remain one pair.
- Two operators racing the same booking/version produce exactly one accepted resumption.
- Equivalent operator/key replay returns the same immutable action even after the booking
  finishes. Changed payloads or stale versions conflict.
- Missing effect evidence causes409 and no effective supplier call. Legacy REVIEW without
  a checkpoint is explicitly non-resumable; no prior workflow state is invented.
- Audit update attempts are rejected by PostgreSQL. Injected outbox failure rolls back the
  recovery action, booking transition and work reset together.
- Three total resumptions bound manual retries. A fourth attempt remains blocked.
- Redacted views exclude customer/passenger input; audit records contain an opaque operator
  fingerprint, reason, booking/version and time. Private checkpoints stay in the owning
  context's database boundary and are not served through operations APIs.
- Hexagonal dependencies remain enforced: operations consumes a published recovery port,
  never hotel/rail tables, concrete repositories, supplier models or financial internals.

No new dependency/framework or deployment service was introduced. New migration V15 creates
immutable checkpoint/audit tables and queue indexes; previous migrations and financial
records are untouched. Event schema v1 gains Hotel/RailReconciliationResumed types with the
same state-only envelope shape; no external event consumer is configured or claimed updated.

## Requested work that remains

- Authorized railway ticketing, real PNR lookup, live gateways and externally valid hotel
  check-in documents: blocked on provider names, approved access/contracts and secure
  credentials. The platform remains explicitly MOCK; no fake integration was enabled.
- Partial-passenger/segment cancellation (BE-REF-010, P1): still pending item allocations,
  remaining-party eligibility, cumulative refund entitlement and document revision.
- Hotel modifications (BE-HTL-014, P0): still pending amendment quotes, price differences,
  supplier replacement and compensation.
- Supplier-supported pay-at-property (BE-HTL-012 P0 / FE-HT-16 P1): still pending a distinct
  deferred-payment strategy, liability rules and supplier-backed confirmations.
- Legacy missing-checkpoint, contradictory/no-receipt and flight REVIEW resolution; genuine
  IdP operator/MFA setup; approval-governed financial adjustments; production retention,
  monitoring, load/DR/security gates and AWS deployment remain unfinished.

See [ADR0013](adr/0013-constrained-review-recovery.md), [runbook](runbooks/review-recovery.md)
and the [requirements matrix](requirements/rtm.md). No production readiness, compliance,
real charge, real booking or achieved SLO is asserted.
