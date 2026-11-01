output "web_origin" { value = "https://${var.domain_name}" }
output "database_endpoint" { value = aws_db_instance.main.address }
output "database_master_secret_arn" {
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
  description = "Bootstrap administrators only. Application IAM has no access to this secret."
}
output "task_security_group_id" { value = aws_security_group.tasks.id }
output "cluster_name" { value = aws_ecs_cluster.main.name }
output "service_enabled" { value = var.database_ready }
