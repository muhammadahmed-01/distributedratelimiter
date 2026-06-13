/**
 * Account isolation — parallel accounts from one IP, independent quotas.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';
import { ACCOUNT_LIMIT, ISOLATION_ACCOUNTS, ISOLATION_ITERS_PER_VU } from './lib/limits.js';

const accountAllowed = new Counter('isolation_allowed');
const accountBlocked = new Counter('isolation_blocked');

const BLOCKED_PER_VU = ISOLATION_ITERS_PER_VU - ACCOUNT_LIMIT;
const ALLOWED_TOTAL = ISOLATION_ACCOUNTS * ACCOUNT_LIMIT;
const BLOCKED_TOTAL = ISOLATION_ACCOUNTS * BLOCKED_PER_VU;

export const options = {
    scenarios: {
        parallel_accounts: {
            executor: 'per-vu-iterations',
            vus: ISOLATION_ACCOUNTS,
            iterations: ISOLATION_ITERS_PER_VU,
            maxDuration: '60s',
            tags: { filter: 'account_isolation' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        isolation_allowed: [
            `count>=${Math.floor(ALLOWED_TOTAL * 0.95)}`,
            `count<=${Math.ceil(ALLOWED_TOTAL * 1.05)}`,
        ],
        isolation_blocked: [
            `count>=${Math.floor(BLOCKED_TOTAL * 0.8)}`,
            `count<=${Math.ceil(BLOCKED_TOTAL * 1.2)}`,
        ],
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
            __ITER < ACCOUNT_LIMIT || r.status === 429,
    });

    sleep(0.01);
}
