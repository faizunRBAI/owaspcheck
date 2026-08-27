# Deployment Summary — Internal Developer Portal (`owaspcheck`)

How this system reaches production, what each stage owns, and how to operate it
afterwards.

---

## Target environment

| Property | Value |
| --- | --- |
| Cloud | AWS |
| Region | `us-east-1` |
| Account | Configured platform AWS account |
| Compute | EC2 `t3.small`, Ubuntu 22.04 LTS |
| Address | Elastic IP (stable across instance restarts) |
| Database | RDS PostgreSQL 16.4, `db.t3.micro`, single-AZ, private |
| Image delivery | Registry-free — `docker save` → workflow artifact → `docker load` |
| Front door | nginx on `:80` → container on `127.0.0.1:8080` |
| State | Platform-managed S3 backend, key `<project>/terraform.tfstate` |

---

## Pipeline

The deploy workflow is **rendered from `.udap/pipeline.yaml`** — edit the spec,
never `.github/workflows/deploy.yml`.

```
build
  ├─→ unit_tests ──────────┐
  ├─→ integration_tests ───┤
  ├─→ checkstyle ──────────┼─→ docker_build ─→ provision ─→ puppet_bootstrap
  ├─→ spotbugs ────────────┤                                       │
  └─→ dependency_check ────┘                                       ▼
                                                               configure
                                                                   │
                                                                   ▼
                                                                verify ─→ perf
```

### Stage responsibilities

| Stage | Owns | Fails when |
| --- | --- | --- |
| `build` | `mvn clean compile` on JDK 21 | Compilation error |
| `unit_tests` | Surefire (`*Test`) | Any unit/API test fails |
| `integration_tests` | Failsafe (`*IT`) on Testcontainers PostgreSQL | Entity/migration drift, CRUD regression |
| `checkstyle` | Style ruleset | Any `error`-severity violation |
| `spotbugs` | Bytecode analysis | Any unexcluded finding |
| `dependency_check` | OWASP CVE scan (NVD API) | CVE with CVSS ≥ 9 |
| `docker_build` | Multi-stage image → `docker save` → workflow artifact | Build or export failure |
| `provision` | `terraform apply` — VPC, EC2, EIP, SGs, IAM, RDS | Any apply error |
| `puppet_bootstrap` | `puppet apply` — Java, Docker, users, hardening | Non-0/2 Puppet exit code |
| `configure` | Ansible — nginx, env file, `docker load`, start | Playbook failure or unhealthy app |
| `verify` | `GET /actuator/health` must return **200** | Non-200 after retries |
| `perf` | k6 smoke test | Any threshold breached |

The quality gates run **in parallel** after `build` and all must pass before an
image is built — a failing test never produces a deployable artifact.

---

## Image delivery: why there is no container registry

The original design pushed the image to **GHCR** (`ghcr.io/<owner>/owaspcheck`).
That path is blocked on this account: the repository lives under a GitHub
**organization**, and creating the first organization-level package requires a
permission the workflow's `GITHUB_TOKEN` does not hold. GHCR rejects the push
with:

```
denied: installation not allowed to Create organization package
```

Rather than depend on an organization setting outside this repository, image
delivery is **registry-free**:

1. `docker_build` builds the image in CI — where the Dockerfile is already
   proven and the build toolchain exists — tagged `owaspcheck:<sha>`.
2. `docker save … | gzip` exports it to `owaspcheck-image.tar.gz`, uploaded as a
   workflow artifact with a 1-day retention.
3. `configure` downloads that artifact, `scp`s it to the instance, and Ansible
   loads it with `docker load`.

The EC2 host therefore never authenticates to a registry and never needs a JDK
or Maven — it receives a finished image. The image tag stays pinned to the
commit SHA, so rollback remains deterministic.

**Trade-offs.** There is no central image store to pull from for ad-hoc runs or
other environments, and the tarball crosses the network on every deploy instead
of relying on layer caching. To restore GHCR later: enable **Package creation**
in the organization's member privileges, then revert `docker_build` to a
`build_push` stage that logs in and pushes, and restore the Ansible pull.

---

## Two-phase server configuration

This is the deliberate split you asked for, and the reason it is worth the
extra stage:

**Phase 1 — Puppet (`puppet_bootstrap`), masterless `puppet apply`.**
Owns the *server baseline*: things that are true of the host regardless of what
runs on it.

- `baseline` — CA certificates, curl, jq, chrony (clock accuracy matters for JWT
  expiry), UTC timezone
- `java` — OpenJDK 21 JDK + `JAVA_HOME` for operational tooling (`jcmd`,
  `jstack`)
- `docker` — Docker CE from the official repository, bounded json-file logging,
  `live-restore`, deploy user in the `docker` group
- `users` — `idp` system user/group at uid/gid 10001 (matching the container's
  non-root uid), `idp-operators` group, `/opt/idp` at `0750`, logrotate policy
- `hardening` — CIS-informed: SSH key-only + no root login + bounded idle
  sessions, sysctl network hardening, `unattended-upgrades`, `auditd`, strict
  permissions on `/etc/{passwd,shadow,group,gshadow}`, `umask 027`

Puppet runs with `--detailed-exitcodes`; exit code **2** means "changes applied"
and is treated as success by the stage.

