# test_ip_and_jwt.ps1
# Test both IP filter and JWT filter behaviour.
#  - Phase A: requests WITHOUT JWT (simulate anonymous/attacker traffic)
#  - Phase B: requests WITH JWT (authenticated)
# Prints HTTP status, response body and X-RateLimit headers.

param()

# === Configuration ===
$BaseUrl   = "http://localhost:8080/api/hello"   # endpoint to hit
$Ip        = "203.0.113.10"                     # X-Forwarded-For header value
$OtherIp   = "198.51.100.25"
$JwtToken  = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiYWNjb3VudElkIjoiYWNjLTAwMSIsInVzZXJJZCI6InUtMTIzIiwiZXhwIjoxODkzNDU2MDAwfQ.KpCyEp0YVTL-N5xQndD2Q0Z4TLvJaNHU3jQ3T7H-lFA"           # put a valid token for auth phase
$Limit     = 5                                  # expected ip limit (for readable output)
$Burst     = 7                                  # how many requests to send in each phase
$DelayMs   = 300                                # delay between requests

function Send-Request {
    param(
        [string]$url,
        [string]$ipHeader,
        [string]$jwtToken = $null
    )

    $headers = @{ "X-Forwarded-For" = $ipHeader }
    if ($jwtToken -and $jwtToken.Trim() -ne "") {
        $headers.Add("Authorization", "Bearer $jwtToken")
    }

    try {
        $response = Invoke-WebRequest -Uri $url -Headers $headers -Method GET -ErrorAction Stop
        # Success
        $status = $response.StatusCode
        $body = $response.Content
        $limitHeader = $response.Headers["X-RateLimit-Limit"]
        $remainingHeader = $response.Headers["X-RateLimit-Remaining"]
        $retryAfter = $response.Headers["Retry-After"]

        return [PSCustomObject]@{
            Status    = $status
            Body      = $body
            Limit     = $limitHeader
            Remaining = $remainingHeader
            RetryAfter= $retryAfter
        }
    } catch {
        # Failure (4xx/5xx)
        $ex = $_.Exception
        $status = $null
        $body = "<no body>"
        $limitHeader = $null
        $remainingHeader = $null
        $retryAfter = $null

        if ($ex.Response -ne $null) {
            try {
                $status = $ex.Response.StatusCode.value__
            } catch { $status = $null }
            try {
                $stream = $ex.Response.GetResponseStream()
                if ($stream -ne $null) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $body = $reader.ReadToEnd()
                    $reader.Close()
                }
            } catch {
                $body = "<failed to read body>"
            }
            try { $limitHeader = $ex.Response.Headers["X-RateLimit-Limit"] } catch {}
            try { $remainingHeader = $ex.Response.Headers["X-RateLimit-Remaining"] } catch {}
            try { $retryAfter = $ex.Response.Headers["Retry-After"] } catch {}
        } else {
            $body = $ex.Message
        }

        return [PSCustomObject]@{
            Status    = $status
            Body      = $body
            Limit     = $limitHeader
            Remaining = $remainingHeader
            RetryAfter= $retryAfter
        }
    }
}

Write-Host "=== IP+JWT Rate Limit Test ==="
Write-Host "Endpoint: $BaseUrl"
Write-Host "IP: $Ip, Other IP: $OtherIp"
Write-Host "Burst size: $Burst, Delay: ${DelayMs}ms"
Write-Host "`n--- PHASE A: Requests WITHOUT JWT (anonymous) ---`n"

for ($i = 1; $i -le $Burst; $i++) {
    $result = Send-Request -url $BaseUrl -ipHeader $Ip -jwtToken $null
    if ($result.Status -eq 200 -or $result.Status -eq 201) {
        Write-Host ("#{0} -> Allowed (Status: {1}) Remaining: {2}" -f $i, $result.Status, $result.Remaining) -ForegroundColor Green
    } else {
        Write-Host ("#{0} -> Blocked/Other (Status: {1}) Response: {2} Remaining: {3}" -f $i, $result.Status, $result.Body, $result.Remaining) -ForegroundColor Red
    }
    Start-Sleep -Milliseconds $DelayMs
}

Write-Host "`n--- PHASE A: Other IP sanity check (should be independent) ---`n"
$result = Send-Request -url $BaseUrl -ipHeader $OtherIp -jwtToken $null
Write-Host ("Other IP -> Status: {0}, Remaining: {1}, Body: {2}" -f $result.Status, $result.Remaining, $result.Body)

Write-Host "`n--- PHASE B: Requests WITH JWT (authenticated) ---`n"
if ($JwtToken -eq "<PASTE_VALID_JWT_HERE>" -or [string]::IsNullOrWhiteSpace($JwtToken)) {
    Write-Host "Warning: JwtToken variable is empty or placeholder. Phase B will likely get 401. Paste a valid token into the script to test authenticated flow." -ForegroundColor Yellow
}

for ($i = 1; $i -le $Burst; $i++) {
    $result = Send-Request -url $BaseUrl -ipHeader $Ip -jwtToken $JwtToken
    if ($result.Status -eq 200 -or $result.Status -eq 201) {
        Write-Host ("#{0} -> Auth Allowed (Status: {1}) Remaining: {2}" -f $i, $result.Status, $result.Remaining) -ForegroundColor Green
    } else {
        Write-Host ("#{0} -> Auth Blocked/Other (Status: {1}) Response: {2} Remaining: {3}" -f $i, $result.Status, $result.Body, $result.Remaining) -ForegroundColor Red
    }
    Start-Sleep -Milliseconds $DelayMs
}

Write-Host "`nTest complete."
