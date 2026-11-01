# AWS deployment candidate — explicit MOCK pilot

This configuration provisions resources only when a separately configured, authorized account-specific plan is applied. **No AWS plan or deployment has been executed.** Local `terraform test` uses a mocked AWS provider and fake identifiers; it does not contact an AWS account. This is not a production-readiness or compliance certification.

## Implemented topology

An existing approved VPC supplies two private and two public subnets across at least two AZs. Terraform checks subnet VPC/AZ/private-address attributes during the real plan. Private Fargate tasks contain the Next.js frontend and Java API together; only the frontend port is reachable from the HTTPS ALB. The API binds to localhost. PostgreSQL RDS is private, Multi-AZ, KMS encrypted, TLS-required, backed up for 14 days and deletion-protected. The app database role is separate from the RDS-managed master credential. Private application and IdP secrets are referenced by ARN, never read into Terraform state. WAF common rules/IP limits, restricted security groups, CloudWatch logs/alarms, bounded scaling, application-log retention, resource tags and a monthly budget are configured.

No new Redis, broker, Kubernetes cluster or microservice is introduced. Existing durable PostgreSQL workflows remain authoritative. CloudFront/static caching, cross-region DR, production tracing/dashboards, threat review and account-wide CloudTrail/GuardDuty/Security Hub configuration are separate release work; do not duplicate organization controls without examining the target account.

## Required inputs before any deployment

1. Approved AWS account/profile, region, environment and owner. The provider rejects an account ID mismatch. `aws configure list-profiles` currently returned no profiles in this workspace environment; none was selected.
2. VPC/public/private subnet IDs with DNS enabled, public ALB Internet Gateway routes, private NAT or suitable endpoints, and HTTPS egress to the authorized OIDC provider/ECR/Secrets Manager. Route readiness still requires account-specific inspection.
3. DNS name, hosted-zone ID and already validated regional ACM certificate. The module creates the DNS alias; confirm the name is intended for this environment before apply.
4. Authorized external OIDC issuer/client/scopes and secret ARN. Operators additionally need signed `travbook_roles`, `amr` and recent `auth_time` claims; ordinary customer accounts must not be promoted for testing.
5. Reviewed immutable API/web ECR digests and matching CPU architecture. **Build the web image with `--build-arg API_URL=http://127.0.0.1:8080`** for the shared Fargate task. Compose images use a different build-time proxy URL and cannot be reused unchanged.
6. Region-supported PostgreSQL 17 minor version; non-master application database credentials stored in a JSON Secrets Manager secret (`username`/`password`); KMS key ARNs if those external secrets use customer-managed keys.
7. Existing SNS alarm topic with policies allowing CloudWatch/Budgets publication, explicit monthly budget, and activated Environment cost-allocation tag. Budget notifications are not a hard spending cap.
8. Approved encrypted/versioned S3 state bucket and KMS key with least-privilege access and native lockfile support. Terraform state is sensitive even without credential values.

The module initially has `database_ready=false`: RDS/network controls can be provisioned while **no application service runs**. This still creates billable infrastructure if applied. An authorized bootstrap/migration process must create a `travbook` login role and grant it ownership of the application database/schema (not superuser or `rds_superuser`), populate the application secret, verify CA trust/IdP/network/images, and then set `database_ready=true`. Startup Flyway currently requires schema-owner DDL privileges; a separate migration role/task remains a production hardening gate. Never give application IAM access to the master secret. No bootstrap password should be committed or passed as a Terraform variable.

The application remains `INTEGRATION_MODE=MOCK` even in this cloud candidate. Valid travel documents and real financial/supplier effects require separately implemented authorized adapters and onboarding; credentials alone must never switch modes.

## Local validation executed / reproducible checks

```sh
terraform -chdir=infra/aws init -backend=false -input=false
terraform -chdir=infra/aws fmt -check -recursive
terraform -chdir=infra/aws validate
terraform -chdir=infra/aws test
```

AWS provider 6.71.0 is pinned with checksums in `.terraform.lock.hcl`; local validation used Terraform 1.17.0. Five mocked plan tests cover the disabled-service prerequisite, redundant/private tasks and rollback, encrypted/private protected DB, and rejection of mutable images, insecure identity and single-subnet input. These checks do not prove AWS service availability, IAM runtime behavior, RDS TLS connectivity, WAF compatibility with real traffic, restore time or SLOs.

Once the required inputs exist, [plan-aws.sh](../../scripts/plan-aws.sh) performs an account identity check and creates a restricted plan file. It never applies. Review the exact resources, costs, DNS change, security and migration plan before deployment. After an approved apply, verify HTTPS/OIDC/CSRF, all MOCK browser lifecycles, two-task recovery, current worker counts and balanced journals; perform backup restore and rollback drills before any production claim. Disable `database_ready` only through a reviewed change: that removes the service and stops task execution while preserving the database. Database/KMS prevent-destroy protections must not be bypassed as routine rollback.

Image `/tmp` volumes retain non-root writable permissions through the Dockerfile `VOLUME` declaration; runtime roots remain read-only. The API image embeds and verifies the public [RDS CA bundle](../../deploy/certificates/README.md). Logs must not contain tokens, passenger data or payment credentials. ALB request access logging is deliberately disabled: its full request URL can contain OAuth authorization codes and cannot be selectively redacted. Use aggregated ALB/WAF metrics and redacted application logs; add a reviewed redacting telemetry path before retaining request logs. WAF sampled requests are disabled to avoid retaining incidental request data.

## Six-pillar evidence and residual work

| Pillar | Implemented configuration | Verification remaining |
| --- | --- | --- |
| Operational excellence | IaC, health/readiness, service rollback, logs/alarms, CI validation | Account deployment, dashboard/alert drill, migration/rollback evidence |
| Security | Account guard, private tiers, TLS/CA, exact secret/ECR IAM, KMS, WAF, no task permissions | IdP/MFA provisioning, secret rotation, penetration/IAM/network review |
| Reliability | Multi-AZ DB, two task minimum, 14-day backups, deletion protection, durable app workflows | Restore/DR drill; no RTO/RPO benchmark claimed |
| Performance | Bounded 2–4 task autoscaling, health probes, existing indexed durable claims | Sizing/load/soak and DB connection budget validation |
| Cost optimization | Tags, explicit budget, capacity cap, log retention | Regional cost estimate, activated cost tag, cost-per-search/booking |
| Sustainability | Existing modular deployment, no redundant broker/cache, bounded capacity/log lifecycle | Utilization and transfer/storage measurements |

Primary design references: [ECS task networking/localhost](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-networking-awsvpc.html), [execution roles](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-iam-roles.html), [RDS TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html), [Fargate volume permissions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/bind-mounts.html), [ALB logged request fields](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html), [Terraform ECS service](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecs_service).
