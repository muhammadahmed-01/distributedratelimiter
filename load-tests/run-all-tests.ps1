# Run k6 filter tests in sequence with Redis flush between isolated tests.
# Usage: .\load-tests\run-all-tests.ps1

$ErrorActionPreference = "Stop"
$JwtKey = $env:JWT_SIGNING_KEY
if (-not $JwtKey) {
    $JwtKey = "my-super-secret-signing-key-which-must-be-32-bytes!"
}

$IpLimit = if ($env:RATELIMIT_IP_LIMIT) { $env:RATELIMIT_IP_LIMIT } else { "2000" }
$AccountLimit = if ($env:RATELIMIT_ACCOUNT_LIMIT) { $env:RATELIMIT_ACCOUNT_LIMIT } else { "200" }
$RedisContainer = "distributed-rate-limiter-redis-1"

function Flush-Redis {
    Write-Host "`n>> Flushing Redis for clean filter state..." -ForegroundColor Cyan
    docker exec $RedisContainer redis-cli FLUSHALL | Out-Null
}

function Run-K6($script, $label) {
    Write-Host "`n========================================" -ForegroundColor Yellow
    Write-Host "  $label" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Yellow
    k6 run -e "JWT_SIGNING_KEY=$JwtKey" -e "RATELIMIT_IP_LIMIT=$IpLimit" -e "RATELIMIT_ACCOUNT_LIMIT=$AccountLimit" $script
    if ($LASTEXITCODE -ne 0) { throw "k6 failed: $script" }
}

Flush-Redis
Run-K6 "load-tests/ip_filter_test.js" "IP Filter (isolated)"

Flush-Redis
Run-K6 "load-tests/jwt_filter_test.js" "JWT Filter (isolated)"

Flush-Redis
Run-K6 "load-tests/account_filter_test.js" "Account Filter (isolated)"

Flush-Redis
Run-K6 "load-tests/ip_jwt_combo_test.js" "IP + JWT (auth blocked by IP quota)"

Flush-Redis
Run-K6 "load-tests/shared_ip_counter_test.js" "Shared IP counter (anon + auth)"

Flush-Redis
Run-K6 "load-tests/multi_account_isolation_test.js" "Multi-account isolation (same IP)"

Flush-Redis
Run-K6 "load-tests/health_bypass_test.js" "Health endpoint bypass"

Flush-Redis
Run-K6 "load-tests/full_pipeline_test.js" "Full Pipeline (all filters)"

Write-Host "`nAll k6 tests completed successfully." -ForegroundColor Green
