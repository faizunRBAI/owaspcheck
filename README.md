# owaspcheck — Internal Developer Portal

Enterprise service catalog API: projects, teams, environments and deployments,
secured with JWT and deployed to AWS through a fully automated pipeline.

```
Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Docker · Terraform · Puppet · Ansible · GitHub Actions
```

| | |
| --- | --- |
| **Live** | `http://<elastic-ip>/` |
| **API docs** | `http://<elastic-ip>/swagger-ui.html` |
| **Health** | `http://<elastic-ip>/actuator/health` |

---

## What it does

A developer portal backend that answers "who owns this service, where does it
run, and what was deployed there?"

- **Projects** — the service catalog, with repository links and lifecycle stage
- **Teams** — ownership, contact email and Slack channel
- **Environments** — dev/staging/prod per project, with region and endpoint
- **Deployments** — version, commit SHA, status and who triggered it
- **JWT auth** — stateless HS256 bearer tokens, BCrypt-hashed credentials
- **OpenAPI** — generated spec and Swagger UI, always matching the code

---

## Quick start (local)

Requires JDK 21 and Docker.

```bash
# 1. Start PostgreSQL
docker run -d --name idp-db \
  -e POSTGRES_DB=idp -e POSTGRES_USER=idp -e POSTGRES_PASSWORD=idp \
  -p 5432:5432 postgres:16-alpine

# 2. Configure
cp .env.example .env

# 3. Run (Flyway creates the schema on startup)
./mvnw spring-boot:run
```

Then open <http://localhost:8080/> for the landing page or
<http://localhost:8080/swagger-ui.html> to explore the API.

```bash
# Register and call the catalog
TOKEN=$(curl -sX POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@enterprise.example","password":"Str0ngPassw0rd"}' \
  | jq -r .accessToken)

curl -s localhost:8080/api/v1/projects -H "Authorization: Bearer $TOKEN" | jq
```

---

## Repository layout

```
src/main/java/com/enterprise/idp/
  ├── domain/       JPA entities (Project, Team, Environment, Deployment, UserAccount)
  ├── repository/   Spring Data repositories
  ├── service/      Business rules and transactions
  ├── controller/   REST endpoints
  ├── security/     JWT provider, filter, security configuration
  ├── mapper/       Entity ↔ DTO conversion
  ├── dto/          Request/response records
  ├── exception/    Domain exceptions + global handler
  └── config/       OpenAPI metadata
src/main/resources/db/migration/   Flyway migrations (V1 schema, V2 seed data)
infra/            Terraform — VPC, EC2, EIP, security groups, IAM, RDS
puppet/           Masterless bootstrap — Java, Docker, users, OS hardening
ansible/          Application delivery — nginx, container, env, start
tests/performance/  k6 smoke test
config/           Checkstyle, SpotBugs, OWASP suppression rulesets
docs/             API docs, deployment summary, test report, deployment diagram
.udap/            architecture.d2 (source of truth) + pipeline.yaml
```

---

## Architecture

```
Developer ──HTTP 80──► Elastic IP ──► nginx ──proxy_pass──► Docker container :8080
                                                                    │
                                                            JDBC 5432 (private)
                                                                    ▼
                                                        RDS PostgreSQL 16
```

The application binds `127.0.0.1:8080` only — nginx is the sole public listener.
RDS lives in private subnets and accepts connections exclusively from the
application security group.

See `.udap/architecture.d2` (source of truth) and `docs/deployment-diagram.d2`.

---

## Testing

```bash
./mvnw test                            # unit + API tests (H2)
./mvnw verify -Dsurefire.skip=true     # integration tests (Testcontainers, needs Docker)
./mvnw checkstyle:check
./mvnw compile spotbugs:check
./mvnw dependency-check:check -DnvdApiKey=$NVD_API_KEY

BASE_URL=http://localhost:8080 k6 run tests/performance/smoke.js
```

Integration tests run against a real PostgreSQL 16 container with the production
Flyway migrations and `hibernate.ddl-auto=validate` — entity/schema drift fails
the build, not the deploy.

Full strategy: [`docs/test-report.md`](docs/test-report.md).

---

## Deployment

The pipeline is **rendered from `.udap/pipeline.yaml`**. Edit the spec, never
`.github/workflows/deploy.yml`.

```
build → [unit_tests | integration_tests | checkstyle | spotbugs | dependency_check]
      → docker_push → provision → puppet_bootstrap → configure → verify → perf
```

Server configuration is deliberately two-phase:

| Phase | Tool | Owns |
| --- | --- | --- |
| Bootstrap | Puppet (`puppet apply`) | Java, Docker, system users, OS hardening |
| Delivery | Ansible | nginx, container, environment, service start |

Puppet establishes the baseline that's true of the host; Ansible ships what
changes every release.

Full details: [`docs/deployment-summary.md`](docs/deployment-summary.md).

### Required secrets

Platform-provided: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
`TF_STATE_BUCKET`, `PROJECT_NAME`, `SSH_USER`, `SSH_PRIVATE_KEY`,
`SSH_PUBLIC_KEY`.

Set on the repository: `DB_PASSWORD`, `JWT_SECRET` (≥32 chars), `NVD_API_KEY`.

---

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `idp` | Database name |
| `DB_USER` | `idp` | Database user |
| `DB_PASSWORD` | — | Database password |
| `SERVER_PORT` | `8080` | Application listen port |
| `JWT_SECRET` | dev fallback | HS256 signing key, **≥32 chars** |
| `JWT_EXPIRATION_MS` | `3600000` | Token lifetime |

The application refuses to start if `JWT_SECRET` is shorter than 32 characters —
a weak key fails fast instead of silently degrading token security.

### Rotating `JWT_SECRET`

Update the repository secret and redeploy. All existing tokens are invalidated
immediately; clients must re-authenticate. There is no dual-key grace period.

---

## API

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | public |
| POST | `/api/v1/auth/login` | public |
| GET/POST | `/api/v1/projects` | bearer |
| GET/PUT/DELETE | `/api/v1/projects/{id}` | bearer |
| GET/POST | `/api/v1/teams` | bearer |
| GET/POST | `/api/v1/environments` | bearer |
| GET/POST | `/api/v1/deployments` | bearer |

Filters: `/projects?teamId=`, `/environments?projectId=`,
`/deployments?environmentId=`.

Full reference: [`docs/api-documentation.md`](docs/api-documentation.md).

---

## Known limitations

1. **HTTP only** — TLS requires a domain; add certbot for HTTPS.
2. **Single instance** — no ALB/autoscaling; instance replacement means downtime.
3. **Single-AZ RDS** — 7-day backups enabled; enable Multi-AZ for production SLAs.
4. **SSH open to `0.0.0.0/0`** — GitHub-hosted runner IPs are broad; key-only
   auth and no root login mitigate. Restrict to a bastion or go SSM-only.
5. **`skip_final_snapshot = true`** — clean teardown, but destructive.

---

Built and deployed with [UDAP](https://udap.ai).
