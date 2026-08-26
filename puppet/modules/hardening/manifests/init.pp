# OS hardening baseline, modelled on the CIS Ubuntu Linux 22.04 Benchmark.
#
# Scope is deliberately limited to controls that are safe to apply to a
# single-purpose application host reached over SSH from CI. Controls that would
# break the deploy path (for example disabling SSH entirely, or enabling a
# restrictive firewall before Ansible has run) are intentionally excluded and
# documented as such.
class hardening {

  # ---------------------------------------------------------------------
  # SSH daemon policy
  # ---------------------------------------------------------------------
  # Key-only authentication, no direct root login, bounded idle sessions.
  file { '/etc/ssh/sshd_config.d/99-hardening.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0600',
    content => @(EOT),
      # Managed by Puppet - do not edit.
      PermitRootLogin no
      PasswordAuthentication no
      PermitEmptyPasswords no
      ChallengeResponseAuthentication no
      KbdInteractiveAuthentication no
      X11Forwarding no
      MaxAuthTries 4
      LoginGraceTime 30
      ClientAliveInterval 300
      ClientAliveCountMax 2
      AllowTcpForwarding no
      Protocol 2
      | EOT
    notify  => Exec['sshd_reload'],
  }

  # Validate before reloading: a malformed drop-in would otherwise lock CI out
  # of the host permanently.
  exec { 'sshd_reload':
    command     => '/usr/sbin/sshd -t && /bin/systemctl reload ssh',
    refreshonly => true,
    path        => ['/usr/sbin', '/usr/bin', '/bin', '/sbin'],
  }

  # ---------------------------------------------------------------------
  # Kernel network hardening
  # ---------------------------------------------------------------------
  file { '/etc/sysctl.d/99-hardening.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(EOT),
      # Managed by Puppet - do not edit.
      # Reverse-path filtering and spoofing protection
      net.ipv4.conf.all.rp_filter = 1
      net.ipv4.conf.default.rp_filter = 1
      net.ipv4.conf.all.accept_source_route = 0
      net.ipv4.conf.default.accept_source_route = 0
      net.ipv4.conf.all.accept_redirects = 0
      net.ipv4.conf.default.accept_redirects = 0
      net.ipv4.conf.all.secure_redirects = 0
      net.ipv4.conf.all.send_redirects = 0
      net.ipv4.conf.default.send_redirects = 0
      net.ipv4.conf.all.log_martians = 1
      net.ipv4.icmp_echo_ignore_broadcasts = 1
      net.ipv4.icmp_ignore_bogus_error_responses = 1
      net.ipv4.tcp_syncookies = 1
      # IPv6 router advertisements are not used on this host
      net.ipv6.conf.all.accept_ra = 0
      net.ipv6.conf.all.accept_redirects = 0
      # Restrict kernel pointer and dmesg exposure
      kernel.dmesg_restrict = 1
      kernel.kptr_restrict = 2
      # Core dumps may contain secrets
      fs.suid_dumpable = 0
      | EOT
    notify  => Exec['sysctl_reload'],
  }

  exec { 'sysctl_reload':
    command     => '/sbin/sysctl --system',
    refreshonly => true,
    path        => ['/sbin', '/usr/sbin', '/usr/bin', '/bin'],
  }

  # ---------------------------------------------------------------------
  # Automatic security updates
  # ---------------------------------------------------------------------
  package { 'unattended-upgrades':
    ensure => installed,
  }

  file { '/etc/apt/apt.conf.d/20auto-upgrades':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(EOT),
      // Managed by Puppet - do not edit.
      APT::Periodic::Update-Package-Lists "1";
      APT::Periodic::Unattended-Upgrade "1";
      APT::Periodic::AutocleanInterval "7";
      | EOT
    require => Package['unattended-upgrades'],
  }

  service { 'unattended-upgrades':
    ensure  => running,
    enable  => true,
    require => Package['unattended-upgrades'],
  }

  # ---------------------------------------------------------------------
  # Filesystem permissions on sensitive files
  # ---------------------------------------------------------------------
  file { '/etc/passwd':
    ensure => file,
    owner  => 'root',
    group  => 'root',
    mode   => '0644',
  }

  file { '/etc/shadow':
    ensure => file,
    owner  => 'root',
    group  => 'shadow',
    mode   => '0640',
  }

  file { '/etc/group':
    ensure => file,
    owner  => 'root',
    group  => 'root',
    mode   => '0644',
  }

  file { '/etc/gshadow':
    ensure => file,
    owner  => 'root',
    group  => 'shadow',
    mode   => '0640',
  }

  # ---------------------------------------------------------------------
  # Login policy
  # ---------------------------------------------------------------------
  file { '/etc/issue.net':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "Authorized access only. All activity is logged and monitored.\n",
  }

  # Restrictive default umask for interactive sessions.
  file { '/etc/profile.d/99-umask.sh':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "# Managed by Puppet - do not edit.\numask 027\n",
  }

  # ---------------------------------------------------------------------
  # Auditing
  # ---------------------------------------------------------------------
  package { 'auditd':
    ensure => installed,
  }

  service { 'auditd':
    ensure  => running,
    enable  => true,
    require => Package['auditd'],
  }

  # NOTE: a host firewall (ufw/nftables) is intentionally NOT enabled here.
  # Ingress is already restricted by the EC2 security group to 22/80/443, and
  # enabling a local firewall during bootstrap - before Ansible has configured
  # nginx - risks locking the deploy pipeline out of the instance.
}
