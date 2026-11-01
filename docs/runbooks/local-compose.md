# Local application deployment with Docker Compose

Use this for synthetic MOCK data only. It is not a production deployment configuration. Run from the repository root with Docker Engine/Desktop/OrbStack and Compose v2+ available. The initial build needs internet access to pinned base images and the Maven/npm registries; no host Java, Maven or Node is needed to run the stack.

## Start and update

Stop native Travbook development servers first; ports 3000, 8080, 8180 and 54329 must be available or already belong to these Compose services.

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

Open <http://127.0.0.1:3000> (use `127.0.0.1`, not `localhost`, to match the identity realm). Sign in with the public local fixtures `alice` / `alice-local-only` or `bob` / `bob-local-only`. Use fictional traveller data. API health is <http://127.0.0.1:8080/actuator/health>. Search, quotation, booking, checkout, summary and cancellation/refund flows remain explicitly MOCK.

All four published ports bind to host loopback. The API uses PostgreSQL and Keycloak service DNS internally. The frontend proxy destination is a **build argument**, not a runtime setting: editing `API_URL` requires rebuilding the frontend. The fixed Compose identity profile is intentionally not parameterized into arbitrary production endpoints. Production external OIDC requires its existing HTTPS profile and a separately reviewed TLS/secret/network deployment.

Source changes require `docker compose up -d --build --wait`; restarting alone does not rebuild images. Base images are pinned by digest; update/review those pins and re-run security scans and this verification when upgrading. Image builds package the API with tests skipped because Testcontainers runs separately in verification. A successful image build is not a passing test suite.

## Stop, diagnose and recover

```sh
docker compose logs --tail 100 backend frontend
docker compose restart backend frontend
docker compose up -d --wait --wait-timeout 180
docker compose stop
```

`stop` retains containers and the existing `travbook_travbook-data` PostgreSQL volume. Never run `down -v`, delete that volume, or reset migrations to fix startup. Keycloak fixture state lives in its container; recreating it reimports the public realm and may invalidate IdP sessions/keys, requiring sign-in again. PostgreSQL customer ownership remains tied to the same public issuer/subject. Restarting the application containers preserves JDBC sessions and business data.

Failed readiness: inspect container health output and bounded logs; confirm engine resources, port conflicts, database health and profile validation. The API root is read-only; temporary files use a 128MiB tmpfs. The web has 32MiB tmpfs; future filesystem-backed image/ISR features must provide an explicit writable cache or remote adapter before enablement. No writable source bind mounts or Docker socket are present in either application container.

The frontend probe reads `/api/v1/locations/DEL` through its real proxy; API probe reads `/actuator/health`. Unhealthy status alone does not restart a living process. Do not repeatedly restart unknown payment/supplier operations to force success. Consult the [checkout](mock-checkout.md), [refund](mock-cancellation-refunds.md) and [event](booking-event-delivery.md) recovery runbooks. Existing REVIEW outcomes and ambiguous legacy cancellation policies require audited resolution work; this deployment does not reinterpret or clear them.

Rollback: retain a previously verified image pair before updating, then restore that pair with the same compatible database schema. This slice adds no migration. Do not roll back the database or use an older application against incompatible future migrations. There is no automated zero-downtime rollout or restore evidence yet.

## Verify the deployed images

Host Node/npm and Chromium are needed for these verification commands, not ordinary deployment:

```sh
npm ci
npm exec -- playwright install chromium
PLAYWRIGHT_EXTERNAL_SERVERS=1 npm run test:e2e
node scripts/verify-compose-recovery.mjs
```

Run the recovery script **after**, not concurrently with, the browser suite. It intentionally restarts the API/frontend, creates one fictional confirmed MOCK booking, verifies retained login and My Trips, replays the original booking/checkout commands and checks persisted effect counts plus balanced journal entries. It does not delete existing data. No auth network traces, tokens or cookies are saved. Stop with `docker compose stop` when finished if the local application is no longer needed.

Backend correctness/architecture/security verification remains `sh scripts/maven.sh -f apps/api/pom.xml verify` with Java 21, Maven and the Docker engine for isolated Testcontainers. See [execution evidence](../verification-compose.md) for actual outcomes, and [ADR 0008](../adr/0008-local-compose-application.md) for decisions and limits.
