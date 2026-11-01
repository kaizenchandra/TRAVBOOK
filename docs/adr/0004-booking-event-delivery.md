# ADR 0004 — Durable local booking event delivery

Accepted for the booking-intent slice. Preserve existing API status enums and add an owned, read-only `/api/v1/bookings/{id}/timeline` contract. The authoritative intent remains separate from its eventually consistent history. No payment, reservation or confirmation is introduced.

Use a local durable consumer behind a delivery port, with PostgreSQL as the authority. This is an explicit adaptation of FRD section 32's broker acknowledgement: the publisher acknowledges only after the local consumer transaction commits. There is no broker acknowledgement or SQS integration to claim. A future broker adapter must retain this acknowledgement boundary and consumer guarantees.

V6 seeds delivery work for existing unpublished booking events and inserts new delivery work in the originating outbox transaction. Per-aggregate order, SKIP LOCKED claims, database-clock leases and fencing permit bounded parallel dispatch. Each claim consumes an attempt before delivery; five failed/crashed attempts quarantine the event. Retry delays use bounded exponential backoff and deterministic jitter. Later events for that aggregate cannot pass an unpublished predecessor; unrelated bookings continue.

The receiver validates schema version, mode, envelope identity, event/status/failure combinations and legal aggregate progression. Immutable timeline rows double as the durable inbox marker, committed atomically. Identical event redelivery succeeds without another row; conflicting IDs/versions, missing predecessors and malformed events are quarantined. No transaction spans the delivery call. A process can die after consumer commit and before acknowledgement; a new lease safely redelivers.

Only an operator with database access may replay quarantined work using the audited database function after diagnosis. Replay keeps the original event identity/content, records actor/reason, and resets the bounded delivery budget. No public replay endpoint or unimplemented admin button exists. Pricing events remain stored and undispatched; adding consumers there is separate work.

Acceptance: AC-019–022 in the requirement decisions, exercised with PostgreSQL, HTTP, actual child-process crash/restart, and desktop/mobile UI. Infrastructure deployment, external transport and production alert routing are not included.
