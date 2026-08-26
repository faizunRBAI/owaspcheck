# Creates the system users and groups the platform runs under.
#
# The application container runs as an unprivileged uid; this class creates the
# matching host-side account so bind-mounted files and logs have a real owner
# and are not written as root.
class users (
  String  $service_user  = 'idp',
  String  $service_group = 'idp',
  Integer $service_uid   = 10001,
  Integer $service_gid   = 10001,
) {

  group { $service_group:
    ensure => present,
    gid    => $service_gid,
    system => true,
  }

  user { $service_user:
    ensure     => present,
    uid        => $service_uid,
    gid        => $service_group,
    system     => true,
    shell      => '/usr/sbin/nologin',
    home       => '/opt/idp',
    managehome => false,
    comment    => 'Internal Developer Portal service account',
    require    => Group[$service_group],
  }

  # Operators group: members may read application logs without root.
  group { 'idp-operators':
    ensure => present,
    system => true,
  }

  file { '/opt/idp':
    ensure  => directory,
    owner   => $service_user,
    group   => $service_group,
    mode    => '0750',
    require => User[$service_user],
  }

  file { '/opt/idp/config':
    ensure  => directory,
    owner   => $service_user,
    group   => $service_group,
    mode    => '0750',
    require => File['/opt/idp'],
  }

  file { '/var/log/idp':
    ensure  => directory,
    owner   => $service_user,
    group   => 'idp-operators',
    mode    => '0750',
    require => [User[$service_user], Group['idp-operators']],
  }

  # Bound log growth for anything written outside Docker's log driver.
  file { '/etc/logrotate.d/idp':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(EOT),
      # Managed by Puppet - do not edit.
      /var/log/idp/*.log {
          daily
          rotate 14
          compress
          delaycompress
          missingok
          notifempty
          copytruncate
      }
      | EOT
    require => File['/var/log/idp'],
  }
}
