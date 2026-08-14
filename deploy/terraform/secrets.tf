resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password

  tags = { Name = "${var.project_name}-db-password" }
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project_name}/JWT_SECRET"
  type  = "SecureString"
  value = var.jwt_secret

  tags = { Name = "${var.project_name}-jwt-secret" }
}
