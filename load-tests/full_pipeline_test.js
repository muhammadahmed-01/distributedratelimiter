/**
 * Full pipeline — authenticated burst then high-concurrency race through all filters.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, generateJWT } from './lib/jwt.js';
import { ACCOUNT_LIMIT } from './lib/limits.js';

const stackAllowed = new Counter('stack_allowed');
const stackBlocked = new Counter('stack_blocked');
const stackBlockedIp = new Counter('stack_blocked_ip');
const stackBlockedAccount = new Counter('stack_blocked_account');

const burstToken = generateJWT('acc-pipeline-burst');
const raceToken = generateJWT('acc-pipeline-race');

export const options = {
    scenarios: {
        authenticated_burst: {
            executor: 'constant-arrival-rate',
            rate: 300,
            timeUnit: '1s',
            duration: '3s',
            preAllocatedVUs: 60,
            maxVUs: 80,
            exec: 'authenticatedBurst',
            tags: { phase: 'burst' },
        },
        distributed_race: {
            executor: 'constant-arrival-rate',
            rate: 3000,
            timeUnit: '1s',
            duration: '20s',
            preAllocatedVUs: 200,
            maxVUs: 300,
            startTime: '25s',
            exec: 'distributedRace',
            tags: { phase: 'race' },
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        stack_allowed: [`count>=${ACCOUNT_LIMIT}`],
        stack_blocked: ['count>=5000'],
        http_reqs: ['count>=50000'],
    },
};

export function authenticatedBurst() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${burstToken}` },
    });

    if (res.status === 200) {
        stackAllowed.add(1);
    } else if (res.status === 429) {
        stackBlocked.add(1);
        if (res.body && res.body.includes('"type":"ip"')) {
            stackBlockedIp.add(1);
        } else if (res.body && res.body.includes('"type":"account"')) {
            stackBlockedAccount.add(1);
        }
    }

    check(res, {
        'stack: status 200 or 429': (r) => r.status === 200 || r.status === 429,
        'stack: has IP rate limit headers': (r) =>
            r.headers['X-Ratelimit-Limit'] !== undefined ||
            r.headers['X-RateLimit-Limit'] !== undefined,
        'stack burst: 429 is account not ip': (r) =>
            r.status !== 429 || (r.body && r.body.includes('"type":"account"')),
    });

    sleep(0.001);
}

export function distributedRace() {
    const res = http.get(BASE_URL, {
        headers: { Authorization: `Bearer ${raceToken}` },
    });

    if (res.status === 200) {
        stackAllowed.add(1);
    } else if (res.status === 429) {
        stackBlocked.add(1);
        if (res.body && res.body.includes('"type":"ip"')) {
            stackBlockedIp.add(1);
        } else if (res.body && res.body.includes('"type":"account"')) {
            stackBlockedAccount.add(1);
        }
    }

    check(res, {
        'stack race: 200 or 429': (r) => r.status === 200 || r.status === 429,
        'stack race: 429 body has type': (r) =>
            r.status !== 429 || (r.body && (r.body.includes('"type":"ip"') || r.body.includes('"type":"account"'))),
    });

    sleep(0.001);
}
