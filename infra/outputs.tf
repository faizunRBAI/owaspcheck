output "instance_public_ip" {
  description = "Elastic IP address of the application instance."
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "EC2 instance id of the application host."
  value       = aws_instance.app.id
}

output "application_url" {
  description = "Base URL of the deployed portal."
  value       = "http://${aws_eip.app.public_ip}"
}

output "health_check_url" {
  description = "Health endpoint asserted by the verify stage."
  value       = "http://${aws_eip.app.public_ip}/actuator/health"
}

output "db_address" {
  description = "RDS endpoint hostname (without port)."
  value       = aws_db_instance.main.address
}

output "db_port" {
  description = "RDS port."
  value       = aws_db_instance.main.port
}

output "db_name" {
  description = "Database name created on the RDS instance."
  value       = aws_db_instance.main.db_name
}

output "db_username" {
  description = "Master username for the RDS instance."
  value       = aws_db_instance.main.username
}

output "vpc_id" {
  description = "Id of the portal VPC."
  value       = aws_vpc.main.id
}
