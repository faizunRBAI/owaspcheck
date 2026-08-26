# Test Report — Internal Developer Portal (`owaspcheck`)

This document describes the test strategy, the suites that run in CI, and where
to find the generated evidence for any given pipeline run.

> **Where the live numbers are.** This file describes *what* is tested and the
> gates that enforce it. Per-run pass/fail counts, coverage percentages and CVE
> findings are produced by the pipeline and published as workflow artifacts —
> they are deliberately not hardcoded here, because a checked-in number goes
> stale the moment the next commit lands.

---

## Test pyramid

| Level | Suite | Runs in | Isolation |
| --- | --- | --- | --- |
| Unit | `*Test` (services, JWT) | `unit_tests` stage | Mockito, no I/O |
| Component/API | `*ApiTest` (MockMvc) | `unit_tests` stage | In-memory H2 |
| Integration | `*IT` (Testcontainers) | `integration_tests` stage | Real PostgreSQL 16 |
| Performance | `tests/performance/smoke.js` | `perf` stage | Live deployed instance |

Surefire runs `**/*Test.java`; Failsafe runs `**/*IT.java`. The split is
enforced in `pom.xml`, so integration tests never slow down the fast feedback
loop.

---

## Unit tests

### `JwtTokenProviderTest`

Covers the security-critical token logic:

| Case | Asserts |
| --- | --- |
| Token round-trip | `sub` and `role` claims survive issue → parse |
| Expiry reporting | `expiresInSeconds` derives from `expiration-ms` |
| Tampered token | Signature mismatch is rejected, not thrown |
| Garbage input | Non-JWT input returns invalid rather than raising |
| Foreign signature | A token signed with different material is rejected |
| Expired token | A 1 ms token is invalid after it lapses |
| Weak signing material | Material < 32 bytes fails fast at construction |

The last case matters operationally: it turns a silently weak HS256 key into a
startup failure.

### `TeamServiceTest`, `ProjectServiceTest`

Business rules with mocked repositories:

- List/read mapping including derived fields (`teamName`)
- `404` path — missing id raises `ResourceNotFoundException`
- `409` path — duplicate name raises `DuplicateResourceException` and **never**
  calls `save`
- Rename collision detection on update
- Team resolution on project create, including unknown-team `404`
- Detaching a project from its team (`teamId: null`)

---

## API tests — `AuthControllerApiTest`

Full Spring context on H2, exercising the HTTP layer and the security filter
chain:

| Case | Expected |
| --- | --- |
| `POST /api/v1/auth/register` | `201` + non-empty `accessToken` |
| Invalid registration payload | `400` with `fieldErrors` |
| Login with unknown credentials | `401` |
| `GET /api/v1/projects` anonymous | `401` |
| `GET /actuator/health` | `200` (public) |
| `GET /v3/api-docs` | `200` (public) |

These assert the **public/private boundary** declared in `SecurityConfig` — a
regression that accidentally exposes the catalog fails here.

---

## Integration tests — `CatalogCrudIT`

Runs against a real `postgres:16-alpine` container via Testcontainers, with
**Flyway migrations applied exactly as in production** and
`hibernate.ddl-auto=validate`. This is what proves the JPA entities and the SQL
migrations agree — a mismatch fails the build rather than the deploy.

| Case | Asserts |
| --- | --- |
| Seed data migrated | `V2` reference data is queryable through the API |
| Full lifecycle | Team → Project → Environment → Deployment created and read back |
| Relationship resolution | `environmentName` resolved on a deployment read |
| Filtering | `?environmentId=` returns only that environment's deployments |
| Status update | `PUT` transitions a deployment to `SUCCEEDED` |
| Cascade delete | Deleting a project removes its environments and deployments |
| Duplicate name | Second create returns `409` |
| Unknown id | `404` with the standard error envelope |
| Unauthenticated | `401` without a token |

Each test authenticates as a freshly registered user, so the suite is
order-independent and safe to re-run.

**Requires a Docker daemon.** This suite fails hard where none is available —
that is intentional. It is the only check proving the entity model and the
migrations agree, so it is never silently downgraded to H2.

---

## Performance test — `tests/performance/smoke.js`

k6 smoke test executed by the `perf` stage **against the deployed instance**
after the health check passes.

Profile: ramp 1 → 5 VUs over 20 s, hold 5 VUs for 40 s, ramp down.

Thresholds (build fails if breached):

| Threshold | Value | Rationale |
| --- | --- | --- |
| `http_req_failed` | `rate < 0.05` | Under 5% request failures |
| `http_req_duration` | `p(95) < 2000ms` | Conservative for one t3.small |
| `portal_errors` | `rate < 0.05` | Custom per-group error rate |
| `checks` | `rate > 0.95` | Functional assertions hold under load |

Scenario groups: `health`, `landing page`, `openapi`, `catalog` (authenticated),
`authz` (confirms anonymous access is still rejected **under load**, not just in
a quiet unit test).

Thresholds are intentionally loose. This gate is designed to catch a broken or
badly misconfigured deployment, not micro-regressions — tightening it without
capacity data would produce flaky builds.

---

## Static analysis and security gates

