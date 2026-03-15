$baseUrl = "http://127.0.0.1:8082"
$loginPath = "/user/login"
$password = "123456"

$startNo = 1
$endNo = 200
$phonePrefix = "139"

$outFile = "tokens.csv"

# 先写表头
"token" | Out-File -FilePath $outFile -Encoding utf8

for ($i = $startNo; $i -le $endNo; $i++) {
    $phone = $phonePrefix + $i.ToString("00000000")

    $bodyObj = @{
        phone = $phone
        password = $password
    }

    $bodyJson = $bodyObj | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Uri ($baseUrl + $loginPath) `
            -Method Post `
            -ContentType "application/json" `
            -Body $bodyJson

        if ($resp.success -eq $true -and $null -ne $resp.data -and $resp.data -ne "") {
            $resp.data | Out-File -FilePath $outFile -Append -Encoding utf8
            Write-Host "OK $phone"
        } else {
            Write-Host "FAIL $phone -> unexpected response"
            $resp | ConvertTo-Json -Depth 10
        }
    }
    catch {
        Write-Host "FAIL $phone -> $($_.Exception.Message)"
    }
}

Write-Host "Done -> $outFile"