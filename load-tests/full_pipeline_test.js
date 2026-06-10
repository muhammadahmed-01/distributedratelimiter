/**
 * Full pipeline test — all filters in one request path:
 *   1) Authenticated burst: IP -> JWT -> Account (same token, 15 requests)
 *   2) Distributed race: 50 VUs hammer shared account through full stack
 *
 * Flush Redis before running for clean counters.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/full_pipeline_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';

const stackAllowed = new Counter('stack_allowed');
const stackBlocked = new Counter('stack_blocked');

const burstToken = generateJWT('acc-pipeline-burst');
const raceToken = generateJWT('acc-pipeline-race');

export const options = {
    scenarios: {
        authenticated_burst: {
            executor: 'shared-iterations',
            vus: 3,
            iterations: 15,
            maxDuration: '20s',
            exec: 'authenticatedBurst',
            tags: { phase: 'burst' },
        },
        distributed_race: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 50,
            maxVUs: 50,
            startTime: '22s',
            exec: 'distributedRace',
            tags: { phase: 'race' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        stack_allowed: ['count>=10'],
        stack_blocked: ['count>=3'],
    },
};

export function authenticatedBurst() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${burstToken}` },
    });

    if (res.status === 200) {
        stackAllowed.add(1);
    } else if (res.status === 429) {
        stackBlocked.add(1);
    }

    check(res, {
        'stack: status 200 or 429': (r) => r.status === 200 || r.status === 429,
        'stack: has IP rate limit headers': (r) =>
            r.headers['X-Ratelimit-Limit'] !== undefined ||
            r.headers['X-RateLimit-Limit'] !== undefined,
        'stack: 429 is account not ip': (r) =>
            r.status !== 429 || (r.body && r.body.includes('"type":"account"')),
    });

    sleep(0.05);
}

export function distributedRace() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${raceToken}` },
    });

    if (res.status === 200) {
        stackAllowed.add(1);
    } else if (res.status === 429) {
        stackBlocked.add(1);
    }

    check(res, {
        'stack race: 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.01);
}
