# Security, operations and six-pillar evidence

Current scope: local OIDC authentication, customer ownership, MOCK flight discovery, immutable quotations and durable booking intents with read-only validation/recovery. No production readiness or regulatory compliance is asserted. Source NFRs remain proposed targets; throughput, SLO, RTO and RPO attainment are unmeasured.

## Security boundary

| Threat | Implemented control | Evidence / residual work |
|---|---|---|
| Forged or misdirected authentication | Spring OIDC code flow, PKCE S256, state/nonce; access-token signature, issuer, API audience, expiry and scopes | Real Keycloak browser flows; signed JWKS/token negatives; external provider onboarding/MFA/registration/OTP still pending |
| Session theft/fixation or login lingering | HttpOnly SameSite=Lax JDBC session, login session rotation, ID-token expiry guard, idle timeout, CSRF-protected local/provider logout | Browser cookie/rotation/logout tests and persisted-session expiry test; HTTPS/Secure enforced outside explicit loopback local mode |
| Access/refresh token exposure | Tokens discarded server-side after authentication; browser storage contains no auth tokens | Browser checks; ID token/security context remains in privileged session storage; deployment encryption and DB roles are still required |
| Another customer reads/accepts a quote | Customer from verified issuer+subject; unique identity mapping; all quote queries scoped to owner; indistinguishable 404 for foreign/missing quote | Alice/Bob HTTP/browser tests; booking and checkout ownership implemented, protected document access pending |
| CSRF / forged financial-looking actions | CSRF for cookie quote mutations and logout; bearer requests still require valid JWT and scopes; no CORS allowlist | Missing/wrong-CSRF browser tests; no real charge/reservation operations exist; MOCK checkout requires explicit consent |
| Browser invents prices or owners | Create request accepts only offer ID; fresh provider read; BigDecimal/string money; immutable server-owned snapshot | Extra-field rejection, actual HTTP schema checks; original traveller-category/age eligibility is checked by the durable booking slice |
| Duplicate or conflicting commands | Durable customer/operation/key uniqueness, canonical fingerprint, one acceptance per quote, atomic command/resource/outbox commit | Concurrent same/different keys, changed-body conflicts and rollback tests |
| Historical pricing changes | No update endpoint; DB triggers reject quote/receipt/command changes and event-content changes; expiry derived separately | PostgreSQL immutability tests and migration guard tests; legal retention/archival policy pending |
| Unavailable or expired inventory | Bounded read-only supplier calls; explicit partial/empty errors; quote capped by current observation/departure; no inventory hold claim | Discovery/quote expiry tests; final booking inventory/compensation remains unimplemented |
| Malformed input / exhaustion | Domain/transport validation, JSON/form body bounds, parameterized JDBC, bounded supplier executor; process-wide limiter covers API and auth paths | Negative tests; distributed per-client limiting, production sizing and penetration testing pending |
| Sensitive diagnostic exposure | Safe problem responses and server-generated correlation IDs; no request/token dumps; auth browser network traces disabled | Tests inspect problem bodies; broader log/telemetry redaction audit still needed |
| Coupling across contexts | Pure domain, core-owned ports, inbound adapter isolation and no context cycles; pricing consumes published customer/search contracts | ArchUnit, including a new pricing boundary rule |

The local realm contains public synthetic account passwords and must never be deployed. Non-local OIDC login requires HTTPS origin/issuer and Secure cookies. The development profile is restricted to loopback. Missing identity configuration does not become a fake signed-in account. MOCK supplier mode remains independently enforced.

## AWS Well-Architected review

| Pillar | Selected target | Implemented increment | Evidence | Remaining release gates |
|---|---|---|---|---|
| Operational Excellence | ECS Fargate, CloudWatch, reproducible delivery | Local Compose/tasks; health/readiness; HTTP/provider metrics; CI configuration; auth/quote runbook | Local builds, database/API/browser evidence | Hosted CI, Terraform, deploy/rollback, distributed tracing, actionable alarms and on-call drills |
| Security | Isolated accounts, private network, IAM, KMS/Secrets Manager, WAF | OIDC/CSRF/session ownership, input limits, deny unimplemented routes, no browser tokens, dependency audit | Signed-token/CSRF/owner/session tests | External identity assurance/MFA, encrypted managed data, restricted runtime DB role, production TLS ingress and vulnerability/penetration assessments |
| Reliability | PostgreSQL authority, Multi-AZ RDS, durable workflows | Atomic quotes/intents/commands/outbox, immutable history, leased validation, fencing and bounded retry | Testcontainers contention/rollback/upgrades and actual child-JVM crash/restart | External event transport, effective reservation/payment recovery, restore/DR and provider reconciliation |
| Performance Efficiency | Modular deployment until measured split is needed | Bounded supplier budget; indexed owner/command lookups; no remote call in quote transaction; capped search pages | Functional deadline/concurrency tests only | p95, peak/burst/soak, pool sizing and contention benchmarks |
| Cost Optimization | Avoid premature cache/broker/service fleet; future budgets/tags | No AWS resources provisioned or paid provider calls; one local IdP and one DB | Config inspection | Cloud/provider cost metrics and capacity budgets |
| Sustainability | Reuse stable observations; bounded client work | Debounced location requests; no server quote polling; snapshot/filter reuse; validation services stopped after work | Browser behavior and teardown | Automated non-personal snapshot cleanup, session storage sizing, measured utilization/lifecycle improvements |

