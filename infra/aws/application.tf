resource "aws_cloudwatch_log_group" "application" {
  name              = "/travbook/${var.environment}/application"
  retention_in_days = 30
}
resource "aws_iam_role" "execution" {
  name               = "${local.name}-execution"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole", Condition = { StringEquals = { "aws:SourceAccount" = var.account_id }, ArnLike = { "aws:SourceArn" = "arn:aws:ecs:${var.region}:${var.account_id}:*" } } }] })
}
resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = aws_iam_role.execution.assume_role_policy
  # Application code does not call AWS APIs; no task policies are attached.
}
resource "aws_iam_role_policy" "execution" {
  name = "specific-images-logs-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({ Version = "2012-10-17", Statement = concat([
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = local.repositories },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.application.arn}:*" },
    { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = [var.database_credentials_secret_arn, var.oidc_client_secret_arn] }
  ], length(var.secret_kms_key_arns) > 0 ? [{ Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.secret_kms_key_arns, Condition = { StringEquals = { "kms:ViaService" = "secretsmanager.${var.region}.amazonaws.com" } } }] : []) })
}
resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}
resource "aws_ecs_task_definition" "main" {
  family                   = local.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "3072"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    cpu_architecture        = var.cpu_architecture
    operating_system_family = "LINUX"
  }
  ephemeral_storage { size_in_gib = 21 }
  volume { name = "api-temp" }
  volume { name = "web-temp" }
  container_definitions = jsonencode([
    {
      name            = "backend", image = var.api_image, essential = true, user = "10001:10001", readonlyRootFilesystem = true, memory = 2048,
      portMappings    = [{ containerPort = 8080, protocol = "tcp" }], stopTimeout = 45,
      linuxParameters = { capabilities = { drop = ["ALL"] } },
      mountPoints     = [{ sourceVolume = "api-temp", containerPath = "/tmp", readOnly = false }],
      environment = [for name, value in {
        SPRING_PROFILES_ACTIVE = "oidc", BIND_ADDRESS = "127.0.0.1", WEB_ORIGIN = "https://${var.domain_name}",
        INTEGRATION_MODE       = "MOCK", SESSION_COOKIE_SECURE = "true", OIDC_ISSUER = var.oidc_issuer,
        OIDC_CLIENT_ID         = var.oidc_client_id, OIDC_AUDIENCE = "travbook-api",
        DATABASE_URL           = "jdbc:postgresql://${aws_db_instance.main.address}:5432/travbook?sslmode=verify-full&sslrootcert=/app/certs/rds-global-bundle.pem"
      } : { name = name, value = value }],
      secrets = [
        { name = "DATABASE_USERNAME", valueFrom = "${var.database_credentials_secret_arn}:username::" },
        { name = "DATABASE_PASSWORD", valueFrom = "${var.database_credentials_secret_arn}:password::" },
        { name = "OIDC_CLIENT_SECRET", valueFrom = var.oidc_client_secret_arn }
      ],
      healthCheck      = { command = ["CMD-SHELL", "curl --fail --silent --max-time 3 http://127.0.0.1:8080/actuator/health/readiness || exit 1"], interval = 15, timeout = 5, retries = 3, startPeriod = 60 },
      logConfiguration = { logDriver = "awslogs", options = { "awslogs-group" = aws_cloudwatch_log_group.application.name, "awslogs-region" = var.region, "awslogs-stream-prefix" = "api" } }
    },
    {
      name             = "frontend", image = var.web_image, essential = true, user = "1000:1000", readonlyRootFilesystem = true, memory = 768,
      portMappings     = [{ containerPort = 3000, protocol = "tcp" }],
      dependsOn        = [{ containerName = "backend", condition = "HEALTHY" }],
      linuxParameters  = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true },
      mountPoints      = [{ sourceVolume = "web-temp", containerPath = "/tmp", readOnly = false }],
      environment      = [{ name = "HOSTNAME", value = "0.0.0.0" }, { name = "PORT", value = "3000" }],
      logConfiguration = { logDriver = "awslogs", options = { "awslogs-group" = aws_cloudwatch_log_group.application.name, "awslogs-region" = var.region, "awslogs-stream-prefix" = "web" } }
    }
  ])
}
resource "aws_ecs_service" "main" {
  count                              = var.database_ready ? 1 : 0
  name                               = local.name
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.main.arn
  desired_count                      = 2
  launch_type                        = "FARGATE"
  platform_version                   = "1.4.0"
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  wait_for_steady_state              = true
  enable_execute_command             = false
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "frontend"
    container_port   = 3000
  }
  lifecycle { ignore_changes = [desired_count] }
  depends_on = [aws_lb_listener.https, aws_iam_role_policy.execution]
}
resource "aws_appautoscaling_target" "main" {
  count              = var.database_ready ? 1 : 0
  min_capacity       = 2
  max_capacity       = 4
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.main[0].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}
resource "aws_appautoscaling_policy" "cpu" {
  count              = var.database_ready ? 1 : 0
  name               = local.name
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.main[0].resource_id
  scalable_dimension = aws_appautoscaling_target.main[0].scalable_dimension
  service_namespace  = "ecs"
  target_tracking_scaling_policy_configuration {
    target_value       = 65
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageCPUUtilization" }
  }
}
resource "aws_cloudwatch_metric_alarm" "unhealthy" {
  alarm_name          = "${local.name}-unhealthy"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  period              = 60
  evaluation_periods  = 3
  statistic           = "Maximum"
  treat_missing_data  = "notBreaching"
  dimensions          = { LoadBalancer = aws_lb.main.arn_suffix, TargetGroup = aws_lb_target_group.web.arn_suffix }
  alarm_actions       = [var.alarm_topic_arn]
}
resource "aws_cloudwatch_metric_alarm" "database_storage" {
  alarm_name          = "${local.name}-database-storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  comparison_operator = "LessThanThreshold"
  threshold           = 5368709120
  period              = 300
  evaluation_periods  = 2
  statistic           = "Minimum"
  treat_missing_data  = "breaching"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  alarm_actions       = [var.alarm_topic_arn]
}
resource "aws_budgets_budget" "monthly" {
  name         = local.name
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
  cost_filter {
    name   = "TagKeyValue"
    values = [format("user:Environment$%s", var.environment)]
  }
  notification {
    comparison_operator       = "GREATER_THAN"
    threshold                 = 80
    threshold_type            = "PERCENTAGE"
    notification_type         = "ACTUAL"
    subscriber_sns_topic_arns = [var.alarm_topic_arn]
  }
}
