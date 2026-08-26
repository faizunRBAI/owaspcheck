-- Seed reference data so a fresh portal is not empty on first login.

INSERT INTO teams (name, description, owner_email, slack_channel)
VALUES
    ('Platform Engineering', 'Owns shared developer platform and CI/CD tooling', 'platform@enterprise.example', '#platform-eng'),
    ('Payments', 'Owns payment processing services', 'payments@enterprise.example', '#payments')
ON CONFLICT (name) DO NOTHING;

INSERT INTO projects (name, description, repository_url, lifecycle, team_id)
SELECT 'developer-portal', 'Internal Developer Portal catalog API', 'https://github.com/enterprise/owaspcheck', 'PRODUCTION', t.id
FROM teams t WHERE t.name = 'Platform Engineering'
ON CONFLICT (name) DO NOTHING;

INSERT INTO environments (name, type, region, endpoint_url, project_id)
SELECT 'production', 'PROD', 'us-east-1', 'http://localhost:8080', p.id
FROM projects p WHERE p.name = 'developer-portal'
ON CONFLICT (project_id, name) DO NOTHING;
