$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:SERVER_PORT = "8089"
$env:DB_URL = "jdbc:sqlserver://localhost;instanceName=SQLEXPRESS03;databaseName=quanlydaotao;encrypt=true;trustServerCertificate=true"
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:DB_USERNAME = "sa"
$env:SPRING_DATASOURCE_USERNAME = "sa"

$credentialPath = Join-Path $PSScriptRoot ".local-sql-credential.xml"

if (-not (Test-Path -LiteralPath $credentialPath)) {
    $secret = Read-Host "Nhap mat khau SQL Server cua sa (chi lan dau)" -AsSecureString
    $credential = [System.Management.Automation.PSCredential]::new("sa", $secret)
    $credential | Export-Clixml -LiteralPath $credentialPath
}

$credential = Import-Clixml -LiteralPath $credentialPath
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD

# Kiem tra cong, khong tu dung ung dung khac
$listener = Get-NetTCPConnection -LocalPort 8089 -State Listen `
    -ErrorAction SilentlyContinue

if ($listener) {
    $response = $null

    try {
        $response = Invoke-RestMethod `
            -Uri "http://localhost:8089/v3/api-docs" `
            -TimeoutSec 5
    }
    catch {}

    $isNhom8 = $false

    if ($null -ne $response -and $null -ne $response.paths) {
        $isNhom8 = $response.paths.PSObject.Properties.Name `
            -contains "/api/n8/classes"
    }

    if ($isNhom8) {
        Start-Process "http://localhost:8089/"
        Write-Host "Ung dung da chay. Da mo trang web." -ForegroundColor Green
        exit 0
    }

    throw "Cong 8089 dang duoc su dung nhung chua xac nhan duoc API nhom 8. Kiem tra Terminal Java dang chay."
}

# Cho Java khoi dong thanh cong roi mo trinh duyet
$browserJob = Start-Job -ScriptBlock {
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        try {
            $response = Invoke-WebRequest `
                -Uri "http://localhost:8089/" `
                -UseBasicParsing `
                -TimeoutSec 2

            if ($response.StatusCode -eq 200) {
                Start-Process "http://localhost:8089/"
                return
            }
        }
        catch {}

        Start-Sleep -Seconds 1
    }
}

$arguments = "--server.port=8089 --spring.datasource.url=$($env:DB_URL) --spring.datasource.username=sa"

try {
    Write-Host "Dang chay nhom 8 tai http://localhost:8089/" -ForegroundColor Cyan
    Write-Host "Giu Terminal nay mo. Nhan Ctrl+C khi muon dung."

    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=$arguments"
}
finally {
    Stop-Job -Job $browserJob -ErrorAction SilentlyContinue
    Remove-Job -Job $browserJob -Force -ErrorAction SilentlyContinue

    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
}