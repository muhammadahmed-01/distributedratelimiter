/**
 * Isolated account filter test — same accountId, valid JWT, moderate rate.
 * IP limit (100/min) should not trigger; account limit (10/min) should.
 *
 * Run: k6 run -e JWT_SIGNING_KEY=... load-tests/account_filter_test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';

const accountAllowed = new Counter('account_allowed');
const accountBlocked = new Counter('account_blocked');

const ACCOUNT_ID = 'acc-k6-isolated';
const TOKEN = generateJWT(ACCOUNT_ID);

export const options = {
    scenarios: {
        account_filter_only: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 12,
            maxDuration: '30s',
            tags: { filter: 'account' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        account_allowed: ['count>=10'],
        account_blocked: ['count>=2'],
    },
};

export default function () {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });

    if (res.status === 200) {
        accountAllowed.add(1);
    } else if (res.status === 429) {
        accountBlocked.add(1);
    }

    check(res, {
        'account: status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'account: 429 body type is account': (r) =>
            r.status !== 429 || (r.body && r.body.includes('"type":"account"')),
    });

    sleep(0.05);
}
