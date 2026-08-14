variable "aws_region" {
  description = "AWS region to deploy into. ap-south-1 keeps latency low to the Supabase pooler this app already points at."
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Short name used to prefix/tag every resource this stack creates."
  type        = string
  default     = "syncengine"
}

variable "container_image_tag" {
  description = "Tag of the image in ECR to deploy (pushed by scripts/push.sh or CI before `terraform apply`)."
  type        = string
  default     = "latest"
}

variable "desired_count" {
  description = "Number of Fargate tasks to run. The app fans collaboration events out over Redis pub/sub (see RedisOperationBroadcaster), so it's safe to run more than one."
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "Fargate task vCPU units (256 = .25 vCPU, 512 = .5 vCPU, 1024 = 1 vCPU)."
  type        = string
  default     = "512"
}

variable "task_memory" {
  description = "Fargate task memory in MiB. Must be a valid pairing for task_cpu."
  type        = string
  default     = "1024"
}

variable "db_url" {
  description = "JDBC URL for Postgres (Supabase pooler), e.g. jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:5432/postgres"
  type        = string
}

variable "db_username" {
  description = "Postgres username."
  type        = string
}

variable "db_password" {
  description = "Postgres password. Stored as a SecureString in SSM Parameter Store, never in the task definition itself."
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "Secret used to sign JWTs. Stored as a SecureString in SSM Parameter Store."
  type        = string
  sensitive   = true
}

variable "cors_allowed_origins" {
  description = "Comma-separated list of origins allowed to call the API / open the WebSocket (your deployed frontend URL)."
  type        = string
}

variable "google_client_id" {
  description = "Google OAuth client ID, if used. Leave blank to fall back to the app's default."
  type        = string
  default     = ""
}

variable "swagger_enabled" {
  description = "Whether to expose /swagger-ui and /v3/api-docs in this environment."
  type        = bool
  default     = false
}
