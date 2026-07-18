$ErrorActionPreference = 'Stop'

$script = Join-Path $PSScriptRoot 'build-latest.ps1'
$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("polaris-build-latest-" + [guid]::NewGuid())
$sourceDirectory = Join-Path $temporary 'source'
$destination = Join-Path $temporary 'latest'
$source = Join-Path $sourceDirectory 'Polaris-4.2.50-jar-with-dependencies.jar'

try {
    New-Item -ItemType Directory -Path $sourceDirectory -Force | Out-Null
    [System.IO.File]::WriteAllBytes($source, [byte[]](1, 2, 3, 4, 5, 255))

    & $script -SourceJar $source -Destination $destination

    $copy = Join-Path $destination (Split-Path $source -Leaf)
    $hashFile = "$copy.sha256"
    if(!(Test-Path -LiteralPath $copy)) { throw "Copied JAR is missing: $copy" }
    if(!(Test-Path -LiteralPath $hashFile)) { throw "SHA-256 file is missing: $hashFile" }

    $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    $copyHash = (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash.ToLowerInvariant()
    if($sourceHash -ne $copyHash) { throw 'Copied JAR hash differs from source' }

    $expected = "$sourceHash  $(Split-Path $copy -Leaf)"
    $actual = (Get-Content -Raw -LiteralPath $hashFile).Trim()
    if($actual -ne $expected) { throw "Unexpected checksum file: $actual" }

    $ci = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '..\.github\workflows\ci.yml')
    if($ci -notmatch 'build-latest\.test\.ps1') {
        throw 'CI does not run the build-latest contract test'
    }

    $release = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '..\.github\workflows\build-release.yml')
    if($release -notmatch 'sha256sum' -or $release -notmatch '\.sha256') {
        throw 'Release workflow does not publish the SHA-256 file'
    }

    Write-Output 'build-latest contract verified.'
}
finally {
    if(Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}
