/**
 * Isolated JWT filter test — low request rate to avoid IP/account exhaustion.
 * Tests: anonymous pass-through, valid JWT, invalid JWT, missing accountId claim.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/jwt_filter_test.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, generateJWT } from './lib/jwt.js';

export const options = {
    scenarios: {
        jwt_filter_only: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 4,
            maxDuration: '30s',
            tags: { filter: 'jwt' },
        },
    },
    thresholds: {
        checks: ['rate==1.0'],
    },
};

export default function () {
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
