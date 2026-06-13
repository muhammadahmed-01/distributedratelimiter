/**
 * IP + JWT — exhaust IP quota, then prove valid JWT still gets 429 type=ip.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';
import { IP_LIMIT, IP_OVERAGE } from './lib/limits.js';

const ipExhaustAllowed = new Counter('ip_exhaust_allowed');
const authBlockedByIp = new Counter('auth_blocked_by_ip');

const TOKEN = generateJWT('acc-ip-jwt-combo');
const AUTH_AFTER = Math.max(50, Math.floor(IP_OVERAGE * 0.5));
const ALLOWED_MIN = IP_LIMIT - Math.floor(IP_OVERAGE * 0.1);

export const options = {
    scenarios: {
        exhaust_ip_anonymous: {
            executor: 'constant-arrival-rate',
            rate: 600,
            timeUnit: '1s',
            duration: '5s',
            preAllocatedVUs: 80,
            maxVUs: 120,
            exec: 'exhaustIp',
            tags: { phase: 'exhaust' },
        },
        auth_after_ip_exhaust: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '3s',
            preAllocatedVUs: 20,
            maxVUs: 30,
            startTime: '6s',
            exec: 'authAfterExhaust',
            tags: { phase: 'auth_after_ip' },
        },
    },
    thresholds: {
        checks: ['rate==1.0'],
        ip_exhaust_allowed: [`count>=${ALLOWED_MIN}`],
        auth_blocked_by_ip: [`count>=${AUTH_AFTER}`],
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

    sleep(0.001);
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

    sleep(0.01);
}
