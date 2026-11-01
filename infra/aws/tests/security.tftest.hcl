mock_provider "aws" {
  mock_resource "aws_db_instance" {
    defaults = {
      address            = "mock-rds.ap-south-1.rds.amazonaws.com"
      master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:master-example", kms_key_id = "mock", secret_status = "active" }]
    }
  }
}
variables {
  account_id                      = "123456789012"
  region                          = "ap-south-1"
  environment                     = "staging"
  owner                           = "test-only"
  vpc_id                          = "vpc-00000000000000001"
  private_subnet_ids              = ["subnet-00000000000000001", "subnet-00000000000000002"]
  public_subnet_ids               = ["subnet-00000000000000003", "subnet-00000000000000004"]
  domain_name                     = "travel.example.test"
  zone_id                         = "Z000000000000000001"
  certificate_arn                 = "arn:aws:acm:ap-south-1:123456789012:certificate/00000000-0000-0000-0000-000000000001"
  api_image                       = "123456789012.dkr.ecr.ap-south-1.amazonaws.com/travbook-api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  web_image                       = "123456789012.dkr.ecr.ap-south-1.amazonaws.com/travbook-web@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  oidc_issuer                     = "https://identity.example.test/realms/travbook"
  oidc_client_id                  = "test-client"
  oidc_client_secret_arn          = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:oidc-example"
  database_credentials_secret_arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:application-db-example"
  database_engine_version         = "17.6"
  monthly_budget_usd              = 100
  alarm_topic_arn                 = "arn:aws:sns:ap-south-1:123456789012:alarms"
}
run "foundation_has_no_running_service" {
  command = plan
  assert {
    condition     = length(aws_ecs_service.main) == 0
    error_message = "Do not run application tasks before explicit database/identity bootstrap readiness."
  }
  assert {
    condition     = aws_db_instance.main.multi_az && aws_db_instance.main.storage_encrypted && !aws_db_instance.main.publicly_accessible && aws_db_instance.main.backup_retention_period == 14 && aws_db_instance.main.deletion_protection
    error_message = "Database must remain private, encrypted, backed up and protected."
  }
  assert {
    condition     = aws_lb_listener.https.port == 443 && aws_lb_listener.https.protocol == "HTTPS" && aws_lb.main.enable_deletion_protection
    error_message = "Only HTTPS should be exposed."
  }
  assert {
    condition     = aws_db_instance.main.manage_master_user_password && aws_db_instance.main.password == null
    error_message = "Never put master credential values into Terraform state."
  }
}
run "application_is_private_and_recoverable" {
  command = plan
  variables { database_ready = true }
  assert {
    condition     = aws_ecs_service.main[0].desired_count == 2 && !aws_ecs_service.main[0].network_configuration[0].assign_public_ip && aws_ecs_service.main[0].deployment_circuit_breaker[0].rollback && !aws_ecs_service.main[0].enable_execute_command
    error_message = "Private redundant tasks, rollback and no shell access are required."
  }
  assert {
    condition     = aws_appautoscaling_target.main[0].min_capacity == 2 && aws_appautoscaling_target.main[0].max_capacity == 4
    error_message = "Scaling must be bounded."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.web.from_port == 3000 && aws_vpc_security_group_ingress_rule.database.from_port == 5432
    error_message = "Do not expose backend or database ports to the internet."
  }
}
run "reject_mutable_images" {
  command = plan
  variables { api_image = "example:latest" }
  expect_failures = [var.api_image]
}
run "reject_insecure_identity" {
  command = plan
  variables { oidc_issuer = "http://identity.example.test" }
  expect_failures = [var.oidc_issuer]
}
run "reject_single_subnet" {
  command = plan
  variables { private_subnet_ids = ["subnet-00000000000000001"] }
  expect_failures = [var.private_subnet_ids]
}

override_data {
  target = data.aws_subnet.private["subnet-00000000000000001"]
  values = { vpc_id = "vpc-00000000000000001", availability_zone = "ap-south-1a", map_public_ip_on_launch = false }
}

override_data {
  target = data.aws_subnet.private["subnet-00000000000000002"]
  values = { vpc_id = "vpc-00000000000000001", availability_zone = "ap-south-1b", map_public_ip_on_launch = false }
}

override_data {
  target = data.aws_subnet.public["subnet-00000000000000003"]
  values = { vpc_id = "vpc-00000000000000001", availability_zone = "ap-south-1a", map_public_ip_on_launch = false }
}

override_data {
  target = data.aws_subnet.public["subnet-00000000000000004"]
  values = { vpc_id = "vpc-00000000000000001", availability_zone = "ap-south-1b", map_public_ip_on_launch = false }
}
