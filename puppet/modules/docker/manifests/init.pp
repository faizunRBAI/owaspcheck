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

  # Resolved at CATALOG COMPILE time from facter, never by shell interpolation.
  #
  # A previous revision built this line with nested shell quoting:
  #   echo "deb [...] $(. /etc/os-release && echo \$VERSION_CODENAME) stable"
  # Passing that through Puppet's string parsing into `bash -c` expanded
  # $VERSION_CODENAME in the OUTER shell (to an empty string) before the
  # subshell ever sourced /etc/os-release. The resulting line was
  #   deb [...] https://download.docker.com/linux/ubuntu  stable
  # with the codename missing, which apt rejects as
  #   E: Malformed entry 1 in list file .../docker.list (Component)
  # That breaks EVERY apt operation on the host, so puppet could not even
  # prefetch the package provider and the whole catalog failed.
  $docker_codename = fact('os.distro.codename')
  $docker_arch     = fact('os.architecture')

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

  # A file resource rather than an exec with creates:. The exec's guard only
  # checked EXISTENCE, so a host that already had the malformed line would
  # skip regeneration forever and stay broken. A file resource enforces the
  # CONTENT, which makes the fix self-healing on re-run.
  file { '/etc/apt/sources.list.d/docker.list':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "deb [arch=${docker_arch} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${docker_codename} stable\n",
    require => File['/etc/apt/keyrings/docker.gpg'],
    notify  => Exec['docker_apt_update'],
  }

  exec { 'docker_apt_update':
    command     => '/usr/bin/apt-get update',
    refreshonly => true,
    timeout     => 600,
    require     => File['/etc/apt/sources.list.d/docker.list'],
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
    path    => ['/usr/bin', '/bin', '/usr/sbin', '/sbin'],
    require => Service['docker'],
  }
}
