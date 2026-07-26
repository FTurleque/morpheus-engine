param(
    [switch]$SkipUpdate,
    [switch]$SkipPackaging
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Branch = 'm15/acceptance-verification-evidence'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$LogRoot = Join-Path $RepoRoot 'target\validation\m15'
$MavenLog = Join-Path $LogRoot 'maven-clean-test.log'
$PackagingLog = Join-Path $LogRoot 'windows-packaging.log'

New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null
Set-Location $RepoRoot

function Write-Stage([string]$Title) {
    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor Cyan
    Write-Host ("M15 :: {0}" -f $Title) -ForegroundColor Cyan
    Write-Host ('=' * 78) -ForegroundColor Cyan
}

function Invoke-NativeLogged {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogFile
    )

    Write-Stage $Label
    & $Command @Arguments 2>&1 | Tee-Object -FilePath $LogFile
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode. Log: $LogFile"
    }
}

try {
    Write-Stage 'Workspace'

    $dirty = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw 'git status failed.'
    }
    if ($dirty.Count -gt 0) {
        Write-Host 'Working tree contains local changes:' -ForegroundColor Yellow
        $dirty | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        throw 'Refusing to update/test a dirty working tree. Commit or stash local changes first.'
    }

    if (-not $SkipUpdate) {
        Write-Host "Updating $Branch from origin..."
        git fetch origin
        if ($LASTEXITCODE -ne 0) { throw 'git fetch origin failed.' }

        $currentBranch = (git branch --show-current).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot determine current Git branch.' }
        if ($currentBranch -ne $Branch) {
            git switch $Branch
            if ($LASTEXITCODE -ne 0) { throw "Cannot switch to $Branch." }
        }

        git pull --ff-only origin $Branch
        if ($LASTEXITCODE -ne 0) { throw "Cannot fast-forward $Branch from origin." }
    }

    $testedSha = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve tested SHA.' }
    Write-Host "Branch : $Branch"
    Write-Host "SHA    : $testedSha"

    Write-Stage 'Toolchain'
    java -version
    if ($LASTEXITCODE -ne 0) { throw 'java -version failed.' }
    .\mvnw.cmd --version
    if ($LASTEXITCODE -ne 0) { throw 'Maven Wrapper validation failed.' }

    Invoke-NativeLogged `
        -Label 'Full Maven reactor — clean test' `
        -Command '.\mvnw.cmd' `
        -Arguments @('clean', 'test') `
        -LogFile $MavenLog

    if (-not $SkipPackaging) {
        Invoke-NativeLogged `
            -Label 'Windows portable packaging + smokes' `
            -Command 'powershell.exe' `
            -Arguments @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', '.\distribution\build-portable.ps1') `
            -LogFile $PackagingLog
    }

    Write-Stage 'RESULT'
    Write-Host 'M15 VALIDATION PASSED' -ForegroundColor Green
    Write-Host "Tested SHA : $testedSha" -ForegroundColor Green
    Write-Host "Maven log  : $MavenLog" -ForegroundColor Green
    if (-not $SkipPackaging) {
        Write-Host "Package log: $PackagingLog" -ForegroundColor Green
    } else {
        Write-Host 'Packaging   : SKIPPED by request' -ForegroundColor Yellow
    }
    exit 0
}
catch {
    Write-Stage 'RESULT'
    Write-Host 'M15 VALIDATION FAILED' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ''
    Write-Host 'Fix the reported failure, then rerun the SAME command.' -ForegroundColor Yellow
    exit 1
}
