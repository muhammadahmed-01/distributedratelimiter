/**
 * Health bypass — /actuator/health skips IP rate limiting after API exhaustion.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, HEALTH_URL } from './lib/jwt.js';
import { IP_OVERAGE } from './lib/limits.js';

const apiBlocked = new Counter('api_blocked');
const healthOk = new Counter('health_ok');

const HEALTH_CHECKS = 100;
const BLOCKED_MIN = Math.floor(IP_OVERAGE * 0.5);

export const options = {
    scenarios: {
        exhaust_api: {
            executor: 'constant-arrival-rate',
            rate: 700,
            timeUnit: '1s',
            duration: '5s',
            preAllocatedVUs: 80,
            maxVUs: 120,
            exec: 'exhaustApi',
            tags: { phase: 'exhaust_api' },
        },
        health_after_exhaust: {
            executor: 'constant-arrival-rate',
            rate: 50,
            timeUnit: '1s',
            duration: '4s',
            preAllocatedVUs: 10,
            maxVUs: 20,
            startTime: '6s',
            exec: 'healthAfterExhaust',
            tags: { phase: 'health' },
        },
    },
    thresholds: {
        checks: ['rate==1.0'],
        api_blocked: [`count>=${BLOCKED_MIN}`],
        health_ok: [`count>=${HEALTH_CHECKS}`],
    },
};

export function exhaustApi() {
    const res = http.get(BASE_URL);

    if (res.status === 429) {
        apiBlocked.add(1);
    }

    check(res, {
        'exhaust api: status 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.001);
}

export function healthAfterExhaust() {
    const res = http.get(HEALTH_URL);

    if (res.status === 200) {
        healthOk.add(1);
    }

    check(res, {
        'health: always 200 even when API IP exhausted': (r) => r.status === 200,
        'health: body reports UP': (r) =>
            r.body && (r.body.includes('"status":"UP"') || r.body.includes('"status":"up"')),
    });

    sleep(0.01);
}
