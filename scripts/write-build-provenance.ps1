param(
    [string]$OutputPath = "target/m21-supply-chain/build-provenance.properties"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot
try {
    $pom = Get-Content -Raw "pom.xml"
    $match = [regex]::Match($pom, '<version>([^<]+)</version>')
    if (-not $match.Success) { throw "Cannot resolve product version from pom.xml" }
    $version = $match.Groups[1].Value.Trim()
    $gitSha = (git rev-parse HEAD).Trim()
    $gitRef = (git describe --tags --exact-match HEAD 2>$null)
    if (-not $gitRef) {
        $gitRef = if ($env:GITHUB_REF_NAME) { $env:GITHUB_REF_NAME } else { (git branch --show-current).Trim() }
    }
    if (-not $gitRef) { $gitRef = "detached" }
    $clean = ((git status --porcelain | Measure-Object).Count -eq 0).ToString().ToLowerInvariant()
    $java = ((& java -version 2>&1 | Select-Object -First 1).ToString() -replace '[\r\n=]', ' ').Trim()
    $maven = ((& .\mvnw.cmd -v | Select-Object -First 1).ToString() -replace '[\r\n=]', ' ').Trim()
    $sbom = Join-Path $repoRoot "target/m21-supply-chain/morpheus-sbom.json"
    $sbomSha256 = if (Test-Path $sbom) { (Get-FileHash -Algorithm SHA256 $sbom).Hash.ToLowerInvariant() } else { "missing" }
    $generatedAt = [DateTimeOffset]::UtcNow.ToString("o")

    $output = Join-Path $repoRoot $OutputPath
    New-Item -ItemType Directory -Force -Path (Split-Path $output -Parent) | Out-Null
    @(
        "schema=morpheus-build-provenance-v1"
        "product=MORPHEUS"
        "version=$version"
        "gitSha=$gitSha"
        "gitRef=$gitRef"
        "workspaceClean=$clean"
        "os=$([System.Runtime.InteropServices.RuntimeInformation]::OSDescription -replace '[\r\n=]', ' ')"
        "java=$java"
        "maven=$maven"
        "sbomSha256=$sbomSha256"
        "generatedAt=$generatedAt"
    ) | Set-Content -Encoding UTF8 $output

    Write-Host "M21 provenance written to $output"
} finally {
    Pop-Location
}
