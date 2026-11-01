locals {
  name         = "travbook-${var.environment}"
  repositories = [for image in [var.api_image, var.web_image] : "arn:aws:ecr:${split(".", image)[3]}:${split(".", image)[0]}:repository/${split("@", split("amazonaws.com/", image)[1])[0]}"]
}
resource "aws_security_group" "alb" {
  name_prefix = "${local.name}-alb-"
  description = "HTTPS ingress to travel frontend only"
  vpc_id      = var.vpc_id
}
resource "aws_security_group" "tasks" {
  name_prefix = "${local.name}-tasks-"
  description = "Private web/API task boundary"
  vpc_id      = var.vpc_id
}
resource "aws_security_group" "db" {
  name_prefix = "${local.name}-db-"
  description = "PostgreSQL from application tasks only"
  vpc_id      = var.vpc_id
}
resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
resource "aws_vpc_security_group_egress_rule" "alb_web" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 3000
}
resource "aws_vpc_security_group_ingress_rule" "web" {
  security_group_id            = aws_security_group.tasks.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 3000
}
resource "aws_vpc_security_group_egress_rule" "tls" {
  security_group_id = aws_security_group.tasks.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
resource "aws_vpc_security_group_egress_rule" "database" {
  security_group_id            = aws_security_group.tasks.id
  referenced_security_group_id = aws_security_group.db.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_vpc_security_group_ingress_rule" "database" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
resource "aws_kms_key" "database" {
  description             = "${local.name} transactional data encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  lifecycle { prevent_destroy = true }
}
resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = var.private_subnet_ids
  lifecycle {
    precondition {
      condition     = length(toset([for subnet in data.aws_subnet.private : subnet.availability_zone])) >= 2 && alltrue([for subnet in data.aws_subnet.private : subnet.vpc_id == var.vpc_id && !subnet.map_public_ip_on_launch])
      error_message = "Database/task subnets must be private, in the approved VPC and span at least two AZs."
    }
  }
}
resource "aws_db_parameter_group" "main" {
  name_prefix = "${local.name}-"
  family      = "postgres17"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}
resource "aws_db_instance" "main" {
  identifier                      = local.name
  engine                          = "postgres"
  engine_version                  = var.database_engine_version
  instance_class                  = var.database_class
  db_name                         = "travbook"
  username                        = "travbook_admin"
  manage_master_user_password     = true
  master_user_secret_kms_key_id   = aws_kms_key.database.arn
  allocated_storage               = 30
  max_allocated_storage           = 100
  storage_type                    = "gp3"
  storage_encrypted               = true
  kms_key_id                      = aws_kms_key.database.arn
  multi_az                        = true
  publicly_accessible             = false
  db_subnet_group_name            = aws_db_subnet_group.main.name
  vpc_security_group_ids          = [aws_security_group.db.id]
  parameter_group_name            = aws_db_parameter_group.main.name
  backup_retention_period         = 14
  backup_window                   = "20:00-21:00"
  maintenance_window              = "sun:21:30-sun:22:30"
  deletion_protection             = true
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${local.name}-final"
  copy_tags_to_snapshot           = true
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  auto_minor_version_upgrade      = false
  apply_immediately               = false
  lifecycle { prevent_destroy = true }
}
resource "aws_lb" "main" {
  name                       = local.name
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = var.public_subnet_ids
  enable_deletion_protection = true
  drop_invalid_header_fields = true
  # ALB access logs include OAuth callback query credentials and cannot redact them.
  # Use aggregated ALB/WAF metrics and redacted application logs.
  lifecycle {
    precondition {
      condition     = length(toset([for subnet in data.aws_subnet.public : subnet.availability_zone])) >= 2 && alltrue([for subnet in data.aws_subnet.public : subnet.vpc_id == var.vpc_id])
      error_message = "ALB subnets must span at least two AZs in the approved VPC."
    }
  }
}
resource "aws_lb_target_group" "web" {
  name        = local.name
  port        = 3000
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    path                = "/api/v1/locations/DEL"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  deregistration_delay = 45
}
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
  }
}
resource "aws_route53_record" "web" {
  zone_id = var.zone_id
  name    = var.domain_name
  type    = "A"
  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}
resource "aws_wafv2_web_acl" "main" {
  name  = local.name
  scope = "REGIONAL"
  default_action {
    allow {}
  }
  rule {
    name     = "managed-common"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name}-common"
      sampled_requests_enabled   = false
    }
  }
  rule {
    name     = "bounded-ip-traffic"
    priority = 2
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name}-rate"
      sampled_requests_enabled   = false
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = local.name
    sampled_requests_enabled   = false
  }
}
resource "aws_wafv2_web_acl_association" "main" {
  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.main.arn
}

# Validate caller-supplied network topology during the account-specific plan.
data "aws_subnet" "private" {
  for_each = toset(var.private_subnet_ids)
  id       = each.value
}
data "aws_subnet" "public" {
  for_each = toset(var.public_subnet_ids)
  id       = each.value
}
