/**
 * IP + JWT combination — IP limit applies before JWT/account.
 * Phase 1: exhaust IP quota with anonymous traffic.
 * Phase 2: valid JWT requests must still get 429 type=ip (not account).
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/ip_jwt_combo_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';

const ipExhaustAllowed = new Counter('ip_exhaust_allowed');
const authBlockedByIp = new Counter('auth_blocked_by_ip');

const TOKEN = generateJWT('acc-ip-jwt-combo');

export const options = {
    scenarios: {
        exhaust_ip_anonymous: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 105,
            maxDuration: '30s',
            exec: 'exhaustIp',
            tags: { phase: 'exhaust' },
        },
        auth_after_ip_exhaust: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 5,
            maxDuration: '15s',
            startTime: '3s',
            exec: 'authAfterExhaust',
            tags: { phase: 'auth_after_ip' },
        },
    },
    thresholds: {
        checks: ['rate==1.0'],
        ip_exhaust_allowed: ['count>=95'],
        auth_blocked_by_ip: ['count>=5'],
    },
};

export function exhaustIp() {
    const res = http.get(BASE_URL);

    if (res.status === 200) {
        ipExhaustAllowed.add(1);
    }

    check(res, {
        'exhaust: status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.01);
}

export function authAfterExhaust() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });

    if (res.status === 429) {
        authBlockedByIp.add(1);
    }

    check(res, {
        'auth after exhaust: blocked with 429': (r) => r.status === 429,
        'auth after exhaust: 429 type is ip not account': (r) =>
            r.body && r.body.includes('"type":"ip"'),
    });

    sleep(0.05);
}
