param(
    [string] $Mysql = $env:E2E_MYSQL,
    [string] $Database = $env:E2E_DB_NAME,
    [string] $Ticket = $env:E2E_SSO_TICKET,
    [string] $SecondTicket = $env:E2E_SECOND_SSO_TICKET
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Mysql)) { throw 'E2E_MYSQL is required' }
if ([string]::IsNullOrWhiteSpace($Database)) { throw 'E2E_DB_NAME is required' }
if ([string]::IsNullOrWhiteSpace($Ticket)) { throw 'E2E_SSO_TICKET is required' }
if ([string]::IsNullOrWhiteSpace($SecondTicket)) { throw 'E2E_SECOND_SSO_TICKET is required' }
if (-not (Test-Path -LiteralPath $Mysql)) { throw "MySQL client not found: $Mysql" }
if ($env:E2E_DB_HOST -notin @('127.0.0.1', 'localhost', '::1')) { throw 'E2E_DB_HOST must use a loopback host' }
if ($Database -notmatch '^polaris_e2e_[A-Za-z0-9_]+$') { throw 'E2E_DB_NAME must start with polaris_e2e_' }
if ($Ticket -notmatch '^[A-Za-z0-9._-]+$') { throw 'E2E_SSO_TICKET contains unsupported characters' }
if ($SecondTicket -notmatch '^[A-Za-z0-9._-]+$') { throw 'E2E_SECOND_SSO_TICKET contains unsupported characters' }

$repo = Split-Path -Parent $PSScriptRoot
$baseDatabase = Join-Path $repo 'Emulator\src\main\resources\db\migration\V20260518000000__base_database.sql'
$seed = Join-Path $PSScriptRoot 'seed.sql'

# MYSQL_PWD keeps the password out of the process list.
if (-not [string]::IsNullOrEmpty($env:E2E_DB_PASSWORD)) { $env:MYSQL_PWD = $env:E2E_DB_PASSWORD }
$resetSql = "DROP DATABASE IF EXISTS ``$Database``; CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
& $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" --execute $resetSql
if ($LASTEXITCODE -ne 0) { throw "Database reset failed with exit code $LASTEXITCODE" }

& $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" "--database=$Database" --default-character-set=utf8mb4 --execute "source $($baseDatabase.Replace('\', '/'))"
if ($LASTEXITCODE -ne 0) { throw "Database import failed with exit code $LASTEXITCODE" }

$seedContent = "SET @e2e_sso_ticket='$Ticket';`nSET @e2e_second_sso_ticket='$SecondTicket';`n" + [System.IO.File]::ReadAllText($seed)
$seedContent | & $Mysql "--host=$($env:E2E_DB_HOST)" "--port=$($env:E2E_DB_PORT)" "--user=$($env:E2E_DB_USER)" "--database=$Database"
if ($LASTEXITCODE -ne 0) { throw "E2E seed failed with exit code $LASTEXITCODE" }
