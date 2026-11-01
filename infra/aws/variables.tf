variable "account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "Provide the explicitly approved AWS account ID."
  }
}
variable "region" { type = string }
variable "environment" {
  type = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Use an isolated staging or production environment."
  }
}
variable "owner" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(distinct(var.private_subnet_ids)) >= 2
    error_message = "At least two distinct private subnets in different AZs are required."
  }
}
variable "public_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(distinct(var.public_subnet_ids)) >= 2
    error_message = "At least two distinct public ALB subnets in different AZs are required."
  }
}
variable "domain_name" {
  type = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]+[a-z]$", var.domain_name))
    error_message = "Provide a DNS hostname, without scheme, path or credentials."
  }
}
variable "zone_id" { type = string }
variable "certificate_arn" { type = string }
variable "api_image" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9/_-]+@sha256:[0-9a-f]{64}$", var.api_image))
    error_message = "Use a reviewed private ECR image pinned by SHA-256 digest."
  }
}
variable "web_image" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9/_-]+@sha256:[0-9a-f]{64}$", var.web_image))
    error_message = "Use a digest-pinned image built with API_URL=http://127.0.0.1:8080."
  }
}
variable "cpu_architecture" {
  type    = string
  default = "ARM64"
  validation {
    condition     = contains(["ARM64", "X86_64"], var.cpu_architecture)
    error_message = "Image architecture must match ARM64 or X86_64."
  }
}
variable "oidc_issuer" {
  type = string
  validation {
    condition     = can(regex("^https://[^/?#@]+(/[^?#]*)?$", var.oidc_issuer))
    error_message = "An authorized HTTPS OIDC issuer is required."
  }
}
variable "oidc_client_id" { type = string }
variable "oidc_client_secret_arn" { type = string }
variable "database_credentials_secret_arn" {
  type        = string
  description = "Existing JSON secret with username/password for a non-master application role; never pass secret values to Terraform."
}
variable "secret_kms_key_arns" {
  type    = list(string)
  default = []
}
variable "database_engine_version" {
  type = string
  validation {
    condition     = can(regex("^17\\.[0-9]+$", var.database_engine_version))
    error_message = "Choose an available, tested PostgreSQL 17 minor version in the target region."
  }
}
variable "database_ready" {
  type        = bool
  default     = false
  description = "Enable two application tasks only after creating the least-privilege DB role/secret and validating IdP/images/network prerequisites."
}
variable "database_class" {
  type    = string
  default = "db.t4g.medium"
}
variable "monthly_budget_usd" {
  type = number
  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "An explicit positive monthly budget is required."
  }
}
variable "alarm_topic_arn" { type = string }

variable "aws_profile" {
  type        = string
  default     = null
  description = "Explicit local profile when planning interactively; null permits approved CI workload identity."
}
