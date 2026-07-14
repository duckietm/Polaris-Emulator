param(
    [switch] $ValidateOnly,
    [string] $JarPath,
    [string] $RuntimeDirectory
)

$ErrorActionPreference = 'Stop'
$required = @(
    'E2E_DB_HOST', 'E2E_DB_PORT', 'E2E_DB_NAME', 'E2E_DB_USER',
    'E2E_SSO_TICKET', 'E2E_GAME_PORT', 'E2E_RAW_PORT', 'E2E_RCON_PORT'
)

foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "$name is required" }
}

if ($ValidateOnly) {
    Write-Output 'E2E environment is valid'
    exit 0
}

$repo = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $jar = Get-ChildItem (Join-Path $repo 'Emulator\target') -Filter '*-jar-with-dependencies.jar' |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if (-not $jar) { throw 'Packaged emulator JAR not found; run mvn package first' }
    $JarPath = $jar.FullName
}
if (-not (Test-Path -LiteralPath $JarPath)) { throw "JAR not found: $JarPath" }

if ([string]::IsNullOrWhiteSpace($RuntimeDirectory)) {
    $RuntimeDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "polaris-e2e-runtime-$PID"
}
New-Item -ItemType Directory -Force -Path $RuntimeDirectory | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $RuntimeDirectory 'logging\errors') | Out-Null

$config = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'config.ini.template'))
$values = @{
    E2E_DB_HOST = $env:E2E_DB_HOST
    E2E_DB_PORT = $env:E2E_DB_PORT
    E2E_DB_NAME = $env:E2E_DB_NAME
    E2E_DB_USER = $env:E2E_DB_USER
    E2E_DB_PASSWORD = $env:E2E_DB_PASSWORD
    E2E_GAME_PORT = $env:E2E_GAME_PORT
    E2E_RAW_PORT = $env:E2E_RAW_PORT
    E2E_RCON_PORT = $env:E2E_RCON_PORT
}
foreach ($entry in $values.GetEnumerator()) {
    $config = $config.Replace('${' + $entry.Key + '}', [string] $entry.Value)
}
[System.IO.File]::WriteAllText((Join-Path $RuntimeDirectory 'config.ini'), $config, [System.Text.UTF8Encoding]::new($false))

$stdout = Join-Path $RuntimeDirectory 'polaris.stdout.log'
$stderr = Join-Path $RuntimeDirectory 'polaris.stderr.log'
$process = Start-Process java -ArgumentList @('-Dhabbo.console.style=plain', '-jar', (Resolve-Path $JarPath).Path) -WorkingDirectory $RuntimeDirectory -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
$probe = "http://127.0.0.1:$($env:E2E_GAME_PORT)/__e2e/session-count?userId=900001"
$deadline = [DateTime]::UtcNow.AddSeconds(90)

while ([DateTime]::UtcNow -lt $deadline) {
    if ($process.HasExited) { throw "Polaris exited with code $($process.ExitCode); see $stderr" }
    try {
        $response = Invoke-RestMethod -Uri $probe -TimeoutSec 2
        if ($null -ne $response.activeSessions) {
            [pscustomobject]@{ pid = $process.Id; wsUrl = "ws://127.0.0.1:$($env:E2E_GAME_PORT)"; probeUrl = "http://127.0.0.1:$($env:E2E_GAME_PORT)/__e2e"; runtime = $RuntimeDirectory } | ConvertTo-Json -Compress
            exit 0
        }
    }
    catch {
    }
    Start-Sleep -Milliseconds 250
}

Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
throw "Polaris did not become ready before the 90 second deadline; see $stdout and $stderr"