**Phase 2 — Ansible (`configure`).**
Owns *application delivery* — what changes on every release:

- `nginx` role — reverse proxy vhost, default site removed, `nginx -t`
  validation before reload, security headers, gzip
- `app` role — `0600` root-owned env file, `docker load` from the shipped
  tarball, image-tag verification, systemd unit, service restart, archive
  cleanup, dangling-image prune

Only `ansible.builtin` modules are used, so `ansible-core` resolves everything
with no extra collection install.

> **A note on the firewall.** No host firewall (ufw/nftables) is enabled during
> Puppet hardening. Ingress is already constrained by the EC2 security group to
> 22/80/443, and enabling a local firewall *before* Ansible has configured nginx
> risks locking the pipeline out of the instance. This is a documented decision,
> not an oversight.

---

## Secrets

| Secret | Origin | Consumed by |
| --- | --- | --- |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Platform | `provision`, `configure` |
| `TF_STATE_BUCKET`, `PROJECT_NAME` | Platform | Terraform backend |
| `SSH_USER`, `SSH_PRIVATE_KEY`, `SSH_PUBLIC_KEY` | Platform | `puppet_bootstrap`, `configure` |
| `DB_PASSWORD` | Generated (alphanumeric, ≥20) | RDS master password, app env |
| `JWT_SECRET` | Generated (alphanumeric, ≥32) | Token signing |
| `NVD_API_KEY` | Supplied by the operator | `dependency_check` |

No registry credential is required — image delivery is registry-free.

Secrets appear in files only as `${{ secrets.NAME }}`. The app env file is
written at configure time to `0600` root-owned `/opt/idp/config/app.env` with
`no_log: true` — values are never in the repository, the image, or terraform
state.

---

## Data flow at deploy time

The `configure` stage does **not** receive infrastructure values as job outputs.
GitHub silently drops job outputs containing secret substrings, and
`PROJECT_NAME` is a secret — so any RDS endpoint derived from it would arrive
empty. Instead, `configure` re-runs `terraform init` with the same backend flags
and reads `terraform output -raw db_address` itself. This is the self-sufficient
job pattern and it is why the DB endpoint is never hardcoded or reconstructed.

---

## Verification

The deploy is green only when:

1. `GET http://<eip>/actuator/health` returns **HTTP 200** (retried with backoff
   — the JVM needs boot time after the container starts)
2. The status code is explicitly asserted to equal `200`
3. `GET /v3/api-docs` returns 200, proving the API surface is live, not just the
   process
4. The k6 smoke test passes all thresholds

The health endpoint includes the JPA datasource check, so a `200` also proves
PostgreSQL connectivity through the security group.

---

## Operating the deployment

```bash
# Service state
sudo systemctl status idp-portal
sudo journalctl -u idp-portal -f

# Container
docker ps
docker logs -f idp-portal

# Which image is loaded
docker images owaspcheck

# nginx
sudo nginx -t
sudo tail -f /var/log/nginx/portal-error.log

# Re-apply the server baseline
sudo /opt/puppetlabs/bin/puppet apply \
  --modulepath=/tmp/puppet/modules /tmp/puppet/manifests/site.pp
```

### Rollback

`rollback.strategy: rerun`. To return to a previous version, re-run the deploy
from the last known-good commit — the image is rebuilt from that commit and
tagged with its SHA, so the rollback is deterministic. Terraform reconciles
infrastructure from the same state key.

### Redeploying application code only

A change touching only `src/` still runs the full chain, but `terraform apply`
is a no-op (~30 s) and the effective change is the new image plus an Ansible
service restart.

---

## Cost

| Component | Estimate (us-east-1) |
| --- | --- |
| EC2 `t3.small`, on-demand | ~USD 15/mo |
| 30 GiB gp3 root volume | ~USD 2.40/mo |
| RDS `db.t3.micro` + 20 GiB gp3 | ~USD 13-15/mo |
| Elastic IP (attached) | free |
| **Total** | **~USD 31-33/mo** |

Excludes data transfer. Registry storage costs nothing here because no registry
is used. An idle EIP (instance stopped) is billed hourly — destroy the stack
rather than stopping the instance.

---

## Known limitations

These are deliberate Tier-2 scope decisions, not defects:

1. **HTTP only.** No TLS — the deployment is reached by IP, and certificates
   need a domain. Add certbot + a DNS record for HTTPS.
2. **Single instance.** No ALB or auto-scaling; instance replacement means
   downtime. The EIP keeps the address stable.
3. **Single-AZ RDS.** 7-day automated backups are enabled, but an AZ failure
   requires a restore. Enable Multi-AZ for production SLAs.
4. **SSH open to 0.0.0.0/0** on port 22 so GitHub-hosted runners (whose IP
   ranges are broad and change) can reach the host. Key-only auth and root login
   disabled mitigate this; restrict to a bastion or use SSM-only access to
   remove it.
5. **`skip_final_snapshot = true`** on RDS — teardown is clean but destructive.
   Flip it before holding data you care about.
6. **No container registry.** Images are shipped as workflow artifacts because
   the organization blocks package creation (see *Image delivery* above). This
   means no central image store and a full image transfer per deploy.
