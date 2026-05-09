import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// K6 Options
export const options = {
    scenarios: {
        ip_limit_test: {
            executor: 'constant-vus',
            vus: 1,
            duration: '15s',
            tags: { test_type: 'ip_limit' },
        },
        jwt_auth_test: {
            executor: 'constant-vus',
            vus: 1,
            duration: '10s',
            startTime: '15s',
            tags: { test_type: 'jwt_auth' },
        },
        account_limit_test: {
            executor: 'constant-vus',
            vus: 1,
            duration: '15s',
            startTime: '25s',
            tags: { test_type: 'account_limit' },
        },
        distributed_race_condition_test: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '15s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            startTime: '40s',
            tags: { test_type: 'distributed_race' },
        },
    },
    thresholds: {
        'http_req_failed': ['rate<1.0'], // We expect failures (429, 401)
    },
};

const BASE_URL = 'http://localhost:8080/api/hello';
const JWT_SECRET = 'my-super-secret-signing-key-which-must-be-32-bytes!';

// Helper: Generate a valid JWT
function generateJWT(accountId) {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const payload = encoding.b64encode(JSON.stringify({ 
        accountId: accountId, 
        userId: 'u123', 
        sub: 'test-user', 
        exp: Math.floor(Date.now() / 1000) + 3600 
    }), 'rawurl');
    const signature = crypto.hmac('sha256', JWT_SECRET, `${header}.${payload}`, 'base64url');
    return `${header}.${payload}.${signature}`;
}

export default function () {
    const scenario = __ENV.k6_scenario || __VU > 0 ? __VU : 1; 
    
    // 1. IP Limit Test
    if (__VU === 1 && Date.now() % 35000 < 10000) {
        // One IP continuously firing. Expect first few to pass, rest to fail 429.
        const res = http.get(BASE_URL, {
            headers: {
                'X-Forwarded-For': '10.0.0.1',
                'Authorization': `Bearer ${generateJWT('acc-ip-test')}`
            }
        });
        check(res, {
            'is status 200 or 429': (r) => r.status === 200 || r.status === 429,
            'rate limit header present': (r) => r.headers['X-Ratelimit-Limit'] !== undefined,
        });
        sleep(0.05); // 20 req/s, will trip 100/60s IP limit quickly
    }

    // 2. JWT Auth Test
    if (__VU === 1 && Date.now() % 35000 > 10000 && Date.now() % 35000 <= 15000) {
        // Test valid then invalid JWT
        const validRes = http.get(BASE_URL, {
            headers: {
                'X-Forwarded-For': '10.0.0.2',
                'Authorization': `Bearer ${generateJWT('acc-jwt-test')}`
            }
        });
        check(validRes, { 'valid jwt -> 200': (r) => r.status === 200 });

        const invalidRes = http.get(BASE_URL, {
            headers: {
                'X-Forwarded-For': '10.0.0.2',
                'Authorization': `Bearer invalid-token.abc.def`
            }
        });
        check(invalidRes, { 'invalid jwt -> 401': (r) => r.status === 401 });
        sleep(0.5);
    }

    // 3. Account Limit Test
    if (__VU === 1 && Date.now() % 35000 > 15000 && Date.now() % 35000 <= 25000) {
        // Exhaust account limit (10 reqs) without exhausting IP limit (100 reqs)
        const res = http.get(BASE_URL, {
            headers: {
                'X-Forwarded-For': '10.0.0.3', // Different IP
                'Authorization': `Bearer ${generateJWT('acc-exhaust-test')}`
            }
        });
        check(res, {
            'account limit -> 200 or 429': (r) => r.status === 200 || r.status === 429,
        });
        sleep(0.2); // 5 req/s, will trip 10 req account limit in 2 seconds
    }

    // 4. Distributed Race Condition Test
    if (__VU > 1) {
        // 50 concurrent VUs hitting the same account using different IPs
        // The account limit is 10. Exactly 10 should succeed, rest fail.
        const res = http.get(BASE_URL, {
            headers: {
                'X-Forwarded-For': `192.168.1.${__VU}`, // Unique IP per VU avoids IP ban
                'Authorization': `Bearer ${generateJWT('acc-race-test')}` // Shared account
            }
        });
        check(res, {
            'distributed race -> 200 or 429': (r) => r.status === 200 || r.status === 429,
        });
    }
}
