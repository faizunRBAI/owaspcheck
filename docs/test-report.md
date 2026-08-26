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
| Foreign signature | A token signed with a different key is rejected |
| Expired token | A 1 ms token is invalid after it lapses |
| Weak secret | A secret < 32 bytes fails fast at construction |

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
| `checkstyle` | Checkstyle 3.5 | Any `error`-severity violation |
| `spotbugs` | SpotBugs 4.8, effort Max, threshold Medium | Any unexcluded bug |
| `dependency_check` | OWASP Dependency Check 10 | Any CVE with **CVSS ≥ 9** |

Coverage is measured by JaCoCo (`prepare-agent` + `report` at `verify`).

### On suppressions

`config/spotbugs/exclude.xml` and `config/owasp/suppressions.xml` each carry a
written rationale per entry. The OWASP suppression file ships **empty of
findings** — it contains only a documented template. Suppressing a real CVE to
make the build green is a policy violation; the correct fix is a dependency
upgrade.

The `failBuildOnCVSS=9` threshold fails the build on critical findings only.
High/medium findings are reported in the published HTML artifact for triage
without blocking every deploy on a transitive advisory. Tighten this once the
dependency baseline is clean.

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
./mvnw test                      # unit + API tests (H2)
./mvnw verify -Dsurefire.skip=true   # integration tests (needs Docker)
./mvnw checkstyle:check
./mvnw compile spotbugs:check
./mvnw dependency-check:check -DnvdApiKey=$NVD_API_KEY

BASE_URL=http://localhost:8080 k6 run tests/performance/smoke.js
```

Integration tests require a running Docker daemon — Testcontainers starts and
disposes the PostgreSQL container automatically.
