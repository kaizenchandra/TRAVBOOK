# Train search and availability — MOCK

Open <http://127.0.0.1:3000/trains>, or choose Trains in the homepage service hub. Build/update using the [Compose runbook](local-compose.md); Flyway applies V11 additively. Never reset the named PostgreSQL volume or modify earlier migrations. No new credentials or services are required.

## Try the slice

1. Choose New Delhi/NDLS → Mumbai Central/MMCT, two days ahead, General quota. Autocomplete supports station name, city and code; use arrows/Enter or select a suggestion. Swap reverses both canonical codes.
2. Compare sample operating days, departure/arrival dates and class cells. Overnight arrivals show their actual next-day date. The evening fixture does not operate on Sunday. All schedules and `MOCK-` service numbers are synthetic.
3. Filter by class and status; the filter matches one class entry with both conditions. Refresh/back preserves criteria/filters. No matched trains is distinct from an unavailable provider.
4. Review Sleeper on the first morning train and request current availability. The deterministic fixture changes AVAILABLE to RAC 3 and adds INR 50 to the base fare. The one-adult total is computed by the backend, including an INR 30 simulated fee. No tax/tariff or concession compliance is claimed.
5. Select AC 2 tier to see WAITLIST; select Tatkal two days ahead to see NOT_OPEN. The fixture opens at 08:00 IST the previous day. The interface and response restrictions state this is **not an official booking rule**.
6. MAO destination produces partial supplier failure; JP produces successful empty availability; HWH simulates both providers failing. These are fixture outcomes, not factual travel availability.

AVAILABLE is not reserved inventory. RAC and waitlist do not guarantee a berth. No prediction percentage, ticket, PNR, passenger eligibility, booking confirmation or payment is generated. The sample route displays endpoints only and is not a complete official route or running-status feed.

## APIs and recovery

| API | Purpose |
|---|---|
| `GET /api/v1/railway-stations?q=...` | At most 10 canonical station matches from the six-row fixture catalog |
| `POST /api/v1/trains/search` | Validated route/date/quota, bounded provider calls and saved snapshot |
| `GET /api/v1/train-searches/{id}` | Stable paginated/filterable search |
| `GET /api/v1/trains/{id}` | Saved journey and original class observations |
| `GET /api/v1/trains/{id}/availability?travelClass=SL&quota=GENERAL` | Fresh provider-specific class/quota observation |

Classes: SL, THREE_AC, TWO_AC. Quotas: GENERAL, TATKAL. Dates use Asia/Kolkata and the demonstration window of today through 60 days ahead. Public observations require no customer identity and contain no guest/railway credentials. Unknown/missing transport parameters return 400; semantic route/date constraints 422; missing references 404; stale/departed observations 409; rate limiting 429; provider outages 503. Existing `application/problem+json` and correlation IDs apply.

Snapshot reads expire after 15 minutes; fresh availability after at most 60 seconds and never after departure/search expiry. Recheck manually; no background polling is used. A failed fresh check must not be replaced with cached confirmation. Read retries are safe and do not create reservations or idempotency commands. PostgreSQL retains observations across restarts; physical retention cleanup is not yet scheduled.

Operational metrics: `travbook.rail.search` / `travbook.rail.availability`, tagged by fixed provider, mode and outcome. There are two threads/eight queued tasks and a 1.5-second budget, without blind retry. Hosted alerts, production load/DR/cost evidence and official provider quota policies remain release gates. SANDBOX/LIVE fail closed. Do not infer ticketing authorization from information availability.

Verification: [execution evidence](../verification-trains.md). Decisions/source handling: [ADR 0010](../adr/0010-railway-discovery.md). Remaining work: passenger/quota eligibility, permitted railway authentication, durable booking/payment/ticketing, PNR/vouchers and cancellations/refunds; authorized live data is a separate dependency.
