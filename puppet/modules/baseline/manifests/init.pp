# Common packages and system settings every portal host needs.
#
# Directory ownership for /opt/idp and /var/log/idp is deliberately NOT declared
# here - the users class owns those resources once the service account exists.
class baseline {

  $base_packages = [
    'ca-certificates',
    'curl',
    'gnupg',
    'lsb-release',
    'unzip',
    'jq',
    'chrony',
    'python3',
    'acl',
  ]

  package { $base_packages:
    ensure => installed,
  }

  # Reliable clock: required for correct JWT expiry validation and TLS.
  service { 'chrony':
    ensure  => running,
    enable  => true,
    require => Package['chrony'],
  }

  file { '/etc/timezone':
    ensure  => file,
    content => "Etc/UTC\n",
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
  }
}
