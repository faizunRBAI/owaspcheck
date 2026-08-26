# Internal Developer Portal — API Documentation

The portal exposes a REST API for the enterprise service catalog. The
authoritative, always-current contract is the generated OpenAPI document:

| Artifact | Location |
| --- | --- |
| Swagger UI | `http://<host>/swagger-ui.html` |
| OpenAPI 3 JSON | `http://<host>/v3/api-docs` |

This document explains the semantics that the generated spec cannot express on
its own.

---

## Authentication

All catalog endpoints require a JWT bearer token. Tokens are HS256-signed, carry
`sub` (username), `role` and `iss` claims, and expire after 1 hour by default
(`JWT_EXPIRATION_MS`).

### Obtain a token

```bash
# Register once
curl -sX POST http://<host>/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@enterprise.example","password":"Str0ngPassw0rd"}'

# Subsequent logins
curl -sX POST http://<host>/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Str0ngPassw0rd"}'
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "username": "alice",
  "role": "ROLE_USER"
}
```

### Use the token

```bash
curl -s http://<host>/api/v1/projects \
  -H "Authorization: Bearer $TOKEN"
```

Requests without a valid token receive `401 Unauthorized`.

---

## Public endpoints

These require no authentication:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Landing page (HTML) |
| GET | `/actuator/health` | Liveness/readiness, includes database check |
| GET | `/actuator/info` | Build information |
| GET | `/v3/api-docs` | OpenAPI document |
| GET | `/swagger-ui.html` | Interactive API explorer |
| POST | `/api/v1/auth/register` | Create an account |
| POST | `/api/v1/auth/login` | Exchange credentials for a token |

---

## Resource model

```
Team 1 ──── * Project 1 ──── * Environment 1 ──── * Deployment
```

- Deleting a **Project** cascades to its Environments and their Deployments.
- Deleting a **Team** detaches its Projects (`team_id` becomes `NULL`); projects
  are never deleted implicitly.
- Environment names are unique **per project**, not globally.

---

## Teams — `/api/v1/teams`

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/v1/teams` | `200` array of teams |
| GET | `/api/v1/teams/{id}` | `200` team, `404` if absent |
| POST | `/api/v1/teams` | `201` + `Location`, `409` on duplicate name |
| PUT | `/api/v1/teams/{id}` | `200` updated team |
| DELETE | `/api/v1/teams/{id}` | `204` |

```json
{
  "name": "Platform Engineering",
  "description": "Owns shared developer platform and CI/CD tooling",
  "ownerEmail": "platform@enterprise.example",
  "slackChannel": "#platform-eng"
}
```

Constraints: `name` required, unique, ≤150 chars. `ownerEmail` required, valid
email.

---

## Projects — `/api/v1/projects`

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/v1/projects` | `200` array; `?teamId=<id>` filters by owner |
| GET | `/api/v1/projects/{id}` | `200` project, `404` if absent |
| POST | `/api/v1/projects` | `201`, `409` duplicate name, `404` unknown team |
| PUT | `/api/v1/projects/{id}` | `200` updated project |
| DELETE | `/api/v1/projects/{id}` | `204` (cascades) |

```json
{
  "name": "payments-api",
  "description": "Card payment processing service",
  "repositoryUrl": "https://github.com/enterprise/payments-api",
  "lifecycle": "PRODUCTION",
  "teamId": 1
}
```

`lifecycle` ∈ `EXPERIMENTAL` (default) | `PRODUCTION` | `DEPRECATED`.
`teamId` is optional — `null` leaves the project unowned.

---

## Environments — `/api/v1/environments`

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/v1/environments` | `200` array; `?projectId=<id>` filters |
| GET | `/api/v1/environments/{id}` | `200`, `404` if absent |
| POST | `/api/v1/environments` | `201`, `409` duplicate name in project |
| PUT | `/api/v1/environments/{id}` | `200` |
| DELETE | `/api/v1/environments/{id}` | `204` (cascades to deployments) |

```json
{
  "name": "production",
  "type": "PROD",
  "region": "us-east-1",
  "endpointUrl": "https://payments.enterprise.example",
  "projectId": 1
}
```

`type` ∈ `DEV` (default) | `STAGING` | `PROD`. `projectId` is required.

---

## Deployments — `/api/v1/deployments`

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/api/v1/deployments` | `200` array; `?environmentId=<id>` filters |
| GET | `/api/v1/deployments/{id}` | `200`, `404` if absent |
| POST | `/api/v1/deployments` | `201`, `404` unknown environment |
| PUT | `/api/v1/deployments/{id}` | `200` |
| DELETE | `/api/v1/deployments/{id}` | `204` |

```json
{
  "version": "1.4.2",
  "status": "SUCCEEDED",
  "commitSha": "9f2c1ab",
  "triggeredBy": "ci-bot",
  "notes": "Promoted from staging",
  "environmentId": 3
}
```

`status` ∈ `PENDING` (default) | `IN_PROGRESS` | `SUCCEEDED` | `FAILED` |
`ROLLED_BACK`.

---

## Error format

Every error uses the same envelope:

```json
{
  "timestamp": "2026-08-26T10:15:30.123",
  "status": 409,
  "error": "Conflict",
  "message": "A project named 'payments-api' already exists",
  "path": "/api/v1/projects",
  "fieldErrors": {}
}
```

On validation failures (`400`), `fieldErrors` maps each rejected field to its
message:

```json
{
  "status": 400,
  "message": "Request validation failed",
  "fieldErrors": {
    "ownerEmail": "must be a well-formed email address",
    "name": "must not be blank"
  }
}
```

| Status | Meaning |
| --- | --- |
| 400 | Validation failed — see `fieldErrors` |
| 401 | Missing, expired or invalid bearer token |
| 404 | Resource (or a referenced resource) does not exist |
| 409 | Uniqueness constraint violated |

---

## Worked example

```bash
HOST=http://<host>
TOKEN=$(curl -sX POST $HOST/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Str0ngPassw0rd"}' | jq -r .accessToken)

TEAM=$(curl -sX POST $HOST/api/v1/teams \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Payments","ownerEmail":"payments@enterprise.example"}' | jq -r .id)

PROJECT=$(curl -sX POST $HOST/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"payments-api\",\"lifecycle\":\"PRODUCTION\",\"teamId\":$TEAM}" | jq -r .id)

ENV=$(curl -sX POST $HOST/api/v1/environments \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"production\",\"type\":\"PROD\",\"region\":\"us-east-1\",\"projectId\":$PROJECT}" | jq -r .id)

curl -sX POST $HOST/api/v1/deployments \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"version\":\"1.0.0\",\"status\":\"SUCCEEDED\",\"environmentId\":$ENV}"
```