## Incomplete work

Booking intent acceptance and VALIDATE_OFFER v1 workflow execution/recovery are implemented. A MOCK authorize/reserve/capture strategy and cancel/void compensation are now implemented. External supplier/payment integrations, production ledger/settlement reconciliation, documents, customer cancellations/refunds, notifications, operations, remaining travel categories and AWS infrastructure remain unfinished. Account registration/OTP/recovery, editing/contact data, consent/deletion, saved travellers and privileged MFA remain separate P0/P1 work. There is no live supplier or payment integration.

Pricing events remain stored and undispatched. Booking events now have local durable delivery, immutable inbox/timeline receipts, ordering checks, bounded retries/quarantine and audited replay. External brokers and downstream payment/notification consumers are not implemented. A booking intent now copies the owned accepted quote, original category mix and normalized traveller details into immutable input, then revalidates fresh provider observations outside transactions. DB leases/fencing, bounded reads/backoff/jitter and an actual child-JVM crash/restart test establish this read-only recovery path. No reservation/financial effect is inferred from quote acceptance or validation completion.

Booking status/requirements/history require booking scopes and ownership. Names/DOB are kept out of URL/browser storage, summary responses, events and network test traces; denied refresh hides cached private data. Use fictional details locally. Managed encryption, restricted runtime DB privileges and PII retention/deletion are still required before real customer data. Normal row immutability guards do not protect against a privileged database administrator or TRUNCATE/DDL permission.

Authentication and API rate budgets are independently bounded (120 and 240 burst tokens, each refilling four per second). This fixes API traffic starving login capacity; neither per-client fairness nor distributed edge limiting is claimed. UI status polling stops at validation-terminal state or 20 reads, backs off to eight seconds and pauses in the background.

AWS Terraform and deployed application containers have not been delivered. V7 implements independent MOCK checkout/payment/supplier state and accounting fixtures; production financial/provider enablement remains pending. The broader architecture remains a target. All six pillar targets, legal/provider obligations, runtime DB least privilege and production security assessment remain release gates.

See [booking ADR](adr/0003-durable-booking-intent.md), [booking runbook](runbooks/booking-intents.md), [current evidence](verification-booking-intents.md) and the [RTM](requirements/rtm.md). Earlier pillar tables describe the foundation; booking adds atomic intent/workflow/outbox persistence, fenced restart recovery and private status/history. Production deployment, financial recovery and all unmeasured NFR gates remain unchanged.

Event-delivery increment: [ADR 0004](adr/0004-booking-event-delivery.md), [runbook](runbooks/booking-event-delivery.md), and [verification](verification-booking-events.md). Operational Excellence adds local backlog/outcome metrics and replay diagnosis; Reliability adds actual lost-ack JVM recovery, fencing, transactional publication rollback and idempotent receipt checks; Security adds owned history and strict privacy-safe envelope validation. Performance/Cost/Sustainability retain one modular process and bounded work without adding a broker. No AWS resources or production SLO, DR, load or compliance results are claimed.

## MOCK checkout increment

V2 adds owned, scoped and CSRF-protected explicit consent to simulated authorize/reserve/capture. Booking calls payments only through its published contract; ArchUnit rejects reverse coupling. Stable operation IDs, durable unknown-before-dispatch observations, provider receipt lookup, database-clock fencing, bounded attempts and cancel/void compensation protect the implemented MOCK sequence. Capture produces immutable balanced accounting fixtures. Real payment credentials are never collected. See [ADR](adr/0005-mock-checkout.md), [runbook](runbooks/mock-checkout.md) and [evidence](verification-mock-checkout.md).

