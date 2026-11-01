# Bus, holiday and cab fulfilment — local MOCK

Requires the existing PostgreSQL, OIDC identity, API and web stack. V21 is additive; preserve the database volume. Use `docker compose build backend frontend` and `docker compose up -d --wait --wait-timeout 180` after backing up the retained database. Never use `down -v` to upgrade. SANDBOX/LIVE fail closed in the discovery configuration.

## Customer flow

Bus search → choose all seats/boarding/drop points → Continue to booking terms. Cab detail → Continue to booking terms. Direct holiday quote → Continue to booking terms. Enquiry packages first require an owned local enquiry; that record remains unchanged, and requesting new booking terms obtains an explicitly simulated supplier quotation.

Sign in through the existing OIDC flow. Get a new booking quotation, inspect current prices and the sample refund policy, accept, enter fictional passengers/contact, save the booking, then explicitly pay with the MOCK gateway. Passenger ages must match the selected bus/holiday party; at least one adult 18+ is required. Bus seats are allocated only by the durable hold operation. Confirmation requires an accepted supplier receipt, never just payment capture. A cab confirmation reserves a category and does not invent a driver or registration. Preassembled holiday bundles record each included component.

My Trips links to `/journeys/trips`. Quote pages recover an existing booking after lost acknowledgement/refresh. Polling is bounded to 30 successful updates with 2–10 second intervals; processing continues on the server. Refresh status manually afterwards. Documents are immutable, hash-verified, owner-protected text downloads and explicitly invalid for travel/check-in. Downloads are denied once cancellation starts.

The full simulated amount is refundable only for cancellation requests made before the quotation's cutoff: bus/cab departure minus two hours, holiday departure minus 24 hours. An immutable cancellation quote expires within two minutes and is bound to the booking version. Cancellation releases inventory before initiating the independent refund. No rescheduling or partial cancellation is implied.

## Durable work and recovery

`JOURNEY_WORKER_ENABLED` defaults true; `JOURNEY_POLL_MS` defaults 300. These can be set explicitly for local failure testing. Existing payment refund worker settles accepted mock refunds. The provider simulator maintains separate reservation, seat, component and operation-receipt tables; it is not an in-memory fixture.

Every workflow step has a deterministic operation ID. Workers claim rows with SKIP LOCKED and a 15-second fenced lease, call providers outside the local transaction, then atomically save the state/outbox/document. After a crash, another worker looks up the original effect before issuing an idempotent call. Delayed/unknown responses use bounded backoff with jitter; ordinary effects stop after five attempts and refund status after twenty. REVIEW preserves the unresolved step and financial states. Do not manually set CONFIRMED, delete receipts, alter a quote, or repeat a new payment to resolve REVIEW. An authenticated operator recovery endpoint for these new cases is not implemented; preserve evidence for investigation.

Bus hold expiry is authoritative even if the worker is stopped: expired unconfirmed allocations do not block a new hold, and the old booking cannot pay after its quote expires. The worker later records failure/release. Confirmed seats remain allocated until supplier cancellation. All seat changes serialize on the provider trip key across independent searches.

`RESPONSE_LOST` deliberately commits the provider effect before throwing; the next claim recovers its receipt. `AUTH_DECLINED`, `CAPTURE_DECLINED`, and `RESERVATION_REJECTED` exercise decline, void/release, and captured-payment compensation. These controls are MOCK-only, with no external financial effects.

## Observability, security and limitations

Monitor `travbook.journeys.worker` outcome counters, due/leased/REVIEW rows in `journey_work`, independent payment/refund state, balanced payment journals, and versioned `journey_outbox` envelopes. Correlation IDs connect owned operations. Events contain state only, not passengers or email. Private responses remain no-store and require booking scopes and resource ownership; cookie mutations also require CSRF.

Outbox records are durable but no new downstream notification/broker consumer is enabled. Production metrics/dashboards, distributed rate limiting, retention automation, supplier onboarding, real signed gateway callbacks for these services, authorized travel documents, operational REVIEW resolution, driver assignment/tracking, package payment milestones and independently assembled multi-provider packages remain incomplete. No AWS resources were deployed, and no production SLO/compliance claim is made.

Rollback: stop the new worker and use a reviewed forward fix. Do not roll back V21 or overwrite accepted quotes/financial history. Restore drills and production deployment remain separate release gates.
