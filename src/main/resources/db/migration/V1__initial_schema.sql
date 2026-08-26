-- Internal Developer Portal - initial schema

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'ROLE_USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(1000),
    owner_email VARCHAR(255) NOT NULL,
    slack_channel VARCHAR(120),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL UNIQUE,
    description  VARCHAR(1000),
    repository_url VARCHAR(500),
    lifecycle    VARCHAR(32) NOT NULL DEFAULT 'EXPERIMENTAL',
    team_id      BIGINT REFERENCES teams(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE environments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(32)  NOT NULL DEFAULT 'DEV',
    region      VARCHAR(64),
    endpoint_url VARCHAR(500),
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_environment_per_project UNIQUE (project_id, name)
);

CREATE TABLE deployments (
    id             BIGSERIAL PRIMARY KEY,
    version        VARCHAR(120) NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    commit_sha     VARCHAR(64),
    triggered_by   VARCHAR(150),
    notes          VARCHAR(1000),
    environment_id BIGINT NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_team ON projects(team_id);
CREATE INDEX idx_environments_project ON environments(project_id);
CREATE INDEX idx_deployments_environment ON deployments(environment_id);
CREATE INDEX idx_deployments_status ON deployments(status);
