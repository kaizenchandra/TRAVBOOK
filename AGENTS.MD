# AGENTS.md

## Purpose and scope

Build an independent travel platform inspired by MakeMyTrip: flights, trains, buses,
hotels/homestays, holidays, and shared customer, pricing, booking, payment, trips,
cancellation/refund, notification, and operations capabilities. Deliver cabs and other
P1/P2 enhancements at their recorded priorities. Use independent branding and licensed
assets; do not copy proprietary code or imply supplier access that does not exist.

These instructions apply throughout the repository. Follow applicable directory
instructions and higher-priority instructions. This file defines engineering rules;
it does not assert that any repository layout, command, integration, or feature exists.

## Start every implementation task

- Read the task, applicable repository instructions, available master prompt, frontend
  PRD, and backend FRD. Inspect the actual repository, working changes, manifests,
  wrappers, CI, architecture, and tests before proposing changes. Preserve unrelated work.
- If no repository is supplied, say so. Produce only the requested supported artifacts;
  never claim repository inspection, implementation, or passing tests without evidence.
- If a referenced specification is absent, record the gap. Preserve known requirements
  and proceed on independent work; do not invent missing requirement text or IDs.
- Choose the smallest useful vertical slice and maintain a short plan for substantial
  work. Implement, integrate, verify, and update documentation within the requested scope.
  Do not finish an implementation request with only diagrams, scaffolding, or a roadmap.
- Resolve routine choices independently. Record significant decisions in ADRs. Ask only
  when unresolved ambiguity materially affects correctness, security, supplier rights,
  compatibility, or irreversible operations. Do not enable live financial/supplier
  effects or deploy infrastructure without authorization covering those actions.

## Requirements and traceability

- Preserve source identifiers, acceptance conditions, provider constraints, and P0/P1/P2
  priorities. Namespace collisions with `FE-`, `BE-`, `NFR-`, `INT-`, and `AC-`; retain
  the original identifier and source reference. Never silently downgrade or omit P0 work.
- Maintain a requirements register and requirements traceability matrix (RTM) in the
  existing documentation location; otherwise use `docs/requirements/`. Record conflicts,
  assumptions, external dependencies, and explicit resolutions.
- Each RTM row must link: business outcome and source IDs; priority; acceptance criteria;
  route/component/hook; API operation; use case; aggregate; inbound/outbound ports;
  adapters; tables/migrations; events; authorization; failure/idempotency behavior;
  test IDs and execution evidence; and status (`Not started`, `In progress`, `Blocked`,
  `Verified`). Use explicit `N/A` with a reason where a layer is irrelevant.
- Follow: requirement → acceptance criteria → domain/contract → backend/adapters/data
  → typed frontend integration → tests. Mark `Verified` only when the required acceptance
  checks actually pass. Record mocked, sandbox, and live evidence separately.

## Stack and architecture

- Frontend: Next.js, React, TypeScript; shared accessible components/design tokens;
  Tailwind CSS, TanStack Query, React Hook Form and Zod where appropriate. Keep client
  state minimal. Reuse compatible established choices instead of gratuitous rewrites.
- Backend: Java 21, a supported compatible Spring Boot release, Maven, PostgreSQL,
  Flyway, Spring Security/OIDC, and OpenAPI. Use Redis only for suitable caching/ephemeral
  coordination. Pin compatible dependencies using existing lockfiles/BOMs.
- Start with a modular backend. Contexts include identity/customer, catalog/location,
  supplier connectivity, search, pricing/offers, booking, payments, ledger/reconciliation,
  trips/documents, notifications, holidays, operations, and analytics. A context does not
  automatically require a microservice. Justify new frameworks and deployment boundaries.

### Mandatory Clean/Hexagonal backend boundaries

Organize by business context, then layer; adapt paths to the actual repository:

```text
<context>/domain/{model,event,service}
<context>/application/{port/in,port/out,usecase}
<context>/adapter/in/{web,messaging}
<context>/adapter/out/{persistence,supplier,payment,messaging}
<context>/config
```

- Dependencies point inward: inbound adapter → application use case → domain;
  outbound adapters implement core-owned ports. Composition/configuration wires adapters.
