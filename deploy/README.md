# Deploying SyncEngine's backend to AWS

Architecture: **ECS Fargate** behind an **Application Load Balancer**, reading/writing the
existing **Supabase Postgres** instance, with a new **ElastiCache Redis** node for
pub/sub and session caching. Images live in **ECR**. All of it is defined in
[`deploy/terraform`](./terraform).

No NAT Gateway — Fargate tasks run in public subnets with public IPs, locked down by
security groups. This is cheaper for a small deployment; see `network.tf` if you want
to move to private subnets + NAT later.

## Prerequisites

Install once, locally:

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html), then `aws configure` with an IAM user/role that has admin (or at least ECS/ECR/EC2/ELB/ElastiCache/IAM/SSM) permissions.
- [Terraform >= 1.5](https://developer.hashicorp.com/terraform/install)
- Docker (to build/push the image)

## 1. Provision the infrastructure

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars: db_username, db_password, jwt_secret, cors_allowed_origins, ...

terraform init
terraform plan   # review what it's about to create
terraform apply
```

This creates: VPC + 2 public subnets, security groups, ECR repo, ALB + target group,
ECS cluster/task/service (starts with a placeholder `:latest` image — it'll be
unhealthy until step 2), ElastiCache Redis, SSM parameters for the two secrets, and a
GitHub OIDC role for CI (step 3).

Note the outputs, especially `ecr_repository_url`, `alb_dns_name`, and
`github_deploy_role_arn`.

## 2. Build and push the image, first time by hand

```bash
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-south-1.amazonaws.com

docker build -t <ecr_repository_url>:latest .
docker push <ecr_repository_url>:latest

aws ecs update-service --cluster syncengine-cluster --service syncengine-service --force-new-deployment
```

Give it a minute, then check `http://<alb_dns_name>/actuator/health` — should return
`{"status":"UP"}`. If it doesn't, check the CloudWatch log group `/ecs/syncengine`
(`terraform output cloudwatch_log_group`).

## 3. Wire up CI (optional but recommended)

[`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) builds the image,
pushes to ECR, and updates the ECS service on every push to `main`, using the
`github_deploy_role_arn` OIDC role from step 1 (no AWS keys stored in GitHub).

In the GitHub repo settings → Secrets and variables → Actions → **Variables**, add:

- `AWS_DEPLOY_ROLE_ARN` = the `github_deploy_role_arn` Terraform output

That's it — push to `main` and it deploys.

## 4. Point the frontend at it

Update the frontend's API base URL / WebSocket URL to `http://<alb_dns_name>` (or your
domain once you add one — see below), and make sure `cors_allowed_origins` in
`terraform.tfvars` matches the frontend's actual origin exactly (scheme + host, no
trailing slash), then `terraform apply` again if you change it.

## Adding a custom domain + HTTPS

Once you have a domain:

1. Request/validate a certificate in **ACM** for that domain (must be in the same
   region as the ALB).
2. In `alb.tf`, change the HTTP listener's default action to a redirect to HTTPS, and
   add an `aws_lb_listener` on port 443 using the ACM cert, forwarding to the same
   target group.
3. Point a Route 53 (or your DNS provider's) record at the ALB's DNS name (alias if
   Route 53, CNAME otherwise).
4. Update `cors_allowed_origins` to the frontend's real domain, and the frontend's API
   URL to `https://your-api-domain`.

## Scaling notes

- The app broadcasts collaboration events across instances via Redis pub/sub
  (`RedisOperationBroadcaster`), so `desired_count > 1` is safe — a client connected to
  any task sees operations from clients connected to any other task.
- Bump `desired_count`, `task_cpu`, `task_memory` in `terraform.tfvars` as load grows.
  For real autoscaling, add an `aws_appautoscaling_target`/`policy` pair on the ECS
  service tracking CPU or request count per target.

## Cost (rough, ap-south-1, idle)

- Fargate (0.5 vCPU / 1 GB, 1 task): ~$15/mo
- ALB: ~$17/mo + traffic
- ElastiCache cache.t3.micro: ~$12/mo
- ECR, CloudWatch logs, data transfer: a few dollars

No RDS charge since Postgres stays on Supabase. Total: roughly **$45-50/mo** baseline.

## Tearing it down

```bash
cd deploy/terraform
terraform destroy
```

This deletes everything the stack created (ALB, ECS, ElastiCache, VPC, ECR — the ECR
repo and its images too, since it has no `prevent_destroy`). Supabase is untouched
since it was never managed by this Terraform.
