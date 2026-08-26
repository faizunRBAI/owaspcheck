variable "project_name" {
  description = "Branch-scoped project name used as the prefix for every resource."
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "ssh_public_key" {
  description = "Public key registered as the EC2 key pair for deploy access."
  type        = string
}

variable "db_password" {
  description = "Master password for the RDS PostgreSQL instance."
  type        = string
  sensitive   = true
}

variable "instance_type" {
  description = "EC2 instance type hosting the application container."
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "RDS instance class for the PostgreSQL database."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GiB for the RDS instance."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Initial database name created on the RDS instance."
  type        = string
  default     = "idp"
}

variable "db_username" {
  description = "Master username for the RDS PostgreSQL instance."
  type        = string
  default     = "idpadmin"
}

variable "vpc_cidr" {
  description = "CIDR block for the portal VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "app_port" {
  description = "Port the application container listens on inside the host."
  type        = number
  default     = 8080
}
