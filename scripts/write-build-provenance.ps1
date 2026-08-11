param(
    [string]$OutputPath = "target/m21-supply-chain/build-provenance.properties"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Get-Sha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            return ([System.BitConverter]::ToString($sha256.ComputeHash($stream)) -replace '-', '').ToLowerInvariant()
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

Push-Location $repoRoot
try {
    $pom = Get-Content -Raw "pom.xml"
    $match = [regex]::Match($pom, '<version>([^<]+)</version>')
    if (-not $match.Success) { throw "Cannot resolve product version from pom.xml" }
    $version = $match.Groups[1].Value.Trim()
    $gitSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve git HEAD" }

    $gitRef = (git tag --points-at HEAD | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect tags pointing at git HEAD" }
    if ($gitRef) { $gitRef = $gitRef.ToString().Trim() }
    if (-not $gitRef) {
        $gitRef = if ($env:GITHUB_REF_NAME) { $env:GITHUB_REF_NAME } else { (git branch --show-current).Trim() }
        if ($LASTEXITCODE -ne 0) { throw "Cannot resolve current git branch" }
    }
    if (-not $gitRef) { $gitRef = "detached" }

    $clean = ((git status --porcelain --untracked-files=no | Measure-Object).Count -eq 0).ToString().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect tracked git workspace status" }

    # java -version writes its normal version banner to stderr. Windows PowerShell 5.1
    # converts redirected native stderr into ErrorRecord instances when
    # ErrorActionPreference=Stop, so capture both Java and Maven through cmd.exe.
    $javaOutput = @(& $env:ComSpec /d /c "java -version 2>&1")
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve Java version (exit $LASTEXITCODE)" }
    if ($javaOutput.Count -eq 0) { throw "Java version output is empty" }
    $java = ($javaOutput[0].ToString() -replace '[\r\n=]', ' ').Trim()

    $mavenOutput = @(& $env:ComSpec /d /c "mvnw.cmd -v 2>&1")
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve Maven Wrapper version (exit $LASTEXITCODE)" }
    if ($mavenOutput.Count -eq 0) { throw "Maven Wrapper version output is empty" }
    $maven = ($mavenOutput[0].ToString() -replace '[\r\n=]', ' ').Trim()

    $sbom = Join-Path $repoRoot "target/m21-supply-chain/morpheus-sbom.json"
    $sbomSha256 = if (Test-Path $sbom) { Get-Sha256 $sbom } else { "missing" }
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