Earlier incomplete-work statements describe prior increments. MOCK reservation/payment recovery and compensation are now implemented; external providers, hosted payment/webhook security, refunds, documents, checkout-event dispatch, settlement reconciliation and operator resolution remain outstanding. Operational Excellence adds checkout counters/runbooks and upgrade cautions; Reliability adds actual supplier-commit JVM recovery and failed local completion tests; Security adds resource/scenario binding and immutable journal guards. Performance, cost and sustainability remain unbenchmarked targets on one bounded modular deployment. No AWS resources were deployed. The prior development dependency audit finding is now resolved by the verified guarded adaptation described in ADR 0006; ongoing dependency scanning remains a release gate.

## Protected MOCK summary increment

Trips reads authoritative confirmed state through published booking contracts, persists bounded immutable text/hash/command/outbox atomically, and authorizes every metadata/content request. Birth dates and tokens never enter the file; names and itinerary remain private. No-store/nosniff attachment responses and denied-read UI hiding are verified. This adds local controls/evidence under Operational Excellence, Security and Reliability; database-backed 32KiB fixtures avoid unnecessary object storage under Performance/Cost/Sustainability, without claiming measured NFR attainment. Managed encryption, retention/deletion, provider-issued documents, refunds and production DB roles remain pending. See [ADR 0006](adr/0006-generator-security-and-mock-documents.md), [runbook](runbooks/mock-documents-and-generator.md) and [evidence](verification-documents-security.md).

## MOCK cancellation/refund increment

Explicit policy recognition prevents guessed legacy refund/tax entitlement. Ownership/CSRF/idempotency protect customer consent; provider cancellation must be verified before refund submission. Account locks reserve pending refund amounts and prevent totals exceeding captured funds. Immutable reversing entries preserve original captures. Two independently fenced, bounded workers handle orchestration and MOCK settlement; unknown outcomes enter REVIEW. V3 status and v2 conflict guards prevent outdated confirmation displays, and active aftercare blocks document access. [ADR 0007](adr/0007-mock-cancellation-refunds.md), [runbook](runbooks/mock-cancellation-refunds.md), and [verification](verification-cancellation-refunds.md).

Operational Excellence adds counters and safe diagnostics; Security adds consent/entitlement/version boundaries; Reliability adds actual process-loss recovery and transaction/lease tests; Performance/Cost/Sustainability retain bounded local work and no unnecessary new broker/compute fleet. No SLO/load/cost-efficiency targets are measured or AWS resources deployed. Alert routing, audited operator resolution, late outcomes after REVIEW, real providers, remaining travel verticals and production DB/identity controls remain release gates.

## Local application container increment

Earlier statements that application containers are absent describe prior increments. [Compose](../compose.yaml) now builds and runs the Java API and Next standalone frontend alongside the retained PostgreSQL volume and local fixture identity. The [dedicated local identity policy](../apps/api/src/main/java/com/travbook/platform/security/IdentityEnvironmentPolicy.java) allows an internal container bind only for the fixed MOCK topology; published ports remain loopback. The native loopback and external HTTPS/Secure-cookie guards still apply. No host-provided forwarded header controls the sign-in destination.

| Pillar | Implemented change and verification | Remaining risk |
|---|---|---|
| Operational Excellence | Reproducible multi-stage builds, health-gated startup, rotating application logs, graceful stop, Compose CI job and [runbook](runbooks/local-compose.md) | Hosted CI execution, image scanning/signing, alerts, automated rollback and rolling releases remain unverified |
| Security | Digest-pinned bases, non-root/read-only application containers, bounded tmpfs, dropped capabilities, no-new-privileges; separate public OIDC issuer and internal token/JWK paths retain validation | Local HTTP, public fixtures and privileged development database user cannot be promoted to production; host port isolation is an infrastructure control |
| Reliability | Preserved PostgreSQL volume/migrations and production-image integration/restart checks; [evidence](verification-compose.md) | Application restart does not establish backup/restore, Multi-AZ, DR or production supplier reconciliation |
| Performance Efficiency | Explicit local memory/CPU/PID bounds; frontend readiness checks a real proxy/catalog read | Limits are development defaults, not load-tested production sizing or SLO results |
| Cost Optimization | One API and one web process; cached build dependencies; no new managed services | Container image vulnerability scanning, cloud cost measurements and budgets remain pending |
| Sustainability | Standalone web runtime and JRE stage omit build toolchains/source; health probes avoid creating sessions; bounded logs/temp storage | No measured efficiency or energy improvement claimed; stop unused local services |

See [ADR 0008](adr/0008-local-compose-application.md). This delivery changes no historical cancellation policy or immutable financial record. Legacy policy review, audited operational resolution, external tickets/integrations, remaining travel verticals and AWS remain incomplete.

## Hotel discovery increment

