/**
 * Shared IP counter — anonymous then authenticated traffic uses one IP bucket.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';
import { IP_LIMIT } from './lib/limits.js';

const anonAllowed = new Counter('anon_allowed');
const authAllowed = new Counter('auth_allowed');
const authIpBlocked = new Counter('auth_ip_blocked');

const TOKEN = generateJWT('acc-shared-ip');
const ANON_SEED = IP_LIMIT - 100;
const AUTH_TOP_UP = 200;
const AUTH_ALLOWED_EXPECTED = 100;

export const options = {
    scenarios: {
        anon_seed: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: ANON_SEED,
            maxDuration: '30s',
            exec: 'anonSeed',
            tags: { phase: 'anon_seed' },
        },
        auth_top_up: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '2s',
            preAllocatedVUs: 30,
            maxVUs: 50,
            startTime: '5s',
            exec: 'authTopUp',
            tags: { phase: 'auth_top_up' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        anon_allowed: [`count>=${ANON_SEED - 50}`],
        auth_allowed: [
            `count>=${AUTH_ALLOWED_EXPECTED - 20}`,
            `count<=${AUTH_ALLOWED_EXPECTED + 20}`,
        ],
        auth_ip_blocked: [`count>=${AUTH_TOP_UP - AUTH_ALLOWED_EXPECTED - 30}`],
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

    sleep(0.001);
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

    sleep(0.001);
}
