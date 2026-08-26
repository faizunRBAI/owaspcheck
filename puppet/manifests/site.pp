# Masterless Puppet bootstrap for the Internal Developer Portal host.
#
# Responsibilities (server baseline only - the application itself is delivered
# afterwards by Ansible):
#   * install the Java runtime
#   * install and enable Docker
#   * create system users and groups
#   * apply OS hardening
#
# Applied by the CI puppet_bootstrap stage with:
#   puppet apply --detailed-exitcodes \
#     --modulepath=/tmp/puppet/modules /tmp/puppet/manifests/site.pp
#
# Exit code 2 means "changes applied successfully" and is treated as success.

node default {

  # Refresh the package index once; every package resource depends on it.
  exec { 'apt_update':
    command => '/usr/bin/apt-get update',
    path    => ['/usr/bin', '/bin', '/usr/sbin', '/sbin'],
    timeout => 900,
  }

  Package {
    require => Exec['apt_update'],
  }

  include baseline
  include java
  include docker
  include users
  include hardening

  # Explicit ordering: baseline packages, then runtimes, then accounts, then
  # the hardening pass that tightens what the earlier classes created.
  Class['baseline']
  -> Class['java']
  -> Class['docker']
  -> Class['users']
  -> Class['hardening']
}
