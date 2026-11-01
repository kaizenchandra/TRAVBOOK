# Implemented journey enhancement boundaries

The existing modular journey context remains the owner. [Care](../apps/api/src/main/java/com/travbook/journeys/domain/model/Care.java) defines immutable terms, paid entitlements, action phases, funding summaries and forward-only dispatch states. [AdvancedJourneyService](../apps/api/src/main/java/com/travbook/journeys/application/usecase/AdvancedJourneyService.java) owns customer use cases; [JourneyCareWorker](../apps/api/src/main/java/com/travbook/journeys/application/usecase/JourneyCareWorker.java) coordinates durable work. Controllers validate transport, resolve principals and invoke inbound ports only.

```mermaid
flowchart LR
  UI[Next.js typed quote / trip / aftercare / dispatch UI] --> API[AdvancedJourneyController]
  API --> IN[AdvancedJourneys]
  IN --> APP[AdvancedJourneyService]
  APP --> MODEL[Care / Journey domain invariants]
  APP --> STORE[JourneyStore / JourneyCareStore]
  STORE --> DB[(PostgreSQL platform state + immutable outbox)]
  WORK[ProcessJourneyCare / fenced worker] --> STORE
  WORK --> EFFECT[JourneyCareEffects]
  EFFECT --> PAY[Published PaymentOperations / PaymentRefunds]
  EFFECT --> SUP[JourneyCareSupplier]
  SUP --> PROVIDER[(Separate durable MOCK component inventory / receipts)]
  APP --> CAB[JourneyCabDispatch]
  CAB --> DISPATCH[(Separate MOCK provider dispatch + immutable commands)]
  OPS[Signed-MFA operations] --> REC[JourneyCareRecovery]
  REC --> STORE
  REC --> EFFECT
```

Remote effect calls never run inside platform transactions. Provider simulators assert that boundary. Current state plus receipt/amount/currency/reference/owner checks guard recovery. Owner-bound foreign keys, unique active-action/component keys, optimistic versions, immutable quote-use/refund-entitlement records and lease tokens enforce concurrency and financial integrity. V23 is additive; quote/booking record shapes and supplier fingerprints from older versions are preserved.

```mermaid
sequenceDiagram
 actor C as Customer
 participant A as Journey application
 participant D as Platform DB
 participant W as Durable worker
 participant S as MOCK supplier
 participant P as Payment/refund ports
 C->>A: Accept partial cancellation quote
 A->>D: Atomic command + quote-use + action + booking state + outbox
 W->>D: Claim fenced action
 W->>S: Lookup / cancel selected components
 S-->>W: Durable exact receipt
 W->>D: Active-component update + revised document + outbox
 loop Each paid entitlement account
  W->>P: Lookup / refund stable operation ID
  P-->>W: Pending or settled receipt
  W->>D: Independent refund progress
 end
 C->>A: Refresh owned trip / current document
```

```mermaid
stateDiagram-v2
 [*] --> WAIT_REPLACEMENT: Accepted bus replacement
 WAIT_REPLACEMENT --> CANCEL: Replacement confirmed
 WAIT_REPLACEMENT --> FAILED: Replacement failed/expired
 [*] --> VALIDATE: Holiday balance request
 VALIDATE --> AUTHORIZE
 AUTHORIZE --> CAPTURE: Authorized
 AUTHORIZE --> FAILED: Declined
 CAPTURE --> SUCCEEDED: Captured balance
 CAPTURE --> VOID: Capture rejected
 VOID --> FAILED
 [*] --> CANCEL: Accepted selected-component cancellation
 CANCEL --> REFUND: Supplier confirms release
 REFUND --> REFUND_STATUS: Pending
 REFUND_STATUS --> REFUND: Next funding account
 REFUND_STATUS --> SUCCEEDED: All entitlements settled
 CANCEL --> REVIEW: Unknown after bounded retries
 CAPTURE --> REVIEW: Unknown after bounded retries
 REFUND_STATUS --> REVIEW: Still unresolved
 REVIEW --> CANCEL: Verified saved checkpoint
```

Booking, initial payment, reservation, refund, care action, holiday balance and dispatch states remain separate. Initial capture is not proof of full funding; full funding is not proof of supplier confirmation. The UI exposes both and only serves a current document when their required conditions hold. The original booking is not cancelled merely because a replacement payment succeeded.
