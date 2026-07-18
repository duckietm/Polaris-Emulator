param(
    [Parameter(Mandatory = $true)]
    [string]$ItemsSource,
    [Parameter(Mandatory = $true)]
    [string]$FurnitureData,
    [Parameter(Mandatory = $true)]
    [string]$NitroRoot,
    [Parameter(Mandatory = $true)]
    [string]$Report,
    [switch]$ItemsJson,
    [switch]$RequireSwf,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $repoRoot 'Emulator'
$jar = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'target') -Filter '*-jar-with-dependencies.jar' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
$latestSource = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src') -Recurse -File |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if ($null -eq $jar -or ($null -ne $latestSource -and $latestSource.LastWriteTimeUtc -gt $jar.LastWriteTimeUtc)) {
    & mvn -B -f (Join-Path $moduleRoot 'pom.xml') -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jar = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'target') -Filter '*-jar-with-dependencies.jar' |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

$sourceOption = if ($ItemsJson) { '--items' } else { '--items-sql-dump' }
$arguments = @(
    '-cp', $jar.FullName,
    'com.eu.habbo.tools.furni.FurniConsistencyCli',
    $sourceOption, (Resolve-Path -LiteralPath $ItemsSource).Path,
    '--furniture-data', (Resolve-Path -LiteralPath $FurnitureData).Path,
    '--bundles', (Join-Path $NitroRoot 'nitro-assets\bundled\furniture'),
    '--icons', (Join-Path $NitroRoot 'swf\dcr\hof_furni\icons'),
    '--report', $Report
)
if ($RequireSwf) { $arguments += @('--swf', (Join-Path $NitroRoot 'swf\dcr\hof_furni')) }
if ($Quiet) { $arguments += '--quiet' }

& java @arguments
exit $LASTEXITCODE
