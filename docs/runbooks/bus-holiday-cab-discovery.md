# Bus, holiday and cab discovery (MOCK)

[ADR 0018](../adr/0018-bus-holiday-cab-discovery.md) scopes this first slice. Bus/holiday P0 and cab P1 priorities are preserved. Existing flight, hotel and rail booking workflows are independent.

## Bus

Open `/buses`; sample routes include Delhi–Jaipur, Delhi–Agra, Mumbai–Pune and Bengaluru–Mysuru (reverse routes also supported). Enter one to six declared passenger ages, 5–120, and a date within 90 days. Filter by operator, seater/sleeper, AC, price, departure period and boarding point; sort/paginate on the persisted snapshot.

Choose seats in passenger order. Upper berths require age 12+. Grey occupied/restricted seats cannot be selected; restricted seats require provider assistance, not a gender inference. Selecting seats invokes a backend fare check; partial selection is clearly incomplete. All fares are simulated inclusive seat totals. L2 demonstrates a changed all-in fare (+INR 50); seater L6 demonstrates inventory lost after the initial observation. Refreshing the map marks L6 occupied. No seat hold is created or implied. Booking/payment/ticket controls are not enabled.

Delhi–Agra demonstrates partial provider failure. Departures from Goa demonstrate total provider outage. Unsupported routes return healthy empty results. Snapshot lifetime is at most ten minutes, capped by first departure; fare checks last at most sixty seconds and never extend the snapshot.

## Holidays

Open `/holidays`; Goa and Jaipur are domestic fixtures, Bali is international. Filter destination, region, theme, adult starting price and duration; inspect day-wise accommodation/meals/transfers and exclusions. Images are original SVG illustrations, not supplier property/destination photographs.

Sign in through the existing local OIDC provider to create an owned quote. Supported Goa comfort/activity customization updates the immutable final itinerary and inclusions as well as price. Pricing: adult-priced traveller 12+ at base; ages 0–4 at25%, 5–11 at60%; COMFORT adds INR750/person/night; optional activity adds INR1,200/person; synthetic10% tax plus INR200 fee. These are fixture rules, not actual travel or tax policy. Quotes expire after ten minutes and do not hold inventory or confirm traveller/document eligibility.

ENQUIRY packages accept fictional contact details and explicit consent. The request is stored as RECORDED_LOCALLY with immutable quote and contact snapshot. No email, callback, message or supplier request is dispatched. Duplicate equivalent keys return the original request; changed input conflicts. After a lost acknowledgement, refresh the quotation and open its linked saved enquiry. Other customers get404. Direct-fulfilment packages can be quoted, but booking/payment is not enabled; their enquiry endpoint rejects rather than changing provider workflow silently.

Goa filtering demonstrates partial supplier coverage; Shimla demonstrates total outage; unknown destinations produce healthy empty results. Holiday events contain IDs and lifecycle metadata only, no contact fields; external outbox delivery is not implemented.

## Cabs

Open `/cabs` for airport transfers, local4/8/12-hour rentals, or outstation one-way/round trips. Pickup must be more than two hours ahead within90days; local pickup/drop area is the same; round-trip returns are within7days and must accommodate the simulated route duration. Capacity and luggage constraints are authoritative.

Fixture routes: airport↔centre28km; Delhi↔Jaipur270km, Delhi↔Agra230km, Mumbai↔Pune150km; local bundle10km/hour. Round-trip kilometres double. Vehicle rates are category-specific and include the displayed bundle, synthetic tax and fee. Tolls/parking and actual excess usage are not invented or included silently. SUV repricing demonstrates an additional INR100 base plus INR10 synthetic tax. No driver, registration, reservation or live tracking exists.

Goa airport pickup demonstrates partial coverage; Jaipur pickup demonstrates total provider outage. Cross-region routes outside the fixture return empty. Snapshots expire at the earlier of ten minutes or the two-hour scheduling cutoff. Rechecks last at most sixty seconds and cannot extend expiry.

## Operation, retention and deployment

All three provider adapters fail closed outside explicit MOCK mode. Read execution uses a shared4-thread pool, bounded8-task queue, max4 configured profiles per call (two used), and1500ms deadline. Cancellation/rejection is observable in supplier coverage and Micrometer counters; this is not achieved search-SLO evidence.

V20 adds only new context-owned tables; original booking/payment/refund records are unchanged. Snapshot stores accept only fixed table names. Holiday mutations atomically save command/result/outbox; owner/quote FK and immutability constraints prevent cross-account binding and silent overwrite. No remote operation runs inside that database transaction. Snapshots contain only public criteria; contact data is private and never enters URLs/events. Automated retention/deletion and outbox publication remain production work.

Use existing Compose build/up commands. The Docker context includes source/contracts/original assets and excludes dependencies, targets, test output, secrets and local log/dump/backup artifacts. Never delete the PostgreSQL volume to reset a feature. New data is additive; older images do not expose these routes. A local dump does not establish restore or disaster-recovery readiness.

Next P0 slice: durable bus seat holds, owned booking quotes/passenger checkout and supplier-aware payment/reservation/recovery; then holiday direct/enquiry fulfilment, milestones and component policies. Cab P1 reservations and later driver updates remain tracked separately. Real providers require authorized onboarding; no AWS deployment or production compliance is claimed.