The hotel context exposes anonymous read-only search/detail/selection observations with strict occupancy/date/body validation, rate limits, supplier deadlines and parameterized persistence. Explicitly permitted hotel POSTs do not reserve stock or create financial mutations; no guest PII is collected. Existing owned flight quote/booking endpoints remain unchanged. Architecture checks forbid hotel discovery reaching into flight pricing/booking/payments. All non-MOCK modes fail closed. No provider credential, live review, popularity or scarcity claim is introduced.

Operational Excellence adds hotel search/reprice outcome counters, a runbook and acceptance evidence. Reliability adds atomic snapshot/index writes, rollback/reconstruction tests, provider timeout/partial failure and rate-expiry handling. Security retains validation boundaries and exposes no guest/financial capabilities. Performance/Cost/Sustainability retain one modular process, bounded concurrency, paged snapshots and small local SVG assets; no benchmarks or measured efficiency claims. Physical search cleanup, distributed rate limiting, owned hotel quotations/reservations, alerts, production supplier governance and all AWS controls remain pending. [ADR](adr/0009-hotel-discovery-and-rate-selection.md) · [evidence](verification-hotels.md).

## Railway observation increment

Rail discovery adds constrained public station/search/journey/availability routes, canonical IDs, input/rate limits, safe errors and no railway credentials/passenger PII. Architecture tests prohibit coupling these observations to financial/booking contexts. No ticketing permission or confirmed ticket state is inferred from AVAILABLE/RAC/WAITLIST. Unsupported modes fail closed; PNR, reservation and live running-status APIs remain absent.

Operational Excellence adds outcome counters, additive migration/build gates and a diagnostic runbook. Reliability adds atomic snapshot/index writes, bounded providers, partial-vs-empty distinction, expiration and quota-boundary tests. Security preserves read-only scope and existing ownership/CSRF boundaries elsewhere. Performance/Cost/Sustainability retain one deployment, two bounded supplier threads, paged stored observations and no extra broker/cache or imagery downloads. No SLO/load, DR/restore, cloud cost or efficiency target is claimed. Physical retention cleanup, external provider/official-rule validation, passenger eligibility, durable train booking/payment, operational resolution and AWS remain pending. [ADR](adr/0010-railway-discovery.md) · [evidence](verification-trains.md).

## Owned hotel lifecycle increment

OIDC owner/scopes, CSRF, strict internal return paths, immutable/versioned consent, bounded guest fields and private data hiding protect the new hotel commands. Voucher authorization is repeated on each read/download; integrity/size checks and no-store attachment delivery protect the simulated artifact. Cancellation blocks voucher access. Events contain only states, with schema and database guards excluding guest fields. Quote/booking rows explicitly persist MOCK mode and owner/resource bindings; confirmation requires supplier confirmation plus captured payment.

Operational Excellence adds durable hotel activity, worker counts, event schemas, recovery fixtures and a runbook. Reliability adds transactional commands/outbox, per-night provider stock contention, database-clock lease fencing, cancel/void compensation, refund commitments/reversing entries, and real process-loss recovery after reservation/capture/cancel/refund commits. Security adds resource/version/deadline/guest and document controls while preserving prior scopes. Performance/Cost/Sustainability keep one modular API, bounded workers, indexed claims/stock queries, small immutable text vouchers and no new broker or cloud fleet; sizing/latency/cost/energy targets are not measured. Manual REVIEW resolution, external publication/notifications, provider onboarding, retention/encryption/production database roles, restore/DR and AWS remain release gates. [ADR](adr/0011-owned-hotel-lifecycle.md) · [verification](verification-hotel-lifecycle.md).

## Rail lifecycle increment

[Rail ADR](adr/0012-owned-rail-lifecycle.md), [architecture](rail-lifecycle-architecture.md),
[runbook](runbooks/rail-lifecycle.md) and [evidence](verification-rail-lifecycle.md) extend the
existing modular deployment without new AWS resources. All six pillars retain their release
gates: operational work uses durable/fenced recovery and a worker metric; security uses OIDC
ownership/CSRF, protected bounded documents and PII-free events; reliability uses canonical
capacity locks, receipt lookup, compensation and actual process-loss tests; performance uses
bounded query/page/poll/lease budgets but has no load benchmark; cost adds no broker/cache or
external paid call; sustainability reuses the existing processes and avoids redundant side
effects. Proposed SLOs, production least-privilege infrastructure, restore/DR, cost metrics and
measured sustainability improvements remain unverified. No AWS deployment was performed.

