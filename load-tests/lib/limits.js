/** Must match application.properties defaults (override via k6 -e RATELIMIT_*). */
export const IP_LIMIT = parseInt(__ENV.RATELIMIT_IP_LIMIT || '2000', 10);
export const ACCOUNT_LIMIT = parseInt(__ENV.RATELIMIT_ACCOUNT_LIMIT || '200', 10);

export const IP_OVERAGE = Math.max(100, Math.floor(IP_LIMIT * 0.1));
export const ACCOUNT_OVERAGE = Math.max(40, Math.floor(ACCOUNT_LIMIT * 0.15));

export const IP_BURST_TOTAL = IP_LIMIT + IP_OVERAGE;
export const ACCOUNT_BURST_TOTAL = ACCOUNT_LIMIT + ACCOUNT_OVERAGE;

/** Leave headroom under IP_LIMIT for multi-account isolation (N accounts × iters each). */
export const ISOLATION_ACCOUNTS = parseInt(__ENV.ISOLATION_ACCOUNTS || '8', 10);
export const ISOLATION_ITERS_PER_VU = ACCOUNT_LIMIT + Math.max(20, Math.floor(ACCOUNT_LIMIT * 0.1));
