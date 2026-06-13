/**
 * Isolated account filter test — same accountId, sustained authenticated load.
 * IP limit should not trigger; account limit should.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';
import { ACCOUNT_LIMIT, ACCOUNT_OVERAGE } from './lib/limits.js';

const accountAllowed = new Counter('account_allowed');
const accountBlocked = new Counter('account_blocked');

const TOKEN = generateJWT('acc-k6-isolated');
const BLOCKED_MIN = Math.floor(ACCOUNT_OVERAGE * 0.5);
const ALLOWED_MIN = ACCOUNT_LIMIT - Math.floor(ACCOUNT_OVERAGE * 0.1);

export const options = {
    scenarios: {
        account_filter_burst: {
            executor: 'constant-arrival-rate',
            rate: 400,
            timeUnit: '1s',
            duration: '2s',
            preAllocatedVUs: 80,
            maxVUs: 100,
            tags: { filter: 'account' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        account_allowed: [`count>=${ALLOWED_MIN}`],
        account_blocked: [`count>=${BLOCKED_MIN}`],
        http_reqs: ['count>=500'],
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

    sleep(0.001);
}