| Gate | Tool | Failure condition |
| --- | --- | --- |
| `checkstyle` | Checkstyle 3.6 | Any `error`-severity violation |
| `spotbugs` | SpotBugs 4.8, effort Max, threshold Medium | Any unexcluded bug |
| `dependency_check` | OWASP Dependency Check 12.1.9 | Any CVE with **CVSS ≥ 9** |

Coverage is measured by JaCoCo (`prepare-agent` + `report` at `verify`).

---

## Dependency vulnerability policy — read this before touching suppressions

`failBuildOnCVSS` is **9** and has never been raised. High/medium findings are
published in the HTML artifact for triage without blocking every deploy on a
transitive advisory; criticals block.

### Current state (as of 2026-08-26)

`config/owasp/suppressions.xml` is **not empty**. It carries four dated entries
covering 16 critical CVEs. Understanding why matters more than the count.

**What was fixed by upgrading.** The first deploy failed on 20 criticals against
Spring Boot 3.3.5. The response was an upgrade, not a suppression:

| Change | Effect |
| --- | --- |
| `spring-boot-starter-parent` 3.3.5 → **3.5.9** | Current supported GA line |
| `tomcat.version` pinned 10.1.31 → **10.1.48** | Newest 10.1.x |
| `jjwt` 0.12.6 → 0.13.0, `springdoc` 2.6.0 → 2.8.13 | Patched transitive trees |

That cleared five criticals outright: **CVE-2024-50379, CVE-2024-56337,
CVE-2025-24813, CVE-2025-31651, CVE-2025-55754**.

**What could not be fixed by upgrading.** 16 criticals remained — filed against
the *newest published release* of each library, with nothing newer to move to:

| Dependency | Version | Newest available? | Criticals |
| --- | --- | --- | --- |
| `spring-boot`, `spring-boot-starter-web` | 3.5.9 | yes | CVE-2026-40974 (9.8), CVE-2026-40971 (9.1) |
| `spring-core`, `spring-web` | 6.2.15 | Boot-managed | CVE-2026-41855 (9.8) |
| `spring-security-core`, `spring-security-web` | 6.5.7 | Boot-managed | CVE-2026-22732 (9.1) |
| `tomcat-embed-core` | 10.1.48 | yes | 9 CVEs (9.1–9.8) |

These are suppressed with **individually enumerated CVE ids** — no package-wide
wildcards — each stating the version confirmed to have no fix.

### The rules

1. A suppression is permitted **only** when the dependency is already on its
   newest published release. "The build is red" is not a justification.
2. Every entry names exact CVE ids. Never a blanket package wildcard.
3. Every entry carries an `until` date.
4. `failBuildOnCVSS` is never raised to dodge a finding.

### ⚠️ Expiry: 2026-11-30

All four entries expire then and **the build will go red by design**. That is
the mechanism forcing re-review.

Do **not** simply extend the dates. Re-run the scan, upgrade whatever now has a
patched release, delete those entries, and re-date only what is still genuinely
unpatched.

### Compensating controls for the residual risk

Suppression does not remove risk, so the deployment reduces exposure elsewhere:

- Not multi-tenant; holds no payment or PII data
- Container runs as a non-root uid, `--read-only`, `--security-opt
  no-new-privileges`, memory-capped
- Tomcat is not directly internet-facing — nginx terminates public traffic and
  proxies to `127.0.0.1:8080`
- Only 80/443/22 reachable; RDS is private, reachable only from the app SG
- OS patching automated via `unattended-upgrades` (Puppet hardening module)

### A note on the scanner itself

Dependency Check was moved **10.0.4 → 12.1.9**. Version 10.0.4's embedded H2
schema declares `reference.url` as `VARCHAR(1000)`, while NVD now publishes
reference URLs well beyond that:

```
DatabaseException: Error updating 'CVE-2026-6785';
Value too long for column "URL CHARACTER VARYING(1000)": "...(1585)"
```

On 10.0.4 this aborts the feed update mid-download, leaving the CVE database
incomplete — the scan then runs against partial data, which is worse than a
loud failure. The Sonatype OSS Index analyzer is also disabled explicitly
(no credentials are held; it logged `Invalid credentials for the OSS Index` for
every artifact and disabled itself anyway). NVD remains authoritative.

### SpotBugs exclusions

`config/spotbugs/exclude.xml` documents a rationale per entry: JPA entity and
Spring-injected-collaborator accessors flagged as `EI_EXPOSE_REP`, immutable
record DTOs, and test sources. No finding is excluded to silence a real defect.

---

## Artifacts published per run

| Artifact | Contents |
| --- | --- |
| `unit-test-report` | `target/surefire-reports` (XML + txt) |
| `integration-test-report` | `target/failsafe-reports` |
| `owasp-dependency-check-report` | `dependency-check-report.html` |

Download them from the **Actions → run → Artifacts** panel.

---

## Running locally

```bash
./mvnw test                          # unit + API tests (H2)
./mvnw verify -Dsurefire.skip=true   # integration tests (needs Docker)
./mvnw checkstyle:check
./mvnw compile spotbugs:check

# NVD_API_KEY is read from the environment by the plugin configuration
NVD_API_KEY=... ./mvnw dependency-check:check

BASE_URL=http://localhost:8080 k6 run tests/performance/smoke.js
```

Integration tests require a running Docker daemon — Testcontainers starts and
disposes the PostgreSQL container automatically.
