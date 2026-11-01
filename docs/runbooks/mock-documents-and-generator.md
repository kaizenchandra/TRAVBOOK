# Protected MOCK summaries and generator maintenance

See [ADR 0006](../adr/0006-generator-security-and-mock-documents.md) and [verification](../verification-documents-security.md). This feature produces a development confirmation summary, **not a ticket, voucher or tax invoice**. No PNR, real payment or supplier access is invented.

## Use the document flow

Start the existing local services using the [README](../../README.md). Flyway V8 is additive and preserves existing data. Complete an explicitly consented MOCK checkout; its status must be CONFIRMED, payment CAPTURED and supplier CONFIRMED. Under **MOCK documents**, choose **Prepare MOCK summary**, then **Download MOCK summary**. The resulting UTF-8 text includes the authoritative references, journey/timezones, traveller names, accepted price and fixture policies. It omits birth dates, tokens and customer identity IDs. Keep using fictional traveller data locally.

Generation is an explicit synchronous POST, not a side effect of reading a booking. An empty list means eligible but not generated. The request accepts only the fixed kind `MOCK_CONFIRMATION_SUMMARY`, plus a required idempotency header. Prices, reference values, owner and content are never browser-authoritative. Same customer/key/request replays one resource; different keys for the same booking recover its one immutable summary. Changed booking under the same key conflicts. A lost response can be recovered through **Recover saved summary** or page refresh; mutation retries are not automatic.

All three endpoints enforce booking ownership and current confirmation eligibility:

- `POST /api/v1/bookings/{id}/documents`: `bookings:write`, cookie CSRF, idempotency key; returns 201 with metadata.
- `GET /api/v1/bookings/{id}/documents`: `bookings:read`; zero or one metadata record; no pagination for the fixed kind/version.
- `GET /api/v1/bookings/{id}/documents/{documentId}/content`: `bookings:read`, binding to both booking and document; streams the immutable attachment bytes. No token is put in its URL.

Foreign/missing resources return 404; pending/failed/unconfirmed checkout returns 409; invalid scopes return 403. Bodies are at most 32KiB UTF-8 and are integrity-checked against SHA-256 on every load. Downloads use a fixed UUID-based filename, `text/plain; charset=UTF-8`, `Content-Disposition: attachment`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. The UI revokes its temporary Blob URL after initiating the user-requested download. Denied access hides the surrounding cached private booking view.

## Persistence and recovery

Trips owns `trip_document`, `trip_document_command` and `trip_document_outbox`. Source data is read through published booking contracts; Trips does not read booking/payment tables directly. The exact body/hash, source checkout/version, command ID and `MockDocumentCreated` event commit atomically. Unique constraints handle concurrent first requests. Document, command and event contents reject normal UPDATE/DELETE; a privileged administrator with DDL/TRUNCATE permissions can bypass those guards, so production role separation is still required.

There is no remote storage operation during the transaction. PostgreSQL is sufficient for the bounded local text fixture; S3, real provider PDFs, archival/retention policy and customer data deletion require separate implementation. A failed transaction leaves no partial document/key/event. Retrying the original key after recovery is safe. Do not overwrite documents to change policies or prices: introduce a reviewed new kind/version with its own contract. Existing summaries are snapshots at issue time, not ongoing assertions of travel validity.

Safe diagnostic reads omit bodies and traveller details:

```sql
SELECT kind,mode,count(*) AS documents,max(byte_length) AS largest_bytes
FROM trip_document GROUP BY kind,mode;

SELECT count(*) AS missing_creation_events
FROM trip_document d LEFT JOIN trip_document_outbox e ON e.document_id=d.id
WHERE e.id IS NULL;
```

The second count should be zero. A document integrity failure produces a safe error; do not serve corrupted bytes or edit its stored hash to bypass the check. Investigate through restricted operator access. Document events contain identifiers/kind/hash only, no body, names or DOB. They remain **stored, not dispatched**; this increment does not claim notifications or external broker delivery. Checkout event dispatch, cancellation/refund and real tickets/invoices remain outstanding.

## Reproducible dependency fix

The prior three npm audit findings came from one chain: openapi-typescript → Redocly core → minimatch. The root override now pins Redocly core 1.34.22 and the actual patched minimatch 11.0.1 package. The [postinstall script](../../scripts/patch-redocly-minimatch.cjs) adapts Redocly's single callable-import consumer to minimatch's named export. It validates exact package versions and the expected upstream source SHA-256, is idempotent, and refuses unknown source bytes. It does not spoof versions or suppress advisories.

Use normal `npm ci`, then `npm run contract:check`, `npm run contract:test` and `npm audit --audit-level=moderate`. If intentionally installing with lifecycle scripts disabled, explicitly run `node scripts/patch-redocly-minimatch.cjs` before generation/checks. The matching regression test detects an unpatched consumer; do not remove the test or lower the audit threshold to make CI pass. npm may report an optional fsevents lifecycle script awaiting approval on macOS; no additional script permission was granted for this work.

When upgrading the generator/Redocly/minimatch, review upstream changes first. A source/version mismatch should stop installation. Prefer removing the override and compatibility patch once upstream supports a safe dependency chain; retain tests for generated types, nullable/escaped references and header-pattern matching. A direct Redocly 2.72.1 replacement was tested in isolation and rejected because the generator imports unexported internals. The application lockfile retains the working 1.x adaptation; the rejected candidate was never applied to project dependencies.

Audit zero is a point-in-time dependency result, not a claim of general security or compliance. Full production security, real gateway/provider onboarding, least-privilege runtime DB access, hosted CI and AWS deployment remain release gates.
