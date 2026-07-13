param(
    [string] $Mysql = $env:E2E_MYSQL,
    [string] $Database = $env:E2E_DB_NAME,
    [string] $Ticket = $env:E2E_SSO_TICKET
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Mysql)) { throw 'E2E_MYSQL is required' }
if ([string]::IsNullOrWhiteSpace($Database)) { throw 'E2E_DB_NAME is required' }
if ([string]::IsNullOrWhiteSpace($Ticket)) { throw 'E2E_SSO_TICKET is required' }
if (-not (Test-Path -LiteralPath $Mysql)) { throw "MySQL client not found: $Mysql" }
if ($env:E2E_DB_HOST -notin @('127.0.0.1', 'localhost', '::1')) { throw 'E2E_DB_HOST must use a loopback host' }
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'E2E_DB_NAME contains unsupported characters' }
if ($Ticket -notmatch '^[A-Za-z0-9._-]+$') { throw 'E2E_SSO_TICKET contains unsupported characters' }

$repo = Split-Path -Parent $PSScriptRoot
$dump = Join-Path $repo 'Database\Default Database\FullDatabase.sql'
$migration = Join-Path $repo 'Database\Database Updates\002_backgounds_border.sql'
$seed = Join-Path $PSScriptRoot 'seed.sql'
$temporaryDump = Join-Path ([System.IO.Path]::GetTempPath()) "polaris-e2e-$PID.sql"

try {
    $content = [System.IO.File]::ReadAllText($dump)
    $content = $content.Replace('CREATE DATABASE IF NOT EXISTS `habbo`', "CREATE DATABASE IF NOT EXISTS ``$Database``")
    $content = $content.Replace('USE `habbo`;', "USE ``$Database``;")
    [System.IO.File]::WriteAllText($temporaryDump, $content, [System.Text.UTF8Encoding]::new($false))

    $passwordArgument = if ([string]::IsNullOrEmpty($env:E2E_DB_PASSWORD)) { @() } else { @("--password=$($env:E2E_DB_PASSWORD)") }
    & $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" @passwordArgument --default-character-set=utf8mb4 --execute "source $($temporaryDump.Replace('\', '/'))"
    if ($LASTEXITCODE -ne 0) { throw "Database import failed with exit code $LASTEXITCODE" }

    [System.IO.File]::ReadAllText($migration) | & $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" @passwordArgument "--database=$Database"
    if ($LASTEXITCODE -ne 0) { throw "Database migration failed with exit code $LASTEXITCODE" }

    $seedContent = "SET @e2e_sso_ticket='$Ticket';`n" + [System.IO.File]::ReadAllText($seed)
    $seedContent | & $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" @passwordArgument "--database=$Database"
    if ($LASTEXITCODE -ne 0) { throw "E2E seed failed with exit code $LASTEXITCODE" }
}
finally {
    Remove-Item -LiteralPath $temporaryDump -Force -ErrorAction SilentlyContinue
}
