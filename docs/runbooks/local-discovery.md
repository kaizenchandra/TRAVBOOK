# Local discovery operations

Run commands from the root; see the [local guide](../../README.md). All data is non-personal MOCK discovery data. No financial reconciliation operation exists in this increment.

## Startup and dependency failure

1. Start `docker compose up -d --wait postgres`.
2. Start the API with `sh scripts/maven.sh -f apps/api/pom.xml spring-boot:run` and web with `npm run dev` (or VS Code tasks).
3. Read `http://127.0.0.1:8080/actuator/health` for database-backed readiness. Use `.../health/liveness` for process liveness.
4. If database startup fails, inspect the local container health and the configured URL/credentials. Never disable Flyway or substitute H2 to pass the gate.
5. If `INTEGRATION_MODE` is SANDBOX/LIVE, startup must fail. Restore explicit MOCK only for local simulated work; do not use this as a fallback for an authorized provider outage.

Every API response has a server-generated `X-Correlation-ID`; supplied correlation values are not echoed. Error bodies expose safe messages, codes and the same ID. HTTP request metrics and provider outcome counters are registered, but Prometheus is denied by the public security chain until a restricted management interface is designed. No publicly exposed monitoring endpoint is enabled merely for convenience.

## Search recovery

- 400/422: fix fields; backend validation is authoritative.
- 404: unknown canonical airport or snapshot; start a new search.
- 409 SEARCH_EXPIRED/OFFER_EXPIRED: repeat search and inspect new prices. Do not extend old expiry in the database.
- 409 OFFER_UNAVAILABLE: return to results and select another offer.
- Partial coverage: show returned results and the unavailable-provider warning; do not claim complete market coverage.
- 503: retry manually after the provider/database is available. Read-only search/reprice can safely retry; this policy must not be copied into financial commands.
- 429: respect `Retry-After`. Current limiter is process-wide, not a distributed or per-user production abuse control.

## Retention and local teardown

Expired searches are rejected but remain stored for diagnostic inspection. Before shared use, add a bounded cleanup worker; initial retention policy is delete non-personal discovery snapshots after 24 hours. The following maintenance transaction can be run by an authorized local database operator; it has not been automated:

```sql
BEGIN;
DELETE FROM search_offer WHERE search_id IN
  (SELECT id FROM search_snapshot WHERE expires_at < now() - interval '24 hours');
DELETE FROM search_snapshot WHERE expires_at < now() - interval '24 hours';
COMMIT;
```

Stop web/API tasks with Ctrl-C. `docker compose stop postgres` preserves local data. Do not remove the volume unless intentionally discarding the local sample searches. Testcontainers creates and removes its own isolated test database automatically.

## Migration / rollback

V1 only adds the catalog and search tables. Existing specification files are preserved. Application rollback can stop the application while retaining these additive tables; no destructive down migration is supplied. A future migration must be forward-compatible and tested against an existing database, not just an empty container.

No backup/restore drill, production deployment, live provider call, payment operation or AWS failover has been performed. Those are separate release gates in the [security/operations review](../security-and-operations.md).
