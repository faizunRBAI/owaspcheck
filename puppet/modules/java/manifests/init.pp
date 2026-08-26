# Installs the Java 21 runtime.
#
# The application ships as a container image with its own JRE, but a host-level
# JDK is required for operational tooling (jcmd, jstack, jmap) when diagnosing a
# running service, and for any future non-containerized workload on this host.
class java (
  String $package_name = 'openjdk-21-jdk-headless',
  String $java_home    = '/usr/lib/jvm/java-21-openjdk-amd64',
) {

  package { $package_name:
    ensure => installed,
  }

  # Make the toolchain discoverable for login shells.
  file { '/etc/profile.d/java.sh':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "# Managed by Puppet - do not edit.\nexport JAVA_HOME=${java_home}\nexport PATH=\"\${JAVA_HOME}/bin:\${PATH}\"\n",
    require => Package[$package_name],
  }
}
