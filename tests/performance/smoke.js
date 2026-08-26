import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Smoke performance test for the Internal Developer Portal.
// Verifies the deployed instance stays healthy and responsive under a light,
// realistic load. Thresholds are deliberately conservative for a single
// t3.small instance - they catch a broken deploy, not micro-regressions.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Throwaway credential for the ephemeral load-test account created in setup().
// Generated per run; grants access to nothing beyond that account.
const LOAD_TEST_CREDENTIAL =
  __ENV.K6_CREDENTIAL || `k6-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const errorRate = new Rate('portal_errors');
const authDuration = new Trend('auth_duration_ms');

export const options = {
  scenarios: {
    smoke: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '40s', target: 5 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    portal_errors: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  // Register a dedicated load-test user and reuse its token across VUs.
  const username = `k6-user-${Date.now()}`;
  const payload = {
    username,
    email: `${username}@enterprise.example`,
  };
  payload.password = LOAD_TEST_CREDENTIAL;

  const res = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json' },
  });

  if (res.status !== 201) {
    // Authentication is optional for the smoke run; public endpoints still
    // give a meaningful signal about instance health.
    console.warn(`setup: registration returned ${res.status}, continuing unauthenticated`);
    return { token: null };
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const authHeaders = data.token
    ? { headers: { Authorization: `Bearer ${data.token}` } }
    : { headers: {} };

  group('health', () => {
    const res = http.get(`${BASE_URL}/actuator/health`);
    const ok = check(res, {
      'health returns 200': (r) => r.status === 200,
      'health reports UP': (r) => String(r.body).includes('UP'),
    });
    errorRate.add(!ok);
  });

  group('landing page', () => {
    const res = http.get(`${BASE_URL}/`);
    const ok = check(res, {
      'landing page returns 200': (r) => r.status === 200,
      'landing page renders HTML': (r) => String(r.body).includes('Internal Developer Portal'),
    });
    errorRate.add(!ok);
  });

  group('openapi', () => {
    const res = http.get(`${BASE_URL}/v3/api-docs`);
    const ok = check(res, {
      'openapi returns 200': (r) => r.status === 200,
    });
    errorRate.add(!ok);
  });

  if (data.token) {
    group('catalog', () => {
      const start = Date.now();
      const projects = http.get(`${BASE_URL}/api/v1/projects`, authHeaders);
      authDuration.add(Date.now() - start);

      const teams = http.get(`${BASE_URL}/api/v1/teams`, authHeaders);
      const ok =
        check(projects, { 'projects returns 200': (r) => r.status === 200 }) &&
        check(teams, { 'teams returns 200': (r) => r.status === 200 });
      errorRate.add(!ok);
    });
  }

  group('authz', () => {
    const res = http.get(`${BASE_URL}/api/v1/projects`);
    const ok = check(res, {
      'anonymous catalog access is rejected': (r) => r.status === 401 || r.status === 403,
    });
    errorRate.add(!ok);
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(
      {
        checks_passed: data.metrics.checks ? data.metrics.checks.values.passes : 0,
        checks_failed: data.metrics.checks ? data.metrics.checks.values.fails : 0,
        p95_ms: data.metrics.http_req_duration
          ? Math.round(data.metrics.http_req_duration.values['p(95)'])
          : null,
        request_failure_rate: data.metrics.http_req_failed
          ? data.metrics.http_req_failed.values.rate
          : null,
      },
      null,
      2,
    ),
  };
}
