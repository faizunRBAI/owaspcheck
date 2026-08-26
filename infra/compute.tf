data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "deploy" {
  key_name   = "${var.project_name}-deploy-key"
  public_key = var.ssh_public_key

  tags = {
    Name = "${var.project_name}-deploy-key"
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.deploy.key_name
  iam_instance_profile   = aws_iam_instance_profile.app.name

  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv2 only
  }

  # user_data only prepares the box for SSH; all real configuration is
  # performed by Puppet (bootstrap) and Ansible (application delivery).
  user_data = <<-EOT
    #!/bin/bash
    set -euxo pipefail
    export DEBIAN_FRONTEND=noninteractive
    for i in $(seq 1 30); do
      if apt-get update; then break; fi
      echo "apt-get update failed (attempt $i), retrying"
      sleep 10
    done
    apt-get install -y --no-install-recommends ca-certificates curl gnupg lsb-release
    touch /var/lib/cloud/instance/udap-bootstrap-complete
  EOT

  tags = {
    Name = "${var.project_name}-app"
    Role = "application"
  }

  # IAM instance profile creation races instance launch; make the dependency
  # explicit so the first apply does not fail intermittently.
  depends_on = [
    aws_iam_instance_profile.app,
    aws_internet_gateway.main,
  ]
}

resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = {
    Name = "${var.project_name}-eip"
  }

  depends_on = [aws_internet_gateway.main]
}
