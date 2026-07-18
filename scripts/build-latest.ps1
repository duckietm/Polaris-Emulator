[CmdletBinding()]
param(
    [string]$Destination,
    [switch]$SkipTests,
    [string]$SourceJar
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$moduleRoot = Join-Path $repoRoot 'Emulator'

if([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $repoRoot 'Latest_Compiled_Version'
}

if([string]::IsNullOrWhiteSpace($SourceJar)) {
    Push-Location $moduleRoot
    try {
        $mavenArguments = @('-B', 'clean', 'package')
        if($SkipTests) { $mavenArguments += '-DskipTests' }
        & mvn @mavenArguments
        if($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }

    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $moduleRoot 'pom.xml')
    $artifactId = [string]$pom.project.artifactId
    $version = [string]$pom.project.version
    $SourceJar = Join-Path $moduleRoot "target\$artifactId-$version-jar-with-dependencies.jar"
}

$sourcePath = [System.IO.Path]::GetFullPath($SourceJar)
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if(!(Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
    throw "Built JAR not found: $sourcePath"
}

New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
$fileName = Split-Path $sourcePath -Leaf
$finalJar = Join-Path $destinationPath $fileName
$temporaryJar = "$finalJar.tmp-$PID"
$finalHash = "$finalJar.sha256"
$temporaryHash = "$finalHash.tmp-$PID"

try {
    Copy-Item -LiteralPath $sourcePath -Destination $temporaryJar -Force
    Move-Item -LiteralPath $temporaryJar -Destination $finalJar -Force

    $hash = (Get-FileHash -LiteralPath $finalJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksum = "$hash  $fileName$([Environment]::NewLine)"
    [System.IO.File]::WriteAllText(
        $temporaryHash,
        $checksum,
        [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryHash -Destination $finalHash -Force
}
finally {
    Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temporaryHash -Force -ErrorAction SilentlyContinue
}

Write-Output "JAR: $finalJar"
Write-Output "SHA256: $finalHash"
