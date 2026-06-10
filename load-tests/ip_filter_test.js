/**
 * Isolated IP filter test — anonymous requests only (no JWT).
 * Expect: first 100 requests/min allowed, then 429 with type=ip.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/ip_filter_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL } from './lib/jwt.js';

const ipAllowed = new Counter('ip_allowed');
const ipBlocked = new Counter('ip_blocked');

export const options = {
    scenarios: {
        ip_filter_only: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 110,
            maxDuration: '30s',
            tags: { filter: 'ip' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        ip_allowed: ['count>=95'],
        ip_blocked: ['count>=5'],
    },
};

export default function () {
    const res = http.get(BASE_URL);

    if (res.status === 200) {
        ipAllowed.add(1);
    } else if (res.status === 429) {
        ipBlocked.add(1);
    }

    check(res, {
        'ip: status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'ip: rate limit headers present': (r) =>
            r.headers['X-Ratelimit-Limit'] !== undefined ||
            r.headers['X-RateLimit-Limit'] !== undefined,
        'ip: 429 body type is ip': (r) =>
            r.status !== 429 || (r.body && r.body.includes('"type":"ip"')),
    });

    sleep(0.01);
}
