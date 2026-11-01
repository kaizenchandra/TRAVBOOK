# Hotel discovery and room revalidation (MOCK)

Build/recreate the existing application containers using the [Compose runbook](local-compose.md). Flyway applies additive V10 automatically. Keep the PostgreSQL named volume; no reset or legacy data rewrite is needed. Open <http://127.0.0.1:3000/hotels> or choose Hotels from the home service hub. The existing flight booking/payment journeys remain separate. Hotels now also have an [owned quotation/booking/aftercare lifecycle](hotel-lifecycle.md) in MOCK mode.

## Functional checks

1. Search Delhi, seven days ahead, two nights, one or more rooms. Enter each child's age at check-in. Providers currently contain fictional Delhi/Mumbai/Goa/Bengaluru properties, each available by city, locality, address, landmark, property name or provider-qualified ID.
2. Filter by property type, sample stars, amenity, full-stay budget and refundable room availability. Sort by full-stay total or sample rating. Refresh/back preserves search criteria and filters. Results show all-room/all-night totals with simulated taxes and fees.
3. Open Compare rooms. Switch original illustrations, inspect house rules, nightly charges, included meals and policy cutoffs. Choose one rate for each room; selections persist in the URL.
4. Recheck selected rooms. FLEX/VALUE keep their sample price; FAMILY increases by INR 250 per night per selected family room before simulated tax. Review changed totals/policies explicitly. STUDIO returns unavailable and permits selection of another rate.
5. Three FLEX/VALUE room selections exceed the shared two-standard-room pool. Choose family rooms or search fewer rooms, then revalidate. No inventory hold or booking is created.
6. Goa returns partial supplier coverage. Jaipur returns successful empty availability. Shimla deliberately returns a total provider outage. These must remain distinct UI/API outcomes.

Fixture charges: adult 3 adds INR 800/night in the family plan; child ages 0–2 add zero, ages 3–11 add INR 500/night, ages 12–17 add INR 900/night. Saturdays add INR 400/room/night. Simulated tax is 10% of nightly subtotal, rounded HALF_UP to two decimals; service fee is INR 100 per room per stay. These are test fixtures, not applicable tax law or licensed supplier prices.

The FLEX/FAMILY cancellation cutoff is 14:00 Asia/Kolkata two days before check-in, with no refund at/after that instant. A passed cutoff is displayed as non-refundable even when the plan name contains FLEX. A rate check alone commits no cancellation entitlement. Continue with an owned quote, explicit acceptance and guest checkout to enter the durable MOCK booking workflow.

## Contracts and recovery

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/hotels/search` | Validated occupancy/date search and saved snapshot |
| `GET /api/v1/hotel-searches/{id}` | Stable filtered/paged results |
| `GET /api/v1/hotels/{id}?searchId=...` | Contextual property details and rates |
| `GET /api/v1/hotels/{id}/rooms?searchId=...` | Contextual room comparison |
| `POST /api/v1/hotels/offers/{id}/reprice` | Fresh whole-selection observation; ordered offerIds with path id equal to first |

Malformed transport constraints/missing context return 400; semantic date/selection violations return 422. Missing resources return 404, stale searches and unavailable selected rooms return 409, rate limiting returns 429 and unavailable providers return 503 with problem+json/correlation IDs. Repeated reprice reads are safe; there is no financial mutation or idempotency record. Expired observations require a new search/recheck, never a forced confirmation.

PostgreSQL preserves snapshots across application restarts. Their expiry is enforced on reads; physical cleanup/retention scheduling is not yet implemented. `hotel_search_expiry` and `hotel_offer_search` support future bounded cleanup. Do not use these observation tables as room stock or reservation authority. Read-only hotel metrics are `travbook.hotels.search` / `travbook.hotels.reprice`, labelled only by fixed provider, mode and outcome. Alerting, dashboards and production capacity remain pending.

These public discovery operations create no guest record, reservation, voucher or payment. The separate owned stay APIs implement the MOCK booking lifecycle. Original illustrations are labelled as illustrations; no real reviews, scarcity or popularity are invented. SANDBOX/LIVE fail closed until authorized adapters and contracts exist. The owned hotel lifecycle uses a separate stays domain and published payment contracts; it does not reuse the flight quote model.

Verification commands and actual results: [hotel evidence](../verification-hotels.md). Architecture: [ADR 0009](../adr/0009-hotel-discovery-and-rate-selection.md).