Threat cases tested: foreign quote/booking/PNR/document access, session switching during
mutation, return-path injection, missing CSRF, forged/contradictory effect receipts, changed
idempotent payload, expired quote, stock contention, stale lease, outbox rollback and process
loss. Synthetic PNRs are not real railway identifiers. Railway credentials are never collected.
Retention/deletion and authorized operational resolution remain release blockers.

## Operator recovery boundary

Hotel/rail REVIEW recovery now requires a verified administrator-controlled role and recent
signed MFA evidence, supports no force-status or amount adjustment, and commits immutable
actor-fingerprinted audit with state/work/outbox. Legacy checkpoints are not fabricated.
The [runbook](runbooks/review-recovery.md) describes access configuration and escalation.
[Evidence](verification-review-recovery.md) separates signed test-principal verification from
unperformed real IdP MFA enrollment/challenge. Operational Excellence and Reliability gain
constrained recovery; Security gains explicit privileged-access checks. No new AWS resources,
performance/cost/sustainability benchmark, compliance assertion or production release gate
is claimed. Flight reconciliation, arbitrary adjustments/approval workflows, retention and
production operator provisioning remain unfinished.

## Passenger aftercare and proven legacy outcomes

V16 retains original booking terms, uses explicitly accepted selected-passenger scopes,
checks remaining-party eligibility, preserves immutable cumulative refund history and rejects
obsolete document revisions. Per-quote stable provider/refund IDs and captured-fund limits
bound financial effects; API v1 fails closed where it cannot represent the current state.
Legacy persistence mapping is explicit; HTTP validation is not relaxed.

V17 can record a terminal legacy hotel/rail outcome only from current provider/payment state
and matching immutable receipts. Proof/audit/state/work/outbox are atomic and original
financial records are unchanged. Unproven outcomes remain REVIEW. The existing trusted
role/recent-MFA gate applies; real operator provisioning is still required. No new AWS
resources, achieved SLO, live integration or compliance result is claimed. See
[passenger evidence](verification-passenger-cancellation.md) and
[legacy evidence](verification-legacy-reconciliation.md).

Hotel deferred/amendment controls (ADR 0016): V2 enforces the same OIDC scopes, ownership and CSRF as existing hotel operations. The server chooses supplier-supported collection terms; browser money is never accepted. Expected booking version and single-use accepted quotes prevent conflicting amendment/cancellation commands. Original booking input, accepted quotes, provider receipts, closed amendment history and document revisions remain auditable. Deferred stays never imply a captured payment; unknown amendment outcomes block documents. No property no-show charge, prepaid adjustment, operator role grant or live provider enablement is present. Migration V18 preserves original payloads and financial entries; rollback compatibility and recovery are documented in the deferred-hotel runbook.

V19 hotel changes add owned booking/version-bound quotations and current-account funding verification before replacement authorization. Concurrent cancellation/change commands serialize on work then booking; used quotes cannot create standalone reservations. Replacement financial accounts remain immutable and refunds use distinct entitlements with capture caps. Pending changes block documents and cancellation; old v1/v2 detail/aftercare routes fail closed. Current guest manifests reference immutable provider receipts and remain owner protected; they are excluded from state-only events and documents omit birth dates. This does not implement production PII retention/deletion or external document validation. See the funded-hotel runbook for migration and rollback restrictions.

V20 bus/holiday/cab discovery retains global request-size/rate/correlation/no-store guards. Anonymous endpoints expose only observation/reprice operations; holiday quote/enquiry mutations require verified customer ownership, booking scopes, CSRF and scoped idempotency. Quote/enquiry FK binding and immutable command/result/outbox transactions prevent cross-owner or duplicate records. Contact fields are excluded from events, URLs and application logs; no external message is sent. These contexts have no payment/booking-internal dependency under ArchUnit. Provider pools/queues/deadlines and explicit partial coverage are operational controls, not measured production SLOs. Retention, dispatch, licensed integrations and the remaining booking workflows stay unfinished.

### Journey fulfilment additions (MOCK)

The [journey lifecycle](journey-lifecycle-architecture.md) adds owned quotes/bookings/documents under `bookings:read/write`, strict OIDC return paths and cookie CSRF. Customer-supplied prices are absent from mutation contracts. Passenger names reject controls; documents render as escaped text and download through authenticated reads, never public storage URLs. Confirmation requires matching supplier operation/resource/mode/fingerprint and a verified capture. Refunds use the original payment account; full cancellation quotes bind exact booking version and deadline. Supplier and platform writes have independent transactional boundaries bridged by immutable receipts. Event schemas forbid contact/PII fields. REVIEW is visible to the owner without a fabricated final outcome; privileged recovery for this new context remains unfinished. No production compliance or authorized supplier access is implied.
