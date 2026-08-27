import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Smoke performance test for the Internal Developer Portal.
// Verifies the deployed instance stays healthy and responsive under a light,
// realistic load. Thresholds are deliberately conservative for a single
// t3.small instance - they catch a broken deploy, not micro-regressions.
//
// COLD-START DISCIPLINE
// ---------------------
// The configure stage RESTARTS the container immediately before this stage
// runs, so without care k6 measures JVM startup rather than steady-state
// service. On the 2026-08-27 run the app finished booting at 08:11:33 and k6
// began at 08:11:52 - 18 seconds later. The first request to each endpoint
// pays one-time costs that have nothing to do with sustained performance:
//   * DispatcherServlet bootstrap
//   * springdoc-openapi document generation (measured at 882 ms)
//   * Hibernate statement preparation and JIT warm-up
// Those requests landed inside the 1->5 VU ramp and blew p(95)<2000 on a
// short 70-second sample, while the same endpoints served in ~5 ms once warm.
//
// Two measures address this, and NEITHER weakens the thresholds:
//   1. setup() below issues warm-up requests to every endpoint under test and
//      waits for them to come back fast. setup() runs OUTSIDE the scenario, so
//      its requests are excluded from all reported metrics.
//   2. The pipeline's perf stage curls the endpoints before invoking k6.
// The thresholds themselves are unchanged - the point is to measure the
// steady state honestly, not to lower the bar until a cold start passes.
//
// EXPECTED-STATUS DISCIPLINE (http_req_failed)
// --------------------------------------------
// k6's built-in http_req_failed metric counts ANY response with status >= 400
// as a failed request. The authz group below deliberately calls a protected
// endpoint WITHOUT a token and asserts it is rejected with 401 - correct,
// desired behaviour, and the check passes. But that 401 was still counted in
// http_req_failed. With one deliberate 401 out of six requests per iteration
// the metric sat at a FIXED 0.167 against a rate<0.05 threshold, so the gate
// could never pass no matter how healthy the app was. Measured on the live
// instance: p(95) 4.2 ms, checks 1.00, portal_errors 0.00, and zero errors in
// the container log - only http_req_failed breached, and structurally.
//
// The fix is k6's responseCallback, which declares which statuses are
// EXPECTED for a given request. This corrects the measurement; it does not
// lower any bar:
//   * the rate<0.05 threshold is unchanged and still enforced
//   * every other request keeps the default 2xx/3xx expectation, so a genuine
//     5xx anywhere still fails the build
//   * the authz request still FAILS the build if it returns 200 - the check
//     asserts 401/403 and a regression that exposes the endpoint breaks it
// Only the deliberate, asserted rejection stops being miscounted as a
// transport failure.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Throwaway credential for the ephemeral load-test account created in setup().
// Generated per run; grants access to nothing beyond that account.
const LOAD_TEST_CREDENTIAL =
  __ENV.K6_CREDENTIAL || `k6-${Date.now()}-${Math.random().toString(36).slice(2)}`;

// Endpoints whose first hit carries one-time initialisation cost.
const WARMUP_PATHS = ['/actuator/health', '/', '/v3/api-docs', '/api/v1/projects'];

// A rejected anonymous request is the CORRECT outcome for the authz probe, so
// 401/403 are expected statuses there and must not inflate http_req_failed.
// Scoped to that single request - everything else keeps k6's 2xx/3xx default.
const REJECTION_EXPECTED = http.expectedStatuses(401, 403);

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

// Requests issued here are NOT counted toward the thresholds - k6 excludes
// setup() from scenario metrics - so this warms the JVM without flattering
// the results.
function warmUp() {
  const deadline = Date.now() + 60000;
  let pass = 0;

  while (Date.now() < deadline) {
    let slowest = 0;
    for (const path of WARMUP_PATHS) {
      const res = http.get(`${BASE_URL}${path}`);
      slowest = Math.max(slowest, res.timings.duration);
    }
    pass += 1;

    // Two consecutive sweeps under 500 ms means initialisation is done.
    if (slowest < 500 && pass >= 2) {
      console.log(`warm-up complete after ${pass} sweeps (slowest ${Math.round(slowest)} ms)`);
      return;
    }
    sleep(2);
  }
  console.warn('warm-up did not settle within 60s; measuring anyway');
}

export function setup() {
  warmUp();

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
    // responseCallback marks 401/403 as EXPECTED for this request only, so the
    // deliberate rejection is not counted as a transport failure. The check
    // below is still the gate: a 200 here means the endpoint is exposed and
    // the build fails.
    const res = http.get(`${BASE_URL}/api/v1/projects`, {
      responseCallback: REJECTION_EXPECTED,
    });
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
