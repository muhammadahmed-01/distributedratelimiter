/**
 * Account isolation — multiple accounts from the same IP do not share account quotas.
 * 5 VUs × 12 requests each (60 total, under IP limit of 100).
 * Each account should get 10 allowed + 2 blocked (type=account), not ip.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/multi_account_isolation_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';

const accountAllowed = new Counter('isolation_allowed');
const accountBlocked = new Counter('isolation_blocked');

export const options = {
    scenarios: {
        parallel_accounts: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 12,
            maxDuration: '30s',
            tags: { filter: 'account_isolation' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        isolation_allowed: ['count>=48', 'count<=52'],
        isolation_blocked: ['count>=8', 'count<=12'],
    },
};

export default function () {
    const accountId = `acc-isolation-${__VU}`;
    const token = generateJWT(accountId);

    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (res.status === 200) {
        accountAllowed.add(1);
    } else if (res.status === 429) {
        accountBlocked.add(1);
    }

    check(res, {
        'isolation: status 200 or 429': (r) => r.status === 200 || r.status === 429,
        'isolation: never blocked by ip': (r) =>
            r.status !== 429 || !r.body || !r.body.includes('"type":"ip"'),
        'isolation: account block after quota': (r) =>
            __ITER < 10 || r.status === 429,
    });

    sleep(0.05);
}
