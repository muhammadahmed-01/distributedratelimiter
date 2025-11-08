# test_ip_jwt_account.ps1
param()

# === Configuration ===
$BaseUrl   = "http://localhost:8080/api/hello"   # endpoint to hit
$Ip        = "203.0.113.10"                     # X-Forwarded-For header
$OtherIp   = "198.51.100.25"
$JwtToken  = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiYWNjb3VudElkIjoiYWNjLTAwMSIsInVzZXJJZCI6InUtMTIzIiwiZXhwIjoxODkzNDU2MDAwfQ.KpCyEp0YVTL-N5xQndD2Q0Z4TLvJaNHU3jQ3T7H-lFA"                                 # <-- paste valid token here
$Burst     = 7
$DelayMs   = 300
$AccountId = "test-user-123"                    # account for account-level limiter

function Read-ResponseBodySafely($responseObj) {
    if ($null -eq $responseObj) { return "<no response>" }
    try { if ($responseObj -and $responseObj.Content) { return $responseObj.Content } } catch {}
    try {
        $stream = $responseObj.GetResponseStream()
        if ($stream -ne $null) {
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            $reader.Close()
            return $body
        }
    } catch {}
    return "<could not read body>"
}

function Send-Request {
    param(
        [string]$url,
        [string]$ipHeader,
        [string]$jwtToken = $null,
        [string]$accountId = $null
    )

    $headers = @{ "X-Forwarded-For" = $ipHeader }
    if ($jwtToken -and $jwtToken.Trim() -ne "") { $headers.Add("Authorization", "Bearer $jwtToken") }
    if ($accountId -and $accountId.Trim() -ne "") { $headers.Add("X-Account-Id", $accountId) }

    try {
        $response = Invoke-WebRequest -Uri $url -Headers $headers -Method GET -ErrorAction Stop
        $body = Read-ResponseBodySafely $response
        return [PSCustomObject]@{
            Status    = $response.StatusCode
            Body      = $body
            Headers   = $response.Headers
        }
    } catch {
        $ex = $_.Exception
        $status = $null
        $body = "<no body>"
        $hdrs = $null
        if ($ex.Response -ne $null) {
            try { $status = $ex.Response.StatusCode.value__ } catch {}
            try {
                $stream = $ex.Response.GetResponseStream()
                if ($stream -ne $null) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $body = $reader.ReadToEnd()
                    $reader.Close()
                }
            } catch { $body = "<failed to read body>" }
            try { $hdrs = $ex.Response.Headers } catch {}
        } else { $body = $ex.Message }
        return [PSCustomObject]@{
            Status    = $status
            Body      = $body
            Headers   = $hdrs
        }
    }
}

function Print-Result($i, $tag, $result) {
    $status = if ($null -eq $result.Status) { "<no-status>" } else { $result.Status }
    $remaining = "<n/a>"
    if ($result.Headers -ne $null) {
        try { $remaining = $result.Headers["X-RateLimit-Remaining"] } catch {}
    }
    if ($status -ge 200 -and $status -lt 300) {
        Write-Host ("{0} #{1} -> Allowed (Status: {2}) Remaining: {3}" -f $tag, $i, $status, $remaining) -ForegroundColor Green
    } else {
        Write-Host ("{0} #{1} -> Blocked/Other (Status: {2}) Remaining: {3}" -f $tag, $i, $status, $remaining) -ForegroundColor Red
        Write-Host ("    Body: {0}" -f $result.Body)
    }
}

Write-Host "=== IP + JWT + Account Rate Limit Tester ==="
Write-Host "Endpoint: $BaseUrl"
Write-Host "Burst: $Burst, DelayMs: $DelayMs"
Write-Host ""

# --- Phase A: Anonymous IP-only requests ---
Write-Host "--- PHASE A: Anonymous (IP-only) ---`n"
for ($i = 1; $i -le $Burst; $i++) {
    $r = Send-Request -url $BaseUrl -ipHeader $Ip
    Print-Result $i "ANON" $r
    Start-Sleep -Milliseconds $DelayMs
}

# --- Phase B: Authenticated requests (IP + JWT) ---
Write-Host "`n--- PHASE B: Authenticated (IP + JWT) ---`n"
if ([string]::IsNullOrWhiteSpace($JwtToken)) {
    Write-Host "Warning: JwtToken is empty. Paste your token into the script variable for authenticated flow." -ForegroundColor Yellow
}
for ($i = 1; $i -le $Burst; $i++) {
    $r = Send-Request -url $BaseUrl -ipHeader $Ip -jwtToken $JwtToken
    Print-Result $i "AUTH" $r
    Start-Sleep -Milliseconds $DelayMs
}

# --- Phase C: Authenticated + Account (IP + JWT + Account limiter) ---
Write-Host "`n--- PHASE C: Authenticated + Account Rate Limit ---`n"
for ($i = 1; $i -le $Burst + 3; $i++) {  # extra requests to exceed account limit
    $r = Send-Request -url $BaseUrl -ipHeader $Ip -jwtToken $JwtToken -accountId $AccountId
    Print-Result $i "ACC" $r
    Start-Sleep -Milliseconds $DelayMs
}

# --- Phase C: Other account sanity check ---
Write-Host "`n--- SANITY: Other Account ---"
$r = Send-Request -url $BaseUrl -ipHeader $Ip -jwtToken $JwtToken -accountId "other-user"
Print-Result 1 "OTHER-ACC" $r

Write-Host "`nDone."
