param(
    [string] $Mysql = $env:E2E_MYSQL,
    [string] $Database = $env:E2E_DB_NAME
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Mysql)) { throw 'E2E_MYSQL is required' }
if (-not (Test-Path -LiteralPath $Mysql)) { throw "MySQL client not found: $Mysql" }
foreach ($name in @('E2E_DB_HOST', 'E2E_DB_PORT', 'E2E_DB_NAME', 'E2E_DB_USER')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "$name is required" }
}
if ($env:E2E_DB_HOST -notin @('127.0.0.1', 'localhost', '::1')) { throw 'E2E_DB_HOST must use a loopback host' }
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'E2E_DB_NAME contains unsupported characters' }

$passwordArgument = if ([string]::IsNullOrEmpty($env:E2E_DB_PASSWORD)) { @() } else { @("--password=$($env:E2E_DB_PASSWORD)") }
$deadline = [DateTime]::UtcNow.AddSeconds(10)

while ([DateTime]::UtcNow -lt $deadline) {
    $state = & $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" @passwordArgument "--database=$Database" --batch --skip-column-names --execute "SELECT CONCAT(user_id, ':', room_id) FROM items WHERE id = 900004"
    if ($LASTEXITCODE -ne 0) { throw "Inventory persistence query failed with exit code $LASTEXITCODE" }
    if (($state | Out-String).Trim() -eq '900001:0') {
        Write-Output 'Inventory item 900004 persisted as 900001:0'
        exit 0
    }
    Start-Sleep -Milliseconds 250
}

throw 'Inventory item 900004 did not persist as 900001:0'
