# ADR 0015 — Reconcile proven legacy outcomes, never invent a checkpoint

AC-059: a privileged role/recent-MFA operator may record a provable terminal MOCK outcome for
an old hotel/rail REVIEW without a checkpoint. It is distinct from resuming an unknown step.

Required proof is read through published ports: current supplier status/allocation, current
captured payment and committed/settled refund totals, plus matching immutable reserve/capture
and (where applicable) cancellation/refund receipts. Bind owner, booking, amount, currency,
mode and references. Fulfilment requires no cancellation intent or refunds and all original
units still allocated. Cancellation requires the original accepted whole-party entitlement,
zero supplier allocation, and exactly the expected settled refund (or verified zero due).

Only these positive outcomes are supported. Missing evidence, partial-passenger cases,
rejected/voided/other ambiguous cases, contradictory totals and existing checkpoints are
rejected. There is no guessed previous step, forced success, new supplier mutation or money
movement. LIVE/SANDBOX and real provider proof remain unimplemented.

The original review version is fenced under the existing work/booking lock order. Operator
key replay is durable; terminal state, work completion, immutable audit/proof and outbox commit
atomically. The proof contains canonical facts and effect IDs, never tokens or passenger data.
The existing three-action budget includes legacy reconciliation. New workflows may later use
the checkpoint path; proof does not rewrite original quotes, policies or financial history.
