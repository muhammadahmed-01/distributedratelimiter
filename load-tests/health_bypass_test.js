/**
 * Health bypass — /actuator/health skips IP rate limiting.
 * Phase 1: exhaust IP quota on /api/hello.
 * Phase 2: /actuator/health must still return 200.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/health_bypass_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, HEALTH_URL } from './lib/jwt.js';

const apiBlocked = new Counter('api_blocked');
const healthOk = new Counter('health_ok');

export const options = {
    scenarios: {
        exhaust_api: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 110,
            maxDuration: '30s',
            exec: 'exhaustApi',
            tags: { phase: 'exhaust_api' },
        },
        health_after_exhaust: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 20,
            maxDuration: '15s',
            startTime: '3s',
            exec: 'healthAfterExhaust',
            tags: { phase: 'health' },
        },
    },
    thresholds: {
        checks: ['rate==1.0'],
        api_blocked: ['count>=5'],
        health_ok: ['count==20'],
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

    sleep(0.01);
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

    sleep(0.05);
}