- Domain owns aggregates, immutable value objects, policies, invariants, and legal state
  transitions. No Spring/JPA annotations, HTTP clients, AWS/messaging SDKs, supplier DTOs,
  or persistence dependencies in the domain.
- Application owns use cases, ports, authorization coordination, workflow and transaction
  boundaries. Do not depend on concrete adapters, repositories, or provider SDKs. Keep
  framework transaction implementation in boundary/configuration code where feasible.
- Controllers/consumers parse and validate transport input, resolve principals, invoke
  inbound ports, and translate results/errors. They must not access persistence directly
  or own business rules. Frontend validation is for usability; authoritative rules remain
  in the backend.
- Persistence entities and external DTOs stay in adapters and map to core models. Contexts
  communicate through published contracts, not another context's tables or internals.
- Enforce dependency direction, no cycles, controller isolation, and supplier/persistence
  model isolation with ArchUnit or equivalent automated architecture tests.

## API-first frontend/backend integration

- Define/update OpenAPI before implementing HTTP behavior. Generate the TypeScript client
  and keep generation reproducible; do not hand-edit generated files or scatter duplicate
  response types. CI must detect contract/client drift and incompatible changes.
- Specify schemas, validation, authentication/ownership, status/error codes, pagination,
  idempotency, versions, money/time conventions, freshness, and examples. Use stable error
  codes with `application/problem+json` and correlation IDs; expose no internal secrets.
- Flow: page → feature → query/mutation hook → typed client → controller → inbound port
  → use case/domain → outbound port → adapter. Any Next.js BFF must preserve this ownership.
- Cover locations; vertical searches/details/availability/repricing; quotes; booking
  creation/status/My Trips; payment intents/status/webhooks; cancellation quotes/requests;
  refunds and protected documents. Extend contracts for customer, offers, cabs, and admin
  requirements. Do not treat illustrative endpoint paths as already implemented APIs.
- Backend owns prices, inventory validation, booking confirmation, payment/refund decisions,
  and financial records. Never trust browser totals or gateway return URLs as proof.
- Build mobile-first responsive screens targeting WCAG 2.2 AA: semantic controls, keyboard
  access, visible focus, labels, readable contrast, reduced motion, and adequate touch targets.
  Preserve navigation/search state; expose loading, empty, partial, stale, validation,
  authentication-expired, failure, retry, and asynchronous pending states where relevant.
- Require explicit acceptance of changed prices. Show backend-authoritative booking,
  reservation, payment, and refund states. Use bounded polling/backoff and terminal-state
  detection. Do not optimistically confirm financial actions or leave functional-looking
  controls without implemented behavior.

## Supplier and payment modes

- Implement provider-independent ports and explicit `MOCK`, `SANDBOX`, `LIVE` modes for
  each integration. Default local/test operation to deterministic `MOCK`; credentials
  being present must never implicitly switch to `LIVE`.
- Label simulated results, payments, references, tickets, and documents in UI/API metadata
  and generated artifacts. Never present fabricated PNRs or mock confirmations as real.
- Permit `SANDBOX`/`LIVE` only through authorized provider APIs and explicit environment
  configuration. Fail closed on missing credentials or invalid configuration; never
  silently fall back to mock. Guard against accidental mixed-mode fulfilment.
- Maintain a supplier capability matrix: authorization/onboarding status, modes, search,
  reprice, hold, reserve, ticket, status, cancel/refund, limits, timeout/retry semantics,
  idempotency, error mapping, and contract tests. Respect railway authentication/berth
  rules and provider-specific enquiry or deferred-payment workflows.
- Bound search fan-out, concurrency, timeouts, retries, and costs. Normalize results,
  disclose freshness and partial failures, and never translate an outage into false
  zero availability. Do not bypass commercial restrictions with unauthorized scraping.

## Booking and financial correctness

- Model separate persisted state machines for booking, reservation/item, payment, refund,
  and workflow execution. Payment success alone never means booking confirmation.
