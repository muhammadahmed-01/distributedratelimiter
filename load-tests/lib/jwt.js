import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const JWT_SECRET = __ENV.JWT_SIGNING_KEY || 'my-super-secret-signing-key-which-must-be-32-bytes!';
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/hello';
export const HEALTH_URL = __ENV.HEALTH_URL || BASE_URL.replace('/api/hello', '/actuator/health');

export function generateJWT(accountId, userId = 'u-k6') {
    const payload = {
        userId,
        sub: 'k6-test-user',
        exp: Math.floor(Date.now() / 1000) + 3600,
    };
    if (accountId !== undefined && accountId !== null) {
        payload.accountId = accountId;
    }
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const body = encoding.b64encode(JSON.stringify(payload), 'rawurl');
    const signature = crypto.hmac('sha256', JWT_SECRET, `${header}.${body}`, 'base64url');
    return `${header}.${body}.${signature}`;
}
