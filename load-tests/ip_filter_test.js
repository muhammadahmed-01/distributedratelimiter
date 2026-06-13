/**
 * Isolated IP filter test — high-volume anonymous burst.
 * Expect: first IP_LIMIT requests/min allowed, then 429 type=ip.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL } from './lib/jwt.js';
import { IP_LIMIT, IP_OVERAGE } from './lib/limits.js';

const ipAllowed = new Counter('ip_allowed');
const ipBlocked = new Counter('ip_blocked');

const BLOCKED_MIN = Math.floor(IP_OVERAGE * 0.5);
const ALLOWED_MIN = IP_LIMIT - Math.floor(IP_OVERAGE * 0.1);

export const options = {
    scenarios: {
        ip_filter_burst: {
            executor: 'constant-arrival-rate',
            rate: 800,
            timeUnit: '1s',
            duration: '6s',
            preAllocatedVUs: 100,
            maxVUs: 150,
            tags: { filter: 'ip' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        ip_allowed: [`count>=${ALLOWED_MIN}`],
        ip_blocked: [`count>=${BLOCKED_MIN}`],
        http_reqs: ['count>=3000'],
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

    sleep(0.001);
}