- Use immutable, versioned, expiring server quotes; revalidate inventory and price before
  commitment. Use `BigDecimal` with currency and explicit rounding rules, never floating
  point. Store instants consistently and preserve journey-local timezone information.
- Protect every booking/payment/cancellation/refund mutation against duplicate effects:
  durable idempotency records scoped to principal and operation, request fingerprints,
  database uniqueness, atomic claims, and safe concurrent replay. Reject key reuse with
  changed payloads; return the original operation/status for equivalent retries.
- Persist workflows and external references so workers resume after crashes. Support
  provider-specific hold/pay/confirm sequences, expiry, compensation, and partial package
  fulfilment. Never hold a database transaction open during a remote call.
- A remote timeout is an unknown outcome, not proof of failure. Persist uncertainty and
  reconcile by provider reference/status before repeating any potentially effective call.
  Bound retries with backoff/jitter; retry side effects only when safely idempotent.
- Atomically commit local state and outbox events. Make consumers tolerant of at-least-once
  delivery through durable deduplication, ordering/version guards, bounded retries, DLQs,
  and documented replay. Do not claim end-to-end exactly-once effects.
- Verify webhook signatures over provider-required payloads, freshness/replay protections,
  deduplication, expected amounts/currencies/references, and legal state transitions.
  Handle delayed and out-of-order callbacks; acknowledge according to provider protocol
  only after durable acceptance. Use hosted/tokenized payment collection.
- Keep financial/audit records immutable; correct through linked adjustment entries.
  Apply double-entry semantics when platform responsibilities require them. Reconcile
  payment, supplier, refund, and settlement records; expose unresolved cases to operations.
  Tickets/vouchers require actual supplier confirmation in the configured mode.
- PostgreSQL is the transactional authority. Use constraints, version checks/locking,
  safe Flyway migrations, tested indexes, and explicit transaction boundaries. Redis and
  in-memory locks are not sufficient authorities for financial or booking invariants.

## Security and AWS Well-Architected controls

Enforce OIDC, resource ownership, least privilege, privileged-operation MFA, secure
sessions, input/rate limits, CSRF protection where applicable, constrained CORS, TLS,
encryption, secret management, PII redaction/retention, and protected document downloads.
Never commit credentials or log tokens, payment data, or unnecessary traveller details.
Assess applicable regulatory/provider obligations before live enablement; controls do
not constitute evidence of compliance.

For each AWS pillar, record decisions, resources, implemented controls, verification
evidence, and residual risks:

| Pillar | Required engineering evidence |
| --- | --- |
| Operational Excellence | Terraform/IaC, CI/CD, health/readiness, redacted logs, traces, metrics, actionable alarms, runbooks, deployment/rollback validation. |
| Security | Environment isolation, least-privilege IAM, KMS/Secrets Manager, network protection, audit trails, threat model, vulnerability/security checks. |
| Reliability | Multi-AZ design for production, durable workflows/outbox, bounded retries/bulkheads, reconciliation, backups, restore/DR and failure-recovery tests. |
| Performance Efficiency | Measured latency and capacity, bounded supplier calls, suitable caches/indexes/pools, profiling, load/soak tests, justified scaling. |
| Cost Optimization | Budgets, tags, sizing/autoscaling, retention controls, supplier call budgets, cost per search and confirmed booking. |
| Sustainability | Efficient utilization, fewer redundant calls/transfers, storage lifecycle policies, idle-resource cleanup, measured efficiency improvements. |

Prefer ECS Fargate, RDS/Aurora PostgreSQL, suitable Redis, SQS/EventBridge, S3, CloudFront,
WAF, and managed observability where justified. EKS/Kafka require a documented need.
Keep infrastructure reproducible and separate environments; planning a deployment is
not authorization to execute it.

Preserve source NFRs. Record initial targets as proposed until validated: owned critical
booking API availability 99.95%, search 99.9%, aggregated search p95 ≤3 s, durable command
acceptance p95 ≤500 ms, RTO ≤60 min, RPO ≤5 min. Treat 500 searches/s, 50 booking commands/s,
and 5× bursts as illustrative load hypotheses. Define measurement windows/workloads;
separate owned latency from supplier latency. Never report targets as achieved results.

