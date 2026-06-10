/**
 * IP counter is shared — anonymous and authenticated requests count toward the same IP bucket.
 * Phase 1: 95 anonymous requests.
 * Phase 2: 15 authenticated requests → only ~5 should pass; rest blocked as type=ip.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/shared_ip_counter_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';

const anonAllowed = new Counter('anon_allowed');
const authAllowed = new Counter('auth_allowed');
const authIpBlocked = new Counter('auth_ip_blocked');

const TOKEN = generateJWT('acc-shared-ip');

export const options = {
    scenarios: {
        anon_seed: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 95,
            maxDuration: '20s',
            exec: 'anonSeed',
            tags: { phase: 'anon_seed' },
        },
        auth_top_up: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 15,
            maxDuration: '20s',
            startTime: '2s',
            exec: 'authTopUp',
            tags: { phase: 'auth_top_up' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        anon_allowed: ['count>=90'],
        auth_allowed: ['count>=3', 'count<=7'],
        auth_ip_blocked: ['count>=8'],
    },
};

export function anonSeed() {
    const res = http.get(BASE_URL);

    if (res.status === 200) {
        anonAllowed.add(1);
    }

    check(res, {
        'seed: anonymous status 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.01);
}

export function authTopUp() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });

    if (res.status === 200) {
        authAllowed.add(1);
    } else if (res.status === 429) {
        authIpBlocked.add(1);
    }

    check(res, {
        'top-up: status 200 or 429': (r) => r.status === 200 || r.status === 429,
        'top-up: 429 is ip not account': (r) =>
            r.status !== 429 || (r.body && r.body.includes('"type":"ip"')),
    });

    sleep(0.05);
}
