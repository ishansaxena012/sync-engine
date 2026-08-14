terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Local state by default for a first deploy. Once this is working, move to an
  # S3 backend (with a DynamoDB lock table) so state isn't only on your laptop:
  #
  # backend "s3" {
  #   bucket         = "your-tfstate-bucket"
  #   key            = "syncengine/terraform.tfstate"
  #   region         = "ap-south-1"
  #   dynamodb_table = "terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
}