## Phased delivery and acceptance gates

Deliver complete vertical slices; phase numbers organize scope and do not authorize
unrequested expansion. Each phase records requirements, dependencies, frontend/backend,
contracts, migrations, infrastructure, tests, evidence, and outstanding blockers.

| Phase | Exit gate |
| --- | --- |
| 0 — Discovery | Actual repository/source assessment, RTM/backlog, supplier constraints, ADRs, boundaries and proposed NFRs documented. |
| 1 — Foundation | Next.js and Spring Boot run together locally; auth, database/migrations, OpenAPI/client, deterministic mocks, CI and architecture checks work. |
| 2 — Catalog/search | Integrated home, locations, category searches and partial/error states pass acceptance tests. |
| 3 — Selection/quotes | Detail, availability/repricing, policies and immutable quotations work through the UI and API. |
| 4 — Booking/checkout | Durable booking workflow, idempotency, outbox, contention and compensation pass deterministic failure tests. |
| 5 — First complete vertical | Choose by authorized sandbox availability, or explicitly demonstrate MOCK; payment, reservation, confirmation, My Trips and documents pass end-to-end recovery tests. |
| 6 — Remaining P0 verticals | Flights, trains, buses, hotels/homestays and holidays have verified provider-aware paths; access-blocked paths remain explicitly incomplete. |
| 7 — Post-booking/operations | Cancellation, full/partial refunds, reconciliation, notifications, audit and support paths pass acceptance tests. |
| 8 — Hardening | Security, compatibility, load/soak, failure injection, restore/DR, cost and all six pillar reviews have release evidence. |
| 9 — Enhancements | Deliver prioritized P1/P2 features without weakening core booking/payment correctness. |

## Verification and definition of done

- Discover real commands from manifests, Maven wrappers, package scripts, README and CI.
  Record the exact commands used; do not invent scripts or label proposed commands as run.
- Backend: JUnit/domain/use-case tests; Testcontainers persistence/migration tests;
  OpenAPI/provider contracts; ArchUnit; resource authorization; concurrency, idempotency,
  webhook, outbox/replay, reconciliation and crash/restart tests.
- Frontend: type/lint/build checks, component/form/API-state tests, accessibility and
  responsive checks, and Playwright integrated journeys. Unit mocks alone do not verify
  frontend/backend integration or authorized supplier operation.
- Cover category search/selection; checkout; expired quotes/changed prices; exhausted
  inventory; duplicate/concurrent booking, payment and refund requests; failed payments;
  payment success with supplier pending/failure; late confirmation after timeout;
  duplicate/out-of-order callbacks; cancellation with delayed refund; unauthorized access;
  partial supplier outage; mobile/keyboard use; checkout refresh; worker restart; and
  event replay. Assert persisted state and external effect counts, not just HTTP success.
- Run relevant checks, fix introduced failures, and report pre-existing failures and
  unavailable prerequisites separately. Never weaken tests or remove assertions to pass.
- A feature is done only when its acceptance criteria, cross-layer contract, architecture,
  security/correctness checks, migration compatibility, observability and necessary
  operational documentation are verified. Blocked provider access does not equal done.

## Documentation and completion report

Keep the RTM, plan, ADRs, OpenAPI/event schemas, migrations, supplier matrix, state/sequence
diagrams, local setup/environment examples, CI/IaC, threat model, runbooks, and NFR evidence
aligned with actual implementation. Reuse existing locations; create documentation as
needed for the delivered scope. Document reproducible local operation using deterministic
mock providers without external credentials.

At handoff report:

1. Delivered behavior and requirement IDs; key changed files and decisions.
2. Verified UI → API → use case → provider/persistence paths and their integration modes.
3. Exact checks executed and outcomes; distinguish passed, failed, skipped, and not run.
4. Remaining requirements, assumptions, provider/access blockers and material risks.
5. Migration/configuration/operational steps and the next actionable work.

Never claim real reservations, compliance, production readiness, benchmarks, or passing
tests without supporting evidence. Never describe disconnected screens, unused endpoints,
or placeholders as a completed customer journey.
