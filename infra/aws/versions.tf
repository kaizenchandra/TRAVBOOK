terraform {
  required_version = ">= 1.10, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "= 6.71.0" }
  }
  backend "s3" {}
}
provider "aws" {
  profile             = var.aws_profile
  region              = var.region
  allowed_account_ids = [var.account_id]
  default_tags { tags = { Project = "travbook", Environment = var.environment, Owner = var.owner, ManagedBy = "Terraform" } }
}
