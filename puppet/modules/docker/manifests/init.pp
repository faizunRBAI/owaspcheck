# Installs Docker CE from Docker's official apt repository and enables the
# daemon. The application container is deployed later by Ansible.
class docker (
  String $docker_user = 'ubuntu',
) {

  $docker_packages = [
    'docker-ce',
    'docker-ce-cli',
    'containerd.io',
    'docker-buildx-plugin',
    'docker-compose-plugin',
  ]

  file { '/etc/apt/keyrings':
    ensure => directory,
    owner  => 'root',
    group  => 'root',
    mode   => '0755',
  }

  # Fetch and dearmor Docker's signing key. Guarded by creates:, so this runs
  # exactly once per host.
  exec { 'docker_gpg_key':
    command => '/bin/bash -c "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg"',
    creates => '/etc/apt/keyrings/docker.gpg',
    path    => ['/usr/bin', '/bin', '/usr/sbin', '/sbin'],
    timeout => 300,
    require => File['/etc/apt/keyrings'],
  }

  file { '/etc/apt/keyrings/docker.gpg':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    require => Exec['docker_gpg_key'],
  }

  exec { 'docker_apt_source':
    command => '/bin/bash -c "echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo \\$VERSION_CODENAME) stable\" > /etc/apt/sources.list.d/docker.list"',
    creates => '/etc/apt/sources.list.d/docker.list',
    path    => ['/usr/bin', '/bin', '/usr/sbin', '/sbin'],
    require => File['/etc/apt/keyrings/docker.gpg'],
  }

  exec { 'docker_apt_update':
    command     => '/usr/bin/apt-get update',
    refreshonly => false,
    timeout     => 600,
    subscribe   => Exec['docker_apt_source'],
    require     => Exec['docker_apt_source'],
  }

  package { $docker_packages:
    ensure  => installed,
    require => Exec['docker_apt_update'],
  }

  # Conservative daemon configuration: bounded log growth so a chatty container
  # cannot fill the root volume.
  file { '/etc/docker/daemon.json':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(EOT),
      {
        "log-driver": "json-file",
        "log-opts": {
          "max-size": "20m",
          "max-file": "5"
        },
        "live-restore": true
      }
      | EOT
    require => Package[$docker_packages],
    notify  => Service['docker'],
  }

  service { 'docker':
    ensure  => running,
    enable  => true,
    require => Package[$docker_packages],
  }

  # Allow the deploy user to drive Docker without sudo, which is what the
  # Ansible stage relies on.
  exec { "docker_group_${docker_user}":
    command => "/usr/sbin/usermod -aG docker ${docker_user}",
    unless  => "/usr/bin/id -nG ${docker_user} | /bin/grep -qw docker",
    require => Service['docker'],
  }
}
