# ADR 0020 — Evidence-gated journey recovery and AWS deployment preparation

Status: accepted; scoped local MOCK recovery and Terraform policy checks verified. No live integration or AWS deployment performed.

AC-075: privileged bus/holiday/cab REVIEW inspection and audited resumption using an immutable pre-review checkpoint and matching provider receipt. Existing operator role/recent signed MFA rules remain mandatory. No customer role promotion, forced status correction or unrelated financial instruction. Three recovery attempts maximum; concurrent/stale versions and changed idempotency payloads conflict. V21 reviews without a checkpoint remain blocked; no inferred prior state.

AC-076: operator API and real-backend desktop/mobile UI verification, including insufficient-role/MFA denial, no PII, missing/mismatched receipt, concurrent actions, rollback, replay and retained-data migration.

AC-077: reproducible Terraform deployment preparation for the existing modular web/API and PostgreSQL. Validate locally without selecting an AWS account, spending funds or making supplier calls. Account/profile, environment/region, TLS/domain, OIDC and immutable image references must be supplied before a concrete deployment plan. Real supplier/payment onboarding and valid provider travel documents remain blocked on authorized contracts/access.

Plan: extend OpenAPI/public recovery kinds → domain checkpoint invariants → atomic V22 checkpoint/audit and store recovery → existing operations UI → integration/browser tests; prepare and validate explicit MOCK AWS infrastructure using official provider documentation. Preserve partial cancellation/rescheduling, package milestones and driver assignment/tracking as separate remaining scope, not silently completed by recovery controls.

Compatibility decision: preserve `/api/v1/operations/reviews` HOTEL/RAIL enums; use additive `/api/v2/operations/reviews` for BUS/HOLIDAY/CAB. AWS request access logs are disabled because ALB retains full callback query URLs; aggregate metrics/redacted application logs are used instead. [Verification](../verification-journey-review-cloud.md).
