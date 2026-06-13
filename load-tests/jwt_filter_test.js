/**
 * JWT filter — correctness checks plus parallel invalid-token load.
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, generateJWT } from './lib/jwt.js';

export const options = {
    scenarios: {
        jwt_correctness: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 4,
            maxDuration: '30s',
            exec: 'correctnessSteps',
            tags: { phase: 'correctness' },
        },
        jwt_invalid_storm: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '3s',
            preAllocatedVUs: 50,
            maxVUs: 80,
            startTime: '2s',
            exec: 'invalidTokenStorm',
            tags: { phase: 'invalid_storm' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate>0.4'],
        http_reqs: ['count>=500'],
    },
};

export function correctnessSteps() {
    const step = __ITER % 4;

    if (step === 0) {
        const res = http.get(BASE_URL);
        check(res, {
            'jwt: anonymous request passes (200)': (r) => r.status === 200,
        });
    } else if (step === 1) {
        const res = http.get(BASE_URL, {
            headers: { Authorization: `Bearer ${generateJWT('acc-jwt-valid')}` },
        });
        check(res, {
            'jwt: valid token passes (200)': (r) => r.status === 200,
        });
    } else if (step === 2) {
        const res = http.get(BASE_URL, {
            headers: { Authorization: 'Bearer not.a.valid.jwt' },
        });
        check(res, {
            'jwt: invalid token rejected (401)': (r) => r.status === 401,
            'jwt: 401 body error is invalid_token': (r) =>
                r.body && r.body.includes('invalid_token'),
        });
    } else {
        const tokenWithoutAccount = generateJWT(undefined);
        const res = http.get(BASE_URL, {
            headers: { Authorization: `Bearer ${tokenWithoutAccount}` },
        });
        check(res, {
            'jwt: missing accountId rejected (401)': (r) => r.status === 401,
        });
    }
}

export function invalidTokenStorm() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: 'Bearer not.a.valid.jwt' },
    });

    check(res, {
        'jwt storm: invalid token rejected (401)': (r) => r.status === 401,
    });
}
