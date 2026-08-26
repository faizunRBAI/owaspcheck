#!/usr/bin/env bash
# Installs the Puppet agent (used in masterless "puppet apply" mode) on
# Ubuntu 22.04. Idempotent: exits early when the agent is already present.
set -euo pipefail

PUPPET_BIN="/opt/puppetlabs/bin/puppet"

if [ -x "$PUPPET_BIN" ]; then
    echo "Puppet already installed: $("$PUPPET_BIN" --version)"
    exit 0
fi

export DEBIAN_FRONTEND=noninteractive

# Wait for cloud-init / unattended-upgrades to release the dpkg lock.
for i in $(seq 1 30); do
    if ! fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1; then
        break
    fi
    echo "Waiting for dpkg lock to be released (attempt $i)"
    sleep 10
done

CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
RELEASE_DEB="/tmp/puppet-release.deb"

# puppet8-release pins the Puppet 8 apt repository for this Ubuntu release.
for i in $(seq 1 5); do
    if curl -fsSL -o "$RELEASE_DEB" \
        "https://apt.puppet.com/puppet8-release-${CODENAME}.deb"; then
        break
    fi
    echo "Failed to download Puppet release package (attempt $i)"
    sleep 10
done

dpkg -i "$RELEASE_DEB"

for i in $(seq 1 5); do
    if apt-get update; then break; fi
    echo "apt-get update failed (attempt $i)"
    sleep 10
done

apt-get install -y --no-install-recommends puppet-agent

rm -f "$RELEASE_DEB"

echo "Installed Puppet: $("$PUPPET_BIN" --version)"
